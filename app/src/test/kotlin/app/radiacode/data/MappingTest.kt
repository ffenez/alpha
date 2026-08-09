package app.radiacode.data

import app.radiacode.data.db.EventEntity
import app.radiacode.protocol.Event
import app.radiacode.protocol.EventId
import app.radiacode.protocol.RareData
import app.radiacode.protocol.RealTimeData
import app.radiacode.protocol.Spectrum
import kotlin.test.Test
import kotlin.test.assertEquals

class MappingTest {

    @Test
    fun `RealTimeData maps to sample entity`() {
        val record = RealTimeData(
            timestampMillis = 1_700_000_128_000,
            tsOffset10ms = 0,
            countRate = 11.5f,
            countRateErr = 1.5f,
            doseRate = 0.0005f,
            doseRateErr = 2.0f,
            flags = 3,
            realTimeFlags = 4,
        )
        val entity = record.toEntity()
        assertEquals(1_700_000_128_000, entity.timestamp)
        assertEquals(11.5f, entity.countRate)
        assertEquals(1.5f, entity.countRateErr)
        assertEquals(0.0005f, entity.doseRate)
        assertEquals(2.0f, entity.doseRateErr)
        assertEquals(3, entity.flags)
        assertEquals(4, entity.realTimeFlags)
    }

    @Test
    fun `RareData maps battery and temperature`() {
        val record = RareData(
            timestampMillis = 5_000,
            tsOffset10ms = 0,
            durationSeconds = 3600,
            dose = 0.25f,
            temperature = 21.5f,
            chargeLevel = 87.5f,
            flags = 1,
        )
        val entity = record.toEntity()
        assertEquals(5_000, entity.timestamp)
        assertEquals(3600, entity.durationSeconds)
        assertEquals(0.25f, entity.dose)
        assertEquals(21.5f, entity.temperature)
        assertEquals(87.5f, entity.batteryPercent)
    }

    @Test
    fun `device event maps with source and readable name`() {
        val record = Event(
            timestampMillis = 6_000,
            tsOffset10ms = 0,
            eventId = EventId.DOSE_RATE_ALARM1,
            eventCode = 9,
            eventParam1 = 2,
            flags = 0,
        )
        val entity = record.toEntity()
        assertEquals(EventEntity.SOURCE_DEVICE, entity.source)
        assertEquals(9, entity.code)
        assertEquals("DOSE_RATE_ALARM1", entity.name)
        assertEquals(2, entity.param1)
    }

    @Test
    fun `spectrum roundtrips through the snapshot entity`() {
        val spectrum = Spectrum(
            durationSeconds = 600,
            a0 = -6f,
            a1 = 2.4f,
            a2 = 0.0004f,
            counts = List(1024) { it % 7 },
        )
        val entity = spectrum.toEntity(timestamp = 42, accumulated = true)
        assertEquals(42, entity.timestamp)
        assertEquals(true, entity.accumulated)
        assertEquals(1024, entity.channelCount)
        assertEquals(spectrum, entity.toSpectrum())
    }
}
