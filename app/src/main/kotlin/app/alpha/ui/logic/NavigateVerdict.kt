package app.alpha.ui.logic

import app.alpha.analysis.DetectionLimitsMath
import app.alpha.analysis.RateComparisonResult
import app.alpha.analysis.RateTest
import app.alpha.ui.text.SearchRu
import app.alpha.ui.text.SearchStrings
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

    /**
     * Состояние и величина ОДНОЙ строкой: «→ Без заметного изменения · 1,01×».
     *
     * Раньше это были две строки под большим числом — сначала состояние,
     * потом отношение, — и карточка занимала треть экрана, отвечая на один
     * вопрос: какой сейчас счёт.
     */
    fun trendLine(state: NavigateState, t: SearchStrings = SearchRu): String =
        t.navTrendLine(trendLabel(state.trend, t), localRatio(state.trendComparison, t))

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
     * «1,00× к недавнему уровню» — the one ratio the main card shows.
     *
     * The denominator is named **in the same string** as the number: a bare
     * «1,00×» would be a ratio to nothing in particular. It is called «недавний
     * уровень» and not «локальный»: the word has to say that this level is
     * computed by the app from the seconds just before, which is a different
     * thing from the точка отсчёта the operator froze by hand — and the screen
     * shows both.
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
     * The **one** big number of the guidance module: how many times the current
     * rate differs from the точка отсчёта.
     *
     * It is the ratio and not a percentage of change, and it is shown even
     * before the test resolves a difference — with the direction line saying
     * «Разница пока не подтверждена» above it. Those are two different
     * statements: the number is the estimate the windows produced, the line is
     * whether it may be relied on. The percentage stays out of the working
     * screen entirely and lives in «Почему такой вывод», where it appears only
     * once the test has resolved the difference: «+31 %» that cannot be told
     * from noise is worse than no number at all.
     */
    fun ratioHeadline(state: NavigateState, t: SearchStrings = SearchRu): String {
        val ratio = state.referenceRatio ?: return t.navDeltaDash
        if (!ratio.isFinite() || ratio <= 0.0) return t.navDeltaDash
        return "${num2(ratio)}×"
    }

    /** «+31 %» — only once the exact test resolved a difference; else null. */
    fun percentLabel(delta: ReferenceDelta): String? =
        (delta as? ReferenceDelta.Resolved)?.let { "${signed(it.percent)} %" }

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

    /**
     * The quiet line under the big number: **the denominator by name and by
     * value**, or the reason there is no comparison yet.
     *
     * «к точке отсчёта 25,1» — a ratio whose denominator is not on the screen
     * is a ratio to nothing in particular, and the reference is the one number
     * on this screen the operator chose themselves.
     */
    fun deltaCaption(
        state: NavigateState,
        delta: ReferenceDelta,
        t: SearchStrings = SearchRu,
    ): String = when (delta) {
        ReferenceDelta.NoReference -> t.navRefNone
        ReferenceDelta.Collecting -> t.navDeltaCaptionCollecting
        else -> state.reference
            ?.let { t.navRefBase(num1(it.ratePerSecond)) }
            ?: t.navDeltaCaptionCollecting
    }

    /**
     * Строка под главным числом: во сколько раз, ОТ ЧЕГО и насколько точно.
     *
     * Крупное число на экране одно — сама скорость счёта; отношение читается
     * подписью под ним. Отдельным крупным числом оно спорило с показанием за
     * внимание, а человек в этот момент несёт прибор и смотрит на одно место.
     *
     * Знаменатель назван всегда: отношение без знаменателя — отношение ни к
     * чему. Интервал приписывается, когда он посчитан: без него «×2,1»
     * читается как точное значение.
     */
    fun referenceSummary(
        state: NavigateState,
        delta: ReferenceDelta,
        t: SearchStrings = SearchRu,
    ): String? {
        if (state.reference == null) return null
        val parts = mutableListOf<String>()
        val ratio = state.referenceRatio?.takeIf { it.isFinite() && it > 0.0 }
        val base = state.reference?.ratePerSecond?.let { t.navRefBase(num1(it)) }
        if (ratio != null && base != null) {
            parts += "${num2(ratio)}× $base"
        } else if (base != null) {
            parts += base
        }
        val comparison = state.referenceComparison
        if (comparison != null &&
            comparison.ratioLow.isFinite() &&
            comparison.ratioHigh.isFinite()
        ) {
            parts += t.navWhyInterval.lowercase() +
                " ${num2(comparison.ratioLow)}–${num2(comparison.ratioHigh)}×"
        } else if (delta == ReferenceDelta.Collecting) {
            parts += t.navDeltaCaptionCollecting
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /**
     * Что происходит, пока разница не подтверждена — одной фразой без чисел.
     *
     * Числа этой фразы (интервал и то, что он накрывает 1×) стоят в «Почему
     * такой вывод»: на рабочем экране они были главной строкой, хотя человек
     * в этот момент несёт прибор, а не разбирает статистику.
     */
    fun unresolvedNote(delta: ReferenceDelta, t: SearchStrings = SearchRu): String? =
        (delta as? ReferenceDelta.Unresolved)?.let { t.navUnresolvedNote }

    /**
     * Разбор вывода наведения — то, что раньше стояло на рабочем экране.
     *
     * Порядок отвечает на вопрос, с которым сюда приходят: **что сейчас → с
     * чем сравнивается → во сколько раз → насколько это надёжно → на чём
     * посчитано**. Ни одно число не потеряно при уборке экрана; исчезло только
     * требование разбирать статистику, стоя с прибором в руке.
     */
    fun whyLines(
        state: NavigateState,
        delta: ReferenceDelta,
        cps: Float?,
        t: SearchStrings = SearchRu,
    ): List<WhyLine> {
        val lines = mutableListOf<WhyLine>()
        if (cps != null) lines += WhyLine(t.navWhyNow, num1(cps.toDouble()))
        state.reference?.let { lines += WhyLine(t.navWhyReference, num1(it.ratePerSecond)) }
        state.referenceRatio?.takeIf { it.isFinite() && it > 0.0 }?.let {
            lines += WhyLine(t.navWhyRatio, "${num2(it)}×")
        }
        val comparison = state.referenceComparison
        if (comparison != null &&
            comparison.ratioLow.isFinite() &&
            comparison.ratioHigh.isFinite()
        ) {
            lines += WhyLine(
                label = t.navWhyInterval,
                value = "${num2(comparison.ratioLow)}–${num2(comparison.ratioHigh)}×",
                critical = if (delta is ReferenceDelta.Unresolved) t.navWhyIntervalNote else null,
            )
        }
        // Процент печатается ТОЛЬКО когда тест разрешил различие: «+31 %»,
        // неотличимое от шума, хуже отсутствия числа.
        percentLabel(delta)?.let { lines += WhyLine(t.navWhyDifference, it) }
        // Чувствительность этого измерения: какое превышение вообще было бы
        // замечено за набранное время. Без неё «различие не подтверждено» —
        // утверждение без границы: не подтверждено ПРИ КАКОЙ чувствительности?
        DetectionLimitsMath.of(state.fast, state.reference?.window)?.let { limits ->
            limits.detectableRatio?.takeIf { it.isFinite() }?.let { ratio ->
                lines += WhyLine(
                    label = t.navWhyDetectable,
                    value = "${num2(ratio)}×",
                    // Это КРИТИЧЕСКОЕ: без него отказ читается как «здесь
                    // ничего нет», хотя он говорит лишь «меньшего мы бы не
                    // увидели».
                    critical = t.navWhyDetectableNote,
                )
            }
        }
        state.local?.ratePerSecond?.let {
            lines += WhyLine(t.navWhyRecent, num1(it), note = t.navWhyRecentNote)
        }
        windowsNote(state, t)?.let { lines += WhyLine(t.navWhyWindows, it) }
        lines += WhyLine(
            label = t.navWhyCriterion,
            value = t.navWhyCriterionValue(percentOfAlpha()),
            note = t.navWhyCriterionNote,
        )
        return lines
    }

    /** α как процент — «1» для 0,01: порог называется числом, а не словом. */
    private fun percentOfAlpha(): String {
        val percent = NavigateEngine.ALPHA * 100.0
        return if (percent >= 1.0) {
            String.format(Locale.US, "%.0f", percent)
        } else {
            String.format(Locale.US, "%.1f", percent).replace('.', ',')
        }
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
