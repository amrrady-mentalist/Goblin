package com.example.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

// Exact styling colors matching the screenshot
private val ScreenBg = Color(0xFFFAFBFB)
private val TextTitleColor = Color(0xFF263238)
private val TextDescColor = Color(0xFF78909C)
private val YellowAccent = Color(0xFFEAB308)
private val YellowTrack = Color(0xFFFACC15)
private val ButtonDark = Color(0xFF1E293B)
private val DividerColor = Color(0xFFF1F5F9)
private val InputBoxBg = Color(0xFFF1F5F9)

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
    isScreenOffModeActive: Boolean = false,
    isVisualModeEnabled: Boolean = false,
    vibrationWithVisual: Boolean = true,
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
    onNavigateBack: () -> Unit = {},
    onOpenTutorial: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var smartAlarmInputText by remember(smartAlarmThreshold) {
        mutableStateOf(smartAlarmThreshold.toInt().toString())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScreenBg)
            )

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

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
                            uncheckedTrackColor = Color(0xFFCBD5E1)
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

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

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
                        inactiveTrackColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.testTag("sensitivity_slider")
                )

                Text(
                    text = "Swipe right to increase reading distance, swipe left to avoid false positives. If the phone vibrates when it is not moving and there is not a manget close to it, then swipe left. (More info in the video tutorial).",
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
                        inactiveTrackColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.testTag("adaptive_sensitivity_slider")
                )

                Text(
                    text = "Swipe right to increase reading distance, swipe left to increase freedom of movement. If the phone vibrates when it is moving and there is not a manget close to it, then swipe left.\n(More info in the video tutorial)",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

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
                            uncheckedTrackColor = Color(0xFFCBD5E1)
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
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        cursorColor = Color(0xFF0F172A)
                    )
                )

                Text(
                    text = "Activating the smart alarm triggers vibration while the app is not ready to function because the magnetic sensor exceeds the defined value or is saturated. The user must move to reset the alarm.",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

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
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.testTag("sleeping_mode_switch")
                    )
                }

                Text(
                    text = "When this mode is active the App will remain inactive, to activate bring a magnet close tho the phone until you feel the vibrations. In order to be able to change the status of the \"sleeping mode\", the \"activate sensor\" status must be turned off\n(More info in the video tutorial)",
                    color = TextDescColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                // 7. MicroTesla Value Section
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

                    Text(
                        text = "$liveMicroTesla µT",
                        color = TextTitleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("microtesla_value_text")
                    )
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

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

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

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
                            uncheckedTrackColor = Color(0xFFCBD5E1)
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
                                uncheckedTrackColor = Color(0xFFCBD5E1)
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

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
