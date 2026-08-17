/*
 * Kotlin port of cdump/radiacode (https://github.com/cdump/radiacode).
 * Original work: MIT License, Copyright 2021 Maxim Andreev.
 */
package app.alpha.protocol

/**
 * A framed request ready to be written to the device, plus the 4-byte header
 * the device echoes back at the start of the matching response.
 */
class Request(val bytes: ByteArray, val header: ByteArray) {

    /**
     * Verifies the response echoes this request's header and returns a reader
     * positioned at the response body.
     *
     * @param response complete response body (without the u32 length prefix),
     *                 as emitted by [ResponseAssembler].
     */
    /**
     * Is this response the answer to THIS request?
     *
     * Ответы прибора приходят по общему каналу уведомлений, и в нём может
     * оказаться чужой кадр — хвост прежнего обмена или ответ на команду,
     * отправленную не нами. Проверка отделена от разбора, чтобы клиент мог
     * ПРОПУСТИТЬ чужой кадр и дождаться своего, а не рвать сессию.
     */
    fun matches(response: ByteArray): Boolean =
        response.size >= 4 && response.copyOfRange(0, 4).contentEquals(header)

    fun matchResponse(response: ByteArray): BytesReader {
        if (response.size < 4) {
            throw ProtocolException("Response too short: ${response.size} bytes")
        }
        val echoed = response.copyOfRange(0, 4)
        if (!echoed.contentEquals(header)) {
            throw ProtocolException(
                "Response header mismatch: req=${header.toHexString()} resp=${echoed.toHexString()}"
            )
        }
        return BytesReader(response, pos = 4)
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}

/**
 * Builds framed requests:
 * `<u32 LE len><u16 LE command><0x00><seqNo>` + args,
 * where seqNo = 0x80 + counter % 32 and len covers everything after the length field.
 */
class RequestFramer {
    private var seq = 0

    fun nextRequest(command: Int, args: ByteArray = ByteArray(0)): Request {
        val seqNo = 0x80 + seq
        seq = (seq + 1) % 32

        val header = byteArrayOf(
            (command and 0xFF).toByte(),
            ((command shr 8) and 0xFF).toByte(),
            0x00,
            seqNo.toByte(),
        )
        val len = header.size + args.size
        val bytes = ByteArray(4 + len)
        bytes[0] = (len and 0xFF).toByte()
        bytes[1] = ((len shr 8) and 0xFF).toByte()
        bytes[2] = ((len shr 16) and 0xFF).toByte()
        bytes[3] = ((len shr 24) and 0xFF).toByte()
        header.copyInto(bytes, 4)
        args.copyInto(bytes, 8)
        return Request(bytes, header)
    }
}

/**
 * Reassembles length-prefixed responses from a stream of notification chunks.
 *
 * The device sends each response as `<u32 LE len><body>` split across BLE
 * notifications at arbitrary boundaries. Feed every notification payload in
 * arrival order; complete response bodies (length prefix stripped) are
 * returned as they become available.
 */
class ResponseAssembler {
    private var buffer = ByteArray(0)

    fun feed(chunk: ByteArray): List<ByteArray> {
        buffer += chunk
        val completed = mutableListOf<ByteArray>()
        while (buffer.size >= 4) {
            val len = (buffer[0].toInt() and 0xFF) or
                ((buffer[1].toInt() and 0xFF) shl 8) or
                ((buffer[2].toInt() and 0xFF) shl 16) or
                ((buffer[3].toInt() and 0xFF) shl 24)
            if (len < 0) throw ProtocolException("Negative response length: $len")
            if (buffer.size < 4 + len) break
            completed += buffer.copyOfRange(4, 4 + len)
            buffer = buffer.copyOfRange(4 + len, buffer.size)
        }
        return completed
    }

    /** Bytes buffered but not yet forming a complete response. */
    fun pendingBytes(): Int = buffer.size

    fun reset() {
        buffer = ByteArray(0)
    }
}
