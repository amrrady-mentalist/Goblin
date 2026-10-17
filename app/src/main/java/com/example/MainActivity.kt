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
import com.example.ui.theme.LocalGoblinThemeColors
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GoblinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkMode) {
                GoblinMainApp(viewModel = viewModel)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Intercept volume keys for pocket hands-free tare
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // A held key auto-repeats; only the first press should tare, otherwise
            // holding it spams baseline resets and confirmation ticks.
            if (event != null && event.repeatCount > 0) {
                return viewModel.volumeKeyTare.value
            }
            if (viewModel.onVolumeKeyTriggered()) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // Intentionally no onDestroy() that stops the sensor engine: the engine is
    // owned by the ViewModel, which survives activity recreation (theme/language
    // change, split-screen resize...). GoblinViewModel.onCleared() stops it when
    // the ViewModel is really finished.
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoblinMainApp(viewModel: GoblinViewModel) {
    val reading by viewModel.readingState.collectAsStateWithLifecycle()
    val creatureState by viewModel.creatureState.collectAsStateWithLifecycle()
    val isPoweredOn by viewModel.isPoweredOn.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isTrickRunning by viewModel.isTrickRunning.collectAsStateWithLifecycle()
    val liveMicroTesla by viewModel.liveMicroTesla.collectAsStateWithLifecycle()
    val isSaturated by viewModel.isSaturated.collectAsStateWithLifecycle()

    val territory by viewModel.territory.collectAsStateWithLifecycle()
    val magnetPower by viewModel.magnetPower.collectAsStateWithLifecycle()
    val magnetMovement by viewModel.magnetMovement.collectAsStateWithLifecycle()
    val phoneMovement by viewModel.phoneMovement.collectAsStateWithLifecycle()
    val verticalOnly by viewModel.verticalOnly.collectAsStateWithLifecycle()
    val rangeSummary by viewModel.rangeSummary.collectAsStateWithLifecycle()

    val vibePattern by viewModel.vibePattern.collectAsStateWithLifecycle()
    val vibeCount by viewModel.vibeCount.collectAsStateWithLifecycle()
    val vibeStrength by viewModel.vibeStrength.collectAsStateWithLifecycle()
    val vibeSpeed by viewModel.vibeSpeed.collectAsStateWithLifecycle()
    val vibeLength by viewModel.vibeLength.collectAsStateWithLifecycle()
    val secondPattern by viewModel.secondPattern.collectAsStateWithLifecycle()
    val secondCount by viewModel.secondCount.collectAsStateWithLifecycle()

    val isScreenOffModeActive by viewModel.isScreenOffModeActive.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val colors = LocalGoblinThemeColors.current

    // In dark mode (matching the splash screen) or stealth mode, status bar icons render light/white.
    // In light mode, status bar icons render dark for visibility against the pale gradient.
    val statusBarContext = LocalContext.current
    val statusBarView = LocalView.current
    SideEffect {
        val window = (statusBarContext as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, statusBarView).isAppearanceLightStatusBars =
                !isScreenOffModeActive && !isDarkMode
        }
    }
    val isVisualModeEnabled by viewModel.isVisualModeEnabled.collectAsStateWithLifecycle()
    val vibrationWithVisual by viewModel.vibrationWithVisual.collectAsStateWithLifecycle()
    val isVisualDotVisible by viewModel.isVisualDotVisible.collectAsStateWithLifecycle()
    val isVisualDotStrong by viewModel.isVisualDotStrong.collectAsStateWithLifecycle()

    val isCalibratingObject by viewModel.isCalibratingObject.collectAsStateWithLifecycle()
    val calibrationPhase by viewModel.calibrationPhase.collectAsStateWithLifecycle()
    val calibrationProgress by viewModel.calibrationProgress.collectAsStateWithLifecycle()
    val calibrationMessage by viewModel.calibrationMessage.collectAsStateWithLifecycle()

    var isSplashVisible by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(colors.screenGradient)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = colors.navBg,
                    contentColor = colors.navUnselected,
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
                            selectedIconColor = colors.navSelected,
                            selectedTextColor = colors.accentGold,
                            indicatorColor = colors.navIndicator,
                            unselectedIconColor = colors.navUnselected,
                            unselectedTextColor = colors.navUnselected
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
                            selectedIconColor = colors.navSelected,
                            selectedTextColor = colors.accentGold,
                            indicatorColor = colors.navIndicator,
                            unselectedIconColor = colors.navUnselected,
                            unselectedTextColor = colors.navUnselected
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
                            creatureState = creatureState,
                            reading = reading,
                            liveMicroTesla = liveMicroTesla,
                            isSaturated = isSaturated,
                            territory = territory,
                            magnetPower = magnetPower,
                            magnetMovement = magnetMovement,
                            phoneMovement = phoneMovement,
                            verticalOnly = verticalOnly,
                            rangeSummary = rangeSummary,
                            vibePattern = vibePattern,
                            vibeCount = vibeCount,
                            vibeStrength = vibeStrength,
                            vibeSpeed = vibeSpeed,
                            vibeLength = vibeLength,
                            secondPattern = secondPattern,
                            secondCount = secondCount,
                            isVisualModeEnabled = isVisualModeEnabled,
                            vibrationWithVisual = vibrationWithVisual,
                            isCalibratingObject = isCalibratingObject,
                            calibrationPhase = calibrationPhase,
                            calibrationProgress = calibrationProgress,
                            calibrationMessage = calibrationMessage,
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = { viewModel.setDarkMode(!isDarkMode) },
                            onToggleTrick = { viewModel.toggleTrick() },
                            onToggleActivateSensor = { viewModel.togglePower() },
                            onTerritoryChange = { viewModel.setTerritory(it) },
                            onMagnetPowerChange = { viewModel.setMagnetPower(it) },
                            onMagnetMovementChange = { viewModel.setMagnetMovement(it) },
                            onPhoneMovementChange = { viewModel.setPhoneMovement(it) },
                            onVerticalOnlyChange = { viewModel.setVerticalOnly(it) },
                            onVibePatternChange = { viewModel.setVibePattern(it) },
                            onVibeCountChange = { viewModel.setVibeCount(it) },
                            onVibeStrengthChange = { viewModel.setVibeStrength(it) },
                            onVibeSpeedChange = { viewModel.setVibeSpeed(it) },
                            onVibeLengthChange = { viewModel.setVibeLength(it) },
                            onSecondPatternChange = { viewModel.setSecondPattern(it) },
                            onSecondCountChange = { viewModel.setSecondCount(it) },
                            onTestVibration = { viewModel.testMainVibration() },
                            onTestSecondVibration = { viewModel.testSecondVibration() },
                            onToggleScreenOffMode = { viewModel.setScreenOffMode(it) },
                            onToggleVisualMode = { viewModel.setVisualModeEnabled(it) },
                            onToggleVibrationWithVisual = { viewModel.setVibrationWithVisual(it) },
                            onCalibrate = { viewModel.calibrateToCurrentObject() },
                            onCancelCalibration = { viewModel.cancelCalibration() },
                            onDismissCalibrationMessage = { viewModel.dismissCalibrationMessage() },
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
