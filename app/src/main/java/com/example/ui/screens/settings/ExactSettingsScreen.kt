package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CreatureState
import com.example.domain.model.MagneticReading
import com.example.domain.model.VibePattern
import com.example.sensor.DetectionModel
import com.example.sensor.MagneticSensorEngine.CalibrationPhase
import kotlin.math.roundToInt

// Frosted-glass design language: a soft gradient backdrop with a few blurred
// color blobs behind translucent "frosted" cards.
private val GradientTop = Color(0xFFEFE9FF)
private val GradientBottom = Color(0xFFE3F3FF)
private val ScreenGradient = Brush.verticalGradient(listOf(GradientTop, GradientBottom))
private val BlobLavender = Color(0xFFD6C6FF)
private val BlobSky = Color(0xFFBFE4FF)
private val BlobPeach = Color(0xFFFFE1C7)

private val TextTitleColor = Color(0xFF241B3D)
private val TextDescColor = Color(0xFF6B6480)
private val YellowAccent = Color(0xFFEAB308)
private val YellowTrack = Color(0xFFFACC15)
private val ButtonDark = Color(0xFF1E1533)
private val WarnRed = Color(0xFFDC2626)

private val GlassCardBg = Color(0xFFFFFFFF).copy(alpha = 0.55f)
private val GlassCardBorder = Color(0xFFFFFFFF).copy(alpha = 0.65f)
private val GlassStroke = Color(0xFFFFFFFF).copy(alpha = 0.45f)
private val GlassMuted = Color(0xFFFFFFFF).copy(alpha = 0.40f)

