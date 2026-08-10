package app.radiacode.analysis

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadonTrendTest {

    /** 3 keV per channel: 609 keV ≈ ch 203, 352 keV ≈ ch 117, 1024 channels. */
    private val cal = EnergyCalibration(0f, 3f, 0f)
    private val channels = 1024

    /** Flat continuum with optional Gaussian-ish peaks injected at energies. */
    private fun spectrum(
        continuumPerChannel: Int,
        peaks: Map<Float, Int> = emptyMap(),
    ): List<Int> {
        val counts = IntArray(channels) { continuumPerChannel }
        for ((keV, total) in peaks) {
            val center = cal.channelAt(keV).toInt()
            val half = 5
            val perChannel = total / (2 * half + 1)
            for (ch in (center - half)..(center + half)) counts[ch] += perChannel
        }
        return counts.toList()
    }

    // --- ROI net ---

    @Test
    fun `roiNet recovers an injected peak over a flat continuum`() {
        val injected = 1100 // 11 channels x 100
        val counts = spectrum(continuumPerChannel = 50, peaks = mapOf(RadonTrend.BI214_KEV to injected))
        val roi = RadonTrend.roiNet(counts, cal, RadonTrend.BI214_KEV)
        assertNotNull(roi)
        // Continuum subtraction should recover the peak within a few percent.
        assertTrue(
            abs(roi.netCounts - injected) < injected * 0.05f,
            "net ${roi.netCounts} vs injected $injected",
        )
        assertTrue(roi.sigmaCounts > 0f)
    }

    @Test
    fun `roiNet on pure background is near zero within sigma`() {
        val counts = spectrum(continuumPerChannel = 80)
        val roi = RadonTrend.roiNet(counts, cal, RadonTrend.PB214_KEV)!!
        assertTrue(abs(roi.netCounts) <= 3f * roi.sigmaCounts + 1e-3f)
        assertEquals(0f, roi.netCounts, 1e-3f) // deterministic flat input: exactly 0
    }

    @Test
    fun `roiNet refuses windows off the spectrum`() {
        assertNull(RadonTrend.roiNet(List(64) { 1 }, cal, RadonTrend.BI214_KEV))
    }

    // --- intervals ---

    private fun snap(
        atMinutes: Long,
        seconds: Long,
        counts: List<Int>,
    ) = RadonTrend.Snapshot(atMinutes * 60_000L, seconds, counts, cal)

    @Test
    fun `intervals diff consecutive snapshots and skip resets`() {
        val base = spectrum(10)
        val grown = spectrum(20, peaks = mapOf(RadonTrend.BI214_KEV to 550))
        val afterReset = spectrum(1)
        val points = RadonTrend.intervals(
            listOf(
                snap(0, 600, base),
                snap(60, 1200, grown), // valid pair: +600 s
                snap(120, 30, afterReset), // reset: duration fell → skipped
                snap(180, 630, spectrum(3)), // valid vs afterReset: +600 s
            ),
        )
        assertEquals(2, points.size)
        assertEquals(600L, points[0].deltaSeconds)
        assertTrue(points[0].bi214Cps > 0.5f) // 550 counts / 600 s ≈ 0.9 cps
        assertEquals(180L * 60_000L, points[1].endMillis)
    }

    @Test
    fun `intervals skip channel-grid changes`() {
        val points = RadonTrend.intervals(
            listOf(
                snap(0, 600, spectrum(10)),
                RadonTrend.Snapshot(60 * 60_000L, 1200, List(256) { 20 }, cal),
            ),
        )
        assertTrue(points.isEmpty())
    }

    // --- hourly + median ---

    private fun point(hour: Long, rate: Float, seconds: Long = 600) = RadonTrend.IntervalPoint(
        endMillis = hour * RadonTrend.HOUR_MILLIS + 30 * 60_000L,
        deltaSeconds = seconds,
        bi214Cps = rate / 2,
        pb214Cps = rate / 2,
        indexCps = rate,
        sigmaCps = 0.05f,
    )

    @Test
    fun `hourly buckets weight by measured seconds`() {
        val hours = RadonTrend.hourly(
            listOf(
                point(10, rate = 1f, seconds = 300),
                point(10, rate = 3f, seconds = 900), // same hour, 3× the weight
                point(11, rate = 2f),
            ),
        )
        assertEquals(2, hours.size)
        // (1·300 + 3·900) / 1200 = 2.5
        assertEquals(2.5f, hours[0].rateCps, 1e-4f)
        assertEquals(1200L, hours[0].seconds)
        assertEquals(2f, hours[1].rateCps, 1e-4f)
    }

    @Test
    fun `median needs enough hours`() {
        val two = listOf(point(1, 1f), point(2, 5f))
        assertNull(RadonTrend.medianRate(RadonTrend.hourly(two)))
        val hours = RadonTrend.hourly(listOf(point(1, 1f), point(2, 5f), point(3, 2f)))
        assertEquals(2f, RadonTrend.medianRate(hours)!!, 1e-4f)
    }

    // --- trend ---

    @Test
    fun `rising and falling trends are detected, flat background stays flat`() {
        val rising = RadonTrend.hourly((0L..7L).map { point(it, 0.5f + 0.2f * it) })
        assertEquals(RadonTrend.Trend.RISING, RadonTrend.trend(rising))

        val falling = RadonTrend.hourly((0L..7L).map { point(it, 2.0f - 0.2f * it) })
        assertEquals(RadonTrend.Trend.FALLING, RadonTrend.trend(falling))

        // Pure background with tiny jitter well inside σ (0.05): flat.
        val jitter = listOf(0.50f, 0.51f, 0.49f, 0.50f, 0.52f, 0.49f, 0.50f, 0.51f)
        val flat = RadonTrend.hourly(jitter.mapIndexed { i, r -> point(i.toLong(), r) })
        assertEquals(RadonTrend.Trend.FLAT, RadonTrend.trend(flat))

        assertEquals(RadonTrend.Trend.UNKNOWN, RadonTrend.trend(RadonTrend.hourly(emptyList())))
    }

    // --- hourly thinning ---

    @Test
    fun `selectHourlyIds keeps the last snapshot of each hour in order`() {
        val h = RadonTrend.HOUR_MILLIS
        val ids = RadonTrend.selectHourlyIds(
            listOf(
                RadonTrend.Meta(1, 10 * h + 60_000L, 60),
                RadonTrend.Meta(2, 10 * h + 50 * 60_000L, 3000), // last of hour 10
                RadonTrend.Meta(3, 11 * h + 5 * 60_000L, 3300), // last of hour 11
                RadonTrend.Meta(4, 12 * h, 3600),
                RadonTrend.Meta(5, 12 * h + 30 * 60_000L, 5400), // last of hour 12
            ),
        )
        assertEquals(listOf(2L, 3L, 5L), ids)
    }
}
