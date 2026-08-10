package app.radiacode.ui.logic

import app.radiacode.data.DoseUnitSetting
import java.util.Locale

/**
 * Least-squares trend over the visible chart window (design: «Тренд/ч» on
 * the hero card). Pure JVM, tested.
 */
object TrendFit {

    /** Below this µSv/h-per-hour magnitude the trend reads as flat («→»). */
    const val FLAT_EPSILON_MICRO_SV = 0.0005f

    /**
     * Ordinary least-squares slope through the present columns, converted to
     * per-hour units ([bucketMillis] is the spacing between columns). Null
     * when fewer than two points are present.
     */
    fun slopePerHour(columns: List<Float?>, bucketMillis: Long): Float? {
        val points = columns.mapIndexedNotNull { index, value ->
            value?.let { index.toDouble() to it.toDouble() }
        }
        if (points.size < 2) return null
        val n = points.size.toDouble()
        val sumX = points.sumOf { it.first }
        val sumY = points.sumOf { it.second }
        val sumXY = points.sumOf { it.first * it.second }
        val sumXX = points.sumOf { it.first * it.first }
        val denominator = n * sumXX - sumX * sumX
        if (denominator == 0.0) return null
        val slopePerBucket = (n * sumXY - sumX * sumY) / denominator
        val bucketsPerHour = 3_600_000.0 / bucketMillis
        return (slopePerBucket * bucketsPerHour).toFloat()
    }

    /**
     * «+0,004 ↗» / «−0,012 ↘» / «0,000 →» in the display unit. The arrow is
     * sign with a flatness epsilon so noise does not oscillate the glyph.
     */
    fun label(slopeMicroSvHPerHour: Float, unit: DoseUnitSetting): String {
        val display = DoseFormat.rateValue(slopeMicroSvHPerHour, unit)
        val text = when (unit) {
            DoseUnitSetting.MICRO_SIEVERT -> String.format(Locale.US, "%+.3f", display)
            DoseUnitSetting.MICRO_ROENTGEN -> String.format(Locale.US, "%+.1f", display)
        }.replace('.', ',').replace("-", "−")
        val arrow = when {
            slopeMicroSvHPerHour > FLAT_EPSILON_MICRO_SV -> "↗"
            slopeMicroSvHPerHour < -FLAT_EPSILON_MICRO_SV -> "↘"
            else -> "→"
        }
        return "$text $arrow"
    }
}
