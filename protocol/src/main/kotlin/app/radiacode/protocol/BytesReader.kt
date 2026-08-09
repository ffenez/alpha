/*
 * Kotlin port of cdump/radiacode (https://github.com/cdump/radiacode).
 * Original work: MIT License, Copyright 2021 Maxim Andreev.
 */
package app.radiacode.protocol

/**
 * Sequential little-endian reader over a [ByteArray] with position tracking.
 * Port of cdump's `BytesBuffer`.
 */
class BytesReader(private val data: ByteArray, private var pos: Int = 0) {

    /** Number of unread bytes remaining. */
    fun remaining(): Int = data.size - pos

    /** Remaining unread bytes as a copy. */
    fun bytes(): ByteArray = data.copyOfRange(pos, data.size)

    fun readU8(): Int = next().toInt() and 0xFF

    fun readI8(): Int = next().toInt()

    fun readU16(): Int {
        require(2)
        val v = (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        return v
    }

    fun readI16(): Int = readU16().toShort().toInt()

    fun readU32(): Long = readI32().toLong() and 0xFFFFFFFFL

    fun readI32(): Int {
        require(4)
        val v = (data[pos].toInt() and 0xFF) or
            ((data[pos + 1].toInt() and 0xFF) shl 8) or
            ((data[pos + 2].toInt() and 0xFF) shl 16) or
            ((data[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return v
    }

    fun readF32(): Float = Float.fromBits(readI32())

    fun readBytes(count: Int): ByteArray {
        require(count)
        val v = data.copyOfRange(pos, pos + count)
        pos += count
        return v
    }

    fun skip(count: Int) {
        require(count)
        pos += count
    }

    /** Length-prefixed ASCII string: `<u8 len><bytes>`. */
    fun readString(): String {
        val len = readU8()
        return readBytes(len).toString(Charsets.US_ASCII)
    }

    private fun next(): Byte {
        require(1)
        return data[pos++]
    }

    private fun require(count: Int) {
        if (pos + count > data.size) {
            throw ProtocolException("BytesReader: $count bytes required, but only ${data.size - pos} remain")
        }
    }
}

/** Malformed or unexpected protocol data. */
class ProtocolException(message: String) : Exception(message)
