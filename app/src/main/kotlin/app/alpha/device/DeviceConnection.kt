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
     * ## Что здесь происходит
     *
     * База `подключение + 128 с` — эмпирическая константа cdump: у прибора
     * смещения записей отрицательные (живая запись ≈ −128 с), и эта база
     * сводит их к настоящему времени. Константа снята на одной модели и на
     * других уходит на минуты, поэтому её уточняет поправка: новейшая ЖИВАЯ
     * запись должна приходиться на момент прихода ответа.
     *
     * ## Почему поправка не берётся с каждого ответа
     *
     * Прибор хранит до тысячи часов автономных наблюдений и после подключения
     * отдаёт их тем же `DATA_BUF` — порциями, много ответов подряд. У такой
     * порции новейшая запись САМА историческая, и якорь по ней означал бы
     * «всё, что накоплено за два часа, случилось сейчас»: история
     * расплющивалась в момент подключения, а строки бились об уникальный
     * индекс `samples.timestamp`.
     *
     * Поэтому поправка меняется, только когда ответ УЖЕ живой: новейшая
     * запись моложе [SYNC_WINDOW_MILLIS]. Порог взят из рабочего инструмента
     * сообщества (rcrtlog: «догнали живое», когда `now − метка < 5 с`), а не
     * назначен. Пока идёт слив, база остаётся той, что рассчитана при
     * подключении, — ровно как в эталонной библиотеке.
     *
     * ## Предохранитель
     *
     * Пере-якорение делается только когда новейшая запись ответа отличается от
     * прежней: остановившийся прибор повторяет последнюю запись, и двигать по
     * ней базу значило бы штамповать старое показание текущим временем.
     */
    @Volatile
    var clockCorrectionMillis: Long = 0L
        private set

    /** Сырое смещение новейшей записи прошлого ответа — признак «прибор пишет». */
    private var lastNewestRawMillis: Long? = null

    /** Была ли база уже поставлена по измерению в этом сеансе. */
    private var anchored: Boolean = false

    /** Когда поток последний раз был живым — по нему видно, что он застрял. */
    private var lastSyncMillis: Long = clock()

    /**
     * Сколько записей отброшено как мусор за сеанс: метка вне всякого
     * правдоподобия. Число уходит в отладочный отчёт — «мусора не было» и
     * «мусор молча выброшен» должны различаться.
     */
    @Volatile
    var garbageRecords: Int = 0
        private set

    /**
     * Может ли запись с такой меткой существовать.
     *
     * Снизу — глубина собственной памяти прибора с запасом: он хранит около
     * тысячи часов, и запись старше этого не бывает. Сверху — минуты: до
     * первого якоря живые записи лежат впереди на эмпирические 128 с, а
     * дальше в будущее не уходит ничто.
     */
    private fun plausible(timestampMillis: Long): Boolean {
        val age = clock() - timestampMillis
        return age >= -FUTURE_TOLERANCE_MILLIS && age <= MEMORY_DEPTH_MILLIS
    }

    /**
     * Принять ли новую базу.
     *
     * Первый якорь сеанса двигает базу на минуты — ровно на ту эмпирическую
     * константу, которую он и уточняет. Дальше база живая и может ползти
     * только на секунды: это дрейф часов, а не прыжок. Прыжок означает
     * испорченную метку, и его место — в мусоре, а не во времени сеанса.
     *
     * Отдельный случай — застрявший поток: если живых записей нет дольше
     * [STUCK_MILLIS], база уже неверна, и её надо ставить заново по любой
     * правдоподобной записи. Без этого одна испорченная метка уводила время
     * до конца сеанса (полевой отчёт: «история 979 ч 59 мин», связь на
     * экране потеряна, помогал только перезапуск).
     */
    private fun accepts(corrected: Long): Boolean {
        val shift = kotlin.math.abs(corrected - clockCorrectionMillis)
        if (!anchored) return shift <= FIRST_ANCHOR_MILLIS
        if (clock() - lastSyncMillis >= STUCK_MILLIS) return true
        return synchronised && shift <= DRIFT_MILLIS
    }

    /**
     * Возраст новейшей записи последнего ответа, мс: сколько времени назад её
     * сделал прибор. Ноль — поток живой, часы — идёт слив накопленного.
     */
    @Volatile
    var newestAgeMillis: Long = 0L
        private set

    /** Записей в последнем ответе прибора, до отбраковки невозможных меток. */
    @Volatile
    var lastReplyRecords: Int = 0
        private set

    /** Возраст САМОЙ СТАРОЙ записи последнего ответа, мс; null — ответ пуст. */
    @Volatile
    var lastReplyOldestAgeMillis: Long? = null
        private set

    /**
     * Догнал ли слив живое время.
     *
     * Запись из БУДУЩЕГО (возраст отрицательный) считается живой: пока якорь
     * не поставлен, база завышена на эмпирические 128 с, и все живые записи
     * лежат впереди. Запись из ПРОШЛОГО глубже окна означает слив
     * накопленного.
     */
    val synchronised: Boolean get() = newestAgeMillis <= SYNC_WINDOW_MILLIS

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
        // Ответ, как его прислал прибор, — ДО отбраковки: по этим двум числам
        // видно, отдаёт ли прибор накопленное за разрыв связи, и виден ответ
        // целиком, включая записи, которые сейчас будут выброшены.
        lastReplyRecords = result.records.size
        lastReplyOldestAgeMillis = result.records.minOfOrNull { it.timestampMillis }
            ?.let { clock() - it }
        // Мусорные метки выбрасываются ДО всего остального: прибор изредка
        // присылает запись со смещением в сотни суток (полевой журнал: одна
        // запись на +980 ч и одна на −3550 ч за сеанс). Такая метка не
        // измерение: попав в базу, она рисует точку в месяце отсюда, а попав
        // в якорь — уводит время всего сеанса.
        val dropped = result.records.count { !plausible(it.timestampMillis) }
        if (dropped > 0) {
            garbageRecords += dropped
            result = DataBufResult(
                records = result.records.filter { plausible(it.timestampMillis) },
                seqGaps = result.seqGaps,
            )
        }

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
            newestAgeMillis = clock() - newest
            val corrected = clock() - rawNewest
            if (lastNewestRawMillis != rawNewest && accepts(corrected)) {
                lastNewestRawMillis = rawNewest
                if (corrected != clockCorrectionMillis) {
                    clockCorrectionMillis = corrected
                    anchored = true
                    lastSyncMillis = clock()
                    // Ответ перечитывается с исправленной базой: записи одного
                    // ответа не должны нести метки из двух эпох.
                    result = DataBufDecoder.decode(
                        payload,
                        baseTimeMillis + clockCorrectionMillis,
                    )
                }
            }
            if (synchronised) lastSyncMillis = clock()
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

        /**
         * До какого возраста новейшей записи поток считается живым, мс —
         * **инженерный параметр**. Пять секунд: прибор пишет раз в секунду и
         * отдаёт написанное по первому запросу, поэтому в живом потоке
         * новейшая запись моложе секунды с запасом на осечку чтения. Тот же
         * порог использует rcrtlog как признак «слив догнал живое».
         */
        const val SYNC_WINDOW_MILLIS = 5_000L

        /**
         * Насколько далеко вперёд может лежать метка живой записи —
         * **инженерный параметр**. Пять минут: до первого якоря все живые
         * записи стоят впереди на эмпирические 128 с, и запас втрое покрывает
         * разброс этой константы между моделями (на Zero сообщество наблюдало
         * до двух минут).
         */
        const val FUTURE_TOLERANCE_MILLIS = 5L * 60_000L

        /**
         * Насколько старой может быть запись прибора — **инженерный
         * параметр**. Тысяча сто часов: вендор заявляет около тысячи часов
         * автономной записи, остальное — запас.
         */
        const val MEMORY_DEPTH_MILLIS = 1_100L * 3_600_000L

        /**
         * Насколько первый якорь сеанса вправе подвинуть базу — **инженерный
         * параметр**. Пять минут: он уточняет эмпирическую константу 128 с, и
         * больше её собственного разброса ему двигать нечего.
         */
        const val FIRST_ANCHOR_MILLIS = 5L * 60_000L

        /**
         * Насколько база вправе ползти после первого якоря — **инженерный
         * параметр**. Десять секунд: это дрейф часов прибора за сеанс, а
         * скачок больше означает испорченную метку.
         */
        const val DRIFT_MILLIS = 10_000L

        /**
         * Сколько поток может не быть живым, прежде чем базу ставят заново, —
         * **инженерный параметр**. Минута: слив накопленного идёт порциями и
         * догоняет живое за секунды, а минута молчания означает, что база уже
         * неверна.
         */
        const val STUCK_MILLIS = 60_000L

        private fun u32le(v: Long): ByteArray = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
        )
    }
}
