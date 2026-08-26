package app.alpha.data

import app.alpha.baseline.BaselineExclusion
import app.alpha.data.db.DownsampledSample
import app.alpha.data.db.EventDao
import app.alpha.data.db.EventEntity
import app.alpha.data.db.MeasurementSessionEntity
import app.alpha.data.db.ProfileDao
import app.alpha.data.db.ProfileEntity
import app.alpha.data.db.RangeStats
import app.alpha.data.db.SampleDao
import app.alpha.data.db.PreAggregateDao
import app.alpha.data.db.SessionDao
import app.alpha.data.db.SpectrumDao
import app.alpha.data.db.TrackDao
import app.alpha.ui.logic.ChartMapping
import app.alpha.ui.logic.DeletionPlan
import app.alpha.ui.logic.FlightDetect
import app.alpha.ui.logic.ProfileTree

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
    /** Прибор, которым велась запись; null — пометки нет (прежняя версия). */
    val deviceSerial: String? = null,
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
    private val preAggregateDao: PreAggregateDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    // --- deletion (История) ---

    /**
     * Counts what deleting these sessions and snapshots would take away,
     * **without** touching anything (`ui/logic/HistoryDeletion` turns it into
     * the confirmation text).
     *
     * Deleting a session means deleting the measurements inside it — this is
     * the one place in the app where data really disappears, so the user is
     * told the size of it before, not after.
     */
    suspend fun deletionPlan(sessionIds: Set<Long>, spectrumIds: Set<Long>): DeletionPlan {
        var samples = 0L
        var events = 0
        var seconds = 0L
        for (id in sessionIds) {
            val session = sessionDao.session(id) ?: continue
            val from = session.startedAt
            val to = session.endedAt ?: clock()
            samples += preAggregateDao.rawCount(from, to).toLong()
            events += eventDao.countInRange(from, to)
            seconds += ((to - from) / 1000L).coerceAtLeast(0L)
        }
        return DeletionPlan(
            sessions = sessionIds.size,
            samples = samples,
            events = events,
            spectra = spectrumIds.size,
            seconds = seconds,
        )
    }

    /**
     * Deletes the selected sessions with their measurements, and the selected
     * spectra.
     *
     * The **derived** rows go with the raw ones: minute scalars and hourly
     * sketches (ADR 004) are a cache of the samples, and a chart that keeps
     * drawing a period whose measurements were deleted would be showing
     * something the app can no longer justify. Tracks and unselected spectra
     * are left alone — they are their own records, and the confirmation says
     * so.
     */
    suspend fun delete(sessionIds: Set<Long>, spectrumIds: Set<Long>) {
        for (id in sessionIds) {
            val session = sessionDao.session(id) ?: continue
            val from = session.startedAt
            val to = session.endedAt ?: clock()
            sampleDao.deleteRange(from, to)
            eventDao.deleteRange(from, to)
            preAggregateDao.deleteMinutes(from, to)
            preAggregateDao.deleteHours(from, to)
            sessionDao.delete(id)
        }
        if (spectrumIds.isNotEmpty()) spectrumDao.deleteByIds(spectrumIds.toList())
    }

    // --- lifecycle (service) ---

    /**
     * @param deviceSerial чей прибор ведёт запись; null — прибор неизвестен.
     *   Пометка своя, потому что отнесение по измерениям отрезка перестаёт
     *   работать, как только измерения убраны уборкой журнала.
     */
    suspend fun open(profileId: Long?, deviceSerial: String? = null): Long =
        sessionDao.insert(
            MeasurementSessionEntity(
                profileId = profileId,
                startedAt = clock(),
                deviceSerial = deviceSerial,
            ),
        )

    /**
     * Продолжить последнюю запись или начать новую.
     *
     * Перезапуск службы — не решение человека. Системе ничего не стоит убить
     * фоновый процесс и поднять его заново, и каждый такой круг раньше
     * добавлял в журнал новую запись: за три часа в одном месте их набиралось
     * восемь. Здесь запись продолжается, если она о ТОМ ЖЕ месте и с её конца
     * прошло меньше [graceMillis]; иначе честно закрывается и открывается
     * новая.
     *
     * Возврат: id записи, в которую пишем дальше.
     */
    suspend fun resumeOrOpen(profileId: Long?, graceMillis: Long): Long {
        val now = clock()
        val latest = sessionDao.latest()
        val lastActivity = latest?.let { it.endedAt ?: sampleDao.latestTimestamp() ?: it.startedAt }
        val continuable = latest != null &&
            latest.profileId == profileId &&
            lastActivity != null &&
            now - lastActivity <= graceMillis
        if (continuable) {
            if (latest.endedAt != null) sessionDao.reopen(latest.id)
            return latest.id
        }
        if (latest?.endedAt == null && latest != null) {
            sessionDao.close(latest.id, lastActivity ?: now)
        }
        return open(profileId)
    }

    suspend fun close(sessionId: Long, endedAt: Long = clock()) =
        sessionDao.close(sessionId, endedAt)

    /**
     * Crash recovery on service start: sessions left open by a killed process
     * end at the last recorded sample (honest end, not "now").
     *
     * Закрытие здесь — только уборка следов падения. Продолжится ли запись,
     * решает [resumeOrOpen] при первом же подключении: закрытая полминуты
     * назад запись о том же месте — та же самая запись.
     */
    suspend fun closeStale() {
        val endedAt = sampleDao.latestTimestamp() ?: clock()
        sessionDao.closeAllOpen(endedAt)
        sessionDao.repairNegativeDurations()
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

    /**
     * События ленты Истории за интервал, новые сверху.
     *
     * Прежние точечные `deviation` сюда НЕ входят: журнал показывает
     * подтверждённые эпизоды, а не каждое срабатывание детектора
     * (`history_semantic_events_redesign.md`). Сами записи остаются в базе и в
     * экспорте — данные измерений не удаляются ради чистой ленты; их видно во
     * вкладке событий и в резервной копии.
     */
    suspend fun deviationEvents(
        from: Long,
        to: Long,
        limit: Int = 200,
        includeLegacy: Boolean = false,
    ): List<EventEntity> = eventDao.inRangeBySource(
        from = from,
        to = to,
        sources = buildList {
            add(EventEntity.SOURCE_HOTSPOT)
            addAll(EventEntity.EPISODE_SOURCES)
            if (includeLegacy) add(EventEntity.SOURCE_DEVIATION)
        },
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
            deviceSerial = session.deviceSerial,
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
