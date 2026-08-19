package app.alpha.ui.logic

import app.alpha.analysis.FingerprintComparison
import app.alpha.analysis.FingerprintDimension
import app.alpha.analysis.FingerprintState
import app.alpha.baseline.Admission
import app.alpha.baseline.wording
import app.alpha.ui.text.MonitorCatalogue
import app.alpha.ui.text.MonitorRu
import app.alpha.ui.text.MonitorStrings
import app.alpha.ui.text.RuStrings
import app.alpha.ui.text.Strings
import app.alpha.baseline.Baseline
import app.alpha.baseline.BaselineAdmission
import app.alpha.baseline.BaselineState
import app.alpha.data.DoseUnitSetting
import app.alpha.ui.text.uiDecimal

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
    /** Медиана профиля — средняя засечка шкалы «P10 · медиана · P90». */
    val medianLabel: String,
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
                medianLabel = DoseFormat.rate(baseline.doseMedianMicroSvH, unit),
                currentLabel = DoseFormat.rate(currentMicroSvH, unit),
                position = raw.coerceIn(0f, 1f),
                outside = currentMicroSvH < low || currentMicroSvH > high,
            )
        }
    }
}

/**
 * Глубина, на которой лежит блок (14.md: «математику не убирать, а разложить
 * по глубине»).
 *
 *  - [PLAIN] — что я вижу и что это значит;
 *  - [METHOD] — медиана, P10–P90, статистика профиля, принцип сравнения;
 *  - [EXPERT] — MAD, χ², z, число корзин, формулы, точные пороги.
 *
 * Уровни — это порядок раскрытия, а не порядок важности: ни одно число не
 * исчезает, оно переезжает глубже.
 */
enum class WhyLevel { PLAIN, METHOD, EXPERT }

/** One block of the sheet; см. [WhyLevel] — на каком раскрытии он показан. */
data class WhySection(
    val title: String,
    val lines: List<WhyLine>,
    /** Пояснение секции: прячется вместе со всеми пояснениями. */
    val note: String? = null,
    /** Нехватка данных и отказ метода — видно всегда. */
    val critical: String? = null,
    val level: WhyLevel = WhyLevel.PLAIN,
    val tone: WhyTone = WhyTone.UNKNOWN,
) {
    /** Всё, что лежит за первым раскрытием, — «показать методику и расчёты». */
    val advanced: Boolean get() = level != WhyLevel.PLAIN
}

/**
 * The whole «Почему такой вывод» sheet as data (why-spec §13, §18).
 *
 * The order is the audit trail of a human conclusion: **вывод → непосредственное
 * доказательство → статистическая основа → состояние профиля → критерии
 * алгоритма**, разложенный по трём уровням глубины ([WhyLevel], 14.md):
 * человеческий → научный → экспертный. Ни одно число не пропадает при
 * переезде вглубь: MAD, число корзин, χ² и z по-прежнему на экране, но за
 * двумя раскрытиями, а не в первой же строке.
 */
