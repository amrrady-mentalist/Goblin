package com.example.domain.model

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The three detection styles the detector understands internally. The app no longer shows
 * them as presets: Magnetic Power picks the "still near" check and the Up/Down switch picks
 * the vertical-only style.
 *
 * STRONG_MAGNET    -- a movement in the field triggers the cue, and a second
 *                     "still near" check then tells you whether the object stays close.
 * WEAK_MAGNET      -- any movement in the field triggers the cue. Nothing else.
 * WEAK_MAGNET_UP_DOWN -- same, but only when the change is mainly vertical.
 */
enum class LocatorMode(val label: String, val shortName: String, val description: String) {
    STRONG_MAGNET(
        "Strong Magnet",
        "Strong",
        "Watch charger, fridge magnet or similar. Any movement in the field triggers the cue, then a second check tells you whether the object is still close."
    ),
    WEAK_MAGNET(
        "Weak Magnet",
        "Weak",
        "Earbud or similar. Any movement in the magnetic field triggers the cue, from any direction."
    ),
    WEAK_MAGNET_UP_DOWN(
        "Weak Magnet – Up/Down",
        "Weak Up/Down",
        "Earbud or similar. Only a change caused by the hand moving up or down triggers the cue; sideways movement is ignored."
    );

    companion object {
        /** Reads a stored name, including the three names used before the presets existed. */
        fun fromStored(name: String?): LocatorMode = when (name) {
            "STRONG_MAGNET" -> STRONG_MAGNET
            "WEAK_MAGNET", "PROXIMITY_50CM", "EARBUD_DETECTOR" -> WEAK_MAGNET
            "WEAK_MAGNET_UP_DOWN", "Y_AXIS_VERTICAL" -> WEAK_MAGNET_UP_DOWN
            else -> WEAK_MAGNET
        }
    }
}

enum class GesturePhase(val label: String) {
    IDLE("The goblin sleeps."),
    HAND_RISING("Scent rising..."),
    HAND_APEX("Apex reached"),
    HAND_RETURNING("Returning..."),
    HAND_ARC_CONFIRMED("Movement detected!")
}

enum class CreatureState(val label: String, val subtitle: String) {
    DORMANT("Powered Off", "Sensors in standby. Tap Power to turn on."),
    CALIBRATING("Attuning to Room", "Learning ambient room magnetism..."),
    SLUMBERING("Armed & Guarding", "Attuned to room baseline. Waiting for magnetic object."),
    STIRRING("Stirring", "Faint magnetic movement detected in the room."),
    AWAKE("Awake", "Active magnetic movement nearby."),
    STRIKING("Striking!", "Magnetic movement detected! Alerting performer.")
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
    val gesturePhase: GesturePhase = GesturePhase.IDLE,
    val isHandArcDetected: Boolean = false,
    val scentStrengthPercent: Int = 0,
    val scentStatusText: String = "Dormant",
    val estimatedDistanceCm: Int = -1,
    // The tolerance that was in force when this reading was produced (base
    // threshold plus the motion allowance). Carried with the reading so a strike
    // is classified weak/strong against the threshold that actually triggered it.
    val thresholdUsed: Float = 0f,
    val isStillNear: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalFieldMagnitude: Float
        get() = sqrt(x * x + y * y + z * z)

    val isYAxisDominant: Boolean
        get() = abs(deltaY) > abs(deltaX) && abs(deltaY) > abs(deltaZ)

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

/** How a cue feels. Used for the main detection cue and the "still near" second cue. */
enum class VibePattern(val label: String) {
    PULSES("Pulses"),
    BUZZ("Buzz"),
    STUTTER("Stutter"),
    TICK("Tick"),
    OFF("Off")
}

// Kept only because the saved venue profiles in the database refer to them. The app's
// vibration choices are now VibePattern plus the Vibration settings.
enum class HapticFeedbackType(val displayName: String, val description: String) {
    DOUBLE_STRONG("2 Strong Pulses (Pocket)", "Two unmistakable heavy vibrations designed for deep pocket detection."),
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
