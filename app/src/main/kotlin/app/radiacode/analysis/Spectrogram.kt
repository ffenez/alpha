package app.radiacode.analysis

import kotlin.math.ln
import kotlin.math.log10

/**
 * Pure math for the спектрограмма waterfall (SPEC «Spectrogram», Advanced):
 * Energy × Time × Intensity. Time columns are per-poll interval spectra
 * (current accumulated minus previous accumulated, clamped ≥ 0), energy rows
 * are geometric («log-ish») bands over 20–3000 keV, and the rendered
 * intensity is a per-column log normalization — each time slice shows its
 * spectral *structure* whatever the absolute count rate was.
 *
 * JVM-tested; no Android dependencies.
 */
object Spectrogram {

    /** Waterfall rows. 96 keeps CsI(Tl) FWHM ≳ one band across the range. */
    const val BAND_COUNT = 96

    /** Below ~20 keV the RC-110 acquisition threshold dominates. */
    const val MIN_KEV = 20f
    const val MAX_KEV = 3000f

    private val LOG_SPAN = ln(MAX_KEV / MIN_KEV)

    /**
     * Vertical position of an energy on the waterfall, 0 (=[MIN_KEV]) .. 1
     * (=[MAX_KEV]); null outside the plotted range. Geometric scale: equal
     * fractions are equal energy *ratios*, so the low-energy region where
     * scintillator spectra live gets its fair share of rows.
     */
    fun fractionOfEnergy(keV: Float): Float? {
        if (keV < MIN_KEV || keV > MAX_KEV) return null
        return (ln(keV / MIN_KEV) / LOG_SPAN)
    }

    /** Band row (0-based from [MIN_KEV]) for an energy; null out of range. */
    fun bandOfEnergy(keV: Float): Int? {
        val fraction = fractionOfEnergy(keV) ?: return null
        return (fraction * BAND_COUNT).toInt().coerceAtMost(BAND_COUNT - 1)
    }

    /** Geometric center energy of a band, keV. */
    fun bandCenterKeV(band: Int): Float {
        val t = (band + 0.5f) / BAND_COUNT
        return MIN_KEV * kotlin.math.exp(t * LOG_SPAN)
    }

    /**
     * Sums per-channel interval counts into the [BAND_COUNT] energy bands
     * using the spectrum's own calibration. Channels outside 20–3000 keV are
     * dropped (threshold noise below, empty overflow above).
     */
    fun bandCounts(counts: IntArray, calibration: EnergyCalibration): FloatArray {
        val bands = FloatArray(BAND_COUNT)
        // Крайний канал не относится ни к одной полосе: он граница шкалы, а
        // не энергия ([SpectrumEdge]).
        for (channel in SpectrumEdge.analysable(counts.size)) {
            val n = counts[channel]
            if (n <= 0) continue
            val band = bandOfEnergy(calibration.energyAt(channel.toFloat())) ?: continue
            bands[band] += n
        }
        return bands
    }

    /**
     * Interval spectrum: [current] accumulated-since-reset minus [previous].
     * Null when there is no valid interval — first poll, channel-grid change,
     * or a reset between polls (accumulation time did not grow). Small
     * negative per-channel diffs (device-side rebinning jitter) clamp to 0.
     */
    fun intervalCounts(
        currentCounts: List<Int>,
        currentSeconds: Long,
        previousCounts: List<Int>?,
        previousSeconds: Long,
    ): IntArray? {
        if (previousCounts == null) return null
        if (currentCounts.size != previousCounts.size) return null
        if (currentSeconds <= previousSeconds) return null
        return IntArray(currentCounts.size) { i ->
            (currentCounts[i] - previousCounts[i]).coerceAtLeast(0)
        }
    }

