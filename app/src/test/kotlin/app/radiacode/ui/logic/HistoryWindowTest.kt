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

    @Test
    fun `the reload cadence follows the column width, not the window length`() {
        // Полевой случай: полуторачасовое окно давало перечитывание раз в 15 с,
        // и карточка выглядела замершей при живом полноэкранном графике —
        // данные у них одни и те же. Новая колонка появляется раз в свою
        // ширину, и читать чаще её четверти незачем.
        val ninetyMinutes = 91 * 60_000L
        val bucket = ChartSeriesModel.bucketMillis(ninetyMinutes)

        val cadence = ChartWindows.refreshMillis(bucket)

        assertTrue(cadence < 15_000L, "перечитывание раз в $cadence мс")
        assertEquals(bucket / 4, cadence)
    }

    @Test
    fun `a minute window reloads at one hertz`() {
        // На коротком окне колонка равна секунде, и четверть секунды упирается
        // в нижнюю границу: чаще, чем прибор пишет, читать нечего.
        val bucket = ChartSeriesModel.bucketMillis(60_000L)

        assertEquals(1_000L, ChartWindows.refreshMillis(bucket))
    }

    @Test
    fun `the default window is live, not hours wide`() {
        // Полевая жалоба «графики не обновляются в реальном времени» при
        // полной трассе конвейера: потери нет, но при шестичасовом окне
        // колонка около полутора минут, и новая точка появляется раз в это
        // время. Край ползёт, линия стоит — на глаз это замерший график.
        val default = ChartWindows.PERIODS[ChartWindows.DEFAULT_PERIOD_INDEX].second
        assertEquals(5 * 60_000L, default)

        val column = ChartSeriesModel.bucketMillis(default)
        assertTrue(column <= 3_000L, "колонка $column мс — движение не разглядеть")
        assertEquals(1_000L, ChartWindows.refreshMillis(column))
    }

    @Test
    fun `a pan inside the loaded range needs no query`() {
        // Загрузка берёт четверть окна запаса с каждой стороны именно ради
        // этого: сдвиг внутри прочитанного — перепроецирование неизменного
        // снимка, а не поход в базу. Без проверки покрытия каждый рывок
        // пальцем упирался бы в диск.
        val window = ChartWindows.latest(5 * 60_000L, now)
        val loaded = ChartWindows.loadRange(window, now)

        // На пятиминутном окне запас абсолютный — час: полчаса хода пальцем
        // проходят без единого запроса, и подгрузки не видно вовсе.
        val halfHour = ChartWindow(window.fromMillis - 1_800_000L, window.toMillis - 1_800_000L)
        val far = ChartWindow(window.fromMillis - 7_200_000L, window.toMillis - 7_200_000L)

        assertTrue(ChartWindows.covers(loaded, halfHour), "полчаса уже прочитаны")
        assertTrue(!ChartWindows.covers(loaded, far), "два часа читаются заново")
    }
}
