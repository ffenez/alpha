package app.alpha.ui.logic

import app.alpha.ui.text.EfficiencyRu
import app.alpha.ui.text.EfficiencyStrings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Активность словами: значение, единица и число знаков.
 *
 * Единица выбирается по величине (Бк → кБк → МБк), потому что диапазон
 * бытовых источников — от единиц беккерелей до мегабеккерелей, и «37000000 Бк»
 * читается хуже, чем «37 МБк». Знаков после запятой ровно столько, сколько
 * несёт смысл: три значащие цифры и не больше — активность известна с
 * точностью процентов, и четвёртая цифра была бы выдумана.
 */
object ActivityFormat {

    /** Порог перехода к килобеккерелям. */
    private const val KILO = 1_000.0

    /** Порог перехода к мегабеккерелям. */
    private const val MEGA = 1_000_000.0

    /**
     * Значение с единицей: «1,2 кБк», «37 МБк», «84 Бк».
     *
     * @param becquerel активность, Бк; отрицательная приводится к нулю
     */
    fun value(becquerel: Double, s: EfficiencyStrings = EfficiencyRu): String {
        val value = becquerel.coerceAtLeast(0.0)
        val (scaled, unit) = when {
            value >= MEGA -> value / MEGA to s.unitMBq
            value >= KILO -> value / KILO to s.unitKBq
            else -> value to s.unitBq
        }
        return "${significant(scaled)} $unit"
    }

    /**
     * Относительная неопределённость целыми процентами: у активности она
     * редко ниже нескольких процентов, и десятые доли процента при этом —
     * ложная точность.
     */
    fun percent(relative: Double): String = (abs(relative) * 100).roundToInt().toString()

    /** Три значащие цифры: 1,23 · 12,3 · 123. */
    private fun significant(value: Double): String {
        val decimals = when {
            value >= 100.0 -> 0
            value >= 10.0 -> 1
            else -> 2
        }
        return String.format(Locale.US, "%.${decimals}f", value).replace('.', ',')
    }
}
