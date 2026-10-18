package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DetectionEventEntity
import com.example.data.local.GoblinDatabase
import com.example.data.local.GoblinPreferences
import com.example.data.local.GoblinRepository
import com.example.domain.model.CreatureState
import com.example.domain.model.MagneticReading
import com.example.domain.model.VibePattern
import com.example.haptics.DiscreetHapticEngine
import com.example.haptics.VibePlan
import com.example.haptics.VibeSpec
import com.example.sensor.MagneticSensorEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val isTrickRunning: StateFlow<Boolean>
    val liveMicroTesla: StateFlow<Int>
    val isSaturated: StateFlow<Boolean>

    // The two sliders and the strong-object check
    val sensitivity: StateFlow<Float>
    val phoneMovement: StateFlow<Int>
    val strongCheck: StateFlow<Boolean>
    val rangeSummary: StateFlow<String>

    // Screen Off Mode (Pitch Black Immersive Mode)
    private val _isScreenOffModeActive = MutableStateFlow(false)
    val isScreenOffModeActive: StateFlow<Boolean> = _isScreenOffModeActive.asStateFlow()

    // Visual Mode (tiny dot in the top-left corner)
    private val _isVisualModeEnabled = MutableStateFlow(true)
    val isVisualModeEnabled: StateFlow<Boolean> = _isVisualModeEnabled.asStateFlow()

    // Vibrate as well as show the dot (otherwise the dot is the only cue)
    private val _vibrationWithVisual = MutableStateFlow(true)
    val vibrationWithVisual: StateFlow<Boolean> = _vibrationWithVisual.asStateFlow()

    // Live state of the tiny dot
    private val _isVisualDotVisible = MutableStateFlow(false)
    val isVisualDotVisible: StateFlow<Boolean> = _isVisualDotVisible.asStateFlow()
    // true = strong/close hit (dot renders green), false = weak hit (renders yellow)
    private val _isVisualDotStrong = MutableStateFlow(false)
    val isVisualDotStrong: StateFlow<Boolean> = _isVisualDotStrong.asStateFlow()
    // Bumped on every change so a stale auto-hide timer can't turn the dot off after a
    // newer event already lit it.
    private var visualDotGeneration = 0

    // Dark Mode (matches the midnight & gold splash screen theme)
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // --- Vibration settings ---
    private val _vibePattern = MutableStateFlow(VibePattern.PULSES)
    val vibePattern: StateFlow<VibePattern> = _vibePattern.asStateFlow()
    private val _vibeCount = MutableStateFlow(2)
    val vibeCount: StateFlow<Int> = _vibeCount.asStateFlow()
    private val _vibeStrength = MutableStateFlow(80)
    val vibeStrength: StateFlow<Int> = _vibeStrength.asStateFlow()
    private val _vibeSpeed = MutableStateFlow(50)
    val vibeSpeed: StateFlow<Int> = _vibeSpeed.asStateFlow()
    private val _vibeLength = MutableStateFlow(40)
    val vibeLength: StateFlow<Int> = _vibeLength.asStateFlow()
    private val _secondPattern = MutableStateFlow(VibePattern.TICK)
    val secondPattern: StateFlow<VibePattern> = _secondPattern.asStateFlow()
    private val _secondCount = MutableStateFlow(2)
    val secondCount: StateFlow<Int> = _secondCount.asStateFlow()

    private fun mainSpec() = VibeSpec(
        _vibePattern.value, _vibeCount.value, _vibeStrength.value, _vibeSpeed.value, _vibeLength.value
    )

    // The second cue is a little softer than the main one so the two feel different.
    private fun secondSpec() = VibeSpec(
        _secondPattern.value, _secondCount.value,
        (_vibeStrength.value * 0.85f).roundToInt(), _vibeSpeed.value, _vibeLength.value
    )

    // --- Calibration (passthrough from the sensor engine) ---
    val isCalibratingObject: StateFlow<Boolean> get() = sensorEngine.isCalibratingObject
    val calibrationPhase: StateFlow<MagneticSensorEngine.CalibrationPhase> get() = sensorEngine.calibrationPhase
    val calibrationProgress: StateFlow<Float> get() = sensorEngine.calibrationProgress
    val objectCalibrationPeak: StateFlow<Float> get() = sensorEngine.objectCalibrationPeak

    private val _calibrationMessage = MutableStateFlow<String?>(null)
    val calibrationMessage: StateFlow<String?> = _calibrationMessage.asStateFlow()

    fun calibrateToCurrentObject() {
        _calibrationMessage.value = null
        sensorEngine.startObjectCalibration()
    }

    fun cancelCalibration() {
        sensorEngine.cancelObjectCalibration()
    }

    fun dismissCalibrationMessage() {
        _calibrationMessage.value = null
    }

    private val _currentTab = MutableStateFlow(PerformanceTab.SETTINGS)
    val currentTab: StateFlow<PerformanceTab> = _currentTab.asStateFlow()

    private val _volumeKeyTare = MutableStateFlow(true)
    val volumeKeyTare: StateFlow<Boolean> = _volumeKeyTare.asStateFlow()

    init {
        val database = GoblinDatabase.getInstance(application)
        repository = GoblinRepository(database.dao())
        sensorEngine = MagneticSensorEngine(application, viewModelScope)
        hapticEngine = DiscreetHapticEngine(application)
        preferences = GoblinPreferences(application)

        // Restore persisted settings
        sensorEngine.setTrickRunning(preferences.isTrickRunning)
        sensorEngine.setPower(preferences.isSensorPower)
        sensorEngine.setSensitivity(preferences.sensitivity)
        sensorEngine.setPhoneMovement(preferences.phoneMovement)
        sensorEngine.setStrongCheck(preferences.strongCheck)
        sensorEngine.setCalibratedLevelPeak(preferences.calibratedLevelPeak)

        _isVisualModeEnabled.value = preferences.isVisualModeEnabled
        _vibrationWithVisual.value = preferences.vibrationWithVisual
        _isDarkMode.value = preferences.isDarkMode
        _vibePattern.value = preferences.vibePattern
        _vibeCount.value = preferences.vibeCount
        _vibeStrength.value = preferences.vibeStrength
        _vibeSpeed.value = preferences.vibeSpeed
        _vibeLength.value = preferences.vibeLength
        _secondPattern.value = preferences.secondPattern
        _secondCount.value = preferences.secondCount

        readingState = sensorEngine.readingState
        creatureState = sensorEngine.creatureState
        isPoweredOn = sensorEngine.isPoweredOn
        isTrickRunning = sensorEngine.isTrickRunning
        liveMicroTesla = sensorEngine.liveMicroTesla
        isSaturated = sensorEngine.isSaturated
        sensitivity = sensorEngine.sensitivity
        phoneMovement = sensorEngine.phoneMovement
        strongCheck = sensorEngine.strongCheck
        rangeSummary = sensorEngine.rangeSummary

        // Strike events from the sensor engine
        viewModelScope.launch {
            sensorEngine.strikeEvents.collectLatest { reading ->
                val threshold = if (reading.thresholdUsed > 0f) reading.thresholdUsed
                else sensorEngine.calculateEffectiveThreshold()
                // A hit well above the trigger level is "strong/close": the dot turns green.
                val isStrongHit = threshold > 0f && reading.deltaMagnitude >= threshold * STRONG_HIT_FACTOR

                // 1. Light the dot (Screen Off Mode or Visual Mode)
                if (_isScreenOffModeActive.value || _isVisualModeEnabled.value) {
                    visualDotGeneration++
                    val myGeneration = visualDotGeneration
                    _isVisualDotStrong.value = isStrongHit
                    _isVisualDotVisible.value = true
                    launch {
                        delay(2200L)
                        if (visualDotGeneration == myGeneration) _isVisualDotVisible.value = false
                    }
                }

                // 2. Vibrate, unless the dot is the only cue
                val spec = mainSpec()
                if ((!_isVisualModeEnabled.value || _vibrationWithVisual.value) &&
                    spec.pattern != VibePattern.OFF
                ) {
                    // The sensor must ignore the motor for as long as it runs, or the
                    // vibration itself would read as a disturbance.
                    sensorEngine.notifyHapticPulse(VibePlan.durationMs(spec) + HAPTIC_MARGIN_MS)
                    hapticEngine.play(spec)
                }

                // 3. Log
                repository.logDetectionEvent(
                    DetectionEventEntity(
                        timestamp = reading.timestamp,
                        peakDelta = reading.deltaMagnitude,
                        dominantDirection = reading.dominantDirection,
                        fogLevel = 0f,
                        thresholdAtEvent = threshold,
                        durationMs = 250L
                    )
                )
            }
        }

        // Strong objects: the second cue says "the object is still close".
        viewModelScope.launch {
            sensorEngine.stillNearEvents.collectLatest {
                val spec = secondSpec()
                if ((!_isVisualModeEnabled.value || _vibrationWithVisual.value) &&
                    spec.pattern != VibePattern.OFF
                ) {
                    sensorEngine.notifyHapticPulse(VibePlan.durationMs(spec) + HAPTIC_MARGIN_MS)
                    hapticEngine.play(spec)
                }
            }
        }
        // ...and the dot stays lit for as long as it stays close.
        viewModelScope.launch {
            sensorEngine.isStillNear.collectLatest { near ->
                if (!(_isScreenOffModeActive.value || _isVisualModeEnabled.value)) return@collectLatest
                visualDotGeneration++
                val myGeneration = visualDotGeneration
                if (near) {
                    _isVisualDotStrong.value = true
                    _isVisualDotVisible.value = true
                } else {
                    delay(1200L)
                    if (visualDotGeneration == myGeneration) _isVisualDotVisible.value = false
                }
            }
        }

        // Calibration result: the engine has already applied the new slider values; save
        // them and show a short message.
        viewModelScope.launch {
            sensorEngine.calibrationEvents.collectLatest { result ->
                if (result.success) {
                    preferences.sensitivity = result.newSensitivity
                    preferences.strongCheck = result.newStrongCheck
                    if (result.newPhoneMovement >= 0) preferences.phoneMovement = result.newPhoneMovement
                    preferences.calibratedLevelPeak = result.levelPeak
                    val sb = StringBuilder("Calibrated. Sensitivity set to ${(result.newSensitivity * 100).roundToInt()}%")
                    sb.append(if (result.newStrongCheck) ", strong-object check on" else ", strong-object check off")
                    if (result.newPhoneMovement >= 0) {
                        sb.append(", Phone Movement to ${result.newPhoneMovement}%")
                    } else {
                        sb.append(". Phone Movement was left alone: the phone was barely moved in step 2")
                    }
                    sb.append(".")
                    if (!result.reachable) {
                        sb.append(" This object is very weak for this phone: keep it close to the phone.")
                    }
                    _calibrationMessage.value = sb.toString()
                } else {
                    _calibrationMessage.value =
                        "Didn't see a clear signal. In the last 4 seconds, bring the object right up to the phone and pull it away, then try again."
                }
            }
        }
    }

    // --- Switches and sliders ---

    fun setTrickRunning(running: Boolean) {
        sensorEngine.setTrickRunning(running)
        preferences.isTrickRunning = running
    }

    fun toggleTrick() {
        sensorEngine.toggleTrick()
        preferences.isTrickRunning = sensorEngine.isTrickRunning.value
    }

    fun setPower(on: Boolean) {
        sensorEngine.setPower(on)
        preferences.isSensorPower = on
    }

    fun togglePower() {
        sensorEngine.togglePower()
        preferences.isSensorPower = sensorEngine.isPoweredOn.value
    }

    fun setSensitivity(value: Float) {
        sensorEngine.setSensitivity(value)
        preferences.sensitivity = sensorEngine.sensitivity.value
    }

    fun setPhoneMovement(value: Int) {
        sensorEngine.setPhoneMovement(value)
        preferences.phoneMovement = sensorEngine.phoneMovement.value
    }

    fun setStrongCheck(on: Boolean) {
        sensorEngine.setStrongCheck(on)
        preferences.strongCheck = on
    }

    // --- Vibration ---

    fun setVibePattern(pattern: VibePattern) {
        _vibePattern.value = pattern
        preferences.vibePattern = pattern
    }

    fun setVibeCount(count: Int) {
        val v = count.coerceIn(1, 5)
        _vibeCount.value = v
        preferences.vibeCount = v
    }

    fun setVibeStrength(value: Int) {
        val v = value.coerceIn(0, 100)
        _vibeStrength.value = v
        preferences.vibeStrength = v
    }

    fun setVibeSpeed(value: Int) {
        val v = value.coerceIn(0, 100)
        _vibeSpeed.value = v
        preferences.vibeSpeed = v
    }

    fun setVibeLength(value: Int) {
        val v = value.coerceIn(0, 100)
        _vibeLength.value = v
        preferences.vibeLength = v
    }

    fun setSecondPattern(pattern: VibePattern) {
        _secondPattern.value = pattern
        preferences.secondPattern = pattern
    }

    fun setSecondCount(count: Int) {
        val v = count.coerceIn(1, 5)
        _secondCount.value = v
        preferences.secondCount = v
    }

    fun testMainVibration() {
        playTest(mainSpec())
    }

    fun testSecondVibration() {
        playTest(secondSpec())
    }

    private fun playTest(spec: VibeSpec) {
        if (spec.pattern == VibePattern.OFF) return
        sensorEngine.notifyHapticPulse(VibePlan.durationMs(spec) + HAPTIC_MARGIN_MS)
        hapticEngine.play(spec)
    }

    // --- Display modes ---

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

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        preferences.isDarkMode = enabled
    }

    fun setTab(tab: PerformanceTab) {
        _currentTab.value = tab
    }

    fun tareBaseline() {
        sensorEngine.tareBaseline()
    }

    fun setVolumeKeyTare(enabled: Boolean) {
        _volumeKeyTare.value = enabled
    }

    /**
     * A volume key was clicked: always clears a sensor saturation, and (when enabled)
     * re-centres the detector. Returns true when the key press was used.
     */
    fun onVolumeKeyTriggered(): Boolean {
        sensorEngine.clearSaturation()
        if (_volumeKeyTare.value) {
            tareBaseline()
            // A short confirmation tick; the sensor must ignore its own motor.
            val tick = VibeSpec(VibePattern.TICK, 1, _vibeStrength.value, _vibeSpeed.value, _vibeLength.value)
            sensorEngine.notifyHapticPulse(VibePlan.durationMs(tick) + HAPTIC_MARGIN_MS)
            hapticEngine.play(tick)
            return true
        }
        return false
    }

    override fun onCleared() {
        super.onCleared()
        hapticEngine.cancel()
        sensorEngine.stopListening()
    }

    private companion object {
        const val STRONG_HIT_FACTOR = 2.2f
        const val HAPTIC_MARGIN_MS = 250L
    }
}
