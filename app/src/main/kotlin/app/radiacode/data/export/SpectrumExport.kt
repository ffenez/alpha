package app.radiacode.data.export

import app.radiacode.data.SpectrumBlob
import app.radiacode.data.db.SpectrumSnapshotEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure mapping between stored snapshots and the exchange formats: default
 * names, device model derived from the serial number, entity → [RcResultData].
 * JVM-tested; the screens only add SAF plumbing around these.
 */
object SpectrumExport {

    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val TITLE_STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    /**
     * «RC-102-000115» → «RadiaCode-102»: the serial prefix carries the model.
     * Without a serial the honest answer is just the brand.
     */
    fun modelFromSerial(serialNumber: String?): String {
        val digits = serialNumber?.let { Regex("^RC-(\\d+)").find(it.trim()) }
            ?.groupValues?.get(1)
        return if (digits != null) "RadiaCode-$digits" else "RadiaCode"
    }

    /** Display title of a snapshot: its label, else «Спектр 09.08.2026 12:30». */
    fun title(entity: SpectrumSnapshotEntity, zone: ZoneId = ZoneId.systemDefault()): String =
        entity.label ?: ("Спектр " + Instant.ofEpochMilli(entity.timestamp).atZone(zone)
            .format(TITLE_STAMP))

    /** Suggested export file name: «radiacode-20260809-123045.xml». */
    fun fileName(
        timestampMillis: Long,
        extension: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String =
        "radiacode-" + Instant.ofEpochMilli(timestampMillis).atZone(zone).format(FILE_STAMP) +
            "." + extension

    fun toRcSpectrum(
        entity: SpectrumSnapshotEntity,
        serialNumber: String?,
        name: String,
    ): RcSpectrum = RcSpectrum(
        name = name,
        serialNumber = serialNumber,
        a0 = entity.a0,
        a1 = entity.a1,
        a2 = entity.a2,
        measurementSeconds = entity.durationSeconds,
        counts = SpectrumBlob.decode(entity.counts),
    )

    /** Snapshot → N42 measurement; start = snapshot time − live time. */
    fun toN42Measurement(
        entity: SpectrumSnapshotEntity,
        classCode: String,
    ): N42.Measurement = N42.Measurement(
        classCode = classCode,
        startMillis = entity.timestamp - entity.durationSeconds * 1000L,
        durationSeconds = entity.durationSeconds,
        a0 = entity.a0,
        a1 = entity.a1,
        a2 = entity.a2,
        counts = SpectrumBlob.decode(entity.counts),
    )

    /** Processing metadata lines of a snapshot (spec §22) — N42 `<Remark>`s. */
    fun metadataLines(
        entity: SpectrumSnapshotEntity,
        appVersion: String? = null,
    ): List<String> = ProcessingMetadata.of(entity, appVersion).lines()

    /**
     * RC-XML document for a snapshot, with the recorded background reference
     * as BackgroundEnergySpectrum when present. Wall-clock Start/EndTime
     * bracket the accumulation: end = when the snapshot was taken, start =
     * end − live time (both in millis here; the writer converts to seconds
     * resolution).
     *
     * `SampleInfo/Note` always carries the processing metadata of spec §22
     * (normalization, background method, calibration, algorithm versions) —
     * it is built from the row itself, so no export path can omit it.
     */
    fun toResultData(
        entity: SpectrumSnapshotEntity,
        background: SpectrumSnapshotEntity?,
        serialNumber: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        appVersion: String? = null,
    ): RcResultData {
        val serial = serialNumber?.trim()?.ifEmpty { null }
        val name = title(entity, zone)
        return RcResultData(
            deviceModel = modelFromSerial(serial),
            sampleName = name,
            sampleNote = ProcessingMetadata.of(entity, appVersion).asText(),
            startMillis = entity.timestamp - entity.durationSeconds * 1000L,
            endMillis = entity.timestamp,
            spectrum = toRcSpectrum(entity, serial, name),
            background = background?.let {
                toRcSpectrum(it, serial, "Фон " + title(it, zone).removePrefix("Спектр "))
            },
        )
    }
}
