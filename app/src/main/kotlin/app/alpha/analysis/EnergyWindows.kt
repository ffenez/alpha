package app.alpha.analysis

import app.alpha.device.DeviceModel

import app.alpha.ui.text.SpectrumRu
import app.alpha.ui.text.SpectrumStrings
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * One analysis window on the energy axis, keV. **A parameter of the analysis,
 * not a physical category** (spec §7): the bounds are editable and the UI must
 * never present them as «types of radiation».
 */
data class EnergyWindowSpec(val startKeV: Float, val endKeV: Float) {
    val widthKeV: Float get() = endKeV - startKeV
}

/**
 * Energy windows and the descriptive spectral index (spec §7).
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formula.** For a window [E₁,E₂): C = Σ Nᵢ over the channels whose
 *    centre energy falls inside the window; R_window = C / t; the Poisson 1σ of
 *    the rate is σ_R = √C / t (spec §5: Var(N) = λ ≈ N). The share of the
 *    window in the spectrum is C / ΣN over all channels. The spectral index is
 *    R_low / R_high; since both rates share the same live time t it equals
 *    C_low / C_high, and its 1σ by error propagation of two *independent*
 *    (disjoint windows) Poisson counts is σ = index·√(1/C_low + 1/C_high).
 * 2. **Assumptions.** Independent Poisson counting (no dead-time/pile-up
 *    correction — the device reports none, spec §5); the quadratic calibration
 *    E(ch) = a₀ + a₁·ch + a₂·ch² is monotonic over the channel range; disjoint
 *    windows give independent counts.
 * 3. **Units.** C — counts; t — seconds; R — counts/s (imp/s); σ_R —
 *    counts/s; share and index — dimensionless.
 * 4. **Reference.** Spec §7 (window sums), §5 with [R2]/[R3] (Poisson counting).
 * 5. **Validation.** Synthetic spectra in `EnergyWindowsTest` (exact sums,
 *    edge channels, disjointness, σ propagation, index propagation). Real RC-110
 *    data: pending field validation.
 * 6. **Limitations.** No detector-response or efficiency correction — the
 *    numbers describe *counts in this instrument*, not photon fluence; window
 *    edges land on whole channels (see below), so a requested 100–300 keV
 *    window is realised as the channel range actually covered; the index is a
 *    descriptive composition number and **is not a measure of danger**.
 * 7. **Tests.** `app/src/test/.../analysis/EnergyWindowsTest.kt`.
 * 8. **Algorithm version.** [AlgorithmVersions.ENERGY_WINDOWS].
 * 9. **User-facing meaning.** «Сколько импульсов пришло из этого куска
 *    энергетической шкалы» — a description of spectrum composition, useful for
 *    comparing two measurements of the same scene. It says nothing about
 *    dose, danger or the identity of a radionuclide.
 *
 * ## Partial channels at the window edges
 *
 * A channel is an indivisible count bin. Splitting the edge channel
 * proportionally would produce fractional counts, and a fractional count is no
 * longer Poisson — √C would stop being its uncertainty (the same reason
 * [SpectrumMerge] refuses to rebin). So the window takes **whole channels**:
 * channel i (covering [E(i−0.5), E(i+0.5))) belongs to the window when its
 * centre E(i) lies in [E₁,E₂). The ranges of adjacent windows therefore never
 * share a channel, and the realised span is reported in
 * [WindowResult.coveredStartKeV]/[WindowResult.coveredEndKeV] (the outer bin
 * edges of the first and last channel) so the difference from the requested
 * bounds stays visible instead of being silently rounded away.
 *
 * Pure JVM; no Android dependencies.
 */
object EnergyWindows {

    const val ALGORITHM_VERSION = AlgorithmVersions.ENERGY_WINDOWS

    /** Defaults of spec §7 — analysis parameters, editable by the user. */
    val DEFAULTS: List<EnergyWindowSpec> = listOf(
        EnergyWindowSpec(100f, 300f),
        EnergyWindowSpec(300f, 700f),
        EnergyWindowSpec(700f, 1500f),
    )

