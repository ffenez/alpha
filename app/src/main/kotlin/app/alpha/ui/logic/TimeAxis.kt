package app.alpha.ui.logic

import app.alpha.ui.text.ChartAxisRu
import app.alpha.ui.text.ChartAxisStrings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Round hh:mm labels for a chart time axis (design: axes are always
 * labeled). Ticks fall on «nice» wall-clock steps (5/10/15/20/30/60… min),
 * so a one-hour window reads 13:15 · 13:30 · 13:45. Pure JVM, tested.
 */
object TimeAxis {

    private val STEPS_MINUTES = listOf(1L, 2, 5, 10, 15, 20, 30, 60, 120, 180, 360, 720, 1440)
    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

    /** Fraction (0..1 across the window) → label, about [count] ticks. */
    fun labels(
        fromMillis: Long,
        toMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        count: Int = 4,
    ): List<Pair<Float, String>> {
        val window = toMillis - fromMillis
        if (window <= 0 || count <= 0) return emptyList()
        val rawStepMinutes = window / 60_000.0 / count
        val stepMinutes = STEPS_MINUTES.firstOrNull { it >= rawStepMinutes }
            ?: STEPS_MINUTES.last()
        val stepMillis = stepMinutes * 60_000L

        // Ticks on the wall-clock grid of the local zone (offset-aware).
        val offsetMillis = zone.rules.getOffset(Instant.ofEpochMilli(fromMillis))
            .totalSeconds * 1000L
        // ceilDiv (Java 17 has only floorDiv): -floorDiv(-a, b).
        var tick = -Math.floorDiv(-(fromMillis + offsetMillis), stepMillis) * stepMillis -
            offsetMillis
        val result = mutableListOf<Pair<Float, String>>()
        // Первая метка нового дня несёт ЧИСЛО.
        //
        // На окне короче двух суток подписи были только часами, и переход
        // через полночь ничем не отмечался: «23:00 · 01:00 · 03:00» читается
        // как один вечер, хотя между второй и третьей меткой сменилась дата.
        // Числа у КАЖДОЙ метки при этом не нужны — повторённое четыре раза
        // «3 авг» не различает ничего.
        var previousDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        while (tick <= toMillis) {
            val fraction = (tick - fromMillis).toFloat() / window
            val moment = Instant.ofEpochMilli(tick).atZone(zone)
            val clock = moment.format(HH_MM)
            val date = moment.toLocalDate()
            result += fraction to if (date != previousDate) {
                HistoryFormat.day(tick, zone) + " " + clock
            } else {
                clock
            }
            previousDate = date
            tick += stepMillis
        }
        return result
    }

    private val RELATIVE_STEPS_SECONDS = listOf(
        5L, 10, 15, 30,
        60, 120, 300, 600, 900, 1_800,
        3_600, 7_200, 10_800, 21_600, 43_200,
        86_400, 172_800, 604_800,
    )

    /**
     * Метки живого окна относительно [nowMillis]: «−4 мин · −2 мин · сейчас».
     *
     * Позиция каждой метки — её настоящая временная координата в окне, а не
     * равномерная раскладка: график с отступом справа обязан ставить «сейчас»
     * туда, где now, а не на кромку.
     */
    fun relativeLabels(
        fromMillis: Long,
        toMillis: Long,
        nowMillis: Long,
        s: ChartAxisStrings = ChartAxisRu,
        count: Int = 4,
    ): List<Pair<Float, String>> {
        val window = toMillis - fromMillis
        if (window <= 0 || count <= 0) return emptyList()
        val rawStepSeconds = window / 1000.0 / count
        val stepSeconds = RELATIVE_STEPS_SECONDS.firstOrNull { it >= rawStepSeconds }
            ?: RELATIVE_STEPS_SECONDS.last()
        val stepMillis = stepSeconds * 1000L
        val result = mutableListOf<Pair<Float, String>>()
        var tick = nowMillis
        while (tick >= fromMillis) {
            if (tick <= toMillis) {
                val agoSeconds = (nowMillis - tick) / 1000L
                result += (tick - fromMillis).toFloat() / window to s.agoLabel(agoSeconds)
            }
            tick -= stepMillis
        }
        return result.reversed()
    }

    /** Beyond this span a clock label repeats every day and stops informing. */
    const val DAY_LABEL_SPAN_MILLIS = 2L * 24 * 3_600_000L

    private val STEPS_DAYS = listOf(1L, 2, 3, 7, 14, 30, 60)

    /**
     * Wall-clock labels appropriate to the span: hh:mm for windows up to two
     * days, calendar days («3 авг») above that — a 30-day chart labelled
     * 00:00 · 00:00 · 00:00 would carry no information at all.
     */
    fun autoLabels(
        fromMillis: Long,
        toMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        count: Int = 4,
    ): List<Pair<Float, String>> =
        if (toMillis - fromMillis <= DAY_LABEL_SPAN_MILLIS) {
            labels(fromMillis, toMillis, zone, count)
        } else {
            dayLabels(fromMillis, toMillis, zone, count)
        }

    /** Ticks on local midnights, «nice» day steps, labelled «3 авг». */
    fun dayLabels(
        fromMillis: Long,
        toMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        count: Int = 4,
    ): List<Pair<Float, String>> {
        val window = toMillis - fromMillis
        if (window <= 0 || count <= 0) return emptyList()
        val rawStepDays = window / 86_400_000.0 / count
        val stepDays = STEPS_DAYS.firstOrNull { it >= rawStepDays } ?: STEPS_DAYS.last()
        var date = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        var tick = date.atStartOfDay(zone).toInstant().toEpochMilli()
        if (tick < fromMillis) {
            date = date.plusDays(1)
            tick = date.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val result = mutableListOf<Pair<Float, String>>()
        while (tick <= toMillis) {
            result += (tick - fromMillis).toFloat() / window to HistoryFormat.day(tick, zone)
            date = date.plusDays(stepDays)
            tick = date.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        return result
    }
}
