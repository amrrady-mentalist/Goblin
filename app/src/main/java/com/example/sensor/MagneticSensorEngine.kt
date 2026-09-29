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

    // Power State Flow (On / Off)
    private val _isPoweredOn = MutableStateFlow(true)
    val isPoweredOn: StateFlow<Boolean> = _isPoweredOn.asStateFlow()

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
    @Volatile var targetMinStrengthPercent: Int = 0
    @Volatile var targetMaxStrengthPercent: Int = 100

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

    // Device physical movement gating (ignoring phone's own rotation/handling)
    private var lastMotionTimeMs = 0L
    private val MOTION_SETTLE_WINDOW_MS = 550L

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

    // Strike debounce
    private var lastStrikeTimeMs = 0L

    // Configuration parameters
    @Volatile var isRoomWideMode: Boolean = true
    @Volatile var fogLevel: Float = 0.35f
    @Volatile var customBaseThreshold: Float = 1.2f
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

    /**
     * Dynamic threshold calculation based on LocatorMode, Fog setting, and ambient noise floor.
     */
    fun calculateEffectiveThreshold(): Float {
        return when (_locatorMode.value) {
            LocatorMode.PROXIMITY_50CM -> {
                val base = 0.70f + (2.20f * fogLevel)
                max(0.65f, base + (noiseFloor * 0.8f))
            }
            LocatorMode.EARBUD_DETECTOR -> {
                val base = 0.55f + (1.60f * fogLevel)
                max(0.50f, base + (noiseFloor * 0.6f))
            }
            LocatorMode.Y_AXIS_VERTICAL -> {
                val base = 0.60f + (customBaseThreshold * 0.40f * fogLevel)
                max(0.55f, base + (yNoiseFloor * 1.5f))
            }
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
                // Deviations from Earth gravity (9.81 m/s²) indicate device physical movement
                val dynamicAccel = abs(accelMag - SensorManager.GRAVITY_EARTH)
                if (dynamicAccel > 0.35f) {
                    lastMotionTimeMs = System.currentTimeMillis()
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                val rotationSpeed = sqrt(gx * gx + gy * gy + gz * gz)
                if (rotationSpeed > 0.22f) { // Angular rotation > 0.22 rad/s
                    lastMotionTimeMs = System.currentTimeMillis()
                }
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

        // 1. Low-Pass Smoothing: Completely eliminates hardware white noise jitter
        if (!isBaselineInitialized) {
            smoothBx = rawX
            smoothBy = rawY
            smoothBz = rawZ
            smoothScalar = rawScalar
            baseBx = rawX
            baseBy = rawY
            baseBz = rawZ
            isBaselineInitialized = true
        } else {
            smoothBx = (1f - SMOOTHING_ALPHA) * smoothBx + SMOOTHING_ALPHA * rawX
            smoothBy = (1f - SMOOTHING_ALPHA) * smoothBy + SMOOTHING_ALPHA * rawY
            smoothBz = (1f - SMOOTHING_ALPHA) * smoothBz + SMOOTHING_ALPHA * rawZ
            smoothScalar = (1f - SMOOTHING_ALPHA) * smoothScalar + SMOOTHING_ALPHA * rawScalar
        }

        // 2. Physical phone handling detection
        val isDevicePhysicallyMoving = (nowMs - lastMotionTimeMs) < MOTION_SETTLE_WINDOW_MS

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

        // 6. Dynamic Baseline Lockout & Long-Term Drift Tracking
        val isDisturbanceActive = disturbance > (noiseFloor * 1.5f)

        if (isDisturbanceActive) {
            // FREEZE baseline adaptation completely so a moving magnet is NEVER swallowed!
            undisturbedDurationMs = 0L
            lastStaticCheckTimeMs = nowMs
        } else {
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

        // 7. Threshold Evaluation
        val threshold = calculateEffectiveThreshold()

        // Scent strength (0 to 100%) mapped against threshold
        val scentStrengthPercent = ((disturbance / max(0.5f, threshold * 1.3f)) * 100f).toInt().coerceIn(0, 100)

        // In-Window check for targeted magnetic strength (user configured)
        val isWithinTargetWindow = scentStrengthPercent in targetMinStrengthPercent..targetMaxStrengthPercent

        // Mode-Specific Strike Criteria
        val meetsModeCriteria = when (_locatorMode.value) {
            LocatorMode.PROXIMITY_50CM, LocatorMode.EARBUD_DETECTOR -> {
                disturbance >= threshold
            }
            LocatorMode.Y_AXIS_VERTICAL -> {
                // Strict vertical up/down movement requires Y-axis dominance over lateral X
                val isYDominant = abs(dy) >= (abs(dx) * 1.10f)
                disturbance >= threshold && isYDominant
            }
        }

        val isStrike = meetsModeCriteria &&
                isWithinTargetWindow &&
                !isCalibratingRoom &&
                !isDevicePhysicallyMoving &&
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
