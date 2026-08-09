package app.radiacode.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Aggregated bucket for downsampled chart queries. */
data class DownsampledSample(
    val bucketStart: Long,
    val avgDoseRate: Float,
    val maxDoseRate: Float,
    val avgCountRate: Float,
    val sampleCount: Int,
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

    /** Same bucketed aggregation restricted to one place (baseline input). */
    @Query(
        """
        SELECT (timestamp / :bucketMillis) * :bucketMillis AS bucketStart,
               AVG(doseRate) AS avgDoseRate,
               MAX(doseRate) AS maxDoseRate,
               AVG(countRate) AS avgCountRate,
               COUNT(*) AS sampleCount
        FROM samples
        WHERE placeId = :placeId AND timestamp BETWEEN :from AND :to
        GROUP BY timestamp / :bucketMillis
        ORDER BY bucketStart
        """,
    )
    suspend fun downsampledRangeForPlace(
        placeId: Long,
        from: Long,
        to: Long,
        bucketMillis: Long,
    ): List<DownsampledSample>

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

    /** Detach measurements from a deleted place; the samples stay. */
    @Query("UPDATE samples SET placeId = NULL WHERE placeId = :placeId")
    suspend fun detachPlace(placeId: Long)

    @Query("SELECT COUNT(*) FROM samples")
    suspend fun count(): Long

    @Query("SELECT MAX(timestamp) FROM samples")
    suspend fun latestTimestamp(): Long?

    @Query("DELETE FROM samples WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface PlaceDao {

    @Insert
    suspend fun insert(place: PlaceEntity): Long

    @Query("UPDATE places SET name = :name WHERE id = :placeId")
    suspend fun rename(placeId: Long, name: String)

    @Query("DELETE FROM places WHERE id = :placeId")
    suspend fun delete(placeId: Long)

    @Query("SELECT * FROM places ORDER BY createdAt")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places ORDER BY createdAt")
    suspend fun all(): List<PlaceEntity>

    @Query("SELECT COUNT(*) FROM places")
    suspend fun count(): Long
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
}

@Dao
interface SpectrumDao {

    @Insert
    suspend fun insert(snapshot: SpectrumSnapshotEntity): Long

    @Query("SELECT * FROM spectra WHERE accumulated = :accumulated ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(accumulated: Boolean): Flow<SpectrumSnapshotEntity?>

    /** Newest user-recorded background reference (Спектр overlay/subtraction). */
    @Query("SELECT * FROM spectra WHERE isBackgroundReference = 1 ORDER BY timestamp DESC LIMIT 1")
    fun observeBackgroundReference(): Flow<SpectrumSnapshotEntity?>

    @Query("SELECT * FROM spectra WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun observeRange(from: Long, to: Long): Flow<List<SpectrumSnapshotEntity>>

    @Query("SELECT COUNT(*) FROM spectra WHERE timestamp BETWEEN :from AND :to")
    suspend fun countInRange(from: Long, to: Long): Int
}