    /**
     * Границы редактирования диапазонов, кэВ.
     *
     * Верх — потолок шкалы серии RadiaCode (3 МэВ у всех моделей в таблице
     * [DeviceModel]); низ — ноль, потому что нижняя граница окна может
     * начинаться от самого порога, а порог у моделей разный (20 кэВ у 103 и
     * 110, 25 у 103G, 30 у Zero). Числа взяты из [DeviceModel], а не вписаны
     * заново: появится модель с другой шкалой — правится одна таблица.
     */
    val MIN_BOUND_KEV = 0f
    val MAX_BOUND_KEV = DeviceModel.entries.maxOf { it.maxEnergyKeV }

    /** Narrower than this is below the detector FWHM everywhere — not a window. */
    const val MIN_WIDTH_KEV = 10f

    /** Result for one window. [counts] is a whole-channel sum (see class KDoc). */
    data class WindowResult(
        val spec: EnergyWindowSpec,
        /** First/last channel of the window; empty window ⇒ last < first. */
        val firstChannel: Int,
        val lastChannel: Int,
        /** Outer bin edges actually covered, keV (0 for an empty window). */
        val coveredStartKeV: Float,
        val coveredEndKeV: Float,
        val counts: Long,
        /** C / t, counts per second; 0 when t ≤ 0. */
        val rateCps: Double,
        /** √C / t, counts per second (Poisson 1σ). */
        val sigmaCps: Double,
        /** C / ΣN over the whole spectrum; 0 when the spectrum is empty. */
        val fraction: Double,
    ) {
        val isEmpty: Boolean get() = lastChannel < firstChannel
    }

    /** R_low/R_high with its propagated 1σ (spec §7 — descriptive only). */
    data class SpectralIndex(
        val lowWindow: EnergyWindowSpec,
        val highWindow: EnergyWindowSpec,
        val value: Double,
        val sigma: Double,
    )

    data class Analysis(
        val windows: List<WindowResult>,
        val durationSeconds: Long,
        val totalCounts: Long,
        /** Null when either end window has no counts — a ratio would be a fiction. */
        val index: SpectralIndex?,
    )

    /**
     * Channels whose centre energy lies in [spec.startKeV, spec.endKeV).
     * Null when the window falls outside the spectrum entirely.
     */
    fun channelRange(
        spec: EnergyWindowSpec,
        calibration: EnergyCalibration,
        channelCount: Int,
    ): IntRange? {
        if (channelCount <= 0 || spec.endKeV <= spec.startKeV) return null
        // Крайний канал не является измерением энергии в этой точке, поэтому
        // не попадает ни в одно окно ([SpectrumEdge]).
        val usable = SpectrumEdge.lastAnalysableChannel(channelCount) + 1
        val first = ceil(calibration.channelAt(spec.startKeV)).toInt().coerceIn(0, usable)
        val last = (ceil(calibration.channelAt(spec.endKeV)).toInt() - 1)
            .coerceIn(-1, usable - 1)
        if (last < first) return null
        return first..last
    }

    /** One window over [counts]; [totalCounts] is the whole-spectrum sum. */
    fun window(
        counts: List<Int>,
        durationSeconds: Long,
        calibration: EnergyCalibration,
        spec: EnergyWindowSpec,
        totalCounts: Long = counts.sumOf { it.toLong() },
    ): WindowResult {
        val range = channelRange(spec, calibration, counts.size)
        if (range == null) {
            return WindowResult(
                spec = spec,
                firstChannel = 0,
                lastChannel = -1,
                coveredStartKeV = 0f,
                coveredEndKeV = 0f,
                counts = 0L,
                rateCps = 0.0,
                sigmaCps = 0.0,
                fraction = 0.0,
            )
        }
        var sum = 0L
        for (channel in range) sum += counts[channel].toLong()
        val t = durationSeconds.toDouble()
        return WindowResult(
            spec = spec,
            firstChannel = range.first,
            lastChannel = range.last,
            coveredStartKeV = calibration.energyAt(range.first - 0.5f),
            coveredEndKeV = calibration.energyAt(range.last + 0.5f),
            counts = sum,
            rateCps = if (t > 0.0) sum / t else 0.0,
            sigmaCps = if (t > 0.0) sqrt(sum.toDouble()) / t else 0.0,
            fraction = if (totalCounts > 0L) sum.toDouble() / totalCounts else 0.0,
        )
    }