    /**
     * Rendered intensity of one cell, 0..1: log10(1+v) / log10(1+columnMax)
     * where columnMax is the largest band value of the same time slice.
     * Log scaling keeps single counts visible next to a photopeak;
     * per-column normalization makes the spectral shape readable at any
     * count rate (documented on-screen: «яркость — лог-шкала по столбцу»).
     */
    fun intensity(value: Float, columnMax: Float): Float {
        if (value <= 0f || columnMax <= 0f) return 0f
        return (log10(1f + value) / log10(1f + columnMax)).coerceIn(0f, 1f)
    }

    /** Count-weighted mean photon energy of a banded slice; null if empty. */
    fun meanEnergyKeV(bandCounts: FloatArray): Float? {
        var total = 0.0
        var weighted = 0.0
        for (band in bandCounts.indices) {
            val n = bandCounts[band]
            if (n <= 0f) continue
            total += n
            weighted += n.toDouble() * bandCenterKeV(band)
        }
        if (total <= 0.0) return null
        return (weighted / total).toFloat()
    }

    /** Energy gridlines for the waterfall y-axis (fraction 0..1 → keV label). */
    val ENERGY_TICKS_KEV = listOf(50f, 100f, 300f, 600f, 1000f, 2000f)

    /**
     * Merges adjacent slices so at most [maxColumns] columns are rendered
     * (2 h at 5 s cadence = 1440 slices; a bitmap column per slice would be
     * sub-pixel). Band counts and Δt add; dose/CPS take the merged group's
     * latest known value; the timestamp is the group's last. Counts are
     * conserved — merging never invents or hides intensity.
     */
    fun aggregate(slices: List<SpectrogramSlice>, maxColumns: Int): List<SpectrogramSlice> {
        if (maxColumns <= 0 || slices.size <= maxColumns) return slices
        val groupSize = (slices.size + maxColumns - 1) / maxColumns
        return slices.chunked(groupSize).map { group ->
            val bands = FloatArray(BAND_COUNT)
            for (slice in group) {
                for (b in 0 until BAND_COUNT) bands[b] += slice.bandCounts[b]
            }
            SpectrogramSlice(
                timestampMillis = group.last().timestampMillis,
                intervalSeconds = group.sumOf { it.intervalSeconds },
                bandCounts = bands,
                cps = group.lastOrNull { it.cps != null }?.cps,
                doseMicroSvH = group.lastOrNull { it.doseMicroSvH != null }?.doseMicroSvH,
            )
        }
    }
}

/** One waterfall column: an interval spectrum banded into energy rows. */
class SpectrogramSlice(
    val timestampMillis: Long,
    /** Accumulation Δt covered by this slice, seconds. */
    val intervalSeconds: Long,
    /** Counts per energy band (row 0 = [Spectrogram.MIN_KEV]). */
    val bandCounts: FloatArray,
    /** Latest 1 Hz count rate at slice time; null if the stream was silent. */
    val cps: Float?,
    /** Latest dose rate at slice time, µSv/h; null if unknown. */
    val doseMicroSvH: Float?,
) {
    val totalCounts: Float = bandCounts.sum()
    val meanEnergyKeV: Float? = Spectrogram.meanEnergyKeV(bandCounts)
}

/**
 * Fixed-capacity ring of waterfall slices, oldest dropped first. At the
 * 5 s poll cadence [DEFAULT_CAPACITY] covers the last ~2 hours in memory
 * (~0.6 MB); nothing is persisted — saved spectrum snapshots are the
 * durable record.
 */
class SpectrogramRing(private val capacity: Int = DEFAULT_CAPACITY) {

    private val slices = ArrayDeque<SpectrogramSlice>()

    @Synchronized
    fun add(slice: SpectrogramSlice) {
        slices.addLast(slice)
        while (slices.size > capacity) slices.removeFirst()
    }

    /** Oldest → newest. */
    @Synchronized
    fun snapshot(): List<SpectrogramSlice> = slices.toList()

    @Synchronized
    fun latest(): SpectrogramSlice? = slices.lastOrNull()

    @Synchronized
    fun clear() = slices.clear()

    companion object {
        /** 2 h × 60 min × 12 slices/min (5 s cadence). */
        const val DEFAULT_CAPACITY = 1440
    }
}
