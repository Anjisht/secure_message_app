package roy.ij.obscure.features.chat

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import roy.ij.obscure.App
import roy.ij.obscure.data.crypto.CryptoHelper
import roy.ij.obscure.data.network.SocketManager
import roy.ij.obscure.data.network.MemberDto
import roy.ij.obscure.data.network.RetrofitClient
import roy.ij.obscure.data.network.ApiService
import roy.ij.obscure.data.network.UploadUrlReq
import javax.crypto.SecretKey
import java.time.Instant
import roy.ij.obscure.analytics.AnalyticsTracker

enum class MsgType { TEXT, MEDIA }
enum class MessageStatus { SENDING, SENT, FAILED }

data class ChatMessage(
    val id: String?,
    val alias: String,
    val text: String? = null,
    val mine: Boolean,
    val at: Long,
    val type: MsgType = MsgType.TEXT,
    val mediaLocalPath: String? = null,
    val mediaMime: String? = null,
    val status: MessageStatus = MessageStatus.SENT
)

data class ChatUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val myUserId: String? = null,
    val roomId: String,
    val members: List<MemberDto> = emptyList(),
    val messages: List<ChatMessage> = emptyList()
)



@RequiresApi(Build.VERSION_CODES.O)
class ChatViewModel(
    private val token: String,
    private val roomId: String,
    private val api: ApiService = RetrofitClient.api,
    private val repo: ChatRepository = ChatRepository(RetrofitClient.api)
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState(roomId = roomId))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val socket = SocketManager(token)

    companion object {
        private const val SOCKET_ACK_TIMEOUT_MS = 10_000L
    }

    init {
        viewModelScope.launch {
            try {
                // 1) who am I
                val meResp = repo.me(token) // { user: { id, username } }
                val meUser = (meResp["user"] as? Map<*, *>) ?: throw IllegalStateException("me failed")
                val myUserId = meUser["id"] as String

                // 2) load members & keys
                val membersResp = repo.members(token, roomId)
                val members = membersResp.members

                // 3) connect socket + join
                socket.connect()
                socket.joinRoom(roomId) { /* ack ignored here */ }

                // 4) subscribe to incoming messages
                socket.onNewMessage { msg ->
                    try {
                        val senderId = msg.optString("senderId")
                        val myId = state.value.myUserId
                        if (senderId == myId) return@onNewMessage

                        val type = msg.optString("type", "text")
                        val alias = msg.optString("alias", "Unknown")

                        // 1️⃣ find my encryption key
                        var myEncKey: String? = null
                        if (msg.has("encKey")) {
                            // backend now sends this flat field
                            myEncKey = msg.getString("encKey")
                        } else if (msg.has("keyEnvelope")) {
                            val envs = msg.getJSONArray("keyEnvelope")
                            for (i in 0 until envs.length()) {
                                val e = envs.getJSONObject(i)
                                if (e.getString("userId") == myId) {
                                    myEncKey = e.getString("encKey")
                                    break
                                }
                            }
                        }

                        if (myEncKey == null) {
                            appendMessage(ChatMessage(null, "System", "⚠️ no key for this message", false, System.currentTimeMillis()))
                            return@onNewMessage
                        }

                        val secret = CryptoHelper.unwrapAesKey(myEncKey)
                        val iv = msg.optString("iv", "")

                        if (type == "media") {
                            // ---------- MEDIA MESSAGE ----------
                            val fileUrl = msg.optString("fileUrl", "")
                            val fileKey = msg.optString("fileKey", null)
                            val mime = msg.optString("fileMime", "application/octet-stream")

                            // find my AES key
                            val myEncKey = when {
                                msg.has("encKey") -> msg.getString("encKey")
                                msg.has("keyEnvelope") -> {
                                    val envs = msg.getJSONArray("keyEnvelope")
                                    var k: String? = null
                                    for (i in 0 until envs.length()) {
                                        val e = envs.getJSONObject(i)
                                        if (e.getString("userId") == myId) {
                                            k = e.getString("encKey"); break
                                        }
                                    }
                                    k
                                }
                                else -> null
                            }

                            if (myEncKey == null) {
                                appendMessage(ChatMessage(null, "System", "⚠️ no key for this media", false, System.currentTimeMillis()))
                                return@onNewMessage
                            }

                            val secret = CryptoHelper.unwrapAesKey(myEncKey)
                            val iv = msg.optString("iv", "")

                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    // 1️⃣ download encrypted file directly
                                    val encBytes: ByteArray = if (!fileKey.isNullOrEmpty()) {
                                        val signed = api.getDownloadUrl("Bearer $token", fileKey)
                                        download(signed.downloadUrl)
                                    } else {
                                        download(fileUrl) // fallback
                                    }

                                    // 2️⃣ decrypt bytes
                                    val plain = CryptoHelper.decryptBytes(encBytes, iv, secret)

                                    // 3️⃣ determine file extension
                                    val ext = android.webkit.MimeTypeMap.getSingleton()
                                        .getExtensionFromMimeType(mime) ?: "bin"

                                    // 4️⃣ save decrypted file to cache
                                    val file = java.io.File(App.context.cacheDir, "bc_${System.currentTimeMillis()}.$ext")
                                    file.outputStream().use { it.write(plain) }

                                    // 5️⃣ append message with file path
                                    withContext(Dispatchers.Main) {
                                        appendMessage(
                                            ChatMessage(
                                                id = null,
                                                alias = alias,
                                                text = null,
                                                mine = false,
                                                at = System.currentTimeMillis(),
                                                type = MsgType.MEDIA,
                                                mediaLocalPath = file.absolutePath,
                                                mediaMime = mime
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    AnalyticsTracker.recordHandledException(e, "chat")
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        appendMessage(ChatMessage(null, "System", "⚠️ media decrypt failed", false, System.currentTimeMillis()))
                                    }
                                }
                            }
                            return@onNewMessage
                        }
                        else {
                            // ---------- TEXT MESSAGE ----------
                            val text = CryptoHelper.decryptAes(msg.getString("ciphertext"), iv, secret)
                            val id = msg.optString("_id", null)
                            val at = Instant.parse(msg.getString("createdAt")).toEpochMilli()
                            appendMessage(ChatMessage(id, alias, text, mine = false, at = at))
                        }
                    } catch (e: Exception) {
                        AnalyticsTracker.recordHandledException(e, "chat")
                        e.printStackTrace()
                        appendMessage(ChatMessage(null, "System", "⚠️ failed to decrypt message", false, System.currentTimeMillis()))
                    }
                }


                // 5) (optional) load history once
                // 5) Load and decrypt message history before listening to new messages
                loadHistory(token, roomId, myUserId)


                _state.update { it.copy(loading = false, myUserId = myUserId, members = members) }
            } catch (e: Exception) {
                AnalyticsTracker.recordHandledException(e, "chat")
                _state.update { it.copy(loading = false, error = e.message ?: "init failed") }
            }
        }
    }

    fun send(text: String) {
        val s = state.value
        val myUserId = s.myUserId ?: return
        val members = s.members.filter { it.publicKey != null } // only those with keys

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // 1) encrypt content once
                val secret = CryptoHelper.generateAesKey()
                val (ciphertext, iv) = CryptoHelper.encryptAes(text, secret)

                // 2) wrap AES key for EVERY approved member (including self so history works offline)
                val envelopes = members.map { m ->
                    val encKey = CryptoHelper.wrapAesKey(secret, m.publicKey!!)
                    mapOf("userId" to m.userId, "encKey" to encKey)
                }

                // 🔹 Debug log here
                println("📤 Sending message with envelopes:")
                envelopes.forEach {
                    println(" -> userId=${it["userId"]}, encKey.len=${(it["encKey"] as String).length}")
                }

                // 3) payload
                val payload = JSONObject(
                    mapOf(
                        "roomId" to s.roomId,
                        "ciphertext" to ciphertext,
                        "iv" to iv,
                        "keyEnvelope" to envelopes
                    )
                )

                // 4) pending UI, then emit and wait for server persistence ack
                val localId = "local-${System.currentTimeMillis()}"
                appendMessage(
                    ChatMessage(
                        id = localId,
                        alias = "Me",
                        text = text,
                        mine = true,
                        at = System.currentTimeMillis(),
                        status = MessageStatus.SENDING
                    )
                )

                val ack = sendSocketMessage(payload)
                if (ack?.optBoolean("ok", false) == true) {
                    AnalyticsTracker.action(
                        "send_message_success",
                        mapOf("feature" to "chat", "message_type" to "text")
                    )
                    updateMessage(localId) { current ->
                        current.copy(
                            id = ack.optString("id").takeIf { it.isNotBlank() } ?: current.id,
                            at = parseEpochMillis(ack.optString("createdAt")) ?: current.at,
                            status = MessageStatus.SENT
                        )
                    }
                } else {
                    AnalyticsTracker.failure("send_message", "socket_ack_failed")
                    updateMessage(localId) { current ->
                        current.copy(status = MessageStatus.FAILED)
                    }
                }
            } catch (e: Exception) {
                AnalyticsTracker.failure("send_message", e.message ?: "exception")
                AnalyticsTracker.recordHandledException(e, "chat")
                e.printStackTrace()
                appendMessage(ChatMessage(null, "System", "❌ send failed: ${e.message}", true, System.currentTimeMillis()))
            }
        }
    }

    private fun appendMessage(m: ChatMessage) {
        _state.update { it.copy(messages = it.messages + m) }
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _state.update { current ->
            current.copy(messages = current.messages.map { message ->
                if (message.id == id) transform(message) else message
            })
        }
    }

    private suspend fun sendSocketMessage(payload: JSONObject): JSONObject? =
        withTimeoutOrNull(SOCKET_ACK_TIMEOUT_MS) {
            val ack = CompletableDeferred<JSONObject?>()
            socket.sendMessage(payload) { response ->
                ack.complete(response)
            }
            ack.await()
        }

    private fun parseEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private suspend fun loadHistory(token: String, roomId: String, myId: String) = withContext(Dispatchers.IO) {
        try {
            val hist = repo.history(token, roomId)
            val arr = (hist["messages"] as? List<*>) ?: return@withContext
            val msgs = arr.mapNotNull { any ->
                try {
                    val m = any as Map<*, *>
                    val envs = m["keyEnvelope"] as? List<*> ?: return@mapNotNull null
                    val myEnv = envs.firstOrNull { (it as Map<*, *>)["userId"] == myId } as? Map<*, *> ?: return@mapNotNull null
                    val secret = CryptoHelper.unwrapAesKey(myEnv["encKey"] as String)
                    val type = (m["type"] as? String) ?: "text"
                    val alias = m["alias"]?.toString() ?: "Unknown"
                    val at = Instant.parse(m["createdAt"] as String).toEpochMilli()
                    val iv = (m["iv"] as? String) ?: ""

                    if (type == "media") {
                        val isMine = (m["senderId"]?.toString() == myId)
                        val fileKey = m["fileKey"]?.toString()
                        val fileUrl = m["fileUrl"]?.toString()
                        val mime = m["fileMime"]?.toString() ?: "application/octet-stream"

                        // 1️⃣ Download encrypted bytes (prefer signed URL if available)
                        val encBytes = when {
                            !fileKey.isNullOrBlank() -> {
                                val signed = api.getDownloadUrl("Bearer $token", fileKey)
                                download(signed.downloadUrl)
                            }
                            !fileUrl.isNullOrBlank() -> download(fileUrl)
                            else -> return@mapNotNull null
                        }

                        // 2️⃣ Decrypt the bytes
                        val plain = CryptoHelper.decryptBytes(encBytes, iv, secret)

                        // 3️⃣ Determine extension & save locally
                        val ext = android.webkit.MimeTypeMap.getSingleton()
                            .getExtensionFromMimeType(mime) ?: "bin"
                        val file = java.io.File(App.context.cacheDir, "hist_${System.currentTimeMillis()}.$ext")
                        file.outputStream().use { it.write(plain) }

                        // 4️⃣ Return as ChatMessage with media info
                        ChatMessage(
                            id = m["_id"]?.toString(),
                            alias = alias,
                            text = null, // ✅ we don’t show text for media
                            mine = isMine,
                            at = at,
                            type = MsgType.MEDIA,
                            mediaLocalPath = file.absolutePath,
                            mediaMime = mime
                        )
                    } else {
                        // --- handle text messages ---
                        val text = CryptoHelper.decryptAes(
                            m["ciphertext"] as String,
                            iv,
                            secret
                        )
                        ChatMessage(
                            id = m["_id"]?.toString(),
                            alias = alias,
                            text = text,
                            mine = (m["senderId"] as? String) == myId,
                            at = at
                        )
                    }
                } catch (e: Exception) {
                    AnalyticsTracker.recordHandledException(e, "chat")
                    e.printStackTrace()
                    null
                }
            }
            _state.update { it.copy(messages = msgs.sortedBy { it.at }) }
        } catch (e: Exception) {
            AnalyticsTracker.recordHandledException(e, "chat")
            e.printStackTrace()
            appendMessage(ChatMessage(null, "System", "⚠️ failed to load history", false, System.currentTimeMillis()))
        }
    }

    override fun onCleared() {
        super.onCleared()
        socket.disconnect()
    }

    fun sendMedia(uri: Uri) {
        val ctx = App.instance // or pass from Composable
        val members = state.value.members
        val localId = "local-media-${System.currentTimeMillis()}"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = ctx.contentResolver
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"

                withContext(Dispatchers.Main) {
                    appendMessage(
                        ChatMessage(
                            id = localId,
                            alias = "Me",
                            mine = true,
                            at = System.currentTimeMillis(),
                            type = MsgType.MEDIA,
                            // Show selected file immediately using its content URI
                            mediaLocalPath = uri.toString(),
                            mediaMime = mime,
                            status = MessageStatus.SENDING
                        )
                    )
                }

                // 1️⃣ get presigned URL
                val upload = api.getUploadUrl("Bearer $token", UploadUrlReq(mime))

                // 2️⃣ read bytes
                val input = contentResolver.openInputStream(uri)!!
                val plain = input.readBytes()
                input.close()

                // 3️⃣ encrypt file
                val secret = CryptoHelper.generateAesKey()
                val (ciphertext, iv) = CryptoHelper.encryptBytes(plain, secret)

                // 4️⃣ upload encrypted bytes
                val ok = putToUrl(upload.uploadUrl, ciphertext, mime)
                if (!ok) throw Exception("Upload failed")

                // 5️⃣ wrap AES key for each member
                val envelopes = members.map { m ->
                    val encKey = CryptoHelper.wrapAesKey(secret, m.publicKey!!)
                    mapOf("userId" to m.userId, "encKey" to encKey)
                }

                // 6️⃣ emit socket message
                val payload = JSONObject(
                    mapOf(
                        "roomId" to state.value.roomId,
                        "type" to "media",
                        "fileUrl" to upload.fileUrl,
                        "fileKey" to upload.fileKey,
                        "fileMime" to mime,
                        "iv" to iv,
                        "keyEnvelope" to envelopes
                    )
                )
                val ack = sendSocketMessage(payload)
                if (ack?.optBoolean("ok", false) == true) {
                    AnalyticsTracker.action(
                        "send_message_success",
                        mapOf("feature" to "chat", "message_type" to "media")
                    )
                    updateMessage(localId) { current ->
                        current.copy(
                            id = ack.optString("id").takeIf { it.isNotBlank() } ?: current.id,
                            at = parseEpochMillis(ack.optString("createdAt")) ?: current.at,
                            status = MessageStatus.SENT
                        )
                    }
                } else {
                    AnalyticsTracker.failure("send_message", "media_socket_ack_failed")
                    updateMessage(localId) { current ->
                        current.copy(status = MessageStatus.FAILED)
                    }
                }


            } catch (e: Exception) {
                AnalyticsTracker.failure("send_message", e.message ?: "exception")
                AnalyticsTracker.recordHandledException(e, "chat")
                e.printStackTrace()
                updateMessage(localId) { current ->
                    current.copy(status = MessageStatus.FAILED)
                }
            }
        }
    }

    private fun putToUrl(url: String, data: ByteArray, mime: String): Boolean {
        return try {
            val req = okhttp3.Request.Builder()
                .url(url)
                .put(okhttp3.RequestBody.create(mime.toMediaTypeOrNull(), data))
                .build()
            val resp = okhttp3.OkHttpClient().newCall(req).execute()
            resp.isSuccessful.also { resp.close() }
        } catch (_: Exception) { false }
    }

    private fun download(url: String): ByteArray {
        val req = okhttp3.Request.Builder().url(url).build()
        return okhttp3.OkHttpClient().newCall(req).execute().use { it.body!!.bytes() }
    }

}
