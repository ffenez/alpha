package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Окно следует накопленной истории.
 *
 * Полевой случай после переустановки: все три карточки открывались на шести
 * часах, пять с половиной из которых пусты, а всё измеренное сжималось в
 * несколько пикселей у правого края. У дозы это меняло САМ ПУТЬ ЧТЕНИЯ — окно
 * длиннее шести часов уходит на почасовые скетчи, и вся короткая история
 * складывалась в ОДНУ часовую колонку, то есть в одну точку, тогда как счёт и
 * жёсткость показывали ряд. Один поток измерений выглядел на трёх карточках
 * по-разному.
 */
class HistoryWindowTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `a short history shrinks the window to itself`() {
        val chosen = ChartWindows.latest(6 * 3_600_000L, now)
        val earliest = now - 12 * 60_000L

        val window = ChartWindows.limitedByHistory(chosen, earliest)

        assertEquals(earliest, window.fromMillis)
        assertEquals(now, window.toMillis, "правый край всегда «сейчас»")
    }

    @Test
    fun `a long history leaves the chosen step alone`() {
        // Дорастя до ступени, окно становится скользящим — то есть ведёт себя
        // ровно так, как раньше.
        val chosen = ChartWindows.latest(6 * 3_600_000L, now)

        val window = ChartWindows.limitedByHistory(chosen, now - 30 * 3_600_000L)

        assertEquals(chosen, window)
    }

    @Test
    fun `one measurement does not give a window of zero width`() {
        val chosen = ChartWindows.latest(6 * 3_600_000L, now)

        val window = ChartWindows.limitedByHistory(chosen, now - 500L)

        assertEquals(ChartWindows.MIN_HISTORY_SPAN_MILLIS, window.spanMillis)
    }

    @Test
    fun `no measurements at all leave the window as chosen`() {
        val chosen = ChartWindows.latest(30 * 60_000L, now)

        assertEquals(chosen, ChartWindows.limitedByHistory(chosen, null))
    }

    @Test
    fun `a shrunk window reaches the exact path, which is what lost the series`() {
        // Суть дефекта в одном утверждении: после подтяжки окно доходит до
        // точного пути чтения, и доза строится тем же рядом, что счёт и
        // жёсткость. До подтяжки оно уходило на скетчи и давало одну колонку.
        val chosen = ChartWindows.latest(6 * 3_600_000L, now)
        val fresh = ChartWindows.limitedByHistory(chosen, now - 12 * 60_000L)

        val loadOfChosen = ChartWindows.loadRange(chosen, now)
        val loadOfFresh = ChartWindows.loadRange(fresh, now)

        assertTrue(
            QuantilePaths.methodFor(loadOfChosen.spanMillis) == QuantileMethod.KLL_SKETCH,
            "шестичасовое окно с запасом на подгрузку и правда идёт по скетчам",
        )
        assertEquals(QuantileMethod.EXACT_RAW, QuantilePaths.methodFor(loadOfFresh.spanMillis))
    }
}
