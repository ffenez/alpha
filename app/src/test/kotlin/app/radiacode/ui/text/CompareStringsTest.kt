package app.radiacode.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Перевод области — перенос ПРАВИЛА, а не подстановка слов: английский каталог
 * обязан отказываться ровно там же, где отказывается русский.
 */
class CompareStringsTest {

    private val catalogues = CompareCatalogue.all

    @Test
    fun `every catalogue fills every string`() {
        for (catalogue in catalogues) {
            val texts = catalogue.allTexts()
            assertTrue(texts.isNotEmpty())
            for (text in texts) assertTrue(text.isNotBlank(), "пустая строка")
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
                    assertTrue(
                        !word.containsMatchIn(text.lowercase()),
                        "«$word»: $text",
                    )
                }
            }
        }
    }

    @Test
    fun `no verdict claims that the spectra are the same`() {
        // Критерий проверял ОТЛИЧИЕ: ни один вердикт не имеет права
        // утверждать равенство спектров — ни по-русски, ни по-английски.
        val claimsEquality = listOf(
            Regex("""совпада"""),
            Regex("""такой же"""),
            Regex("""идентичн"""),
            Regex("""равен"""),
            Regex("""\bsame\b"""),
            Regex("""\bidentical\b"""),
            Regex("""\bmatch\w*\b"""),
            Regex("""\bequal\w*\b"""),
            Regex("""\bunchanged\b"""),
        )
        for (catalogue in catalogues) {
            val verdicts = listOf(
                catalogue.verdictNoDifference,
                catalogue.verdictPossibleExcess,
                catalogue.verdictExcess,
                catalogue.verdictPossibleDeficit,
                catalogue.verdictDeficit,
            )
            for (verdict in verdicts) {
                for (word in claimsEquality) {
                    assertTrue(
                        !word.containsMatchIn(verdict.lowercase()),
                        "«$word»: $verdict",
                    )
                }
            }
        }
    }

    @Test
    fun `significance is claimed only where it is defined`() {
        // «Значимое» и «significant» стоят рядом с числом или не стоят вовсе:
        // в таблице вердикт — слово без своего p-значения.
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                val lower = text.lowercase()
                assertTrue(!lower.contains("значим"), text)
                assertTrue(!lower.contains("significan"), text)
            }
        }
    }

    @Test
    fun `catalogues are actually translated`() {
        assertTrue(CompareRu.verdictNoDifference != CompareEn.verdictNoDifference)
        assertTrue(CompareRu.chartDiffTitle != CompareEn.chartDiffTitle)
        assertEquals(CompareRu, CompareCatalogue.of(AppLanguage.RU))
        assertEquals(CompareEn, CompareCatalogue.of(AppLanguage.EN))
        // Единицы переезжают вместе с языком, обозначения статистики — нет.
        assertTrue(CompareEn.columnDiff.contains("counts/s"))
        assertTrue(CompareEn.columnEnergy.contains("keV"))
        assertTrue(CompareEn.chartDiffCaption.contains("σ"))
    }
}
