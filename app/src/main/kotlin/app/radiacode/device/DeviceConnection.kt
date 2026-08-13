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
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Измеренная поправка к эмпирической базе времени, мс (≤ 0).
     *
     * База `подключение + 128 с` — константа из cdump, и на живом приборе она
     * врёт: полевой отчёт показывал «возраст показания: −96 с», то есть метки
     * записей уезжали в БУДУЩЕЕ на полторы минуты. Следствия видимые: правый
     * край графика «не дотягивается» до свежих отсчётов, а на стыках
     * переподключений сырые метки наслаиваются и рвутся.
     *
     * Вместо константы — измерение по обе стороны.
     *
     * **Вниз — сразу**: запись не может прийти раньше, чем была сделана,
     * поэтому опережение новейшей записи над часами телефона это заведомо
     * завышенная база.
     *
     * **Вверх — только по минимуму окна**, и вот почему. Первый ответ после
     * подключения может содержать запись из БУФЕРА прибора с большим
     * смещением; односторонний храповик вычитал по ней слишком много, и дальше
     * все метки оседали на десятки секунд в ПРОШЛОМ — навсегда, потому что
     * подниматься он не умел. Полевая картина этого дефекта: зелёный кружок
     * связи, «нет новых данных · 31 с» и графики, идущие с постоянным
     * отставанием. Одна старая запись — честная задержка буфера, её чинить
     * нельзя; но если САМАЯ СВЕЖАЯ запись каждого ответа подряд стара на
     * десятки секунд, значит занижена база. Поэтому вверх поправка идёт по
     * минимуму возраста за окно ответов — классический min-фильтр
     * синхронизации часов, устойчивый к разовым выбросам.
     */
    @Volatile
    var clockCorrectionMillis: Long = 0L
        private set

    /** Возрасты новейших записей последних ответов — окно min-фильтра. */
    private val recentAges = ArrayDeque<Long>()

    /** Drains buffered device records; poll at ~1 Hz for real-time data. */
    suspend fun readDataBuf(): DataBufResult {
        val payload = readVs(Vs.DATA_BUF)
        var result = DataBufDecoder.decode(payload, baseTimeMillis + clockCorrectionMillis)
        val newest = result.records.maxOfOrNull { it.timestampMillis }
        if (newest != null) {
            val shift = correctionShift(newest - clock())
            if (shift != 0L) {
                clockCorrectionMillis += shift
                // Тот же ответ перечитывается с исправленной базой: записи
                // одного ответа не должны нести метки из двух эпох.
                result = DataBufDecoder.decode(payload, baseTimeMillis + clockCorrectionMillis)
            }
        }
        return result
    }

    /**
     * Сколько прибавить к поправке по опережению новейшей записи ответа.
     *
     * Возвращает 0, когда трогать базу не нужно. Окно возрастов чистится при
     * каждом сдвиге: после переезда базы прежние измерения относятся к другой
     * эпохе и в фильтре им не место.
     */
    private fun correctionShift(aheadMillis: Long): Long {
        if (aheadMillis > FUTURE_TOLERANCE_MILLIS) {
            recentAges.clear()
            return -aheadMillis
        }
        val age = -aheadMillis
        recentAges.addLast(age)
        while (recentAges.size > LAG_WINDOW_RESPONSES) recentAges.removeFirst()
        if (recentAges.size < LAG_WINDOW_RESPONSES) return 0L
        val minAge = recentAges.min()
        if (minAge <= LAG_TOLERANCE_MILLIS) return 0L
        recentAges.clear()
        return minAge
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

        /**
         * Насколько метка записи может честно опережать часы телефона:
         * задержка BLE-переноса и шаг опроса. **Инженерный параметр**: всё,
         * что дальше, — завышенная база, а не физика.
         */
        const val FUTURE_TOLERANCE_MILLIS = 2_000L

        /**
         * Сколько ответов подряд должны отставать, прежде чем база поднимется.
         * **Инженерный параметр**: при опросе раз в секунду это ~10 с — дольше
         * любой разовой задержки переноса и короче, чем человек успевает
         * заметить отставание графика.
         */
        const val LAG_WINDOW_RESPONSES = 10

        /**
         * Отставание, ниже которого база считается верной.
         * **Инженерный параметр**: 5 с — заведомо больше секундного шага
         * записи и задержки BLE, заведомо меньше «графики идут с опозданием».
         */
        const val LAG_TOLERANCE_MILLIS = 5_000L

        private fun u32le(v: Long): ByteArray = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
        )
    }
}
