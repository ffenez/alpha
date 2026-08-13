package app.radiacode.ui.logic

import app.radiacode.baseline.Baseline
import app.radiacode.data.JsonMap
import app.radiacode.ui.text.MonitorRu
import app.radiacode.ui.text.MonitorStrings

/**
 * «Уровень изменился надолго» (why-spec §7).
 *
 * A deviation that lasts does **not** become the new usual by itself. If it
 * did, a source that stays where it is would quietly redefine the place it is
 * in, and the app would lose the only reference it has for saying that
 * anything changed at all. So the app may *ask*, and only the user may answer.
 *
 * What the answer does is equally deliberate: starting a new period keeps the
 * old one (`baseline_epochs`) and never touches a single raw measurement —
 * the epoch moves what the statistics read, not what was recorded.
 */
object ProfileShift {

    /**
     * How long the readings must keep differing before the offer appears.
     *
     * **Product parameter, not a physical constant** (§7 says this explicitly).
     * Six hours is long enough that an evening of cooking with a granite
     * counter, a flight or a medical procedure passes without a question, and
     * short enough that a genuinely moved instrument does not spend a day
     * measuring against the wrong place. It needs field validation before the
     * feature can be called finished.
     */
    const val OFFER_AFTER_SECONDS = 6L * 3600L

    /**
     * After «Оставить как есть» the question stays away this long.
     * **Product parameter**: an offer that returns on every glance at the
     * sheet is not an offer, it is nagging.
     */
    const val DECLINE_QUIET_MILLIS = 24L * 3600_000L

    /** Whether to show the offer at all. */
    fun shouldOffer(
        status: MonitorStatus,
        declinedAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        val held = when (status) {
            is MonitorStatus.AboveUsual -> status.heldSeconds
            is MonitorStatus.Alert -> status.heldSeconds
            else -> return false
        }
        if (held < OFFER_AFTER_SECONDS) return false
        if (declinedAtMillis != null && nowMillis - declinedAtMillis < DECLINE_QUIET_MILLIS) {
            return false
        }
        return true
    }

    fun title(s: MonitorStrings = MonitorRu): String = s.shiftTitle

    fun sentence(profileName: String?, s: MonitorStrings = MonitorRu): String =
        s.shiftSentence(profileName)

    /**
     * What each answer does, said before it is given — the update is not
     * reversible by the app, so it may not be a surprise.
     */
    fun explanation(s: MonitorStrings = MonitorRu): String = s.shiftExplanation

    fun updateAction(s: MonitorStrings = MonitorRu): String = s.shiftUpdateAction
    fun keepAction(s: MonitorStrings = MonitorRu): String = s.shiftKeepAction

    /** Русские варианты по умолчанию — для вызовов без каталога языка. */
    val TITLE: String get() = title()
    val EXPLANATION: String get() = explanation()
    val UPDATE_ACTION: String get() = updateAction()
    val KEEP_ACTION: String get() = keepAction()
}

/**
 * The snapshot of a closed baseline period (why-spec §7: «старый baseline
 * сохранить в истории»). Flat JSON so it can be read back by pure JVM code.
 */
object BaselineSnapshot {

    fun encode(baseline: Baseline): String = JsonMap.of(
        "p10" to baseline.doseLowMicroSvH,
        "median" to baseline.doseMedianMicroSvH,
        "p90" to baseline.doseHighMicroSvH,
        "p25" to baseline.doseP25MicroSvH,
        "p75" to baseline.doseP75MicroSvH,
        "mad" to baseline.doseMadMicroSvH,
        "cpsLow" to baseline.cpsLow,
        "cpsMedian" to baseline.cpsMedian,
        "cpsHigh" to baseline.cpsHigh,
        "accumulatedSeconds" to baseline.accumulatedSeconds,
        "sampleCount" to baseline.sampleCount,
        "bucketCount" to baseline.bucketCount,
    )

    /** Reads back what is needed to show a past period; null on garbage. */
    fun decodeRange(raw: String?): Pair<Float, Float>? {
        val map = JsonMap.decode(raw)
        val low = map["p10"]?.toFloatOrNull() ?: return null
        val high = map["p90"]?.toFloatOrNull() ?: return null
        return low to high
    }
}
