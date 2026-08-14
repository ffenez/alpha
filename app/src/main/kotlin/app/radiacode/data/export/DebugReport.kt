package app.radiacode.data.export

import app.radiacode.analysis.AlgorithmVersions
import app.radiacode.service.StreamTrace
import app.radiacode.ui.logic.ChartTrace

/**
 * Цена частоты опроса спектра, ИЗМЕРЕННАЯ (ADR 007), а не выведенная из числа
 * опросов: запросы считает служба, байты — приёмник ответа.
 *
 * Эти три числа существуют затем, чтобы предупреждение про батарею писалось
 * ПОСЛЕ измерения, а не до него: из частоты расход энергии не выводится — он
 * зависит от размера ответа, интервала BLE-соединения и числа пробуждений.
 * Поэтому в отчёте лежат факты (запросов, байт, время работы), а вывод про
 * батарею не делается вовсе.
 */
data class SpectrumTraffic(
    /** Выбранная ступень частоты, [app.radiacode.data.SpectrumPollPolicy.id]. */
    val policy: String,
    val requests: Long,
    /** Байты полезной нагрузки ответов со спектром (без BLE-обвязки). */
    val payloadBytes: Long,
    /** Сколько работает служба; 0 — не работает, тогда «в час» не считается. */
    val serviceUptimeMillis: Long,
    /** Срезов спектрограммы в базе. */
    val storedSlices: Int,
) {
    private val hours: Double get() = serviceUptimeMillis / 3_600_000.0

    /** Запросов в час; null — служба работала слишком мало, чтобы делить. */
    fun requestsPerHour(): Double? = if (hours >= MIN_HOURS) requests / hours else null

    fun bytesPerHour(): Double? = if (hours >= MIN_HOURS) payloadBytes / hours else null

    companion object {
        /** Ниже минуты работы частное — шум деления, а не скорость. */
        const val MIN_HOURS = 1.0 / 60
    }
}

/** Снимок состояния приложения для отладочного отчёта. */
data class DebugSnapshot(
    val appVersion: String,
    val androidSdk: Int,
    val deviceModel: String,
    /** Прибор: серийник и прошивка; null = не подключён. */
    val instrumentSerial: String?,
    val instrumentFirmware: String?,
    /** Модель, как её ОПОЗНАЛО приложение по серийнику. */
    val instrumentModel: String?,
    /** Опознана ли модель вообще: «нет» = работаем на общем профиле. */
    val instrumentModelKnown: Boolean,
    /** Версия формата спектра, объявленная прибором (`SpecFormatVersion`). */
    val spectrumFormatVersion: Int?,
    /** Ключи конфигурации прибора, важные для разбора совместимости. */
    val instrumentConfig: List<String>,
    /** Причина последнего неудавшегося подключения (класс и сообщение). */
    val connectionFailure: String?,
    /** Последний спектр: число каналов, калибровка, накопление. */
    val spectrumChannels: Int?,
    val spectrumCalibration: String?,
    val spectrumSeconds: Long?,
    /** Здоровье потока: пропуски seq в DATA_BUF и число переподключений. */
    val seqGapTotal: Int,
    val reconnectCount: Int,
    /**
     * Покадровая трасса обмена, свежие такты последними.
     *
     * Существует ради одного полевого случая: «нет новых данных · N с» при
     * зелёном кружке связи. На экране «записи не пришли» и «пришли, но не
     * записались» выглядят одинаково, а различаются только здесь.
     */
    val streamTicks: List<StreamTrace.Tick> = emptyList(),
    /**
     * Трасса конвейера графика, свежие проходы последними: на каком этапе
     * исчезают точки — в базе, в снимке, в кадре или уже ниже кадра.
     */
    val chartPasses: List<ChartTrace.Pass> = emptyList(),
    /** Жив ли цикл перечитывания графиков Главной (полевой случай «замерли»). */
    val chartsRefreshedAgoSeconds: Long?,
    val chartsRefreshCount: Int,
    /** Насколько эмпирическая база `+128 с` была стянута измерением, мс. */
    val clockCorrectionMillis: Long,
    val serviceRunning: Boolean,
    val connection: String,
    /** Последнее показание: мощность дозы мкЗв/ч, счёт с⁻¹, возраст в секундах. */
    val doseRateMicroSvH: Float?,
    val doseErrPercent: Float?,
    val countRate: Float?,
    val sampleAgeSeconds: Long?,
    val profileName: String?,
    val contextWording: String?,
    /** Строки статуса ровно в том виде, в каком их видит человек. */
    val statusHeadline: String,
    val statusDetail: String?,
    val baselineWording: String,
    val admissionWording: String,
    /** Параметры тревоги: L1, L2, длительность, множитель к P90. */
    val alarmL1MicroSvH: Float,
    val alarmL2MicroSvH: Float,
    val alarmPersistenceSeconds: Int,
    val alarmRelativeFactor: Float,
    val alarmSensitivity: String,
    /** Состояние детектора отклонения — то, из-за чего «ничего не происходит». */
    val aboveUsualSinceMillis: Long?,
    val alarmConditionSinceMillis: Long?,
    val alertSinceMillis: Long?,
    val nowMillis: Long,
    /** Объёмы хранилища: сколько чего записано. */
    val sampleCount: Long,
    val sessionCount: Long,
    val spectrumCount: Long,
    val minuteStatCount: Int,
    val hourSketchCount: Int,
    /** Настройки, влияющие на поведение экранов. */
    val doseUnit: String,
    val theme: String,
    val searchFeedbackMode: String,
    val searchBackgroundWording: String,
    val fingerprintWording: String,
    /**
     * Опрос спектра: частота, объём и время работы (ADR 007). Значение по
     * умолчанию — «нечего сказать»: раздел просто не печатается.
     */
    val spectrumTraffic: SpectrumTraffic? = null,
    /**
     * Запись следа: что известно приложению о координатах.
     *
     * «След не пишется» на чужом устройстве неразбираемо: непонятно, дошла ли
     * подписка до системы, приходят ли фиксы и доезжают ли они до базы. Здесь
     * нет ни одной координаты — только состояние подписки и счётчики.
     */
    val track: TrackDiagnostics? = null,
)

