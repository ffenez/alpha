package app.alpha.device

import app.alpha.protocol.RealTimeData
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Диагностический журнал смещений: он обязан писать то, что прислал прибор, и
 * ничего не стоить, пока выключен.
 */
class RawOffsetLogTest {

    @BeforeTest
    fun setUp() {
        RawOffsetLog.clear()
        RawOffsetLog.enabled = false
    }

    @AfterTest
    fun tearDown() {
        RawOffsetLog.clear()
        RawOffsetLog.enabled = false
    }

    private fun realTime(tsOffset10ms: Int, baseTimeMillis: Long) = RealTimeData(
        timestampMillis = baseTimeMillis + tsOffset10ms.toLong() * 10L,
        tsOffset10ms = tsOffset10ms,
        countRate = 1.0f,
        countRateErr = 5.0f,
        doseRate = 0.1f,
        doseRateErr = 5.0f,
        flags = 0,
        realTimeFlags = 0,
    )

    @Test
    fun `выключенный журнал не пишет ничего`() {
        RawOffsetLog.reply(
            nowMillis = 1_000L,
            records = listOf(realTime(-12_800, 0L)),
            correctionMillis = 0L,
            baseTimeMillis = 0L,
        )
        assertEquals(0, RawOffsetLog.size)
        assertEquals("", RawOffsetLog.dump())
    }

    @Test
    fun `смещения пишутся как прислал прибор`() {
        RawOffsetLog.enabled = true
        val base = 1_000_000L
        // Порция накопленного: самая старая запись на два часа назад.
        RawOffsetLog.reply(
            nowMillis = base + 500L,
            records = listOf(realTime(-720_000, base), realTime(-719_000, base)),
            correctionMillis = 0L,
            baseTimeMillis = base,
        )
        val line = RawOffsetLog.dump()
        assertTrue("raw_min=-720000" in line, line)
        assertTrue("raw_newest_rt=-719000" in line, line)
        // Возраст новейшей записи считается по действующей базе: 7190 с назад.
        assertTrue("age_s=7190" in line, line)
        assertTrue("records=2 rt=2" in line, line)
    }

    @Test
    fun `догнавший живое ответ виден по нулевому возрасту`() {
        RawOffsetLog.enabled = true
        val base = 5_000_000L
        RawOffsetLog.reply(
            nowMillis = base + 10_000L,
            records = listOf(realTime(1_000, base)),
            correctionMillis = 0L,
            baseTimeMillis = base,
        )
        assertTrue("age_s=0.0" in RawOffsetLog.dump(), RawOffsetLog.dump())
    }

    @Test
    fun `журнал не растёт бесконечно`() {
        RawOffsetLog.enabled = true
        repeat(RawOffsetLog.CAPACITY + 50) { index ->
            RawOffsetLog.reply(
                nowMillis = index.toLong(),
                records = listOf(realTime(index, 0L)),
                correctionMillis = 0L,
                baseTimeMillis = 0L,
            )
        }
        assertEquals(RawOffsetLog.CAPACITY, RawOffsetLog.size)
        // Кольцо теряет старое, а не новое: последняя строка на месте.
        assertTrue("at=${RawOffsetLog.CAPACITY + 49}" in RawOffsetLog.dump())
    }
}
