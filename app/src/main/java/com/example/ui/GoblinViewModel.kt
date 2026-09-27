package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DetectionEventEntity
import com.example.data.local.GoblinDatabase
import com.example.data.local.GoblinRepository
import com.example.data.local.VenueProfileEntity
import com.example.domain.model.CreatureState
import com.example.domain.model.HapticFeedbackType
import com.example.domain.model.LocatorMode
import com.example.domain.model.MagneticReading
import com.example.domain.model.VibrationStrength
import com.example.haptics.DiscreetHapticEngine
import com.example.notification.DetectionNotificationManager
import com.example.sensor.MagneticSensorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PerformanceTab {
    CREATURE,
    MENTALIST_HUD,
    LOG_REHEARSAL
}

class GoblinViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GoblinRepository
    val sensorEngine: MagneticSensorEngine
    val hapticEngine: DiscreetHapticEngine
    val notificationManager: DetectionNotificationManager

    val readingState: StateFlow<MagneticReading>
    val creatureState: StateFlow<CreatureState>
    val isPoweredOn: StateFlow<Boolean>
    val locatorMode: StateFlow<LocatorMode>

    private val _isRoomWideMode = MutableStateFlow(true)
    val isRoomWideMode: StateFlow<Boolean> = _isRoomWideMode.asStateFlow()

    private val _currentTab = MutableStateFlow(PerformanceTab.CREATURE)
    val currentTab: StateFlow<PerformanceTab> = _currentTab.asStateFlow()

    private val _fogLevel = MutableStateFlow(0.35f)
    val fogLevel: StateFlow<Float> = _fogLevel.asStateFlow()

    private val _customThresholdDelta = MutableStateFlow(1.2f)
    val customThresholdDelta: StateFlow<Float> = _customThresholdDelta.asStateFlow()

    private val _effectiveThreshold = MutableStateFlow(0.28f)
    val effectiveThreshold: StateFlow<Float> = _effectiveThreshold.asStateFlow()

    private val _hapticType = MutableStateFlow(HapticFeedbackType.DOUBLE_STRONG)
    val hapticType: StateFlow<HapticFeedbackType> = _hapticType.asStateFlow()

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
        notificationManager = DetectionNotificationManager(application)

        readingState = sensorEngine.readingState
        creatureState = sensorEngine.creatureState
        isPoweredOn = sensorEngine.isPoweredOn
        locatorMode = sensorEngine.locatorMode

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

                // Play 2 strong vibrations (or configured haptic sensation)
                hapticEngine.playStrikeFeedback(reading.deltaMagnitude, reading.rateOfChange)

                // Send immediate local notification with Hand Arc context
                notificationManager.sendImmediateDetectionNotification(
                    deltaMagnitude = reading.deltaMagnitude,
                    dominantDirection = reading.dominantDirection,
                    isRoomWide = _isRoomWideMode.value,
                    isHandArc = reading.isHandArcDetected || (sensorEngine.locatorMode.value == LocatorMode.WHICH_HAND_ARC)
                )

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
    }

    fun setPower(on: Boolean) {
        sensorEngine.setPower(on)
    }

    fun togglePower() {
        sensorEngine.togglePower()
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
    }

    fun toggleLocatorMode() {
        sensorEngine.toggleLocatorMode()
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
    }

    fun setVibrationStrength(strength: VibrationStrength) {
        _vibrationStrength.value = strength
        hapticEngine.strength = strength
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
