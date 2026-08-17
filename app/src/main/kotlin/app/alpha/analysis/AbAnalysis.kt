package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.ln
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * A/B experiment math (spec §9, §16): background subtraction with time
 * scaling, Poisson-appropriate statistics and the strictly limited verdict
 * vocabulary of spec §8.
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formulas.**
 *    - Net counts with time scaling: `net = G − B·(t_G/t_B)`, where G is the
 *      gross measurement (run A / object) accumulated for t_G and B the
 *      background (run B) accumulated for t_B. Scaling B by the *time ratio*
 *      puts both on the same exposure before subtraction — the whole point of
 *      §9 («не простое визуальное вычитание кривых»).
 *    - Uncertainty per IAEA [R4]: `σ_net = √(G + B·(t_G/t_B)²)`.
 *      **Derivation:** G and B are independent Poisson counts, so
 *      Var(G) = G and Var(B) = B (the observed count estimates the variance,
 *      spec §5). The time ratio r = t_G/t_B is a constant, therefore
 *      Var(net) = Var(G − rB) = Var(G) + r²·Var(B) = G + r²B, and σ_net is its
 *      square root. Nothing here assumes the difference is Gaussian — that is a
 *      separate step below.
 *    - Rate form (identical statistic, different units):
 *      R_A = G/t_G, R_B = B/t_B, ΔR = R_A − R_B = net/t_G,
 *      σ_ΔR = √(G/t_G² + B/t_B²) = σ_net/t_G.
 *    - **χ²-like statistic** (adequate counts): z = net/σ_net, i.e. the
 *      difference in units of its own 1σ. Its square is the one-degree-of-
 *      freedom χ² of the two-sample comparison.
 *    - **Poisson likelihood-ratio statistic** (low counts): under H₀ both runs
 *      observe the same rate λ, so the maximum-likelihood expectations are
 *      Ĝ = (G+B)·t_G/(t_G+t_B) and B̂ = (G+B)·t_B/(t_G+t_B), and the deviance
 *      is D = 2·[G·ln(G/Ĝ) + B·ln(B/B̂)] (with 0·ln0 ≡ 0). D is asymptotically
 *      χ²(1), so the signed significance is z = sign(ΔR)·√D.
 *    - **Full spectrum:** the same per-channel statistic summed over channels;
 *      the total (deviance or Pearson χ²) has ν = number of channels with
 *      counts, converted to a significance by the Wilson–Hilferty
 *      approximation z = ((χ²/ν)^⅓ − (1 − 2/(9ν))) / √(2/(9ν)). That test is
 *      one-sided: only a statistic *above* its expectation ν is evidence of a
 *      difference, so the reported z is floored at 0.
 * 2. **Assumptions.** Independent Poisson counting in both runs (spec §5, no
 *    dead-time/pile-up correction); the *same documented geometry* in A and B —
 *    the app records it as text and shows it during B, but cannot verify it;
 *    a shared energy calibration for per-channel and per-window comparisons
 *    (checked against [SpectrumCompare.CALIBRATION_TOLERANCE_KEV]); channels
 *    treated as independent (the detector response correlates neighbours, which
 *    makes the full-spectrum ν optimistic — see limitations).
 * 3. **Units.** counts (dimensionless), seconds, counts/s; z, χ², deviance —
 *    dimensionless.
 * 4. **Reference.** IAEA net-peak-area/uncertainty guidance [R4]; NIST
 *    Poisson-counting basis [R2], [R3]; low-count likelihood methods [R5].
 * 5. **Validation.** Synthetic pairs in `AbAnalysisTest` (identical runs ⇒
 *    z ≈ 0; known excess ⇒ known z; equal times reduce σ_net to √(G+B); the
 *    method switch; verdict thresholds). Real RC-110 A/B pairs: **pending** —
 *    the screen is marked «экспериментальная функция» until then.
 * 6. **Limitations.** Verdicts describe *statistical* difference between two
 *    measurements, never danger and never «what was found» (spec §2); the
 *    full-spectrum test treats channels as independent, so its ν is optimistic
 *    and its z should be read as an ordering, not a p-value; the dose-rate
 *    comparison uses the spread of 1 Hz readings, which are serially
 *    correlated, so its uncertainty is a lower bound; nothing corrects for
 *    detector efficiency, so «net» is net *counts in this instrument*.
 * 7. **Tests.** `app/src/test/.../analysis/AbAnalysisTest.kt`.
 * 8. **Algorithm version.** [AlgorithmVersions.AB_ANALYSIS].
 * 9. **User-facing meaning.** Three verdicts only — `consistent` («различий не
 *    видно»), `changed` («различие есть») and `strong evidence of change`
 *    («сильные свидетельства различия»). A «% похожести» is never produced:
 *    spec §8 forbids it until a metric with a defined statistical meaning is
 *    validated.
 *
 * Pure JVM; no Android dependencies.
 */
