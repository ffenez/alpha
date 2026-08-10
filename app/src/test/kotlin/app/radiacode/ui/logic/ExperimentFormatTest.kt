package app.radiacode.ui.logic

import app.radiacode.analysis.AbAnalysis
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.EnergyWindowSpec
import app.radiacode.analysis.EnergyWindows
import app.radiacode.data.db.ExperimentEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Wording of the A/B screen. The verdict vocabulary of spec §8 is closed, and
 * a similarity percentage must not exist anywhere — those two facts are what
 * this file guards.
 */
class ExperimentFormatTest {

    @Test
    fun `verdict tokens are exactly the vocabulary of the specification`() {
        assertEquals("consistent", ExperimentFormat.verdictToken(AbAnalysis.Verdict.CONSISTENT))
        assertEquals("changed", ExperimentFormat.verdictToken(AbAnalysis.Verdict.CHANGED))
        assertEquals(
            "strong evidence of change",
            ExperimentFormat.verdictToken(AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE),
        )
        assertEquals(3, AbAnalysis.Verdict.entries.size)
    }

    @Test
    fun `no wording promises a similarity percentage or danger`() {
        val texts = AbAnalysis.Verdict.entries.flatMap {
            listOf(
                ExperimentFormat.verdictLabel(it),
                ExperimentFormat.verdictHeadline(it, "A", "B"),
                ExperimentFormat.verdictToken(it),
            )
        } + listOf(
            ExperimentFormat.EXPERIMENTAL_NOTE,
            ExperimentFormat.DISTANCE_WARNING,
            ExperimentFormat.SHIELDING_WARNING,
            ExperimentFormat.INDEX_NOTE,
            ExperimentFormat.GEOMETRY_PROMPT,
        ) + ExperimentEntity.KINDS.map { ExperimentFormat.kindHint(it) }

        for (text in texts) {
            assertFalse(text.contains("%"), "similarity percentages are forbidden: $text")
            assertFalse(text.contains("похожест"), text)
            assertFalse(text.contains("обнаружен"), text)
            // «опасность» may appear only as a denial, never as a claim.
            if (text.contains("опасн")) {
                assertTrue(
                    text.contains("не об опасности") || text.contains("не мера опасности"),
                    "danger may only be denied, never claimed: $text",
                )
            }
        }
    }

    @Test
    fun `headline names both runs and never claims what was found`() {
        val headline = ExperimentFormat.verdictHeadline(
            AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE,
            "A",
            "B",
        )
        assertTrue(headline.contains("A") && headline.contains("B"))
        assertTrue(headline.contains("различия") || headline.contains("различие"))
    }

    @Test
    fun `method labels state which statistic was used`() {
        assertTrue(
            ExperimentFormat.methodLabel(AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO)
                .contains("правдоподоб"),
        )
        assertTrue(ExperimentFormat.methodLabel(AbAnalysis.Method.CHI_SQUARE).contains("χ²"))
        assertEquals("LR", ExperimentFormat.methodShort(AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO))
        assertEquals("χ²", ExperimentFormat.methodShort(AbAnalysis.Method.CHI_SQUARE))
        // The explanation quotes the documented switch threshold.
        assertTrue(
            ExperimentFormat.methodExplanation(AbAnalysis.Method.CHI_SQUARE)
                .contains(AbAnalysis.NORMAL_APPROX_MIN_COUNTS.toInt().toString()),
        )
    }

    @Test
    fun `kinds cover every stored scenario`() {
        ExperimentEntity.KINDS.forEach { kind ->
            assertTrue(ExperimentFormat.kindLabel(kind).isNotBlank())
            assertTrue(ExperimentFormat.kindHint(kind).isNotBlank())
            assertTrue(ExperimentFormat.runRoleLabel(kind, 0).isNotBlank())
            assertTrue(ExperimentFormat.runRoleLabel(kind, 1).isNotBlank())
        }
        assertEquals("объект", ExperimentFormat.runRoleLabel(ExperimentEntity.KIND_BACKGROUND_VS_OBJECT, 0))
        assertEquals("фон", ExperimentFormat.runRoleLabel(ExperimentEntity.KIND_BACKGROUND_VS_OBJECT, 1))
        assertEquals("без материала", ExperimentFormat.runRoleLabel(ExperimentEntity.KIND_SHIELDING, 0))
        assertEquals("с материалом", ExperimentFormat.runRoleLabel(ExperimentEntity.KIND_SHIELDING, 1))
    }

    @Test
    fun `run letters go A B C`() {
        assertEquals("A", ExperimentFormat.runLetter(0))
        assertEquals("B", ExperimentFormat.runLetter(1))
        assertEquals("C", ExperimentFormat.runLetter(2))
        assertEquals("R27", ExperimentFormat.runLetter(26))
    }

    @Test
    fun `distance warning states geometry, scattering and background`() {
        val warning = ExperimentFormat.DISTANCE_WARNING
        assertTrue(warning.contains("1/r²"))
        assertTrue(warning.contains("точечный"))
        assertTrue(warning.contains("рассеива"))
        assertTrue(warning.contains("фон"))
    }

    @Test
    fun `shielding warning refuses universal attenuation coefficients`() {
        assertTrue(ExperimentFormat.SHIELDING_WARNING.contains("ослаблен"))
        assertTrue(ExperimentFormat.SHIELDING_WARNING.contains("не выводятся"))
    }

    @Test
    fun `the spectral index note says it is not a measure of danger`() {
        assertTrue(ExperimentFormat.INDEX_NOTE.contains("не мера опасности"))
        assertTrue(ExperimentFormat.INDEX_NOTE.contains("параметр анализа"))
    }

    @Test
    fun `numbers are formatted with a decimal comma and a sign where it matters`() {
        assertEquals("12,35", ExperimentFormat.decimal(12.3456))
        assertEquals("0,0012", ExperimentFormat.decimal(0.00123))
        assertEquals("+1,20 имп/с", ExperimentFormat.signedCps(1.2))
        assertEquals("−1,20 имп/с", ExperimentFormat.signedCps(-1.2))
        assertEquals("+5,3σ", ExperimentFormat.zLabel(5.29))
        assertEquals("−5,3σ", ExperimentFormat.zLabel(-5.29))
    }

    @Test
    fun `durations and distances read naturally`() {
        assertEquals("45 с", ExperimentFormat.duration(45))
        assertEquals("5 мин", ExperimentFormat.duration(300))
        assertEquals("5 мин 30 с", ExperimentFormat.duration(330))
        assertEquals("10 см", ExperimentFormat.distance(10f))
        assertEquals("1,50 м", ExperimentFormat.distance(150f))
    }

    @Test
    fun `window cells report rate with sigma and share`() {
        val counts = List(2000) { if (it in 100..299) 5 else 0 }
        val window = EnergyWindows.window(
            counts,
            100,
            EnergyCalibration(0f, 1f, 0f),
            EnergyWindowSpec(100f, 300f),
        )
        assertEquals("100–300", ExperimentFormat.windowLabel(window.spec))
        assertTrue(ExperimentFormat.windowRate(window).contains("±"))
        assertEquals("100%", ExperimentFormat.windowShare(window))

        val index = assertNotNull(
            EnergyWindows.spectralIndex(window, window.copy(counts = 500L)),
        )
        assertTrue(ExperimentFormat.indexLabel(index).contains("±"))
        assertEquals("R(100–300) / R(100–300)", ExperimentFormat.indexCaption(index))
    }
}
