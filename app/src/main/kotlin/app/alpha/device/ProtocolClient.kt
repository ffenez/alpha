package app.alpha.device

import app.alpha.protocol.BytesReader
import app.alpha.protocol.RequestFramer
import app.alpha.protocol.ResponseAssembler
import java.io.IOException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Прибор не ответил на команду за отведённое время.
 *
 * Отдельный тип, а не `TimeoutCancellationException`: тайм-аут это ФАКТ О
 * ПРИБОРЕ, а не отмена нашей корутины. Из-за общего предка `CancellationException`
 * один медленный ответ выглядел для вызывающего кода как «нас отменили» — и
 * цикл связи заканчивался навсегда вместо переподключения.
 */
class DeviceTimeoutException(message: String) : IOException(message)

/**
 * Sequential request/response executor over a [DeviceLink].
 *
 * The device supports exactly one request in flight; [execute] serializes
 * callers with a mutex, writes the framed request in [CHUNK_SIZE]-byte
 * write-with-response chunks with [INTER_CHUNK_DELAY_MILLIS] between them
 * (mkgeiger/RadiaCode recipe), then waits for the response whose first four
 * bytes echo the request header.
 *
 * **Чужой кадр пропускается, а не рвёт обмен.** Канал уведомлений один на
 * всё: в нём оказывается и хвост прежнего обмена, и кадры, которых мы не
 * заказывали. Прежде первый же такой кадр считался ошибкой протокола, и
 * сессия падала — полевая картина: нажатие кнопок на самом приборе обрывало
 * связь в приложении. Теперь [execute] ждёт СВОЙ ответ по эху заголовка,
 * считая пропущенные кадры в [unmatchedFrames] для отладочного отчёта.
 *
 * The owner must pump [DeviceLink.notifications] into [onNotification].
 */
class ProtocolClient(
    private val link: DeviceLink,
    private val timeouts: Timeouts = Timeouts(),
) {

    private val framer = RequestFramer()
    private val assembler = ResponseAssembler()
    private val responses = Channel<ByteArray>(Channel.UNLIMITED)
    private val inFlight = Mutex()

    /** Кадры, пришедшие не на наш запрос, за сессию — только для диагностики. */
    @Volatile
    var unmatchedFrames: Int = 0
        private set

    /** Feed every notification payload here, in arrival order. */
    fun onNotification(chunk: ByteArray) {
        for (response in assembler.feed(chunk)) {
            responses.trySend(response)
        }
    }

    /**
     * Executes one command and returns a reader positioned at the response body
     * (after the echoed header).
     *
     * Throws [DeviceTimeoutException], если свой ответ не пришёл за отведённое
     * время. Чужие кадры не ошибка: они пропускаются и считаются в
     * [unmatchedFrames].
     */
    suspend fun execute(command: Int, args: ByteArray = EMPTY): BytesReader = inFlight.withLock {
        drainPending()
        val request = framer.nextRequest(command, args)
        val timeout = timeouts.forRequest(command, args)
        try {
            return withTimeout(timeout) {
                writeChunked(request.bytes)
                var response = responses.receive()
                while (!request.matches(response)) {
                    unmatchedFrames += 1
                    response = responses.receive()
                }
                request.matchResponse(response)
            }
        } catch (_: TimeoutCancellationException) {
            throw DeviceTimeoutException("No response to command 0x${command.toString(16)} in $timeout ms")
        }
    }

    /** Drops buffered responses from previous timed-out/mismatched requests. */
    private fun drainPending() {
        while (responses.tryReceive().isSuccess) Unit
    }

    private suspend fun writeChunked(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + CHUNK_SIZE, bytes.size)
            link.write(bytes.copyOfRange(offset, end))
            offset = end
            if (offset < bytes.size) delay(INTER_CHUNK_DELAY_MILLIS)
        }
    }

    companion object {
        const val CHUNK_SIZE = 18
        const val INTER_CHUNK_DELAY_MILLIS = 5L
        private val EMPTY = ByteArray(0)
    }
}
