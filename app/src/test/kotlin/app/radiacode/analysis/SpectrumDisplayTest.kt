package app.radiacode.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Typical RadiaCode-110 calibration: ~2.9 keV/channel, slight quadratic term. */
private val CALIBRATION = EnergyCalibration(a0 = -5f, a1 = 2.85f, a2 = 0.0004f)

class SpectrumDisplayTest {

    // --- calibration ---

    @Test
    fun `channelAt inverts energyAt across the range`() {
        for (channel in listOf(0, 100, 232, 511, 1023)) {
            val energy = CALIBRATION.energyAt(channel.toFloat())
            if (energy < 0f) continue // below-zero energies clamp to channel 0
            assertEquals(channel.toFloat(), CALIBRATION.channelAt(energy), 0.01f)
        }
    }

    @Test
    fun `channelAt falls back to linear inversion when a2 is zero`() {
        val linear = EnergyCalibration(a0 = 0f, a1 = 3f, a2 = 0f)
        assertEquals(220f, linear.channelAt(660f), 0.001f)
    }

    // --- aggregation ---

    @Test
    fun `aggregateMax keeps the peak of each bucket`() {
        val values = List(100) { if (it == 37) 500f else 1f }
        val columns = SpectrumDisplay.aggregateMax(values, 0..99, 10)
        assertEquals(10, columns.size)
        assertEquals(500f, columns[3]) // channel 37 lands in bucket 3
        assertEquals(1f, columns[0])
    }

    @Test
    fun `aggregateMax respects the channel range`() {
        val values = List(100) { if (it < 50) 100f else 1f }
        val columns = SpectrumDisplay.aggregateMax(values, 50..99, 5)
        assertTrue(columns.all { it == 1f }, "channels outside the range must not leak in")
    }

    @Test
    fun `columnForChannel matches aggregateMax bucketing`() {
        val range = 0..99
        val values = List(100) { if (it == 37) 500f else 1f }
        val columns = SpectrumDisplay.aggregateMax(values, range, 10)
        val column = SpectrumDisplay.columnForChannel(37, range, 10)
        assertEquals(500f, columns[column!!])
        assertNull(SpectrumDisplay.columnForChannel(100, range, 10))
    }

    // --- lin/log height mapping ---

    @Test
    fun `linear heights are proportional and positive values stay visible`() {
        assertEquals(50, SpectrumDisplay.columnHeightPx(50f, 100f, 100, logScale = false))
        assertEquals(1, SpectrumDisplay.columnHeightPx(0.1f, 100f, 100, logScale = false))
        assertEquals(0, SpectrumDisplay.columnHeightPx(0f, 100f, 100, logScale = false))
    }

    @Test
    fun `log heights step one decade per gridline spacing`() {
        // top = 10^4 over 100 px: each decade is 25 px.
        assertEquals(25, SpectrumDisplay.columnHeightPx(10f, 10_000f, 100, logScale = true))
        assertEquals(50, SpectrumDisplay.columnHeightPx(100f, 10_000f, 100, logScale = true))
        assertEquals(100, SpectrumDisplay.columnHeightPx(10_000f, 10_000f, 100, logScale = true))
        // 1 count sits at the baseline but is still 1 px visible.
        assertEquals(1, SpectrumDisplay.columnHeightPx(1f, 10_000f, 100, logScale = true))
    }

    @Test
    fun `logTop picks the next power of ten`() {
        assertEquals(10f, SpectrumDisplay.logTop(3f))
        assertEquals(100f, SpectrumDisplay.logTop(11f))
        assertEquals(10_000f, SpectrumDisplay.logTop(9_999f))
        assertEquals(4, SpectrumDisplay.decadeCount(10_000f))
    }

    @Test
    fun `decade labels read 1 10 100 1k 10k`() {
        assertEquals(
            listOf("1", "10", "100", "1k", "10k"),
            (0..4).map { SpectrumDisplay.decadeLabel(it) },
        )
    }

    // --- zoom windowing ---

    @Test
    fun `zoom in halves the window about its center and zoom out restores it`() {
        val full = EnergyWindow(0f, 3000f)
        val zoomed = SpectrumDisplay.zoomIn(full, full)
        assertEquals(1500f, zoomed.widthKeV, 0.01f)
        assertEquals(750f, zoomed.startKeV, 0.01f)
        val restored = SpectrumDisplay.zoomOut(zoomed, full)
        assertEquals(full.startKeV, restored.startKeV, 0.01f)
        assertEquals(full.endKeV, restored.endKeV, 0.01f)
    }

