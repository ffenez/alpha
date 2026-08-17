package app.alpha.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class VsCodecTest {

    @Test
    fun `read request args are u32 LE id`() {
        assertContentEquals(
            LeWriter().raw(0x00, 0x01, 0x00, 0x00).build(),
            VsCodec.readRequestArgs(Vs.DATA_BUF),
        )
        assertContentEquals(
            LeWriter().raw(0x0A, 0x00, 0xFF, 0xFF).build(),
            VsCodec.readRequestArgs(Vsfr.SYS_TARGET_VERSION),
        )
    }

    @Test
    fun `write string args carry id, length and payload`() {
        // spectrum reset: WR_VIRT_STRING <id=0x200><len=0>
        assertContentEquals(
            LeWriter().u32(0x200).u32(0).build(),
            VsCodec.writeStringArgs(Vs.SPECTRUM.toLong()),
        )
        val calib = LeWriter().f32(1.0f).f32(2.0f).f32(3.0f).build()
        assertContentEquals(
            LeWriter().u32(0x202).u32(12).raw(calib).build(),
            VsCodec.writeStringArgs(Vs.ENERGY_CALIB.toLong(), calib),
        )
    }

    @Test
    fun `write sfr args carry id and raw data`() {
        assertContentEquals(
            LeWriter().u32(Vsfr.DEVICE_TIME).u32(0).build(),
            VsCodec.writeSfrArgs(Vsfr.DEVICE_TIME, LeWriter().u32(0).build()),
        )
    }

    @Test
    fun `parseReadPayload returns exactly len bytes`() {
        val payload = byteArrayOf(10, 20, 30, 40, 50)
        val body = LeWriter().u32(1).u32(payload.size.toLong()).raw(payload).build()
        assertContentEquals(payload, VsCodec.parseReadPayload(BytesReader(body)))
    }

    @Test
    fun `parseReadPayload tolerates the trailing NUL firmware quirk`() {
        val payload = byteArrayOf(1, 2, 3)
        val body = LeWriter().u32(1).u32(3).raw(payload).u8(0x00).build()
        assertContentEquals(payload, VsCodec.parseReadPayload(BytesReader(body)))
    }

    @Test
    fun `parseReadPayload keeps a meaningful trailing extra byte as an error`() {
        // extra byte that is NOT 0x00 must not be silently dropped
        val body = LeWriter().u32(1).u32(3).raw(1, 2, 3).u8(0x77).build()
        assertFailsWith<ProtocolException> { VsCodec.parseReadPayload(BytesReader(body)) }
    }

    @Test
    fun `parseReadPayload rejects non-1 retcode`() {
        val body = LeWriter().u32(0x80000005L).u32(0).build()
        assertFailsWith<ProtocolException> { VsCodec.parseReadPayload(BytesReader(body)) }
    }

    @Test
    fun `parseReadPayload rejects length mismatch`() {
        val body = LeWriter().u32(1).u32(10).raw(1, 2, 3).build()
        assertFailsWith<ProtocolException> { VsCodec.parseReadPayload(BytesReader(body)) }
    }

    @Test
    fun `parseWriteResponse accepts retcode 1 with empty rest`() {
        VsCodec.parseWriteResponse(BytesReader(LeWriter().u32(1).build()))
    }

    @Test
    fun `parseWriteResponse rejects failure retcode and trailing bytes`() {
        assertFailsWith<ProtocolException> {
            VsCodec.parseWriteResponse(BytesReader(LeWriter().u32(0).build()))
        }
        assertFailsWith<ProtocolException> {
            VsCodec.parseWriteResponse(BytesReader(LeWriter().u32(1).u8(0).build()))
        }
    }

    @Test
    fun `setTimeArgs encode as day month year-2000 pad sec min hour pad`() {
        assertContentEquals(
            byteArrayOf(9, 8, 26, 0, 45, 30, 13, 0),
            VsCodec.setTimeArgs(year = 2026, month = 8, day = 9, hour = 13, minute = 30, second = 45),
        )
    }
}
