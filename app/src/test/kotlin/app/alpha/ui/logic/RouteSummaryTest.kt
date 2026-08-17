package app.alpha.ui.logic

import app.alpha.ui.text.HistoryRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun route(
    id: Long = 1L,
    name: String = "",
    startedAt: Long = 0L,
    endedAt: Long? = 3_600_000L,
    distance: Double? = 3_800.0,
    count: Int = 4_654,
    avg: Float? = 0.12f,
    max: Float? = 0.21f,
) = RouteSummary(
    id = id,
    name = name,
    startedAt = startedAt,
    endedAt = endedAt,
    distanceMeters = distance,
    measurementCount = count,
    avgDoseMicroSvH = avg,
    maxDoseMicroSvH = max,
)

class RouteSummaryTest {

    @Test
    fun `duration comes from the recorded ends`() {
        assertEquals(3_600L, route().durationSeconds)
        // Идущая запись: длительность считается до сих пор, а не до нуля.
        assertEquals(0L, route(endedAt = null).durationSeconds)
        assertTrue(route(endedAt = null).running)
    }

    /**
     * Доза маршрута — ИНТЕГРАЛ ПО ИЗМЕРЕНИЯМ, который считает репозиторий
     * ([app.alpha.data.TrackRepository]), а не средняя мощность на календарное
     * время: минуты без показаний дозы не дают. Сводка её только несёт, и у
     * записи без измерений её нет вовсе.
     */
    @Test
    fun `the route carries the measured dose and invents none`() {
        assertNull(route().doseMicroSv)
        assertEquals(0.12, route().copy(doseMicroSv = 0.12).doseMicroSv!!, 1e-6)
        assertNull(route(endedAt = null).doseMicroSv)
    }
}

class RouteTitleTest {

    /**
     * Имя даётся ПОСЛЕ прогулки и не обязательно: пока его нет, маршрут
     * подписан временем начала — иначе список был бы из одинаковых слов
     * «Маршрут». День в подпись не входит: он стоит заголовком группы.
     */
    @Test
    fun `a nameless route is titled by the time it started`() {
        val titled = RouteFormat.title(route(name = "Дом → парк"), nowMillis = 0L)
        assertEquals("Дом → парк", titled)

        val untitled = RouteFormat.title(route(name = "   "), nowMillis = 0L)
        assertTrue(untitled.startsWith("Маршрут · "), untitled)
        assertTrue(untitled.any { it.isDigit() }, untitled)
        // Ни числа месяца, ни его названия — только время.
        assertTrue(HistoryRu.months.none { it in untitled }, untitled)
    }

    @Test
    fun `a name of spaces is no name, and a name has a limit`() {
        assertEquals("", RouteFormat.cleanName("   "))
        assertEquals("Дом", RouteFormat.cleanName("  Дом  "))
        assertEquals(
            RouteFormat.MAX_NAME_LENGTH,
            RouteFormat.cleanName("я".repeat(500)).length,
        )
    }
}

/**
 * Миниатюра — форма маршрута, а не карта: по ней узнают свою прогулку.
 * Проверяется то, из-за чего она перестала бы быть похожей.
 */
class RouteShapeTest {

    private fun at(latitude: Double, longitude: Double, dose: Float? = 0.12f) =
        RouteShapePoint(latitude = latitude, longitude = longitude, doseMicroSvH = dose)

    @Test
    fun `the whole route fits the square and keeps its proportions`() {
        val points = listOf(at(55.0, 37.0), at(55.01, 37.0), at(55.01, 37.02))
        val shape = RouteShape.normalize(points)
        assertEquals(points.size, shape.size)
        assertTrue(shape.all { it.x in 0f..1f && it.y in 0f..1f }, "$shape")
        // Север сверху: самая северная точка получает наименьший экранный y.
        assertTrue(shape[1].y < shape[0].y, "$shape")
    }

    /** Измерение едет вместе с формой: миниатюра красится тем, что намерено. */
    @Test
    fun `the measurement travels with the shape`() {
        val shape = RouteShape.normalize(
            listOf(at(55.0, 37.0, dose = 0.11f), at(55.01, 37.0, dose = 0.31f)),
        )
        assertEquals(0.11f, shape[0].value)
        assertEquals(0.31f, shape[1].value)
        // Точка без измерения остаётся без значения, а не получает соседнее.
        assertNull(RouteShape.normalize(listOf(at(55.0, 37.0, dose = null))).single().value)
    }

    /** Долгота сжимается по широте — иначе маршрут вытягивался бы поперёк. */
    @Test
    fun `longitude is squeezed by the latitude`() {
        // Квадрат в градусах на широте 60° вдвое у́же в метрах, чем в высоту.
        val shape = RouteShape.normalize(
            listOf(at(60.0, 30.0), at(60.01, 30.0), at(60.0, 30.01)),
        )
        val height = shape[0].y - shape[1].y
        val width = shape[2].x - shape[0].x
        assertTrue(width < height * 0.75f, "width=$width height=$height")
    }

    @Test
    fun `a route that never moved is a dot in the middle`() {
        val shape = RouteShape.normalize(List(5) { at(55.0, 37.0) })
        assertTrue(shape.all { it.x == 0.5f && it.y == 0.5f }, "$shape")
    }

    @Test
    fun `nothing recorded, nothing drawn`() {
        assertTrue(RouteShape.normalize(emptyList()).isEmpty())
    }

    /** Прореживание держит около заданного числа точек и никогда не меньше 1. */
    @Test
    fun `the stride keeps the thumbnail cheap`() {
        assertEquals(1, RouteShape.stride(50, target = 120))
        assertEquals(1, RouteShape.stride(120, target = 120))
        assertEquals(39, RouteShape.stride(4_654, target = 120))
        assertTrue(4_654 / RouteShape.stride(4_654, target = 120) <= 120)
    }
}
