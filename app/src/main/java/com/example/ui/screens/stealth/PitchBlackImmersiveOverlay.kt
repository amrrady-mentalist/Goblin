package com.example.ui.screens.stealth

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Pitch Black Immersive Screen:
 * 1. True OLED pitch-black screen (Color.Black)
 * 2. Immersive mode: hides status bar & navigation bar to appear locked/off
 * 3. Keeps screen on so magnetometer continues full background polling
 * 4. Visual Mode: Shows a very tiny green dot in the top-left corner on magnetic detection
 * 5. Gesture: Swipe down with 2 fingers to exit back to the app
 */
@Composable
fun PitchBlackImmersiveOverlay(
    isVisualModeEnabled: Boolean,
    isVisualDotVisible: Boolean,
    isVisualDotStrong: Boolean = false,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    // Manage Immersive Mode and Keep Screen On
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("pitch_black_screen")
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var startY1 = 0f
                    var startY2 = 0f
                    var isTrackingTwoFingers = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressedChanges = event.changes.filter { it.pressed }

                        if (pressedChanges.size >= 2) {
                            val p1 = pressedChanges[0]
                            val p2 = pressedChanges[1]

                            if (!isTrackingTwoFingers) {
                                isTrackingTwoFingers = true
                                startY1 = p1.position.y
                                startY2 = p2.position.y
                            } else {
                                val dy1 = p1.position.y - startY1
                                val dy2 = p2.position.y - startY2

                                // 2 fingers swiped down by at least 140 pixels
                                if (dy1 > 140f && dy2 > 140f) {
                                    p1.consume()
                                    p2.consume()
                                    onExit()
                                    break
                                }
                            }
                        } else {
                            isTrackingTwoFingers = false
                        }
                    }
                }
            }
    ) {
        // Visual Mode: a faint, small indicator in the top-left corner — meant to be
        // readable by the performer up close, not noticeable to a spectator across the
        // room. Yellow = a weak/borderline hit, green = a strong/close one.
        if (isVisualDotVisible) {
            val haloColor = if (isVisualDotStrong) Color(0x1500FF66) else Color(0x15FFD400)
            val coreColor = if (isVisualDotStrong) Color(0x6600FF66) else Color(0x66FFD400)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 24.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(haloColor), // Faint halo
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(coreColor) // Faint core, just visible up close
                        .testTag("visual_mode_dot")
                )
            }
        }
    }
}
