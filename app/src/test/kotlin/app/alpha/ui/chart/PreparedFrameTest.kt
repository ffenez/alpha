package app.alpha.ui.chart

import app.alpha.ui.logic.ChartBucket
import app.alpha.ui.logic.DoseScale
import app.alpha.ui.logic.DoseScales
import app.alpha.ui.logic.LinearDoseScale
import app.alpha.ui.logic.LogDoseScale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChartProjectionTest {

    private fun bucket(
        start: Long,
        median: Float,
        min: Float = median,
        max: Float = median,
        spread: Float = 0f,
        widthMillis: Long = 1_000,
    ) = ChartBucket(
        startMillis = start,
        endMillis = start + widthMillis,
        min = min,
        max = max,
        median = median,
        q10 = median - spread,
        q25 = median - spread / 2f,
        q75 = median + spread / 2f,
        q90 = median + spread,
        sampleCount = 1,
    )

    private val scale = LinearDoseScale(1f)

    @Test
    fun `columns are placed by wall-clock time across the plot width`() {
        // Midpoints at 500, 5500, 10500 inside a 0..11000 window.
        val buckets = listOf(bucket(0, 0.5f), bucket(5_000, 0.5f), bucket(10_000, 0.5f))
        val p = ChartProjection.project(buckets, 0L, 11_000L, scale, 0f, 110f, 0f, 100f)
        assertEquals(3, p.count)
        assertEquals(5f, p.x[0], 1e-3f)
        assertEquals(55f, p.x[1], 1e-3f)
        assertEquals(105f, p.x[2], 1e-3f)
    }

    @Test
    fun `value zero sits on the bottom row and the top of the scale on the top row`() {
        val buckets = listOf(bucket(0, 0f), bucket(1_000, 1f))
        val p = ChartProjection.project(buckets, 0L, 2_000L, scale, 0f, 100f, 10f, 100f)
        assertEquals(110f, p.medianY[0], 1e-3f) // top + height
        assertEquals(10f, p.medianY[1], 1e-3f) // top
    }

    @Test
    fun `the two quantile envelopes nest around the median line`() {
        val buckets = listOf(bucket(0, 0.5f, min = 0.2f, max = 0.8f, spread = 0.1f))
        val p = ChartProjection.project(buckets, 0L, 1_000L, scale, 0f, 100f, 0f, 100f)
        // Screen y grows downwards: Q90 sits above Q10, Q75 above Q25.
        assertTrue(p.q90Y[0] < p.q10Y[0])
        assertTrue(p.q75Y[0] < p.q25Y[0])
        // Q25–Q75 is inside Q10–Q90, and the median inside both.
        assertTrue(p.q75Y[0] > p.q90Y[0])
        assertTrue(p.q25Y[0] < p.q10Y[0])
        assertTrue(p.medianY[0] in p.q75Y[0]..p.q25Y[0])
    }

    @Test
    fun `a column keeps its index so extremum markers land on it`() {
        val buckets = listOf(bucket(0, 0.5f), bucket(5_000, 0.5f), bucket(10_000, 0.5f))
        val p = ChartProjection.project(buckets, 4_000L, 11_000L, scale, 0f, 100f, 0f, 100f)
        assertEquals(2, p.count)
        assertEquals(0, p.indexOfBucket(1))
        assertEquals(1, p.indexOfBucket(2))
        assertEquals(null, p.indexOfBucket(0))
        assertEquals(null, PreparedFrame.EMPTY.indexOfBucket(0))
    }

    @Test
    fun `columns outside the window are not projected`() {
        val buckets = listOf(bucket(0, 0.5f), bucket(50_000, 0.5f), bucket(90_000, 0.5f))
        val p = ChartProjection.project(buckets, 40_000L, 60_000L, scale, 0f, 100f, 0f, 100f)
        assertEquals(1, p.count)
        assertEquals(1, p.source[0])
    }

    @Test
    fun `a log scale marks unplottable columns instead of pinning them to zero`() {
        val buckets = listOf(bucket(0, 0f), bucket(1_000, 0.1f))
        val p = ChartProjection.project(
            buckets,
            0L,
            2_000L,
            LogDoseScale(0.01f, 10f),
            0f,
            100f,
            0f,
            100f,
        )
        assertFalse(p.plottable[0])
        assertTrue(p.plottable[1])
    }

    @Test
    fun `an empty frame projects to nothing rather than to a degenerate line`() {
        assertEquals(0, ChartProjection.project(emptyList(), 0L, 1_000L, scale, 0f, 10f, 0f, 10f).count)
        assertEquals(
            0,
            ChartProjection.project(listOf(bucket(0, 1f)), 0L, 0L, scale, 0f, 10f, 0f, 10f).count,
        )
    }

    @Test
    fun `the nearest column lookup drives the crosshair`() {
        val buckets = listOf(bucket(0, 0.5f), bucket(5_000, 0.5f), bucket(10_000, 0.5f))
        val p = ChartProjection.project(buckets, 0L, 11_000L, scale, 0f, 110f, 0f, 100f)
        assertEquals(0, p.nearestIndex(0f))
        assertEquals(1, p.nearestIndex(50f))
        assertEquals(2, p.nearestIndex(110f))
        assertEquals(null, PreparedFrame.EMPTY.nearestIndex(10f))
    }

    @Test
    fun `a real gap breaks the series, even when the columns are neighbours in the list`() {
        // Пустые колонки в снимок не попадают вовсе, поэтому соседство по
        // индексу не означает соседства во времени. Полевая картина этого
        // дефекта: длинный идеально прямой диагональный участок с
        // расширяющимся конвертом — картинка измерений, которых не было.
        val from = 0L
        val step = 60_000L
        val buckets = listOf(
            bucket(start = 0, median = 0.10f, widthMillis = step),
            bucket(start = step, median = 0.11f, widthMillis = step),
            // Полчаса спустя: следующая колонка с данными.
            bucket(start = 30 * step, median = 0.12f, widthMillis = step),
        )

        val pixels = ChartProjection.project(
            buckets = buckets,
            fromMillis = from,
            toMillis = 31 * step,
            scale = scale,
            leftPx = 0f,
            widthPx = 100f,
            topPx = 0f,
            heightPx = 100f,
        )

        assertEquals(3, pixels.count)
        assertFalse(pixels.segmentStart[0])
        assertFalse(pixels.segmentStart[1], "соседние по времени колонки не рвутся")
        assertTrue(pixels.segmentStart[2], "через получасовой пропуск линия обязана рваться")
    }

    @Test
    fun `columns one step apart are one segment`() {
        val step = 1_000L
        val buckets = (0 until 5).map {
            bucket(start = it * step, median = 0.1f, widthMillis = step)
        }

        val pixels = ChartProjection.project(
            buckets = buckets,
            fromMillis = 0,
            toMillis = 5 * step,
            scale = scale,
            leftPx = 0f,
            widthPx = 100f,
            topPx = 0f,
            heightPx = 100f,
        )

        assertTrue(pixels.segmentStart.none { it })
    }

    @Test
    fun `one second jitter at one hertz is not a gap`() {
        // Прибор пишет раз в секунду, и на минутном окне колонка тоже равна
        // секунде: пропущенная запись — это дрожание переноса, а не остановка
        // потока. Прежний порог в полторы ширины рвал линию на куски при
        // исправно идущих измерениях — на экране это выглядело как обрыв
        // графика с плашкой значения справа, к которой ничего не ведёт.
        val step = 1_000L
        val buckets = listOf(
            bucket(start = 0, median = 0.15f, widthMillis = step),
            bucket(start = 1 * step, median = 0.15f, widthMillis = step),
            // Секунда пропущена.
            bucket(start = 3 * step, median = 0.16f, widthMillis = step),
            bucket(start = 4 * step, median = 0.16f, widthMillis = step),
        )

        val pixels = ChartProjection.project(
            buckets = buckets,
            fromMillis = 0,
            toMillis = 5 * step,
            scale = scale,
            leftPx = 0f,
            widthPx = 100f,
            topPx = 0f,
            heightPx = 100f,
        )

        assertTrue(pixels.segmentStart.none { it }, "дрожание не рвёт линию")
    }

    @Test
    fun `a real stop still breaks the line on a one second grid`() {
        val step = 1_000L
        val buckets = listOf(
            bucket(start = 0, median = 0.15f, widthMillis = step),
            // Прибор молчал восемь секунд.
            bucket(start = 9 * step, median = 0.16f, widthMillis = step),
        )

        val pixels = ChartProjection.project(
            buckets = buckets,
            fromMillis = 0,
            toMillis = 10 * step,
            scale = scale,
            leftPx = 0f,
            widthPx = 100f,
            topPx = 0f,
            heightPx = 100f,
        )

        assertTrue(pixels.segmentStart[1])
    }
}
