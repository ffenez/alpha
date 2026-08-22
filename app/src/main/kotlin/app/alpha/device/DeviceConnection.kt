package app.alpha.device

import app.alpha.protocol.Command
import app.alpha.protocol.DataBufDecoder
import app.alpha.protocol.DataBufResult
import app.alpha.protocol.RealTimeData
import app.alpha.protocol.Spectrum
import app.alpha.protocol.SpectrumDecoder
import app.alpha.protocol.Vs
import app.alpha.protocol.VsCodec
import app.alpha.protocol.Vsfr
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
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Поправка к эмпирической базе времени прибора, мс.
     *
     * ## Правило: новейшая запись ответа приходится на момент его прихода
     *
     * База `подключение + 128 с` — константа из cdump; на живом приборе она
     * уводит метки в будущее на десятки секунд.
     *
     * Прибор пишет раз в секунду и отдаёт написанное по первому запросу,
     * поэтому самая свежая запись ответа сделана практически в момент его
     * получения. Поправка считается одним вычитанием и заново на каждом ответе
     * с новой записью. Более ранние записи того же ответа сохраняют свои
     * интервалы: сдвигается весь ответ целиком.
     *
     * ## Почему не фильтр по окну
     *
     * У min-фильтра с окном есть время сходимости, и всё это время метки
     * лежат в прошлом, а свежие строки натыкаются на уникальный индекс
     * `samples.timestamp` и отбрасываются. Прямой якорь сходится за один ответ.
     *
     * ## Предохранитель
     *
     * Пере-якорение делается только когда новейшая запись ответа отличается от
     * прежней: остановившийся прибор повторяет последнюю запись, и двигать по
     * ней базу значило бы штамповать старое показание текущим временем.
     * Признак — «смещение изменилось», а не «выросло»: буферная запись может
     * лежать впереди живых.
     */
    @Volatile
    var clockCorrectionMillis: Long = 0L
        private set

    /** Сырое смещение новейшей записи прошлого ответа — признак «прибор пишет». */
    private var lastNewestRawMillis: Long? = null

    /** Drains buffered device records; poll at ~1 Hz for real-time data. */
    suspend fun readDataBuf(): DataBufResult {
        val payload = readVs(Vs.DATA_BUF)
        var result = DataBufDecoder.decode(payload, baseTimeMillis + clockCorrectionMillis)
        // Диагностика пишет смещения ДО пере-якорения: смысл журнала в том,
        // что прислал прибор, а не в том, что из этого сделала поправка.
        RawOffsetLog.reply(
            nowMillis = clock(),
            records = result.records,
            correctionMillis = clockCorrectionMillis,
            baseTimeMillis = baseTimeMillis,
        )
        // Якорь ставится ТОЛЬКО по RealTimeData: в одном ответе приходят
        // записи разных групп (RealTimeData, RawData, DoseRateDB, RareData),
        // и прибор стамповает их по-разному — максимум по всем записям
        // подтягивал базу к чужой группе, и ряд `samples` систематически
        // уезжал в прошлое.
        val newest = result.records
            .filterIsInstance<RealTimeData>()
            .maxOfOrNull { it.timestampMillis }
        if (newest != null) {
            val rawNewest = newest - clockCorrectionMillis
            if (lastNewestRawMillis != rawNewest) {
                lastNewestRawMillis = rawNewest
                val corrected = clock() - rawNewest
                if (corrected != clockCorrectionMillis) {
                    clockCorrectionMillis = corrected
                    // Ответ перечитывается с исправленной базой: записи одного
                    // ответа не должны нести метки из двух эпох.
                    result = DataBufDecoder.decode(
                        payload,
                        baseTimeMillis + clockCorrectionMillis,
                    )
                }
            }
        }
        return result
    }

    /**
     * Объём полезной нагрузки ответов со спектром за эту сессию, байт.
     * Считается ЗДЕСЬ, потому что здесь ещё виден сам ответ: это реальный
     * объём, снятый с прибора, а не оценка по числу каналов (формат v1 —
     * RLE, и распакованный размер про эфир ничего не говорит).
     */
    @Volatile
    var spectrumPayloadBytes: Long = 0L
        private set

    /** Spectrum since the last spectrum reset. */
    suspend fun readSpectrum(): Spectrum {
        val payload = readVs(Vs.SPECTRUM)
        spectrumPayloadBytes += payload.size
        return SpectrumDecoder.decode(payload, info.spectrumFormatVersion)
    }

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

    /**
     * Звук самого прибора (VSFR SOUND_ON) — он пищит и без телефона.
     *
     * Запись u32 0/1, как в референсной реализации `cdump/radiacode`
     * (`set_sound_on`). Тем же путём, которым при подключении уже пишется
     * DEVICE_TIME, — то есть путь проверен на приборе.
     */
    suspend fun setDeviceSoundOn(on: Boolean) {
        writeSfrU32(Vsfr.SOUND_ON, if (on) 1L else 0L)
    }

    /** Вибрация самого прибора (VSFR VIBRO_ON), `cdump: set_vibro_on`. */
    suspend fun setDeviceVibroOn(on: Boolean) {
        writeSfrU32(Vsfr.VIBRO_ON, if (on) 1L else 0L)
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

            RawOffsetLog.session(
                nowMillis = clock(),
                baseTimeMillis = baseTimeMillis,
                serial = serial,
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
                clock = clock,
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
