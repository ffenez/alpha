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

    /** Нулевая систематика: проверяется арифметика взвешивания, а не физика. */
    private val noSystematic: (Double) -> Double = { 0.0 }

    @Test
    fun `a consistent scale reports no systematic shift`() {
        val result = CalibrationDiagnostics.evaluate(
            listOf(
                CalibrationResidual(661.7, 1.2, 6.6),
                CalibrationResidual(1460.8, -0.8, 14.6),
                CalibrationResidual(2614.5, 2.0, 26.1),
            ),
            noSystematic,
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
            noSystematic,
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
            noSystematic,
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

    /**
     * Без оценки систематики сдвиг не объявляется.
     *
     * Отклонение остатка от нуля складывается из статистики центроида и ухода
     * самой шкалы; вторая часть больше первой на порядок. Считая значимость по
     * одной статистике, «выделенным» пришлось бы называть почти любой сдвиг —
     * поэтому вердикт говорит именно о нехватке σ_cal, а не о нехватке линий.
     */
    @Test
    fun `no shift is claimed while the scale scatter is unknown`() {
        val result = CalibrationDiagnostics.evaluate(
            listOf(
                CalibrationResidual(1120.3, 12.7, 1.0),
                CalibrationResidual(1460.8, -28.6, 0.5),
            ),
        )
        assertEquals(CalibrationVerdict.SIGMA_NOT_ESTIMATED, result.verdict)
        assertNull(result.shiftKeV)
        assertNull(result.shiftUncertaintyKeV)
    }

    /**
     * Систематика входит в знаменатель: те же остатки, что «выделялись» по
     * одной статистике, перестают быть значимыми, когда учтён уход шкалы.
     */
    @Test
    fun `systematic uncertainty widens the denominator`() {
        val residuals = listOf(
            CalibrationResidual(661.7, 6.0, 1.0),
            CalibrationResidual(1460.8, 6.5, 1.0),
            CalibrationResidual(2614.5, 5.5, 1.0),
        )
        val bare = CalibrationDiagnostics.evaluate(residuals, noSystematic)
        assertEquals(CalibrationVerdict.POSSIBLE_SYSTEMATIC_SHIFT, bare.verdict)

        val withScale = CalibrationDiagnostics.evaluate(residuals) { 15.0 }
        assertEquals(CalibrationVerdict.CONSISTENT, withScale.verdict)
        // Само значение сдвига при этом не меняется — меняется его ошибка.
        assertTrue(abs(withScale.shiftKeV!! - bare.shiftKeV!!) < 0.2)
        assertTrue(withScale.shiftUncertaintyKeV!! > bare.shiftUncertaintyKeV!!)
    }
}
