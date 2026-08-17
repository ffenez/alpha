package app.alpha.data.preagg

import app.alpha.analysis.quantiles.KllSketch
import app.alpha.data.db.PreAggregateDao
import app.alpha.data.preagg.PreAggregateMath.HOUR_MILLIS
import app.alpha.data.preagg.PreAggregateMath.MINUTE_MILLIS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * State of the history backfill — what the chart shows while the
 * pre-aggregation is still being built (CHART SPEC §32: an approximate result
 * must say what it is).
 */
data class BackfillProgress(
    val hoursDone: Int = 0,
    val hoursTotal: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
) {
    /** 0..1; 1 when there was nothing to do. */
    val fraction: Float
        get() = if (hoursTotal <= 0) 1f else (hoursDone.toFloat() / hoursTotal).coerceIn(0f, 1f)
}

/**
 * Writer of the versioned pre-aggregation of ADR 004: minute scalars and
 * hourly mergeable quantile sketches, both derived from `samples` and both
 * rebuildable at any time.
 *
 * ## How it stays correct
 *
 * Everything is **recomputed from raw**, never incremented. That single rule
 * gives all the properties this layer has to have:
 *
 *  - *idempotent*: writing the same minute twice writes the same bytes;
 *  - *restart-safe*: a process killed mid-minute cannot double-count, because
 *    there is no counter to double — the next pass reads the raw samples of
 *    that minute again;
 *  - *self-healing*: the RC-110 hands over buffered records after a
 *    reconnect, so samples can arrive for a minute that was already closed.
 *    [advance] therefore rewrites the last few minutes on every tick and
 *    rebuilds the newest complete hour whenever its stored `count` disagrees
 *    with the raw table.
 *
 * Ordering inside an hour is «minutes first, sketch last», and the sketch row
 * is what [backfill] treats as «this hour is done». A crash between the two
 * leaves the hour unbuilt rather than half-built.
 *
 * ## Cost
 *
 * [advance] runs once a minute and reads ~3 minutes of raw rows (~180) plus
 * one `COUNT(*)` over an indexed range. Closing an hour reads that hour once
 * (~3600 rows). [backfill] walks the history hour by hour, one indexed range
 * scan each, and yields between hours so it can be cancelled at any moment.
 */
