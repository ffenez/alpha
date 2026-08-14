package app.radiacode.ui.logic

import app.radiacode.ui.text.ChartTextRu
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Справка описывает ТО, ЧТО СЕЙЧАС НА ЭКРАНЕ. Рассказ про полосу профиля,
 * которой нет, и про маркеры, которых нет, — это не помощь, а лишний текст,
 * который человек будет искать глазами на графике.
 */
class ChartInfoTest {

    private fun sections(
        metric: ChartMetric = ChartMetric.DOSE,
        band: Boolean = true,
        markers: Boolean = true,
        episodes: Boolean = true,
        method: QuantileMethod = QuantileMethod.EXACT_RAW,
        logScale: Boolean = false,
        logDropped: Int = 0,
        historical: Boolean = false,
        // Справка описывает КАРТИНКУ, а картинка бывает двух видов; по
        // умолчанию проверяется сглаженный — тот, у которого есть заливки.
        detail: ChartDetailMode = ChartDetailMode.SMOOTHED,
    ) = ChartInfo.sections(
        metric = metric,
        hasBaselineBand = band,
        hasExtremeMarkers = markers,
        hasEpisodes = episodes,
        method = method,
        logScale = logScale,
        logDropped = logDropped,
        detail = detail,
        historical = historical,
    )

    /**
     * Справка описывает ТУ картинку, что на экране.
     *
     * В подробном виде квантильных заливок нет вовсе, и объяснять их — значит
     * объяснять пустое место; в сглаженном они есть, и без определения
     * оставлять их нельзя.
     */
    @Test
    fun `the view is named and only its own anatomy is explained`() {
        val detailed = allText(sections(detail = ChartDetailMode.DETAILED))
        val smoothed = allText(sections(detail = ChartDetailMode.SMOOTHED))

        assertTrue(detailed.any { it == ChartTextRu.detailNote }, "$detailed")
        assertTrue(detailed.none { it == ChartTextRu.anatomyEnvelopes }, "$detailed")
        assertTrue(smoothed.any { it == ChartTextRu.smoothedNote }, "$smoothed")
        assertTrue(smoothed.any { it == ChartTextRu.anatomyEnvelopes }, "$smoothed")
    }

    /** Первый уровень: то, что видно сразу. */
    private fun lines(sections: List<ChartInfoSection>) = sections.flatMap { it.lines }

    /** Второй уровень: то, что раскрывается «Подробнее». */
    private fun details(sections: List<ChartInfoSection>) = sections.flatMap { it.details }

    private fun allText(sections: List<ChartInfoSection>) = lines(sections) + details(sections)

    @Test
    fun `what is not drawn is not explained`() {
        val without = lines(sections(band = false, markers = false, episodes = false))
        assertTrue(without.none { it.contains("▲") }, "$without")
        assertTrue(without.none { it.contains("эпизод") }, "$without")
        // Отсутствие полосы объясняется прямо, а не молчанием.
        assertTrue(without.any { it.contains("ещё не собран") }, "$without")

        val with = lines(sections())
        assertTrue(with.any { it.contains("▲") })
        assertTrue(with.any { it.contains("P10–P90") })
    }

    /**
     * Путь квантилей назван на обоих уровнях: первый говорит, откуда взяты
     * числа и насколько им можно верить, второй — как именно они посчитаны
     * (14.md §3: реализация не должна быть первым, что человек читает).
     */
    @Test
    fun `the quantile path of this window is named`() {
        val exact = sections(method = QuantileMethod.EXACT_RAW)
        assertTrue(lines(exact).any { it.contains("по сохранённым измерениям") }, "$exact")
        assertTrue(details(exact).any { it.contains("Квантили точные") }, "$exact")

        val sketch = sections(method = QuantileMethod.KLL_SKETCH)
        assertTrue(lines(sketch).any { it.contains("сжатую историю") }, "$sketch")
        assertTrue(lines(sketch).any { it.contains("приближённые") }, "$sketch")
        assertTrue(details(sketch).any { it.contains("Ошибка ранга") }, "$sketch")

        val coarse = sections(method = QuantileMethod.SUB_BUCKET_MEANS)
        assertTrue(lines(coarse).any { it.contains("пока приблизительная") }, "$coarse")
        assertTrue(
            details(coarse).any { it.contains("без доказанной границы точности") },
            "$coarse",
        )
        // Слово «ошибка» с первого уровня ушло: речь об аппроксимации
        // статистики, а не об ошибке измерения.
        assertTrue(lines(coarse).none { it.contains("ошибк") }, "$coarse")
    }

