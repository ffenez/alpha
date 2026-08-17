package app.alpha.analysis.evidence

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalibrationDiagnosticsTest {

    @Test
    fun `one residual is not a diagnosis`() {
        val result = CalibrationDiagnostics.evaluate(
            listOf(CalibrationResidual(661.7, 7.0, 3.0)),
        )
        assertEquals(CalibrationVerdict.NOT_EVALUATED, result.verdict)
        assertNull(result.shiftKeV)
    }

    @Test
    fun `a consistent scale reports no systematic shift`() {
        val result = CalibrationDiagnostics.evaluate(
            listOf(
                CalibrationResidual(661.7, 1.2, 6.6),
                CalibrationResidual(1460.8, -0.8, 14.6),
                CalibrationResidual(2614.5, 2.0, 26.1),
            ),
        )
        assertEquals(CalibrationVerdict.CONSISTENT, result.verdict)
        assertNotNull(result.shiftKeV)
    }

    @Test
    fun `a systematic shift is reported with its own uncertainty`() {
        val result = CalibrationDiagnostics.evaluate(
            listOf(
                CalibrationResidual(661.7, 7.4, 2.0),
                CalibrationResidual(1460.8, 7.1, 2.5),
                CalibrationResidual(2614.5, 7.9, 3.0),
            ),
        )
        assertEquals(CalibrationVerdict.POSSIBLE_SYSTEMATIC_SHIFT, result.verdict)
        val shift = assertNotNull(result.shiftKeV)
        val sigma = assertNotNull(result.shiftUncertaintyKeV)
        assertTrue(abs(shift - 7.4) < 0.4, "сдвиг $shift")
        // Неопределённость среднего меньше любой отдельной, но не ноль.
        assertTrue(sigma in 0.5..2.0, "σ сдвига $sigma")
    }

    @Test
    fun `weights follow the uncertainties, not the count of points`() {
        val result = CalibrationDiagnostics.evaluate(
            listOf(
                CalibrationResidual(661.7, 0.0, 0.5),
                CalibrationResidual(2614.5, 40.0, 30.0),
            ),
        )
        val shift = assertNotNull(result.shiftKeV)
        // Точный остаток тянет оценку к нулю: среднее арифметическое дало бы 20.
        assertTrue(shift < 1.0, "взвешенный сдвиг $shift")
    }

    @Test
    fun `slope appears only with three or more residuals and follows the trend`() {
        val two = CalibrationDiagnostics.evaluate(
            listOf(
                CalibrationResidual(661.7, 1.0, 2.0),
                CalibrationResidual(1460.8, 2.0, 2.0),
            ),
        )
        assertNull(two.slopePerKeV)

        val three = CalibrationDiagnostics.evaluate(
            listOf(
                CalibrationResidual(661.7, 1.0, 2.0),
                CalibrationResidual(1460.8, 5.0, 2.0),
                CalibrationResidual(2614.5, 12.0, 2.0),
            ),
        )
        val slope = assertNotNull(three.slopePerKeV)
        assertTrue(slope > 0.0, "остатки растут с энергией: $slope")
    }
}
