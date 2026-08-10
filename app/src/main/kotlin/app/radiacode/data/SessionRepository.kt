package app.radiacode.data

import app.radiacode.baseline.BaselineExclusion
import app.radiacode.data.db.DownsampledSample
import app.radiacode.data.db.EventDao
import app.radiacode.data.db.EventEntity
import app.radiacode.data.db.MeasurementSessionEntity
import app.radiacode.data.db.ProfileDao
import app.radiacode.data.db.ProfileEntity
import app.radiacode.data.db.RangeStats
import app.radiacode.data.db.SampleDao
import app.radiacode.data.db.SessionDao
import app.radiacode.data.db.SpectrumDao
import app.radiacode.data.db.TrackDao
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.FlightDetect
import app.radiacode.ui.logic.ProfileTree

/**
 * Baseline participation of one session (spec §20): the journal must say
 * whether the session taught the baseline anything and, if not, why.
 */
data class SessionAdmission(
    /** Seconds of measurement admitted into baseline statistics. */
    val admittedSeconds: Long,
    /** Excluded seconds per reason, biggest first. */
    val exclusions: List<ExclusionSummary>,
) {
    val included: Boolean get() = admittedSeconds > 0L
    val excludedSeconds: Long get() = exclusions.sumOf { it.seconds }

    companion object {
        val EMPTY = SessionAdmission(0L, emptyList())
    }
}

/** One session with everything the History list shows. */
data class SessionSummary(
    val id: Long,
    /** Profile of the session; null = «без профиля». */
    val profileId: Long?,
    /** Profile display name («Дом / Спальня»); null = «без профиля». */
    val profileName: String?,
    val startedAt: Long,
    /** Open sessions report the current time as a provisional end. */
    val endedAt: Long?,
    val stats: RangeStats,
    /** Accumulated dose over the session, µSv (calculated). */
    val doseMicroSv: Double,
    val hasSpectrum: Boolean,
    val hasTrack: Boolean,
    /**
     * ~2+ minutes of track points above 3000 м GPS altitude in the session
     * range (1 Hz points ≈ seconds; the exact sustain check runs in the
     * session detail on the loaded points).
     */
    val hasFlight: Boolean = false,
    /** Baseline participation of this session (spec §20). */
    val admission: SessionAdmission = SessionAdmission.EMPTY,
)

/**
 * Measurement sessions (SPEC «History»): lifecycle for the service, windowed
 * summary pages for the UI. Summaries aggregate the samples table by time
 * range — sessions never duplicate measurement data.
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao,
    private val profileDao: ProfileDao,
    private val spectrumDao: SpectrumDao,
    private val trackDao: TrackDao,
    private val eventDao: EventDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    // --- lifecycle (service) ---

    suspend fun open(profileId: Long?): Long =
        sessionDao.insert(MeasurementSessionEntity(profileId = profileId, startedAt = clock()))

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
        val profiles = profileDao.all()
        return sessions.map { session -> summarize(session, profiles) }
    }

    suspend fun summary(sessionId: Long): SessionSummary? {
        val session = sessionDao.session(sessionId) ?: return null
        return summarize(session, profileDao.all())
    }

    /**
     * История: correct which profile a past session belonged to (spec §20).
     * The session, its samples and the profile-dependent part of the baseline
     * verdict all move together, so the statistics of both profiles stay
     * consistent with what the journal now claims. Reasons that describe the
     * measurement itself (stale stream, experiment, quarantine…) are left
     * untouched — a later correction of the place cannot make a bad
     * measurement good.
     */
    suspend fun reassignProfile(sessionId: Long, profileId: Long?) {
        val session = sessionDao.session(sessionId) ?: return
        val to = session.endedAt ?: clock()
        val learningEnabled = profileId
            ?.let { profileDao.byId(it)?.baselineLearning }
            ?: false
        sessionDao.reassignProfile(sessionId, profileId)
        sampleDao.reassignRange(session.startedAt, to, profileId)
        sampleDao.rewriteLearningVerdict(
            from = session.startedAt,
            to = to,
            reason = if (learningEnabled) null else BaselineExclusion.LEARNING_OFF.storageKey,
            learningOffReason = BaselineExclusion.LEARNING_OFF.storageKey,
        )
    }

    /** Baseline participation of an arbitrary time range (spec §20). */
    suspend fun admission(from: Long, to: Long): SessionAdmission = SessionAdmission(
        admittedSeconds = sampleDao.admittedCountInRange(from, to).toLong(),
        exclusions = sampleDao.exclusionCountsInRange(from, to).mapNotNull { row ->
            BaselineExclusion.fromStorage(row.reason)?.let {
                ExclusionSummary(it, row.samples.toLong())
            }
        },
    )

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
        profiles: List<ProfileEntity>,
    ): SessionSummary {
        val to = session.endedAt ?: clock()
        val stats = sampleDao.rangeStats(session.startedAt, to)
        val doseBuckets = sampleDao.downsampledRange(session.startedAt, to, DOSE_BUCKET_MILLIS)
        val profile = session.profileId?.let { id -> profiles.firstOrNull { it.id == id } }
        return SessionSummary(
            id = session.id,
            profileId = session.profileId,
            profileName = profile?.let { ProfileTree.displayName(it, profiles) },
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            stats = stats,
            doseMicroSv = ChartMapping.integrateDoseMicroSv(doseBuckets),
            hasSpectrum = spectrumDao.countInRange(session.startedAt, to) > 0,
            hasTrack = trackDao.countOverlapping(session.startedAt, to) > 0,
            hasFlight = trackDao.highAltitudePointCount(
                from = session.startedAt,
                to = to,
                minAltitudeMeters = FlightDetect.MIN_ALTITUDE_METERS,
            ) >= FlightDetect.SUSTAIN_MILLIS / 1000L,
            admission = admission(session.startedAt, to),
        )
    }

    companion object {
        private const val DOSE_BUCKET_MILLIS = 10L * 60_000L
    }
}
