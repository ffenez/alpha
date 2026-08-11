package app.radiacode.analysis

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Жёсткость is the vendor's documented ratio — dose per count — so what these
 * tests pin is that it behaves like a quotient and refuses to be one when the
 * denominator cannot carry it.
 */
class HardnessTest {

    @Test
    fun `the coefficient is dose per count in the units the vendor defines`() {
        // 0,20 µSv/h = 20 µR/h over 40 counts per second → 0,50.
        val value = assertNotNull(
            Hardness.of(doseRateMicroSvH = 0.20, countRate = 40.0, seconds = 60.0),
        )
        assertEquals(0.5, value.value, 1e-9)
        assertEquals("0,50", Hardness.format(value.value))
        assertEquals(2_400.0, value.counts, 1e-9)
    }

    @Test
    fun `harder radiation raises it, more of the same radiation does not`() {
        val soft = assertNotNull(Hardness.of(0.10, 40.0, 60.0))
        // Twice the dose at the same count rate: each event carries twice as
        // much — that is what «harder» means here.
        val hard = assertNotNull(Hardness.of(0.20, 40.0, 60.0))
        assertEquals(2.0, hard.value / soft.value, 1e-9)

        // Twice as much of the very same radiation: both rates double, H holds.
        val brighter = assertNotNull(Hardness.of(0.20, 80.0, 60.0))
        assertEquals(soft.value, brighter.value, 1e-9)
    }

    @Test
    fun `uncertainty carries both inputs, and counting alone understates it`() {
        val countingOnly = assertNotNull(Hardness.of(0.20, 40.0, 60.0))
        // σ/H = 1/√N with N = 2400 → ≈ 2 %.
        assertEquals(countingOnly.value / sqrt(2_400.0), countingOnly.sigma, 1e-9)
        assertTrue(!Hardness.sigmaKnown(null))

        val withDoseError = assertNotNull(
            Hardness.of(0.20, 40.0, 60.0, doseErrPercent = 8.0),
        )
        assertTrue(withDoseError.sigma > countingOnly.sigma)
        assertTrue(Hardness.sigmaKnown(8.0))
        // √(0,08² + (1/√2400)²) ≈ 0,0825 of the value.
        assertEquals(0.0825 * withDoseError.value, withDoseError.sigma, 1e-4)
    }

    @Test
    fun `a longer window narrows the uncertainty without moving the value`() {
        val short = assertNotNull(Hardness.of(0.20, 40.0, 60.0))
        val long = assertNotNull(Hardness.of(0.20, 40.0, 240.0))
        assertEquals(short.value, long.value, 1e-9)
        assertEquals(short.sigma / 2.0, long.sigma, 1e-9)
    }

    @Test
    fun `a quiet or thin window gets no coefficient instead of a division`() {
        assertNull(Hardness.of(0.20, countRate = 0.0, seconds = 60.0))
        assertNull(Hardness.of(0.20, countRate = 0.2, seconds = 60.0), "below the rate floor")
        assertNull(Hardness.of(0.20, countRate = 40.0, seconds = 0.0))
        assertNull(Hardness.of(0.20, countRate = 1.0, seconds = 30.0), "30 counts is not enough")
        assertNull(Hardness.of(Double.NaN, 40.0, 60.0))
        assertNull(Hardness.of(0.20, Double.POSITIVE_INFINITY, 60.0))
    }

    @Test
    fun `the explanation says what it is and what it is not`() {
        val text = Hardness.EXPLANATION
        assertTrue(text.contains("отношение мощности дозы к скорости счёта"), text)
        assertTrue(text.contains("не мера опасности"), text)
        // The tempting reading — «это средняя энергия фотона» — is denied
        // explicitly: the vendor documents a ratio, not an energy.
        assertTrue(text.contains("не средняя энергия фотона"), text)
    }
}
