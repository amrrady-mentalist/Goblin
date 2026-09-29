package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MagneticSensorEngine(
    private val context: Context,
    private val externalScope: CoroutineScope
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val magnetometer: Sensor? = sensorManager?.let { sm ->
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
            ?: sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
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

    // Kinematic Gesture Buffer specifically recording vertical Y-axis and lateral axes
    private data class FluxHistorySample(
        val timeMs: Long,
        val dy: Float,
        val dx: Float,
        val dz: Float,
        val deltaMag: Float
    )
    private val sampleHistory = ArrayList<FluxHistorySample>(140)
    private var lastConfirmedGestureTimeMs = 0L

    val isSensorAvailable: Boolean get() = magnetometer != null

    // Room baseline adaptation variables
    private var baseBx = 0f
    private var baseBy = 0f
    private var baseBz = 0f
    private var isBaselineInitialized = false

    // Room understanding / learning phase
    private var isCalibratingRoom = true
    private var calibrationStartTimeMs = System.currentTimeMillis()
    private var calibSampleCount = 0
    private var calibSumX = 0f
    private var calibSumY = 0f
    private var calibSumZ = 0f

    // Device motion & Inertial gating (ignoring phone's own movement)
    private var lastMotionTimeMs = 0L
    private val MOTION_SETTLE_WINDOW_MS = 650L // Wait 650ms after motion stops before arming

    // Rolling noise floor estimation
    private var noiseFloor = 0.5f
    private val recentDeltas = FloatArray(16) { 0.2f }
    private var deltaIndex = 0

    // Strict Y-Axis noise floor tracking
    private var yNoiseFloor = 0.20f
    private val recentYDeltas = FloatArray(16) { 0.15f }
    private var yDeltaIndex = 0

    // Timing and rate of change
    private var lastTimestampNs: Long = 0
    private var lastDeltaMagnitude: Float = 0f

    // Strike debounce
    private var lastStrikeTimeMs: Long = 0L

    // Configuration parameters
    @Volatile var isRoomWideMode: Boolean = true // Ultra-high sensitivity for whole room detection
    @Volatile var fogLevel: Float = 0.35f // 0.0 (hypersensitive) to 1.0 (heavy stage shield)
    @Volatile var customBaseThreshold: Float = 1.2f // microTesla (standard tabletop)
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
        magnetometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        gyroscope?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        proximitySensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    /**
     * Starts understanding the magnetic field in the room in the background.
     * Samples the ambient room field while ignoring initial handoff/handling noise.
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
     * Instantly tares / recalibrates the baseline to current room conditions.
     */
    fun tareBaseline() {
        if (!_isPoweredOn.value) return
        val current = _readingState.value
        baseBx = current.x
        baseBy = current.y
        baseBz = current.z
        isBaselineInitialized = true
        isCalibratingRoom = false
        _creatureState.value = CreatureState.SLUMBERING
    }

    /**
     * Calculates the dynamic threshold based on LocatorMode, Fog setting,
     * and the measured environmental noise floor.
     * Enforces a robust anti-ghosting floor to prevent false triggers when nothing is moving.
     */
    fun calculateEffectiveThreshold(): Float {
        return when (_locatorMode.value) {
            LocatorMode.PROXIMITY_50CM -> {
                // Focus on 0 to 50 cm perimeter:
                // At 40-50 cm, magnetic delta is typically 0.8 to 2.5 uT.
                // Fog slider adjusts sensitivity from sensitive (0.70 uT) to shielded (2.8 uT)
                val base = 0.70f + (2.10f * fogLevel)
                max(0.65f, base + (noiseFloor * 0.7f))
            }
            LocatorMode.EARBUD_DETECTOR -> {
                // Calibrated for wireless earbud neodymium driver magnet moving within 0-40 cm
                val base = 0.60f + (1.60f * fogLevel)
                max(0.55f, base + (noiseFloor * 0.5f))
            }
            LocatorMode.Y_AXIS_VERTICAL -> {
                val base = 0.65f + (customBaseThreshold * 0.40f * fogLevel)
                max(0.60f, base + (yNoiseFloor * 1.5f))
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isPoweredOn.value) return

        // Anti-ghosting: if the phone just pulsed its haptic motor, ignore magnetometer readings
        // during and immediately after vibration so the motor doesn't re-trigger itself!
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
                if (dynamicAccel > 0.40f) {
                    lastMotionTimeMs = System.currentTimeMillis()
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                val rotationSpeed = sqrt(gx * gx + gy * gy + gz * gz)
                if (rotationSpeed > 0.18f) { // Angular rotation > 0.18 rad/s
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
        val nowMs = System.currentTimeMillis()

        // Detect if the phone itself is moving (picked up, turned, laid down)
        val isDevicePhysicallyMoving = (nowMs - lastMotionTimeMs) < MOTION_SETTLE_WINDOW_MS

        // Phase 1: First reading initialization
        if (!isBaselineInitialized) {
            baseBx = rawX
            baseBy = rawY
            baseBz = rawZ
            isBaselineInitialized = true
        }

        // Phase 2: Understanding room magnetism in the background
        if (isCalibratingRoom) {
            calibSumX += rawX
            calibSumY += rawY
            calibSumZ += rawZ
            calibSampleCount++

            // If phone is moving, reset calibration timer to ensure clean room baseline once placed down
            if (isDevicePhysicallyMoving) {
                calibrationStartTimeMs = nowMs
                calibSampleCount = 0
                calibSumX = 0f
                calibSumY = 0f
                calibSumZ = 0f
                baseBx = rawX
                baseBy = rawY
                baseBz = rawZ
            } else if (nowMs - calibrationStartTimeMs > 1200L && calibSampleCount >= 20) {
                // Room field learned while phone was resting
                baseBx = calibSumX / calibSampleCount
                baseBy = calibSumY / calibSampleCount
                baseBz = calibSumZ / calibSampleCount
                isCalibratingRoom = false
                _creatureState.value = CreatureState.SLUMBERING
            }

            _readingState.value = MagneticReading(
                x = rawX,
                y = rawY,
                z = rawZ,
                baselineX = baseBx,
                baselineY = baseBy,
                baselineZ = baseBz,
                deltaX = 0f,
                deltaY = 0f,
                deltaZ = 0f,
                deltaMagnitude = 0f,
                rateOfChange = 0f,
                noiseFloor = noiseFloor,
                isPhoneMoving = isDevicePhysicallyMoving,
                isRoomAttuned = !isCalibratingRoom,
                timestamp = nowMs
            )
            return
        }

        // Phase 3: Phone Motion Immunity (Ignoring phone's own movement)
        if (isDevicePhysicallyMoving) {
            // Fast adaptation follows the phone's new physical orientation in the room's magnetic field
            val dtSec = if (lastTimestampNs > 0) {
                val rawDt = (event.timestamp - lastTimestampNs) / 1_000_000_000f
                if (rawDt in 0.0005f..0.5f) rawDt else 0.01f
            } else 0.01f
            val rapidAlpha = (1.0f - exp(-dtSec / 0.15f)).coerceIn(0.10f, 0.90f)
            baseBx = (1f - rapidAlpha) * baseBx + rapidAlpha * rawX
            baseBy = (1f - rapidAlpha) * baseBy + rapidAlpha * rawY
            baseBz = (1f - rapidAlpha) * baseBz + rapidAlpha * rawZ
            lastTimestampNs = event.timestamp
            lastDeltaMagnitude = 0f

            // Suppress all false alerts while the phone moves!
            _creatureState.value = CreatureState.SLUMBERING

            _readingState.value = MagneticReading(
                x = rawX,
                y = rawY,
                z = rawZ,
                baselineX = baseBx,
                baselineY = baseBy,
                baselineZ = baseBz,
                deltaX = 0f,
                deltaY = 0f,
                deltaZ = 0f,
                deltaMagnitude = 0f,
                rateOfChange = 0f,
                noiseFloor = noiseFloor,
                isPhoneMoving = true,
                isRoomAttuned = false,
                timestamp = nowMs
            )
            return
        }

        // Phase 4: Stationary Phone Detecting Moving Magnetic Objects
        // Phone is resting flat on a surface or held steady in pocket.
        // Any change in the magnetic field is strictly an external moving magnet!
        val dx = rawX - baseBx
        val dy = rawY - baseBy
        val dz = rawZ - baseBz
        val vectorDelta = sqrt(dx * dx + dy * dy + dz * dz)

        // Compute total scalar flux density magnitude
        val currentScalar = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
        val baseScalar = sqrt(baseBx * baseBx + baseBy * baseBy + baseBz * baseBz)
        val scalarDelta = abs(currentScalar - baseScalar)

        // A moving magnet causes distortion in both vector and scalar field
        val deltaMag = max(vectorDelta, scalarDelta)

        // Instantaneous rate of change dB/dt
        val dtSec = if (lastTimestampNs > 0) {
            val rawDt = (event.timestamp - lastTimestampNs) / 1_000_000_000f
            if (rawDt in 0.0005f..0.5f) rawDt else 0.01f
        } else {
            0.01f
        }
        val rateOfChange = abs(deltaMag - lastDeltaMagnitude) / dtSec
        lastTimestampNs = event.timestamp
        lastDeltaMagnitude = deltaMag

        val threshold = calculateEffectiveThreshold()

        val absDy = abs(dy)
        val absDx = abs(dx)
        val absDz = abs(dz)

        // Y-Axis Noise floor calculation during quiet ambient periods
        if (absDy < threshold * 0.5f) {
            recentYDeltas[yDeltaIndex % recentYDeltas.size] = absDy
            yDeltaIndex++
            var sumY = 0f
            for (d in recentYDeltas) sumY += d
            yNoiseFloor = max(0.08f, sumY / recentYDeltas.size)
        }

        // Overall 3D Noise floor calculation
        if (deltaMag < threshold * 0.6f) {
            recentDeltas[deltaIndex % recentDeltas.size] = deltaMag
            deltaIndex++
            var sum = 0f
            for (d in recentDeltas) sum += d
            noiseFloor = max(0.10f, sum / recentDeltas.size)
        }

        // Dynamic Baseline Adaptation using an Exponential Moving Average (EMA) filter:
        // Tau controls the adaptation rate dynamically based on magnetic activity:
        // 1. Actively moving magnet (rateOfChange >= 0.35 uT/s):
        //    tau = 30.0s — Keeps the baseline steady so fast hand sweeps are not absorbed or missed.
        // 2. Stationary magnet holding still nearby (deltaMag > threshold * 0.40f && rateOfChange < 0.20 uT/s):
        //    tau = 3.5s — Gradually adapts the baseline to the new local DC offset. This completely
        //    eliminates continuous endless vibrations when an earbud or prop rests near the phone.
        // 3. Normal quiet ambient background (deltaMag <= threshold * 0.40f):
        //    tau = 2.0s — Continuously eliminates baseline drift, sensor temperature drift, and slow room fluctuations.
        val isActivelyMoving = rateOfChange >= 0.35f
        val isStationaryMagnet = deltaMag > (threshold * 0.40f) && !isActivelyMoving

        val tauSec = when {
            isActivelyMoving -> 30.0f
            isStationaryMagnet -> 3.5f
            else -> 2.0f
        }
        val emaAlpha = (1.0f - exp(-dtSec / tauSec)).coerceIn(0.0001f, 0.90f)

        baseBx = (1f - emaAlpha) * baseBx + emaAlpha * rawX
        baseBy = (1f - emaAlpha) * baseBy + emaAlpha * rawY
        baseBz = (1f - emaAlpha) * baseBz + emaAlpha * rawZ

        // Append sample with vertical Y-axis and lateral axes for strict Y-axis analysis
        sampleHistory.add(FluxHistorySample(nowMs, dy, dx, dz, deltaMag))
        while (sampleHistory.size > 140 || (sampleHistory.isNotEmpty() && nowMs - sampleHistory.first().timeMs > 2400L)) {
            sampleHistory.removeAt(0)
        }

        // Calculate Scent Strength (0 to 100%) mapped against perimeter threshold
        val rawPercent = ((deltaMag / max(0.6f, threshold * 1.5f)) * 100f).toInt().coerceIn(0, 100)
        val scentStrengthPercent = rawPercent

        val estimatedDistanceCm = when {
            deltaMag < 0.35f -> -1
            deltaMag > 15.0f -> 5
            deltaMag > 7.0f -> 12
            deltaMag > 3.5f -> 22
            deltaMag > 1.8f -> 32
            deltaMag > 0.8f -> 45
            else -> 50
        }

        // Target Strength Filter (user-configured min..max percentage)
        val isInTargetWindow = scentStrengthPercent in targetMinStrengthPercent..targetMaxStrengthPercent

        // Movement evaluation based on active LocatorMode (0-50cm, Earbuds, or Y-Axis)
        val (isStrike, currentGesturePhase) = when (_locatorMode.value) {
            LocatorMode.Y_AXIS_VERTICAL -> {
                evaluateYAxisArcGesture(nowMs, threshold)
            }
            LocatorMode.EARBUD_DETECTOR -> {
                val moving = (deltaMag >= threshold && (rateOfChange >= 0.40f || deltaMag >= threshold * 1.25f))
                val triggered = moving && isInTargetWindow
                val phase = when {
                    triggered -> GesturePhase.HAND_ARC_CONFIRMED
                    deltaMag >= threshold * 0.70f -> GesturePhase.HAND_RISING
                    else -> GesturePhase.IDLE
                }
                Pair(triggered, phase)
            }
            LocatorMode.PROXIMITY_50CM -> {
                val moving = (deltaMag >= threshold && (rateOfChange >= 0.35f || deltaMag >= threshold * 1.20f))
                val triggered = moving && isInTargetWindow
                val phase = when {
                    triggered -> GesturePhase.HAND_ARC_CONFIRMED
                    deltaMag >= threshold * 0.65f -> GesturePhase.HAND_RISING
                    else -> GesturePhase.IDLE
                }
                Pair(triggered, phase)
            }
        }

        val scentStatusText = when {
            !_isPoweredOn.value -> "The goblin sleeps."
            isDevicePhysicallyMoving -> "Ignoring phone movement"
            isStrike -> "Movement Detected!"
            currentGesturePhase == GesturePhase.HAND_RISING || deltaMag >= threshold * 0.70f -> "Strong Scent ($scentStrengthPercent%)"
            deltaMag >= threshold * 0.35f -> "Faint Scent ($scentStrengthPercent%)"
            else -> "The goblin sleeps."
        }

        // Determine Creature State
        val newState = when {
            isStrike -> CreatureState.STRIKING
            scentStrengthPercent >= 65 || deltaMag >= threshold * 0.70f -> CreatureState.AWAKE
            scentStrengthPercent >= 25 || deltaMag >= threshold * 0.35f -> CreatureState.STIRRING
            else -> CreatureState.SLUMBERING
        }
        _creatureState.value = newState

        val reading = MagneticReading(
            x = rawX,
            y = rawY,
            z = rawZ,
            baselineX = baseBx,
            baselineY = baseBy,
            baselineZ = baseBz,
            deltaX = dx,
            deltaY = dy,
            deltaZ = dz,
            deltaMagnitude = deltaMag,
            rateOfChange = rateOfChange,
            noiseFloor = if (_locatorMode.value == LocatorMode.Y_AXIS_VERTICAL) yNoiseFloor else noiseFloor,
            isPhoneMoving = isDevicePhysicallyMoving,
            isRoomAttuned = true,
            gesturePhase = currentGesturePhase,
            isHandArcDetected = isStrike,
            scentStrengthPercent = scentStrengthPercent,
            scentStatusText = scentStatusText,
            estimatedDistanceCm = estimatedDistanceCm,
            timestamp = nowMs
        )
        _readingState.value = reading

        // Emit strike event for haptic cue (debounced)
        if (isStrike) {
            if (nowMs - lastStrikeTimeMs >= debounceMs) {
                lastStrikeTimeMs = nowMs
                externalScope.launch(Dispatchers.Default) {
                    _strikeEvents.emit(reading)
                }
            }
        }
    }

    /**
     * Specifically evaluates vertical Y-axis movement (10-30cm up and down).
     * Eliminates ghost movement triggers by requiring:
     * 1. Y-axis peak exceeds the robust anti-ghost threshold (>= 0.60 uT).
     * 2. Y-axis is dominant over lateral X-axis and Z-axis, ensuring purely vertical movement.
     * 3. Symmetrical return to baseline within human hand motion duration (350ms - 1850ms).
     * 4. Exceeds Y-axis noise floor by at least 2.5x SNR.
     */
    private fun evaluateYAxisArcGesture(nowMs: Long, threshold: Float): Pair<Boolean, GesturePhase> {
        if (sampleHistory.size < 10) return Pair(false, GesturePhase.IDLE)

        val latest = sampleHistory.last()
        val latestDy = abs(latest.dy)

        // Consider recent 1900ms window
        val cutoff = nowMs - 1900L
        val recent = sampleHistory.filter { it.timeMs >= cutoff }
        if (recent.size < 8) return Pair(false, GesturePhase.IDLE)

        // Find peak vertical excursion (|dy|) in recent window
        var peakIdx = -1
        var peakDy = -1f
        for (i in recent.indices) {
            val s = recent[i]
            val dyMag = abs(s.dy)
            if (dyMag > peakDy) {
                peakDy = dyMag
                peakIdx = i
            }
        }

        if (peakIdx == -1) return Pair(false, GesturePhase.IDLE)
        val peakSample = recent[peakIdx]
        val peakAge = nowMs - peakSample.timeMs

        // Dynamic Gesture Phase indication for live HUD/visual feedback
        val currentPhase = when {
            latestDy < threshold * 0.35f -> GesturePhase.IDLE
            peakAge < 150L && latestDy >= threshold * 0.70f -> GesturePhase.HAND_APEX
            latestDy >= threshold * 0.40f && latestDy > abs(recent.first().dy) -> GesturePhase.HAND_RISING
            latestDy >= threshold * 0.30f && latestDy < peakDy -> GesturePhase.HAND_RETURNING
            else -> GesturePhase.IDLE
        }

        // 1. Anti-Ghost Check: Must cross robust Y-axis threshold
        if (peakDy < threshold) return Pair(false, currentPhase)

        // 2. Anti-Ghost SNR Check: Must exceed Y noise floor significantly
        if (peakDy < yNoiseFloor * 2.5f) return Pair(false, currentPhase)

        // 3. Strict Y-Axis Restriction:
        // Must be predominantly on the Y-axis (vertical up/down), NOT sideways (X) or forward/back (Z)
        val peakDx = abs(peakSample.dx)
        val peakDz = abs(peakSample.dz)
        val isYDominant = peakDy >= (peakDx * 1.25f) && peakDy >= (peakDz * 1.05f)
        if (!isYDominant) {
            // Horizontal or non-vertical motion rejected!
            return Pair(false, currentPhase)
        }

        // Peak must not be at the very latest sample (it must have completed apex and returned)
        if (peakIdx >= recent.size - 2) return Pair(false, currentPhase)
        if (peakAge !in 100L..950L) return Pair(false, currentPhase)

        // Find baseline on Y before peak (where the hand started moving up)
        var startIdx = -1
        var minBeforePeak = Float.MAX_VALUE
        for (i in 0 until peakIdx) {
            val s = recent[i]
            val dtToPeak = peakSample.timeMs - s.timeMs
            val dyVal = abs(s.dy)
            if (dtToPeak in 100L..950L && dyVal < minBeforePeak) {
                minBeforePeak = dyVal
                startIdx = i
            }
        }
        if (startIdx == -1) return Pair(false, currentPhase)
        val startSample = recent[startIdx]

        // 4. Return-to-Baseline Checks:
        // Hand started near baseline
        val startDy = abs(startSample.dy)
        val isStartNearBase = startDy <= (peakDy * 0.45f) || startDy <= (threshold * 0.45f)
        if (!isStartNearBase) return Pair(false, currentPhase)

        // Hand returned back down to baseline
        val isEndNearBase = latestDy <= (peakDy * 0.45f) || latestDy <= (threshold * 0.50f)
        if (!isEndNearBase) return Pair(false, currentPhase)

        // 5. Human kinematic timing check (350ms to 1850ms)
        val totalDuration = nowMs - startSample.timeMs
        val riseDuration = peakSample.timeMs - startSample.timeMs
        val fallDuration = nowMs - peakSample.timeMs

        if (totalDuration !in 320L..1900L) return Pair(false, currentPhase)
        if (riseDuration !in 100L..1000L) return Pair(false, currentPhase)
        if (fallDuration !in 100L..1000L) return Pair(false, currentPhase)

        // Debounce confirmed gestures so one stroke triggers exactly once
        if (nowMs - lastConfirmedGestureTimeMs < 1200L) {
            return Pair(false, GesturePhase.HAND_ARC_CONFIRMED)
        }
        lastConfirmedGestureTimeMs = nowMs
        return Pair(true, GesturePhase.HAND_ARC_CONFIRMED)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
