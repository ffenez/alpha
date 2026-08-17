package app.alpha.analysis.evidence

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolutionTest {

    @Test
    fun `sqrt model reproduces the vendor point at 662 keV`() {
        val model = SqrtResolution(0.084)
        assertEquals(0.084 * 662.0, model.fwhmKeV(662.0), 1e-6)
    }

    @Test
    fun `relative resolution is not constant across the scale`() {
        val model = SqrtResolution(0.084)
        val at200 = model.fwhmKeV(200.0) / 200.0
        val at2614 = model.fwhmKeV(2614.5) / 2614.5
        // Заявленные 8,4 % относятся к 662 кэВ; ниже — хуже, выше — лучше.
        assertTrue(at200 > 0.084, "относительное разрешение на 200 кэВ = $at200")
        assertTrue(at2614 < 0.084, "относительное разрешение на 2614 кэВ = $at2614")
    }

    @Test
    fun `measured model is a drop-in replacement`() {
        // FWHM² = a + bE: подставляется, когда появятся калибровочные точки.
        val measured = MeasuredResolution(a = 100.0, b = 3.5, c = 0.0)
        assertEquals(kotlin.math.sqrt(100.0 + 3.5 * 662.0), measured.fwhmKeV(662.0), 1e-9)
        val sigma = measured.sigmaKeV(662.0)
        assertTrue(abs(sigma * 2.3548 - measured.fwhmKeV(662.0)) < 1e-9)
    }

    @Test
    fun `lines closer than one FWHM are not resolvable`() {
        // Pb-214 351,9 и I-131 364,5 при FWHM ≈ 39 кэВ — классическая пара,
        // которую сцинтиллятор не разводит.
        assertTrue(!ResolutionAmbiguities.resolvable(351.9, 364.5, TEST_RESOLUTION))
        // K-40 1460,8 и Bi-214 1764,5 разводятся уверенно.
        assertTrue(ResolutionAmbiguities.resolvable(1460.8, 1764.5, TEST_RESOLUTION))
    }

    @Test
    fun `ambiguity group names rivals from other nuclides`() {
        val peak = peakAt(351.9)
        val group = ResolutionAmbiguities.ambiguityFor(peak, lineOf("Pb-214", 351.9), TEST_RESOLUTION)
        assertNotNull(group)
        assertTrue("I-131" in group.nuclides, "альтернативы: ${group.nuclides}")
        assertTrue("Pb-214" in group.nuclides)
    }

    @Test
    fun `a line with no rivals produces no group`() {
        val peak = peakAt(2614.5)
        assertNull(
            ResolutionAmbiguities.ambiguityFor(peak, lineOf("Tl-208", 2614.5), TEST_RESOLUTION),
        )
    }
}
