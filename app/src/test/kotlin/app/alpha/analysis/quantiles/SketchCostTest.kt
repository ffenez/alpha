package app.alpha.analysis.quantiles

import app.alpha.data.db.RawSampleRow
import app.alpha.data.preagg.PreAggregateMath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The numbers ADR 004 documents, measured instead of quoted: storage per day
 * and per month, merge cost of a 30-day window, and the rank error the merge
 * actually shows (CHART SPEC §34, §37G).
 *
 * These are assertions with headroom, not benchmarks: they exist so a change
 * that quietly triples the size of a sketch or the cost of a long window fails
 * a test instead of a phone.
 */
class SketchCostTest {

    private class Lcg(private var state: Long = 555L) {
        fun nextUnit(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            return (state ushr 11).toDouble() / (1L shl 53).toDouble()
        }
    }

    private fun hourOfSamples(hourStart: Long, seed: Long): List<RawSampleRow> {
        val rng = Lcg(seed)
        return (0 until 3600).map {
            RawSampleRow(
                timestamp = hourStart + it * 1000L,
                doseRate = (0.10 + rng.nextUnit() * 0.04).toFloat(),
                admitted = 1,
                profileId = 1L,
            )
        }
    }

    @Test
    fun `storage per day and per month stays inside the ADR 004 budget`() {
        val hour = assertNotNull(PreAggregateMath.hour(0, hourOfSamples(0, seed = 1L)))
        val minutes = PreAggregateMath.minutes(hourOfSamples(0, seed = 1L))

        // One stored hour: the blob plus its scalars.
        val hourBytes = hour.sketch.size + HOUR_SCALAR_BYTES
        // One stored minute: twelve scalars.
        val minuteBytes = MINUTE_SCALAR_BYTES

        val perDay = 24L * hourBytes + 1440L * minuteBytes
        val perMonth = 30 * perDay

        println("sketch blob: ${hour.sketch.size} B, hour row ≈ $hourBytes B")
        println("pre-aggregation ≈ ${perDay / 1024} KiB/day, ${perMonth / 1024} KiB/month")
        assertTrue(minutes.size == 60)
        assertTrue(hour.sketch.size <= 2_048, "hourly blob ${hour.sketch.size} B")
        assertTrue(perDay <= 250 * 1024, "per day $perDay B")
        assertTrue(perMonth <= 7 * 1024 * 1024, "per month $perMonth B")
        // The raw samples of the same day are far bigger — the pre-aggregation
        // is a few percent on top, not a second copy of the data.
        val rawPerDay = 86_400L * RAW_ROW_BYTES
        assertTrue(perDay * 20 < rawPerDay, "pre-aggregation must stay a small fraction of raw")
    }

    @Test
    fun `merging a 30-day window is cheap and stays accurate`() {
        val hours = 720
        val sketches = ArrayList<KllSketch>(hours)
        val all = FloatArray(hours * 3600)
        var at = 0
        for (h in 0 until hours) {
            val rows = hourOfSamples(h * 3_600_000L, seed = 100L + h)
            val values = FloatArray(rows.size)
            rows.forEachIndexed { i, row -> values[i] = row.doseRate }
            values.copyInto(all, at)
            at += values.size
            sketches += KllSketch.of(values)
        }
        val started = System.nanoTime()
        val merged = assertNotNull(KllSketch.mergeAll(sketches))
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000.0
        println("merged $hours hourly sketches in $elapsedMillis ms, ${merged.storedItems} items kept")

        val comparison = QuantileDiagnostics.compare(all, merged)
        println("30-day merge: max rank error ${comparison.maxRankError}")
        assertTrue(comparison.countsAgree)
        assertTrue(comparison.maxRankError <= 0.02, "measured ${comparison.maxRankError}")
        assertTrue(abs(merged.min - all.min()) < 1e-9f)
        // A day level would only pay off if merging were slow; it is not, so
        // ADR 004 stays at two levels. The bound keeps that decision honest.
        assertTrue(elapsedMillis < 2_000, "merge took $elapsedMillis ms")
    }

    companion object {
        /** minute_stats row: 6×8 B + 4×4 B (+ nullable profile), rounded up. */
        private const val MINUTE_SCALAR_BYTES = 100

        /** hour_sketches row without the blob. */
        private const val HOUR_SCALAR_BYTES = 60

        /** One `samples` row: 7 numeric columns plus row overhead. */
        private const val RAW_ROW_BYTES = 60
    }
}
