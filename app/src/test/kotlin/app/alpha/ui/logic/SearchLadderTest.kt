package app.alpha.ui.logic

import app.alpha.analysis.CountWindow
import app.alpha.analysis.RateComparison
import app.alpha.analysis.RateComparisonResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ladder is what keeps a single bright second from becoming a find
 * (search redesign §11): every step up costs both magnitude *and* time.
 */
class SearchLadderTest {

    /** A deterministic window whose scatter is a known multiple of the mean. */
    private fun window(rate: Double, seconds: Int): CountWindow {
        val times = LongArray(seconds) { it * 1_000L }
        val rates = DoubleArray(seconds) { i ->
            (rate + if (i % 2 == 0) 1.0 else -1.0).coerceAtLeast(0.0)
        }
        return CountWindow.reconstruct(times, rates)
    }

    private val background = window(rate = 25.0, seconds = 45)

    private fun compare(rate: Double, seconds: Int = 3): RateComparisonResult =
        RateComparison.compare(window(rate, seconds), background)

    private fun step(
        state: SearchLadderState,
        rate: Double?,
        nowMillis: Long,
        direction: SearchDirection = SearchDirection.UNKNOWN,
        seconds: Int = 3,
    ): SearchLadderState = SearchLadder.step(
        state,
        LadderInput(
            nowMillis = nowMillis,
            comparison = rate?.let { compare(it, seconds) },
            streamFresh = rate != null,
            direction = direction,
        ),
    )

    @Test
    fun `a background stream never leaves the background rung`() {
        var state = SearchLadderState()
        for (second in 0..60) {
            state = step(state, rate = 25.0, nowMillis = second * 1_000L)
            assertEquals(SearchLevel.BACKGROUND, state.level, "at ${second}s")
        }
        assertTrue(state.spikes.isEmpty())
    }

    @Test
    fun `one differing window is a possible change, never a confirmation`() {
        val state = step(SearchLadderState(), rate = 90.0, nowMillis = 1_000L)
        assertEquals(SearchLevel.POSSIBLE_CHANGE, state.level)
        assertTrue(!state.confirmed)
        assertNull(state.confirmedSinceMillis)
    }

    @Test
    fun `a sustained excess confirms exactly at the confirmation time`() {
        var state = SearchLadderState()
        state = step(state, rate = 90.0, nowMillis = 0L)
        assertEquals(SearchLevel.POSSIBLE_CHANGE, state.level)

        state = step(state, rate = 90.0, nowMillis = SearchLadder.CONFIRM_MILLIS - 1)
        assertEquals(SearchLevel.POSSIBLE_CHANGE, state.level)

        state = step(state, rate = 90.0, nowMillis = SearchLadder.CONFIRM_MILLIS)
        assertEquals(SearchLevel.CONFIRMED_EXCESS, state.level)
        assertEquals(SearchLadder.CONFIRM_MILLIS, state.confirmedSinceMillis)
        assertTrue(state.confirmed)
    }

    @Test
    fun `a confirmed state survives a short quiet stretch and releases after it`() {
        var state = SearchLadderState()
        state = step(state, rate = 90.0, nowMillis = 0L)
        state = step(state, rate = 90.0, nowMillis = SearchLadder.CONFIRM_MILLIS)
        assertTrue(state.confirmed)

        val quietFrom = SearchLadder.CONFIRM_MILLIS + 1_000L
        state = step(state, rate = 25.0, nowMillis = quietFrom)
        assertEquals(SearchLevel.CONFIRMED_EXCESS, state.level, "one quiet window must not flicker")

        state = step(state, rate = 25.0, nowMillis = quietFrom + SearchLadder.RELEASE_MILLIS - 1)
        assertEquals(SearchLevel.CONFIRMED_EXCESS, state.level)

        state = step(state, rate = 25.0, nowMillis = quietFrom + SearchLadder.RELEASE_MILLIS)
        assertEquals(SearchLevel.BACKGROUND, state.level)
        assertNull(state.confirmedSinceMillis)
    }

    @Test
    fun `a run that ends before confirmation is remembered as a marker, not a find`() {
        var state = SearchLadderState()
        state = step(state, rate = 90.0, nowMillis = 1_000L)
        state = step(state, rate = 90.0, nowMillis = 2_000L)
        state = step(state, rate = 25.0, nowMillis = 3_000L)

        assertEquals(SearchLevel.BACKGROUND, state.level)
        assertEquals(1, state.spikes.size)
        val spike = state.spikes.single()
        assertEquals(1_000L, spike.fromMillis)
        assertEquals(3_000L, spike.toMillis)
        assertTrue(spike.peakRatio > 3.0, "${spike.peakRatio}")
    }

