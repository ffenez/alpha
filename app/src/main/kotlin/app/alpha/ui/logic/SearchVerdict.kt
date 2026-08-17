package app.alpha.ui.logic

import app.alpha.ui.text.RuStrings
import app.alpha.ui.text.SearchRu
import app.alpha.ui.text.SearchStrings
import app.alpha.ui.text.Strings
import app.alpha.analysis.Dispersion
import app.alpha.analysis.RateComparisonResult
import app.alpha.analysis.RateTest
import app.alpha.analysis.UncertaintyModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The sentence Поиск is allowed to say, and the numbers behind it
 * (search redesign §3, §4, §11, §12).
 *
 * Two rules shape every string here. A difference that the test did not confirm
 * is **never** called «повышение» or «понижение» — it is called what it is, an
 * unconfirmed difference. And nothing on this screen may say «норма»,
 * «безопасно» or «опасно»: the count rate is not a dose and not a risk, and the
 * whole mode answers one question — «выше ли счёт записанного фона». Both rules
 * are pinned by `SearchVerdictTest`.
 */
object SearchVerdict {

    /** The one line under the big number. */
    fun headline(
        level: SearchLevel,
        direction: SearchDirection,
        hasBackground: Boolean,
        s: Strings = RuStrings,
    ): String {
        // Направление — приписка к вердикту, а не его часть: оно уточняет,
        // куда идёт счёт, но вывод делает не оно.
        fun withDirection(base: String): String = when (direction) {
            SearchDirection.RISING -> "$base · ${s.countRising}"
            SearchDirection.FALLING -> "$base · ${s.countFalling}"
            else -> base
        }
        return when (level) {
            SearchLevel.UNKNOWN ->
                if (!hasBackground) s.searchNoBackground else s.searchWaiting
            // «На уровне фона» — утверждение о равенстве, которого тест не
            // делал: непринятие различия не доказывает совпадение (NIST,
            // интервалы для слабого пуассоновского сигнала на фоне).
            SearchLevel.BACKGROUND -> withDirection(s.searchNoExcess)
            SearchLevel.POSSIBLE_CHANGE -> s.searchSmallChange
            SearchLevel.CONFIRMED_EXCESS -> withDirection(s.searchConfirmedExcess)
            SearchLevel.CONFIRMED_DEFICIT -> s.searchConfirmedDeficit
        }
    }

    /**
     * Half a sentence naming what the verdict was compared with, so the
     * headline is never read as an absolute statement about the place.
     */
    fun explanation(
        level: SearchLevel,
        comparison: RateComparisonResult?,
        s: Strings = RuStrings,
    ): String {
        val since = comparison?.let { ratioPhrase(it, s) }
        val confirm = seconds(SearchLadder.CONFIRM_MILLIS, s)
        return when (level) {
            SearchLevel.UNKNOWN -> s.searchCannotCompare
            SearchLevel.BACKGROUND -> s.searchNotConfirmed(since)
            SearchLevel.POSSIBLE_CHANGE -> s.searchTooShort(confirm)
            SearchLevel.CONFIRMED_EXCESS -> s.searchExcessExplained(confirm, since)
            SearchLevel.CONFIRMED_DEFICIT -> s.searchDeficitExplained(confirm, since)
        }
    }

    /** «×1,8 к фону (95 % интервал 1,5–2,2)» — always with its denominator. */
    fun ratioPhrase(comparison: RateComparisonResult, s: Strings = RuStrings): String? {
        if (comparison.test == RateTest.NONE) return null
        val ratio = comparison.ratio
        if (!ratio.isFinite() || ratio <= 0.0) return null
        val level = (comparison.confidenceLevel * 100).roundToInt()
        val low = comparison.ratioLow
        val high = comparison.ratioHigh
        val interval = if (low.isFinite() && high.isFinite()) {
            s.confidenceInterval(level, num2(low), num2(high))
        } else {
            null
        }
        return s.ratioToBackground(num2(ratio), interval)
    }

    /**
     * «×1,8» — the ratio alone, for the chart marker, where the denominator is
     * named by the dashed background line right under it. Never used in a
     * sentence: there the phrase above carries its reference.
     */
    fun ratioShort(comparison: RateComparisonResult?): String? {
        val ratio = comparison?.ratio ?: return null
        if (comparison.test == RateTest.NONE || !ratio.isFinite() || ratio <= 0.0) return null
        return "×${num2(ratio)}"
    }

    /**
     * Relative difference in whole percent, **with** its reference named by the
     * caller. Null when there is nothing to divide by (§12: no percentage
     * without saying of what).
     */
    fun deltaPercent(comparison: RateComparisonResult?): Int? {
        val background = comparison?.background?.ratePerSecond ?: return null
        if (background <= 0.0) return null
        val current = comparison.current.ratePerSecond
        return (((current - background) / background) * 100.0).roundToInt()
    }

