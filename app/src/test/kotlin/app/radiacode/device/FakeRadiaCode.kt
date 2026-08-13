package app.radiacode.device

import app.radiacode.protocol.Command
import app.radiacode.protocol.ResponseAssembler
import app.radiacode.protocol.Vs
import java.nio.charset.Charset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow

/** Little-endian wire helpers for building fake device responses. */
object Wire {
    fun u16(v: Int): ByteArray = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    fun u32(v: Long): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    fun i32(v: Int): ByteArray = u32(v.toLong() and 0xFFFFFFFFL)

    fun f32(v: Float): ByteArray = i32(v.toRawBits())

    fun str(s: String): ByteArray = byteArrayOf(s.length.toByte()) + s.toByteArray(Charsets.US_ASCII)

    /** `<u32 LE len><body>` frame as sent over notifications. */
    fun frame(body: ByteArray): ByteArray = u32(body.size.toLong()) + body
}

/**
 * Protocol-level fake of a RadiaCode device: answers framed requests with
 * framed responses. No BLE, no timing — pure wire format.
 */
class FakeRadiaCode(
    var serial: String = "RC-110-001234",
    var bootVersion: Pair<Int, Int> = 4 to 9,
    var targetVersion: Pair<Int, Int> = 4 to 8,
    var configText: String = "DeviceName=RadiaCode-110\r\nSpecFormatVersion=1\r\n",
) {
    /** Payloads returned by consecutive DATA_BUF reads (then empty). */
    val dataBufPayloads = ArrayDeque<ByteArray>()

    /** Payload returned for SPECTRUM / SPEC_ACCUM reads. */
    var spectrumPayload: ByteArray = ByteArray(0)

    /** Every request seen: command code to args. */
    val requests = mutableListOf<Pair<Int, ByteArray>>()

    /** Handles one complete request body, returns the framed response. */
    fun respond(requestBody: ByteArray): ByteArray {
        val header = requestBody.copyOfRange(0, 4)
        val command = (requestBody[0].toInt() and 0xFF) or ((requestBody[1].toInt() and 0xFF) shl 8)
        val args = requestBody.copyOfRange(4, requestBody.size)
        requests += command to args

        val body = when (command) {
            Command.SET_EXCHANGE, Command.SET_TIME -> ByteArray(0)
            Command.WR_VIRT_SFR, Command.WR_VIRT_STRING -> Wire.u32(1)
            Command.GET_VERSION ->
                Wire.u16(bootVersion.second) + Wire.u16(bootVersion.first) + Wire.str("Feb 01 2024") +
                    Wire.u16(targetVersion.second) + Wire.u16(targetVersion.first) + Wire.str("Mar 02 2025")
            Command.RD_VIRT_STRING -> vsReadBody(vsId(args))
            else -> error("FakeRadiaCode: unexpected command 0x${"%04x".format(command)}")
        }
        return Wire.frame(header + body)
    }

    private fun vsReadBody(id: Int): ByteArray {
        val payload = when (id) {
            Vs.SERIAL_NUMBER -> serial.toByteArray(Charsets.US_ASCII)
            Vs.CONFIGURATION -> configText.toByteArray(Charset.forName("windows-1251"))
            Vs.DATA_BUF -> dataBufPayloads.removeFirstOrNull() ?: ByteArray(0)
            Vs.SPECTRUM, Vs.SPEC_ACCUM -> spectrumPayload
            else -> error("FakeRadiaCode: unexpected VS read 0x${"%x".format(id)}")
        }
        return Wire.u32(1) + Wire.u32(payload.size.toLong()) + payload
    }

    private fun vsId(args: ByteArray): Int =
        (args[0].toInt() and 0xFF) or ((args[1].toInt() and 0xFF) shl 8) or
            ((args[2].toInt() and 0xFF) shl 16) or ((args[3].toInt() and 0xFF) shl 24)
}

/**
 * [DeviceLink] backed by a [FakeRadiaCode]: reassembles written chunks into
 * requests and emits framed responses on [notifications].
 */
class FakeDeviceLink(private val fake: FakeRadiaCode) : DeviceLink {

    private val requestAssembler = ResponseAssembler()
    private val disconnectSignal = CompletableDeferred<Unit>()

    /** Chunk sizes observed, to assert the 18-byte write recipe. */
    val chunkSizes = mutableListOf<Int>()

    var closed = false
        private set

    override val notifications = MutableSharedFlow<ByteArray>(replay = 64, extraBufferCapacity = 64)

    /**
     * Сколько следующих ответов проглотить, не отправляя.
     * Так выглядит занятый прибор: человек нажимает кнопки на его экране, и
     * ответ на наш запрос не приходит вовсе.
     */
    var swallowNextResponses = 0

    override suspend fun write(chunk: ByteArray) {
        check(chunk.size <= ProtocolClient.CHUNK_SIZE) { "chunk of ${chunk.size} bytes" }
        chunkSizes += chunk.size
        for (request in requestAssembler.feed(chunk)) {
            // Проглоченный запрос не РАЗБИРАЕТСЯ вовсе: занятый прибор не
            // тратит на него свой буфер, и очередь ответов остаётся ждать.
            if (swallowNextResponses > 0) {
                swallowNextResponses -= 1
                continue
            }
            notifications.emit(fake.respond(request))
        }
    }

    override suspend fun awaitDisconnect() {
        disconnectSignal.await()
    }

    override suspend fun close() {
        closed = true
    }

    /** Simulates a BLE link drop. */
    fun dropLink() {
        disconnectSignal.complete(Unit)
    }
}

/** Builds a DATA_BUF payload with a single RealTimeData record (eid=0, gid=0). */
fun realTimeDataRecord(
    seq: Int,
    tsOffset10ms: Int,
    countRate: Float,
    doseRate: Float,
    countRateErr10: Int = 15,
    doseRateErr10: Int = 20,
    flags: Int = 0,
    realTimeFlags: Int = 0,
): ByteArray =
    byteArrayOf(seq.toByte(), 0, 0) + Wire.i32(tsOffset10ms) +
        Wire.f32(countRate) + Wire.f32(doseRate) +
        Wire.u16(countRateErr10) + Wire.u16(doseRateErr10) +
        Wire.u16(flags) + byteArrayOf(realTimeFlags.toByte())

/**
 * Запись `GRP_RawData` (eid=0, gid=1) — «чужая» группа того же ответа.
 *
 * Существует ради одного полевого факта: прибор стамповает группы ПО-РАЗНОМУ,
 * и в одном ответе чужая запись может быть свежее ряда измерений на десятки
 * секунд. Якорь часов обязан этого не замечать.
 */
fun rawDataRecord(seq: Int, tsOffset10ms: Int, countRate: Float, doseRate: Float): ByteArray =
    byteArrayOf(seq.toByte(), 0, 1) + Wire.i32(tsOffset10ms) +
        Wire.f32(countRate) + Wire.f32(doseRate)
