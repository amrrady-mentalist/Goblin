package com.example.sensor

import com.example.domain.model.LocatorMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The detection maths, with no Android classes in it so it can be unit-tested with
 * recorded or synthetic sensor data.
 *
 * Detection is V1's, unchanged: every raw sample through a 0.25 low-pass, and the change
 * is the peak-to-peak span of the smoothed field strength over the last 400 ms.
 *
 * What replaces V1's "phone is moving, stop detecting" gate (idea from X-Vision):
 *  - A tolerance that RISES INSTANTLY with phone movement and FALLS SLOWLY afterwards.
 *    Movement makes the detector harder to trigger; it never makes it blind or throws
 *    away data, so a magnet can still be read while the phone is being moved.
 *  - Movement is measured from the accelerometer AND the gyro (how far the phone has
 *    turned lately), because tilting a phone in place changes the field but barely
 *    changes accelerometer magnitude.
 *  - Strong Magnet preset: a second "still near" check against a reference level that
 *    is frozen from just BEFORE the trigger.
 *
 * Threading: everything except the @Volatile settings is touched from one thread only
 * (the sensor thread).
 */
class FieldDetector {

    enum class HoldEnd { PASS_BY, GONE, PHONE_MOVED, TIMEOUT }

    /** The result of one processed 40 ms step. */
    data class Step(
        val timeMs: Long,
        val disturbance: Float,
        val threshold: Float,
        val isTrigger: Boolean,
        val stillNearConfirmed: Boolean,
        val holdEnded: HoldEnd?,
        val isAttuned: Boolean,
        val motionAllowance: Float,
        val levelShift: Float,
        val smoothScalar: Float,
        val smoothX: Float,
        val smoothY: Float,
        val smoothZ: Float,
        val deltaX: Float,
        val deltaY: Float,
        val deltaZ: Float
    )

    // ---- Settings (written from other threads, read on the sensor thread) ----
    /** Only count a change that is mainly vertical (the Up/Down switch). */
    @Volatile var verticalOnly: Boolean = false

    /** Run the "is the object still close?" check after a detection (strong objects). */
    @Volatile var stillNearCheck: Boolean = false

    /**
     * Convenience for tests and older code: sets [verticalOnly] / [stillNearCheck] from one of
     * the old three presets.
     */
    var mode: LocatorMode
        get() = when {
            verticalOnly -> LocatorMode.WEAK_MAGNET_UP_DOWN
            stillNearCheck -> LocatorMode.STRONG_MAGNET
            else -> LocatorMode.WEAK_MAGNET
        }
        set(value) {
            verticalOnly = value == LocatorMode.WEAK_MAGNET_UP_DOWN
            stillNearCheck = value == LocatorMode.STRONG_MAGNET
        }

    /**
     * The size of change (µT) that counts as a detection when the phone is still. When 0,
     * it is worked out from [sensitivity]; the app sets it from Territory, Magnetic Power
     * and Magnet Movement (see [DetectionModel]).
     */
    @Volatile var baseThresholdUt: Float = 0f
    @Volatile var sensitivity: Float = 1.9f
    @Volatile var adaptiveSensitivity: Int = 8

    /** Peak level shift from the last object calibration; 0 = never calibrated. */
    @Volatile var calibratedLevelPeak: Float = 0f

    /** The engine sets this false while haptics are playing, calibrating, paused, etc. */
    @Volatile var allowTrigger: Boolean = true

    // ---- Read-only state ----
    var isAttuned = false
        private set
    var noiseFloor = 0.15f
        private set
    var isStillNear = false
        private set
    val motionAllowance: Float get() = allowance

    /** Strong Magnet only works if the calibrated object shifts the level enough. */
    fun isConfirmationUsable(): Boolean =
        calibratedLevelPeak <= 0f || calibratedLevelPeak >= USABLE_LEVEL_PEAK

    fun confirmMargin(): Float {
        val peak = calibratedLevelPeak
        return if (peak > 0f) max(MIN_LEVEL_MARGIN, peak * CALIBRATED_MARGIN_FRACTION)
        else DEFAULT_LEVEL_MARGIN
    }

    fun baseThreshold(): Float {
        val fixed = baseThresholdUt
        return if (fixed > 0f) max(0.35f, fixed) else max(0.35f, 3.8f / sensitivity.coerceAtLeast(0.1f))
    }

