package app.radiacode.ui.logic

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Переход через полночь обязан быть виден: «23:00 · 01:00» читается как один
 * вечер, хотя между метками сменилась дата.
 */
class TimeAxisDateTest {

    private val zone = ZoneId.of("UTC")

    /** 2 января 2026, 22:00 UTC. */
    private val evening = 1_767_391_200_000L

    @Test
    fun `the first label of a new day carries the date`() {
        val labels = TimeAxis.labels(
            fromMillis = evening,
            toMillis = evening + 6 * 3_600_000L,
            zone = zone,
            count = 4,
        )
        val texts = labels.map { it.second }
        assertTrue(texts.any { it.any(Char::isLetter) }, "нет ни одной подписи с числом: $texts")
        // И не у каждой: повторённое число ничего не различает.
        assertTrue(texts.count { it.any(Char::isLetter) } < texts.size, "$texts")
    }

    @Test
    fun `a window inside one day stays clock-only`() {
        val labels = TimeAxis.labels(
            fromMillis = evening - 6 * 3_600_000L,
            toMillis = evening - 3_600_000L,
            zone = zone,
            count = 4,
        )
        assertTrue(labels.none { it.second.any(Char::isLetter) }, "${labels.map { it.second }}")
    }
}
