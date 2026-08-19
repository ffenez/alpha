package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EfficiencyCurveTest {

    /** Модельная кривая: ln ε = −2 − 0,8·ln E — степенной спад, как у реальной. */
    private fun modelEfficiency(energyKeV: Double): Double = exp(-2.0 - 0.8 * ln(energyKeV))

    private fun point(energyKeV: Double, sigma: Double = 0.05) = EfficiencyPoint(
        energyKeV = energyKeV,
        efficiency = modelEfficiency(energyKeV),
        relativeSigma = sigma,
        nuclide = "модель",
    )

    @Test
    fun `степенная зависимость восстанавливается точно`() {
        val curve = EfficiencyCurve.of(listOf(point(60.0), point(662.0), point(1332.0)))
        assertNotNull(curve)
        for (energy in listOf(100.0, 300.0, 900.0)) {
            val value = curve.efficiencyAt(energy)
            assertNotNull(value)
            val relative = abs(value.efficiency - modelEfficiency(energy)) / modelEfficiency(energy)
            assertTrue(relative < 0.02, "на $energy кэВ: ${value.efficiency}")
        }
    }

    @Test
    fun `за пределами калибровки значение не выдаётся`() {
        val curve = EfficiencyCurve.of(listOf(point(60.0), point(662.0), point(1332.0)))
        assertNotNull(curve)
        assertNull(curve.efficiencyAt(30.0), "экстраполяция вниз")
        assertNull(curve.efficiencyAt(2600.0), "экстраполяция вверх")
        assertNotNull(curve.efficiencyAt(60.0))
        assertNotNull(curve.efficiencyAt(1332.0))
    }

    @Test
    fun `неопределённость больше вдали от точек`() {
        val curve = EfficiencyCurve.of(
            listOf(point(60.0), point(80.0), point(1300.0), point(1332.0)),
        )
        assertNotNull(curve)
        val nearPoint = curve.efficiencyAt(70.0)
        val betweenClusters = curve.efficiencyAt(400.0)
        assertNotNull(nearPoint)
        assertNotNull(betweenClusters)
        assertTrue(
            betweenClusters.relativeSigma > nearPoint.relativeSigma,
            "${betweenClusters.relativeSigma} против ${nearPoint.relativeSigma}",
        )
    }

    @Test
    fun `точные точки дают меньшую неопределённость`() {
        val loose = EfficiencyCurve.of(
            listOf(point(60.0, 0.20), point(662.0, 0.20), point(1332.0, 0.20)),
        )
        val tight = EfficiencyCurve.of(
            listOf(point(60.0, 0.02), point(662.0, 0.02), point(1332.0, 0.02)),
        )
        assertNotNull(loose)
        assertNotNull(tight)
        val a = loose.efficiencyAt(300.0)!!.relativeSigma
        val b = tight.efficiencyAt(300.0)!!.relativeSigma
        assertTrue(b < a, "$b против $a")
    }

    @Test
    fun `степень растёт с числом точек`() {
        assertEquals(0, EfficiencyCurve.orderFor(1))
        assertEquals(1, EfficiencyCurve.orderFor(2))
        assertEquals(1, EfficiencyCurve.orderFor(3))
        assertEquals(2, EfficiencyCurve.orderFor(4))
        assertEquals(3, EfficiencyCurve.orderFor(6))
        assertEquals(3, EfficiencyCurve.orderFor(12))
    }

    @Test
    fun `одной точки не хватает`() {
        assertNull(EfficiencyCurve.of(listOf(point(662.0))))
        assertNull(EfficiencyCurve.of(emptyList()))
    }

    @Test
    fun `точки на одной энергии не задают наклон`() {
        assertNull(EfficiencyCurve.of(listOf(point(662.0), point(662.0))))
    }

    @Test
    fun `согласие считается только при степенях свободы`() {
        val exact = EfficiencyCurve.of(listOf(point(60.0), point(1332.0)))
        assertNotNull(exact)
        assertNull(exact.reducedChiSquare, "две точки на прямой — согласие не проверяется")

        val many = EfficiencyCurve.of(
            listOf(point(60.0), point(122.0), point(662.0), point(1332.0)),
        )
        assertNotNull(many)
        val chi = many.reducedChiSquare
        assertNotNull(chi)
        // Точки лежат на модели без шума — χ²/ndf близко к нулю.
        assertTrue(chi < 0.5, "χ²/ndf = $chi")
    }

    @Test
    fun `непригодные точки отбрасываются`() {
        val curve = EfficiencyCurve.of(
            listOf(
                point(60.0),
                EfficiencyPoint(-5.0, 0.1, 0.05, "плохая"),
                EfficiencyPoint(300.0, 0.0, 0.05, "нулевая"),
                point(1332.0),
            ),
        )
        assertNotNull(curve)
        assertEquals(2, curve.points.size)
    }
}
