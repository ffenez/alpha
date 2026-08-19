package app.alpha.ui.logic

import app.alpha.data.DoseUnitSetting
import app.alpha.ui.text.RuStrings
import app.alpha.ui.text.Strings
import app.alpha.ui.text.uiDecimal
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

    fun rateWithUnit(microSvH: Float, unit: DoseUnitSetting, s: Strings = RuStrings): String =
        "${rate(microSvH, unit)} ${rateUnitLabel(unit, s)}"

    fun rateUnitLabel(unit: DoseUnitSetting, s: Strings = RuStrings): String = when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> s.unitMicroSv
        DoseUnitSetting.MICRO_ROENTGEN -> s.unitMicroR
    }

    /** Accumulated dose (µSv stored) in the display unit. */
    fun dose(microSv: Double, unit: DoseUnitSetting): String = when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> format(microSv.toFloat(), unit)
        DoseUnitSetting.MICRO_ROENTGEN ->
            format((microSv * MICRO_R_PER_MICRO_SV).toFloat(), unit)
    }

    fun doseWithUnit(microSv: Double, unit: DoseUnitSetting, s: Strings = RuStrings): String =
        "${dose(microSv, unit)} ${doseUnitLabel(unit, s)}"

    fun doseUnitLabel(unit: DoseUnitSetting, s: Strings = RuStrings): String = when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> s.unitDoseMicroSv
        DoseUnitSetting.MICRO_ROENTGEN -> s.unitDoseMicroR
    }

    /**
     * Dose of an *extrapolation* (spec §6 projection): precision falls as the
     * number grows and thousands are grouped — «1 310 мкЗв», «124 мкЗв»,
     * «12,3 мкЗв», «0,42 мкЗв».
     * A projected year written as «1234,56» would claim a precision the
     * assumption «rate stays the same» cannot carry.
     */
    fun doseCoarseWithUnit(
        microSv: Double,
        unit: DoseUnitSetting,
        s: Strings = RuStrings,
    ): String {
        val value = when (unit) {
            DoseUnitSetting.MICRO_SIEVERT -> microSv
            DoseUnitSetting.MICRO_ROENTGEN -> microSv * MICRO_R_PER_MICRO_SV
        }
        val text = when {
            value >= 1000.0 -> group((Math.round(value / 10.0) * 10L))
            value >= 100.0 -> Math.round(value).toString()
            value >= 10.0 -> String.format(Locale.US, "%.1f", value).uiDecimal()
            else -> String.format(Locale.US, "%.2f", value).uiDecimal()
        }
        return "$text ${doseUnitLabel(unit, s)}"
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

    /**
     * Средняя мощность, из которой посчитана проекция, — с точностью, при
     * которой проекция ВОСПРОИЗВОДИТСЯ.
     *
     * Обычный формат печатал 0,16 мкЗв/ч, а проекция считалась из 0,1553:
     * человек, перепроверив на калькуляторе, получал 1 400 вместо 1 360 и был
     * прав. Три знака после запятой возвращают согласованность.
     */
    fun rateBasisWithUnit(
        microSvH: Double,
        unit: DoseUnitSetting,
        s: Strings = RuStrings,
    ): String {
        val value = when (unit) {
            DoseUnitSetting.MICRO_SIEVERT -> microSvH
            DoseUnitSetting.MICRO_ROENTGEN -> microSvH * MICRO_R_PER_MICRO_SV
        }
        val digits = when (unit) {
            DoseUnitSetting.MICRO_SIEVERT -> 3
            DoseUnitSetting.MICRO_ROENTGEN -> 1
        }
        val text = String.format(Locale.US, "%.${digits}f", value).uiDecimal()
        return "$text ${rateUnitLabel(unit, s)}"
    }

    /** «0,09–0,14» — the baseline typical band, values in the display unit. */
    fun range(lowMicroSvH: Float, highMicroSvH: Float, unit: DoseUnitSetting): String =
        "${rate(lowMicroSvH, unit)}–${rate(highMicroSvH, unit)}"

    /**
     * Точность следует за величиной, а не за единицей.
     *
     * Фиксированные два знака теряли измерение: короткая сессия со средней
     * 0,003 мкЗв/ч печаталась как «0,00», то есть интерфейс показывал ноль там,
     * где прибор что-то измерил. Мелкие значения получают столько знаков,
     * сколько нужно, чтобы остаться числом; крупные — не больше, чем несут.
     */
    private fun format(value: Float, unit: DoseUnitSetting): String = when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> when {
            value <= 0f -> "0.00"
            value < 0.001f -> String.format(Locale.US, "%.4f", value)
            value < 0.01f -> String.format(Locale.US, "%.3f", value)
            else -> String.format(Locale.US, "%.2f", value)
        }
        DoseUnitSetting.MICRO_ROENTGEN -> when {
            value >= 100f -> String.format(Locale.US, "%.0f", value)
            value >= 1f -> String.format(Locale.US, "%.1f", value)
            value <= 0f -> "0.0"
            value < 0.1f -> String.format(Locale.US, "%.3f", value)
            else -> String.format(Locale.US, "%.2f", value)
        }
    }.uiDecimal()
}
