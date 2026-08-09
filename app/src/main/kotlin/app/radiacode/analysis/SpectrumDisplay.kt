package app.radiacode.analysis

import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Quadratic energy calibration E(ch) = a0 + a1·ch + a2·ch² (keV). */
data class EnergyCalibration(val a0: Float, val a1: Float, val a2: Float) {

    fun energyAt(channel: Float): Float = a0 + a1 * channel + a2 * channel * channel

    /**
     * Inverse of [energyAt]: the (fractional) channel for an energy, clamped
     * at 0. Takes the positive root of the quadratic; RadiaCode calibrations
     * have a1 > 0 and |a2| ≪ a1, so the mapping is monotonic over 0..1023.
     */
    fun channelAt(energyKeV: Float): Float {
        if (kotlin.math.abs(a2) < 1e-7f) {
            if (a1 == 0f) return 0f
            return ((energyKeV - a0) / a1).coerceAtLeast(0f)
        }
        val discriminant = a1 * a1 - 4f * a2 * (a0 - energyKeV)
        if (discriminant <= 0f) return 0f
        return ((-a1 + sqrt(discriminant)) / (2f * a2)).coerceAtLeast(0f)
    }
}

/** Visible energy range of the spectrum chart — the whole zoom state. */
data class EnergyWindow(val startKeV: Float, val endKeV: Float) {
    val widthKeV: Float get() = endKeV - startKeV
}

/**
 * Pure display math for the Спектр screen: channel aggregation into pixel
 * columns, lin/log height mapping, zoom windowing, display-only smoothing and
 * background normalization. Raw spectrum data is never modified (SPEC:
 * smoothing/filtering is a visual function only). JVM-tested.
 */
object SpectrumDisplay {

    /** Narrowest zoom window; keeps ≥ ~1 channel per pixel column. */
    const val MIN_WINDOW_KEV = 300f

    /** Display smoothing: centered moving average over 2·radius+1 channels. */
    const val SMOOTH_RADIUS = 2

    private const val ZOOM_STEP = 2f

    /** Full visible range: channel 0 (clamped to 0 keV) .. last channel. */
    fun fullWindow(calibration: EnergyCalibration, channelCount: Int): EnergyWindow {
        val start = calibration.energyAt(0f).coerceAtLeast(0f)
        val end = calibration.energyAt((channelCount - 1).toFloat())
        return EnergyWindow(start, max(end, start + MIN_WINDOW_KEV))
    }

    fun zoomIn(window: EnergyWindow, full: EnergyWindow): EnergyWindow =
        scaleAbout(window, full, 1f / ZOOM_STEP, focusFraction = 0.5f)

    fun zoomOut(window: EnergyWindow, full: EnergyWindow): EnergyWindow =
        scaleAbout(window, full, ZOOM_STEP, focusFraction = 0.5f)

    /**
     * Pinch: gesture scale s > 1 zooms in (window shrinks by 1/s) about the
     * gesture focus, expressed as a 0..1 fraction of the chart width.
     */
    fun pinch(window: EnergyWindow, full: EnergyWindow, scale: Float, focusFraction: Float): EnergyWindow {
        if (scale <= 0f) return window
        return scaleAbout(window, full, 1f / scale, focusFraction)
    }

    /** Drag pan: positive [deltaFraction] (drag right) moves the window left. */
    fun pan(window: EnergyWindow, full: EnergyWindow, deltaFraction: Float): EnergyWindow {
        val shift = -deltaFraction * window.widthKeV
        return clampInto(EnergyWindow(window.startKeV + shift, window.endKeV + shift), full)
    }

    private fun scaleAbout(
        window: EnergyWindow,
        full: EnergyWindow,
        widthFactor: Float,
        focusFraction: Float,
    ): EnergyWindow {
        val newWidth = (window.widthKeV * widthFactor).coerceIn(MIN_WINDOW_KEV, full.widthKeV)
        val focusKeV = window.startKeV + focusFraction.coerceIn(0f, 1f) * window.widthKeV
        val start = focusKeV - focusFraction.coerceIn(0f, 1f) * newWidth
        return clampInto(EnergyWindow(start, start + newWidth), full)
    }

    /** Shifts (without shrinking below its width) [window] inside [full]. */
    fun clampInto(window: EnergyWindow, full: EnergyWindow): EnergyWindow {
        val width = window.widthKeV.coerceIn(MIN_WINDOW_KEV, full.widthKeV)
        var start = window.startKeV
        if (start + width > full.endKeV) start = full.endKeV - width
        if (start < full.startKeV) start = full.startKeV
        return EnergyWindow(start, start + width)
    }

    /** Channels covered by the window, clamped to the spectrum. */
    fun channelRange(window: EnergyWindow, calibration: EnergyCalibration, channelCount: Int): IntRange {
        val first = calibration.channelAt(window.startKeV).toInt().coerceIn(0, channelCount - 1)
        val last = ceil(calibration.channelAt(window.endKeV)).toInt().coerceIn(first, channelCount - 1)
        return first..last
    }

    /**
     * Aggregates the channels of [range] into [columnCount] pixel columns
     * taking the maximum per bucket — peaks survive downsampling, which is
     * what a spectrum reading is about (an average would flatten them).
     */
    fun aggregateMax(values: List<Float>, range: IntRange, columnCount: Int): List<Float> {
        val columns = FloatArray(columnCount)
        val span = (range.last - range.first + 1).coerceAtLeast(1)
        for (channel in range) {
            if (channel !in values.indices) continue
            val column = ((channel - range.first).toLong() * columnCount / span).toInt()
                .coerceIn(0, columnCount - 1)
            if (values[channel] > columns[column]) columns[column] = values[channel]
        }
        return columns.toList()
    }