class PreAggregator(
    private val dao: PreAggregateDao,
    private val sketchK: Int = KllSketch.DEFAULT_K,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val progressState = MutableStateFlow(BackfillProgress())

    /** Backfill progress for the UI («предагрегация … %»). */
    val progress: StateFlow<BackfillProgress> = progressState.asStateFlow()

    @Volatile
    private var started = false

    /**
     * Starts the single background writer: one history backfill, then a
     * minute-by-minute close loop. Called from exactly one place (the
     * measurement service) so there is never a second writer.
     */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) {
            try {
                backfill()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed backfill must not take the measurement service
                // down: the chart falls back to the coarse path and says so.
            }
            while (isActive) {
                try {
                    advance()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Same: the next tick tries again.
                }
                delay(ADVANCE_INTERVAL_MILLIS)
            }
        }
    }

    /**
     * Closes what can be closed at [nowMillis]: the minutes that have just
     * passed, and the newest complete hour when it is missing or stale.
     * Idempotent — calling it twice changes nothing.
     */
    suspend fun advance(nowMillis: Long = clock()) {
        val currentMinute = PreAggregateMath.minuteStartOf(nowMillis)
        val tailStart = currentMinute - LIVE_TAIL_MINUTES * MINUTE_MILLIS
        if (currentMinute > tailStart) {
            rewriteMinutes(tailStart, currentMinute - 1)
        }
        // Hours are closed with a grace period: the tail of an hour can still
        // arrive from the device's buffer right after the boundary.
        val newestComplete = PreAggregateMath.hourStartOf(nowMillis - HOUR_GRACE_MILLIS) - HOUR_MILLIS
        for (i in 0 until VERIFY_HOURS) {
            val hourStart = newestComplete - i * HOUR_MILLIS
            if (hourStart < 0) break
            if (!isHourCurrent(hourStart)) buildHour(hourStart)
        }
    }

    /**
     * Rebuilds every hour of the stored history that has no current-version
     * sketch. Idempotent, resumable (the sketch rows themselves are the resume
     * marker — no side file, no setting), and cancellable between hours.
     *
     * Runs on whatever dispatcher the caller provides; [start] gives it
     * [Dispatchers.IO], and it never touches the UI thread.
     */
    suspend fun backfill(
        nowMillis: Long = clock(),
        onProgress: (BackfillProgress) -> Unit = {},
    ): BackfillProgress {
        val earliest = dao.earliestSampleTime()
        val latest = dao.latestSampleTime()
        if (earliest == null || latest == null) {
            return finish(BackfillProgress(finished = true), onProgress)
        }
        val firstHour = PreAggregateMath.hourStartOf(earliest)
        val lastHour = minOf(
            PreAggregateMath.hourStartOf(latest),
            PreAggregateMath.hourStartOf(nowMillis - HOUR_GRACE_MILLIS) - HOUR_MILLIS,
        )
        if (lastHour < firstHour) {
            return finish(BackfillProgress(finished = true), onProgress)
        }
        val built = dao.builtHourStarts(
            from = firstHour,
            to = lastHour,
            algorithmVersion = KllSketch.ALGORITHM_VERSION,
            sketchK = sketchK,
        ).toHashSet()
        val total = ((lastHour - firstHour) / HOUR_MILLIS + 1).toInt()
        var done = built.size
        var state = BackfillProgress(hoursDone = done, hoursTotal = total, running = true)
        progressState.value = state
        onProgress(state)

        var hourStart = firstHour
        var sinceReport = 0
        while (hourStart <= lastHour) {
            coroutineContext.ensureActive()
            if (hourStart !in built) {
                buildHour(hourStart)
                done++
                sinceReport++
                if (sinceReport >= PROGRESS_EVERY_HOURS) {
                    sinceReport = 0
                    state = state.copy(hoursDone = done)
                    progressState.value = state
                    onProgress(state)
                }
            }
            hourStart += HOUR_MILLIS
        }
        return finish(
            BackfillProgress(hoursDone = total, hoursTotal = total, finished = true),
            onProgress,
        )
    }

    /**
     * Recomputes one hour end to end: its 60 minute rows and its sketch. An
     * hour without samples leaves no rows behind — a gap in the data stays a
     * gap in the pre-aggregation (CHART SPEC §25).
     */
    suspend fun buildHour(hourStart: Long) {
        val hourEnd = hourStart + HOUR_MILLIS - 1
        val rows = dao.rawSamples(hourStart, hourEnd)
        dao.deleteMinutes(hourStart, hourEnd)
        if (rows.isEmpty()) {
            dao.deleteHours(hourStart, hourEnd)
            return
        }
        dao.upsertMinutes(PreAggregateMath.minutes(rows))
        val sketch = PreAggregateMath.hour(hourStart, rows, sketchK)
        if (sketch == null) {
            dao.deleteHours(hourStart, hourEnd)
        } else {
            dao.upsertHours(listOf(sketch))
        }
    }

    /** True when the stored sketch of [hourStart] matches the raw table. */
    private suspend fun isHourCurrent(hourStart: Long): Boolean {
        val hourEnd = hourStart + HOUR_MILLIS - 1
        val stored = dao.hourSketches(hourStart, hourEnd).firstOrNull()
        val rawCount = dao.rawCount(hourStart, hourEnd)
        if (stored == null) return rawCount == 0
        return stored.algorithmVersion == KllSketch.ALGORITHM_VERSION &&
            stored.sketchK == sketchK &&
            stored.count == rawCount
    }

    private suspend fun rewriteMinutes(from: Long, to: Long) {
        val rows = dao.rawSamples(from, to)
        dao.deleteMinutes(from, to)
        if (rows.isNotEmpty()) dao.upsertMinutes(PreAggregateMath.minutes(rows))
    }

    private fun finish(
        state: BackfillProgress,
        onProgress: (BackfillProgress) -> Unit,
    ): BackfillProgress {
        progressState.value = state
        onProgress(state)
        return state
    }

    companion object {
        /** How often closed minutes are written while measuring. */
        const val ADVANCE_INTERVAL_MILLIS = 60_000L

        /**
         * Minutes rewritten on every tick. Three covers the device handing
         * over its buffer after a short link drop; anything older is caught by
         * the hour rebuild.
         */
        const val LIVE_TAIL_MINUTES = 3L

        /** An hour is only closed this long after its boundary (late records). */
        const val HOUR_GRACE_MILLIS = 2 * MINUTE_MILLIS

        /** Newest hours whose stored count is verified against raw on each tick. */
        const val VERIFY_HOURS = 3

        private const val PROGRESS_EVERY_HOURS = 8
    }
}
