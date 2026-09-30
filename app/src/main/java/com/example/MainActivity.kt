package com.example

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.GoblinViewModel
import com.example.ui.PerformanceTab
import com.example.ui.screens.creature.CreatureScreen
import com.example.ui.screens.hud.MentalistHudScreen
import com.example.ui.screens.log.EventLogScreen
import com.example.ui.screens.settings.ExactSettingsScreen
import com.example.ui.screens.settings.SettingsSheet
import com.example.ui.screens.settings.TutorialScreen
import com.example.ui.screens.stealth.PitchBlackImmersiveOverlay
import com.example.ui.screens.stealth.StealthOverlay
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
    val isRoomWideMode by viewModel.isRoomWideMode.collectAsStateWithLifecycle()
    val locatorMode by viewModel.locatorMode.collectAsStateWithLifecycle()
    val rumbleMode by viewModel.rumbleMode.collectAsStateWithLifecycle()
    val targetMinStrength by viewModel.targetMinStrengthPercent.collectAsStateWithLifecycle()
    val targetMaxStrength by viewModel.targetMaxStrengthPercent.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val fogLevel by viewModel.fogLevel.collectAsStateWithLifecycle()
    val effectiveThreshold by viewModel.effectiveThreshold.collectAsStateWithLifecycle()
    val hapticType by viewModel.hapticType.collectAsStateWithLifecycle()
    val vibrationStrength by viewModel.vibrationStrength.collectAsStateWithLifecycle()
    val isStealthActive by viewModel.isStealthActive.collectAsStateWithLifecycle()
    val stealthMicroDot by viewModel.stealthMicroDot.collectAsStateWithLifecycle()
    val autoPocketStealth by viewModel.autoPocketStealth.collectAsStateWithLifecycle()
    val volumeKeyTare by viewModel.volumeKeyTare.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val venueProfiles by viewModel.venueProfiles.collectAsStateWithLifecycle()
    val recentEvents by viewModel.recentEvents.collectAsStateWithLifecycle()

    // Settings matching screenshot
    val isTrickRunning by viewModel.isTrickRunning.collectAsStateWithLifecycle()
    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
    val adaptiveSensitivity by viewModel.adaptiveSensitivity.collectAsStateWithLifecycle()
    val isSmartAlarmEnabled by viewModel.isSmartAlarmEnabled.collectAsStateWithLifecycle()
    val smartAlarmThreshold by viewModel.smartAlarmThreshold.collectAsStateWithLifecycle()
    val isSleepingMode by viewModel.isSleepingMode.collectAsStateWithLifecycle()
    val liveMicroTesla by viewModel.liveMicroTesla.collectAsStateWithLifecycle()

    val isScreenOffModeActive by viewModel.isScreenOffModeActive.collectAsStateWithLifecycle()
    val isVisualModeEnabled by viewModel.isVisualModeEnabled.collectAsStateWithLifecycle()
    val vibrationWithVisual by viewModel.vibrationWithVisual.collectAsStateWithLifecycle()
    val isVisualDotVisible by viewModel.isVisualDotVisible.collectAsStateWithLifecycle()

    // Immediate Notification Permission Request for Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(Unit) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var showSettingsSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MidnightBg)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MidnightBg,
            topBar = {
                if (currentTab != PerformanceTab.SETTINGS && currentTab != PerformanceTab.TUTORIAL) {
                    TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        when (creatureState) {
                                            com.example.domain.model.CreatureState.DORMANT -> Color(0xFF64748B)
                                            com.example.domain.model.CreatureState.CALIBRATING -> Color(0xFF38BDF8)
                                            com.example.domain.model.CreatureState.SLUMBERING -> SlumberGreen
                                            com.example.domain.model.CreatureState.STIRRING -> StirringAmber
                                            com.example.domain.model.CreatureState.AWAKE -> ElectricCyan
                                            com.example.domain.model.CreatureState.STRIKING -> StrikeMagenta
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = "GOBLIN",
                                color = TextHigh,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                letterSpacing = 2.sp
                            )

                            // Active Profile pill
                            activeProfile?.let { profile ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MidnightCard)
                                        .border(1.dp, MidnightCardBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = profile.name.substringBefore(" (").take(16),
                                        color = ElectricCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        // Master On / Off Power Button
                        IconButton(
                            onClick = { viewModel.togglePower() },
                            modifier = Modifier.testTag("topbar_power_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = if (isPoweredOn) "Turn Off Detector" else "Turn On Detector",
                                tint = if (isPoweredOn) SlumberGreen else Color(0xFFEF4444)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.setTab(PerformanceTab.SETTINGS) },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = TextHigh
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBg)
                )
            }
        },
            bottomBar = {
                NavigationBar(
                    containerColor = MidnightSurface,
                    contentColor = TextMedium,
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
                            selectedIconColor = MidnightBg,
                            selectedTextColor = Color(0xFFEAB308),
                            indicatorColor = Color(0xFFEAB308),
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextDim
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
                            selectedIconColor = MidnightBg,
                            selectedTextColor = Color(0xFFEAB308),
                            indicatorColor = Color(0xFFEAB308),
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextDim
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
                onExit = { viewModel.setScreenOffMode(false) }
            )
        }

        // Configuration & Preset Bottom Sheet
        if (showSettingsSheet) {
            SettingsSheet(
                venueProfiles = venueProfiles,
                activeProfile = activeProfile,
                currentHapticType = hapticType,
                currentStrength = vibrationStrength,
                locatorMode = locatorMode,
                targetMinStrengthPercent = targetMinStrength,
                targetMaxStrengthPercent = targetMaxStrength,
                autoPocketStealth = autoPocketStealth,
                volumeKeyTare = volumeKeyTare,
                stealthMicroDot = stealthMicroDot,
                onSelectProfile = { viewModel.selectProfile(it) },
                onSaveProfile = { name, desc -> viewModel.saveCurrentAsProfile(name, desc) },
                onDeleteProfile = { viewModel.deleteProfile(it) },
                onSelectLocatorMode = { viewModel.setLocatorMode(it) },
                onSetTargetStrengthWindow = { minP, maxP -> viewModel.setTargetStrengthWindow(minP, maxP) },
                onSelectHaptic = { viewModel.setHapticType(it) },
                onSelectStrength = { viewModel.setVibrationStrength(it) },
                onToggleAutoPocket = { viewModel.setAutoPocketStealth(it) },
                onToggleVolumeTare = { viewModel.setVolumeKeyTare(it) },
                onToggleMicroDot = { viewModel.setStealthMicroDot(it) },
                onTestHaptic = { viewModel.testHaptic() },
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}
