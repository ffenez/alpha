package app.alpha.smoke

import app.alpha.AppGraph
import app.alpha.data.db.SampleEntity
import app.alpha.data.db.TrackSessionEntity
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Доза маршрута считается по ИЗМЕРЕНИЯМ, а не по календарному времени.
 *
 * Прогулка с выключенным экраном, потерянной связью или закрытым приложением
 * оставляет в записи дыру. Прежняя оценка «средняя мощность × длительность»
 * приписывала этой дыре дозу — час прогулки, из которого измерены десять
 * минут, давал вшестеро больше, чем прибор видел, и это число уезжало в отчёт
 * как измеренное.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RouteDoseTest {

    private var graph: AppGraph? = null

    @After
    fun tearDown() {
        graph?.database?.close()
    }

    /** 1 мкЗв/ч в сырых единицах прибора. */
    private val oneMicroSvHRaw = 1f / 10_000f

    @Test
    fun `unmeasured minutes add no dose`() = runBlocking {
        val graph = Smoke.graph().also { this@RouteDoseTest.graph = it }
        val start = 1_700_000_000_000L
        val hour = 3_600_000L
        // Измерено 600 секунд из часа: ровно 1 мкЗв/ч, дальше запись молчит.
        graph.database.sampleDao().insertAll(
            (0 until 600).map { i ->
                SampleEntity(
                    timestamp = start + i * 1000L,
                    doseRate = oneMicroSvHRaw,
                    doseRateErr = 0f,
                    countRate = 10f,
                    countRateErr = 0f,
                    flags = 0,
                    realTimeFlags = 0,
                )
            },
        )
        val sessionId = graph.database.trackDao().insertSession(
            TrackSessionEntity(name = "gap", startedAt = start, endedAt = start + hour),
        )
        val session = graph.database.trackDao().session(sessionId)!!

        val summary = graph.trackRepository.routeSummary(session)

        // 1 мкЗв/ч × 600 с = 0,1667 мкЗв. Оценка по длительности дала бы 1 мкЗв.
        assertEquals(600.0 / 3600.0, summary.doseMicroSv!!, 1e-3)
    }

    @Test
    fun `a running route has no dose yet`() = runBlocking {
        val graph = Smoke.graph().also { this@RouteDoseTest.graph = it }
        val start = 1_700_000_000_000L
        val sessionId = graph.database.trackDao().insertSession(
            TrackSessionEntity(name = "running", startedAt = start, endedAt = null),
        )
        val session = graph.database.trackDao().session(sessionId)!!

        assertNull(graph.trackRepository.routeSummary(session).doseMicroSv)
    }
}
