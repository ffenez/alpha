package app.radiacode.ui.logic

/**
 * Visible time window of the fullscreen live chart. Pure math for pinch-zoom,
 * pan and live-follow (window ↔ pixel-fraction mapping); JVM-tested. The
 * screen holds one [ChartWindow] as state and feeds gestures through
 * [ChartWindows].
 */
data class ChartWindow(val fromMillis: Long, val toMillis: Long) {
    val spanMillis: Long get() = toMillis - fromMillis
}

object ChartWindows {

    /** Zoom bounds: 1 minute … 7 days. */
    const val MIN_SPAN_MILLIS = 60_000L
    const val MAX_SPAN_MILLIS = 7L * 24 * 3_600_000L

    /** Period chips of the fullscreen chart. */
    val PERIODS: List<Pair<String, Long>> = listOf(
        "15м" to 15L * 60_000L,
        "1ч" to 3_600_000L,
        "6ч" to 6L * 3_600_000L,
        "24ч" to 24L * 3_600_000L,
        "7д" to MAX_SPAN_MILLIS,
    )

    /** Window ending at now with the given span. */
    fun latest(spanMillis: Long, nowMillis: Long): ChartWindow {
        val span = spanMillis.coerceIn(MIN_SPAN_MILLIS, MAX_SPAN_MILLIS)
        return ChartWindow(nowMillis - span, nowMillis)
    }

    /** Live-follow tick: keep the span, pin the right edge to now. */
    fun follow(window: ChartWindow, nowMillis: Long): ChartWindow =
        latest(window.spanMillis, nowMillis)

    /**
     * Pan by a fraction of the span (positive = later in time). The right
     * edge clamps at now; the span never changes.
     */
    fun pan(window: ChartWindow, deltaFraction: Float, nowMillis: Long): ChartWindow {
        val shift = (window.spanMillis * deltaFraction).toLong()
        var from = window.fromMillis + shift
        var to = window.toMillis + shift
        if (to > nowMillis) {
            from -= to - nowMillis
            to = nowMillis
        }
        return ChartWindow(from, to)
    }

    /**
     * Zoom by [factor] (>1 = zoom in) keeping the time under [focusFraction]
     * (0..1 across the plot) fixed. Span clamps to [MIN_SPAN_MILLIS]..
     * [MAX_SPAN_MILLIS], the right edge clamps at now.
     */
    fun zoom(
        window: ChartWindow,
        factor: Float,
        focusFraction: Float,
        nowMillis: Long,
    ): ChartWindow {
        if (factor <= 0f) return window
        val span = (window.spanMillis / factor).toLong()
            .coerceIn(MIN_SPAN_MILLIS, MAX_SPAN_MILLIS)
        val focus = focusFraction.coerceIn(0f, 1f)
        val focusTime = timeAt(window, focus)
        var from = focusTime - (span * focus).toLong()
        var to = from + span
        if (to > nowMillis) {
            from -= to - nowMillis
            to = nowMillis
        }
        return ChartWindow(from, to)
    }

    /** Fraction (0..1) → epoch millis inside the window. */
    fun timeAt(window: ChartWindow, fraction: Float): Long =
        window.fromMillis + (window.spanMillis * fraction.coerceIn(0f, 1f)).toLong()

    /** Epoch millis → fraction (0..1) inside the window. */
    fun fraction(window: ChartWindow, timeMillis: Long): Float {
        if (window.spanMillis <= 0L) return 0f
        return ((timeMillis - window.fromMillis).toFloat() / window.spanMillis)
            .coerceIn(0f, 1f)
    }

    /** Downsampling bucket for the window at the given column count, ≥1 s. */
    fun bucketMillis(spanMillis: Long, columns: Int): Long =
        (spanMillis / columns.coerceAtLeast(1)).coerceAtLeast(1_000L)

    /**
     * The window sits at the live edge when now is within one bucket of the
     * right edge — panning back to «сейчас» re-enables following naturally.
     */
    fun isAtLiveEdge(window: ChartWindow, nowMillis: Long, bucketMillis: Long): Boolean =
        nowMillis - window.toMillis <= bucketMillis

    /**
     * Live refresh cadence: 1 Hz appends on short windows; long windows only
     * change a bucket every bucketMillis, so refreshing faster than a quarter
     * bucket (capped at 15 s) would waste queries without new pixels.
     */
    fun refreshMillis(bucketMillis: Long): Long =
        (bucketMillis / 4).coerceIn(1_000L, 15_000L)
}
