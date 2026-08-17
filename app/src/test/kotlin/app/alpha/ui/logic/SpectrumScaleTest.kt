package app.alpha.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Все три масштаба — монотонные преобразования одного числа: они меняют
 * распределение высоты, но не порядок и не содержание.
 */
class SpectrumScaleTest {

    private val top = 10_000f

    private val scales = listOf(
        SpectrumScale.Linear,
        SpectrumScale.Power(2),
        SpectrumScale.Power(5),
        SpectrumScale.Log,
    )

    @Test
    fun `every scale is monotone and stays inside the field`() {
        for (scale in scales) {
            var previous = -1f
            for (value in listOf(0f, 1f, 10f, 100f, 1_000f, 5_000f, top)) {
                val fraction = scale.fraction(value, top)
                assertTrue(fraction in 0f..1f, "$scale: $value → $fraction")
                assertTrue(fraction >= previous, "$scale не монотонен на $value")
                previous = fraction
            }
            assertEquals(1f, scale.fraction(top, top), 1e-4f, scale.toString())
        }
    }

    @Test
    fun `power with root one is the linear scale`() {
        // Ползунок начинается с 1/1 именно поэтому: переход непрерывен, а не
        // прыгает в другой режим.
        for (value in listOf(0f, 250f, 5_000f, top)) {
            assertEquals(
                SpectrumScale.Linear.fraction(value, top),
                SpectrumScale.Power(1).fraction(value, top),
                1e-5f,
            )
        }
    }

    @Test
    fun `a bigger root lifts small values, approaching but never becoming log`() {
        val small = 100f
        val linear = SpectrumScale.Linear.fraction(small, top)
        val sqrt = SpectrumScale.Power(2).fraction(small, top)
        val deep = SpectrumScale.Power(10).fraction(small, top)
        val log = SpectrumScale.Log.fraction(small, top)
        assertTrue(linear < sqrt, "$linear !< $sqrt")
        assertTrue(sqrt < deep, "$sqrt !< $deep")
        // Степенной подходит к логарифму, но остаётся другим преобразованием:
        // на нуле он равен нулю, а логарифм там имеет пол в один отсчёт.
        assertTrue(abs(deep - log) > 1e-4f, "$deep vs $log")
        assertEquals(0f, SpectrumScale.Power(10).fraction(0f, top))
    }

    @Test
    fun `log minor ticks fill the decade and stay inside the frame`() {
        val minor = SpectrumScale.Log.minorTicks(top)
        assertTrue(minor.isNotEmpty())
        assertTrue(minor.all { it < top && it > 0f }, "$minor")
        // Восемь линий на декаду: 2…9.
        assertTrue(minor.count { it in 1f..9.9f } == 8, "$minor")
        // И они не совпадают с подписанными делениями.
        val major = SpectrumScale.Log.ticks(top).toSet()
        assertTrue(minor.none { it in major })
    }

    @Test
    fun `power ticks are evenly spaced on screen, uneven in value`() {
        val scale = SpectrumScale.Power(3)
        val ticks = scale.ticks(top)
        val fractions = ticks.map { scale.fraction(it, top) }
        // Равные расстояния по высоте — 0,25 / 0,5 / 0,75.
        for ((index, expected) in listOf(0.25f, 0.5f, 0.75f).withIndex()) {
            assertEquals(expected, fractions[index], 1e-3f, "$fractions")
        }
        // При этом сами значения неравномерны — шкала неравномерна, и подписи
        // обязаны это показывать.
        assertTrue(ticks[1] - ticks[0] < ticks[2] - ticks[1])
    }

    @Test
    fun `an unknown stored mode falls back to log, and the root is clamped`() {
        assertEquals(SpectrumScale.Log, SpectrumScale.of(null, 2))
        assertEquals(SpectrumScale.Log, SpectrumScale.of("mystery", 2))
        assertEquals(SpectrumScale.Linear, SpectrumScale.of("linear", 2))
        assertEquals(SpectrumScale.Power(4), SpectrumScale.of("power", 4))
        // За пределами лестницы масштаб остаётся определённым.
        assertTrue(SpectrumScale.Power(99).fraction(100f, top) in 0f..1f)
        assertTrue(SpectrumScale.Power(0).fraction(100f, top) in 0f..1f)
    }
}
