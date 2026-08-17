package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Перевод области — перенос ПРАВИЛА, а не подстановка слов: английский каталог
 * обязан отказываться ровно там же, где отказывается русский.
 */
class SpectrumStringsTest {

    private val catalogues = SpectrumCatalogue.all

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
            // «опасность» разрешена только как ОТКАЗ («индекс — не мера
            // опасности», спец §7); утверждающие формы запрещены, и это
            // отдельно проверяет тест ниже.
            Regex("""\bопасн(о|ый|ая|ое)\b"""),
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
    fun `hazard is only ever denied`() {
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                val lower = text.lowercase()
                if (lower.contains("опасн")) {
                    assertTrue(lower.contains("не мера опасности"), text)
                }
                if (lower.contains("hazard")) {
                    assertTrue(lower.contains("not a measure of hazard"), text)
                }
            }
        }
    }

    @Test
    fun `a line match is never called a detection`() {
        // Совпадение энергии — гипотеза: ни один язык не имеет права назвать
        // его обнаружением нуклида, и шкала уверенности не идёт выше средней.
        val claimsDetection = listOf(
            Regex("""обнаруж"""),
            Regex("""выявлен"""),
            // «detector» — это железо, а не вывод: запрещён именно глагол.
            Regex("""\bdetect(s|ed|ing|ion)?\b"""),
            Regex("""\bidentified\b"""),
            Regex("""\bconfirmed\b"""),
            Regex("""\bhigh confidence\b"""),
        )
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                for (word in claimsDetection) {
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word»: $text")
                }
            }
        }
    }

    @Test
    fun `catalogues are actually translated`() {
        assertTrue(SpectrumRu.importedTitle != SpectrumEn.importedTitle)
        assertTrue(SpectrumRu.edgeExplanation != SpectrumEn.edgeExplanation)
        assertEquals(SpectrumRu, SpectrumCatalogue.of(AppLanguage.RU))
        assertEquals(SpectrumEn, SpectrumCatalogue.of(AppLanguage.EN))
        // Единицы переезжают вместе с языком, обозначения статистики — нет.
        assertEquals("keV", SpectrumEn.unitKeV)
        // Пин обновлён вместе с шапкой таблицы: колонка называет ВЕЛИЧИНУ и её
        // неопределённость — σ_R = √C/t, стандартная неопределённость скорости
        // счёта. Прежнее «±Σ» на экране было следствием перевода шапки в
        // верхний регистр (σ → Σ) и читалось как знак суммы.
        assertEquals("counts/s ± σ", SpectrumEn.columnRate)
        assertEquals("с⁻¹ ± σ", SpectrumRu.columnRate)
        assertTrue(SpectrumEn.shapeChiSquare(18, "4,1").startsWith("χ²"))
    }

    @Test
    fun `the spectral ratio is described, never presented as a measure of harm`() {
        assertTrue(SpectrumRu.indexNote.contains("не мера опасности"), SpectrumRu.indexNote)
        assertTrue(
            SpectrumRu.indexNote.contains("не дозиметрическая величина"),
            SpectrumRu.indexNote,
        )
        assertTrue(
            SpectrumEn.indexNote.contains("not a measure of harm"),
            SpectrumEn.indexNote,
        )
        assertTrue(
            SpectrumEn.indexNote.contains("not a dosimetric quantity"),
            SpectrumEn.indexNote,
        )
        // Величина названа отношением, а не самостоятельной характеристикой.
        assertEquals("Спектральное отношение", SpectrumRu.ratioTitle)
        assertEquals("Spectral ratio", SpectrumEn.ratioTitle)
    }

    @Test
    fun `hardness and the spectral ratio are never the same word`() {
        // Жёсткость — коэффициент прибора Ḋ/R, спектральное отношение — два
        // участка спектра. Слово «жёсткость» может встречаться на этом экране
        // ТОЛЬКО там, где сказано, что это разные величины.
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                val lower = text.lowercase()
                if (lower.contains("жёсткост")) {
                    assertTrue(lower.contains("это не «жёсткость»"), text)
                }
                if (lower.contains("hardness")) {
                    assertTrue(lower.contains("this is not «hardness»"), text)
                }
            }
        }
        assertTrue(SpectrumRu.ratioNotHardness.contains("Ḋ/R"))
        assertTrue(SpectrumEn.ratioNotHardness.contains("Ḋ/R"))
    }

    @Test
    fun `english counts the channels of the instrument`() {
        assertEquals("1 channel", SpectrumEn.channels(1))
        assertEquals("1024 channels", SpectrumEn.channels(1024))
        assertEquals("1024 канала", SpectrumRu.channels(1024))
        assertEquals("1 канал", SpectrumRu.channels(1))
        assertEquals("11 каналов", SpectrumRu.channels(11))
        assertEquals("22 канала", SpectrumRu.channels(22))
        assertEquals("25 каналов", SpectrumRu.channels(25))
    }
}
