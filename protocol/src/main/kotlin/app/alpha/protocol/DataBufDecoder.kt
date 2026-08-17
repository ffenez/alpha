/*
 * Kotlin port of cdump/radiacode (https://github.com/cdump/radiacode).
 * Original work: MIT License, Copyright 2021 Maxim Andreev.
 */
package app.alpha.protocol

/**
 * Result of one DATA_BUF decode pass. [seqGaps] counts sequence-number
 * discontinuities: records the device dropped (buffer overflow between
 * polls) — a diagnostic, not an error, because record boundaries stay
 * intact (each record is self-delimiting by its eid/gid).
 */
data class DataBufResult(
    val records: List<DataBufRecord>,
    val seqGaps: Int,
)

/**
 * Decoder for the VS DATA_BUF payload: a sequence of records, each
 * `<u8 seq><u8 eid><u8 gid><i32 tsOffset(10ms units)>` followed by a
 * group-specific body. Port of cdump's `decode_VS_DATA_BUF`.
 *
 * A sequence-number gap does not stop the parse (qtradiacode approach):
 * the stream stays record-aligned, so decoding resyncs on the next record
 * and only counts the gap in [DataBufResult.seqGaps]. Rare records
 * (accumulated dose, battery) and events after a gap are not lost.
 */
object DataBufDecoder {

    /**
     * @param payload DATA_BUF payload (after VS read envelope parsing)
     * @param baseTimeMillis caller-established base time; record timestamps are
     *        `baseTimeMillis + tsOffset * 10 ms`. cdump sets the base to
     *        (host time at init + 128 s).
     */
    fun decode(payload: ByteArray, baseTimeMillis: Long): DataBufResult {
        val r = BytesReader(payload)
        val ret = mutableListOf<DataBufRecord>()
        var nextSeq: Int? = null
        var seqGaps = 0

        while (r.remaining() >= 7) {
            val seq = r.readU8()
            val eid = r.readU8()
            val gid = r.readU8()
            val tsOffset = r.readI32()
            val ts = baseTimeMillis + tsOffset.toLong() * 10

            if (nextSeq != null && nextSeq != seq) seqGaps++
            nextSeq = (seq + 1) % 256

            when {
                eid == 0 && gid == 0 -> { // GRP_RealTimeData
                    val countRate = r.readF32()
                    val doseRate = r.readF32()
                    val countRateErr = r.readU16()
                    val doseRateErr = r.readU16()
                    val flags = r.readU16()
                    val rtFlags = r.readU8()
                    ret += RealTimeData(
                        timestampMillis = ts,
                        tsOffset10ms = tsOffset,
                        countRate = countRate,
                        countRateErr = countRateErr / 10f,
                        doseRate = doseRate,
                        doseRateErr = doseRateErr / 10f,
                        flags = flags,
                        realTimeFlags = rtFlags,
                    )
                }

                eid == 0 && gid == 1 -> { // GRP_RawData
                    ret += RawData(
                        timestampMillis = ts,
                        tsOffset10ms = tsOffset,
                        countRate = r.readF32(),
                        doseRate = r.readF32(),
                    )
                }

                eid == 0 && gid == 2 -> { // GRP_DoseRateDB
                    val count = r.readU32()
                    val countRate = r.readF32()
                    val doseRate = r.readF32()
                    val doseRateErr = r.readU16()
                    val flags = r.readU16()
                    ret += DoseRateDB(
                        timestampMillis = ts,
                        tsOffset10ms = tsOffset,
                        count = count,
                        countRate = countRate,
                        doseRate = doseRate,
                        doseRateErr = doseRateErr / 10f,
                        flags = flags,
                    )
                }

                eid == 0 && gid == 3 -> { // GRP_RareData
                    val duration = r.readU32()
                    val dose = r.readF32()
                    val temperature = r.readU16()
                    val charge = r.readU16()
                    val flags = r.readU16()
                    ret += RareData(
                        timestampMillis = ts,
                        tsOffset10ms = tsOffset,
                        durationSeconds = duration,
                        dose = dose,
                        temperature = (temperature - 2000) / 100f,
                        chargeLevel = charge / 100f,
                        flags = flags,
                    )
                }

                eid == 0 && gid == 4 -> r.skip(16) // GRP_UserData <IffHH>
                eid == 0 && gid == 5 -> r.skip(16) // GRP_SheduleData <IffHH>
                eid == 0 && gid == 6 -> r.skip(6) // GRP_AccelData <HHH>

                eid == 0 && gid == 7 -> { // GRP_Event
                    val event = r.readU8()
                    val param1 = r.readU8()
                    val flags = r.readU16()
                    ret += Event(
                        timestampMillis = ts,
                        tsOffset10ms = tsOffset,
                        eventId = EventId.fromCode(event),
                        eventCode = event,
                        eventParam1 = param1,
                        flags = flags,
                    )
                }

                eid == 0 && gid == 8 -> r.skip(6) // GRP_RawCountRate <fH>
                eid == 0 && gid == 9 -> r.skip(6) // GRP_RawDoseRate <fH>

                eid == 1 && gid == 1 -> skipSampleBlock(r, 8)
                eid == 1 && gid == 2 -> skipSampleBlock(r, 16)
                eid == 1 && gid == 3 -> skipSampleBlock(r, 14)

                // Unknown record type: stop — its body length is unknown, so
                // the remaining bytes cannot be re-aligned.
                else -> return DataBufResult(ret, seqGaps)
            }
        }
        return DataBufResult(ret, seqGaps)
    }

    /** `<u16 samplesNum><u32 smplTimeMs>` + samplesNum * sampleSize bytes. */
    private fun skipSampleBlock(r: BytesReader, sampleSize: Int) {
        val samplesNum = r.readU16()
        r.skip(4) // smpl_time_ms
        r.skip(sampleSize * samplesNum)
    }
}
