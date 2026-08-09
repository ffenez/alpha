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

    @Query("SELECT COUNT(*) FROM samples")
    suspend fun count(): Long

    @Query("DELETE FROM samples WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
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
}

@Dao
interface SpectrumDao {

    @Insert
    suspend fun insert(snapshot: SpectrumSnapshotEntity): Long

    @Query("SELECT * FROM spectra WHERE accumulated = :accumulated ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(accumulated: Boolean): Flow<SpectrumSnapshotEntity?>

    @Query("SELECT * FROM spectra WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun observeRange(from: Long, to: Long): Flow<List<SpectrumSnapshotEntity>>
}