/** Сбор фона места: сколько уже собрано и сколько нужно. */
data class WhyLearning(
    val collected: String,
    val required: String,
    /** Доля от нуля до единицы — для полосы прогресса. */
    val fraction: Float,
)

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
    /**
     * Первый уровень справки (§4 ТЗ): что сейчас, сколько собрано фона, чем
     * кончилось сравнение и сколько времени в фон не пошло. Разбор методики —
     * уровнем ниже.
     */
    val learning: WhyLearning?,
    val comparison: String,
    val excluded: String?,
    val sections: List<WhySection>,
    val legend: String,
    /**
     * Обязательная оговорка первого уровня: вывод описывает отличие от
     * СВОЕГО обычного фона, а не радиационную безопасность.
     */
    val caveat: String = "",
) {
    fun sections(level: WhyLevel): List<WhySection> = sections.filter { it.level == level }

    val hasAdvanced: Boolean get() = sections.any { it.level == WhyLevel.METHOD }
    val hasExpert: Boolean get() = sections.any { it.level == WhyLevel.EXPERT }
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

    /** Каталог этой области — рядом с общим, по тому же языку. */
    private var m: MonitorStrings = MonitorRu

    /** Пояснение меток достоверности — на языке интерфейса. */
    fun legend(strings: Strings = RuStrings): String = strings.evidenceLegend

    fun build(input: WhyInput, strings: Strings = RuStrings): WhyReport {
        s = strings
        m = MonitorCatalogue.of(strings.language)
        val baseline = (input.baselineState as? BaselineState.Active)?.baseline
        val unit = input.unit
        val current = input.doseRateMicroSvH
        return WhyReport(
            status = statusHeadline(input.status, s),
            tone = toneOf(input.status),
            sentence = WhyExplain.verdictExplanation(input.status, m),
            nowValue = current?.let { DoseFormat.rateWithUnit(it, unit, s = s) },
            usualValue = baseline?.let {
                "${DoseFormat.range(it.doseLowMicroSvH, it.doseHighMicroSvH, unit)} " +
                    DoseFormat.rateUnitLabel(unit, s = s)
            },
            scale = if (baseline != null && current != null) {
                WhyScale.of(current, baseline, unit)
            } else {
                null
            },
            learning = (input.baselineState as? BaselineState.Learning)?.let {
                WhyLearning(
                    collected = m.hoursShort(hours(it.accumulatedSeconds)),
                    required = m.hoursShort(hours(it.requiredSeconds)),
                    fraction = if (it.requiredSeconds > 0) {
                        (it.accumulatedSeconds.toFloat() / it.requiredSeconds).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                )
            },
            // Сравнение — отдельный блок со своим заголовком: «Недостаточно
            // данных» в строке «Сейчас» читалось как ЗНАЧЕНИЕ измерения.
            comparison = if (baseline == null) {
                m.comparisonNotEnough
            } else {
                statusHeadline(input.status, s)
            },
            excluded = exclusionSummary(input),
            sections = buildList {
                // Первый уровень: что сейчас, с чем сравнили, сколько данных,
                // что со спектром — и в каком состоянии сама статистика.
                add(nowSection(input))
                add(comparisonSection(input, baseline))
                if (baseline != null) add(dataVolumeSection(baseline))
                add(stateSection(input))
                add(spectralSection(input.fingerprint))
                // Второй: что исключено, статистика профиля, критерии алгоритма.
                exclusionsSection(input)?.let { add(it) }
                if (baseline != null) add(statisticsSection(input, baseline, unit))
                add(criteriaSection(input))
                // Третий: MAD, корзины, формулы, χ² и z.
                add(calculationsSection(input, baseline, unit))
            },
            legend = legend(s),
            caveat = s.notASafetyConclusion,
        )
    }

    /**
     * Одна строка вместо трёх повторов: «12 мин · Поиск или эксперимент» —
     * сколько времени и по какому поводу.
     */
    private fun exclusionSummary(input: WhyInput): String? {
        val exclusions = input.exclusions
        val current = (input.admission as? Admission.Excluded)?.reason
        val seconds = exclusions.sumOf { it.seconds }
        val reason = exclusions.maxByOrNull { it.seconds }?.reason ?: current ?: return null
        if (seconds <= 0L && current == null) return null
        return m.excludedLine(
            duration = durationWording(seconds.coerceAtLeast(0L), m),
            reason = reason.wording(m),
        )
    }

    /** «0,9» / «3» — часы для строки прогресса. */
    private fun hours(seconds: Long): String {
        val value = seconds / 3600.0
        return if (value >= 10 || value == Math.floor(value)) {
            "${value.toLong()}"
        } else {
            String.format(java.util.Locale.US, "%.1f", value).uiDecimal()
        }
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
                        ?.let { DoseFormat.rateWithUnit(it, input.unit, s = s) } ?: "—",
                    // Чья это погрешность — говорится там же, где её видно:
                    // ± приходит ОТ ПРИБОРА и относится к этому показанию.
                    critical = s.deviceErrorNote,
                ),
            )
            add(
                WhyLine(
                    label = s.countRate,
                    value = input.cps?.let { "${Uncertainty.cpsWithSigma(it)} ${s.cpsUnit}" }
                        ?: "—",
                    critical = s.countIsNotDose,
                ),
            )
            add(
                WhyLine(
                    label = s.dataSection,
                    value = freshnessLabel(input.freshness),
                ),
            )
            add(
                WhyLine(
                    label = s.profile,
                    value = input.profileName ?: s.outsideProfile,
                    critical = input.contextWording,
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
                        critical = learning?.let { shortfallWording(it, m) },
                    ),
                    WhyLine(
                        label = s.comparisonRuns,
                        value = s.withThresholdL1(
                            DoseFormat.rateWithUnit(input.thresholds.l1MicroSvH, input.unit, s = s),
                        ),
                    ),
                ),
                critical = s.thresholdIsNotSafety,
            )
        }
        val unit = input.unit
        val current = input.doseRateMicroSvH
        return WhySection(
            title = s.comparisonSection,
            lines = listOf(
                WhyLine(
                    // Человеческое имя диапазона: P10–P90 стоит на шкале и во
                    // втором уровне, где рядом объяснено, что это такое.
                    label = s.usualRangeHere,
                    value = DoseFormat.range(
                        baseline.doseLowMicroSvH,
                        baseline.doseHighMicroSvH,
                        unit,
                    ) + " " + DoseFormat.rateUnitLabel(unit, s = s),
                ),
                WhyLine(
                    label = s.currentValue,
                    value = current?.let { DoseFormat.rateWithUnit(it, unit, s = s) } ?: "—",
                ),
                WhyLine(
                    label = s.position,
                    value = positionWording(current, baseline),
                ),
            ),
            note = s.bandExplained,
        )
    }

    private fun positionWording(current: Float?, baseline: Baseline): String = when {
        current == null -> "—"
        current < baseline.doseLowMicroSvH -> s.belowUsualRange
        current > baseline.doseHighMicroSvH -> s.aboveUsualRange
        else -> s.insideUsualRange
    }

    /** То же положение P-нотацией — для второго уровня. */
    private fun positionNotation(current: Float?, baseline: Baseline): String = when {
        current == null -> "—"
        current < baseline.doseLowMicroSvH -> s.belowP10
        current > baseline.doseHighMicroSvH -> s.aboveP90
        else -> s.insideBand
    }

    // ------------------------------------------------- §4a «Сколько данных»

    /**
     * Объём данных — первый уровень: «достаточно ли этого, чтобы сравнивать»
     * спрашивают раньше, чем «как посчитана медиана».
     */
    private fun dataVolumeSection(baseline: Baseline): WhySection = WhySection(
        title = s.dataVolume,
        lines = listOf(
            WhyLine(
                label = s.usedForComparison,
                value = durationWording(baseline.accumulatedSeconds, m),
                note = s.suitableMeasurements,
            ),
        ),
    )

    // ----------------------------------------- §5 «Статистика профиля»

    private fun statisticsSection(
        input: WhyInput,
        baseline: Baseline,
        unit: DoseUnitSetting,
    ): WhySection =
        WhySection(
            title = s.profileStatistics,
            level = WhyLevel.METHOD,
            lines = listOf(
                WhyLine(
                    label = s.median,
                    value = DoseFormat.rateWithUnit(baseline.doseMedianMicroSvH, unit, s = s),
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
                    label = "P10–P90",
                    value = DoseFormat.range(
                        baseline.doseLowMicroSvH,
                        baseline.doseHighMicroSvH,
                        unit,
                    ),
                    evidence = Evidence.STATISTICALLY_DETECTED,
                ),
                // То же утверждение, что на первом уровне, но точной нотацией:
                // «внутри P10–P90» вместо «внутри обычного диапазона».
                WhyLine(
                    label = s.position,
                    value = positionNotation(input.doseRateMicroSvH, baseline),
                    evidence = Evidence.CALCULATED,
                ),
                // «Пригодных данных» ничего не объясняло без критерия
                // пригодности — критерий стоит здесь же, строкой ниже (§3).
                WhyLine(
                    label = s.usableData,
                    value = durationWording(baseline.accumulatedSeconds, m),
                    evidence = Evidence.CALCULATED,
                    note = s.usableDataNote,
                ),
                // n называется тем, что оно есть: показаниями прибора. Равным
                // секундам экспозиции оно не является — при пропусках их меньше.
                WhyLine(
                    label = s.measurementsCount,
                    value = SpectrumFormat.groupThousands(baseline.sampleCount),
                    evidence = Evidence.CALCULATED,
                    note = s.measurementsCountNote,
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
        // §12: на первом уровне одно состояние и одно «зачем». Внутренние
        // причины лежат уровнем глубже — в «Что исключено из статистики».
        val explanation = when {
            learning != null -> shortfallWording(learning, m)
            updating -> s.updatingNote
            else -> s.notUpdatingNote
        }
        return WhySection(
            // Not «Статистика профиля»: that heading belongs to the numbers
            // above, and two identical headings in one sheet is one heading
            // too many.
            title = s.statisticsState,
            lines = listOf(WhyLine(label = s.state, value = headline)),
            note = explanation,
            tone = when {
                learning != null -> WhyTone.ATTENTION
                updating -> WhyTone.OK
                else -> WhyTone.ATTENTION
            },
        )
    }

    /**
     * Второй уровень §12: что исключено, по каким причинам и надолго ли.
     */
    private fun exclusionsSection(input: WhyInput): WhySection? {
        val exclusions = input.exclusions
        val current = (input.admission as? Admission.Excluded)?.reason
        if (exclusions.isEmpty() && current == null) return null
        val lines = buildList {
            if (current != null) {
                add(
                    WhyLine(
                        label = s.excludedNow,
                        value = current.wording(m),
                        evidence = Evidence.CALCULATED,
                    ),
                )
            }
            if (exclusions.isNotEmpty()) {
                add(
                    WhyLine(
                        label = s.excludedFromStatistics,
                        value = durationWording(exclusions.sumOf { it.seconds }, m),
                        evidence = Evidence.CALCULATED,
                    ),
                )
                // Reasons as separate lines: a bare total says nothing about
                // what the statistics refused, or why (§6).
                exclusions.sortedByDescending { it.seconds }.forEach {
                    add(
                        WhyLine(
                            label = it.reason.wording(m),
                            value = durationWording(it.seconds, m),
                            evidence = Evidence.CALCULATED,
                        ),
                    )
                }
            }
        }
        return WhySection(
            title = s.excludedSection,
            level = WhyLevel.METHOD,
            lines = lines,
            note = quarantineWording(s),
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
            level = WhyLevel.METHOD,
            lines = listOf(
                WhyLine(
                    label = s.absoluteThresholdL1,
                    value = DoseFormat.rateWithUnit(thresholds.l1MicroSvH, unit, s = s),
                    evidence = Evidence.CALCULATED,
                ),
                WhyLine(
                    label = s.relativeCriterion,
                    value = s.timesProfileP90(factorLabel(thresholds.relativeFactor)),
                    evidence = Evidence.CALCULATED,
                ),
                WhyLine(
                    label = s.minimumDuration,
                    value = durationWording(thresholds.persistenceSeconds.toLong(), m),
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
                    value = durationWording(BaselineAdmission.QUARANTINE_MILLIS / 1000, m),
                    evidence = Evidence.CALCULATED,
                    note = s.fromEndOfDeviation,
                ),
            ),
            note = s.criteriaNote,
        )
    }

    // ------------------------------- третий уровень: «Расчёты и формулы»

    /**
     * Экспертный уровень (14.md): MAD с формулой, число корзин, пуассоновская
     * 1σ, бюджет неопределённости дозы и статистика сравнения формы спектра
     * (χ² по корзинам и z). Ни одно из этих чисел не исчезло с экрана — они
     * просто лежат на два раскрытия глубже.
     */
    private fun calculationsSection(
        input: WhyInput,
        baseline: Baseline?,
        unit: DoseUnitSetting,
    ): WhySection {
        val shape = input.fingerprint?.of(FingerprintDimension.SPECTRUM)
        return WhySection(
            title = s.calculationsSection,
            level = WhyLevel.EXPERT,
            lines = buildList {
                if (baseline != null) {
                    add(
                        WhyLine(
                            label = "MAD",
                            value = DoseFormat.rateWithUnit(
                                baseline.doseMadMicroSvH,
                                unit,
                                s = s,
                            ),
                            evidence = Evidence.STATISTICALLY_DETECTED,
                            note = s.madNote,
                        ),
                    )
                    add(
                        WhyLine(
                            label = s.minuteBuckets,
                            value = "${baseline.bucketCount}",
                            evidence = Evidence.CALCULATED,
                            note = s.honestN,
                        ),
                    )
                }
                if (shape != null && shape.state != FingerprintState.NOT_EVALUATED) {
                    add(
                        WhyLine(
                            label = s.shapeStatistics,
                            value = shape.detail,
                            evidence = Evidence.STATISTICALLY_DETECTED,
                        ),
                    )
                }
            },
            note = s.poissonNote + " " + s.deviceErrorBudget,
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
        // Первый уровень говорит ОДНОЙ фразой, что сравнивается и с чем;
        // «χ² по 677 корзинам, z = 1,7» живёт в «Расчётах и формулах».
        val note = when (shape?.state) {
            null, FingerprintState.NOT_EVALUATED -> s.spectralNoReference
            FingerprintState.NOT_ENOUGH_DATA -> s.spectralTooLittlePlain
            else -> s.spectralComparedPlain
        }
        return WhySection(
            title = s.spectralComparison,
            lines = listOf(
                WhyLine(
                    label = s.state,
                    value = value,
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
fun shortfallWording(state: BaselineState.Learning, s: MonitorStrings = MonitorRu): String =
    s.ofMinimumRequired(
        durationWording(state.accumulatedSeconds, s),
        durationWording(state.requiredSeconds, s),
    )
