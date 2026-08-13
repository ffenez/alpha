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
     * Измеренная поправка к эмпирической базе времени, мс.
     *
     * База `подключение + 128 с` — константа из cdump, и на живом приборе она
     * врёт: полевой отчёт показывал «возраст показания: −96 с», то есть метки
     * записей уезжали в БУДУЩЕЕ на полторы минуты.
     *
     * Оценка — классический **min-фильтр** синхронизации часов: запись не может
     * прийти раньше, чем сделана, поэтому задержка переноса всегда ≥ 0, и
     * МИНИМАЛЬНЫЙ возраст новейших записей за окно — лучшая оценка
     * систематического смещения базы. Одно правило работает в обе стороны:
     * отрицательный минимум (запись из будущего) опускает базу, положительный
     * сверх допуска — поднимает.
     *
     * **Два предохранителя, каждый из своего полевого дефекта.**
     *
     * (1) В фильтр попадают только ответы с НОВОЙ записью. Если прибор
     * перестал писать, его последняя запись повторяется из ответа в ответ и
     * стареет с каждой секундой — но это простой потока, а не уход часов, и
     * поднимать по нему базу означало бы штамповать старые показания
     * сегодняшним временем. Признак — именно «смещение изменилось», а не
     * «выросло»: буферная запись может лежать далеко впереди живых, и
     * требование роста заперло бы базу в её эпохе навсегда.
     *
     * (2) Вверх база идёт минимум по ДВУМ измерениям, вниз — сразу. Первый
     * ответ после подключения приходит из БУФЕРА прибора, и его новейшая запись
     * может быть какой угодно; одиночного измерения хватило бы, чтобы увести
     * базу на час. Запись из будущего однозначна при любом прочтении, поэтому
     * вниз ждать нечего.
     *
     * Прежний односторонний храповик (только вниз) дал свой дефект: буферная
     * запись при подключении опускала базу на десятки секунд, и подняться она
     * уже не могла — зелёный кружок связи, «нет новых данных · 31 с» и графики
     * с постоянным отставанием.
     */
    @Volatile
    var clockCorrectionMillis: Long = 0L
        private set

    /** Сырое смещение новейшей записи прошлого ответа — признак «прибор пишет». */
    private var lastNewestRawMillis: Long? = null

    /** Окно min-фильтра: пары «момент ответа — возраст его новейшей записи». */
    private val recentAges = ArrayDeque<LongArray>()

    /** Drains buffered device records; poll at ~1 Hz for real-time data. */
    suspend fun readDataBuf(): DataBufResult {
        val payload = readVs(Vs.DATA_BUF)
        var result = DataBufDecoder.decode(payload, baseTimeMillis + clockCorrectionMillis)
        val newest = result.records.maxOfOrNull { it.timestampMillis }
        if (newest != null) {
            val shift = correctionShift(newest - clockCorrectionMillis, clock())
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
     * Сколько прибавить к поправке; 0 — база в допуске и трогать её не нужно.
     *
     * @param rawNewest смещение новейшей записи ответа БЕЗ текущей поправки —
     *   только оно сравнимо между ответами, поправка между ними меняется.
     */
    private fun correctionShift(rawNewest: Long, nowMillis: Long): Long {
        val isNewRecord = lastNewestRawMillis?.let { rawNewest != it } ?: true
        lastNewestRawMillis = rawNewest
        if (!isNewRecord) return 0L
        val age = nowMillis - (rawNewest + clockCorrectionMillis)
        recentAges.addLast(longArrayOf(nowMillis, age))
        while (recentAges.isNotEmpty() &&
            nowMillis - recentAges.first()[0] > FILTER_WINDOW_MILLIS
        ) {
            recentAges.removeFirst()
        }
        val minAge = recentAges.minOf { it[1] }
        if (minAge < -FUTURE_TOLERANCE_MILLIS) {
            recentAges.clear()
            return minAge
        }
        if (recentAges.size < MIN_SAMPLES_FOR_LIFT || minAge <= LAG_TOLERANCE_MILLIS) return 0L
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
         * Окно min-фильтра.
         * **Инженерный параметр**: при опросе раз в секунду это ~10 измерений —
         * достаточно, чтобы минимум пришёлся на ответ без задержки переноса, и
         * коротко настолько, что реальный уход часов не успевает накопиться.
         */
        const val FILTER_WINDOW_MILLIS = 10_000L

        /**
         * Сколько измерений нужно, чтобы ПОДНЯТЬ базу.
         * **Инженерный параметр**: два — минимум, при котором одиночная
         * буферная запись перестаёт решать. Вниз предохранитель не нужен.
         */
        const val MIN_SAMPLES_FOR_LIFT = 2

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
