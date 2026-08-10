package app.radiacode.data.db

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

/** Spectrum snapshot metadata without the counts blob (radon hourly thinning). */
data class SpectrumMetaRow(
    val id: Long,
    val timestamp: Long,
    val durationSeconds: Long,
)

/** Aggregate over one session's time range (see [SampleDao.rangeStats]). */
data class RangeStats(
    val sampleCount: Int,
    val avgDoseRate: Float?,
    val minDoseRate: Float?,
    val maxDoseRate: Float?,
    val avgCountRate: Float?,
    val maxCountRate: Float?,
)

/**
 * How many samples one baseline-admission verdict accounts for
 * (`reason` = [app.radiacode.baseline.BaselineExclusion.storageKey]).
 */
data class ExclusionCount(val reason: String, val samples: Int)

@Dao
interface SampleDao {

    /** IGNORE + unique timestamp index deduplicates reconnect overlap. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<SampleEntity>)

    @Query("SELECT * FROM samples ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<SampleEntity?>

    @Query("SELECT * FROM samples WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun observeRange(from: Long, to: Long): Flow<List<SampleEntity>>

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

    @Query("SELECT * FROM profiles ORDER BY createdAt")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt")
    suspend fun all(): List<ProfileEntity>

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

    @Query("UPDATE measurement_sessions SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun close(sessionId: Long, endedAt: Long)

    /** Crash recovery: close whatever a killed service left open. */
    @Query("UPDATE measurement_sessions SET endedAt = :endedAt WHERE endedAt IS NULL")
    suspend fun closeAllOpen(endedAt: Long)

    @Query("SELECT * FROM measurement_sessions WHERE id = :sessionId")
    suspend fun session(sessionId: Long): MeasurementSessionEntity?

    /** История: the user corrects which profile a past session belonged to. */
    @Query("UPDATE measurement_sessions SET placeId = :profileId WHERE id = :sessionId")
    suspend fun reassignProfile(sessionId: Long, profileId: Long?)

    /** Windowed page, newest first — History stays smooth on months of data. */
    @Query("SELECT * FROM measurement_sessions ORDER BY startedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<MeasurementSessionEntity>

    @Query("SELECT COUNT(*) FROM measurement_sessions")
    suspend fun count(): Long
}

@Dao
interface RareDataDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<RareDataEntity>)

    @Query("SELECT * FROM rare_data ORDER BY timestamp DESC LIMIT 1")
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

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun observeRange(from: Long, to: Long): Flow<List<EventEntity>>

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
}

@Dao
interface TrackDao {

    @Insert
    suspend fun insertSession(session: TrackSessionEntity): Long

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
}

@Dao
interface SpectrumDao {

    @Insert
    suspend fun insert(snapshot: SpectrumSnapshotEntity): Long

    /** Latest device-measured spectrum; imported files never count as one. */
    @Query(
        """
        SELECT * FROM spectra WHERE accumulated = :accumulated AND origin != 'import'
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

    /** Device snapshots in a session range («спектр» badge); imports excluded. */
    @Query(
        """
        SELECT COUNT(*) FROM spectra
        WHERE timestamp BETWEEN :from AND :to AND origin != 'import'
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

    /**
     * Device since-reset snapshot metadata in a range, blobs not loaded —
     * the radon screen thins these to one row per hour before fetching
     * full spectra by id.
     */
    @Query(
        """
        SELECT id, timestamp, durationSeconds FROM spectra
        WHERE origin != 'import' AND accumulated = 0 AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp
        """,
    )
    suspend fun deviceSnapshotMeta(from: Long, to: Long): List<SpectrumMetaRow>
}
