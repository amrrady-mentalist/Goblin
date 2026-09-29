package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.model.HapticFeedbackType
import com.example.domain.model.MagneticDirection
import com.example.domain.model.VibrationStrength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromHaptic(value: HapticFeedbackType): String = value.name

    @TypeConverter
    fun toHaptic(value: String): HapticFeedbackType = runCatching {
        HapticFeedbackType.valueOf(value)
    }.getOrDefault(HapticFeedbackType.GHOST_TAP)

    @TypeConverter
    fun fromStrength(value: VibrationStrength): String = value.name

    @TypeConverter
    fun toStrength(value: String): VibrationStrength = runCatching {
        VibrationStrength.valueOf(value)
    }.getOrDefault(VibrationStrength.MEDIUM)

    @TypeConverter
    fun fromDirection(value: MagneticDirection): String = value.name

    @TypeConverter
    fun toDirection(value: String): MagneticDirection = runCatching {
        MagneticDirection.valueOf(value)
    }.getOrDefault(MagneticDirection.OMNI)
}

@Database(
    entities = [VenueProfileEntity::class, DetectionEventEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GoblinDatabase : RoomDatabase() {
    abstract fun dao(): GoblinDao

    companion object {
        @Volatile
        private var INSTANCE: GoblinDatabase? = null

        fun getInstance(context: Context): GoblinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GoblinDatabase::class.java,
                    "goblin_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default venue presets
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getInstance(context).dao()
                            seedDefaultProfiles(dao)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedDefaultProfiles(dao: GoblinDao) {
            val defaults = listOf(
                VenueProfileEntity(
                    name = "PK Magnetic Ring",
                    description = "High sensitivity for faint magnetic ring passes (5-15cm range).",
                    fogLevel = 0.20f,
                    customThresholdDelta = 1.6f,
                    hapticType = HapticFeedbackType.GHOST_TAP,
                    vibrationStrength = VibrationStrength.SUBTLE,
                    debounceMs = 350L,
                    isBuiltIn = true
                ),
                VenueProfileEntity(
                    name = "Concealed Magnetic Coin",
                    description = "Balanced for magnetic coins, shims, and PK pens. Great for close-up magic.",
                    fogLevel = 0.38f,
                    customThresholdDelta = 3.2f,
                    hapticType = HapticFeedbackType.HEARTBEAT,
                    vibrationStrength = VibrationStrength.MEDIUM,
                    debounceMs = 400L,
                    isBuiltIn = true
                ),
                VenueProfileEntity(
                    name = "Bar & Lounge Walk-Around",
                    description = "Enhanced noise filtering against speaker coils, metal counters, and patron phones.",
                    fogLevel = 0.55f,
                    customThresholdDelta = 5.0f,
                    hapticType = HapticFeedbackType.SHARP_STRIKE,
                    vibrationStrength = VibrationStrength.PRONOUNCED,
                    debounceMs = 450L,
                    isBuiltIn = true
                ),
                VenueProfileEntity(
                    name = "Theatre & Stage Performance",
                    description = "Heavy fog shielding against theatrical lighting rigs and heavy stage mains.",
                    fogLevel = 0.75f,
                    customThresholdDelta = 8.5f,
                    hapticType = HapticFeedbackType.SHARP_STRIKE,
                    vibrationStrength = VibrationStrength.PRONOUNCED,
                    debounceMs = 500L,
                    isBuiltIn = true
                ),
                VenueProfileEntity(
                    name = "Subtle Flux Exploration",
                    description = "Fluid dynamic purr feedback that vibrates continuously with magnetic speed.",
                    fogLevel = 0.25f,
                    customThresholdDelta = 2.0f,
                    hapticType = HapticFeedbackType.DYNAMIC_PURR,
                    vibrationStrength = VibrationStrength.MEDIUM,
                    debounceMs = 150L,
                    isBuiltIn = true
                )
            )
            for (p in defaults) {
                dao.insertProfile(p)
            }
        }
    }
}
