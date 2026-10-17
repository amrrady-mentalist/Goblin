package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.model.VibePattern

/** Saves the performer's settings so they survive an app restart. */
class GoblinPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("goblin_detector_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TRICK_RUNNING = "key_trick_running"
        private const val KEY_SENSOR_POWER = "key_sensor_power"
        private const val KEY_VISUAL_MODE_ENABLED = "key_visual_mode_enabled"
        private const val KEY_VIBRATION_WITH_VISUAL = "key_vibration_with_visual"
        private const val KEY_CALIBRATED_LEVEL_PEAK = "key_calibrated_level_peak"

        // The four sliders and the Up/Down switch
        private const val KEY_TERRITORY = "key_territory"
        private const val KEY_MAGNET_POWER = "key_magnet_power"
        private const val KEY_MAGNET_MOVEMENT = "key_magnet_movement"
        private const val KEY_PHONE_MOVEMENT = "key_phone_movement"
        private const val KEY_VERTICAL_ONLY = "key_vertical_only"

        // Vibration
        private const val KEY_VIBE_PATTERN = "key_vibe_pattern"
        private const val KEY_VIBE_COUNT = "key_vibe_count"
        private const val KEY_VIBE_STRENGTH = "key_vibe_strength"
        private const val KEY_VIBE_SPEED = "key_vibe_speed"
        private const val KEY_VIBE_LENGTH = "key_vibe_length"
        private const val KEY_SECOND_PATTERN = "key_second_pattern"
        private const val KEY_SECOND_COUNT = "key_second_count"
    }

    var isTrickRunning: Boolean
        get() = prefs.getBoolean(KEY_TRICK_RUNNING, true)
        set(value) = prefs.edit().putBoolean(KEY_TRICK_RUNNING, value).apply()

    var isSensorPower: Boolean
        get() = prefs.getBoolean(KEY_SENSOR_POWER, true)
        set(value) = prefs.edit().putBoolean(KEY_SENSOR_POWER, value).apply()

    var isVisualModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_VISUAL_MODE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VISUAL_MODE_ENABLED, value).apply()

    var vibrationWithVisual: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_WITH_VISUAL, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_WITH_VISUAL, value).apply()

    /** How far the last calibrated object moved the field level (µT); 0 = never calibrated. */
    var calibratedLevelPeak: Float
        get() = prefs.getFloat(KEY_CALIBRATED_LEVEL_PEAK, 0f)
        set(value) = prefs.edit().putFloat(KEY_CALIBRATED_LEVEL_PEAK, value).apply()

    var territory: Float
        get() = prefs.getFloat(KEY_TERRITORY, 1f)
        set(value) = prefs.edit().putFloat(KEY_TERRITORY, value).apply()

    var magnetPower: Float
        get() = prefs.getFloat(KEY_MAGNET_POWER, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_MAGNET_POWER, value).apply()

    var magnetMovement: Float
        get() = prefs.getFloat(KEY_MAGNET_MOVEMENT, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_MAGNET_MOVEMENT, value).apply()

    var phoneMovement: Int
        get() = prefs.getInt(KEY_PHONE_MOVEMENT, 50)
        set(value) = prefs.edit().putInt(KEY_PHONE_MOVEMENT, value).apply()

    var verticalOnly: Boolean
        get() = prefs.getBoolean(KEY_VERTICAL_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_VERTICAL_ONLY, value).apply()

    private fun pattern(key: String, default: VibePattern): VibePattern =
        try {
            VibePattern.valueOf(prefs.getString(key, default.name) ?: default.name)
        } catch (e: Exception) {
            default
        }

    var vibePattern: VibePattern
        get() = pattern(KEY_VIBE_PATTERN, VibePattern.PULSES)
        set(value) = prefs.edit().putString(KEY_VIBE_PATTERN, value.name).apply()

    var vibeCount: Int
        get() = prefs.getInt(KEY_VIBE_COUNT, 2)
        set(value) = prefs.edit().putInt(KEY_VIBE_COUNT, value).apply()

    var vibeStrength: Int
        get() = prefs.getInt(KEY_VIBE_STRENGTH, 80)
        set(value) = prefs.edit().putInt(KEY_VIBE_STRENGTH, value).apply()

    var vibeSpeed: Int
        get() = prefs.getInt(KEY_VIBE_SPEED, 50)
        set(value) = prefs.edit().putInt(KEY_VIBE_SPEED, value).apply()

    var vibeLength: Int
        get() = prefs.getInt(KEY_VIBE_LENGTH, 40)
        set(value) = prefs.edit().putInt(KEY_VIBE_LENGTH, value).apply()

    var secondPattern: VibePattern
        get() = pattern(KEY_SECOND_PATTERN, VibePattern.TICK)
        set(value) = prefs.edit().putString(KEY_SECOND_PATTERN, value.name).apply()

    var secondCount: Int
        get() = prefs.getInt(KEY_SECOND_COUNT, 2)
        set(value) = prefs.edit().putInt(KEY_SECOND_COUNT, value).apply()
}