/** Состояние записи следа — без единой координаты. */
data class TrackDiagnostics(
    val recording: Boolean,
    val state: String,
    val precise: Boolean,
    val providersEnabled: List<String>,
    val providersSubscribed: List<String>,
    val fixes: Int,
    val points: Int,
    val lastFixAgeSeconds: Long?,
    val lastProvider: String?,
    val lastAccuracyMeters: Float?,
)

/**
 * Текстовый отчёт «что сейчас думает приложение» — для разбора полевых
 * наблюдений вида «поставил порог, а на экране ничего».
 *
 * Отчёт **описывает состояние, а не измерения**: в нём нет ни координат, ни
 * рядов отсчётов, ни содержимого спектров — только то, что нужно, чтобы
 * объяснить поведение интерфейса. Это сказано в самом файле, чтобы человек,
 * которому его отправят, знал, что именно он получил.
 *
 * Строки статуса берутся ровно те же, что показаны на экране: отчёт, в котором
 * формулировки пересобраны заново, отвечал бы на другой вопрос.
 */
object DebugReport {

    const val PRIVACY_NOTE =
        "В отчёте нет координат, треков, спектров и рядов измерений — только " +
            "состояние приложения, параметры прибора и настройки. Файл создаётся " +
            "по вашей команде и никуда не отправляется."

    fun fileName(nowMillis: Long, stamp: (Long) -> String): String =
        "radiacode-debug-${stamp(nowMillis)}.txt"

