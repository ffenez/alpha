package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Подгонка проверяется на линиях с ИЗВЕСТНОЙ формой: восстановление параметров,
 * а не «сошлось — и хорошо».
 */
class PeakShapeFitTest {

    private val continuumLevel = 40.0

    private fun spectrum(shape: PeakShapeFit.Shape, size: Int = 200): List<Int> =
        List(size) { i -> (shape.at(i.toDouble()) + continuumLevel).roundToInt() }

    private fun gaussian(size: Int, center: Double, sigma: Double, amplitude: Double): List<Int> =
        List(size) { i ->
            val z = (i - center) / sigma
            (amplitude * exp(-0.5 * z * z) + continuumLevel).roundToInt()
        }

    @Test
    fun `симметричная линия восстанавливается по центру и ширине`() {
        val counts = gaussian(200, center = 100.0, sigma = 6.0, amplitude = 900.0)
        val fit = PeakShapeFit.fit(
            counts = counts,
            range = 80..120,
            continuumAt = { continuumLevel },
            centerGuess = 100.0,
            sigmaGuess = 6.0,
        )
        assertNotNull(fit)
        assertTrue(abs(fit.shape.centerChannel - 100.0) < 0.3, "центр ${fit.shape.centerChannel}")
        assertTrue(abs(fit.shape.sigmaChannels - 6.0) < 0.5, "σ ${fit.shape.sigmaChannels}")
        // Гауссиана — частный случай формы: хвосты уходят далеко, FWHM = 2,355σ.
        assertTrue(
            abs(fit.fwhmChannels - 2.3548 * 6.0) < 0.8,
            "FWHM ${fit.fwhmChannels}",
        )
    }

    @Test
    fun `хвостатая линия описывается своими параметрами`() {
        val truth = PeakShapeFit.Shape(
            amplitude = 800.0,
            centerChannel = 100.0,
            sigmaChannels = 5.0,
            tailLeft = 1.0,
            tailRight = 3.0,
        )
        val fit = PeakShapeFit.fit(
            counts = spectrum(truth),
            range = 70..130,
            continuumAt = { continuumLevel },
            centerGuess = 100.0,
            sigmaGuess = 5.0,
        )
        assertNotNull(fit)
        assertTrue(abs(fit.shape.centerChannel - 100.0) < 0.5, "центр ${fit.shape.centerChannel}")
        // Левый хвост длиннее: точка сшивки слева меньше, чем справа.
        assertTrue(
            fit.shape.tailLeft < fit.shape.tailRight,
            "хвосты ${fit.shape.tailLeft} и ${fit.shape.tailRight}",
        )
        assertTrue(fit.shape.asymmetry > 1.0, "асимметрия ${fit.shape.asymmetry}")
    }

    @Test
    fun `на хвостатой линии симметричный центр смещён, а подгонка нет`() {
        val truth = PeakShapeFit.Shape(
            amplitude = 800.0,
            centerChannel = 100.0,
            sigmaChannels = 5.0,
            tailLeft = 0.8,
            tailRight = 3.5,
        )
        val counts = spectrum(truth)
        // Центр тяжести нетто — то, чем пик описывался до подгонки.
        var weight = 0.0
        var moment = 0.0
        for (i in 70..130) {
            val net = counts[i] - continuumLevel
            if (net <= 0) continue
            weight += net
            moment += net * i
        }
        val moments = moment / weight
        val fit = PeakShapeFit.fit(counts, 70..130, { continuumLevel }, 100.0, 5.0)
        assertNotNull(fit)
        assertTrue(moments < 99.0, "центр тяжести $moments не ушёл в хвост")
        assertTrue(
            abs(fit.shape.centerChannel - 100.0) < abs(moments - 100.0),
            "подгонка ${fit.shape.centerChannel} не ближе центра тяжести $moments",
        )
    }

    @Test
    fun `дублет отбраковывается по согласию`() {
        val a = PeakShapeFit.Shape(700.0, 95.0, 4.0, 2.0, 2.0)
        val b = PeakShapeFit.Shape(700.0, 112.0, 4.0, 2.0, 2.0)
        val counts = List(200) { i ->
            (a.at(i.toDouble()) + b.at(i.toDouble()) + continuumLevel).roundToInt()
        }
        val fit = PeakShapeFit.fit(counts, 75..132, { continuumLevel }, 103.0, 8.0)
        assertNull(fit, "дублет описан одной линией: ${fit?.reducedC}")
    }

    @Test
    fun `площадь линии восстанавливается`() {
        val truth = PeakShapeFit.Shape(800.0, 100.0, 5.0, 2.0, 2.0)
        val expected = (60..140).sumOf { truth.at(it.toDouble()) }
        val fit = PeakShapeFit.fit(spectrum(truth), 60..140, { continuumLevel }, 100.0, 5.0)
        assertNotNull(fit)
        val relative = abs(fit.netCounts - expected) / expected
        assertTrue(relative < 0.1, "площадь ${fit.netCounts} против $expected")
    }

    @Test
    fun `наклонный континуум учитывается`() {
        val truth = PeakShapeFit.Shape(600.0, 100.0, 5.0, 1.5, 2.5)
        val slope = { channel: Int -> 200.0 - channel * 1.0 }
        val counts = List(200) { i -> (truth.at(i.toDouble()) + slope(i)).roundToInt() }
        val fit = PeakShapeFit.fit(counts, 70..130, { slope(it) }, 100.0, 5.0)
        assertNotNull(fit)
        assertTrue(abs(fit.shape.centerChannel - 100.0) < 0.6, "центр ${fit.shape.centerChannel}")
    }

    @Test
    fun `узкое окно отклоняется`() {
        val counts = gaussian(200, 100.0, 6.0, 900.0)
        assertNull(PeakShapeFit.fit(counts, 96..104, { continuumLevel }, 100.0, 6.0))
    }

    @Test
    fun `пустое окно без превышения отклоняется`() {
        val counts = List(200) { continuumLevel.roundToInt() }
        assertNull(PeakShapeFit.fit(counts, 70..130, { continuumLevel }, 100.0, 5.0))
    }

    @Test
    fun `неопределённость центра падает с площадью`() {
        val small = PeakShapeFit.fit(
            spectrum(PeakShapeFit.Shape(120.0, 100.0, 5.0, 2.0, 2.0)),
            70..130, { continuumLevel }, 100.0, 5.0,
        )
        val large = PeakShapeFit.fit(
            spectrum(PeakShapeFit.Shape(4000.0, 100.0, 5.0, 2.0, 2.0)),
            70..130, { continuumLevel }, 100.0, 5.0,
        )
        assertNotNull(small)
        assertNotNull(large)
        assertTrue(
            large.centerSigmaChannels < small.centerSigmaChannels,
            "${large.centerSigmaChannels} против ${small.centerSigmaChannels}",
        )
    }
}
