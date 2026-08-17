package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Перевод справки графика переносит ПРАВИЛО, а не слова: английский каталог
 * подчиняется тем же запретам, что и русский, и отказывается от утверждения
 * там же, где отказывается русский.
 */
class ChartTextStringsTest {

    private val catalogues = listOf(ChartTextRu, ChartTextEn)

    @Test
    fun `every catalogue fills every string`() {
        for (catalogue in catalogues) {
            val texts = catalogue.allTexts()
            assertTrue(texts.isNotEmpty())
            for (text in texts) assertTrue(text.isNotBlank(), "пустая строка: $catalogue")
        }
    }

    @Test
    fun `no catalogue may promise safety`() {
        val forbidden = listOf(
            Regex("""\bбезопасн\w*\b"""),
            Regex("""\bопасн\w*\b"""),
            Regex("""\bдопустим\w*\b"""),
            Regex("""\bнорма\b"""),
            Regex("""\bnormal\b(?! distribution)"""),
            Regex("""\bsafe\b"""),
            Regex("""\bdangerous\b"""),
            Regex("""\bharmless\b"""),
        )
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                for (word in forbidden) {
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word»: $text")
                }
            }
        }
    }

    @Test
    fun `both languages refuse the same claims`() {
        // Полосы — разброс измерений: ни один язык не имеет права назвать их
        // погрешностью прибора или доверительным интервалом.
        // Первый уровень отказывается от «погрешности прибора» сразу — эту
        // подмену человек делает чаще всего; «доверительный интервал» и
        // «неопределённость измерения» названы на втором уровне, где вообще
        // появляются P25–P75 и P10–P90.
        assertTrue(ChartTextRu.anatomyEnvelopes.contains("не погрешность прибора"))
        assertTrue(ChartTextRu.anatomyEnvelopesDetail.contains("не доверительный интервал"))
        assertTrue(
            ChartTextRu.anatomyEnvelopesDetail.contains("не неопределённость измерения"),
        )
        assertTrue(ChartTextEn.anatomyEnvelopes.contains("not the instrument's uncertainty"))
        assertTrue(ChartTextEn.anatomyEnvelopesDetail.contains("not a confidence interval"))
        assertTrue(
            ChartTextEn.anatomyEnvelopesDetail.contains("not the uncertainty of the measurement"),
        )
        // Маркер ▲ — сравнение, а не вердикт об аномалии.
        assertTrue(ChartTextRu.referenceMarkers.contains("не признак аномалии"))
        assertTrue(ChartTextEn.referenceMarkers.contains("not a sign of an anomaly"))
        // Исторический диапазон места — не норматив.
        assertTrue(ChartTextRu.referenceProfileBand.contains("не норматив"))
        assertTrue(ChartTextEn.referenceProfileBand.contains("not a regulatory limit"))
        // У грубой оценки нет доказанной границы ТОЧНОСТИ, и говорится это на
        // втором уровне: слово «ошибка» на первом читалось как ошибка
        // измерения, чем она не является (14.md §3).
        assertTrue(
            ChartTextRu.quantilesSubBucketMeansDetail.contains("без доказанной границы точности"),
        )
        assertTrue(
            ChartTextEn.quantilesSubBucketMeansDetail.contains("no proven bound on its accuracy"),
        )
        assertTrue(!ChartTextRu.quantilesSubBucketMeans.contains("ошибк"))
        assertTrue(!ChartTextEn.quantilesSubBucketMeans.lowercase().contains("error"))
        // Первый уровень называет ПРИЧИНУ приближённости, а не реализацию.
        assertTrue(ChartTextRu.quantilesSubBucketMeans.contains("приблизительная"))
        assertTrue(ChartTextRu.quantilesSketch("≈ 1 %").contains("сжатую историю"))
    }

    /**
     * 14.md §4: в пользовательском тексте порядковые статистики называются
     * P-нотацией. «Процентиль» читается людьми, «квартиль Q» — нет, а
     * математический смысл тот же.
     */
    @Test
    fun `user-facing text uses the P notation, never Q`() {
        val q = Regex("""Q\d""")
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                assertTrue(!q.containsMatchIn(text), "Q-нотация: $text")
            }
        }
    }

    @Test
    fun `the catalogues are really translated`() {
        assertTrue(ChartTextRu.infoTitle != ChartTextEn.infoTitle)
        assertTrue(ChartTextRu.median != ChartTextEn.median)
        assertTrue(ChartTextRu.quantilesExact != ChartTextEn.quantilesExact)
    }
}
