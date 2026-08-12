package app.radiacode.ui.logic

import app.radiacode.analysis.FingerprintComparison
import app.radiacode.analysis.FingerprintDimension
import app.radiacode.analysis.FingerprintState
import app.radiacode.baseline.Admission
import app.radiacode.ui.text.RuStrings
import app.radiacode.ui.text.Strings
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineAdmission
import app.radiacode.baseline.BaselineState
import app.radiacode.data.DoseUnitSetting

/**
 * Colour of a statement (why-spec §14). Colour codes **state**, never a
 * medical or sanitary conclusion: green means «внутри исторического диапазона
 * этого профиля», not «безопасно».
 */
enum class WhyTone { OK, ATTENTION, ALARM, UNKNOWN }

/** Where the current value sits inside the profile's historical band (§2). */
data class WhyScale(
    val lowLabel: String,
    val highLabel: String,
    val currentLabel: String,
    /** 0 = at P10, 1 = at P90; outside the band it clamps and [outside] is set. */
    val position: Float,
    val outside: Boolean,
) {
    companion object {
        fun of(
            currentMicroSvH: Float,
            baseline: Baseline,
            unit: DoseUnitSetting,
        ): WhyScale {
            val low = baseline.doseLowMicroSvH
            val high = baseline.doseHighMicroSvH
            val span = (high - low).takeIf { it > 0f }
            // A degenerate band (P10 == P90 — a very flat history) has no
            // inside to place a dot in, so the position follows the side the
            // value is on rather than sitting in a middle that means nothing.
            val raw = when {
                span != null -> (currentMicroSvH - low) / span
                currentMicroSvH > high -> 1f
                currentMicroSvH < low -> 0f
                else -> 0.5f
            }
            return WhyScale(
                lowLabel = DoseFormat.rate(low, unit),
                highLabel = DoseFormat.rate(high, unit),
                currentLabel = DoseFormat.rate(currentMicroSvH, unit),
                position = raw.coerceIn(0f, 1f),
                outside = currentMicroSvH < low || currentMicroSvH > high,
            )
        }
    }
}

/** One block of the sheet; [advanced] blocks live behind «Показать расчёты». */
data class WhySection(
    val title: String,
    val lines: List<WhyLine>,
    /** Paragraph under the numbers that keeps them from being over-read. */
    val note: String? = null,
    val advanced: Boolean = false,
    val tone: WhyTone = WhyTone.UNKNOWN,
)

/**
 * The whole «Почему такой вывод» sheet as data (why-spec §13, §18).
 *
 * The order is the audit trail of a human conclusion: **вывод → непосредственное
 * доказательство → статистическая основа → состояние профиля → критерии
 * алгоритма**. Numbers that only a researcher needs (MAD, buckets, thresholds)
 * are marked [WhySection.advanced] and stay folded until asked for — the spec
 * is explicit that the screen must not open with MAD and quarantine.
 */
data class WhyReport(
    val status: String,
    val tone: WhyTone,
    /** One human sentence: what was compared with what. */
    val sentence: String,
    /** «Сейчас» — the current reading, formatted; null when there is none. */
    val nowValue: String?,
    /** «Обычно здесь» — the historical band of this profile. */
    val usualValue: String?,
    val scale: WhyScale?,
    val sections: List<WhySection>,
    val legend: String,
) {
    val hasAdvanced: Boolean get() = sections.any { it.advanced }
}

/**
 * Assembles [WhyReport] from what the Монитор already knows.
 *
 * Everything here is pure text and pure arithmetic so the wording can be
 * asserted in tests: this sheet is the app's only promise of explainability,
 * and a silently changed phrase is a scientific regression, not a cosmetic one
 * (why-spec §16, §17).
 */
object WhyReportBuilder {

    /**
     * Каталог языка держится в поле на время сборки отчёта: у сборщика два
     * десятка приватных функций, и протаскивать параметр через каждую значило
     * бы переписать файл ради одного и того же аргумента. Сборка синхронная и
     * однопоточная (её вызывает композиция или отчёт), поэтому поле безопасно.
     */
    private var s: Strings = RuStrings

