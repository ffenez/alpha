package app.alpha.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpectrumDecoderTest {

    private fun header(durationSeconds: Long, a0: Float, a1: Float, a2: Float) =
        LeWriter().u32(durationSeconds).f32(a0).f32(a1).f32(a2)

    @Test
    fun `decodes v0 spectrum with raw u32 channels`() {
        val payload = header(3600, 1.5f, 2.5f, 0.25f)
            .i32(0).i32(17).i32(65536).i32(1_000_000)
            .build()
        val s = SpectrumDecoder.decode(payload, formatVersion = 0)
        assertEquals(3600L, s.durationSeconds)
        assertEquals(1.5f, s.a0)
        assertEquals(2.5f, s.a1)
        assertEquals(0.25f, s.a2)
        assertEquals(listOf(0, 17, 65536, 1_000_000), s.counts)
    }

    @Test
    fun `decodes v1 spectrum covering every RLE code`() {
        val payload = header(600, 0f, 3f, 0f)
            // code 1: two u8 absolute values [5, 7]
            .u16((2 shl 4) or 1).u8(5).u8(7)
            // code 0: three zeros (resets `last` to 0)
            .u16((3 shl 4) or 0)
            // code 2: i8 deltas from last=0: +10 -> 10, -3 -> 7
            .u16((2 shl 4) or 2).i8(10).i8(-3)
            // code 3: i16 delta +1000 -> 1007
            .u16((1 shl 4) or 3).i16(1000)
            // code 4: i24 delta +100000 -> 101007 (LE bytes a0 86 01)
            .u16((1 shl 4) or 4).raw(0xA0, 0x86, 0x01)
            // code 5: i32 delta -100000 -> 1007
            .u16((1 shl 4) or 5).i32(-100_000)
            // code 4 with negative delta: -1000 -> 7 (LE bytes 18 fc ff)
            .u16((1 shl 4) or 4).raw(0x18, 0xFC, 0xFF)
            .build()

        val s = SpectrumDecoder.decode(payload, formatVersion = 1)
        assertEquals(600L, s.durationSeconds)
        assertEquals(
            listOf(5, 7, 0, 0, 0, 10, 7, 1007, 101_007, 1007, 7),
            s.counts,
        )
    }

    @Test
    fun `v1 zero run resets the delta baseline`() {
        val payload = header(1, 0f, 1f, 0f)
            .u16((1 shl 4) or 3).i16(500) // last=0 -> 500
            .u16((1 shl 4) or 0) // zero, last=0
            .u16((1 shl 4) or 2).i8(4) // delta from 0 -> 4
            .build()
        val s = SpectrumDecoder.decode(payload, formatVersion = 1)
        assertEquals(listOf(500, 0, 4), s.counts)
    }

    @Test
    fun `v1 u8 absolute run also updates the delta baseline`() {
        val payload = header(1, 0f, 1f, 0f)
            .u16((1 shl 4) or 3).i16(1000)
            .u16((1 shl 4) or 1).u8(200) // absolute 200, last=200
            .u16((1 shl 4) or 2).i8(-50) // 150
            .build()
        assertEquals(listOf(1000, 200, 150), SpectrumDecoder.decode(payload, formatVersion = 1).counts)
    }

    @Test
    fun `v1 rejects unsupported RLE code`() {
        val payload = header(1, 0f, 0f, 0f).u16((1 shl 4) or 7).build()
        assertFailsWith<ProtocolException> { SpectrumDecoder.decode(payload, formatVersion = 1) }
    }

    @Test
    fun `rejects unsupported format version`() {
        val payload = header(1, 0f, 0f, 0f).build()
        assertFailsWith<ProtocolException> { SpectrumDecoder.decode(payload, formatVersion = 2) }
    }

    @Test
    fun `energy calibration is quadratic in the channel number`() {
        val s = Spectrum(durationSeconds = 1, a0 = 10f, a1 = 2f, a2 = 0.5f, counts = emptyList())
        assertEquals(10f, s.channelToEnergy(0))
        assertEquals(12.5f, s.channelToEnergy(1))
        assertEquals(10f + 2f * 100 + 0.5f * 100 * 100, s.channelToEnergy(100))
        assertEquals(s.channelToEnergy(512), Spectrum.channelToEnergy(512, 10f, 2f, 0.5f))
    }

    @Test
    fun `decodes a full 1024-channel v0 spectrum`() {
        val w = header(7200, -5.6f, 2.38f, 0.00042f)
        repeat(1024) { w.i32(it * 3) }
        val s = SpectrumDecoder.decode(w.build(), formatVersion = 0)
        assertEquals(1024, s.counts.size)
        assertEquals(3069, s.counts[1023])
    }
}
