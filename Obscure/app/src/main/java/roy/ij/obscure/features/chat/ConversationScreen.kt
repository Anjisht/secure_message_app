package roy.ij.obscure.features.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import roy.ij.obscure.analytics.AnalyticsTracker
import roy.ij.obscure.util.CurrentChat
import java.io.File

private val BrandGreen = Color(0xFF05C655)
private val BrandPeriwinkle = Color(0xFF91A6E1)

private val ChatBg = Color(0xFFF6F7FB)
private val SurfaceSoft = Color(0xFFFFFFFF)
private val SurfaceSoft2 = Color(0xFFEEF1F7)
private val OutlineSoft = Color(0x1A0B1220)

private val BubbleMine = BrandGreen
private val BubbleOther = SurfaceSoft
private val BubbleOtherBorder = Color(0x140B1220)

private val ChatColorScheme = lightColorScheme(
    primary = BrandGreen,
    secondary = BrandPeriwinkle,
    background = ChatBg,
    surface = SurfaceSoft,
    surfaceVariant = SurfaceSoft2,
    outline = OutlineSoft,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF101828),
    onSurface = Color(0xFF101828),
    onSurfaceVariant = Color(0xFF3B475E),
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ConversationScreen(viewModel: ChatViewModel) {
    ConversationRoute(viewModel = viewModel)
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ConversationRoute(viewModel: ChatViewModel) {
    val uiState by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }

    DisposableEffect(uiState.roomId) {
        CurrentChat.set(uiState.roomId)
        onDispose { CurrentChat.set(null) }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            AnalyticsTracker.action(
                "send_message_attempt",
                mapOf("feature" to "chat", "message_type" to "media")
            )
            viewModel.sendMedia(it)
        }
    }

    ConversationContent(
        uiState = uiState,
        input = input,
        onInputChange = { input = it },
        onSend = {
            val message = input.trim()
            if (message.isNotEmpty()) {
                AnalyticsTracker.action(
                    "send_message_attempt",
                    mapOf("feature" to "chat", "message_type" to "text")
                )
                viewModel.send(message)
                input = ""
            }
        },
        onAttach = {
            AnalyticsTracker.action("attach_media_tap", mapOf("feature" to "chat"))
            pickFileLauncher.launch("*/*")
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversationContent(
    uiState: ChatUiState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier
) {
    MaterialTheme(colorScheme = ChatColorScheme) {
        val listState = rememberLazyListState()
        val title = rememberConversationTitle(uiState)
        val isGroupChat = rememberIsGroupChat(uiState)

        LaunchedEffect(uiState.messages.size) {
            if (uiState.messages.isNotEmpty()) {
                listState.animateScrollToItem(uiState.messages.lastIndex)
            }
        }

        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("conversation_screen"),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                ConversationTopBar(
                    title = title,
                    subtitle = if (isGroupChat) "Group chat" else "Secure conversation"
                )
            },
            bottomBar = {
                ConversationComposerHost {
                    ConversationComposer(
                        input = input,
                        onInputChange = onInputChange,
                        onAttach = onAttach,
                        onSend = onSend
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imeNestedScroll()
                    .testTag("conversation_messages"),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AnimatedVisibility(visible = uiState.loading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("conversation_loading"),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                uiState.error?.let { error ->
                    item {
                        ErrorBanner(text = error)
                    }
                }

                itemsIndexed(
                    items = uiState.messages,
                    key = { index, message -> message.id ?: "${message.at}-$index" }
                ) { index, message ->
                    val previous = uiState.messages.getOrNull(index - 1)
                    val showSenderName = isGroupChat &&
                        !message.mine &&
                        (previous == null || previous.alias != message.alias)

                    MessageRow(
                        message = message,
                        showSenderName = showSenderName
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberConversationTitle(uiState: ChatUiState): String {
    return remember(uiState.messages, uiState.members) {
        uiState.messages.firstOrNull { !it.mine }?.alias
            ?: uiState.members.firstOrNull { it.userId != uiState.myUserId }?.alias
            ?: "Conversation"
    }
}

@Composable
private fun rememberIsGroupChat(uiState: ChatUiState): Boolean {
    return remember(uiState.messages, uiState.members) {
        val distinctAliases = uiState.messages.map { it.alias }.distinct().size
        val memberCount = uiState.members.size
        distinctAliases > 2 || memberCount > 2
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ConversationTopBar(
    title: String,
    subtitle: String
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(BrandPeriwinkle.copy(alpha = 0.24f))
                            .border(1.dp, BrandPeriwinkle.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = { }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
private fun ConversationComposerHost(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .testTag("conversation_composer"),
        tonalElevation = 4.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        content()
    }
}

@Composable
fun ConversationComposer(
    input: String,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
            placeholder = { Text("Type a message...") },
            shape = RoundedCornerShape(24.dp),
            maxLines = 5,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (input.isNotBlank()) onSend()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
        )

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = onAttach,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .semantics { contentDescription = "Attach file" }
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null)
        }

        Spacer(Modifier.width(8.dp))

        FilledIconButton(
            onClick = onSend,
            enabled = input.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
            ),
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Send message" }
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
        }
    }
}

@Composable
private fun ErrorBanner(text: String) {
    Surface(
        color = Color(0xFFFFF2F2),
        contentColor = Color(0xFFB42318),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    showSenderName: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start
        ) {
            if (showSenderName) {
                Text(
                    text = message.alias,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                )
            }

            when (message.type) {
                MsgType.TEXT -> MessageBubbleText(
                    text = message.text.orEmpty(),
                    mine = message.mine
                )
                MsgType.MEDIA -> MediaBubble(message)
            }

            MessageStatusLabel(message)
        }
    }
}

@Composable
private fun MessageStatusLabel(message: ChatMessage) {
    if (!message.mine || message.status == MessageStatus.SENT) return

    Text(
        text = when (message.status) {
            MessageStatus.SENDING -> "Sending..."
            MessageStatus.FAILED -> "Failed to send"
            MessageStatus.SENT -> ""
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (message.status == MessageStatus.FAILED) {
            Color(0xFFB42318)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(top = 2.dp, end = 6.dp)
    )
}

@Composable
private fun MessageBubbleText(
    text: String,
    mine: Boolean
) {
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 6.dp,
        bottomEnd = if (mine) 6.dp else 18.dp
    )

    val background = if (mine) BubbleMine else BubbleOther
    val foreground = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val borderColor = if (mine) Color.Transparent else BubbleOtherBorder

    Box(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .shadow(
                elevation = if (mine) 2.dp else 1.dp,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MediaBubble(message: ChatMessage) {
    val context = LocalContext.current
    val path = message.mediaLocalPath
    val mime = message.mediaMime ?: "application/octet-stream"
    val isImage = message.mediaMime?.startsWith("image/") == true

    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (message.mine) 18.dp else 6.dp,
        bottomEnd = if (message.mine) 6.dp else 18.dp
    )

    val clickable = Modifier
        .widthIn(max = 320.dp)
        .shadow(elevation = if (message.mine) 2.dp else 1.dp, shape = shape, clip = false)
        .clip(shape)
        .clickable(
            enabled = path != null,
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {
            path?.let { openFile(context, it, mime) }
        }

    if (isImage && path != null) {
        val model: Any = if (path.startsWith("content:")) Uri.parse(path) else File(path)

        Image(
            painter = rememberAsyncImagePainter(model),
            contentDescription = "Image attachment",
            modifier = clickable
                .heightIn(max = 320.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), shape)
        )
    } else {
        Surface(
            modifier = clickable,
            color = if (message.mine) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = shape,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = attachmentLabel(path, mime),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to open",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun attachmentLabel(path: String?, mime: String): String {
    val fileName = when {
        path.isNullOrBlank() -> "Attachment"
        path.startsWith("content:") -> "Attachment"
        else -> runCatching { File(path).name }.getOrDefault("Attachment")
    }

    val shortMime = when {
        mime.contains("pdf", ignoreCase = true) -> "PDF"
        mime.startsWith("video/") -> "Video"
        mime.startsWith("audio/") -> "Audio"
        mime.startsWith("image/") -> "Image"
        else -> "File"
    }

    return "$shortMime - $fileName"
}

private fun openFile(context: Context, path: String, mime: String) {
    val uri = if (path.startsWith("content:")) {
        Uri.parse(path)
    } else {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(path)
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
