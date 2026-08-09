package app.radiacode.data

/**
 * Channel-count blob codec for spectrum snapshots: i32 LE per channel.
 * Fixed layout (independent of JVM endianness) so DB files stay portable.
 */
object SpectrumBlob {

    fun encode(counts: List<Int>): ByteArray {
        val bytes = ByteArray(counts.size * 4)
        for ((index, value) in counts.withIndex()) {
            val offset = index * 4
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        return bytes
    }

    fun decode(blob: ByteArray): List<Int> {
        require(blob.size % 4 == 0) { "Spectrum blob size ${blob.size} is not a multiple of 4" }
        val counts = ArrayList<Int>(blob.size / 4)
        var offset = 0
        while (offset < blob.size) {
            counts += (blob[offset].toInt() and 0xFF) or
                ((blob[offset + 1].toInt() and 0xFF) shl 8) or
                ((blob[offset + 2].toInt() and 0xFF) shl 16) or
                ((blob[offset + 3].toInt() and 0xFF) shl 24)
            offset += 4
        }
        return counts
    }
}