    // ---- Inertial inputs ----
    private var lastAccelMs = -1_000_000L
    private var lastAccelDev = 0f

    private class RotSample(val timeMs: Long, val angle: Float)
    private val rotWindow = ArrayDeque<RotSample>()
    private var rotWindowSum = 0f
    private var rotTotal = 0f
    private var lastGyroMs = -1L

    fun onAccelerometer(timeMs: Long, ax: Float, ay: Float, az: Float) {
        val dev = abs(sqrt(ax * ax + ay * ay + az * az) - GRAVITY)
        lastAccelDev = if (dev < ACCEL_DEADBAND) 0f else dev - ACCEL_DEADBAND
        lastAccelMs = timeMs
    }

    fun onGyroscope(timeMs: Long, wx: Float, wy: Float, wz: Float) {
        val w = sqrt(wx * wx + wy * wy + wz * wz)
        val dtS = if (lastGyroMs < 0) 0f else ((timeMs - lastGyroMs) / 1000f).coerceIn(0f, 0.1f)
        lastGyroMs = timeMs
        val inc = if (w > GYRO_DEADBAND) w * dtS else 0f
        if (inc > 0f) {
            rotWindow.addLast(RotSample(timeMs, inc))
            rotWindowSum += inc
            rotTotal += inc
        }
        pruneRotation(timeMs)
    }

    private fun pruneRotation(nowMs: Long) {
        while (rotWindow.isNotEmpty() && rotWindow.first().timeMs < nowMs - ROT_WINDOW_MS) {
            rotWindowSum -= rotWindow.removeFirst().angle
        }
        if (rotWindow.isEmpty() || rotWindowSum < 0f) rotWindowSum = 0f
    }

    // ---- Magnetic input: V1's smoothing, unchanged ----
    // Every raw sample goes through the same low-pass filter V1 used (alpha 0.25 per
    // sample), and the change is the peak-to-peak span of that smoothed signal over the
    // last 400 ms. Only the phone-movement handling differs from V1 (see motion allowance).
    private var smoothInit = false
    private var smS = 0f
    private var smX = 0f
    private var smY = 0f
    private var smZ = 0f
    private var lastBinMs = -1L

    /**
     * Feed one raw magnetometer sample. [worldX]..[worldZ] is the reading rotated into a
     * fixed (gravity-anchored) frame when a rotation reference exists; pass the raw
     * values with [hasWorld] = false otherwise. Returns a [Step] for every sample.
     */
    fun onMagnetic(
        timeMs: Long,
        rawX: Float, rawY: Float, rawZ: Float,
        worldX: Float, worldY: Float, worldZ: Float,
        hasWorld: Boolean
    ): Step? {
        // A long silence (e.g. the sensor was paused): don't join old and new data.
        if (lastBinMs > 0 && timeMs - lastBinMs > GAP_MS) {
            clearHistory()
            smoothInit = false
        }
        val scalar = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
        if (!smoothInit) {
            smS = scalar; smX = worldX; smY = worldY; smZ = worldZ
            smoothInit = true
        } else {
            smS += SMOOTHING_ALPHA * (scalar - smS)
            smX += SMOOTHING_ALPHA * (worldX - smX)
            smY += SMOOTHING_ALPHA * (worldY - smY)
            smZ += SMOOTHING_ALPHA * (worldZ - smZ)
        }
        return processBin(timeMs, smS, smX, smY, smZ, hasWorld)
    }

    // ---- Windows ----
    private class Bin(val timeMs: Long, val scalar: Float, val x: Float, val y: Float, val z: Float)

    private val bins = ArrayDeque<Bin>()   // last few raw bins, for the short average
    private val maWin = ArrayDeque<Bin>()  // short-averaged series over the last WINDOW_MS

    private var allowance = 0f
    private var quietSum = 0f
    private var quietN = 0

    // Attunement
    private var attuneStartMs = -1L
    private var attuneSum = 0f
    private var attuneN = 0

    // Reference level (Strong Magnet only uses it, but it is cheap to keep always)
    private var refLevel = 0f
    private var hasRef = false
    private class RefPoint(val timeMs: Long, val value: Float)
    private val refHistory = ArrayDeque<RefPoint>()

    // Trigger re-arming
    private var armed = true
    private var quietSinceMs = -1L
    private var lastTriggerMs = -1_000_000L

