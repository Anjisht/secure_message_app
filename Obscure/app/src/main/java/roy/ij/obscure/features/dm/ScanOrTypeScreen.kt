package roy.ij.obscure.features.dm

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch
import org.json.JSONObject
import roy.ij.obscure.data.network.ApiService
import roy.ij.obscure.data.network.DmStartReq
import roy.ij.obscure.data.network.RetrofitClient

@Composable
fun ScanOrTypeScreen(
    token: String,
    onSuccess: (roomId: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current as Activity
    val api = remember { RetrofitClient.api }

    val scanner = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val intent = res.data ?: return@rememberLauncherForActivityResult
        val result = IntentIntegrator.parseActivityResult(res.resultCode, intent)
        val contents = result?.contents ?: return@rememberLauncherForActivityResult
        try {
            val json = JSONObject(contents)
            val userId = json.optString("userId", null)
            val uname = json.optString("username", null)

            if (!userId.isNullOrBlank()) {
                startDm(api, token, userId, null, onSuccess) { error = it }
            } else if (!uname.isNullOrBlank()) {
                startDm(api, token, null, uname, onSuccess) { error = it }
            } else {
                error = "Invalid QR payload"
            }
        } catch (e: Exception) {
            error = "Invalid QR"
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Start a chat", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = username, onValueChange = { username = it },
            label = { Text("Enter username") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            startDm(api, token, null, username.trim().ifBlank { null }, onSuccess) { error = it }
        }, enabled = username.isNotBlank()) { Text("Start chat") }
        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            val integrator = IntentIntegrator(context).apply {
                setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                setPrompt("Scan user's QR")
                setBeepEnabled(false)
                setCameraId(0)
                setOrientationLocked(true)
                captureActivity = PortraitCaptureActivity::class.java
            }
            scanner.launch(integrator.createScanIntent())
        }) { Text("Scan QR") }

        error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
    }
}

private fun startDm(
    api: ApiService,
    token: String,
    userId: String?,
    username: String?,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
//    androidx.lifecycle.lifecycleScope.launchWhenResumed((api as? Any)?.let { }) // no-op to keep snippet minimal
    // use a simple coroutine:
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val body = DmStartReq(targetUsername = username, targetUserId = userId)
            val resp = api.startDm("Bearer $token", body)
            with(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.GlobalScope.launch(this) { onSuccess(resp.roomId) }
            }
        } catch (e: Exception) {
            with(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.GlobalScope.launch(this) { onError(e.message ?: "Failed") }
            }
        }
    }
}