    @Test
    fun `zoom never narrows below the minimum window`() {
        val full = EnergyWindow(0f, 3000f)
        var window = full
        repeat(10) { window = SpectrumDisplay.zoomIn(window, full) }
        assertEquals(SpectrumDisplay.MIN_WINDOW_KEV, window.widthKeV, 0.01f)
    }

    @Test
    fun `pan shifts and clamps at the edges`() {
        val full = EnergyWindow(0f, 3000f)
        val window = EnergyWindow(1000f, 1500f)
        val panned = SpectrumDisplay.pan(window, full, deltaFraction = -0.5f)
        assertEquals(1250f, panned.startKeV, 0.01f)
        val clamped = SpectrumDisplay.pan(window, full, deltaFraction = 10f)
        assertEquals(0f, clamped.startKeV, 0.01f)
        assertEquals(500f, clamped.widthKeV, 0.01f)
    }

    @Test
    fun `pinch about a focus keeps the focus energy in place`() {
        val full = EnergyWindow(0f, 3000f)
        val pinched = SpectrumDisplay.pinch(full, full, scale = 2f, focusFraction = 0.25f)
        assertEquals(1500f, pinched.widthKeV, 0.01f)
        // Focus energy 750 keV must stay at fraction 0.25 of the new window.
        assertEquals(750f, pinched.startKeV + 0.25f * pinched.widthKeV, 0.5f)
    }

    @Test
    fun `channelRange covers the window and stops before the edge channel`() {
        val full = SpectrumDisplay.fullWindow(CALIBRATION, 1024)
        val range = SpectrumDisplay.channelRange(full, CALIBRATION, 1024)
        assertTrue(range.first >= 0)
        // 1023 — граница шкалы, а не точка спектра: она не рисуется и не
        // задаёт масштаб оси (SpectrumEdge).
        assertEquals(1022, range.last)
        assertTrue(full.endKeV < CALIBRATION.energyAt(1023f))
    }

    // --- smoothing (display-only) ---

    @Test
    fun `moving average smooths without touching the input`() {
        val input = listOf(0f, 0f, 10f, 0f, 0f)
        val smoothed = SpectrumDisplay.movingAverage(input, radius = 1)
        assertEquals(listOf(0f, 0f, 10f, 0f, 0f), input, "raw data must stay untouched")
        assertEquals(10f / 3, smoothed[2], 0.001f)
        assertEquals(10f / 3, smoothed[1], 0.001f)
        assertEquals(0f, smoothed[4], 0.001f)
    }

    @Test
    fun `moving average preserves a constant series`() {
        val input = List(20) { 7f }
        val smoothed = SpectrumDisplay.movingAverage(input)
        assertTrue(smoothed.all { kotlin.math.abs(it - 7f) < 1e-4 })
    }

    // --- background normalization ---

    @Test
    fun `subtraction scales the background by the time ratio`() {
        // Background: 600 s, 60 counts in a channel => 0.1 cps.
        // Current: 300 s => expect 30 background counts scaled off.
        val current = listOf(50, 10)
        val background = listOf(60, 100)
        val result = SpectrumDisplay.subtractBackground(current, 300, background, 600)
        assertEquals(50f - 30f, result[0], 0.001f)
        // Negative residual clamps to zero.
        assertEquals(0f, result[1], 0.001f)
    }

    @Test
    fun `subtraction with equal durations is a plain difference`() {
        val result = SpectrumDisplay.subtractBackground(listOf(10), 100, listOf(4), 100)
        assertEquals(6f, result[0], 0.001f)
    }

    @Test
    fun `overlay scaling matches the subtraction normalization`() {
        val scaled = SpectrumDisplay.scaleToDuration(listOf(60), backgroundSeconds = 600, currentSeconds = 300)
        assertEquals(30f, scaled[0], 0.001f)
    }

    @Test
    fun `zero background duration falls back to unscaled counts`() {
        val scaled = SpectrumDisplay.scaleToDuration(listOf(60), backgroundSeconds = 0, currentSeconds = 300)
        assertEquals(60f, scaled[0], 0.001f)
    }

    // --- energy ticks ---

    @Test
    fun `full range uses 500 keV ticks without the zero tick`() {
        val ticks = SpectrumDisplay.energyTicks(EnergyWindow(0f, 3000f))
        assertEquals(listOf(500, 1000, 1500, 2000, 2500, 3000), ticks.map { it.keV })
        assertEquals(0.5f, ticks[2].fraction, 0.001f) // 1500 keV mid-range
    }

    @Test
    fun `zoomed windows get finer ticks`() {
        val ticks = SpectrumDisplay.energyTicks(EnergyWindow(600f, 900f))
        assertEquals(listOf(600, 700, 800, 900), ticks.map { it.keV })
    }
}
