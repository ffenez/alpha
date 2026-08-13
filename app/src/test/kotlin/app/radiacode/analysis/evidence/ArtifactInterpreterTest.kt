package app.radiacode.analysis.evidence

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtifactInterpreterTest {

    private fun explain(vararg energies: Double): List<PeakExplanation> =
        ArtifactInterpreter.explain(energies.map { peakAt(it) }, TEST_RESOLUTION)

    @Test
    fun `511 keV is an annihilation feature, not a nuclide`() {
        val found = explain(511.0).filterIsInstance<PeakExplanation.Annihilation>()
        assertEquals(1, found.size)
        assertTrue(abs(found.first().deltaKeV) < 1e-6)
    }

    @Test
    fun `escape peaks are only proposed below a strong high-energy parent`() {
        val explanations = explain(2614.5, 2103.5, 1592.5)
        val single = explanations.filterIsInstance<PeakExplanation.SingleEscape>()
        val double = explanations.filterIsInstance<PeakExplanation.DoubleEscape>()
        assertTrue(
            single.any { it.peak.centroidKeV == 2103.5 && it.parent.centroidKeV == 2614.5 },
            "E − 511 от 2614,5: $single",
        )
        assertTrue(
            double.any { it.peak.centroidKeV == 1592.5 && it.parent.centroidKeV == 2614.5 },
            "E − 1022 от 2614,5: $double",
        )
    }

    @Test
    fun `no escape interpretation below the pair production threshold`() {
        // 661,7 кэВ ниже порога рождения пар — вылета аннигиляционных фотонов
        // не бывает, и «пика вылета» на 150,7 кэВ быть не может.
        val explanations = explain(661.7, 150.7)
        assertTrue(explanations.none { it is PeakExplanation.SingleEscape })
        assertTrue(explanations.none { it is PeakExplanation.DoubleEscape })
    }

    @Test
    fun `sum peak needs both cascade members to be visible`() {
        val withBoth = explain(1173.2, 1332.5, 2505.7)
            .filterIsInstance<PeakExplanation.SumPeak>()
        assertEquals(1, withBoth.size)
        assertEquals("Co-60", withBoth.first().cascade.nuclide)

        // Без обеих линий каскада сумма — предсказание из ничего.
        val withoutMembers = explain(2505.7).filterIsInstance<PeakExplanation.SumPeak>()
        assertTrue(withoutMembers.isEmpty())
    }

    @Test
    fun `backscatter follows the Compton formula and saturates below 255 keV`() {
        assertTrue(ArtifactInterpreter.backscatterKeV(2614.5) < ArtifactInterpreter.BACKSCATTER_MAX_KEV)
        val found = explain(2614.5, 233.0).filterIsInstance<PeakExplanation.Backscatter>()
        assertEquals(1, found.size)
        assertEquals(2614.5, found.first().parent?.centroidKeV)
    }

    @Test
    fun `explanations never claim detection - they only remove the need for a new nuclide`() {
        // Тип объяснения не несёт вердикта: у него нет ни «уверенности», ни
        // «обнаружен». Проверяем контракт структуры, а не текста.
        val explanation = explain(511.0).first()
        assertTrue(explanation is PeakExplanation.Annihilation)
        assertEquals(511.0, explanation.peak.centroidKeV)
    }
}
