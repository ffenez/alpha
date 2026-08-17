package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Сравнение маршрутов описывает наблюдение и отказывается от вывода там, где
 * данных на вывод нет. Проверяется ровно этот отказ.
 */
class RouteDiffTest {

    private fun walk(
        latitude: Double,
        longitude: Double,
        values: List<Float>,
        startAt: Long = 0L,
    ) = values.mapIndexed { index, value ->
        MapTrackPoint(
            timestamp = startAt + index * 1_000L,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = 5f,
            doseMicroSvH = value,
            cps = value * 100f,
        )
    }

    @Test
    fun `a place walked by only one route is not a difference`() {
        val first = walk(55.0000, 37.0000, List(20) { 0.12f })
        // Другой конец города: общих клеток нет вовсе.
        val second = walk(55.5000, 37.5000, List(20) { 0.30f })
        val result = RouteDiff.compare(first, second, TrackMetric.DOSE)
        assertEquals(0, result.matched)
        assertTrue(result.differing.isEmpty())
    }

    @Test
    fun `a handful of fixes in a cell is not compared`() {
        val first = walk(55.0, 37.0, List(3) { 0.12f })
        val second = walk(55.0, 37.0, List(3) { 0.30f })
        assertEquals(0, RouteDiff.compare(first, second, TrackMetric.DOSE).matched)
    }

    /**
     * Перекрывшиеся разбросы — не «одинаково», а «различия не видно»: клетка
     * сопоставлена, но в число различающихся не входит.
     */
    @Test
    fun `overlapping spreads are counted as matched and not as a difference`() {
        val first = walk(55.0, 37.0, List(20) { 0.12f + (it % 5) * 0.01f })
        val second = walk(55.0, 37.0, List(20) { 0.13f + (it % 5) * 0.01f })
        val result = RouteDiff.compare(first, second, TrackMetric.DOSE)
        assertEquals(1, result.matched)
        assertTrue(result.differing.isEmpty(), "${result.cells}")
        assertTrue(result.cells.single().overlapping)
    }

    @Test
    fun `a separated spread is called higher, with the denominator named`() {
        val first = walk(55.0, 37.0, List(20) { 0.30f })
        val second = walk(55.0, 37.0, List(20) { 0.12f })
        val result = RouteDiff.compare(first, second, TrackMetric.DOSE)
        assertEquals(1, result.matched)
        val cell = result.cells.single()
        assertTrue(cell.higher)
        assertTrue(!cell.lower)
        assertEquals(2.5f, cell.ratio, 1e-3f)
        assertEquals(1, result.higher.size)
        assertEquals(0, result.lower.size)
    }

    @Test
    fun `the direction follows the order of the arguments`() {
        val low = walk(55.0, 37.0, List(20) { 0.12f })
        val high = walk(55.0, 37.0, List(20) { 0.30f })
        assertTrue(RouteDiff.compare(low, high, TrackMetric.DOSE).cells.single().lower)
    }

    /** Величина сравнения — та, что выбрана на экране, а не «доза всегда». */
    @Test
    fun `the compared quantity is the chosen one`() {
        val first = walk(55.0, 37.0, List(20) { 0.30f })
        val second = walk(55.0, 37.0, List(20) { 0.12f })
        val byCps = RouteDiff.compare(first, second, TrackMetric.CPS)
        assertEquals(1, byCps.matched)
        assertEquals(30f, byCps.cells.single().medianA, 1e-3f)
    }

    @Test
    fun `an empty route has nothing to compare`() {
        assertEquals(0, RouteDiff.compare(emptyList(), walk(55.0, 37.0, listOf(0.1f)), TrackMetric.DOSE).matched)
    }
}

/**
 * Курсор графика и кольцо на карте — один момент маршрута. Считается это
 * здесь, поэтому здесь и проверяется.
 */
class RouteProfileTest {

    private val times = listOf(1_000L, 2_000L, 3_000L, 10_000L)

    @Test
    fun `the fraction spans the whole route`() {
        assertEquals(0f, RouteProfile.fractionOf(1_000L, 1_000L, 10_000L))
        assertEquals(1f, RouteProfile.fractionOf(10_000L, 1_000L, 10_000L))
        // Маршрут длиной в миг: делить нечего, точка слева.
        assertEquals(0f, RouteProfile.fractionOf(5L, 5L, 5L))
    }

    @Test
    fun `a touch picks the nearest moment, not the one before it`() {
        assertEquals(0, RouteProfile.indexAt(times, 0f))
        assertEquals(3, RouteProfile.indexAt(times, 1f))
        // 0,9 от 1–10 с это 9,1 с: ближайший момент — 10 с, а не предыдущий.
        assertEquals(3, RouteProfile.indexAt(times, 0.9f))
        // 0,5 это 5,5 с: до 3 с ближе, чем до 10 с.
        assertEquals(2, RouteProfile.indexAt(times, 0.5f))
        assertNull(RouteProfile.indexAt(emptyList(), 0.5f))
    }

    @Test
    fun `a tap on the track finds the same moment on the chart`() {
        assertEquals(1, RouteProfile.indexOfTime(times, 2_100L))
        assertEquals(3, RouteProfile.indexOfTime(times, 999_999L))
        assertNull(RouteProfile.indexOfTime(emptyList(), 1L))
    }

    /**
     * Ось значений не притягивается к нулю: профиль показывает, где уровень
     * менялся, и прижатая к нулю линия скрывала бы ровно это.
     */
    @Test
    fun `the value axis covers what was measured`() {
        val bounds = RouteProfile.bounds(listOf(0.11f, 0.14f, 0.12f))
        assertEquals(0.11f, bounds!!.start)
        assertEquals(0.14f, bounds.endInclusive)
        // Ровный маршрут: края разводятся, иначе линия легла бы на границу.
        val flat = RouteProfile.bounds(listOf(0.12f, 0.12f))!!
        assertTrue(flat.endInclusive > flat.start)
        assertNull(RouteProfile.bounds(emptyList()))
    }
}
