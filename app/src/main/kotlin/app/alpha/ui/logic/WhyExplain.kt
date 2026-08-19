package app.alpha.ui.logic

import app.alpha.baseline.Admission
import app.alpha.baseline.AlarmThresholds
import app.alpha.baseline.BaselineState
import app.alpha.data.DoseUnitSetting
import app.alpha.data.ExclusionSummary
import app.alpha.ui.text.MonitorRu
import app.alpha.ui.text.MonitorStrings
import app.alpha.ui.text.uiDecimal

/**
 * One line of the «Почему?» sheet: what it is, its value, how sure we are.
 *
 * [evidence] is `null` on the first level of disclosure (§21): the permanent
 * «изм. · расч. · стат.» markers stood next to every value, so they stopped
 * being read at all. Where the source of a number actually matters — inside
 * «показать методику и расчёты» — the marker is still there and the legend
 * under the sheet spells it out in words.
 */
data class WhyLine(
    val label: String,
    val value: String,
    val evidence: Evidence? = null,
    /**
     * Пояснение: как считалось, что означает термин. Прячется вместе со всеми
     * пояснениями (CLAUDE.md, три категории интерфейса).
     */
    val note: String? = null,
    /**
     * То, без чего число нельзя истолковать: ±σ, объём замера, отказ метода,
     * «сравнивать не с чем». Видно ВСЕГДА, при любом положении переключателя
     * «Пояснения» — иначе строка «21,8» выглядела бы точной.
     */
    val critical: String? = null,
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
    /** Сравнение с эталоном места (ADR 005); null = не считалось. */
    val fingerprint: app.alpha.analysis.FingerprintComparison? = null,
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

    /**
     * One sentence saying against what the verdict was made (CHART SPEC §18).
     * It names the historical P10–P90 of the profile and never calls it a norm
     * or a safe range: the band describes this place's own history.
     */
    fun verdictExplanation(
        status: MonitorStatus,
        s: MonitorStrings = MonitorRu,
    ): String = when (status) {
        MonitorStatus.Unknown -> s.verdictNoReading
        is MonitorStatus.Fixed -> s.verdictNoBand
        is MonitorStatus.Usual -> s.verdictInsideBand
        is MonitorStatus.AboveUsual -> s.verdictAboveBand
        is MonitorStatus.AboveThreshold -> s.verdictAboveThreshold
        is MonitorStatus.Alert -> s.verdictAlert
    }

}

/** «45 с» / «12 мин» / «3,5 ч» — compact duration for explanation text. */
fun durationWording(seconds: Long, s: MonitorStrings = MonitorRu): String = when {
    seconds < 60 -> s.secondsShort(seconds)
    seconds < 3600 -> s.minutesShort(seconds / 60)
    else -> {
        val hours = seconds / 3600.0
        // Whole hours read as integers, same convention as the baseline wording.
        val text = if (hours >= 10 || hours == Math.floor(hours)) {
            "${hours.toLong()}"
        } else {
            String.format(java.util.Locale.US, "%.1f", hours).uiDecimal()
        }
        s.hoursShort(text)
    }
}
