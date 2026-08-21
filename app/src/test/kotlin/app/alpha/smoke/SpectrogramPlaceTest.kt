package app.alpha.smoke

import app.alpha.data.TrackRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * «Где снят этот срез»: спектрограмма отвечает «когда», маршрут — «где», и
 * сшивает их время.
 *
 * Проверяется правило сшивки, а не картинка: берётся БЛИЖАЙШАЯ точка в
 * пределах допуска, а за его границей ответа нет вовсе — соседняя улица не то
 * же самое место.
 */
@RunWith(AndroidJUnit4::class)
class SpectrogramPlaceTest {

    @Test
    fun `берётся ближайшая по времени точка маршрута`() = runBlocking {
        val graph = Smoke.graph()
        val track = graph.trackRepository
        val sessionId = track.startSession(name = "проверка")
        val now = 1_700_000_000_000L
        track.addPoint(sessionId, now - 20_000, 55.7500, 37.6000, 8f, null, null)
        track.addPoint(sessionId, now + 5_000, 55.7600, 37.6100, 8f, null, null)

        val near = track.pointNear(now)
        assertEquals(55.7600, near?.latitude)
    }

    @Test
    fun `за пределами допуска места нет`() = runBlocking {
        val graph = Smoke.graph()
        val track = graph.trackRepository
        val sessionId = track.startSession(name = "проверка")
        val now = 1_700_000_000_000L
        // Точка старше допуска: маршрут в этот момент не писался, и выдавать
        // её за место среза значило бы придумать координаты.
        track.addPoint(
            sessionId = sessionId,
            timestamp = now - TrackRepository.POSITION_TOLERANCE_MILLIS - 1_000,
            latitude = 55.75,
            longitude = 37.60,
            accuracyMeters = 8f,
            doseRate = null,
            countRate = null,
        )
        assertNull(track.pointNear(now))
    }
}
