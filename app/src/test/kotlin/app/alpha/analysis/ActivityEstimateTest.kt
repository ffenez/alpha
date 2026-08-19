package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityEstimateTest {

    private val efficiency = EfficiencyValue(efficiency = 0.01, relativeSigma = 0.10)

    @Test
    fun `активность считается по определению`() {
        // N = 10 000 имп. за 1 000 с при ε = 0,01 и p = 0,85:
        // A = 10 000 / (1 000 · 0,01 · 0,85) = 1 176,5 Бк.
        val estimate = ActivityMath.of(
            netCounts = 10_000.0,
            netSigma = 120.0,
            seconds = 1_000.0,
            efficiency = efficiency,
            intensity = 0.85,
            detectable = 400.0,
        )
        assertNotNull(estimate)
        assertNotNull(estimate.becquerel)
        assertEquals(1176.47, estimate.becquerel, 0.05)
    }

    @Test
    fun `неопределённость складывается из счёта, эффективности и выхода`() {
        val estimate = ActivityMath.of(
            netCounts = 10_000.0,
            netSigma = 200.0,
            seconds = 1_000.0,
            efficiency = efficiency,
            intensity = 0.85,
            intensitySigma = 0.017,
            detectable = 400.0,
        )
        assertNotNull(estimate)
        val expected = sqrt(0.02 * 0.02 + 0.10 * 0.10 + 0.02 * 0.02)
        assertEquals(expected, estimate.relativeSigma!!, 1e-6)
    }

    @Test
    fun `эффективность обычно и есть ведущий вклад`() {
        val estimate = ActivityMath.of(
            netCounts = 100_000.0,
            netSigma = 400.0,
            seconds = 1_000.0,
            efficiency = efficiency,
            intensity = 0.85,
            detectable = 400.0,
        )
        assertNotNull(estimate)
        // Счёт даёт 0,4 %, эффективность — 10 %: итог почти равен второму.
        assertEquals(0.10, estimate.relativeSigma!!, 0.002)
    }

    @Test
    fun `неразличимая линия даёт только верхнюю границу`() {
        val estimate = ActivityMath.of(
            netCounts = 120.0,
            netSigma = 90.0,
            seconds = 1_000.0,
            efficiency = efficiency,
            intensity = 0.85,
            detectable = 300.0,
        )
        assertNotNull(estimate)
        assertNull(estimate.becquerel, "активность названа для неразличимой линии")
        assertNull(estimate.sigmaBecquerel)
        assertTrue(estimate.upperBecquerel > 0.0)
        // Верхняя граница = (120 + 1,645·90) / (1000·0,01·0,85).
        assertEquals(31.53, estimate.upperBecquerel, 0.05)
    }

    @Test
    fun `предел обнаружения переводится в беккерели`() {
        val estimate = ActivityMath.of(
            netCounts = 10_000.0,
            netSigma = 120.0,
            seconds = 1_000.0,
            efficiency = efficiency,
            intensity = 0.85,
            detectable = 850.0,
        )
        assertNotNull(estimate)
        assertEquals(100.0, estimate.detectableBecquerel, 0.01)
    }

    @Test
    fun `непригодные множители не дают числа`() {
        assertNull(
            ActivityMath.of(10_000.0, 100.0, 0.0, efficiency, 0.85, detectable = 100.0),
            "нулевое время",
        )
        assertNull(
            ActivityMath.of(
                10_000.0, 100.0, 1_000.0,
                EfficiencyValue(0.0, 0.1), 0.85, detectable = 100.0,
            ),
            "нулевая эффективность",
        )
        assertNull(
            ActivityMath.of(10_000.0, 100.0, 1_000.0, efficiency, 0.0, detectable = 100.0),
            "нулевой выход",
        )
    }

    @Test
    fun `распад пересчитывает паспортную активность`() {
        // Ровно один период полураспада — половина.
        val half = ActivityMath.decayed(
            certifiedBecquerel = 100_000.0,
            elapsedSeconds = ActivityMath.halfLifeSecondsFromYears(30.08),
            halfLifeSeconds = ActivityMath.halfLifeSecondsFromYears(30.08),
        )
        assertNotNull(half)
        assertEquals(50_000.0, half, 1.0)

        // Cs-137 через десять лет: 2^(−10/30,08) = 0,7944.
        val decade = ActivityMath.decayed(
            certifiedBecquerel = 100_000.0,
            elapsedSeconds = ActivityMath.halfLifeSecondsFromYears(10.0),
            halfLifeSeconds = ActivityMath.halfLifeSecondsFromYears(30.08),
        )
        assertNotNull(decade)
        assertEquals(79_444.0, decade, 50.0)
    }

    @Test
    fun `измерение до аттестации не пересчитывается`() {
        assertNull(
            ActivityMath.decayed(100_000.0, -1.0, ActivityMath.halfLifeSecondsFromYears(30.0)),
        )
    }

    @Test
    fun `точка эффективности обратна расчёту активности`() {
        val point = ActivityMath.efficiencyPoint(
            netCounts = 10_000.0,
            netSigma = 100.0,
            seconds = 1_000.0,
            activityBecquerel = 1176.47,
            activityRelativeSigma = 0.05,
            intensity = 0.85,
            energyKeV = 661.7,
            nuclide = "Cs-137",
        )
        assertNotNull(point)
        assertEquals(0.01, point.efficiency, 1e-5)
        // σ_отн = √(0,01² + 0,05²).
        assertEquals(sqrt(0.01 * 0.01 + 0.05 * 0.05), point.relativeSigma, 1e-6)
    }

    @Test
    fun `круг замыкается — точка эталона возвращает его же активность`() {
        val truth = 5_000.0
        val point = ActivityMath.efficiencyPoint(
            netCounts = 42_500.0,
            netSigma = 210.0,
            seconds = 1_000.0,
            activityBecquerel = truth,
            activityRelativeSigma = 0.05,
            intensity = 0.85,
            energyKeV = 661.7,
            nuclide = "Cs-137",
        )
        assertNotNull(point)
        val estimate = ActivityMath.of(
            netCounts = 42_500.0,
            netSigma = 210.0,
            seconds = 1_000.0,
            efficiency = EfficiencyValue(point.efficiency, point.relativeSigma),
            intensity = 0.85,
            detectable = 500.0,
        )
        assertNotNull(estimate)
        assertTrue(abs(estimate.becquerel!! - truth) < 1.0, "получилось ${estimate.becquerel}")
    }
}
