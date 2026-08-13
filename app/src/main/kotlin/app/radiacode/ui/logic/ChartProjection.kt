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
    /** Outer robust envelope Q10–Q90 (CHART SPEC §6). */
    val q10Y: FloatArray,
    val q90Y: FloatArray,
    /** Inner robust envelope Q25–Q75. */
    val q25Y: FloatArray,
    val q75Y: FloatArray,
    val plottable: BooleanArray,
    /**
     * Начинается ли в этой колонке НОВЫЙ отрезок линии.
     *
     * Пустые колонки в снимок не попадают вовсе (`ChartSeriesModel.fold`
     * выбрасывает их, чтобы не рисовать нулей там, где измерений не было), и
     * из-за этого две колонки по краям получасового пропуска оказывались
     * соседями по индексу. Линия и полосы разброса честно шли от одной к
     * другой — и на экране получался длинный идеально прямой диагональный
     * участок с расширяющимся конвертом, то есть картинка измерений, которых
     * не было. Разрыв определяется по ВРЕМЕНИ между соседями, а не по их
     * наличию в списке.
     */
    val segmentStart: BooleanArray,
) {
    val count: Int get() = x.size

    /**
     * Drawn column that came from [bucketIndex] of the source list, or null
     * when that column is outside the frame. The projection keeps a
     * contiguous slice, so this is arithmetic, not a search — the extremum
     * markers use it once per frame.
     */
    fun indexOfBucket(bucketIndex: Int): Int? {
        if (count == 0) return null
        val k = bucketIndex - source[0]
        return if (k in 0 until count) k else null
    }

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
            FloatArray(0), FloatArray(0), FloatArray(0), BooleanArray(0), BooleanArray(0),
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
    /**
     * Во сколько ширин колонки должен разойтись шаг по времени, чтобы это
     * считалось пропуском. **Инженерный параметр**: полторы — соседние
     * колонки стоят на расстоянии ровно одной ширины, и допуск в половину
     * покрывает округление границ, не пропуская настоящую дыру.
     */
    const val GAP_FACTOR = 1.5

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
        val q10Y = FloatArray(n)
        val q90Y = FloatArray(n)
        val q25Y = FloatArray(n)
        val q75Y = FloatArray(n)
        val plottable = BooleanArray(n)
        val segmentStart = BooleanArray(n)
        // Ширина колонки берётся у самих данных: снимок её знает, а сюда
        // приходят уже отобранные колонки.
        val stepMillis = buckets.getOrNull(first)
            ?.let { (it.endMillis - it.startMillis).takeIf { width -> width > 0L } }
            ?: 0L
        var previousMid = Long.MIN_VALUE

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
            if (stepMillis > 0L && previousMid != Long.MIN_VALUE) {
                segmentStart[k] = b.midMillis - previousMid > stepMillis * GAP_FACTOR
            }
            previousMid = b.midMillis
            medianY[k] = yOf(fMedian, topPx, heightPx)
            // A quantile the scale cannot place (log scale, zero value) falls
            // back to the median row: the envelope then collapses onto the
            // line instead of being pinned to the frame floor.
            q10Y[k] = yOf(scale.fractionOrNull(b.q10) ?: fMedian, topPx, heightPx)
            q25Y[k] = yOf(scale.fractionOrNull(b.q25) ?: fMedian, topPx, heightPx)
            q75Y[k] = yOf(scale.fractionOrNull(b.q75) ?: fMedian, topPx, heightPx)
            q90Y[k] = yOf(scale.fractionOrNull(b.q90) ?: fMedian, topPx, heightPx)
        }
        return ChartPixels(source, x, medianY, q10Y, q90Y, q25Y, q75Y, plottable, segmentStart)
    }

    /** Fraction (0 = bottom) → pixel row inside the plot rectangle. */
    fun yOf(fraction: Float, topPx: Float, heightPx: Float): Float =
        topPx + (1f - fraction.coerceIn(0f, 1f)) * heightPx
}
