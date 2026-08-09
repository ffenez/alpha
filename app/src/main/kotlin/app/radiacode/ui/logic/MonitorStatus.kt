package app.radiacode.ui.logic

import app.radiacode.baseline.ABOVE_USUAL_MIN_DWELL_SECONDS
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.DeviationSnapshot
import app.radiacode.data.DoseUnitSetting

/**
 * Main-screen status (SPEC «Главный экран» + «Главная идея продукта»).
 *
 * With an active baseline the wording compares against the personal typical
 * band of the current place; while the baseline is learning it falls back to
 * the honest fixed-threshold comparison. Deviation statuses come from the
 * service's persistence trackers — magnitude AND duration, never a single
 * 1 Hz jump.
 */
sealed interface MonitorStatus {

    /** No current reading to judge. */
    data object Unknown : MonitorStatus

    /** Baseline not ready: fixed-threshold comparison (pre-baseline fallback). */
    data class Fixed(val above: Boolean, val thresholdMicroSvH: Float) : MonitorStatus

    /** Within the personal typical band of this place. */
    data class Usual(val baseline: Baseline) : MonitorStatus

    /** Above the typical band and holding (magnitude + dwell). */
    data class AboveUsual(val baseline: Baseline, val heldSeconds: Long) : MonitorStatus

    /** Confirmed persistent deviation — the alarm-worthy state. */
    data class Alert(
        val baseline: Baseline?,
        val heldSeconds: Long,
        val thresholdMicroSvH: Float,
    ) : MonitorStatus

    companion object {
        fun of(
            doseRateMicroSvH: Float?,
            baselineState: BaselineState?,
            deviation: DeviationSnapshot,
            thresholds: AlarmThresholds,
            nowMillis: Long,
        ): MonitorStatus {
            if (doseRateMicroSvH == null) return Unknown
            val baseline = (baselineState as? BaselineState.Active)?.baseline

            val alertSince = deviation.alertSince
            if (alertSince != null) {
                return Alert(
                    baseline = baseline,
                    heldSeconds = heldSeconds(nowMillis, alertSince),
                    thresholdMicroSvH = thresholds.l1MicroSvH,
                )
            }

            if (baseline != null) {
                val aboveSince = deviation.aboveUsualSince
                val held = aboveSince?.let { heldSeconds(nowMillis, it) } ?: 0L
                return if (aboveSince != null && held >= ABOVE_USUAL_MIN_DWELL_SECONDS) {
                    AboveUsual(baseline, held)
                } else {
                    Usual(baseline)
                }
            }

            return Fixed(
                above = doseRateMicroSvH >= thresholds.l1MicroSvH,
                thresholdMicroSvH = thresholds.l1MicroSvH,
            )
        }

        private fun heldSeconds(nowMillis: Long, sinceMillis: Long): Long =
            ((nowMillis - sinceMillis) / 1000L).coerceAtLeast(0L)
    }
}

/** The big status word(s). Never claims safety. */
fun statusHeadline(status: MonitorStatus): String = when (status) {
    MonitorStatus.Unknown -> "Нет данных"
    is MonitorStatus.Fixed -> if (status.above) "Выше порога" else "Фон в норме"
    is MonitorStatus.Usual -> "Обычный фон"
    is MonitorStatus.AboveUsual -> "Выше обычного"
    is MonitorStatus.Alert -> "Уровень радиации изменился"
}

/** Second line under the status: the honest context of the comparison. */
fun statusDetail(status: MonitorStatus, unit: DoseUnitSetting): String? = when (status) {
    MonitorStatus.Unknown -> null
    is MonitorStatus.Fixed ->
        if (status.above) "порог ${DoseFormat.rateWithUnit(status.thresholdMicroSvH, unit)}" else null
    is MonitorStatus.Usual ->
        "для этого места · ${baselineRange(status.baseline, unit)} ${DoseFormat.rateUnitLabel(unit)}"
    is MonitorStatus.AboveUsual ->
        "обычно здесь ${baselineRange(status.baseline, unit)} · ${heldWording(status.heldSeconds)}"
    is MonitorStatus.Alert -> {
        val reference = status.baseline?.let { "обычно здесь ${baselineRange(it, unit)}" }
            ?: "порог ${DoseFormat.rateWithUnit(status.thresholdMicroSvH, unit)}"
        "$reference · ${heldWording(status.heldSeconds)}"
    }
}

private fun baselineRange(baseline: Baseline, unit: DoseUnitSetting): String =
    DoseFormat.range(baseline.doseLowMicroSvH, baseline.doseHighMicroSvH, unit)

/** «держится 45 с» / «держится 4 мин» / «держится 1 ч 12 мин». */
fun heldWording(heldSeconds: Long): String {
    val text = when {
        heldSeconds < 60 -> "$heldSeconds с"
        heldSeconds < 3600 -> "${heldSeconds / 60} мин"
        else -> "${heldSeconds / 3600} ч ${heldSeconds % 3600 / 60} мин"
    }
    return "держится $text"
}

/**
 * CPS line (SPEC: «28 CPS · within your normal range», «64 CPS · +150%»).
 * Without an active baseline the number stands alone — no fake context.
 */
fun cpsWording(cps: Float?, baselineState: BaselineState?): String {
    if (cps == null) return "— CPS"
    val value = "${cps.toInt()} CPS"
    val baseline = (baselineState as? BaselineState.Active)?.baseline ?: return value
    if (baseline.cpsMedian <= 0f) return value
    return when {
        cps > baseline.cpsHigh -> {
            val percent = ((cps - baseline.cpsMedian) / baseline.cpsMedian * 100f).toInt()
            "$value · +$percent% к обычному"
        }
        cps < baseline.cpsLow -> {
            val percent = ((baseline.cpsMedian - cps) / baseline.cpsMedian * 100f).toInt()
            "$value · −$percent% к обычному"
        }
        else -> "$value · в обычном диапазоне"
    }
}

/** Learning progress: «изучаю обычный фон — 1.5 ч из 3». */
fun learningWording(state: BaselineState.Learning): String {
    val collected = formatHours(state.accumulatedSeconds)
    val required = formatHours(state.requiredSeconds)
    return "изучаю обычный фон — $collected ч из $required"
}

/** Chart footnote: «baseline собран за 26 ч наблюдений». */
fun baselineCollectedWording(baseline: Baseline): String =
    "baseline собран за ${formatHours(baseline.accumulatedSeconds)} ч наблюдений"

private fun formatHours(seconds: Long): String {
    val hours = seconds / 3600.0
    return if (hours >= 10 || hours == Math.floor(hours)) {
        "${hours.toLong()}"
    } else {
        String.format(java.util.Locale.US, "%.1f", hours)
    }
}

/** 0.30 -> "0.30"; readings keep two decimals so digits don't jump. */
fun formatMicroSv(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)
