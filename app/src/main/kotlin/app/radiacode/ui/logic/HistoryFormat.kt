package app.radiacode.ui.logic

import app.radiacode.data.SessionAdmission
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

    /**
     * Baseline participation of a session (spec §20): «в обычный фон: да» or
     * «нет» with the dominating reason. Never says just «нет» — an
     * unexplained exclusion is worse than none.
     */
    fun admissionLine(admission: SessionAdmission): String {
        val excluded = admission.exclusions
        return when {
            excluded.isEmpty() && admission.included -> "в обычный фон: да"
            admission.included -> {
                val top = excluded.first()
                "в обычный фон: частично · вне обучения " +
                    "${durationWording(admission.excludedSeconds)} — ${top.reason.label}"
            }
            excluded.isEmpty() -> "в обычный фон: нет измерений"
            else -> "в обычный фон: нет — ${excluded.first().reason.label}"
        }
    }

    /**
     * Dose projection sentence (spec §6). The wording is fixed by the spec and
     * pinned by a test: it must state the *condition*, must not call the
     * result an annual (effective) dose, and must not promise anything about
     * the person carrying the device.
     */
    fun doseProjectionSentence(doseWithUnit: String): String =
        "если средняя измеренная внешняя фотонная мощность дозы останется такой же — " +
            "за год ≈ $doseWithUnit"

    /**
     * «по 26 ч измерений · средняя 0,155 мкЗв/ч» — сначала ОБЪЁМ наблюдений.
     *
     * Проекция на год из суток наблюдений — это прежде всего утверждение об
     * объёме данных, и он должен стоять рядом с числом, а не строкой ниже
     * мелким шрифтом.
     */
    fun doseProjectionBasis(rateWithUnit: String, measuredSeconds: Long): String =
        "по ${duration(measuredSeconds)} измерений · средняя $rateWithUnit"

    /** What the projection deliberately does not include (spec §6, §23). */
    const val DOSE_PROJECTION_CAVEAT =
        "Это не годовая эффективная доза человека: в неё не входят внутреннее " +
            "облучение, радон, медицинские процедуры и всё время, когда прибор " +
            "не измерял или не был рядом."

    /** Shown instead of the projection when the window is too thin (spec §6). */
    fun doseProjectionUnavailable(measuredSeconds: Long): String =
        "измерений пока мало (${duration(measuredSeconds)}) — за год пересчитывать не из чего"

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