    /** Пояснение меток достоверности — на языке интерфейса. */
    fun legend(strings: Strings = RuStrings): String = strings.evidenceLegend

    fun build(input: WhyInput, strings: Strings = RuStrings): WhyReport {
        s = strings
        val baseline = (input.baselineState as? BaselineState.Active)?.baseline
        val unit = input.unit
        val current = input.doseRateMicroSvH
        return WhyReport(
            status = statusHeadline(input.status),
            tone = toneOf(input.status),
            sentence = WhyExplain.verdictExplanation(input.status),
            nowValue = current?.let { DoseFormat.rateWithUnit(it, unit) },
            usualValue = baseline?.let {
                "${DoseFormat.range(it.doseLowMicroSvH, it.doseHighMicroSvH, unit)} " +
                    DoseFormat.rateUnitLabel(unit)
            },
            scale = if (baseline != null && current != null) {
                WhyScale.of(current, baseline, unit)
            } else {
                null
            },
            sections = buildList {
                add(nowSection(input))
                add(comparisonSection(input, baseline))
                if (baseline != null) add(statisticsSection(baseline, unit))
                add(stateSection(input))
                add(criteriaSection(input))
                add(spectralSection(input.fingerprint))
            },
            legend = legend(s),
        )
    }

    /** §14: colour is the state of the comparison, never a safety verdict. */
    fun toneOf(status: MonitorStatus): WhyTone = when (status) {
        MonitorStatus.Unknown -> WhyTone.UNKNOWN
        is MonitorStatus.Fixed -> if (status.above) WhyTone.ATTENTION else WhyTone.OK
        is MonitorStatus.Usual -> WhyTone.OK
        is MonitorStatus.AboveUsual -> WhyTone.ATTENTION
        is MonitorStatus.AboveThreshold -> WhyTone.ATTENTION
        is MonitorStatus.Alert -> WhyTone.ALARM
    }

    // ------------------------------------------------------------- §3 «Сейчас»

    private fun nowSection(input: WhyInput): WhySection = WhySection(
        title = s.nowSection,
        lines = buildList {
            add(
                WhyLine(
                    label = s.doseRate,
                    value = input.doseRateMicroSvH
                        ?.let { DoseFormat.rateWithUnit(it, input.unit) } ?: "—",
                    evidence = Evidence.MEASURED,
                ),
            )
            add(
                WhyLine(
                    label = s.countRate,
                    value = input.cps?.let { Uncertainty.cpsWithSigma(it) } ?: "—",
                    evidence = Evidence.MEASURED,
                    note = s.poissonNote,
                ),
            )
            add(
                WhyLine(
                    label = s.dataSection,
                    value = freshnessLabel(input.freshness),
                    evidence = Evidence.MEASURED,
                ),
            )
            add(
                WhyLine(
                    label = s.profile,
                    value = input.profileName ?: s.outsideProfile,
                    evidence = Evidence.MEASURED,
                    note = input.contextWording,
                ),
            )
        },
    )

    // ------------------------------------------- §4 «Сравнение с профилем»

    private fun comparisonSection(input: WhyInput, baseline: Baseline?): WhySection {
        if (baseline == null) {
            val learning = input.baselineState as? BaselineState.Learning
            return WhySection(
                title = s.comparisonSection,
                lines = listOf(
                    WhyLine(
                        label = s.historicalRange,
                        value = s.notCollectedYet,
                        evidence = Evidence.STATISTICALLY_DETECTED,
                        note = learning?.let { shortfallWording(it) },
                    ),
                    WhyLine(
                        label = s.comparisonRuns,
                        value = s.withThresholdL1(
                            DoseFormat.rateWithUnit(input.thresholds.l1MicroSvH, input.unit),
                        ),
                        evidence = Evidence.CALCULATED,
                    ),
                ),
                note = s.thresholdIsNotSafety,
            )
        }
        val unit = input.unit
        val current = input.doseRateMicroSvH
        return WhySection(
            title = s.comparisonSection,
            lines = listOf(
                WhyLine(
                    label = "P10–P90",
                    value = DoseFormat.range(
                        baseline.doseLowMicroSvH,
                        baseline.doseHighMicroSvH,
                        unit,
                    ) + " " + DoseFormat.rateUnitLabel(unit),
                    evidence = Evidence.STATISTICALLY_DETECTED,
                ),
                WhyLine(
                    label = s.currentValue,
                    value = current?.let { DoseFormat.rateWithUnit(it, unit) } ?: "—",
                    evidence = Evidence.MEASURED,
                ),
                WhyLine(
                    label = s.position,
                    value = positionWording(current, baseline),
                    evidence = Evidence.CALCULATED,
                ),
            ),
            note = s.bandExplained,
        )
    }

