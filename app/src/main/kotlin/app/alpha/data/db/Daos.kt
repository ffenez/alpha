package app.alpha.data.db

import app.alpha.device.DoseUnits

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Aggregated bucket for downsampled chart queries. */
data class DownsampledSample(
    val bucketStart: Long,
    val avgDoseRate: Float,
    val maxDoseRate: Float,
    val avgCountRate: Float,
    val sampleCount: Int,
)

/**
 * Bucket aggregate carrying the moments the fullscreen chart needs: the true
 * extremes of the interval plus Σx and Σx², from which the pooled mean and the
 * population σ of any group of buckets are exact (no average of averages).
 * SQLite has no `stddev`, so the sums are reduced in Kotlin
 * ([app.alpha.ui.logic.ChartSeriesModel]).
 */
data class DoseBucketAggregate(
    val bucketStart: Long,
    val minDoseRate: Float,
    val maxDoseRate: Float,
    val sumDoseRate: Double,
    val sumSqDoseRate: Double,
    val sampleCount: Int,
)

/**
 * Тот же агрегат корзины, но для произвольной величины: скорости счёта или
 * жёсткости. Отдельный тип, а не переиспользованный [DoseBucketAggregate], —
 * чтобы имена полей не врали про содержимое.
 */
data class ValueBucketAggregate(
    val bucketStart: Long,
    val minValue: Float,
    val maxValue: Float,
    val sumValue: Double,
    val sumSqValue: Double,
    val sampleCount: Int,
)

/** Spectrum snapshot metadata without the counts blob (radon hourly thinning). */
data class SpectrumMetaRow(
    val id: Long,
    val timestamp: Long,
    val durationSeconds: Long,
)

/** Aggregate over one session's time range (see [SampleDao.rangeStats]). */
/**
 * Сводка по диапазону измерений.
 *
 * **Дозовые поля здесь СЫРЫЕ — в единицах прибора, как они лежат в
 * `samples.doseRate`** (CLAUDE.md: сырые значения не конвертируются при
 * записи). Умножение на [DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR] делает
 * потребитель; для показа есть свойства `…MicroSvH`, и в UI использовать надо
 * их.
 */
data class RangeStats(
    val sampleCount: Int,
    val avgDoseRate: Float?,
    val minDoseRate: Float?,
    val maxDoseRate: Float?,
    val avgCountRate: Float?,
    val maxCountRate: Float?,
) {
    val avgDoseRateMicroSvH: Float?
        get() = avgDoseRate?.let { DoseUnits.rawToMicroSievertPerHour(it) }

    val minDoseRateMicroSvH: Float?
        get() = minDoseRate?.let { DoseUnits.rawToMicroSievertPerHour(it) }

    val maxDoseRateMicroSvH: Float?
        get() = maxDoseRate?.let { DoseUnits.rawToMicroSievertPerHour(it) }
}

/**
 * How many samples one baseline-admission verdict accounts for
 * (`reason` = [app.alpha.baseline.BaselineExclusion.storageKey]).
 */
data class ExclusionCount(val reason: String, val samples: Int)

/** Сколько измерений в окне и их края — вход трассы конвейера графика. */
data class RangeCensus(
    val count: Int,
    val minTimestamp: Long?,
    val maxTimestamp: Long?,
)

@Dao
interface SampleDao {

