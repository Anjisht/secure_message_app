package roy.ij.obscure.features.chat

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import roy.ij.obscure.navigation.NavRoutes
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import roy.ij.obscure.analytics.AnalyticsTracker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    navController: NavController,
    token: String
) {
    val vm: ChatListViewModel = viewModel()

    if (Build.VERSION.SDK_INT >= 33) {
        val ctx = LocalContext.current
        var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(true) }
        val granted = ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            showNotificationPermissionDialog = false
        }

        if (!granted && showNotificationPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationPermissionDialog = false },
                title = { Text("Stay notified") },
                text = {
                    Text(
                        "Obscure can send message notifications so you do not miss new replies. You can change this later in Android settings."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNotificationPermissionDialog = false
                            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    ) {
                        Text("Allow notifications")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNotificationPermissionDialog = false }) {
                        Text("Not now")
                    }
                }
            )
        }
    }

    val rooms by vm.rooms.collectAsState()
    LaunchedEffect(token) { vm.load(token) }

    val myUserId by vm.myUserId.collectAsState()

    var profileRoomId by remember { mutableStateOf<String?>(null) }
    val roomVm: RoomViewModel = viewModel()
    LaunchedEffect(token) { roomVm.setToken(token) }

    var fabExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Obscure",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    var expanded by remember { mutableStateOf(false) }

                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            onClick = {
                                expanded = false
                                // Later: add logout logic here
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            if (rooms.isEmpty()) {

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No conversations yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Start a room or scan someone’s QR to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 24.dp,
                        bottom = 120.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rooms) { room ->
                        val roomId = room["roomId"] as String
                        val members = room["members"] as List<Map<String, Any>>
                        val isDm = (room["type"] as? String) == "dm"

                        val me = members.find { it["userId"] == myUserId }
                        val amApproved = me?.get("status") == "approved"

                        val displayName = if (isDm) {
                            val other = members.find { it["userId"] != myUserId }
                            other?.get("username") as? String
                                ?: other?.get("alias") as? String
                                ?: "Unknown User"
                        } else {
                            "Room • $roomId"
                        }

                        RoomRow(
                            roomId = roomId,
                            isApproved = amApproved,
                            isDm = isDm,
                            displayName = displayName,
                            onOpen = {
                                AnalyticsTracker.action(
                                    "chat_open_tap",
                                    mapOf(
                                        "feature" to "chat",
                                        "room_type" to if (isDm) "dm" else "room"
                                    )
                                )
                                navController.navigate(
                                    NavRoutes.Conversation.create(roomId)
                                )
                            },
                            onOpenProfile = {
                                AnalyticsTracker.action("room_profile_open", mapOf("feature" to "chat"))
                                profileRoomId = roomId
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (fabExpanded) {
                    SmallFab("Show My QR") {
                        fabExpanded = false
                        AnalyticsTracker.action("profile_qr_open_tap", mapOf("feature" to "connect"))
                        navController.navigate(NavRoutes.MyQr.route)
                    }
                    Spacer(Modifier.height(8.dp))

                    SmallFab("Scan / Type Username") {
                        fabExpanded = false
                        AnalyticsTracker.action("scan_type_open_tap", mapOf("feature" to "connect"))
                        navController.navigate(NavRoutes.ScanOrType.route)
                    }
                    Spacer(Modifier.height(8.dp))

                    SmallFab("Create / Join Room") {
                        fabExpanded = false
                        AnalyticsTracker.action("room_create_join_open_tap", mapOf("feature" to "room"))
                        navController.navigate(NavRoutes.Room.route)
                    }
                    Spacer(Modifier.height(8.dp))
//
//                    SmallFab("Join Room") {
//                        fabExpanded = false
//                        navController.navigate(NavRoutes.Room.route)
//                    }
//                    Spacer(Modifier.height(8.dp))
                }

                FloatingActionButton(
                    onClick = {
                        AnalyticsTracker.action("connect_menu_toggle", mapOf("feature" to "connect"))
                        fabExpanded = !fabExpanded
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = if (fabExpanded) "×" else "+",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            if (profileRoomId != null) {
                RoomDetailsSheet(
                    roomId = profileRoomId!!,
                    vm = roomVm,
                    onClose = { profileRoomId = null }
                )
            }
        }
    }
}

@Composable
private fun SmallFab(text: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        text = { Text(text) },
        onClick = onClick,
        icon = {},
        expanded = true
    )
}

@Composable
private fun RoomRow(
    roomId: String,
    isApproved: Boolean,
    isDm: Boolean,
    displayName: String,
    onOpen: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = isApproved,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onOpen() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = if (isApproved) "Tap to open" else "Waiting for approval",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isApproved)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            IconButton(onClick = onOpenProfile) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Room profile",
                    tint = Color.Black
                )
            }
        }
    }
}
