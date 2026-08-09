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

    /**
     * SPEC wording, verbatim shape: «Возможное совпадение: Cs-137 · пик у
     * 662 кэВ · уверенность: низкая · нужно подтверждение». Never «обнаружен».
     */
    fun hintLine(hint: IsotopeHint): String =
        "Возможное совпадение: ${hint.isotope} · " +
            "пик у ${hint.peak.energyKeV.roundToInt()} кэВ · " +
            "уверенность: ${confidenceLabel(hint.confidence)} · нужно подтверждение"

    fun confidenceLabel(confidence: HintConfidence): String = when (confidence) {
        HintConfidence.LOW -> "низкая"
        HintConfidence.MEDIUM -> "средняя"
    }

    /** Calming note for natural lines: «обычный природный фон». */
    fun hintNote(hint: IsotopeHint): String? = when {
        hint.natural && hint.chain != null ->
            "${hint.isotope} — из цепочки ${hint.chain}, обычный природный фон"
        hint.natural -> "${hint.isotope} — обычный природный фон"
        else -> null
    }

    /** «также похоже: I-131» — alternative candidates for the same peak. */
    fun hintAlternatives(hint: IsotopeHint): String? =
        if (hint.alternatives.isEmpty()) {
            null
        } else {
            "также похоже: ${hint.alternatives.joinToString(", ")}"
        }
}
