package app.alpha.analysis.quantiles

import java.util.Arrays
import kotlin.math.abs

/**
 * Result of computing one window **both ways** — exact order statistics of the
 * raw samples against the merged sketches (CHART SPEC §34, §37G).
 *
 * The error that matters is the **rank** error: where the approximate answer
 * actually sits in the true distribution. A value difference alone would be
 * unreadable — on a flat distribution a 1 % rank error is invisible, on a
 * steep tail the same 1 % is a big number — so both are reported and the rank
 * error is the one the accuracy claim is made about.
 */
data class QuantileComparison(
    val probabilities: DoubleArray,
    /** Exact nearest-rank quantiles of the raw samples. */
    val exactValues: FloatArray,
    /** The same quantiles as the approximate path reports them. */
    val approximateValues: FloatArray,
    /** |true rank of the approximate value − requested p|, per probability. */
    val rankErrors: DoubleArray,
    /** Raw samples the exact side actually read. */
    val sampleCount: Int,
    /** Observations the sketch claims — must equal [sampleCount]. */
    val sketchCount: Long,
    /** Accuracy parameter of the compared sketch. */
    val k: Int,
) {
    val maxRankError: Double get() = rankErrors.maxOrNull() ?: 0.0

    /** Largest |approx − exact| in the value's own unit. */
    val maxValueError: Float
        get() {
            var worst = 0f
            for (i in exactValues.indices) {
                val delta = abs(approximateValues[i] - exactValues[i])
                if (delta > worst) worst = delta
            }
            return worst
        }

    /** True when the sketch describes exactly the samples that were read. */
    val countsAgree: Boolean get() = sketchCount == sampleCount.toLong()

    override fun equals(other: Any?): Boolean =
        other is QuantileComparison &&
            probabilities.contentEquals(other.probabilities) &&
            exactValues.contentEquals(other.exactValues) &&
            approximateValues.contentEquals(other.approximateValues) &&
            sampleCount == other.sampleCount &&
            sketchCount == other.sketchCount &&
            k == other.k

    override fun hashCode(): Int {
        var h = probabilities.contentHashCode()
        h = 31 * h + exactValues.contentHashCode()
        h = 31 * h + approximateValues.contentHashCode()
        h = 31 * h + sampleCount
        h = 31 * h + sketchCount.hashCode()
        h = 31 * h + k
        return h
    }
}

/**
 * The developer/research diagnostic of ADR 004: run the same window through
 * both paths and report the error that was actually observed, instead of
 * quoting a bound from a paper.
 *
 * It is deliberately *not* part of any render path — the exact side reads every
 * raw sample of the window, which is exactly what the chart refuses to do.
 */
object QuantileDiagnostics {

    /** The five probabilities the chart draws (CHART SPEC §6). */
    val PROBABILITIES: DoubleArray = doubleArrayOf(0.10, 0.25, 0.50, 0.75, 0.90)

    fun compare(
        rawValues: FloatArray,
        sketch: KllSketch,
        probabilities: DoubleArray = PROBABILITIES,
    ): QuantileComparison {
        val sorted = rawValues.copyOf()
        Arrays.sort(sorted)
        val exact = KllSketch.exactQuantiles(sorted, probabilities)
        val approximate = sketch.quantiles(probabilities)
        val errors = DoubleArray(probabilities.size) { i ->
            if (sorted.isEmpty()) 0.0 else abs(rankOf(sorted, approximate[i]) - probabilities[i])
        }
        return QuantileComparison(
            probabilities = probabilities,
            exactValues = exact,
            approximateValues = approximate,
            rankErrors = errors,
            sampleCount = rawValues.size,
            sketchCount = sketch.count,
            k = sketch.k,
        )
    }

    /** Fraction of [sorted] that is ≤ [value] — the true rank, 0..1. */
    fun rankOf(sorted: FloatArray, value: Float): Double {
        if (sorted.isEmpty()) return 0.0
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] <= value) lo = mid + 1 else hi = mid
        }
        return lo.toDouble() / sorted.size
    }
}
