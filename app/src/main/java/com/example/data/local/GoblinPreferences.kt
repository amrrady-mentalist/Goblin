package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent preferences for Goblin Detector.
 * Ensures user configuration (sensitivity, thresholds, smart alarm,
 * smartwatch call relay, modes) persists across app restarts.
 */
class GoblinPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("goblin_detector_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TRICK_RUNNING = "key_trick_running"
        private const val KEY_SENSOR_POWER = "key_sensor_power"
        private const val KEY_SENSITIVITY = "key_sensitivity"
        private const val KEY_ADAPTIVE_SENSITIVITY = "key_adaptive_sensitivity"
        private const val KEY_SMART_ALARM_ENABLED = "key_smart_alarm_enabled"
        private const val KEY_SMART_ALARM_THRESHOLD = "key_smart_alarm_threshold"
        private const val KEY_SLEEPING_MODE = "key_sleeping_mode"
        private const val KEY_VISUAL_MODE_ENABLED = "key_visual_mode_enabled"
        private const val KEY_VIBRATION_WITH_VISUAL = "key_vibration_with_visual"
        private const val KEY_SMARTWATCH_CALL_ENABLED = "key_smartwatch_call_enabled"
        private const val KEY_LOCATOR_MODE = "key_locator_mode"
        private const val KEY_HAPTIC_TYPE = "key_haptic_type"
        private const val KEY_VIBRATION_STRENGTH = "key_vibration_strength"
    }

    var isTrickRunning: Boolean
        get() = prefs.getBoolean(KEY_TRICK_RUNNING, true)
        set(value) = prefs.edit().putBoolean(KEY_TRICK_RUNNING, value).apply()

    var isSensorPower: Boolean
        get() = prefs.getBoolean(KEY_SENSOR_POWER, true)
        set(value) = prefs.edit().putBoolean(KEY_SENSOR_POWER, value).apply()

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, 1.9f)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value).apply()

    var adaptiveSensitivity: Int
        get() = prefs.getInt(KEY_ADAPTIVE_SENSITIVITY, 8)
        set(value) = prefs.edit().putInt(KEY_ADAPTIVE_SENSITIVITY, value).apply()

    var isSmartAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMART_ALARM_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_ALARM_ENABLED, value).apply()

    var smartAlarmThreshold: Float
        get() = prefs.getFloat(KEY_SMART_ALARM_THRESHOLD, 65f)
        set(value) = prefs.edit().putFloat(KEY_SMART_ALARM_THRESHOLD, value).apply()

    var isSleepingMode: Boolean
        get() = prefs.getBoolean(KEY_SLEEPING_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_SLEEPING_MODE, value).apply()

    var isVisualModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_VISUAL_MODE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VISUAL_MODE_ENABLED, value).apply()

    var vibrationWithVisual: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_WITH_VISUAL, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_WITH_VISUAL, value).apply()

    var isSmartwatchCallEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMARTWATCH_CALL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SMARTWATCH_CALL_ENABLED, value).apply()

    var locatorModeName: String
        get() = prefs.getString(KEY_LOCATOR_MODE, "PROXIMITY_50CM") ?: "PROXIMITY_50CM"
        set(value) = prefs.edit().putString(KEY_LOCATOR_MODE, value).apply()

    var hapticTypeName: String
        get() = prefs.getString(KEY_HAPTIC_TYPE, "DOUBLE_STRONG") ?: "DOUBLE_STRONG"
        set(value) = prefs.edit().putString(KEY_HAPTIC_TYPE, value).apply()

    var vibrationStrengthName: String
        get() = prefs.getString(KEY_VIBRATION_STRENGTH, "MEDIUM") ?: "MEDIUM"
        set(value) = prefs.edit().putString(KEY_VIBRATION_STRENGTH, value).apply()
}
