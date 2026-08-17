package app.alpha.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UncertaintyTest {

    @Test
    fun `poisson sigma is sqrt of cps over tau`() {
        // τ = 1 s: σ = √cps.
        assertTrue(abs(Uncertainty.cpsSigma(25f) - 5f) < 1e-6f)
        // Longer averaging shrinks the rate uncertainty: σ = √(cps/τ).
        assertTrue(abs(Uncertainty.cpsSigma(25f, tauSeconds = 25f) - 1f) < 1e-6f)
        assertEquals(0f, Uncertainty.cpsSigma(-1f))
    }

    @Test
    fun `cps line carries the value and its sigma`() {
        assertEquals("24,3 ±4,9 с⁻¹", Uncertainty.cpsWithSigma(24.3f))
        assertEquals("±6,2 с⁻¹ (1σ Пуассон)", Uncertainty.cpsSigmaLine(38.2f))
    }

    @Test
    fun `device error percent is shown verbatim without a sigma claim`() {
        assertEquals("±8%", Uncertainty.errPercentLabel(8.3f))
        assertNull(Uncertainty.errPercentLabel(0f))
        assertNull(Uncertainty.errPercentLabel(null))
    }

    @Test
    fun `one-decimal comma formatting`() {
        assertEquals("24,3", Uncertainty.num1(24.31f))
        assertEquals("0,0", Uncertainty.num1(0f))
    }
}
