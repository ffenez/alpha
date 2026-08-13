package app.radiacode.analysis

import app.radiacode.baseline.Baseline
import app.radiacode.ui.text.MonitorRu
import app.radiacode.ui.text.MonitorStrings

/** What a statement is about — the UI picks wording and icon by kind, not by parsing text. */
enum class DeviationKind {
    /** The middle of the window moved out of the profile's middle half. */
    MEDIAN_SHIFT,

    /** The window's middle half is materially wider than the profile's. */
    SPREAD_WIDER,

    /** The window's median sits outside the profile's historical P10–P90. */
    OUTSIDE_PROFILE_BAND,

    /** A brief excursion above the profile's P90 that did not hold. */
    SHORT_SPIKE,
}

/**
 * One number a statement rests on. Values stay raw (µSv/h or seconds) — the
 * screen formats them with the user's display unit, so the analysis layer
 * never hard-codes a unit conversion (CLAUDE.md invariant).
 */
data class DeviationNumber(
    val label: String,
    val value: Double,
    val unit: DeviationUnit,
)

enum class DeviationUnit { MICRO_SV_PER_HOUR, SECONDS }

/**
 * A descriptive statement about the current window versus the profile
 * baseline. [text] is the whole claim — it says what moved, never why, never
 * how significant, and never how confident anyone is.
 */
data class DeviationStatement(
    val kind: DeviationKind,
    val text: String,
    /** Everything the «Почему?» sheet needs to show the statement's basis. */
    val numbers: List<DeviationNumber>,
)

/** Statistics of the visible window, in the units the chart already holds. */
data class WindowSummary(
    val medianMicroSvH: Float,
    /** Q25 and Q75 of the window. */
    val p25MicroSvH: Float,
    val p75MicroSvH: Float,
    val maxMicroSvH: Float,
    /** Independent observed values behind the quantiles (sub-buckets). */
    val observations: Int,
    /** Measured seconds inside the window. */
    val measuredSeconds: Long,
    /** Measured seconds spent at or above the profile's P90. */
    val secondsAboveProfileP90: Long,
)

/**
 * Descriptive current-vs-baseline statements (graph spec §35).
 *
 * ## Why this exists and what it deliberately does not do
 *
 * Graph spec §35 draws a hard line: from quantile overlap alone one may say
 * «медиана сместилась», «разброс шире», «вне исторического P10–P90 профиля»,
 * «короткий всплеск» — and one may **not** derive «+4.2σ» or «statistically
 * significant». That is the whole content of this object: comparisons of order
 * statistics, stated as comparisons of order statistics. The candidate formal
 * test lives in [AnomalyStatistics] behind
 * [ExperimentalRadiationStatistics] and is not consulted here.
 *
 * ## Scientific release gate (spec §24 / graph spec §41)
 *
 * 1. **Formula.** Pure comparisons: median vs the profile's [Baseline]
 *    P25/P75 and P10/P90; window IQR vs profile IQR scaled by
 *    [SPREAD_FACTOR]; window max vs profile P90 together with the measured
 *    time spent above it.
 * 2. **Assumptions.** (a) the baseline is mature and was learned from admitted
 *    samples only (spec §4.2) — the caller passes `BaselineState.Active` data;
 *    (b) both sides are order statistics of the same quantity in the same
 *    units; (c) nothing here assumes a distribution shape, which is why no
 *    probability is attached to any statement.
 * 3. **Units.** µSv/h for levels, seconds for durations; carried explicitly in
 *    [DeviationNumber.unit].
 * 4. **Reference.** Graph spec §35 (allowed statements), §39 (terminology).
 * 5. **Validation data.** Deterministic cases in `DescriptiveDeviationTest`;
 *    no RC-110 recording is needed because no inference is made — these are
 *    comparisons, not detections.
 * 6. **Limitations.** A statement is **not** a detection: it describes the
 *    window on screen. The comparisons are made on autocorrelated data, which
 *    is exactly why they carry no evidence claim. Thresholds
 *    ([SPREAD_FACTOR], [SHORT_SPIKE_MAX_SECONDS]) are engineering choices for
 *    quiet UI, not scientific constants, and a statement appearing or
 *    disappearing near them means nothing physical.
 * 7. **Tests.** `app/src/test/.../analysis/DescriptiveDeviationTest.kt`
 *    (wording is pinned: forbidden words are asserted absent).
 * 8. **Algorithm version.** [AlgorithmVersions.DESCRIPTIVE_DEVIATION].
 * 9. **User-facing meaning.** «Что изменилось по сравнению с обычным фоном
 *    этого профиля» — a description with its numbers attached, at evidence
 *    level CALCULATED (both sides are computed order statistics), never
 *    STATISTICALLY DETECTED.
 *
 * Pure JVM; no Android dependencies.
 */
object DescriptiveDeviation {

    const val ALGORITHM_VERSION = AlgorithmVersions.DESCRIPTIVE_DEVIATION

    /**
     * The window's middle half must be this many times wider than the
     * profile's before «разброс шире» is said.
     *
     * **Engineering parameter.** Rationale: an IQR estimated from a short
     * window swings by tens of percent purely from counting noise, so a factor
     * near 1 would flicker; 1.5 is the round number above that flicker. No
     * statistical meaning is attached to it.
     */
    const val SPREAD_FACTOR = 1.5f

