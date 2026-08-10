package app.radiacode.ui.logic

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
        while (tick <= toMillis) {
            val fraction = (tick - fromMillis).toFloat() / window
            result += fraction to Instant.ofEpochMilli(tick).atZone(zone).format(HH_MM)
            tick += stepMillis
        }
        return result
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
