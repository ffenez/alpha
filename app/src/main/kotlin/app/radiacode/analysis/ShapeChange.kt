package app.radiacode.analysis

import kotlin.math.roundToInt

/** What a shape comparison is allowed to conclude. */
enum class ShapeVerdict {
    /** Too few counts on one side; nothing is claimed either way. */
    NOT_ENOUGH_DATA,

    /** The two shapes agree within counting statistics. */
    CONSISTENT,

    /** The shapes differ by more than counting statistics explains. */
    CHANGED,
}

/** One comparison of two accumulated spectra, shape only. */
data class ShapeComparison(
    val referenceCounts: Double,
    val excursionCounts: Double,
    /** Bins after merging; the degrees of freedom are this minus one. */
    val bins: Int,
    val chiSquare: Double,
    val degreesOfFreedom: Int,
    /** Wilson–Hilferty z of the statistic; 0 = perfect agreement. */
    val z: Double,
    val verdict: ShapeVerdict,
) {
    val changed: Boolean get() = verdict == ShapeVerdict.CHANGED
}

/**
 * Did the **shape** of the spectrum change, not just its intensity
 * (search redesign §13)?
 *
 * ## Why shape is a separate question
 *
 * A count-rate excursion says the instrument is receiving more photons. It
 * says nothing about *which* photons: moving closer to the same natural
 * background raises the rate with an unchanged spectrum, while a source adds
 * counts at its own energies. So this is a strictly separate, strictly
 * research-level observation — it is never merged into the count-rate verdict
 * and it never names a nuclide, which needs peaks and statistics this test
 * does not have (§12, §13).
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formula.** Two-sample χ² test of homogeneity on binned counts. With
 *    reference counts x₁ᵢ (total N₁) and excursion counts x₂ᵢ (total N₂):
 *
 *    ```text
 *    χ² = Σᵢ (N₂·x₁ᵢ − N₁·x₂ᵢ)² / (N₁·N₂·(x₁ᵢ + x₂ᵢ)),   ν = k − 1
 *    ```
 *
 *    The totals cancel by construction, so a spectrum that merely got
 *    **brighter** with the same shape produces no signal at all — which is
 *    exactly the property this question needs. Bins are merged from the
 *    instrument's energy bands until each holds at least [MIN_BIN_COUNTS]
 *    combined counts, because χ² on nearly-empty bins is not χ².
 * 2. **Assumptions.** Both samples are multinomial over the same binning
 *    (the same [Spectrogram] band grid, so no rebinning and no fractional
 *    counts), and the two intervals are disjoint in time. Dead time and
 *    pile-up are ignored, as everywhere else at moderate rates.
 * 3. **Units.** Counts are dimensionless; χ² and z are dimensionless.
 * 4. **Reference.** The classical two-sample χ² homogeneity test for binned
 *    data; the normal approximation is [AbAnalysis.wilsonHilferty], the same
 *    one the A/B comparison uses, so two parts of the app cannot disagree
 *    about what «z» means.
 * 5. **Validation data.** `ShapeChangeTest`: the same shape scaled up is
 *    `CONSISTENT` regardless of brightness, a shifted line is `CHANGED`, thin
 *    data is `NOT_ENOUGH_DATA` rather than a coin flip.
 * 6. **Limitations.** [MIN_BIN_COUNTS], [MIN_TOTAL_COUNTS] and [Z_CHANGED] are
 *    **engineering parameters**. A `CHANGED` verdict means «the two accumulated
 *    spectra are not two samples of one distribution» — it does not identify
 *    anything, does not imply a source, and is deliberately shown only as an
 *    invitation to open the spectrum.
 * 7. **Tests.** `app/src/test/.../analysis/ShapeChangeTest.kt`.
 * 8. **Algorithm version.** [AlgorithmVersions.SHAPE_CHANGE].
 * 9. **User-facing meaning.** «Изменился не только счёт, но и форма спектра» —
 *    an invitation to look, never a finding.
 */
object ShapeChange {

    const val ALGORITHM_VERSION = AlgorithmVersions.SHAPE_CHANGE

