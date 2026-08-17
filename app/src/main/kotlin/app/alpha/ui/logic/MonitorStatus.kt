package app.alpha.ui.logic

import app.alpha.baseline.ABOVE_USUAL_MIN_DWELL_SECONDS
import app.alpha.baseline.AlarmThresholds
import app.alpha.ui.text.MonitorCatalogue
import app.alpha.ui.text.MonitorRu
import app.alpha.ui.text.MonitorStrings
import app.alpha.ui.text.RuStrings
import app.alpha.ui.text.Strings
import app.alpha.baseline.Baseline
import app.alpha.baseline.BaselineState
import app.alpha.baseline.DeviationSnapshot
import app.alpha.data.DoseUnitSetting

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

    /**
     * Значение уже выше порога тревоги, но ещё не выдержало по длительности.
     *
     * Отдельная ступень, потому что молчать здесь нельзя: человек сам поставил
     * порог и смотрит на число выше него. Тревога — про величину И
     * длительность, поэтому ступень называет обе.
     */
    data class AboveThreshold(
        val baseline: Baseline?,
        val heldSeconds: Long,
        val requiredSeconds: Long,
        val thresholdMicroSvH: Float,
    ) : MonitorStatus

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

            // Порог тревоги виден сразу, ещё до выдержки, и виден независимо
            // от того, собран ли исторический диапазон места: это НАСТРОЙКА
            // пользователя, а не статистика, и прятать её превышение внутри
            // «в обычном диапазоне» нельзя.
            val conditionSince = deviation.alarmConditionSince
            if (conditionSince != null) {
                return AboveThreshold(
                    baseline = baseline,
                    heldSeconds = heldSeconds(nowMillis, conditionSince),
                    requiredSeconds = thresholds.persistenceSeconds.toLong(),
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

/**
 * The big status word(s).
 *
 * CHART SPEC §18/§39: the verdict names **what it is compared with** and never
 * uses «норма / безопасно / допустимо» — a historical percentile of one place
 * is not a safety statement. «Обычный фон» is therefore spelled out as «В
 * обычном диапазоне этого профиля»; [statusHeadlineShort] is the only shorter
 * variant, for places where the line must physically fit.
 */
fun statusHeadline(status: MonitorStatus, s: Strings = RuStrings): String = when (status) {
    MonitorStatus.Unknown -> s.statusNoData
    is MonitorStatus.Fixed -> if (status.above) s.statusAboveL1 else s.statusBelowL1
    is MonitorStatus.Usual -> s.statusUsual
    is MonitorStatus.AboveUsual -> s.statusAboveUsual
    is MonitorStatus.AboveThreshold -> s.statusAboveThreshold
    is MonitorStatus.Alert -> s.statusAlert
}

/** Short variant for narrow slots; same meaning, same forbidden words. */
fun statusHeadlineShort(status: MonitorStatus, s: Strings = RuStrings): String = when (status) {
    is MonitorStatus.Usual -> s.statusUsualShort
    is MonitorStatus.AboveThreshold -> s.statusAboveThresholdShort
    else -> statusHeadline(status, s)
}

/**
 * Second line under the status — the reference is **always** shown (§18):
 * which band, and how much history it is built on.
 */
fun statusDetail(
    status: MonitorStatus,
    unit: DoseUnitSetting,
    s: Strings = RuStrings,
): String? = when (status) {
    MonitorStatus.Unknown -> null
    is MonitorStatus.Fixed ->
        s.detailNoBaseline(DoseFormat.rateWithUnit(status.thresholdMicroSvH, unit, s = s))
    is MonitorStatus.Usual ->
        // «baseline» — внутреннее имя движка; на экране у величины есть
        // человеческое название, и смешивать их незачем.
        s.detailUsual(
            baselineRange(status.baseline, unit),
            DoseFormat.rateUnitLabel(unit, s = s),
            baselineCollectedShort(status.baseline, MonitorCatalogue.of(s.language)),
        )
    is MonitorStatus.AboveUsual ->
        s.detailAboveUsual(
            baselineRange(status.baseline, unit),
            DoseFormat.rateUnitLabel(unit, s = s),
            heldWording(status.heldSeconds, s),
        )
    is MonitorStatus.AboveThreshold ->
        // Величина И длительность: обе названы, поэтому ожидание видно, а не
        // выглядит как «приложение ничего не заметило».
        s.detailAboveThreshold(
            DoseFormat.rateWithUnit(status.thresholdMicroSvH, unit, s = s),
            heldWording(status.heldSeconds, s),
            spanWording(status.requiredSeconds, s),
        )
    is MonitorStatus.Alert -> {
        val reference = status.baseline
            ?.let {
                s.referenceProfileBand(baselineRange(it, unit), DoseFormat.rateUnitLabel(unit, s = s))
            }
            ?: s.referenceThreshold(DoseFormat.rateWithUnit(status.thresholdMicroSvH, unit, s = s))
        s.detailAlert(reference, heldWording(status.heldSeconds, s))
    }
}

private fun baselineRange(baseline: Baseline, unit: DoseUnitSetting): String =
    DoseFormat.range(baseline.doseLowMicroSvH, baseline.doseHighMicroSvH, unit)

/** «45 с» / «4 мин» / «1 ч 12 мин» — длительность без приставки. */
fun spanWording(seconds: Long, s: Strings = RuStrings): String = when {
    seconds < 60 -> s.seconds(seconds)
    seconds < 3600 -> s.minutes(seconds / 60)
    else -> s.hoursMinutes(seconds / 3600, seconds % 3600 / 60)
}

/** «уже 45 с» / «уже 4 мин» / «уже 1 ч 12 мин». */
fun heldWording(heldSeconds: Long, s: Strings = RuStrings): String =
    s.held(spanWording(heldSeconds, s))

/** Learning progress: «изучаю обычный фон — 1,5 ч из 3». */
fun learningWording(state: BaselineState.Learning, s: MonitorStrings = MonitorRu): String =
    s.collectingUsualBackground(
        formatHours(state.accumulatedSeconds),
        formatHours(state.requiredSeconds),
    )

/** Settings wording: «baseline собран за 26 ч наблюдений». */
fun baselineCollectedWording(baseline: Baseline, s: MonitorStrings = MonitorRu): String =
    s.usualBackgroundCollected(formatHours(baseline.accumulatedSeconds))

/** Status-line reference (§18 «baseline: 18 h»): «26,4 ч». */
fun baselineCollectedShort(baseline: Baseline, s: MonitorStrings = MonitorRu): String =
    s.hoursShort(formatHours(baseline.accumulatedSeconds))

private fun formatHours(seconds: Long): String {
    val hours = seconds / 3600.0
    return if (hours >= 10 || hours == Math.floor(hours)) {
        "${hours.toLong()}"
    } else {
        String.format(java.util.Locale.US, "%.1f", hours).replace('.', ',')
    }
}
