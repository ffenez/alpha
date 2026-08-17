package app.radiacode.data.export.html

import app.radiacode.data.export.html.HtmlDocument.facts
import app.radiacode.data.export.html.HtmlDocument.hero
import app.radiacode.data.export.html.HtmlDocument.note
import app.radiacode.data.export.html.HtmlDocument.section
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/** Точка маршрута в отчёте. */
data class ReportRoutePoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val value: Double,
)

/** Что делать с координатами при выгрузке (§29 ТЗ). */
enum class RoutePrivacy {
    /** Полные координаты. */
    FULL,

    /** Скрыть начало и конец: дом обычно там. */
    TRIM_ENDS,

    /** Совсем без координат — остаётся только график во времени. */
    NO_COORDINATES,
}

/**
 * Отчёт о маршруте.
 *
 * ## Карта внутри файла
 *
 * Карта нарисована вектором по самим координатам: ограничивающий
 * прямоугольник, линия следа, окрашенная по величине, начало и конец,
 * масштабная линейка и указатель севера. Никаких тайлов: отчёт обязан
 * открываться без сети, а тайловый сервер — это и внешняя зависимость, и
 * рассказ ему о том, где человек ходил.
 *
 * ## Карта и график связаны
 *
 * Наведение на график двигает метку по следу, и наоборот: маршрут и время —
 * два взгляда на одну прогулку, и разъезжаться им незачем (§20 ТЗ).
 */
data class RouteReport(
    val title: String,
    val subtitle: String?,
    val heroCells: List<Triple<String, String, String?>>,
    val points: List<ReportRoutePoint>,
    val valueUnit: String,
    val privacy: RoutePrivacy,
    val details: List<Pair<String, String>>,
    val notes: List<String>,
    val footer: String,
    val strings: ReportStrings,
    val timeLabel: (Long) -> String,
)

object RouteReportHtml {

    const val TYPE = "route"

    /** Поле карты в единицах SVG. */
    private const val MAP_WIDTH = 900
    private const val MAP_HEIGHT = 420
    private const val MAP_PAD = 24

    fun render(report: RouteReport): String {
        val s = report.strings
        val visible = when (report.privacy) {
            RoutePrivacy.FULL -> report.points
            RoutePrivacy.TRIM_ENDS -> trimEnds(report.points)
            RoutePrivacy.NO_COORDINATES -> emptyList()
        }
        return HtmlDocument.page(
            type = TYPE,
            title = report.title,
            subtitle = report.subtitle,
            metadata = report.details,
            footer = report.footer,
        ) {
            hero(report.heroCells)

            if (visible.isNotEmpty()) {
                section(s.trackSection) {
                    append(map(visible, report))
                    // Режим координат виден человеку ДО того, как он отправит
                    // файл: молча менять данные нельзя (§29 ТЗ).
                    when (report.privacy) {
                        RoutePrivacy.TRIM_ENDS -> note(s.privacyTrimmed)
                        else -> Unit
                    }
                }
            } else if (report.privacy == RoutePrivacy.NO_COORDINATES) {
                section(s.trackSection) { note(s.privacyDropped) }
            }

            if (report.points.isNotEmpty()) {
                section(s.doseSection) {
                    append(
                        HtmlChart.figure(
                            id = "route-series",
                            points = report.points.map { point ->
                                HtmlChart.Point(
                                    x = point.timestamp.toDouble(),
                                    value = point.value,
                                    label = "${report.timeLabel(point.timestamp)} · " +
                                        "${SessionReportHtml.formatValue(point.value)} " +
                                        report.valueUnit,
                                )
                            },
                            axisLabels = SessionReportHtml.timeAxis(
                                report.points.map { it.timestamp },
                                report.timeLabel,
                            ),
                            valueUnit = report.valueUnit,
                            title = s.doseSection,
                            labels = s.chartLabels,
                        ),
                    )
                }
            }

            if (report.details.isNotEmpty()) section(s.detailsSection) { facts(report.details) }
            if (report.notes.isNotEmpty()) {
                section(s.notesSection) { for (line in report.notes) note(line) }
            }
        }
    }

    /** Скрыть начало и конец следа — то же правило, что и у файлов маршрута. */
    fun trimEnds(points: List<ReportRoutePoint>): List<ReportRoutePoint> = RouteTrim.ends(points)

