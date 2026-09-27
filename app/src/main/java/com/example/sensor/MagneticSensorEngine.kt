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
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    // Current State Flow
    private val _readingState = MutableStateFlow(MagneticReading())
    val readingState: StateFlow<MagneticReading> = _readingState.asStateFlow()

    private val _creatureState = MutableStateFlow(CreatureState.SLUMBERING)
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

    // Baseline adaptation variables
    private var baseBx = 0f
    private var baseBy = 0f
    private var baseBz = 0f
    private var isBaselineInitialized = false

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
    @Volatile var fogLevel: Float = 0.35f // 0.0 (hypersensitive) to 1.0 (heavy stage shield)
    @Volatile var customBaseThreshold: Float = 3.0f // microTesla
    @Volatile var debounceMs: Long = 350L

    init {
        startListening()
    }

    fun startListening() {
        magnetometer?.let { sensor ->
            sensorManager?.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        proximitySensor?.let { sensor ->
            sensorManager?.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    /**
     * Instantly tares / recalibrates the baseline to current magnetic conditions.
     * Perfect for resetting room magnetism before or during a routine.
     */
    fun tareBaseline() {
        val current = _readingState.value
        baseBx = current.x
        baseBy = current.y
        baseBz = current.z
        isBaselineInitialized = true
        _creatureState.value = CreatureState.SLUMBERING
    }

    /**
     * Calculates the dynamic threshold based on the Fog slider setting
     * and the measured environmental noise floor.
     */
    fun calculateEffectiveThreshold(): Float {
        // Fog maps exponentially from subtle detection (~1.0 uT) to dense shield (~16.0 uT)
        val fogScaled = customBaseThreshold * (0.4f + 3.2f * fogLevel * fogLevel)
        // Add dynamic environmental noise floor margin
        return max(0.8f, fogScaled + noiseFloor * (1.0f + 1.5f * fogLevel))
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val dist = event.values[0]
            val maxRange = event.sensor.maximumRange
            _isPocketCovered.value = dist < 2.5f || (dist < maxRange && maxRange <= 5.0f)
            return
        }

        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD ||
            event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
        ) {
            processMagneticEvent(event)
        }
    }

    private fun processMagneticEvent(event: SensorEvent) {
        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]

        if (!isBaselineInitialized) {
            baseBx = rawX
            baseBy = rawY
            baseBz = rawZ
            isBaselineInitialized = true
        }

        // Compute delta vector from adaptive baseline
        val dx = rawX - baseBx
        val dy = rawY - baseBy
        val dz = rawZ - baseBz
        val deltaMag = sqrt(dx * dx + dy * dy + dz * dz)

        // Instantaneous rate of change dB/dt
        val nowMs = System.currentTimeMillis()
        val dtSec = if (lastTimestampNs > 0) {
            max(0.005f, (event.timestamp - lastTimestampNs) / 1_000_000_000f)
        } else {
            0.02f
        }
        val rateOfChange = abs(deltaMag - lastDeltaMagnitude) / dtSec
        lastTimestampNs = event.timestamp
        lastDeltaMagnitude = deltaMag

        // Update rolling noise floor buffer during calm states
        val threshold = calculateEffectiveThreshold()
        if (deltaMag < threshold * 0.6f) {
            recentDeltas[deltaIndex % recentDeltas.size] = deltaMag
            deltaIndex++
            var sum = 0f
            for (d in recentDeltas) sum += d
            noiseFloor = max(0.3f, sum / recentDeltas.size)
        }

        // Dynamic Room Baseline Adaptation:
        // When quiet, slowly absorb environmental drifts.
        // When active magnetic change is present, freeze/slow adaptation so the magnet isn't canceled out!
        val isDisturbed = deltaMag > (threshold * 0.5f)
        val alpha = if (isDisturbed) {
            0.0005f // Nearly frozen while magnet moves near phone
        } else {
            0.035f // Smooth adaptive tracking of room background
        }

        baseBx = (1f - alpha) * baseBx + alpha * rawX
        baseBy = (1f - alpha) * baseBy + alpha * rawY
        baseBz = (1f - alpha) * baseBz + alpha * rawZ

        // Determine Creature State
        val newState = when {
            deltaMag >= threshold -> CreatureState.STRIKING
            deltaMag >= threshold * 0.70f -> CreatureState.AWAKE
            deltaMag >= threshold * 0.35f -> CreatureState.STIRRING
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
            timestamp = nowMs
        )
        _readingState.value = reading

        // Check strike trigger for haptics
        if (deltaMag >= threshold) {
            if (nowMs - lastStrikeTimeMs >= debounceMs) {
                lastStrikeTimeMs = nowMs
                externalScope.launch(Dispatchers.Default) {
                    _strikeEvents.emit(reading)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Uncalibrated / Calibrated accuracy change
    }
}
