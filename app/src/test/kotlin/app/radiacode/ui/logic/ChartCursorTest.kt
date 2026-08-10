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
        mean = value,
        sigma = 0f,
        sampleCount = 1,
    )

    // --- live-follow / pause ---

    @Test
    fun `placing the crosshair suspends live-follow and shows the pause`() {
        val state = ChartInteractions.cursorAt(ChartInteraction(follow = true), 0.4f)
        assertFalse(state.follow)
        assertEquals(0.4f, state.cursorFraction)
        assertTrue(state.paused)
    }

    @Test
    fun `the crosshair fraction is clamped to the plot`() {
        assertEquals(0f, ChartInteractions.cursorAt(ChartInteraction(), -3f).cursorFraction)
        assertEquals(1f, ChartInteractions.cursorAt(ChartInteraction(), 9f).cursorFraction)
    }

    @Test
    fun `dismissing resumes following only at the live edge`() {
        val paused = ChartInteractions.cursorAt(ChartInteraction(), 0.5f)
        assertTrue(ChartInteractions.dismissCursor(paused, atLiveEdge = true).follow)
        assertFalse(ChartInteractions.dismissCursor(paused, atLiveEdge = false).follow)
        assertNull(ChartInteractions.dismissCursor(paused, atLiveEdge = false).cursorFraction)
    }

    @Test
    fun `a pan into the past stops following, a pan back to now resumes it`() {
        val live = ChartInteraction(follow = true)
        assertFalse(ChartInteractions.afterTransform(live, atLiveEdge = false).follow)
        val parked = ChartInteraction(follow = false)
        assertTrue(ChartInteractions.afterTransform(parked, atLiveEdge = true).follow)
    }

    @Test
    fun `a transform always drops the crosshair, it referred to another range`() {
        val withCursor = ChartInteraction(follow = false, cursorFraction = 0.3f)
        assertNull(ChartInteractions.afterTransform(withCursor, atLiveEdge = false).cursorFraction)
    }

    @Test
    fun `jump to now and period change are both fully live states`() {
        for (state in listOf(ChartInteractions.jumpToNow(), ChartInteractions.periodChanged())) {
            assertTrue(state.follow)
            assertNull(state.cursorFraction)
            assertFalse(state.paused)
        }
    }

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

    // --- ratio to the usual band ---

    @Test
    fun `the ratio only speaks about an excess over the usual band`() {
        assertEquals(4.8f, CursorReadout.ratioToUsual(0.48f, 0.10f)!!, 1e-5f)
        assertNull(CursorReadout.ratioToUsual(0.05f, 0.10f))
        assertNull(CursorReadout.ratioToUsual(0.5f, null))
        assertNull(CursorReadout.ratioToUsual(0.5f, 0f))
    }

    @Test
    fun `the ratio label uses a decimal comma`() {
        assertEquals("×4,8 к привычному", CursorReadout.ratioLabel(4.8f))
    }
}
