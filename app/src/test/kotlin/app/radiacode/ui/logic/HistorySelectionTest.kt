package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistorySelectionTest {

    @Test
    fun `selection starts empty, toggles both kinds and cancels clean`() {
        var selection = HistorySelection().start()
        assertTrue(selection.active && selection.isEmpty)

        selection = selection.toggleSession(1).toggleSession(2).toggleSpectrum(7)
        assertEquals(3, selection.count)
        assertEquals(setOf(1L, 2L), selection.sessions)
        assertEquals(setOf(7L), selection.spectra)

        selection = selection.toggleSession(2)
        assertEquals(setOf(1L), selection.sessions)

        selection = selection.cancel()
        assertTrue(!selection.active && selection.isEmpty)
    }

    @Test
    fun `the action names the count, and says nothing when nothing is ticked`() {
        assertEquals("Удалить", HistoryDeletion.actionLabel(HistorySelection().start()))
        assertEquals(
            "Удалить · 2",
            HistoryDeletion.actionLabel(HistorySelection().start().toggleSession(1).toggleSpectrum(2)),
        )
    }
}

/**
 * Deleting measurements is the one place in the app where data really goes
 * away. The confirmation is therefore an account of what happens, and these
 * tests pin that it stays one.
 */
class HistoryDeletionTest {

    private val sessions = DeletionPlan(
        sessions = 3,
        samples = 41_203,
        events = 2,
        spectra = 0,
        seconds = 11 * 3600L,
    )

    @Test
    fun `the title says what kind of thing is going`() {
        assertEquals("Удалить 3 сессии?", HistoryDeletion.title(sessions))
        assertEquals(
            "Удалить 1 спектр?",
            HistoryDeletion.title(sessions.copy(sessions = 0, spectra = 1)),
        )
        assertEquals(
            "Удалить выбранное?",
            HistoryDeletion.title(sessions.copy(spectra = 2)),
        )
    }

    @Test
    fun `the body names the measurements, not just the sessions`() {
        val body = HistoryDeletion.body(sessions)
        assertTrue(body.contains("3 сессии"), body)
        assertTrue(body.contains(grouped(41_203)), "a big number must be readable: $body")
        assertTrue(body.contains("навсегда"), body)
        assertTrue(body.contains("2 события"), body)
        assertTrue(body.contains("11 ч"), body)
    }

    @Test
    fun `what survives is said out loud`() {
        val keeps = HistoryDeletion.keepsWording(sessions)
        assertTrue(keeps.contains("Отменить удаление нельзя"), keeps)
        assertTrue(keeps.contains("маршруты"), keeps)
        assertTrue(keeps.contains("Обычный фон профиля пересчитается"), keeps)

        // Deleting only spectra touches no measurement at all — and says so.
        val spectraOnly = HistoryDeletion.keepsWording(
            DeletionPlan(sessions = 0, samples = 0, events = 0, spectra = 2, seconds = 0),
        )
        assertTrue(spectraOnly.contains("не затрагиваются"), spectraOnly)
    }

    @Test
    fun `russian plurals are right where a user will notice`() {
        fun title(n: Int) = HistoryDeletion.title(sessions.copy(sessions = n))
        assertEquals("Удалить 1 сессию?", title(1))
        assertEquals("Удалить 2 сессии?", title(2))
        assertEquals("Удалить 5 сессий?", title(5))
        assertEquals("Удалить 11 сессий?", title(11))
        assertEquals("Удалить 21 сессию?", title(21))
        assertEquals("Удалить 112 сессий?", title(112))
    }

    /** Built from the constant: the separator is invisible in a source file. */
    private fun grouped(vararg groups: String) =
        groups.joinToString(HistoryDeletion.GROUP_SEPARATOR.toString())

    private fun grouped(value: Long) = HistoryDeletion.count(value)

    @Test
    fun `thousands are grouped by a space that cannot break the line`() {
        assertEquals(grouped("41", "203"), HistoryDeletion.count(41_203))
        assertEquals("999", HistoryDeletion.count(999))
        assertEquals(grouped("1", "000", "000"), HistoryDeletion.count(1_000_000))
        assertEquals("0", HistoryDeletion.count(0))
        assertTrue(
            HistoryDeletion.GROUP_SEPARATOR.code == 0x00A0,
            "the separator must be a no-break space",
        )
    }
}
