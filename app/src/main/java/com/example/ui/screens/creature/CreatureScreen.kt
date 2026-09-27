package com.example.ui.screens.creature

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CreatureState
import com.example.domain.model.HapticFeedbackType
import com.example.domain.model.MagneticReading
import com.example.ui.components.OrganicEyeCanvas
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardBorder
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.SlumberGreen
import com.example.ui.theme.StirringAmber
import com.example.ui.theme.StrikeMagenta
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextHigh
import com.example.ui.theme.TextMedium
import java.util.Locale

@Composable
fun CreatureScreen(
    creatureState: CreatureState,
    reading: MagneticReading,
    effectiveThreshold: Float,
    fogLevel: Float,
    hapticType: HapticFeedbackType,
    onFogChange: (Float) -> Unit,
    onTareBaseline: () -> Unit,
    onEnterStealth: () -> Unit,
    onTestHaptic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val stateColor by animateColorAsState(
        targetValue = when (creatureState) {
            CreatureState.SLUMBERING -> SlumberGreen
            CreatureState.STIRRING -> StirringAmber
            CreatureState.AWAKE -> ElectricCyan
            CreatureState.STRIKING -> StrikeMagenta
        },
        animationSpec = tween(200),
        label = "state_color"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Status Indicator & Creature Mood Banner
        CreatureStatusCard(
            creatureState = creatureState,
            stateColor = stateColor,
            reading = reading
        )

        // 2. Central Living Eye Visualizer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MidnightCard,
                            MidnightSurface,
                            Color(0xFF070A0F)
                        )
                    )
                )
                .border(1.5.dp, stateColor.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
                .testTag("creature_eye_container"),
            contentAlignment = Alignment.Center
        ) {
            OrganicEyeCanvas(
                creatureState = creatureState,
                deltaMagnitude = reading.deltaMagnitude,
                threshold = effectiveThreshold,
                modifier = Modifier.fillMaxSize()
            )

            // Overlaid Live Delta Readout at bottom of eye card
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(MidnightSurface.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .border(1.dp, stateColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Flux delta",
                        tint = stateColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = String.format(Locale.US, "Δ %.2f µT", reading.deltaMagnitude),
                        color = TextHigh,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = String.format(Locale.US, "(Fog: %.1f µT)", effectiveThreshold),
                        color = TextMedium,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 3. Proximity / Strike Progress Meter
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MAGNETIC SHIFT",
                    color = TextMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (reading.deltaMagnitude >= effectiveThreshold) "BREACHED" else reading.dominantDirection.label,
                    color = stateColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = {
                    (reading.deltaMagnitude / (effectiveThreshold * 1.3f)).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = stateColor,
                trackColor = MidnightCardBorder
            )
        }

        // 4. Fog Control Card (Sensitivity vs Stability)
        FogControlCard(
            fogLevel = fogLevel,
            effectiveThreshold = effectiveThreshold,
            onFogChange = onFogChange
        )

        // 5. Tactile Performance Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onTareBaseline,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("tare_baseline_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SlumberGreen.copy(alpha = 0.2f),
                    contentColor = SlumberGreen
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlumberGreen.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.CompassCalibration,
                    contentDescription = "Tare Baseline",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tare Room",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = onEnterStealth,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("stealth_mode_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = TextHigh
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Stealth Mode",
                    modifier = Modifier.size(20.dp),
                    tint = ElectricCyan
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Stealth Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // 6. Active Haptic Cue Summary & Test Button
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TACTILE CUE",
                        color = TextMedium,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = hapticType.displayName,
                        color = TextHigh,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = hapticType.description,
                        color = TextDim,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                OutlinedButton(
                    onClick = onTestHaptic,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                    modifier = Modifier.testTag("test_haptic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Test Vibration",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CreatureStatusCard(
    creatureState: CreatureState,
    stateColor: Color,
    reading: MagneticReading
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, stateColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(stateColor, CircleShape)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = creatureState.label,
                        color = stateColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    if (creatureState == CreatureState.STRIKING) {
                        Text(
                            text = "• ${reading.dominantDirection.label}",
                            color = StrikeMagenta,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = creatureState.subtitle,
                    color = TextMedium,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun FogControlCard(
    fogLevel: Float,
    effectiveThreshold: Float,
    onFogChange: (Float) -> Unit
) {
    val fogLabel = when {
        fogLevel < 0.25f -> "Whisper Thin (Hyper Sensitive)"
        fogLevel < 0.55f -> "Balanced Mist (Close-up Coins & Rings)"
        fogLevel < 0.80f -> "Dense Cloak (Bar & Street Noise)"
        else -> "Stage Shield (Heavy Theatre Lighting & Iron)"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Adjust,
                        contentDescription = "Fog Control",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "FOG CONTROL",
                        color = TextHigh,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = String.format(Locale.US, "Threshold: %.1f µT", effectiveThreshold),
                    color = ElectricCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = fogLabel,
                color = TextMedium,
                fontSize = 12.sp
            )

            Slider(
                value = fogLevel,
                onValueChange = onFogChange,
                valueRange = 0.05f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = MidnightCardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fog_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "More Sensitive",
                    color = TextDim,
                    fontSize = 11.sp
                )
                Text(
                    text = "More Stable",
                    color = TextDim,
                    fontSize = 11.sp
                )
            }
        }
    }
}
