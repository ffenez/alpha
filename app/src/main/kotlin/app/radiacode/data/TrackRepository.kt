package app.radiacode.data

import app.radiacode.data.db.TrackAreaSummaryRow
import app.radiacode.data.db.TrackBoundsRow
import app.radiacode.data.db.TrackDao
import app.radiacode.data.db.TrackGridBinRow
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
        altitudeMeters: Double? = null,
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
                altitudeMeters = altitudeMeters,
            ),
        )
    }

    fun sessions(): Flow<List<TrackSessionEntity>> = trackDao.observeSessions()

    fun points(sessionId: Long): Flow<List<TrackPointEntity>> = trackDao.observePoints(sessionId)

    suspend fun session(sessionId: Long): TrackSessionEntity? = trackDao.session(sessionId)

    /** Newest track session; null = nothing was ever recorded. */
    suspend fun latestSession(): TrackSessionEntity? = trackDao.latestSession()

    /** Track sessions overlapping a measurement-session time range. */
    suspend fun sessionsOverlapping(from: Long, to: Long): List<TrackSessionEntity> =
        trackDao.sessionsOverlapping(from, to)

    // --- accumulated map («все записи») ---

    /** Box covering every fix ever recorded; null when nothing was recorded. */
    suspend fun allPointsBounds(): TrackBoundsRow? =
        trackDao.allPointsBounds().takeIf { it.minLatitude != null }

    /**
     * Exact aggregate of one viewport over all recordings — the numbers the
     * summary card shows. Never derived from the drawn subset.
     */
    suspend fun areaSummary(
        useDose: Boolean,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
        maxAccuracyMeters: Float,
    ): TrackAreaSummaryRow = trackDao.boundsSummary(
        useDose = useDose,
        minLatitude = minLatitude,
        maxLatitude = maxLatitude,
        minLongitude = minLongitude,
        maxLongitude = maxLongitude,
        maxAccuracyMeters = maxAccuracyMeters,
    )

    /** Grid × value histogram of a viewport (see `ui/logic/TrackGrid`). */
    @Suppress("LongParameterList")
    suspend fun gridHistogram(
        useDose: Boolean,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
        maxAccuracyMeters: Float,
        latStepDeg: Double,
        lonStepDeg: Double,
        valueMin: Float,
        valueStep: Float,
        limit: Int,
    ): List<TrackGridBinRow> = trackDao.gridHistogram(
        useDose = useDose,
        minLatitude = minLatitude,
        maxLatitude = maxLatitude,
        minLongitude = minLongitude,
        maxLongitude = maxLongitude,
        maxAccuracyMeters = maxAccuracyMeters,
        latStepDeg = latStepDeg,
        lonStepDeg = lonStepDeg,
        valueMin = valueMin,
        valueStep = valueStep,
        limit = limit,
    )
}
