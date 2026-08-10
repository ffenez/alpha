package app.radiacode.analysis

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpectrogramTest {

    /** Linear calibration: 3 keV per channel, so channel 100 = 300 keV. */
    private val calibration = EnergyCalibration(0f, 3f, 0f)

    // --- banding ---

    @Test
    fun `band fractions are monotonic and hit the range ends`() {
        assertEquals(0f, Spectrogram.fractionOfEnergy(Spectrogram.MIN_KEV))
        assertEquals(1f, Spectrogram.fractionOfEnergy(Spectrogram.MAX_KEV)!!, 1e-5f)
        val f100 = Spectrogram.fractionOfEnergy(100f)!!
        val f600 = Spectrogram.fractionOfEnergy(600f)!!
        val f2000 = Spectrogram.fractionOfEnergy(2000f)!!
        assertTrue(f100 < f600 && f600 < f2000)
        // Geometric scale: equal energy ratios are equal fractions.
        val fA = Spectrogram.fractionOfEnergy(40f)!! - Spectrogram.fractionOfEnergy(20f)!!
        val fB = Spectrogram.fractionOfEnergy(2000f)!! - Spectrogram.fractionOfEnergy(1000f)!!
        assertEquals(fA, fB, 1e-5f)
    }

    @Test
    fun `energies outside 20-3000 keV are dropped`() {
        assertNull(Spectrogram.fractionOfEnergy(10f))
        assertNull(Spectrogram.fractionOfEnergy(3500f))
        assertNull(Spectrogram.bandOfEnergy(19.9f))
        assertEquals(Spectrogram.BAND_COUNT - 1, Spectrogram.bandOfEnergy(3000f))
        assertEquals(0, Spectrogram.bandOfEnergy(20f))
    }

    @Test
    fun `band center energy round-trips into its own band`() {
        for (band in 0 until Spectrogram.BAND_COUNT) {
            assertEquals(band, Spectrogram.bandOfEnergy(Spectrogram.bandCenterKeV(band)))
        }
    }

    @Test
    fun `bandCounts sums channels into the right bands and drops out-of-range`() {
        val counts = IntArray(1024)
        counts[100] = 7 // 300 keV
        counts[200] = 3 // 600 keV
        counts[3] = 99 // 9 keV — below threshold, dropped
        val bands = Spectrogram.bandCounts(counts, calibration)
        assertEquals(7f, bands[Spectrogram.bandOfEnergy(300f)!!])
        assertEquals(3f, bands[Spectrogram.bandOfEnergy(600f)!!])
        assertEquals(10f, bands.sum())
    }

    // --- interval derivation ---

    @Test
    fun `interval is the channel-wise difference of accumulations`() {
        val interval = Spectrogram.intervalCounts(
            currentCounts = listOf(10, 20, 30),
            currentSeconds = 65,
            previousCounts = listOf(4, 20, 15),
            previousSeconds = 60,
        )
        assertNotNull(interval)
        assertEquals(listOf(6, 0, 15), interval.toList())
    }

    @Test
    fun `small negative diffs clamp to zero`() {
        val interval = Spectrogram.intervalCounts(
            currentCounts = listOf(10, 19),
            currentSeconds = 65,
            previousCounts = listOf(4, 20),
            previousSeconds = 60,
        )
        assertEquals(listOf(6, 0), interval!!.toList())
    }

    @Test
    fun `no interval on first poll, reset, or grid change`() {
        // First poll: no previous.
        assertNull(Spectrogram.intervalCounts(listOf(1, 2), 10, null, 0))
        // Reset between polls: accumulation time did not grow.
        assertNull(Spectrogram.intervalCounts(listOf(1, 2), 5, listOf(9, 9), 60))
        assertNull(Spectrogram.intervalCounts(listOf(1, 2), 60, listOf(9, 9), 60))
        // Channel-grid change.
        assertNull(Spectrogram.intervalCounts(listOf(1, 2, 3), 65, listOf(1, 2), 60))
    }

    // --- intensity normalization ---

    @Test
    fun `intensity is 0 at zero, 1 at the column max, log-compressed between`() {
        assertEquals(0f, Spectrogram.intensity(0f, 100f))
        assertEquals(0f, Spectrogram.intensity(5f, 0f))
        assertEquals(1f, Spectrogram.intensity(100f, 100f))
        val mid = Spectrogram.intensity(10f, 100f)
        // Log scaling: 10 of 100 renders far brighter than the linear 0.1.
        assertTrue(mid > 0.4f && mid < 0.7f, "expected log compression, got $mid")
    }

    // --- mean energy ---

    @Test
    fun `mean energy is the count-weighted band center`() {
        val bands = FloatArray(Spectrogram.BAND_COUNT)
        val bandLow = Spectrogram.bandOfEnergy(100f)!!
        val bandHigh = Spectrogram.bandOfEnergy(1000f)!!
        bands[bandLow] = 3f
        bands[bandHigh] = 1f
        val mean = Spectrogram.meanEnergyKeV(bands)!!
        val expected = (3f * Spectrogram.bandCenterKeV(bandLow) +
            1f * Spectrogram.bandCenterKeV(bandHigh)) / 4f
        assertTrue(abs(mean - expected) < 0.5f)
        assertNull(Spectrogram.meanEnergyKeV(FloatArray(Spectrogram.BAND_COUNT)))
    }

    // --- ring buffer ---

    private fun slice(ts: Long, counts: Float = 1f): SpectrogramSlice {
        val bands = FloatArray(Spectrogram.BAND_COUNT)
        bands[10] = counts
        return SpectrogramSlice(ts, 5, bands, cps = null, doseMicroSvH = null)
    }

    @Test
    fun `ring drops oldest beyond capacity and keeps order`() {
        val ring = SpectrogramRing(capacity = 3)
        for (i in 1..5) ring.add(slice(i.toLong()))
        val snapshot = ring.snapshot()
        assertEquals(listOf(3L, 4L, 5L), snapshot.map { it.timestampMillis })
        assertEquals(5L, ring.latest()!!.timestampMillis)
        ring.clear()
        assertTrue(ring.snapshot().isEmpty())
    }

    // --- column aggregation for rendering ---

    @Test
    fun `aggregate merges adjacent slices conserving counts and delta-t`() {
        val slices = (1..10).map { slice(it.toLong(), counts = 2f) }
        val merged = Spectrogram.aggregate(slices, maxColumns = 4)
        assertTrue(merged.size <= 4)
        assertEquals(20f, merged.map { it.totalCounts }.sum())
        assertEquals(50L, merged.sumOf { it.intervalSeconds })
        // Timestamps stay ordered; each group is stamped with its last slice.
        assertEquals(merged.map { it.timestampMillis }.sorted(), merged.map { it.timestampMillis })
        assertEquals(10L, merged.last().timestampMillis)
    }

    @Test
    fun `aggregate is identity when it already fits`() {
        val slices = (1..5).map { slice(it.toLong()) }
        assertEquals(slices, Spectrogram.aggregate(slices, maxColumns = 10))
    }
}
