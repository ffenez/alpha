package app.radiacode.analysis

/**
 * Dose projection (spec §6): a *mathematical extrapolation* of the measured
 * external photon dose rate, never an annual effective dose of a person.
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formula.** Accumulated dose D = ∫Ḋ(t)dt; with a practically constant
 *    dose rate D ≈ Ḋ·t. The mean measured rate over a window is taken as the
 *    integral divided by the *measured* time, Ḋ̄ = D_window / t_measured, and the
 *    projection is D_horizon = Ḋ̄ · t_horizon.
 * 2. **Assumptions.** (a) the dose rate stays what it was measured to be —
 *    that is the whole content of the statement; (b) the measured window is
 *    representative (gaps in measurement are excluded, not interpolated:
 *    t_measured counts only seconds with real samples); (c) the instrument's
 *    external photon dose rate is what it reports — the app never converts CPS
 *    to dose itself (spec §1, §23).
 * 3. **Units.** Ḋ̄ — µSv/h; t — hours; D — µSv. Dimensional check:
 *    [µSv/h]·[h] = [µSv] (pinned by tests).
 * 4. **Reference.** Spec §6; [HOURS_PER_YEAR] = 365.25 d × 24 h (Julian year).
 * 5. **Validation.** Synthetic checks in `DoseProjectionTest`; nothing here
 *    depends on device specifics, so no RC-110 dataset is required.
 * 6. **Limitations.** This is *not* an annual effective dose: it excludes
 *    internal exposure, radon, medical procedures, cosmic-ray exposure while
 *    flying outside the measured window and any time the instrument was not
 *    measuring or not carried. It also assumes the future looks like the past
 *    window, which nothing guarantees.
 * 7. **Tests.** `app/src/test/.../analysis/DoseProjectionTest.kt`.
 * 8. **Algorithm version.** [AlgorithmVersions.DOSE_PROJECTION].
 * 9. **User-facing meaning.** «Если средняя измеренная внешняя фотонная
 *    мощность дозы останется такой же — за год ≈ X мкЗв». Forbidden wordings:
 *    «годовая доза», «годовая эффективная доза», «вы получите».
 *
 * Pure JVM; no Android dependencies.
 */
object DoseProjection {

    const val ALGORITHM_VERSION = AlgorithmVersions.DOSE_PROJECTION

    /** Julian year: 365.25 d × 24 h. */
    const val HOURS_PER_YEAR = 8766.0

    const val HOURS_PER_DAY = 24.0
    const val HOURS_PER_MONTH = HOURS_PER_YEAR / 12.0

    /** Below this measured time the mean rate is too thin to extrapolate. */
    const val MIN_MEASURED_SECONDS = 3600L

    data class Projection(
        /** Mean measured dose rate over the window, µSv/h. */
        val meanRateMicroSvPerHour: Double,
        /** Seconds of *actual measurement* behind the mean (not wall time). */
        val measuredSeconds: Long,
        /** Extrapolation horizon, hours. */
        val horizonHours: Double,
        /** Ḋ̄ · t_horizon, µSv. */
        val doseMicroSv: Double,
    )

    /**
     * Mean measured dose rate: µSv accumulated over [measuredSeconds] of real
     * samples → µSv/h. Null when there is nothing to average.
     */
    fun meanRateMicroSvPerHour(doseMicroSv: Double, measuredSeconds: Long): Double? {
        if (measuredSeconds <= 0L || !doseMicroSv.isFinite() || doseMicroSv < 0.0) return null
        return doseMicroSv / (measuredSeconds / 3600.0)
    }

    /** D = Ḋ·t. [meanRateMicroSvPerHour] in µSv/h, [horizonHours] in h → µSv. */
    fun project(meanRateMicroSvPerHour: Double, horizonHours: Double): Double {
        if (!meanRateMicroSvPerHour.isFinite() || meanRateMicroSvPerHour < 0.0) return 0.0
        if (!horizonHours.isFinite() || horizonHours <= 0.0) return 0.0
        return meanRateMicroSvPerHour * horizonHours
    }

    /**
     * Full projection from an integrated window. Null when the window carries
     * less than [MIN_MEASURED_SECONDS] of real measurement — extrapolating a
     * year from a few minutes would be theatre, not arithmetic.
     */
    fun fromIntegral(
        doseMicroSv: Double,
        measuredSeconds: Long,
        horizonHours: Double = HOURS_PER_YEAR,
    ): Projection? {
        if (measuredSeconds < MIN_MEASURED_SECONDS) return null
        val mean = meanRateMicroSvPerHour(doseMicroSv, measuredSeconds) ?: return null
        return Projection(
            meanRateMicroSvPerHour = mean,
            measuredSeconds = measuredSeconds,
            horizonHours = horizonHours,
            doseMicroSv = project(mean, horizonHours),
        )
    }
}