    // Still-near hold
    private var holdActive = false
    private var holdConfirmed = false
    private var holdStartMs = 0L
    private var holdFrozenRef = 0f
    private var holdRotAtFreeze = 0f
    private var holdBelowSinceMs = -1L
    private var holdLevelShift = 0f

    // Object calibration capture (level shift relative to a reference frozen at the start)
    private var capturing = false
    private var capRef = 0f
    private var capPeakShift = 0f

    private var lastSmooth: Bin? = null

    /** Forget the averaged history but keep the bin currently being filled. */
    private fun clearHistory() {
        bins.clear()
        maWin.clear()
        smoothInit = false
    }

    fun clearWindows() {
        clearHistory()
    }

    /** Called when samples were deliberately dropped (e.g. while the vibration motor runs). */
    fun markGap() = clearWindows()

    /** Begin (or restart) learning the room: a fresh second of stillness. */
    fun startAttunement() {
        isAttuned = false
        attuneStartMs = -1L
        attuneSum = 0f
        attuneN = 0
        endHoldSilently()
        clearWindows()
        allowance = 0f
    }

    /** Re-centre right now (the volume-key tare). */
    fun tare() {
        val s = lastSmooth
        if (s != null) {
            refLevel = s.scalar
            hasRef = true
            refHistory.clear()
        }
        endHoldSilently()
        clearWindows()
        isAttuned = true
        armed = true
        quietSinceMs = -1L
    }

    fun beginLevelCapture() {
        capturing = true
        capRef = refAt((lastSmooth?.timeMs ?: 0L) - FREEZE_LOOKBACK_MS)
        capPeakShift = 0f
    }

    fun endLevelCapture(): Float {
        capturing = false
        return capPeakShift
    }

    private fun endHoldSilently() {
        holdActive = false
        holdConfirmed = false
        isStillNear = false
        holdBelowSinceMs = -1L
    }

    private fun refAt(targetMs: Long): Float {
        if (!hasRef) return lastSmooth?.scalar ?: 0f
        var best: RefPoint? = null
        for (p in refHistory) {
            if (p.timeMs <= targetMs) best = p else break
        }
        return best?.value ?: (refHistory.firstOrNull()?.value ?: refLevel)
    }

