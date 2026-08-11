package app.radiacode.ui.logic

import app.radiacode.analysis.RateComparisonResult
import app.radiacode.analysis.RateTest
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
    ): String = when (level) {
        SearchLevel.UNKNOWN ->
            if (!hasBackground) "Фон не записан — сравнивать не с чем" else "Ждём данные прибора"
        SearchLevel.BACKGROUND -> when (direction) {
            SearchDirection.RISING -> "На уровне записанного фона · счёт растёт"
            SearchDirection.FALLING -> "На уровне записанного фона · счёт снижается"
            else -> "На уровне записанного фона"
        }
        SearchLevel.POSSIBLE_CHANGE ->
            "Небольшое изменение — пока недостаточно данных"
        SearchLevel.CONFIRMED_EXCESS -> when (direction) {
            SearchDirection.RISING -> "Устойчивое превышение фонового счёта · продолжает расти"
            SearchDirection.FALLING -> "Устойчивое превышение фонового счёта · счёт снижается"
            else -> "Устойчивое превышение фонового счёта"
        }
        SearchLevel.CONFIRMED_DEFICIT ->
            "Счёт устойчиво ниже записанного фона"
    }

    /**
     * Half a sentence naming what the verdict was compared with, so the
     * headline is never read as an absolute statement about the place.
     */
    fun explanation(level: SearchLevel, comparison: RateComparisonResult?): String {
        val since = comparison?.let { ratioPhrase(it) }
        return when (level) {
            SearchLevel.UNKNOWN ->
                "Без записанного фона и живого потока данных сравнение невозможно."
            SearchLevel.BACKGROUND ->
                "Различие с записанным фоном не подтверждено статистикой счёта" +
                    (since?.let { ": $it" } ?: ".")
            SearchLevel.POSSIBLE_CHANGE ->
                "Отличие есть, но держится меньше ${seconds(SearchLadder.CONFIRM_MILLIS)} — " +
                    "по одному короткому окну вывод не делается."
            SearchLevel.CONFIRMED_EXCESS ->
                "Скорость счёта выше записанного фона дольше " +
                    "${seconds(SearchLadder.CONFIRM_MILLIS)}" +
                    (since?.let { ", $it" } ?: "") +
                    ". Это утверждение о скорости счёта, а не о дозе и не об изотопе."
            SearchLevel.CONFIRMED_DEFICIT ->
                "Скорость счёта ниже записанного фона дольше " +
                    "${seconds(SearchLadder.CONFIRM_MILLIS)}" +
                    (since?.let { ", $it" } ?: "") +
                    ". Так выглядит уход от источника или экранирование."
        }
    }

    /** «×1,8 к фону (95 % интервал 1,5–2,2)» — always with its denominator. */
    fun ratioPhrase(comparison: RateComparisonResult): String? {
        if (comparison.test == RateTest.NONE) return null
        val ratio = comparison.ratio
        if (!ratio.isFinite() || ratio <= 0.0) return null
        val level = (comparison.confidenceLevel * 100).roundToInt()
        val low = comparison.ratioLow
        val high = comparison.ratioHigh
        val interval = if (low.isFinite() && high.isFinite()) {
            " ($level % интервал ${num2(low)}–${num2(high)})"
        } else {
            ""
        }
        return "×${num2(ratio)} к записанному фону$interval"
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

    /** «↑ сигнал растёт» — the hot-and-cold half, deliberately not a verdict. */
    fun directionLabel(direction: SearchDirection): String? = when (direction) {
        SearchDirection.UNKNOWN -> null
        SearchDirection.STEADY -> "→ ровно"
        SearchDirection.RISING -> "↑ сигнал растёт"
        SearchDirection.FALLING -> "↓ сигнал снижается"
    }

    /** Line about short excursions that never reached the confirmation time. */
    fun spikeLine(spikes: List<SpikeMarker>): String? {
        if (spikes.isEmpty()) return null
        val peak = spikes.maxOf { it.peakRatio }
        return "короткие всплески: ${spikes.size} · сильнейший ×${num2(peak)} к фону — " +
            "не подтверждены длительностью, отмечены как события"
    }

    /**
     * The research layer (§4): every number the verdict stands on, including
     * the ones that make it weaker.
     */
    fun whyLines(input: SearchWhyInput): List<WhyLine> {
        val lines = ArrayList<WhyLine>(12)
        val comparison = input.comparison

        lines += WhyLine(
            label = "Скорость счёта сейчас",
            value = input.cps?.let { "${Uncertainty.num1(it)} с⁻¹" } ?: "нет данных",
            evidence = Evidence.MEASURED,
            note = input.cps?.let { Uncertainty.cpsSigmaLine(it) },
        )

        val background = input.background
        lines += WhyLine(
            label = "Записанный фон",
            value = background?.let { "${Uncertainty.num1(it.cps)} с⁻¹" } ?: "не записан",
            evidence = Evidence.MEASURED,
            note = background?.let {
                "±${Uncertainty.num1(it.sigma)} с⁻¹ · ${it.window.samples} показаний · " +
                    "экспозиция ${num1(it.window.seconds)} с · качество: ${it.quality.label}"
            },
        )

        if (comparison == null) {
            lines += WhyLine(
                label = "Сравнение",
                value = "не выполнялось",
                evidence = Evidence.STATISTICALLY_DETECTED,
                note = if (background == null) {
                    "нет записанного фона — сравнивать не с чем"
                } else {
                    "в решающем окне нет показаний: поток данных прерван"
                },
            )
            return lines
        }

        lines += WhyLine(
            label = "Окно решения",
            value = "${num1(comparison.current.seconds)} с",
            evidence = Evidence.MEASURED,
            note = "${num0(comparison.current.counts)} импульсов в окне " +
                "(${comparison.current.samples} показаний)" +
                gapNote(comparison.current.gapSeconds),
        )
        lines += WhyLine(
            label = "Окно фона",
            value = "${num1(comparison.background.seconds)} с",
            evidence = Evidence.MEASURED,
            note = "${num0(comparison.background.counts)} импульсов" +
                gapNote(comparison.background.gapSeconds),
        )
        lines += WhyLine(
            label = "Разность",
            value = signed(comparison.differencePerSecond) + " с⁻¹",
            evidence = Evidence.CALCULATED,
            note = "±${num2(comparison.differenceSigma)} с⁻¹ (1σ) · " +
                (deltaPercent(comparison)?.let { "${signedPercent(it)} к записанному фону" } ?: ""),
        )
        ratioPhrase(comparison)?.let {
            lines += WhyLine(
                label = "Отношение скоростей",
                value = "×${num2(comparison.ratio)}",
                evidence = Evidence.STATISTICALLY_DETECTED,
                note = it,
            )
        }
        lines += WhyLine(
            label = "Критерий",
            value = shortTestName(comparison.test),
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = comparison.test.label + " · модель неопределённости: " +
                comparison.model.label,
        )
        lines += WhyLine(
            label = "Значимость",
            value = pLabel(comparison.pValue),
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = buildString {
                append("порог отличия α = ${num2(SearchLadder.ALPHA)}")
                comparison.zEquivalent?.let { append(" · z = ${num2(it)}") }
                append(" · p — вероятность увидеть такое различие, если скорости равны")
            },
        )
        lines += WhyLine(
            label = "Разброс показаний",
            value = comparison.fanoFactor?.let { "F = ${num2(it)}" } ?: "не оценивался",
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = comparison.dispersion.label +
                if (comparison.dispersionFactor > 1.0) {
                    " · счёт поделён на φ = ${num2(comparison.dispersionFactor)}"
                } else {
                    ""
                },
        )
        lines += WhyLine(
            label = "Длительность отклонения",
            value = input.heldMillis?.let { "${seconds(it)}" } ?: "нет",
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = "подтверждение требует ${seconds(SearchLadder.CONFIRM_MILLIS)} подряд, " +
                "снятие — ${seconds(SearchLadder.RELEASE_MILLIS)} согласия",
        )
        lines += WhyLine(
            label = "Поток данных",
            value = if (input.streamFresh) "идёт" else "прерван",
            evidence = Evidence.MEASURED,
            note = "окна строятся по времени прибора; пропуски укорачивают " +
                "экспозицию, а не растягивают последнее показание",
        )
        lines += WhyLine(
            label = "Спектральная форма",
            value = "не оценивается",
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = "этот экран сравнивает только скорость счёта; изотоп по одному " +
                "росту счёта не определяется — это вкладка «Спектр»",
        )
        return lines
    }

    private fun gapNote(gapSeconds: Double): String =
        if (gapSeconds > 0.5) " · пропуск потока ${num1(gapSeconds)} с" else ""

    private fun shortTestName(test: RateTest): String = when (test) {
        RateTest.CONDITIONAL_BINOMIAL -> "условный биномиальный"
        RateTest.QUASI_BINOMIAL -> "квазибиномиальный"
        RateTest.NONE -> "нет"
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

    fun seconds(millis: Long): String = "${millis / 1000} с"

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
