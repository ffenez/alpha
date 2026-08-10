package app.radiacode.device

import app.radiacode.protocol.BytesReader
import app.radiacode.protocol.ProtocolException
import app.radiacode.protocol.RequestFramer
import app.radiacode.protocol.ResponseAssembler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Sequential request/response executor over a [DeviceLink].
 *
 * The device supports exactly one request in flight; [execute] serializes
 * callers with a mutex, writes the framed request in [CHUNK_SIZE]-byte
 * write-with-response chunks with [INTER_CHUNK_DELAY_MILLIS] between them
 * (mkgeiger/RadiaCode recipe), then waits for the response whose first four
 * bytes echo the request header.
 *
 * A header-echo mismatch usually means a stale response from an earlier
 * exchange was still in flight (e.g. after a timeout mid-read). Instead of
 * failing the session, [execute] waits [MISMATCH_RETRY_BACKOFF_MILLIS] for
 * the stale traffic to land, drains every pending response, and retries the
 * command once with a fresh sequence number. A second mismatch propagates.
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

    /** Feed every notification payload here, in arrival order. */
    fun onNotification(chunk: ByteArray) {
        for (response in assembler.feed(chunk)) {
            responses.trySend(response)
        }
    }

    /**
     * Executes one command and returns a reader positioned at the response body
     * (after the echoed header). Throws [app.radiacode.protocol.ProtocolException]
     * on a header mismatch and [kotlinx.coroutines.TimeoutCancellationException]
     * on timeout.
     */
    suspend fun execute(command: Int, args: ByteArray = EMPTY): BytesReader = inFlight.withLock {
        drainPending()
        try {
            attempt(command, args)
        } catch (_: ProtocolException) {
            // Header mismatch: let in-flight stale notifications land, flush
            // them, then retry once with a fresh sequence number.
            delay(MISMATCH_RETRY_BACKOFF_MILLIS)
            drainPending()
            attempt(command, args)
        }
    }

    private suspend fun attempt(command: Int, args: ByteArray): BytesReader {
        val request = framer.nextRequest(command, args)
        return withTimeout(timeouts.forRequest(command, args)) {
            writeChunked(request.bytes)
            request.matchResponse(responses.receive())
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

        /** Pause before the single mismatch retry: stale BLE traffic settles. */
        const val MISMATCH_RETRY_BACKOFF_MILLIS = 100L
        private val EMPTY = ByteArray(0)
    }
}
