package app.alpha.analysis.evidence

import app.alpha.analysis.EnergyCalibration
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Диагностика калибровки на СИНТЕТИЧЕСКИХ спектрах: спектр строится по
 * известной модели ширины, и проверяется, что движок её восстанавливает, а
 * там, где данных мало, отказывается с названной причиной.
 */
class BackgroundCalibrationTest {

    private val calibration = EnergyCalibration(0f, 3f, 0f)
    private val channels = 1024
    private val start = SqrtResolution(0.084)

    /** Истинная модель прибора-«образца»: FWHM = √(400 + 2,5·E). */
    private fun trueFwhm(energyKeV: Double) = sqrt(400.0 + 2.5 * energyKeV)

    private fun spectrum(
        peaks: List<Triple<Double, Double, Double>>,
        continuum: Double = 400.0,
    ): List<Int> {
        val counts = DoubleArray(channels) { continuum }
        for ((energy, area, fwhm) in peaks) {
            val sigma = fwhm / 2.3548
            for (ch in 0 until channels) {
                val e = calibration.energyAt(ch.toFloat()).toDouble()
                val z = (e - energy) / sigma
                if (kotlin.math.abs(z) > 6.0) continue
                counts[ch] += area * 3.0 / (sigma * sqrt(2.0 * Math.PI)) * exp(-0.5 * z * z)
            }
        }
        return counts.map { Math.round(it).toInt() }
    }

    private fun accumulation(counts: List<Int>) = CalibrationAccumulation(
        id = "long",
        counts = counts,
        calibration = calibration,
        seconds = 120_000L,
        intervalCount = 200,
        hoursCovered = 33,
        fromMillis = 0L,
        toMillis = 120_000_000L,
    )

    private fun fourLines(shift: (Double) -> Double = { 0.0 }) = spectrum(
        listOf(1120.3, 1460.8, 1764.5, 2614.5).map { energy ->
            Triple(energy + shift(energy), 400_000.0, trueFwhm(energy))
        },
    )

    @Test
    fun `usable lines are measured and the width model is recovered`() {
        val report = BackgroundCalibration.analyse(
            listOf(accumulation(fourLines())),
            start,
        )
        assertEquals(4, report.measurements.size, "${report.measurements.map { it.line.energyKeV }}")
        val fit = (report.fit as? ResolutionFitOutcome.Fitted)?.fit
        assertNotNull(fit, "подгонка обязана состояться: ${report.fit}")
        // Восстановленная ширина сходится с той, из которой построен спектр.
        for (energy in listOf(1200.0, 1800.0, 2400.0)) {
            val recovered = fit.model().fwhmKeV(energy)
            val truth = trueFwhm(energy)
            assertTrue(
                kotlin.math.abs(recovered - truth) < 0.12 * truth,
                "E=$energy: $recovered против $truth",
            )
        }
    }

    @Test
    fun `the fitted model never falls with energy`() {
        val fit = (
            BackgroundCalibration.analyse(listOf(accumulation(fourLines())), start).fit
                as ResolutionFitOutcome.Fitted
            ).fit
        var previous = 0.0
        var energy = 50.0
        while (energy <= 3000.0) {
            val width = fit.model().fwhmKeV(energy)
            assertTrue(width >= previous - 1e-9, "ширина упала на $energy кэВ")
            previous = width
            energy += 50.0
        }
    }

    @Test
    fun `two lines are refused instead of drawing a curve through them`() {
        val counts = spectrum(
            listOf(1460.8, 2614.5).map { Triple(it, 400_000.0, trueFwhm(it)) },
        )
        val report = BackgroundCalibration.analyse(listOf(accumulation(counts)), start)
        val refused = report.fit as? ResolutionFitOutcome.Refused
        assertNotNull(refused, "по двум точкам кривой быть не должно: ${report.fit}")
        assertEquals(ResolutionFitRefusal.NOT_ENOUGH_LINES, refused.reason)
        assertEquals(2, refused.points)
    }

    @Test
    fun `a merged pair never becomes a fit point`() {
        // В спектре только 583,2 и 609,3: обе линии отбор отбрасывает, и
        // подгонке остаётся ноль точек.
        val counts = spectrum(
            listOf(583.2, 609.3).map { Triple(it, 400_000.0, trueFwhm(it)) },
        )
        val report = BackgroundCalibration.analyse(listOf(accumulation(counts)), start)
        assertTrue(
            report.measurements.none { it.line.energyKeV in listOf(583.2, 609.3) },
            "измерено: ${report.measurements.map { it.line.energyKeV to it.significance }}",
        )
        assertEquals(
            LineRejection.BLENDED_WITH_OTHER_ACTIVITY,
            report.candidates.first { it.line.energyKeV == 609.3 }.rejection,
        )
    }

    @Test
    fun `sigma of the scale comes from the scatter of the residuals`() {
        // Шкала уехала на +6 кэВ, но не одинаково: разброс остатков и есть
        // искомая величина, а среднее — систематический сдвиг.
        val offsets = mapOf(1120.3 to 4.0, 1460.8 to 6.0, 1764.5 to 9.0, 2614.5 to 5.0)
        val report = BackgroundCalibration.analyse(
            listOf(accumulation(fourLines { offsets.getValue(it) })),
            start,
        )
        val scale = report.scale
        assertNotNull(scale)
        assertTrue(scale.shiftKeV!! > 3.0, "сдвиг ${scale.shiftKeV}")
        assertEquals(CalibrationVerdict.POSSIBLE_SYSTEMATIC_SHIFT, scale.verdict)
        // Разброс задан руками (4, 6, 9, 5) — SD около 2,2 кэВ.
        assertTrue(scale.sigmaKeV in 1.0..4.0, "σ_cal ${scale.sigmaKeV}")
        assertTrue(!scale.statisticalOnly, "разброс заметно больше статистики")
        assertTrue(scale.sigmaFraction > 0.0 && scale.sigmaFraction < 0.01)
    }

    @Test
    fun `no shift is claimed when the residuals are consistent with zero`() {
        val report = BackgroundCalibration.analyse(listOf(accumulation(fourLines())), start)
        val scale = report.scale
        assertNotNull(scale)
        assertEquals(CalibrationVerdict.CONSISTENT, scale.verdict)
    }

    @Test
    fun `two lines of one nuclide give a relative response and refuse point geometry`() {
        val report = BackgroundCalibration.analyse(listOf(accumulation(fourLines())), start)
        val point = report.response.singleOrNull()
        assertNotNull(point, "ожидалась одна пара Bi-214: ${report.response}")
        assertEquals("Bi-214", point.nuclide)
        assertEquals(1120.3, point.lowerKeV)
        assertEquals(1764.5, point.upperKeV)
        // Площади в синтетике равны, выходы Bi-214 почти равны — отклик ≈ 1.
        assertTrue(point.ratio in 0.8..1.2, "отклик ${point.ratio}")
        assertTrue(point.sigma > 0.0)
        assertEquals(ResponseGeometry.DISTRIBUTED_BACKGROUND, point.geometry)
        assertEquals(
            ResponseRefusal.POINT_GEOMETRY_NOT_COVERED,
            RelativeResponseEstimator.refusalForPointSource(),
        )
    }

    @Test
    fun `an empty spectrum measures nothing and evaluates nothing`() {
        val report = BackgroundCalibration.analyse(
            listOf(accumulation(List(channels) { 400 })),
            start,
        )
        assertTrue(report.measurements.isEmpty())
        assertNull(report.scale)
        assertTrue(report.response.isEmpty())
        assertTrue(report.notFound.isNotEmpty(), "пригодные линии обязаны быть названы")
    }
}
