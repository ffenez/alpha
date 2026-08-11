package app.radiacode.analysis

import kotlin.math.sqrt

/** Жёсткость одного окна: (мкрем/ч)/(имп/с) и её 1σ. */
data class HardnessValue(
    /** H = Ḋ/R in (µrem/h)/cps — the vendor's dimensionless coefficient. */
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
 * H = (мкрем/ч) / (имп/с)
 * ```
 *
 * and says it lets one follow changes of the **spectral character** of the
 * radiation with the intensity component removed (Android app manual; the
 * device manual notes firmware ≥ 3.0 and that RadiaCode Zero does not compute
 * it).
 *
 * ## What it is, and what it is not
 *
 * H is a **dose quantity per unit count rate**. It is related to the energy
 * composition of the detected radiation — but it is **not** the mean photon
 * energy and **not** the mean energy absorbed in the crystal. The numerator is
 * not «energy deposited in the scintillator»: it is a dosimetric estimate the
 * instrument derives through its own energy response, so calling H an average
 * energy would attribute to it a physical meaning it does not have. The vendor
 * is careful about this too — the manual speaks of *which energies prevail*,
 * never of an average energy. No screen in this app may print «средняя
 * энергия: 0,52»; `HardnessTest` pins that wording out.
 *
 * ## Why it is useful anyway
 *
 * It is **designed to suppress the influence of overall intensity**. If the
 * shape of the field stays the same and its intensity grows k-fold, then
 * R₂ = k·R₁ and, approximately, Ḋ₂ = k·Ḋ₁, so
 *
 * ```text
 * H₂ = k·Ḋ₁ / (k·R₁) = H₁
 * ```
 *
 * — walking towards the same source moves counts and dose together and leaves
 * H roughly where it was, while a change of the energy structure of the field
 * moves the ratio. «Roughly» is the operative word: statistical noise, the
 * detector's energy response and the uncertainty of the dose-rate estimate all
 * keep H from being exactly constant, so this is a *tendency to lean on*, not
 * an orthogonality to rely on.
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formula.** H = D̄ / R̄ over a window, with D̄ the mean dose rate in µrem/h
 *    (= µSv/h × 100) and R̄ the mean count rate in s⁻¹. Uncertainty by the
 *    quotient rule:
 *
 *    ```text
 *    σ_H / H = √( (σ_D/D̄)² + (σ_R/R̄)² ),   σ_R = √(N)/t
 *    ```
 *
 *    σ_D is the instrument's own reported error of the dose rate, so the app
 *    never invents an uncertainty for a value it did not derive.
 * 2. **Assumptions.** Both rates are formed from the **same registered
 *    events**, so they are correlated — and the covariance between the
 *    firmware's dose-rate and count-rate algorithms is **not published**.
 *    Without it neither the sign nor the size of the correction to σ_H can be
 *    stated: the σ above is an *estimate computed without covariance*, not a
 *    bound, and it must not be described as conservative. Measuring that
 *    correlation on a real RC-110 is a field-protocol item.
 * 3. **Units.** H is (µrem/h)/(s⁻¹) — comparable to the official app's number
 *    only in exactly these units.
 * 4. **Reference.** RadiaCode Android app manual (the definition and the
 *    «follow spectral changes without the intensity component» wording); RC-10x
 *    device manual (firmware requirement). The vendor does not publish how the
 *    firmware derives the displayed value beyond this ratio, so the number is
 *    reproduced from the two rates the device reports, not reverse-engineered.
 * 5. **Validation data.** `HardnessTest`: the ratio follows dose and inverts
 *    with count rate, a k-fold intensity change leaves it put, σ grows when
 *    either input is noisy, a thin or silent window returns null instead of a
 *    division artefact. **Not yet compared against the number the official app
 *    shows on the same device**, and the dose/count covariance is unmeasured —
 *    both need the instrument.
 * 6. **Limitations.** H is a *derived* view of dose and count rate: it adds no
 *    information the two do not already carry, which is why the fingerprint
 *    detector gives it no statistical vote of its own (ADR 005). It is not a
 *    mean photon energy, not a nuclide and not a measure of danger.
 * 7. **Tests.** `app/src/test/.../analysis/HardnessTest.kt`.
 * 8. **Algorithm version.** [AlgorithmVersions.HARDNESS].
 * 9. **User-facing meaning.** [EXPLANATION].
 */
object Hardness {

    const val ALGORITHM_VERSION = AlgorithmVersions.HARDNESS

    /** 1 µSv/h = 100 µrem/h — the vendor's coefficient is defined in µrem/h. */
    const val MICRO_REM_PER_MICRO_SV = 100.0

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
        "Жёсткость — дозовая величина на единицу скорости счёта, (мкрем/ч)/(имп/с). " +
            "Она связана с тем, какие энергии преобладают в регистрируемом излучении, " +
            "но это не средняя энергия фотона и не мера опасности."

    /**
     * Вторая строка — зачем она нужна. Отдельно от [EXPLANATION], потому что
     * первая говорит, что это, а эта — что с этим делать.
     */
    const val PURPOSE =
        "Она подавляет влияние общей интенсивности: если поле то же, а его стало " +
            "больше, доза и счёт растут вместе, а отношение остаётся примерно " +
            "прежним. Точного постоянства нет — мешают статистический шум, " +
            "энергетическая характеристика детектора и погрешность оценки дозы."

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

        val value = doseRateMicroSvH * MICRO_REM_PER_MICRO_SV / countRate
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

    /**
     * Что именно означает показанная σ. Печатается там, где показывается
     * погрешность: она посчитана **без ковариации** дозы и счёта, которая не
     * опубликована, поэтому это оценка, а не граница.
     */
    const val SIGMA_CAVEAT =
        "Погрешность посчитана по правилу частного. Доза и счёт формируются из " +
            "одних и тех же событий, а ковариация их алгоритмов в приборе не " +
            "опубликована — поэтому это оценка, а не гарантированная граница."

    /** «0,52» — two decimals, the way the official app shows it. */
    fun format(value: Double): String =
        String.format(java.util.Locale.US, "%.2f", value).replace('.', ',')
}
