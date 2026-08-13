package app.radiacode.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Список обновлений виден пользователю, поэтому правила русского каталога
 * действуют и на английский: никаких обещаний безопасности и никаких
 * внутренних имён, по которым человеку ничего не понять.
 */
class ReleaseStringsTest {

    private val catalogues = ReleaseCatalogue.all

    @Test
    fun `every catalogue fills every string`() {
        for (catalogue in catalogues) {
            val texts = catalogue.allTexts()
            assertTrue(texts.isNotEmpty())
            for (text in texts) {
                assertTrue(text.isNotBlank(), "пустая строка в списке обновлений")
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
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word»: $text")
                }
            }
        }
    }

    @Test
    fun `no internal names leak into what the user reads`() {
        // То же правило, что у русского списка: запись объясняет, что
        // изменилось на экране, а не как это устроено внутри.
        val internal = listOf(
            "baseline", "snapshot", "repository", "dao", "compose", "sql",
            "buildframe", "chartmetric", "datastore",
        )
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                for (name in internal) {
                    assertTrue(!text.lowercase().contains(name), "«$name»: $text")
                }
            }
        }
    }

    @Test
    fun `the refusal to claim a match survives translation`() {
        // Две записи существуют ради отказа утверждать совпадение. По-английски
        // они обязаны отказываться так же, иначе перевод усилил бы вывод.
        assertTrue(ReleaseEn.v010Lines.any { it.contains("does not prove a match") })
        assertTrue(ReleaseEn.v005Lines.any { it.contains("does not prove") })
        assertTrue(ReleaseEn.v005Lines.any { it.contains("does not name the cause") })
    }

    @Test
    fun `catalogues differ where they must`() {
        assertTrue(ReleaseRu.v010Title != ReleaseEn.v010Title)
        assertEquals(ReleaseRu, ReleaseCatalogue.of(AppLanguage.RU))
        assertEquals(ReleaseEn, ReleaseCatalogue.of(AppLanguage.EN))
        // Незнакомый язык каталога области решается общим правилом языка.
        assertEquals(ReleaseRu, ReleaseCatalogue.of(AppLanguage.SYSTEM))
        // Каждая запись каталога заполнена: столько же строк, сколько в русском.
        assertEquals(ReleaseRu.allTexts().size, ReleaseEn.allTexts().size)
    }
}
