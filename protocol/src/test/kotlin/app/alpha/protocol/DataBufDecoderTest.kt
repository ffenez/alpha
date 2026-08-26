package app.alpha.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DataBufDecoderTest {

    private val baseTime = 1_700_000_000_000L

    @Test
    fun `decodes a multi-record buffer with skipped groups and seq wrap 255 to 0`() {
        val buf = LeWriter()
            // seq=254, RealTimeData (0,0), ts offset -12800 (i.e. -128 s)
            .u8(254).u8(0).u8(0).i32(-12800)
            .f32(1.25f).f32(0.5f).u16(15).u16(23).u16(0x0102).u8(3)
            // seq=255, AccelData (0,6) - skipped, 6 bytes
            .u8(255).u8(0).u8(6).i32(-12750)
            .u16(100).u16(200).u16(300)
            // seq=0 (wrap), RareData (0,3)
            .u8(0).u8(0).u8(3).i32(-12700)
            .u32(86400).f32(0.001234f).u16(2456).u16(9987).u16(1)
            // seq=1, Event (0,7): CHARGE_START, param 42
            .u8(1).u8(0).u8(7).i32(-12650)
            .u8(7).u8(42).u16(0)
            // seq=2, DoseRateDB (0,2)
            .u8(2).u8(0).u8(2).i32(-12600)
            .u32(4_000_000_000L).f32(3.5f).f32(0.12f).u16(5).u16(2)
            // seq=3, RawData (0,1)
            .u8(3).u8(0).u8(1).i32(-12550)
            .f32(2.25f).f32(0.75f)
            .build()

        val result = DataBufDecoder.decode(buf, baseTime)
        val records = result.records
        assertEquals(5, records.size)
        assertEquals(0, result.seqGaps)

        val rt = assertIs<RealTimeData>(records[0])
        assertEquals(baseTime - 128_000, rt.timestampMillis)
        assertEquals(-12800, rt.tsOffset10ms)
        assertEquals(1.25f, rt.countRate)
        assertEquals(0.5f, rt.doseRate)
        assertEquals(1.5f, rt.countRateErr)
        assertEquals(2.3f, rt.doseRateErr)
        assertEquals(0x0102, rt.flags)
        assertEquals(3, rt.realTimeFlags)

        val rare = assertIs<RareData>(records[1])
        assertEquals(baseTime - 127_000, rare.timestampMillis)
        assertEquals(86400L, rare.durationSeconds)
        assertEquals(0.001234f, rare.dose)
        assertEquals((2456 - 2000) / 100f, rare.temperature) // 4.56 degC
        assertEquals(9987 / 100f, rare.chargeLevel) // 99.87 %
        assertEquals(1, rare.flags)

        val event = assertIs<Event>(records[2])
        assertEquals(EventId.CHARGE_START, event.eventId)
        assertEquals(7, event.eventCode)
        assertEquals(42, event.eventParam1)
        assertEquals(0, event.flags)

        val db = assertIs<DoseRateDB>(records[3])
        assertEquals(4_000_000_000L, db.count) // count is u32, above Int.MAX_VALUE
        assertEquals(3.5f, db.countRate)
        assertEquals(0.12f, db.doseRate)
        assertEquals(0.5f, db.doseRateErr)
        assertEquals(2, db.flags)

        val raw = assertIs<RawData>(records[4])
        assertEquals(2.25f, raw.countRate)
        assertEquals(0.75f, raw.doseRate)
        assertEquals(baseTime - 125_500, raw.timestampMillis)
    }

    @Test
    fun `resyncs after a sequence jump and keeps parsing, counting the gap`() {
        val buf = LeWriter()
            .u8(10).u8(0).u8(1).i32(0).f32(1f).f32(2f)
            .u8(12).u8(0).u8(1).i32(100).f32(3f).f32(4f) // seq jump: 11 expected
            .build()
        val result = DataBufDecoder.decode(buf, baseTime)
        assertEquals(2, result.records.size)
        assertEquals(1, result.seqGaps)
        assertEquals(1f, assertIs<RawData>(result.records[0]).countRate)
        assertEquals(3f, assertIs<RawData>(result.records[1]).countRate)
    }

    @Test
    fun `gap mid-buffer still yields later RareData and Event records`() {
        val buf = LeWriter()
            // seq=10, RawData
            .u8(10).u8(0).u8(1).i32(0).f32(1f).f32(2f)
            // seq=14 (three records lost), RareData
            .u8(14).u8(0).u8(3).i32(100)
            .u32(3600).f32(0.5f).u16(2456).u16(9900).u16(0)
            // seq=15, Event: SPECTRUM_RESET
            .u8(15).u8(0).u8(7).i32(200).u8(19).u8(0).u16(0)
            .build()
        val result = DataBufDecoder.decode(buf, baseTime)
        assertEquals(3, result.records.size)
        assertEquals(1, result.seqGaps)
        val rare = assertIs<RareData>(result.records[1])
        assertEquals(3600L, rare.durationSeconds)
        val event = assertIs<Event>(result.records[2])
        assertEquals(EventId.SPECTRUM_RESET, event.eventId)
    }

    @Test
    fun `counts each independent gap separately`() {
        val buf = LeWriter()
            .u8(1).u8(0).u8(1).i32(0).f32(1f).f32(2f)
            .u8(5).u8(0).u8(1).i32(100).f32(3f).f32(4f) // gap 1
            .u8(9).u8(0).u8(1).i32(200).f32(5f).f32(6f) // gap 2
            .build()
        val result = DataBufDecoder.decode(buf, baseTime)
        assertEquals(3, result.records.size)
        assertEquals(2, result.seqGaps)
    }

    @Test
    fun `stops at an unknown group without throwing`() {
        val buf = LeWriter()
            .u8(1).u8(0).u8(1).i32(0).f32(1f).f32(2f)
            .u8(2).u8(9).u8(9).i32(0).raw(1, 2, 3, 4) // unknown eid/gid
            .build()
        val records = DataBufDecoder.decode(buf, baseTime).records
        assertEquals(1, records.size)
    }

    @Test
    fun `skips eid 1 sample blocks using their declared sample count`() {
        val buf = LeWriter()
            // seq=5, (1,1): 2 samples x 8 bytes
            .u8(5).u8(1).u8(1).i32(0)
            .u16(2).u32(1000).raw(ByteArray(16))
            // seq=6, RawData
            .u8(6).u8(0).u8(1).i32(50).f32(9f).f32(8f)
            .build()
        val records = DataBufDecoder.decode(buf, baseTime).records
        assertEquals(1, records.size)
        val raw = assertIs<RawData>(records[0])
        assertEquals(9f, raw.countRate)
        assertEquals(baseTime + 500, raw.timestampMillis)
    }

    @Test
    fun `unknown event code maps to UNKNOWN but keeps the raw code`() {
        val buf = LeWriter()
            .u8(0).u8(0).u8(7).i32(0).u8(200).u8(1).u16(0)
            .build()
        val event = assertIs<Event>(DataBufDecoder.decode(buf, baseTime).records.single())
        assertEquals(EventId.UNKNOWN, event.eventId)
        assertEquals(200, event.eventCode)
    }

    @Test
    fun `empty and truncated buffers produce no records`() {
        assertEquals(0, DataBufDecoder.decode(ByteArray(0), baseTime).records.size)
        assertEquals(0, DataBufDecoder.decode(byteArrayOf(1, 0, 0, 5, 0), baseTime).records.size)
    }

    /**
     * Полевой случай (0.68.0, отчёт прибора): «16 bytes required, but only 6
     * remain» — ответ оборвался внутри тела записи. Разобранные до обрыва
     * записи — полноценные измерения, и терять их вместе с хвостом нельзя:
     * ошибка отменяла ВЕСЬ ответ, то есть секунду показаний.
     */
    @Test
    fun `a body cut off at the end keeps the records decoded before it`() {
        val buf = LeWriter()
            .u8(0).u8(0).u8(0).i32(-12800)
            .f32(1.25f).f32(0.5f).u16(15).u16(23).u16(0).u8(0)
            // seq=1, UserData (0,4): тело 16 байт, а в ответе осталось 6.
            .u8(1).u8(0).u8(4).i32(-12750)
            .raw(1, 2, 3, 4, 5, 6)
            .build()

        val result = DataBufDecoder.decode(buf, baseTime)

        assertEquals(1, result.records.size)
        assertIs<RealTimeData>(result.records[0])
        assertTrue(result.truncated, "оборванный ответ не помечен")
    }

    @Test
    fun `a whole reply is not marked truncated`() {
        val buf = LeWriter()
            .u8(0).u8(0).u8(0).i32(-12800)
            .f32(1.25f).f32(0.5f).u16(15).u16(23).u16(0).u8(0)
            .build()

        assertFalse(DataBufDecoder.decode(buf, baseTime).truncated)
    }
}