    /** Column index for a channel under the same bucketing as [aggregateMax]. */
    fun columnForChannel(channel: Int, range: IntRange, columnCount: Int): Int? {
        if (channel < range.first || channel > range.last) return null
        val span = (range.last - range.first + 1).coerceAtLeast(1)
        return ((channel - range.first).toLong() * columnCount / span).toInt()
            .coerceIn(0, columnCount - 1)
    }

    /**
     * Display-only smoothing: centered moving average over 2·[radius]+1
     * channels; edges average over the available neighbors. The input list is
     * untouched.
     */
    fun movingAverage(values: List<Float>, radius: Int = SMOOTH_RADIUS): List<Float> {
        if (radius <= 0 || values.isEmpty()) return values
        val result = FloatArray(values.size)
        for (i in values.indices) {
            val from = max(0, i - radius)
            val to = kotlin.math.min(values.size - 1, i + radius)
            var sum = 0f
            for (j in from..to) sum += values[j]
            result[i] = sum / (to - from + 1)
        }
        return result.toList()
    }

    /**
     * «Минус фон»: channel-wise max(0, current − background · timeRatio) where
     * timeRatio = currentSeconds / backgroundSeconds. The background counts are
     * scaled to the live time of the current accumulation — assuming the
     * background rate was stationary, this makes both spectra comparable in
     * counts even when the accumulation times differ. Negative residuals clamp
     * to zero (display only; raw data stays untouched).
     */
    fun subtractBackground(
        current: List<Int>,
        currentSeconds: Long,
        background: List<Int>,
        backgroundSeconds: Long,
    ): List<Float> {
        val ratio = timeRatio(currentSeconds, backgroundSeconds)
        return List(current.size) { i ->
            val bg = background.getOrElse(i) { 0 }
            max(0f, current[i] - bg * ratio)
        }
    }

    /** Background series scaled to the current live time (overlay display). */
    fun scaleToDuration(
        background: List<Int>,
        backgroundSeconds: Long,
        currentSeconds: Long,
    ): List<Float> {
        val ratio = timeRatio(currentSeconds, backgroundSeconds)
        return background.map { it * ratio }
    }

    private fun timeRatio(currentSeconds: Long, backgroundSeconds: Long): Float =
        if (backgroundSeconds <= 0L) 1f else currentSeconds.toFloat() / backgroundSeconds

    /**
     * Log-scale top: the smallest power of ten ≥ the data maximum (at least
     * 10). With a power-of-ten top the decade gridlines land on even fractions
     * of the plot height.
     */
    fun logTop(maxValue: Float): Float {
        val target = max(maxValue, 10f)
        return 10f.pow(ceil(log10(target.toDouble())).toFloat())
    }

    /** Number of decades between 1 and [logTop] (≥ 1). */
    fun decadeCount(top: Float): Int = max(1, log10(top.toDouble()).roundToInt())

    /**
     * Column height in plot pixels. Linear: proportional to value/top. Log:
     * proportional to log10(value)/log10(top) with 1 count at the baseline.
     * Any positive value is at least 1 px tall — single counts stay visible.
     */
    fun columnHeightPx(value: Float, top: Float, plotHeightPx: Int, logScale: Boolean): Int {
        if (value <= 0f || top <= 0f) return 0
        val fraction = if (logScale) {
            log10(max(value, 1f).toDouble()).toFloat() / log10(top.toDouble()).toFloat()
        } else {
            value / top
        }
        return (fraction * plotHeightPx).roundToInt().coerceIn(1, plotHeightPx)
    }

    /** Pixel row (0 = top) of the gridline for 10^[decade] on a log plot. */
    fun decadeRow(decade: Int, top: Float, plotHeightPx: Int): Int {
        val decades = decadeCount(top)
        val fraction = decade.toFloat() / decades
        return (plotHeightPx - 1 - fraction * (plotHeightPx - 1)).roundToInt()
            .coerceIn(0, plotHeightPx - 1)
    }

    /** «1», «10», «100», «1k», «10k» — decade labels for the log axis. */
    fun decadeLabel(decade: Int): String {
        val value = 10.0.pow(decade)
        return when {
            value >= 1_000_000 -> "${(value / 1_000_000).toInt()}M"
            value >= 1_000 -> "${(value / 1_000).toInt()}k"
            else -> "${value.toInt()}"
        }
    }

    /** Energy tick: horizontal position as a 0..1 fraction plus its label. */
    data class EnergyTick(val fraction: Float, val keV: Int)

    /**
     * Round-энергия ticks (500/1000/… кэВ on the full range; finer steps when
     * zoomed) that fall inside the window, as fractions of the chart width.
     */
    fun energyTicks(window: EnergyWindow): List<EnergyTick> {
        val step = when {
            window.widthKeV > 1500f -> 500
            window.widthKeV > 600f -> 200
            else -> 100
        }
        val first = ceil(window.startKeV / step).toInt() * step
        val ticks = mutableListOf<EnergyTick>()
        var keV = max(first, step) // skip the 0 keV tick: the left edge is its own label
        while (keV <= window.endKeV) {
            ticks += EnergyTick((keV - window.startKeV) / window.widthKeV, keV)
            keV += step
        }
        return ticks
    }
}
