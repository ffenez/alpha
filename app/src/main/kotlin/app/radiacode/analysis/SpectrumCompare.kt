package app.radiacode.analysis

import app.radiacode.ui.text.SpectrumRu
import app.radiacode.ui.text.SpectrumStrings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure math for the spectrum comparator (История → «Сравнить»). Two modes:
 *
 * 1. [extractInterval] — A−B of the *same accumulation*: the later snapshot
 *    minus the earlier one gives the spectrum of just the interval between
 *    them, with MeasurementTime = Δt of the live times. Validated hard: same
 *    channel grid, same calibration, per-channel monotonicity, plausible wall
 *    clock. Δt is carried in seconds; millis appear only at the export
 *    boundary via an explicit ×1000 (the community Diff-Calc tool divides
 *    wall millis wrong — the tests here pin the correct conversion).
 *
 * 2. [compareRates] — two independent measurements: per-channel count *rates*
 *    (cps) with Poisson errors, difference A−B with σ propagation, and honest
 *    per-region verdicts in units of σ. Calibrations that differ beyond
 *    tolerance are handled by resampling B onto A's energy grid
 *    ([resample]: counts redistributed by energy-bin overlap, uniform density
 *    within a source bin).
 *
 * JVM-tested; no Android dependencies.
 */
object SpectrumCompare {

    /**
     * Calibrations closer than this (max energy shift over the shared
     * channels) count as identical: ~2 channels ≈ 5 keV, well under the
     * detector FWHM (≈20 keV at 100 keV, ≈50 keV at 662 keV), so peaks stay
     * aligned within their natural width.
     */
    const val CALIBRATION_TOLERANCE_KEV = 5f

    /** Wall-clock vs Δt plausibility slack for interval extraction. */
    private const val WALL_CLOCK_SLACK_SECONDS = 60L
    private const val WALL_CLOCK_SLACK_FRACTION = 0.1

    /** One spectrum snapshot as the comparator sees it. */
    data class Input(
        val counts: List<Int>,
        val durationSeconds: Long,
        val calibration: EnergyCalibration,
        /** When the snapshot was saved (epoch millis). */
        val timestampMillis: Long,
    )

    /** Max |E_a(ch) − E_b(ch)| over the channel range, keV. */
    fun calibrationDeltaKeV(
        a: EnergyCalibration,
        b: EnergyCalibration,
        channelCount: Int,
    ): Float {
        var worst = 0f
        for (ch in 0 until channelCount) {
            val x = ch.toFloat()
            worst = max(worst, abs(a.energyAt(x) - b.energyAt(x)))
        }
        return worst
    }

    // --- mode 1: interval extraction ---

    sealed interface IntervalOutcome {
        data class Ok(
            /** Later-minus-earlier counts per channel. */
            val counts: List<Int>,
            /** Live time of the interval, SECONDS (Δt of the accumulations). */
            val durationSeconds: Long,
            /** Wall-clock bracket: [endMillis] − Δt·1000. */
            val startMillis: Long,
            val endMillis: Long,
            val calibration: EnergyCalibration,
            val warnings: List<String>,
        ) : IntervalOutcome

        data class Invalid(val reason: String) : IntervalOutcome
    }

    /**
     * Later minus earlier of the same accumulation. Order is determined by
     * live time (the accumulation clock), not by the pick order.
     */
    fun extractInterval(
        first: Input,
        second: Input,
        s: SpectrumStrings = SpectrumRu,
    ): IntervalOutcome {
        if (first.counts.size != second.counts.size) {
            return IntervalOutcome.Invalid(
                s.intervalChannelMismatch(first.counts.size, second.counts.size),
            )
        }
        if (first.durationSeconds == second.durationSeconds) {
            return IntervalOutcome.Invalid(s.intervalSameDuration)
        }
        val later = if (first.durationSeconds > second.durationSeconds) first else second
        val earlier = if (later === first) second else first

        val calibrationDelta =
            calibrationDeltaKeV(later.calibration, earlier.calibration, later.counts.size)
        if (calibrationDelta > CALIBRATION_TOLERANCE_KEV) {
            return IntervalOutcome.Invalid(
                s.intervalCalibrationMismatch("%.1f".format(calibrationDelta)),
            )
        }

        var violations = 0
        val counts = ArrayList<Int>(later.counts.size)
        for (i in later.counts.indices) {
            val diff = later.counts[i] - earlier.counts[i]
            if (diff < 0) violations++
            counts += diff
        }
        if (violations > 0) {
            return IntervalOutcome.Invalid(s.intervalNegativeChannels(violations))
        }

        val warnings = mutableListOf<String>()
        if (later.timestampMillis < earlier.timestampMillis) {
            warnings += s.intervalOrderWarning
        }

        val deltaSeconds = later.durationSeconds - earlier.durationSeconds
        val wallSeconds = (later.timestampMillis - earlier.timestampMillis) / 1000L
        val slack = max(
            WALL_CLOCK_SLACK_SECONDS,
            (deltaSeconds * WALL_CLOCK_SLACK_FRACTION).toLong(),
        )
        if (later.timestampMillis >= earlier.timestampMillis &&
            abs(wallSeconds - deltaSeconds) > slack
        ) {
            warnings += s.intervalWallClockWider(wallSeconds, deltaSeconds)
        }

        // Seconds → millis exactly once, explicitly: Δt·1000.
        val endMillis = later.timestampMillis
        val startMillis = endMillis - deltaSeconds * 1000L

        return IntervalOutcome.Ok(
            counts = counts,
            durationSeconds = deltaSeconds,
            startMillis = startMillis,
            endMillis = endMillis,
            calibration = later.calibration,
            warnings = warnings,
        )
    }

