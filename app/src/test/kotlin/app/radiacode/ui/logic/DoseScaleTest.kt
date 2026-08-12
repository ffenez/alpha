package app.radiacode.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoseScaleTest {

    // --- linear ---

    @Test
    fun `linear scale maps its bottom to zero and its top to one`() {
        val scale = LinearDoseScale(0.5f)
        assertEquals(0f, scale.fractionOrNull(0f))
        assertEquals(1f, scale.fractionOrNull(0.5f))
        assertEquals(0.5f, scale.fractionOrNull(0.25f)!!, 1e-5f)
        // Out of frame clamps rather than escaping the plot.
        assertEquals(1f, scale.fractionOrNull(9f))
    }

    // --- logarithmic ---

    @Test
    fun `log scale spreads decades evenly`() {
        val scale = LogDoseScale(0.01f, 10f)
        assertEquals(0f, scale.fractionOrNull(0.01f)!!, 1e-5f)
        assertEquals(1f, scale.fractionOrNull(10f)!!, 1e-5f)
        assertEquals(1f / 3f, scale.fractionOrNull(0.1f)!!, 1e-5f)
        assertEquals(2f / 3f, scale.fractionOrNull(1f)!!, 1e-5f)
    }

    @Test
    fun `log scale refuses to place zero or negative values`() {
        val scale = LogDoseScale(0.01f, 10f)
        assertNull(scale.fractionOrNull(0f))
        assertNull(scale.fractionOrNull(-1f))
    }

    @Test
    fun `dropped buckets are counted so the screen can say so`() {
        val zero = ChartBucket(0, 1_000, 0f, 0f, 0f)
        val ok = ChartBucket(1_000, 2_000, 0.1f, 0.1f, 0.1f)
        assertEquals(1, DoseScales.logDroppedBuckets(listOf(zero, ok, null)))
        assertEquals(0, DoseScales.logDroppedBuckets(listOf(ok)))
    }

    @Test
    fun `log ticks are one two five per decade inside the frame`() {
        val ticks = LogDoseScale(0.01f, 10f).ticks()
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it > 0.01f && it < 10f })
        assertTrue(ticks.zipWithNext().all { (a, b) -> a < b })
        assertTrue(ticks.any { abs(it - 0.1f) < 1e-6f })
        assertTrue(ticks.any { abs(it - 0.2f) < 1e-6f })
        assertTrue(ticks.any { abs(it - 0.5f) < 1e-6f })
    }

    @Test
    fun `log frame snaps to whole decades around the data`() {
        val scale = DoseScales.of(
            logarithmic = true,
            lows = listOf(0.05f),
            highs = listOf(0.4f),
            minSpan = 0.04f,
            alarmLevel = 0.3f,
        ) as LogDoseScale
        assertEquals(0.01f, scale.minValue, 1e-6f)
        assertTrue(scale.maxValue >= 0.4f)
        // Whole decade: log10 of the top is an integer.
        val decades = Math.log10(scale.maxValue.toDouble())
        assertTrue(abs(decades - Math.round(decades)) < 1e-6)
    }

    @Test
    fun `log frame never sinks below the physical floor`() {
        val scale = DoseScales.of(
            logarithmic = true,
            lows = listOf(0f),
            highs = listOf(0.2f),
            minSpan = 0.04f,
        )
        assertTrue(scale.minValue >= DoseScales.LOG_FLOOR_MICRO_SV_H)
        assertTrue(scale.maxValue > scale.minValue)
    }

    // --- robust autoscale (4.md) ---

    /** Ровный фон 0,14–0,18 — то, что прибор показывает почти всегда. */
    private fun background(): Pair<List<Float>, List<Float>> =
        List(60) { 0.14f + (it % 5) * 0.01f } to List(60) { 0.15f + (it % 5) * 0.01f }

    @Test
    fun `the background fills the frame instead of hugging the top of a zero axis`() {
        val (lows, highs) = background()
        val scale = DoseScales.of(
            logarithmic = false,
            lows = lows,
            highs = highs,
            minSpan = 0.04f,
        )
        // Нулевая ось отдала бы фону меньше четверти высоты; кадр по данным —
        // почти всю.
        assertTrue(scale.minValue > 0.1f, "minValue=${scale.minValue}")
        assertTrue(scale.maxValue < 0.25f, "maxValue=${scale.maxValue}")
        val occupied = (0.19f - 0.14f) / (scale.maxValue - scale.minValue)
        assertTrue(occupied > 0.5f, "данные занимают только ${occupied * 100} % кадра")
    }

    @Test
    fun `a distant alarm level does not stretch the axis, a near one does`() {
        val (lows, highs) = background()
        val distant = DoseScales.of(
            logarithmic = false,
            lows = lows,
            highs = highs,
            minSpan = 0.04f,
            alarmLevel = 0.3f,
        )
        // §4.md: далёкий L1 не уничтожает масштаб — его несёт указатель у кромки.
        assertTrue(distant.maxValue < 0.3f, "maxValue=${distant.maxValue}")

        val near = DoseScales.of(
            logarithmic = false,
            lows = lows,
            highs = highs,
            minSpan = 0.04f,
            alarmLevel = 0.2f,
        )
        assertTrue(near.maxValue >= 0.2f, "maxValue=${near.maxValue}")
    }

    @Test
    fun `one spike does not squeeze the rest of the series`() {
        val lows = List(100) { 0.15f } + listOf(0.15f)
        val highs = List(100) { 0.16f } + listOf(12f)
        val scale = DoseScales.of(
            logarithmic = false,
            lows = lows,
            highs = highs,
            minSpan = 0.04f,
        )
        assertTrue(scale.maxValue < 1f, "выброс определил кадр: maxValue=${scale.maxValue}")
    }

    @Test
    fun `an almost constant level is not magnified into wild swings`() {
        val scale = DoseScales.of(
            logarithmic = false,
            lows = List(50) { 0.1500f },
            highs = List(50) { 0.1502f },
            minSpan = 0.04f,
        )
        assertEquals(0.04f, scale.maxValue - scale.minValue, 1e-5f)
    }

    @Test
    fun `the frame never goes below zero`() {
        val scale = DoseScales.of(
            logarithmic = false,
            lows = List(10) { 0.001f },
            highs = List(10) { 0.002f },
            minSpan = 0.04f,
        )
        assertEquals(0f, scale.minValue)
    }

    @Test
    fun `without data the frame still shows what is known`() {
        val scale = DoseScales.of(
            logarithmic = false,
            lows = emptyList(),
            highs = emptyList(),
            minSpan = 0.04f,
            alarmLevel = 0.3f,
        )
        assertTrue(scale.maxValue >= 0.3f)
    }

    @Test
    fun `ticks of a lifted frame stay inside it and keep nice steps`() {
        val scale = LinearDoseScale(maxValue = 0.19f, minValue = 0.13f)
        val ticks = scale.ticks()
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it > scale.minValue && it < scale.maxValue }, "$ticks")
        assertTrue(ticks.zipWithNext().all { (a, b) -> b > a })
        // Шаг «красивый»: 0,01 / 0,02 / 0,05 …
        val step = ticks.zipWithNext().map { (a, b) -> b - a }.firstOrNull()
        if (step != null) {
            val mantissa = step / Math.pow(10.0, Math.floor(Math.log10(step.toDouble()))).toFloat()
            assertTrue(
                abs(mantissa - 1f) < 1e-3 || abs(mantissa - 2f) < 1e-3 || abs(mantissa - 5f) < 1e-3,
                "step=$step",
            )
        }
    }
}
