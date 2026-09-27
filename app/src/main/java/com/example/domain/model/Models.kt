package com.example.domain.model

import kotlin.math.abs
import kotlin.math.sqrt

enum class CreatureState(val label: String, val subtitle: String) {
    DORMANT("Powered Off", "Sensors in standby. Tap Power to turn on."),
    CALIBRATING("Attuning to Room", "Learning ambient room magnetism..."),
    SLUMBERING("Armed & Guarding", "Attuned to room baseline. Waiting for magnetic object."),
    STIRRING("Stirring", "Faint magnetic movement detected in the room."),
    AWAKE("Awake", "Active magnetic movement nearby."),
    STRIKING("Striking!", "Magnetic movement detected! Alerting performer.")
}

enum class HapticFeedbackType(val displayName: String, val description: String) {
    GHOST_TAP("Ghost Tap", "Microscopic tactile click. Imperceptible to spectators, felt in pocket."),
    HEARTBEAT("Creature Heartbeat", "Dual organic thud that intensifies with speed of approach."),
    DYNAMIC_PURR("Flux Purr", "Continuous vibration scaled to instantaneous magnetic rate."),
    SHARP_STRIKE("Sharp Strike", "Crisp, unmistakable pulse for loud venues and walk-around."),
    GEIGER_PULSE("Geiger Ticks", "Rapid rhythmic clicks accelerating as the magnet sweeps closer."),
    MUTE("Silent / Visual Only", "Mute all vibrations for visual practice or covert screen viewing.")
}

enum class VibrationStrength(val label: String, val multiplier: Float) {
    SUBTLE("Subtle (Pocket / Covert)", 0.6f),
    MEDIUM("Standard (Performer)", 1.0f),
    PRONOUNCED("Pronounced (Heavy Fabric)", 1.5f)
}

enum class MagneticDirection(val label: String, val hint: String) {
    TOP_EDGE("Top Edge", "Magnet moved near top speaker/camera"),
    BOTTOM_EDGE("Bottom Edge", "Magnet passed charging port / base"),
    RIGHT_EDGE("Right Edge", "Magnet swept along right side"),
    LEFT_EDGE("Left Edge", "Magnet swept along left side"),
    BACK_FACE("Back Face", "Magnet approached rear of phone"),
    SCREEN_FACE("Screen Face", "Magnet approached screen display"),
    OMNI("Ambient / Distant", "Uniform magnetic shift")
}

data class MagneticReading(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val baselineX: Float = 0f,
    val baselineY: Float = 0f,
    val baselineZ: Float = 0f,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val deltaZ: Float = 0f,
    val deltaMagnitude: Float = 0f,
    val rateOfChange: Float = 0f,
    val noiseFloor: Float = 0.5f,
    val isPhoneMoving: Boolean = false,
    val isRoomAttuned: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalFieldMagnitude: Float
        get() = sqrt(x * x + y * y + z * z)

    val dominantDirection: MagneticDirection
        get() {
            val ax = abs(deltaX)
            val ay = abs(deltaY)
            val az = abs(deltaZ)
            val maxAxis = maxOf(ax, ay, az)
            if (maxAxis < 0.4f) return MagneticDirection.OMNI

            return when (maxAxis) {
                ax -> if (deltaX > 0) MagneticDirection.RIGHT_EDGE else MagneticDirection.LEFT_EDGE
                ay -> if (deltaY > 0) MagneticDirection.TOP_EDGE else MagneticDirection.BOTTOM_EDGE
                else -> if (deltaZ > 0) MagneticDirection.SCREEN_FACE else MagneticDirection.BACK_FACE
            }
        }
}
