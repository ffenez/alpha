package app.radiacode.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Справка о нуклиде переводится вместе со своим ПРАВИЛОМ: она описывает
 * нуклид, а не находку. Английский обязан отказываться от вывода ровно там,
 * где отказывается русский, и не обещать безопасности ни в одном языке.
 */
class NuclideStringsTest {

    private val catalogues = NuclideCatalogue.all

    @Test
    fun `nothing in either language promises safety`() {
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
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word» in: $text")
                }
            }
        }
    }

    @Test
    fun `no card text claims the nuclide was detected`() {
        // Вводного абзаца, который произносил «обнаружение», чтобы его
        // отрицать, больше нет: потолок держит статус, и слова находки в
        // каталоге запрещены БЕЗ исключений.
        val detection = Regex(
            """обнаружен|выявлен|найден нуклид|\bdetected\b|\bidentified\b|\bconfirms\b""",
        )
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                assertTrue(!detection.containsMatchIn(text.lowercase()), "находка в: $text")
            }
        }
    }

    @Test
    fun `the status keeps the caution ceiling in both languages`() {
        assertEquals("ВОЗМОЖНОЕ СОВПАДЕНИЕ", NuclideRu.statusPossibleMatch)
        assertEquals("POSSIBLE MATCH", NuclideEn.statusPossibleMatch)
        // Отказ переводится как отказ: «не оценивалось» ≠ «не найдено».
        assertTrue(NuclideRu.lineNotEvaluated != NuclideRu.lineNotFound)
        assertTrue(NuclideEn.lineNotEvaluated != NuclideEn.lineNotFound)
    }

    @Test
    fun `no card gives medical or dose advice`() {
        // §23: карточка не переводит наблюдение в вред и не называет доз.
        val advice = Regex("""\bмЗв\b|\bврач\b|\bmSv\b|\bdoctor\b|\bexposure limit\b""")
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                assertTrue(!advice.containsMatchIn(text.lowercase()), "совет/доза в: $text")
            }
        }
    }

    @Test
    fun `data is not translated`() {
        // Символы нуклидов, энергии и выходы — данные: в обоих языках они
        // печатаются одинаково, переводится только слово вокруг числа.
        assertEquals("609,3 кэВ · 45,5 % на распад", NuclideRu.gammaLine("609,3", "45,5"))
        assertEquals("609,3 keV · 45,5 % per decay", NuclideEn.gammaLine("609,3", "45,5"))
        assertTrue(NuclideEn.co60Confirmation.contains("1173"), NuclideEn.co60Confirmation)
        assertTrue(NuclideEn.tl208Everyday.contains("2615 keV"), NuclideEn.tl208Everyday)
    }

    @Test
    fun `both languages keep the same number of texts`() {
        assertEquals(NuclideRu.allTexts().size, NuclideEn.allTexts().size)
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                assertTrue(text.isNotBlank(), "пустая строка в каталоге")
            }
        }
    }
}