object AbAnalysis {

    const val ALGORITHM_VERSION = AlgorithmVersions.AB_ANALYSIS

    /**
     * Method switch. Below this many counts in either run the Gaussian
     * σ = √N of a Poisson variable is a poor description of its tail (NIST on
     * low-level counting [R3], weak Poisson signals [R5]); at or above it the
     * normal approximation is standard practice. 25 counts is the conventional
     * «λ ≥ 20–25» rule of thumb — it is a documented parameter of this
     * algorithm, not a law of nature, and bumping it bumps
     * [AlgorithmVersions.AB_ANALYSIS].
     */
    const val NORMAL_APPROX_MIN_COUNTS = 25.0

    /** |z| below this — the two runs are consistent. */
    const val Z_CHANGED = 2.0

    /** |z| at or above this — strong evidence of change. */
    const val Z_STRONG = 4.0

    /** The only vocabulary allowed by spec §8. Never a similarity percentage. */
    enum class Verdict { CONSISTENT, CHANGED, STRONG_EVIDENCE_OF_CHANGE }

    /** Which statistic produced [Comparison.z]; always stated in the result. */
    enum class Method {
        /** Poisson deviance (likelihood ratio) — low counts. */
        POISSON_LIKELIHOOD_RATIO,

        /** z = net/σ_net, χ²-like — adequate counts. */
        CHI_SQUARE,
    }

    fun verdictFor(z: Double): Verdict {
        val magnitude = abs(z)
        return when {
            magnitude >= Z_STRONG -> Verdict.STRONG_EVIDENCE_OF_CHANGE
            magnitude >= Z_CHANGED -> Verdict.CHANGED
            else -> Verdict.CONSISTENT
        }
    }

    /** One counting measurement: raw counts over a live time. */
    data class Counting(val counts: Double, val seconds: Double) {
        val rateCps: Double get() = if (seconds > 0.0) counts / seconds else 0.0
    }

    /** net = G − B·r and its σ (IAEA [R4]); [ratio] is r = t_G/t_B. */
    data class NetResult(val net: Double, val sigma: Double, val ratio: Double)

    /**
     * Background subtraction with time scaling. Returns null when either live
     * time is non-positive — there is no rate to compare.
     */
    fun net(gross: Counting, background: Counting): NetResult? {
        if (gross.seconds <= 0.0 || background.seconds <= 0.0) return null
        val ratio = gross.seconds / background.seconds
        val net = gross.counts - background.counts * ratio
        val variance = gross.counts + background.counts * ratio * ratio
        return NetResult(net = net, sigma = sqrt(variance), ratio = ratio)
    }

