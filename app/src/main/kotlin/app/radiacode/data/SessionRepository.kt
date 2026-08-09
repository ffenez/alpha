package app.radiacode.data

import app.radiacode.data.db.DownsampledSample
import app.radiacode.data.db.EventDao
import app.radiacode.data.db.EventEntity
import app.radiacode.data.db.MeasurementSessionEntity
import app.radiacode.data.db.PlaceDao
import app.radiacode.data.db.RangeStats
import app.radiacode.data.db.SampleDao
import app.radiacode.data.db.SessionDao
import app.radiacode.data.db.SpectrumDao
import app.radiacode.data.db.TrackDao
import app.radiacode.ui.logic.ChartMapping

/** One session with everything the History list shows. */
data class SessionSummary(
    val id: Long,
    /** Place name at session start; null = «без места». */
    val placeName: String?,
    val startedAt: Long,
    /** Open sessions report the current time as a provisional end. */
    val endedAt: Long?,
    val stats: RangeStats,
    /** Accumulated dose over the session, µSv (calculated). */
    val doseMicroSv: Double,
    val hasSpectrum: Boolean,
    val hasTrack: Boolean,
)

/**
 * Measurement sessions (SPEC «History»): lifecycle for the service, windowed
 * summary pages for the UI. Summaries aggregate the samples table by time
 * range — sessions never duplicate measurement data.
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao,
    private val placeDao: PlaceDao,
    private val spectrumDao: SpectrumDao,
    private val trackDao: TrackDao,
    private val eventDao: EventDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    // --- lifecycle (service) ---

    suspend fun open(placeId: Long?): Long =
        sessionDao.insert(MeasurementSessionEntity(placeId = placeId, startedAt = clock()))

    suspend fun close(sessionId: Long, endedAt: Long = clock()) =
        sessionDao.close(sessionId, endedAt)

    /**
     * Crash recovery on service start: sessions left open by a killed process
     * end at the last recorded sample (honest end, not "now").
     */
    suspend fun closeStale() {
        val endedAt = sampleDao.latestTimestamp() ?: clock()
        sessionDao.closeAllOpen(endedAt)
    }

    // --- History queries ---

    suspend fun count(): Long = sessionDao.count()

    suspend fun page(offset: Int, limit: Int): List<SessionSummary> {
        val sessions = sessionDao.page(limit = limit, offset = offset)
        if (sessions.isEmpty()) return emptyList()
        val placeNames = placeDao.all().associate { it.id to it.name }
        return sessions.map { session -> summarize(session, placeNames) }
    }

    suspend fun summary(sessionId: Long): SessionSummary? {
        val session = sessionDao.session(sessionId) ?: return null
        val placeNames = placeDao.all().associate { it.id to it.name }
        return summarize(session, placeNames)
    }

    suspend fun chartBuckets(sessionId: Long, bucketMillis: Long): List<DownsampledSample> {
        val session = sessionDao.session(sessionId) ?: return emptyList()
        return sampleDao.downsampledRange(
            from = session.startedAt,
            to = session.endedAt ?: clock(),
            bucketMillis = bucketMillis,
        )
    }

    /** Deviation/hotspot events inside a time range, newest first. */
    suspend fun deviationEvents(
        from: Long,
        to: Long,
        limit: Int = 200,
    ): List<EventEntity> = eventDao.inRangeBySource(
        from = from,
        to = to,
        sources = listOf(EventEntity.SOURCE_HOTSPOT, EventEntity.SOURCE_DEVIATION),
        limit = limit,
    )

    private suspend fun summarize(
        session: MeasurementSessionEntity,
        placeNames: Map<Long, String>,
    ): SessionSummary {
        val to = session.endedAt ?: clock()
        val stats = sampleDao.rangeStats(session.startedAt, to)
        val doseBuckets = sampleDao.downsampledRange(session.startedAt, to, DOSE_BUCKET_MILLIS)
        return SessionSummary(
            id = session.id,
            placeName = session.placeId?.let { placeNames[it] },
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            stats = stats,
            doseMicroSv = ChartMapping.integrateDoseMicroSv(doseBuckets),
            hasSpectrum = spectrumDao.countInRange(session.startedAt, to) > 0,
            hasTrack = trackDao.countOverlapping(session.startedAt, to) > 0,
        )
    }

    companion object {
        private const val DOSE_BUCKET_MILLIS = 10L * 60_000L
    }
}
