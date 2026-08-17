package app.radiacode.data.export.html

import app.radiacode.data.export.html.HtmlDocument.facts
import app.radiacode.data.export.html.HtmlDocument.hero
import app.radiacode.data.export.html.HtmlDocument.note
import app.radiacode.data.export.html.HtmlDocument.section

/** Ряд отчёта: подписанные точки во времени и единица величины. */
data class ReportSeries(
    val title: String,
    val unit: String,
    /** Момент (мс) → значение; момент нужен для подписи и связи с картой. */
    val points: List<Pair<Long, Double>>,
)

/** Событие журнала в отчёте: когда и что это было. */
data class ReportEvent(val timeText: String, val text: String)

/**
 * Отчёт о сессии измерений.
 *
 * Сессия — это отрезок времени, когда прибор был на связи: отчёт отвечает на
 * вопрос «что показывал прибор всё это время», а не «какие строки лежат в
 * таблице». Пустых разделов в нём нет: если жёсткости не считали, блока
 * жёсткости не существует (§18 ТЗ).
 */
data class SessionReport(
    val title: String,
    val subtitle: String?,
    val heroCells: List<Triple<String, String, String?>>,
    val series: List<ReportSeries>,
    val events: List<ReportEvent>,
    val details: List<Pair<String, String>>,
    val notes: List<String>,
    val footer: String,
    val strings: ReportStrings,
    /** Подписи оси времени: доля момента → человеческое время. */
    val timeLabel: (Long) -> String,
)

object SessionReportHtml {

    const val TYPE = "session"

    fun render(report: SessionReport): String {
        val s = report.strings
        return HtmlDocument.page(
            type = TYPE,
            title = report.title,
            subtitle = report.subtitle,
            metadata = report.details,
            footer = report.footer,
        ) {
            hero(report.heroCells)

            for ((index, series) in report.series.withIndex()) {
                if (series.points.isEmpty()) continue
                section(series.title) {
                    append(
                        HtmlChart.figure(
                            id = "series$index",
                            points = series.points.map { (time, value) ->
                                HtmlChart.Point(
                                    x = time.toDouble(),
                                    value = value,
                                    label = "${report.timeLabel(time)} · " +
                                        "${formatValue(value)} ${series.unit}",
                                )
                            },
                            axisLabels = timeAxis(series.points.map { it.first }, report.timeLabel),
                            valueUnit = series.unit,
                            title = series.title,
                        ),
                    )
                }
            }

            if (report.events.isNotEmpty()) {
                section(s.eventsSection) {
                    append("<table>\n<tbody>\n")
                    for (event in report.events) {
                        append("<tr><th scope=\"row\">")
                        append(HtmlDocument.escape(event.timeText))
                        append("</th><td>").append(HtmlDocument.escape(event.text))
                        append("</td></tr>\n")
                    }
                    append("</tbody>\n</table>\n")
                }
            }

            if (report.details.isNotEmpty()) section(s.detailsSection) { facts(report.details) }
            if (report.notes.isNotEmpty()) {
                section(s.notesSection) { for (line in report.notes) note(line) }
            }
        }
    }

    /** Пять-шесть подписей времени: больше на ширине отчёта не читается. */
    internal fun timeAxis(times: List<Long>, label: (Long) -> String): List<Pair<Double, String>> {
        if (times.isEmpty()) return emptyList()
        val first = times.first()
        val last = times.last()
        if (last <= first) return listOf(first.toDouble() to label(first))
        val count = 5
        return (0..count).map { step ->
            val at = first + (last - first) * step / count
            at.toDouble() to label(at)
        }
    }

    internal fun formatValue(value: Double): String = when {
        value >= 100 -> String.format(java.util.Locale.US, "%.0f", value)
        value >= 10 -> String.format(java.util.Locale.US, "%.1f", value).replace('.', ',')
        else -> String.format(java.util.Locale.US, "%.3f", value).replace('.', ',')
    }
}