    /**
     * Comparison of two counting measurements (total counts, one energy
     * window, or any other count pair).
     */
    data class Comparison(
        val label: String,
        val a: Counting,
        val b: Counting,
        /** G − B·(t_A/t_B), counts. */
        val net: Double,
        /** √(G + B·(t_A/t_B)²), counts. */
        val netSigma: Double,
        val rateA: Double,
        val rateB: Double,
        /** R_A − R_B, counts/s. */
        val rateDiff: Double,
        /** 1σ of [rateDiff], counts/s. */
        val rateDiffSigma: Double,
        /** χ²-like significance net/σ_net (always computed). */
        val zChiSquare: Double,
        /** Signed √deviance of the Poisson likelihood ratio (always computed). */
        val zLikelihoodRatio: Double,
        /** The method actually used for [z] and [verdict]. */
        val method: Method,
        val z: Double,
        val verdict: Verdict,
    )

    /** Compares two counting measurements; null when a live time is missing. */
    fun compareCounts(label: String, a: Counting, b: Counting): Comparison? {
        val netResult = net(a, b) ?: return null
        val rateA = a.rateCps
        val rateB = b.rateCps
        val rateDiff = rateA - rateB
        val zChi = if (netResult.sigma > 0.0) netResult.net / netResult.sigma else 0.0
        val deviance = poissonDeviance(a.counts, a.seconds, b.counts, b.seconds)
        val zLr = if (rateDiff == 0.0) 0.0 else sign(rateDiff) * sqrt(deviance)
        val method = methodFor(a.counts, b.counts)
        val z = if (method == Method.CHI_SQUARE) zChi else zLr
        return Comparison(
            label = label,
            a = a,
            b = b,
            net = netResult.net,
            netSigma = netResult.sigma,
            rateA = rateA,
            rateB = rateB,
            rateDiff = rateDiff,
            rateDiffSigma = if (a.seconds > 0.0) netResult.sigma / a.seconds else 0.0,
            zChiSquare = zChi,
            zLikelihoodRatio = zLr,
            method = method,
            z = z,
            verdict = verdictFor(z),
        )
    }

    /** Documented switch: both runs need adequate counts for the χ²-like form. */
    fun methodFor(countsA: Double, countsB: Double): Method =
        if (countsA >= NORMAL_APPROX_MIN_COUNTS && countsB >= NORMAL_APPROX_MIN_COUNTS) {
            Method.CHI_SQUARE
        } else {
            Method.POISSON_LIKELIHOOD_RATIO
        }

    /**
     * Deviance D = 2·[G·ln(G/Ĝ) + B·ln(B/B̂)] of the two-sample Poisson
     * likelihood ratio (H₀: equal rates), with 0·ln0 ≡ 0. Asymptotically
     * χ²(1). Zero when either exposure is missing.
     */
    fun poissonDeviance(
        countsA: Double,
        secondsA: Double,
        countsB: Double,
        secondsB: Double,
    ): Double {
        if (secondsA <= 0.0 || secondsB <= 0.0) return 0.0
        val total = countsA + countsB
        if (total <= 0.0) return 0.0
        val expectedA = total * secondsA / (secondsA + secondsB)
        val expectedB = total * secondsB / (secondsA + secondsB)
        var d = 0.0
        if (countsA > 0.0 && expectedA > 0.0) d += countsA * ln(countsA / expectedA)
        if (countsB > 0.0 && expectedB > 0.0) d += countsB * ln(countsB / expectedB)
        return (2.0 * d).coerceAtLeast(0.0)
    }

    // --- energy windows ---

    /** Window sums of a run, ready for [compareCounts]. */
    fun windowCounting(
        counts: List<Int>,
        durationSeconds: Long,
        calibration: EnergyCalibration,
        spec: EnergyWindowSpec,
    ): Counting {
        val window = EnergyWindows.window(counts, durationSeconds, calibration, spec)
        return Counting(counts = window.counts.toDouble(), seconds = durationSeconds.toDouble())
    }

