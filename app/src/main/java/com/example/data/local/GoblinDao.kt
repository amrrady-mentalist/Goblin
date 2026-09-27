package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GoblinDao {
    @Query("SELECT * FROM venue_profiles ORDER BY isBuiltIn DESC, id ASC")
    fun getAllProfiles(): Flow<List<VenueProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: VenueProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: VenueProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: VenueProfileEntity)

    @Query("SELECT * FROM detection_events ORDER BY timestamp DESC LIMIT 100")
    fun getRecentEvents(): Flow<List<DetectionEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DetectionEventEntity): Long

    @Query("DELETE FROM detection_events")
    suspend fun clearAllEvents()
}
