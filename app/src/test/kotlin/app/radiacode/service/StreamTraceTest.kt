package app.radiacode.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Трасса существует ради одного полевого случая: «нет новых данных · N с» при
 * зелёном кружке связи. На экране «записи не пришли» и «записи пришли, но не
 * записались» выглядят ОДИНАКОВО, а лечатся по-разному — различить их можно
 * только этими числами.
 */
class StreamTraceTest {

    private fun tick(
        at: Long,
        records: Int = 1,
        age: Long? = 300,
        correction: Long = -128_000,
        inserted: Int = 1,
        dropped: Int = 0,
    ) = StreamTrace.Tick(at, records, age, correction, inserted, dropped)

    @Test
    fun `the ring keeps the freshest ticks and drops the oldest`() {
        val trace = StreamTrace()
        repeat(StreamTrace.CAPACITY + 50) { trace.add(tick(at = it.toLong())) }

        val ticks = trace.snapshot()
        assertEquals(StreamTrace.CAPACITY, ticks.size)
        // Свежие такты — последние: отчёт снимают сразу после симптома.
        assertEquals((StreamTrace.CAPACITY + 49).toLong(), ticks.last().atMillis)
        assertEquals(50L, ticks.first().atMillis)
    }

    @Test
    fun `silently dropped rows are counted`() {
        // Отброс уникальным индексом `samples.timestamp` не виден ни на
        // экране, ни в логах — а именно он означает «записи идут, а последнего
        // показания не прибавляется».
        val trace = StreamTrace()
        trace.add(tick(at = 1, inserted = 1, dropped = 0))
        trace.add(tick(at = 2, inserted = 0, dropped = 1))
        trace.add(tick(at = 3, inserted = 0, dropped = 2))

        assertEquals(3, trace.droppedTotal())
    }

    @Test
    fun `an empty reply is recorded as such, not as a missing tick`() {
        // Пустой ответ DATA_BUF штатен при частом опросе. Он обязан попасть в
        // трассу: «тактов не было» и «такты были пустыми» — разные диагнозы.
        val trace = StreamTrace()
        trace.add(tick(at = 1, records = 0, age = null, inserted = 0))

        val only = trace.snapshot().single()
        assertEquals(0, only.records)
        assertTrue(only.newestAgeMillis == null)
    }
}
