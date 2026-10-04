package com.example

import android.app.Activity
import androidx.core.view.WindowCompat
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.GoblinViewModel
import com.example.ui.PerformanceTab
import com.example.ui.screens.settings.ExactSettingsScreen
import com.example.ui.screens.settings.TutorialScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.screens.stealth.PitchBlackImmersiveOverlay
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardBorder
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlumberGreen
import com.example.ui.theme.StirringAmber
import com.example.ui.theme.StrikeMagenta
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextHigh
import com.example.ui.theme.TextMedium

class MainActivity : ComponentActivity() {

    private val viewModel: GoblinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                GoblinMainApp(viewModel = viewModel)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Intercept volume keys for pocket hands-free tare
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (viewModel.onVolumeKeyTriggered()) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.sensorEngine.stopListening()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoblinMainApp(viewModel: GoblinViewModel) {
    val reading by viewModel.readingState.collectAsStateWithLifecycle()
    val creatureState by viewModel.creatureState.collectAsStateWithLifecycle()
    val isPoweredOn by viewModel.isPoweredOn.collectAsStateWithLifecycle()
    val locatorMode by viewModel.locatorMode.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val hapticType by viewModel.hapticType.collectAsStateWithLifecycle()
    val vibrationStrength by viewModel.vibrationStrength.collectAsStateWithLifecycle()
    val isStealthActive by viewModel.isStealthActive.collectAsStateWithLifecycle()
    val autoPocketStealth by viewModel.autoPocketStealth.collectAsStateWithLifecycle()
    val volumeKeyTare by viewModel.volumeKeyTare.collectAsStateWithLifecycle()

    // Settings matching screenshot
    val isTrickRunning by viewModel.isTrickRunning.collectAsStateWithLifecycle()
    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
    val adaptiveSensitivity by viewModel.adaptiveSensitivity.collectAsStateWithLifecycle()
    val isSmartAlarmEnabled by viewModel.isSmartAlarmEnabled.collectAsStateWithLifecycle()
    val smartAlarmThreshold by viewModel.smartAlarmThreshold.collectAsStateWithLifecycle()
    val isSleepingMode by viewModel.isSleepingMode.collectAsStateWithLifecycle()
    val liveMicroTesla by viewModel.liveMicroTesla.collectAsStateWithLifecycle()

    val isScreenOffModeActive by viewModel.isScreenOffModeActive.collectAsStateWithLifecycle()

    // The Settings/Tutorial screens are light (frosted glass over a pale gradient),
    // so the status bar icons need to render dark to stay visible -- otherwise
    // they're white-on-white. The stealth screen hides the status bar entirely, so
    // this only matters while that's not showing.
    val statusBarContext = LocalContext.current
    val statusBarView = LocalView.current
    SideEffect {
        val window = (statusBarContext as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, statusBarView).isAppearanceLightStatusBars =
                !isScreenOffModeActive
        }
    }
    val isVisualModeEnabled by viewModel.isVisualModeEnabled.collectAsStateWithLifecycle()
    val vibrationWithVisual by viewModel.vibrationWithVisual.collectAsStateWithLifecycle()
    val isVisualDotVisible by viewModel.isVisualDotVisible.collectAsStateWithLifecycle()
    val isVisualDotStrong by viewModel.isVisualDotStrong.collectAsStateWithLifecycle()

    val isUtTriggerEnabled by viewModel.isUtTriggerEnabled.collectAsStateWithLifecycle()
    val utBaselinePattern by viewModel.utBaselinePattern.collectAsStateWithLifecycle()
    val utPeakPattern by viewModel.utPeakPattern.collectAsStateWithLifecycle()
    val activeUtTier by viewModel.activeUtTier.collectAsStateWithLifecycle()
    val isCalibratingObject by viewModel.isCalibratingObject.collectAsStateWithLifecycle()
    val objectCalibrationPeak by viewModel.objectCalibrationPeak.collectAsStateWithLifecycle()
    val calibrationMessage by viewModel.calibrationMessage.collectAsStateWithLifecycle()
    val strongHitMultiplier by viewModel.strongHitMultiplier.collectAsStateWithLifecycle()

    var isSplashVisible by remember { mutableStateOf(true) }

