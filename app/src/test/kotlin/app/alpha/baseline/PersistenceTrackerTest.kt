package app.alpha.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PersistenceTrackerTest {

    private fun tracker(persistenceSeconds: Long = 120, gapSeconds: Long = 15) =
        PersistenceTracker(
            persistenceMillis = persistenceSeconds * 1000,
            gapToleranceMillis = gapSeconds * 1000,
        )

    @Test
    fun `idle while condition is not met`() {
        val t = tracker()
        repeat(300) { second ->
            val a = t.onSample(second * 1000L, conditionMet = false)
            assertEquals(PersistenceTracker.State.Idle, a.state)
            assertFalse(a.fired)
        }
    }

    @Test
    fun `magnitude alone is not enough - short excursion never confirms`() {
        val t = tracker(persistenceSeconds = 120)
        // 60 s above, then back to normal: builds but never confirms.
        for (second in 0 until 60) {
            val a = t.onSample(second * 1000L, conditionMet = true)
            assertIs<PersistenceTracker.State.Building>(a.state)
            assertFalse(a.fired)
        }
        for (second in 80 until 300) {
            val a = t.onSample(second * 1000L, conditionMet = false)
            assertEquals(PersistenceTracker.State.Idle, a.state)
            assertFalse(a.fired)
        }
    }

    @Test
    fun `confirms after persistence and fires exactly once`() {
        val t = tracker(persistenceSeconds = 120)
        var fires = 0
        for (second in 0 until 300) {
            val a = t.onSample(second * 1000L, conditionMet = true)
            if (a.fired) fires++
            if (second < 120) assertIs<PersistenceTracker.State.Building>(a.state)
            else assertIs<PersistenceTracker.State.Confirmed>(a.state)
        }
        assertEquals(1, fires)
        val confirmed = t.onSample(300_000, true)
        assertIs<PersistenceTracker.State.Confirmed>(confirmed.state)
        assertEquals(0L, (confirmed.state as PersistenceTracker.State.Confirmed).sinceMillis)
    }

    @Test
    fun `short dips within gap tolerance do not reset the excursion`() {
        val t = tracker(persistenceSeconds = 120, gapSeconds = 15)
        var fired = false
        for (second in 0 until 200) {
            // A 5 s dip every minute — noise around the boundary.
            val met = second % 60 !in 30 until 35
            val a = t.onSample(second * 1000L, met)
            if (a.fired) fired = true
        }
        assertTrue(fired)
    }

    @Test
    fun `gap longer than tolerance resets and rearms`() {
        val t = tracker(persistenceSeconds = 60, gapSeconds = 15)
        var fires = 0
        // First excursion confirms.
        for (second in 0 until 90) if (t.onSample(second * 1000L, true).fired) fires++
        // 30 s fully below: reset.
        for (second in 90 until 120) {
            val a = t.onSample(second * 1000L, false)
            if (second > 105) assertEquals(PersistenceTracker.State.Idle, a.state)
        }
        // Second excursion fires again.
        for (second in 120 until 200) if (t.onSample(second * 1000L, true).fired) fires++
        assertEquals(2, fires)
    }

    @Test
    fun `reset drops all state`() {
        val t = tracker(persistenceSeconds = 60)
        for (second in 0 until 90) t.onSample(second * 1000L, true)
        t.reset()
        val a = t.onSample(91_000, true)
        assertIs<PersistenceTracker.State.Building>(a.state)
        assertEquals(91_000L, (a.state as PersistenceTracker.State.Building).sinceMillis)
    }
}
