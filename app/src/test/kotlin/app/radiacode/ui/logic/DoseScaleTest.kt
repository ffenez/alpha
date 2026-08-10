package app.radiacode.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoseScaleTest {

    // --- linear ---

    @Test
    fun `linear scale maps zero to the bottom and the top to one`() {
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
            dataMin = 0.05f,
            dataMax = 0.4f,
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
        val scale = DoseScales.of(logarithmic = true, dataMin = 0f, dataMax = 0.2f)
        assertTrue(scale.minValue >= DoseScales.LOG_FLOOR_MICRO_SV_H)
        assertTrue(scale.maxValue > scale.minValue)
    }

    @Test
    fun `linear frame keeps the alarm line in view when it is reachable`() {
        val scale = DoseScales.of(
            logarithmic = false,
            dataMin = 0.05f,
            dataMax = 0.2f,
            alarmLevel = 0.3f,
        )
        assertTrue(scale.maxValue >= 0.3f)
    }
}
