package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.domain.model.CreatureState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SlumberGreen
import com.example.ui.theme.StirringAmber
import com.example.ui.theme.StrikeMagenta
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrganicEyeCanvas(
    creatureState: CreatureState,
    deltaMagnitude: Float,
    threshold: Float,
    modifier: Modifier = Modifier
) {
    // Breathing idle animation
    val infiniteTransition = rememberInfiniteTransition(label = "creature_breathing")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val auraRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Dynamic pupil dilation animated to delta magnitude
    val pupilDilation = remember { Animatable(0.25f) }
    LaunchedEffect(deltaMagnitude, threshold) {
        val target = (0.22f + (deltaMagnitude / (threshold * 1.5f)).coerceIn(0f, 0.55f))
        pupilDilation.animateTo(target, tween(120))
    }

    // Color transition based on creature state
    val mainColor = when (creatureState) {
        CreatureState.DORMANT -> Color(0xFF64748B)
        CreatureState.CALIBRATING -> Color(0xFF38BDF8)
        CreatureState.SLUMBERING -> SlumberGreen
        CreatureState.STIRRING -> StirringAmber
        CreatureState.AWAKE -> ElectricCyan
        CreatureState.STRIKING -> StrikeMagenta
    }

    val glowAlpha = when (creatureState) {
        CreatureState.DORMANT -> 0.12f
        CreatureState.CALIBRATING -> 0.50f
        CreatureState.SLUMBERING -> 0.35f
        CreatureState.STIRRING -> 0.65f
        CreatureState.AWAKE -> 0.85f
        CreatureState.STRIKING -> 1.0f
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = minOf(size.width, size.height)
        val eyeRadius = (minDim * 0.40f) * breatheScale

        // 1. Outer Magnetic Flux Aura rings
        val ringCount = if (creatureState == CreatureState.STRIKING) 4 else 3
        for (i in 1..ringCount) {
            val ringRadius = eyeRadius * (1.1f + i * 0.16f)
            val ringAlpha = (glowAlpha * 0.4f) / i
            drawCircle(
                color = mainColor.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = center,
                style = Stroke(
                    width = (3.5f - i * 0.6f),
                    cap = StrokeCap.Round
                )
            )
        }

        // 2. Swirling Magnetic Filament Tendrils
        val filamentCount = if (creatureState == CreatureState.STRIKING) 16 else 10
        val baseAngleRad = (auraRotation * PI / 180f).toFloat()
        for (i in 0 until filamentCount) {
            val angle = baseAngleRad + (i * 2f * PI.toFloat() / filamentCount)
            val startDist = eyeRadius * 0.95f
            val endDist = eyeRadius * (1.25f + 0.2f * sin(angle * 3f))
            val p1 = Offset(
                center.x + cos(angle) * startDist,
                center.y + sin(angle) * startDist
            )
            val p2 = Offset(
                center.x + cos(angle + 0.35f) * endDist,
                center.y + sin(angle + 0.35f) * endDist
            )
            drawLine(
                color = mainColor.copy(alpha = glowAlpha * 0.45f),
                start = p1,
                end = p2,
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }

        // 3. Sclera / Occult Outer Void
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF141C28),
                    Color(0xFF0C1018),
                    Color(0xFF06090E)
                ),
                center = center,
                radius = eyeRadius
            ),
            radius = eyeRadius,
            center = center
        )

        // Limbal Ring
        drawCircle(
            color = mainColor.copy(alpha = 0.7f),
            radius = eyeRadius,
            center = center,
            style = Stroke(width = 4f)
        )

        // 4. Glowing Iris
        val irisRadius = eyeRadius * 0.68f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    mainColor.copy(alpha = glowAlpha * 0.9f),
                    mainColor.copy(alpha = glowAlpha * 0.45f),
                    Color(0xFF090E17)
                ),
                center = center,
                radius = irisRadius
            ),
            radius = irisRadius,
            center = center
        )

        // Iris radial striations / creature veins
        val veinCount = 24
        for (v in 0 until veinCount) {
            val theta = (v * 2f * PI.toFloat() / veinCount) + (auraRotation * 0.02f)
            val vStart = Offset(
                center.x + cos(theta) * (irisRadius * 0.35f),
                center.y + sin(theta) * (irisRadius * 0.35f)
            )
            val vEnd = Offset(
                center.x + cos(theta) * (irisRadius * 0.92f),
                center.y + sin(theta) * (irisRadius * 0.92f)
            )
            drawLine(
                color = mainColor.copy(alpha = glowAlpha * 0.35f),
                start = vStart,
                end = vEnd,
                strokeWidth = 1.5f
            )
        }

        // 5. Creature Slit Pupil with dynamic dilation
        val pupilWidth = irisRadius * pupilDilation.value
        val pupilHeight = irisRadius * 0.85f

        val pupilPath = Path().apply {
            moveTo(center.x, center.y - pupilHeight)
            cubicTo(
                center.x + pupilWidth * 1.3f, center.y - pupilHeight * 0.3f,
                center.x + pupilWidth * 1.3f, center.y + pupilHeight * 0.3f,
                center.x, center.y + pupilHeight
            )
            cubicTo(
                center.x - pupilWidth * 1.3f, center.y + pupilHeight * 0.3f,
                center.x - pupilWidth * 1.3f, center.y - pupilHeight * 0.3f,
                center.x, center.y - pupilHeight
            )
            close()
        }

        drawPath(
            path = pupilPath,
            color = Color(0xFF030508)
        )

        // Glowing slit core
        drawPath(
            path = pupilPath,
            color = mainColor.copy(alpha = glowAlpha * 0.85f),
            style = Stroke(width = if (creatureState == CreatureState.STRIKING) 3.5f else 1.5f)
        )

        // 6. Cornea specular reflection highlight (gives wet organic life)
        val specCenter = Offset(center.x - irisRadius * 0.32f, center.y - irisRadius * 0.36f)
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = irisRadius * 0.11f,
            center = specCenter
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.4f),
            radius = irisRadius * 0.05f,
            center = Offset(specCenter.x + irisRadius * 0.16f, specCenter.y + irisRadius * 0.14f)
        )
    }
}