    /** Первый уровень объясняет, а не определяет: P50 и P25–P75 — второй. */
    @Test
    fun `the human level explains, the second level names the statistics`() {
        val all = sections()
        assertTrue(lines(all).any { it.contains("типичный уровень") }, "$all")
        assertTrue(lines(all).any { it.contains("средние 50 %") }, "$all")
        assertTrue(lines(all).none { it.contains("P50") }, "$all")
        assertTrue(details(all).any { it.contains("P50") }, "$all")
        assertTrue(details(all).any { it.contains("P25–P75") }, "$all")
    }

    @Test
    fun `the log scale says how many buckets it could not draw`() {
        val dropped = lines(sections(logScale = true, logDropped = 7))
        assertTrue(dropped.any { it.contains("не показано — 7") }, "$dropped")
        val none = lines(sections(logScale = true, logDropped = 0))
        assertTrue(none.none { it.contains("не показано") }, "$none")
    }

    @Test
    fun `count rate and hardness say why long windows are missing and lose the dose threshold`() {
        for (metric in listOf(ChartMetric.COUNT_RATE, ChartMetric.HARDNESS)) {
            val text = lines(sections(metric = metric))
            assertTrue(text.any { it.contains("Порога тревоги на этом графике нет") }, "$text")
            assertTrue(text.any { it.contains("Окна длиннее") }, "$text")
        }
        val hardness = lines(sections(metric = ChartMetric.HARDNESS))
        assertTrue(hardness.any { it.contains("по каждому отсчёту") }, "$hardness")
    }

    @Test
    fun `a historical range explains its own edge instead of the live one`() {
        val live = lines(sections())
        assertTrue(live.any { it.contains("к живому краю") }, "$live")
        assertTrue(live.none { it.contains("сохранённый диапазон") }, "$live")

        val past = lines(sections(historical = true))
        // Двойное нажатие возвращает к сессии, а не к «сейчас».
        assertTrue(past.any { it.contains("к концу сессии") }, "$past")
        assertTrue(past.none { it.contains("к живому краю") }, "$past")
        // И график честно говорит, что новые измерения сюда не приходят.
        assertTrue(past.any { it.contains("сохранённый диапазон") }, "$past")
    }

    @Test
    fun `no machine-readable leftovers and no promises of safety`() {
        val forbidden = listOf(
            Regex("""\bбезопасн(о|ый|ая|ое)\b"""),
            Regex("""\bопасн(о|ый|ая|ое)\b"""),
            Regex("""\bдопустим\w*\b"""),
            Regex("""\bнормальн\w*\b(?! распределени)"""),
            Regex("""\bнорма\b"""),
        )
        for (metric in ChartMetric.entries) {
            for (method in QuantileMethod.entries) {
                val text = allText(sections(metric = metric, method = method)) +
                    sections(metric = metric, method = method).map { it.title }
                for (line in text) {
                    // Ни JSON, ни имён классов: справка — это текст, а не отладочный вывод.
                    assertTrue(!line.contains("{"), line)
                    assertTrue(!line.contains("KLL"), line)
                    for (word in forbidden) {
                        assertTrue(!word.containsMatchIn(line.lowercase()), "«$word» in: $line")
                    }
                }
            }
        }
    }
}
