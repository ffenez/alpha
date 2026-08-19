package app.alpha.smoke

import app.alpha.service.ServiceStatus
import app.alpha.ui.logic.SearchFeedbackChannels
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Отклик обязан звучать НЕЗАВИСИМО от того, какой экран открыт: полевой отчёт
 * «не работает ни один отклик» проверяется здесь, где звука не слышно, но
 * видно, что именно хаб отправляет в железо.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedbackHubTest {

    private fun sample(cps: Float) = ServiceStatus.LiveSample(
        deviceTimestampMillis = System.currentTimeMillis(),
        receivedAtMillis = System.currentTimeMillis(),
        doseRate = 0.14f,
        doseRateErr = 9f,
        countRate = cps,
        countRateErr = 1f,
    )

    @Test
    fun `clicks follow the count rate without any screen`() = runBlocking {
        val graph = Smoke.graph()
        graph.settings.setSearchFeedbackChannels(SearchFeedbackChannels(clicks = true))
        val hub = graph.feedbackHub
        hub.start()
        try {
            graph.serviceStatus.onSample(sample(25f))
            // Хаб слушает поток отсчётов; ему нужен один проход планировщика.
            repeat(20) {
                if (hub.output.value.clicksPerSecond > 0f) return@repeat
                delay(50)
            }
            assertTrue(
                "щелчки молчат: ${hub.output.value}",
                hub.output.value.clicksPerSecond > 0f,
            )
        } finally {
            hub.stop()
        }
    }

    @Test
    fun `a stale reading silences the clicks`() = runBlocking {
        val graph = Smoke.graph()
        graph.settings.setSearchFeedbackChannels(SearchFeedbackChannels(clicks = true))
        val hub = graph.feedbackHub
        hub.start()
        try {
            // Отсчёт пришёл десять секунд назад: поток оборван, и щёлкать по
            // последнему известному числу значило бы врать о том, что мерят.
            graph.serviceStatus.onSample(
                sample(25f).copy(receivedAtMillis = System.currentTimeMillis() - 10_000L),
            )
            delay(300)
            assertEquals(0f, hub.output.value.clicksPerSecond, 1e-6f)
        } finally {
            hub.stop()
        }
    }

    @Test
    fun `everything off stays silent`() = runBlocking {
        val graph = Smoke.graph()
        graph.settings.setSearchFeedbackChannels(SearchFeedbackChannels())
        val hub = graph.feedbackHub
        hub.start()
        try {
            graph.serviceStatus.onSample(sample(25f))
            delay(300)
            assertEquals(0f, hub.output.value.clicksPerSecond, 1e-6f)
        } finally {
            hub.stop()
        }
    }

    /**
     * Полевой отчёт: включил тон, выключил — и щелчки больше не вернулись.
     *
     * Режим тона в движке эксклюзивный, поэтому «залипший» тон означает
     * молчание всех щелчков. Проверяется именно возврат.
     */
    @Test
    fun `turning the tone off brings the clicks back`() = runBlocking {
        val graph = Smoke.graph()
        val hub = graph.feedbackHub
        hub.start()
        try {
            graph.settings.setSearchFeedbackChannels(
                SearchFeedbackChannels(clicks = true, tone = true),
            )
            delay(300)
            graph.settings.setSearchFeedbackChannels(SearchFeedbackChannels(clicks = true))
            graph.serviceStatus.onSample(sample(25f))
            repeat(20) {
                if (hub.output.value.toneHz == null && hub.output.value.clicksPerSecond > 0f) {
                    return@repeat
                }
                delay(50)
            }
            assertEquals(null, hub.output.value.toneHz)
            assertTrue(
                "щелчки не вернулись после выключения тона: ${hub.output.value}",
                hub.output.value.clicksPerSecond > 0f,
            )
        } finally {
            hub.stop()
        }
    }
}
