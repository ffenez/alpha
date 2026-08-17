package app.alpha.data

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpectrumBlobTest {

    @Test
    fun `roundtrips 1024 channels`() {
        val random = Random(42)
        val counts = List(1024) { random.nextInt(0, 1 shl 24) }
        assertEquals(counts, SpectrumBlob.decode(SpectrumBlob.encode(counts)))
    }

    @Test
    fun `layout is fixed little-endian`() {
        assertContentEquals(
            byteArrayOf(0x01, 0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F),
            SpectrumBlob.encode(listOf(1, Int.MAX_VALUE)),
        )
    }

    @Test
    fun `rejects torn blobs`() {
        assertFailsWith<IllegalArgumentException> { SpectrumBlob.decode(ByteArray(5)) }
    }
}
