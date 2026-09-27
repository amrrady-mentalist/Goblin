package com.example.data.local

import kotlinx.coroutines.flow.Flow

class GoblinRepository(private val dao: GoblinDao) {
    val profiles: Flow<List<VenueProfileEntity>> = dao.getAllProfiles()
    val recentEvents: Flow<List<DetectionEventEntity>> = dao.getRecentEvents()

    suspend fun saveProfile(profile: VenueProfileEntity): Long {
        return if (profile.id == 0L) {
            dao.insertProfile(profile)
        } else {
            dao.updateProfile(profile)
            profile.id
        }
    }

    suspend fun deleteProfile(profile: VenueProfileEntity) {
        if (!profile.isBuiltIn) {
            dao.deleteProfile(profile)
        }
    }

    suspend fun logDetectionEvent(event: DetectionEventEntity): Long {
        return dao.insertEvent(event)
    }

    suspend fun clearEvents() {
        dao.clearAllEvents()
    }
}
