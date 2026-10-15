package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.domain.model.CreatureState
import com.example.domain.model.GesturePhase
import com.example.domain.model.LocatorMode
import com.example.domain.model.MagneticReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Android side of the detector: registers the sensors, feeds them to [FieldDetector]
 * and turns what comes back into the flows the rest of the app uses.
 *
 * All of the detection maths lives in [FieldDetector] (pure Kotlin, unit-testable).
 * This class owns only the Android plumbing, the settings flows and the events.
 *
 * Sensor rates are the same as V1.
 */
class MagneticSensorEngine(
    private val context: Context,
    private val externalScope: CoroutineScope
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val magnetometer: Sensor? = sensorManager?.let { sm ->
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
    }
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    // Game rotation vector: orientation from gyro + accelerometer only (never the
    // magnetometer). Used to express the field in a gravity-anchored frame, which the
    // Up/Down preset needs to tell "vertical" from "sideways" whatever way the phone
    // is held.
    private val gameRotationVector: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotationMatrix = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )
    private var hasRotationSample = false

    // The detection maths. Touched only from the sensor thread (its settings are volatile).
    private val detector = FieldDetector()

    // Power State Flow (On / Off - "Activate sensor")
    private val _isPoweredOn = MutableStateFlow(true)
    val isPoweredOn: StateFlow<Boolean> = _isPoweredOn.asStateFlow()

    // Start Trick (Running state)
    private val _isTrickRunning = MutableStateFlow(true)
    val isTrickRunning: StateFlow<Boolean> = _isTrickRunning.asStateFlow()

    fun setTrickRunning(running: Boolean) {
        if (!_isPoweredOn.value && running) {
            setPower(true)
        }
        _isTrickRunning.value = running
    }

    fun toggleTrick() {
        setTrickRunning(!_isTrickRunning.value)
    }

    // Sensitivity (default 1.9, step 0.1, range 0.5 to 10.0): how small a change in the
    // field counts as a detection when the phone is still.
    private val _sensitivity = MutableStateFlow(1.9f)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    fun setSensitivity(value: Float) {
        val v = ((value * 10f).toInt() / 10f).coerceIn(0.5f, 10f)
        _sensitivity.value = v
        detector.sensitivity = v
    }

    // Adaptive Sensitivity (default 8, range 1 to 20): how much phone movement is
    // forgiven. Higher = the detector stays sharper while the phone moves (more
    // sensitive, more risk of a false alarm); lower = it forgives more movement.
    private val _adaptiveSensitivity = MutableStateFlow(8)
    val adaptiveSensitivity: StateFlow<Int> = _adaptiveSensitivity.asStateFlow()

    fun setAdaptiveSensitivity(value: Int) {
        val v = value.coerceIn(1, 20)
        _adaptiveSensitivity.value = v
        detector.adaptiveSensitivity = v
    }

    // Smart Alarm (Saturation / dead sensor warning, default enabled, threshold 150 uT)
    private val _isSmartAlarmEnabled = MutableStateFlow(true)
    val isSmartAlarmEnabled: StateFlow<Boolean> = _isSmartAlarmEnabled.asStateFlow()

    private val _smartAlarmThreshold = MutableStateFlow(150f)
    val smartAlarmThreshold: StateFlow<Float> = _smartAlarmThreshold.asStateFlow()

    fun setSmartAlarmEnabled(enabled: Boolean) {
        _isSmartAlarmEnabled.value = enabled
    }

    fun setSmartAlarmThreshold(thresh: Float) {
        _smartAlarmThreshold.value = thresh.coerceIn(50f, 600f)
    }

    // Sleeping Mode (Covert magnet wakeup)
    private val _isSleepingMode = MutableStateFlow(false)
    val isSleepingMode: StateFlow<Boolean> = _isSleepingMode.asStateFlow()

    fun setSleepingMode(sleeping: Boolean) {
        _isSleepingMode.value = sleeping
    }

    fun toggleSleepingMode() {
        _isSleepingMode.value = !_isSleepingMode.value
    }

    // Live MicroTesla readout (e.g. 32 uT)
    private val _liveMicroTesla = MutableStateFlow(32)
    val liveMicroTesla: StateFlow<Int> = _liveMicroTesla.asStateFlow()

    // Event flows for Smart Alarm & Sleeping Wake
    private val _smartAlarmEvents = MutableSharedFlow<Float>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val smartAlarmEvents: SharedFlow<Float> = _smartAlarmEvents.asSharedFlow()

    private val _sleepWakeEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sleepWakeEvents: SharedFlow<Unit> = _sleepWakeEvents.asSharedFlow()

    private var lastSmartAlarmTimeMs = 0L
    private var zeroSinceMs = 0L

    // Current State Flow
    private val _readingState = MutableStateFlow(MagneticReading())
    val readingState: StateFlow<MagneticReading> = _readingState.asStateFlow()

    private val _creatureState = MutableStateFlow(CreatureState.CALIBRATING)
    val creatureState: StateFlow<CreatureState> = _creatureState.asStateFlow()

    // Strike Event Trigger Flow for Haptics & Logging
    private val _strikeEvents = MutableSharedFlow<MagneticReading>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val strikeEvents: SharedFlow<MagneticReading> = _strikeEvents.asSharedFlow()

    // Strong Magnet: "the object is still close" confirmation.
    // [isStillNear] is true from the moment it is confirmed until the object leaves or
    // the phone is moved; [stillNearEvents] fires once at the moment of confirmation.
    private val _isStillNear = MutableStateFlow(false)
    val isStillNear: StateFlow<Boolean> = _isStillNear.asStateFlow()

    private val _stillNearEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val stillNearEvents: SharedFlow<Unit> = _stillNearEvents.asSharedFlow()

    // Pocket proximity detection state
    private val _isPocketCovered = MutableStateFlow(false)
    val isPocketCovered: StateFlow<Boolean> = _isPocketCovered.asStateFlow()

    // Detection preset
    private val _locatorMode = MutableStateFlow(LocatorMode.WEAK_MAGNET)
    val locatorMode: StateFlow<LocatorMode> = _locatorMode.asStateFlow()

    fun setLocatorMode(mode: LocatorMode) {
        _locatorMode.value = mode
        detector.mode = mode
    }

    fun toggleLocatorMode() {
        val all = LocatorMode.values()
        setLocatorMode(all[(_locatorMode.value.ordinal + 1) % all.size])
    }

    /** The stored level shift of the calibrated object (0 = never calibrated). */
    fun setCalibratedLevelPeak(value: Float) {
        detector.calibratedLevelPeak = value.coerceAtLeast(0f)
    }

    /** False when Strong Magnet is selected but the calibrated object is too weak for it. */
    val isConfirmationUsable: Boolean get() = detector.isConfirmationUsable()

    // Anti-Ghosting Haptic Motor Blanking (elapsedRealtime milliseconds)
    @Volatile private var hapticPulseBlankUntilMs = 0L
    private var wasBlanked = false

    fun notifyHapticPulse(durationMs: Long = 450L) {
        hapticPulseBlankUntilMs = SystemClock.elapsedRealtime() + durationMs
    }

    // Dedicated background thread for sensor processing
    private var sensorThread: HandlerThread? = null
    @Volatile private var sensorHandler: Handler? = null
    private val lifecycleLock = Any()

    val isSensorAvailable: Boolean get() = magnetometer != null

    private var lastUiEmitTimeMs = 0L

    // Manual object calibration: capture the peak disturbance (and how far the field LEVEL
    // moves) from a short, deliberate pass of the actual object being used tonight.
    private val _isCalibratingObject = MutableStateFlow(false)
    val isCalibratingObject: StateFlow<Boolean> = _isCalibratingObject.asStateFlow()

    private val _objectCalibrationPeak = MutableStateFlow(0f)
    val objectCalibrationPeak: StateFlow<Float> = _objectCalibrationPeak.asStateFlow()

    data class ObjectCalibrationResult(
        val success: Boolean,
        val peakDisturbance: Float,
        val noiseFloorAtCapture: Float,
        val suggestedSensitivity: Float,
        /** How far the field level moved at its most (µT). Drives the Strong Magnet check. */
        val levelPeak: Float = 0f,
        /** False when this object is too weak for Strong Magnet's "still near" check. */
        val confirmationUsable: Boolean = true
    )
    private val _calibrationEvents = MutableSharedFlow<ObjectCalibrationResult>(
        replay = 0,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val calibrationEvents: SharedFlow<ObjectCalibrationResult> = _calibrationEvents.asSharedFlow()

    private var calibCaptureEndTimeMs = 0L
    private var calibCapturedPeak = 0f

    /**
     * Starts a short capture window. Caller should be moving the real object through
     * the intended detection range for the full [durationMs] while this runs.
     */
    fun startObjectCalibration(durationMs: Long = 3000L) {
        runOnSensorThread {
            calibCaptureEndTimeMs = SystemClock.elapsedRealtime() + durationMs
            calibCapturedPeak = 0f
            detector.beginLevelCapture()
            _objectCalibrationPeak.value = 0f
            _isCalibratingObject.value = true
        }
    }

    private fun finishObjectCalibration() {
        _isCalibratingObject.value = false
        val peak = calibCapturedPeak
        val levelPeak = detector.endLevelCapture()
        val noise = detector.noiseFloor
        // A real object pass should stand well clear of the room's own noise.
        val success = peak > max(noise * 1.6f, 0.3f)
        val suggested = if (success) {
            // Threshold roughly between the room noise and the object's peak.
            val targetThreshold = max(noise * 1.8f, peak * 0.45f)
            ((3.8f / targetThreshold).coerceIn(0.5f, 10.0f) * 10f).toInt() / 10f
        } else {
            _sensitivity.value
        }
        if (success) detector.calibratedLevelPeak = levelPeak
        val usable = !success || levelPeak >= FieldDetector.USABLE_LEVEL_PEAK
        externalScope.launch(Dispatchers.Default) {
            _calibrationEvents.emit(
                ObjectCalibrationResult(
                    success = success,
                    peakDisturbance = peak,
                    noiseFloorAtCapture = noise,
                    suggestedSensitivity = suggested,
                    levelPeak = if (success) levelPeak else 0f,
                    confirmationUsable = usable
                )
            )
        }
    }

    // Configuration parameters
    @Volatile var debounceMs: Long = 400L

    init {
        startListening()
        startRoomCalibration()
    }

    fun setPower(enabled: Boolean) {
        if (_isPoweredOn.value == enabled) return
        _isPoweredOn.value = enabled
        if (enabled) {
            startListening()
            startRoomCalibration()
        } else {
            stopListening()
            _isCalibratingObject.value = false
            _isStillNear.value = false
            _creatureState.value = CreatureState.DORMANT
            _readingState.value = _readingState.value.copy(
                deltaMagnitude = 0f,
                rateOfChange = 0f,
                isPhoneMoving = false,
                isRoomAttuned = false,
                isStillNear = false
            )
        }
    }

    fun togglePower() {
        setPower(!_isPoweredOn.value)
    }

    fun startListening() {
        synchronized(lifecycleLock) {
            if (!_isPoweredOn.value) return
            if (sensorThread == null) {
                val thread = HandlerThread("MagneticSensorEngineThread")
                thread.start()
                sensorThread = thread
                sensorHandler = Handler(thread.looper)
            }
            val handler = sensorHandler
            // Same rates as V1: magnetometer as fast as possible, motion sensors at GAME.
            registerSensorSafely(magnetometer, SensorManager.SENSOR_DELAY_FASTEST, handler)
            registerSensorSafely(accelerometer, SensorManager.SENSOR_DELAY_GAME, handler)
            registerSensorSafely(gyroscope, SensorManager.SENSOR_DELAY_GAME, handler)
            registerSensorSafely(gameRotationVector, SensorManager.SENSOR_DELAY_GAME, handler)
            registerSensorSafely(proximitySensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
        }
    }

    private fun registerSensorSafely(sensor: Sensor?, samplingPeriodUs: Int, handler: Handler?) {
        if (sensor == null || sensorManager == null) return
        try {
            sensorManager.registerListener(this, sensor, samplingPeriodUs, handler)
        } catch (secEx: SecurityException) {
            Log.w(TAG, "Sampling rate restricted for ${sensor.name}, falling back to GAME delay", secEx)
            try {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, handler)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback sensor registration failed for ${sensor.name}", fallbackEx)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not register sensor ${sensor.name}", e)
        }
    }

    fun stopListening() {
        synchronized(lifecycleLock) {
            try {
                sensorManager?.unregisterListener(this)
            } catch (_: Exception) {}
            try {
                sensorThread?.quitSafely()
            } catch (_: Exception) {}
            sensorThread = null
            sensorHandler = null
            hasRotationSample = false
        }
    }

    /** Re-learns the room: needs about a second of the phone being still. */
    fun startRoomCalibration() {
        runOnSensorThread {
            detector.startAttunement()
            _isStillNear.value = false
            _creatureState.value = CreatureState.CALIBRATING
        }
    }

    /**
     * All detector state is confined to the sensor thread. Callers on other threads post
     * their changes here. With no sensor thread running there is nothing to change (the
     * sensors are off), so the request is dropped -- starting the sensors always begins
     * with a fresh room attunement.
     */
    private fun runOnSensorThread(block: () -> Unit) {
        val handler = sensorHandler ?: return
        if (Looper.myLooper() == handler.looper) block() else handler.post(block)
    }

    /** Instantly re-centres on the current field (volume-key tare). */
    fun tareBaseline() {
        if (!_isPoweredOn.value) return
        runOnSensorThread {
            detector.tare()
            _isStillNear.value = false
            _creatureState.value = CreatureState.SLUMBERING
        }
    }

    /** The tolerance in force right now (base threshold plus the movement allowance). */
    fun calculateEffectiveThreshold(): Float {
        val scale = (10f / _adaptiveSensitivity.value.toFloat()).coerceIn(0.5f, 10f)
        return detector.baseThreshold() + detector.motionAllowance * scale
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isPoweredOn.value) return
        val nowMs = SystemClock.elapsedRealtime()

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val dist = event.values[0]
                val maxRange = event.sensor.maximumRange
                _isPocketCovered.value = dist < 2.5f || (dist < maxRange && maxRange <= 5.0f)
            }

            Sensor.TYPE_ACCELEROMETER ->
                detector.onAccelerometer(nowMs, event.values[0], event.values[1], event.values[2])

            Sensor.TYPE_GYROSCOPE ->
                detector.onGyroscope(nowMs, event.values[0], event.values[1], event.values[2])

            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                hasRotationSample = true
            }

            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> processMagneticEvent(event, nowMs)
        }
    }

    private fun processMagneticEvent(event: SensorEvent, nowMs: Long) {
        // Anti-ghosting: drop magnetometer samples while the vibration motor runs, and
        // tell the detector there is a hole so it never joins the data on either side.
        if (nowMs < hapticPulseBlankUntilMs) {
            if (!wasBlanked) {
                wasBlanked = true
                detector.markGap()
            }
            return
        }
        wasBlanked = false

        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]
        val rawScalar = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
        _liveMicroTesla.value = rawScalar.toInt()

        checkSmartAlarm(rawScalar, nowMs)

        // Express the field in a gravity-anchored frame when the rotation reference exists.
        val hasWorld = hasRotationSample
        val worldX: Float
        val worldY: Float
        val worldZ: Float
        if (hasWorld) {
            worldX = rotationMatrix[0] * rawX + rotationMatrix[1] * rawY + rotationMatrix[2] * rawZ
            worldY = rotationMatrix[3] * rawX + rotationMatrix[4] * rawY + rotationMatrix[5] * rawZ
            worldZ = rotationMatrix[6] * rawX + rotationMatrix[7] * rawY + rotationMatrix[8] * rawZ
        } else {
            worldX = rawX
            worldY = rawY
            worldZ = rawZ
        }

        // Sleeping mode: a strong field wakes the app. This is checked on the raw sample.
        val sleeping = _isSleepingMode.value
        if (sleeping && rawScalar > 80f) wakeFromSleep()

        detector.allowTrigger = _isTrickRunning.value &&
            !_isSleepingMode.value &&
            !_isCalibratingObject.value

        val step = detector.onMagnetic(nowMs, rawX, rawY, rawZ, worldX, worldY, worldZ, hasWorld)
            ?: return
        handleStep(step, rawScalar, nowMs)
    }

    private fun wakeFromSleep() {
        if (!_isSleepingMode.value) return
        _isSleepingMode.value = false
        _isTrickRunning.value = true
        externalScope.launch(Dispatchers.Default) { _sleepWakeEvents.emit(Unit) }
    }

    /**
     * Smart Alarm: warns about a saturated sensor (reading far too high) or a dead one
     * (reading stuck at zero), either of which would make the trick silently fail.
     */
    private fun checkSmartAlarm(rawScalar: Float, nowMs: Long) {
        if (!_isSmartAlarmEnabled.value) return
        var fire = rawScalar >= _smartAlarmThreshold.value
        if (rawScalar < ZERO_READING_UT) {
            if (zeroSinceMs == 0L) zeroSinceMs = nowMs
            if (nowMs - zeroSinceMs >= ZERO_READING_MS) fire = true
        } else {
            zeroSinceMs = 0L
        }
        if (fire && nowMs - lastSmartAlarmTimeMs >= 2500L) {
            lastSmartAlarmTimeMs = nowMs
            externalScope.launch(Dispatchers.Default) { _smartAlarmEvents.emit(rawScalar) }
        }
    }

    private fun handleStep(step: FieldDetector.Step, rawScalar: Float, nowMs: Long) {
        // Object calibration capture.
        if (_isCalibratingObject.value) {
            if (step.disturbance > calibCapturedPeak) {
                calibCapturedPeak = step.disturbance
                _objectCalibrationPeak.value = calibCapturedPeak
            }
            if (nowMs >= calibCaptureEndTimeMs) finishObjectCalibration()
        }

        // Sleeping mode: nothing else happens, apart from a very large change waking it.
        if (_isSleepingMode.value) {
            if (step.disturbance > 20f) wakeFromSleep()
            emitReading(step, nowMs, force = false, isStrike = false,
                status = "Sleeping Mode: Bring magnet close to wake", percent = 0)
            return
        }

        // Still-near bookkeeping (Strong Magnet).
        if (step.stillNearConfirmed) {
            _isStillNear.value = true
            externalScope.launch(Dispatchers.Default) { _stillNearEvents.emit(Unit) }
        }
        if (step.holdEnded != null) _isStillNear.value = false

        val percent = ((step.disturbance / max(0.5f, step.threshold * 1.3f)) * 100f)
            .toInt().coerceIn(0, 100)
        val isStrike = step.isTrigger
        val movingNow = step.motionAllowance > MOVING_DISPLAY_UT

        val status = when {
            !step.isAttuned -> "Attuning to room…"
            isStrike -> "Movement Detected!"
            _isStillNear.value -> "Object still close"
            movingNow -> "Allowing for phone movement"
            percent >= 65 -> "Strong Field ($percent%)"
            percent >= 25 -> "Faint Ripple ($percent%)"
            else -> "The goblin sleeps."
        }

        _creatureState.value = when {
            !step.isAttuned -> CreatureState.CALIBRATING
            isStrike -> CreatureState.STRIKING
            percent >= 65 -> CreatureState.AWAKE
            percent >= 25 -> CreatureState.STIRRING
            else -> CreatureState.SLUMBERING
        }

        val reading = emitReading(step, nowMs, force = isStrike, isStrike = isStrike,
            status = status, percent = percent)

        if (isStrike) {
            externalScope.launch(Dispatchers.Default) { _strikeEvents.emit(reading) }
        }
    }

    private fun emitReading(
        step: FieldDetector.Step,
        nowMs: Long,
        force: Boolean,
        isStrike: Boolean,
        status: String,
        percent: Int
    ): MagneticReading {
        val reading = MagneticReading(
            x = step.smoothX,
            y = step.smoothY,
            z = step.smoothZ,
            deltaX = step.deltaX,
            deltaY = step.deltaY,
            deltaZ = step.deltaZ,
            deltaMagnitude = step.disturbance,
            rateOfChange = step.disturbance * 2.5f,
            noiseFloor = detector.noiseFloor,
            isPhoneMoving = step.motionAllowance > MOVING_DISPLAY_UT,
            isRoomAttuned = step.isAttuned,
            gesturePhase = if (isStrike) GesturePhase.HAND_ARC_CONFIRMED else GesturePhase.IDLE,
            isHandArcDetected = isStrike,
            scentStrengthPercent = percent,
            scentStatusText = status,
            thresholdUsed = step.threshold,
            isStillNear = _isStillNear.value,
            timestamp = System.currentTimeMillis()
        )
        if (force || nowMs - lastUiEmitTimeMs >= 16L) {
            lastUiEmitTimeMs = nowMs
            _readingState.value = reading
        }
        return reading
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val TAG = "MagneticSensorEngine"
        const val MOVING_DISPLAY_UT = 0.5f             // allowance above this reads as "phone moving"
        const val ZERO_READING_UT = 0.5f
        const val ZERO_READING_MS = 1000L
    }
}
