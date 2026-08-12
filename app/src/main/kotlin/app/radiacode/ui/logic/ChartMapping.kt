package app.radiacode.ui.logic

import app.radiacode.data.db.DownsampledSample
import app.radiacode.device.DoseUnits
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Pure mapping from Room's downsampled buckets to fixed-width chart columns,
 * plus the summary numbers a data screen must carry (design-language.md:
 * every chart shows min/avg/max/σ). JVM-tested.
 */
object ChartMapping {

    /** Aligns [toMillis] minus the window onto the bucket grid Room groups by. */
    fun alignedFrom(toMillis: Long, windowMillis: Long, bucketMillis: Long): Long =
        floor((toMillis - windowMillis).toDouble() / bucketMillis).toLong() * bucketMillis

    /**
     * Distributes buckets into [columnCount] slots by bucket start; slots with
     * no data stay null (rendered as gaps — missing data is not interpolated).
     */
    fun toColumns(
        buckets: List<DownsampledSample>,
        alignedFromMillis: Long,
        bucketMillis: Long,
        columnCount: Int,
        value: (DownsampledSample) -> Float,
    ): List<Float?> {
        val columns = arrayOfNulls<Float>(columnCount)
        for (bucket in buckets) {
            val index = ((bucket.bucketStart - alignedFromMillis) / bucketMillis).toInt()
            if (index in 0 until columnCount) columns[index] = value(bucket)
        }
        return columns.toList()
    }

    data class Stats(
        val min: Float,
        val avg: Float,
        val median: Float,
        val max: Float,
        val sigma: Float,
        val count: Int,
    )

    /** Population σ and median over present columns; null when nothing is present. */
    fun stats(columns: List<Float?>): Stats? {
        val values = columns.filterNotNull()
        if (values.isEmpty()) return null
        val min = values.min()
        val max = values.max()
        val avg = values.sum() / values.size
        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        }
        val variance = values.sumOf { val d = (it - avg).toDouble(); d * d } / values.size
        return Stats(
            min = min,
            avg = avg,
            median = median,
            max = max,
            sigma = sqrt(variance).toFloat(),
            count = values.size,
        )
    }

    /**
     * «Nice» y-axis gridline values below [yMax]: step is 1/2/5·10^k chosen
     * so that 2–5 lines fit. Values ascend and exclude 0 and [yMax] itself.
     */
    fun yTicks(yMax: Float): List<Float> {
        if (yMax <= 0f) return emptyList()
        var step = niceStep(yMax / 5.0)
        if (yMax / step > 5.5) step = niceStep(yMax / 4.0)
        val ticks = mutableListOf<Float>()
        var v = step
        while (v < yMax * 0.98) {
            ticks += v.toFloat()
            v += step
        }
        return ticks
    }

    /** «Красивый» шаг 1/2/5·10^k, не меньше [raw]. */
    fun niceStep(raw: Double): Double {
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

    /**
     * Accumulated dose over buckets of 1 Hz samples, µSv. Each bucket
     * contributes avgDoseRate (µSv/h) for its measured seconds
     * ([DownsampledSample.sampleCount] ≈ seconds at 1 Hz). Calculated value —
     * the UI must label it as such (SPEC: Measured vs Calculated).
     */
    fun integrateDoseMicroSv(buckets: List<DownsampledSample>): Double =
        buckets.sumOf { bucket ->
            DoseUnits.rawToMicroSievertPerHour(bucket.avgDoseRate).toDouble() *
                bucket.sampleCount / 3600.0
        }

    /**
     * Chart scale top: headroom over the data, and the alarm line stays in
     * frame when it is within reach of the data (not pinned when data is far
     * below it, which would flatten the columns into noise).
     */
    fun yMax(dataMax: Float?, alarmLevel: Float?): Float {
        val data = (dataMax ?: 0f).coerceAtLeast(MIN_Y_MAX)
        val withHeadroom = data * 1.25f
        return if (alarmLevel != null && alarmLevel > 0f && alarmLevel <= data * ALARM_SNAP_FACTOR) {
            maxOf(withHeadroom, alarmLevel * 1.15f)
        } else {
            withHeadroom
        }
    }

    /** Column height in plot pixels; non-zero values are at least 1 px tall. */
    fun columnHeightPx(value: Float, yMax: Float, plotHeightPx: Int): Int {
        if (value <= 0f || yMax <= 0f) return 0
        val h = Math.round(value / yMax * plotHeightPx)
        return h.coerceIn(1, plotHeightPx)
    }

    /** Pixel row (0 = top) for a horizontal level line; null if out of frame. */
    fun rowForLevel(level: Float, yMax: Float, plotHeightPx: Int): Int? {
        if (level <= 0f || yMax <= 0f || level > yMax) return null
        val row = plotHeightPx - 1 - Math.round(level / yMax * (plotHeightPx - 1))
        return row.coerceIn(0, plotHeightPx - 1)
    }

    private const val MIN_Y_MAX = 0.001f
    private const val ALARM_SNAP_FACTOR = 4f
}
