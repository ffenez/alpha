package app.radiacode.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** Linear 3 keV/channel calibration: 1024 channels cover 0..3069 keV. */
private val CALIBRATION = EnergyCalibration(a0 = 0f, a1 = 3f, a2 = 0f)

/** Smooth Compton-like continuum: high at low energies, tail at high. */
private fun continuum(channel: Int): Double = 400.0 * exp(-channel / 180.0) + 25.0

/** Adds a Gaussian peak with the detector's FWHM at [energyKeV]. */
private fun MutableList<Double>.addPeak(energyKeV: Float, amplitude: Double) {
    val center = CALIBRATION.channelAt(energyKeV).toDouble()
    val sigma = PeakDetection.fwhmKeV(energyKeV) / 2.355 / 3.0 // keV -> channels
    for (i in indices) {
        this[i] += amplitude * exp(-0.5 * ((i - center) / sigma) * ((i - center) / sigma))
    }
}

private fun toCounts(values: List<Double>, noise: Random? = null): List<Int> =
    values.map { v ->
        val n = noise?.nextGaussian()?.times(sqrt(v)) ?: 0.0
        (v + n).coerceAtLeast(0.0).toInt()
    }

private fun Random.nextGaussian(): Double {
    // Box-Muller; enough for test noise.
    val u1 = nextDouble().coerceAtLeast(1e-12)
    val u2 = nextDouble()
    return sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
}

private fun syntheticSpectrum(vararg peaks: Pair<Float, Double>, noise: Random? = null): List<Int> {
    val values = MutableList(1024) { continuum(it) }
    for ((energy, amplitude) in peaks) values.addPeak(energy, amplitude)
    return toCounts(values, noise)
}

class PeakDetectionTest {

    @Test
    fun `finds the K-40 peak on a noisy natural background`() {
        val counts = syntheticSpectrum(1460.8f to 60.0, noise = Random(42))
        val peaks = PeakDetection.detect(counts, CALIBRATION)
        assertTrue(
            peaks.any { abs(it.energyKeV - 1460.8f) < PeakDetection.fwhmKeV(1460.8f) },
            "K-40 peak not found among ${peaks.map { it.energyKeV }}",
        )
    }

    @Test
    fun `finds an injected Cs-137 peak`() {
        val counts = syntheticSpectrum(661.7f to 120.0, 1460.8f to 60.0, noise = Random(7))
        val peaks = PeakDetection.detect(counts, CALIBRATION)
        assertTrue(
            peaks.any { abs(it.energyKeV - 661.7f) < PeakDetection.fwhmKeV(661.7f) },
            "Cs-137 peak not found among ${peaks.map { it.energyKeV }}",
        )
    }

    @Test
    fun `reports no peaks on a smooth continuum`() {
        val counts = syntheticSpectrum()
        val peaks = PeakDetection.detect(counts, CALIBRATION)
        assertTrue(peaks.isEmpty(), "false peaks on pure continuum: ${peaks.map { it.energyKeV }}")
    }

    @Test
    fun `no false Cs-137 on a noisy background without a source`() {
        val counts = syntheticSpectrum(1460.8f to 60.0, noise = Random(3))
        val hints = IsotopeMatcher.match(PeakDetection.detect(counts, CALIBRATION))
        assertTrue(
            hints.none { it.isotope == "Cs-137" },
            "phantom Cs-137 on background: $hints",
        )
    }

    @Test
    fun `flat spectrum stays quiet`() {
        val counts = List(1024) { 50 }
        assertTrue(PeakDetection.detect(counts, CALIBRATION).isEmpty())
    }

    @Test
    fun `merged candidates report one peak per line`() {
        val counts = syntheticSpectrum(661.7f to 200.0)
        val peaks = PeakDetection.detect(counts, CALIBRATION)
            .filter { abs(it.energyKeV - 661.7f) < 2 * PeakDetection.fwhmKeV(661.7f) }
        assertTrue(peaks.size == 1, "expected one merged Cs-137 peak, got $peaks")
    }

    @Test
    fun `fwhm model matches the 8 percent spec point at 662 keV`() {
        assertTrue(abs(PeakDetection.fwhmKeV(662f) - 0.08f * 662f) < 0.5f)
    }

    @Test
    fun `a one-channel spike is not a peak, however significant it is`() {
        // Ровно тот случай, ради которого добавлена проверка ширины: узкий
        // выброс имеет огромную значимость, но фотопик такой ширины
        // невозможен — разрешение детектора задаёт конечную FWHM.
        val counts = MutableList(1024) { continuum(it) }
        val channel = CALIBRATION.channelAt(900f).toInt()
        counts[channel] = counts[channel] + 20_000.0
        val peaks = PeakDetection.detect(counts.map { it.toInt() }, CALIBRATION)
        assertTrue(
            peaks.none { abs(it.energyKeV - 900f) < 30f },
            "одноканальный выброс принят за пик: $peaks",
        )
    }

    @Test
    fun `significance carries the uncertainty of the background estimate too`() {
        // Значимость = нетто/σ(нетто); σ² включает и статистику окна пика, и
        // неопределённость оценки континуума, поэтому она СТРОГО меньше
        // прежнего net/√(B·width).
        val counts = MutableList(1024) { continuum(it) }
        counts.addPeak(662f, amplitude = 3_000.0)
        val peak = PeakDetection.detect(counts.map { it.toInt() }, CALIBRATION)
            .minByOrNull { abs(it.energyKeV - 662f) }
        assertTrue(peak != null, "пик 662 кэВ не найден")
        val net = peak!!.netCounts
        val optimistic = net / sqrt(net.toDouble()).toFloat()
        assertTrue(
            peak.significance < optimistic,
            "значимость ${peak.significance} не может быть выше оптимистичной $optimistic",
        )
        assertTrue(peak.significance > 4f, "настоящий пик обязан остаться значимым")
    }
}
