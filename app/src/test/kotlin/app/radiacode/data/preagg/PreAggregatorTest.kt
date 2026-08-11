package app.radiacode.data.preagg

import app.radiacode.analysis.quantiles.KllSketch
import app.radiacode.data.db.DosePoint
import app.radiacode.data.db.HourSketchEntity
import app.radiacode.data.db.MinuteRollup
import app.radiacode.data.db.MinuteStatEntity
import app.radiacode.data.db.PreAggregateDao
import app.radiacode.data.db.RawSampleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-memory stand-in for the derived tables (ADR 004). */
internal class FakePreAggregateDao(
    val samples: MutableList<RawSampleRow> = mutableListOf(),
) : PreAggregateDao {

    val minuteRows = sortedMapOf<Long, MinuteStatEntity>()
    val hourRows = sortedMapOf<Long, HourSketchEntity>()

    /** How many times raw samples were scanned — the cost the backfill pays. */
    var rawScans = 0

    override suspend fun rawSamples(from: Long, to: Long): List<RawSampleRow> {
        rawScans++
        return samples.filter { it.timestamp in from..to }.sortedBy { it.timestamp }
    }

    override suspend fun rawDosePage(afterTimestamp: Long, to: Long, limit: Int): List<DosePoint> =
        samples.filter { it.timestamp > afterTimestamp && it.timestamp <= to }
            .sortedBy { it.timestamp }
            .take(limit)
            .map { DosePoint(it.timestamp, it.doseRate) }

    override suspend fun rawCount(from: Long, to: Long): Int =
        samples.count { it.timestamp in from..to }

    override suspend fun earliestSampleTime(): Long? = samples.minOfOrNull { it.timestamp }

    override suspend fun latestSampleTime(): Long? = samples.maxOfOrNull { it.timestamp }

    override suspend fun upsertMinutes(rows: List<MinuteStatEntity>) {
        rows.forEach { minuteRows[it.minuteStart] = it }
    }

    override suspend fun deleteMinutes(from: Long, to: Long) {
        minuteRows.keys.filter { it in from..to }.toList().forEach { minuteRows.remove(it) }
    }

    override suspend fun minutes(from: Long, to: Long): List<MinuteStatEntity> =
        minuteRows.values.filter { it.minuteStart in from..to }

    override suspend fun minuteRollup(from: Long, to: Long): MinuteRollup {
        val rows = minutes(from, to)
        if (rows.isEmpty()) return MinuteRollup(0, null, null, null, null, null, null, null, null)
        return MinuteRollup(
            minutes = rows.size,
            sampleCount = rows.sumOf { it.count },
            admittedCount = rows.sumOf { it.admittedCount },
            sumDoseRate = rows.sumOf { it.sumDoseRate },
            sumSqDoseRate = rows.sumOf { it.sumSqDoseRate },
            minDoseRate = rows.minOf { it.minDoseRate },
            maxDoseRate = rows.maxOf { it.maxDoseRate },
            firstSampleTime = rows.minOf { it.firstSampleTime },
            lastSampleTime = rows.maxOf { it.lastSampleTime },
        )
    }

    override suspend fun minuteCount(): Int = minuteRows.size

    override suspend fun upsertHours(rows: List<HourSketchEntity>) {
        rows.forEach { hourRows[it.hourStart] = it }
    }

    override suspend fun hourSketches(from: Long, to: Long): List<HourSketchEntity> =
        hourRows.values.filter { it.hourStart in from..to }

    override suspend fun builtHourStarts(
        from: Long,
        to: Long,
        algorithmVersion: Int,
        sketchK: Int,
    ): List<Long> = hourRows.values
        .filter {
            it.hourStart in from..to &&
                it.algorithmVersion == algorithmVersion &&
                it.sketchK == sketchK
        }
        .map { it.hourStart }

    override suspend fun hourCount(from: Long, to: Long): Int =
        hourRows.keys.count { it in from..to }

    override suspend fun deleteHours(from: Long, to: Long) {
        hourRows.keys.filter { it in from..to }.toList().forEach { hourRows.remove(it) }
    }
}

