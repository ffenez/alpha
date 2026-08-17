package app.alpha.data.export

import app.alpha.data.SpectrumBlob
import app.alpha.data.db.SpectrumSnapshotEntity
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SpectrumExportTest {

    private val zone = ZoneId.of("Europe/Moscow")

    private fun entity(
        timestamp: Long,
        durationSeconds: Long,
        label: String? = null,
        counts: List<Int> = listOf(5, 6, 7, 8),
    ) = SpectrumSnapshotEntity(
        id = 1,
        timestamp = timestamp,
        accumulated = false,
        durationSeconds = durationSeconds,
        label = label,
        a0 = -5.5f,
        a1 = 2.4f,
        a2 = 4.0E-4f,
        channelCount = counts.size,
        counts = SpectrumBlob.encode(counts),
    )

    @Test
    fun `model derives from serial prefix`() {
        assertEquals("RadiaCode-102", SpectrumExport.modelFromSerial("RC-102-000115"))
        assertEquals("RadiaCode-110", SpectrumExport.modelFromSerial("RC-110-123456"))
        assertEquals("RadiaCode", SpectrumExport.modelFromSerial(null))
        assertEquals("RadiaCode", SpectrumExport.modelFromSerial("SN0042"))
    }

    @Test
    fun `title uses label else timestamp`() {
        val at = ZonedDateTime.of(2026, 8, 9, 12, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Проба", SpectrumExport.title(entity(at, 60, label = "Проба"), zone))
        assertEquals("Спектр 09.08.2026 12:30", SpectrumExport.title(entity(at, 60), zone))
    }

    @Test
    fun `file name carries a sortable stamp`() {
        val at = ZonedDateTime.of(2026, 8, 9, 12, 30, 45, 0, zone).toInstant().toEpochMilli()
        assertEquals("alpha-20260809-123045.xml", SpectrumExport.fileName(at, "xml", zone))
        assertEquals("alpha-20260809-123045.n42", SpectrumExport.fileName(at, "n42", zone))
    }

    @Test
    fun `result data brackets the accumulation and carries the background`() {
        val end = ZonedDateTime.of(2026, 8, 9, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val main = entity(end, durationSeconds = 600, label = "Проба")
        val bg = entity(end - 3_600_000L, durationSeconds = 1800, counts = listOf(1, 1, 1, 1))

        val data = SpectrumExport.toResultData(main, bg, "RC-110-000042", zone)

        assertEquals("RadiaCode-110", data.deviceModel)
        assertEquals("Проба", data.sampleName)
        assertEquals(end, data.endMillis)
        // StartTime = EndTime − live time in SECONDS (not ms — the community
        // Diff-Calc bug this project must not repeat).
        assertEquals(end - 600_000L, data.startMillis)
        assertEquals(600L, data.spectrum.measurementSeconds)
        assertEquals(listOf(5, 6, 7, 8), data.spectrum.counts)
        assertEquals("RC-110-000042", data.spectrum.serialNumber)

        val background = assertNotNull(data.background)
        assertEquals(1800L, background.measurementSeconds)
        assertEquals(listOf(1, 1, 1, 1), background.counts)
    }

    @Test
    fun `result data without background or serial stays honest`() {
        val end = ZonedDateTime.of(2026, 8, 9, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val data = SpectrumExport.toResultData(entity(end, 60), null, null, zone)
        assertEquals("RadiaCode", data.deviceModel)
        assertNull(data.background)
        assertNull(data.spectrum.serialNumber)
    }

    @Test
    fun `exported result data roundtrips through the codec`() {
        val end = ZonedDateTime.of(2026, 8, 9, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val data = SpectrumExport.toResultData(
            entity(end, 600, label = "Проба"),
            entity(end - 1000L, 1800, counts = listOf(1, 2, 3, 4)),
            "RC-110-000042",
            zone,
        )
        val parsed = RcXml.parse(RcXml.write(data, zone), zone)
        assertEquals(emptyList(), parsed.warnings)
        assertEquals(data, parsed.data)
    }
}
