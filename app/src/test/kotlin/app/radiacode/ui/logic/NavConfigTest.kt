package app.radiacode.ui.logic

import app.radiacode.ui.components.AppTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavConfigTest {

    // --- parse / serialize (persistence format) ---

    @Test
    fun `null storage yields defaults with everything visible`() {
        val entries = NavConfig.parse(null)
        assertEquals(
            listOf(AppTab.SEARCH, AppTab.SPECTRUM, AppTab.MAP, AppTab.HISTORY),
            entries.map { it.tab },
        )
        assertTrue(entries.all { it.visible })
    }

    @Test
    fun `serialize and parse round trip preserves order and visibility`() {
        val entries = listOf(
            NavEntry(AppTab.MAP, visible = true),
            NavEntry(AppTab.SEARCH, visible = false),
            NavEntry(AppTab.HISTORY, visible = true),
            NavEntry(AppTab.SPECTRUM, visible = false),
        )
        assertEquals("MAP,!SEARCH,HISTORY,!SPECTRUM", NavConfig.serialize(entries))
        assertEquals(entries, NavConfig.parse(NavConfig.serialize(entries)))
    }

    @Test
    fun `unknown names and HOME in storage are ignored`() {
        val entries = NavConfig.parse("MAP,GARBAGE,HOME,!SEARCH")
        assertEquals(
            listOf(AppTab.MAP, AppTab.SEARCH, AppTab.SPECTRUM, AppTab.HISTORY),
            entries.map { it.tab },
        )
    }

    @Test
    fun `tabs missing from storage append visible in default order`() {
        val entries = NavConfig.parse("HISTORY")
        assertEquals(
            listOf(AppTab.HISTORY, AppTab.SEARCH, AppTab.SPECTRUM, AppTab.MAP),
            entries.map { it.tab },
        )
        assertTrue(entries.all { it.visible })
    }

    @Test
    fun `duplicates keep the first occurrence`() {
        val entries = NavConfig.parse("MAP,!MAP,SEARCH,SPECTRUM,HISTORY")
        assertEquals(4, entries.size)
        assertTrue(entries.first { it.tab == AppTab.MAP }.visible)
    }

    @Test
    fun `storage hiding everything falls back to all visible`() {
        val entries = NavConfig.parse("!SEARCH,!SPECTRUM,!MAP,!HISTORY")
        assertTrue(entries.all { it.visible })
        // The stored order is still respected.
        assertEquals(
            listOf(AppTab.SEARCH, AppTab.SPECTRUM, AppTab.MAP, AppTab.HISTORY),
            entries.map { it.tab },
        )
    }

    // --- bar rendering ---

    @Test
    fun `nav bar always starts with HOME and skips hidden tabs`() {
        val entries = listOf(
            NavEntry(AppTab.MAP, visible = true),
            NavEntry(AppTab.SEARCH, visible = false),
            NavEntry(AppTab.HISTORY, visible = true),
            NavEntry(AppTab.SPECTRUM, visible = false),
        )
        assertEquals(
            listOf(AppTab.HOME, AppTab.MAP, AppTab.HISTORY),
            NavConfig.tabsForBar(entries),
        )
    }

    // --- toggle guard ---

    @Test
    fun `toggle hides and shows a tab`() {
        val hidden = NavConfig.toggle(NavConfig.DEFAULT, AppTab.MAP)!!
        assertEquals(false, hidden.first { it.tab == AppTab.MAP }.visible)
        val shown = NavConfig.toggle(hidden, AppTab.MAP)!!
        assertEquals(true, shown.first { it.tab == AppTab.MAP }.visible)
    }

    @Test
    fun `guard forbids hiding the last visible tab`() {
        var entries: List<NavEntry> = NavConfig.DEFAULT
        entries = NavConfig.toggle(entries, AppTab.SEARCH)!!
        entries = NavConfig.toggle(entries, AppTab.SPECTRUM)!!
        entries = NavConfig.toggle(entries, AppTab.MAP)!!
        assertNull(NavConfig.toggle(entries, AppTab.HISTORY))
    }

    // --- ordering ---

    @Test
    fun `move up and down swap neighbours`() {
        val up = NavConfig.move(NavConfig.DEFAULT, AppTab.SPECTRUM, -1)
        assertEquals(
            listOf(AppTab.SPECTRUM, AppTab.SEARCH, AppTab.MAP, AppTab.HISTORY),
            up.map { it.tab },
        )
        val down = NavConfig.move(NavConfig.DEFAULT, AppTab.SPECTRUM, 1)
        assertEquals(
            listOf(AppTab.SEARCH, AppTab.MAP, AppTab.SPECTRUM, AppTab.HISTORY),
            down.map { it.tab },
        )
    }

    @Test
    fun `move clamps at the edges`() {
        assertEquals(NavConfig.DEFAULT, NavConfig.move(NavConfig.DEFAULT, AppTab.SEARCH, -1))
        assertEquals(NavConfig.DEFAULT, NavConfig.move(NavConfig.DEFAULT, AppTab.HISTORY, 1))
    }

    @Test
    fun `hidden tabs keep their place when moved around`() {
        val hidden = NavConfig.toggle(NavConfig.DEFAULT, AppTab.SPECTRUM)!!
        val moved = NavConfig.move(hidden, AppTab.SPECTRUM, 1)
        assertEquals(
            listOf(AppTab.SEARCH, AppTab.MAP, AppTab.SPECTRUM, AppTab.HISTORY),
            moved.map { it.tab },
        )
        assertEquals(false, moved.first { it.tab == AppTab.SPECTRUM }.visible)
    }
}
