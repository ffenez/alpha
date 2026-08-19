package app.alpha.smoke

import app.alpha.ui.logic.SearchFeedbackChannels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Каналы отклика обязаны переживать запись и чтение: полевой отчёт «не
 * работает ни один отклик» проверяется здесь, а не глазами.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedbackChannelsStorageTest {

    @Test
    fun `a saved channel comes back on`() = runBlocking {
        val graph = Smoke.graph()
        // На чистой установке отклик молчит — так было и до разделения на
        // каналы: звук в поле включают осознанно.
        assertTrue(graph.settings.searchFeedbackChannels.first().silent)

        graph.settings.setSearchFeedbackChannels(
            SearchFeedbackChannels(clicks = true, vibro = true),
        )
        val stored = graph.settings.searchFeedbackChannels.first()
        assertEquals(SearchFeedbackChannels(clicks = true, vibro = true), stored)
        assertTrue(stored.usesSound)
        assertTrue(stored.usesReference)
    }

    @Test
    fun `switching everything off really is silence`() = runBlocking {
        val graph = Smoke.graph()
        graph.settings.setSearchFeedbackChannels(SearchFeedbackChannels())
        assertTrue(graph.settings.searchFeedbackChannels.first().silent)
    }
}
