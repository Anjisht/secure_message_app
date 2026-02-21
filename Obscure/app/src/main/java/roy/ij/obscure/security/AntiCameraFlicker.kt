package roy.ij.obscure.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box

/**
 * High-frequency screen flicker so that:
 * - **Human eyes**: See a stable image (persistence of vision fuses ~90–120 Hz).
 * - **Phone cameras** (typically 30 fps / 60 fps): Capture inconsistent frames
 *   (often the "dark" phase or mixed), so a recording looks flickery or unreadable.
 *
 * Flicker runs at ~125 Hz so cameras rarely get a clean full-brightness frame.
 */
private const val FLICKER_INTERVAL_MS = 12L  // ~125 Hz

/**
 * Wraps [content] in a layer that draws a high-frequency flicker overlay on top.
 * Content remains visible to the eye; cameras recording the screen see flicker/dark frames.
 * Touch events pass through (no extra hit-testable overlay).
 *
 * @param enabled When false, no flicker is applied.
 * @param dimAlpha Alpha of the dark overlay (0f–1f). ~0.45 keeps content readable while still defeating cameras.
 */
@Composable
fun AntiCameraFlickerBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dimAlpha: Float = 0.45f,
    content: @Composable () -> Unit
) {
    var phase by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(FLICKER_INTERVAL_MS)
            phase = !phase
        }
    }
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (enabled && phase) {
                drawRect(Color.Black.copy(alpha = dimAlpha))
            }
        }
    ) {
        content()
    }
}