    /** IGNORE + unique timestamp index deduplicates reconnect overlap. */
    /**
     * @return rowid каждой строки; **-1 означает, что строка ОТБРОШЕНА**
     *   уникальным индексом по `timestamp`. Возврат нужен именно ради этого:
     *   до него отброс был не отличим от записи ни на экране, ни в логах.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<SampleEntity>): List<Long>

    /**
     * Страница измерений по идентификатору — для потоковой резервной копии.
     *
     * Ключевая пагинация, а не OFFSET: у миллиона строк смещение заставляет
     * базу пересчитывать пропущенное на каждой странице, и копия замедляется
     * тем сильнее, чем дальше зашла.
     */
    @Query("SELECT * FROM samples WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(afterId: Long, limit: Int): List<SampleEntity>

    /**
     * То же, но только с указанного момента — копия за период.
     *
     * Отбор идёт по времени измерения, а не по идентификатору: строки с
     * меньшим id могут быть свежее, если история переносилась с другого
     * телефона.
     */
    @Query(
        "SELECT * FROM samples WHERE id > :afterId AND timestamp >= :from " +
            "ORDER BY id LIMIT :limit",
    )
    suspend fun pageSince(afterId: Long, from: Long, limit: Int): List<SampleEntity>

    @Query("SELECT COUNT(*) FROM samples WHERE timestamp >= :from")
    suspend fun countSince(from: Long): Long

    /** Полная очистка — только при восстановлении «заменить данные». */
    @Query("DELETE FROM samples")
    suspend fun clear()

    /**
     * Последнее ЗАПИСАННОЕ показание — по порядку вставки, а не по метке
     * времени.
     *
     * Метки записей стоят на базе времени прибора, а она измеряется по ходу
     * сеанса (`DeviceConnection.clockCorrectionMillis`) и может уехать назад:
     * после сдвига свежие записи получают метки МЕНЬШЕ уже лежащих, и
     * `ORDER BY timestamp DESC` отдаёт давнюю строку. `id` монотонен по
     * вставке при любой поправке часов.
     */
    /** Начало истории измерений; null — измерений нет вовсе. */
    @Query("SELECT MIN(timestamp) FROM samples")
    suspend fun earliestTimestamp(): Long?

    /**
     * Что лежит в окне ДО всякой обработки — первый этап трассы конвейера.
     * Три индексных агрегата одним запросом.
     */
    @Query(
        """
        SELECT COUNT(*) AS count, MIN(timestamp) AS minTimestamp,
               MAX(timestamp) AS maxTimestamp
        FROM samples WHERE timestamp BETWEEN :from AND :to
        """,
    )
    suspend fun rangeCensus(from: Long, to: Long): RangeCensus

    @Query("SELECT * FROM samples ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<SampleEntity?>

    @Query("SELECT * FROM samples WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun observeRange(from: Long, to: Long): Flow<List<SampleEntity>>

    /**
     * One-shot read of a range (A/B run statistics): a run is minutes long, so
     * this is hundreds of rows — the spread of the readings cannot be computed
     * by SQLite, which has no STDDEV.
     */
    @Query("SELECT * FROM samples WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun rangeList(from: Long, to: Long): List<SampleEntity>

    /**
     * Time-bucketed aggregation for charts; `bucketMillis` is the bucket width.
     * Served by the timestamp index (range scan + streaming group-by).
     */
    @Query(
        """
        SELECT (timestamp / :bucketMillis) * :bucketMillis AS bucketStart,
               AVG(doseRate) AS avgDoseRate,
               MAX(doseRate) AS maxDoseRate,
               AVG(countRate) AS avgCountRate,
               COUNT(*) AS sampleCount
        FROM samples
        WHERE timestamp BETWEEN :from AND :to
        GROUP BY timestamp / :bucketMillis
        ORDER BY bucketStart
        """,
    )
    suspend fun downsampledRange(from: Long, to: Long, bucketMillis: Long): List<DownsampledSample>

    /**
     * Bucketed moments for the fullscreen dose chart. One index range scan and
     * a streaming group-by inside SQLite; the caller asks for a bounded number
     * of buckets, so a 30-day window costs the same as a 15-minute one.
     */
    @Query(
        """
        SELECT (timestamp / :bucketMillis) * :bucketMillis AS bucketStart,
               MIN(doseRate) AS minDoseRate,
               MAX(doseRate) AS maxDoseRate,
               SUM(doseRate) AS sumDoseRate,
               SUM(doseRate * doseRate) AS sumSqDoseRate,
               COUNT(*) AS sampleCount
        FROM samples
        WHERE timestamp BETWEEN :from AND :to
        GROUP BY timestamp / :bucketMillis
        ORDER BY bucketStart
        """,
    )
    suspend fun doseBucketRange(
        from: Long,
        to: Long,
        bucketMillis: Long,
    ): List<DoseBucketAggregate>

    /**
     * То же для СКОРОСТИ СЧЁТА: механика полноэкранного графика (квантильные
     * конверты, экстремумы, курсор) работает с одной формой агрегата.
     */
    @Query(
        """
        SELECT (timestamp / :bucketMillis) * :bucketMillis AS bucketStart,
               MIN(countRate) AS minValue,
               MAX(countRate) AS maxValue,
               SUM(countRate) AS sumValue,
               SUM(countRate * countRate) AS sumSqValue,
               COUNT(*) AS sampleCount
        FROM samples
        WHERE timestamp BETWEEN :from AND :to
        GROUP BY timestamp / :bucketMillis
        ORDER BY bucketStart
        """,
    )
    suspend fun countRateBucketRange(
        from: Long,
        to: Long,
        bucketMillis: Long,
    ): List<ValueBucketAggregate>

    /**
     * И для ЖЁСТКОСТИ — отношения, посчитанного **по каждому отсчёту**, а не
     * по средним корзины: среднее отношений и отношение средних это разные
     * числа, и второе нельзя выдавать за первое. Отсчёты с малым счётом
     * выбрасываются здесь же — делить на них нечего (порог совпадает с
     * `Hardness.MIN_COUNT_RATE`).
     */
    @Query(
        """
        SELECT (timestamp / :bucketMillis) * :bucketMillis AS bucketStart,
               MIN(doseRate / countRate) AS minValue,
               MAX(doseRate / countRate) AS maxValue,
               SUM(doseRate / countRate) AS sumValue,
               SUM((doseRate / countRate) * (doseRate / countRate)) AS sumSqValue,
               COUNT(*) AS sampleCount
        FROM samples
        WHERE timestamp BETWEEN :from AND :to AND countRate >= :minCountRate
        GROUP BY timestamp / :bucketMillis
        ORDER BY bucketStart
        """,
    )
    suspend fun hardnessBucketRange(
        from: Long,
        to: Long,
        bucketMillis: Long,
        minCountRate: Float,
    ): List<ValueBucketAggregate>

    /**
     * Same bucketed aggregation restricted to one profile and to samples the
     * admission pipeline let through (`baselineExcluded IS NULL`) — the only
     * query that feeds baseline statistics. Excluded samples stay in the table
     * and remain visible on charts and in История.
     */
    @Query(
        """
        SELECT (timestamp / :bucketMillis) * :bucketMillis AS bucketStart,
               AVG(doseRate) AS avgDoseRate,
               MAX(doseRate) AS maxDoseRate,
               AVG(countRate) AS avgCountRate,
               COUNT(*) AS sampleCount
        FROM samples
        WHERE placeId = :profileId AND timestamp BETWEEN :from AND :to
              AND baselineExcluded IS NULL
        GROUP BY timestamp / :bucketMillis
        ORDER BY bucketStart
        """,
    )
    suspend fun downsampledRangeForProfile(
        profileId: Long,
        from: Long,
        to: Long,
        bucketMillis: Long,
    ): List<DownsampledSample>

    /** Exclusion breakdown for one profile («Почему?» and профиль summary). */
    @Query(
        """
        SELECT baselineExcluded AS reason, COUNT(*) AS samples
        FROM samples
        WHERE placeId = :profileId AND timestamp BETWEEN :from AND :to
              AND baselineExcluded IS NOT NULL
        GROUP BY baselineExcluded
        ORDER BY samples DESC
        """,
    )
    suspend fun exclusionCountsForProfile(
        profileId: Long,
        from: Long,
        to: Long,
    ): List<ExclusionCount>

    /** Exclusion breakdown over a plain time range (session journal rows). */
    @Query(
        """
        SELECT baselineExcluded AS reason, COUNT(*) AS samples
        FROM samples
        WHERE timestamp BETWEEN :from AND :to AND baselineExcluded IS NOT NULL
        GROUP BY baselineExcluded
        ORDER BY samples DESC
        """,
    )
    suspend fun exclusionCountsInRange(from: Long, to: Long): List<ExclusionCount>

    @Query(
        """
        SELECT COUNT(*) FROM samples
        WHERE timestamp BETWEEN :from AND :to AND baselineExcluded IS NULL
        """,
    )
    suspend fun admittedCountInRange(from: Long, to: Long): Int

    /** The same, for one profile: what may feed its reference fingerprint. */
    @Query(
        """
        SELECT COUNT(*) FROM samples
        WHERE timestamp BETWEEN :from AND :to
          AND placeId = :profileId AND baselineExcluded IS NULL
        """,
    )
    suspend fun admittedCountForProfile(profileId: Long, from: Long, to: Long): Int

    /** История: move a session's measurements to another profile. */
    @Query("UPDATE samples SET placeId = :profileId WHERE timestamp BETWEEN :from AND :to")
    suspend fun reassignRange(from: Long, to: Long, profileId: Long?)

    /**
     * Re-evaluates the profile-dependent admission verdict over a range after
     * the profile was reassigned: only the «learning off» reason can appear or
     * disappear, every other reason describes the measurement itself and stays.
     */
    @Query(
        """
        UPDATE samples SET baselineExcluded = :reason
        WHERE timestamp BETWEEN :from AND :to
              AND (baselineExcluded IS NULL OR baselineExcluded = :learningOffReason)
        """,
    )
    suspend fun rewriteLearningVerdict(
        from: Long,
        to: Long,
        reason: String?,
        learningOffReason: String,
    )

    /** One aggregate pass over a time range (session summaries). */
    @Query(
        """
        SELECT COUNT(*) AS sampleCount,
               AVG(doseRate) AS avgDoseRate,
               MIN(doseRate) AS minDoseRate,
               MAX(doseRate) AS maxDoseRate,
               AVG(countRate) AS avgCountRate,
               MAX(countRate) AS maxCountRate
        FROM samples
        WHERE timestamp BETWEEN :from AND :to
        """,
    )
    suspend fun rangeStats(from: Long, to: Long): RangeStats

    /** Detach measurements from a deleted profile; the samples stay. */
    @Query("UPDATE samples SET placeId = NULL WHERE placeId = :profileId")
    suspend fun detachProfile(profileId: Long)

    @Query("SELECT COUNT(*) FROM samples")
    suspend fun count(): Long

    @Query("SELECT MAX(timestamp) FROM samples")
    suspend fun latestTimestamp(): Long?

    /**
     * Deletes the measurements of one interval — the user removing a session
     * from История. Retention ([deleteOlderThan]) is age-based; this one is an
     * explicit act on a named period, which is why it is a separate query.
     */
    @Query("DELETE FROM samples WHERE timestamp BETWEEN :from AND :to")
    suspend fun deleteRange(from: Long, to: Long): Int

    @Query("DELETE FROM samples WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface ProfileDao {

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("UPDATE profiles SET name = :name WHERE id = :profileId")
    suspend fun rename(profileId: Long, name: String)

    @Query("UPDATE profiles SET archived = :archived WHERE id = :profileId OR parentId = :profileId")
    suspend fun setArchivedWithChildren(profileId: Long, archived: Boolean)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun delete(profileId: Long)

    /** Detach children of a deleted parent instead of cascading a delete. */
    @Query("UPDATE profiles SET parentId = NULL WHERE parentId = :profileId")
    suspend fun detachChildren(profileId: Long)

    @Query("UPDATE profiles SET baselineEpochMillis = :epochMillis, shiftDeclinedAtMillis = NULL WHERE id = :profileId")
    suspend fun setBaselineEpoch(profileId: Long, epochMillis: Long)

    @Query("UPDATE profiles SET shiftDeclinedAtMillis = :atMillis WHERE id = :profileId")
    suspend fun setShiftDeclined(profileId: Long, atMillis: Long)

    @Insert
    suspend fun insertEpoch(epoch: BaselineEpochEntity): Long

    @Query("SELECT * FROM baseline_epochs WHERE profileId = :profileId ORDER BY endedAtMillis DESC")
    suspend fun epochs(profileId: Long): List<BaselineEpochEntity>

    @Insert
    suspend fun insertFingerprint(fingerprint: ProfileFingerprintEntity): Long

    /** Действующий эталон места: самый свежий. */
    @Query(
        """
        SELECT * FROM profile_fingerprints WHERE profileId = :profileId
        ORDER BY createdAt DESC LIMIT 1
        """,
    )
    suspend fun newestFingerprint(profileId: Long): ProfileFingerprintEntity?

    @Query(
        """
        SELECT * FROM profile_fingerprints WHERE profileId = :profileId
        ORDER BY createdAt DESC LIMIT 1
        """,
    )
    fun observeNewestFingerprint(profileId: Long): Flow<ProfileFingerprintEntity?>

    @Query("SELECT * FROM profiles ORDER BY createdAt")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt")
    suspend fun all(): List<ProfileEntity>

    /** Всё, что привязано к профилям, — для резервной копии одним чтением. */
    @Query("SELECT * FROM profile_networks ORDER BY createdAt")
    suspend fun allNetworks(): List<ProfileNetworkEntity>

    @Query("SELECT * FROM baseline_epochs ORDER BY endedAtMillis")
    suspend fun allEpochs(): List<BaselineEpochEntity>

    @Query("SELECT * FROM profile_fingerprints ORDER BY createdAt")
    suspend fun allFingerprints(): List<ProfileFingerprintEntity>

    @Query("DELETE FROM profiles")
    suspend fun clearProfiles()

    @Query("DELETE FROM baseline_epochs")
    suspend fun clearEpochs()

    @Query("DELETE FROM profile_fingerprints")
    suspend fun clearFingerprints()

    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun byId(profileId: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE role = :role ORDER BY createdAt LIMIT 1")
    suspend fun byRole(role: String): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Long

    // --- Wi-Fi bindings ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetwork(network: ProfileNetworkEntity): Long

    @Query("DELETE FROM profile_networks WHERE id = :id")
    suspend fun deleteNetwork(id: Long)

    @Query("DELETE FROM profile_networks WHERE profileId = :profileId")
    suspend fun deleteNetworksOf(profileId: Long)

    @Query("SELECT * FROM profile_networks ORDER BY createdAt")
    fun observeNetworks(): Flow<List<ProfileNetworkEntity>>

    @Query("SELECT * FROM profile_networks WHERE networkHash = :hash LIMIT 1")
    suspend fun networkByHash(hash: String): ProfileNetworkEntity?
}

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: MeasurementSessionEntity): Long

    /** Страница сессий для резервной копии. */
    @Query("SELECT * FROM measurement_sessions WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(afterId: Long, limit: Int): List<MeasurementSessionEntity>

    @Query(
        "SELECT * FROM measurement_sessions WHERE id > :afterId AND startedAt >= :from " +
            "ORDER BY id LIMIT :limit",
    )
    suspend fun pageSince(afterId: Long, from: Long, limit: Int): List<MeasurementSessionEntity>

    @Query("SELECT COUNT(*) FROM measurement_sessions WHERE startedAt >= :from")
    suspend fun countSince(from: Long): Long

    /**
     * Какие из этих сессий уже есть. Сессия — это ОТРЕЗОК ВРЕМЕНИ, и две
     * разные не могут начаться в одну миллисекунду: начало и есть её ключ.
     */
    @Query("SELECT startedAt FROM measurement_sessions WHERE startedAt IN (:startedAt)")
    suspend fun existingStarts(startedAt: List<Long>): List<Long>

    @Query("DELETE FROM measurement_sessions")
    suspend fun clear()

    @Query("UPDATE measurement_sessions SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun close(sessionId: Long, endedAt: Long)

    /** Crash recovery: close whatever a killed service left open. */
    @Query("UPDATE measurement_sessions SET endedAt = :endedAt WHERE endedAt IS NULL")
    suspend fun closeAllOpen(endedAt: Long)

    /** Newest session — the candidate a restarted service continues. */
    @Query("SELECT * FROM measurement_sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun latest(): MeasurementSessionEntity?

    /** Re-opens a session that was closed by a restart, not by a person. */
    @Query("UPDATE measurement_sessions SET endedAt = NULL WHERE id = :sessionId")
    suspend fun reopen(sessionId: Long)

    @Query("SELECT * FROM measurement_sessions WHERE id = :sessionId")
    suspend fun session(sessionId: Long): MeasurementSessionEntity?

    /** История: the user corrects which profile a past session belonged to. */
    @Query("UPDATE measurement_sessions SET placeId = :profileId WHERE id = :sessionId")
    suspend fun reassignProfile(sessionId: Long, profileId: Long?)

    /** Windowed page, newest first — History stays smooth on months of data. */
    @Query("SELECT * FROM measurement_sessions ORDER BY startedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<MeasurementSessionEntity>

    @Query("DELETE FROM measurement_sessions WHERE id = :sessionId")
    suspend fun delete(sessionId: Long)

    @Query("SELECT COUNT(*) FROM measurement_sessions")
    suspend fun count(): Long
}

@Dao
interface RareDataDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<RareDataEntity>): List<Long>

    @Query("SELECT * FROM rare_data WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(afterId: Long, limit: Int): List<RareDataEntity>

    @Query(
        "SELECT * FROM rare_data WHERE id > :afterId AND timestamp >= :from " +
            "ORDER BY id LIMIT :limit",
    )
    suspend fun pageSince(afterId: Long, from: Long, limit: Int): List<RareDataEntity>

    @Query("SELECT COUNT(*) FROM rare_data WHERE timestamp >= :from")
    suspend fun countSince(from: Long): Long

    @Query("SELECT COUNT(*) FROM rare_data")
    suspend fun count(): Long

    @Query("DELETE FROM rare_data")
    suspend fun clear()

    /** По порядку вставки — по той же причине, что у `SampleDao.observeLatest`. */
    @Query("SELECT * FROM rare_data ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<RareDataEntity?>

    @Query("SELECT * FROM rare_data WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun observeRange(from: Long, to: Long): Flow<List<RareDataEntity>>
}

@Dao
interface EventDao {

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Insert
    suspend fun insertAll(events: List<EventEntity>)

    @Query("SELECT * FROM events WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(afterId: Long, limit: Int): List<EventEntity>

    @Query(
        "SELECT * FROM events WHERE id > :afterId AND timestamp >= :from " +
            "ORDER BY id LIMIT :limit",
    )
    suspend fun pageSince(afterId: Long, from: Long, limit: Int): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events WHERE timestamp >= :from")
    suspend fun countSince(from: Long): Long

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Long

    /** Есть ли уже такое событие — у событий нет уникального ключа в схеме. */
    @Query(
        "SELECT timestamp FROM events WHERE timestamp IN (:timestamps) AND source = :source",
    )
    suspend fun existingTimestamps(timestamps: List<Long>, source: String): List<Long>

    @Query("DELETE FROM events")
    suspend fun clear()

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun observeRange(from: Long, to: Long): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM events WHERE timestamp BETWEEN :from AND :to")
    suspend fun countInRange(from: Long, to: Long): Int

    /** Events of a deleted period: an event about measurements that no longer
     *  exist is a dangling record, not history. */
    @Query("DELETE FROM events WHERE timestamp BETWEEN :from AND :to")
    suspend fun deleteRange(from: Long, to: Long): Int

    /** App-detected deviations/hotspots for History interleaving. */
    @Query(
        """
        SELECT * FROM events
        WHERE timestamp BETWEEN :from AND :to AND source IN (:sources)
        ORDER BY timestamp DESC LIMIT :limit
        """,
    )
    suspend fun inRangeBySource(
        from: Long,
        to: Long,
        sources: List<String>,
        limit: Int,
    ): List<EventEntity>

    /**
     * Located hotspots inside a map viewport, any time («все записи»): the
     * accumulated map shows every sustained excess ever recorded there, not
     * just the ones of the session on screen.
     */
    @Query(
        """
        SELECT * FROM events
        WHERE source = :source
          AND latitude BETWEEN :minLatitude AND :maxLatitude
          AND longitude BETWEEN :minLongitude AND :maxLongitude
        ORDER BY timestamp DESC LIMIT :limit
        """,
    )
    suspend fun locatedInBounds(
        source: String,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
        limit: Int,
    ): List<EventEntity>
}

@Dao
interface TrackDao {

    @Insert
    suspend fun insertSession(session: TrackSessionEntity): Long

    /** Страницы маршрутов и точек для резервной копии. */
    @Query("SELECT * FROM track_sessions WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun sessionPage(afterId: Long, limit: Int): List<TrackSessionEntity>

    @Query(
        "SELECT * FROM track_sessions WHERE id > :afterId AND startedAt >= :from " +
            "ORDER BY id LIMIT :limit",
    )
    suspend fun sessionPageSince(afterId: Long, from: Long, limit: Int): List<TrackSessionEntity>

    @Query("SELECT COUNT(*) FROM track_sessions WHERE startedAt >= :from")
    suspend fun sessionCountSince(from: Long): Long

    /** Все маршруты разом — их немного, а точкам нужен их ключ. */
    @Query("SELECT * FROM track_sessions ORDER BY startedAt")
    suspend fun sessionsOnce(): List<TrackSessionEntity>

    @Query("SELECT * FROM track_points WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun pointPage(afterId: Long, limit: Int): List<TrackPointEntity>

    @Query(
        "SELECT * FROM track_points WHERE id > :afterId AND timestamp >= :from " +
            "ORDER BY id LIMIT :limit",
    )
    suspend fun pointPageSince(afterId: Long, from: Long, limit: Int): List<TrackPointEntity>

    @Query("SELECT COUNT(*) FROM track_points WHERE timestamp >= :from")
    suspend fun pointCountSince(from: Long): Long

    @Query("SELECT COUNT(*) FROM track_sessions")
    suspend fun sessionCount(): Long

    @Query("SELECT COUNT(*) FROM track_points")
    suspend fun totalPointCount(): Long

    /** Маршрут по его естественному ключу: начало записи и название. */
    @Query("SELECT id FROM track_sessions WHERE startedAt = :startedAt AND name = :name LIMIT 1")
    suspend fun sessionByKey(startedAt: Long, name: String): Long?

    @Query(
        "SELECT timestamp FROM track_points WHERE sessionId = :sessionId " +
            "AND timestamp IN (:timestamps)",
    )
    suspend fun existingPointTimes(sessionId: Long, timestamps: List<Long>): List<Long>

    @Insert
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_sessions")
    suspend fun clearSessions()

    @Query("UPDATE track_sessions SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endedAt: Long)

    @Query("SELECT * FROM track_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<TrackSessionEntity>>

    @Query("SELECT * FROM track_sessions WHERE id = :sessionId")
    suspend fun session(sessionId: Long): TrackSessionEntity?

    /** Newest track session — the map shows it when nothing is recording. */
    @Query("SELECT * FROM track_sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestSession(): TrackSessionEntity?

    /** Track sessions overlapping a time range (open map from a measurement session). */
    @Query(
        """
        SELECT * FROM track_sessions
        WHERE startedAt <= :to AND COALESCE(endedAt, startedAt) >= :from
        ORDER BY startedAt
        """,
    )
    suspend fun sessionsOverlapping(from: Long, to: Long): List<TrackSessionEntity>

    @Insert
    suspend fun insertPoint(point: TrackPointEntity): Long

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp")
    fun observePoints(sessionId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT COUNT(*) FROM track_points WHERE sessionId = :sessionId")
    suspend fun pointCount(sessionId: Long): Int

    /** Полный след одним чтением — для расчёта, а не для отрисовки. */
    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun pointsOnce(sessionId: Long): List<TrackPointEntity>

    /** Track sessions overlapping a time range (History «трек» badge). */
    @Query(
        """
        SELECT COUNT(*) FROM track_sessions
        WHERE startedAt <= :to AND COALESCE(endedAt, startedAt) >= :from
        """,
    )
    suspend fun countOverlapping(from: Long, to: Long): Int

    /**
     * Track points above an altitude in a time range (История «полёт» badge).
     * Points arrive at ~1 Hz, so the count approximates seconds spent above
     * the threshold; the exact sustain check runs on the loaded points in the
     * session detail.
     */
    @Query(
        """
        SELECT COUNT(*) FROM track_points
        WHERE timestamp BETWEEN :from AND :to AND altitudeMeters > :minAltitudeMeters
        """,
    )
    suspend fun highAltitudePointCount(from: Long, to: Long, minAltitudeMeters: Double): Int

    @Query("DELETE FROM track_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    /** Незакрытые маршруты: их оставил после себя сбой или выключение. */
    @Query("SELECT * FROM track_sessions WHERE endedAt IS NULL")
    suspend fun unfinishedSessions(): List<TrackSessionEntity>

    /** Последняя записанная точка маршрута — по ней закрывается прерванный. */
    @Query("SELECT MAX(timestamp) FROM track_points WHERE sessionId = :sessionId")
    suspend fun lastPointTime(sessionId: Long): Long?

    @Query("UPDATE track_sessions SET endedAt = :endedAt, interrupted = 1 WHERE id = :sessionId")
    suspend fun markInterrupted(sessionId: Long, endedAt: Long)

    /** Название маршрута даётся после прогулки и меняется когда угодно. */
    @Query("UPDATE track_sessions SET name = :name WHERE id = :sessionId")
    suspend fun renameSession(sessionId: Long, name: String)

    /** Расстояние считается один раз по полному следу и хранится. */
    @Query("UPDATE track_sessions SET distanceMeters = :meters WHERE id = :sessionId")
    suspend fun setDistance(sessionId: Long, meters: Double)

    /**
     * Строка списка маршрутов одним запросом: сколько измерений, когда они
     * шли и каковы среднее с максимумом.
     *
     * Считается в SQLite, а не в приложении: список маршрутов иначе означал бы
     * чтение десятков тысяч координат ради четырёх чисел на карточку. Значения
     * дозы остаются СЫРЫМИ приборными — в мкЗв/ч их переводит один и тот же
     * край приложения, что и везде.
     */
    @Query(
        """
        SELECT COUNT(*) AS pointCount,
               MIN(timestamp) AS firstTime, MAX(timestamp) AS lastTime,
               AVG(doseRate) AS avgDoseRaw, MAX(doseRate) AS maxDoseRaw,
               AVG(countRate) AS avgCps, MAX(countRate) AS maxCps
        FROM track_points WHERE sessionId = :sessionId
        """,
    )
    suspend fun routeSummary(sessionId: Long): TrackRouteSummaryRow

    /**
     * Геометрия для миниатюры: каждая n-я точка маршрута.
     *
     * Прореживание делает SQLite по номеру строки, а не приложение по всему
     * списку: миниатюра размером в ноготь не становится вернее от четырёх
     * тысяч координат, а список из двадцати маршрутов — заметно дешевле.
     */
    @Query(
        """
        SELECT latitude, longitude, doseRate FROM (
            SELECT latitude, longitude, doseRate,
                   ROW_NUMBER() OVER (ORDER BY timestamp) AS rowNumber
            FROM track_points WHERE sessionId = :sessionId
        ) WHERE (rowNumber - 1) % :stride = 0
        """,
    )
    suspend fun routeShape(sessionId: Long, stride: Int): List<TrackShapeRow>

    /**
     * Bounding box of every recorded fix — the first camera of «все записи»
     * (nothing else knows where the user has ever measured). All four values
     * are null when nothing was ever recorded.
     */
    @Query(
        """
        SELECT MIN(latitude) AS minLatitude, MAX(latitude) AS maxLatitude,
               MIN(longitude) AS minLongitude, MAX(longitude) AS maxLongitude
        FROM track_points
        """,
    )
    suspend fun allPointsBounds(): TrackBoundsRow

    /**
     * Aggregate over every fix inside a map viewport, across all recordings.
     * This is the honest denominator of the accumulated map: the summary card
     * is computed from these numbers, never from the subset that got drawn.
     *
     * `useDose` picks the metric column without a second copy of the query;
     * fixes worse than `maxAccuracyMeters` are excluded here and in the grid
     * histogram alike, so both talk about the same set of points.
     */
    @Query(TrackGridSql.AREA_SUMMARY)
    suspend fun boundsSummary(
        useDose: Boolean,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
        maxAccuracyMeters: Float,
    ): TrackAreaSummaryRow

    /**
     * Grid + value histogram of the accumulated map: one row per (cell, value
     * bin) with the exact count, extremes and time span of that pair. Bounded
     * by cells × value bins instead of by the number of fixes, so a viewport
     * costs the same whether the user recorded one walk or three years of them
     * (see [app.alpha.ui.logic.TrackGrid] for the math and the reasoning).
     *
     * `CAST(… AS INTEGER)` truncates towards zero rather than flooring, which
     * is why coordinates are shifted into positive space (+90 / +180) before
     * the division — the pure `TrackGrid.latKey/lonKey` do exactly the same.
     * SQLite before 3.35 (below API 31) has no `floor()`, so this is not
     * stylistic.
     */
    @Query(TrackGridSql.GRID_HISTOGRAM)
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
    ): List<TrackGridBinRow>
}

/** Числа одной строки списка маршрутов; всё null, когда точек нет. */
data class TrackRouteSummaryRow(
    val pointCount: Int,
    val firstTime: Long?,
    val lastTime: Long?,
    /** Приборные единицы — в мкЗв/ч переводит край приложения. */
    val avgDoseRaw: Double?,
    val maxDoseRaw: Double?,
    val avgCps: Double?,
    val maxCps: Double?,
)

/**
 * Прореженная геометрия маршрута для миниатюры вместе с измерением: по ногтю
 * видно не только форму прогулки, но и где уровень был выше.
 */
data class TrackShapeRow(
    val latitude: Double,
    val longitude: Double,
    /** Приборные единицы; в мкЗв/ч переводит край приложения. */
    val doseRate: Float?,
)

/** Bounding box of stored fixes; all null when there are none. */
data class TrackBoundsRow(
    val minLatitude: Double?,
    val maxLatitude: Double?,
    val minLongitude: Double?,
    val maxLongitude: Double?,
)

/** Exact aggregate of one viewport over the full matching set. */
data class TrackAreaSummaryRow(
    val pointCount: Int,
    val valueCount: Int,
    /** Raw device units for dose, CPS for count rate; null = no values. */
    val minValue: Float?,
    val maxValue: Float?,
    val firstTime: Long?,
    val lastTime: Long?,
)

/** One (cell, value bin) pair of the accumulated-map histogram. */
data class TrackGridBinRow(
    val latKey: Int,
    val lonKey: Int,
    val valueKey: Int,
    val pointCount: Int,
    val minValue: Float,
    val maxValue: Float,
    val minTime: Long,
    val maxTime: Long,
)

/** An experiment together with its runs (one screen read). */
data class ExperimentWithRuns(
    val experiment: ExperimentEntity,
    val runs: List<ExperimentRunEntity>,
)

@Dao
interface ExperimentDao {

    @Insert
    suspend fun insert(experiment: ExperimentEntity): Long

    /** Страница опытов для резервной копии. */
    @Query("SELECT * FROM experiments WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(afterId: Long, limit: Int): List<ExperimentEntity>

    @Query(
        "SELECT * FROM experiments WHERE id > :afterId AND createdAt >= :from " +
            "ORDER BY id LIMIT :limit",
    )
    suspend fun pageSince(afterId: Long, from: Long, limit: Int): List<ExperimentEntity>

    @Query("SELECT COUNT(*) FROM experiments WHERE createdAt >= :from")
    suspend fun countSince(from: Long): Long

    /** Опыт по естественному ключу: момент создания и вид. */
    @Query("SELECT id FROM experiments WHERE createdAt = :createdAt AND kind = :kind LIMIT 1")
    suspend fun byKey(createdAt: Long, kind: String): Long?

    @Query("DELETE FROM experiments")
    suspend fun clear()

    @Query("UPDATE experiments SET note = :note WHERE id = :experimentId")
    suspend fun setNote(experimentId: Long, note: String)

    @Query("UPDATE experiments SET geometry = :geometry WHERE id = :experimentId")
    suspend fun setGeometry(experimentId: Long, geometry: String)

    /** Runs cascade with the experiment (foreign key ON DELETE CASCADE). */
    @Query("DELETE FROM experiments WHERE id = :experimentId")
    suspend fun delete(experimentId: Long)

    @Query("SELECT * FROM experiments ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExperimentEntity>>

    /** Опыты одного вида — журналу нужны только измерения продуктов. */
    @Query("SELECT * FROM experiments WHERE kind = :kind ORDER BY createdAt DESC LIMIT :limit")
    fun observeByKind(kind: String, limit: Int): Flow<List<ExperimentEntity>>

    @Query("SELECT * FROM experiments WHERE id = :experimentId")
    suspend fun byId(experimentId: Long): ExperimentEntity?

    @Query("SELECT COUNT(*) FROM experiments")
    suspend fun count(): Long

    @Insert
    suspend fun insertRun(run: ExperimentRunEntity): Long

    @Update
    suspend fun updateRun(run: ExperimentRunEntity)

    @Query("DELETE FROM experiment_runs WHERE id = :runId")
    suspend fun deleteRun(runId: Long)

    @Query("SELECT * FROM experiment_runs WHERE experimentId = :experimentId ORDER BY startedAt")
    suspend fun runs(experimentId: Long): List<ExperimentRunEntity>

    @Query("SELECT * FROM experiment_runs WHERE experimentId = :experimentId ORDER BY startedAt")
    fun observeRuns(experimentId: Long): Flow<List<ExperimentRunEntity>>

    @Query("SELECT * FROM experiment_runs WHERE id = :runId")
    suspend fun run(runId: Long): ExperimentRunEntity?
}

@Dao
interface SpectrumDao {

    @Insert
    suspend fun insert(snapshot: SpectrumSnapshotEntity): Long

    /**
     * Страница спектров для резервной копии. Порция мелкая: у каждой строки
     * внутри тысячи каналов, и сотня спектров разом — это уже мегабайты.
     */
    @Query("SELECT * FROM spectra WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(afterId: Long, limit: Int): List<SpectrumSnapshotEntity>

    /** Своё имя снимка: человек называет его так, как узнает через год. */
    @Query("UPDATE spectra SET label = :label WHERE id = :id")
    suspend fun rename(id: Long, label: String?)

    /**
     * Поправить профиль снимка.
     *
     * Меняется только привязка: сами отсчёты, время и калибровка остаются
     * теми же — исправляется запись о том, ГДЕ снимали, а не то, что сняли.
     */
    @Query("UPDATE spectra SET profileId = :profileId, profileName = :profileName WHERE id = :id")
    suspend fun setProfile(id: Long, profileId: Long?, profileName: String?)

    @Query(
        "SELECT * FROM spectra WHERE id > :afterId AND timestamp >= :from " +
            "ORDER BY id LIMIT :limit",
    )
    suspend fun pageSince(afterId: Long, from: Long, limit: Int): List<SpectrumSnapshotEntity>

    @Query("SELECT COUNT(*) FROM spectra WHERE timestamp >= :from")
    suspend fun countSince(from: Long): Long

    /** Какие из этих спектров уже есть: момент съёмки — их естественный ключ. */
    @Query("SELECT timestamp FROM spectra WHERE timestamp IN (:timestamps)")
    suspend fun existingTimestamps(timestamps: List<Long>): List<Long>

    @Query("DELETE FROM spectra")
    suspend fun clear()

    /** Latest device-measured spectrum; imported files never count as one. */
    @Query(
        """
        SELECT * FROM spectra
        WHERE accumulated = :accumulated AND origin NOT IN ('import', 'derived')
        ORDER BY timestamp DESC LIMIT 1
        """,
    )
    fun observeLatest(accumulated: Boolean): Flow<SpectrumSnapshotEntity?>

    /** Newest user-recorded background reference (Спектр overlay/subtraction). */
    @Query(
        """
        SELECT * FROM spectra WHERE isBackgroundReference = 1 AND origin != 'import'
        ORDER BY timestamp DESC LIMIT 1
        """,
    )
    fun observeBackgroundReference(): Flow<SpectrumSnapshotEntity?>

    @Query("SELECT * FROM spectra WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun observeRange(from: Long, to: Long): Flow<List<SpectrumSnapshotEntity>>

    /**
     * Момент последнего приборного снимка — сигнал «появились новые данные»
     * для рядов, которые считаются ПО снимкам (радон, линия во времени).
     *
     * Возвращается одна метка, а не строки: ряду нужен повод пересчитаться, а
     * не сами спектры — их он потом читает сам, прореженными до часа. Room
     * пересылает значение при каждом изменении таблицы, поэтому опрос по
     * таймеру перестаёт быть основным способом узнать о новом снимке.
     */
    @Query(
        """
        SELECT MAX(timestamp) FROM spectra
        WHERE origin NOT IN ('import', 'derived')
        """,
    )
    fun observeLatestDeviceSnapshotAt(): Flow<Long?>

    /** Device snapshots in a session range («спектр» badge); imports excluded. */
    @Query(
        """
        SELECT COUNT(*) FROM spectra
        WHERE timestamp BETWEEN :from AND :to AND origin NOT IN ('import', 'derived')
        """,
    )
    suspend fun countInRange(from: Long, to: Long): Int

    /**
     * История list: explicit user saves, imported files and background
     * references — the periodic autosaves stay out (one row per minute would
     * drown the list).
     */
    @Query(
        """
        SELECT * FROM spectra
        WHERE origin IN ('user', 'import') OR isBackgroundReference = 1
        ORDER BY timestamp DESC LIMIT :limit
        """,
    )
    fun observeSaved(limit: Int): Flow<List<SpectrumSnapshotEntity>>

    @Query("SELECT * FROM spectra WHERE id = :id")
    suspend fun byId(id: Long): SpectrumSnapshotEntity?

    @Query("SELECT COUNT(*) FROM spectra")
    suspend fun count(): Long

    /** Deletes the chosen snapshots — an explicit act from История. */
    @Query("DELETE FROM spectra WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    /**
     * Device since-reset snapshot metadata in a range, blobs not loaded —
     * the radon screen thins these to one row per hour before fetching
     * full spectra by id.
     */
    @Query(
        """
        SELECT id, timestamp, durationSeconds FROM spectra
        WHERE origin NOT IN ('import', 'derived') AND accumulated = 0
              AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp
        """,
    )
    suspend fun deviceSnapshotMeta(from: Long, to: Long): List<SpectrumMetaRow>
}
