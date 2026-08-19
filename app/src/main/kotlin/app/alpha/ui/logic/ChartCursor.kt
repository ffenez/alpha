package app.alpha.ui.logic

import app.alpha.ui.text.ChartAxisRu
import app.alpha.ui.text.ChartAxisStrings

import app.alpha.ui.text.uiDecimal
import java.util.Locale

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
    fun ratioLabel(
        ratio: Float,
        denominator: RatioDenominator,
        s: ChartAxisStrings = ChartAxisRu,
    ): String {
        val number = String.format(Locale.US, "%.1f", ratio).uiDecimal()
        return s.ratio(number, denominatorWording(denominator, s))
    }

    fun denominatorWording(
        denominator: RatioDenominator,
        s: ChartAxisStrings = ChartAxisRu,
    ): String = when (denominator) {
        RatioDenominator.BASELINE_P90 -> s.denominatorProfileP90
        RatioDenominator.BASELINE_MEDIAN -> s.denominatorProfileMedian
    }

    /**
     * The «Почему?» half-sentence shown wherever the ratio appears. P90 is a
     * description of this profile's history, never a permitted level (§8).
     */
    fun ratioExplanation(
        denominator: RatioDenominator,
        s: ChartAxisStrings = ChartAxisRu,
    ): String = when (denominator) {
        RatioDenominator.BASELINE_P90 -> s.profileP90Explained
        RatioDenominator.BASELINE_MEDIAN -> s.profileMedianExplained
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
        s: ChartAxisStrings = ChartAxisRu,
        // Лямбда последней: иначе вызов с trailing-lambda перестаёт собираться.
        format: (Long) -> String,
    ): String = if (windowMillis <= 1_000L) {
        s.atMoment(format(atMillis))
    } else {
        s.atInterval(format(atMillis), format(atMillis + windowMillis))
    }

    private const val MIN_NOTABLE_RATIO = 1.0f
}