private const val HOUR = 3_600_000L
private const val MINUTE = 60_000L

/** Epoch-aligned start of an arbitrary day, so hour/minute maths is readable. */
private const val DAY0 = 1_754_000_000_000L / HOUR * HOUR

private fun sample(
    timestamp: Long,
    doseRate: Float,
    admitted: Boolean = true,
    profileId: Long? = 1L,
) = RawSampleRow(timestamp, doseRate, if (admitted) 1 else 0, profileId)

/** One hour of 1 Hz samples starting at [start]. */
private fun hourOfSamples(start: Long, value: (Int) -> Float = { 0.1f }): List<RawSampleRow> =
    (0 until 3600).map { sample(start + it * 1000L, value(it)) }

class PreAggregateMathTest {

    @Test
    fun `minute scalars are exact and extremum timestamps are instants`() {
        val start = DAY0
        val rows = listOf(
            sample(start, 0.10f),
            sample(start + 1000, 0.30f),
            sample(start + 2000, 0.05f),
            sample(start + 3000, 0.20f),
        )
        val minute = PreAggregateMath.minutes(rows).single()
        assertEquals(start, minute.minuteStart)
        assertEquals(4, minute.count)
        assertEquals(0.05f, minute.minDoseRate)
        assertEquals(0.30f, minute.maxDoseRate)
        assertEquals(start + 2000, minute.minAtMillis)
        assertEquals(start + 1000, minute.maxAtMillis)
        assertEquals(start, minute.firstSampleTime)
        assertEquals(start + 3000, minute.lastSampleTime)
        assertEquals(0.65, minute.sumDoseRate, 1e-6)
        assertEquals(4, minute.admittedCount)
        assertEquals(1L, minute.profileId)
    }

    @Test
    fun `sum of squares gives the exact population SD of the raw samples`() {
        val values = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val rows = values.mapIndexed { i, v -> sample(DAY0 + i * 1000L, v) }
        val minute = PreAggregateMath.minutes(rows).single()
        val mean = minute.sumDoseRate / minute.count
        val variance = minute.sumSqDoseRate / minute.count - mean * mean
        val exactMean = values.average()
        val exactVariance = values.map { (it - exactMean) * (it - exactMean) }.average()
        assertEquals(exactVariance, variance, 1e-9)
    }

    @Test
    fun `excluded samples still count as measurements but not as admitted`() {
        val rows = listOf(
            sample(DAY0, 0.1f, admitted = true),
            sample(DAY0 + 1000, 0.9f, admitted = false),
        )
        val minute = PreAggregateMath.minutes(rows).single()
        assertEquals(2, minute.count)
        assertEquals(1, minute.admittedCount)
        assertEquals(0.9f, minute.maxDoseRate, "raw data are never dropped")
    }

    @Test
    fun `a minute that spans a profile switch belongs to nobody`() {
        val rows = listOf(
            sample(DAY0, 0.1f, profileId = 1L),
            sample(DAY0 + 1000, 0.1f, profileId = 2L),
        )
        assertNull(PreAggregateMath.minutes(rows).single().profileId)
    }

    @Test
    fun `minutes are grouped on the absolute grid and sorted`() {
        val rows = listOf(
            sample(DAY0 + 2 * MINUTE + 500, 0.2f),
            sample(DAY0 + 100, 0.1f),
            sample(DAY0 + MINUTE + 100, 0.3f),
        )
        val minutes = PreAggregateMath.minutes(rows)
        assertEquals(listOf(DAY0, DAY0 + MINUTE, DAY0 + 2 * MINUTE), minutes.map { it.minuteStart })
    }

