package app.radiacode.ui.logic

import app.radiacode.analysis.RateComparisonResult
import app.radiacode.analysis.RateTest
import app.radiacode.ui.text.SearchRu
import app.radiacode.ui.text.SearchStrings
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What «Наведение» is allowed to say.
 *
 * Two rules shape every string built here. There is no louder word for a bigger
 * change — the magnitude is a **ratio with its interval**, always. And a
 * percentage is printed only when the exact test has resolved a difference;
 * otherwise the screen shows a dash and says why, because «+31 %» that is
 * indistinguishable from noise is worse than no number at all.
 */
object NavigateVerdict {

    /** The one line under the big number. */
    fun trendLabel(trend: NavigateTrend, t: SearchStrings = SearchRu): String = when (trend) {
        NavigateTrend.COLLECTING -> t.navTrendCollecting
        NavigateTrend.NO_CHANGE -> t.navTrendNoChange
        NavigateTrend.RISING -> t.navTrendRising
        NavigateTrend.FALLING -> t.navTrendFalling
    }

    /** «×1,6 (95 % 1,2–2,1)» — the size of the change, with its uncertainty. */
    fun ratioPhrase(
        comparison: RateComparisonResult?,
        t: SearchStrings = SearchRu,
    ): String? {
        if (comparison == null || comparison.test == RateTest.NONE) return null
        val ratio = comparison.ratio
        if (!ratio.isFinite() || ratio <= 0.0) return null
        val level = (comparison.confidenceLevel * 100).roundToInt()
        val low = comparison.ratioLow
        val high = comparison.ratioHigh
        if (!low.isFinite() || !high.isFinite()) return t.navRatio(num2(ratio), null)
        return t.navRatio(num2(ratio), t.navRatioInterval(level, num2(low), num2(high)))
    }

    /**
     * «×1,00 к локальному уровню» — the one ratio the main card shows.
     *
     * The denominator is named **in the same string** as the number: a bare
     * «×1,00» would be a ratio to nothing in particular, and the local level is
     * not the profile's фон.
     */
    fun localRatio(comparison: RateComparisonResult?, t: SearchStrings = SearchRu): String? {
        if (comparison == null || comparison.test == RateTest.NONE) return null
        val ratio = comparison.ratio
        if (!ratio.isFinite() || ratio <= 0.0) return null
        return t.navRatioToLocal(num2(ratio))
    }

    /** «95 % интервал 0,72–1,37» — the uncertainty of [localRatio], one line. */
    fun localInterval(comparison: RateComparisonResult?, t: SearchStrings = SearchRu): String? {
        if (comparison == null || comparison.test == RateTest.NONE) return null
        val low = comparison.ratioLow
        val high = comparison.ratioHigh
        if (!low.isFinite() || !high.isFinite()) return null
        val level = (comparison.confidenceLevel * 100).roundToInt()
        return t.navRatioInterval(level, num2(low), num2(high))
    }

    /** Which two windows the direction was decided on — never left unnamed. */
    fun windowsNote(state: NavigateState, t: SearchStrings = SearchRu): String? {
        val fast = state.fast?.seconds ?: return null
        val local = state.local?.seconds ?: return null
        return t.navWindows(num1(fast), num1(local))
    }

    /**
     * The **one** big number of the guidance module: a percentage, or a dash.
     *
     * A dash is not a missing value here, it is the answer: until the exact
     * test resolves a difference from the точка отсчёта there is no percentage
     * that would be true, and [deltaCaption] says which of the four reasons
     * applies.
     */
    fun deltaHeadline(delta: ReferenceDelta, t: SearchStrings = SearchRu): String =
        when (delta) {
            is ReferenceDelta.Resolved -> "${signed(delta.percent)} %"
            else -> t.navDeltaDash
        }

    /**
     * The direction line of the guidance module — **against the точка
     * отсчёта**.
     *
     * Its denominator is not the one [trendLabel] uses: the card above compares
     * the newest window with the seconds before it, this module compares it
     * with a point the operator froze. Two different comparisons, so two
     * different lines — and never the same sentence printed twice.
     */
    fun referenceDirection(delta: ReferenceDelta, t: SearchStrings = SearchRu): String =
        when (delta) {
            ReferenceDelta.NoReference -> t.navRefNone
            ReferenceDelta.Collecting -> t.navRefCollecting
            is ReferenceDelta.Unresolved -> t.navRefUnresolved
            is ReferenceDelta.Resolved -> if (delta.percent >= 0) t.navRefAbove else t.navRefBelow
        }

    /** The quiet line under [deltaHeadline]: the denominator, or the reason. */
    fun deltaCaption(delta: ReferenceDelta, t: SearchStrings = SearchRu): String = when (delta) {
        ReferenceDelta.NoReference -> t.navDeltaCaptionNoReference
        ReferenceDelta.Collecting -> t.navDeltaCaptionCollecting
        is ReferenceDelta.Unresolved ->
            t.navDeltaCaptionUnresolved(num2(delta.low), num2(delta.high))

        is ReferenceDelta.Resolved -> t.navDeltaCaptionResolved(num2(delta.ratio))
    }

    /**
     * «максимум 47,6 · 18 с назад», or nothing at all.
     *
     * The maximum carries its age because a held peak without one says nothing
     * about where to walk: the same number means «прямо сейчас» and «полминуты
     * назад в другом углу комнаты». It is a secondary caption — the reference
     * point has its own control now, and repeating it here would be the third
     * place the same number lives.
     */
    fun peakLine(
        state: NavigateState,
        nowMillis: Long,
        t: SearchStrings = SearchRu,
    ): String? {
        val peak = state.peak ?: return null
        val ago = ((nowMillis - peak.atMillis) / 1000L).coerceAtLeast(0L)
        return t.navPeakValue(num1(peak.ratePerSecond), ago.toInt())
    }

    /** «Отсчёт 26,0 с⁻¹ · 11:44» — the compact state of the reference control. */
    fun referenceLine(
        reference: NavigateReference?,
        timeOfDay: String?,
        t: SearchStrings = SearchRu,
    ): String? {
        if (reference == null || timeOfDay == null) return null
        return t.navReferenceSet(num1(reference.ratePerSecond), timeOfDay)
    }

    private fun signed(percent: Int): String = if (percent >= 0) "+$percent" else "−${-percent}"

    private fun num1(value: Double): String =
        String.format(Locale.US, "%.1f", value).replace('.', ',')

    private fun num2(value: Double): String =
        String.format(Locale.US, "%.2f", value).replace('.', ',')
}
