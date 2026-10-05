package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-performance, anti-ghosting Magnetic Sensor Engine.
 *
 * Uses:
 * 1. Low-Pass Smoothing (alpha = 0.25) to eradicate hardware sensor white noise.
 * 2. 400ms Sliding Window Envelope (Peak-to-Peak) to measure real dynamic disturbance
 *    without noise-amplifying derivatives.
 * 3. Strict Baseline Lockout: freezes baseline adaptation whenever an active magnetic
 *    object moves nearby so the signal is NEVER absorbed or swallowed.
 * 4. Inertial Phone Stillness Gating: uses accelerometer/gyroscope to suppress false
 *    triggers when the phone itself is turned, picked up, or handled.
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

    // Game rotation vector: orientation derived from gyroscope+accelerometer only,
    // deliberately NOT from the magnetometer, so it gives an independent read on how
    // much the phone itself has turned. Used to "derotate" the raw magnetic reading
    // into a stable frame, so a phone that's merely being turned in a hand or pocket
    // doesn't look like the field itself changed.
    private val gameRotationVector: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val hasRotationReference: Boolean get() = gameRotationVector != null
    private val rotationMatrix = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )
    private var hasRotationSample = false

    // Power State Flow (On / Off - "Activate sensor")
    private val _isPoweredOn = MutableStateFlow(true)
    val isPoweredOn: StateFlow<Boolean> = _isPoweredOn.asStateFlow()

    // 1. Start Trick (Running state)
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

    // 2. Sensitivity (Stationary sensitivity, default 1.9, step 0.1, range 0.5 to 10.0)
    private val _sensitivity = MutableStateFlow(1.9f)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    fun setSensitivity(value: Float) {
        _sensitivity.value = (value * 10f).toInt() / 10f
    }

    // 3. Adaptative Sensitivity (Moving / walk sensitivity, default 8, step 1, range 1 to 20)
    private val _adaptiveSensitivity = MutableStateFlow(8)
    val adaptiveSensitivity: StateFlow<Int> = _adaptiveSensitivity.asStateFlow()

    fun setAdaptiveSensitivity(value: Int) {
        _adaptiveSensitivity.value = value.coerceIn(1, 20)
    }

    // 4. Smart Alarm (Saturation Warning, default enabled, threshold 150 uT)
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

    // 5. Sleeping Mode (Covert magnet wakeup)
    private val _isSleepingMode = MutableStateFlow(false)
    val isSleepingMode: StateFlow<Boolean> = _isSleepingMode.asStateFlow()

    fun setSleepingMode(sleeping: Boolean) {
        _isSleepingMode.value = sleeping
    }

    fun toggleSleepingMode() {
        _isSleepingMode.value = !_isSleepingMode.value
    }

    // 6. Live MicroTesla readout (e.g. 32 uT)
    private val _liveMicroTesla = MutableStateFlow(32)
    val liveMicroTesla: StateFlow<Int> = _liveMicroTesla.asStateFlow()

    // 7. Event flows for Smart Alarm & Sleeping Wake
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

    // Pocket proximity detection state
    private val _isPocketCovered = MutableStateFlow(false)
    val isPocketCovered: StateFlow<Boolean> = _isPocketCovered.asStateFlow()

    // Locator Mode: Proximity 0-50cm, Earbud Detector, or Strict Y-Axis
    private val _locatorMode = MutableStateFlow(LocatorMode.PROXIMITY_50CM)
    val locatorMode: StateFlow<LocatorMode> = _locatorMode.asStateFlow()

    fun setLocatorMode(mode: LocatorMode) {
        _locatorMode.value = mode
    }

    fun toggleLocatorMode() {
        _locatorMode.value = when (_locatorMode.value) {
            LocatorMode.PROXIMITY_50CM -> LocatorMode.EARBUD_DETECTOR
            LocatorMode.EARBUD_DETECTOR -> LocatorMode.Y_AXIS_VERTICAL
            LocatorMode.Y_AXIS_VERTICAL -> LocatorMode.PROXIMITY_50CM
        }
    }

    // Target Magnetic Field Strength Window (0% to 100%)

    // Anti-Ghosting Haptic Motor Blanking
    private var hapticPulseBlankUntilMs = 0L

    fun notifyHapticPulse(durationMs: Long = 450L) {
        hapticPulseBlankUntilMs = System.currentTimeMillis() + durationMs
    }

    // Dedicated background thread for sensor processing
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var lastUiEmitTimeMs = 0L

    val isSensorAvailable: Boolean get() = magnetometer != null

    // Reference Baseline (Ambient room DC offset)
    private var baseBx = 0f
    private var baseBy = 0f
    private var baseBz = 0f
    private var isBaselineInitialized = false

    // Low-pass filtered sensor stream (kills white noise jitter)
    private var smoothBx = 0f
    private var smoothBy = 0f
    private var smoothBz = 0f
    private var smoothScalar = 0f
    private val SMOOTHING_ALPHA = 0.25f

    // Room understanding / learning phase
    private var isCalibratingRoom = true
    private var calibrationStartTimeMs = System.currentTimeMillis()
    private var calibSampleCount = 0
    private var calibSumX = 0f
    private var calibSumY = 0f
    private var calibSumZ = 0f

    // Device physical movement gating (ignoring phone's own rotation/handling).
    // A held or pocketed phone is never perfectly still, so this has to tell
    // ordinary hand tremor/micro-jostle apart from an actual pickup/reposition:
    // tremor is brief and oscillating, a real reposition is a sustained push in
    // one direction. Rather than reacting to a single sample crossing a threshold,
    // it requires the elevated reading to hold continuously for a short stretch.
    private var lastMotionTimeMs = 0L
    private val MOTION_SETTLE_WINDOW_MS = 550L
    private val REPOSITION_ACCEL_THRESHOLD = 0.9f   // m/s^2 off gravity
    private val REPOSITION_GYRO_THRESHOLD = 0.45f   // rad/s
    private val REPOSITION_SUSTAIN_MS = 120L
    private var accelAboveSinceMs = 0L
    private var gyroAboveSinceMs = 0L

    // Sliding Window Envelope for Peak-to-Peak Disturbance Detection
    private data class WindowSample(
        val timeMs: Long,
        val scalar: Float,
        val dx: Float,
        val dy: Float,
        val dz: Float
    )
    private val windowSamples = ArrayList<WindowSample>(60)
    private val WINDOW_SPAN_MS = 400L

    // Noise floor tracking
    private var noiseFloor = 0.22f
    private var yNoiseFloor = 0.15f
    private var quietSampleCount = 0
    private var quietSumDisturbance = 0f
    private var quietSumY = 0f

    // Static field recovery timer (if a magnet is placed near phone and remains completely still)
    private var undisturbedDurationMs = 0L
    private var lastStaticCheckTimeMs = 0L

    // Saturation/stuck-reading escape hatch: if the field reads as "disturbed"
    // continuously for far longer than any real object pass ever takes, the
    // freeze below is almost certainly stuck on something that isn't an ongoing
    // detection (sensor saturation, residual magnetization, interference) rather
    // than genuinely still happening. Without this, the only way out was moving
    // the phone enough to force a re-read.
    private var disturbanceActiveSinceMs = 0L
    private val STUCK_DISTURBANCE_TIMEOUT_MS = 4000L

    // Strike debounce
    private var lastStrikeTimeMs = 0L

    // Manual object calibration: capture the peak disturbance from a short, deliberate
    // pass of the actual object being used tonight (earbud, watch charger, fridge magnet,
    // etc.) and derive a sensitivity that sits safely between that peak and room noise.
    // This replaces guessing at a fixed sensitivity number per object.
    private val _isCalibratingObject = MutableStateFlow(false)
    val isCalibratingObject: StateFlow<Boolean> = _isCalibratingObject.asStateFlow()

    private val _objectCalibrationPeak = MutableStateFlow(0f)
    val objectCalibrationPeak: StateFlow<Float> = _objectCalibrationPeak.asStateFlow()

    data class ObjectCalibrationResult(
        val success: Boolean,
        val peakDisturbance: Float,
        val noiseFloorAtCapture: Float,
        val suggestedSensitivity: Float
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
     * the intended detection range (right up against where a spectator's hand will be)
     * for the full [durationMs] while this runs.
     */
    fun startObjectCalibration(durationMs: Long = 3000L) {
        calibCaptureEndTimeMs = System.currentTimeMillis() + durationMs
        calibCapturedPeak = 0f
        _objectCalibrationPeak.value = 0f
        _isCalibratingObject.value = true
    }

    private fun finishObjectCalibration() {
        _isCalibratingObject.value = false
        val peak = calibCapturedPeak
        // A real object pass should stand well clear of the room's own noise floor;
        // if it didn't, the object never got close enough during the capture window.
        val success = peak > noiseFloor * 1.6f
        val suggested = if (success) {
            // Sit the threshold roughly halfway between the measured noise and the
            // object's peak -- comfortably below a real hit, comfortably above noise.
            val targetThreshold = max(noiseFloor * 1.8f, peak * 0.45f)
            ((3.8f / targetThreshold).coerceIn(0.5f, 10.0f) * 10f).toInt() / 10f
        } else {
            _sensitivity.value
        }
        externalScope.launch(Dispatchers.Default) {
            _calibrationEvents.emit(
                ObjectCalibrationResult(
                    success = success,
                    peakDisturbance = peak,
                    noiseFloorAtCapture = noiseFloor,
                    suggestedSensitivity = suggested
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
            _creatureState.value = CreatureState.DORMANT
            _readingState.value = _readingState.value.copy(
                deltaMagnitude = 0f,
                rateOfChange = 0f,
                isPhoneMoving = false,
                isRoomAttuned = false
            )
        }
    }

    fun togglePower() {
        setPower(!_isPoweredOn.value)
    }

    fun startListening() {
        if (!_isPoweredOn.value) return
        if (sensorThread == null) {
            sensorThread = HandlerThread("MagneticSensorEngineThread").apply {
                start()
                sensorHandler = Handler(looper)
            }
        }
        val handler = sensorHandler
        // Magnetometer polled at SENSOR_DELAY_FASTEST with fallback to GAME
        registerSensorSafely(magnetometer, SensorManager.SENSOR_DELAY_FASTEST, handler)
        // Inertial motion sensors for detecting phone physical motion
        registerSensorSafely(accelerometer, SensorManager.SENSOR_DELAY_GAME, handler)
        registerSensorSafely(gyroscope, SensorManager.SENSOR_DELAY_GAME, handler)
        registerSensorSafely(proximitySensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
        registerSensorSafely(gameRotationVector, SensorManager.SENSOR_DELAY_GAME, handler)
    }

    private fun registerSensorSafely(sensor: Sensor?, preferredDelay: Int, handler: Handler?) {
        if (sensor == null || sensorManager == null) return
        try {
            sensorManager.registerListener(this, sensor, preferredDelay, handler)
        } catch (secEx: SecurityException) {
            Log.w("MagneticSensorEngine", "HIGH_SAMPLING_RATE_SENSORS restricted for ${sensor.name}, falling back to GAME delay", secEx)
            try {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, handler)
            } catch (fallbackEx: Exception) {
                Log.e("MagneticSensorEngine", "Fallback sensor registration failed for ${sensor.name}", fallbackEx)
            }
        } catch (e: Exception) {
            Log.e("MagneticSensorEngine", "Could not register sensor ${sensor.name}", e)
        }
    }

    fun stopListening() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
        try {
            sensorThread?.quitSafely()
        } catch (_: Exception) {}
        sensorThread = null
        sensorHandler = null
    }

    /**
     * Calibrates baseline to ambient room field while resting.
     */
    fun startRoomCalibration() {
        isCalibratingRoom = true
        calibrationStartTimeMs = System.currentTimeMillis()
        calibSampleCount = 0
        calibSumX = 0f
        calibSumY = 0f
        calibSumZ = 0f
        _creatureState.value = CreatureState.CALIBRATING
    }

    /**
     * Instantly tares baseline to current field.
     */
    fun tareBaseline() {
        if (!_isPoweredOn.value) return
        val current = _readingState.value
        baseBx = current.x
        baseBy = current.y
        baseBz = current.z
        smoothBx = current.x
        smoothBy = current.y
        smoothBz = current.z
        smoothScalar = sqrt(baseBx * baseBx + baseBy * baseBy + baseBz * baseBz)
        windowSamples.clear()
        isBaselineInitialized = true
        isCalibratingRoom = false
        _creatureState.value = CreatureState.SLUMBERING
    }

    val isPhonePhysicallyMoving: Boolean
        get() = (System.currentTimeMillis() - lastMotionTimeMs) < MOTION_SETTLE_WINDOW_MS

    /**
     * Dynamic threshold calculation based on Sensitivity (stationary) or Adaptive Sensitivity (moving).
     */
    fun calculateEffectiveThreshold(): Float {
        val sens = _sensitivity.value
        val adaptSens = _adaptiveSensitivity.value
        return if (isPhonePhysicallyMoving) {
            max(1.0f, 18.0f / adaptSens.toFloat())
        } else {
            max(0.35f, 3.8f / sens)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isPoweredOn.value) return

        // Anti-ghosting: ignore during haptic vibration motor pulses
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD ||
            event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
            if (System.currentTimeMillis() < hapticPulseBlankUntilMs) return
        }

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
                val accelMag = sqrt(ax * ax + ay * ay + az * az)
                // Deviations from Earth gravity (9.81 m/s²) indicate device physical movement.
                // Only counts once it's held above the threshold continuously for
                // REPOSITION_SUSTAIN_MS -- a single brief tremor spike decays back down
                // before that timer is reached and never flags "moving".
                val dynamicAccel = abs(accelMag - SensorManager.GRAVITY_EARTH)
                val nowAccelMs = System.currentTimeMillis()
                if (dynamicAccel > REPOSITION_ACCEL_THRESHOLD) {
                    if (accelAboveSinceMs == 0L) accelAboveSinceMs = nowAccelMs
                    if (nowAccelMs - accelAboveSinceMs >= REPOSITION_SUSTAIN_MS) {
                        lastMotionTimeMs = nowAccelMs
                    }
                } else {
                    accelAboveSinceMs = 0L
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                val rotationSpeed = sqrt(gx * gx + gy * gy + gz * gz)
                val nowGyroMs = System.currentTimeMillis()
                if (rotationSpeed > REPOSITION_GYRO_THRESHOLD) {
                    if (gyroAboveSinceMs == 0L) gyroAboveSinceMs = nowGyroMs
                    if (nowGyroMs - gyroAboveSinceMs >= REPOSITION_SUSTAIN_MS) {
                        lastMotionTimeMs = nowGyroMs
                    }
                } else {
                    gyroAboveSinceMs = 0L
                }
            }

            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                hasRotationSample = true
            }

            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                processMagneticEvent(event)
            }
        }
    }

    private fun processMagneticEvent(event: SensorEvent) {
        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]
        val rawScalar = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
        val nowMs = System.currentTimeMillis()

        // Update live MicroTesla value
        _liveMicroTesla.value = rawScalar.toInt()

        // Smart Alarm Saturation Check
        if (_isSmartAlarmEnabled.value && rawScalar >= _smartAlarmThreshold.value) {
            if (nowMs - lastSmartAlarmTimeMs >= 2500L) {
                lastSmartAlarmTimeMs = nowMs
                externalScope.launch(Dispatchers.Default) {
                    _smartAlarmEvents.emit(rawScalar)
                }
            }
        }

        // Derotate the raw reading using the gyro+accel-only rotation reference,
        // so turning the phone in a hand or pocket doesn't look like the field
        // itself changed -- only a genuinely external disturbance survives this.
        // Falls back to the raw device-frame reading if the sensor is unavailable
        // or hasn't produced a sample yet.
        val isRotationCompensated = hasRotationReference && hasRotationSample
        val worldX: Float
        val worldY: Float
        val worldZ: Float
        if (isRotationCompensated) {
            worldX = rotationMatrix[0] * rawX + rotationMatrix[1] * rawY + rotationMatrix[2] * rawZ
            worldY = rotationMatrix[3] * rawX + rotationMatrix[4] * rawY + rotationMatrix[5] * rawZ
            worldZ = rotationMatrix[6] * rawX + rotationMatrix[7] * rawY + rotationMatrix[8] * rawZ
        } else {
            worldX = rawX
            worldY = rawY
            worldZ = rawZ
        }

        // 1. Low-Pass Smoothing: Completely eliminates hardware white noise jitter
        if (!isBaselineInitialized) {
            smoothBx = worldX
            smoothBy = worldY
            smoothBz = worldZ
            smoothScalar = rawScalar
            baseBx = worldX
            baseBy = worldY
            baseBz = worldZ
            isBaselineInitialized = true
        } else {
            smoothBx = (1f - SMOOTHING_ALPHA) * smoothBx + SMOOTHING_ALPHA * worldX
            smoothBy = (1f - SMOOTHING_ALPHA) * smoothBy + SMOOTHING_ALPHA * worldY
            smoothBz = (1f - SMOOTHING_ALPHA) * smoothBz + SMOOTHING_ALPHA * worldZ
            smoothScalar = (1f - SMOOTHING_ALPHA) * smoothScalar + SMOOTHING_ALPHA * rawScalar
        }

        // 2. Physical phone handling detection. Suppressed entirely during manual
        // object calibration: the whole point of calibration is the phone being
        // handled while an object is moved near it, and the fixed-duration capture
        // must always reach its deadline check below or it can never finish.
        val isDevicePhysicallyMoving = !_isCalibratingObject.value &&
            (nowMs - lastMotionTimeMs) < MOTION_SETTLE_WINDOW_MS

        // 3. Room Attunement / Initial Calibration Phase
        if (isCalibratingRoom) {
            calibSumX += smoothBx
            calibSumY += smoothBy
            calibSumZ += smoothBz
            calibSampleCount++

            if (isDevicePhysicallyMoving) {
                calibrationStartTimeMs = nowMs
                calibSampleCount = 0
                calibSumX = 0f
                calibSumY = 0f
                calibSumZ = 0f
                baseBx = smoothBx
                baseBy = smoothBy
                baseBz = smoothBz
            } else if (nowMs - calibrationStartTimeMs > 1000L && calibSampleCount >= 15) {
                baseBx = calibSumX / calibSampleCount
                baseBy = calibSumY / calibSampleCount
                baseBz = calibSumZ / calibSampleCount
                isCalibratingRoom = false
                _creatureState.value = CreatureState.SLUMBERING
            }

            if ((nowMs - lastUiEmitTimeMs) >= 20L) {
                lastUiEmitTimeMs = nowMs
                _readingState.value = MagneticReading(
                    x = smoothBx,
                    y = smoothBy,
                    z = smoothBz,
                    baselineX = baseBx,
                    baselineY = baseBy,
                    baselineZ = baseBz,
                    deltaMagnitude = 0f,
                    rateOfChange = 0f,
                    noiseFloor = noiseFloor,
                    isPhoneMoving = isDevicePhysicallyMoving,
                    isRoomAttuned = !isCalibratingRoom,
                    timestamp = nowMs
                )
            }
            return
        }

        // 4. Phone Motion Handling (Fast re-alignment when phone itself is picked up/turned)
        if (isDevicePhysicallyMoving) {
            // Rapidly track orientation so false alarms are suppressed
            val rapidAlpha = 0.20f
            baseBx = (1f - rapidAlpha) * baseBx + rapidAlpha * smoothBx
            baseBy = (1f - rapidAlpha) * baseBy + rapidAlpha * smoothBy
            baseBz = (1f - rapidAlpha) * baseBz + rapidAlpha * smoothBz
            windowSamples.clear()
            _creatureState.value = CreatureState.SLUMBERING

            if ((nowMs - lastUiEmitTimeMs) >= 20L) {
                lastUiEmitTimeMs = nowMs
                _readingState.value = MagneticReading(
                    x = smoothBx,
                    y = smoothBy,
                    z = smoothBz,
                    baselineX = baseBx,
                    baselineY = baseBy,
                    baselineZ = baseBz,
                    deltaMagnitude = 0f,
                    rateOfChange = 0f,
                    noiseFloor = noiseFloor,
                    isPhoneMoving = true,
                    isRoomAttuned = false,
                    scentStatusText = "Ignoring phone motion",
                    timestamp = nowMs
                )
            }
            return
        }

        // 5. Stationary Phone: Pure External Magnetic Disturbance Detection
        val dx = smoothBx - baseBx
        val dy = smoothBy - baseBy
        val dz = smoothBz - baseBz
        val vectorDelta = sqrt(dx * dx + dy * dy + dz * dz)

        // Append to 400ms Sliding Window Buffer
        windowSamples.add(WindowSample(nowMs, smoothScalar, dx, dy, dz))
        val windowCutoff = nowMs - WINDOW_SPAN_MS
        while (windowSamples.isNotEmpty() && windowSamples.first().timeMs < windowCutoff) {
            windowSamples.removeAt(0)
        }

        // Calculate Envelope: Peak-to-Peak spread across the 400ms window
        var minScalar = Float.MAX_VALUE
        var maxScalar = -Float.MAX_VALUE
        var maxVecDelta = 0f
        var minVecDelta = Float.MAX_VALUE

        for (sample in windowSamples) {
            if (sample.scalar < minScalar) minScalar = sample.scalar
            if (sample.scalar > maxScalar) maxScalar = sample.scalar

            val vd = sqrt(sample.dx * sample.dx + sample.dy * sample.dy + sample.dz * sample.dz)
            if (vd > maxVecDelta) maxVecDelta = vd
            if (vd < minVecDelta) minVecDelta = vd
        }

        val scalarSpan = if (windowSamples.size >= 3) max(0f, maxScalar - minScalar) else 0f
        val vectorSpan = if (windowSamples.size >= 3) max(0f, maxVecDelta - minVecDelta) else 0f

        // Disturbance: True dynamic ripple caused by an external moving magnetic field
        val disturbance = maxOf(scalarSpan, vectorSpan, vectorDelta * 0.85f)

        // Object calibration capture: record the peak disturbance seen while the
        // performer deliberately passes tonight's object through range.
        if (_isCalibratingObject.value) {
            if (disturbance > calibCapturedPeak) {
                calibCapturedPeak = disturbance
                _objectCalibrationPeak.value = calibCapturedPeak
            }
            if (nowMs >= calibCaptureEndTimeMs) {
                finishObjectCalibration()
            }
        }

        // 6. Dynamic Baseline Lockout & Long-Term Drift Tracking
        val isDisturbanceActive = disturbance > (noiseFloor * 1.5f)

        if (isDisturbanceActive) {
            // FREEZE baseline adaptation completely so a moving magnet is NEVER swallowed!
            undisturbedDurationMs = 0L
            lastStaticCheckTimeMs = nowMs

            if (disturbanceActiveSinceMs == 0L) disturbanceActiveSinceMs = nowMs
            val stuckDurationMs = nowMs - disturbanceActiveSinceMs
            if (stuckDurationMs > STUCK_DISTURBANCE_TIMEOUT_MS) {
                // Stuck well past any real pass's duration -- slowly let the baseline
                // catch back up to the current field so it can resolve on its own,
                // without needing the phone physically moved to "shake it loose."
                val unstickAlpha = 0.01f
                baseBx = (1f - unstickAlpha) * baseBx + unstickAlpha * smoothBx
                baseBy = (1f - unstickAlpha) * baseBy + unstickAlpha * smoothBy
                baseBz = (1f - unstickAlpha) * baseBz + unstickAlpha * smoothBz
            }
        } else {
            disturbanceActiveSinceMs = 0L
            // Quiet ambient period: gently eliminate slow temperature drift (tau ~ 8s)
            val driftAlpha = 0.006f
            baseBx = (1f - driftAlpha) * baseBx + driftAlpha * smoothBx
            baseBy = (1f - driftAlpha) * baseBy + driftAlpha * smoothBy
            baseBz = (1f - driftAlpha) * baseBz + driftAlpha * smoothBz

            // Update rolling noise floor during stillness
            quietSumDisturbance += disturbance
            quietSumY += abs(dy)
            quietSampleCount++
            if (quietSampleCount >= 25) {
                noiseFloor = max(0.12f, (quietSumDisturbance / quietSampleCount) * 1.35f)
                yNoiseFloor = max(0.08f, (quietSumY / quietSampleCount) * 1.25f)
                quietSumDisturbance = 0f
                quietSumY = 0f
                quietSampleCount = 0
            }

            // Static Recovery: If a magnetic object is placed next to the phone and rests
            // completely motionless for > 3.5s, gently absorb the static DC offset
            if (lastStaticCheckTimeMs > 0L) {
                undisturbedDurationMs += (nowMs - lastStaticCheckTimeMs)
            }
            lastStaticCheckTimeMs = nowMs
            if (undisturbedDurationMs > 3500L && vectorDelta > 0.5f) {
                // Settle to new static background
                val settleAlpha = 0.02f
                baseBx = (1f - settleAlpha) * baseBx + settleAlpha * smoothBx
                baseBy = (1f - settleAlpha) * baseBy + settleAlpha * smoothBy
                baseBz = (1f - settleAlpha) * baseBz + settleAlpha * smoothBz
            }
        }

        // 6b. Sleeping Mode Handling:
        // In sleeping mode, normal alerts are dormant until a magnet is brought close to wake the app
        if (_isSleepingMode.value) {
            if (rawScalar > 80f || disturbance > 20f) {
                _isSleepingMode.value = false
                _isTrickRunning.value = true
                externalScope.launch(Dispatchers.Default) {
                    _sleepWakeEvents.emit(Unit)
                }
            }
            if ((nowMs - lastUiEmitTimeMs) >= 20L) {
                lastUiEmitTimeMs = nowMs
                _readingState.value = MagneticReading(
                    x = smoothBx,
                    y = smoothBy,
                    z = smoothBz,
                    baselineX = baseBx,
                    baselineY = baseBy,
                    baselineZ = baseBz,
                    deltaMagnitude = disturbance,
                    scentStatusText = "Sleeping Mode: Bring magnet close to wake",
                    timestamp = nowMs
                )
            }
            return
        }

        // 7. Threshold Evaluation
        val threshold = calculateEffectiveThreshold()

        // Scent strength (0 to 100%) mapped against threshold
        val scentStrengthPercent = ((disturbance / max(0.5f, threshold * 1.3f)) * 100f).toInt().coerceIn(0, 100)

        // In-Window check for targeted magnetic strength (user configured)

        // Mode-Specific Strike Criteria
        val meetsModeCriteria = when (_locatorMode.value) {
            LocatorMode.PROXIMITY_50CM, LocatorMode.EARBUD_DETECTOR -> {
                disturbance >= threshold
            }
            LocatorMode.Y_AXIS_VERTICAL -> {
                if (isRotationCompensated) {
                    // True vertical (world Z, anchored by gravity -- correct no matter
                    // how the phone is oriented in a hand or pocket) must dominate the
                    // horizontal (world X/Y) component of the change.
                    val horizontalDelta = sqrt(dx * dx + dy * dy)
                    val isVerticalDominant = abs(dz) >= (horizontalDelta * 1.10f)
                    disturbance >= threshold && isVerticalDominant
                } else {
                    // No independent rotation reference available on this device --
                    // fall back to the phone's own Y axis, accurate only if it's held
                    // with a fairly consistent upright orientation.
                    val isYDominant = abs(dy) >= (abs(dx) * 1.10f)
                    disturbance >= threshold && isYDominant
                }
            }
        }

        val isStrike = _isTrickRunning.value &&
                meetsModeCriteria &&
                !isCalibratingRoom &&
                !_isCalibratingObject.value &&
                (nowMs >= hapticPulseBlankUntilMs)

        // Status text
        val scentStatusText = when {
            isDevicePhysicallyMoving -> "Ignoring phone movement"
            isStrike -> "Movement Detected!"
            scentStrengthPercent >= 65 -> "Strong Field ($scentStrengthPercent%)"
            scentStrengthPercent >= 25 -> "Faint Ripple ($scentStrengthPercent%)"
            else -> "The goblin sleeps."
        }

        // Creature animation state
        val newState = when {
            isStrike -> CreatureState.STRIKING
            scentStrengthPercent >= 65 -> CreatureState.AWAKE
            scentStrengthPercent >= 25 -> CreatureState.STIRRING
            else -> CreatureState.SLUMBERING
        }
        _creatureState.value = newState

        // Construct live reading
        val reading = MagneticReading(
            x = smoothBx,
            y = smoothBy,
            z = smoothBz,
            baselineX = baseBx,
            baselineY = baseBy,
            baselineZ = baseBz,
            deltaX = dx,
            deltaY = dy,
            deltaZ = dz,
            deltaMagnitude = disturbance,
            rateOfChange = scalarSpan * 2.5f,
            noiseFloor = if (_locatorMode.value == LocatorMode.Y_AXIS_VERTICAL) yNoiseFloor else noiseFloor,
            isPhoneMoving = false,
            isRoomAttuned = true,
            gesturePhase = if (isStrike) GesturePhase.HAND_ARC_CONFIRMED else GesturePhase.IDLE,
            isHandArcDetected = isStrike,
            scentStrengthPercent = scentStrengthPercent,
            scentStatusText = scentStatusText,
            timestamp = nowMs
        )

        // Throttle UI rendering to 60fps, but emit strikes instantaneously
        if (isStrike || (nowMs - lastUiEmitTimeMs) >= 16L) {
            lastUiEmitTimeMs = nowMs
            _readingState.value = reading
        }

        // Emit strike event for haptic pulse and logging (debounced)
        if (isStrike) {
            if (nowMs - lastStrikeTimeMs >= debounceMs) {
                lastStrikeTimeMs = nowMs
                externalScope.launch(Dispatchers.Default) {
                    _strikeEvents.emit(reading)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
