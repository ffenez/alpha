package app.alpha.baseline

import app.alpha.ui.text.MonitorRu
import app.alpha.ui.text.MonitorStrings

/**
 * Why an interval was kept out of the baseline statistics (spec §4.2).
 *
 * [storageKey] is what lands in `samples.baselineExcluded`; it is a stable
 * on-disk contract — never rename a key, add a new one instead. The short
 * user-facing wording (История rows, «Почему?» sheet) lives in the language
 * catalogue: ключ хранится на диске и не зависит от языка, а подпись
 * показывается человеку и обязана быть на языке интерфейса.
 */
enum class BaselineExclusion(val storageKey: String) {
    /** Condition 1: the profile has baseline learning switched off. */
    LEARNING_OFF("learning_off"),

    /** Condition 2: the context is not confidently known (Wi-Fi just vanished). */
    CONTEXT_UNCERTAIN("context_uncertain"),

    /** Condition 3: the measurement stream is not fresh (spec §21). */
    STREAM_STALE("stream_stale"),

    /** Condition 4: Поиск / source / A-B experiment is running (spec §18). */
    EXPERIMENT("experiment"),

    /** Condition 5: quarantine window after a detected deviation episode. */
    QUARANTINE("quarantine"),

    /** Condition 6: the measurement statistics are unusable. */
    STATISTICS_UNUSABLE("statistics_unusable"),

    /** Condition 7: the user froze the baseline manually. */
    MANUAL_FREEZE("manual_freeze"),
    ;

    /** Русская подпись по умолчанию; на языке интерфейса — [wording]. */
    val label: String get() = wording()

    companion object {
        fun fromStorage(key: String?): BaselineExclusion? =
            key?.let { stored -> entries.firstOrNull { it.storageKey == stored } }
    }
}

/** Короткая причина словами человека — без слов «baseline» и «обучение». */
fun BaselineExclusion.wording(s: MonitorStrings = MonitorRu): String = when (this) {
    BaselineExclusion.LEARNING_OFF -> s.exclusionLearningOff
    BaselineExclusion.CONTEXT_UNCERTAIN -> s.exclusionContextUncertain
    BaselineExclusion.STREAM_STALE -> s.exclusionStreamStale
    BaselineExclusion.EXPERIMENT -> s.exclusionExperiment
    BaselineExclusion.QUARANTINE -> s.exclusionQuarantine
    BaselineExclusion.STATISTICS_UNUSABLE -> s.exclusionStatisticsUnusable
    BaselineExclusion.MANUAL_FREEZE -> s.exclusionManualFreeze
}

/** Verdict of [BaselineAdmission]. */
sealed interface Admission {
    /** The interval may take part in baseline statistics. */
    data object Admitted : Admission

    data class Excluded(val reason: BaselineExclusion) : Admission

    /** `null` = admitted, otherwise the storage key of the reason. */
    val storageKey: String?
        get() = (this as? Excluded)?.reason?.storageKey
}

/**
 * Everything the admission decision depends on, gathered at write time.
 *
 * Units: [sampleAgeMillis] milliseconds, [doseRateMicroSvH] µSv/h,
 * [countRateCps] counts per second, [countRateErrPercent] and
 * [doseRateErrPercent] percent as reported by the device (dr_err/10).
 */
data class AdmissionInput(
    /** Condition 1 — [app.alpha.data.db.ProfileEntity.baselineLearning]. */
    val profileLearningEnabled: Boolean,
    /** Condition 2 — see [app.alpha.context.MeasurementContext.isReliable]. */
    val contextReliable: Boolean,
    /** Condition 3 — age of this sample against the wall clock, ms. */
    val sampleAgeMillis: Long,
    /** Condition 4 — Поиск screen, source experiment or A/B run. */
    val experimentActive: Boolean,
    /** Condition 5 — quarantine deadline, epoch ms; null = no quarantine. */
    val quarantineUntilMillis: Long?,
    val nowMillis: Long,
    /** Condition 6 inputs. */
    val doseRateMicroSvH: Float,
    val countRateCps: Float,
    val countRateErrPercent: Float,
    val doseRateErrPercent: Float,
    /** Condition 7 — Настройки → «Заморозить baseline». */
    val manuallyFrozen: Boolean,
)