    /** Per-window comparison of two runs on their own calibrations. */
    fun compareWindows(
        aCounts: List<Int>,
        aSeconds: Long,
        aCalibration: EnergyCalibration,
        bCounts: List<Int>,
        bSeconds: Long,
        bCalibration: EnergyCalibration,
        specs: List<EnergyWindowSpec> = EnergyWindows.DEFAULTS,
    ): List<Comparison> = specs.mapNotNull { spec ->
        compareCounts(
            label = "${spec.startKeV.toInt()}–${spec.endKeV.toInt()} кэВ",
            a = windowCounting(aCounts, aSeconds, aCalibration, spec),
            b = windowCounting(bCounts, bSeconds, bCalibration, spec),
        )
    }

    // --- full spectrum ---

    data class SpectrumComparison(
        /** Channels that carried counts in at least one run. */
        val channelsUsed: Int,
        /** Σ per-channel Poisson deviance (G-test). */
        val deviance: Double,
        /** Σ (G − rB)²/(G + r²B) — Pearson-like. */
        val chiSquare: Double,
        val degreesOfFreedom: Int,
        /**
         * Wilson–Hilferty significance of the statistic chosen by [method],
         * one-sided (floored at 0 — see [compareSpectra]).
         */
        val z: Double,
        val method: Method,
        val verdict: Verdict,
    )

    /**
     * Channel-by-channel comparison of two spectra of possibly different live
     * times. Null when the grids differ or a live time is missing — resampled
     * fractional counts are not Poisson counts, so refusing is the honest
     * answer (same rule as [SpectrumMerge]).
     */
    fun compareSpectra(
        aCounts: List<Int>,
        aSeconds: Long,
        bCounts: List<Int>,
        bSeconds: Long,
    ): SpectrumComparison? {
        if (aCounts.size != bCounts.size || aCounts.isEmpty()) return null
        if (aSeconds <= 0L || bSeconds <= 0L) return null
        val ratio = aSeconds.toDouble() / bSeconds.toDouble()
        var deviance = 0.0
        var chiSquare = 0.0
        var used = 0
        var sumA = 0.0
        var sumB = 0.0
        for (i in aCounts.indices) {
            val g = aCounts[i].toDouble()
            val b = bCounts[i].toDouble()
            if (g + b <= 0.0) continue
            used++
            sumA += g
            sumB += b
            deviance += poissonDeviance(g, aSeconds.toDouble(), b, bSeconds.toDouble())
            val variance = g + b * ratio * ratio
            if (variance > 0.0) {
                val diff = g - b * ratio
                chiSquare += diff * diff / variance
            }
        }
        if (used == 0) return null
        // Channel occupancy decides the method: the normal approximation is
        // about the counts in a bin, not about their total.
        val method = methodFor(sumA / used, sumB / used)
        val statistic = if (method == Method.CHI_SQUARE) chiSquare else deviance
        // One-sided by construction: only an *excess* of the statistic over its
        // expectation ν is evidence that the spectra differ. A statistic below ν
        // means the two agree better than chance would predict, which is not
        // evidence of change, so the significance floors at 0 instead of turning
        // a perfect match into a large negative z.
        val z = wilsonHilferty(statistic, used).coerceAtLeast(0.0)
        return SpectrumComparison(
            channelsUsed = used,
            deviance = deviance,
            chiSquare = chiSquare,
            degreesOfFreedom = used,
            z = z,
            method = method,
            verdict = verdictFor(z),
        )
    }

    /**
     * Wilson–Hilferty normal approximation of a χ²(ν) statistic:
     * z = ((χ²/ν)^⅓ − (1 − 2/(9ν))) / √(2/(9ν)). Unsigned by nature — a
     * multi-channel test has no single direction.
     */
    fun wilsonHilferty(chiSquare: Double, degreesOfFreedom: Int): Double {
        if (degreesOfFreedom <= 0) return 0.0
        val v = degreesOfFreedom.toDouble()
        val term = 2.0 / (9.0 * v)
        return (cbrt(chiSquare / v) - (1.0 - term)) / sqrt(term)
    }

