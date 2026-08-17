package app.alpha.device

import app.alpha.protocol.Command
import app.alpha.protocol.ResponseAssembler
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Minimal link that captures chunks; responses are injected via the client. */
private class CapturingLink : DeviceLink {
    val chunks = mutableListOf<ByteArray>()
    val requestAssembler = ResponseAssembler()
    val completedRequests = mutableListOf<ByteArray>()
    var onRequest: ((ByteArray) -> Unit)? = null

    override val notifications: Flow<ByteArray> = emptyFlow()

    override suspend fun write(chunk: ByteArray) {
        chunks += chunk
        for (request in requestAssembler.feed(chunk)) {
            completedRequests += request
            onRequest?.invoke(request)
        }
    }

    override suspend fun awaitDisconnect() = Unit
    override suspend fun close() = Unit
}

@kotlinx.coroutines.ExperimentalCoroutinesApi
class ProtocolClientTest {

    @Test
    fun `writes requests in chunks of at most 18 bytes`() = runTest {
        val link = CapturingLink()
        val client = ProtocolClient(link)
        link.onRequest = { request ->
            client.onNotification(Wire.frame(request.copyOfRange(0, 4)))
        }

        // 4 (len) + 4 (header) + 30 (args) = 38 bytes -> 18 + 18 + 2.
        client.execute(Command.GET_STATUS, ByteArray(30))

        assertEquals(listOf(18, 18, 2), link.chunks.map { it.size })
        val reassembled = link.chunks.reduce(ByteArray::plus)
        assertEquals(38, reassembled.size)
        // Length prefix covers everything after itself.
        assertEquals(34, reassembled[0].toInt())
    }

    @Test
    fun `matches response to request header and returns body reader`() = runTest {
        val link = CapturingLink()
        val client = ProtocolClient(link)
        link.onRequest = { request ->
            client.onNotification(Wire.frame(request.copyOfRange(0, 4) + byteArrayOf(7, 8, 9)))
        }

        val reader = client.execute(Command.GET_STATUS)

        assertContentEquals(byteArrayOf(7, 8, 9), reader.bytes())
    }

    @Test
    fun `a foreign frame is skipped, not treated as a broken exchange`() = runTest {
        // Полевой дефект: нажатие кнопок на самом приборе обрывало связь в
        // приложении. Канал уведомлений один на всё, и кадр, которого мы не
        // заказывали, считался ошибкой протокола — сессия падала и
        // пересобиралась с паузой. Чужой кадр не ошибка: ждём свой ответ.
        val link = CapturingLink()
        val client = ProtocolClient(link)
        link.onRequest = { request ->
            client.onNotification(Wire.frame(byteArrayOf(0x0A, 0, 0, 0x7F)))
            client.onNotification(Wire.frame(request.copyOfRange(0, 4) + byteArrayOf(42)))
        }

        val reader = client.execute(Command.GET_STATUS)

        assertContentEquals(byteArrayOf(42), reader.bytes())
        // Повтора не потребовалось: запрос ушёл ровно один раз.
        assertEquals(1, link.completedRequests.size)
        assertEquals(1, client.unmatchedFrames)
    }

    @Test
    fun `a burst of foreign frames does not consume the answer`() = runTest {
        val link = CapturingLink()
        val client = ProtocolClient(link)
        link.onRequest = { request ->
            repeat(5) { i ->
                client.onNotification(Wire.frame(byteArrayOf(0x0A, 0, 0, (0x70 + i).toByte())))
            }
            client.onNotification(Wire.frame(request.copyOfRange(0, 4) + byteArrayOf(7)))
        }

        val reader = client.execute(Command.GET_STATUS)

        assertContentEquals(byteArrayOf(7), reader.bytes())
        assertEquals(5, client.unmatchedFrames)
    }

    @Test
    fun `a timeout is a fact about the device, not a cancellation`() = runTest {
        // Тип имеет значение: `TimeoutCancellationException` — наследник
        // `CancellationException`, и вызывающий код читал один медленный ответ
        // как «нас отменили». Цикл связи заканчивался НАВСЕГДА вместо
        // переподключения — прибор «отваливался» до перезапуска службы.
        val link = CapturingLink()
        val client = ProtocolClient(link)

        val startedAt = testScheduler.currentTime
        val failure = assertFailsWith<DeviceTimeoutException> { client.execute(Command.GET_STATUS) }

        assertEquals(12_000, testScheduler.currentTime - startedAt)
        assertTrue(failure !is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun `stale responses from a timed-out request are dropped`() = runTest {
        val link = CapturingLink()
        val client = ProtocolClient(link)

        // First request times out; its response arrives too late.
        assertFailsWith<DeviceTimeoutException> { client.execute(Command.GET_STATUS) }
        val lateRequest = link.completedRequests.removeAt(0)
        client.onNotification(Wire.frame(lateRequest.copyOfRange(0, 4) + byteArrayOf(1)))

        // Next request must not be confused by the stale response.
        link.onRequest = { request ->
            client.onNotification(Wire.frame(request.copyOfRange(0, 4) + byteArrayOf(42)))
        }
        val reader = client.execute(Command.GET_STATUS)
        assertContentEquals(byteArrayOf(42), reader.bytes())
    }

    @Test
    fun `responses split across notifications are reassembled`() = runTest {
        val link = CapturingLink()
        val client = ProtocolClient(link)
        link.onRequest = { request ->
            val frame = Wire.frame(request.copyOfRange(0, 4) + byteArrayOf(1, 2, 3, 4, 5))
            // Deliver in 3-byte notification chunks.
            var offset = 0
            while (offset < frame.size) {
                val end = minOf(offset + 3, frame.size)
                client.onNotification(frame.copyOfRange(offset, end))
                offset = end
            }
        }

        val reader = client.execute(Command.GET_STATUS)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), reader.bytes())
    }

    @Test
    fun `inter-chunk delay is applied`() = runTest {
        val link = CapturingLink()
        val client = ProtocolClient(link)
        link.onRequest = { request ->
            client.onNotification(Wire.frame(request.copyOfRange(0, 4)))
        }

        val startedAt = testScheduler.currentTime
        client.execute(Command.GET_STATUS, ByteArray(30)) // 3 chunks -> 2 delays
        assertTrue(testScheduler.currentTime - startedAt >= 10)
    }
}