    @Test
    fun `a barely-different run leaves no marker at all`() {
        // A long window makes even a 1,2× difference statistically clear — and
        // it still leaves no pin, because a marker is about standing out, not
        // about passing a test.
        val small = compare(rate = 30.0, seconds = 20)
        assertTrue(small.pValue < SearchLadder.ALPHA, "p = ${small.pValue}")
        assertTrue(small.excessConfirmedByInterval)
        assertTrue(small.ratio < SearchLadder.SPIKE_MIN_RATIO, "ratio = ${small.ratio}")

        var state = SearchLadderState()
        state = step(state, rate = 30.0, nowMillis = 1_000L, seconds = 20)
        state = step(state, rate = 25.0, nowMillis = 2_000L)
        assertTrue(state.spikes.isEmpty(), "${state.spikes}")
    }

    @Test
    fun `markers are capped so one sweep of a room cannot grow without bound`() {
        var state = SearchLadderState()
        var now = 0L
        repeat(SearchLadder.MAX_SPIKES + 5) {
            state = step(state, rate = 90.0, nowMillis = now + 1_000L)
            state = step(state, rate = 25.0, nowMillis = now + 2_000L)
            now += 2_000L
        }
        assertEquals(SearchLadder.MAX_SPIKES, state.spikes.size)
    }

    @Test
    fun `a lost stream drops the verdict but keeps the markers`() {
        var state = SearchLadderState()
        state = step(state, rate = 90.0, nowMillis = 1_000L)
        state = step(state, rate = 25.0, nowMillis = 2_000L)
        val spikes = state.spikes
        assertEquals(1, spikes.size)

        state = step(state, rate = null, nowMillis = 3_000L)
        assertEquals(SearchLevel.UNKNOWN, state.level)
        assertEquals(SearchDirection.UNKNOWN, state.direction)
        assertEquals(spikes, state.spikes)
    }

    @Test
    fun `a confirmed excess does not survive a lost stream`() {
        var state = SearchLadderState()
        state = step(state, rate = 90.0, nowMillis = 0L)
        state = step(state, rate = 90.0, nowMillis = SearchLadder.CONFIRM_MILLIS)
        assertTrue(state.confirmed)

        state = step(state, rate = null, nowMillis = SearchLadder.CONFIRM_MILLIS + 1_000L)
        assertEquals(SearchLevel.UNKNOWN, state.level)
    }

    @Test
    fun `a sustained deficit lands on its own rung, never on the excess one`() {
        var state = SearchLadderState()
        state = step(state, rate = 5.0, nowMillis = 0L)
        state = step(state, rate = 5.0, nowMillis = SearchLadder.CONFIRM_MILLIS)
        assertEquals(SearchLevel.CONFIRMED_DEFICIT, state.level)
        assertTrue(state.confirmed)
    }

    @Test
    fun `held duration is reported from the start of the difference`() {
        var state = SearchLadderState()
        state = step(state, rate = 90.0, nowMillis = 10_000L)
        state = step(state, rate = 90.0, nowMillis = 10_000L + SearchLadder.CONFIRM_MILLIS)
        assertEquals(
            SearchLadder.CONFIRM_MILLIS,
            state.confirmedForMillis(10_000L + SearchLadder.CONFIRM_MILLIS * 2),
        )
    }
}

/** «Теплее или холоднее» — the robust short-window slope. */
class SearchDirectionFitTest {

    private fun times(count: Int): LongArray = LongArray(count) { it * 1_000L }

    @Test
    fun `too few readings say nothing`() {
        val n = SearchDirectionFit.MIN_POINTS - 1
        assertEquals(
            SearchDirection.UNKNOWN,
            SearchDirectionFit.of(times(n), FloatArray(n) { 25f }),
        )
    }

    @Test
    fun `a flat stream is steady, not rising`() {
        val n = 10
        assertEquals(
            SearchDirection.STEADY,
            SearchDirectionFit.of(times(n), FloatArray(n) { 25f }),
        )
    }

    @Test
    fun `a clear climb is rising and its mirror is falling`() {
        val n = 10
        val rising = FloatArray(n) { 25f + it * 6f }
        assertEquals(SearchDirection.RISING, SearchDirectionFit.of(times(n), rising))

        val falling = FloatArray(n) { 79f - it * 6f }
        assertEquals(SearchDirection.FALLING, SearchDirectionFit.of(times(n), falling))
    }

    @Test
    fun `one outlying second cannot flip the arrow`() {
        val n = 10
        val flatWithSpike = FloatArray(n) { if (it == 4) 300f else 25f }
        assertEquals(SearchDirection.STEADY, SearchDirectionFit.of(times(n), flatWithSpike))
    }

    @Test
    fun `only the newest window counts`() {
        // 60 s of climb, then 10 s of flat: the arrow describes now, not the past.
        val n = 70
        val values = FloatArray(n) { if (it < 60) 25f + it * 2f else 145f }
        assertEquals(SearchDirection.STEADY, SearchDirectionFit.of(times(n), values))
    }
}
