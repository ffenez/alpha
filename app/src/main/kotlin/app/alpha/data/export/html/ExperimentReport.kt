package app.alpha.data.export.html

import app.alpha.data.export.html.HtmlDocument.facts
import app.alpha.data.export.html.HtmlDocument.hero
import app.alpha.data.export.html.HtmlDocument.note
import app.alpha.data.export.html.HtmlDocument.section

/** Один прогон опыта в отчёте. */
data class ReportRun(
    val label: String,
    val timeText: String,
    val durationText: String,
    val counts: Long?,
    val rateText: String?,
    /** Спектр прогона, если он снимался. */
    val spectrum: List<Int>?,
    val energies: List<Double>?,
)

/** Строка сравнения: величина, A, B, значимость и вывод словами. */
data class ReportComparison(
    val label: String,
    val a: String,
    val b: String,
    val significance: String,
    val verdict: String,
)

/**
 * Отчёт об опыте A/B.
 *
 * HTML полезен здесь больше всего: он сохраняет ГЕОМЕТРИЮ — как лежал образец,
 * на каком расстоянии, чем экранировали, — то самое, что теряет таблица чисел,
 * и без чего результат перестаёт быть воспроизводимым.
 *
 * Вывод остаётся описательным: «различие подтверждено» говорит про ЭТИ
 * измерения в ЭТОЙ геометрии и не превращается в утверждение о веществе или
 * его опасности (§25 ТЗ).
 */
data class ExperimentReport(
    val title: String,
    val subtitle: String?,
    val verdictText: String,
    val geometry: List<Pair<String, String>>,
    val runs: List<ReportRun>,
    val comparisons: List<ReportComparison>,
    val details: List<Pair<String, String>>,
    val notes: List<String>,
    val footer: String,
    val strings: ReportStrings,
)

object ExperimentReportHtml {

    const val TYPE = "experiment"

    fun render(report: ExperimentReport): String {
        val s = report.strings
        return HtmlDocument.page(
            type = TYPE,
            title = report.title,
            subtitle = report.subtitle,
            metadata = report.details,
            footer = report.footer,
        ) {
            hero(listOf(Triple(report.verdictText, s.resultLabel, null)))

            if (report.geometry.isNotEmpty()) {
                section(s.geometrySection) { facts(report.geometry) }
            }

            if (report.runs.isNotEmpty()) {
                section(s.runsSection) {
                    append("<table>\n<thead><tr>")
                    append("<th scope=\"col\">").append(HtmlDocument.escape(s.runLabel))
                    append("</th><th scope=\"col\">").append(HtmlDocument.escape(s.duration))
                    append("</th><th scope=\"col\">").append(HtmlDocument.escape(s.totalCounts))
                    append("</th><th scope=\"col\">").append(HtmlDocument.escape(s.countSection))
                    append("</th></tr></thead>\n<tbody>\n")
                    for (run in report.runs) {
                        append("<tr><th scope=\"row\">").append(HtmlDocument.escape(run.label))
                        append("</th><td>").append(HtmlDocument.escape(run.durationText))
                        append("</td><td>")
                        append(HtmlDocument.escape(run.counts?.let { formatCount(it) } ?: "—"))
                        append("</td><td>").append(HtmlDocument.escape(run.rateText ?: "—"))
                        append("</td></tr>\n")
                    }
                    append("</tbody>\n</table>\n")
                }
            }

            val withSpectra = report.runs.filter { !it.spectrum.isNullOrEmpty() }
            if (withSpectra.isNotEmpty()) {
                section(s.spectrumSection) {
                    for ((index, run) in withSpectra.withIndex()) {
                        val counts = run.spectrum ?: continue
                        val energies = run.energies ?: counts.indices.map { it.toDouble() }
                        append("<h3>").append(HtmlDocument.escape(run.label)).append("</h3>\n")
                        append(
                            HtmlChart.figure(
                                id = "run$index",
                                points = counts.mapIndexed { channel, value ->
                                    HtmlChart.Point(
                                        x = energies.getOrElse(channel) { channel.toDouble() },
                                        value = value.toDouble(),
                                        label = s.peakReadout(
                                            energies.getOrElse(channel) { channel.toDouble() },
                                            value.toDouble(),
                                        ),
                                    )
                                },
                                axisLabels = emptyList(),
                                valueUnit = s.countsUnit,
                                title = run.label,
                                logarithmic = true,
                                labels = s.chartLabels,
                            ),
                        )
                    }
                }
            }

            if (report.comparisons.isNotEmpty()) {
                section(s.comparisonSection) {
                    append("<table>\n<thead><tr>")
                    append("<th scope=\"col\">").append(HtmlDocument.escape(s.quantity))
                    append("</th><th scope=\"col\">A</th><th scope=\"col\">B</th>")
                    append("<th scope=\"col\">").append(HtmlDocument.escape(s.significance))
                    append("</th><th scope=\"col\">").append(HtmlDocument.escape(s.resultLabel))
                    append("</th></tr></thead>\n<tbody>\n")
                    for (row in report.comparisons) {
                        append("<tr><th scope=\"row\">").append(HtmlDocument.escape(row.label))
                        append("</th><td>").append(HtmlDocument.escape(row.a))
                        append("</td><td>").append(HtmlDocument.escape(row.b))
                        append("</td><td>").append(HtmlDocument.escape(row.significance))
                        append("</td><td>").append(HtmlDocument.escape(row.verdict))
                        append("</td></tr>\n")
                    }
                    append("</tbody>\n</table>\n")
                }
            }

            if (report.details.isNotEmpty()) section(s.detailsSection) { facts(report.details) }
            section(s.notesSection) {
                note(s.experimentDisclaimer)
                for (line in report.notes) note(line)
            }
        }
    }

    private fun formatCount(value: Long): String =
        value.toString().reversed().chunked(3).joinToString(" ").reversed()
}