    fun build(snapshot: DebugSnapshot, stamp: (Long) -> String): String = buildString {
        appendLine("# Отладочный отчёт Alpha")
        appendLine(PRIVACY_NOTE)
        appendLine()

        appendLine("## Приложение")
        appendLine("версия: ${snapshot.appVersion}")
        appendLine("Android SDK: ${snapshot.androidSdk} · ${snapshot.deviceModel}")
        appendLine("время отчёта: ${stamp(snapshot.nowMillis)}")
        appendLine()

        appendLine("## Прибор")
        appendLine("подключение: ${snapshot.connection}")
        appendLine("сервис измерения: ${if (snapshot.serviceRunning) "работает" else "остановлен"}")
        appendLine("серийный номер: ${snapshot.instrumentSerial ?: "—"}")
        appendLine("прошивка: ${snapshot.instrumentFirmware ?: "—"}")
        appendLine(
            "модель: ${snapshot.instrumentModel ?: "—"}" +
                if (snapshot.instrumentModel != null && !snapshot.instrumentModelKnown) {
                    " (не опознана — общий профиль)"
                } else {
                    ""
                },
        )
        appendLine("формат спектра: ${snapshot.spectrumFormatVersion?.toString() ?: "—"}")
        for (line in snapshot.instrumentConfig) appendLine("конфигурация: $line")
        snapshot.connectionFailure?.let { appendLine("последняя ошибка связи: $it") }

        snapshot.track?.let { track ->
            appendLine()
            appendLine("## След на карте")
            appendLine("запись: ${if (track.recording) "идёт" else "не идёт"}")
            appendLine("состояние: ${track.state}")
            appendLine("разрешение: ${if (track.precise) "точное" else "приблизительное"}")
            appendLine(
                "источники включены: " +
                    track.providersEnabled.joinToString(" · ").ifEmpty { "нет" },
            )
            appendLine(
                "подписка удалась на: " +
                    track.providersSubscribed.joinToString(" · ").ifEmpty { "ни на один" },
            )
            appendLine("фиксов получено: ${track.fixes} · записано точек: ${track.points}")
            appendLine(
                "последний фикс: " + (
                    track.lastFixAgeSeconds?.let {
                        "$it с назад · ${track.lastProvider ?: "?"} · " +
                            "±${track.lastAccuracyMeters?.toInt() ?: "?"} м"
                    } ?: "не было"
                    ),
            )
        }
        appendLine()

        appendLine("## Спектр")
        appendLine("каналов: ${snapshot.spectrumChannels?.toString() ?: "—"}")
        appendLine("калибровка: ${snapshot.spectrumCalibration ?: "—"}")
        appendLine("накопление: ${snapshot.spectrumSeconds?.let { "$it с" } ?: "—"}")
        appendLine()

        appendLine("## Последнее показание")
        appendLine("мощность дозы: ${format(snapshot.doseRateMicroSvH)} мкЗв/ч")
        appendLine("погрешность прибора: ${format(snapshot.doseErrPercent)} %")
        appendLine("скорость счёта: ${format(snapshot.countRate)} с⁻¹")
        appendLine("возраст показания: ${snapshot.sampleAgeSeconds?.let { "$it с" } ?: "—"}")
        appendLine()

        appendLine("## Что показано на экране")
        appendLine("статус: ${snapshot.statusHeadline}")
        appendLine("подпись: ${snapshot.statusDetail ?: "—"}")
        appendLine("профиль: ${snapshot.profileName ?: "вне профиля"}")
        appendLine("контекст: ${snapshot.contextWording ?: "—"}")
        appendLine("исторический диапазон: ${snapshot.baselineWording}")
        appendLine("допуск измерений: ${snapshot.admissionWording}")
        appendLine()

        appendLine("## Тревога")
        appendLine("чувствительность: ${snapshot.alarmSensitivity}")
        appendLine("L1: ${format(snapshot.alarmL1MicroSvH)} мкЗв/ч")
        appendLine("L2: ${format(snapshot.alarmL2MicroSvH)} мкЗв/ч")
        appendLine("относительный критерий: ×${format(snapshot.alarmRelativeFactor)} к P90 профиля")
        appendLine("минимальная длительность: ${snapshot.alarmPersistenceSeconds} с")
        appendLine("условие тревоги выполняется с: ${since(snapshot.alarmConditionSinceMillis, snapshot.nowMillis, stamp)}")
        appendLine("выше обычного с: ${since(snapshot.aboveUsualSinceMillis, snapshot.nowMillis, stamp)}")
        appendLine("тревога подтверждена с: ${since(snapshot.alertSinceMillis, snapshot.nowMillis, stamp)}")
        appendLine()

        appendLine("## Поток")
        appendLine("пропусков seq в DATA_BUF: ${snapshot.seqGapTotal}")
        appendLine("поправка часов прибора: ${snapshot.clockCorrectionMillis / 1000} с")
        appendLine("переподключений за сеанс: ${snapshot.reconnectCount}")
        val dropped = snapshot.streamTicks.sumOf { it.dropped }
        appendLine("записей отброшено при вставке: $dropped")
        appendLine(
            "графики Главной обновлялись: " + (
                snapshot.chartsRefreshedAgoSeconds?.let { "${it} с назад" } ?: "ни разу"
                ) + " · всего обновлений: ${snapshot.chartsRefreshCount}",
        )
        appendLine()

        if (snapshot.streamTicks.isNotEmpty()) {
            appendLine("## Такты обмена")
            appendLine("время · записей · возраст новейшей · поправка · записано/отброшено")
            for (tick in snapshot.streamTicks) {
                val age = tick.newestAgeMillis?.let { "${it} мс" } ?: "нет записей"
                appendLine(
                    stamp(tick.atMillis) + " · " + tick.records + " · " + age +
                        " · " + tick.correctionMillis / 1000 + " с · " +
                        tick.inserted + "/" + tick.dropped,
                )
            }
            appendLine()
        }

        if (snapshot.chartPasses.isNotEmpty()) {
            appendLine("## Конвейер графика")
            appendLine("время · величина · окно · Room · снимок · кадр · вывод")
            for (pass in snapshot.chartPasses) {
                val verdict = when (ChartTrace.verdict(pass)) {
                    ChartTrace.Verdict.NO_DATA_IN_ROOM -> "нет данных в базе"
                    ChartTrace.Verdict.LOST_IN_SNAPSHOT -> "потеря в запросе/окне"
                    ChartTrace.Verdict.LOST_IN_FRAME -> "потеря в свёртке/отборе"
                    ChartTrace.Verdict.FRAME_COMPLETE -> {
                        val lag = ChartTrace.frameLagMillis(pass)
                        if (lag != null && lag > 0) "кадр полон · отставание ${lag / 1000} с"
                        else "кадр полон"
                    }
                }
                appendLine(
                    stamp(pass.atMillis) + " · " + pass.metric +
                        " · " + (pass.windowEnd - pass.windowStart) / 1000 + " с" +
                        " · " + pass.roomCount + " строк" +
                        (pass.roomMax?.let { " (край −${(pass.nowMillis - it) / 1000} с)" } ?: "") +
                        " · " + pass.snapshotBuckets + " кол." +
                        " · " + pass.frameBuckets + " кол." +
                        " · " + verdict,
                )
            }
            appendLine()
        }

        appendLine("## Данные")
        appendLine("измерений: ${snapshot.sampleCount}")
        appendLine("сессий: ${snapshot.sessionCount}")
        appendLine("спектров: ${snapshot.spectrumCount}")
        appendLine("минутных агрегатов: ${snapshot.minuteStatCount}")
        appendLine("часовых скетчей: ${snapshot.hourSketchCount}")
        appendLine()

        snapshot.spectrumTraffic?.let { traffic ->
            appendLine("## Опрос спектра")
            appendLine("политика частоты: ${traffic.policy}")
            appendLine("время работы службы: ${traffic.serviceUptimeMillis / 1000L} с")
            appendLine("запросов спектра: ${traffic.requests}${perHour(traffic.requestsPerHour())}")
            appendLine(
                "байт ответов: ${traffic.payloadBytes}" +
                    perHour(traffic.bytesPerHour()),
            )
            appendLine("срезов спектрограммы в базе: ${traffic.storedSlices}")
            // Расход батареи из этих чисел НЕ выводится: он зависит от
            // интервала BLE-соединения и числа пробуждений, которых здесь нет.
            appendLine()
        }

        appendLine("## Настройки")
        appendLine("единицы: ${snapshot.doseUnit}")
        appendLine("тема: ${snapshot.theme}")
        appendLine("отклик Поиска: ${snapshot.searchFeedbackMode}")
        appendLine("фон Поиска: ${snapshot.searchBackgroundWording}")
        appendLine("эталон места: ${snapshot.fingerprintWording}")
        appendLine()

        appendLine("## Версии алгоритмов")
        for ((name, version) in AlgorithmVersions.all) appendLine("$name: v$version")
    }

    /** «(≈120 в час)» либо пусто: слишком короткая работа частного не даёт. */
    private fun perHour(value: Double?): String =
        value?.let { " (≈${String.format(java.util.Locale.US, "%.0f", it)} в час)" } ?: ""

    private fun since(millis: Long?, nowMillis: Long, stamp: (Long) -> String): String {
        if (millis == null) return "нет"
        val heldSeconds = ((nowMillis - millis) / 1000L).coerceAtLeast(0L)
        return "${stamp(millis)} (держится $heldSeconds с)"
    }

    private fun format(value: Float?): String =
        value?.let { String.format(java.util.Locale.US, "%.3f", it).trimEnd('0').trimEnd('.') }
            ?: "—"
}
