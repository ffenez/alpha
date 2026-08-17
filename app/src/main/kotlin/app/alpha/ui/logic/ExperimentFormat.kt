package app.alpha.ui.logic

import app.alpha.analysis.AbAnalysis
import app.alpha.analysis.EnergyWindowSpec
import app.alpha.analysis.EnergyWindows
import app.alpha.data.db.ExperimentEntity
import app.alpha.ui.text.ExperimentRu
import app.alpha.ui.text.ExperimentStrings
import app.alpha.ui.text.SpectrumRu
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Wording for the A/B experiment screen and its report (spec §8, §9, §16).
 *
 * The verdict vocabulary is closed on purpose: `consistent` / `changed` /
 * `strong evidence of change` and nothing else. A «% похожести» has no defined
 * statistical meaning until a metric is validated on RC-110 data, so it must
 * not exist anywhere in the UI — `ExperimentFormatTest` pins that.
 *
 * Каталог приходит ПАРАМЕТРОМ со значением по умолчанию `ExperimentRu`:
 * эти функции вызывает и экран (там язык выбран человеком), и текстовый
 * отчёт эксперимента, который от языка интерфейса зависеть не должен —
 * его читают вместе с автором, а не только автор.
 *
 * Разделитель дроби остаётся запятой на любом языке: строки запинены
 * тестами и не должны зависеть ни от локали телефона, ни от языка
 * интерфейса (то же правило, что в [Uncertainty] и `SearchVerdict`).
 *
 * Pure JVM, tested.
 */
object ExperimentFormat {

    // --- verdicts (spec §8) ---

    /** Canonical English token of the verdict — the report carries it too. */
    fun verdictToken(verdict: AbAnalysis.Verdict): String = when (verdict) {
        AbAnalysis.Verdict.CONSISTENT -> "consistent"
        AbAnalysis.Verdict.CHANGED -> "changed"
        AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE -> "strong evidence of change"
    }

    fun verdictLabel(
        verdict: AbAnalysis.Verdict,
        s: ExperimentStrings = ExperimentRu,
    ): String = when (verdict) {
        AbAnalysis.Verdict.CONSISTENT -> s.verdictConsistent
        AbAnalysis.Verdict.CHANGED -> s.verdictChanged
        AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE -> s.verdictStrongEvidence
    }

    /** Headline sentence: says what the verdict is *about*, never «опасно». */
    fun verdictHeadline(
        verdict: AbAnalysis.Verdict,
        aLabel: String,
        bLabel: String,
        s: ExperimentStrings = ExperimentRu,
    ): String = when (verdict) {
        AbAnalysis.Verdict.CONSISTENT -> s.headlineConsistent(aLabel, bLabel)
        AbAnalysis.Verdict.CHANGED -> s.headlineChanged(aLabel, bLabel)
        AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE -> s.headlineStrongEvidence(aLabel, bLabel)
    }

    fun methodLabel(
        method: AbAnalysis.Method,
        s: ExperimentStrings = ExperimentRu,
    ): String = when (method) {
        AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO -> s.methodPoisson
        AbAnalysis.Method.CHI_SQUARE -> s.methodChiSquare
    }

    fun methodShort(method: AbAnalysis.Method): String = when (method) {
        AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO -> "LR"
        AbAnalysis.Method.CHI_SQUARE -> "χ²"
    }

    /** Why this statistic was chosen — the switch is a documented parameter. */
    fun methodExplanation(
        method: AbAnalysis.Method,
        s: ExperimentStrings = ExperimentRu,
    ): String {
        val minCounts = AbAnalysis.NORMAL_APPROX_MIN_COUNTS.toInt()
        return when (method) {
            AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO -> s.methodExplanationPoisson(minCounts)
            AbAnalysis.Method.CHI_SQUARE -> s.methodExplanationChiSquare(minCounts)
        }
    }

    // --- kinds ---

    fun kindLabel(kind: String, s: ExperimentStrings = ExperimentRu): String = when (kind) {
        ExperimentEntity.KIND_BACKGROUND_VS_OBJECT -> s.kindBackgroundVsObject
        ExperimentEntity.KIND_PLACE_VS_PLACE -> s.kindPlaceVsPlace
        ExperimentEntity.KIND_DISTANCE -> s.kindDistance
        ExperimentEntity.KIND_SHIELDING -> s.kindShielding
        ExperimentEntity.KIND_CUSTOM -> s.scenarioCustom
        ExperimentEntity.KIND_FOOD -> s.kindFood
        else -> kind
    }

    fun kindHint(kind: String, s: ExperimentStrings = ExperimentRu): String = when (kind) {
        ExperimentEntity.KIND_BACKGROUND_VS_OBJECT -> s.hintBackgroundVsObject
        ExperimentEntity.KIND_PLACE_VS_PLACE -> s.hintPlaceVsPlace
        ExperimentEntity.KIND_DISTANCE -> s.hintDistance
        ExperimentEntity.KIND_SHIELDING -> s.hintShielding
        ExperimentEntity.KIND_CUSTOM -> s.scenarioCustomHint
        ExperimentEntity.KIND_FOOD -> s.hintFood
        else -> ""
    }