    // Frosted-glass shell: a soft translucent nav bar over the same light gradient
    // the two screens use, replacing the old dark "Obsidian" chrome so the whole
    // app reads as one consistent, modern look rather than a dark frame around
    // light content.
    val glassNavBg = Color(0xFFFFFFFF).copy(alpha = 0.75f)
    val glassNavSelected = Color(0xFF241B3D)
    val glassNavUnselected = Color(0xFF8A86A0)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEFE9FF))) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = glassNavBg,
                    contentColor = glassNavUnselected,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = currentTab == PerformanceTab.SETTINGS,
                        onClick = { viewModel.setTab(PerformanceTab.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = glassNavSelected,
                            selectedTextColor = Color(0xFFEAB308),
                            indicatorColor = Color(0xFFEAB308).copy(alpha = 0.25f),
                            unselectedIconColor = glassNavUnselected,
                            unselectedTextColor = glassNavUnselected
                        ),
                        modifier = Modifier.testTag("tab_settings")
                    )

                    NavigationBarItem(
                        selected = currentTab == PerformanceTab.TUTORIAL,
                        onClick = { viewModel.setTab(PerformanceTab.TUTORIAL) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Tutorial"
                            )
                        },
                        label = { Text("Tutorial") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = glassNavSelected,
                            selectedTextColor = Color(0xFFEAB308),
                            indicatorColor = Color(0xFFEAB308).copy(alpha = 0.25f),
                            unselectedIconColor = glassNavUnselected,
                            unselectedTextColor = glassNavUnselected
                        ),
                        modifier = Modifier.testTag("tab_tutorial")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    PerformanceTab.SETTINGS -> {
                        ExactSettingsScreen(
                            isTrickRunning = isTrickRunning,
                            isSensorActivated = isPoweredOn,
                            sensitivity = sensitivity,
                            adaptiveSensitivity = adaptiveSensitivity,
                            isSmartAlarmEnabled = isSmartAlarmEnabled,
                            smartAlarmThreshold = smartAlarmThreshold,
                            isSleepingMode = isSleepingMode,
                            liveMicroTesla = liveMicroTesla,
                            isScreenOffModeActive = isScreenOffModeActive,
                            isVisualModeEnabled = isVisualModeEnabled,
                            vibrationWithVisual = vibrationWithVisual,
                            isUtTriggerEnabled = isUtTriggerEnabled,
                            utBaselinePattern = utBaselinePattern,
                            utPeakPattern = utPeakPattern,
                            activeUtTier = activeUtTier,
                            isCalibratingObject = isCalibratingObject,
                            objectCalibrationPeak = objectCalibrationPeak,
                            calibrationMessage = calibrationMessage,
                            strongHitMultiplier = strongHitMultiplier,
                            locatorMode = locatorMode,
                            creatureState = creatureState,
                            onToggleTrick = { viewModel.toggleTrick() },
                            onToggleActivateSensor = { viewModel.togglePower() },
                            onSensitivityChange = { viewModel.setSensitivity(it) },
                            onAdaptiveSensitivityChange = { viewModel.setAdaptiveSensitivity(it) },
                            onToggleSmartAlarm = { viewModel.setSmartAlarmEnabled(!isSmartAlarmEnabled) },
                            onSmartAlarmThresholdChange = { viewModel.setSmartAlarmThreshold(it) },
                            onToggleSleepingMode = { viewModel.toggleSleepingMode() },
                            onToggleScreenOffMode = { viewModel.setScreenOffMode(it) },
                            onToggleVisualMode = { viewModel.setVisualModeEnabled(it) },
                            onToggleVibrationWithVisual = { viewModel.setVibrationWithVisual(it) },
                            onToggleUtTrigger = { viewModel.setUtTriggerEnabled(it) },
                            onUtBaselinePatternChange = { viewModel.setUtBaselinePattern(it) },
                            onUtPeakPatternChange = { viewModel.setUtPeakPattern(it) },
                            onTestUtBaselinePattern = { viewModel.testUtBaselinePattern() },
                            onTestUtPeakPattern = { viewModel.testUtPeakPattern() },
                            onCalibrateToObject = { viewModel.calibrateToCurrentObject() },
                            onDismissCalibrationMessage = { viewModel.dismissCalibrationMessage() },
                            onStrongHitMultiplierChange = { viewModel.setStrongHitMultiplier(it) },
                            onSelectLocatorMode = { viewModel.setLocatorMode(it) },
                            onNavigateBack = {},
                            onOpenTutorial = { viewModel.setTab(PerformanceTab.TUTORIAL) }
                        )
                    }

                    PerformanceTab.TUTORIAL -> {
                        TutorialScreen(
                            onNavigateBack = { viewModel.setTab(PerformanceTab.SETTINGS) }
                        )
                    }
                }
            }
        }

        // Screen Off Mode (Pitch Black Immersive Mode)
        if (isScreenOffModeActive) {
            PitchBlackImmersiveOverlay(
                isVisualModeEnabled = isVisualModeEnabled,
                isVisualDotVisible = isVisualDotVisible,
                isVisualDotStrong = isVisualDotStrong,
                onExit = { viewModel.setScreenOffMode(false) }
            )
        }

        // Animated Splash Screen matching image background (#0A1A2A)
        if (isSplashVisible) {
            SplashScreen(onSplashFinished = { isSplashVisible = false })
        }
    }
}
