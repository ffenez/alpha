package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Продукты — то самое место, где проще всего пообещать лишнее: человек
 * приходит с вопросом «это можно есть», а прибор отвечает на другой вопрос.
 * Эти проверки держат границу.
 */
class FoodStringsTest {

    private val catalogues = FoodCatalogue.all

    @Test
    fun `no wording ever calls a product safe or dangerous`() {
        // Формы слов, а не подстроки: «чисто бета-излучающие» — это не
        // утверждение о чистоте продукта, и запрет обязан их различать.
        val forbidden = listOf(
            Regex("\\bбезопасн\\w*"),
            Regex("\\bопасн\\w*"),
            Regex("\\bчист(ый|ая|ое|ые|ого|ота)\\b"),
            Regex("\\bнорм(а|е|у|альн\\w*)\\b"),
            Regex("\\bдопустим\\w*"),
            Regex("\\bвредн\\w*"),
            Regex("\\bsafe\\b"),
            Regex("\\bdangerous\\b"),
            Regex("\\bharmless\\b"),
            Regex("\\bclean\\b"),
            Regex("\\bnormal\\b"),
        )
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                assertTrue(text.isNotBlank())
                for (word in forbidden) {
                    assertTrue(
                        !word.containsMatchIn(text.lowercase()),
                        "«${word.pattern}» в «$text»",
                    )
                }
            }
        }
    }

    /**
     * Беккерели без валидированной эффективностной калибровки — псевдоточность
     * (IAEA TRS-295, EPA MARLAP гл. 15). Ни в одной формулировке их быть не
     * может, даже в справке.
     */
    @Test
    fun `no becquerels are ever promised`() {
        val units = listOf("бк/кг", "бк /кг", "bq/kg", "беккерел", "becquerel")
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                for (unit in units) {
                    // Единственное разрешённое упоминание — отказ их считать.
                    val mentions = text.lowercase().contains(unit)
                    val refuses = text.lowercase().let {
                        it.contains("не считает") || it.contains("does not compute")
                    }
                    assertTrue(!mentions || refuses, "«$unit» обещано в «$text»")
                }
            }
        }
    }

    /** «Обнаружен» не пишется нигде в приложении — и здесь тоже. */
    @Test
    fun `nothing is ever declared detected`() {
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                assertTrue(!text.lowercase().contains("обнаруж"), text)
            }
        }
    }

    /**
     * Вывод о линии не называет нуклид: совпадение по энергии это гипотеза, и
     * решение принимает движок доказательств, а не экран продукта.
     */
    @Test
    fun `a line is never turned into a nuclide`() {
        for (catalogue in catalogues) {
            val body = catalogue.verdictLineBody("662 кэВ")
            assertTrue(!body.contains("Cs-137"), body)
            assertTrue(!body.contains("цези"), body)
        }
    }

    /**
     * Справка объясняет не «как нажимать», а что портит измерение: место,
     * положение прибора, ёмкость, подготовку, время и границы метода.
     */
    @Test
    fun `the guide covers what actually spoils a measurement`() {
        for (catalogue in catalogues) {
            val guide = catalogue.guide()
            assertTrue(guide.size >= 8, "разделов справки ${guide.size}")
            assertTrue(guide.all { it.first.isNotBlank() && it.second.isNotBlank() })
            val whole = guide.joinToString(" ") { it.second }.lowercase()
            // Границы метода названы прямо, а не подразумеваются.
            val betaNamed = whole.contains("бета") || whole.contains("beta")
            assertTrue(betaNamed, "чисто бета-излучающие нуклиды не названы")
        }
        // Природный калий назван: иначе настоящая линия K-40 в кураге читается
        // как загрязнение.
        assertTrue(FoodRu.guide().any { it.second.contains("1461") })
        assertTrue(FoodEn.guide().any { it.second.contains("1461") })
    }

    @Test
    fun `both languages carry the same set of strings`() {
        assertEquals(FoodRu.allTexts().size, FoodEn.allTexts().size)
    }
}
