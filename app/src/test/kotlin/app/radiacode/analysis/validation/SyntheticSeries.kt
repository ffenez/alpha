package app.radiacode.analysis.validation

import java.io.File
import kotlin.math.exp

/**
 * Deterministic stand-ins for RC-110 recordings, so the validation harness has
 * something to run against **today** (graph spec §36.5–36.7, §37A).
 *
 * They are explicitly *not* a substitute for real data: a synthetic series can
 * only show that the machinery behaves as designed on the model it was built
 * from. What the real recordings must look like is documented in
 * `docs/analysis/trend-and-anomaly.md`.
 *
 * ## The model
 *
 * 1. counts per second C_t ~ Poisson(λ) — the standard model for independent
 *    nuclear events at low/moderate rates (spec §5);
 * 2. the reported dose rate is an **exponentially integrated** version of the
 *    counts, x_t = (1−a)·x_{t−1} + a·k·C_t, because the instrument itself
 *    integrates before reporting. This is what makes 1 Hz readings serially
 *    correlated: the series is AR(1)-like with ρ₁ ≈ 1−a and an integrated
 *    autocorrelation time τ ≈ (2−a)/a.
 *
 * Randomness comes from a fixed 64-bit LCG seeded by the caller — the same
 * seed always gives the same series on any JVM, so a harness run is a
 * repeatable measurement, not a lottery.
 */
object SyntheticSeries {

    /** λ = 30 counts/s and k chosen so a quiet indoor background reads ≈0.12 µSv/h. */
    const val DEFAULT_LAMBDA = 30.0

    const val DEFAULT_SCALE = 0.12 / 30.0

    /** Integrator constant: a = 0.2 → ρ₁ ≈ 0.8, τ ≈ 9 samples. */
    const val DEFAULT_ALPHA = 0.2

    /** Stationary background of [seconds] samples at 1 Hz, µSv/h. */
    fun stationary(
        seconds: Int,
        seed: Long,
        lambda: Double = DEFAULT_LAMBDA,
        alpha: Double = DEFAULT_ALPHA,
        scale: Double = DEFAULT_SCALE,
    ): DoubleArray = withStep(seconds, seed, stepAt = seconds, stepFactor = 1.0, lambda, alpha, scale)

    /**
     * The same background with the mean count rate multiplied by [stepFactor]
     * from sample [stepAt] onwards — the «controlled step» of spec §37B.
     */
    fun withStep(
        seconds: Int,
        seed: Long,
        stepAt: Int,
        stepFactor: Double,
        lambda: Double = DEFAULT_LAMBDA,
        alpha: Double = DEFAULT_ALPHA,
        scale: Double = DEFAULT_SCALE,
    ): DoubleArray {
        val rng = Lcg(seed)
        val out = DoubleArray(seconds)
        // Start the integrator at the stationary mean so there is no burn-in
        // artefact at the head of the series.
        var x = lambda * scale
        for (t in 0 until seconds) {
            val mean = if (t >= stepAt) lambda * stepFactor else lambda
            val counts = rng.poisson(mean)
            x = (1.0 - alpha) * x + alpha * (counts * scale)
            out[t] = x
        }
        return out
    }

    /** Deterministic 64-bit LCG (Numerical Recipes constants) + Knuth's Poisson. */
    class Lcg(private var state: Long) {
        fun nextDouble(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            // Top 53 bits → [0,1); never exactly 0, so ln/multiplication are safe.
            val bits = (state ushr 11) and ((1L shl 53) - 1)
            return (bits + 0.5) / (1L shl 53).toDouble()
        }

        /** Knuth's product method; exact for the λ ≈ 30 used here. */
        fun poisson(lambda: Double): Int {
            val limit = exp(-lambda)
            var k = 0
            var p = 1.0
            do {
                k++
                p *= nextDouble()
            } while (p > limit)
            return k - 1
        }
    }
}

/**
 * CSV of a real recording, for promoting the candidate test out of
 * experimental.
 *
 * **Format** — one sample per line, `timestamp_ms,dose_rate_usv_h`:
 * ```text
 * # RC-110, кухня, прибор неподвижен, 2026-08-10
 * 1754812800000,0.118
 * 1754812801000,0.121
 * ```
 * Lines starting with `#` and blank lines are ignored; a header line whose
 * first field is not a number is ignored too. Timestamps are epoch
 * milliseconds and must be non-decreasing; a jump of more than
 * [GAP_MILLIS] starts a new segment (a BLE outage is a gap, never
 * interpolated — graph spec §25).
 *
 * **Where to put the file**: `app/src/test/resources/validation/`. The names
 * the harness looks for are [STATIONARY_FILE] and [STEP_FILE]. The files are
 * not committed (they are personal measurement data); when they are absent the
 * validation test runs on the synthetic series only and says so.
 */
object ValidationCsv {

    const val DIRECTORY = "app/src/test/resources/validation"
    const val STATIONARY_FILE = "stationary-rc110.csv"
    const val STEP_FILE = "step-rc110.csv"
    const val GAP_MILLIS = 5_000L

    /** Contiguous stretch of a recording: 1 Hz samples with no gap inside. */
    data class Segment(val startMillis: Long, val values: DoubleArray) {
        override fun equals(other: Any?): Boolean =
            other is Segment && other.startMillis == startMillis &&
                other.values.contentEquals(values)

        override fun hashCode(): Int = 31 * startMillis.hashCode() + values.contentHashCode()
    }

    /** Segments of [file], or an empty list when it does not exist. */
    fun read(file: File): List<Segment> {
        if (!file.isFile) return emptyList()
        val segments = ArrayList<Segment>()
        var start = 0L
        var previous = Long.MIN_VALUE
        var current = ArrayList<Double>()
        for (raw in file.readLines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(',', ';')
            if (parts.size < 2) continue
            val timestamp = parts[0].trim().toLongOrNull() ?: continue
            val value = parts[1].trim().replace(',', '.').toDoubleOrNull() ?: continue
            if (previous != Long.MIN_VALUE && timestamp - previous > GAP_MILLIS) {
                if (current.size > 1) segments += Segment(start, current.toDoubleArray())
                current = ArrayList()
                start = timestamp
            }
            if (current.isEmpty()) start = timestamp
            current += value
            previous = timestamp
        }
        if (current.size > 1) segments += Segment(start, current.toDoubleArray())
        return segments
    }
}
