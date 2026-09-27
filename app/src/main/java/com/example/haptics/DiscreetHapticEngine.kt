package com.example.haptics

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.domain.model.HapticFeedbackType
import com.example.domain.model.VibrationStrength
import kotlin.math.min

class DiscreetHapticEngine(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var hapticType: HapticFeedbackType = HapticFeedbackType.DOUBLE_STRONG
    var strength: VibrationStrength = VibrationStrength.MEDIUM
    var isEnabled: Boolean = true

    fun playStrikeFeedback(deltaMagnitude: Float = 5f, rateOfChange: Float = 10f) {
        if (!isEnabled || hapticType == HapticFeedbackType.MUTE) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        when (hapticType) {
            HapticFeedbackType.DOUBLE_STRONG -> playDoubleStrong(vib)
            HapticFeedbackType.GHOST_TAP -> playGhostTap(vib)
            HapticFeedbackType.HEARTBEAT -> playHeartbeat(vib)
            HapticFeedbackType.DYNAMIC_PURR -> playDynamicPurr(vib, deltaMagnitude, rateOfChange)
            HapticFeedbackType.SHARP_STRIKE -> playSharpStrike(vib)
            HapticFeedbackType.GEIGER_PULSE -> playGeigerTick(vib)
            HapticFeedbackType.MUTE -> Unit
        }
    }

    private fun playDoubleStrong(vib: Vibrator) {
        val amp = (255 * strength.multiplier).toInt().coerceIn(180, 255)
        // 2 clear, heavy pulses: 160ms on, 110ms gap, 200ms on
        val timings = longArrayOf(0, 160, 110, 200)
        val amplitudes = intArrayOf(0, amp, 0, amp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vib.hasAmplitudeControl()) {
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(longArrayOf(0, 160, 110, 200), -1)
        }
    }

    private fun playGhostTap(vib: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                vib.vibrate(effect)
                return
            } catch (_: Exception) {
                // Fallback to micro-pulse
            }
        }
        val duration = (12 * strength.multiplier).toLong().coerceIn(8, 25)
        val amp = (60 * strength.multiplier).toInt().coerceIn(30, 160)
        vibrateOneShot(vib, duration, amp)
    }

    private fun playHeartbeat(vib: Vibrator) {
        val amp1 = (80 * strength.multiplier).toInt().coerceIn(40, 200)
        val amp2 = (140 * strength.multiplier).toInt().coerceIn(70, 255)
        val timings = longArrayOf(0, 18, 90, 28)
        val amplitudes = intArrayOf(0, amp1, 0, amp2)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vib.hasAmplitudeControl()) {
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(longArrayOf(0, 20, 80, 30), -1)
        }
    }

    private fun playDynamicPurr(vib: Vibrator, delta: Float, rate: Float) {
        val intensity = min(1.0f, (delta / 12f) * 0.7f + (rate / 25f) * 0.3f)
        val duration = (25 + 30 * intensity).toLong()
        val amp = (70 + (160 * intensity * strength.multiplier)).toInt().coerceIn(40, 255)
        vibrateOneShot(vib, duration, amp)
    }

    private fun playSharpStrike(vib: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && strength == VibrationStrength.MEDIUM) {
            try {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                return
            } catch (_: Exception) {
                // Fallback
            }
        }
        val duration = (50 * strength.multiplier).toLong().coerceIn(30, 85)
        val amp = (220 * strength.multiplier).toInt().coerceIn(120, 255)
        vibrateOneShot(vib, duration, amp)
    }

    private fun playGeigerTick(vib: Vibrator) {
        val duration = 10L
        val amp = (90 * strength.multiplier).toInt().coerceIn(45, 180)
        vibrateOneShot(vib, duration, amp)
    }

    private fun vibrateOneShot(vib: Vibrator, duration: Long, amplitude: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val clampedAmp = if (vib.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
            vib.vibrate(VibrationEffect.createOneShot(duration, clampedAmp))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(duration)
        }
    }

    fun testFeedback(type: HapticFeedbackType) {
        val savedType = hapticType
        hapticType = type
        playStrikeFeedback(deltaMagnitude = 8f, rateOfChange = 15f)
        hapticType = savedType
    }
}