    /**
     * «↑ счёт растёт» — половина «горячо-холодно», сознательно не вердикт.
     *
     * Голое «ровно» на экране не читалось: непонятно, что именно ровно и за
     * какое время это сказано. Все три подписи называют ВЕЛИЧИНУ (счёт) и
     * относятся к последним [SearchDirectionFit.WINDOW_MILLIS] — окно
     * названо рядом, в подписи под чипом.
     */
    fun directionLabel(direction: SearchDirection, s: Strings = RuStrings): String? =
        when (direction) {
            SearchDirection.UNKNOWN -> null
            SearchDirection.STEADY -> "→ ${s.countSteady}"
            SearchDirection.RISING -> "↑ ${s.countRising}"
            SearchDirection.FALLING -> "↓ ${s.countFalling}"
        }

    /** За какое время сделан вывод о направлении — подпись под чипом. */
    fun directionNote(
        direction: SearchDirection,
        windowMillis: Long,
        s: Strings = RuStrings,
    ): String? =
        if (direction == SearchDirection.UNKNOWN) {
            null
        } else {
            s.directionOverLast(windowMillis / 1000)
        }

    /** Line about short excursions that never reached the confirmation time. */
    fun spikeLine(spikes: List<SpikeMarker>, t: SearchStrings = SearchRu): String? {
        if (spikes.isEmpty()) return null
        val peak = spikes.maxOf { it.peakRatio }
        return t.spikes(spikes.size, "×${num2(peak)}")
    }

    /**
     * The research layer (§4): every number the verdict stands on, including
     * the ones that make it weaker.
     */
    fun whyLines(
        input: SearchWhyInput,
        s: Strings = RuStrings,
        t: SearchStrings = SearchRu,
    ): List<WhyLine> {
        val lines = ArrayList<WhyLine>(12)
        val comparison = input.comparison

        lines += WhyLine(
            label = t.whyCountRateNow,
            value = input.cps?.let { "${Uncertainty.num1(it)} ${t.cpsUnit}" } ?: t.valueNoData,
            evidence = Evidence.MEASURED,
            note = input.cps?.let { Uncertainty.cpsSigmaLine(it) },
        )

        val background = input.background
        lines += WhyLine(
            label = t.whyBackground,
            value = background?.let { "${Uncertainty.num1(it.cps)} ${t.cpsUnit}" }
                ?: t.valueNotRecorded,
            evidence = Evidence.MEASURED,
            note = background?.let {
                t.backgroundWindowNote(
                    sigma = Uncertainty.num1(it.sigma),
                    samples = it.window.samples,
                    exposure = num1(it.window.seconds),
                    quality = SearchBaseline.qualityLabel(it.quality, t),
                )
            },
        )

        if (comparison == null) {
            lines += WhyLine(
                label = t.whyComparison,
                value = t.valueNotPerformed,
                evidence = Evidence.STATISTICALLY_DETECTED,
                note = if (background == null) t.noBackgroundToCompare else t.noReadingsInWindow,
            )
            return lines
        }

        lines += WhyLine(
            label = t.whyDecisionWindow,
            value = t.secondsValue(num1(comparison.current.seconds)),
            evidence = Evidence.MEASURED,
            note = t.countsInWindow(num0(comparison.current.counts), comparison.current.samples) +
                gapNote(comparison.current.gapSeconds, t),
        )
        lines += WhyLine(
            label = t.whyBackgroundWindow,
            value = t.secondsValue(num1(comparison.background.seconds)),
            evidence = Evidence.MEASURED,
            note = t.counts(num0(comparison.background.counts)) +
                gapNote(comparison.background.gapSeconds, t),
        )
        lines += WhyLine(
            label = t.whyDifference,
            value = signed(comparison.differencePerSecond) + " ${t.cpsUnit}",
            evidence = Evidence.CALCULATED,
            note = t.differenceNote(
                sigma = num2(comparison.differenceSigma),
                percent = deltaPercent(comparison)?.let { signedPercent(it) },
            ),
        )
        ratioPhrase(comparison, s)?.let {
            lines += WhyLine(
                label = t.whyRatio,
                value = "×${num2(comparison.ratio)}",
                evidence = Evidence.STATISTICALLY_DETECTED,
                // Интервал без названного метода — просто пара чисел: у
                // пуассоновских счётов нормальное приближение на малых
                // числах даёт не тот охват, ради которого интервал и строят.
                note = t.ratioNote(it),
            )
        }
        lines += WhyLine(
            label = t.whyCriterion,
            value = shortTestName(comparison.test, t),
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = t.criterionNote(testLabel(comparison.test, t), modelLabel(comparison.model, t)),
        )
        lines += WhyLine(
            label = t.whySignificance,
            value = pLabel(comparison.pValue),
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = t.significanceNote(
                alpha = num2(SearchLadder.ALPHA),
                z = comparison.zEquivalent?.let { num2(it) },
            ),
        )
        lines += WhyLine(
            label = t.whyScatter,
            value = comparison.fanoFactor?.let { "F = ${num2(it)}" } ?: t.valueScatterNotEvaluated,
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = t.dispersionNote(
                dispersion = dispersionLabel(comparison.dispersion, t),
                phi = if (comparison.dispersionFactor > 1.0) {
                    num2(comparison.dispersionFactor)
                } else {
                    null
                },
            ),
        )
        lines += WhyLine(
            label = t.whyHold,
            value = input.heldMillis?.let { seconds(it, s) } ?: t.valueNoHold,
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = t.holdNote(
                confirm = seconds(SearchLadder.CONFIRM_MILLIS, s),
                release = seconds(SearchLadder.RELEASE_MILLIS, s),
            ),
        )
        lines += WhyLine(
            label = t.whyStream,
            value = if (input.streamFresh) t.valueStreamRunning else t.valueStreamBroken,
            evidence = Evidence.MEASURED,
            note = t.streamNote,
        )
        // «Не оценивается» — это не «изменений нет»: экран сравнивает счёт.
        lines += WhyLine(
            label = t.whyShape,
            value = t.valueShapeNotEvaluated,
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = t.shapeNote,
        )
        return lines
    }