    @Test
    fun `hour sketch keeps exact count and extremes with their instants`() {
        val rows = hourOfSamples(DAY0) { 0.1f + (it % 60) * 0.001f }.toMutableList()
        rows[1234] = sample(DAY0 + 1234 * 1000L, 5.0f)
        val hour = assertNotNull(PreAggregateMath.hour(DAY0, rows))
        assertEquals(3600, hour.count)
        assertEquals(5.0f, hour.maxDoseRate)
        assertEquals(DAY0 + 1234 * 1000L, hour.maxAtMillis)
        assertEquals(KllSketch.ALGORITHM_VERSION, hour.algorithmVersion)
        assertEquals(KllSketch.DEFAULT_K, hour.sketchK)
        val restored = assertNotNull(KllSketch.fromByteArray(hour.sketch))
        assertEquals(3600L, restored.count)
        assertEquals(5.0f, restored.max)
    }

    @Test
    fun `rebuilding an hour produces byte-identical bytes`() {
        val rows = hourOfSamples(DAY0) { 0.1f + (it % 37) * 0.002f }
        val first = assertNotNull(PreAggregateMath.hour(DAY0, rows))
        val again = assertNotNull(PreAggregateMath.hour(DAY0, rows.shuffled()))
        assertTrue(first.sketch.contentEquals(again.sketch), "order of arrival must not matter")
    }

    @Test
    fun `an empty hour has no sketch`() {
        assertNull(PreAggregateMath.hour(DAY0, emptyList()))
    }
}

class PreAggregatorTest {

    private fun aggregator(dao: PreAggregateDao) = PreAggregator(dao) { DAY0 }

    @Test
    fun `backfill builds every complete hour once`() = runTest {
        val dao = FakePreAggregateDao()
        dao.samples += hourOfSamples(DAY0)
        dao.samples += hourOfSamples(DAY0 + HOUR)
        val now = DAY0 + 2 * HOUR + 10 * MINUTE
        val progress = PreAggregator(dao).backfill(now)

        assertEquals(2, dao.hourRows.size)
        assertEquals(120, dao.minuteRows.size)
        assertTrue(progress.finished)
        assertEquals(2, progress.hoursTotal)
    }

    @Test
    fun `backfill is idempotent and resumable`() = runTest {
        val dao = FakePreAggregateDao()
        repeat(3) { dao.samples += hourOfSamples(DAY0 + it * HOUR) }
        val now = DAY0 + 3 * HOUR + 10 * MINUTE
        PreAggregator(dao).backfill(now)
        val bytesBefore = dao.hourRows.mapValues { it.value.sketch.toList() }
        val scansAfterFirst = dao.rawScans

        PreAggregator(dao).backfill(now)
        assertEquals(scansAfterFirst, dao.rawScans, "a finished hour is never rescanned")
        assertEquals(bytesBefore, dao.hourRows.mapValues { it.value.sketch.toList() })

        // Resume: one hour lost its sketch (crash between the writes).
        dao.hourRows.remove(DAY0 + HOUR)
        PreAggregator(dao).backfill(now)
        assertEquals(scansAfterFirst + 1, dao.rawScans, "only the missing hour is rebuilt")
        assertEquals(bytesBefore, dao.hourRows.mapValues { it.value.sketch.toList() })
    }

    @Test
    fun `backfill rebuilds sketches of another algorithm version`() = runTest {
        val dao = FakePreAggregateDao()
        dao.samples += hourOfSamples(DAY0)
        val now = DAY0 + 2 * HOUR
        PreAggregator(dao).backfill(now)
        val stale = dao.hourRows.getValue(DAY0).copy(algorithmVersion = 0, sketch = ByteArray(4))
        dao.hourRows[DAY0] = stale

        PreAggregator(dao).backfill(now)
        assertEquals(KllSketch.ALGORITHM_VERSION, dao.hourRows.getValue(DAY0).algorithmVersion)
        assertTrue(dao.hourRows.getValue(DAY0).sketch.size > 4)
    }