    /** Вектор следа: проекция, линия, цвет по величине, масштаб и север. */
    private fun map(points: List<ReportRoutePoint>, report: RouteReport): String {
        val projected = points.map { point ->
            val x = point.longitude
            val y = ln(tan(PI / 4 + Math.toRadians(point.latitude) / 2))
            Triple(x, y, point)
        }
        val minX = projected.minOf { it.first }
        val maxX = projected.maxOf { it.first }
        val minY = projected.minOf { it.second }
        val maxY = projected.maxOf { it.second }
        val spanX = (maxX - minX).takeIf { it > 1e-9 } ?: 1e-9
        val spanY = (maxY - minY).takeIf { it > 1e-9 } ?: 1e-9
        // Одинаковый масштаб по осям: растянутый след — это другая форма
        // прогулки, а не та же самая.
        val scale = minOf(
            (MAP_WIDTH - 2 * MAP_PAD) / spanX,
            (MAP_HEIGHT - 2 * MAP_PAD) / spanY,
        )
        val offsetX = (MAP_WIDTH - spanX * scale) / 2
        val offsetY = (MAP_HEIGHT - spanY * scale) / 2
        fun px(x: Double) = offsetX + (x - minX) * scale
        fun py(y: Double) = MAP_HEIGHT - (offsetY + (y - minY) * scale)

        val values = points.map { it.value }
        val low = values.min()
        val high = values.max().takeIf { it > low } ?: (low + 1)

        val out = StringBuilder(16 * 1024)
        out.append("<figure id=\"route-map\" data-track=\"")
        val json = StringBuilder()
        val writer = app.radiacode.data.export.backup.Json.Writer(json)
        writer.beginArray()
        for ((x, y, point) in projected) {
            writer.beginObject()
                .field("t", point.timestamp)
                .field("x", px(x))
                .field("y", py(y))
                .endObject()
        }
        writer.endArray()
        out.append(HtmlDocument.escape(json.toString())).append("\">\n")
        // Карта разворачивается так же, как график: след во всю ширину ладони
        // читается не лучше графика.
        out.append("<div class=\"controls\">")
            .append(HtmlChart.expandButton("route-map", report.strings.chartLabels))
            .append("</div>\n")
        out.append("<svg viewBox=\"0 0 $MAP_WIDTH $MAP_HEIGHT\" role=\"img\" aria-label=\"")
            .append(HtmlDocument.escape(report.strings.routeSection)).append("\">\n")

        // След рисуется отрезками: цвет каждого отрезка — по величине, и
        // цвет не единственный носитель — рядом стоит шкала с числами.
        for (index in 1 until projected.size) {
            val (x1, y1, previous) = projected[index - 1]
            val (x2, y2, point) = projected[index]
            val ratio = ((point.value - low) / (high - low)).coerceIn(0.0, 1.0)
            out.append("<line x1=\"").append(fmt(px(x1))).append("\" y1=\"").append(fmt(py(y1)))
                .append("\" x2=\"").append(fmt(px(x2))).append("\" y2=\"").append(fmt(py(y2)))
                .append("\" stroke=\"").append(color(ratio))
                .append("\" stroke-width=\"3\" stroke-linecap=\"round\"/>\n")
            if (previous.timestamp == 0L) continue
        }

        val start = projected.first()
        val end = projected.last()
        out.append("<circle cx=\"").append(fmt(px(start.first))).append("\" cy=\"")
            .append(fmt(py(start.second)))
            .append("\" r=\"6\" fill=\"none\" stroke=\"var(--ink)\" stroke-width=\"2\"/>\n")
        out.append("<circle cx=\"").append(fmt(px(end.first))).append("\" cy=\"")
            .append(fmt(py(end.second))).append("\" r=\"5\" fill=\"var(--ink)\"/>\n")
        out.append("<circle class=\"track-cursor\" cx=\"0\" cy=\"0\" r=\"7\" fill=\"none\" ")
            .append("stroke=\"var(--data)\" stroke-width=\"3\" style=\"display:none\"/>\n")

        // Масштабная линейка: длина в метрах считается по широте середины.
        val midLat = points.map { it.latitude }.average()
        val metersPerUnit = 111_320.0 * cos(Math.toRadians(midLat))
        val barMeters = niceDistance(spanX * metersPerUnit / 4)
        val barPx = barMeters / metersPerUnit * scale
        val barY = MAP_HEIGHT - 16.0
        out.append("<line x1=\"").append(MAP_PAD).append("\" y1=\"").append(fmt(barY))
            .append("\" x2=\"").append(fmt(MAP_PAD + barPx)).append("\" y2=\"").append(fmt(barY))
            .append("\" stroke=\"var(--ink2)\" stroke-width=\"2\"/>\n")
        out.append("<text x=\"").append(MAP_PAD).append("\" y=\"").append(fmt(barY - 6))
            .append("\" fill=\"var(--ink2)\" font-size=\"11\">")
            .append(HtmlDocument.escape(distanceLabel(barMeters))).append("</text>\n")
        out.append("<text x=\"").append(MAP_WIDTH - MAP_PAD).append("\" y=\"").append(MAP_PAD)
            .append("\" fill=\"var(--ink2)\" font-size=\"12\" text-anchor=\"end\">С ↑</text>\n")
        out.append("</svg>\n</figure>\n")
        out.append(TRACK_SCRIPT)
        return out.toString()
    }

