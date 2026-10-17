package com.example.haptics

import com.example.domain.model.VibePattern
import kotlin.math.roundToInt

/** What to play. Strength, speed and length are all 0..100. */
data class VibeSpec(
    val pattern: VibePattern,
    val count: Int = 2,
    val strength: Int = 70,
    val speed: Int = 50,
    val length: Int = 40
)

/**
 * Turns a [VibeSpec] into a waveform (on/off times and strength). Pure Kotlin, so it can
 * be checked on a desktop.
 */
object VibePlan {

    class Plan(val timings: LongArray, val amplitudes: IntArray, val totalMs: Long)

    private fun lerp(a: Float, b: Float, t: Int) = a + (b - a) * (t.coerceIn(0, 100) / 100f)

    /** Pulse strength 40..255; always strong enough to feel through a pocket. */
    fun amplitude(strength: Int): Int = lerp(40f, 255f, strength).roundToInt()

    fun build(spec: VibeSpec): Plan? {
        val amp = amplitude(spec.strength)
        val n = spec.count.coerceIn(1, 5)
        val timings = ArrayList<Long>()
        val amps = ArrayList<Int>()
        fun add(onMs: Long, gapMs: Long, last: Boolean) {
            timings.add(onMs); amps.add(amp)
            if (!last) { timings.add(gapMs); amps.add(0) }
        }
        when (spec.pattern) {
            VibePattern.OFF -> return null
            VibePattern.BUZZ -> {
                timings.add(lerp(300f, 3000f, spec.length).toLong()); amps.add(amp)
            }
            VibePattern.PULSES -> {
                val on = lerp(60f, 400f, spec.length).toLong()
                val gap = lerp(300f, 60f, spec.speed).toLong()
                for (i in 0 until n) add(on, gap, i == n - 1)
            }
            VibePattern.STUTTER -> {
                val gap = lerp(120f, 30f, spec.speed).toLong()
                for (i in 0 until n) add(35L, gap, i == n - 1)
            }
            VibePattern.TICK -> {
                val gap = lerp(250f, 80f, spec.speed).toLong()
                for (i in 0 until n) add(15L, gap, i == n - 1)
            }
        }
        // The first entry is the delay before the vibration starts.
        val t = LongArray(timings.size + 1)
        val a = IntArray(amps.size + 1)
        for (i in timings.indices) { t[i + 1] = timings[i]; a[i + 1] = amps[i] }
        return Plan(t, a, t.sum())
    }

    /** How long the motor runs; the sensor ignores the magnetometer for this long plus a margin. */
    fun durationMs(spec: VibeSpec): Long = build(spec)?.totalMs ?: 0L
}