    /** Run labels of the scenario: what A and B mean here. */
    fun runRoleLabel(
        kind: String,
        index: Int,
        s: ExperimentStrings = ExperimentRu,
    ): String = when (kind) {
        ExperimentEntity.KIND_BACKGROUND_VS_OBJECT ->
            if (index == 0) s.roleObject else s.roleBackground
        ExperimentEntity.KIND_SHIELDING ->
            if (index == 0) s.roleWithoutMaterial else s.roleWithMaterial
        ExperimentEntity.KIND_PLACE_VS_PLACE ->
            s.rolePlace(runLetter(index))
        ExperimentEntity.KIND_DISTANCE -> s.rolePoint(runLetter(index))
        // У продукта прогоны названы ролями, а не буквами: «A» и «B» человек
        // тут же забудет, а «фон» и «продукт» помнить не нужно.
        ExperimentEntity.KIND_FOOD ->
            if (index == 0) s.roleBackground else s.roleSample
        else -> s.roleRun(runLetter(index))
    }

    /** A, B, C… (Latin letters keep the report readable in any locale). */
    fun runLetter(index: Int): String =
        if (index < 26) ('A' + index).toString() else "R${index + 1}"

    // --- mandated warnings (spec §16) ---
    //
    // Свойства, а не `const`: текст живёт в каталоге области, а отчёт
    // эксперимента (`data/export/ExperimentReport`) читает русский вариант —
    // файл экспорта не зависит от языка интерфейса.

    val DISTANCE_WARNING: String get() = ExperimentRu.distanceWarning

    val SHIELDING_WARNING: String get() = ExperimentRu.shieldingWarning

    val EXPERIMENTAL_BADGE: String get() = ExperimentRu.experimentalBadge

    val EXPERIMENTAL_NOTE: String get() = ExperimentRu.experimentalNote

    val GEOMETRY_PROMPT: String get() = ExperimentRu.geometryPrompt

    // --- numbers ---

    /** «12,3 имп/с» / «0,42 имп/с» — precision follows magnitude. */
    fun cps(value: Double, s: ExperimentStrings = ExperimentRu): String =
        "${decimal(value)} ${s.countsPerSecond}"

    /** «12,3 ±0,4» — без единицы: её ставит колонка или вызывающий. */
    fun rateWithSigma(value: Double, sigma: Double): String =
        "${decimal(value)} ±${decimal(sigma)}"

    fun cpsWithSigma(
        value: Double,
        sigma: Double,
        s: ExperimentStrings = ExperimentRu,
    ): String = "${rateWithSigma(value, sigma)} ${s.countsPerSecond}"

    /** Signed rate difference «+1,20 имп/с». */
    fun signedCps(value: Double, s: ExperimentStrings = ExperimentRu): String =
        (if (value < 0) "−" else "+") + decimal(abs(value)) + " " + s.countsPerSecond

    fun signedCounts(value: Double): String =
        (if (value < 0) "−" else "+") + decimal(abs(value))

    fun zLabel(z: Double): String {
        val text = String.format(Locale.US, "%.1f", abs(z)).replace('.', ',')
        return (if (z < 0) "−" else "+") + text + "σ"
    }

    fun decimal(value: Double): String {
        val magnitude = abs(value)
        val digits = when {
            magnitude >= 1000.0 -> 0
            magnitude >= 100.0 -> 1
            magnitude >= 1.0 -> 2
            magnitude >= 0.01 -> 3
            else -> 4
        }
        return String.format(Locale.US, "%.${digits}f", value).replace('.', ',')
    }

    fun duration(seconds: Long, s: ExperimentStrings = ExperimentRu): String {
        if (seconds < 60) return s.seconds(seconds)
        val minutes = seconds / 60
        val rest = seconds % 60
        return if (rest == 0L) s.minutes(minutes) else s.minutesSeconds(minutes, rest)
    }

    fun distance(distanceCm: Float, s: ExperimentStrings = ExperimentRu): String =
        if (distanceCm >= 100f) {
            s.meters(decimal(distanceCm / 100.0))
        } else {
            s.centimeters(distanceCm.roundToInt())
        }

    // --- energy windows (spec §7) ---
    //
    // Окна показывает и экран Спектра (`EnergyWindowsSection`), который живёт
    // в другой области перевода: подписи здесь числовые, а две оговорки ниже
    // переехали в её каталог (`SpectrumStrings`). Здесь остались русские
    // формы — их печатает ОТЧЁТ эксперимента, а отчёт не должен зависеть от
    // языка интерфейса того, кто его снял.

    fun windowLabel(spec: EnergyWindowSpec): String =
        "${spec.startKeV.roundToInt()}–${spec.endKeV.roundToInt()}"

    fun windowRate(window: EnergyWindows.WindowResult): String =
        "${decimal(window.rateCps)} ±${decimal(window.sigmaCps)}"

    fun windowShare(window: EnergyWindows.WindowResult): String =
        "${(window.fraction * 100).roundToInt()}%"

    fun indexLabel(index: EnergyWindows.SpectralIndex): String =
        "${decimal(index.value)} ±${decimal(index.sigma)}"

    fun indexCaption(index: EnergyWindows.SpectralIndex): String =
        "R(${windowLabel(index.lowWindow)}) / R(${windowLabel(index.highWindow)})"

    val INDEX_NOTE: String get() = SpectrumRu.indexNote

    val WINDOWS_EDGE_NOTE: String get() = SpectrumRu.windowsEdgeNote
}
