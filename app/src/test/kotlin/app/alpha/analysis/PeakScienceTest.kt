package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Научные проверки поиска пиков на синтетике с ЗАРАНЕЕ известной истиной
 * (`SPECTRUM_VALIDATION.md` §15): спектры строятся из заданных площади,
 * положения и ширины, и проверяется, что анализ возвращает их обратно.
 *
 * Тесты намеренно не повторяют реализацию: ожидаемые числа берутся из
 * параметров генератора, а не из того, что вернул анализ.
 */
class PeakScienceTest {

    /** Квадратичная шкала прибора из fixture: 1024 канала на 0…2804 кэВ. */
    private val calibration = EnergyCalibration(a0 = 6.8822f, a1 = 2.3377f, a2 = 3.8714e-4f)
    private val resolution = 0.084f

    private fun sigmaChannels(energyKeV: Float): Double {
        val channel = calibration.channelAt(energyKeV)
        val keVPerChannel = calibration.a1 + 2f * calibration.a2 * channel
        return PeakDetection.fwhmKeV(energyKeV, resolution) / 2.3548 / keVPerChannel
    }

    /**
     * Спектр: экспоненциальный континуум [base]·exp(−ch/[decay]) плюс
     * гауссианы заданной ПЛОЩАДИ; [seed] включает пуассоновский шум.
     */
    private fun spectrum(
        peaks: List<Pair<Float, Double>>,
        base: Double = 400.0,
        decay: Double = 180.0,
        seed: Int? = null,
        channels: Int = 1024,
        floor: Double = 5.0,
    ): List<Int> {
        val values = MutableList(channels) { base * exp(-it / decay) + floor }
        for ((energyKeV, area) in peaks) {
            val center = calibration.channelAt(energyKeV).toDouble()
            val sigma = sigmaChannels(energyKeV)
            for (i in values.indices) {
                val z = (i - center) / sigma
                values[i] += area / (sigma * sqrt(2 * Math.PI)) * exp(-0.5 * z * z)
            }
        }
        val random = seed?.let { Random(it) }
        return values.map { v ->
            if (random == null) v.toInt() else poisson(random, v)
        }
    }

    /** Пуассон через нормальное приближение при больших λ, иначе Кнут. */
    private fun poisson(random: Random, lambda: Double): Int {
        if (lambda > 30) {
            val u1 = random.nextDouble().coerceAtLeast(1e-12)
            val u2 = random.nextDouble()
            val g = sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
            return (lambda + g * sqrt(lambda)).coerceAtLeast(0.0).toInt()
        }
        var k = 0
        var p = 1.0
        val target = exp(-lambda)
        while (true) {
            p *= random.nextDouble()
            if (p <= target) return k
            k++
        }
    }

    private fun detect(counts: List<Int>) = PeakDetection.detect(
        counts = counts,
        calibration = calibration,
        resolution662 = resolution,
        minEnergyKeV = 40f,
    )

    @Test
    fun `a broad weak line on a few counts per channel is found, not filtered as a spike`() {
        // Тот самый класс дефекта: линия 2614,5 кэВ шириной 110 кэВ на
        // континууме в 2 импульса на канал. Ширина по одному каналу здесь
        // неизмерима, и прежний гейт формы выбрасывал линию целиком.
        val counts = spectrum(listOf(2614.5f to 500.0), base = 1_500.0, decay = 150.0, seed = 3)
        val peak = detect(counts).minByOrNull { abs(it.energyKeV - 2614.5f) }
        assertTrue(peak != null, "широкая слабая линия не найдена: ${detect(counts).map { it.energyKeV }}")
        assertTrue(
            abs(peak!!.energyKeV - 2614.5f) < PeakDetection.fwhmKeV(2614.5f, resolution) / 2f,
            "положение ${peak.energyKeV} далеко от истинного 2614,5",
        )
        assertTrue(peak.significance > 4f, "значимость ${peak.significance}")
    }