    private fun processBin(
        t: Long, scalar: Float, wx: Float, wy: Float, wz: Float, hasWorld: Boolean
    ): Step {
        val dtS = if (lastBinMs > 0) ((t - lastBinMs) / 1000f).coerceIn(0.001f, 0.5f)
        else 0.005f
        lastBinMs = t

        // Already smoothed by onMagnetic (V1's filter); no further averaging.
        val ma = Bin(t, scalar, wx, wy, wz)
        lastSmooth = ma
        maWin.addLast(ma)
        while (maWin.isNotEmpty() && maWin.first().timeMs < t - WINDOW_MS) maWin.removeFirst()

        // Motion allowance: instant rise, slow fall.
        pruneRotation(t)
        val accelNow = if (t - lastAccelMs <= 250) lastAccelDev else 0f
        val motionTarget = ACCEL_UT_PER_MS2 * accelNow + ROT_UT_PER_RAD * rotWindowSum
        allowance = if (motionTarget >= allowance) motionTarget
        else max(motionTarget, allowance * exp(-dtS / ALLOWANCE_TAU_S))
        val adaptiveScale = (10f / adaptiveSensitivity.coerceIn(1, 20).toFloat()).coerceIn(0.5f, 10f)
        val threshold = baseThreshold() + allowance * adaptiveScale

        // Peak-to-peak span of the short-averaged series over the window.
        var minS = Float.MAX_VALUE; var maxS = -Float.MAX_VALUE
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (m in maWin) {
            if (m.scalar < minS) minS = m.scalar; if (m.scalar > maxS) maxS = m.scalar
            if (m.x < minX) minX = m.x; if (m.x > maxX) maxX = m.x
            if (m.y < minY) minY = m.y; if (m.y > maxY) maxY = m.y
            if (m.z < minZ) minZ = m.z; if (m.z > maxZ) maxZ = m.z
        }
        val enough = maWin.size >= MIN_SPAN_BINS
        val spanS = if (enough) maxS - minS else 0f
        val spanX = if (enough) maxX - minX else 0f
        val spanY = if (enough) maxY - minY else 0f
        val spanZ = if (enough) maxZ - minZ else 0f

        val oldest = maWin.firstOrNull() ?: ma
        val dX = ma.x - oldest.x
        val dY = ma.y - oldest.y
        val dZ = ma.z - oldest.z

        var disturbance = spanS
        var directionOk = true
        if (verticalOnly) {
            // World frame (gravity-anchored): Z is true vertical however the phone is
            // oriented. Without a rotation reference, fall back to the phone's own axes.
            val vertical = if (hasWorld) spanZ else spanY
            val horizontal = if (hasWorld) sqrt(spanX * spanX + spanY * spanY) else spanX
            disturbance = vertical
            directionOk = vertical >= horizontal * VERTICAL_DOMINANCE
        }

        // Room attunement: need a second of stillness.
        if (!isAttuned) {
            if (allowance > ATTUNE_MOTION_LIMIT_UT) {
                attuneStartMs = -1L; attuneSum = 0f; attuneN = 0
            } else {
                if (attuneStartMs < 0) attuneStartMs = t
                attuneSum += ma.scalar
                attuneN++
                if (t - attuneStartMs >= ATTUNE_MS && attuneN >= ATTUNE_MIN_BINS) {
                    refLevel = attuneSum / attuneN
                    hasRef = true
                    refHistory.clear()
                    isAttuned = true
                }
            }
            return step(t, disturbance, threshold, false, false, null, ma, dX, dY, dZ)
        }

        // Noise floor from quiet periods (used by calibration and the status display).
        if (disturbance < noiseFloor * 1.5f && allowance < 0.3f) {
            quietSum += disturbance
            quietN++
            if (quietN >= 25) {
                noiseFloor = max(0.08f, (quietSum / quietN) * 1.35f)
                quietSum = 0f; quietN = 0
            }
        }

        // Reference level follows the room slowly, and is frozen during a hold.
        if (!holdActive && disturbance < threshold * 0.5f) {
            refLevel += (ma.scalar - refLevel) * (1f - exp(-dtS / REF_TAU_S))
        }
        refHistory.addLast(RefPoint(t, refLevel))
        while (refHistory.isNotEmpty() && refHistory.first().timeMs < t - REF_HISTORY_MS) {
            refHistory.removeFirst()
        }

        if (capturing) {
            val shift = abs(ma.scalar - capRef)
            if (shift > capPeakShift) capPeakShift = shift
        }

        // Trigger, with re-arming so one event cannot fire twice.
        val meets = disturbance >= threshold && directionOk
        // While an object is confirmed "still near", the still-near check owns the story:
        // the object leaving is reported as GONE, not as a fresh detection.
        val isTrigger = allowTrigger && meets && (t - lastTriggerMs >= MIN_GAP_MS) &&
            !(holdActive && holdConfirmed)
        var confirmed = false
        var ended: HoldEnd? = null

        if (isTrigger) {
            lastTriggerMs = t
            if (stillNearCheck && isConfirmationUsable()) {
                holdActive = true
                holdConfirmed = false
                isStillNear = false
                holdStartMs = t
                holdFrozenRef = refAt(t - FREEZE_LOOKBACK_MS)
                holdRotAtFreeze = rotTotal
                holdBelowSinceMs = -1L
                holdLevelShift = 0f
            }
        } else if (holdActive) {
            val result = updateHold(t, ma.scalar)
            confirmed = result.first
            ended = result.second
            if (ended == HoldEnd.PHONE_MOVED) {
                refLevel = ma.scalar
                refHistory.clear()
            }
            if (ended == HoldEnd.GONE || ended == HoldEnd.TIMEOUT) {
                // The object leaving still shows up in the 700 ms window. Consume it, so
                // the departure is not reported as a brand-new detection straight after.
                // Suppress for a moment so it is not reported as a new detection.
                lastTriggerMs = t + POST_HOLD_QUIET_MS - MIN_GAP_MS
            }
        }

        return step(t, disturbance, threshold, isTrigger, confirmed, ended, ma, dX, dY, dZ)
    }

