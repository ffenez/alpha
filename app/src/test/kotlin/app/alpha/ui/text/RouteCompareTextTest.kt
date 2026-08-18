package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Итог сравнения маршрутов понятен без статистических терминов, а методика
 * живёт отдельно (`ROUTE_COMPARE_UI_CLEANUP.md`).
 *
 * Арифметика итога проверяется здесь же: сопоставлено = отличается + без
 * заметной разницы, и остаток не берётся из воздуха.
 */
class RouteCompareTextTest {

    private val catalogues = listOf(HistoryRu, HistoryEn)

    @Test
    fun `the result says what happened, without the method`() {
        val method = Regex("""P10|P90|медиан|median|клетк|cell|значим|significan""")
        for (s in catalogues) {
            val result = listOf(
                s.routeComparePlaces(91),
                s.routeCompareDiffering(34),
                s.routeCompareHigherOn(19, 1),
                s.routeCompareSame(57),
            )
            for (text in result) {
                assertTrue(
                    !method.containsMatchIn(text.lowercase()),
                    "методика в итоге: $text",
                )
            }
        }
    }

    @Test
    fun `the direction names the route it is about`() {
        for (s in catalogues) {
            val higher = s.routeCompareHigherOn(19, 1)
            val lower = s.routeCompareHigherOn(15, 2)
            assertTrue(higher.contains("1"), higher)
            assertTrue(lower.contains("2"), lower)
            assertTrue(higher != lower)
            // «Выше» без указания маршрута непонятно, поэтому имя маршрута
            // стоит в той же строке.
            assertTrue(higher.contains(s.routeNumber(1).dropLast(2)), higher)
        }
    }

    @Test
    fun `the numbers of the result add up`() {
        val matched = 91
        val higher = 19
        val lower = 15
        val same = matched - higher - lower
        assertEquals(57, same)
        assertTrue(HistoryRu.routeCompareSame(same).startsWith("57"))
        assertTrue(HistoryRu.routeCompareDiffering(higher + lower).startsWith("34"))
    }

    @Test
    fun `the method text keeps the terms and the limit`() {
        for (s in catalogues) {
            assertTrue(s.routeMethodDifference.contains("P10"), s.routeMethodDifference)
            assertTrue(s.routeMethodPatch("30 м", 5).contains("5"))
            // Ограничение метода названо словами и в справке, и на экране.
            val limit = (s.routeMethodLimit + " " + s.routeCompareDescriptive).lowercase()
            assertTrue(limit.contains("значим") || limit.contains("significan"), limit)
        }
    }

    @Test
    fun `the main result never calls the difference significant`() {
        val forbidden = Regex("""статистически значим|statistically significant""")
        for (s in catalogues) {
            for (text in s.allTexts()) {
                assertTrue(!forbidden.containsMatchIn(text.lowercase()), text)
            }
        }
    }
}
