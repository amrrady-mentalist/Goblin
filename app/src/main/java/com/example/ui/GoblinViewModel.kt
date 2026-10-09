package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DetectionEventEntity
import com.example.data.local.GoblinDatabase
import com.example.data.local.GoblinPreferences
import com.example.data.local.GoblinRepository
import com.example.domain.model.CreatureState
import com.example.domain.model.HapticFeedbackType
import com.example.domain.model.LocatorMode
import com.example.domain.model.MagneticReading
import com.example.domain.model.UtBaselinePattern
import com.example.domain.model.UtPeakPattern
import com.example.domain.model.UtTriggerTier
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

    // Live state of the tiny stealth dot
    private val _isVisualDotVisible = MutableStateFlow(false)
    val isVisualDotVisible: StateFlow<Boolean> = _isVisualDotVisible.asStateFlow()
    // true = strong/close hit (dot renders green), false = weak hit (renders yellow)
    private val _isVisualDotStrong = MutableStateFlow(false)
    val isVisualDotStrong: StateFlow<Boolean> = _isVisualDotStrong.asStateFlow()
    // Bumped on every strike so a stale auto-hide timer from an earlier strike
    // can't turn the dot off after a newer strike already re-lit it.
    private var visualDotGeneration = 0

    // Two-tier haptic feel for a single confirmed detection: "Detected" plays when
    // the object just cleared threshold, "Strong/Close" plays when it cleared it by
    // a wide margin. Both now key off the one relative detector below, not a
    // separate absolute-µT trigger.
    private val _isUtTriggerEnabled = MutableStateFlow(true)
    val isUtTriggerEnabled: StateFlow<Boolean> = _isUtTriggerEnabled.asStateFlow()

    private val _utBaselinePattern = MutableStateFlow(UtBaselinePattern.SINGLE_PULSE)
    val utBaselinePattern: StateFlow<UtBaselinePattern> = _utBaselinePattern.asStateFlow()

    private val _utPeakPattern = MutableStateFlow(UtPeakPattern.CONTINUOUS)
    val utPeakPattern: StateFlow<UtPeakPattern> = _utPeakPattern.asStateFlow()

    private val _activeUtTier = MutableStateFlow(UtTriggerTier.IDLE)
    val activeUtTier: StateFlow<UtTriggerTier> = _activeUtTier.asStateFlow()

    // A detection whose disturbance clears threshold by this multiple counts as
    // "strong/close" and plays the Peak pattern (and green dot) instead of the
    // Detected pattern (yellow dot).
    private val _strongHitMultiplier = MutableStateFlow(2.2f)
    val strongHitMultiplier: StateFlow<Float> = _strongHitMultiplier.asStateFlow()

    fun setStrongHitMultiplier(value: Float) {
        val clamped = value.coerceIn(1.2f, 4.0f)
        _strongHitMultiplier.value = clamped
        preferences.strongHitMultiplier = clamped
    }

    private var activeUtTierGeneration = 0
    private var continuousStopGeneration = 0

    // Manual per-object calibration (passthrough from the sensor engine)
    val isCalibratingObject: StateFlow<Boolean> get() = sensorEngine.isCalibratingObject
    val objectCalibrationPeak: StateFlow<Float> get() = sensorEngine.objectCalibrationPeak

    private val _calibrationMessage = MutableStateFlow<String?>(null)
    val calibrationMessage: StateFlow<String?> = _calibrationMessage.asStateFlow()

    fun calibrateToCurrentObject() {
        sensorEngine.startObjectCalibration()
    }

    fun dismissCalibrationMessage() {
        _calibrationMessage.value = null
    }

    private val _currentTab = MutableStateFlow(PerformanceTab.SETTINGS)
    val currentTab: StateFlow<PerformanceTab> = _currentTab.asStateFlow()

    private val _hapticType = MutableStateFlow(HapticFeedbackType.DOUBLE_STRONG)
    val hapticType: StateFlow<HapticFeedbackType> = _hapticType.asStateFlow()

    private val _vibrationStrength = MutableStateFlow(VibrationStrength.MEDIUM)
    val vibrationStrength: StateFlow<VibrationStrength> = _vibrationStrength.asStateFlow()

    private val _isStealthActive = MutableStateFlow(false)
    val isStealthActive: StateFlow<Boolean> = _isStealthActive.asStateFlow()

    private val _autoPocketStealth = MutableStateFlow(true)
    val autoPocketStealth: StateFlow<Boolean> = _autoPocketStealth.asStateFlow()

    private val _volumeKeyTare = MutableStateFlow(true)
    val volumeKeyTare: StateFlow<Boolean> = _volumeKeyTare.asStateFlow()

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
        _isUtTriggerEnabled.value = preferences.isUtTriggerEnabled
        _strongHitMultiplier.value = preferences.strongHitMultiplier
        _utBaselinePattern.value = preferences.utBaselinePattern
        _utPeakPattern.value = preferences.utPeakPattern
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
        // The flows above only drive the UI -- the haptic engine itself has to be
        // handed the saved choices too, or it plays its defaults after every restart.
        hapticEngine.hapticType = _hapticType.value
        hapticEngine.strength = _vibrationStrength.value

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

        // Listen for strike events from sensor engine
        viewModelScope.launch {
            sensorEngine.strikeEvents.collectLatest { reading ->
                // Tell the sensor to ignore its own vibration motor's interference
                // before firing it, so the pulse can't re-trigger another "strike".
                sensorEngine.notifyHapticPulse()

                // One detector, classified once per strike: a hit that only just
                // cleared threshold is "weak"; one that cleared it by a wide margin
                // (a strong magnet, or the object passing very close) is "strong".
                // Drives both the dot color and which haptic pattern plays.
                val threshold = sensorEngine.calculateEffectiveThreshold()
                val isStrongHit = threshold > 0f && reading.deltaMagnitude >= threshold * _strongHitMultiplier.value

                // 1. If in Screen Off Mode OR Visual Mode is enabled, light up the dot
                if (_isScreenOffModeActive.value || _isVisualModeEnabled.value) {
                    visualDotGeneration++
                    val myGeneration = visualDotGeneration
                    _isVisualDotStrong.value = isStrongHit
                    _isVisualDotVisible.value = true
                    launch {
                        delay(2200L)
                        // Only turn it off if no newer strike has re-lit it since
                        if (visualDotGeneration == myGeneration) {
                            _isVisualDotVisible.value = false
                        }
                    }
                }

                // 2. Play phone vibration if Visual Mode is disabled OR vibrationWithVisual is active
                if (!_isVisualModeEnabled.value || _vibrationWithVisual.value) {
                    // Any earlier continuous buzz must be fully cancelled (and its flag
                    // reset) before a new pattern plays, otherwise a leftover "running"
                    // flag can make the next strong hit silently do nothing.
                    // (A new strong hit while one is still running just extends it.)
                    if (!(isStrongHit && _utPeakPattern.value == UtPeakPattern.CONTINUOUS &&
                            _isUtTriggerEnabled.value)) {
                        hapticEngine.stopContinuousVibration()
                    }
                    // Keep the magnetometer ignoring the motor for as long as this
                    // pattern actually vibrates, not just the default 450 ms.
                    val isLongContinuous = isStrongHit && _isUtTriggerEnabled.value &&
                        _utPeakPattern.value == UtPeakPattern.CONTINUOUS
                    sensorEngine.notifyHapticPulse(
                        when {
                            isLongContinuous -> 1500L
                            isStrongHit -> 700L
                            else -> 450L
                        }
                    )
                    if (_isUtTriggerEnabled.value) {
                        // Flash the live badge to show which pattern just fired
                        activeUtTierGeneration++
                        val myTierGeneration = activeUtTierGeneration
                        _activeUtTier.value = if (isStrongHit) UtTriggerTier.PEAK_ACTIVE else UtTriggerTier.BASELINE_ACTIVE
                        launch {
                            delay(1200L)
                            if (activeUtTierGeneration == myTierGeneration) {
                                _activeUtTier.value = UtTriggerTier.IDLE
                            }
                        }

                        if (isStrongHit) {
                            hapticEngine.playUtPeakPattern(_utPeakPattern.value)
                            if (_utPeakPattern.value == UtPeakPattern.CONTINUOUS) {
                                // Launched on viewModelScope, not inside this collectLatest
                                // block: a newer strike cancels that block, which used to
                                // cancel this stop timer and leave the buzz "running".
                                continuousStopGeneration++
                                val myStopGeneration = continuousStopGeneration
                                viewModelScope.launch {
                                    delay(1200L)
                                    if (continuousStopGeneration == myStopGeneration) {
                                        hapticEngine.stopContinuousVibration()
                                    }
                                }
                            }
                        } else {
                            hapticEngine.playUtBaselinePattern(_utBaselinePattern.value)
                        }
                    } else {
                        hapticEngine.playStrikeFeedback(reading.deltaMagnitude, reading.rateOfChange)
                    }
                }

                // Log detection event
                val event = DetectionEventEntity(
                    timestamp = reading.timestamp,
                    peakDelta = reading.deltaMagnitude,
                    dominantDirection = reading.dominantDirection,
                    fogLevel = 0f,
                    thresholdAtEvent = sensorEngine.calculateEffectiveThreshold(),
                    durationMs = 250L
                )
                repository.logDetectionEvent(event)
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

        // Smart Alarm saturation warning listener
        viewModelScope.launch {
            sensorEngine.smartAlarmEvents.collectLatest {
                sensorEngine.notifyHapticPulse(700L)
                hapticEngine.playSaturationAlarm()
            }
        }

        // Sleeping mode wake-up confirmation listener
        viewModelScope.launch {
            sensorEngine.sleepWakeEvents.collectLatest {
                sensorEngine.notifyHapticPulse(700L)
                hapticEngine.playWakeConfirmation()
            }
        }

        // Manual object calibration result: apply the suggested sensitivity and
        // surface a one-line result for the Settings screen to show.
        viewModelScope.launch {
            sensorEngine.calibrationEvents.collectLatest { result ->
                if (result.success) {
                    setSensitivity(result.suggestedSensitivity)
                    _calibrationMessage.value =
                        "Calibrated — peak ${"%.2f".format(result.peakDisturbance)}µT over " +
                        "${"%.2f".format(result.noiseFloorAtCapture)}µT room noise. " +
                        "Sensitivity set to ${result.suggestedSensitivity}."
                } else {
                    _calibrationMessage.value =
                        "Didn't see a clear signal — bring the object closer during the next pass and try again."
                }
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

    fun setLocatorMode(mode: LocatorMode) {
        sensorEngine.setLocatorMode(mode)
        preferences.locatorModeName = mode.name
    }

    fun toggleLocatorMode() {
        sensorEngine.toggleLocatorMode()
        preferences.locatorModeName = sensorEngine.locatorMode.value.name
    }

    fun setTab(tab: PerformanceTab) {
        _currentTab.value = tab
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

    fun setAutoPocketStealth(enabled: Boolean) {
        _autoPocketStealth.value = enabled
    }

    fun setVolumeKeyTare(enabled: Boolean) {
        _volumeKeyTare.value = enabled
    }

    fun testHaptic() {
        hapticEngine.testFeedback(_hapticType.value)
    }

    // --- µT Trigger Control Methods ---
    fun setUtTriggerEnabled(enabled: Boolean) {
        _isUtTriggerEnabled.value = enabled
        preferences.isUtTriggerEnabled = enabled
        if (!enabled) {
            hapticEngine.stopContinuousVibration()
            _activeUtTier.value = UtTriggerTier.IDLE
        }
    }

    fun setUtBaselinePattern(pattern: UtBaselinePattern) {
        _utBaselinePattern.value = pattern
        preferences.utBaselinePattern = pattern
    }

    fun setUtPeakPattern(pattern: UtPeakPattern) {
        _utPeakPattern.value = pattern
        preferences.utPeakPattern = pattern
        if (_activeUtTier.value == UtTriggerTier.PEAK_ACTIVE) {
            hapticEngine.stopContinuousVibration()
            hapticEngine.playUtPeakPattern(pattern)
        }
    }

    fun testUtBaselinePattern() {
        hapticEngine.playUtBaselinePattern(_utBaselinePattern.value)
    }

    fun testUtPeakPattern() {
        hapticEngine.playUtPeakPattern(_utPeakPattern.value)
        if (_utPeakPattern.value == UtPeakPattern.CONTINUOUS) {
            viewModelScope.launch {
                delay(2000L)
                hapticEngine.stopContinuousVibration()
            }
        }
    }

    fun stopTestingUt() {
        hapticEngine.stopContinuousVibration()
    }

    fun onVolumeKeyTriggered(): Boolean {
        if (_volumeKeyTare.value) {
            tareBaseline()
            // The confirmation tick comes right after the baseline reset, so the
            // magnetometer must ignore the motor or it reads as a disturbance.
            sensorEngine.notifyHapticPulse(450L)
            hapticEngine.testFeedback(HapticFeedbackType.GHOST_TAP)
            return true
        }
        return false
    }

    override fun onCleared() {
        super.onCleared()
        hapticEngine.stopContinuousVibration()
        sensorEngine.stopListening()
    }
}
