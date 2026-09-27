package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.domain.model.CreatureState
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
    private var noiseFloor = 0.6f
    private val recentDeltas = FloatArray(16) { 0.2f }
    private var deltaIndex = 0

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
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
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
     * Calculates the dynamic threshold based on Room-Wide mode, Fog setting,
     * and the measured environmental noise floor.
     */
    fun calculateEffectiveThreshold(): Float {
        return if (isRoomWideMode) {
            // Room-wide faint motion detection:
            // Sub-microTesla threshold (0.18 uT - 0.45 uT) calibrated dynamically to noise floor
            val scaledFog = 0.12f + (0.30f * fogLevel * fogLevel)
            max(0.16f, scaledFog + (noiseFloor * 0.7f))
        } else {
            val fogScaled = customBaseThreshold * (0.4f + 3.2f * fogLevel * fogLevel)
            max(0.6f, fogScaled + noiseFloor * (1.0f + 1.5f * fogLevel))
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isPoweredOn.value) return

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
            // When the phone itself moves or is being placed flat on a table:
            // Fast adaptation follows the phone's new physical orientation in the room's magnetic field.
            val rapidAlpha = 0.35f
            baseBx = (1f - rapidAlpha) * baseBx + rapidAlpha * rawX
            baseBy = (1f - rapidAlpha) * baseBy + rapidAlpha * rawY
            baseBz = (1f - rapidAlpha) * baseBz + rapidAlpha * rawZ

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
            max(0.005f, (event.timestamp - lastTimestampNs) / 1_000_000_000f)
        } else {
            0.02f
        }
        val rateOfChange = abs(deltaMag - lastDeltaMagnitude) / dtSec
        lastTimestampNs = event.timestamp
        lastDeltaMagnitude = deltaMag

        val threshold = calculateEffectiveThreshold()

        // Noise floor calculation during quiet ambient periods
        if (deltaMag < threshold * 0.6f) {
            recentDeltas[deltaIndex % recentDeltas.size] = deltaMag
            deltaIndex++
            var sum = 0f
            for (d in recentDeltas) sum += d
            noiseFloor = max(0.06f, sum / recentDeltas.size)
        }

        // Dynamic Baseline Adaptation:
        // When quiet, slowly track room temperature/ambient drifts.
        // When an external magnetic disturbance is detected, freeze adaptation completely
        // so the moving magnet is never canceled out!
        val isMagnetPresent = deltaMag > (threshold * 0.45f)
        val adaptationAlpha = if (isMagnetPresent) 0.0f else 0.003f

        baseBx = (1f - adaptationAlpha) * baseBx + adaptationAlpha * rawX
        baseBy = (1f - adaptationAlpha) * baseBy + adaptationAlpha * rawY
        baseBz = (1f - adaptationAlpha) * baseBz + adaptationAlpha * rawZ

        // Trigger condition for room-wide and faint movement:
        // Trigger if delta magnitude reaches threshold, OR if there is an active magnetic rate transient (dB/dt)
        // indicating a moving magnetic device across the room
        val isStrike = deltaMag >= threshold || (deltaMag >= threshold * 0.65f && rateOfChange >= 0.85f)

        // Determine Creature State
        val newState = when {
            isStrike -> CreatureState.STRIKING
            deltaMag >= threshold * 0.70f || rateOfChange >= 0.60f -> CreatureState.AWAKE
            deltaMag >= threshold * 0.35f || rateOfChange >= 0.30f -> CreatureState.STIRRING
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
            noiseFloor = noiseFloor,
            isPhoneMoving = false,
            isRoomAttuned = true,
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
