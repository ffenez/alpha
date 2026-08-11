package app.radiacode.ui.logic

import app.radiacode.baseline.Admission
import app.radiacode.baseline.AlarmThresholds
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
 * The verdict wording of «Почему такой вывод» (spec §17, why-spec §2).
 *
 * The body of the sheet is assembled by [WhyReportBuilder]; what stays here is
 * the pair of sentences the rest of the app also uses — the headline verdict
 * and the one line naming what it was compared with. Both are pure text so the
 * wording can be asserted in tests: this sheet is the app's only promise of
 * explainability, and a silently changed phrase here is a scientific
 * regression, not a cosmetic one.
 */
object WhyExplain {

    fun verdict(status: MonitorStatus): String = statusHeadline(status)

    /**
     * One sentence saying against what the verdict was made (CHART SPEC §18).
     * It names the historical P10–P90 of the profile and never calls it a norm
     * or a safe range: the band describes this place's own history.
     */
    fun verdictExplanation(status: MonitorStatus): String = when (status) {
        MonitorStatus.Unknown -> "Текущего измерения нет — сравнивать не с чем."
        is MonitorStatus.Fixed ->
            "Исторический P10–P90 профиля ещё не собран, поэтому сравнение идёт " +
                "только с порогом тревоги L1."
        is MonitorStatus.Usual ->
            "Текущая мощность дозы находится внутри исторического P10–P90 профиля."
        is MonitorStatus.AboveUsual ->
            "Текущая мощность дозы держится выше исторического P10–P90 профиля."
        is MonitorStatus.Alert ->
            "Превышение держится дольше заданного времени: это сравнение с порогом L1 " +
                "и с историческим P10–P90 профиля, а не оценка опасности."
    }

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
