package roy.ij.obscure.security

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val MAX_TIME_MS = 1200L
private val MIN_STROKE_DP = 55.dp

/**
 * Panic gesture: quick vertical pattern **up → down → up → down** (or reverse)
 * on screen. When detected, sends the app to home and removes it from recents.
 *
 * Use this modifier on the root content (e.g. Scaffold) so the gesture works
 * from anywhere in the app.
 */
@Composable
fun Modifier.panicGesture(): Modifier = composed {
    val context = LocalContext.current
    val density = LocalDensity.current
    val minStrokePx = with(density) { MIN_STROKE_DP.toPx() }

    this.then(
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                var lastY = 0f
                var strokeCount = 0
                var lastDirection = 0
                var currentDistance = 0f
                var firstStrokeTime = 0L
                var tracking = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue

                    when {
                        change.pressed && !tracking -> {
                            tracking = true
                            lastY = change.position.y
                            strokeCount = 0
                            lastDirection = 0
                            currentDistance = 0f
                            firstStrokeTime = 0L
                        }
                        change.pressed && tracking -> {
                            val y = change.position.y
                            val delta = lastY - y
                            lastY = y

                            if (abs(delta) < 1f) continue

                            val dir = if (delta > 0) 1 else -1
                            currentDistance += delta

                            val strokeDirection = if (currentDistance > 0) 1 else -1
                            val strokeMagnitude = abs(currentDistance)

                            if (strokeMagnitude >= minStrokePx) {
                                val now = System.currentTimeMillis()
                                if (firstStrokeTime == 0L) firstStrokeTime = now
                                if (now - firstStrokeTime > MAX_TIME_MS) {
                                    strokeCount = 0
                                    lastDirection = 0
                                    currentDistance = 0f
                                    firstStrokeTime = now
                                }

                                if (lastDirection == 0 || strokeDirection != lastDirection) {
                                    strokeCount++
                                    lastDirection = strokeDirection
                                    currentDistance = 0f

                                    if (strokeCount >= 4) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                            (context as? Activity)?.finishAndRemoveTask()
                                        }
                                        return@awaitPointerEventScope
                                    }
                                } else {
                                    currentDistance = 0f
                                }
                            }
                        }
                        !change.pressed -> {
                            tracking = false
                            lastY = 0f
                        }
                    }
                }
            }
        }
    )
}