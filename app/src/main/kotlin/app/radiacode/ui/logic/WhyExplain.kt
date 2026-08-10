package app.radiacode.ui.logic

import app.radiacode.baseline.Admission
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineState
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.ExclusionSummary

/** One line of the «Почему?» sheet: what it is, its value, how sure we are. */
data class WhyLine(
    val label: String,
    val value: String,
    val evidence: Evidence,
    /** Optional half-sentence that keeps the line from being over-read. */
    val note: String? = null,
)

/** Everything the «Почему?» sheet needs; assembled by the screen, not queried here. */
data class WhyInput(
    val status: MonitorStatus,
    val baselineState: BaselineState?,
    val doseRateMicroSvH: Float?,
    val cps: Float?,
    val freshness: Freshness,
    val thresholds: AlarmThresholds,
    val admission: Admission,
    val exclusions: List<ExclusionSummary>,
    val unit: DoseUnitSetting,
    /** Profile the numbers belong to, «Дом / Спальня». */
    val profileName: String?,
    /** How the profile was chosen: «авто», «вручную», «место не подтверждено». */
    val contextWording: String,
)

/**
 * «Почему?» (spec §17): the exact data that produced the verdict on the main
 * screen — nothing summarised into a score, nothing hidden.
 *
 * The assembly is pure text so the wording can be asserted in tests: this
 * sheet is the app's only promise of explainability, and a silently changed
 * phrase here is a scientific regression, not a cosmetic one.
 *
 * Every line carries its [Evidence] level and levels are never merged: the
 * dose rate is MEASURED, the band is STATISTICALLY_DETECTED, the verdict
 * quotes both instead of collapsing them into «опасно/безопасно».
 */
object WhyExplain {

    fun verdict(status: MonitorStatus): String = statusHeadline(status)

    fun lines(input: WhyInput): List<WhyLine> = buildList {
        add(
            WhyLine(
                label = "Сейчас",
                value = input.doseRateMicroSvH
                    ?.let { DoseFormat.rateWithUnit(it, input.unit) } ?: "—",
                evidence = Evidence.MEASURED,
            ),
        )
        add(
            WhyLine(
                label = "Счёт",
                value = input.cps?.let { Uncertainty.cpsWithSigma(it) } ?: "—",
                evidence = Evidence.MEASURED,
                note = "1σ — Пуассон √(cps/τ), τ = 1 с",
            ),
        )
        add(
            WhyLine(
                label = "Поток данных",
                value = freshnessLabel(input.freshness),
                evidence = Evidence.MEASURED,
            ),
        )
        add(
            WhyLine(
                label = "Профиль",
                value = input.profileName ?: "без профиля",
                evidence = Evidence.MEASURED,
                note = input.contextWording,
            ),
        )

        when (val state = input.baselineState) {
            is BaselineState.Active -> addAll(baselineLines(state.baseline, input.unit))
            is BaselineState.Learning -> add(
                WhyLine(
                    label = "Обычный фон",
                    value = "ещё не собран",
                    evidence = Evidence.STATISTICALLY_DETECTED,
                    note = learningWording(state),
                ),
            )
            null -> add(
                WhyLine(
                    label = "Обычный фон",
                    value = "нет данных",
                    evidence = Evidence.STATISTICALLY_DETECTED,
                ),
            )
        }

        add(
            WhyLine(
                label = "Порог тревоги",
                value = "L1 ${DoseFormat.rateWithUnit(input.thresholds.l1MicroSvH, input.unit)} " +
                    "или ${factorLabel(input.thresholds.relativeFactor)}× обычного",
                evidence = Evidence.CALCULATED,
                note = heldWording(input.thresholds.persistenceSeconds.toLong()) +
                    " — иначе тревоги нет",
            ),
        )
        add(admissionLine(input.admission))
        add(exclusionLine(input.exclusions))
        add(
            WhyLine(
                label = "Спектральная аномалия",
                value = "не оценивается",
                evidence = Evidence.STATISTICALLY_DETECTED,
                note = "сравнение спектра с профилем появится позже — " +
                    "пока вывод строится только по мощности дозы и счёту",
            ),
        )
    }

    private fun baselineLines(baseline: Baseline, unit: DoseUnitSetting): List<WhyLine> = listOf(
        WhyLine(
            label = "Обычный диапазон",
            value = DoseFormat.range(baseline.doseLowMicroSvH, baseline.doseHighMicroSvH, unit) +
                " ${DoseFormat.rateUnitLabel(unit)}",
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = "P10–P90 профиля",
        ),
        WhyLine(
            label = "Медиана · P25–P75",
            value = DoseFormat.rate(baseline.doseMedianMicroSvH, unit) + " · " +
                DoseFormat.range(baseline.doseP25MicroSvH, baseline.doseP75MicroSvH, unit),
            evidence = Evidence.STATISTICALLY_DETECTED,
        ),
        WhyLine(
            label = "MAD",
            value = DoseFormat.rate(baseline.doseMadMicroSvH, unit) + " " +
                DoseFormat.rateUnitLabel(unit),
            evidence = Evidence.STATISTICALLY_DETECTED,
            note = "median(|xᵢ − медиана|) — разброс без допущения нормальности",
        ),
        WhyLine(
            label = "Собрано",
            value = durationWording(baseline.accumulatedSeconds) + " · " +
                "${baseline.bucketCount} минутных корзин",
            evidence = Evidence.CALCULATED,
            note = "только допущенные измерения",
        ),
    )

    private fun admissionLine(admission: Admission): WhyLine = when (admission) {
        Admission.Admitted -> WhyLine(
            label = "Сейчас учится",
            value = "да",
            evidence = Evidence.MEASURED,
            note = "текущие измерения входят в обычный фон профиля",
        )
        is Admission.Excluded -> WhyLine(
            label = "Сейчас учится",
            value = "нет",
            evidence = Evidence.MEASURED,
            note = admission.reason.label + " — измерения всё равно записываются",
        )
    }

    private fun exclusionLine(exclusions: List<ExclusionSummary>): WhyLine {
        if (exclusions.isEmpty()) {
            return WhyLine(
                label = "Исключено из обучения",
                value = "нет интервалов",
                evidence = Evidence.CALCULATED,
            )
        }
        val total = exclusions.sumOf { it.seconds }
        val top = exclusions.take(TOP_REASONS).joinToString(", ") {
            "${it.reason.label} (${durationWording(it.seconds)})"
        }
        return WhyLine(
            label = "Исключено из обучения",
            value = durationWording(total),
            evidence = Evidence.CALCULATED,
            note = top,
        )
    }

    private fun factorLabel(factor: Float): String =
        if (factor == factor.toInt().toFloat()) "${factor.toInt()}" else "$factor"

    private const val TOP_REASONS = 3
}

/** «45 с» / «12 мин» / «3,5 ч» — compact duration for explanation text. */
fun durationWording(seconds: Long): String = when {
    seconds < 60 -> "$seconds с"
    seconds < 3600 -> "${seconds / 60} мин"
    else -> {
        val hours = seconds / 3600.0
        // Whole hours read as integers, same convention as the baseline wording.
        val text = if (hours >= 10 || hours == Math.floor(hours)) {
            "${hours.toLong()}"
        } else {
            String.format(java.util.Locale.US, "%.1f", hours).replace('.', ',')
        }
        "$text ч"
    }
}
