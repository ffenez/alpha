package app.alpha.ui.text

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

    /**
     * Запись объясняет, что изменилось на экране, а не как это устроено внутри.
     *
     * Список разделён по языкам не ради послабления: `snapshot` и `baseline`
     * стоят на САМИХ английских экранах, и запрет на них в списке обновлений
     * заставил бы называть одно и то же разными словами. В русском тексте то же
     * латинское слово остаётся жаргоном, и там запрет действует.
     */
    @Test
    fun `no internal names leak into what the user reads`() {
        val internal = listOf(
            "repository", "dao", "compose", "sql",
            "buildframe", "chartmetric", "datastore",
        )
        val jargonInRussian = internal + listOf("baseline", "snapshot")
        // Слово целиком, а не подстрока: «decomposed» — обычное английское
        // слово, а не имя из кода.
        fun word(name: String) = Regex("""\b${Regex.escape(name)}\b""")
        for (text in ReleaseRu.allTexts()) {
            for (name in jargonInRussian) {
                assertTrue(!word(name).containsMatchIn(text.lowercase()), "«$name»: $text")
            }
        }
        for (text in ReleaseEn.allTexts()) {
            for (name in internal) {
                assertTrue(!word(name).containsMatchIn(text.lowercase()), "«$name»: $text")
            }
        }
    }

    /**
     * Отказ утверждать совпадение переживает и перевод, и сокращение.
     *
     * Записи сжались до одной-двух фраз, а из выжимки первой вылетает
     * оговорка. Здесь вылететь она не имеет права: запись описывает вывод,
     * который отказывается утверждать совпадение, и без оговорки описание
     * превращается в обещание.
     */
    @Test
    fun `the refusal to claim a match survives translation and shortening`() {
        assertTrue(
            ReleaseEn.v005Summary.contains("does not prove a match"),
            ReleaseEn.v005Summary,
        )
        assertTrue(
            ReleaseRu.v005Summary.contains("не доказывает совпадение"),
            ReleaseRu.v005Summary,
        )
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
