package app.alpha.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FramingTest {

    @Test
    fun `request framing for GET_STATUS without args`() {
        val req = RequestFramer().nextRequest(Command.GET_STATUS)
        // <u32 len=4><u16 cmd=0x0005><0x00><seq=0x80>
        val expected = LeWriter().raw(0x04, 0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x80).build()
        assertContentEquals(expected, req.bytes)
        assertContentEquals(byteArrayOf(0x05, 0x00, 0x00, 0x80.toByte()), req.header)
    }

    @Test
    fun `request framing for SET_EXCHANGE with init payload`() {
        val req = RequestFramer().nextRequest(Command.SET_EXCHANGE, Command.SET_EXCHANGE_PAYLOAD)
        val expected = LeWriter()
            .raw(0x08, 0x00, 0x00, 0x00) // len = 8
            .raw(0x07, 0x00, 0x00, 0x80) // cmd 0x0007, zero, seq
            .raw(0x01, 0xFF, 0x12, 0xFF) // args
            .build()
        assertContentEquals(expected, req.bytes)
    }

    @Test
    fun `sequence number starts at 0x80 and wraps after 32 requests`() {
        val framer = RequestFramer()
        val seqNos = (0 until 70).map { framer.nextRequest(Command.GET_STATUS).header[3].toInt() and 0xFF }
        assertEquals((0x80..0x9F).toList(), seqNos.take(32))
        assertEquals(0x80, seqNos[32])
        assertEquals(0x9F, seqNos[63])
        assertEquals(0x80 + 5, seqNos[69])
        assertTrue(seqNos.all { it in 0x80..0x9F })
    }

    @Test
    fun `matchResponse accepts echoed header and positions reader after it`() {
        val req = RequestFramer().nextRequest(Command.GET_STATUS)
        val response = LeWriter().raw(req.header).u32(0xDEADBEEFL).build()
        val reader = req.matchResponse(response)
        assertEquals(0xDEADBEEFL, reader.readU32())
        assertEquals(0, reader.remaining())
    }

    @Test
    fun `matchResponse rejects mismatched header`() {
        val framer = RequestFramer()
        val req1 = framer.nextRequest(Command.GET_STATUS)
        val req2 = framer.nextRequest(Command.GET_STATUS)
        val response = LeWriter().raw(req1.header).u32(0L).build()
        assertFailsWith<ProtocolException> { req2.matchResponse(response) }
    }

    @Test
    fun `matchResponse rejects short response`() {
        val req = RequestFramer().nextRequest(Command.GET_STATUS)
        assertFailsWith<ProtocolException> { req.matchResponse(byteArrayOf(0x05, 0x00)) }
    }
}

class ResponseAssemblerTest {

    private fun response(body: ByteArray): ByteArray =
        LeWriter().i32(body.size).raw(body).build()

    @Test
    fun `single chunk containing a complete response`() {
        val body = LeWriter().raw(0x05, 0x00, 0x00, 0x80).u32(42).build()
        val out = ResponseAssembler().feed(response(body))
        assertEquals(1, out.size)
        assertContentEquals(body, out[0])
    }

    @Test
    fun `response fragmented across chunks including a split length prefix`() {
        val body = ByteArray(40) { (it + 1).toByte() }
        val stream = response(body)
        val assembler = ResponseAssembler()

        // length prefix split in the middle
        assertTrue(assembler.feed(stream.copyOfRange(0, 2)).isEmpty())
        assertTrue(assembler.feed(stream.copyOfRange(2, 20)).isEmpty())
        assertTrue(assembler.feed(stream.copyOfRange(20, 43)).isEmpty())
        val out = assembler.feed(stream.copyOfRange(43, stream.size))
        assertEquals(1, out.size)
        assertContentEquals(body, out[0])
        assertEquals(0, assembler.pendingBytes())
    }

    @Test
    fun `chunk spanning the boundary of two responses emits both in order`() {
        val body1 = LeWriter().raw(0x0A, 0x00, 0x00, 0x80).u16(1).build()
        val body2 = LeWriter().raw(0x0B, 0x00, 0x00, 0x81).u16(2).build()
        val stream = response(body1) + response(body2)
        val assembler = ResponseAssembler()

        val first = assembler.feed(stream.copyOfRange(0, body1.size + 6))
        assertEquals(1, first.size)
        assertContentEquals(body1, first[0])

        val second = assembler.feed(stream.copyOfRange(body1.size + 6, stream.size))
        assertEquals(1, second.size)
        assertContentEquals(body2, second[0])
    }

    @Test
    fun `two complete responses in one chunk`() {
        val body1 = byteArrayOf(1, 2, 3, 4, 5)
        val body2 = byteArrayOf(6, 7, 8, 9)
        val out = ResponseAssembler().feed(response(body1) + response(body2))
        assertEquals(2, out.size)
        assertContentEquals(body1, out[0])
        assertContentEquals(body2, out[1])
    }

    @Test
    fun `reset drops buffered partial data`() {
        val assembler = ResponseAssembler()
        assembler.feed(byteArrayOf(0x10, 0x00, 0x00))
        assertEquals(3, assembler.pendingBytes())
        assembler.reset()
        assertEquals(0, assembler.pendingBytes())
    }
}
