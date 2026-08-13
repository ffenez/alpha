package app.radiacode.ui.text

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Деталка сессии и Радон говорят о наблюдении, а не о вреде: ни один язык
 * области не имеет права пообещать «безопасно», и радон ни в одном языке не
 * становится концентрацией в Бк/м³.
 */
class SessionRadonStringsTest {

    private val catalogues = SessionRadonCatalogue.all

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
                        "«${word.pattern}»: $text",
                    )
                }
            }
        }
    }

    @Test
    fun `every string is filled and the two languages differ`() {
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                assertTrue(text.isNotBlank(), "пустая строка каталога")
            }
        }
        assertTrue(SessionRadonRu.radonTag != SessionRadonEn.radonTag)
        assertTrue(SessionRadonRu.chartLineNote != SessionRadonEn.chartLineNote)
    }

    @Test
    fun `radon stays a relative indicator in every language`() {
        for (catalogue in catalogues) {
            // Единица допустима ровно один раз — внутри отрицания; отдельного
            // упоминания «концентрации» как показанной величины быть не может.
            val caveat = catalogue.radonCaveat.lowercase()
            assertTrue(
                caveat.contains("не концентрация") || caveat.contains("not a radon concentration"),
                "радон обязан отказаться от концентрации: $caveat",
            )
            for (text in catalogue.allTexts()) {
                val lower = text.lowercase()
                if (lower == caveat) continue
                assertTrue(
                    !lower.contains("бк/м") && !lower.contains("bq/m"),
                    "беккерели вне отрицания: $text",
                )
            }
        }
    }

    @Test
    fun `a trend without direction is not called steady`() {
        for (catalogue in catalogues) {
            val flat = catalogue.trendFlat.lowercase()
            assertTrue(
                !flat.contains("стабильн") && !flat.contains("steady") &&
                    !flat.contains("stable") && !flat.contains("постоянн"),
                "правило не доказывает постоянство: $flat",
            )
        }
    }
}
