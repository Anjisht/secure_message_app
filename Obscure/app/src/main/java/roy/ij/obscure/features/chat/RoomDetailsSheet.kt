package roy.ij.obscure.features.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import roy.ij.obscure.features.chat.RoomViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailsSheet(
    roomId: String,
    vm: RoomViewModel,
    onClose: () -> Unit
) {
    val info by vm.info.collectAsState()
    LaunchedEffect(roomId) { vm.refresh(roomId) }

    ModalBottomSheet(onDismissRequest = onClose) {
        Text(
            "Room $roomId",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge
        )
        info?.let { r ->
            if (r.isAdmin) {
                Text("Pending requests", modifier = Modifier.padding(horizontal = 16.dp))
                r.members.filter { it.status == "pending" }.forEach { m ->
                    Column(Modifier.padding(16.dp)) {
                        Text("Alias: ${m.alias}")
                        if (!m.joinNote.isNullOrBlank()) Text("Note: ${m.joinNote}")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { vm.approve(r.roomId, m.userId) }) { Text("Approve") }
                            OutlinedButton(onClick = { vm.deny(r.roomId, m.userId) }) { Text("Deny") }
                        }
                    }
                    Divider()
                }
            }
            Text("Members", modifier = Modifier.padding(horizontal = 16.dp))
            r.members.filter { it.status == "approved" }.forEach { m ->
                ListItem(headlineContent = { Text("${m.alias}") })
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) { Text("Close") }
        } ?: run {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}
