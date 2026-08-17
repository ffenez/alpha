/*
 * Kotlin port of cdump/radiacode (https://github.com/cdump/radiacode).
 * Original work: MIT License, Copyright 2021 Maxim Andreev.
 * Cross-checked against mkgeiger/RadiaCode (MIT).
 */
package app.alpha.protocol

/**
 * Decoder for VS SPECTRUM / SPEC_ACCUM / SPEC_DIFF payloads.
 *
 * Header: `<u32 duration seconds><f32 a0><f32 a1><f32 a2>`, then channel
 * counts in format v0 (raw u32 per channel) or v1 (RLE + delta encoding).
 * The format version comes from the device configuration text
 * (`SpecFormatVersion=` line); 1024 channels on RadiaCode devices.
 */
object SpectrumDecoder {

    fun decode(payload: ByteArray, formatVersion: Int): Spectrum {
        val r = BytesReader(payload)
        val duration = r.readU32()
        val a0 = r.readF32()
        val a1 = r.readF32()
        val a2 = r.readF32()
        val counts = when (formatVersion) {
            0 -> decodeCountsV0(r)
            1 -> decodeCountsV1(r)
            else -> throw ProtocolException("Unsupported spectrum format version $formatVersion")
        }
        return Spectrum(durationSeconds = duration, a0 = a0, a1 = a1, a2 = a2, counts = counts)
    }

    private fun decodeCountsV0(r: BytesReader): List<Int> {
        val ret = mutableListOf<Int>()
        while (r.remaining() > 0) {
            ret += r.readI32()
        }
        return ret
    }

    /**
     * v1: sequence of runs. Each run starts with `<u16 word>`:
     * count = high 12 bits, code = low 4 bits. Per value in the run:
     * 0 = zero, 1 = u8 absolute, 2 = i8 delta, 3 = i16 delta,
     * 4 = i24 delta, 5 = i32 delta (deltas relative to the previous value).
     */
    private fun decodeCountsV1(r: BytesReader): List<Int> {
        val ret = mutableListOf<Int>()
        var last = 0
        while (r.remaining() > 0) {
            val word = r.readU16()
            val count = (word shr 4) and 0x0FFF
            val code = word and 0x0F
            repeat(count) {
                val v = when (code) {
                    0 -> 0
                    1 -> r.readU8()
                    2 -> last + r.readI8()
                    3 -> last + r.readI16()
                    4 -> {
                        val a = r.readU8()
                        val b = r.readU8()
                        val c = r.readI8()
                        last + ((c shl 16) or (b shl 8) or a)
                    }
                    5 -> last + r.readI32()
                    else -> throw ProtocolException("Unsupported RLE code $code in spectrum v1")
                }
                last = v
                ret += v
            }
        }
        return ret
    }
}