    // --- mode 2: independent rate comparison ---

    sealed interface RateOutcome {
        data class Ok(
            /** Per channel of A's grid: rate difference A−B, counts/s. */
            val diffCps: List<Float>,
            /** 1σ of the difference: √(nA/tA² + nB/tB²). */
            val sigmaCps: List<Float>,
            /** B was resampled onto A's energy grid. */
            val resampled: Boolean,
            /** B's counts on A's grid (fractional when resampled) — overlay data. */
            val bCountsOnGrid: List<Float>,
            /** The shared grid — A's calibration. */
            val calibration: EnergyCalibration,
            val warnings: List<String>,
        ) : RateOutcome

        data class Invalid(val reason: String) : RateOutcome
    }

    /** Rate difference A−B of two independent measurements, on A's grid. */
    fun compareRates(a: Input, b: Input, s: SpectrumStrings = SpectrumRu): RateOutcome {
        if (a.durationSeconds <= 0 || b.durationSeconds <= 0) {
            return RateOutcome.Invalid(s.ratesZeroDuration)
        }
        if (a.counts.size != b.counts.size) {
            return RateOutcome.Invalid(s.ratesChannelMismatch(a.counts.size, b.counts.size))
        }

        val warnings = mutableListOf<String>()
        val delta = calibrationDeltaKeV(a.calibration, b.calibration, a.counts.size)
        val resampled = delta > CALIBRATION_TOLERANCE_KEV
        val bCounts: DoubleArray = if (resampled) {
            warnings += s.ratesResampled("%.1f".format(delta))
            resample(b.counts, b.calibration, a.calibration, a.counts.size)
        } else {
            DoubleArray(b.counts.size) { b.counts[it].toDouble() }
        }

        val ta = a.durationSeconds.toDouble()
        val tb = b.durationSeconds.toDouble()
        val diff = FloatArray(a.counts.size)
        val sigma = FloatArray(a.counts.size)
        for (i in a.counts.indices) {
            val na = a.counts[i].toDouble()
            val nb = bCounts[i]
            diff[i] = (na / ta - nb / tb).toFloat()
            sigma[i] = sqrt(na / (ta * ta) + nb / (tb * tb)).toFloat()
        }
        return RateOutcome.Ok(
            diffCps = diff.toList(),
            sigmaCps = sigma.toList(),
            resampled = resampled,
            bCountsOnGrid = List(bCounts.size) { bCounts[it].toFloat() },
            calibration = a.calibration,
            warnings = warnings,
        )
    }

    /**
     * Rebins [counts] from grid [from] onto grid [to] ([channelCount] target
     * channels). Each source bin spans the energies of its half-channel
     * edges; its counts spread over the target bins it overlaps,
     * proportionally to the overlap (uniform density inside a source bin).
     * Total counts are preserved up to grid-edge clipping. Fractional counts
     * are inherent to rebinning — callers treat them as expectations.
     */
    fun resample(
        counts: List<Int>,
        from: EnergyCalibration,
        to: EnergyCalibration,
        channelCount: Int,
    ): DoubleArray {
        val result = DoubleArray(channelCount)
        for (i in counts.indices) {
            val n = counts[i]
            if (n == 0) continue
            val e0 = from.energyAt(i - 0.5f)
            val e1 = from.energyAt(i + 0.5f)
            if (e1 <= e0) continue // non-monotonic edge; nothing sane to do
            // Position of the source bin on the target channel axis.
            val c0 = to.channelAt(e0)
            val c1 = to.channelAt(e1)
            if (c1 <= c0) continue
            val width = c1 - c0
            var j = kotlin.math.floor(c0 + 0.5f).toInt()
            val jLast = kotlin.math.floor(c1 + 0.5f).toInt()
            while (j <= jLast) {
                if (j in 0 until channelCount) {
                    val overlap = min(c1, j + 0.5f) - max(c0, j - 0.5f)
                    if (overlap > 0f) result[j] += n * (overlap / width).toDouble()
                }
                j++
            }
        }
        return result
    }

