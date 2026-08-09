/*
 * Kotlin port of cdump/radiacode (https://github.com/cdump/radiacode).
 * Original work: MIT License, Copyright 2021 Maxim Andreev.
 */
package app.radiacode.protocol

/**
 * Payload codec for virtual string (VS) and virtual SFR (VSFR) commands.
 * All ids are u32 LE on the wire; [Long] is used to cover the full unsigned range.
 */
object VsCodec {

    /** Args for RD_VIRT_STRING / RD_VIRT_SFR: `<u32 id>`. */
    fun readRequestArgs(id: Long): ByteArray = u32le(id)

    fun readRequestArgs(id: Int): ByteArray = readRequestArgs(id.toLong())

    /** Args for WR_VIRT_SFR: `<u32 id><data>`. */
    fun writeSfrArgs(id: Long, data: ByteArray = ByteArray(0)): ByteArray = u32le(id) + data

    /** Args for WR_VIRT_STRING: `<u32 id><u32 len><payload>`. */
    fun writeStringArgs(id: Long, payload: ByteArray = ByteArray(0)): ByteArray =
        u32le(id) + u32le(payload.size.toLong()) + payload

    /**
     * Parses a RD_VIRT_STRING response body: `<u32 retcode><u32 len><payload>`.
     *
     * Tolerates the known firmware quirk where the payload carries one extra
     * trailing 0x00 byte beyond the declared length.
     *
     * @param reader positioned after the 4-byte echoed header
     * @return the payload, exactly `len` bytes
     */
    fun parseReadPayload(reader: BytesReader): ByteArray {
        val retcode = reader.readU32()
        if (retcode != 1L) {
            throw ProtocolException("VS read failed: retcode=$retcode")
        }
        val len = reader.readU32().toInt()
        var payload = reader.bytes()
        // HACK from cdump: workaround for new firmware bug(?) - extra trailing 0x00
        if (payload.size == len + 1 && payload[payload.size - 1] == 0.toByte()) {
            payload = payload.copyOfRange(0, len)
        }
        if (payload.size != len) {
            throw ProtocolException("VS read: got ${payload.size} bytes, expected $len")
        }
        return payload
    }

    /** Parses a WR_VIRT_SFR / WR_VIRT_STRING response body: `<u32 retcode>` and nothing else. */
    fun parseWriteResponse(reader: BytesReader) {
        val retcode = reader.readU32()
        if (retcode != 1L) {
            throw ProtocolException("VS/VSFR write failed: retcode=$retcode")
        }
        if (reader.remaining() != 0) {
            throw ProtocolException("VS/VSFR write: ${reader.remaining()} unexpected trailing bytes")
        }
    }

    /** Args for SET_TIME: `<day><month><year-2000><0><second><minute><hour><0>`. */
    fun setTimeArgs(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): ByteArray =
        byteArrayOf(
            day.toByte(),
            month.toByte(),
            (year - 2000).toByte(),
            0,
            second.toByte(),
            minute.toByte(),
            hour.toByte(),
            0,
        )

    private fun u32le(v: Long): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )
}
