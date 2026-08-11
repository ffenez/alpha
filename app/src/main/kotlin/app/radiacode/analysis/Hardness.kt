package app.radiacode.analysis

import kotlin.math.sqrt

/** Жёсткость одного окна: (мкР/ч)/(имп/с) и её 1σ. */
data class HardnessValue(
    /** H = D/R in (µR/h)/cps — the vendor's dimensionless coefficient. */
    val value: Double,
    /** 1σ of H, propagated from both inputs. */
    val sigma: Double,
    /** Mean dose rate of the window, µSv/h (what H was divided from). */
    val doseRateMicroSvH: Double,
    /** Mean count rate of the window, s⁻¹ (what H was divided by). */
    val countRate: Double,
    /** Counts the window rests on — the weight of the estimate. */
    val counts: Double,
)

/**
 * **Жёсткость излучения** — the vendor's own coefficient, computed the way the
 * vendor documents it.
 *
 * The RadiaCode manual defines it as a dimensionless coefficient
 *
 * ```text
 * H = (мкР/ч) / (имп/с)
 * ```
 *
 * and describes it as characterising *which energies prevail in the spectrum,
 * letting one watch spectral changes free of the intensity component*
 * (Android app manual; the device manual notes it needs firmware ≥ 3.0 and is
 * not computed for RadiaCode Zero).
 *
 * That definition is worth taking literally, because it explains what the
 * number is and what it is not. Dose per count is the **average energy
 * deposited per registered event**: it rises when harder photons dominate and
 * is unmoved by how many of them arrive. It is therefore not an independent
 * measurement — it is a **ratio of the two quantities the app already shows**,
 * and it is exactly as trustworthy as they are.
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formula.** H = D̄ / R̄ over a window, with D̄ the mean dose rate in µR/h
 *    (= µSv/h × 100) and R̄ the mean count rate in s⁻¹. Uncertainty by
 *    propagation of a quotient:
 *
 *    ```text
 *    σ_H / H = √( (σ_D/D̄)² + (σ_R/R̄)² ),   σ_R = √(N)/t
 *    ```
 *
 *    σ_D is the instrument's own reported error of the dose rate (percent), so
 *    the app never invents an uncertainty for a value it did not derive.
 * 2. **Assumptions.** Both rates come from the same window of the same 1 Hz
 *    stream; the two are correlated (they count the same photons), and that
 *    correlation is **not** modelled — the quotient rule assumes independence,
 *    which makes σ_H a conservative over-estimate here rather than an
 *    optimistic one. Stated because an unstated assumption is the failure this
 *    codebase spends the most effort avoiding.
 * 3. **Units.** H is (µR/h)/(s⁻¹) — dimensionless only by convention, and only
 *    comparable to the official app's number in exactly these units.
 * 4. **Reference.** RadiaCode Android app manual (definition and the
 *    «intensity-free spectral change» wording); RC-10x device manual (firmware
 *    requirement). The vendor does **not** publish how the firmware derives its
 *    displayed value beyond this ratio, so the number is reproduced, not
 *    reverse-engineered: we compute the documented ratio from the two rates the
 *    device reports over BLE.
 * 5. **Validation data.** `HardnessTest`: the ratio follows dose and inverts
 *    with count rate, σ grows when either input is noisy, a thin or silent
 *    window returns null instead of a division artefact. **Not yet compared
 *    against the number the official app shows on the same device** — that is
 *    the one check that cannot be done without the instrument (field protocol).
 * 6. **Limitations.** H is a *derived* view of dose and count rate: it adds no
 *    information the two do not already carry, and it moves whenever either
 *    does — a detector that reports dose through its own energy compensation
 *    will move H with the compensation, not only with the spectrum. It is not
 *    a mean photon energy, not a nuclide, not a measure of danger.
 * 7. **Tests.** `app/src/test/.../analysis/HardnessTest.kt`.
 * 8. **Algorithm version.** [AlgorithmVersions.HARDNESS].
 * 9. **User-facing meaning.** [EXPLANATION].
 */
object Hardness {

    const val ALGORITHM_VERSION = AlgorithmVersions.HARDNESS

    /** 1 µSv/h = 100 µR/h — the vendor's coefficient is defined in µR/h. */
    const val MICRO_R_PER_MICRO_SV = 100.0

    /**
     * Fewest counts in a window before a ratio is reported.
     *
     * **Engineering parameter.** At 100 counts the count-rate term alone
     * carries 10 % — dividing by a number that noisy produces a line that
     * describes its own denominator.
     */
    const val MIN_COUNTS = 100.0

    /** Below this count rate the quotient is not a quantity but a division. */
    const val MIN_COUNT_RATE = 0.5

    /** The sentence that travels with the number wherever it is shown. */
    const val EXPLANATION =
        "Жёсткость — отношение мощности дозы к скорости счёта: сколько дозы " +
            "приходится на один зарегистрированный импульс. Она описывает, какие " +
            "энергии преобладают, и не зависит от того, много их или мало. " +
            "Это не мера опасности и не средняя энергия фотона."

    /**
     * Жёсткость of a window.
     *
     * [doseRateMicroSvH] and [countRate] are the **means over the same window**;
     * [seconds] is its exposure, [doseErrPercent] the instrument's own reported
     * error of the dose rate (null = unknown, then only the counting term is
     * propagated and the σ is understated — the caller is told by [sigmaKnown]).
     */
    fun of(
        doseRateMicroSvH: Double,
        countRate: Double,
        seconds: Double,
        doseErrPercent: Double? = null,
    ): HardnessValue? {
        if (!doseRateMicroSvH.isFinite() || !countRate.isFinite()) return null
        if (countRate < MIN_COUNT_RATE || seconds <= 0.0 || doseRateMicroSvH < 0.0) return null
        val counts = countRate * seconds
        if (counts < MIN_COUNTS) return null

        val value = doseRateMicroSvH * MICRO_R_PER_MICRO_SV / countRate
        val relativeCount = 1.0 / sqrt(counts)
        val relativeDose = (doseErrPercent ?: 0.0) / 100.0
        val relative = sqrt(relativeDose * relativeDose + relativeCount * relativeCount)
        return HardnessValue(
            value = value,
            sigma = value * relative,
            doseRateMicroSvH = doseRateMicroSvH,
            countRate = countRate,
            counts = counts,
        )
    }

    /** True when the σ of [of] includes the instrument's dose error, not only counting. */
    fun sigmaKnown(doseErrPercent: Double?): Boolean = doseErrPercent != null

    /** «0,52» — two decimals, the way the official app shows it. */
    fun format(value: Double): String =
        String.format(java.util.Locale.US, "%.2f", value).replace('.', ',')
}
