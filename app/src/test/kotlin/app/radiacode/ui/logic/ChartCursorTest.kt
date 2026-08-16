package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartCursorTest {

    private fun bucket(start: Long, value: Float = 0.1f) = ChartBucket(
        startMillis = start,
        endMillis = start + 1_000,
        min = value,
        max = value,
        median = value,
        sampleCount = 1,
    )

    // Правила слежения и паузы переехали в движок графика: они теперь
    // свойства окна, а не отдельного состояния экрана — см. `ViewportTest` и
    // `ChartGestureTest` в `ui/chart`.

    // --- column lookup ---

    @Test
    fun `the crosshair snaps to the nearest column by time`() {
        val buckets = listOf(bucket(0), bucket(10_000), bucket(20_000))
        assertEquals(0L, CursorReadout.nearestBucket(buckets, 100L)!!.startMillis)
        assertEquals(10_000L, CursorReadout.nearestBucket(buckets, 9_000L)!!.startMillis)
        assertEquals(20_000L, CursorReadout.nearestBucket(buckets, 99_000L)!!.startMillis)
    }

    @Test
    fun `an empty frame has no column under the crosshair`() {
        assertNull(CursorReadout.nearestBucket(emptyList(), 0L))
    }

    // --- ratio to a named statistic of the profile (spec §17) ---

    @Test
    fun `the ratio only speaks about an excess over its denominator`() {
        assertEquals(4.8f, CursorReadout.ratioTo(0.48f, 0.10f)!!, 1e-5f)
        assertNull(CursorReadout.ratioTo(0.05f, 0.10f))
        assertNull(CursorReadout.ratioTo(0.5f, null))
        assertNull(CursorReadout.ratioTo(0.5f, 0f))
    }

    @Test
    fun `the ratio label always names the denominator`() {
        assertEquals(
            "×4,8 к P90 профиля",
            CursorReadout.ratioLabel(4.8f, RatioDenominator.BASELINE_P90),
        )
        assertEquals(
            "×4,8 к медиане профиля",
            CursorReadout.ratioLabel(4.8f, RatioDenominator.BASELINE_MEDIAN),
        )
        // «×4,8 к привычному» is the exact wording the spec forbids (§17, §39).
        for (denominator in RatioDenominator.entries) {
            val label = CursorReadout.ratioLabel(1.5f, denominator)
            assertFalse(label.contains("привычн"), label)
            assertFalse(label.contains("обычн"), label)
            assertTrue(label.contains("профиля"), label)
        }
    }

    @Test
    fun `the ratio carries an explanation of what its denominator is`() {
        val p90 = CursorReadout.ratioExplanation(RatioDenominator.BASELINE_P90)
        assertTrue(p90.contains("90 %"), p90)
        assertTrue(p90.contains("не норматив"), p90)
        assertTrue(
            CursorReadout.ratioExplanation(RatioDenominator.BASELINE_MEDIAN).contains("половина"),
        )
    }

    // --- readout wording (spec §16) ---

    @Test
    fun `the readout names the interval the column covers, not one instant`() {
        val label = CursorReadout.binRangeLabel(bucket(0)) { millis -> "t$millis" }
        assertEquals("t0–t1000", label)
    }

    @Test
    fun `an extremum time is an instant only when the aggregation knows it`() {
        assertEquals(
            "в t5000",
            CursorReadout.extremeTimeLabel(5_000L, 1_000L) { millis -> "t$millis" },
        )
        assertEquals(
            "в t5000–t65000",
            CursorReadout.extremeTimeLabel(5_000L, 60_000L) { millis -> "t$millis" },
        )
    }
}
