package app.radiacode.data.export

import app.radiacode.analysis.AlgorithmVersions
import app.radiacode.data.SpectrumBlob
import app.radiacode.data.db.SpectrumSnapshotEntity
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Spec §22: every export states the normalization, the background method, the
 * calibration and the algorithm versions behind its numbers.
 */
class ProcessingMetadataTest {

    private val zone = ZoneId.of("Europe/Moscow")

    private fun entity(analysisMeta: String? = null) = SpectrumSnapshotEntity(
        id = 1,
        timestamp = 1_800_000_000_000L,
        accumulated = false,
        durationSeconds = 600,
        label = "Проба",
        analysisMeta = analysisMeta,
        a0 = -5.5f,
        a1 = 2.4f,
        a2 = 4.0E-4f,
        channelCount = 4,
        counts = SpectrumBlob.encode(listOf(1, 2, 3, 4)),
    )

    @Test
    fun `a raw snapshot honestly reports that nothing was computed`() {
        val metadata = ProcessingMetadata.of(entity())
        val text = metadata.asText()
        assertTrue(text.contains("нормализация: ${ProcessingMetadata.NORMALIZATION_RAW}"), text)
        assertTrue(text.contains("фон: ${ProcessingMetadata.BACKGROUND_NONE}"), text)
        assertTrue(text.contains("E(кэВ) = a0 + a1·ch + a2·ch²"), text)
        assertTrue(text.contains("каналов 4"), text)
        assertTrue(text.contains("нет производных расчётов"), text)
        assertTrue(text.contains("время накопления: 600 с"), text)
    }

    @Test
    fun `a derived snapshot carries its method and algorithm versions`() {
        val stamp = ProcessingMetadata.stamp(
            method = "interval_subtraction (A−B)",
            algorithms = listOf("spectrum_compare"),
            extra = mapOf("sourceIds" to "3,4"),
        )
        val metadata = ProcessingMetadata.of(entity(stamp), appVersion = "0.1.0-alpha")
        val text = metadata.asText()
        assertTrue(text.contains("метод получения: interval_subtraction (A−B)"), text)
        assertTrue(
            text.contains("spectrum_compare v${AlgorithmVersions.SPECTRUM_COMPARE}"),
            text,
        )
        assertTrue(text.contains("sourceIds: 3,4"), text)
        assertTrue(text.contains("приложение: 0.1.0-alpha"), text)
        assertEquals(
            mapOf("spectrum_compare" to AlgorithmVersions.SPECTRUM_COMPARE),
            metadata.algorithmVersions,
        )
    }

    @Test
    fun `unknown algorithm keys are dropped instead of invented`() {
        val stamp = ProcessingMetadata.stamp(method = "x", algorithms = listOf("no_such_thing"))
        assertEquals(emptyMap(), ProcessingMetadata.of(entity(stamp)).algorithmVersions)
    }

    @Test
    fun `RC-XML export carries the metadata in the sample note`() {
        val data = SpectrumExport.toResultData(
            entity = entity(),
            background = null,
            serialNumber = "RC-110-000042",
            zone = zone,
            appVersion = "0.1.0-alpha",
        )
        val note = assertNotNull(data.sampleNote)
        assertTrue(note.contains("нормализация:"), note)
        assertTrue(note.contains("фон:"), note)
        assertTrue(note.contains("калибровка:"), note)
        assertTrue(note.contains("версии алгоритмов:"), note)

        // And it survives the writer/parser round trip.
        val parsed = RcXml.parse(RcXml.write(data, zone), zone)
        assertEquals(note, parsed.data.sampleNote)
    }

    @Test
    fun `N42 export carries the metadata as Remark elements`() {
        val snapshot = entity(
            ProcessingMetadata.stamp(
                method = "channel_sum (merge)",
                algorithms = listOf("spectrum_merge"),
            ),
        )
        val xml = N42.write(
            foreground = SpectrumExport.toN42Measurement(snapshot, N42.CLASS_FOREGROUND),
            softwareVersion = "0.1.0-alpha",
            zone = zone,
            remarks = SpectrumExport.metadataLines(snapshot, "0.1.0-alpha"),
        )
        assertTrue(xml.contains("<Remark>нормализация:"), xml)
        assertTrue(xml.contains("метод получения: channel_sum (merge)"), xml)
        assertTrue(
            xml.contains("spectrum_merge v${AlgorithmVersions.SPECTRUM_MERGE}"),
            xml,
        )
        // Remarks come before the creator name, as the N42-2011 sequence wants.
        assertTrue(
            xml.indexOf("<Remark>") < xml.indexOf("<RadInstrumentDataCreatorName>"),
            xml,
        )
    }
}
