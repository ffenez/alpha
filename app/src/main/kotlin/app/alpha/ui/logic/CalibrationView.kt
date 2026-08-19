package app.alpha.ui.logic

import app.alpha.analysis.CalibrationDataset
import app.alpha.analysis.evidence.BackgroundCalibration
import app.alpha.analysis.evidence.CalibrationReport
import app.alpha.analysis.evidence.CalibrationVerdict
import app.alpha.analysis.evidence.ScaleUncertaintyEstimator
import app.alpha.analysis.evidence.LineRejection
import app.alpha.analysis.evidence.ResolutionFitOutcome
import app.alpha.analysis.evidence.ResolutionFitRefusal
import app.alpha.analysis.evidence.ResolutionFitting
import app.alpha.ui.text.CalibrationRu
import app.alpha.ui.text.CalibrationStrings
import app.alpha.ui.text.HistoryRu
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.uiDecimal
import java.time.ZoneId
import java.util.Locale

/** Строка таблицы опорных линий — ровно то, что рисует экран. */
data class CalibrationLineRow(
    val nuclide: String,
    val tableKeV: String,
    val observedKeV: String,
    val deltaKeV: String,
    val widthKeV: String,
    val significance: String,
    val source: String,
    /** Остаток заметно больше своей σ — строка подсвечивается. */
    val deltaStandsOut: Boolean,
)

/**
 * Сборка экрана «Калибровка (диагностика)» из отчёта движка.
 *
 * Чистый JVM: экран только рисует то, что здесь собрано, а тесты проверяют
 * формулировки и отказы без Android. Каталог приходит ПАРАМЕТРОМ со значением
 * по умолчанию — так же, как у остальной логики `ui/logic`.
 */
object CalibrationView {

    /** Десятичная запятая — общая для всего приложения, не языковая. */
    fun number(value: Double, digits: Int): String =
        String.format(Locale.US, "%.${digits}f", value).uiDecimal()

    fun signed(value: Double, digits: Int): String =
        (if (value >= 0.0) "+" else "−") + number(kotlin.math.abs(value), digits)

    /** Длительность словами берётся у общего форматтера — он уже переведён. */
    fun duration(seconds: Long, h: HistoryStrings = HistoryRu): String =
        HistoryFormat.duration(seconds, h)

