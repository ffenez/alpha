package app.alpha.data

import app.alpha.data.db.EventEntity
import app.alpha.data.db.RareDataEntity
import app.alpha.data.db.SampleEntity
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.protocol.Event
import app.alpha.protocol.RareData
import app.alpha.protocol.RealTimeData
import app.alpha.protocol.Spectrum

/** DATA_BUF records -> Room entities. Timestamps come pre-resolved from base_time. */

fun RealTimeData.toEntity(
    profileId: Long? = null,
    baselineExcluded: String? = null,
): SampleEntity = SampleEntity(
    timestamp = timestampMillis,
    doseRate = doseRate,
    doseRateErr = doseRateErr,
    countRate = countRate,
    countRateErr = countRateErr,
    flags = flags,
    realTimeFlags = realTimeFlags,
    profileId = profileId,
    baselineExcluded = baselineExcluded,
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

fun Spectrum.toEntity(
    timestamp: Long,
    accumulated: Boolean,
    isBackgroundReference: Boolean = false,
    origin: String = SpectrumSnapshotEntity.ORIGIN_AUTO,
    label: String? = null,
    /** Reproducibility stamp for derived spectra (spec §22); null for raw ones. */
    analysisMeta: String? = null,
    /** Провенанс (ADR 008): чей это спектр и из какой эпохи накопления. */
    deviceSerial: String? = null,
    firmware: String? = null,
    epochId: Long? = null,
    trigger: String? = null,
    /** Профиль на момент съёмки: ссылка и имя, каким оно было тогда. */
    profileId: Long? = null,
    profileName: String? = null,
): SpectrumSnapshotEntity =
    SpectrumSnapshotEntity(
        timestamp = timestamp,
        accumulated = accumulated,
        isBackgroundReference = isBackgroundReference,
        origin = origin,
        label = label,
        analysisMeta = analysisMeta,
        durationSeconds = durationSeconds,
        a0 = a0,
        a1 = a1,
        a2 = a2,
        channelCount = counts.size,
        counts = SpectrumBlob.encode(counts),
        deviceSerial = deviceSerial,
        firmware = firmware,
        epochId = epochId,
        trigger = trigger,
        profileId = profileId,
        profileName = profileName,
    )

fun SpectrumSnapshotEntity.toSpectrum(): Spectrum = Spectrum(
    durationSeconds = durationSeconds,
    a0 = a0,
    a1 = a1,
    a2 = a2,
    counts = SpectrumBlob.decode(counts),
)
