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
}
