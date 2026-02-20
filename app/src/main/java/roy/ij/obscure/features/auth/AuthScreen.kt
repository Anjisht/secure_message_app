package roy.ij.obscure.features.auth


import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.navigation.NavController
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import roy.ij.baatcheet.features.auth.AuthViewModel
import roy.ij.baatcheet.ui.components.FancyTextField
import androidx.compose.ui.unit.IntOffset
import androidx.fragment.app.FragmentActivity
import roy.ij.baatcheet.navigation.NavRoutes
import roy.ij.baatcheet.security.SecureStore
import roy.ij.baatcheet.security.promptToEncryptAndStore
import kotlin.math.roundToInt


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    navController: NavController
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val shakeOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorHint by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val ctx = LocalContext.current
    val activity = ctx as FragmentActivity

    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("chat_intro.json"))
    val success by rememberLottieComposition(LottieCompositionSpec.Asset("success.json"))

    val bgShift by
    rememberInfiniteTransition(label = "bg").animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000), RepeatMode.Reverse),
        label = "bgShift"
    )

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
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
            if (state.token != null) {
                LottieAnimation(success, iterations = 1, modifier = Modifier.size(180.dp))

                LaunchedEffect(state.token) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    delay(800)

                    val token = state.token!!
                    println("🔑 Token ready, launching biometric enrollment prompt…")

                    // 🧩 Always force biometric prompt after every login/register
                    SecureStore.saveBiometricEnabled(context, false) // reset flag to re-trigger prompt

                    promptToEncryptAndStore(activity, token) { success ->
                        if (success) {
                            println("✅ Biometric lock enabled and token securely stored.")
                        } else {
                            println("❌ User skipped biometric setup.")
                            // clear any leftover blob to avoid confusion
                            SecureStore.clearAll(context)
                            SecureStore.saveUsername(context, username)
                        }

                        // ✅ navigate after prompt completes
                        navController.navigate(NavRoutes.ChatList.route) {
                            popUpTo(NavRoutes.Auth.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .padding(24.dp)
                        .offset{IntOffset(shakeOffset.value.roundToInt(), 0) } // 🌀 shake animation
                        .clip(RoundedCornerShape(28.dp))
                ) {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState()) // ✅ makes it scrollable with keyboard
                            .imePadding() // ✅ moves up when keyboard opens
                            .navigationBarsPadding()
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LottieAnimation(
                            composition = composition,
                            iterations = LottieConstants.IterateForever,
                            modifier = Modifier.size(140.dp)
                        )

                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isLogin) "Welcome Back" else "Join BaatCheet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold

                        )

                        Spacer(Modifier.height(20.dp))

                        FancyTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                errorHint = when {
                                    it.isEmpty() -> "Username cannot be empty"
                                    it.length < 3 -> "Too short"
                                    !Regex("^[a-zA-Z0-9_]{3,24}$").matches(it) -> "Only letters, numbers, underscores"
                                    else -> null
                                }
                            },
                            label = "Username",
                            hint = errorHint,
                            isError = errorHint != null
                        )

                        Spacer(Modifier.height(12.dp))

                        FancyTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            hint = if (password.length in 1..9) "Password too short (min 10 chars)" else null,
                            isPassword = true,
                            isError = password.length in 1..9
                        )

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (isLogin) viewModel.login(context, username, password)
                                else viewModel.register(context, username, password)
                            },
                            enabled = username.isNotBlank() && password.length >= 10,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(if (isLogin) "Continue" else "Sign Up")
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }

                        TextButton(onClick = { isLogin = !isLogin }) {
                            Text(
                                if (isLogin)
                                    "Don’t have an account? Register"
                                else
                                    "Already have an account? Login"
                            )
                        }

                        AnimatedVisibility(visible = state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(top = 16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        LaunchedEffect(state.error) {
                            state.error?.let { backendError ->
                                // 🧠 Map backend error -> user friendly message
                                val userMessage = when {
                                    backendError.contains("username & password required", true) -> "Please enter both username and password."
                                    backendError.contains("Invalid credentials", true) -> "Incorrect username or password."
                                    backendError.contains("Username already taken", true) -> "This username is already taken."
                                    backendError.contains("Invalid username format", true) -> "Invalid username format. Use a–z, 0–9, or _."
                                    backendError.contains("Password too short", true) -> "Password must be at least 10 characters."
                                    backendError.contains("Server error", true) -> "Server is currently down — please try again soon."
                                    else -> backendError
                                }

                                // 🎚 subtle shake animation
                                coroutineScope.launch {
                                    repeat(3) {
                                        shakeOffset.animateTo(
                                            targetValue = 16f,
                                            animationSpec = tween(40, easing = LinearEasing)
                                        )
                                        shakeOffset.animateTo(
                                            targetValue = -16f,
                                            animationSpec = tween(40, easing = LinearEasing)
                                        )
                                    }
                                    shakeOffset.animateTo(0f, tween(40))
                                }

                                // 💥 haptic + snackbar feedback
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                snackbarHostState.showSnackbar(userMessage)
                            }
                        }
                    }
                }
            }
        }
    }
}