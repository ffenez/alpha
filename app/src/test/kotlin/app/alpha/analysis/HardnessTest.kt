package app.alpha.analysis

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
        // 0,20 µSv/h = 20 µrem/h over 40 counts per second → 0,50.
        val value = assertNotNull(
            Hardness.of(doseRateMicroSvH = 0.20, countRate = 40.0, seconds = 60.0),
        )
        assertEquals(0.5, value.value, 1e-9)
        assertEquals("0,50", Hardness.format(value.value))
        assertEquals(2_400.0, value.counts, 1e-9)
    }

    @Test
    fun `a harder field raises it, more of the same field does not`() {
        val soft = assertNotNull(Hardness.of(0.10, 40.0, 60.0))
        // The same count rate carrying twice the dose: the ratio doubles.
        val harder = assertNotNull(Hardness.of(0.20, 40.0, 60.0))
        assertEquals(2.0, harder.value / soft.value, 1e-9)

        // The intensity-suppressing property, which is the point of the
        // coefficient: k-fold more of the same field moves both rates and
        // leaves H where it was.
        for (k in listOf(0.5, 2.0, 7.5)) {
            val scaled = assertNotNull(Hardness.of(0.10 * k, 40.0 * k, 60.0))
            assertEquals(soft.value, scaled.value, 1e-9, "k = $k")
        }
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
    fun `the explanation says what it is and denies what it is not`() {
        val text = Hardness.EXPLANATION
        assertTrue(text.contains("дозовая величина на единицу скорости счёта"), text)
        assertTrue(text.contains("(мкрем/ч)/(имп/с)"), text)
        assertTrue(text.contains("не мера опасности"), text)
        // The tempting reading — «это средняя энергия» — is denied outright:
        // the numerator is a dosimetric estimate made through the detector's
        // own energy response, not energy deposited in the crystal.
        assertTrue(text.contains("не средняя энергия фотона"), text)
    }

    /**
     * The claim this app must never make. Left as a test rather than a comment
     * because it is the phrase a later UI edit would reach for first.
     */
    @Test
    fun `no wording turns the coefficient into an energy or a bound`() {
        val texts = listOf(Hardness.EXPLANATION, Hardness.PURPOSE, Hardness.SIGMA_CAVEAT)
        for (text in texts) {
            val lower = text.lowercase()
            assertTrue(
                !Regex("(?<!не )средняя энергия").containsMatchIn(lower),
                "«средняя энергия» claimed in: $text",
            )
            assertTrue(!lower.contains("ортогональ"), "orthogonality is not claimed: $text")
            assertTrue(!lower.contains("консерватив"), "the sigma is not called conservative")
        }
    }

    @Test
    fun `the purpose explains the suppression and its limits`() {
        val text = Hardness.PURPOSE
        assertTrue(text.contains("подавляет влияние общей интенсивности"), text)
        // …and never sells it as exact.
        assertTrue(text.contains("Точного постоянства нет"), text)
        assertTrue(text.contains("энергетическая характеристика детектора"), text)
    }

    @Test
    fun `the sigma is presented as an estimate, not as a bound`() {
        val text = Hardness.SIGMA_CAVEAT
        assertTrue(text.contains("ковариация"), text)
        assertTrue(text.contains("не опубликована"), text)
        assertTrue(text.contains("оценка, а не гарантированная граница"), text)
    }
}
