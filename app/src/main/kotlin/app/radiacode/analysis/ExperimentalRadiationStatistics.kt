package app.radiacode.analysis

/**
 * Marks a **candidate** statistic that has not passed the scientific release
 * gate (spec §24, graph spec §36, §41).
 *
 * Anything wearing this annotation may be computed, logged and validated, but
 * its output must never reach the user as a claim: no «статистически значимо»,
 * no p-value, no σ, no percentage of confidence. The UI is allowed only the
 * descriptive statements of [DescriptiveDeviation] until the gate is passed —
 * which for a current-vs-baseline test means measured false-positive rate
 * under continuous scanning and measured detection power on real RC-110
 * recordings (`docs/analysis/trend-and-anomaly.md`).
 *
 * Opting in is deliberate friction: a call site that writes
 * `@OptIn(ExperimentalRadiationStatistics::class)` has stated that it is
 * research or validation code, not product UI.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Candidate statistic: not validated on RC-110 recordings, must not " +
        "reach the user as a claim (graph spec §36).",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalRadiationStatistics
