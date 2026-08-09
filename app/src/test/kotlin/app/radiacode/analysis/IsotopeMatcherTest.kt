package app.radiacode.analysis

import app.radiacode.ui.logic.SpectrumFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun peak(energyKeV: Float, snr: Float, channel: Int = (energyKeV / 3f).toInt()) =
    Peak(channel = channel, energyKeV = energyKeV, netCounts = 1000f, snr = snr)

class IsotopeMatcherTest {

    @Test
    fun `strong tight Cs-137 peak reaches medium and uses the SPEC wording`() {
        val hints = IsotopeMatcher.match(listOf(peak(662f, snr = 12f)))
        assertEquals(1, hints.size)
        val hint = hints.single()
        assertEquals("Cs-137", hint.isotope)
        assertEquals(HintConfidence.MEDIUM, hint.confidence)
        assertEquals(
            "Возможное совпадение: Cs-137 · пик у 662 кэВ · " +
                "уверенность: средняя · нужно подтверждение",
            SpectrumFormat.hintLine(hint),
        )
        assertNull(SpectrumFormat.hintNote(hint), "Cs-137 is not natural background")
    }

    @Test
    fun `loose energy fit stays low confidence`() {
        // 680 keV is inside the match tolerance of the 661.7 line but outside
        // the tight window (max(1%, FWHM/4) ≈ 13 keV).
        val hints = IsotopeMatcher.match(listOf(peak(680f, snr = 12f)))
        assertEquals("Cs-137", hints.single().isotope)
        assertEquals(HintConfidence.LOW, hints.single().confidence)
    }

    @Test
    fun `weak peak stays low confidence even with perfect energy`() {
        val hints = IsotopeMatcher.match(listOf(peak(661.7f, snr = 5f)))
        assertEquals(HintConfidence.LOW, hints.single().confidence)
    }

    @Test
    fun `lone Co-60 line is low, both lines make it medium`() {
        val lone = IsotopeMatcher.match(listOf(peak(1173.2f, snr = 15f)))
        assertEquals("Co-60", lone.single().isotope)
        assertEquals(HintConfidence.LOW, lone.single().confidence)

        val both = IsotopeMatcher.match(
            listOf(peak(1173.2f, snr = 15f), peak(1332.5f, snr = 12f)),
        )
        // Two lines collapse into one Co-60 suggestion.
        assertEquals(1, both.count { it.isotope == "Co-60" })
        assertEquals(HintConfidence.MEDIUM, both.first { it.isotope == "Co-60" }.confidence)
    }

    @Test
    fun `natural lines carry the calm background note`() {
        val k40 = IsotopeMatcher.match(listOf(peak(1460.8f, snr = 20f))).single()
        assertEquals("K-40 — обычный природный фон", SpectrumFormat.hintNote(k40))

        val bi214 = IsotopeMatcher.match(listOf(peak(609.3f, snr = 9f))).first()
        assertEquals("Bi-214", bi214.isotope)
        assertEquals(
            "Bi-214 — из цепочки Ra-226, обычный природный фон",
            SpectrumFormat.hintNote(bi214),
        )
    }

    @Test
    fun `ambiguous peak lists alternatives`() {
        // 600 keV fits both Bi-214 (609.3) and Tl-208 (583.2).
        val hint = IsotopeMatcher.match(listOf(peak(600f, snr = 9f))).first()
        assertEquals("Bi-214", hint.isotope, "closest line wins")
        assertTrue("Tl-208" in hint.alternatives, "alternatives: ${hint.alternatives}")
        assertEquals("также похоже: Tl-208", SpectrumFormat.hintAlternatives(hint))
    }

    @Test
    fun `wording never claims detection`() {
        val hints = IsotopeMatcher.match(
            listOf(peak(661.7f, snr = 50f), peak(1460.8f, snr = 30f)),
        )
        for (hint in hints) {
            val line = SpectrumFormat.hintLine(hint)
            assertTrue("Возможное совпадение" in line, line)
            assertTrue("нужно подтверждение" in line, line)
            assertTrue("обнаруж" !in line.lowercase(), "wording must never claim detection: $line")
        }
    }

    @Test
    fun `peak far from every line yields no hint`() {
        assertTrue(IsotopeMatcher.match(listOf(peak(2000f, snr = 10f))).isEmpty())
    }

    @Test
    fun `hints sort by peak strength`() {
        val hints = IsotopeMatcher.match(
            listOf(peak(1460.8f, snr = 6f), peak(661.7f, snr = 20f)),
        )
        assertEquals(listOf("Cs-137", "K-40"), hints.map { it.isotope })
    }
}