    /** Строки блока «Материал»: сколько собрано и откуда. */
    fun material(
        selection: CalibrationDataset.Selection,
        s: CalibrationStrings = CalibrationRu,
        h: HistoryStrings = HistoryRu,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<String> {
        val long = selection.long ?: return listOf(s.noMaterial, s.noMaterialExplained)
        val rows = mutableListOf(
            s.longAccumulation(
                hours = duration(long.seconds, h),
                intervals = long.intervalCount,
                from = HistoryFormat.day(long.fromMillis, zone, h),
                to = HistoryFormat.day(long.toMillis, zone, h),
            ),
        )
        val radon = selection.radonRich
        rows += if (radon != null) {
            s.radonAccumulation(duration(radon.seconds, h), radon.intervalCount)
        } else {
            s.radonNotEnough(
                needHours = (CalibrationDataset.MIN_RADON_SECONDS / 3600L).toInt(),
                haveHours = duration(selection.radonSeconds, h),
            )
        }
        return rows
    }

    /** Таблица измеренных линий. */
    fun lineRows(
        report: CalibrationReport,
        s: CalibrationStrings = CalibrationRu,
    ): List<CalibrationLineRow> = report.measurements.map { m ->
        CalibrationLineRow(
            nuclide = m.line.nuclide,
            tableKeV = number(m.line.energyKeV, 1),
            observedKeV = number(m.observedKeV, 1),
            deltaKeV = signed(m.deltaKeV, 1),
            widthKeV = number(m.fwhmKeV, 1),
            significance = number(m.significance, 0),
            source = if (m.sourceId == CalibrationDataset.SOURCE_RADON) {
                s.sourceRadon
            } else {
                s.sourceLong
            },
            // Порог тот же, что у диагностики сдвига: остаток крупнее двух своих
            // σ заметен глазу и обязан быть заметен на экране.
            deltaStandsOut = m.observedSigmaKeV > 0.0 &&
                kotlin.math.abs(m.deltaKeV) > 2.0 * m.observedSigmaKeV,
        )
    }

    /** Пригодные линии, которых не нашлось; пусто — нашлись все. */
    fun notFound(report: CalibrationReport, s: CalibrationStrings = CalibrationRu): String? {
        if (report.notFound.isEmpty()) return null
        val names = report.notFound.joinToString(" · ") { number(it.line.energyKeV, 1) }
        return s.notFound(names)
    }

    /** Почему часть известных линий в опорные не годится — по одной строке. */
    fun rejected(
        report: CalibrationReport,
        s: CalibrationStrings = CalibrationRu,
        limit: Int = 4,
    ): List<String> = report.candidates
        .filter {
            it.rejection == LineRejection.BLENDED_WITH_OTHER_ACTIVITY ||
                it.rejection == LineRejection.BLEND_SHIFTS_CENTROID
        }
        .sortedByDescending { it.line.intensityPercent }
        .take(limit)
        .map { candidate ->
            val line = number(candidate.line.energyKeV, 1)
            when (candidate.rejection) {
                LineRejection.BLENDED_WITH_OTHER_ACTIVITY -> s.rejectedBlend(
                    line,
                    candidate.blockers.joinToString(" · ") { number(it.energyKeV, 1) },
                )
                else -> s.rejectedShift(line)
            }
        }

    /**
     * Блок модели разрешения: либо формула с границами измеренного, либо
     * отказ с НАЗВАННОЙ причиной — кривой по двум точкам здесь не бывает.
     */
    fun resolution(
        report: CalibrationReport,
        s: CalibrationStrings = CalibrationRu,
    ): List<String> = when (val outcome = report.fit) {
        is ResolutionFitOutcome.Fitted -> listOf(
            s.formula(
                number(outcome.fit.a, 0),
                number(outcome.fit.b, 3),
                number(outcome.fit.c, 6),
            ),
            s.measuredRange(
                number(outcome.fit.extrapolatedBelowKeV, 0),
                number(outcome.fit.extrapolatedAboveKeV, 0),
            ),
            s.extrapolatedBelow(number(outcome.fit.extrapolatedBelowKeV, 0)),
        )
        is ResolutionFitOutcome.Refused -> listOf(s.refusalPrefix(refusalReason(outcome, s)))
    }

    private fun refusalReason(
        outcome: ResolutionFitOutcome.Refused,
        s: CalibrationStrings,
    ): String = when (outcome.reason) {
        ResolutionFitRefusal.NOT_ENOUGH_LINES ->
            s.refusalNotEnoughLines(outcome.points, ResolutionFitting.MIN_POINTS)
        ResolutionFitRefusal.NARROW_ENERGY_SPAN -> s.refusalNarrowSpan(
            number(outcome.spanKeV, 0),
            number(ResolutionFitting.MIN_SPAN_KEV, 0),
        )
        ResolutionFitRefusal.NOT_MONOTONE -> s.refusalNotMonotone
        ResolutionFitRefusal.NEGATIVE_NOISE_TERM -> s.refusalNegativeNoise
    }

    /** Блок энергетической шкалы: σ_cal, сдвиг и диапазон проверки. */
    fun scale(
        report: CalibrationReport,
        s: CalibrationStrings = CalibrationRu,
    ): List<String> {
        val scale = report.scale ?: return listOf(s.shiftNotEvaluated)
        val rows = mutableListOf<String>()
        // Разброс шкалы и сдвиг — разные величины: первая требует трёх линий,
        // второй считается уже по двум. Пока разброса нет, так и сказано —
        // молчание о нём не должно выглядеть отказом от всего раздела.
        val sigmaKeV = scale.sigmaKeV
        val sigmaFraction = scale.sigmaFraction
        if (sigmaKeV != null && sigmaFraction != null) {
            rows += s.sigmaCal(number(sigmaKeV, 1), number(sigmaFraction * 100.0, 2) + " %")
            if (scale.statisticalOnly) rows += s.sigmaCalUpperBound
        } else {
            rows += s.scatterNotEvaluated(
                have = scale.residuals.size,
                need = ScaleUncertaintyEstimator.MIN_RESIDUALS_FOR_SCATTER,
            )
        }
        val shift = scale.shiftKeV
        val sigma = scale.shiftUncertaintyKeV
        rows += when {
            scale.verdict == CalibrationVerdict.SIGMA_NOT_ESTIMATED -> s.shiftNeedsSigma
            shift == null || sigma == null -> s.shiftNotEvaluated
            // Без оценённого разброса шкалы значимость сдвига считать нечем:
            // знаменатель был бы занижен, и «выделенным» оказался бы любой.
            scale.verdict == CalibrationVerdict.SIGMA_NOT_ESTIMATED -> s.shiftNeedsSigma
            scale.verdict == CalibrationVerdict.POSSIBLE_SYSTEMATIC_SHIFT ->
                s.shiftResolved(signed(shift, 1), number(sigma, 1))
            else -> s.shiftNotResolved(signed(shift, 1), number(sigma, 1))
        }
        rows += s.scaleRange(
            number(scale.lowestEnergyKeV, 0),
            number(scale.highestEnergyKeV, 0),
        )
        return rows
    }

    /** Блок частичного относительного отклика. */
    fun response(
        report: CalibrationReport,
        s: CalibrationStrings = CalibrationRu,
    ): List<String> {
        if (report.response.isEmpty()) return listOf(s.responseNone)
        val rows = report.response.map {
            s.responsePoint(
                nuclide = it.nuclide,
                upper = number(it.upperKeV, 1),
                lower = number(it.lowerKeV, 1),
                ratio = number(it.ratio, 2),
                sigma = number(it.sigma, 2),
            )
        }.toMutableList()
        rows += s.responseFewPoints(report.response.size)
        return rows
    }

    /**
     * Чего не хватает. Пустой список — не «всё готово», а «нечего назвать»:
     * экран в этом случае просто не рисует блок.
     */
    fun missing(
        report: CalibrationReport,
        s: CalibrationStrings = CalibrationRu,
        h: HistoryStrings = HistoryRu,
        needHours: Int = MIN_USEFUL_HOURS,
    ): List<String> {
        val rows = mutableListOf<String>()
        val hoursMeasured = report.totalSeconds / 3600L
        if (hoursMeasured < needHours) {
            rows += s.needHours(duration(report.totalSeconds, h), needHours)
        }
        notFound(report, s)?.let { rows += it }
        if (rows.isNotEmpty()) rows += s.needMore
        return rows
    }

    /**
     * Сколько часов записи считаем осмысленным минимумом — **инженерный
     * параметр экрана**, а не критерий движка: подгонка отказывает по числу
     * ИЗМЕРЕННЫХ линий, а это число нужно, чтобы честно сказать «данных мало»
     * до того, как человек начнёт искать причину пустой таблицы.
     */
    const val MIN_USEFUL_HOURS = 24

    /** Версия математики, которой получен отчёт, — для строки о принятии. */
    const val ALGORITHM_VERSION = BackgroundCalibration.ALGORITHM_VERSION
}
