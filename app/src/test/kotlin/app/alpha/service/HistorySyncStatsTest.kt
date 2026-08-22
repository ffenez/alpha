package app.alpha.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Счётчики слива памяти прибора: по ним в отчёте видно, пришла ли история и
 * легла ли она в базу. Ноль записей должен означать «истории не было», а не
 * «история потерялась молча».
 */
class HistorySyncStatsTest {

    @Test
    fun `без слива счётчики пусты`() {
        val status = ServiceStatus()
        status.onHistorySynced(1_000L)

        assertEquals(0, status.historyRecords)
        assertEquals(0, status.historyInserted)
        assertEquals(0, status.historyDropped)
        assertNull(status.historySyncedAtMillis)
    }

    @Test
    fun `порции слива складываются, а глубина берётся наибольшая`() {
        val status = ServiceStatus()
        status.onHistoryBatch(
            ageMillis = 7_200_000L,
            records = 120,
            inserted = 118,
            dropped = 2,
            nowMillis = 1_000L,
        )
        status.onHistoryBatch(
            ageMillis = 3_600_000L,
            records = 60,
            inserted = 60,
            dropped = 0,
            nowMillis = 2_000L,
        )

        assertEquals(180, status.historyRecords)
        assertEquals(178, status.historyInserted)
        assertEquals(2, status.historyDropped)
        // Глубина слива — самая старая запись, а не последняя порция.
        assertEquals(7_200_000L, status.historyDeepestMillis)
        assertNull(status.historySyncedAtMillis, "слив ещё идёт")
    }

    @Test
    fun `догнав живое, слив отмечает момент один раз`() {
        val status = ServiceStatus()
        status.onHistoryBatch(3_600_000L, 60, 60, 0, 1_000L)
        status.onHistorySynced(5_000L)
        status.onHistorySynced(9_000L)

        assertEquals(5_000L, status.historySyncedAtMillis)
    }

    @Test
    fun `новая порция истории снимает отметку о догоне`() {
        // Прибор переподключился и снова отдаёт накопленное: отметка «слив
        // закончен» относилась бы к прошлому сливу.
        val status = ServiceStatus()
        status.onHistoryBatch(3_600_000L, 60, 60, 0, 1_000L)
        status.onHistorySynced(5_000L)
        status.onHistoryBatch(1_800_000L, 30, 30, 0, 9_000L)

        assertNull(status.historySyncedAtMillis)
    }
}
