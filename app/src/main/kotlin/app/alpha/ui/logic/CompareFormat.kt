package app.alpha.ui.logic

import app.alpha.analysis.SpectrumCompare
import app.alpha.ui.text.CompareRu
import app.alpha.ui.text.CompareStrings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure formatting for the spectrum comparator screen. JVM-tested. */
object CompareFormat {

    /**
     * Осторожные формулировки вердикта — та же лестница, что у подсказок о
     * нуклидах.
     *
     * «Различие не выделено» — не утверждение о равенстве спектров, а описание
     * того, что критерий не выделил различия: |z| < 2. Слово «значимое»
     * не используется: значимость ОПРЕДЕЛЕНА только там, где число стоит рядом
     * в той же строке таблицы, поэтому |z| ≥ 4 называется «устойчивым».
     *
     * Каталог приходит параметром: функцию зовут и экран, и тесты, а
     * композиционный `LocalStrings` в чистую логику не попадает.
     */
    fun verdictLabel(
        verdict: SpectrumCompare.Verdict,
        s: CompareStrings = CompareRu,
    ): String = when (verdict) {
        SpectrumCompare.Verdict.NOISE -> s.verdictNoDifference
        SpectrumCompare.Verdict.POSSIBLE_EXCESS -> s.verdictPossibleExcess
        SpectrumCompare.Verdict.EXCESS -> s.verdictExcess
        SpectrumCompare.Verdict.POSSIBLE_DEFICIT -> s.verdictPossibleDeficit
        SpectrumCompare.Verdict.DEFICIT -> s.verdictDeficit
    }

    /** «300–700 кэВ». */
    fun regionLabel(startKeV: Float, endKeV: Float): String =
        "${startKeV.roundToInt()}–${endKeV.roundToInt()}"

    /**
     * Signed compact cps: «+9,2», «−0,42», «0» — precision follows magnitude
     * so tiny rates stay readable and big ones stay short.
     */
    fun cps(value: Float): String {
        if (value == 0f) return "0"
        val magnitude = abs(value)
        val digits = when {
            magnitude >= 100f -> 0
            magnitude >= 1f -> 1
            magnitude >= 0.01f -> 2
            else -> 3
        }
        val text = String.format(Locale.US, "%.${digits}f", magnitude).replace('.', ',')
        return (if (value < 0f) "−" else "+") + text
    }

    /** «+5,3σ» — signed significance. */
    fun zLabel(z: Float): String {
        val text = String.format(Locale.US, "%.1f", abs(z)).replace('.', ',')
        return (if (z < 0f) "−" else "+") + text + "σ"
    }

    /** Unsigned y-axis label for the difference chart: «0,42». */
    fun axisCps(value: Float): String {
        val magnitude = abs(value)
        val digits = when {
            magnitude >= 100f -> 0
            magnitude >= 1f -> 1
            magnitude >= 0.01f -> 2
            else -> 3
        }
        return String.format(Locale.US, "%.${digits}f", magnitude).replace('.', ',')
    }
}
