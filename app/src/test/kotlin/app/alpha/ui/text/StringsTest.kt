package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Перевод — перенос ПРАВИЛА, а не подстановка слов.
 *
 * Каталог другого языка обязан подчиняться тем же запретам, что и русский:
 * приложение не имеет права сказать «safe» ровно по той же причине, по
 * которой не говорит «безопасно».
 */
class StringsTest {

    private val catalogues = listOf(RuStrings, EnStrings)

    private fun allText(strings: Strings): List<String> = strings.allTexts()

    @Test
    fun `every catalogue fills every string`() {
        for (catalogue in catalogues) {
            val texts = allText(catalogue)
            assertTrue(texts.isNotEmpty(), catalogue.language.name)
            for (text in texts) {
                assertTrue(text.isNotBlank(), "${catalogue.language}: пустая строка")
            }
        }
    }

    @Test
    fun `no catalogue may promise safety`() {
        val forbidden = listOf(
            Regex("""\bбезопасн\w*\b"""),
            Regex("""\bопасн\w*\b"""),
            Regex("""\bнорма\b"""),
            Regex("""\bsafe\b"""),
            Regex("""\bdangerous\b"""),
            Regex("""\bnormal\b(?! distribution)"""),
            Regex("""\bharmless\b"""),
        )
        for (catalogue in catalogues) {
            for (text in allText(catalogue)) {
                for (word in forbidden) {
                    assertTrue(
                        !word.containsMatchIn(text.lowercase()),
                        "«$word» in ${catalogue.language}: $text",
                    )
                }
            }
        }
    }

    @Test
    fun `the language of the phone decides only when the setting says system`() {
        assertEquals(AppLanguage.RU, AppLanguage.resolve(AppLanguage.SYSTEM, "ru"))
        assertEquals(AppLanguage.RU, AppLanguage.resolve(AppLanguage.SYSTEM, "ru-RU"))
        // Незнакомый язык телефона — английский: он читается большим числом
        // людей, и это единственный честный выбор для неизвестного случая.
        assertEquals(AppLanguage.EN, AppLanguage.resolve(AppLanguage.SYSTEM, "de"))
        assertEquals(AppLanguage.EN, AppLanguage.resolve(AppLanguage.SYSTEM, ""))
        // Явный выбор системе не подчиняется.
        assertEquals(AppLanguage.RU, AppLanguage.resolve(AppLanguage.RU, "en"))
        assertEquals(AppLanguage.EN, AppLanguage.resolve(AppLanguage.EN, "ru"))
    }

    @Test
    fun `an unknown id falls back to the phone's language, not to a guess`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.of(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.of("klingon"))
        assertEquals(AppLanguage.EN, AppLanguage.of("en"))
    }

    @Test
    fun `catalogues differ where they must and agree where they must not`() {
        // Разные языки — разные строки: копипаста каталога проверяется тем,
        // что хотя бы навигация действительно переведена.
        assertTrue(RuStrings.tabSearch != EnStrings.tabSearch)
        assertTrue(RuStrings.doseRate != EnStrings.doseRate)
        // Каталог знает свой язык.
        assertEquals(AppLanguage.RU, RuStrings.language)
        assertEquals(AppLanguage.EN, EnStrings.language)
        assertEquals(EnStrings, stringsFor(AppLanguage.EN))
        assertEquals(RuStrings, stringsFor(AppLanguage.RU))
    }
}
