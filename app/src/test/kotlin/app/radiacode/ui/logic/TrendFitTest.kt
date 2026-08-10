package app.radiacode.ui.logic

import app.radiacode.data.DoseUnitSetting
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrendFitTest {

    @Test
    fun `flat series has zero slope`() {
        val slope = TrendFit.slopePerHour(List(10) { 0.11f }, bucketMillis = 60_000)!!
        assertTrue(abs(slope) < 1e-6f)
    }

    @Test
    fun `linear rise recovers the exact per-hour slope`() {
        // +0.01 per bucket, bucket = 1 min -> +0.6/h.
        val columns = List(10) { 0.1f + 0.01f * it }
        val slope = TrendFit.slopePerHour(columns, bucketMillis = 60_000)!!
        assertTrue(abs(slope - 0.6f) < 1e-4f)
    }

    @Test
    fun `gaps are skipped, not interpolated`() {
        val columns = listOf(0.1f, null, 0.12f, null, 0.14f)
        // Present points (0,0.1),(2,0.12),(4,0.14): slope 0.01/bucket = 0.6/h at 1 min.
        val slope = TrendFit.slopePerHour(columns, bucketMillis = 60_000)!!
        assertTrue(abs(slope - 0.6f) < 1e-4f)
    }

    @Test
    fun `fewer than two points is honest null`() {
        assertNull(TrendFit.slopePerHour(listOf(null, 0.1f, null), bucketMillis = 60_000))
        assertNull(TrendFit.slopePerHour(emptyList(), bucketMillis = 60_000))
    }

    @Test
    fun `label carries sign arrow and comma decimals`() {
        assertEquals("+0,004 ↗", TrendFit.label(0.004f, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("−0,012 ↘", TrendFit.label(-0.012f, DoseUnitSetting.MICRO_SIEVERT))
        // Below the flatness epsilon the arrow reads flat.
        assertEquals("+0,000 →", TrendFit.label(0.0002f, DoseUnitSetting.MICRO_SIEVERT))
        // µR display unit is 100×: one decimal.
        assertEquals("+0,4 ↗", TrendFit.label(0.004f, DoseUnitSetting.MICRO_ROENTGEN))
    }
}
