package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Поиск отвечает на ОДИН вопрос — выше ли счёт записанного фона, — и его
 * отказы обязаны пережить перевод. Тест проверяет то же, что `SearchVerdictTest`
 * проверяет у русского движка: обещаний безопасности нет ни на одном языке, а
 * непринятое различие нигде не становится «повышением».
 */
class SearchStringsTest {

    private val catalogues = SearchCatalogue.all

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
            // «Нормальное приближение не используется» и «нормальное
            // распределение» — названия статистики, а не оценка обстановки:
            // тот же список исключений, что у `SearchVerdictTest`.
            Regex("""\bнормальн\w*\b(?! (распределени|приближени))"""),
            Regex("""\bnormal\b(?! (distribution|approximation))"""),
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
     * Отказы, ради которых область существует. «Не оценивается» не имеет права
     * стать «изменений нет», а названный метод интервала — исчезнуть: без него
     * пара границ это просто два числа.
     */
    @Test
    fun `the scientific refusals survive the translation`() {
        assertTrue(SearchEn.valueShapeNotEvaluated == "not evaluated")
        assertTrue(SearchRu.valueShapeNotEvaluated == "не оценивается")
        assertTrue(SearchEn.shapeNote.contains("count rate only"))
        assertTrue(SearchEn.ratioNote("×1,8").contains("normal approximation is not used"))
        assertTrue(SearchRu.ratioNote("×1,8").contains("нормальное приближение не используется"))
        assertTrue(SearchEn.ratioNote("×1,8").contains("Clopper–Pearson"))
        // Процент печатается только вместе со знаменателем.
        assertTrue(SearchEn.differenceNote("0,05", "+12 %").contains("of the recorded background"))
        assertTrue(SearchRu.differenceNote("0,05", "+12 %").contains("к записанному фону"))
        // Короткий всплеск — маркер, а не находка.
        assertTrue(SearchEn.spikes(2, "×4,2").contains("not confirmed by duration"))
        assertTrue(SearchRu.title != SearchEn.title)
    }

    /**
     * Отдельно от списка запрещённых слов: английский не имеет права сказать,
     * что счёт «равен фону» или «на уровне фона» — тест проверял РАЗЛИЧИЕ, а
     * непринятие различия не доказывает совпадение.
     */
    @Test
    fun `english never states equality with the background`() {
        val equality = listOf(
            "at background level",
            "matches the background",
            "equal to the background",
            "same as the background",
            "background level",
        )
        for (text in SearchEn.allTexts()) {
            for (phrase in equality) {
                assertTrue(!text.lowercase().contains(phrase), "«$phrase»: $text")
            }
        }
    }
}
