package app.alpha.ui.logic

import app.alpha.analysis.NuclideTrend
import app.alpha.analysis.PeakDetection
import app.alpha.analysis.RadonTrend
import app.alpha.ui.text.SessionRadonStrings
import app.alpha.ui.text.uiDecimal
import java.util.Locale

/**
 * Результат ряда линии — словами, потом числами.
 *
 * Экран показывал `−0,0 ±0,0 нетто` и `значимость −12,6` как главный ответ.
 * Это внутренние величины расчёта: отрицательное нетто означает лишь, что
 * оценка континуума по бокам окна вышла чуть выше самого окна, то есть линии
 * там нет. В роли ответа такое число не читается никак.
 */
object LineTrendReport {

    fun build(
        line: NuclideTrend.Line,
        summary: NuclideTrend.Summary?,
        spanText: String?,
        t: SessionRadonStrings,
    ): AnalyticsResult {
        val verdict = ExcessVerdict.of(summary?.significance, NuclideTrend.MIN_SIGNIFICANCE)
        val details = mutableListOf<WhyLine>()
        if (summary != null) {
            details += WhyLine(
                label = t.detailNet,
                value = t.netWithSigma(num2(summary.netCps), num2(summary.sigmaCps)),
            )
            details += WhyLine(
                label = t.detailSignificance,
                value = t.sigmaUnits(num1(summary.significance)),
                note = t.detailSignificanceNote,
            )
        }
        // Окно — то же, по которому и считалось: ±FWHM модели разрешения.
        details += WhyLine(
            label = t.detailWindow,
            value = t.windowAround(
                num1(line.energyKeV),
                num1(PeakDetection.fwhmKeV(line.energyKeV)),
            ),
        )
        details += WhyLine(label = t.detailContinuum, value = t.detailContinuumValue)

        return AnalyticsResult(
            verdict = when (verdict) {
                ExcessVerdict.EXCESS -> t.lineResultExcess
                ExcessVerdict.NOT_RESOLVED -> t.lineResultPlain
                ExcessVerdict.NO_DATA -> t.lineResultNoData
            },
            tone = when (verdict) {
                ExcessVerdict.EXCESS -> ResultTone.NOTABLE
                ExcessVerdict.NOT_RESOLVED -> ResultTone.PLAIN
                ExcessVerdict.NO_DATA -> ResultTone.UNKNOWN
            },
            measurement = summary?.let {
                measurement(it.points, spanText, t)
            },
            // «Данных мало» — отказ метода: он объясняет ПУСТОЙ экран и
            // остаётся при выключенных пояснениях.
            meaning = when (verdict) {
                ExcessVerdict.EXCESS -> t.lineMeaningExcess(line.label)
                ExcessVerdict.NOT_RESOLVED -> t.lineMeaningPlain(line.label)
                ExcessVerdict.NO_DATA -> ""
            },
            unavailable = t.lineMeaningNoData.takeIf { verdict == ExcessVerdict.NO_DATA },
            limitation = t.lineTrendCaveat,
            details = if (summary == null) emptyList() else details,
        )
    }
}

/**
 * Результат радонового индикатора — по тому же шаблону и с той же причиной.
 *
 * Здесь к ней добавляется вторая: «−0,29 сейчас» рядом со словом «радон»
 * читается как измеренная концентрация с непонятным знаком. Концентрации этот
 * экран не измеряет вовсе, и ограничение стоит в самой карточке.
 */
object RadonReport {

    fun build(
        current: RadonTrend.HourPoint?,
        median: Float?,
        hours: Int,
        spanText: String?,
        t: SessionRadonStrings,
    ): AnalyticsResult {
        val significance = current
            ?.takeIf { it.sigmaCps > 0f }
            ?.let { it.rateCps / it.sigmaCps }
        val verdict = ExcessVerdict.of(
            significance = if (current == null) null else significance ?: 0f,
            minSignificance = NuclideTrend.MIN_SIGNIFICANCE,
        )
        val details = mutableListOf<WhyLine>()
        current?.let {
            details += WhyLine(
                label = t.detailCurrent,
                value = t.netWithSigma(num2(it.rateCps), num2(it.sigmaCps)),
            )
        }
        significance?.let {
            details += WhyLine(
                label = t.detailSignificance,
                value = t.sigmaUnits(num1(it)),
                note = t.detailSignificanceNote,
            )
        }
        median?.let { details += WhyLine(label = t.detailMedian, value = num2(it)) }
        if (current != null && median != null && median > 0f) {
            details += WhyLine(
                label = t.detailToMedian,
                value = "×" + num1(current.rateCps / median),
            )
        }
        details += WhyLine(
            label = t.detailWindow,
            value = "Bi-214 ${num1(RadonTrend.BI214_KEV)} · Pb-214 ${num1(RadonTrend.PB214_KEV)}",
        )
        details += WhyLine(label = t.detailContinuum, value = t.detailContinuumValue)

        return AnalyticsResult(
            verdict = when (verdict) {
                ExcessVerdict.EXCESS -> t.radonResultNotable
                ExcessVerdict.NOT_RESOLVED -> t.radonResultPlain
                ExcessVerdict.NO_DATA -> t.radonResultNoData
            },
            tone = when (verdict) {
                ExcessVerdict.EXCESS -> ResultTone.NOTABLE
                ExcessVerdict.NOT_RESOLVED -> ResultTone.PLAIN
                ExcessVerdict.NO_DATA -> ResultTone.UNKNOWN
            },
            measurement = if (hours > 0) measurement(hours, spanText, t) else null,
            meaning = when (verdict) {
                ExcessVerdict.EXCESS -> t.radonMeaningNotable
                ExcessVerdict.NOT_RESOLVED -> t.radonMeaningPlain
                ExcessVerdict.NO_DATA -> ""
            },
            unavailable = t.radonMeaningNoData.takeIf { verdict == ExcessVerdict.NO_DATA },
            limitation = t.radonLimit,
            details = if (current == null) emptyList() else details,
        )
    }
}

/**
 * «13 часовых интервалов · охват 11 ч».
 *
 * Число точек без названия единицы («13 точек») не говорит, чем измерено, а
 * охват без числа точек не говорит, сколько в нём дыр: одиннадцать часов могут
 * состоять из тринадцати часовых интервалов и из трёх.
 */
private fun measurement(intervals: Int, spanText: String?, t: SessionRadonStrings): String {
    val count = t.hourIntervals(intervals)
    return if (spanText == null) count else t.measuredIntervals(count, spanText)
}

private fun num1(value: Float): String =
    String.format(Locale.US, "%.1f", value).uiDecimal()

private fun num2(value: Float): String =
    String.format(Locale.US, "%.2f", value).uiDecimal()
