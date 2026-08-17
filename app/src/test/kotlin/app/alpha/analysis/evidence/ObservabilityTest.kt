package app.alpha.analysis.evidence

import app.alpha.analysis.EnergyCalibration
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObservabilityTest {

    private val range = 20.0..3000.0
    private val bi609 = lineOf("Bi-214", 609.3)
    private val bi1120 = lineOf("Bi-214", 1120.3)
    private val bi1764 = lineOf("Bi-214", 1764.5)

    @Test
    fun `Currie detection limit grows with the background`() {
        assertEquals(2.71, DetectionLimit.currieCounts(0.0), 1e-9)
        val quiet = DetectionLimit.currieCounts(100.0)
        val noisy = DetectionLimit.currieCounts(10_000.0)
        assertTrue(noisy > quiet)
        assertTrue(abs(quiet - (2.71 + 46.5)) < 1e-6)
    }

    @Test
    fun `histogram continuum reads counts per keV around the energy`() {
        val calibration = EnergyCalibration(a0 = 0f, a1 = 3f, a2 = 0f)
        val counts = List(1024) { 30 }
        val continuum = HistogramContinuum(counts, calibration, TEST_RESOLUTION)
        val perKeV = continuum.countsPerKeV(600.0)
        assertNotNull(perKeV)
        // 30 импульсов на канал шириной 3 кэВ = 10 импульсов на кэВ.
        assertTrue(abs(perKeV - 10.0) < 1e-6, "континуум $perKeV")
        assertNull(continuum.countsPerKeV(9000.0), "вне шкалы — не ноль, а неизвестно")
    }

    @Test
    fun `a missing line below the reference is negative evidence`() {
        // Опорная 1764,5 с площадью 1000; 609,3 втрое ярче по выходу и лежит
        // НИЖЕ по энергии, поэтому оценка снизу законна.
        val verdict = LineObservabilityRule.evaluate(
            line = bi609,
            referenceLine = bi1764,
            referenceArea = 1000.0,
            continuum = flatContinuum(1.0),
            resolution = TEST_RESOLUTION,
            energyRangeKeV = range,
        )
        assertEquals(LineObservability.EXPECTED_OBSERVABLE, verdict)
    }

    @Test
    fun `a missing line above the reference stays undetermined without efficiency`() {
        val verdict = LineObservabilityRule.evaluate(
            line = bi1764,
            referenceLine = bi609,
            referenceArea = 1000.0,
            continuum = flatContinuum(1.0),
            resolution = TEST_RESOLUTION,
            energyRangeKeV = range,
        )
        // Без кривой эффективности «сверху» судить нельзя: ε(1764) < ε(609),
        // и оценка по одним выходам завышена.
        assertEquals(LineObservability.UNDETERMINED, verdict)
    }

    @Test
    fun `a faint line on a loud continuum is below the detection limit`() {
        val verdict = LineObservabilityRule.evaluate(
            line = bi1120,
            referenceLine = bi609,
            referenceArea = 60.0,
            continuum = flatContinuum(500.0),
            resolution = TEST_RESOLUTION,
            energyRangeKeV = range,
        )
        assertEquals(LineObservability.BELOW_DETECTION_LIMIT, verdict)
    }

    @Test
    fun `without a continuum nothing can be concluded from absence`() {
        val verdict = LineObservabilityRule.evaluate(
            line = bi609,
            referenceLine = bi1764,
            referenceArea = 1000.0,
            continuum = null,
            resolution = TEST_RESOLUTION,
            energyRangeKeV = range,
        )
        assertEquals(LineObservability.UNDETERMINED, verdict)
    }

    @Test
    fun `a line outside the device scale was never expected`() {
        val verdict = LineObservabilityRule.evaluate(
            line = lineOf("Tl-208", 2614.5),
            referenceLine = bi609,
            referenceArea = 1000.0,
            continuum = flatContinuum(1.0),
            resolution = TEST_RESOLUTION,
            energyRangeKeV = 20.0..1500.0,
        )
        assertEquals(LineObservability.OUT_OF_RANGE, verdict)
    }

    @Test
    fun `an efficiency model turns the one-sided argument into a verdict`() {
        // Заглушка: эффективность падает вдвое на каждую тысячу кэВ.
        val efficiency = DetectorEfficiencyModel { energy -> Estimate(1.0 / (1.0 + energy / 1000.0)) }
        val verdict = LineObservabilityRule.evaluate(
            line = bi1764,
            referenceLine = bi609,
            referenceArea = 1000.0,
            continuum = flatContinuum(1.0),
            resolution = TEST_RESOLUTION,
            efficiency = efficiency,
            energyRangeKeV = range,
        )
        assertEquals(LineObservability.EXPECTED_OBSERVABLE, verdict)
    }
}
