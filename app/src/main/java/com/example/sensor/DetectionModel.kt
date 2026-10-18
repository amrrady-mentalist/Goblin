package com.example.sensor

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Turns the Sensitivity slider into the one number the detector needs (the trigger level),
 * and works that number out from a calibration pass. Pure Kotlin (no Android) so it can be
 * tested on a desktop.
 *
 * Sensitivity 0..1 is a log scale between two ends:
 *  - 0 = only a big change counts ([HIGH_TRIGGER_UT] or more).
 *  - 1 = the smallest change this phone's own noise allows to be trusted.
 *
 * Calibration: the object is brought up to the phone from about [FAR_CM] to [NEAR_CM] and the
 * field level moves by [levelPeak]. From that the object's strength is estimated (a magnet's
 * field falls off with the cube of the distance), and the trigger level is set so a modest
 * movement of that object at a middling distance would still be caught. All constants are
 * estimates; anchoring on the real object absorbs most of the error.
 */
object DetectionModel {

    const val HIGH_TRIGGER_UT = 30f    // trigger level at Sensitivity 0
    const val MIN_THRESHOLD_UT = 0.35f
    const val NOISE_MARGIN = 3f

    // Calibration geometry
    const val REF_CM = 10f
    const val NEAR_CM = 8f             // how close the object comes to the phone in calibration
    const val FAR_CM = 40f             // where it starts from
    const val CLOSEST_CM = 5f
    const val PROJECTION = 0.6f        // only part of a field change shows in total field strength
    const val TRIGGER_FRACTION = 0.5f  // trigger at about half the expected change
    const val TARGET_DISTANCE_CM = 30f // "a middling distance" the calibrated level should still catch
    const val TARGET_MOVE_CM = 10f     // ...for a modest movement of the object
    const val REACH_MARGIN = 1.2f

    /** Objects at least this strong (field at 10 cm, µT) get the "still near" check switched on. */
    const val STRONG_REF_UT = 30f

    private fun fieldAt(refUt: Float, cm: Float) = refUt * (REF_CM / cm.coerceAtLeast(CLOSEST_CM)).pow(3)

    private fun expectedChange(refUt: Float, distanceCm: Float, moveCm: Float): Float {
        val nearer = max(distanceCm - moveCm, CLOSEST_CM)
        return PROJECTION * max(0f, fieldAt(refUt, nearer) - fieldAt(refUt, distanceCm))
    }

    /** The smallest trigger the phone's own noise allows. */
    fun floorUt(noiseFloor: Float) = max(MIN_THRESHOLD_UT, noiseFloor * NOISE_MARGIN)

    private fun highUt(noiseFloor: Float) = max(HIGH_TRIGGER_UT, floorUt(noiseFloor) * 4f)

    /** Trigger level (µT) for a Sensitivity value 0..1. */
    fun threshold(sensitivity: Float, noiseFloor: Float): Float {
        val lo = floorUt(noiseFloor)
        val hi = highUt(noiseFloor)
        return hi * (lo / hi).pow(sensitivity.coerceIn(0f, 1f))
    }

    /** The Sensitivity value 0..1 that gives [thresholdUt]. */
    fun sensitivityFromThreshold(thresholdUt: Float, noiseFloor: Float): Float {
        val lo = floorUt(noiseFloor)
        val hi = highUt(noiseFloor)
        val t = thresholdUt.coerceIn(lo, hi)
        return (ln(hi / t) / ln(hi / lo)).coerceIn(0f, 1f)
    }

    /** Estimated field of the object at 10 cm (µT) from a calibration pass. */
    fun refFieldFromCalibration(levelPeak: Float): Float {
        val geometry = PROJECTION * ((REF_CM / NEAR_CM).pow(3) - (REF_CM / FAR_CM).pow(3))
        return levelPeak / geometry
    }

    /** The trigger level a calibration pass suggests (µT), never below what noise allows. */
    fun thresholdFromCalibration(levelPeak: Float, noiseFloor: Float): Float {
        val ref = refFieldFromCalibration(levelPeak)
        val change = expectedChange(ref, TARGET_DISTANCE_CM, TARGET_MOVE_CM)
        return max(floorUt(noiseFloor) * REACH_MARGIN, TRIGGER_FRACTION * change)
    }

    /** True when a calibrated object is strong enough that the "still near" check makes sense. */
    fun isStrongObject(levelPeak: Float) = refFieldFromCalibration(levelPeak) >= STRONG_REF_UT

    /** Phone Movement 0..100 ("Strict" to "Relaxed") as the detector's 1..20 adaptive value. */
    fun adaptiveFromPhoneMovement(percent: Int): Int =
        (20f - 19f * percent.coerceIn(0, 100) / 100f).roundToInt().coerceIn(1, 20)

    /** One short line for under the slider. */
    fun summary(sensitivity: Float, noiseFloor: Float): String {
        val t = threshold(sensitivity, noiseFloor)
        val text = if (t >= 10f) t.roundToInt().toString() else ((t * 10f).roundToInt() / 10f).toString()
        return "Reacts to a field change of about $text µT"
    }
}
