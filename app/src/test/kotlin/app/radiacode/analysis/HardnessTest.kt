package app.radiacode.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Жёсткость is a **defined index**, not a measured quantity — so what these
 * tests pin is that it behaves like a share of counts and that it refuses to
 * answer when the counts cannot support an answer.
 */
class HardnessTest {

    /** 1 keV per channel from zero: channel index == keV, easy to reason about. */
    private val calibration = EnergyCalibration(a0 = 0f, a1 = 1f, a2 = 0f)

    /** Counts spread evenly over [fromKeV, toKeV). */
    private fun band(fromKeV: Int, toKeV: Int, perChannel: Int, size: Int = 1600): List<Int> =
        List(size) { channel -> if (channel in fromKeV until toKeV) perChannel else 0 }

    private fun plus(a: List<Int>, b: List<Int>): List<Int> = a.mapIndexed { i, v -> v + b[i] }

    @Test
    fun `a purely soft spectrum is not hard, a purely hard one is`() {
        val soft = assertNotNull(Hardness.of(band(100, 300, 5), calibration))
        assertEquals(0.0, soft.fraction, 1e-9)

        val hard = assertNotNull(Hardness.of(band(300, 1500, 5), calibration))
        assertEquals(1.0, hard.fraction, 1e-9)
    }

    @Test
    fun `the fraction is the share of counts above the split`() {
        // 200 channels × 3 below the split, 600 × 1 above: 600 soft, 600 hard.
        val counts = plus(band(100, 300, 3), band(300, 900, 1))
        val value = assertNotNull(Hardness.of(counts, calibration))
        assertEquals(0.5, value.fraction, 1e-9)
        assertEquals(1_200.0, value.bandCounts, 1e-9)
        assertEquals(600.0, value.hardCounts, 1e-9)
        assertEquals(50.0, value.percent, 1e-9)
    }

    @Test
    fun `counts outside the analysis band are not counted at all`() {
        val withNoise = plus(
            plus(band(100, 300, 3), band(300, 900, 1)),
            plus(band(0, 100, 50), band(1500, 1600, 50)),
        )
        val value = assertNotNull(Hardness.of(withNoise, calibration))
        assertEquals(0.5, value.fraction, 1e-9)
        assertEquals(1_200.0, value.bandCounts, 1e-9)
    }

    @Test
    fun `exposure does not move the fraction, only its uncertainty`() {
        val once = assertNotNull(Hardness.of(plus(band(100, 300, 3), band(300, 900, 1)), calibration))
        val fourfold =
            assertNotNull(Hardness.of(plus(band(100, 300, 12), band(300, 900, 4)), calibration))

        assertEquals(once.fraction, fourfold.fraction, 1e-9)
        // σ ∝ 1/√N: four times the counts, half the σ.
        assertEquals(once.sigma / 2.0, fourfold.sigma, 1e-9)
    }

    @Test
    fun `a thin spectrum gets no number instead of a noisy one`() {
        assertNull(Hardness.of(band(100, 900, 0), calibration))
        val thin = band(100, 300, 1).let { plus(it, band(300, 400, 1)) } // 300 counts…
        assertNotNull(Hardness.of(thin, calibration))
        val thinner = band(100, 200, 1) // 100 counts — below the floor
        assertNull(Hardness.of(thinner, calibration))
    }

    @Test
    fun `an interval is the difference, and a reset produces nothing`() {
        val earlier = plus(band(100, 300, 3), band(300, 900, 1))
        val later = plus(earlier, plus(band(100, 300, 1), band(300, 900, 3)))

        val interval = assertNotNull(Hardness.ofInterval(earlier, later, calibration))
        // The *added* counts: 200 soft, 1800 hard.
        assertEquals(1_800.0 / 2_000.0, interval.fraction, 1e-9)

        // A spectrum that got smaller means the accumulation was reset.
        assertNull(Hardness.ofInterval(later, earlier, calibration))
    }

    @Test
    fun `hourly points pool the counts instead of averaging the fractions`() {
        val soft = RadonTrend.Snapshot(
            timestampMillis = RadonTrend.HOUR_MILLIS,
            durationSeconds = 100,
            counts = band(100, 300, 10),
            calibration = calibration,
        )
        // A long, mostly hard interval and a very short soft one in the same
        // hour: the pooled fraction must follow the counts, not the count of
        // intervals.
        val plusHard = RadonTrend.Snapshot(
            timestampMillis = RadonTrend.HOUR_MILLIS + 60_000,
            durationSeconds = 700,
            counts = plus(band(100, 300, 10), band(300, 1500, 100)),
            calibration = calibration,
        )
        val plusSoft = RadonTrend.Snapshot(
            timestampMillis = RadonTrend.HOUR_MILLIS + 120_000,
            durationSeconds = 710,
            counts = plus(plusHard.counts, band(100, 300, 2)),
            calibration = calibration,
        )

        val hours = Hardness.hourly(Hardness.intervals(listOf(soft, plusHard, plusSoft)))
        val hour = hours.single()
        assertEquals(RadonTrend.HOUR_MILLIS, hour.hourStartMillis)
        // 120 000 hard against 400 soft: the short soft interval barely moves it.
        assertTrue(hour.fraction > 0.99, "${hour.fraction}")
        assertTrue(hour.bandCounts > 120_000, "${hour.bandCounts}")
    }

    @Test
    fun `a recalibration between snapshots is not an interval`() {
        val first = RadonTrend.Snapshot(
            timestampMillis = 0,
            durationSeconds = 100,
            counts = band(100, 900, 5),
            calibration = calibration,
        )
        val recalibrated = first.copy(
            timestampMillis = 60_000,
            durationSeconds = 200,
            calibration = EnergyCalibration(a0 = 1f, a1 = 1f, a2 = 0f),
        )
        assertTrue(Hardness.intervals(listOf(first, recalibrated)).isEmpty())
    }

    @Test
    fun `the required sentence travels with the number`() {
        assertTrue(Hardness.EXPLANATION.contains("не мера опасности"), Hardness.EXPLANATION)
        assertTrue(
            !Hardness.EXPLANATION.lowercase().contains("доза"),
            "жёсткость has nothing to do with dose",
        )
    }
}