    // --- per-region verdicts ---

    enum class Verdict { NOISE, POSSIBLE_EXCESS, EXCESS, POSSIBLE_DEFICIT, DEFICIT }

    data class RegionVerdict(
        val startKeV: Float,
        val endKeV: Float,
        /** Summed rate difference over the region, counts/s. */
        val diffCps: Float,
        /** 1σ of the summed difference. */
        val sigmaCps: Float,
        /** Significance in σ units; 0 when σ is 0. */
        val z: Float,
        val verdict: Verdict,
    )

    /** Conventional gamma-spectroscopy bands, keV. */
    val DEFAULT_REGIONS_KEV: List<Pair<Float, Float>> = listOf(
        0f to 100f,
        100f to 300f,
        300f to 700f,
        700f to 1500f,
        1500f to 3000f,
    )

    /**
     * Region verdict: z = Σdiff / √(Σσ²) over the region's channels.
     * |z| < 2 is noise; 2–4 «возможное»; ≥ 4 significant — the same cautious
     * ladder as the isotope hints (never a hard «обнаружено» from 2σ).
     */
    fun regionVerdicts(
        outcome: RateOutcome.Ok,
        channelCount: Int,
        regions: List<Pair<Float, Float>> = DEFAULT_REGIONS_KEV,
    ): List<RegionVerdict> {
        val result = mutableListOf<RegionVerdict>()
        for ((startKeV, endKeV) in regions) {
            // Half-open channel ranges: adjacent regions never share a channel.
            val firstChannel = kotlin.math.ceil(outcome.calibration.channelAt(startKeV))
                .toInt().coerceIn(0, channelCount)
            val lastChannel = (kotlin.math.ceil(outcome.calibration.channelAt(endKeV)).toInt() - 1)
                .coerceIn(-1, channelCount - 1)
            if (lastChannel < firstChannel) continue
            var sum = 0.0
            var variance = 0.0
            for (ch in firstChannel..lastChannel) {
                sum += outcome.diffCps[ch].toDouble()
                val s = outcome.sigmaCps[ch].toDouble()
                variance += s * s
            }
            val sigma = sqrt(variance)
            val z = if (sigma > 0.0) (sum / sigma).toFloat() else 0f
            result += RegionVerdict(
                startKeV = startKeV,
                endKeV = endKeV,
                diffCps = sum.toFloat(),
                sigmaCps = sigma.toFloat(),
                z = z,
                verdict = verdictFor(z),
            )
        }
        return result
    }

    fun verdictFor(z: Float): Verdict = when {
        z >= 4f -> Verdict.EXCESS
        z >= 2f -> Verdict.POSSIBLE_EXCESS
        z <= -4f -> Verdict.DEFICIT
        z <= -2f -> Verdict.POSSIBLE_DEFICIT
        else -> Verdict.NOISE
    }

    // --- chart aggregation for the difference plot ---

    data class DiffColumns(val diff: List<Float>, val sigma: List<Float>)

    /**
     * Buckets per-channel differences into chart columns: differences add,
     * σ adds in quadrature — each column is itself a valid Poisson-propagated
     * measurement of its energy slice.
     */
    fun aggregateDiff(
        diffCps: List<Float>,
        sigmaCps: List<Float>,
        range: IntRange,
        columnCount: Int,
    ): DiffColumns {
        val diff = FloatArray(columnCount)
        val variance = DoubleArray(columnCount)
        val span = (range.last - range.first + 1).coerceAtLeast(1)
        for (channel in range) {
            if (channel !in diffCps.indices) continue
            val column = ((channel - range.first).toLong() * columnCount / span).toInt()
                .coerceIn(0, columnCount - 1)
            diff[column] += diffCps[channel]
            variance[column] += sigmaCps[channel].toDouble() * sigmaCps[channel]
        }
        return DiffColumns(
            diff = diff.toList(),
            sigma = variance.map { sqrt(it).toFloat() },
        )
    }
}
