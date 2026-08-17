package app.alpha.ui.logic

import app.alpha.ui.components.AppTab

/** One configurable bottom-nav slot (Главная is fixed and not listed). */
data class NavEntry(val tab: AppTab, val visible: Boolean)

/**
 * Bottom-nav customization (Настройки → Интерфейс): Поиск/Спектр/Карта/
 * История can be hidden and reordered; Главная is always first. Stored as a
 * CSV of tab names in display order, hidden ones prefixed with `!`
 * (e.g. `SEARCH,!SPECTRUM,MAP,HISTORY`), so a hidden tab keeps its place
 * when re-enabled. Guard: at least one tab besides Главная stays visible.
 * Pure JVM, tested; [app.alpha.data.AppSettings] stores only the string.
 */
object NavConfig {

    const val HIDDEN_PREFIX = "!"

    /** Default order; everything visible (today's behavior). */
    val DEFAULT: List<NavEntry> = listOf(
        NavEntry(AppTab.SEARCH, visible = true),
        NavEntry(AppTab.SPECTRUM, visible = true),
        NavEntry(AppTab.MAP, visible = true),
        NavEntry(AppTab.HISTORY, visible = true),
    )

    fun parse(stored: String?): List<NavEntry> {
        if (stored == null) return DEFAULT
        val parsed = stored.split(',').mapNotNull { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val hidden = trimmed.startsWith(HIDDEN_PREFIX)
            val name = trimmed.removePrefix(HIDDEN_PREFIX)
            val tab = AppTab.entries.firstOrNull { it.name == name && it != AppTab.HOME }
            tab?.let { NavEntry(it, visible = !hidden) }
        }.distinctBy { it.tab }
        // Tabs missing from storage (new app version) append visible, in
        // default order — storage never silently hides a screen.
        val missing = DEFAULT.filter { d -> parsed.none { it.tab == d.tab } }
        val all = parsed + missing
        // Corrupt storage that violates the guard falls back to all-visible
        // in the stored order.
        return if (all.none { it.visible }) all.map { it.copy(visible = true) } else all
    }

    fun serialize(entries: List<NavEntry>): String =
        entries.joinToString(",") { (if (it.visible) "" else HIDDEN_PREFIX) + it.tab.name }

    /** What the NavBar renders: Главная first, then visible entries in order. */
    fun tabsForBar(entries: List<NavEntry>): List<AppTab> =
        listOf(AppTab.HOME) + entries.filter { it.visible }.map { it.tab }

    /**
     * Toggle visibility; returns null when the change would hide the last
     * visible tab (guard: Главная + at least one more).
     */
    fun toggle(entries: List<NavEntry>, tab: AppTab): List<NavEntry>? {
        val target = entries.firstOrNull { it.tab == tab } ?: return entries
        if (target.visible && entries.count { it.visible } <= 1) return null
        return entries.map { if (it.tab == tab) it.copy(visible = !it.visible) else it }
    }

    /** Move a row by [delta] positions (−1 = up, +1 = down), clamped. */
    fun move(entries: List<NavEntry>, tab: AppTab, delta: Int): List<NavEntry> {
        val index = entries.indexOfFirst { it.tab == tab }
        if (index < 0) return entries
        val target = (index + delta).coerceIn(0, entries.lastIndex)
        if (target == index) return entries
        val result = entries.toMutableList()
        val entry = result.removeAt(index)
        result.add(target, entry)
        return result
    }
}
