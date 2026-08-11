package app.radiacode.analysis

import kotlin.math.sqrt

/** Жёсткость одного накопления: доля жёсткой части спектра и её 1σ. */
data class HardnessValue(
    /** N_hard / N_band, 0…1. */
    val fraction: Double,
    /** Binomial 1σ of that fraction. */
    val sigma: Double,
    /** Counts in the analysis band the fraction is taken over. */
    val bandCounts: Double,
    /** Counts above the split — the numerator. */
    val hardCounts: Double,
) {
    /** «54 %» is how the number is read; the fraction is how it is stored. */
    val percent: Double get() = fraction * 100.0
}

/**
 * **Жёсткость** — the average energetic character of the detected photon
 * radiation (spec «Hardness»).
 *
 * The official RadiaCode app shows a number by this name; this is **our own**
 * definition of it, because the vendor's formula is not documented and a
 * number one cannot reproduce has no place in a screen that claims to be
 * checkable. Ours is the plainest thing the quantity can be: the share of the
 * counts that arrived above a fixed split energy.
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formula.** With N_band the counts in [LOW_KEV, HIGH_KEV) and N_hard the
 *    counts in [SPLIT_KEV, HIGH_KEV):
 *
 *    ```text
 *    H = N_hard / N_band,   σ_H = √( H·(1−H) / N_band )
 *    ```
 *
 *    Each registered photon either lands above the split or below it, so given
 *    N_band the numerator is binomial — that, not √N/N, is the honest error of
 *    a ratio of counts drawn from the same total. Channels enter a band by the
 *    energy of their **centre**, never split at the edge (the same rule as
 *    [EnergyWindows]: a fractional count stops being a count).
 * 2. **Assumptions.** The calibration is the instrument's own; the spectrum is
 *    an accumulation, so a **time series** is built from differences of
 *    consecutive snapshots ([intervals]) rather than from the cumulative
 *    spectra, which would smear a change over the whole history.
 * 3. **Units.** H is dimensionless (0…1, shown as %); energies are keV.
 * 4. **Reference.** None — this is a defined index, not a measured physical
 *    quantity. It is documented here so it is never mistaken for one.
 * 5. **Validation data.** `HardnessTest`: a low-energy-only spectrum gives ~0,
 *    a high-energy-only one ~1, doubling the exposure leaves H unchanged while
 *    σ shrinks as 1/√N, and a thin spectrum returns null instead of a number.
 * 6. **Limitations.** [LOW_KEV], [SPLIT_KEV], [HIGH_KEV] and [MIN_BAND_COUNTS]
 *    are **engineering parameters**. H depends on them, so two values are only
 *    comparable if they were computed with the same ones — which is why they
 *    are fixed constants here and not the user-editable windows of §7. H says
 *    nothing about dose, activity, nuclide or danger: a shielded source and a
 *    cosmic-ray-rich sky can produce the same number.
 * 7. **Tests.** `app/src/test/.../analysis/HardnessTest.kt`.
 * 8. **Algorithm version.** [AlgorithmVersions.HARDNESS].
 * 9. **User-facing meaning.** [EXPLANATION] — and never anything beyond it.
 */
object Hardness {

    const val ALGORITHM_VERSION = AlgorithmVersions.HARDNESS

    /**
     * Analysis band. **Engineering parameters**, the same edges the default
     * energy windows of spec §7 use: below 100 keV the RC-110 response and the
     * calibration are least trustworthy, above 1500 keV a hand-held detector
     * collects almost nothing.
     */
    const val LOW_KEV = 100f
    const val SPLIT_KEV = 300f
    const val HIGH_KEV = 1500f

    /**
     * Fewest counts in the band before a fraction is reported.
     * **Engineering parameter**: at 200 counts the 1σ of H near 0,5 is ≈ 3,5 %,
     * which is about the resolution the chart can show; below that the line
     * would be drawing its own noise.
     */
    const val MIN_BAND_COUNTS = 200.0

    /** The sentence the spec requires next to the number, wherever it appears. */
    const val EXPLANATION =
        "Жёсткость описывает среднюю энергетическую характеристику " +
            "зарегистрированного излучения. Это не мера опасности."