    private fun updateHold(t: Long, level: Float): Pair<Boolean, HoldEnd?> {
        val shift = abs(level - holdFrozenRef)
        holdLevelShift = shift

        // A level comparison is only meaningful if the phone has not moved since the freeze.
        if (rotTotal - holdRotAtFreeze > HOLD_ROT_LIMIT_RAD || allowance > HOLD_MOTION_LIMIT_UT) {
            endHoldSilently()
            return Pair(false, HoldEnd.PHONE_MOVED)
        }
        if (t - holdStartMs > HOLD_MAX_MS) {
            endHoldSilently()
            return Pair(false, HoldEnd.TIMEOUT)
        }
        val margin = confirmMargin()
        if (!holdConfirmed) {
            if (t - holdStartMs >= CONFIRM_DELAY_MS) {
                return if (shift >= margin) {
                    holdConfirmed = true
                    isStillNear = true
                    holdBelowSinceMs = -1L
                    Pair(true, null)
                } else {
                    endHoldSilently()
                    Pair(false, HoldEnd.PASS_BY)
                }
            }
        } else {
            if (shift < margin * HOLD_EXIT_FRACTION) {
                if (holdBelowSinceMs < 0) holdBelowSinceMs = t
                if (t - holdBelowSinceMs >= HOLD_EXIT_MS) {
                    endHoldSilently()
                    return Pair(false, HoldEnd.GONE)
                }
            } else {
                holdBelowSinceMs = -1L
            }
        }
        return Pair(false, null)
    }

    private fun step(
        t: Long, disturbance: Float, threshold: Float,
        trigger: Boolean, confirmed: Boolean, ended: HoldEnd?,
        ma: Bin, dX: Float, dY: Float, dZ: Float
    ) = Step(
        timeMs = t,
        disturbance = disturbance,
        threshold = threshold,
        isTrigger = trigger,
        stillNearConfirmed = confirmed,
        holdEnded = ended,
        isAttuned = isAttuned,
        motionAllowance = allowance,
        levelShift = holdLevelShift,
        smoothScalar = ma.scalar,
        smoothX = ma.x, smoothY = ma.y, smoothZ = ma.z,
        deltaX = dX, deltaY = dY, deltaZ = dZ
    )

    companion object {
        const val GRAVITY = 9.81f

        // Fixed processing rate
        const val SMOOTHING_ALPHA = 0.25f    // V1 low-pass, per sample
        const val WINDOW_MS = 400L          // V1 window
        const val ROT_WINDOW_MS = 700L
        const val MIN_SPAN_BINS = 3
        const val GAP_MS = 500L

        // Motion allowance. ESTIMATES -- tune from recordings.
        const val ACCEL_DEADBAND = 0.06f          // m/s^2
        const val ACCEL_UT_PER_MS2 = 1.0f         // uT of extra tolerance per m/s^2 off gravity
        const val GYRO_DEADBAND = 0.02f           // rad/s
        const val ROT_UT_PER_RAD = 8.0f           // uT of extra tolerance per radian turned in the window
        const val ALLOWANCE_TAU_S = 0.5f          // ~1.5 s to fall back, like X-Vision

        // Attunement
        const val ATTUNE_MS = 1000L
        const val ATTUNE_MIN_BINS = 20
        const val ATTUNE_MOTION_LIMIT_UT = 2.0f   // ordinary hand tremor must not block attunement

        // Trigger
        const val VERTICAL_DOMINANCE = 1.1f
        const val REARM_FRACTION = 0.6f
        const val REARM_QUIET_MS = 300L
        const val LOCKOUT_MAX_MS = 2500L
        const val MIN_GAP_MS = 400L          // V1 debounce
        const val POST_HOLD_QUIET_MS = 1500L

        // Reference level and the still-near hold (Strong Magnet)
        const val REF_TAU_S = 8f
        const val REF_HISTORY_MS = 1500L
        const val FREEZE_LOOKBACK_MS = 1000L
        const val CONFIRM_DELAY_MS = 900L
        const val HOLD_MAX_MS = 10_000L
        const val HOLD_EXIT_FRACTION = 0.6f
        const val HOLD_EXIT_MS = 500L
        const val HOLD_ROT_LIMIT_RAD = 0.05f      // ~3 degrees
        const val HOLD_MOTION_LIMIT_UT = 1.5f
        const val MIN_LEVEL_MARGIN = 0.8f
        const val DEFAULT_LEVEL_MARGIN = 1.5f
        const val CALIBRATED_MARGIN_FRACTION = 0.35f
        const val USABLE_LEVEL_PEAK = 2.0f
    }
}
