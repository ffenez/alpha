package app.alpha.data.export.html

import app.alpha.data.export.html.HtmlDocument.facts
import app.alpha.data.export.html.HtmlDocument.note
import app.alpha.data.export.html.HtmlDocument.section

/**
 * Сводный отчёт по нескольким записям.
 *
 * ## Одна ось на всех
 *
 * Записи сделаны в разное время, поэтому по оси отложено время ОТ НАЧАЛА каждой
 * записи, а не календарное: сравнивают ход измерения, а не даты. Это сказано на
 * самом графике подписью оси — иначе читатель решит, что кривые сняты
 * одновременно.
 *
 * ## Что отчёт не делает
 *
 * Он не объявляет записи одинаковыми и не выносит вердикта: наложенные кривые —
 * это наблюдение. Сравнение с проверкой различия живёт в опыте A/B, где для
 * него есть геометрия и критерий.
 */
data class ComparisonReport(
    val title: String,
    val subtitle: String?,
    val unit: String,
    /** Каждая запись — своя кривая; точки уже переведены в секунды от начала. */
    val series: List<ReportSeries>,
    val columns: List<String>,
    val rows: List<List<String>>,
    val details: List<Pair<String, String>>,
    val notes: List<String>,
    val footer: String,
    val strings: ReportStrings,
    /** Подпись оси: секунды от начала → «12 мин». */
    val elapsedLabel: (Double) -> String,
)

object ComparisonReportHtml {

    const val TYPE = "comparison"

    fun render(report: ComparisonReport): String {
        val s = report.strings
        return HtmlDocument.page(
            type = TYPE,
            title = report.title,
            subtitle = report.subtitle,
            metadata = report.details,
            footer = report.footer,
        ) {
            val drawn = report.series.filter { it.points.isNotEmpty() }
            if (drawn.isNotEmpty()) {
                section(s.doseSection) {
                    append(
                        HtmlChart.overlay(
                            id = "comparison",
                            series = drawn.map { series ->
                                HtmlChart.Series(
                                    label = series.title,
                                    points = series.points.map { (elapsed, value) ->
                                        HtmlChart.Point(
                                            x = elapsed.toDouble(),
                                            value = value,
                                            label = "${series.title} · " +
                                                report.elapsedLabel(elapsed.toDouble()) + " · " +
                                                "${SessionReportHtml.formatValue(value)} " +
                                                report.unit,
                                        )
                                    },
                                )
                            },
                            axisLabels = elapsedAxis(drawn, report.elapsedLabel),
                            valueUnit = report.unit,
                            title = report.title,
                            labels = s.chartLabels,
                        ),
                    )
                    note(s.elapsedAxisNote)
                }
            }

            if (report.rows.isNotEmpty()) {
                section(s.recordsSection) {
                    append("<table>\n<thead><tr>")
                    for (column in report.columns) {
                        append("<th scope=\"col\">").append(HtmlDocument.escape(column))
                            .append("</th>")
                    }
                    append("</tr></thead>\n<tbody>\n")
                    for (row in report.rows) {
                        append("<tr>")
                        for ((index, cell) in row.withIndex()) {
                            if (index == 0) {
                                append("<th scope=\"row\">").append(HtmlDocument.escape(cell))
                                    .append("</th>")
                            } else {
                                append("<td>").append(HtmlDocument.escape(cell)).append("</td>")
                            }
                        }
                        append("</tr>\n")
                    }
                    append("</tbody>\n</table>\n")
                }
            }

            if (report.details.isNotEmpty()) section(s.detailsSection) { facts(report.details) }
            section(s.notesSection) {
                note(s.comparisonDisclaimer)
                for (line in report.notes) note(line)
            }
        }
    }

    /** Шесть подписей по самой длинной записи: короткие кривые кончаются раньше. */
    private fun elapsedAxis(
        series: List<ReportSeries>,
        label: (Double) -> String,
    ): List<Pair<Double, String>> {
        val last = series.maxOf { s -> s.points.maxOf { it.first } }.toDouble()
        if (last <= 0) return emptyList()
        val count = 5
        return (0..count).map { step ->
            val at = last * step / count
            at to label(at)
        }
    }
}
