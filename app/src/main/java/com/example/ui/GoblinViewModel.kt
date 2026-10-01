package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DetectionEventEntity
import com.example.data.local.GoblinDatabase
import com.example.data.local.GoblinPreferences
import com.example.data.local.GoblinRepository
import com.example.data.local.VenueProfileEntity
import com.example.domain.model.CreatureState
import com.example.domain.model.HapticFeedbackType
import com.example.domain.model.LocatorMode
import com.example.domain.model.MagneticReading
import com.example.domain.model.RumbleMode
import com.example.domain.model.VibrationStrength
import com.example.haptics.DiscreetHapticEngine
import com.example.sensor.MagneticSensorEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PerformanceTab {
    SETTINGS,
    TUTORIAL
}

class GoblinViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GoblinRepository
    val sensorEngine: MagneticSensorEngine
    val hapticEngine: DiscreetHapticEngine
    val preferences: GoblinPreferences

    val readingState: StateFlow<MagneticReading>
    val creatureState: StateFlow<CreatureState>
    val isPoweredOn: StateFlow<Boolean>
    val locatorMode: StateFlow<LocatorMode>

    // Settings from Screenshot
    val isTrickRunning: StateFlow<Boolean>
    val sensitivity: StateFlow<Float>
    val adaptiveSensitivity: StateFlow<Int>
    val isSmartAlarmEnabled: StateFlow<Boolean>
    val smartAlarmThreshold: StateFlow<Float>
    val isSleepingMode: StateFlow<Boolean>
    val liveMicroTesla: StateFlow<Int>

    // Screen Off Mode (Pitch Black Immersive Mode)
    private val _isScreenOffModeActive = MutableStateFlow(false)
    val isScreenOffModeActive: StateFlow<Boolean> = _isScreenOffModeActive.asStateFlow()

    // Visual Mode (Tiny Green Dot in top-left corner) - default to true
    private val _isVisualModeEnabled = MutableStateFlow(true)
    val isVisualModeEnabled: StateFlow<Boolean> = _isVisualModeEnabled.asStateFlow()

    // Vibration with Visual Mode (use both vibration and visual or visual only)
    private val _vibrationWithVisual = MutableStateFlow(true)
    val vibrationWithVisual: StateFlow<Boolean> = _vibrationWithVisual.asStateFlow()

    // Live state of the tiny green dot
    private val _isVisualDotVisible = MutableStateFlow(false)
    val isVisualDotVisible: StateFlow<Boolean> = _isVisualDotVisible.asStateFlow()

    private val _isRoomWideMode = MutableStateFlow(true)
    val isRoomWideMode: StateFlow<Boolean> = _isRoomWideMode.asStateFlow()

    private val _currentTab = MutableStateFlow(PerformanceTab.SETTINGS)
    val currentTab: StateFlow<PerformanceTab> = _currentTab.asStateFlow()

    private val _fogLevel = MutableStateFlow(0.35f)
    val fogLevel: StateFlow<Float> = _fogLevel.asStateFlow()

    private val _customThresholdDelta = MutableStateFlow(1.2f)
    val customThresholdDelta: StateFlow<Float> = _customThresholdDelta.asStateFlow()

    private val _effectiveThreshold = MutableStateFlow(0.28f)
    val effectiveThreshold: StateFlow<Float> = _effectiveThreshold.asStateFlow()

    private val _hapticType = MutableStateFlow(HapticFeedbackType.DOUBLE_STRONG)
    val hapticType: StateFlow<HapticFeedbackType> = _hapticType.asStateFlow()

    private val _rumbleMode = MutableStateFlow(RumbleMode.STOMPS)
    val rumbleMode: StateFlow<RumbleMode> = _rumbleMode.asStateFlow()

    val targetMinStrengthPercent = MutableStateFlow(0)
    val targetMaxStrengthPercent = MutableStateFlow(100)

    private val _vibrationStrength = MutableStateFlow(VibrationStrength.MEDIUM)
    val vibrationStrength: StateFlow<VibrationStrength> = _vibrationStrength.asStateFlow()

    private val _isStealthActive = MutableStateFlow(false)
    val isStealthActive: StateFlow<Boolean> = _isStealthActive.asStateFlow()

    private val _stealthMicroDot = MutableStateFlow(true)
    val stealthMicroDot: StateFlow<Boolean> = _stealthMicroDot.asStateFlow()

    private val _autoPocketStealth = MutableStateFlow(true)
    val autoPocketStealth: StateFlow<Boolean> = _autoPocketStealth.asStateFlow()

    private val _volumeKeyTare = MutableStateFlow(true)
    val volumeKeyTare: StateFlow<Boolean> = _volumeKeyTare.asStateFlow()

    private val _activeProfile = MutableStateFlow<VenueProfileEntity?>(null)
    val activeProfile: StateFlow<VenueProfileEntity?> = _activeProfile.asStateFlow()

    val venueProfiles: StateFlow<List<VenueProfileEntity>>
    val recentEvents: StateFlow<List<DetectionEventEntity>>

    init {
        val database = GoblinDatabase.getInstance(application)
        repository = GoblinRepository(database.dao())
        sensorEngine = MagneticSensorEngine(application, viewModelScope)
        hapticEngine = DiscreetHapticEngine(application)
        preferences = GoblinPreferences(application)

        // Restore persisted user settings
        sensorEngine.setTrickRunning(preferences.isTrickRunning)
        sensorEngine.setPower(preferences.isSensorPower)
        sensorEngine.setSensitivity(preferences.sensitivity)
        sensorEngine.setAdaptiveSensitivity(preferences.adaptiveSensitivity)
        sensorEngine.setSmartAlarmEnabled(preferences.isSmartAlarmEnabled)
        sensorEngine.setSmartAlarmThreshold(preferences.smartAlarmThreshold)
        sensorEngine.setSleepingMode(preferences.isSleepingMode)
        sensorEngine.setLocatorMode(
            try {
                LocatorMode.valueOf(preferences.locatorModeName)
            } catch (e: Exception) {
                LocatorMode.PROXIMITY_50CM
            }
        )

        _isVisualModeEnabled.value = preferences.isVisualModeEnabled
        _vibrationWithVisual.value = preferences.vibrationWithVisual
        _hapticType.value = try {
            HapticFeedbackType.valueOf(preferences.hapticTypeName)
        } catch (e: Exception) {
            HapticFeedbackType.DOUBLE_STRONG
        }
        _vibrationStrength.value = try {
            VibrationStrength.valueOf(preferences.vibrationStrengthName)
        } catch (e: Exception) {
            VibrationStrength.MEDIUM
        }

        readingState = sensorEngine.readingState
        creatureState = sensorEngine.creatureState
        isPoweredOn = sensorEngine.isPoweredOn
        locatorMode = sensorEngine.locatorMode

        isTrickRunning = sensorEngine.isTrickRunning
        sensitivity = sensorEngine.sensitivity
        adaptiveSensitivity = sensorEngine.adaptiveSensitivity
        isSmartAlarmEnabled = sensorEngine.isSmartAlarmEnabled
        smartAlarmThreshold = sensorEngine.smartAlarmThreshold
        isSleepingMode = sensorEngine.isSleepingMode
        liveMicroTesla = sensorEngine.liveMicroTesla

        venueProfiles = repository.profiles.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        recentEvents = repository.recentEvents.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        // Listen for strike events from sensor engine
        viewModelScope.launch {
            sensorEngine.strikeEvents.collectLatest { reading ->
                // Tell the sensor to ignore its own vibration motor's interference
                // before firing it, so the pulse can't re-trigger another "strike".
                sensorEngine.notifyHapticPulse()

                // 1. If in Screen Off Mode OR Visual Mode is enabled, light up green dot
                if (_isScreenOffModeActive.value || _isVisualModeEnabled.value) {
                    _isVisualDotVisible.value = true
                    launch {
                        delay(2200L)
                        _isVisualDotVisible.value = false
                    }
                }

                // 2. Play phone vibration if Visual Mode is disabled OR vibrationWithVisual is active
                if (!_isVisualModeEnabled.value || _vibrationWithVisual.value) {
                    hapticEngine.playStrikeFeedback(reading.deltaMagnitude, reading.rateOfChange)
                }

                // Log detection event
                val event = DetectionEventEntity(
                    timestamp = reading.timestamp,
                    peakDelta = reading.deltaMagnitude,
                    dominantDirection = reading.dominantDirection,
                    fogLevel = _fogLevel.value,
                    thresholdAtEvent = sensorEngine.calculateEffectiveThreshold(),
                    durationMs = 250L
                )
                repository.logDetectionEvent(event)
            }
        }

        // Real-time observation during Screen Off Mode to guarantee visual dot cue
        viewModelScope.launch {
            sensorEngine.readingState.collectLatest { reading ->
                if (_isScreenOffModeActive.value) {
                    val threshold = sensorEngine.calculateEffectiveThreshold()
                    if (reading.deltaMagnitude >= threshold) {
                        if (_isVisualModeEnabled.value && !_isVisualDotVisible.value) {
                            _isVisualDotVisible.value = true
                            launch {
                                delay(2200L)
                                _isVisualDotVisible.value = false
                            }
                        }
                    }
                }
            }
        }

        // Pocket proximity observation
        viewModelScope.launch {
            sensorEngine.isPocketCovered.collectLatest { covered ->
                if (_autoPocketStealth.value) {
                    if (covered && !_isStealthActive.value) {
                        _isStealthActive.value = true
                    } else if (!covered && _isStealthActive.value) {
                        // Uncovered from pocket
                        _isStealthActive.value = false
                    }
                }
            }
        }

        // Update effective threshold on sensor changes
        viewModelScope.launch {
            readingState.collectLatest {
                _effectiveThreshold.value = sensorEngine.calculateEffectiveThreshold()
            }
        }

        // Smart Alarm saturation warning listener
        viewModelScope.launch {
            sensorEngine.smartAlarmEvents.collectLatest {
                hapticEngine.playSaturationAlarm()
            }
        }

        // Sleeping mode wake-up confirmation listener
        viewModelScope.launch {
            sensorEngine.sleepWakeEvents.collectLatest {
                hapticEngine.playWakeConfirmation()
            }
        }
    }

    fun setTrickRunning(running: Boolean) {
        sensorEngine.setTrickRunning(running)
        preferences.isTrickRunning = running
    }

    fun toggleTrick() {
        sensorEngine.toggleTrick()
        preferences.isTrickRunning = sensorEngine.isTrickRunning.value
    }

    fun setSensitivity(value: Float) {
        sensorEngine.setSensitivity(value)
        preferences.sensitivity = sensorEngine.sensitivity.value
    }

    fun setAdaptiveSensitivity(value: Int) {
        sensorEngine.setAdaptiveSensitivity(value)
        preferences.adaptiveSensitivity = value
    }

    fun setSmartAlarmEnabled(enabled: Boolean) {
        sensorEngine.setSmartAlarmEnabled(enabled)
        preferences.isSmartAlarmEnabled = enabled
    }

    fun setSmartAlarmThreshold(thresh: Float) {
        sensorEngine.setSmartAlarmThreshold(thresh)
        preferences.smartAlarmThreshold = thresh
    }

    fun setSleepingMode(sleeping: Boolean) {
        sensorEngine.setSleepingMode(sleeping)
        preferences.isSleepingMode = sleeping
    }

    fun toggleSleepingMode() {
        sensorEngine.toggleSleepingMode()
        preferences.isSleepingMode = sensorEngine.isSleepingMode.value
    }

    fun setScreenOffMode(active: Boolean) {
        _isScreenOffModeActive.value = active
        if (active) {
            sensorEngine.setPower(true)
            sensorEngine.setTrickRunning(true)
        }
    }

    fun setVisualModeEnabled(enabled: Boolean) {
        _isVisualModeEnabled.value = enabled
        preferences.isVisualModeEnabled = enabled
    }

    fun setVibrationWithVisual(enabled: Boolean) {
        _vibrationWithVisual.value = enabled
        preferences.vibrationWithVisual = enabled
    }

    fun setPower(on: Boolean) {
        sensorEngine.setPower(on)
        preferences.isSensorPower = on
    }

    fun togglePower() {
        sensorEngine.togglePower()
        preferences.isSensorPower = sensorEngine.isPoweredOn.value
    }

    fun setRoomWideMode(enabled: Boolean) {
        _isRoomWideMode.value = enabled
        sensorEngine.isRoomWideMode = enabled
        _effectiveThreshold.value = sensorEngine.calculateEffectiveThreshold()
    }

    fun toggleRoomWideMode() {
        setRoomWideMode(!_isRoomWideMode.value)
    }

    fun setLocatorMode(mode: LocatorMode) {
        sensorEngine.setLocatorMode(mode)
        preferences.locatorModeName = mode.name
    }

    fun toggleLocatorMode() {
        sensorEngine.toggleLocatorMode()
        preferences.locatorModeName = sensorEngine.locatorMode.value.name
    }

    fun setRumbleMode(mode: RumbleMode) {
        _rumbleMode.value = mode
        _hapticType.value = mode.hapticType
        hapticEngine.hapticType = mode.hapticType
        preferences.hapticTypeName = mode.hapticType.name
    }

    fun setTargetStrengthWindow(minPercent: Int, maxPercent: Int) {
        val minP = minPercent.coerceIn(0, 100)
        val maxP = maxPercent.coerceIn(minP, 100)
        targetMinStrengthPercent.value = minP
        targetMaxStrengthPercent.value = maxP
        sensorEngine.targetMinStrengthPercent = minP
        sensorEngine.targetMaxStrengthPercent = maxP
    }

    fun setTab(tab: PerformanceTab) {
        _currentTab.value = tab
    }

    fun setFogLevel(fog: Float) {
        val clamped = fog.coerceIn(0.02f, 1.0f)
        _fogLevel.value = clamped
        sensorEngine.fogLevel = clamped
        _effectiveThreshold.value = sensorEngine.calculateEffectiveThreshold()
    }

    fun setCustomThreshold(thresh: Float) {
        val clamped = thresh.coerceIn(0.8f, 20.0f)
        _customThresholdDelta.value = clamped
        sensorEngine.customBaseThreshold = clamped
        _effectiveThreshold.value = sensorEngine.calculateEffectiveThreshold()
    }

    fun tareBaseline() {
        sensorEngine.tareBaseline()
    }

    fun setHapticType(type: HapticFeedbackType) {
        _hapticType.value = type
        hapticEngine.hapticType = type
        preferences.hapticTypeName = type.name
    }

    fun setVibrationStrength(strength: VibrationStrength) {
        _vibrationStrength.value = strength
        hapticEngine.strength = strength
        preferences.vibrationStrengthName = strength.name
    }

    fun setStealth(active: Boolean) {
        _isStealthActive.value = active
    }

    fun toggleStealth() {
        _isStealthActive.value = !_isStealthActive.value
    }

    fun setStealthMicroDot(show: Boolean) {
        _stealthMicroDot.value = show
    }

    fun setAutoPocketStealth(enabled: Boolean) {
        _autoPocketStealth.value = enabled
    }

    fun setVolumeKeyTare(enabled: Boolean) {
        _volumeKeyTare.value = enabled
    }

    fun selectProfile(profile: VenueProfileEntity) {
        _activeProfile.value = profile
        setFogLevel(profile.fogLevel)
        setCustomThreshold(profile.customThresholdDelta)
        setHapticType(profile.hapticType)
        setVibrationStrength(profile.vibrationStrength)
        sensorEngine.debounceMs = profile.debounceMs
        sensorEngine.tareBaseline()
    }

    fun saveCurrentAsProfile(name: String, description: String) {
        viewModelScope.launch {
            val entity = VenueProfileEntity(
                name = name.ifBlank { "Custom Venue ${System.currentTimeMillis() % 1000}" },
                description = description.ifBlank { "Custom configured magnetic sensitivity" },
                fogLevel = _fogLevel.value,
                customThresholdDelta = _customThresholdDelta.value,
                hapticType = _hapticType.value,
                vibrationStrength = _vibrationStrength.value,
                debounceMs = sensorEngine.debounceMs,
                isBuiltIn = false
            )
            repository.saveProfile(entity)
        }
    }

    fun deleteProfile(profile: VenueProfileEntity) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            if (_activeProfile.value?.id == profile.id) {
                _activeProfile.value = null
            }
        }
    }

    fun testHaptic() {
        hapticEngine.testFeedback(_hapticType.value)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearEvents()
        }
    }

    fun onVolumeKeyTriggered(): Boolean {
        if (_volumeKeyTare.value) {
            tareBaseline()
            hapticEngine.testFeedback(HapticFeedbackType.GHOST_TAP)
            return true
        }
        return false
    }

    override fun onCleared() {
        super.onCleared()
        sensorEngine.stopListening()
    }
}
