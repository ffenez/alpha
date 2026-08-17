package app.alpha.analysis

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ось энергии спектрограммы: две шкалы, один растр.
 *
 * Проверяется то, ради чего шкала вообще выбирается руками: линейная ось
 * ЛИНЕЙНА (равная высота = равные кэВ), геометрическая — геометрична (равная
 * высота = равное отношение), и ни одна из них не переставляет полосы местами.
 * Растр строится по долям высоты, поэтому именно на них и стоят проверки.
 */
class SpectrogramEnergyScaleTest {

    private val log = Spectrogram.EnergyScale.LOG
    private val linear = Spectrogram.EnergyScale.LINEAR

    @Test
    fun `both scales pin the ends of the range`() {
        for (scale in listOf(log, linear)) {
            assertEquals(0f, assertNotNull(Spectrogram.fractionOfEnergy(Spectrogram.MIN_KEV, scale)), 1e-4f)
            assertEquals(1f, assertNotNull(Spectrogram.fractionOfEnergy(Spectrogram.MAX_KEV, scale)), 1e-4f)
        }
    }

    /** Равные доли высоты — равные кэВ: это и есть «линейная». */
    @Test
    fun `the linear scale keeps equal keV per equal height`() {
        val step = Spectrogram.energyAtFraction(0.5f, linear) -
            Spectrogram.energyAtFraction(0.25f, linear)
        val other = Spectrogram.energyAtFraction(0.9f, linear) -
            Spectrogram.energyAtFraction(0.65f, linear)
        assertTrue(abs(step - other) < 1f, "$step vs $other")
    }

    /** Равные доли высоты — равные ОТНОШЕНИЯ: это и есть «геометрическая». */
    @Test
    fun `the log scale keeps equal ratios per equal height`() {
        val ratio = Spectrogram.energyAtFraction(0.5f, log) / Spectrogram.energyAtFraction(0.25f, log)
        val other = Spectrogram.energyAtFraction(0.9f, log) / Spectrogram.energyAtFraction(0.65f, log)
        assertTrue(abs(ratio - other) < 0.01f, "$ratio vs $other")
    }

    @Test
    fun `fraction and energy are inverse of each other`() {
        for (scale in listOf(log, linear)) {
            for (keV in listOf(30f, 100f, 662f, 1460f, 2600f)) {
                val fraction = assertNotNull(Spectrogram.fractionOfEnergy(keV, scale))
                assertEquals(keV, Spectrogram.energyAtFraction(fraction, scale), keV * 0.001f)
            }
        }
    }

    /**
     * Растр — ВЫБОРКА полос, а не их перестановка: с ростом высоты номер полосы
     * не убывает ни на одной шкале, иначе картинка перевернула бы энергию.
     */
    @Test
    fun `rows sample the bands in energy order`() {
        for (scale in listOf(log, linear)) {
            var previous = -1
            for (row in 0..200) {
                val band = assertNotNull(Spectrogram.bandOfFraction(row / 200f, scale))
                assertTrue(band in 0 until Spectrogram.BAND_COUNT, "$band")
                assertTrue(band >= previous, "$scale: $band после $previous")
                previous = band
            }
        }
    }

    /** Обе шкалы покрывают все полосы: ни одна не выпадает из картинки. */
    @Test
    fun `every band gets at least one row`() {
        for (scale in listOf(log, linear)) {
            val rows = 192
            val seen = (0 until rows)
                .mapNotNull { Spectrogram.bandOfFraction((rows - 0.5f - it) / rows, scale) }
                .toSet()
            assertTrue(
                seen.size >= Spectrogram.BAND_COUNT / 2,
                "$scale: полос в растре ${seen.size}",
            )
        }
    }

    /** Засечки стоят внутри диапазона и на равномерной оси — равномерно. */
    @Test
    fun `each scale carries its own ticks`() {
        assertTrue(Spectrogram.ticksKeV(log) != Spectrogram.ticksKeV(linear))
        for (scale in listOf(log, linear)) {
            for (keV in Spectrogram.ticksKeV(scale)) {
                assertNotNull(Spectrogram.fractionOfEnergy(keV, scale), "$keV")
            }
        }
        val linearTicks = Spectrogram.ticksKeV(linear)
        val gaps = linearTicks.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { abs(it - gaps.first()) < 1f }, "$gaps")
    }
}
