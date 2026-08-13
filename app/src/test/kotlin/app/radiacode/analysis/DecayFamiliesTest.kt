package app.radiacode.analysis

import app.radiacode.analysis.evidence.CalibrationDiagnostic
import app.radiacode.analysis.evidence.EvidenceClass
import app.radiacode.analysis.evidence.CalibrationVerdict
import app.radiacode.analysis.evidence.IntensityConsistency
import app.radiacode.analysis.evidence.NotEvaluatedReason
import app.radiacode.analysis.evidence.NuclideEvidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Родство кандидатов: Pb-214 рядом с Bi-214 в таблице читается как две
 * независимые находки, хотя это соседи по одному ряду распада и вместе они и
 * встречаются.
 */
class DecayFamiliesTest {

    private fun candidate(
        nuclide: String,
        chain: String?,
        classification: EvidenceClass = EvidenceClass.SUPPORTED,
    ) = NuclideEvidence(
        nuclide = nuclide,
        chain = chain,
        energyEvidence = emptyList(),
        lines = emptyList(),
        matchedLines = 1,
        expectedObservableLines = 1,
        missingExpectedLines = emptyList(),
        resolutionAmbiguities = emptyList(),
        intensityConsistency = IntensityConsistency.NotEvaluated(
            reason = NotEvaluatedReason.TOO_FEW_MATCHED_LINES,
            ratios = emptyList(),
        ),
        calibrationConsistency = CalibrationDiagnostic(
            residuals = emptyList(),
            shiftKeV = null,
            shiftUncertaintyKeV = null,
            slopePerKeV = null,
            verdict = CalibrationVerdict.NOT_EVALUATED,
        ),
        contradictions = emptyList(),
        classification = classification,
    )

    @Test
    fun `radon daughters are named by their own name`() {
        val families = DecayFamilies.of(
            listOf(
                candidate("Pb-214", "Ra-226"),
                candidate("Bi-214", "Ra-226"),
            ),
        )

        val family = families.single()
        assertTrue(family.radonProgeny)
        assertEquals(listOf("Bi-214", "Pb-214"), family.members)
    }

    @Test
    fun `a chain member outside the radon pair keeps the chain name`() {
        // Ra-226 сам по себе — не «дочерние продукты радона»: подменять ряд
        // именем его части значит утверждать больше, чем известно.
        val families = DecayFamilies.of(
            listOf(
                candidate("Pb-214", "Ra-226"),
                candidate("Ra-226", "Ra-226"),
            ),
        )

        assertTrue(!families.single().radonProgeny)
        assertEquals("Ra-226", families.single().chain)
    }

    @Test
    fun `one nuclide is not a family`() {
        // Иначе строка сообщала бы лишь то, что у нуклида есть ряд.
        assertTrue(DecayFamilies.of(listOf(candidate("Bi-214", "Ra-226"))).isEmpty())
    }

    @Test
    fun `a rejected candidate does not bring relatives`() {
        // Семейство из отвергнутых имён было бы утверждением о том, чего на
        // экране нет: `CONTRADICTED` в таблице показывается прочерком.
        val families = DecayFamilies.of(
            listOf(
                candidate("Pb-214", "Ra-226"),
                candidate("Bi-214", "Ra-226", EvidenceClass.CONTRADICTED),
            ),
        )

        assertTrue(families.isEmpty())
    }

    @Test
    fun `nuclides without a chain form nothing`() {
        // Cs-137 и Co-60 не родня друг другу ни в каком смысле.
        assertTrue(
            DecayFamilies.of(
                listOf(candidate("Cs-137", null), candidate("Co-60", null)),
            ).isEmpty(),
        )
    }

    @Test
    fun `two chains at once are reported separately and in a stable order`() {
        val families = DecayFamilies.of(
            listOf(
                candidate("Tl-208", "Th-232"),
                candidate("Bi-214", "Ra-226"),
                candidate("Pb-212", "Th-232"),
                candidate("Pb-214", "Ra-226"),
            ),
        )

        assertEquals(listOf("Ra-226", "Th-232"), families.map { it.chain })
        assertEquals(listOf("Bi-214", "Pb-214"), families[0].members)
        assertEquals(listOf("Pb-212", "Tl-208"), families[1].members)
    }
}
