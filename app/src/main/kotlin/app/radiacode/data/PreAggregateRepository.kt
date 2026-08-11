package app.radiacode.data

import app.radiacode.data.db.HourSketchEntity
import app.radiacode.data.db.MinuteRollup
import app.radiacode.data.db.MinuteStatEntity
import app.radiacode.data.db.PreAggregateDao
import app.radiacode.data.preagg.PreAggregateMath
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Read side of the pre-aggregation (ADR 004). The chart asks this facade for
 * the long-window path; the writer lives in
 * [app.radiacode.data.preagg.PreAggregator].
 *
 * Everything here is derived data with a version. Raw `samples` remain the
 * source of truth and can rebuild all of it (CHART SPEC §2).
 */
class PreAggregateRepository(private val dao: PreAggregateDao) {

    /** Hourly sketches overlapping a range, ascending. */
    suspend fun hourSketches(fromMillis: Long, toMillis: Long): List<HourSketchEntity> =
        dao.hourSketches(fromMillis, toMillis)

    /**
     * Exact n/Σx/Σx²/min/max of a window from the minute scalars. SQLite
     * aggregates over the primary key and returns one row, so this costs no
     * row transfer whatever the window (ADR 004, CHART SPEC §34).
     */
    suspend fun rollup(fromMillis: Long, toMillis: Long): MinuteRollup =
        dao.minuteRollup(fromMillis, toMillis)

    /**
     * Hourly sketches of a range **plus the trailing hours that are not stored
     * yet**, built on the fly from raw.
     *
     * The writer only closes an hour once it is complete (plus a grace period
     * for late records), so without this the long chart would end up to an
     * hour behind the live edge. Building the tail costs at most
     * [LIVE_TAIL_HOURS] × 3600 raw rows — a bounded, deliberate exception to
     * «long windows never read raw rows», paid only for the newest hours.
     */
    suspend fun hourSketchesWithLiveTail(
        fromMillis: Long,
        toMillis: Long,
        liveTailHours: Int = LIVE_TAIL_HOURS,
    ): List<HourSketchEntity> {
        val stored = dao.hourSketches(fromMillis, toMillis)
        val storedStarts = stored.mapTo(HashSet()) { it.hourStart }
        val newest = PreAggregateMath.hourStartOf(toMillis)
        val live = ArrayList<HourSketchEntity>(liveTailHours)
        for (i in 0 until liveTailHours) {
            val hourStart = newest - i * PreAggregateMath.HOUR_MILLIS
            if (hourStart < fromMillis || hourStart in storedStarts) continue
            coroutineContext.ensureActive()
            val rows = dao.rawSamples(
                hourStart,
                minOf(hourStart + PreAggregateMath.HOUR_MILLIS - 1, toMillis),
            )
            PreAggregateMath.hour(hourStart, rows)?.let { live += it }
        }
        return if (live.isEmpty()) stored else (stored + live).sortedBy { it.hourStart }
    }

    /** Minute scalars of a range (gap inspection, per-profile summaries). */
    suspend fun minutes(fromMillis: Long, toMillis: Long): List<MinuteStatEntity> =
        dao.minutes(fromMillis, toMillis)

    /** Raw samples in a range — the number the row budget of §34 is about. */
    suspend fun rawCount(fromMillis: Long, toMillis: Long): Int =
        dao.rawCount(fromMillis, toMillis)

    /**
     * Every raw dose rate of a window, streamed in pages into one primitive
     * array — the exact reference the diagnostic compares the sketch against
     * (CHART SPEC §34, §37G).
     *
     * Returns null when the window holds more than [maxRows] samples: this is
     * the *slow* path by definition, and refusing loudly is honest, where
     * silently comparing a subrange would report an error that belongs to a
     * different dataset. Cancellable between pages.
     */
    suspend fun rawDoseValues(
        fromMillis: Long,
        toMillis: Long,
        maxRows: Int = MAX_DIAGNOSTIC_ROWS,
    ): FloatArray? {
        val total = dao.rawCount(fromMillis, toMillis)
        if (total > maxRows) return null
        if (total == 0) return FloatArray(0)
        val out = FloatArray(total)
        var written = 0
        var cursor = fromMillis - 1
        while (written < total) {
            coroutineContext.ensureActive()
            val page = dao.rawDosePage(cursor, toMillis, PAGE_ROWS)
            if (page.isEmpty()) break
            for (point in page) {
                if (written >= total) break
                out[written++] = point.doseRate
            }
            cursor = page.last().timestamp
        }
        return if (written == total) out else out.copyOf(written)
    }

    companion object {
        /** Newest hours the chart may build in memory for the live edge. */
        const val LIVE_TAIL_HOURS = 2

        /** Rows the diagnostic is willing to read: ~46 days at 1 Hz. */
        const val MAX_DIAGNOSTIC_ROWS = 4_000_000

        private const val PAGE_ROWS = 100_000
    }
}
