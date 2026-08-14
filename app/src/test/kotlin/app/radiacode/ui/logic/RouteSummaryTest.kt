package app.radiacode.ui.logic

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
     * Доза за маршрут — оценка средней мощности на длительность, и у идущей
     * записи её нет: делить незаконченное не на что.
     */
    @Test
    fun `dose over the route is the mean rate times the time it took`() {
        assertEquals(0.12, route().doseMicroSv!!, 1e-6)
        assertNull(route(endedAt = null).doseMicroSv)
        assertNull(route(avg = null).doseMicroSv)
        // Маршрут, начатый и законченный в одну секунду, дозы не даёт.
        assertNull(route(startedAt = 0L, endedAt = 0L).doseMicroSv)
    }
}

class RouteTitleTest {

    /**
     * Имя даётся ПОСЛЕ прогулки и не обязательно: пока его нет, маршрут
     * подписан датой — иначе список был бы из одинаковых слов «Маршрут».
     */
    @Test
    fun `a nameless route is titled by its date`() {
        val titled = RouteFormat.title(route(name = "Дом → парк"), nowMillis = 0L)
        assertEquals("Дом → парк", titled)

        val untitled = RouteFormat.title(route(name = "   "), nowMillis = 0L)
        assertTrue(untitled.isNotBlank())
        assertTrue(untitled.any { it.isDigit() }, untitled)
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

    @Test
    fun `the whole route fits the square and keeps its proportions`() {
        val points = listOf(
            55.0 to 37.0,
            55.01 to 37.0,
            55.01 to 37.02,
        )
        val shape = RouteShape.normalize(points)
        assertEquals(points.size, shape.size)
        assertTrue(shape.all { it.first in 0f..1f && it.second in 0f..1f }, "$shape")
        // Север сверху: самая северная точка получает наименьший экранный y.
        val northern = shape[1].second
        val southern = shape[0].second
        assertTrue(northern < southern, "$shape")
    }

    /** Долгота сжимается по широте — иначе маршрут вытягивался бы поперёк. */
    @Test
    fun `longitude is squeezed by the latitude`() {
        // Квадрат в градусах на широте 60° вдвое у́же в метрах, чем в высоту.
        val shape = RouteShape.normalize(
            listOf(60.0 to 30.0, 60.01 to 30.0, 60.0 to 30.01),
        )
        val height = shape[0].second - shape[1].second
        val width = shape[2].first - shape[0].first
        assertTrue(width < height * 0.75f, "width=$width height=$height")
    }

    @Test
    fun `a route that never moved is a dot in the middle`() {
        val shape = RouteShape.normalize(List(5) { 55.0 to 37.0 })
        assertTrue(shape.all { it == 0.5f to 0.5f }, "$shape")
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
