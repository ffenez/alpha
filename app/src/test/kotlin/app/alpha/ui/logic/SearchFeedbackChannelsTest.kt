package app.alpha.ui.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Каналы отклика: то, ради чего они перестали быть одним выбором —
 * сочетания (`settings_ui_restructure.md`, acceptance).
 */
class SearchFeedbackChannelsTest {

    @Test
    fun `any combination is expressible`() {
        val clicksAndVibro = SearchFeedbackChannels(clicks = true, vibro = true)
        assertTrue(clicksAndVibro.usesSound)
        assertTrue(clicksAndVibro.usesReference)
        assertFalse(clicksAndVibro.silent)

        val toneAndVibro = SearchFeedbackChannels(tone = true, vibro = true)
        assertTrue(toneAndVibro.usesSound)
        assertTrue(toneAndVibro.usesReference)

        val all = SearchFeedbackChannels(clicks = true, tone = true, vibro = true)
        assertTrue(all.usesSound)
        assertTrue(all.usesReference)
        assertFalse(all.silent)
    }

    @Test
    fun `everything off is the old silent mode`() {
        val none = SearchFeedbackChannels()
        assertTrue(none.silent)
        assertFalse(none.usesSound)
        assertFalse(none.usesReference)
    }

    @Test
    fun `clicks are absolute, the other two are relative`() {
        // Щелчки описывают импульсы и звучат всегда; тон и вибрация говорят об
        // отношении и без знаменателя молчат по построению.
        val clicks = SearchFeedbackChannels(clicks = true)
        assertTrue(clicks.usesSound)
        assertFalse(clicks.usesReference)
        assertFalse(SearchFeedbackChannels(vibro = true).usesSound)
    }

    @Test
    fun `settings saved before the split keep sounding the same`() {
        assertEquals(
            SearchFeedbackChannels(clicks = true),
            SearchFeedbackChannels.ofLegacyMode("clicks"),
        )
        assertEquals(
            SearchFeedbackChannels(tone = true),
            SearchFeedbackChannels.ofLegacyMode("tone"),
        )
        assertEquals(
            SearchFeedbackChannels(vibro = true),
            SearchFeedbackChannels.ofLegacyMode("vibro"),
        )
        assertTrue(SearchFeedbackChannels.ofLegacyMode("off").silent)
        // Сбой хранилища не имеет права оставить прибор молчащим в поле.
        assertEquals(
            SearchFeedbackChannels(clicks = true),
            SearchFeedbackChannels.ofLegacyMode(null),
        )
    }
}