    @Test
    fun `a one-channel spike stays rejected`() {
        val values = spectrum(emptyList()).toMutableList()
        val channel = calibration.channelAt(900f).toInt()
        values[channel] = values[channel] + 20_000
        assertTrue(
            detect(values).none { abs(it.energyKeV - 900f) < 40f },
            "выброс принят за пик: ${detect(values).map { it.energyKeV }}",
        )
    }

    @Test
    fun `the centroid of a line on a steep continuum stays unbiased`() {
        // Континуум падает в e раз за 180 каналов: вычитание ПЛОСКОГО уровня
        // оставляло внутри окна ступеньку и уводило центроиду вниз по шкале.
        val truth = 662.0f
        val deviations = (1..12).map { seed ->
            val counts = spectrum(listOf(truth to 8_000.0), seed = seed)
            val peak = detect(counts).minByOrNull { abs(it.energyKeV - truth) }
            peak?.let { (it.energyKeV - truth).toDouble() } ?: Double.NaN
        }.filter { !it.isNaN() }
        assertTrue(deviations.size >= 10, "линия найдена лишь в ${deviations.size} из 12 спектров")
        val mean = deviations.average()
        // Систематика заметно меньше кванта шкалы (2,3 кэВ) и много меньше
        // ширины линии (56 кэВ).
        assertTrue(abs(mean) < 2.0, "среднее смещение центроиды $mean кэВ")
    }

    @Test
    fun `net area and significance reproduce the injected area`() {
        val area = 6_000.0
        val counts = spectrum(listOf(1460.8f to area), seed = 11)
        val peak = detect(counts).minByOrNull { abs(it.energyKeV - 1460.8f) }!!
        // Окно ±FWHM/2 содержит 76 % площади гауссианы: столько и должно
        // насчитаться нетто (± статистика).
        val expected = area * 0.761
        assertEquals(expected, peak.netCounts.toDouble(), 0.15 * expected)
        // Значимость порядка нетто/√валовых: проверяется порядок, а не
        // повторение формулы.
        assertTrue(peak.significance > 20f, "значимость ${peak.significance}")
    }

    @Test
    fun `two lines one FWHM apart do not merge into one`() {
        // 1173,2 и 1332,5 кэВ (Co-60) разнесены на 159 кэВ при FWHM 74–79 кэВ.
        val counts = spectrum(listOf(1173.2f to 9_000.0, 1332.5f to 9_000.0), seed = 5)
        val found = detect(counts)
        assertTrue(found.any { abs(it.energyKeV - 1173.2f) < 40f }, "нет 1173: ${found.map { it.energyKeV }}")
        assertTrue(found.any { abs(it.energyKeV - 1332.5f) < 40f }, "нет 1332: ${found.map { it.energyKeV }}")
    }

    @Test
    fun `a spectrum without lines yields no peaks`() {
        val counts = spectrum(emptyList(), seed = 17)
        assertEquals(emptyList(), detect(counts), "на чистом континууме найдены пики")
    }

    @Test
    fun `a spectrum with a handful of counts yields no significance at all`() {
        // Низкая статистика: 1–3 импульса на канал. Никакого «4σ» здесь быть
        // не может — нормальное приближение неприменимо ([MIN_GROSS_COUNTS]).
        val counts = spectrum(emptyList(), base = 3.0, decay = 4000.0, seed = 23, floor = 0.4)
        assertTrue(counts.sum() < 4_000, "проверочный спектр слишком богат: ${counts.sum()}")
        assertEquals(emptyList(), detect(counts))
    }

    @Test
    fun `a high-count spectrum keeps the line at its energy`() {
        val counts = spectrum(listOf(661.7f to 400_000.0), base = 4_000.0, seed = 29)
        val peak = detect(counts).minByOrNull { abs(it.energyKeV - 661.7f) }!!
        assertTrue(abs(peak.energyKeV - 661.7f) < 3f, "положение ${peak.energyKeV}")
        assertTrue(peak.significance > 100f, "значимость ${peak.significance}")
    }
}
