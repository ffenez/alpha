package app.radiacode.device

import app.radiacode.protocol.Command
import app.radiacode.protocol.Vs
import app.radiacode.protocol.VsCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeoutsTest {

    private val timeouts = Timeouts()

    @Test
    fun `default timeout is 12s`() {
        assertEquals(12_000, timeouts.forRequest(Command.GET_VERSION, ByteArray(0)))
        assertEquals(
            12_000,
            timeouts.forRequest(Command.RD_VIRT_STRING, VsCodec.readRequestArgs(Vs.DATA_BUF)),
        )
    }

    @Test
    fun `SET_EXCHANGE gets 25s`() {
        assertEquals(25_000, timeouts.forRequest(Command.SET_EXCHANGE, Command.SET_EXCHANGE_PAYLOAD))
    }

    @Test
    fun `spectrum reads get 30s`() {
        for (vs in listOf(Vs.SPECTRUM, Vs.SPEC_ACCUM, Vs.SPEC_DIFF)) {
            assertEquals(
                30_000,
                timeouts.forRequest(Command.RD_VIRT_STRING, VsCodec.readRequestArgs(vs)),
            )
        }
    }

    @Test
    fun `spectrum reset via write is not a slow read`() {
        assertEquals(
            12_000,
            timeouts.forRequest(Command.WR_VIRT_STRING, VsCodec.writeStringArgs(Vs.SPECTRUM.toLong())),
        )
    }
}
