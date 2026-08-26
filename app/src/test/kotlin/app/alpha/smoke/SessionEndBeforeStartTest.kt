package app.alpha.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.alpha.data.db.MeasurementSessionEntity
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Полевой дефект (резервная копия 26.08.2026): девять записей журнала с
 * длительностью около −220 с, все — смена места без единого измерения.
 *
 * Корень: запись закрывается по последнему ИЗМЕРЕНИЮ («честный конец, а не
 * сейчас»), а измерений в ней может не быть вовсе — прибор ушёл из эфира
 * раньше, чем пришло первое показание. Тогда последнее измерение старше самой
 * записи, и конец оказывается перед началом.
 *
 * Инвариант держится в SQL, а не в вызывающем коде: закрытий несколько
 * (переоткрытие, уборка после падения), и проверка в одном из них не защищает
 * остальные.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionEndBeforeStartTest {

    @Test
    fun `closing a session earlier than it started yields zero length, not negative`() =
        runBlocking {
            val graph = Smoke.graph()
            val dao = graph.database.sessionDao()
            val startedAt = 1_700_000_000_000L

            val id = dao.insert(
                MeasurementSessionEntity(profileId = null, startedAt = startedAt, endedAt = null),
            )
            dao.close(id, startedAt - 223_000L)

            assertEquals(startedAt, dao.session(id)?.endedAt)
        }

    @Test
    fun `crash recovery cannot close a session before it started`() = runBlocking {
        val graph = Smoke.graph()
        val dao = graph.database.sessionDao()
        val older = 1_700_000_000_000L
        val newer = older + 600_000L

        dao.insert(MeasurementSessionEntity(profileId = null, startedAt = older, endedAt = null))
        val late = dao.insert(
            MeasurementSessionEntity(profileId = null, startedAt = newer, endedAt = null),
        )
        // Уборка закрывает всё открытое одной меткой — последним измерением,
        // которое старше поздней записи.
        dao.closeAllOpen(older + 60_000L)

        assertEquals(newer, dao.session(late)?.endedAt)
    }

    @Test
    fun `already recorded negative durations are repaired`() = runBlocking {
        val graph = Smoke.graph()
        val dao = graph.database.sessionDao()
        val startedAt = 1_700_000_000_000L

        val id = dao.insert(
            MeasurementSessionEntity(
                profileId = null,
                startedAt = startedAt,
                endedAt = startedAt - 224_000L,
            ),
        )
        dao.repairNegativeDurations()

        assertEquals(startedAt, dao.session(id)?.endedAt)
    }
}
