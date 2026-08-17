package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Спектрограмма объясняет картинку словами, и слова обязаны подчиняться тем же
 * запретам на обоих языках: «safe» нельзя ровно по той причине, по которой
 * нельзя «безопасно».
 */
class SpectrogramStringsTest {

    private val catalogues = SpectrogramCatalogue.all

    @Test
    fun `every catalogue fills every string`() {
        for (catalogue in catalogues) {
            val texts = catalogue.allTexts()
            assertTrue(texts.isNotEmpty())
            for (text in texts) {
                assertTrue(text.isNotBlank(), "пустая строка: $catalogue")
            }
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

    /**
     * Две оговорки, ради которых область вообще существует: режим формы НЕ
     * показывает интенсивность, а пустая колонка означает отсутствие
     * измерений, а не посчитанный ноль. Перевод не имеет права их потерять.
     */
    @Test
    fun `the scientific refusals survive the translation`() {
        assertTrue(SpectrogramEn.shapeNote.contains("does not show absolute intensity"))
        assertTrue(SpectrogramRu.shapeNote.contains("не показывает абсолютную интенсивность"))
        assertTrue(SpectrogramEn.energyRangeNote(20, 3000).contains("no measurements"))
        assertTrue(SpectrogramEn.energyRangeNote(20, 3000).contains("gaps are not filled in"))
        assertTrue(SpectrogramRu.energyRangeNote(20, 3000).contains("измерений в этой ячейке"))
        // Общая шкала окна — то, что делает столбцы сравнимыми.
        assertTrue(SpectrogramEn.intensityNote.contains("One logarithmic scale"))
        // История лежит в базе (ADR 007) — теперь это обещание можно давать, и
        // оно обязано звучать на обоих языках одинаково определённо.
        assertTrue(SpectrogramEn.backgroundNote.contains("survives an app restart"))
        assertTrue(SpectrogramRu.backgroundNote.contains("переживает перезапуск"))
        assertTrue(SpectrogramRu.title != SpectrogramEn.title)
    }

    /**
     * Ступень частоты названа своим ЧИСЛОМ: «обычно» без «30 с» не отвечает на
     * вопрос, какое временнóе разрешение получит история.
     */
    @Test
    fun `every rate step names its own interval`() {
        for (catalogue in catalogues) {
            assertTrue(catalogue.rateDetailed.contains("5"), catalogue.rateDetailed)
            assertTrue(catalogue.rateBalanced.contains("30"), catalogue.rateBalanced)
            assertTrue(catalogue.rateEconomy.contains("10"), catalogue.rateEconomy)
        }
    }

    /**
     * ТЗ §14: на экране остаются только короткие подписи, а полные
     * формулировки живут в справке. Проверяется и то, и другое: подпись
     * коротка, а сама формулировка никуда не делась.
     */
    @Test
    fun `the screen labels are short and the full wording still exists`() {
        for (catalogue in catalogues) {
            assertTrue(catalogue.offlineTag.length <= 24, catalogue.offlineTag)
            assertTrue(catalogue.axisLog.length <= 4, catalogue.axisLog)
            assertTrue(catalogue.axisLinear.length <= 4, catalogue.axisLinear)
            // Полная фраза остаётся: она переехала уровнем ниже, а не исчезла.
            assertTrue(catalogue.offlineHistory.length > catalogue.offlineTag.length)
        }
    }

    /** Отказ сливать через пропуск — часть смысла, а не деталь реализации. */
    @Test
    fun `thinning wording refuses to merge across a gap`() {
        assertTrue(SpectrogramRu.rateThinning(7, 5).contains("Через пропуск"))
        assertTrue(SpectrogramEn.rateThinning(7, 5).contains("never merged across a gap"))
    }
}
