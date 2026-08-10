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

/**
 * What a ratio is divided by. CHART SPEC §17: a ratio without its denominator
 * named is not a statement — «×4,8 к привычному» is forbidden wording.
 */
enum class RatioDenominator { BASELINE_P90, BASELINE_MEDIAN }

/** Readout of one column under the crosshair (CHART SPEC §16). */
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
     * How far a reading sits above a **named** statistic of the profile.
     * Null when that statistic is missing (nothing to compare with) or the
     * reading is not above it: the phrase only makes sense as an excess, so
     * «×1,0» means «ровно на этом уровне».
     */
    fun ratioTo(valueMicroSvH: Float, denominatorMicroSvH: Float?): Float? {
        if (denominatorMicroSvH == null || denominatorMicroSvH <= 0f) return null
        val ratio = valueMicroSvH / denominatorMicroSvH
        return if (ratio >= MIN_NOTABLE_RATIO) ratio else null
    }

    /**
     * «×4,8 к P90 профиля» / «×4,8 к медиане профиля» — the denominator is
     * always part of the sentence (§17, §39).
     */
    fun ratioLabel(ratio: Float, denominator: RatioDenominator): String {
        val number = String.format(Locale.US, "%.1f", ratio).replace('.', ',')
        return "×$number к ${denominatorWording(denominator)}"
    }

    fun denominatorWording(denominator: RatioDenominator): String = when (denominator) {
        RatioDenominator.BASELINE_P90 -> "P90 профиля"
        RatioDenominator.BASELINE_MEDIAN -> "медиане профиля"
    }

    /**
     * The «Почему?» half-sentence shown wherever the ratio appears. P90 is a
     * description of this profile's history, never a permitted level (§8).
     */
    fun ratioExplanation(denominator: RatioDenominator): String = when (denominator) {
        RatioDenominator.BASELINE_P90 ->
            "P90 профиля — уровень, ниже которого оставались 90 % исторических " +
                "измерений этого места; это описание истории, а не норматив"
        RatioDenominator.BASELINE_MEDIAN ->
            "медиана профиля — половина исторических измерений этого места была ниже"
    }

    /** «14:02:00–14:03:00» — the interval the column actually covers. */
    fun binRangeLabel(bucket: ChartBucket, format: (Long) -> String): String =
        "${format(bucket.startMillis)}–${format(bucket.endMillis)}"

    /**
     * «в 14:02:07» when the extremum timestamp is exact (1-second
     * sub-buckets), «в 14:02:00–14:07:00» when the aggregation only knows the
     * interval it happened in. The chart never claims a precision the
     * aggregation does not have.
     */
    fun extremeTimeLabel(
        atMillis: Long,
        windowMillis: Long,
        format: (Long) -> String,
    ): String = if (windowMillis <= 1_000L) {
        "в ${format(atMillis)}"
    } else {
        "в ${format(atMillis)}–${format(atMillis + windowMillis)}"
    }

    private const val MIN_NOTABLE_RATIO = 1.0f
}