    /**
     * Above the profile P90 for no longer than this is called «короткий
     * всплеск» rather than a new level.
     *
     * **Engineering parameter**, aligned with the alarm persistence default
     * (120 s, [app.radiacode.baseline.AlarmThresholds]) so the two features do
     * not tell the user different stories about the same minute.
     */
    const val SHORT_SPIKE_MAX_SECONDS = 120L

    /** Fewer independent values than this and nothing is said at all. */
    const val MIN_OBSERVATIONS = 20

    /** What the UI says when no statement fires. */
    fun usualText(s: MonitorStrings = MonitorRu): String = s.deviationUsual

    /** What the UI says when the window is too thin to compare. */
    fun notEnoughText(s: MonitorStrings = MonitorRu): String = s.deviationNotEnough

    /**
     * Statements about [window] against [baseline], strongest first. Empty
     * means «в обычном диапазоне этого профиля» ([usualText]); null means
     * there is not enough measurement to say anything ([notEnoughText]).
     */
    fun statements(
        window: WindowSummary,
        baseline: Baseline,
        s: MonitorStrings = MonitorRu,
    ): List<DeviationStatement>? {
        if (window.observations < MIN_OBSERVATIONS) return null
        val out = ArrayList<DeviationStatement>(4)

        val outsideBand = window.medianMicroSvH > baseline.doseHighMicroSvH ||
            window.medianMicroSvH < baseline.doseLowMicroSvH
        if (outsideBand) {
            val above = window.medianMicroSvH > baseline.doseHighMicroSvH
            out += DeviationStatement(
                kind = DeviationKind.OUTSIDE_PROFILE_BAND,
                text = if (above) s.deviationAboveBand else s.deviationBelowBand,
                numbers = listOf(
                    rate(s.numberWindowMedian, window.medianMicroSvH),
                    rate(s.numberProfileP10, baseline.doseLowMicroSvH),
                    rate(s.numberProfileP90, baseline.doseHighMicroSvH),
                ),
            )
        } else {
            // Only when the band statement did not fire: two phrases about the
            // same shift would read as two findings.
            val shifted = window.medianMicroSvH > baseline.doseP75MicroSvH ||
                window.medianMicroSvH < baseline.doseP25MicroSvH
            if (shifted) {
                val up = window.medianMicroSvH > baseline.doseP75MicroSvH
                out += DeviationStatement(
                    kind = DeviationKind.MEDIAN_SHIFT,
                    text = if (up) s.deviationShiftedUp else s.deviationShiftedDown,
                    numbers = listOf(
                        rate(s.numberWindowMedian, window.medianMicroSvH),
                        rate(s.numberProfileMedian, baseline.doseMedianMicroSvH),
                        rate(s.numberProfileP25, baseline.doseP25MicroSvH),
                        rate(s.numberProfileP75, baseline.doseP75MicroSvH),
                    ),
                )
            }
        }

        val windowIqr = window.p75MicroSvH - window.p25MicroSvH
        val profileIqr = baseline.doseP75MicroSvH - baseline.doseP25MicroSvH
        if (profileIqr > 0f && windowIqr > SPREAD_FACTOR * profileIqr) {
            out += DeviationStatement(
                kind = DeviationKind.SPREAD_WIDER,
                text = s.deviationSpreadWider,
                numbers = listOf(
                    rate(s.numberWindowIqr, windowIqr.toDouble()),
                    rate(s.numberProfileIqr, profileIqr.toDouble()),
                ),
            )
        }

        val aboveSeconds = window.secondsAboveProfileP90
        if (window.maxMicroSvH > baseline.doseHighMicroSvH &&
            aboveSeconds in 1..SHORT_SPIKE_MAX_SECONDS
        ) {
            out += DeviationStatement(
                kind = DeviationKind.SHORT_SPIKE,
                text = s.deviationShortSpike,
                numbers = listOf(
                    rate(s.numberWindowMax, window.maxMicroSvH),
                    rate(s.numberProfileP90, baseline.doseHighMicroSvH),
                    DeviationNumber(
                        s.numberSecondsAboveP90,
                        aboveSeconds.toDouble(),
                        DeviationUnit.SECONDS,
                    ),
                    DeviationNumber(
                        s.numberMeasuredInWindow,
                        window.measuredSeconds.toDouble(),
                        DeviationUnit.SECONDS,
                    ),
                ),
            )
        }
        return out
    }

    /** Headline for the statements: the honest «nothing to say» is a first-class answer. */
    fun headline(
        statements: List<DeviationStatement>?,
        s: MonitorStrings = MonitorRu,
    ): String = when {
        statements == null -> notEnoughText(s)
        statements.isEmpty() -> usualText(s)
        else -> statements.first().text
    }

    private fun rate(label: String, value: Float) = rate(label, value.toDouble())

    private fun rate(label: String, value: Double) =
        DeviationNumber(label, value, DeviationUnit.MICRO_SV_PER_HOUR)
}
