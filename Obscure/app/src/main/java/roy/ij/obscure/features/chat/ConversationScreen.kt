package roy.ij.obscure.features.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.runtime.DisposableEffect
import roy.ij.obscure.util.CurrentChat
import roy.ij.obscure.features.chat.MsgType
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ConversationScreen(viewModel: ChatViewModel) {

    val ui by viewModel.state.collectAsState()
    val roomId = ui.roomId

    DisposableEffect(roomId) {
        CurrentChat.set(roomId)
        onDispose { CurrentChat.set(null) }
    }

    var input by remember { mutableStateOf("") }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendMedia(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        // Avatar Placeholder
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ui.messages.firstOrNull()?.alias?.take(1)
                                    ?: "?",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Text(
                            text = ui.messages.firstOrNull()?.alias
                                ?: "Conversation",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            if (ui.loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ui.error?.let {
                Text(
                    "Error: $it",
                    color = Color.Red,
                    modifier = Modifier.padding(8.dp)
                )
            }

            val listState = rememberLazyListState()

            LaunchedEffect(ui.messages.size) {
                if (ui.messages.isNotEmpty()) {
                    listState.animateScrollToItem(ui.messages.lastIndex)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ui.messages) { m ->

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            if (m.mine) Arrangement.End else Arrangement.Start
                    ) {

                        Column(
                            horizontalAlignment =
                                if (m.mine) Alignment.End else Alignment.Start
                        ) {

                            if (!m.mine) {
                                Text(
                                    text = m.alias,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            when (m.type) {
                                MsgType.TEXT -> {

                                    Box(
                                        modifier = Modifier
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 16.dp,
                                                    topEnd = 16.dp,
                                                    bottomStart = if (m.mine) 16.dp else 4.dp,
                                                    bottomEnd = if (m.mine) 4.dp else 16.dp
                                                )
                                            )
                                            .background(
                                                if (m.mine)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .padding(
                                                horizontal = 12.dp,
                                                vertical = 8.dp
                                            )
                                            .widthIn(max = 280.dp)
                                    ) {
                                        Text(
                                            text = m.text ?: "",
                                            color =
                                                if (m.mine)
                                                    MaterialTheme.colorScheme.onPrimary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                MsgType.MEDIA -> {
                                    MediaBubble(m)
                                }
                            }
                        }
                    }
                }
            }

            // INPUT BAR
            Surface(
                tonalElevation = 4.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message…") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )

                    Spacer(Modifier.width(8.dp))

                    IconButton(onClick = {
                        pickFileLauncher.launch("*/*")
                    }) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Attach"
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Button(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.send(input)
                                input = ""
                            }
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaBubble(m: ChatMessage) {
    val context = LocalContext.current
    val isImage = m.mediaMime?.startsWith("image/") == true

    val clickable = Modifier
        .clip(RoundedCornerShape(12.dp))
        .clickable(
            enabled = m.mediaLocalPath != null,
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {
            m.mediaLocalPath?.let {
                openFile(context, it, m.mediaMime ?: "application/octet-stream")
            }
        }

    if (isImage && m.mediaLocalPath != null) {
        val model: Any =
            if (m.mediaLocalPath.startsWith("content:"))
                Uri.parse(m.mediaLocalPath)
            else
                java.io.File(m.mediaLocalPath)

        androidx.compose.foundation.Image(
            painter = rememberAsyncImagePainter(model),
            contentDescription = "image",
            modifier = clickable
                .widthIn(max = 280.dp)
                .heightIn(max = 280.dp)
        )
    } else {
        Row(
            clickable
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📎 ${m.mediaMime ?: "file"} (tap to open)")
        }
    }
}

private fun openFile(context: Context, path: String, mime: String) {
    val uri: Uri =
        if (path.startsWith("content:")) {
            Uri.parse(path)
        } else {
            val file = java.io.File(path)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "No app to open this file type",
            Toast.LENGTH_SHORT
        ).show()
    }
}