package roy.ij.obscure.features.chat

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import roy.ij.obscure.navigation.NavRoutes

// Brand colors
private val BrandGreen = Color(0xFF05C655)      // #05c655
private val BrandPeriwinkle = Color(0xFF91A6E1) // #91a6e1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    navController: NavController,
    viewModel: RoomViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    var code by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var roomId by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }

    val scheme = lightColorScheme(
        primary = BrandGreen,
        secondary = BrandPeriwinkle,
        background = Color(0xFFF6F7FB),
        surface = Color.White,
        surfaceVariant = Color(0xFFF0F3FA),
        outline = Color(0x1A0B1220),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF101828),
        onSurface = Color(0xFF101828),
        onSurfaceVariant = Color(0xFF3B475E),
        error = Color(0xFFD92D20),
        onError = Color.White
    )

    val bg = Brush.verticalGradient(
        colors = listOf(
            BrandPeriwinkle.copy(alpha = 0.18f),
            Color.Transparent,
            BrandGreen.copy(alpha = 0.10f)
        )
    )

    val durationValue = duration.trim().toIntOrNull()
    val durationInvalid = duration.isNotBlank() && durationValue == null
    val canCreate = !state.isLoading && !durationInvalid
    val canJoin = !state.isLoading && roomId.trim().isNotEmpty()

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Create / Join Rooms",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(bg)
            ) {
                val scroll = rememberScrollState()

                // Loading bar pinned to top
                AnimatedVisibility(visible = state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .widthIn(max = 560.dp)
                        .align(Alignment.TopCenter)
                        .verticalScroll(scroll)
                        .safeDrawingPadding()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HeaderHero()

                    StatusBanner(
                        loading = state.isLoading,
                        error = state.error,
                        message = state.message,
                        roomId = state.roomId,
                        alias = state.alias
                    )

                    PremiumCard(
                        title = "Create Room",
                        subtitle = "Start a new private room. Share the Room ID with others.",
                        icon = Icons.Default.Add,
                        iconTint = BrandGreen
                    ) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Code phrase (optional)") },
                            placeholder = { Text("Add an optional passphrase") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                            colors = premiumTextFieldColors()
                        )

                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Duration (minutes)") },
                            placeholder = { Text("e.g., 30") },
                            singleLine = true,
                            isError = durationInvalid,
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.Timer, null) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            supportingText = {
                                if (durationInvalid) Text("Please enter a valid number.")
                                else Text("Leave empty for default duration (if supported).")
                            },
                            colors = premiumTextFieldColors()
                        )

                        Button(
                            onClick = {
                                Log.d(
                                    "RoomScreen",
                                    "Creating room with code=$code duration=$duration"
                                )
                                viewModel.createRoom(code.trim().ifBlank { null }, durationValue)
                            },
                            enabled = canCreate,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.35f
                                ),
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(
                                    alpha = 0.9f
                                )
                            )
                        ) {
                            Text("Create")
                        }
                    }

                    PremiumCard(
                        title = "Join Room",
                        subtitle = "Enter a Room ID and optional code phrase to join.",
                        icon = Icons.Default.Search,
                        iconTint = BrandPeriwinkle
                    ) {
                        OutlinedTextField(
                            value = roomId,
                            onValueChange = { roomId = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Room ID") },
                            placeholder = { Text("Paste or type room id") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = premiumTextFieldColors()
                        )

                        OutlinedTextField(
                            value = joinCode,
                            onValueChange = { joinCode = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Code phrase (optional)") },
                            placeholder = { Text("Only if required by the room") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                            colors = premiumTextFieldColors()
                        )

                        Button(
                            onClick = {
                                Log.d("RoomScreen", "Joining room=$roomId joinCode=$joinCode")
                                viewModel.joinRoom(
                                    roomId.trim(),
                                    joinCode.trim().ifBlank { null },
                                    null
                                )
                            },
                            enabled = canJoin,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandPeriwinkle,
                                contentColor = Color.White,
                                disabledContainerColor = BrandPeriwinkle.copy(alpha = 0.45f),
                                disabledContentColor = Color.White.copy(alpha = 0.9f)
                            )
                        ) {
                            Text("Join")
                        }

                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            text = "Only join rooms from people you trust.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
    LaunchedEffect(state.roomId) {
        state.roomId?.let { id ->
            navController.navigate(NavRoutes.Conversation.create(id)) {
                popUpTo(NavRoutes.Room.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

@Composable
private fun HeaderHero() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
//        Text(
//            text = "Create or join a room",
//            style = MaterialTheme.typography.headlineSmall,
//            color = MaterialTheme.colorScheme.onBackground
//        )
        Text(
            text = "Private, quick, and secure — just share an ID or scan a code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PremiumCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconPill(icon = icon, tint = iconTint)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            content()
        }
    }
}

@Composable
private fun StatusBanner(
    loading: Boolean,
    error: String?,
    message: String?,
    roomId: String?,
    alias: String?
) {
    if (!loading && error == null && message == null && roomId == null) return

    val (bg, fg) = when {
        error != null -> Color(0xFFFFF2F2) to Color(0xFFB42318)
        loading -> Color(0xFFF0F3FA) to Color(0xFF3B475E)
        else -> Color(0xFFEFFAF2) to Color(0xFF067647)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bg,
        contentColor = fg,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                error != null -> Icon(Icons.Default.WarningAmber, contentDescription = null)
                loading -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                    color = fg
                )
                else -> Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(fg)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    error != null -> Text("Error: $error", style = MaterialTheme.typography.bodyMedium)
                    loading -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (roomId != null) {
                            Text(
                                text = "Room ID: $roomId" + (alias?.let { " (alias $it)" } ?: ""),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.26f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
private fun premiumTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
)