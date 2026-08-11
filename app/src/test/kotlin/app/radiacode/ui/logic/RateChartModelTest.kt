package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The search chart has one job the ordinary «fit the data» axis actively
 * fights: while the user walks towards a source the rate grows, and an axis
 * that follows it hides exactly that growth (redesign §2).
 */
class RateAutoScaleTest {

    @Test
    fun `nice tops walk the 1-2-5 ladder`() {
        assertEquals(RateAutoScale.MIN_TOP, RateAutoScale.niceTop(0f), "an empty frame has a floor")
        assertEquals(10f, RateAutoScale.niceTop(7f))
        assertEquals(20f, RateAutoScale.niceTop(11f))
        assertEquals(50f, RateAutoScale.niceTop(21f))
        assertEquals(100f, RateAutoScale.niceTop(60f))
        assertEquals(200f, RateAutoScale.niceTop(101f))
    }

    @Test
    fun `the first frame simply fits the data with headroom`() {
        val state = RateAutoScale.next(null, nowMillis = 0L, required = 30f, excursionConfirmed = false)
        assertTrue(state.top >= 30f * RateAutoScale.HEADROOM)
        assertNull(state.shrinkPendingSinceMillis)
    }

    @Test
    fun `growth is immediate - a clipped line is never acceptable`() {
        val start = RateAutoScale.next(null, 0L, required = 30f, excursionConfirmed = false)
        val grown = RateAutoScale.next(start, 1_000L, required = start.top + 1f, excursionConfirmed = false)
        assertTrue(grown.top > start.top, "${grown.top} vs ${start.top}")
        assertTrue(grown.top >= start.top + 1f)
    }

    @Test
    fun `growth happens even mid-excursion`() {
        val start = RateAutoScale.next(null, 0L, required = 30f, excursionConfirmed = false)
        val grown = RateAutoScale.next(start, 1_000L, required = start.top * 3f, excursionConfirmed = true)
        assertTrue(grown.top >= start.top * 3f)
    }

    @Test
    fun `inside the dead zone nothing moves`() {
        val start = RateAutoScale.next(null, 0L, required = 40f, excursionConfirmed = false)
        val inside = start.top * (RateAutoScale.SHRINK_FRACTION + 0.1f)
        var state = start
        for (t in 1..30) {
            state = RateAutoScale.next(state, t * 1_000L, required = inside, excursionConfirmed = false)
        }
        assertEquals(start.top, state.top)
    }

    @Test
    fun `shrinking waits for the hold time and then happens in one step`() {
        val start = RateAutoScale.next(null, 0L, required = 100f, excursionConfirmed = false)
        val low = start.top * (RateAutoScale.SHRINK_FRACTION - 0.1f)

        var state = RateAutoScale.next(start, 1_000L, required = low, excursionConfirmed = false)
        assertEquals(1_000L, state.shrinkPendingSinceMillis)
        assertEquals(start.top, state.top, "must not move while pending")

        state = RateAutoScale.next(
            state,
            1_000L + RateAutoScale.SHRINK_HOLD_MILLIS - 1,
            required = low,
            excursionConfirmed = false,
        )
        assertEquals(start.top, state.top)

        state = RateAutoScale.next(
            state,
            1_000L + RateAutoScale.SHRINK_HOLD_MILLIS,
            required = low,
            excursionConfirmed = false,
        )
        assertTrue(state.top < start.top, "${state.top} vs ${start.top}")
    }

    @Test
    fun `a confirmed excursion freezes the frame`() {
        val start = RateAutoScale.next(null, 0L, required = 100f, excursionConfirmed = false)
        val low = start.top * (RateAutoScale.SHRINK_FRACTION - 0.1f)
        var state = start
        for (t in 1..30) {
            state = RateAutoScale.next(state, t * 1_000L, required = low, excursionConfirmed = true)
        }
        assertEquals(start.top, state.top)
        assertNull(state.shrinkPendingSinceMillis)
    }

    @Test
    fun `a brief return into the dead zone cancels a pending shrink`() {
        val start = RateAutoScale.next(null, 0L, required = 100f, excursionConfirmed = false)
        val low = start.top * (RateAutoScale.SHRINK_FRACTION - 0.1f)
        val inside = start.top * (RateAutoScale.SHRINK_FRACTION + 0.1f)

        var state = RateAutoScale.next(start, 1_000L, required = low, excursionConfirmed = false)
        state = RateAutoScale.next(state, 2_000L, required = inside, excursionConfirmed = false)
        assertNull(state.shrinkPendingSinceMillis)

        state = RateAutoScale.next(
            state,
            2_000L + RateAutoScale.SHRINK_HOLD_MILLIS,
            required = low,
            excursionConfirmed = false,
        )
        assertEquals(start.top, state.top, "the hold time must restart")
    }
}

class RateChartModelTest {

    private fun point(second: Long, cps: Float, confirmed: Boolean = false) =
        SearchPoint(second * 1_000L, cps, confirmed)

    @Test
    fun `a hole longer than two records breaks the line`() {
        assertTrue(!RateChartModel.isGap(0L, 1_000L))
        assertTrue(!RateChartModel.isGap(0L, 2_000L))
        assertTrue(RateChartModel.isGap(0L, 5_000L))
    }

    @Test
    fun `confirmed spans are the maximal runs, including one at the end`() {
        val points = listOf(
            point(0, 25f),
            point(1, 90f, confirmed = true),
            point(2, 92f, confirmed = true),
            point(3, 26f),
            point(4, 88f, confirmed = true),
        )
        assertEquals(listOf(1..2, 4..4), RateChartModel.confirmedSpans(points))
    }

    @Test
    fun `no confirmed readings means no spans`() {
        val points = listOf(point(0, 25f), point(1, 26f))
        assertTrue(RateChartModel.confirmedSpans(points).isEmpty())
    }

    @Test
    fun `the frame must fit both the data and the whole background band`() {
        val points = listOf(point(0, 25f), point(1, 31f))
        assertEquals(40f, RateChartModel.requiredTop(points, bandTop = 40f))
        assertEquals(31f, RateChartModel.requiredTop(points, bandTop = 20f))
        assertEquals(31f, RateChartModel.requiredTop(points, bandTop = null))
        assertEquals(0f, RateChartModel.requiredTop(emptyList(), bandTop = null))
    }
}
