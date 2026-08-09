package app.radiacode.data

import app.radiacode.data.db.EventEntity
import app.radiacode.data.db.RareDataEntity
import app.radiacode.data.db.SampleEntity
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.protocol.Event
import app.radiacode.protocol.RareData
import app.radiacode.protocol.RealTimeData
import app.radiacode.protocol.Spectrum

/** DATA_BUF records -> Room entities. Timestamps come pre-resolved from base_time. */

fun RealTimeData.toEntity(placeId: Long? = null): SampleEntity = SampleEntity(
    timestamp = timestampMillis,
    doseRate = doseRate,
    doseRateErr = doseRateErr,
    countRate = countRate,
    countRateErr = countRateErr,
    flags = flags,
    realTimeFlags = realTimeFlags,
    placeId = placeId,
)

fun RareData.toEntity(): RareDataEntity = RareDataEntity(
    timestamp = timestampMillis,
    dose = dose,
    temperature = temperature,
    batteryPercent = chargeLevel,
    durationSeconds = durationSeconds,
    flags = flags,
)

fun Event.toEntity(): EventEntity = EventEntity(
    timestamp = timestampMillis,
    source = EventEntity.SOURCE_DEVICE,
    code = eventCode,
    name = eventId.name,
    param1 = eventParam1,
    flags = flags,
)

fun Spectrum.toEntity(timestamp: Long, accumulated: Boolean): SpectrumSnapshotEntity =
    SpectrumSnapshotEntity(
        timestamp = timestamp,
        accumulated = accumulated,
        durationSeconds = durationSeconds,
        a0 = a0,
        a1 = a1,
        a2 = a2,
        channelCount = counts.size,
        counts = SpectrumBlob.encode(counts),
    )

fun SpectrumSnapshotEntity.toSpectrum(): Spectrum = Spectrum(
    durationSeconds = durationSeconds,
    a0 = a0,
    a1 = a1,
    a2 = a2,
    counts = SpectrumBlob.decode(counts),
)
