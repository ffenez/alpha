package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Перевод области «Карта» переносит ПРАВИЛО, а не слова: английский каталог
 * подчиняется тем же запретам, что русский. Карта показывает медиану клетки и
 * период записей — это описание выборки, и ни на одном языке она не имеет
 * права превратиться в утверждение об уровне.
 */
class MapStringsTest {

    private val catalogues = MapCatalogue.all

    @Test
    fun `every catalogue fills every string`() {
        for (catalogue in catalogues) {
            val texts = catalogue.allTexts()
            assertTrue(texts.isNotEmpty())
            for (text in texts) {
                assertTrue(text.isNotBlank(), "пустая строка в каталоге карты")
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

    @Test
    fun `the cell names its statistic, not a level`() {
        // Клетка красится МЕДИАНОЙ, и оба языка называют её вслух — иначе цвет
        // читался бы как «уровень здесь».
        for (catalogue in catalogues) {
            assertTrue(catalogue.medianValue("0,12").contains(catalogue.median))
            assertTrue(catalogue.cellSpread("1", "2", "3", "4").contains("P10–P90"))
        }
    }

    @Test
    fun `honesty of the sample survives translation`() {
        // Три оговорки, ради которых экран существует: сколько точек реально
        // вошло в картинку, какие фиксы исключены и почему клетка бледная.
        assertTrue(MapEn.builtFromPoints("50 000").contains("50 000"))
        assertTrue(MapEn.onlyAccurateFixes(50).contains("50"))
        assertTrue(MapEn.paleCells(3, 5).contains("3") && MapEn.paleCells(3, 5).contains("5"))
        assertTrue(MapRu.builtFromPoints("50 000").contains("50 000"))
        assertTrue(MapRu.onlyAccurateFixes(50).contains("50"))
    }

    @Test
    fun `catalogues differ where they must`() {
        assertTrue(MapRu.scopeAll != MapEn.scopeAll)
        assertTrue(MapRu.tilesLoading != MapEn.tilesLoading)
        assertEquals(MapRu, MapCatalogue.of(AppLanguage.RU))
        assertEquals(MapEn, MapCatalogue.of(AppLanguage.EN))
        // Незнакомый язык каталога области решается общим правилом языка.
        assertEquals(MapRu, MapCatalogue.of(AppLanguage.SYSTEM))
    }
}
