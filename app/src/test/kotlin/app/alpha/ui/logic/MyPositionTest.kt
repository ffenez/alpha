package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Я на карте»: what the screen may claim about the user's own position. */
class MyPositionTest {

    private val now = 1_700_000_000_000L

    private fun fix(ageMillis: Long = 0, accuracy: Float = 12f) = PositionFix(
        latitude = 55.75,
        longitude = 37.61,
        accuracyMeters = accuracy,
        timeMillis = now - ageMillis,
    )

    @Test
    fun `no permission wins over everything else`() {
        assertEquals(
            PositionState.NO_PERMISSION,
            MyPosition.state(hasPermission = false, providersEnabled = true, fix = fix()),
        )
    }

    @Test
    fun `providers off is reported before waiting for a fix`() {
        assertEquals(
            PositionState.PROVIDER_OFF,
            MyPosition.state(hasPermission = true, providersEnabled = false, fix = null),
        )
    }

    @Test
    fun `granted but nothing received yet is waiting`() {
        assertEquals(
            PositionState.WAITING_FIX,
            MyPosition.state(hasPermission = true, providersEnabled = true, fix = null),
        )
    }

    @Test
    fun `a fix makes the state fixed`() {
        assertEquals(
            PositionState.FIXED,
            MyPosition.state(hasPermission = true, providersEnabled = true, fix = fix()),
        )
    }

    @Test
    fun `waiting for a fix is said out loud`() {
        assertEquals(
            "жду сигнал GPS",
            MyPosition.chipText(PositionState.WAITING_FIX, null, now),
        )
    }

    @Test
    fun `a fresh fix reports its accuracy`() {
        assertEquals("я · ±12 м", MyPosition.chipText(PositionState.FIXED, fix(), now))
    }

    @Test
    fun `a stale fix reports its age instead of pretending to be current`() {
        val stale = fix(ageMillis = 120_000)
        assertTrue(MyPosition.isStale(stale, now))
        assertEquals("я · фикс 2 мин назад", MyPosition.chipText(PositionState.FIXED, stale, now))
    }

    @Test
    fun `states already explained elsewhere stay silent`() {
        assertNull(MyPosition.chipText(PositionState.NO_PERMISSION, null, now))
        assertNull(MyPosition.chipText(PositionState.PROVIDER_OFF, fix(), now))
    }

    @Test
    fun `the marker survives providers being switched off but not a missing permission`() {
        assertTrue(MyPosition.markerVisible(PositionState.PROVIDER_OFF, fix()))
        assertFalse(MyPosition.markerVisible(PositionState.NO_PERMISSION, fix()))
        assertFalse(MyPosition.markerVisible(PositionState.WAITING_FIX, null))
    }

    @Test
    fun `accuracy the provider never reported is not invented`() {
        assertEquals("точность неизвестна", MyPosition.accuracy(0f))
        assertEquals("±4,5 м", MyPosition.accuracy(4.5f))
        assertEquals("±48 м", MyPosition.accuracy(47.6f))
    }
}