    /**
     * Minimal combined counts per bin after merging. **Engineering parameter**:
     * the χ² approximation is usually quoted as needing expected counts around
     * 5; 10 combined is that with margin, and merging costs only resolution.
     */
    const val MIN_BIN_COUNTS = 10.0

    /**
     * Below this on either side the question is not asked at all.
     * **Engineering parameter**: with fewer counts the test has no power and
     * would answer «consistent» to everything, which reads as evidence of
     * sameness and is not.
     */
    const val MIN_TOTAL_COUNTS = 300.0

    /** Fewest merged bins that still describe a *shape*. Engineering parameter. */
    const val MIN_BINS = 4

    /**
     * z above which the shapes are called different. **Engineering parameter**,
     * the same 3σ-ish level the rest of the app uses for «strong evidence»,
     * kept high because this line invites the user to act.
     */
    const val Z_CHANGED = 3.0

    /**
     * Compares the shape of [reference] with [excursion] — both are counts per
     * energy band on the same grid.
     */
    fun compare(reference: DoubleArray, excursion: DoubleArray): ShapeComparison {
        require(reference.size == excursion.size) { "spectra are on different grids" }
        val n1 = reference.sum()
        val n2 = excursion.sum()
        val empty = ShapeComparison(
            referenceCounts = n1,
            excursionCounts = n2,
            bins = 0,
            chiSquare = 0.0,
            degreesOfFreedom = 0,
            z = 0.0,
            verdict = ShapeVerdict.NOT_ENOUGH_DATA,
        )
        if (n1 < MIN_TOTAL_COUNTS || n2 < MIN_TOTAL_COUNTS) return empty

        val (a, b) = merge(reference, excursion)
        if (a.size < MIN_BINS) return empty

        var chiSquare = 0.0
        for (i in a.indices) {
            val combined = a[i] + b[i]
            if (combined <= 0.0) continue
            val diff = n2 * a[i] - n1 * b[i]
            chiSquare += diff * diff / (n1 * n2 * combined)
        }
        val df = a.size - 1
        // One-sided by construction: only an excess over ν is evidence of a
        // difference; agreement better than chance is not evidence of anything.
        val z = AbAnalysis.wilsonHilferty(chiSquare, df).coerceAtLeast(0.0)
        return ShapeComparison(
            referenceCounts = n1,
            excursionCounts = n2,
            bins = a.size,
            chiSquare = chiSquare,
            degreesOfFreedom = df,
            z = z,
            verdict = if (z >= Z_CHANGED) ShapeVerdict.CHANGED else ShapeVerdict.CONSISTENT,
        )
    }

    /**
     * Merges neighbouring bands until every bin holds at least
     * [MIN_BIN_COUNTS] combined counts. A trailing bin that stays too thin is
     * folded into its predecessor rather than dropped: dropping it would throw
     * away exactly the high-energy end where a new source is most visible.
     */
    private fun merge(a: DoubleArray, b: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val outA = ArrayList<Double>()
        val outB = ArrayList<Double>()
        var accA = 0.0
        var accB = 0.0
        for (i in a.indices) {
            accA += a[i]
            accB += b[i]
            if (accA + accB >= MIN_BIN_COUNTS) {
                outA += accA
                outB += accB
                accA = 0.0
                accB = 0.0
            }
        }
        if (accA + accB > 0.0 && outA.isNotEmpty()) {
            outA[outA.size - 1] = outA.last() + accA
            outB[outB.size - 1] = outB.last() + accB
        }
        return outA.toDoubleArray() to outB.toDoubleArray()
    }

    /** «форма спектра: z = 4,1 по 18 корзинам» — the research one-liner. */
    fun detail(comparison: ShapeComparison): String = when (comparison.verdict) {
        ShapeVerdict.NOT_ENOUGH_DATA ->
            "спектральных данных пока мало: " +
                "${comparison.referenceCounts.roundToInt()} и " +
                "${comparison.excursionCounts.roundToInt()} импульсов"
        else ->
            "χ² по ${comparison.bins} корзинам, z = " +
                "${(comparison.z * 10).roundToInt() / 10.0}".replace('.', ',')
    }
}
