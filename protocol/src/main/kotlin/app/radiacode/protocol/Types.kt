/*
 * Kotlin port of cdump/radiacode (https://github.com/cdump/radiacode).
 * Original work: MIT License, Copyright 2021 Maxim Andreev.
 */
package app.radiacode.protocol

/**
 * A DATA_BUF record. Timestamps are offset-based: the device reports a signed
 * offset in 10 ms units relative to a base time the caller establishes at
 * init ([tsOffset10ms]); [timestampMillis] = baseTimeMillis + tsOffset10ms * 10.
 */
sealed interface DataBufRecord {
    val timestampMillis: Long
    val tsOffset10ms: Int
}

/** Real-time measurement (eid=0, gid=0). Dose rate unit as reported by the device. */
data class RealTimeData(
    override val timestampMillis: Long,
    override val tsOffset10ms: Int,
    val countRate: Float,
    val countRateErr: Float,
    val doseRate: Float,
    val doseRateErr: Float,
    val flags: Int,
    val realTimeFlags: Int,
) : DataBufRecord

/** Raw measurement without error estimates (eid=0, gid=1). */
data class RawData(
    override val timestampMillis: Long,
    override val tsOffset10ms: Int,
    val countRate: Float,
    val doseRate: Float,
) : DataBufRecord

/** Dose-rate history record (eid=0, gid=2). */
data class DoseRateDB(
    override val timestampMillis: Long,
    override val tsOffset10ms: Int,
    val count: Long,
    val countRate: Float,
    val doseRate: Float,
    val doseRateErr: Float,
    val flags: Int,
) : DataBufRecord

/** Periodic status: accumulated dose, temperature (°C), battery charge (%) (eid=0, gid=3). */
data class RareData(
    override val timestampMillis: Long,
    override val tsOffset10ms: Int,
    val durationSeconds: Long,
    val dose: Float,
    val temperature: Float,
    val chargeLevel: Float,
    val flags: Int,
) : DataBufRecord

enum class EventId(val code: Int) {
    POWER_OFF(0),
    POWER_ON(1),
    LOW_BATTERY_SHUTDOWN(2),
    CHANGE_DEVICE_PARAMS(3),
    DOSE_RESET(4),
    USER_EVENT(5),
    BATTERY_EMPTY_ALARM(6),
    CHARGE_START(7),
    CHARGE_STOP(8),
    DOSE_RATE_ALARM1(9),
    DOSE_RATE_ALARM2(10),
    DOSE_RATE_OFFSCALE(11),
    DOSE_ALARM1(12),
    DOSE_ALARM2(13),
    DOSE_OFFSCALE(14),
    TEMPERATURE_TOO_LOW(15),
    TEMPERATURE_TOO_HIGH(16),
    TEXT_MESSAGE(17),
    MEMORY_SNAPSHOT(18),
    SPECTRUM_RESET(19),
    COUNT_RATE_ALARM1(20),
    COUNT_RATE_ALARM2(21),
    COUNT_RATE_OFFSCALE(22),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromCode(code: Int): EventId = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Device event (eid=0, gid=7). */
data class Event(
    override val timestampMillis: Long,
    override val tsOffset10ms: Int,
    val eventId: EventId,
    val eventCode: Int,
    val eventParam1: Int,
    val flags: Int,
) : DataBufRecord

/**
 * Energy spectrum: 1024 channels; E(keV) = a0 + a1*ch + a2*ch².
 */
data class Spectrum(
    val durationSeconds: Long,
    val a0: Float,
    val a1: Float,
    val a2: Float,
    val counts: List<Int>,
) {
    /** Energy in keV for a channel number using the quadratic calibration. */
    fun channelToEnergy(channel: Int): Float = a0 + a1 * channel + a2 * channel * channel

    companion object {
        fun channelToEnergy(channel: Int, a0: Float, a1: Float, a2: Float): Float =
            a0 + a1 * channel + a2 * channel * channel
    }
}