    @Test
    fun `backfill can be cancelled and keeps what it finished`() = runTest {
        val dao = FakePreAggregateDao()
        repeat(24) { dao.samples += hourOfSamples(DAY0 + it * HOUR) }
        val now = DAY0 + 25 * HOUR
        val job = Job()
        val outcome = runCatching {
            withContext(job) {
                PreAggregator(dao).backfill(now) { progress ->
                    if (progress.hoursDone >= 8) job.cancel()
                }
            }
        }
        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertTrue(dao.hourRows.size in 1..23, "partial work is kept: ${dao.hourRows.size}")

        // A later run finishes the job.
        PreAggregator(dao).backfill(now)
        assertEquals(24, dao.hourRows.size)
    }

    @Test
    fun `advance closes passed minutes without double counting on restart`() = runTest {
        val dao = FakePreAggregateDao()
        val minuteStart = DAY0 + 10 * MINUTE
        // Half a minute measured, then the process is killed and restarted.
        dao.samples += (0 until 30).map { sample(minuteStart + it * 1000L, 0.1f) }
        aggregator(dao).advance(minuteStart + MINUTE + 5_000)
        assertEquals(30, dao.minuteRows.getValue(minuteStart).count)

        // The rest of the same minute arrives late (device buffer) — the row is
        // recomputed from raw, not incremented.
        dao.samples += (30 until 60).map { sample(minuteStart + it * 1000L, 0.1f) }
        aggregator(dao).advance(minuteStart + MINUTE + 10_000)
        assertEquals(60, dao.minuteRows.getValue(minuteStart).count)

        aggregator(dao).advance(minuteStart + MINUTE + 20_000)
        assertEquals(60, dao.minuteRows.getValue(minuteStart).count)
        assertEquals(1, dao.minuteRows.size)
    }

    @Test
    fun `advance closes a complete hour and rebuilds it when raw changes`() = runTest {
        val dao = FakePreAggregateDao()
        dao.samples += hourOfSamples(DAY0)
        val now = DAY0 + HOUR + 5 * MINUTE
        PreAggregator(dao).advance(now)
        assertEquals(3600, dao.hourRows.getValue(DAY0).count)

        // Late records for that hour: the stored count disagrees, so the hour
        // is rebuilt instead of staying stale.
        dao.samples += sample(DAY0 + 3599_500, 9.0f)
        PreAggregator(dao).advance(now)
        assertEquals(3601, dao.hourRows.getValue(DAY0).count)
        assertEquals(9.0f, dao.hourRows.getValue(DAY0).maxDoseRate)
    }

    @Test
    fun `an hour is not closed before its grace period`() = runTest {
        val dao = FakePreAggregateDao()
        dao.samples += hourOfSamples(DAY0)
        // One minute past the boundary: still inside the grace window.
        PreAggregator(dao).advance(DAY0 + HOUR + MINUTE)
        assertTrue(dao.hourRows.isEmpty())

        PreAggregator(dao).advance(DAY0 + HOUR + 3 * MINUTE)
        assertEquals(1, dao.hourRows.size)
    }

    @Test
    fun `an hour whose samples disappeared loses its rows`() = runTest {
        val dao = FakePreAggregateDao()
        dao.samples += hourOfSamples(DAY0)
        val now = DAY0 + 2 * HOUR
        PreAggregator(dao).backfill(now)
        assertEquals(60, dao.minuteRows.size)

        dao.samples.clear()
        PreAggregator(dao).buildHour(DAY0)
        assertTrue(dao.minuteRows.isEmpty())
        assertTrue(dao.hourRows.isEmpty(), "a pruned hour must not keep claiming measurements")
    }

    @Test
    fun `backfill on an empty database finishes immediately`() = runTest {
        val dao = FakePreAggregateDao()
        val progress = PreAggregator(dao).backfill(DAY0)
        assertTrue(progress.finished)
        assertEquals(1f, progress.fraction)
        assertTrue(dao.hourRows.isEmpty())
    }
}