    /** Жёсткость of one accumulated spectrum; null when it is too thin. */
    fun of(counts: List<Int>, calibration: EnergyCalibration): HardnessValue? {
        val band = sum(counts, calibration, LOW_KEV, HIGH_KEV)
        val hard = sum(counts, calibration, SPLIT_KEV, HIGH_KEV)
        return value(band, hard)
    }

    /**
     * Жёсткость of the interval between two accumulations of the same run:
     * the later minus the earlier, channel by channel inside the bands.
     *
     * Negative differences (a reset between the two, or a device restart)
     * produce null rather than a clamped fiction.
     */
    fun ofInterval(
        earlier: List<Int>,
        later: List<Int>,
        calibration: EnergyCalibration,
    ): HardnessValue? {
        if (earlier.size != later.size) return null
        val band = sum(later, calibration, LOW_KEV, HIGH_KEV) -
            sum(earlier, calibration, LOW_KEV, HIGH_KEV)
        val hard = sum(later, calibration, SPLIT_KEV, HIGH_KEV) -
            sum(earlier, calibration, SPLIT_KEV, HIGH_KEV)
        if (band < 0.0 || hard < 0.0) return null
        return value(band, hard)
    }

    private fun value(bandCounts: Double, hardCounts: Double): HardnessValue? {
        if (bandCounts < MIN_BAND_COUNTS || hardCounts < 0.0) return null
        val fraction = (hardCounts / bandCounts).coerceIn(0.0, 1.0)
        return HardnessValue(
            fraction = fraction,
            sigma = sqrt(fraction * (1.0 - fraction) / bandCounts),
            bandCounts = bandCounts,
            hardCounts = hardCounts,
        )
    }

    private fun sum(
        counts: List<Int>,
        calibration: EnergyCalibration,
        fromKeV: Float,
        toKeV: Float,
    ): Double {
        val range = EnergyWindows.channelRange(
            spec = EnergyWindowSpec(fromKeV, toKeV),
            calibration = calibration,
            channelCount = counts.size,
        ) ?: return 0.0
        var sum = 0.0
        for (channel in range) sum += counts[channel]
        return sum
    }

    // ------------------------------------------------------------ time series

    /** One point of the жёсткость trend: an interval between two snapshots. */
    data class IntervalPoint(
        /** Wall time of the later snapshot. */
        val endMillis: Long,
        val deltaSeconds: Long,
        val value: HardnessValue,
    )

    data class HourPoint(
        val hourStartMillis: Long,
        val fraction: Double,
        val sigma: Double,
        /** Counts the hour's fraction was taken over. */
        val bandCounts: Double,
    )

    /**
     * Interval points from accumulated snapshots, oldest first. Pairs with a
     * changed channel count or a changed calibration are skipped: a difference
     * across a recalibration is not an interval, it is two different rulers.
     */
    fun intervals(snapshots: List<RadonTrend.Snapshot>): List<IntervalPoint> {
        val sorted = snapshots.sortedBy { it.timestampMillis }
        val result = ArrayList<IntervalPoint>(sorted.size)
        var previous: RadonTrend.Snapshot? = null
        for (current in sorted) {
            val prev = previous
            previous = current
            if (prev == null) continue
            if (prev.counts.size != current.counts.size) continue
            if (prev.calibration != current.calibration) continue
            val delta = current.durationSeconds - prev.durationSeconds
            if (delta <= 0) continue
            val value = ofInterval(prev.counts, current.counts, current.calibration) ?: continue
            result += IntervalPoint(
                endMillis = current.timestampMillis,
                deltaSeconds = delta,
                value = value,
            )
        }
        return result
    }

    /**
     * Hourly points: counts are **pooled** inside the hour and the fraction is
     * taken from the pooled counts — averaging the fractions themselves would
     * weigh a 20-second interval like a 20-minute one.
     */
    fun hourly(points: List<IntervalPoint>): List<HourPoint> =
        points.groupBy { it.endMillis / RadonTrend.HOUR_MILLIS }
            .toSortedMap()
            .mapNotNull { (hour, group) ->
                val band = group.sumOf { it.value.bandCounts }
                val hard = group.sumOf { it.value.hardCounts }
                val pooled = value(band, hard) ?: return@mapNotNull null
                HourPoint(
                    hourStartMillis = hour * RadonTrend.HOUR_MILLIS,
                    fraction = pooled.fraction,
                    sigma = pooled.sigma,
                    bandCounts = band,
                )
            }
}