    /**
     * All windows plus the R_low/R_high index between the first and the last
     * window (the widest-separated pair the user configured).
     */
    fun analyze(
        counts: List<Int>,
        durationSeconds: Long,
        calibration: EnergyCalibration,
        specs: List<EnergyWindowSpec> = DEFAULTS,
    ): Analysis {
        val total = counts.sumOf { it.toLong() }
        val windows = specs.map { window(counts, durationSeconds, calibration, it, total) }
        return Analysis(
            windows = windows,
            durationSeconds = durationSeconds,
            totalCounts = total,
            index = spectralIndex(windows.firstOrNull(), windows.lastOrNull()),
        )
    }

    /**
     * R_low/R_high = C_low/C_high (same live time cancels), 1σ by propagation
     * of two independent Poisson counts. Null when either window is empty:
     * dividing by zero counts would invent a number.
     */
    fun spectralIndex(low: WindowResult?, high: WindowResult?): SpectralIndex? {
        if (low == null || high == null || low === high) return null
        if (low.counts <= 0L || high.counts <= 0L) return null
        val value = low.counts.toDouble() / high.counts.toDouble()
        val relative = sqrt(1.0 / low.counts + 1.0 / high.counts)
        return SpectralIndex(
            lowWindow = low.spec,
            highWindow = high.spec,
            value = value,
            sigma = value * relative,
        )
    }

    // --- editing / storage of the bounds (they are analysis parameters) ---

    /** Refusal reason for a proposed window set, or null when it is usable. */
    fun validate(specs: List<EnergyWindowSpec>, s: SpectrumStrings = SpectrumRu): String? {
        if (specs.isEmpty()) return s.windowsNeedOne
        for (spec in specs) {
            if (!spec.startKeV.isFinite() || !spec.endKeV.isFinite()) {
                return s.windowBoundsNotNumbers
            }
            if (spec.startKeV < MIN_BOUND_KEV || spec.endKeV > MAX_BOUND_KEV) {
                return s.windowBoundsOutOfRange(MIN_BOUND_KEV.toInt(), MAX_BOUND_KEV.toInt())
            }
            if (spec.widthKeV < MIN_WIDTH_KEV) {
                return s.windowTooNarrow(MIN_WIDTH_KEV.toInt())
            }
        }
        val sorted = specs.sortedBy { it.startKeV }
        for (i in 1 until sorted.size) {
            if (sorted[i].startKeV < sorted[i - 1].endKeV) {
                return s.windowsOverlap
            }
        }
        return null
    }

    /** Storage form: «100:300,300:700,700:1500» (stable on disk). */
    fun format(specs: List<EnergyWindowSpec>): String =
        specs.joinToString(",") { "${trim(it.startKeV)}:${trim(it.endKeV)}" }

    /** Parses [format]; any malformed input falls back to [DEFAULTS]. */
    fun parse(raw: String?): List<EnergyWindowSpec> {
        if (raw.isNullOrBlank()) return DEFAULTS
        val parsed = raw.split(',').mapNotNull { part ->
            val halves = part.split(':')
            if (halves.size != 2) return@mapNotNull null
            val start = halves[0].trim().toFloatOrNull() ?: return@mapNotNull null
            val end = halves[1].trim().toFloatOrNull() ?: return@mapNotNull null
            EnergyWindowSpec(start, end)
        }
        if (parsed.size != raw.split(',').size) return DEFAULTS
        return if (validate(parsed) == null) parsed else DEFAULTS
    }

    private fun trim(value: Float): String {
        val rounded = value.toDouble()
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else value.toString()
    }
}
