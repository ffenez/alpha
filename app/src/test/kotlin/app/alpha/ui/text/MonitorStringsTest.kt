package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Главная и «Почему такой вывод» — ядро научной честности приложения, поэтому
 * перевод переносит запреты, а не только слова: зелёный статус означает
 * «внутри исторического диапазона профиля», а не «безопасно», и ни один язык
 * не имеет права сказать иначе.
 */
class MonitorStringsTest {

    private val catalogues = MonitorCatalogue.all

    @Test
    fun `nothing in either language promises safety`() {
        // Целыми словами, а не подстроками: спецификация сама требует фраз
        // «не мера опасности» и «не оценка опасности» — это ОТКАЗ от
        // утверждения о вреде, а не утверждение. Тот же приём, что в
        // `WhyReportTest`.
        val forbidden = listOf(
            Regex("""\bбезопасн\w*\b"""),
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
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word» in: $text")
                }
            }
        }
    }

    @Test
    fun `the engine's internal names never reach the English screen`() {
        // «baseline» — имя движка, а не слово на экране; «learning» и «model»
        // запрещены ровно так же, как «обучение» и «учится» по-русски.
        val forbidden = listOf(
            Regex("""\bbaseline\b"""),
            Regex("""\blearning\b"""),
            Regex("""\bmodel is\b"""),
            Regex("""\bbeing trained\b"""),
        )
        for (text in MonitorEn.allTexts()) {
            for (word in forbidden) {
                assertTrue(!word.containsMatchIn(text.lowercase()), "«$word» in: $text")
            }
        }
    }

    @Test
    fun `hardness stays a vendor coefficient, never an average energy`() {
        // Числитель — дозиметрическая оценка через энергетическую
        // характеристику прибора, а не энергия в кристалле.
        for (catalogue in catalogues) {
            val texts = listOf(
                catalogue.hardnessExplanation,
                catalogue.hardnessPurpose,
                catalogue.hardnessSigmaCaveat,
            )
            for (text in texts) {
                val lower = text.lowercase()
                assertTrue(
                    !Regex("(?<!не )средняя энергия").containsMatchIn(lower),
                    "«средняя энергия» claimed in: $text",
                )
                assertTrue(
                    !Regex("(?<!not )the mean photon energy").containsMatchIn(lower),
                    "mean energy claimed in: $text",
                )
                assertTrue(!lower.contains("average energy"), "average energy in: $text")
                assertTrue(!lower.contains("ортогональ"), "orthogonality claimed: $text")
                assertTrue(!lower.contains("orthogonal"), "orthogonality claimed: $text")
                assertTrue(!lower.contains("консерватив"), "sigma called conservative: $text")
                assertTrue(!lower.contains("conservative"), "sigma called conservative: $text")
            }
        }
    }

    @Test
    fun `descriptive statements stay descriptive`() {
        // Сравнение порядковых статистик не имеет права выглядеть как
        // проверка гипотезы: ни σ, ни p-value, ни процентов уверенности, ни
        // слова «значимо».
        val forbidden = listOf(
            Regex("""σ"""),
            Regex("""p-value|p‑value|\bp <|\bp ="""),
            Regex("""значим"""),
            Regex("""\bsignificant\w*\b"""),
            Regex("""\bconfidence\b"""),
            Regex("""%"""),
        )
        for (catalogue in catalogues) {
            val texts = listOf(
                catalogue.deviationUsual, catalogue.deviationNotEnough,
                catalogue.deviationAboveBand, catalogue.deviationBelowBand,
                catalogue.deviationShiftedUp, catalogue.deviationShiftedDown,
                catalogue.deviationSpreadWider, catalogue.deviationShortSpike,
            )
            for (text in texts) {
                for (word in forbidden) {
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word» in: $text")
                }
            }
        }
    }
}
