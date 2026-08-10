package app.radiacode.ui.logic

/**
 * Bucket → pixel arrays of one chart frame.
 *
 * Everything the draw phase needs is a primitive array indexed by drawn
 * column: no boxing, no per-frame `Offset`/`Path` arithmetic over data
 * classes, no allocation inside `DrawScope`. The arrays are rebuilt only when
 * the snapshot, the window or the plot size change — a pan produces one new
 * [ChartPixels] per gesture frame at O(columns ≤ 200), never a database read.
 *
 * [plottable] is false where the scale cannot place the column honestly (a
 * log scale and a zero median): those columns are left as gaps, never pinned
 * to the frame bottom.
 */
class ChartPixels(
    /** Index of each drawn column inside the snapshot's bucket list. */
    val source: IntArray,
    val x: FloatArray,
    val medianY: FloatArray,
    val minY: FloatArray,
    val maxY: FloatArray,
    val sigmaLoY: FloatArray,
    val sigmaHiY: FloatArray,
    val plottable: BooleanArray,
) {
    val count: Int get() = x.size

    /** Column nearest to a pixel x, or null when the frame is empty. */
    fun nearestIndex(xPx: Float): Int? {
        if (count == 0) return null
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until count) {
            val d = kotlin.math.abs(x[i] - xPx)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    companion object {
        val EMPTY = ChartPixels(
            IntArray(0), FloatArray(0), FloatArray(0), FloatArray(0),
            FloatArray(0), FloatArray(0), FloatArray(0), BooleanArray(0),
        )
    }
}

object ChartProjection {

    /**
     * Projects the columns whose midpoint falls inside [fromMillis]..
     * [toMillis] onto the plot rectangle. X is mapped by wall-clock time, not
     * by column index, so panning an already-loaded snapshot moves the series
     * exactly as far as the finger without touching the database.
     */
    fun project(
        buckets: List<ChartBucket>,
        fromMillis: Long,
        toMillis: Long,
        scale: DoseScale,
        leftPx: Float,
        widthPx: Float,
        topPx: Float,
        heightPx: Float,
    ): ChartPixels {
        val span = toMillis - fromMillis
        if (buckets.isEmpty() || span <= 0L || widthPx <= 0f || heightPx <= 0f) {
            return ChartPixels.EMPTY
        }
        var first = -1
        var last = -2
        for (i in buckets.indices) {
            val mid = buckets[i].midMillis
            if (mid < fromMillis) continue
            if (mid > toMillis) break
            if (first < 0) first = i
            last = i
        }
        val n = last - first + 1
        if (n <= 0) return ChartPixels.EMPTY

        val source = IntArray(n)
        val x = FloatArray(n)
        val medianY = FloatArray(n)
        val minY = FloatArray(n)
        val maxY = FloatArray(n)
        val sigmaLoY = FloatArray(n)
        val sigmaHiY = FloatArray(n)
        val plottable = BooleanArray(n)

        for (k in 0 until n) {
            val b = buckets[first + k]
            source[k] = first + k
            x[k] = leftPx + widthPx * (b.midMillis - fromMillis).toFloat() / span
            val fMedian = scale.fractionOrNull(b.median)
            if (fMedian == null) {
                plottable[k] = false
                continue
            }
            plottable[k] = true
            medianY[k] = yOf(fMedian, topPx, heightPx)
            minY[k] = yOf(scale.fractionOrNull(b.min) ?: 0f, topPx, heightPx)
            maxY[k] = yOf(scale.fractionOrNull(b.max) ?: 1f, topPx, heightPx)
            sigmaLoY[k] = yOf(
                scale.fractionOrNull(b.mean - b.sigma) ?: scale.fractionOrNull(b.min) ?: 0f,
                topPx,
                heightPx,
            )
            sigmaHiY[k] = yOf(scale.fractionOrNull(b.mean + b.sigma) ?: 1f, topPx, heightPx)
        }
        return ChartPixels(source, x, medianY, minY, maxY, sigmaLoY, sigmaHiY, plottable)
    }

    /** Fraction (0 = bottom) → pixel row inside the plot rectangle. */
    fun yOf(fraction: Float, topPx: Float, heightPx: Float): Float =
        topPx + (1f - fraction.coerceIn(0f, 1f)) * heightPx
}
