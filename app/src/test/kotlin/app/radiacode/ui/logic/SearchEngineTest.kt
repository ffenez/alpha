package app.radiacode.ui.logic

import app.radiacode.analysis.CountWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole Поиск decision chain, walked second by second.
 *
 * The most important test here is the last one: the redesign's release
 * criterion (§14) is that *how often the screen redraws must not change the
 * statistical conclusion*, and that is only checkable because the chain is a
 * pure function of the readings and their instants.
 */
class SearchEngineTest {

    /** A 45 s reference at ~25 s⁻¹ with realistic scatter. */
    private fun reference(rate: Double = 25.0, seconds: Int = 45): BackgroundRecord {
        val times = LongArray(seconds) { it * 1_000L }
        val rates = DoubleArray(seconds) { i -> rate + if (i % 2 == 0) 1.0 else -1.0 }
        return BackgroundRecord(
            window = CountWindow.reconstruct(times, rates),
            atMillis = 0L,
            targetSamples = seconds,
            profileId = 1L,
            profileName = "Дом",
            deviceSerial = "RC-110-TEST",
        )
    }

    /** Deterministic «rate at second t» series: background, then a step. */
    private fun series(stepAtSecond: Int?, stepRate: Float): (Int) -> Float = { second ->
        val base = 25f + if (second % 2 == 0) 1f else -1f
        if (stepAtSecond != null && second >= stepAtSecond) stepRate else base
    }

    private fun run(
        seconds: Int,
        rateAt: (Int) -> Float,
        background: BackgroundRecord? = reference(),
        onSecond: (Int, SearchState) -> Unit = { _, _ -> },
    ): SearchState {
        var state = SearchState()
        for (second in 0 until seconds) {
            val now = second * 1_000L
            state = SearchEngine.onReading(state, now, rateAt(second), background, now)
            onSecond(second, state)
        }
        return state
    }

    @Test
    fun `a stationary stream never confirms anything`() {
        run(seconds = 120, rateAt = series(stepAtSecond = null, stepRate = 0f)) { second, state ->
            assertTrue(
                state.level == SearchLevel.BACKGROUND || state.level == SearchLevel.UNKNOWN,
                "at ${second}s the level was ${state.level}",
            )
        }
    }

    @Test
    fun `a real step confirms, and only after the confirmation time`() {
        val stepAt = 20
        var confirmedAt: Int? = null
        val finalState = run(seconds = 40, rateAt = series(stepAt, stepRate = 70f)) { second, state ->
            if (state.level == SearchLevel.CONFIRMED_EXCESS && confirmedAt == null) {
                confirmedAt = second
            }
        }
        val at = assertNotNull(confirmedAt, "a 2,8× step must eventually confirm")
        assertTrue(at > stepAt, "confirmed at ${at}s — a single window is never a verdict")
        assertTrue(
            at <= stepAt + 10,
            "confirmed only at ${at}s — too slow for a hand-held sweep",
        )
        assertEquals(SearchLevel.CONFIRMED_EXCESS, finalState.level)
        assertTrue(finalState.ladder.confirmed)
    }

    @Test
    fun `the confirmed excursion is marked back to where it started`() {
        val stepAt = 20
        val state = run(seconds = 40, rateAt = series(stepAt, stepRate = 70f))
        val marked = state.points.filter { it.confirmed }
        assertTrue(marked.isNotEmpty())
        // Nothing before the step may be marked, and the mark must reach back
        // past the moment the ladder agreed.
        assertTrue(marked.minOf { it.timeMillis } >= (stepAt - 3) * 1_000L)
        assertTrue(marked.size >= 10, "the whole excursion is the event, not its tail")
        assertEquals(1, RateChartModel.confirmedSpans(state.points).size)
    }

    @Test
    fun `a single bright second never confirms and never gets marked`() {
        val state = run(seconds = 60, rateAt = { second ->
            if (second == 30) 400f else 25f + if (second % 2 == 0) 1f else -1f
        })
        assertEquals(SearchLevel.BACKGROUND, state.level)
        assertTrue(state.points.none { it.confirmed })
        assertTrue(state.ladder.spikes.isNotEmpty(), "it must still be remembered as a marker")
    }

    @Test
    fun `a stopped stream drops the verdict instead of freezing it`() {
        var state = run(seconds = 40, rateAt = series(20, stepRate = 70f))
        assertEquals(SearchLevel.CONFIRMED_EXCESS, state.level)

        // No new readings: the decision window empties out.
        state = SearchEngine.onTick(state, reference(), nowMillis = 45_000L)
        assertEquals(SearchLevel.UNKNOWN, state.level)
        assertNull(state.comparison)
        // The tape and its marks stay — the excursion happened.
        assertTrue(state.points.any { it.confirmed })
    }

    @Test
    fun `without a background nothing is compared and nothing is claimed`() {
        val state = run(seconds = 30, rateAt = series(10, stepRate = 200f), background = null)
        assertEquals(SearchLevel.UNKNOWN, state.level)
        assertNull(state.comparison)
        assertTrue(state.points.isNotEmpty(), "the chart still shows the raw stream")
    }

    @Test
    fun `the tape keeps only the drawn minute`() {
        val state = run(seconds = 300, rateAt = series(null, 0f))
        val span = state.points.last().timeMillis - state.points.first().timeMillis
        assertTrue(span <= SearchEngine.TAPE_MILLIS, "span = $span")
        assertTrue(state.points.size in 55..62, "${state.points.size} points")
    }

    @Test
    fun `the decision window is the last seconds of readings, not the wall clock`() {
        val points = (0..10).map { SearchPoint(it * 1_000L, 25f) }
        val window = assertNotNull(SearchEngine.decisionWindow(points, nowMillis = 10_000L))
        assertEquals(3, window.samples)
        assertEquals(3.0, window.seconds, 1e-9)
        // Ten seconds later there is nothing recent to decide on.
        assertNull(SearchEngine.decisionWindow(points, nowMillis = 20_000L))
    }

    @Test
    fun `redraw rate does not change the conclusion`() {
        val rates = series(stepAtSecond = 20, stepRate = 70f)
        val background = reference()

        var lazy = SearchState()
        var eager = SearchState()
        for (second in 0 until 40) {
            val now = second * 1_000L
            lazy = SearchEngine.onReading(lazy, now, rates(second), background, now)
            eager = SearchEngine.onReading(eager, now, rates(second), background, now)
            // The eager screen also re-evaluates four times a second between
            // readings, exactly as a live UI would.
            for (tick in 1..3) {
                eager = SearchEngine.onTick(eager, background, now + tick * 250L)
            }
        }

        assertEquals(lazy.level, eager.level)
        assertEquals(lazy.ladder.confirmedSinceMillis, eager.ladder.confirmedSinceMillis)
        assertEquals(lazy.points.count { it.confirmed }, eager.points.count { it.confirmed })
    }
}
