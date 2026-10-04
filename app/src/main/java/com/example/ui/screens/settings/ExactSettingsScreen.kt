package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import com.example.domain.model.CreatureState
import com.example.domain.model.LocatorMode
import com.example.domain.model.UtBaselinePattern
import com.example.domain.model.UtPeakPattern
import com.example.domain.model.UtTriggerTier
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

// Frosted-glass design language: a soft gradient backdrop with a few blurred
// color blobs behind translucent "frosted" cards. True backdrop-blur (blurring
// whatever sits behind a card) isn't practical in plain Compose, so the glass
// feel comes from layering semi-transparent panels over pre-blurred shapes
// instead -- the same trick most glassmorphism UIs use under the hood.
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
private val DividerColor = Color(0xFFFFFFFF).copy(alpha = 0.5f)

// Frosted card surface: translucent white over the blurred backdrop, with a
// soft light border to catch a glassy edge highlight.
private val GlassCardBg = Color(0xFFFFFFFF).copy(alpha = 0.55f)
private val GlassCardBorder = Color(0xFFFFFFFF).copy(alpha = 0.65f)
private val GlassStroke = Color(0xFFFFFFFF).copy(alpha = 0.45f)
private val InputBoxBg = Color(0xFFFFFFFF).copy(alpha = 0.65f)
private val GlassMuted = Color(0xFFFFFFFF).copy(alpha = 0.40f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExactSettingsScreen(
    isTrickRunning: Boolean,
    isSensorActivated: Boolean,
    sensitivity: Float,
    adaptiveSensitivity: Int,
    isSmartAlarmEnabled: Boolean,
    smartAlarmThreshold: Float,
    isSleepingMode: Boolean,
    liveMicroTesla: Int,
    creatureState: CreatureState = CreatureState.SLUMBERING,
    isScreenOffModeActive: Boolean = false,
    isVisualModeEnabled: Boolean = false,
    vibrationWithVisual: Boolean = true,
    isUtTriggerEnabled: Boolean = true,
    utBaselinePattern: UtBaselinePattern = UtBaselinePattern.SINGLE_PULSE,
    utPeakPattern: UtPeakPattern = UtPeakPattern.CONTINUOUS,
    activeUtTier: UtTriggerTier = UtTriggerTier.IDLE,
    isCalibratingObject: Boolean = false,
    objectCalibrationPeak: Float = 0f,
    calibrationMessage: String? = null,
    strongHitMultiplier: Float = 2.2f,
    locatorMode: LocatorMode = LocatorMode.PROXIMITY_50CM,
    onToggleTrick: () -> Unit,
    onToggleActivateSensor: () -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onAdaptiveSensitivityChange: (Int) -> Unit,
    onToggleSmartAlarm: () -> Unit,
    onSmartAlarmThresholdChange: (Float) -> Unit,
    onToggleSleepingMode: () -> Unit,
    onToggleScreenOffMode: (Boolean) -> Unit = {},
    onToggleVisualMode: (Boolean) -> Unit = {},
    onToggleVibrationWithVisual: (Boolean) -> Unit = {},
    onToggleUtTrigger: (Boolean) -> Unit = {},
    onUtBaselinePatternChange: (UtBaselinePattern) -> Unit = {},
    onUtPeakPatternChange: (UtPeakPattern) -> Unit = {},
    onTestUtBaselinePattern: () -> Unit = {},
    onTestUtPeakPattern: () -> Unit = {},
    onCalibrateToObject: () -> Unit = {},
    onDismissCalibrationMessage: () -> Unit = {},
    onStrongHitMultiplierChange: (Float) -> Unit = {},
    onSelectLocatorMode: (LocatorMode) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onOpenTutorial: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(calibrationMessage) {
        val message = calibrationMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissCalibrationMessage()
        }
    }

    // Deliberately NOT re-keyed to smartAlarmThreshold: this field is the only thing
    // that ever changes that value, so re-keying it here was resetting the field's
    // text (and cursor position) out from under the user's own typing on every
    // keystroke that happened to parse to a valid number -- it never felt "stable"
    // to type into. It initializes once from the persisted value and then is left
    // alone, matching standard "uncontrolled text field" practice.
    // Briefly shows a "Ready" confirmation the moment room calibration finishes
    // (and only then -- not on every later state change), so there's a clear
    // signal of when it's safe to start, instead of the banner just vanishing.
    var wasCalibrating by remember { mutableStateOf(true) }
    var showReadyBanner by remember { mutableStateOf(false) }
    LaunchedEffect(creatureState) {
        if (wasCalibrating && creatureState != CreatureState.CALIBRATING) {
            showReadyBanner = true
            kotlinx.coroutines.delay(1800L)
            showReadyBanner = false
        }
        wasCalibrating = (creatureState == CreatureState.CALIBRATING)
    }

    var smartAlarmInputText by remember {
        mutableStateOf(smartAlarmThreshold.toInt().toString())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenGradient)
    ) {
        // Soft blurred color blobs behind the frosted cards -- this is what reads
        // as "glass" once the translucent card surfaces sit on top of them.
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
            // Top Bar
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Settings",
                            color = TextTitleColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextTitleColor
                        )
                    }
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

            HorizontalDivider(color = GlassStroke, thickness = 0.8.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // 0. Room-calibration status: lets the user know when the app is
                // done reading the surrounding magnetic field and ready to go.
                if (creatureState == CreatureState.CALIBRATING || showReadyBanner) {
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
                                text = "Reading the surrounding magnetic field…",
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
                                text = "Ready — room baseline set",
                                color = TextTitleColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 1. Start Trick Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start Trick",
                        color = TextTitleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Button(
                        onClick = onToggleTrick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTrickRunning) Color(0xFF0F172A) else ButtonDark
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("start_trick_button")
                    ) {
                        Text(
                            text = if (isTrickRunning) "Stop" else "Start",
                            color = if (isTrickRunning) YellowAccent else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 2. Activate sensor Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activate sensor",
                        color = TextTitleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Switch(
                        checked = isSensorActivated,
                        onCheckedChange = { onToggleActivateSensor() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YellowAccent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = GlassMuted
                        ),
                        modifier = Modifier.testTag("activate_sensor_switch")
                    )
                }

                Text(
                    text = "When this option is active the magnetic sensor is turned on\n(It must be active to start the trick)",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = GlassStroke, thickness = 0.8.dp)

                // 2b. Detection Mode -- this was previously only reachable through a
                // bottom sheet with no button anywhere that actually opened it.
                Text(
                    text = "Detection Mode",
                    color = TextTitleColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocatorMode.values().forEach { mode ->
                        val isSelected = locatorMode == mode
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) YellowAccent.copy(alpha = 0.15f) else Color.White)
                                .border(
                                    1.dp,
                                    if (isSelected) YellowAccent else GlassMuted,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectLocatorMode(mode) }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = mode.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF8A6200) else Color(0xFF0F172A)
                                )
                                Text(
                                    text = mode.description,
                                    fontSize = 12.sp,
                                    color = TextDescColor
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Perimeter Proximity and the Earbud trick both react to any movement near the phone, from any direction. Up/Down Y-Axis only reacts to vertical motion and ignores everything else — use it only if the object always moves straight up or down past a stationary phone.",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = GlassStroke, thickness = 0.8.dp)

                // 3. Sensitivity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "Sensitivity: %.1f", sensitivity),
                        color = TextTitleColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Slider(
                    value = sensitivity,
                    onValueChange = { onSensitivityChange(it) },
                    valueRange = 0.5f..8.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = YellowAccent,
                        activeTrackColor = YellowTrack,
                        inactiveTrackColor = GlassStroke
                    ),
                    modifier = Modifier.testTag("sensitivity_slider")
                )

                Text(
                    text = "Swipe right to increase reading distance, swipe left to avoid false positives. If the phone vibrates when it is not moving and there is not a magnet close to it, then swipe left.",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // 4. Adaptative Sensitivity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Adaptative Sensitivity: $adaptiveSensitivity",
                        color = TextTitleColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Slider(
                    value = adaptiveSensitivity.toFloat(),
                    onValueChange = { onAdaptiveSensitivityChange(it.toInt()) },
                    valueRange = 1f..20f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = YellowAccent,
                        activeTrackColor = YellowTrack,
                        inactiveTrackColor = GlassStroke
                    ),
                    modifier = Modifier.testTag("adaptive_sensitivity_slider")
                )

                Text(
                    text = "Swipe right to increase reading distance, swipe left to increase freedom of movement. If the phone vibrates when it is moving and there is not a magnet close to it, then swipe left.",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = GlassStroke, thickness = 0.8.dp)

                // 5. Smart alarm Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart alarm",
                        color = TextTitleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Switch(
                        checked = isSmartAlarmEnabled,
                        onCheckedChange = { onToggleSmartAlarm() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YellowAccent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = GlassMuted
                        ),
                        modifier = Modifier.testTag("smart_alarm_switch")
                    )
                }

                Text(
                    text = "Value smart alarm",
                    color = TextTitleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )

                OutlinedTextField(
                    value = smartAlarmInputText,
                    onValueChange = { text ->
                        if (text.all { it.isDigit() } && text.length <= 4) {
                            smartAlarmInputText = text
                            val parsed = text.toFloatOrNull()
                            if (parsed != null && parsed >= 40f) {
                                onSmartAlarmThresholdChange(parsed)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("smart_alarm_value_input"),
                    textStyle = TextStyle(
                        color = Color(0xFF0F172A),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A),
                        focusedContainerColor = InputBoxBg,
                        unfocusedContainerColor = InputBoxBg,
                        focusedBorderColor = YellowAccent,
                        unfocusedBorderColor = GlassMuted,
                        cursorColor = Color(0xFF0F172A)
                    )
                )

                Text(
                    text = "Activating the smart alarm triggers vibration while the app is not ready to function because the magnetic sensor exceeds the defined value or is saturated. The user must move to reset the alarm.",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = GlassStroke, thickness = 0.8.dp)

                // 6. Sleeping Mode Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sleeping Mode",
                        color = TextTitleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Switch(
                        checked = isSleepingMode,
                        onCheckedChange = {
                            if (isSensorActivated) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "In order to change the status of 'sleeping mode', the 'activate sensor' status must be turned off first."
                                    )
                                }
                            } else {
                                onToggleSleepingMode()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YellowAccent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = GlassMuted
                        ),
                        modifier = Modifier.testTag("sleeping_mode_switch")
                    )
                }

                Text(
                    text = "When this mode is active the App will remain inactive. To activate, bring a magnet close to the phone until you feel the vibrations. In order to change the status of \"sleeping mode\", the \"activate sensor\" status must be turned off first.",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = GlassStroke, thickness = 0.8.dp)

                // 7. MicroTesla Value Section & Dual-Tier Vibration Triggers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MicroTesla Value",
                        color = TextTitleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )

                    // Dot color tracks the same SLUMBERING/STIRRING/AWAKE/STRIKING
                    // tiers the engine already derives the "Detected"/"Strong-Close"
                    // haptic split from, rather than a separate made-up scale: green
                    // while things are quiet, yellow for a faint reading, red once
                    // it's strong enough to count as a real detection.
                    val readingDotColor = when (creatureState) {
                        CreatureState.CALIBRATING, CreatureState.DORMANT -> Color(0xFF94A3B8)
                        CreatureState.SLUMBERING -> Color(0xFF22C55E)
                        CreatureState.STIRRING -> Color(0xFFEAB308)
                        CreatureState.AWAKE, CreatureState.STRIKING -> Color(0xFFDC2626)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("microtesla_value_badge")
                    ) {
                        Text(
                            text = "$liveMicroTesla µT",
                            color = TextTitleColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(readingDotColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // µT Threshold Control Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Two-Feel Vibration",
                            color = TextTitleColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "A normal detection plays the Detected pattern; an unusually strong one plays the Strong/Close pattern instead. Calibrate below for tonight's object.",
                            color = TextDescColor,
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isUtTriggerEnabled,
                        onCheckedChange = onToggleUtTrigger,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YellowAccent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = GlassMuted
                        ),
                        modifier = Modifier.testTag("ut_trigger_switch")
                    )
                }

                if (isUtTriggerEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Strong/close cutoff: how far above threshold a hit must clear
                    // before it counts as "strong" (green dot + Strong/Close pattern)
                    // instead of "weak" (yellow dot + Detected pattern).
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassCardBg)
                            .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Strong/Close Cutoff",
                                color = Color(0xFF0F172A),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${String.format("%.1f", strongHitMultiplier)}x",
                                color = YellowAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Lower = more hits read as \"strong\". Higher = only a very close or very strong object does.",
                            color = TextDescColor,
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        )
                        Slider(
                            value = strongHitMultiplier,
                            onValueChange = onStrongHitMultiplierChange,
                            valueRange = 1.2f..4.0f,
                            steps = 27,
                            colors = SliderDefaults.colors(
                                thumbColor = YellowAccent,
                                activeTrackColor = YellowAccent,
                                inactiveTrackColor = GlassMuted
                            ),
                            modifier = Modifier.testTag("strong_hit_multiplier_slider")
                        )
                    }

                    // Calibrate-to-object card: replaces fixed µT thresholds with a
                    // short capture of whatever object is being used tonight, so the
                    // same detector works whether it's a faint earbud magnet or a
                    // strong fridge magnet.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassCardBg)
                            .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Calibrate To Tonight's Object",
                            color = Color(0xFF0F172A),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isCalibratingObject)
                                "Hold or pass the object through range now… peak seen: ${"%.2f".format(objectCalibrationPeak)} µT"
                            else
                                "Move the object you're using tonight through the real detection range, then tap Calibrate and keep moving it for 3 seconds.",
                            color = TextDescColor,
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        )

                        Button(
                            onClick = onCalibrateToObject,
                            enabled = !isCalibratingObject,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YellowAccent,
                                contentColor = Color.Black,
                                disabledContainerColor = GlassStroke
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("calibrate_object_button")
                        ) {
                            Text(
                                text = if (isCalibratingObject) "Calibrating… hold object nearby" else "Calibrate (3s)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Detected Pattern:",
                            color = Color(0xFF475569),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Pattern Selector Chips for Baseline (wrapped 3-per-row so
                        // longer labels like "Ghost Tick" have room to breathe)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            UtBaselinePattern.values().toList().chunked(3).forEach { rowPatterns ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowPatterns.forEach { pattern ->
                                        val isSelected = utBaselinePattern == pattern
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) YellowAccent else Color.White)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) YellowAccent else GlassMuted,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { onUtBaselinePatternChange(pattern) }
                                                .padding(horizontal = 6.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = pattern.displayName,
                                                fontSize = 11.sp,
                                                lineHeight = 13.sp,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.Black else Color(0xFF334155)
                                            )
                                        }
                                    }
                                    // Pad out the last row so its chips stay the same width as a full row
                                    repeat(3 - rowPatterns.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onTestUtBaselinePattern,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ButtonDark,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("test_ut_baseline_button")
                        ) {
                            Text(
                                text = "Test Detected Vibration (${utBaselinePattern.displayName})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Strong/Close hit pattern: plays automatically instead of the
                    // Detected pattern whenever a confirmed hit clears threshold by a
                    // wide margin (a strong magnet, or the object passing very close).
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassCardBg)
                            .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Strong / Close Hit Pattern",
                            color = Color(0xFF0F172A),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Plays instead of the Detected pattern when a hit comes in unusually strong.",
                            color = TextDescColor,
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        )

                        // Pattern Selector Chips for Peak (wrapped 2-per-row — these
                        // labels, e.g. "Double Heavy Strike", are the longest in the app)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            UtPeakPattern.values().toList().chunked(2).forEach { rowPatterns ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowPatterns.forEach { pattern ->
                                        val isSelected = utPeakPattern == pattern
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) Color(0xFFDC2626) else Color.White)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color(0xFFDC2626) else GlassMuted,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { onUtPeakPatternChange(pattern) }
                                                .padding(horizontal = 6.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = pattern.displayName,
                                                fontSize = 11.sp,
                                                lineHeight = 13.sp,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else Color(0xFF334155)
                                            )
                                        }
                                    }
                                    repeat(2 - rowPatterns.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onTestUtPeakPattern,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("test_ut_peak_button")
                        ) {
                            Text(
                                text = "Test Strong/Close Vibration (${utPeakPattern.displayName})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                HorizontalDivider(color = GlassStroke, thickness = 0.8.dp)

                // 8. Screen Off Mode Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Screen off mode",
                        color = TextTitleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Button(
                        onClick = { onToggleScreenOffMode(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = ButtonDark),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("enter_screen_off_mode_button")
                    ) {
                        Text(
                            text = "Turn Screen Off",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Turns into a pitch black screen in immersive mode so it looks like the phone is locked. The app continues detecting in the background. Swipe down with 2 fingers to get back to the app.",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = GlassStroke, thickness = 0.8.dp)

                // 9. Visual Mode Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Visual mode",
                        color = TextTitleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Switch(
                        checked = isVisualModeEnabled,
                        onCheckedChange = { onToggleVisualMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YellowAccent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = GlassMuted
                        ),
                        modifier = Modifier.testTag("visual_mode_switch")
                    )
                }

                Text(
                    text = "In pitch black screen off mode, a very tiny green dot appears on the very top left corner when a magnet is detected.",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                if (isVisualModeEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Use vibrations with visual mode",
                            color = TextTitleColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )

                        Switch(
                            checked = vibrationWithVisual,
                            onCheckedChange = { onToggleVibrationWithVisual(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = YellowAccent,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = GlassMuted
                            ),
                            modifier = Modifier.testTag("vibration_with_visual_switch")
                        )
                    }

                    Text(
                        text = if (vibrationWithVisual) "Both vibrations and green dot will alert you on detection." else "Vibrations muted: green dot only for silent detection.",
                        color = TextDescColor,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                HorizontalDivider(color = GlassStroke, thickness = 0.8.dp)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
