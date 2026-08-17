package app.alpha.data.export.html

import app.alpha.data.export.html.HtmlDocument.facts
import app.alpha.data.export.html.HtmlDocument.hero
import app.alpha.data.export.html.HtmlDocument.note
import app.alpha.data.export.html.HtmlDocument.section

/**
 * Отчёт о спектре — то, что человек открывает в браузере.
 *
 * ## Почему модель отдельно от базы
 *
 * Отчёт обязан пережить перестройку таблиц: он объясняет ИЗМЕРЕНИЕ, а не
 * показывает строку `spectra`. Поэтому здесь — только числа и слова, а
 * превращение «строка базы → отчёт» живёт на стороне приложения (§35 ТЗ).
 *
 * ## Чего отчёт не заменяет
 *
 * N42 и XML. HTML отвечает человеку, машинные форматы — программам, и
 * подменять одно другим нельзя: рядом с отчётом всегда есть кнопка выгрузки
 * данных (§22 ТЗ).
 */
data class SpectrumReport(
    val title: String,
    val subtitle: String?,
    /** Накопление словами: «126:47:03». */
    val durationText: String,
    val totalCounts: Long,
    /** Отсчёты по каналам — как они есть в приборе. */
    val counts: List<Int>,
    /** Энергия центра канала, кэВ — по той же калибровке, что и на экране. */
    val energies: List<Double>,
    val peaks: List<ReportPeak>,
    /** Строки «что это за измерение»: прибор, прошивка, происхождение. */
    val details: List<Pair<String, String>>,
    /** Оговорки, которые обязаны ехать вместе с числами. */
    val notes: List<String>,
    val footer: String,
    val strings: ReportStrings,
)

/** Найденный пик: энергия, площадь, значимость и возможное совпадение. */
data class ReportPeak(
    val energyKeV: Double,
    val netCounts: Double,
    val significance: Double,
    /** Возможное совпадение по энергии — гипотеза, а не обнаружение. */
    val candidate: String?,
    val confidence: String?,
)

/** Сборка HTML-отчёта о спектре. */
object SpectrumReportHtml {

    const val TYPE = "spectrum"

    fun render(report: SpectrumReport): String {
        val s = report.strings
        return HtmlDocument.page(
            type = TYPE,
            title = report.title,
            subtitle = report.subtitle,
            metadata = report.details.map { it.first to it.second },
            footer = report.footer,
        ) {
            hero(
                listOf(
                    Triple(report.durationText, s.accumulation, null),
                    Triple(formatCount(report.totalCounts), s.totalCounts, null),
                    Triple(report.counts.size.toString(), s.channels, null),
                ),
            )

            section(s.spectrumSection) {
                append(
                    HtmlChart.figure(
                        id = "spectrum",
                        points = report.counts.mapIndexed { channel, value ->
                            val energy = report.energies.getOrElse(channel) { channel.toDouble() }
                            HtmlChart.Point(
                                x = energy,
                                value = value.toDouble(),
                                label = s.peakReadout(energy, value.toDouble()),
                            )
                        },
                        axisLabels = energyAxis(report.energies, s),
                        valueUnit = s.countsUnit,
                        title = s.spectrumSection,
                        logarithmic = true,
                        marks = report.peaks.mapIndexed { index, peak ->
                            HtmlChart.Mark(
                                x = peak.energyKeV,
                                label = formatEnergy(peak.energyKeV),
                                key = "p$index",
                            )
                        },
                        peaksInteractive = report.peaks.isNotEmpty(),
                        labels = s.chartLabels,
                    ),
                )
            }

            if (report.peaks.isNotEmpty()) {
                section(s.peaksSection) {
                    append("<table>\n<thead><tr>")
                    append("<th scope=\"col\">").append(HtmlDocument.escape(s.energy))
                    append("</th><th scope=\"col\">").append(HtmlDocument.escape(s.area))
                    append("</th><th scope=\"col\">").append(HtmlDocument.escape(s.significance))
                    append("</th><th scope=\"col\">").append(HtmlDocument.escape(s.candidate))
                    append("</th></tr></thead>\n<tbody>\n")
                    for ((index, peak) in report.peaks.withIndex()) {
                        append("<tr class=\"peak\" data-figure=\"spectrum\" data-peak=\"p")
                        append(index).append("\" data-readout=\"")
                        append(
                            HtmlDocument.escape(
                                s.peakReadout(peak.energyKeV, peak.netCounts),
                            ),
                        )
                        append("\">")
                        append("<td>").append(HtmlDocument.escape(formatEnergy(peak.energyKeV)))
                        append("</td><td>")
                        append(HtmlDocument.escape(formatCount(peak.netCounts.toLong())))
                        append("</td><td>")
                        append(HtmlDocument.escape(formatSignificance(peak.significance)))
                        append("</td><td>")
                        append(
                            HtmlDocument.escape(
                                listOfNotNull(peak.candidate, peak.confidence)
                                    .joinToString(" · ")
                                    .ifBlank { "—" },
                            ),
                        )
                        append("</td></tr>\n")
                    }
                    append("</tbody>\n</table>\n")
                    note(s.peaksNote)
                }
            }

            if (report.details.isNotEmpty()) {
                section(s.detailsSection) { facts(report.details) }
            }
            if (report.notes.isNotEmpty()) {
                section(s.notesSection) {
                    for (line in report.notes) note(line)
                }
            }
        }
    }

    /** Подписи оси энергий: круглые значения, а не каждый канал. */
    private fun energyAxis(energies: List<Double>, s: ReportStrings): List<Pair<Double, String>> {
        if (energies.isEmpty()) return emptyList()
        val min = energies.first()
        val max = energies.last()
        val span = (max - min).takeIf { it > 0 } ?: return emptyList()
        val step = listOf(100.0, 200.0, 250.0, 500.0, 1000.0).firstOrNull { span / it <= 6 } ?: 1000.0
        val out = mutableListOf<Pair<Double, String>>()
        var value = Math.ceil(min / step) * step
        while (value <= max && out.size < 8) {
            out += value to s.keV(value)
            value += step
        }
        return out
    }

    private fun formatEnergy(value: Double): String =
        String.format(java.util.Locale.US, "%.1f", value).replace('.', ',')

    private fun formatCount(value: Long): String =
        value.toString().reversed().chunked(3).joinToString(" ").reversed()

    private fun formatSignificance(value: Double): String =
        String.format(java.util.Locale.US, "%.1f σ", value).replace('.', ',')
}
