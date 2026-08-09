package app.radiacode.protocol

/** Little-endian byte fixture builder for tests. */
class LeWriter {
    private val bytes = mutableListOf<Byte>()

    fun u8(v: Int) = apply { bytes += (v and 0xFF).toByte() }

    fun i8(v: Int) = apply { bytes += v.toByte() }

    fun u16(v: Int) = apply {
        bytes += (v and 0xFF).toByte()
        bytes += ((v shr 8) and 0xFF).toByte()
    }

    fun i16(v: Int) = u16(v and 0xFFFF)

    fun i32(v: Int) = apply {
        bytes += (v and 0xFF).toByte()
        bytes += ((v shr 8) and 0xFF).toByte()
        bytes += ((v shr 16) and 0xFF).toByte()
        bytes += ((v shr 24) and 0xFF).toByte()
    }

    fun u32(v: Long) = i32(v.toInt())

    fun f32(v: Float) = i32(v.toRawBits())

    fun raw(vararg v: Int) = apply { v.forEach { u8(it) } }

    fun raw(v: ByteArray) = apply { v.forEach { bytes += it } }

    fun build(): ByteArray = bytes.toByteArray()
}
