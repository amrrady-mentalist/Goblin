package com.example.ui.screens.stealth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CreatureState
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.SlumberGreen
import com.example.ui.theme.StrikeMagenta
import kotlinx.coroutines.delay

@Composable
fun StealthOverlay(
    creatureState: CreatureState,
    showMicroDot: Boolean,
    onExitStealth: () -> Unit,
    onTareBaseline: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showHint by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2500)
        showHint = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("stealth_mode_screen")
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onExitStealth()
                    },
                    onLongPress = {
                        onExitStealth()
                    },
                    onTap = {
                        // Single tap tares baseline covertly
                        onTareBaseline()
                    }
                )
            }
    ) {
        // Optional tiny covert micro-dot in corner for glancing
        if (showMicroDot) {
            val dotColor = when (creatureState) {
                CreatureState.STRIKING -> StrikeMagenta.copy(alpha = 0.5f)
                CreatureState.AWAKE -> Color(0xFF00E5FF).copy(alpha = 0.25f)
                CreatureState.STIRRING -> Color(0xFFFFB300).copy(alpha = 0.15f)
                CreatureState.SLUMBERING -> Color(0xFF1E293B).copy(alpha = 0.3f)
                CreatureState.CALIBRATING -> Color(0xFF38BDF8).copy(alpha = 0.4f)
                CreatureState.DORMANT -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomEnd)
                    .size(3.dp)
                    .background(dotColor, CircleShape)
            )
        }

        // Fading initial performer hint
        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = "STEALTH ACTIVE\nDouble-tap or long-press to exit\nSingle-tap to tare room baseline",
                color = Color(0xFF334155),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
