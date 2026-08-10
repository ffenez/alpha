package app.radiacode.ui.logic

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Distribution of the visible window: how much measured time was spent at
 * each dose-rate level. This is the answer the time series alone cannot give
 * — «был ли выброс редким гостем или это новый уровень».
 *
 * Counts are **measured seconds** (raw 1 Hz samples) per bin, not columns, so
 * a bin's height is honest measurement time. Values are the sub-bucket means
 * of the window; at short windows a sub-bucket is one second, so the bins then
 * hold the raw samples themselves.
 */
data class DoseHistogram(
    /** Left edge of bin 0, µSv/h. */
    val lowEdge: Float,
    val binWidth: Float,
    /** Measured samples per bin. */
    val counts: IntArray,
    /** Bins overlapping the baseline P10–P90 band, or null when no baseline. */
    val baselineBins: IntRange?,
    /** First bin containing values at or above the alarm level, or null. */
    val firstAlarmBin: Int?,
) {
    val binCount: Int get() = counts.size
    val maxCount: Int get() = counts.maxOrNull() ?: 0
    val totalCount: Int get() = counts.sum()

    fun binLow(index: Int): Float = lowEdge + index * binWidth
    fun binHigh(index: Int): Float = lowEdge + (index + 1) * binWidth

    /** Value → bin, clamped into range. */
    fun binOf(value: Float): Int =
        (floor(((value - lowEdge) / binWidth).toDouble()).toInt()).coerceIn(0, counts.size - 1)

    // IntArray needs manual equality for a data class used as Compose state.
    override fun equals(other: Any?): Boolean =
        other is DoseHistogram &&
            other.lowEdge == lowEdge &&
            other.binWidth == binWidth &&
            other.counts.contentEquals(counts) &&
            other.baselineBins == baselineBins &&
            other.firstAlarmBin == firstAlarmBin

    override fun hashCode(): Int {
        var h = lowEdge.hashCode()
        h = 31 * h + binWidth.hashCode()
        h = 31 * h + counts.contentHashCode()
        h = 31 * h + (baselineBins?.hashCode() ?: 0)
        h = 31 * h + (firstAlarmBin ?: -1)
        return h
    }
}

object DoseHistograms {

    /** Bins aimed for; the «nice» width rounding may give a few more or fewer. */
    const val TARGET_BINS = 22

    /** Hard cap so a pathological range cannot allocate an unbounded array. */
    const val MAX_BINS = 64

    /**
     * Bins the sub-buckets whose start falls inside [fromMillis]..[toMillis].
     * Bin width is a «nice» 1/2/5·10^k step and the left edge sits on that
     * grid, so the axis labels are readable numbers instead of data noise.
     * Returns null when the window holds no measurements.
     */
    fun build(
        aggregates: List<DoseAggregate>,
        fromMillis: Long,
        toMillis: Long,
        baseline: ClosedFloatingPointRange<Float>? = null,
        alarmLevel: Float? = null,
        targetBins: Int = TARGET_BINS,
    ): DoseHistogram? {
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var present = 0
        for (a in aggregates) {
            if (a.sampleCount <= 0 || a.startMillis < fromMillis || a.startMillis > toMillis) continue
            val v = a.meanMicroSvH
            if (v < min) min = v
            if (v > max) max = v
            present++
        }
        if (present == 0) return null

        val width = binWidth(min, max, targetBins)
        val lowEdge = (floor((min / width).toDouble()) * width).toFloat()
        val binCount = (ceil(((max - lowEdge) / width).toDouble()).toInt() + 1)
            .coerceIn(1, MAX_BINS)
        val counts = IntArray(binCount)
        for (a in aggregates) {
            if (a.sampleCount <= 0 || a.startMillis < fromMillis || a.startMillis > toMillis) continue
            val index = (floor(((a.meanMicroSvH - lowEdge) / width).toDouble()).toInt())
                .coerceIn(0, binCount - 1)
            counts[index] += a.sampleCount
        }

        val baselineBins = baseline?.let {
            val lo = ((it.start - lowEdge) / width).toInt().coerceIn(0, binCount - 1)
            val hi = ((it.endInclusive - lowEdge) / width).toInt().coerceIn(0, binCount - 1)
            if (it.endInclusive < lowEdge || it.start > lowEdge + binCount * width) null
            else lo..hi
        }
        // A bin is «hot» when it can contain values at or above the alarm
        // level, i.e. its upper edge exceeds it — never colour a bin crit
        // that only holds values below the threshold.
        val firstAlarmBin = alarmLevel
            ?.takeIf { it > 0f }
            ?.let { level -> (0 until binCount).firstOrNull { lowEdge + (it + 1) * width > level } }

        return DoseHistogram(
            lowEdge = lowEdge,
            binWidth = width,
            counts = counts,
            baselineBins = baselineBins,
            firstAlarmBin = firstAlarmBin,
        )
    }

    /** Evenly spaced bin-edge values for the strip's axis labels. */
    fun labelValues(histogram: DoseHistogram, count: Int = 4): List<Pair<Float, Float>> {
        if (count <= 0 || histogram.binCount == 0) return emptyList()
        return (0 until count).map { i ->
            val fraction = (i + 0.5f) / count
            fraction to (histogram.lowEdge + fraction * histogram.binCount * histogram.binWidth)
        }
    }

    private fun binWidth(min: Float, max: Float, targetBins: Int): Float {
        val span = (max - min).toDouble()
        if (span <= 0.0) return if (max > 0f) (max / targetBins) else 0.01f
        return niceStep(span / targetBins).toFloat()
    }

    /** 1/2/5·10^k step, same «nice» family as the y axis. */
    private fun niceStep(raw: Double): Double {
        if (raw <= 0.0) return 1.0
        val mag = Math.pow(10.0, floor(Math.log10(raw)))
        val norm = raw / mag
        val nice = when {
            norm <= 1.0 -> 1.0
            norm <= 2.0 -> 2.0
            norm <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * mag
    }
}