    private fun gapNote(gapSeconds: Double, t: SearchStrings): String =
        if (gapSeconds > 0.5) t.gapNote(num1(gapSeconds)) else ""

    private fun shortTestName(test: RateTest, t: SearchStrings): String = when (test) {
        RateTest.CONDITIONAL_BINOMIAL -> t.testConditionalBinomial
        RateTest.QUASI_BINOMIAL -> t.testQuasiBinomial
        RateTest.NONE -> t.testNone
    }

    // Сами перечисления живут в движке (`analysis/`), общем для всех экранов,
    // поэтому их человеческие подписи собираются здесь, из каталога области.
    private fun testLabel(test: RateTest, t: SearchStrings): String = when (test) {
        RateTest.CONDITIONAL_BINOMIAL -> t.testLabelConditionalBinomial
        RateTest.QUASI_BINOMIAL -> t.testLabelQuasiBinomial
        RateTest.NONE -> t.testLabelNone
    }

    private fun modelLabel(model: UncertaintyModel, t: SearchStrings): String = when (model) {
        UncertaintyModel.POISSON -> t.modelPoisson
        UncertaintyModel.EMPIRICAL_VARIANCE -> t.modelEmpiricalVariance
    }

    private fun dispersionLabel(dispersion: Dispersion, t: SearchStrings): String =
        when (dispersion) {
            Dispersion.UNKNOWN -> t.dispersionUnknown
            Dispersion.POISSON_LIKE -> t.dispersionPoissonLike
            Dispersion.OVERDISPERSED -> t.dispersionOverdispersed
            Dispersion.UNDERDISPERSED -> t.dispersionUnderdispersed
        }

    private fun pLabel(p: Double): String = when {
        !p.isFinite() -> "—"
        p < 1e-6 -> "p < 10⁻⁶"
        p < 0.001 -> "p < 0,001"
        else -> "p = ${num3(p)}"
    }

    private fun signedPercent(percent: Int): String =
        if (percent >= 0) "+$percent %" else "−${abs(percent)} %"

    private fun signed(value: Double): String =
        if (value >= 0) "+${num2(value)}" else "−${num2(abs(value))}"

    fun seconds(millis: Long, s: Strings = RuStrings): String = s.seconds(millis / 1000)

    // Formatting matches [Uncertainty]: fixed locale, comma as the decimal
    // separator — the strings are pinned by tests and must not depend on the
    // device locale.
    private fun num0(value: Double): String = String.format(Locale.US, "%.0f", value)

    private fun num1(value: Double): String = decimal(value, 1)

    private fun num2(value: Double): String = decimal(value, 2)

    private fun num3(value: Double): String = decimal(value, 3)

    private fun decimal(value: Double, digits: Int): String =
        String.format(Locale.US, "%.${digits}f", value).replace('.', ',')
}

/** Everything the Поиск «Почему?» sheet shows; assembled by the screen. */
data class SearchWhyInput(
    val cps: Float?,
    val background: BackgroundRecord?,
    val comparison: RateComparisonResult?,
    /** How long the current difference has held, ms; null when there is none. */
    val heldMillis: Long?,
    val streamFresh: Boolean,
)
