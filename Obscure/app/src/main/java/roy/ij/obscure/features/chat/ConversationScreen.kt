package roy.ij.obscure.features.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.runtime.DisposableEffect
import roy.ij.obscure.util.CurrentChat
import roy.ij.obscure.features.chat.MsgType
import coil.compose.rememberAsyncImagePainter


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

    Column(Modifier.fillMaxSize()) {
        if (ui.loading) Text("Loading…")
        ui.error?.let { Text("Error: $it", color = Color.Red) }

        val listState = rememberLazyListState()
        LaunchedEffect(ui.messages.size) {
            if (ui.messages.isNotEmpty()) {
                listState.animateScrollToItem(ui.messages.lastIndex)
            }
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(ui.messages) { m ->
                val bubble = @Composable {
                    when (m.type) {
                        MsgType.TEXT -> Text(
                            "${m.alias}: ${m.text}",
                            Modifier
                                .padding(4.dp)
                                .background(Color.LightGray)
                                .padding(8.dp)
                                .widthIn(max = 260.dp)
                        )

                        MsgType.MEDIA -> MediaBubble(m) // 👈 handle media differently
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start
                ) { bubble() }
            }
        }
        Row(
            Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") }
            )

            IconButton(onClick = { pickFileLauncher.launch("*/*") }) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
            }

            Button(onClick = {
                if (input.isNotBlank()) {
                    viewModel.send(input)
                    input = ""
                }
            }) {
                Text("Send")
            }
        }

    }
}

@Composable
private fun MediaBubble(m: ChatMessage) {
    val context = LocalContext.current
    val isImage = m.mediaMime?.startsWith("image/") == true
    val clickable = Modifier
        .clip(RoundedCornerShape(10.dp))
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
        val model: Any = if (m.mediaLocalPath.startsWith("content:"))
            Uri.parse(m.mediaLocalPath)
        else
            java.io.File(m.mediaLocalPath)

        androidx.compose.foundation.Image(
            painter = coil.compose.rememberAsyncImagePainter(model),
            contentDescription = "image",
            modifier = clickable
                .widthIn(max = 260.dp)
                .heightIn(max = 260.dp)
        )
    } else {
        Row(
            clickable
                .background(Color.LightGray)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📎 ${m.mediaMime ?: "file"} (tap to open)")
        }
    }
}

private fun openFile(context: Context, path: String, mime: String) {
    val uri: Uri = if (path.startsWith("content:")) {
        Uri.parse(path)
    } else {
        val file = java.io.File(path)
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app to open this file type", Toast.LENGTH_SHORT).show()
    }
}
