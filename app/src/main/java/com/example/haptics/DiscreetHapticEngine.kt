package com.example.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Plays [VibeSpec]s on the phone's vibration motor. */
class DiscreetHapticEngine(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var isEnabled: Boolean = true

    fun play(spec: VibeSpec) {
        if (!isEnabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val plan = VibePlan.build(spec) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (vib.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(plan.timings, plan.amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(plan.timings, -1)
            }
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(plan.timings, -1)
        }
    }

    fun cancel() {
        try { vibrator?.cancel() } catch (_: Exception) {}
    }
}
