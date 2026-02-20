package roy.ij.obscure.features.auth

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.airbnb.lottie.compose.*
import roy.ij.obscure.navigation.NavRoutes
import roy.ij.obscure.security.*
import roy.ij.obscure.ui.components.FancyTextField
import roy.ij.obscure.security.SecureStore

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LockScreen(
    navController: NavHostController,
    viewModel: AuthViewModel
) {
    val ctx = LocalContext.current
    val activity = ctx as FragmentActivity
    val username = remember { SecureStore.getUsername(ctx) ?: "" }
    val blob = remember { SecureStore.getTokenBlob(ctx) }
    val state by viewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Lottie
    val lockAnim by rememberLottieComposition(LottieCompositionSpec.Asset("lock.json"))

    // Animated background (same vibe as Auth)
    val bgShift by rememberInfiniteTransition(label = "bg").animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000), RepeatMode.Reverse),
        label = "bgShift"
    )

    // Bottom sheet (password fallback)
    var showPasswordSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local password entry state
    var password by remember { mutableStateOf("") }
    var passError by remember { mutableStateOf<String?>(null) }

    // Auto-prompt biometric only if we have a valid blob and sheet isn’t open
    LaunchedEffect(blob, showPasswordSheet) {
        if (!showPasswordSheet && !blob.isNullOrBlank()) {
            promptToDecryptAndUnlock(activity, blob) { tokenOrNull ->
                if (tokenOrNull != null) {
                    // Set token into VM and go
                    viewModel.restoreSession(tokenOrNull)
                    navController.navigate(NavRoutes.ChatList.route) {
                        popUpTo(NavRoutes.Lock.route) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    // Stay here; user can tap "Use password"
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }

    // React to VM errors from password fallback
    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(
                when {
                    msg.contains("Invalid credentials", true) -> "Incorrect password."
                    msg.contains("Network", true) || msg.contains("Server", true) -> "Network/server error. Try again."
                    else -> msg
                }
            )
        }
    }

    // Navigate on password success (token set by VM)
    LaunchedEffect(state.token) {
        if (state.token != null && showPasswordSheet) {
            showPasswordSheet = false
            navController.navigate(NavRoutes.ChatList.route) {
                popUpTo(NavRoutes.Lock.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        ),
                        start = Offset(bgShift, 0f),
                        end = Offset(bgShift + 1000f, 1000f)
                    )
                )
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {

            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(28.dp))
            ) {
                Column(
                    Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LottieAnimation(
                        composition = lockAnim,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier.size(140.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Welcome back",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                    Text(
                        text = username,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(20.dp))

                    // Primary action: biometric again
                    Button(
                        onClick = {
                            if (blob.isNullOrBlank()) {
                                // no enrollment; ask to login with password
                                showPasswordSheet = true
                            } else {
                                promptToDecryptAndUnlock(activity, blob) { tokenOrNull ->
                                    if (tokenOrNull != null) {
                                        viewModel.restoreSession(tokenOrNull)
                                        navController.navigate(NavRoutes.ChatList.route) {
                                            popUpTo(NavRoutes.Lock.route) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Unlock with biometric")
                    }

                    Spacer(Modifier.height(8.dp))

                    // Secondary: inline password fallback
                    OutlinedButton(
                        onClick = { showPasswordSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Use password")
                    }

                    Spacer(Modifier.height(4.dp))

                    // Tertiary: change account → go to Auth
                    TextButton(onClick = {
                        navController.navigate(NavRoutes.Auth.route) {
                            popUpTo(NavRoutes.Lock.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }) {
                        Text("Change account")
                    }

                    if (state.isLoading) {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                    }
                }
            }
        }

        // Bottom sheet for password entry (no nav to Auth)
        if (showPasswordSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPasswordSheet = false },
                sheetState = sheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Unlock with password",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))

                    FancyTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passError = if (it.isNotEmpty() && it.length < 10)
                                "Password too short (min 10 chars)" else null
                        },
                        label = "Password",
                        hint = passError,
                        isPassword = true,
                        isError = passError != null
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (password.length < 10) {
                                passError = "Password too short (min 10 chars)"
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                return@Button
                            }
                            // Re-login inline (no nav)
                            viewModel.login(ctx, username, password)
                        },
                        enabled = password.length >= 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Continue")
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = { showPasswordSheet = false }
                    ) { Text("Cancel") }

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}