    // --- dose rate (Gaussian on sample means, NOT counting statistics) ---

    /** Dose-rate statistics of one run, µSv/h. */
    data class DoseStats(
        val sampleCount: Int,
        val meanMicroSvH: Double,
        /** Population σ of the readings (spread), µSv/h. */
        val sdMicroSvH: Double,
        val minMicroSvH: Double,
        val maxMicroSvH: Double,
    ) {
        /** σ/√n — a *lower bound*: 1 Hz readings are serially correlated. */
        val standardErrorMicroSvH: Double
            get() = if (sampleCount > 1) sdMicroSvH / sqrt(sampleCount.toDouble()) else 0.0
    }

    fun doseStats(readingsMicroSvH: List<Double>): DoseStats? {
        if (readingsMicroSvH.isEmpty()) return null
        val n = readingsMicroSvH.size
        val mean = readingsMicroSvH.sum() / n
        val variance = readingsMicroSvH.sumOf { val d = it - mean; d * d } / n
        return DoseStats(
            sampleCount = n,
            meanMicroSvH = mean,
            sdMicroSvH = sqrt(variance),
            minMicroSvH = readingsMicroSvH.min(),
            maxMicroSvH = readingsMicroSvH.max(),
        )
    }

    data class MeanComparison(
        val a: DoseStats,
        val b: DoseStats,
        val diffMicroSvH: Double,
        val diffSigmaMicroSvH: Double,
        val z: Double,
        val verdict: Verdict,
    )

    /**
     * Difference of two mean dose rates, z = Δ/√(SEM_A² + SEM_B²). This is a
     * Gaussian comparison of sample means, *not* counting statistics: the
     * device reports dose rate already smoothed, so consecutive 1 Hz readings
     * are correlated and the standard errors above are optimistic. The UI must
     * present this row as supporting evidence next to the counting result, and
     * the report says which statistic each verdict came from.
     */
    fun compareDoseRates(a: DoseStats?, b: DoseStats?): MeanComparison? {
        if (a == null || b == null) return null
        val diff = a.meanMicroSvH - b.meanMicroSvH
        val sigma = sqrt(
            a.standardErrorMicroSvH * a.standardErrorMicroSvH +
                b.standardErrorMicroSvH * b.standardErrorMicroSvH,
        )
        val z = if (sigma > 0.0) diff / sigma else 0.0
        return MeanComparison(
            a = a,
            b = b,
            diffMicroSvH = diff,
            diffSigmaMicroSvH = sigma,
            z = z,
            verdict = verdictFor(z),
        )
    }

    // --- distance scenario (spec §16) ---

    /**
     * Idealised point-source law I ∝ 1/r²: the rate predicted at [distanceCm]
     * from a rate measured at [referenceDistanceCm]. **Only ever shown next to
     * the spec-mandated warning**: real geometry is not a point source, air and
     * surroundings scatter, and the background does not fall off with distance
     * at all — a measured series matching the curve is a coincidence of the
     * setup, not a calibration.
     */
    fun inverseSquarePrediction(
        referenceRateCps: Double,
        referenceDistanceCm: Double,
        distanceCm: Double,
    ): Double? {
        if (referenceDistanceCm <= 0.0 || distanceCm <= 0.0) return null
        val scale = referenceDistanceCm / distanceCm
        return referenceRateCps * scale * scale
    }

    /**
     * Background-corrected distance point: the net rate above the background
     * measured at the same geometry, with its 1σ. Feeds the 1/r² overlay,
     * because the background is what breaks the law at large distances.
     */
    fun netRate(gross: Counting, background: Counting): Pair<Double, Double>? {
        val netResult = net(gross, background) ?: return null
        return (netResult.net / gross.seconds) to (netResult.sigma / gross.seconds)
    }
}
