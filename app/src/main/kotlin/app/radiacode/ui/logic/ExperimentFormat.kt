package app.radiacode.ui.logic

import app.radiacode.analysis.AbAnalysis
import app.radiacode.analysis.EnergyWindowSpec
import app.radiacode.analysis.EnergyWindows
import app.radiacode.data.db.ExperimentEntity
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

    fun verdictLabel(verdict: AbAnalysis.Verdict): String = when (verdict) {
        AbAnalysis.Verdict.CONSISTENT -> "различий не видно"
        AbAnalysis.Verdict.CHANGED -> "есть различие"
        AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE -> "сильные свидетельства различия"
    }

    /** Headline sentence: says what the verdict is *about*, never «опасно». */
    fun verdictHeadline(verdict: AbAnalysis.Verdict, aLabel: String, bLabel: String): String =
        when (verdict) {
            AbAnalysis.Verdict.CONSISTENT ->
                "Измерения $aLabel и $bLabel согласуются между собой"
            AbAnalysis.Verdict.CHANGED ->
                "Между $aLabel и $bLabel есть статистическое различие"
            AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE ->
                "Между $aLabel и $bLabel сильные свидетельства различия"
        }

    fun methodLabel(method: AbAnalysis.Method): String = when (method) {
        AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO -> "Пуассон, отношение правдоподобия"
        AbAnalysis.Method.CHI_SQUARE -> "χ²-подобный, z = нетто/σ"
    }

    fun methodShort(method: AbAnalysis.Method): String = when (method) {
        AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO -> "LR"
        AbAnalysis.Method.CHI_SQUARE -> "χ²"
    }

    /** Why this statistic was chosen — the switch is a documented parameter. */
    fun methodExplanation(method: AbAnalysis.Method): String = when (method) {
        AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO ->
            "мало импульсов (< ${AbAnalysis.NORMAL_APPROX_MIN_COUNTS.toInt()} в прогоне) — " +
                "использован пуассоновский критерий отношения правдоподобия"
        AbAnalysis.Method.CHI_SQUARE ->
            "импульсов достаточно (≥ ${AbAnalysis.NORMAL_APPROX_MIN_COUNTS.toInt()} в каждом " +
                "прогоне) — использован χ²-подобный критерий z = нетто/σ"
    }

    // --- kinds ---

    fun kindLabel(kind: String): String = when (kind) {
        ExperimentEntity.KIND_BACKGROUND_VS_OBJECT -> "Фон и объект"
        ExperimentEntity.KIND_PLACE_VS_PLACE -> "Место и место"
        ExperimentEntity.KIND_DISTANCE -> "Расстояние"
        ExperimentEntity.KIND_SHIELDING -> "Экранирование"
        else -> kind
    }

    fun kindHint(kind: String): String = when (kind) {
        ExperimentEntity.KIND_BACKGROUND_VS_OBJECT ->
            "A — объект у детектора, B — тот же детектор без объекта. Геометрия " +
                "должна быть одинаковой, иначе сравнивается не объект, а положение прибора."
        ExperimentEntity.KIND_PLACE_VS_PLACE ->
            "A и B — два места. Сравниваются измерения как они есть; вывод относится " +
                "к этим двум измерениям, а не к местам вообще."
        ExperimentEntity.KIND_DISTANCE ->
            "Серия прогонов на известных расстояниях от объекта. Плюс, по возможности, " +
                "прогон фона без объекта — без него дальние точки будут в основном фоном."
        ExperimentEntity.KIND_SHIELDING ->
            "A — без материала, B — с материалом, в остальном всё то же самое. " +
                "Универсальных коэффициентов ослабления из такого опыта не выводится."
        else -> ""
    }

    /** Run labels of the scenario: what A and B mean here. */
    fun runRoleLabel(kind: String, index: Int): String = when (kind) {
        ExperimentEntity.KIND_BACKGROUND_VS_OBJECT ->
            if (index == 0) "объект" else "фон"
        ExperimentEntity.KIND_SHIELDING ->
            if (index == 0) "без материала" else "с материалом"
        ExperimentEntity.KIND_PLACE_VS_PLACE ->
            if (index == 0) "место A" else "место B"
        ExperimentEntity.KIND_DISTANCE -> "точка ${runLetter(index)}"
        else -> "прогон ${runLetter(index)}"
    }

    /** A, B, C… (Latin letters keep the report readable in any locale). */
    fun runLetter(index: Int): String =
        if (index < 26) ('A' + index).toString() else "R${index + 1}"

    // --- mandated warnings (spec §16) ---

    const val DISTANCE_WARNING =
        "Сравнение с идеализированной зависимостью 1/r² — только ориентир. Реальный " +
            "источник не точечный, излучение рассеивается на воздухе и окружении, а фон " +
            "с расстоянием не убывает вовсе. Совпадение с кривой не доказывает геометрию, " +
            "расхождение не означает ошибку измерения."

    const val SHIELDING_WARNING =
        "Из этого опыта не выводятся коэффициенты ослабления материала: домашняя " +
            "геометрия неконтролируема, спектр источника неизвестен, а рассеянное " +
            "излучение приходит в детектор в обход материала."

    const val EXPERIMENTAL_BADGE = "экспериментальная функция"

    const val EXPERIMENTAL_NOTE =
        "Функция экспериментальная: статистика реализована и проверена на синтетике, " +
            "но пока не валидирована на реальных измерениях RC-110. Вердикт говорит о " +
            "различии между двумя измерениями, а не об опасности и не о том, что найдено."

    const val GEOMETRY_PROMPT =
        "Опишите геометрию один раз: где лежит объект, на каком расстоянии и как " +
            "повёрнут прибор. Это описание покажется при каждом следующем прогоне — " +
            "повторить его точно и есть смысл A/B."

    // --- numbers ---

    /** «12,3 имп/с» / «0,42 имп/с» — precision follows magnitude. */
    fun cps(value: Double): String = "${decimal(value)} имп/с"

    fun cpsWithSigma(value: Double, sigma: Double): String =
        "${decimal(value)} ±${decimal(sigma)} имп/с"

    /** Signed rate difference «+1,20 имп/с». */
    fun signedCps(value: Double): String =
        (if (value < 0) "−" else "+") + decimal(abs(value)) + " имп/с"

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

    fun duration(seconds: Long): String {
        if (seconds < 60) return "$seconds с"
        val minutes = seconds / 60
        val rest = seconds % 60
        return if (rest == 0L) "$minutes мин" else "$minutes мин $rest с"
    }

    fun distance(distanceCm: Float): String =
        if (distanceCm >= 100f) {
            "${decimal(distanceCm / 100.0)} м"
        } else {
            "${distanceCm.roundToInt()} см"
        }

    // --- energy windows (spec §7) ---

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

    const val INDEX_NOTE =
        "Спектральный индекс — описательная характеристика состава спектра, " +
            "а не мера опасности. Границы окон — параметр анализа, а не физические " +
            "категории излучения."

    const val WINDOWS_EDGE_NOTE =
        "Канал целиком относится к окну, если его центр попал внутрь: дробить счёт " +
            "по краю нельзя — дробный счёт перестаёт быть пуассоновским."
}
