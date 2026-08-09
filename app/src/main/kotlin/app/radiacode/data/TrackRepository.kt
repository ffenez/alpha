package app.radiacode.data

import app.radiacode.data.db.TrackDao
import app.radiacode.data.db.TrackPointEntity
import app.radiacode.data.db.TrackSessionEntity
import kotlinx.coroutines.flow.Flow

/** Track recording: sessions of GPS points joined with the latest dose rate. */
class TrackRepository(
    private val trackDao: TrackDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun startSession(name: String): Long =
        trackDao.insertSession(TrackSessionEntity(name = name, startedAt = clock()))

    suspend fun endSession(sessionId: Long) {
        trackDao.endSession(sessionId, endedAt = clock())
    }

    suspend fun addPoint(
        sessionId: Long,
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        doseRate: Float?,
        countRate: Float?,
    ) {
        trackDao.insertPoint(
            TrackPointEntity(
                sessionId = sessionId,
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                doseRate = doseRate,
                countRate = countRate,
            ),
        )
    }

    fun sessions(): Flow<List<TrackSessionEntity>> = trackDao.observeSessions()

    fun points(sessionId: Long): Flow<List<TrackPointEntity>> = trackDao.observePoints(sessionId)
}
