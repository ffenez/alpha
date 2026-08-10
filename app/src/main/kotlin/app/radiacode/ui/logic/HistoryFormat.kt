package app.radiacode.ui.logic

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pure formatting for История: durations, dates, counts. JVM-tested. */
object HistoryFormat {

    private val MONTHS = listOf(
        "янв", "фев", "мар", "апр", "мая", "июн",
        "июл", "авг", "сен", "окт", "ноя", "дек",
    )

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    /** «45 с» / «12 мин» / «8 ч 12 мин». */
    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return when {
            s < 60 -> "$s с"
            s < 3600 -> "${s / 60} мин"
            else -> {
                val minutes = s % 3600 / 60
                if (minutes == 0L) "${s / 3600} ч" else "${s / 3600} ч $minutes мин"
            }
        }
    }

    /** «8 авг 14:02»; adds the year when it differs from the current one. */
    fun dayTime(millis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val dateTime = Instant.ofEpochMilli(millis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val day = "${dateTime.dayOfMonth} ${MONTHS[dateTime.monthValue - 1]}"
        val year = if (dateTime.year != now.year) " ${dateTime.year}" else ""
        return "$day$year ${dateTime.format(TIME)}"
    }

    /** «9 авг» — day and month only (chart edge labels). */
    fun day(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone)
        return "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
    }

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
