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
import kotlin.math.abs
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

    // ---- The two plain-language sliders ----
    // Sensitivity: how small a change counts (0 = only big changes .. 1 = the most the phone
    // can trust). Calibration sets it from the real object.
    private val _sensitivity = MutableStateFlow(0.5f)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    // Phone Movement: how much movement of the phone itself is forgiven (strict .. relaxed).
    private val _phoneMovement = MutableStateFlow(50)
    val phoneMovement: StateFlow<Int> = _phoneMovement.asStateFlow()

    // Strong-object check: confirm that a strong object is still near after a trigger.
    // Calibration switches it on for strong objects; it can be changed by hand.
    private val _strongCheck = MutableStateFlow(false)
    val strongCheck: StateFlow<Boolean> = _strongCheck.asStateFlow()

    // One line describing what the slider adds up to ("Reacts to a field change of about 2 uT").
    private val _rangeSummary = MutableStateFlow("")
    val rangeSummary: StateFlow<String> = _rangeSummary.asStateFlow()

    fun setSensitivity(value: Float) { _sensitivity.value = value.coerceIn(0f, 1f); applyModel() }
    fun setPhoneMovement(value: Int) { _phoneMovement.value = value.coerceIn(0, 100); applyModel() }
    fun setStrongCheck(on: Boolean) { _strongCheck.value = on; applyModel() }

    /** Hands the current slider values to the detector. Cheap; safe from any thread. */
    private fun applyModel() {
        val noise = max(detector.noiseFloor, calibratedNoise)
        val s = _sensitivity.value
        detector.baseThresholdUt = DetectionModel.threshold(s, noise)
        detector.adaptiveSensitivity = DetectionModel.adaptiveFromPhoneMovement(_phoneMovement.value)
        detector.stillNearCheck = _strongCheck.value
        detector.verticalOnly = false
        _rangeSummary.value = DetectionModel.summary(s, noise)
    }

    // Noise measured in the still phase of the last calibration (0 = none yet).
    @Volatile private var calibratedNoise = 0f

    // Live MicroTesla readout (e.g. 32 uT)
    private val _liveMicroTesla = MutableStateFlow(32)
    val liveMicroTesla: StateFlow<Int> = _liveMicroTesla.asStateFlow()

    // Sensor saturation: a magnet pressed against the phone can drive the magnetometer past
    // its range and leave it unreliable. Detection pauses until it is cleared by a click of
    // a volume key or by any movement of the phone.
    private val _isSaturated = MutableStateFlow(false)
    val isSaturated: StateFlow<Boolean> = _isSaturated.asStateFlow()

    private val saturationUt: Float =
        ((magnetometer?.maximumRange ?: 0f).takeIf { it > 0f }?.times(0.9f) ?: 1800f).coerceAtLeast(200f)

    fun clearSaturation() {
        if (!_isSaturated.value) return
        runOnSensorThread {
            _isSaturated.value = false
            detector.startAttunement()
            _isStillNear.value = false
            _creatureState.value = CreatureState.CALIBRATING
        }
    }

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

    // Calibration: 10 seconds in three steps, with the screen telling the performer what to do.
    //   1. HOLD_STILL  (0-3 s)  phone still, object away     -> measures the room's noise
    //   2. MOVE_PHONE  (3-6 s)  phone moved gently, no object -> sets Phone Movement
    //   3. MOVE_OBJECT (6-10 s) object brought right up to the phone, then pulled away
    //                                                         -> sets Sensitivity and the strong-object check
    enum class CalibrationPhase { IDLE, HOLD_STILL, MOVE_PHONE, MOVE_OBJECT }

    private val _isCalibratingObject = MutableStateFlow(false)
    val isCalibratingObject: StateFlow<Boolean> = _isCalibratingObject.asStateFlow()

    private val _calibrationPhase = MutableStateFlow(CalibrationPhase.IDLE)
    val calibrationPhase: StateFlow<CalibrationPhase> = _calibrationPhase.asStateFlow()

    private val _calibrationProgress = MutableStateFlow(0f)
    val calibrationProgress: StateFlow<Float> = _calibrationProgress.asStateFlow()

    private val _objectCalibrationPeak = MutableStateFlow(0f)
    val objectCalibrationPeak: StateFlow<Float> = _objectCalibrationPeak.asStateFlow()

    data class ObjectCalibrationResult(
        val success: Boolean,
        val peakDisturbance: Float,
        val noiseFloorAtCapture: Float,
        /** How far the field level moved at its most (µT) during the object pass. */
        val levelPeak: Float = 0f,
        /** False when the object is too weak for the "still near" check. */
        val confirmationUsable: Boolean = true,
        val newSensitivity: Float = 0.5f,
        val newStrongCheck: Boolean = false,
        /** The new Phone Movement value, or -1 if the phone was not moved enough to measure it. */
        val newPhoneMovement: Int = -1,
        val reachable: Boolean = true
    )
    private val _calibrationEvents = MutableSharedFlow<ObjectCalibrationResult>(
        replay = 0,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val calibrationEvents: SharedFlow<ObjectCalibrationResult> = _calibrationEvents.asSharedFlow()

    private var calibStartMs = 0L
    private var calibStillMax = 0f
    private var calibMoveMax = 0f
    private var calibMoveAllowance = 0f
    private var calibObjectPeak = 0f
    private var calibObjectPhaseStarted = false

    fun startObjectCalibration(durationMs: Long = CALIBRATION_MS) {
        runOnSensorThread {
            calibDurationMs = durationMs
            calibStartMs = SystemClock.elapsedRealtime()
            calibStillMax = 0f
            calibMoveMax = 0f
            calibMoveAllowance = 0f
            calibObjectPeak = 0f
            calibObjectPhaseStarted = false
            _objectCalibrationPeak.value = 0f
            _calibrationProgress.value = 0f
            _calibrationPhase.value = CalibrationPhase.HOLD_STILL
            _isCalibratingObject.value = true
        }
    }

    private var calibDurationMs = CALIBRATION_MS

    fun cancelObjectCalibration() {
        runOnSensorThread {
            if (_isCalibratingObject.value) {
                detector.endLevelCapture()
                _isCalibratingObject.value = false
                _calibrationPhase.value = CalibrationPhase.IDLE
            }
        }
    }

    /** Called with every step while calibrating. */
    private fun calibrationStep(step: FieldDetector.Step, nowMs: Long) {
        val elapsed = nowMs - calibStartMs
        val fraction = (elapsed.toFloat() / calibDurationMs).coerceIn(0f, 1f)
        _calibrationProgress.value = fraction
        val phase = when {
            fraction < 0.3f -> CalibrationPhase.HOLD_STILL
            fraction < 0.6f -> CalibrationPhase.MOVE_PHONE
            else -> CalibrationPhase.MOVE_OBJECT
        }
        _calibrationPhase.value = phase
        when (phase) {
            CalibrationPhase.HOLD_STILL -> calibStillMax = max(calibStillMax, step.disturbance)
            CalibrationPhase.MOVE_PHONE -> {
                calibMoveMax = max(calibMoveMax, step.disturbance)
                calibMoveAllowance = max(calibMoveAllowance, step.motionAllowance)
            }
            CalibrationPhase.MOVE_OBJECT -> {
                if (!calibObjectPhaseStarted) {
                    calibObjectPhaseStarted = true
                    detector.beginLevelCapture()
                }
                if (step.disturbance > calibObjectPeak) {
                    calibObjectPeak = step.disturbance
                    _objectCalibrationPeak.value = calibObjectPeak
                }
            }
            CalibrationPhase.IDLE -> Unit
        }
        if (elapsed >= calibDurationMs) finishObjectCalibration()
    }

    private fun finishObjectCalibration() {
        val levelPeak = if (calibObjectPhaseStarted) detector.endLevelCapture() else 0f
        _isCalibratingObject.value = false
        _calibrationPhase.value = CalibrationPhase.IDLE

        val noise = max(detector.noiseFloor, calibStillMax / 2f)
        val peak = calibObjectPeak
        // A real pass should stand well clear of the room's own noise.
        val success = peak > max(noise * 1.6f, 0.3f) && levelPeak > 0f

        var newSensitivity = _sensitivity.value
        var newStrong = _strongCheck.value
        var newPhone = -1
        var reachable = true
        var usable = true
        if (success) {
            calibratedNoise = noise
            detector.calibratedLevelPeak = levelPeak
            usable = levelPeak >= FieldDetector.USABLE_LEVEL_PEAK
            val targetThreshold = DetectionModel.thresholdFromCalibration(levelPeak, noise)
            newSensitivity = DetectionModel.sensitivityFromThreshold(targetThreshold, noise)
            newStrong = DetectionModel.isStrongObject(levelPeak) && usable
            // Reachable: the object's own pass stood well above the trigger level.
            reachable = levelPeak >= 2f * targetThreshold

            // Phone Movement: choose how much movement to forgive so the movement seen in
            // step 2 (no object present) would NOT have triggered, with a 15% margin.
            if (calibMoveAllowance >= MIN_MEASURABLE_ALLOWANCE) {
                val base = DetectionModel.threshold(newSensitivity, noise)
                val scale = ((1.15f * calibMoveMax - base) / calibMoveAllowance).coerceIn(0.5f, 10f)
                val adaptive = (10f / scale).coerceIn(1f, 20f)
                // Never fully strict: a little forgiveness keeps a real object pass readable.
                newPhone = (((20f - adaptive) / 19f) * 100f).toInt().coerceIn(MIN_AUTO_PHONE_MOVEMENT, 100)
            }
        }
        val result = ObjectCalibrationResult(
            success = success,
            peakDisturbance = peak,
            noiseFloorAtCapture = noise,
            levelPeak = if (success) levelPeak else 0f,
            confirmationUsable = usable,
            newSensitivity = newSensitivity,
            newStrongCheck = newStrong,
            newPhoneMovement = newPhone,
            reachable = reachable
        )
        if (success) {
            // Apply here too, so detection uses the new values immediately.
            _sensitivity.value = newSensitivity
            _strongCheck.value = newStrong
            if (newPhone >= 0) _phoneMovement.value = newPhone
            applyModel()
        }
        externalScope.launch(Dispatchers.Default) { _calibrationEvents.emit(result) }
    }

    // Configuration parameters
    @Volatile var debounceMs: Long = 400L

    init {
        applyModel()
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
        val scale = (10f / detector.adaptiveSensitivity.toFloat()).coerceIn(0.5f, 10f)
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

            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                detector.onAccelerometer(nowMs, ax, ay, az)
                // Any real movement of the phone clears a sensor saturation.
                if (_isSaturated.value &&
                    abs(sqrt(ax * ax + ay * ay + az * az) - SensorManager.GRAVITY_EARTH) > CLEAR_ACCEL_MS2
                ) clearSaturation()
            }

            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                detector.onGyroscope(nowMs, gx, gy, gz)
                if (_isSaturated.value && sqrt(gx * gx + gy * gy + gz * gz) > CLEAR_GYRO_RADS) clearSaturation()
            }

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

        // Saturation latch: stays on until a volume key is clicked or the phone is moved.
        if (rawScalar >= saturationUt && !_isSaturated.value) {
            _isSaturated.value = true
            _isStillNear.value = false
            _creatureState.value = CreatureState.AWAKE
        }
        if (_isSaturated.value) {
            detector.markGap()
            return
        }

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

        detector.allowTrigger = _isTrickRunning.value && !_isCalibratingObject.value

        val step = detector.onMagnetic(nowMs, rawX, rawY, rawZ, worldX, worldY, worldZ, hasWorld)
            ?: return
        handleStep(step, nowMs)
    }

    private fun handleStep(step: FieldDetector.Step, nowMs: Long) {
        // The noise floor changes slowly; keep the trigger level in step with it.
        if (nowMs - lastModelMs >= MODEL_REFRESH_MS) {
            lastModelMs = nowMs
            applyModel()
        }

        if (_isCalibratingObject.value) calibrationStep(step, nowMs)

        // Still-near bookkeeping (Strong objects).
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
            _isCalibratingObject.value -> "Calibrating…"
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

    private var lastModelMs = 0L

    private companion object {
        const val TAG = "MagneticSensorEngine"
        const val CALIBRATION_MS = 10_000L
        const val MODEL_REFRESH_MS = 250L
        const val MIN_AUTO_PHONE_MOVEMENT = 15
        const val MIN_MEASURABLE_ALLOWANCE = 0.3f      // phone must really have been moved in step 2
        const val MOVING_DISPLAY_UT = 0.5f             // allowance above this reads as "phone moving"
        const val CLEAR_ACCEL_MS2 = 1.2f               // movement that clears a saturation
        const val CLEAR_GYRO_RADS = 0.6f
    }
}
