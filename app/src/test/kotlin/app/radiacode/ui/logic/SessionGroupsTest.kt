package app.radiacode.ui.logic

import app.radiacode.data.SessionAdmission
import app.radiacode.data.SessionSummary
import app.radiacode.data.db.RangeStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Восемь записей «Дом» подряд — это одно измерение с перерывами.
 *
 * Полевой отчёт: за три часа в одном месте журнал показал записи в 4, 8, 58,
 * 17, 12, 32, 33 и 19 минут, причём внутри каждой измерения шли раз в секунду.
 * Рвали их разрывы связи и перезапуски службы, а не решение человека, и
 * склейка возвращает журналу то, что происходило на самом деле.
 */
class SessionGroupsTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    private fun session(
        id: Long,
        startMinutesAgo: Long,
        durationMinutes: Long,
        profileId: Long? = 1L,
        samples: Int = 60,
        avgRaw: Float = 15f,
        dose: Double = 0.01,
        running: Boolean = false,
    ) = SessionSummary(
        id = id,
        profileId = profileId,
        profileName = if (profileId == 1L) "Дом" else "Дача",
        startedAt = now - startMinutesAgo * minute,
        endedAt = if (running) null else now - (startMinutesAgo - durationMinutes) * minute,
        stats = RangeStats(
            sampleCount = samples,
            avgDoseRate = avgRaw,
            minDoseRate = avgRaw - 1f,
            maxDoseRate = avgRaw + 1f,
            avgCountRate = 24f,
            maxCountRate = 30f,
        ),
        doseMicroSv = dose,
        hasSpectrum = false,
        hasTrack = false,
        admission = SessionAdmission(admittedSeconds = durationMinutes * 60, exclusions = emptyList()),
    )

    private fun merge(sessions: List<SessionSummary>, graceMinutes: Long = 30) =
        SessionGroups.merge(sessions, graceMillis = graceMinutes * minute, nowMillis = now)

    @Test
    fun `pieces of one place separated by short breaks become one measurement`() {
        // Журнал идёт от новой записи к старой.
        val groups = merge(
            listOf(
                session(id = 3, startMinutesAgo = 20, durationMinutes = 20),
                session(id = 2, startMinutesAgo = 45, durationMinutes = 20),
                session(id = 1, startMinutesAgo = 70, durationMinutes = 20),
            ),
        )

        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals(listOf(3L, 2L, 1L), group.ids)
        assertEquals(3, group.pieces)
        // Начало — у самой старой части, конец — у самой новой.
        assertEquals(now - 70 * minute, group.startedAt)
        assertEquals(now, group.endedAt)
        // Числа складываются: 180 измерений и три сотых микрозиверта.
        assertEquals(180, group.stats.sampleCount)
        assertEquals(0.03, group.doseMicroSv, 1e-9)
        // Перерывы названы: два по пять минут.
        assertEquals(600L, group.gapSeconds)
    }

    @Test
    fun `a long break is a different measurement`() {
        val groups = merge(
            listOf(
                session(id = 2, startMinutesAgo = 10, durationMinutes = 10),
                session(id = 1, startMinutesAgo = 120, durationMinutes = 30),
            ),
        )

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.pieces == 1 })
    }

    /** Разные места не сливаются, даже если шли встык. */
    @Test
    fun `different places are never merged`() {
        val groups = merge(
            listOf(
                session(id = 2, startMinutesAgo = 10, durationMinutes = 10, profileId = 2L),
                session(id = 1, startMinutesAgo = 25, durationMinutes = 10, profileId = 1L),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals("Дача", groups.first().profileName)
        assertEquals("Дом", groups.last().profileName)
    }

    /** Идущая запись остаётся идущей и в склейке. */
    @Test
    fun `a running piece keeps the group running`() {
        val groups = merge(
            listOf(
                session(id = 2, startMinutesAgo = 5, durationMinutes = 5, running = true),
                session(id = 1, startMinutesAgo = 30, durationMinutes = 20),
            ),
        )

        val group = groups.single()
        assertTrue(group.running)
        assertEquals(null, group.endedAt)
    }

    /**
     * Среднее взвешено ЧИСЛОМ ИЗМЕРЕНИЙ, а не числом записей: короткий кусок
     * не весит столько же, сколько часовой.
     */
    @Test
    fun `the average is weighted by measurements`() {
        val groups = merge(
            listOf(
                session(id = 2, startMinutesAgo = 10, durationMinutes = 10, samples = 100, avgRaw = 20f),
                session(id = 1, startMinutesAgo = 25, durationMinutes = 10, samples = 900, avgRaw = 10f),
            ),
        )

        val avg = groups.single().stats.avgDoseRate!!
        // (100×20 + 900×10) / 1000 = 11
        assertEquals(11f, avg, 1e-3f)
        assertEquals(21f, groups.single().stats.maxDoseRate!!, 1e-3f)
    }

    @Test
    fun `an empty journal stays empty and a single session stays single`() {
        assertEquals(emptyList(), merge(emptyList()))
        val one = merge(listOf(session(id = 1, startMinutesAgo = 10, durationMinutes = 5)))
        assertEquals(1, one.single().pieces)
        assertEquals(0L, one.single().gapSeconds)
    }
}
