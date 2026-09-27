package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
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
import com.example.ui.screens.settings.SettingsSheet
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoblinMainApp(viewModel: GoblinViewModel) {
    val reading by viewModel.readingState.collectAsStateWithLifecycle()
    val creatureState by viewModel.creatureState.collectAsStateWithLifecycle()
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

    var showSettingsSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MidnightBg)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MidnightBg,
            topBar = {
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
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Settings and Presets",
                                tint = TextHigh
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBg)
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MidnightSurface,
                    contentColor = TextMedium,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = currentTab == PerformanceTab.CREATURE,
                        onClick = { viewModel.setTab(PerformanceTab.CREATURE) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.RemoveRedEye,
                                contentDescription = "Creature Eye"
                            )
                        },
                        label = { Text("Creature") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MidnightBg,
                            selectedTextColor = ElectricCyan,
                            indicatorColor = ElectricCyan,
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextDim
                        ),
                        modifier = Modifier.testTag("tab_creature")
                    )

                    NavigationBarItem(
                        selected = currentTab == PerformanceTab.MENTALIST_HUD,
                        onClick = { viewModel.setTab(PerformanceTab.MENTALIST_HUD) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Telemetry Scope"
                            )
                        },
                        label = { Text("Scope HUD") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MidnightBg,
                            selectedTextColor = ElectricCyan,
                            indicatorColor = ElectricCyan,
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextDim
                        ),
                        modifier = Modifier.testTag("tab_hud")
                    )

                    NavigationBarItem(
                        selected = currentTab == PerformanceTab.LOG_REHEARSAL,
                        onClick = { viewModel.setTab(PerformanceTab.LOG_REHEARSAL) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Rehearsal Log"
                            )
                        },
                        label = { Text("Cue Log") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MidnightBg,
                            selectedTextColor = ElectricCyan,
                            indicatorColor = ElectricCyan,
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextDim
                        ),
                        modifier = Modifier.testTag("tab_log")
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
                    PerformanceTab.CREATURE -> {
                        CreatureScreen(
                            creatureState = creatureState,
                            reading = reading,
                            effectiveThreshold = effectiveThreshold,
                            fogLevel = fogLevel,
                            hapticType = hapticType,
                            onFogChange = { viewModel.setFogLevel(it) },
                            onTareBaseline = { viewModel.tareBaseline() },
                            onEnterStealth = { viewModel.setStealth(true) },
                            onTestHaptic = { viewModel.testHaptic() }
                        )
                    }

                    PerformanceTab.MENTALIST_HUD -> {
                        MentalistHudScreen(
                            reading = reading,
                            creatureState = creatureState,
                            effectiveThreshold = effectiveThreshold,
                            onTareBaseline = { viewModel.tareBaseline() }
                        )
                    }

                    PerformanceTab.LOG_REHEARSAL -> {
                        EventLogScreen(
                            events = recentEvents,
                            onClearEvents = { viewModel.clearHistory() }
                        )
                    }
                }
            }
        }

        // Full Screen Stealth Overlay (OLED True Black)
        AnimatedVisibility(
            visible = isStealthActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            StealthOverlay(
                creatureState = creatureState,
                showMicroDot = stealthMicroDot,
                onExitStealth = { viewModel.setStealth(false) },
                onTareBaseline = { viewModel.tareBaseline() }
            )
        }

        // Configuration & Preset Bottom Sheet
        if (showSettingsSheet) {
            SettingsSheet(
                venueProfiles = venueProfiles,
                activeProfile = activeProfile,
                currentHapticType = hapticType,
                currentStrength = vibrationStrength,
                autoPocketStealth = autoPocketStealth,
                volumeKeyTare = volumeKeyTare,
                stealthMicroDot = stealthMicroDot,
                onSelectProfile = { viewModel.selectProfile(it) },
                onSaveProfile = { name, desc -> viewModel.saveCurrentAsProfile(name, desc) },
                onDeleteProfile = { viewModel.deleteProfile(it) },
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
