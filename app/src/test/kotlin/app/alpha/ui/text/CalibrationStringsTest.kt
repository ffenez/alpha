package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Перевод переносит ПРАВИЛО, а не слова: там, где русский отказывается
 * обещать безопасность, называть модель калибровкой прибора или распространять
 * фоновый отклик на точечный источник, английский обязан отказываться так же.
 */
class CalibrationStringsTest {

    private val catalogues = CalibrationCatalogue.all

    @Test
    fun `nothing in either language promises safety`() {
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
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word» в: $text")
                }
            }
        }
    }

    @Test
    fun `accepting the model is not called calibrating the instrument`() {
        // Калибровку прибора приложение не меняет; принимается НАША модель его
        // разрешения, и это сказано словами, а не подразумевается.
        assertTrue(CalibrationRu.acceptedNote.contains("Калибровку самого прибора это не"))
        assertTrue(CalibrationRu.acceptedNote.contains("модель его разрешения"))
        assertTrue(CalibrationEn.acceptedNote.contains("does not change the instrument"))
        assertTrue(CalibrationEn.acceptedNote.contains("model of its resolution"))

        val claimsCalibrated = Regex("""прибор откалибр|instrument is calibrated|calibrates the""")
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                assertTrue(!claimsCalibrated.containsMatchIn(text.lowercase()), text)
            }
        }
    }

    @Test
    fun `the extrapolated region is called extrapolated in both languages`() {
        assertTrue(CalibrationRu.extrapolatedBelow("1120").contains("экстраполяция"))
        assertTrue(CalibrationEn.extrapolatedBelow("1120").contains("extrapolated"))
        assertTrue(CalibrationRu.extrapolationNote.contains("не измерена, а продолжена"))
        assertTrue(CalibrationEn.extrapolationNote.contains("not measured but"))
    }

    @Test
    fun `the response keeps both halves of its limitation`() {
        assertTrue(CalibrationRu.responseCaveat.contains("РАСПРЕДЕЛЁННЫЙ"))
        assertTrue(CalibrationRu.responseCaveat.contains("неприменим"))
        assertTrue(CalibrationRu.responsePointGeometry.contains("отказом"))
        assertTrue(CalibrationEn.responseCaveat.contains("DISTRIBUTED"))
        assertTrue(CalibrationEn.responseCaveat.contains("does not apply"))
        assertTrue(CalibrationEn.responsePointGeometry.contains("refusal"))
    }

    @Test
    fun `the scale is never said to be corrected`() {
        assertTrue(CalibrationRu.noCorrection.contains("не правится"))
        assertTrue(CalibrationEn.noCorrection.contains("never corrected"))
    }

    @Test
    fun `the language picks the catalogue and the two really differ`() {
        assertEquals(CalibrationRu, CalibrationCatalogue.of(AppLanguage.RU))
        assertEquals(CalibrationEn, CalibrationCatalogue.of(AppLanguage.EN))
        assertEquals(CalibrationRu, CalibrationCatalogue.of(AppLanguage.SYSTEM))
        assertEquals(CalibrationRu.allTexts().size, CalibrationEn.allTexts().size)
        val shared = CalibrationRu.allTexts().filter { it in CalibrationEn.allTexts() }
        // Совпадать могут только обозначения, а не формулировки.
        assertTrue(shared.all { it.length <= 3 }, "не переведено: $shared")
    }
}
