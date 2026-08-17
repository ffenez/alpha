package app.alpha.analysis

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
    fun `intensity is 0 at zero, 1 at the top of the shared scale, log between`() {
        assertEquals(0f, Spectrogram.intensity(0f, 100f))
        assertEquals(0f, Spectrogram.intensity(5f, 0f))
        assertEquals(1f, Spectrogram.intensity(100f, 100f))
        val mid = Spectrogram.intensity(10f, 100f)
        // Log scaling: 10 of 100 renders far brighter than the linear 0.1.
        assertTrue(mid > 0.4f && mid < 0.7f, "expected log compression, got $mid")
    }

    @Test
    fun `the shared scale keeps weak columns weak`() {
        // Ровно то, что ломала нормировка внутри столбца: слабая колонка
        // светилась так же, как сильная.
        val weak = column(rate = 1f)
        val strong = column(rate = 100f)
        val top = Spectrogram.scaleTop(listOf(weak, strong), listOf(10..10))
        assertTrue(
            Spectrogram.intensity(weak.rate(10), top) <
                Spectrogram.intensity(strong.rate(10), top),
        )
        // А режим «форма» их по-прежнему уравнивает — на то он и отдельный.
        assertEquals(
            Spectrogram.shapeIntensity(weak.bandCounts[10], weak.bandCounts[10]),
            Spectrogram.shapeIntensity(strong.bandCounts[10], strong.bandCounts[10]),
        )
    }

    private fun column(rate: Float): SpectrogramColumn {
        val bands = FloatArray(Spectrogram.BAND_COUNT)
        bands[10] = rate * 10f
        return SpectrogramColumn(0L, 10_000L, bands, seconds = 10L, cps = null, doseMicroSvH = null)
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

    // --- time grid ---

    /** Срез с [counts] импульсами, снятый в момент [ts] мс. */
    private fun tick(ts: Long, counts: Float): SpectrogramSlice {
        val bands = FloatArray(Spectrogram.BAND_COUNT)
        bands[10] = counts
        return SpectrogramSlice(ts, 5, bands, cps = null, doseMicroSvH = null)
    }

    @Test
    fun `the grid sums polls into time cells and conserves counts`() {
        val slices = (1..12).map { tick(it * 5_000L, counts = 2f) }
        val grid = Spectrogram.grid(slices, 0L, 60_000L, stepMillis = 15_000L)
        assertEquals(4, grid.size)
        assertEquals(24f, grid.filterNotNull().sumOf { it.totalCounts.toDouble() }.toFloat())
        assertEquals(60L, grid.filterNotNull().sumOf { it.seconds })
    }

    @Test
    fun `a gap in the stream stays an empty column instead of collapsing`() {
        // Две минуты тишины между двумя опросами: раньше ось строилась по
        // индексу столбца и пауза исчезала.
        val slices = listOf(tick(5_000L, 10f), tick(125_000L, 10f))
        val grid = Spectrogram.grid(slices, 0L, 130_000L, stepMillis = 10_000L)
        assertEquals(13, grid.size)
        assertNotNull(grid[0])
        assertNotNull(grid[12])
        assertTrue(grid.subList(1, 12).all { it == null }, "пропуск схлопнулся")
    }

    @Test
    fun `an incomplete cell is coloured by rate, not by its raw sum`() {
        val full = Spectrogram.grid(listOf(tick(5_000L, 10f), tick(10_000L, 10f)), 0L, 10_000L, 10_000L)
        val half = Spectrogram.grid(listOf(tick(5_000L, 10f)), 0L, 10_000L, 10_000L)
        // Половина измеренного времени при той же скорости даёт ту же яркость.
        assertEquals(full[0]!!.rate(10), half[0]!!.rate(10), 1e-3f)
        assertTrue(full[0]!!.totalCounts > half[0]!!.totalCounts)
    }

    // --- adaptive display step ---

    @Test
    fun `the step grows when a poll would carry only noise`() {
        // Фон ≈25 имп/с: пятисекундная колонка это ≈1 импульс на полосу.
        val background = (1..24).map { tick(it * 5_000L, counts = 125f) }
        val step = Spectrogram.displayStepSeconds(background, 120_000L, maxColumns = 200)
        assertTrue(step > 5L, "шаг остался $step с при фоне")
        assertTrue(
            125.0 / 5.0 * step / Spectrogram.BAND_COUNT >= Spectrogram.MIN_COUNTS_PER_BAND,
            "шаг $step с не набирает статистику",
        )

        // Сильный источник: событий хватает, разрешение остаётся высоким.
        val hot = (1..24).map { tick(it * 5_000L, counts = 5_000f) }
        assertEquals(
            Spectrogram.DISPLAY_STEPS_SECONDS.first(),
            Spectrogram.displayStepSeconds(hot, 120_000L, maxColumns = 200),
        )
    }

    @Test
    fun `the step never produces more columns than the picture can hold`() {
        val slices = (1..600).map { tick(it * 5_000L, counts = 5_000f) }
        val step = Spectrogram.displayStepSeconds(slices, 3_000_000L, maxColumns = 100)
        assertTrue(3_000_000L / 1000 / step <= 100, "колонок больше, чем помещается")
    }

    @Test
    fun `the step is never finer than the coarsest slice in the window`() {
        // Фоновый опрос раз в 10 минут: срез покрывает 600 с, и картинка не
        // имеет права рисовать рядом с ним пустые пятиминутные ячейки.
        val bands = FloatArray(Spectrogram.BAND_COUNT).also { it[10] = 15_000f }
        val background = (1..6).map {
            SpectrogramSlice(it * 600_000L, 600, bands.copyOf(), cps = null, doseMicroSvH = null)
        }
        val step = Spectrogram.displayStepSeconds(background, 3_600_000L, maxColumns = 240)
        assertTrue(step >= 600L, "шаг $step с мельче среза 600 с")

        val grid = Spectrogram.grid(background, 0L, 3_600_000L, step * 1000L)
        assertTrue(grid.count { it == null } <= 1, "появились ложные пропуски: $grid")
    }

    @Test
    fun `energy bands merge until they carry statistics, once for the whole window`() {
        // 96 полос по одному импульсу на колонку: случайные светлые строчки
        // читаются как спектральные линии, поэтому полосы объединяются.
        val thin = (1..10).map {
            val bands = FloatArray(Spectrogram.BAND_COUNT) { 1f }
            SpectrogramColumn(0L, 5_000L, bands, seconds = 5L, cps = null, doseMicroSvH = null)
        }
        val groups = Spectrogram.bandGroups(thin)
        assertTrue(groups.size < Spectrogram.BAND_COUNT, "полосы не объединились: ${groups.size}")
        // Нарезка покрывает весь диапазон без дыр и пересечений.
        assertEquals(0, groups.first().first)
        assertEquals(Spectrogram.BAND_COUNT - 1, groups.last().last)
        for ((a, b) in groups.zipWithNext()) assertEquals(a.last + 1, b.first)

        // Статистики вдоволь — дробить дальше незачем, полосы остаются свои.
        val rich = (1..10).map {
            val bands = FloatArray(Spectrogram.BAND_COUNT) { 100f }
            SpectrogramColumn(0L, 5_000L, bands, seconds = 5L, cps = null, doseMicroSvH = null)
        }
        assertEquals(Spectrogram.BAND_COUNT, Spectrogram.bandGroups(rich).size)
    }

    @Test
    fun `a wide group is not brighter than a narrow one at the same spectrum`() {
        val bands = FloatArray(Spectrogram.BAND_COUNT) { 10f }
        val column = SpectrogramColumn(
            0L, 10_000L, bands, seconds = 10L, cps = null, doseMicroSvH = null,
        )
        // Средняя скорость на полосу, а не сумма: иначе широкая группа
        // светилась бы ярче при том же спектре.
        assertEquals(column.groupRate(0..0), column.groupRate(0..7), 1e-6f)
    }
}
