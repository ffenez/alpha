package app.radiacode.ui.logic

import app.radiacode.data.SessionAdmission
import app.radiacode.ui.text.HistoryRu
import app.radiacode.ui.text.HistoryStrings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pure formatting for История: durations, dates, counts. JVM-tested. */
object HistoryFormat {

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    /** «45 с» / «12 мин» / «8 ч 12 мин». */
    fun duration(seconds: Long, s: HistoryStrings = HistoryRu): String {
        val value = seconds.coerceAtLeast(0)
        return when {
            value < 60 -> s.seconds(value)
            value < 3600 -> s.minutes(value / 60)
            else -> {
                val minutes = value % 3600 / 60
                if (minutes == 0L) s.hours(value / 3600) else s.hoursMinutes(value / 3600, minutes)
            }
        }
    }

    /** «8 авг 14:02»; adds the year when it differs from the current one. */
    fun dayTime(
        millis: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        s: HistoryStrings = HistoryRu,
    ): String {
        val dateTime = Instant.ofEpochMilli(millis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val day = "${dateTime.dayOfMonth} ${s.months[dateTime.monthValue - 1]}"
        val year = if (dateTime.year != now.year) " ${dateTime.year}" else ""
        return "$day$year ${dateTime.format(TIME)}"
    }

    /**
     * Заголовок дня в списке: «Сегодня», «Вчера» или «14 августа».
     *
     * Дата уходит из каждой строки в заголовок группы: в списке за месяц она
     * повторялась бы у каждой записи, ничего не различая, — а различает
     * запись время и её форма.
     */
    fun dayHeader(
        millis: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        s: HistoryStrings = HistoryRu,
    ): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (date) {
            today -> s.today
            today.minusDays(1) -> s.yesterday
            else -> {
                val year = if (date.year != today.year) " ${date.year}" else ""
                "${date.dayOfMonth} ${s.monthsGenitive[date.monthValue - 1]}$year"
            }
        }
    }

    /** «18:51» — момент внутри уже названного дня. */
    fun timeOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(TIME)

    /** «9 авг» — day and month only (chart edge labels). */
    fun day(
        millis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        s: HistoryStrings = HistoryRu,
    ): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone)
        return "${date.dayOfMonth} ${s.months[date.monthValue - 1]}"
    }

    /**
     * Baseline participation of a session (spec §20): «в обычный фон: да» or
     * «нет» with the dominating reason. Never says just «нет» — an
     * unexplained exclusion is worse than none.
     */
    fun admissionLine(admission: SessionAdmission, s: HistoryStrings = HistoryRu): String {
        val excluded = admission.exclusions
        return when {
            excluded.isEmpty() && admission.included -> s.admissionYes
            admission.included -> s.admissionPartial(
                excluded = durationWording(admission.excludedSeconds),
                reason = excluded.first().reason.label,
            )
            excluded.isEmpty() -> s.admissionNoData
            else -> s.admissionNo(excluded.first().reason.label)
        }
    }

    /**
     * Dose projection sentence (spec §6). The wording is fixed by the spec and
     * pinned by a test: it must state the *condition*, must not call the
     * result an annual (effective) dose, and must not promise anything about
     * the person carrying the device.
     */
    fun doseProjectionSentence(doseWithUnit: String, s: HistoryStrings = HistoryRu): String =
        s.doseProjection(doseWithUnit)

    /**
     * «по 26 ч измерений · средняя 0,155 мкЗв/ч» — сначала ОБЪЁМ наблюдений.
     *
     * Проекция на год из суток наблюдений — это прежде всего утверждение об
     * объёме данных, и он должен стоять рядом с числом, а не строкой ниже
     * мелким шрифтом.
     */
    fun doseProjectionBasis(
        rateWithUnit: String,
        measuredSeconds: Long,
        s: HistoryStrings = HistoryRu,
    ): String = s.doseProjectionBasis(rateWithUnit, duration(measuredSeconds, s))

    /**
     * Одна строка под проекцией: что это за величина и чем она не является.
     *
     * Отказ остаётся на первом уровне ЦЕЛИКОМ — меняется только длина
     * перечисления: список того, что в проекцию не входит, лежит на втором
     * уровне ([doseProjectionCaveat], справка «i»), а отказ называть число
     * годовой эффективной дозой человека виден сразу, рядом с числом.
     */
    fun doseProjectionCaveatShort(s: HistoryStrings = HistoryRu): String =
        s.doseProjectionCaveatShort

    /**
     * What the projection deliberately does not include (spec §6, §23).
     *
     * Функция, а не `const val`: константа не умеет зависеть от языка, а
     * перечень того, что в проекцию НЕ входит, обязан читаться на языке
     * интерфейса — иначе главное ограничение числа остаётся непрочитанным.
     */
    fun doseProjectionCaveat(s: HistoryStrings = HistoryRu): String = s.doseProjectionCaveat

    /** Shown instead of the projection when the window is too thin (spec §6). */
    fun doseProjectionUnavailable(
        measuredSeconds: Long,
        s: HistoryStrings = HistoryRu,
    ): String = s.doseProjectionUnavailable(duration(measuredSeconds, s))

    /** Thousands grouped with a space: 29520 -> «29 520». */
    fun count(value: Int): String {
        val digits = value.toString()
        val sb = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) sb.append(' ')
            sb.append(char)
        }
        return sb.toString()
    }
}
