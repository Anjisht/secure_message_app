package roy.ij.obscure.features.chat

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import roy.ij.obscure.navigation.NavRoutes

@Composable
fun RoomScreen(
    navController: NavController,
    viewModel: RoomViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    var code by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }   // <-- NEW

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create Room")
        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code phrase (optional)") })
        OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration minutes") })
        Button(onClick = {
            viewModel.createRoom(code.ifBlank { null }, duration.toIntOrNull())
        }) { Text("Create") }

        Spacer(Modifier.height(24.dp))
        Text("Join Room")
        OutlinedTextField(value = roomId, onValueChange = { roomId = it }, label = { Text("Room ID") })
        OutlinedTextField(value = joinCode, onValueChange = { joinCode = it }, label = { Text("Code phrase") }) // <-- NEW
        Button(onClick = {
            viewModel.joinRoom(roomId, joinCode.ifBlank { null }, null)  // <-- use joinCode, not code
        }) { Text("Join") }

        Spacer(Modifier.height(24.dp))

        // Show state feedback
        if (state.isLoading) Text("Loading…")
        state.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text("Server says: $it") }
        state.roomId?.let { Text("Your room id: ${state.roomId} (alias ${state.alias})") }
    }
    LaunchedEffect(state.roomId) {
        state.roomId?.let { id ->
            navController.navigate(NavRoutes.Conversation.create(id))
        }
    }
}