package roy.ij.obscure.features.dm

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import roy.ij.obscure.data.network.ApiService
import roy.ij.obscure.data.network.DmStartReq
import roy.ij.obscure.data.network.RetrofitClient

// Brand colors
private val BrandGreen = Color(0xFF05C655)      // #05c655
private val BrandPeriwinkle = Color(0xFF91A6E1) // #91a6e1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanOrTypeScreen(
    token: String,
    onSuccess: (roomId: String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val api = remember { RetrofitClient.api }

    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

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

    fun launchStartDm(targetUserId: String?, targetUsername: String?) {
        if (loading) return
        error = null

        val uname = targetUsername?.trim()?.ifBlank { null }
        val uid = targetUserId?.trim()?.ifBlank { null }

        if (uid == null && uname == null) {
            error = "Enter a username or scan a valid QR."
            return
        }

        loading = true
        scope.launch {
            val result = startDmSafe(api, token, uid, uname)
            loading = false
            result.fold(
                onSuccess = { roomId -> onSuccess(roomId) },
                onFailure = { e -> error = e.message ?: "Failed to start chat" }
            )
        }
    }

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

            when {
                !userId.isNullOrBlank() -> launchStartDm(targetUserId = userId, targetUsername = null)
                !uname.isNullOrBlank() -> launchStartDm(targetUserId = null, targetUsername = uname)
                else -> error = "Invalid QR payload"
            }
        } catch (_: Exception) {
            error = "Invalid QR"
        }
    }

    val bg = Brush.linearGradient(
        colors = listOf(
            BrandPeriwinkle.copy(alpha = 0.18f),
            Color.Transparent,
            BrandGreen.copy(alpha = 0.10f)
        )
    )

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Start a chat",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(bg)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                AnimatedVisibility(visible = loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = error != null) {
                    error?.let { msg ->
                        ErrorCard(msg)
                    }
                }

                // ---- Card: Type username ----
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(22.dp), clip = false),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconPill(icon = Icons.Default.Search)
                            Column {
                                Text(
                                    text = "Type a username",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Start a private chat instantly.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it; if (error != null) error = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Username") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            )
                        )

                        Button(
                            onClick = { launchStartDm(targetUserId = null, targetUsername = username) },
                            enabled = username.trim().isNotEmpty() && !loading,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            )
                        ) {
                            Text(if (loading) "Starting…" else "Start chat")
                        }
                    }
                }

                // ---- Or divider ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Text(
                        text = "OR",
                        modifier = Modifier.padding(horizontal = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }

                // ---- Card: Scan QR ----
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(22.dp), clip = false),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconPill(icon = Icons.Default.QrCodeScanner, tint = BrandPeriwinkle)
                            Column {
                                Text(
                                    text = "Scan a QR",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Fastest way to connect—no typing.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (activity == null) {
                                    Toast.makeText(context, "Activity not available", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val integrator = IntentIntegrator(activity).apply {
                                    setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                                    setPrompt("Scan user's QR")
                                    setBeepEnabled(false)
                                    setCameraId(0)
                                    setOrientationLocked(true)
                                    captureActivity = PortraitCaptureActivity::class.java
                                }
                                scanner.launch(integrator.createScanIntent())
                            },
                            enabled = !loading,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandPeriwinkle,
                                contentColor = Color.White,
                                disabledContainerColor = BrandPeriwinkle.copy(alpha = 0.45f),
                                disabledContentColor = Color.White.copy(alpha = 0.9f)
                            )
                        ) {
                            Text("Scan QR")
                        }

                        Text(
                            text = "Tip: Ask the person to open their QR in Profile.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Small footer hint (optional)
                Text(
                    text = "Only scan QRs from people you trust.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun IconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = BrandGreen
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f))
            .border(1.dp, tint.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF2F2),
        contentColor = Color(0xFFB42318),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Safe DM start (no GlobalScope).
 * Returns Result<String> with roomId on success.
 */
private suspend fun startDmSafe(
    api: ApiService,
    token: String,
    userId: String?,
    username: String?
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val body = DmStartReq(targetUsername = username, targetUserId = userId)
        val resp = api.startDm("Bearer $token", body)
        resp.roomId
    }
}