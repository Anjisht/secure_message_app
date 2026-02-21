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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import roy.ij.obscure.util.CurrentChat
import java.io.File
import kotlin.math.max

// -------------------- Brand Colors --------------------
private val BrandGreen = Color(0xFF05C655)   // #05c655
private val BrandPeriwinkle = Color(0xFF91A6E1) // #91a6e1

private val ChatBg = Color(0xFFF6F7FB)
private val SurfaceSoft = Color(0xFFFFFFFF)
private val SurfaceSoft2 = Color(0xFFEEF1F7)
private val OutlineSoft = Color(0x1A0B1220) // subtle outline

private val BubbleMine = BrandGreen
private val BubbleOther = SurfaceSoft
private val BubbleOtherBorder = Color(0x140B1220)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(viewModel: ChatViewModel) {

    // Local theme override for a more “official” chat look
    val chatColorScheme = lightColorScheme(
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

    MaterialTheme(colorScheme = chatColorScheme) {

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

        val listState = rememberLazyListState()

        LaunchedEffect(ui.messages.size) {
            if (ui.messages.isNotEmpty()) {
                listState.animateScrollToItem(ui.messages.lastIndex)
            }
        }

        // Chat title logic
        val otherPersonName = remember(ui.messages) {
            ui.messages.firstOrNull { !it.mine }?.alias ?: "Conversation"
        }

        val isGroupChat = remember(ui.messages) {
            ui.messages.map { it.alias }.distinct().size > 2
        }

        // “Scroll to bottom” FAB visibility
        val showJumpToBottom by remember {
            derivedStateOf {
                val total = listState.layoutInfo.totalItemsCount
                if (total == 0) return@derivedStateOf false
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                // show if you are not near the bottom
                (total - 1) - lastVisible >= 4
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
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
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(BrandPeriwinkle.copy(alpha = 0.25f))
                                        .border(1.dp, BrandPeriwinkle.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = otherPersonName.take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = otherPersonName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isGroupChat) "Group chat" else "Secure conversation",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { /* TODO: menu */ }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            },
//            floatingActionButton = {
//                AnimatedVisibility(visible = showJumpToBottom) {
//                    FloatingActionButton(
//                        onClick = {
//                            if (ui.messages.isNotEmpty()) {
//                                // quick jump
//                                val target = max(0, ui.messages.lastIndex)
//                                // no animation feels “snappier” for jump button
//                                // but you can animate if you want
//                                // coroutine scope in FAB:
//                            }
//                        },
//                        containerColor = MaterialTheme.colorScheme.surface,
//                        contentColor = MaterialTheme.colorScheme.onSurface,
//                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
//                    ) {
//                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Jump to bottom")
//                    }
//                }
//            }
        ) { innerPadding ->

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                AnimatedVisibility(visible = ui.loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                ui.error?.let {
                    ErrorBanner(text = it)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(ui.messages) { index, m ->

                        val previous = ui.messages.getOrNull(index - 1)
                        val showSenderName =
                            isGroupChat &&
                                    !m.mine &&
                                    (previous == null || previous.alias != m.alias)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start
                            ) {

                                if (showSenderName) {
                                    Text(
                                        text = m.alias,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                                    )
                                }

                                when (m.type) {
                                    MsgType.TEXT -> MessageBubbleText(
                                        text = m.text.orEmpty(),
                                        mine = m.mine
                                    )
                                    MsgType.MEDIA -> MediaBubble(m)
                                }
                            }
                        }
                    }
                }

                // INPUT BAR
                MessageInputBar(
                    input = input,
                    onInputChange = { input = it },
                    onAttach = { pickFileLauncher.launch("*/*") },
                    onSend = {
                        if (input.isNotBlank()) {
                            viewModel.send(input.trim())
                            input = ""
                        }
                    }
                )
            }
        }
    }
}

// -------------------- UI Components --------------------

@Composable
private fun ErrorBanner(text: String) {
    Surface(
        color = Color(0xFFFFF2F2),
        contentColor = Color(0xFFB42318),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp
    ) {
        Text(
            text = "Error: $text",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
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

    val bg = if (mine) BubbleMine else BubbleOther
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
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
            .background(bg)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MessageInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {

            // Rounded input container
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                )
            )

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = onAttach,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach")
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
                modifier = Modifier.size(46.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

// -------------------- Media Bubble --------------------

@Composable
private fun MediaBubble(m: ChatMessage) {
    val context = LocalContext.current
    val isImage = m.mediaMime?.startsWith("image/") == true
    val path = m.mediaLocalPath
    val mime = m.mediaMime ?: "application/octet-stream"

    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (m.mine) 18.dp else 6.dp,
        bottomEnd = if (m.mine) 6.dp else 18.dp
    )

    val clickable = Modifier
        .widthIn(max = 320.dp)
        .shadow(elevation = if (m.mine) 2.dp else 1.dp, shape = shape, clip = false)
        .clip(shape)
        .clickable(
            enabled = path != null,
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {
            path?.let { openFile(context, it, mime) }
        }

    if (isImage && path != null) {
        val model: Any =
            if (path.startsWith("content:")) Uri.parse(path) else File(path)

        Image(
            painter = rememberAsyncImagePainter(model),
            contentDescription = "image",
            modifier = clickable
                .heightIn(max = 320.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), shape)
        )
    } else {
        val label = remember(path, mime) {
            val fileName =
                if (path == null) "Attachment"
                else if (path.startsWith("content:")) "Attachment"
                else runCatching { File(path).name }.getOrDefault("Attachment")

            val shortMime = when {
                mime.contains("pdf", ignoreCase = true) -> "PDF"
                mime.startsWith("video/") -> "Video"
                mime.startsWith("audio/") -> "Audio"
                mime.startsWith("image/") -> "Image"
                else -> "File"
            }

            "$shortMime • $fileName"
        }

        Surface(
            modifier = clickable,
            color = if (m.mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
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
                Text(
                    text = "📎",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = label,
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

// -------------------- File Opener --------------------

private fun openFile(context: Context, path: String, mime: String) {
    val uri: Uri =
        if (path.startsWith("content:")) {
            Uri.parse(path)
        } else {
            val file = File(path)
            FileProvider.getUriForFile(
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