    /**
     * Цвет отрезка следа: от цвета данных к предупреждающему.
     *
     * Числа рядом обязательны — цвет носит различие не один: в отчёте есть и
     * шкала величины, и подпись под курсором.
     */
    private fun color(ratio: Double): String {
        val stops = listOf("var(--data)", "var(--warn)", "var(--crit)")
        val index = (ratio * (stops.size - 1)).toInt().coerceIn(0, stops.lastIndex)
        return stops[index]
    }

    private fun niceDistance(meters: Double): Double {
        val steps = listOf(10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1_000.0, 2_000.0, 5_000.0)
        return steps.firstOrNull { it >= meters } ?: 10_000.0
    }

    private fun distanceLabel(meters: Double): String =
        if (meters >= 1000) "${(meters / 1000).toInt()} км" else "${meters.toInt()} м"

    private fun fmt(value: Double) = String.format(java.util.Locale.US, "%.1f", value)

    /**
     * Связь графика и карты: наведение на график двигает метку по следу.
     * Скрипт крошечный намеренно — отчёт показывает результат, а не переносит
     * движок графиков в браузер.
     */
    private val TRACK_SCRIPT = """
        <script>
        (function () {
          var map = document.getElementById('route-map');
          var chart = document.getElementById('route-series');
          if (!map || !chart) return;
          var track = JSON.parse(map.getAttribute('data-track') || '[]');
          var cursor = map.querySelector('.track-cursor');
          var svg = chart.querySelector('svg');
          if (!track.length || !cursor || !svg) return;
          var times = JSON.parse(chart.getAttribute('data-labels') || '[]');
          var xs = JSON.parse(chart.getAttribute('data-points') || '[]');
          svg.addEventListener('mousemove', function (e) {
            var rect = svg.getBoundingClientRect();
            var box = svg.viewBox.baseVal;
            var x = (e.clientX - rect.left) / rect.width * box.width;
            var best = 0;
            for (var i = 1; i < xs.length; i++) {
              if (Math.abs(xs[i] - x) < Math.abs(xs[best] - x)) best = i;
            }
            var share = xs.length > 1 ? best / (xs.length - 1) : 0;
            var point = track[Math.round(share * (track.length - 1))];
            if (!point) return;
            cursor.setAttribute('cx', point.x);
            cursor.setAttribute('cy', point.y);
            cursor.style.display = '';
          });
          svg.addEventListener('mouseleave', function () { cursor.style.display = 'none'; });
        })();
        </script>
    """.trimIndent()
}

/**
 * Обрезка концов маршрута — одно правило на все выгрузки.
 *
 * Убирается ДОЛЯ пути с каждого конца, а не фиксированное число точек: на
 * медленной прогулке точек у дома больше, и «десять точек» скрыли бы десять
 * метров. Слишком короткий маршрут не обрезается, а исчезает целиком: у следа
 * из пяти точек нет середины, которую можно показать, не показав концы.
 */
object RouteTrim {

    /** Доля пути, снимаемая с каждого конца. */
    const val FRACTION = 0.12

    /** Короче этого маршрут состоит из одних концов. */
    const val MIN_POINTS = 8

    fun <T> ends(points: List<T>, fraction: Double = FRACTION): List<T> {
        if (points.size < MIN_POINTS) return emptyList()
        val cut = (points.size * fraction).toInt().coerceAtLeast(1)
        return points.subList(cut, points.size - cut)
    }
}
