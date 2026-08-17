package app.alpha.analysis

import app.alpha.ui.logic.SpectrumFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun peak(
    energyKeV: Float,
    significance: Float,
    channel: Int = (energyKeV / 3f).toInt(),
) = Peak(
    channel = channel,
    energyKeV = energyKeV,
    netCounts = 1000f,
    significance = significance,
)

class IsotopeMatcherTest {

    @Test
    fun `strong tight Cs-137 peak reaches medium and names its confidence`() {
        val hints = IsotopeMatcher.match(listOf(peak(662f, significance = 12f)))
        assertEquals(1, hints.size)
        val hint = hints.single()
        assertEquals("Cs-137", hint.isotope)
        assertEquals(HintConfidence.MEDIUM, hint.confidence)
        // The peak-table candidate cell always carries the confidence, never
        // a bare isotope name (SPEC: «возможное совпадение», not detection).
        assertEquals("Cs-137 · средняя ур.", SpectrumFormat.candidateCell(hint))
        assertTrue(!hint.natural, "Cs-137 is not natural background")
    }

    @Test
    fun `loose energy fit stays low confidence`() {
        // 680 keV is inside the match tolerance of the 661.7 line but outside
        // the tight window (max(1%, FWHM/4) ≈ 13 keV).
        val hints = IsotopeMatcher.match(listOf(peak(680f, significance = 12f)))
        assertEquals("Cs-137", hints.single().isotope)
        assertEquals(HintConfidence.LOW, hints.single().confidence)
    }

    @Test
    fun `weak peak stays low confidence even with perfect energy`() {
        val hints = IsotopeMatcher.match(listOf(peak(661.7f, significance = 5f)))
        assertEquals(HintConfidence.LOW, hints.single().confidence)
    }

    @Test
    fun `lone Co-60 line is low, both lines make it medium`() {
        val lone = IsotopeMatcher.match(listOf(peak(1173.2f, significance = 15f)))
        assertEquals("Co-60", lone.single().isotope)
        assertEquals(HintConfidence.LOW, lone.single().confidence)

        val both = IsotopeMatcher.match(
            listOf(peak(1173.2f, significance = 15f), peak(1332.5f, significance = 12f)),
        )
        // Two lines collapse into one Co-60 suggestion.
        assertEquals(1, both.count { it.isotope == "Co-60" })
        assertEquals(HintConfidence.MEDIUM, both.first { it.isotope == "Co-60" }.confidence)
    }

    @Test
    fun `natural lines carry the calm природный marker`() {
        val k40 = IsotopeMatcher.match(listOf(peak(1460.8f, significance = 20f))).single()
        assertEquals("K-40 · природный", SpectrumFormat.candidateCell(k40))

        val bi214 = IsotopeMatcher.match(listOf(peak(609.3f, significance = 9f))).first()
        assertEquals("Bi-214", bi214.isotope)
        assertEquals("Bi-214 · природный", SpectrumFormat.candidateCell(bi214))
        assertEquals("Ra-226", bi214.chain, "daughter nuclides keep their chain")
    }

    @Test
    fun `ambiguous peak lists alternatives`() {
        // 600 кэВ подходит и Cs-134 (604,7), и Bi-214 (609,3), и Tl-208 (583,2):
        // ближайшая линия выигрывает, остальные названы альтернативами.
        val hint = IsotopeMatcher.match(listOf(peak(600f, significance = 9f))).first()
        assertEquals("Cs-134", hint.isotope, "closest line wins")
        assertTrue("Bi-214" in hint.alternatives, "alternatives: ${hint.alternatives}")
        assertTrue("Tl-208" in hint.alternatives, "alternatives: ${hint.alternatives}")
    }

    @Test
    fun `wording never claims detection`() {
        val hints = IsotopeMatcher.match(
            listOf(peak(661.7f, significance = 50f), peak(1460.8f, significance = 30f)),
        )
        for (hint in hints) {
            val cell = SpectrumFormat.candidateCell(hint)
            assertTrue(
                "природный" in cell || "ур." in cell,
                "candidate always carries a qualifier: $cell",
            )
            assertTrue("обнаруж" !in cell.lowercase(), "wording must never claim detection: $cell")
        }
    }

    @Test
    fun `peak far from every line yields no hint`() {
        assertTrue(IsotopeMatcher.match(listOf(peak(2000f, significance = 10f))).isEmpty())
    }

    @Test
    fun `hints sort by peak strength`() {
        val hints = IsotopeMatcher.match(
            listOf(peak(1460.8f, significance = 6f), peak(661.7f, significance = 20f)),
        )
        assertEquals(listOf("Cs-137", "K-40"), hints.map { it.isotope })
    }
}