    private fun positionWording(current: Float?, baseline: Baseline): String = when {
        current == null -> "—"
        current < baseline.doseLowMicroSvH -> s.belowP10
        current > baseline.doseHighMicroSvH -> s.aboveP90
        else -> s.insideBand
    }

    // ----------------------------------------- §5 «Статистика профиля»

    private fun statisticsSection(baseline: Baseline, unit: DoseUnitSetting): WhySection =
        WhySection(
            title = s.profileStatistics,
            advanced = true,
            lines = listOf(
                WhyLine(
                    label = s.median,
                    value = DoseFormat.rateWithUnit(baseline.doseMedianMicroSvH, unit),
                    evidence = Evidence.STATISTICALLY_DETECTED,
                ),
                WhyLine(
                    label = "P25–P75",
                    value = DoseFormat.range(
                        baseline.doseP25MicroSvH,
                        baseline.doseP75MicroSvH,
                        unit,
                    ),
                    evidence = Evidence.STATISTICALLY_DETECTED,
                ),
                WhyLine(
                    label = "MAD",
                    value = DoseFormat.rateWithUnit(baseline.doseMadMicroSvH, unit),
                    evidence = Evidence.STATISTICALLY_DETECTED,
                    note = s.madNote,
                ),
                WhyLine(
                    label = "P10–P90",
                    value = DoseFormat.range(
                        baseline.doseLowMicroSvH,
                        baseline.doseHighMicroSvH,
                        unit,
                    ),
                    evidence = Evidence.STATISTICALLY_DETECTED,
                ),
                WhyLine(
                    label = s.usableData,
                    value = durationWording(baseline.accumulatedSeconds),
                    evidence = Evidence.CALCULATED,
                ),
                WhyLine(
                    label = s.minuteBuckets,
                    value = "${baseline.bucketCount}",
                    evidence = Evidence.CALCULATED,
                    note = s.honestN,
                ),
            ),
        )

    // ------------------------------- §6 «Состояние статистики профиля»

    private fun stateSection(input: WhyInput): WhySection {
        val learning = input.baselineState as? BaselineState.Learning
        val updating = input.admission is Admission.Admitted
        val headline = when {
            learning != null -> s.notEnoughData
            updating -> s.updating
            else -> s.temporarilyNotUpdating
        }
        val explanation = when {
            learning != null -> shortfallWording(learning)
            updating -> s.updatingNote
            else -> s.notUpdatingNote
        }
        val exclusions = input.exclusions
        val lines = buildList {
            add(
                WhyLine(
                    label = s.state,
                    value = headline,
                    evidence = Evidence.CALCULATED,
                    note = (input.admission as? Admission.Excluded)?.reason?.label,
                ),
            )
            if (exclusions.isNotEmpty()) {
                add(
                    WhyLine(
                        label = s.excludedFromStatistics,
                        value = durationWording(exclusions.sumOf { it.seconds }),
                        evidence = Evidence.CALCULATED,
                    ),
                )
                // Reasons as separate lines: «карантин» without a reason is not
                // an explanation (§6).
                exclusions.sortedByDescending { it.seconds }.forEach {
                    add(
                        WhyLine(
                            label = it.reason.label,
                            value = durationWording(it.seconds),
                            evidence = Evidence.CALCULATED,
                        ),
                    )
                }
            }
        }
        return WhySection(
            // Not «Статистика профиля»: that heading belongs to the numbers
            // above, and two identical headings in one sheet is one heading
            // too many.
            title = s.statisticsState,
            lines = lines,
            // The quarantine paragraph is shown where it explains something —
            // otherwise it is a warning about a situation the user is not in.
            note = if (exclusions.isEmpty() && updating) {
                explanation
            } else {
                explanation + " " + quarantineWording(s)
            },
            tone = when {
                learning != null -> WhyTone.ATTENTION
                updating -> WhyTone.OK
                else -> WhyTone.ATTENTION
            },
        )
    }

