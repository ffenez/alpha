package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Активность в беккерелях — самое сильное утверждение, которое приложение
 * умеет делать. Тексты обязаны нести его условия: геометрию, неизмеренные
 * линии и отказ показывать число без кривой.
 */
class EfficiencyStringsTest {

    private val catalogues = EfficiencyCatalogue.all

    @Test
    fun `ни один язык не обещает безопасность и не объявляет обнаружение`() {
        val forbidden = listOf(
            Regex("""\bбезопасн\w*\b"""),
            Regex("""\bопасн\w*\b"""),
            Regex("""\bдопустим\w*\b"""),
            Regex("""\bнорма\b"""),
            Regex("""\bобнаруж\w*\b"""),
            Regex("""\bsafe\b"""),
            Regex("""\bdangerous\b"""),
            Regex("""\bharmless\b"""),
            Regex("""\bnormal\b(?! distribution)"""),
        )
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                for (word in forbidden) {
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word» в: $text")
                }
            }
        }
    }

    @Test
    fun `геометрия названа условием в обоих языках`() {
        assertTrue(EfficiencyRu.geometryWarning.contains("в разы"))
        assertTrue(EfficiencyEn.geometryWarning.contains("in factors"))
        assertTrue(EfficiencyRu.activityGeometryNote.contains("геометрии калибровки"))
        assertTrue(EfficiencyEn.activityGeometryNote.contains("calibration geometry"))
    }

    @Test
    fun `отсутствие кривой объясняется отказом, а не недоделкой`() {
        assertTrue(EfficiencyRu.notCalibratedWhy.contains("активность не показывается"))
        assertTrue(EfficiencyRu.notCalibratedWhy.contains("произвольным"))
        assertTrue(EfficiencyEn.notCalibratedWhy.contains("activity is shown nowhere"))
        assertTrue(EfficiencyEn.notCalibratedWhy.contains("arbitrary"))
    }

    @Test
    fun `ненайденная линия названа неизмеренной, а не нулевой`() {
        assertTrue(EfficiencyRu.linesMissed("121,8").contains("не измерена"))
        assertTrue(EfficiencyEn.linesMissed("121.8").contains("not measured"))
    }

    @Test
    fun `каталоги одного размера и не делят формулировки`() {
        assertEquals(EfficiencyRu.allTexts().size, EfficiencyEn.allTexts().size)
        val shared = EfficiencyRu.allTexts().filter { it in EfficiencyEn.allTexts() }
        // Совпасть могут только обозначения, единицы и числовые шаблоны: в них
        // нет слов. Слово от четырёх букв в общей строке означает, что
        // формулировку не перевели.
        val word = Regex("""[\p{L}]{4,}""")
        for (text in shared) {
            assertTrue(!word.containsMatchIn(text), "не переведено: $text")
        }
    }

    @Test
    fun `каталог выбирается по языку`() {
        assertEquals(EfficiencyRu, EfficiencyCatalogue.of(AppLanguage.RU))
        assertEquals(EfficiencyEn, EfficiencyCatalogue.of(AppLanguage.EN))
    }
}
