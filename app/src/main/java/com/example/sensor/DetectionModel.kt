package com.example.sensor

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Turns the four plain-language sliders into the numbers the detector needs, and back.
 * Pure Kotlin (no Android) so it can be tested on a desktop.
 *
 * The physics, simplified: a magnet's field falls off roughly with the CUBE of the
 * distance. So three things decide how big a change the phone will see:
 *  - Magnetic Power: how strong the object is (its field at 10 cm).
 *  - Territory: how far away the object is.
 *  - Magnet Movement: how far the object moves (a shiver or a swing).
 * The trigger level is set to about half of that expected change, but never below what
 * the phone's own sensor noise allows.
 *
 * Every slider is 0..1 here. All constants are estimates; calibration anchors Magnetic
 * Power to the real object, which absorbs most of the error.
 */
object DetectionModel {

    // Ranges the sliders cover
    const val TERRITORY_MIN_CM = 10f
    const val TERRITORY_MAX_CM = 80f
    const val POWER_MIN_UT = 2f        // earbud-class magnet, field at 10 cm
    const val POWER_MAX_UT = 300f      // strong neodymium, field at 10 cm
    const val MOVE_MIN_CM = 1f         // a shiver
    const val MOVE_MAX_CM = 30f        // a swing

    // Model constants
    const val REF_CM = 10f
    const val NEAR_CM = 8f             // how close the object comes to the phone in calibration
    const val FAR_CM = 40f             // where it starts from
    const val CLOSEST_CM = 5f          // no closer than this is ever assumed
    const val PROJECTION = 0.6f        // only part of a field change shows in total field strength
    const val TRIGGER_FRACTION = 0.5f  // trigger at about half the expected change
    const val NOISE_MARGIN = 3f
    const val MIN_THRESHOLD_UT = 0.35f
    const val REACH_MARGIN = 1.2f

    /** Above this Magnetic Power the "still near" check is switched on automatically. */
    const val STRONG_POWER = 0.55f

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun unlerp(a: Float, b: Float, v: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)

    fun radiusCm(territory: Float) = lerp(TERRITORY_MIN_CM, TERRITORY_MAX_CM, territory)
    fun territoryFromCm(cm: Float) = unlerp(TERRITORY_MIN_CM, TERRITORY_MAX_CM, cm)

    fun moveCm(movement: Float) = lerp(MOVE_MIN_CM, MOVE_MAX_CM, movement)

    /** Field (µT) the object makes at 10 cm. Logarithmic, because strengths span ~150x. */
    fun refFieldUt(power: Float): Float =
        POWER_MIN_UT * exp(power.coerceIn(0f, 1f) * ln(POWER_MAX_UT / POWER_MIN_UT))

    fun powerFromRefField(ut: Float): Float =
        (ln(ut.coerceAtLeast(POWER_MIN_UT) / POWER_MIN_UT) / ln(POWER_MAX_UT / POWER_MIN_UT)).coerceIn(0f, 1f)

    private fun fieldAt(refUt: Float, cm: Float) = refUt * (REF_CM / cm.coerceAtLeast(CLOSEST_CM)).pow(3)

    /** The change in total field (µT) when the object moves [moveCm] closer, from [distanceCm]. */
    fun expectedChange(refUt: Float, distanceCm: Float, moveCm: Float): Float {
        val nearer = max(distanceCm - moveCm, CLOSEST_CM)
        return PROJECTION * max(0f, fieldAt(refUt, nearer) - fieldAt(refUt, distanceCm))
    }

    /** The smallest trigger the phone's own noise allows. */
    fun floorUt(noiseFloor: Float) = max(MIN_THRESHOLD_UT, noiseFloor * NOISE_MARGIN)

    fun threshold(territory: Float, power: Float, movement: Float, noiseFloor: Float): Float {
        val change = expectedChange(refFieldUt(power), radiusCm(territory), moveCm(movement))
        return max(floorUt(noiseFloor), TRIGGER_FRACTION * change)
    }

    /** False when the sliders ask for something smaller than the phone's noise can show. */
    fun isReachable(territory: Float, power: Float, movement: Float, noiseFloor: Float): Boolean {
        val change = expectedChange(refFieldUt(power), radiusCm(territory), moveCm(movement))
        return TRIGGER_FRACTION * change >= floorUt(noiseFloor)
    }

    /** The farthest Territory (0..1) that still works for this object and movement. */
    fun maxReliableTerritory(power: Float, movement: Float, noiseFloor: Float): Float {
        val ref = refFieldUt(power)
        val d = moveCm(movement)
        val need = REACH_MARGIN * floorUt(noiseFloor)
        var cm = TERRITORY_MAX_CM
        while (cm > TERRITORY_MIN_CM) {
            if (TRIGGER_FRACTION * expectedChange(ref, cm, d) >= need) return territoryFromCm(cm)
            cm -= 1f
        }
        return 0f
    }

    /**
     * Magnetic Power (0..1) from a calibration pass: the object is brought right up to the
     * phone (about [NEAR_CM]) from a distance, and [levelPeak] is how far the field level
     * moved at the most.
     */
    fun powerFromCalibration(levelPeak: Float): Float {
        val geometry = PROJECTION * ((REF_CM / NEAR_CM).pow(3) - (REF_CM / FAR_CM).pow(3))
        return powerFromRefField(levelPeak / geometry)
    }

    /** Phone Movement 0..100 ("Strict" to "Relaxed") as the detector's 1..20 adaptive value. */
    fun adaptiveFromPhoneMovement(percent: Int): Int =
        (20f - 19f * percent.coerceIn(0, 100) / 100f).roundToInt().coerceIn(1, 20)

    fun usesStillNearCheck(power: Float) = power >= STRONG_POWER

    /** One short line for under the sliders. */
    fun summary(territory: Float, power: Float, movement: Float, noiseFloor: Float): String {
        val move = moveCm(movement)
        val reach = radiusCm(territory).roundToInt()
        return if (isReachable(territory, power, movement, noiseFloor)) {
            "Catches a ${move.roundToInt()} cm movement out to about $reach cm"
        } else {
            "Too ambitious for this phone: try a bigger movement, more power or less distance"
        }
    }
}
