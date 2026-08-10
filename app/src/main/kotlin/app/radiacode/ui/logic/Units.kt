package app.radiacode.ui.logic

import app.radiacode.data.DoseUnitSetting
import java.util.Locale

/**
 * The single dose-display formatter (SPEC «Единицы»): µSv ⇄ µR conversion is
 * display-only (1 µSv/h = 100 µR/h), raw stored values never change
 * (CLAUDE.md invariant). Every screen that shows a dose value goes through
 * this object so the unit toggle applies everywhere at once.
 */
object DoseFormat {

    const val MICRO_R_PER_MICRO_SV = 100f

    fun rateValue(microSvH: Float, unit: DoseUnitSetting): Float = when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> microSvH
        DoseUnitSetting.MICRO_ROENTGEN -> microSvH * MICRO_R_PER_MICRO_SV
    }

    /** Value only, stable digit count so updating numbers do not jitter. */
    fun rate(microSvH: Float, unit: DoseUnitSetting): String =
        format(rateValue(microSvH, unit), unit)

    fun rateWithUnit(microSvH: Float, unit: DoseUnitSetting): String =
        "${rate(microSvH, unit)} ${rateUnitLabel(unit)}"

    fun rateUnitLabel(unit: DoseUnitSetting): String = when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> "мкЗв/ч"
        DoseUnitSetting.MICRO_ROENTGEN -> "мкР/ч"
    }

    /** Accumulated dose (µSv stored) in the display unit. */
    fun dose(microSv: Double, unit: DoseUnitSetting): String = when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> format(microSv.toFloat(), unit)
        DoseUnitSetting.MICRO_ROENTGEN ->
            format((microSv * MICRO_R_PER_MICRO_SV).toFloat(), unit)
    }

    fun doseWithUnit(microSv: Double, unit: DoseUnitSetting): String =
        "${dose(microSv, unit)} ${doseUnitLabel(unit)}"

    fun doseUnitLabel(unit: DoseUnitSetting): String = when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> "мкЗв"
        DoseUnitSetting.MICRO_ROENTGEN -> "мкР"
    }

    /**
     * Dose of an *extrapolation* (spec §6 projection): precision falls as the
     * number grows and thousands are grouped — «1 310 мкЗв», «124 мкЗв»,
     * «12,3 мкЗв», «0,42 мкЗв».
     * A projected year written as «1234,56» would claim a precision the
     * assumption «rate stays the same» cannot carry.
     */
    fun doseCoarseWithUnit(microSv: Double, unit: DoseUnitSetting): String {
        val value = when (unit) {
            DoseUnitSetting.MICRO_SIEVERT -> microSv
            DoseUnitSetting.MICRO_ROENTGEN -> microSv * MICRO_R_PER_MICRO_SV
        }
        val text = when {
            value >= 1000.0 -> group((Math.round(value / 10.0) * 10L))
            value >= 100.0 -> Math.round(value).toString()
            value >= 10.0 -> String.format(Locale.US, "%.1f", value).replace('.', ',')
            else -> String.format(Locale.US, "%.2f", value).replace('.', ',')
        }
        return "$text ${doseUnitLabel(unit)}"
    }

    /** Thousands grouped with a non-breaking-ish space: 12340 → «12 340». */
    private fun group(value: Long): String {
        val digits = value.toString()
        val sb = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) sb.append(' ')
            sb.append(char)
        }
        return sb.toString()
    }

    /** «0,09–0,14» — the baseline typical band, values in the display unit. */
    fun range(lowMicroSvH: Float, highMicroSvH: Float, unit: DoseUnitSetting): String =
        "${rate(lowMicroSvH, unit)}–${rate(highMicroSvH, unit)}"

    /**
     * µSv/h keeps two decimals (0,12); µR/h values are 100× larger, so one
     * decimal below 100 (12,4) and whole numbers above (124). Decimal comma —
     * the app's copy is Russian (design-language.md).
     */
    private fun format(value: Float, unit: DoseUnitSetting): String = when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> String.format(Locale.US, "%.2f", value)
        DoseUnitSetting.MICRO_ROENTGEN ->
            if (value >= 100f) {
                String.format(Locale.US, "%.0f", value)
            } else {
                String.format(Locale.US, "%.1f", value)
            }
    }.replace('.', ',')
}
