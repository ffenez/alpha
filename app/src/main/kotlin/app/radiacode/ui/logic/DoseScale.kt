package app.radiacode.ui.logic

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Value → plot-fraction mapping of the dose axis, linear or logarithmic.
 *
 * Fraction 0 is the bottom of the plot, 1 the top. [fractionOrNull] is the
 * honest mapping: on a log scale a zero or negative value has **no** position
 * — it returns null and the chart draws a gap there instead of inventing a
 * floor. [DoseScales.logDroppedBuckets] counts those buckets so the screen can
 * say out loud how many were left out (SPEC §2: never present a computed
 * substitute as the measurement).
 *
 * Pure JVM, tested.
 */
sealed interface DoseScale {

    /** Bottom of the frame, µSv/h. */
    val minValue: Float

    /** Top of the frame, µSv/h. */
    val maxValue: Float

    val logarithmic: Boolean

    /** Position of [value], or null when this scale cannot show it honestly. */
    fun fractionOrNull(value: Float): Float?

    /** Gridline values inside the frame, ascending, «nice» steps. */
    fun ticks(): List<Float>
}

/** Zero-based linear scale — the default reading of a dose-rate axis. */
data class LinearDoseScale(override val maxValue: Float) : DoseScale {
    override val minValue: Float get() = 0f
    override val logarithmic: Boolean get() = false

    override fun fractionOrNull(value: Float): Float? {
        if (maxValue <= 0f) return null
        return (value / maxValue).coerceIn(0f, 1f)
    }

    override fun ticks(): List<Float> = ChartMapping.yTicks(maxValue)
}

/**
 * Decade scale for wide-dynamic-range windows (7–30 days): a factor-of-ten
 * excursion no longer flattens the ordinary background into a line. Bounds
 * are whole decades so the gridlines are 1/2/5·10^k.
 */
data class LogDoseScale(
    override val minValue: Float,
    override val maxValue: Float,
) : DoseScale {
    override val logarithmic: Boolean get() = true

    private val logMin = log10(minValue.toDouble())
    private val logSpan = log10(maxValue.toDouble()) - logMin

    override fun fractionOrNull(value: Float): Float? {
        if (value <= 0f || logSpan <= 0.0) return null
        return ((log10(value.toDouble()) - logMin) / logSpan).toFloat().coerceIn(0f, 1f)
    }

    override fun ticks(): List<Float> {
        val result = mutableListOf<Float>()
        var decade = floor(logMin).toInt()
        val top = maxValue
        while (decade <= ceil(logMin + logSpan).toInt()) {
            val base = 10.0.pow(decade)
            for (mantissa in MANTISSAS) {
                val v = (base * mantissa).toFloat()
                if (v > minValue && v < top * 0.999f) result += v
            }
            decade++
        }
        return result
    }

    private companion object {
        val MANTISSAS = listOf(1.0, 2.0, 5.0)
    }
}

object DoseScales {

    /** Log frames never go below this — 1 nSv/h is far under any real reading. */
    const val LOG_FLOOR_MICRO_SV_H = 0.001f

    /**
     * Frame for the visible data. [dataMin]/[dataMax] are the extremes that
     * must fit (bucket min–max envelope), [mustFit] are levels that should stay
     * in frame when they are near the data (alarm line, baseline band).
     */
    fun of(
        logarithmic: Boolean,
        dataMin: Float?,
        dataMax: Float?,
        alarmLevel: Float? = null,
        baselineHigh: Float? = null,
    ): DoseScale {
        val top = ChartMapping.yMax(
            maxOf(dataMax ?: 0f, baselineHigh ?: 0f),
            alarmLevel,
        )
        if (!logarithmic) return LinearDoseScale(top)
        val positiveMin = listOfNotNull(dataMin.takeIf { it != null && it > 0f })
            .minOrNull() ?: (top / 100f)
        val bottom = decadeBelow(maxOf(positiveMin, LOG_FLOOR_MICRO_SV_H))
        val ceilTop = decadeAbove(maxOf(top, bottom * 10f))
        return LogDoseScale(bottom, ceilTop)
    }

    /**
     * How many present buckets a log scale cannot place (value ≤ 0). The UI
     * must show this number instead of silently dropping data.
     */
    fun logDroppedBuckets(buckets: List<ChartBucket?>): Int =
        buckets.count { it != null && it.median <= 0f }

    private fun decadeBelow(value: Float): Float =
        10.0.pow(floor(log10(value.toDouble()))).toFloat()

    private fun decadeAbove(value: Float): Float =
        10.0.pow(ceil(log10(value.toDouble()))).toFloat()
}
