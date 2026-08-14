package app.radiacode.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
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

    /**
     * Экран радона обязан отказаться от концентрации ТАМ, ГДЕ ЧЕЛОВЕК ВИДИТ
     * результат, — то есть в самой карточке вывода, а не в подписи внизу
     * страницы, до которой ещё надо долистать.
     */
    @Test
    fun `radon stays a relative indicator in every language`() {
        for (catalogue in catalogues) {
            val limit = catalogue.radonLimit.lowercase()
            assertTrue(
                limit.contains("не измерение концентрации") ||
                    limit.contains("not a measurement of concentration"),
                "радон обязан отказаться от концентрации: $limit",
            )
            // Единица допустима ровно внутри отрицания и нигде больше.
            val allowed = setOf(limit, catalogue.ventilationCheck.lowercase())
            for (text in catalogue.allTexts()) {
                val lower = text.lowercase()
                if (lower in allowed) continue
                assertTrue(
                    !lower.contains("бк/м") && !lower.contains("bq/m"),
                    "беккерели вне отрицания: $text",
                )
            }
        }
    }

    /**
     * Главный ответ обоих экранов — КАТЕГОРИЯ, а не остаток вычитания.
     * `−0,29` в этой роли не читается ни как «мало», ни как «ничего нет»,
     * хотя означает именно второе.
     */
    @Test
    fun `the headline verdicts are words, not numbers`() {
        for (catalogue in catalogues) {
            val radon = listOf(
                catalogue.radonResultNotable,
                catalogue.radonResultPlain,
                catalogue.radonResultNoData,
            )
            val line = listOf(
                catalogue.lineResultExcess,
                catalogue.lineResultPlain,
                catalogue.lineResultNoData,
            )
            for (verdict in radon + line) {
                assertTrue(verdict.none { it.isDigit() }, "в выводе есть число: $verdict")
                assertTrue(!verdict.contains("σ") && !verdict.contains("−"), verdict)
            }
            // Три исхода ОДНОГО экрана различимы: одинаковая строка на два
            // состояния — это «экран не знает, что сказать». Между экранами
            // совпадение допустимо: «данных пока мало» — один и тот же ответ.
            assertEquals(3, radon.toSet().size, "$radon")
            assertEquals(3, line.toSet().size, "$line")
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