// Patterns offered for each cue. "Off" on the main cue means dot only; on the second cue
// it means no second cue.
private val PatternChoices = listOf(
    VibePattern.PULSES, VibePattern.BUZZ, VibePattern.STUTTER, VibePattern.TICK, VibePattern.OFF
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExactSettingsScreen(
    // Status
    isTrickRunning: Boolean,
    isSensorActivated: Boolean,
    creatureState: CreatureState,
    reading: MagneticReading,
    liveMicroTesla: Int,
    isSaturated: Boolean,
    // The four sliders and the Up/Down switch
    territory: Float,
    magnetPower: Float,
    magnetMovement: Float,
    phoneMovement: Int,
    verticalOnly: Boolean,
    rangeSummary: String,
    // Vibration
    vibePattern: VibePattern,
    vibeCount: Int,
    vibeStrength: Int,
    vibeSpeed: Int,
    vibeLength: Int,
    secondPattern: VibePattern,
    secondCount: Int,
    // Display
    isVisualModeEnabled: Boolean,
    vibrationWithVisual: Boolean,
    // Calibration
    isCalibratingObject: Boolean,
    calibrationPhase: CalibrationPhase,
    calibrationProgress: Float,
    calibrationMessage: String?,
    // Callbacks
    onToggleTrick: () -> Unit,
    onToggleActivateSensor: () -> Unit,
    onTerritoryChange: (Float) -> Unit,
    onMagnetPowerChange: (Float) -> Unit,
    onMagnetMovementChange: (Float) -> Unit,
    onPhoneMovementChange: (Int) -> Unit,
    onVerticalOnlyChange: (Boolean) -> Unit,
    onVibePatternChange: (VibePattern) -> Unit,
    onVibeCountChange: (Int) -> Unit,
    onVibeStrengthChange: (Int) -> Unit,
    onVibeSpeedChange: (Int) -> Unit,
    onVibeLengthChange: (Int) -> Unit,
    onSecondPatternChange: (VibePattern) -> Unit,
    onSecondCountChange: (Int) -> Unit,
    onTestVibration: () -> Unit,
    onTestSecondVibration: () -> Unit,
    onToggleScreenOffMode: (Boolean) -> Unit,
    onToggleVisualMode: (Boolean) -> Unit,
    onToggleVibrationWithVisual: (Boolean) -> Unit,
    onCalibrate: () -> Unit,
    onCancelCalibration: () -> Unit,
    onDismissCalibrationMessage: () -> Unit,
    onOpenTutorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(calibrationMessage) {
        val message = calibrationMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissCalibrationMessage()
        }
    }

    // Briefly shows "Ready" the moment the room attunement finishes.
    var wasAttuning by remember { mutableStateOf(true) }
    var showReadyBanner by remember { mutableStateOf(false) }
    LaunchedEffect(creatureState) {
        if (wasAttuning && creatureState != CreatureState.CALIBRATING) {
            showReadyBanner = true
            kotlinx.coroutines.delay(1800L)
            showReadyBanner = false
        }
        wasAttuning = (creatureState == CreatureState.CALIBRATING)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenGradient)
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = (-80).dp, y = (-60).dp)
                .blur(90.dp)
                .clip(CircleShape)
                .background(BlobLavender.copy(alpha = 0.55f))
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = 40.dp)
                .blur(90.dp)
                .clip(CircleShape)
                .background(BlobSky.copy(alpha = 0.55f))
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 60.dp)
                .blur(100.dp)
                .clip(CircleShape)
                .background(BlobPeach.copy(alpha = 0.5f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = "Goblin",
                        color = TextTitleColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenTutorial,
                        modifier = Modifier.testTag("settings_tutorial_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Tutorial",
                            tint = TextDescColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ---- Status ----
                if (isSaturated) {
                    Banner(
                        text = "Sensor saturated. Click a volume key or move the phone to clear it.",
                        color = WarnRed,
                        tag = "saturation_banner"
                    )
                } else if (creatureState == CreatureState.CALIBRATING || showReadyBanner) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassCardBg)
                            .border(1.dp, GlassCardBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("room_calibration_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (creatureState == CreatureState.CALIBRATING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = YellowAccent
                            )
                            Text(
                                text = "Reading the room. Keep the phone still.",
                                color = TextTitleColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "Ready",
                                color = TextTitleColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // ---- Controls: sensor on/off, start/stop ----
                GlassCard {
                    SwitchRow(
                        title = "Sensor",
                        checked = isSensorActivated,
                        onChange = { onToggleActivateSensor() },
                        tag = "activate_sensor_switch"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trick",
                            color = TextTitleColor,
                            fontSize = 16.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Button(
                            onClick = onToggleTrick,
                            colors = ButtonDefaults.buttonColors(containerColor = ButtonDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("start_trick_button")
                        ) {
                            Text(
                                text = if (isTrickRunning) "Stop" else "Start",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    // Live signal strength, so slider changes can be judged at a glance.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = reading.scentStatusText,
                                color = TextDescColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$liveMicroTesla µT",
                                color = TextDescColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        LinearProgressIndicator(
                            progress = { reading.scentStrengthPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = YellowAccent,
                            trackColor = GlassMuted
                        )
                    }
                }

                // ---- Detection ----
                GlassCard(title = "Detection") {
                    LabeledSlider(
                        title = "Territory",
                        valueText = "≈ ${DetectionModel.radiusCm(territory).roundToInt()} cm",
                        leftLabel = "Close",
                        rightLabel = "Far",
                        value = territory,
                        onChange = onTerritoryChange,
                        tag = "territory_slider"
                    )
                    LabeledSlider(
                        title = "Magnetic Power",
                        valueText = "${(magnetPower * 100).roundToInt()}%",
                        leftLabel = "Weak",
                        rightLabel = "Strong",
                        value = magnetPower,
                        onChange = onMagnetPowerChange,
                        tag = "magnet_power_slider"
                    )
                    LabeledSlider(
                        title = "Phone Movement",
                        valueText = "$phoneMovement%",
                        leftLabel = "Strict",
                        rightLabel = "Relaxed",
                        value = phoneMovement / 100f,
                        onChange = { onPhoneMovementChange((it * 100f).roundToInt()) },
                        tag = "phone_movement_slider"
                    )
                    LabeledSlider(
                        title = "Magnet Movement",
                        valueText = "≈ ${DetectionModel.moveCm(magnetMovement).roundToInt()} cm",
                        leftLabel = "Shiver",
                        rightLabel = "Swing",
                        value = magnetMovement,
                        onChange = onMagnetMovementChange,
                        tag = "magnet_movement_slider"
                    )
                    SwitchRow(
                        title = "Up/Down only",
                        checked = verticalOnly,
                        onChange = onVerticalOnlyChange,
                        tag = "up_down_switch"
                    )
                    Text(
                        text = rangeSummary,
                        color = TextDescColor,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.testTag("range_summary")
                    )
                }

                // ---- Calibration ----
                GlassCard(title = "Calibration") {
                    if (isCalibratingObject) {
                        val (step, text) = when (calibrationPhase) {
                            CalibrationPhase.HOLD_STILL ->
                                "1 of 3" to "Hold the phone still. Keep the object away."
                            CalibrationPhase.MOVE_PHONE ->
                                "2 of 3" to "Move the phone gently, as if walking. Object still away."
                            CalibrationPhase.MOVE_OBJECT, CalibrationPhase.IDLE ->
                                "3 of 3" to "Bring the object right up to the phone, then pull it away."
                        }
                        Text(
                            text = step,
                            color = YellowAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = text,
                            color = TextTitleColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp
                        )
                        LinearProgressIndicator(
                            progress = { calibrationProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = YellowAccent,
                            trackColor = GlassMuted
                        )
                        OutlinedButton(
                            onClick = onCancelCalibration,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("cancel_calibration_button")
                        ) {
                            Text("Cancel", color = TextTitleColor, maxLines = 1)
                        }
                    } else {
                        Text(
                            text = "Takes 10 seconds and sets the sliders for you. You can still adjust any of them afterwards.",
                            color = TextDescColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Button(
                            onClick = onCalibrate,
                            colors = ButtonDefaults.buttonColors(containerColor = ButtonDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("calibrate_button")
                        ) {
                            Text(
                                text = "Calibrate",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // ---- Vibration ----
                GlassCard(title = "Vibration") {
                    ChipRow(
                        labels = PatternChoices.map { it.label },
                        selected = PatternChoices.indexOf(vibePattern),
                        onSelect = { onVibePatternChange(PatternChoices[it]) },
                        tagPrefix = "vibe_pattern"
                    )
                    if (vibePattern == VibePattern.OFF) {
                        Text(
                            text = "No vibration. Only the dot shows a detection.",
                            color = TextDescColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    } else {
                        if (vibePattern != VibePattern.BUZZ) {
                            Text(text = "Vibrations", color = TextTitleColor, fontSize = 14.sp, maxLines = 1)
                            ChipRow(
                                labels = listOf("1", "2", "3", "4", "5"),
                                selected = vibeCount - 1,
                                onSelect = { onVibeCountChange(it + 1) },
                                tagPrefix = "vibe_count"
                            )
                        }
                        LabeledSlider(
                            title = "Strength",
                            valueText = "$vibeStrength%",
                            leftLabel = "Soft",
                            rightLabel = "Hard",
                            value = vibeStrength / 100f,
                            onChange = { onVibeStrengthChange((it * 100f).roundToInt()) },
                            tag = "vibe_strength_slider"
                        )
                        if (vibePattern != VibePattern.BUZZ && vibeCount > 1) {
                            LabeledSlider(
                                title = "Speed",
                                valueText = "$vibeSpeed%",
                                leftLabel = "Slow",
                                rightLabel = "Fast",
                                value = vibeSpeed / 100f,
                                onChange = { onVibeSpeedChange((it * 100f).roundToInt()) },
                                tag = "vibe_speed_slider"
                            )
                        }
                        if (vibePattern == VibePattern.PULSES || vibePattern == VibePattern.BUZZ) {
                            LabeledSlider(
                                title = "Length",
                                valueText = "$vibeLength%",
                                leftLabel = "Short",
                                rightLabel = "Long",
                                value = vibeLength / 100f,
                                onChange = { onVibeLengthChange((it * 100f).roundToInt()) },
                                tag = "vibe_length_slider"
                            )
                        }
                        TestButton(text = "Test", onClick = onTestVibration, tag = "test_vibration_button")
                    }
                }

                // ---- Second cue ----
                GlassCard(title = "Second cue") {
                    Text(
                        text = "For strong objects: plays about a second after the first cue if the object stays close.",
                        color = TextDescColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    ChipRow(
                        labels = PatternChoices.map { it.label },
                        selected = PatternChoices.indexOf(secondPattern),
                        onSelect = { onSecondPatternChange(PatternChoices[it]) },
                        tagPrefix = "second_pattern"
                    )
                    if (secondPattern != VibePattern.OFF) {
                        if (secondPattern != VibePattern.BUZZ) {
                            Text(text = "Vibrations", color = TextTitleColor, fontSize = 14.sp, maxLines = 1)
                            ChipRow(
                                labels = listOf("1", "2", "3", "4", "5"),
                                selected = secondCount - 1,
                                onSelect = { onSecondCountChange(it + 1) },
                                tagPrefix = "second_count"
                            )
                        }
                        TestButton(text = "Test", onClick = onTestSecondVibration, tag = "test_second_button")
                    }
                }

                // ---- More ----
                GlassCard(title = "More") {
                    SwitchRow(
                        title = "Show dot",
                        checked = isVisualModeEnabled,
                        onChange = onToggleVisualMode,
                        tag = "visual_mode_switch"
                    )
                    Text(
                        text = "A tiny dot in the top-left corner lights up on a detection.",
                        color = TextDescColor,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    if (isVisualModeEnabled) {
                        SwitchRow(
                            title = "Vibrate too",
                            checked = vibrationWithVisual,
                            onChange = onToggleVibrationWithVisual,
                            tag = "vibration_with_visual_switch"
                        )
                    }
                }

                // ---- Screen Off Mode: last, once everything is set ----
                Button(
                    onClick = { onToggleScreenOffMode(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("enter_screen_off_mode_button")
                ) {
                    Text(
                        text = "Turn Screen Off",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Text(
                    text = "Turns the screen pitch black so it looks locked. Detection keeps running. Swipe down with 2 fingers to come back.",
                    color = TextDescColor,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ------------------------------------------------------------------------------------
// Small building blocks. Every label is single-line so nothing wraps one word per line.
// ------------------------------------------------------------------------------------

@Composable
private fun GlassCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassCardBg)
            .border(1.dp, GlassCardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                color = TextTitleColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        content()
    }
}

@Composable
private fun Banner(text: String, color: Color, tag: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 19.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextTitleColor,
            fontSize = 16.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = YellowAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = GlassMuted
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

/** Title on the left, value on the right, slider, and a short word at each end. */
@Composable
private fun LabeledSlider(
    title: String,
    valueText: String,
    leftLabel: String,
    rightLabel: String,
    value: Float,
    onChange: (Float) -> Unit,
    tag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = TextTitleColor,
                fontSize = 16.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                color = TextDescColor,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = YellowAccent,
                activeTrackColor = YellowTrack,
                inactiveTrackColor = GlassStroke
            ),
            modifier = Modifier.testTag(tag)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = leftLabel, color = TextDescColor, fontSize = 12.sp, maxLines = 1, softWrap = false)
            Text(text = rightLabel, color = TextDescColor, fontSize = 12.sp, maxLines = 1, softWrap = false)
        }
    }
}

/** A row of equal-width choices; the selected one is highlighted. */
@Composable
private fun ChipRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) YellowAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        if (isSelected) YellowAccent else GlassMuted,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(index) }
                    .testTag("${tagPrefix}_$index"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF8A6200) else TextTitleColor,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TestButton(text: String, onClick: () -> Unit, tag: String) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .testTag(tag)
    ) {
        Text(text, color = TextTitleColor, maxLines = 1)
    }
}