/**
 * Baseline admission pipeline (spec §4.2) — the guard against circular
 * learning, where a slowly developing anomaly is absorbed into «the new
 * normal» because the statistics keep eating it.
 *
 * **Model.** Seven independent conditions are evaluated in the order the spec
 * lists them; the first unmet one is returned as the exclusion reason. There
 * is no scoring and no weighting: the decision is a conjunction, and the
 * reported reason is the first violated conjunct, which makes every verdict
 * explainable in one phrase.
 *
 * **Assumptions.**
 *  - The caller supplies one sample's worth of state; the verdict is stored
 *    per sample, so a mixed minute keeps both admitted and excluded seconds
 *    and the minute bucket only aggregates the admitted ones.
 *  - Condition 3 uses the same staleness threshold as the UI
 *    ([app.alpha.ui.logic.Freshness.STALE_AFTER_SECONDS] = 10 s), so what
 *    the user sees as «поток прерван» is exactly what stops learning.
 *  - Condition 6 («статистика измерения пригодна») is deliberately weak: the
 *    device already reports its own uncertainty, so the only samples rejected
 *    are those carrying no information about the level — non-finite or
 *    negative values, zero counts, or a device-reported relative error above
 *    [MAX_RELATIVE_ERROR_PERCENT]. A stricter, physically motivated criterion
 *    (dead-time / pile-up regime, spec §5) needs RC-110 measurements we do not
 *    have yet and is deliberately deferred.
 *
 * **Limitations.** The pipeline decides admission only. It never modifies,
 * smooths or deletes raw data: every sample is stored regardless of the
 * verdict (CLAUDE.md invariant, spec §22).
 *
 * **Units.** See [AdmissionInput]. The verdict itself is dimensionless.
 */
object BaselineAdmission {

    /**
     * Bump when the meaning of a stored verdict changes (spec §22/§24), e.g.
     * a new condition or a changed threshold. Persisted verdicts from older
     * versions stay readable — the key set is append-only.
     */
    const val ALGORITHM_VERSION = 1

    /** Condition 3 threshold, seconds (mirrors the UI freshness rule). */
    const val STALE_AFTER_SECONDS = 10L

    /**
     * Condition 5 window. After a deviation episode is confirmed, the next
     * 30 minutes of measurement stay out of the baseline even once the level
     * came back: the tail of an excursion is not «usual», and 30 min is long
     * enough to cover the decay of a typical indoor radon/washout episode
     * without discarding a meaningful share of a 14-day window (0.15 % per
     * episode).
     */
    const val QUARANTINE_MILLIS = 30L * 60_000L

    /**
     * Condition 6: a device-reported relative error above this carries no
     * usable information about the level (the value is dominated by counting
     * noise). Chosen as a coarse sanity gate, not as a physical limit.
     */
    const val MAX_RELATIVE_ERROR_PERCENT = 50f

    fun evaluate(input: AdmissionInput): Admission {
        if (!input.profileLearningEnabled) return excluded(BaselineExclusion.LEARNING_OFF)
        if (!input.contextReliable) return excluded(BaselineExclusion.CONTEXT_UNCERTAIN)
        if (input.sampleAgeMillis > STALE_AFTER_SECONDS * 1000L) {
            return excluded(BaselineExclusion.STREAM_STALE)
        }
        if (input.experimentActive) return excluded(BaselineExclusion.EXPERIMENT)
        val quarantineUntil = input.quarantineUntilMillis
        if (quarantineUntil != null && input.nowMillis < quarantineUntil) {
            return excluded(BaselineExclusion.QUARANTINE)
        }
        if (!statisticsUsable(input)) return excluded(BaselineExclusion.STATISTICS_UNUSABLE)
        if (input.manuallyFrozen) return excluded(BaselineExclusion.MANUAL_FREEZE)
        return Admission.Admitted
    }

    /** Condition 6 in isolation — see the class KDoc for what «usable» means. */
    fun statisticsUsable(input: AdmissionInput): Boolean {
        val dose = input.doseRateMicroSvH
        val cps = input.countRateCps
        if (!dose.isFinite() || !cps.isFinite()) return false
        if (dose < 0f || cps <= 0f) return false
        if (input.countRateErrPercent > MAX_RELATIVE_ERROR_PERCENT) return false
        if (input.doseRateErrPercent > MAX_RELATIVE_ERROR_PERCENT) return false
        return true
    }

    private fun excluded(reason: BaselineExclusion): Admission = Admission.Excluded(reason)
}

/**
 * Quarantine bookkeeping (condition 5). The deadline is pushed forward while
 * an excursion is still being observed, so the window is measured from the
 * **end** of the episode, not from its start.
 */
class QuarantineWindow(private val windowMillis: Long = BaselineAdmission.QUARANTINE_MILLIS) {

    @Volatile
    var untilMillis: Long? = null
        private set

    /** Call on every sample with the live deviation picture. */
    fun onSample(nowMillis: Long, deviationActive: Boolean) {
        if (deviationActive) untilMillis = nowMillis + windowMillis
    }

    fun clear() {
        untilMillis = null
    }
}
