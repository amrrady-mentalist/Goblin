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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CreatureState
import com.example.domain.model.MagneticReading
import com.example.domain.model.RumbleMode
import com.example.ui.components.OrganicEyeCanvas
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBg
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
    rumbleMode: RumbleMode,
    isPoweredOn: Boolean = true,
    isStealthActive: Boolean = false,
    onTogglePower: () -> Unit = {},
    onFogChange: (Float) -> Unit = {},
    onSelectRumbleMode: (RumbleMode) -> Unit = {},
    onToggleStealth: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onTareBaseline: () -> Unit = {},
    onTestHaptic: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val goblinAccentGreen = Color(0xFF22C55E)
    val stateColor by animateColorAsState(
        targetValue = when (creatureState) {
            CreatureState.DORMANT -> Color(0xFF64748B)
            CreatureState.CALIBRATING -> Color(0xFF38BDF8)
            CreatureState.SLUMBERING -> goblinAccentGreen
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
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Goblin Header Mascot & Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(36.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Goblin Creature Emblem
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(goblinAccentGreen.copy(alpha = 0.15f))
                        .border(1.5.dp, goblinAccentGreen.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "The Goblin",
                        tint = goblinAccentGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "THE GOBLIN",
                    color = goblinAccentGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Finder of Hidden Things",
                    color = goblinAccentGreen.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("open_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info & Settings",
                    tint = goblinAccentGreen.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 2. Central Living Eye Visualizer (Direct Status Representation)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MidnightCard,
                            MidnightSurface,
                            Color(0xFF040608)
                        )
                    )
                )
                .border(1.5.dp, stateColor.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
                .clickable { onTareBaseline() }
                .testTag("creature_eye_container"),
            contentAlignment = Alignment.Center
        ) {
            OrganicEyeCanvas(
                creatureState = creatureState,
                deltaMagnitude = reading.deltaMagnitude,
                threshold = effectiveThreshold,
                modifier = Modifier.fillMaxSize()
            )

            // Overlaid Live Status at Bottom of Eye
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MidnightSurface.copy(alpha = 0.88f))
                    .border(1.dp, stateColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(stateColor, CircleShape)
                    )
                    Text(
                        text = if (!isPoweredOn) {
                            "The goblin sleeps."
                        } else if (reading.isHandArcDetected || creatureState == CreatureState.STRIKING) {
                            "⚡ MOVEMENT DETECTED!"
                        } else if (reading.deltaMagnitude >= effectiveThreshold * 0.7f) {
                            String.format(Locale.US, "Faint scent nearby • Δ %.2f µT", reading.deltaMagnitude)
                        } else {
                            "Nothing moving around phone (0-50 cm)"
                        },
                        color = TextHigh,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. Hunt Mode (Main Power Switch)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hunt_mode_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Hunt Mode",
                        color = goblinAccentGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isPoweredOn) "The goblin hunts." else "The goblin sleeps.",
                        color = TextMedium,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = isPoweredOn,
                    onCheckedChange = { onTogglePower() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = goblinAccentGreen,
                        checkedTrackColor = goblinAccentGreen.copy(alpha = 0.35f),
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.testTag("hunt_mode_switch")
                )
            }
        }

        // 4. Fog Control Slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fogLabel = when {
                        fogLevel < 0.25f -> "Clear"
                        fogLevel < 0.65f -> "Misty"
                        else -> "Heavy"
                    }
                    Text(
                        text = "Fog: $fogLabel",
                        color = goblinAccentGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f µT threshold", effectiveThreshold),
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "Controls sensitivity to slight movement vs strong movement.",
                    color = TextMedium,
                    fontSize = 11.sp
                )

                Slider(
                    value = fogLevel,
                    onValueChange = onFogChange,
                    valueRange = 0.05f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = goblinAccentGreen,
                        activeTrackColor = goblinAccentGreen,
                        inactiveTrackColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fog_slider")
                )
            }
        }

        // 5. Rumble Mode Selector (Footsteps, Stomps, Silence)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Rumble",
                    color = goblinAccentGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                // 3-Button Segmented Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RumbleMode.values().forEach { mode ->
                        val isSelected = rumbleMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (isSelected) goblinAccentGreen else Color.Transparent
                                )
                                .clickable { onSelectRumbleMode(mode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                color = if (isSelected) Color(0xFF022C22) else goblinAccentGreen.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Text(
                    text = rumbleMode.subtitle,
                    color = TextMedium,
                    fontSize = 11.sp
                )
            }
        }

        // 6. Stealth Mode Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, MidnightCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Stealth Mode",
                        color = goblinAccentGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Dark screen while hunting.",
                        color = TextMedium,
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = isStealthActive,
                    onCheckedChange = { onToggleStealth() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = goblinAccentGreen,
                        checkedTrackColor = goblinAccentGreen.copy(alpha = 0.35f),
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.testTag("stealth_mode_switch")
                )
            }
        }

        // 7. PeekSmith & Pro Settings Button
        OutlinedButton(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MidnightCard,
                contentColor = goblinAccentGreen
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, goblinAccentGreen.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("peeksmith_settings_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Settings",
                    tint = goblinAccentGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "PeekSmith & Trick Settings",
                    color = goblinAccentGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 8. Scent Strength Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scent Strength",
                color = goblinAccentGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = reading.scentStatusText,
                color = stateColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Brand Footer Mark
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.dp, goblinAccentGreen.copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "MH",
                    color = goblinAccentGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "v1.1 (2)",
                color = TextDim,
                fontSize = 10.sp
            )
        }
    }
}
