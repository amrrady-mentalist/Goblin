package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.HapticFeedbackType
import com.example.domain.model.MagneticDirection
import com.example.domain.model.VibrationStrength

@Entity(tableName = "venue_profiles")
data class VenueProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val fogLevel: Float, // 0.0f to 1.0f
    val customThresholdDelta: Float, // microteslas (e.g. 1.5uT to 15uT)
    val hapticType: HapticFeedbackType,
    val vibrationStrength: VibrationStrength,
    val debounceMs: Long,
    val isBuiltIn: Boolean = false
)

@Entity(tableName = "detection_events")
data class DetectionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val peakDelta: Float,
    val dominantDirection: MagneticDirection,
    val fogLevel: Float,
    val thresholdAtEvent: Float,
    val durationMs: Long
)