    /**
     * The user-facing version of the quarantine rule (§6). The technical word
     * stays out of the sheet: what matters is *why* the app refuses to absorb a
     * deviation into the usual range.
     */
    fun quarantineWording(strings: Strings = RuStrings): String = strings.quarantineNote

    // ---------------------------------- §8 «Как обнаруживается отклонение»

    private fun criteriaSection(input: WhyInput): WhySection {
        val thresholds = input.thresholds
        val unit = input.unit
        return WhySection(
            title = s.howDetected,
            advanced = true,
            lines = listOf(
                WhyLine(
                    label = s.absoluteThresholdL1,
                    value = DoseFormat.rateWithUnit(thresholds.l1MicroSvH, unit),
                    evidence = Evidence.CALCULATED,
                ),
                WhyLine(
                    label = s.relativeCriterion,
                    value = s.timesProfileP90(factorLabel(thresholds.relativeFactor)),
                    evidence = Evidence.CALCULATED,
                ),
                WhyLine(
                    label = s.minimumDuration,
                    value = durationWording(thresholds.persistenceSeconds.toLong()),
                    evidence = Evidence.CALCULATED,
                    note = s.shorterNotAnnounced,
                ),
                WhyLine(
                    label = s.returnCriterion,
                    value = s.backBelowThreshold,
                    evidence = Evidence.CALCULATED,
                ),
                WhyLine(
                    label = s.exclusionAfterEvent,
                    value = durationWording(BaselineAdmission.QUARANTINE_MILLIS / 1000),
                    evidence = Evidence.CALCULATED,
                    note = s.fromEndOfDeviation,
                ),
            ),
            note = s.criteriaNote,
        )
    }

    // ------------------------------------------- §9 «Спектральное сравнение»

    /**
     * Четыре состояния, а не два (why-spec §9): «не оценивалось» и «изменений
     * не обнаружено» — принципиально разные утверждения, и подменять первое
     * вторым запрещено.
     */
    private fun spectralSection(comparison: FingerprintComparison?): WhySection {
        val shape = comparison?.of(FingerprintDimension.SPECTRUM)
        val value = when (shape?.state) {
            null, FingerprintState.NOT_EVALUATED -> s.notEvaluated
            FingerprintState.NOT_ENOUGH_DATA -> s.notEnoughStatistics
            FingerprintState.SAME -> s.noChangeDetected
            FingerprintState.CHANGED -> s.changeDetected
        }
        val note = when (shape?.state) {
            null, FingerprintState.NOT_EVALUATED ->
                s.spectralNoReference
            FingerprintState.NOT_ENOUGH_DATA ->
                s.spectralTooLittle(shape.detail)
            else ->
                s.spectralCompared(shape.detail)
        }
        return WhySection(
            title = s.spectralComparison,
            lines = listOf(
                WhyLine(
                    label = s.state,
                    value = value,
                    evidence = Evidence.STATISTICALLY_DETECTED,
                ),
            ),
            note = note,
            tone = if (shape?.state == FingerprintState.CHANGED) {
                WhyTone.ATTENTION
            } else {
                WhyTone.UNKNOWN
            },
        )
    }

    private fun factorLabel(factor: Float): String =
        if (factor == factor.toInt().toFloat()) "${factor.toInt()}" else "$factor"
}

/**
 * «2 ч 14 мин из минимально необходимых 3 ч» (why-spec §6).
 *
 * Deliberately not the Монитор's own [learningWording]: that one says «изучаю
 * обычный фон», and this sheet may not suggest a model that is being trained
 * (§6 — «не использовать слова "обучение", "учится"»).
 */
fun shortfallWording(state: BaselineState.Learning): String =
    "${durationWording(state.accumulatedSeconds)} из минимально необходимых " +
        durationWording(state.requiredSeconds)
