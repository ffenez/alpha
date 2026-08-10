package app.radiacode.ui.logic

import java.util.Locale

/**
 * Interaction state of the fullscreen chart: whether the right edge follows
 * «сейчас» and where the crosshair sits.
 *
 * The rule the screen must never break: **a visible crosshair suspends
 * live-follow**. Otherwise the series would slide out from under the reading
 * the user is looking at. While it is suspended the top bar carries a «пауза»
 * chip, so the screen never silently stops being live (SPEC §21 — freshness
 * is always visible).
 */
data class ChartInteraction(
    val follow: Boolean = true,
    /** Plot fraction 0..1 of the crosshair; null = no crosshair. */
    val cursorFraction: Float? = null,
) {
    /** True while the chart deliberately does not track «сейчас». */
    val paused: Boolean get() = cursorFraction != null
}

object ChartInteractions {

    /** Long-press or drag: place the crosshair and suspend live-follow. */
    fun cursorAt(state: ChartInteraction, fraction: Float): ChartInteraction =
        ChartInteraction(follow = false, cursorFraction = fraction.coerceIn(0f, 1f))

    /**
     * Tap outside the crosshair: drop it. Following resumes only if the window
     * is still at the live edge — a chart panned into the past stays there.
     */
    fun dismissCursor(state: ChartInteraction, atLiveEdge: Boolean): ChartInteraction =
        ChartInteraction(follow = atLiveEdge, cursorFraction = null)

    /** «⌖ сейчас»: back to the live edge, crosshair dropped. */
    fun jumpToNow(): ChartInteraction = ChartInteraction(follow = true, cursorFraction = null)

    /**
     * After a pan/pinch: following resumes by itself when the window came back
     * to the live edge; the crosshair is dropped because it referred to a
     * different time range.
     */
    fun afterTransform(state: ChartInteraction, atLiveEdge: Boolean): ChartInteraction =
        ChartInteraction(follow = atLiveEdge, cursorFraction = null)

    /** Period chip: a fresh live window. */
    fun periodChanged(): ChartInteraction = jumpToNow()
}

/** Readout of one column under the crosshair. */
object CursorReadout {

    /**
     * Column nearest in time to the crosshair — the same column the chart
     * snaps its line to (both pick the nearest midpoint, and the projection is
     * linear in time). Null when the visible frame holds no columns.
     */
    fun nearestBucket(buckets: List<ChartBucket>, timeMillis: Long): ChartBucket? {
        if (buckets.isEmpty()) return null
        var best: ChartBucket? = null
        var bestDistance = Long.MAX_VALUE
        for (b in buckets) {
            val d = kotlin.math.abs(b.midMillis - timeMillis)
            if (d < bestDistance) {
                bestDistance = d
                best = b
            } else if (b.midMillis > timeMillis) {
                // Buckets are ordered: distance only grows from here.
                break
            }
        }
        return best
    }

    /**
     * «×4,8 к привычному» — how far the reading sits above the usual band of
     * this place. Null when there is no active baseline (nothing to compare
     * with) or the reading is inside/below the band: the phrase only makes
     * sense as an excess. The comparison is against P90, the upper edge of the
     * usual band, so «×1,0» means «на верхней границе привычного».
     */
    fun ratioToUsual(valueMicroSvH: Float, baselineHighMicroSvH: Float?): Float? {
        if (baselineHighMicroSvH == null || baselineHighMicroSvH <= 0f) return null
        val ratio = valueMicroSvH / baselineHighMicroSvH
        return if (ratio >= MIN_NOTABLE_RATIO) ratio else null
    }

    fun ratioLabel(ratio: Float): String =
        "×${String.format(Locale.US, "%.1f", ratio).replace('.', ',')} к привычному"

    private const val MIN_NOTABLE_RATIO = 1.0f
}
