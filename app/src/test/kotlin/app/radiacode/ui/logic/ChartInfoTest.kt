package app.radiacode.ui.logic

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
    ) = ChartInfo.sections(metric, band, markers, episodes, method, logScale, logDropped)

    private fun lines(sections: List<ChartInfoSection>) = sections.flatMap { it.lines }

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

    @Test
    fun `the quantile path of this window is named`() {
        assertTrue(lines(sections(method = QuantileMethod.EXACT_RAW)).any { it.contains("точные") })
        val sketch = lines(sections(method = QuantileMethod.KLL_SKETCH))
        assertTrue(sketch.any { it.contains("приближённые") }, "$sketch")
        assertTrue(sketch.any { it.contains("Ошибка ранга") }, "$sketch")
        val coarse = lines(sections(method = QuantileMethod.SUB_BUCKET_MEANS))
        assertTrue(coarse.any { it.contains("грубая оценка") }, "$coarse")
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
                val text = lines(sections(metric = metric, method = method)) +
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
