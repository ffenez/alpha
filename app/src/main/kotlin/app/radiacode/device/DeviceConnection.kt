package app.radiacode.device

import app.radiacode.protocol.Command
import app.radiacode.protocol.DataBufDecoder
import app.radiacode.protocol.DataBufResult
import app.radiacode.protocol.Spectrum
import app.radiacode.protocol.SpectrumDecoder
import app.radiacode.protocol.Vs
import app.radiacode.protocol.VsCodec
import app.radiacode.protocol.Vsfr
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Firmware below the minimum supported version (target < 4.8). */
class UnsupportedFirmwareException(version: FwVersion) :
    Exception("Unsupported firmware $version, need target >= ${MIN_FW_MAJOR}.${MIN_FW_MINOR}")

/**
 * Минимальная прошивка, на которой ПРОВЕРЕНА последовательность инициализации.
 *
 * Порог стоит не потому, что старая прошивка заведомо не работает, а потому,
 * что на ней ничего не проверялось: набор виртуальных регистров и формат
 * DATA_BUF менялись между версиями. Прибор серии с более старой прошивкой не
 * «не поддерживается» — он не проверен, и приложение обязано сказать это
 * именно так, а не молча отказать.
 */
internal const val MIN_FW_MAJOR = 4
internal const val MIN_FW_MINOR = 8

/**
 * An initialized protocol session on top of a [ProtocolClient].
 *
 * [establish] runs the cdump init sequence: SET_EXCHANGE -> SET_TIME(now) ->
 * WR_VIRT_SFR(DEVICE_TIME, 0), after which DATA_BUF timestamps are relative to
 * `baseTimeMillis = now + 128 s` (cdump's empirical offset).
 */
class DeviceConnection private constructor(
    private val client: ProtocolClient,
    val info: DeviceInfo,
    val baseTimeMillis: Long,
    val configurationText: String,
) {

    /** Drains buffered device records; poll at ~1 Hz for real-time data. */
    suspend fun readDataBuf(): DataBufResult =
        DataBufDecoder.decode(readVs(Vs.DATA_BUF), baseTimeMillis)

    /** Spectrum since the last spectrum reset. */
    suspend fun readSpectrum(): Spectrum =
        SpectrumDecoder.decode(readVs(Vs.SPECTRUM), info.spectrumFormatVersion)

    /** Accumulated (lifetime) spectrum; canonical VS id 0x205. */
    suspend fun readAccumSpectrum(): Spectrum =
        SpectrumDecoder.decode(readVs(Vs.SPEC_ACCUM), info.spectrumFormatVersion)

    /** Resets the current spectrum (WR_VIRT_STRING SPECTRUM with empty payload, as cdump). */
    suspend fun resetSpectrum() {
        VsCodec.parseWriteResponse(
            client.execute(Command.WR_VIRT_STRING, VsCodec.writeStringArgs(Vs.SPECTRUM.toLong())),
        )
    }

    /** Resets the accumulated dose (VSFR DOSE_RESET := 0). */
    suspend fun resetDose() {
        writeSfrU32(Vsfr.DOSE_RESET, 0L)
    }

    private suspend fun readVs(id: Int): ByteArray =
        VsCodec.parseReadPayload(client.execute(Command.RD_VIRT_STRING, VsCodec.readRequestArgs(id)))

    private suspend fun writeSfrU32(id: Long, value: Long) {
        VsCodec.parseWriteResponse(
            client.execute(Command.WR_VIRT_SFR, VsCodec.writeSfrArgs(id, u32le(value))),
        )
    }

    companion object {

        suspend fun establish(
            client: ProtocolClient,
            address: String,
            clock: () -> Long = System::currentTimeMillis,
            zone: ZoneId = ZoneId.systemDefault(),
        ): DeviceConnection {
            client.execute(Command.SET_EXCHANGE, Command.SET_EXCHANGE_PAYLOAD)

            val now = clock()
            val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
            client.execute(
                Command.SET_TIME,
                VsCodec.setTimeArgs(
                    year = local.year,
                    month = local.monthValue,
                    day = local.dayOfMonth,
                    hour = local.hour,
                    minute = local.minute,
                    second = local.second,
                ),
            )

            // cdump: device timestamps become relative to (now + 128 s) once
            // DEVICE_TIME is zeroed. Known open question: long-term drift (#63).
            val baseTimeMillis = clock() + BASE_TIME_OFFSET_MILLIS
            VsCodec.parseWriteResponse(
                client.execute(Command.WR_VIRT_SFR, VsCodec.writeSfrArgs(Vsfr.DEVICE_TIME, u32le(0))),
            )

            val serial = VsCodec.parseReadPayload(
                client.execute(Command.RD_VIRT_STRING, VsCodec.readRequestArgs(Vs.SERIAL_NUMBER)),
            ).toString(Charsets.US_ASCII)

            val version = DeviceParsers.parseVersion(client.execute(Command.GET_VERSION))
            if (!version.targetAtLeast(MIN_FW_MAJOR, MIN_FW_MINOR)) {
                throw UnsupportedFirmwareException(version)
            }

            val config = DeviceParsers.parseConfiguration(
                VsCodec.parseReadPayload(
                    client.execute(Command.RD_VIRT_STRING, VsCodec.readRequestArgs(Vs.CONFIGURATION)),
                ),
            )

            return DeviceConnection(
                client = client,
                info = DeviceInfo(
                    address = address,
                    serialNumber = serial,
                    firmware = version,
                    spectrumFormatVersion = config.specFormatVersion,
                    configurationLines = config.diagnosticLines,
                ),
                baseTimeMillis = baseTimeMillis,
                configurationText = config.text,
            )
        }

        const val BASE_TIME_OFFSET_MILLIS = 128_000L

        private fun u32le(v: Long): ByteArray = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
        )
    }
}
