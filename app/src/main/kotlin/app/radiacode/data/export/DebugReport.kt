package app.radiacode.data.export

import app.radiacode.analysis.AlgorithmVersions

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
        appendLine("# Отладочный отчёт alpha")
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
        appendLine("переподключений за сеанс: ${snapshot.reconnectCount}")
        appendLine()

        appendLine("## Данные")
        appendLine("измерений: ${snapshot.sampleCount}")
        appendLine("сессий: ${snapshot.sessionCount}")
        appendLine("спектров: ${snapshot.spectrumCount}")
        appendLine("минутных агрегатов: ${snapshot.minuteStatCount}")
        appendLine("часовых скетчей: ${snapshot.hourSketchCount}")
        appendLine()

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

    private fun since(millis: Long?, nowMillis: Long, stamp: (Long) -> String): String {
        if (millis == null) return "нет"
        val heldSeconds = ((nowMillis - millis) / 1000L).coerceAtLeast(0L)
        return "${stamp(millis)} (держится $heldSeconds с)"
    }

    private fun format(value: Float?): String =
        value?.let { String.format(java.util.Locale.US, "%.3f", it).trimEnd('0').trimEnd('.') }
            ?: "—"
}
