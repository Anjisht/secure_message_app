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
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun ChatListScreen(
    navController: NavController,
    token: String
) {
    val vm: ChatListViewModel = viewModel()

    // 🔔 Request POST_NOTIFICATIONS permission on Android 13+
    if (Build.VERSION.SDK_INT >= 33) {
        val ctx = LocalContext.current as Activity
        val granted = ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!granted) {
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* no-op, we just need to request once */ }

            LaunchedEffect(Unit) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val rooms by vm.rooms.collectAsState()
    LaunchedEffect(token) { vm.load(token) }

    val myUserId by vm.myUserId.collectAsState()

    var profileRoomId by remember { mutableStateOf<String?>(null) }
    val roomVm: RoomViewModel = viewModel()
    LaunchedEffect(token) { roomVm.setToken(token) }



    var fabExpanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {

        // 🔹 Rooms list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(rooms) { room ->
                val roomId = room["roomId"] as String
                val members = room["members"] as List<Map<String, Any>>
                val isDm = (room["type"] as? String) == "dm"

                val me = members.find { it["userId"] == myUserId }
                val amApproved = me?.get("status") == "approved"

                // 🧠 For DMs, show the *other person’s* alias or username
                val displayName = if (isDm) {
                    val other = members.find { it["userId"] != myUserId }
                    other?.get("username") as? String ?: other?.get("alias") as? String ?: "Unknown User"
                } else {
                    "Room • $roomId"
                }

                RoomRow(
                    roomId = roomId,
                    isApproved = amApproved,
                    isDm = isDm,
                    displayName = displayName,   // 👈 added this
                    onOpen = {
                        navController.navigate(NavRoutes.Conversation.create(roomId))
                    },
                    onOpenProfile = {
                        profileRoomId = roomId
                    }
                )
            }
        }

        // 🔹 Floating Speed Dial FAB
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (fabExpanded) {
                SmallFab("Show My QR") {
                    fabExpanded = false
                    navController.navigate(NavRoutes.MyQr.route)
                }
                Spacer(Modifier.height(8.dp))

                SmallFab("Scan / Type Username") {
                    fabExpanded = false
                    navController.navigate(NavRoutes.ScanOrType.route)
                }
                Spacer(Modifier.height(8.dp))

                SmallFab("Create Room") {
                    fabExpanded = false
                    navController.navigate(NavRoutes.Room.route)
                }
                Spacer(Modifier.height(8.dp))

                SmallFab("Join Room") {
                    fabExpanded = false
                    navController.navigate(NavRoutes.Room.route)
                }
                Spacer(Modifier.height(8.dp))
            }

            FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                Text(if (fabExpanded) "×" else "+")
            }
        }
        // 🔹 Show the bottom sheet when a room is selected
        if (profileRoomId != null) {
            RoomDetailsSheet(
                roomId = profileRoomId!!,
                vm = roomVm,
                onClose = { profileRoomId = null }
            )
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(
                enabled = isApproved,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onOpen() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(displayName)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenProfile) {
            Icon(Icons.Default.Info, contentDescription = "Room profile")
        }
        Text(if (isApproved) "✅" else "⏳")
    }
}

