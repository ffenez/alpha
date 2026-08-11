package app.radiacode.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * One raw sample reduced to the four columns the pre-aggregation needs. The
 * projection matters: a day of history is 86 400 rows, and dragging the whole
 * entity (with its blobs of flags and error percentages) through the cursor
 * would triple the cost of a backfill for nothing.
 */
data class RawSampleRow(
    val timestamp: Long,
    val doseRate: Float,
    /** 1 when the sample was admitted to the baseline (`baselineExcluded IS NULL`). */
    val admitted: Int,
    val profileId: Long?,
)

/** One raw dose-rate reading with its instant (diagnostic reference path). */
data class DosePoint(val timestamp: Long, val doseRate: Float)

/**
 * Exact rollup of a time range over `minute_stats` (ADR 004): SQLite scans the
 * minute rows over the primary key and returns **one** row, so a 30-day window
 * costs no row transfer at all. n/Σx/Σx²/min/max are exact over the raw
 * samples — only the percentiles need the sketch path.
 */
data class MinuteRollup(
    val minutes: Int,
    val sampleCount: Int?,
    val admittedCount: Int?,
    val sumDoseRate: Double?,
    val sumSqDoseRate: Double?,
    val minDoseRate: Float?,
    val maxDoseRate: Float?,
    val firstSampleTime: Long?,
    val lastSampleTime: Long?,
)

/**
 * Persistence of the versioned pre-aggregation of ADR 004: minute scalars and
 * hourly quantile sketches, both rebuildable from `samples` at any time.
 *
 * Kept apart from [SampleDao] deliberately — this is a derived layer, and a
 * derived layer must never look like the raw data (CHART SPEC §2).
 */
@Dao
interface PreAggregateDao {

    // --- raw input ---------------------------------------------------------

    @Query(
        """
        SELECT timestamp,
               doseRate,
               (baselineExcluded IS NULL) AS admitted,
               placeId AS profileId
        FROM samples
        WHERE timestamp BETWEEN :from AND :to
        ORDER BY timestamp
        """,
    )
    suspend fun rawSamples(from: Long, to: Long): List<RawSampleRow>

    /**
     * One page of raw dose rates, ordered — the exact reference path of the
     * exact-vs-sketch diagnostic (CHART SPEC §34, §37G). Paged by keyset
     * (`timestamp` is unique), so a window of millions of samples can be
     * streamed into a primitive array without holding a cursor open or
     * paying OFFSET's quadratic cost.
     */
    @Query(
        """
        SELECT timestamp, doseRate FROM samples
        WHERE timestamp > :afterTimestamp AND timestamp <= :to
        ORDER BY timestamp LIMIT :limit
        """,
    )
    suspend fun rawDosePage(afterTimestamp: Long, to: Long, limit: Int): List<DosePoint>

    @Query("SELECT COUNT(*) FROM samples WHERE timestamp BETWEEN :from AND :to")
    suspend fun rawCount(from: Long, to: Long): Int

    @Query("SELECT MIN(timestamp) FROM samples")
    suspend fun earliestSampleTime(): Long?

    @Query("SELECT MAX(timestamp) FROM samples")
    suspend fun latestSampleTime(): Long?

    // --- minute scalars ----------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMinutes(rows: List<MinuteStatEntity>)

    /**
     * Clears a range before it is rewritten. Needed because a rebuild may find
     * *fewer* minutes than the previous run (history pruned, samples deleted):
     * REPLACE alone would leave a stale row claiming measurements that no
     * longer exist.
     */
    @Query("DELETE FROM minute_stats WHERE minuteStart BETWEEN :from AND :to")
    suspend fun deleteMinutes(from: Long, to: Long)

    @Query("SELECT * FROM minute_stats WHERE minuteStart BETWEEN :from AND :to ORDER BY minuteStart")
    suspend fun minutes(from: Long, to: Long): List<MinuteStatEntity>

    @Query(
        """
        SELECT COUNT(*) AS minutes,
               SUM(count) AS sampleCount,
               SUM(admittedCount) AS admittedCount,
               SUM(sumDoseRate) AS sumDoseRate,
               SUM(sumSqDoseRate) AS sumSqDoseRate,
               MIN(minDoseRate) AS minDoseRate,
               MAX(maxDoseRate) AS maxDoseRate,
               MIN(firstSampleTime) AS firstSampleTime,
               MAX(lastSampleTime) AS lastSampleTime
        FROM minute_stats
        WHERE minuteStart BETWEEN :from AND :to
        """,
    )
    suspend fun minuteRollup(from: Long, to: Long): MinuteRollup

    @Query("SELECT COUNT(*) FROM minute_stats")
    suspend fun minuteCount(): Int

    // --- hourly sketches ---------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHours(rows: List<HourSketchEntity>)

    @Query("SELECT * FROM hour_sketches WHERE hourStart BETWEEN :from AND :to ORDER BY hourStart")
    suspend fun hourSketches(from: Long, to: Long): List<HourSketchEntity>

    /**
     * Which hours already carry a sketch of the current algorithm version —
     * the resume marker of the backfill. Sketches of an older version are
     * reported as missing, so an algorithm bump rebuilds them from raw.
     */
    @Query(
        """
        SELECT hourStart FROM hour_sketches
        WHERE hourStart BETWEEN :from AND :to
              AND algorithmVersion = :algorithmVersion AND sketchK = :sketchK
        ORDER BY hourStart
        """,
    )
    suspend fun builtHourStarts(
        from: Long,
        to: Long,
        algorithmVersion: Int,
        sketchK: Int,
    ): List<Long>

    @Query("SELECT COUNT(*) FROM hour_sketches WHERE hourStart BETWEEN :from AND :to")
    suspend fun hourCount(from: Long, to: Long): Int

    @Query("DELETE FROM hour_sketches WHERE hourStart BETWEEN :from AND :to")
    suspend fun deleteHours(from: Long, to: Long)
}
