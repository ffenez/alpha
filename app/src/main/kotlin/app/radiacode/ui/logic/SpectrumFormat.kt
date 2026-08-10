package app.radiacode.ui.logic

import app.radiacode.analysis.EnergyWindow
import app.radiacode.analysis.HintConfidence
import app.radiacode.analysis.IsotopeHint
import java.util.Locale
import kotlin.math.roundToInt

/** Pure formatting for the Спектр screen. JVM-tested. */
object SpectrumFormat {

    /** Accumulation clock: «04:32», hours as «1:07:09». */
    fun accumulationClock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = s % 3600 / 60
        val secs = s % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    /** Visible-range indicator: «0–3072 кэВ». */
    fun windowLabel(window: EnergyWindow): String =
        "${window.startKeV.roundToInt()}–${window.endKeV.roundToInt()} кэВ"

    fun confidenceLabel(confidence: HintConfidence): String = when (confidence) {
        HintConfidence.LOW -> "низкая"
        HintConfidence.MEDIUM -> "средняя"
    }

    /** «также похоже: I-131» — alternative candidates for the same peak. */
    fun hintAlternatives(hint: IsotopeHint): String? =
        if (hint.alternatives.isEmpty()) {
            null
        } else {
            "также похоже: ${hint.alternatives.joinToString(", ")}"
        }

    /** Peak-table energy cell: «661,9» (keV, one decimal, comma). */
    fun energyCell(energyKeV: Float): String =
        String.format(Locale.US, "%.1f", energyKeV).replace('.', ',')

    /** Peak-table net-counts cell: «1 240» (rounded, thousands spaced). */
    fun netCell(netCounts: Float): String = groupThousands(netCounts.roundToInt().toLong())

    /** Peak-table SNR cell: «8,2σ». */
    fun snrCell(snr: Float): String =
        String.format(Locale.US, "%.1f", snr).replace('.', ',') + "σ"

    /**
     * Peak-table candidate cell, cautious per SPEC — never «обнаружен»:
     * natural lines read «Bi-214 · природный», the rest carry their
     * confidence: «Cs-137 · средняя ур.».
     */
    fun candidateCell(hint: IsotopeHint): String =
        if (hint.natural) {
            "${hint.isotope} · природный"
        } else {
            "${hint.isotope} · ${confidenceLabel(hint.confidence)} ур."
        }

    /** Header chip: «Δt 12:34 · 184 302 имп». */
    fun accumulationChip(durationSeconds: Long, totalCounts: Long): String =
        "Δt ${accumulationClock(durationSeconds)} · ${groupThousands(totalCounts)} имп"

    /**
     * Calibration footnote: «калибровка: E = −5,6 + 2,41·ch + 4,1·10⁻⁴·ch² ·
     * 1024 канала». Coefficients as the device reports them; a2 in
     * superscript scientific notation.
     */
    fun calibrationLine(a0: Float, a1: Float, a2: Float, channelCount: Int): String {
        val a0Text = String.format(Locale.US, "%.1f", a0)
            .replace('.', ',').replace("-", "−")
        val a1Term = signedTerm(a1, String.format(Locale.US, "%.2f", Math.abs(a1)))
        val a2Term = signedTerm(a2, scientific(Math.abs(a2).toDouble()))
        return "калибровка: E = $a0Text$a1Term·ch$a2Term·ch² · " +
            "$channelCount ${channelsPlural(channelCount)}"
    }

    private fun channelsPlural(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> "каналов"
            mod10 == 1 -> "канал"
            mod10 in 2..4 -> "канала"
            else -> "каналов"
        }
    }

    private fun signedTerm(value: Float, absText: String): String =
        (if (value < 0f) " − " else " + ") + absText.replace('.', ',')

    /** 4.1e-4 → «4,1·10⁻⁴». */
    private fun scientific(value: Double): String {
        if (value == 0.0) return "0"
        val exponent = Math.floor(Math.log10(value)).toInt()
        val mantissa = value / Math.pow(10.0, exponent.toDouble())
        val mantissaText = String.format(Locale.US, "%.1f", mantissa).replace('.', ',')
        return "$mantissaText·10${superscript(exponent)}"
    }

    private val SUPERSCRIPTS = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻',
    )

    private fun superscript(exponent: Int): String =
        exponent.toString().map { SUPERSCRIPTS.getValue(it) }.joinToString("")

    /** Thousands grouped with a space: 184302 → «184 302». */
    fun groupThousands(value: Long): String {
        val digits = value.toString()
        val sb = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && char.isDigit() && (digits.length - index) % 3 == 0) sb.append(' ')
            sb.append(char)
        }
        return sb.toString()
    }
}
