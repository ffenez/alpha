package app.alpha.data.export.html

import app.alpha.data.export.backup.Json
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Графики отчёта — векторные, внутри страницы.
 *
 * ## Почему SVG, а не картинка
 *
 * Картинка мылится при увеличении, плохо печатается и не даёт прочитать
 * значение. Вектор печатается резко, масштабируется и позволяет добавить
 * перекрестие десятком строк скрипта. Внешних библиотек графиков при этом нет
 * ни одной: отчёт обязан открываться через год и без сети.
 *
 * ## Что рисуется, а что нет
 *
 * Отчёт не резервная копия: в него не встраиваются миллионы отсчётов. Ряд
 * прореживается до числа точек, которые видно на ширине графика, и
 * прореживание СОХРАНЯЕТ ПИКИ (минимум и максимум внутри корзины), а не берёт
 * каждое N-е значение — иначе короткий всплеск исчез бы именно там, где он
 * важен (§28 ТЗ).
 */
object HtmlChart {

    /** Точки на графике: столько же, сколько пикселей ширины у отчёта. */
    const val MAX_POINTS = 900

    /** Размеры поля в единицах SVG — не пиксели: страница масштабирует их. */
    const val WIDTH = 900
    const val HEIGHT = 260
    private const val PAD_LEFT = 56
    private const val PAD_RIGHT = 12
    private const val PAD_TOP = 12
    private const val PAD_BOTTOM = 28

    /**
     * Подписи кнопок графика.
     *
     * Приходят из каталога отчёта: раньше «Лин» и «Лог» были вписаны в код и
     * оставались русскими в английском отчёте — кнопка на чужом языке хуже
     * отсутствующей кнопки.
     */
    data class Labels(
        val linear: String = "Лин",
        val logarithmic: String = "Лог",
        val fullScreen: String = "Во весь экран",
    )

    /** Одна точка ряда: положение по оси и значение. */
    data class Point(val x: Double, val value: Double, val label: String)

    /**
     * Прореживание, сохраняющее форму: в каждой корзине остаются крайние
     * значения, поэтому узкий пик не исчезает.
     */
    fun downsample(points: List<Point>, maxPoints: Int = MAX_POINTS): List<Point> {
        if (points.size <= maxPoints) return points
        val bucket = points.size.toDouble() / (maxPoints / 2)
        val out = ArrayList<Point>(maxPoints)
        var index = 0
        while (index < points.size) {
            val end = minOf(points.size, (index + bucket).toInt().coerceAtLeast(index + 1))
            val slice = points.subList(index, end)
            val min = slice.minByOrNull { it.value }
            val max = slice.maxByOrNull { it.value }
            if (min != null && max != null) {
                if (min.x <= max.x) {
                    out += min
                    if (max !== min) out += max
                } else {
                    out += max
                    out += min
                }
            }
            index = end
        }
        return out
    }

    /**
     * Линейный график ряда во времени или по каналам.
     *
     * @param logarithmic вторая копия линии в логарифмическом масштабе:
     *   переключатель на странице показывает одну из них, а не пересчитывает
     *   данные скриптом.
     */
    fun figure(
        id: String,
        points: List<Point>,
        axisLabels: List<Pair<Double, String>>,
        valueUnit: String,
        title: String,
        logarithmic: Boolean = false,
        marks: List<Mark> = emptyList(),
        peaksInteractive: Boolean = false,
        labels: Labels = Labels(),
    ): String {
        if (points.isEmpty()) return ""
        val out = StringBuilder(8 * 1024)
        val reduced = downsample(points)
        val xs = reduced.map { it.x }
        val minX = xs.min()
        val maxX = xs.max()
        val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
        fun px(x: Double) = PAD_LEFT + (WIDTH - PAD_LEFT - PAD_RIGHT) * (x - minX) / spanX

        val values = reduced.map { it.value }
        val maxValue = max(values.max(), 1e-9)
        val minValue = values.min().coerceAtLeast(0.0)

        fun pyLinear(value: Double): Double {
            val span = (maxValue - minValue).takeIf { it > 1e-12 } ?: 1.0
            return HEIGHT - PAD_BOTTOM -
                (HEIGHT - PAD_TOP - PAD_BOTTOM) * (value - minValue) / span
        }

        val logMin = 0.5
        fun pyLog(value: Double): Double {
            val top = ln(max(maxValue, 1.0) + 1.0)
            val bottom = ln(logMin)
            val v = ln(max(value, logMin))
            val span = (top - bottom).takeIf { it > 1e-9 } ?: 1.0
            return HEIGHT - PAD_BOTTOM - (HEIGHT - PAD_TOP - PAD_BOTTOM) * (v - bottom) / span
        }

        out.append("<figure id=\"").append(HtmlDocument.escape(id)).append('"')
        out.append(" data-points=\"")
            .append(HtmlDocument.escape(jsonNumbers(reduced.map { px(it.x) })))
            .append('"')
        out.append(" data-labels=\"")
            .append(HtmlDocument.escape(jsonStrings(reduced.map { it.label })))
            .append('"')
        if (peaksInteractive) out.append(" data-peaks=\"1\"")
        out.append(">\n")

        out.append("<div class=\"controls\">")
        if (logarithmic) {
            out.append(
                "<button type=\"button\" data-set-mode=\"lin\" aria-pressed=\"true\" " +
                    "onclick=\"rcSetMode('${escapeJs(id)}','lin')\">" +
                    HtmlDocument.escape(labels.linear) + "</button>",
            )
            out.append(
                "<button type=\"button\" data-set-mode=\"log\" aria-pressed=\"false\" " +
                    "onclick=\"rcSetMode('${escapeJs(id)}','log')\">" +
                    HtmlDocument.escape(labels.logarithmic) + "</button>",
            )
        }
        out.append(expandButton(id, labels))
        out.append("</div>\n")

        out.append("<svg viewBox=\"0 0 $WIDTH $HEIGHT\" role=\"img\" aria-label=\"")
            .append(HtmlDocument.escape(title)).append("\">\n")

        // Сетка и подписи значений — по линейной шкале; в логарифмическом
        // виде своя сетка, поэтому обе нарисованы и переключаются вместе с
        // линией.
        appendGrid(out, "lin", ::pyLinear, minValue, maxValue, valueUnit)
        if (logarithmic) appendGrid(out, "log", ::pyLog, logMin, maxValue, valueUnit, log = true)

        for ((x, label) in axisLabels) {
            val position = px(x)
            out.append("<line x1=\"").append(fmt(position)).append("\" y1=\"$PAD_TOP\" x2=\"")
                .append(fmt(position)).append("\" y2=\"").append(HEIGHT - PAD_BOTTOM)
                .append("\" stroke=\"var(--line)\" stroke-width=\"1\"/>\n")
            out.append("<text x=\"").append(fmt(position)).append("\" y=\"")
                .append(HEIGHT - 8).append("\" fill=\"var(--muted)\" font-size=\"11\" ")
                .append("text-anchor=\"middle\">").append(HtmlDocument.escape(label))
                .append("</text>\n")
        }

        appendPath(out, "lin", reduced, ::px, ::pyLinear, visible = true)
        if (logarithmic) appendPath(out, "log", reduced, ::px, ::pyLog, visible = false)

        for (mark in marks) {
            val position = px(mark.x)
            out.append("<g data-peak=\"").append(HtmlDocument.escape(mark.key)).append("\">")
            out.append("<line x1=\"").append(fmt(position)).append("\" y1=\"$PAD_TOP\" x2=\"")
                .append(fmt(position)).append("\" y2=\"").append(HEIGHT - PAD_BOTTOM)
                .append("\" stroke=\"var(--warn)\" stroke-width=\"1\" ")
                .append("stroke-dasharray=\"4 4\"/>")
            out.append("<text x=\"").append(fmt(position + 4)).append("\" y=\"")
                .append(PAD_TOP + 12).append("\" fill=\"var(--warn)\" font-size=\"11\">")
                .append(HtmlDocument.escape(mark.label)).append("</text>")
            out.append("</g>\n")
        }

        out.append("<line class=\"cursor-line\" x1=\"0\" y1=\"$PAD_TOP\" x2=\"0\" y2=\"")
            .append(HEIGHT - PAD_BOTTOM)
            .append("\" stroke=\"var(--data)\" stroke-width=\"1\" style=\"display:none\"/>\n")
        out.append("</svg>\n")
        out.append("<p class=\"readout\" aria-live=\"polite\"></p>\n")
        out.append("</figure>\n")
        return out.toString()
    }

    /** Один ряд наложения: своя кривая и своё имя в легенде. */
    data class Series(val label: String, val points: List<Point>)

    /**
     * Несколько рядов на одном поле.
     *
     * Кривые различаются ЦВЕТОМ И ИМЕНЕМ в легенде: цвет один различие не
     * несёт — на печати без цвета и при цветовой слепоте остаётся подпись, а
     * порядок кривых в легенде совпадает с порядком в таблице записей.
     *
     * Шкала — общая: ряды, нарисованные каждый в своём масштабе, выглядят
     * одинаковыми при разнице в десять раз.
     */
    fun overlay(
        id: String,
        series: List<Series>,
        axisLabels: List<Pair<Double, String>>,
        valueUnit: String,
        title: String,
        labels: Labels = Labels(),
    ): String {
        val drawn = series.map { it.copy(points = downsample(it.points)) }
            .filter { it.points.isNotEmpty() }
        if (drawn.isEmpty()) return ""
        val minX = drawn.minOf { s -> s.points.minOf { it.x } }
        val maxX = drawn.maxOf { s -> s.points.maxOf { it.x } }
        val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
        fun px(x: Double) = PAD_LEFT + (WIDTH - PAD_LEFT - PAD_RIGHT) * (x - minX) / spanX

        val maxValue = max(drawn.maxOf { s -> s.points.maxOf { it.value } }, 1e-9)
        val minValue = drawn.minOf { s -> s.points.minOf { it.value } }.coerceAtLeast(0.0)
        fun py(value: Double): Double {
            val span = (maxValue - minValue).takeIf { it > 1e-12 } ?: 1.0
            return HEIGHT - PAD_BOTTOM -
                (HEIGHT - PAD_TOP - PAD_BOTTOM) * (value - minValue) / span
        }

        val out = StringBuilder(16 * 1024)
        out.append("<figure id=\"").append(HtmlDocument.escape(id)).append("\">\n")
        out.append("<div class=\"controls\">").append(expandButton(id, labels)).append("</div>\n")
        out.append("<p class=\"legend\">")
        for ((index, s) in drawn.withIndex()) {
            out.append("<span style=\"color:").append(seriesColor(index)).append("\">\u25A0 ")
                .append(HtmlDocument.escape(s.label)).append("</span> ")
        }
        out.append("</p>\n")
        out.append("<svg viewBox=\"0 0 $WIDTH $HEIGHT\" role=\"img\" aria-label=\"")
            .append(HtmlDocument.escape(title)).append("\">\n")

        appendGrid(out, "lin", ::py, minValue, maxValue, valueUnit)
        for ((x, label) in axisLabels) {
            val position = px(x)
            out.append("<line x1=\"").append(fmt(position)).append("\" y1=\"$PAD_TOP\" x2=\"")
                .append(fmt(position)).append("\" y2=\"").append(HEIGHT - PAD_BOTTOM)
                .append("\" stroke=\"var(--line)\" stroke-width=\"1\"/>\n")
            out.append("<text x=\"").append(fmt(position)).append("\" y=\"")
                .append(HEIGHT - 8).append("\" fill=\"var(--muted)\" font-size=\"11\" ")
                .append("text-anchor=\"middle\">").append(HtmlDocument.escape(label))
                .append("</text>\n")
        }
        for ((index, s) in drawn.withIndex()) {
            out.append("<path fill=\"none\" stroke=\"").append(seriesColor(index))
                .append("\" stroke-width=\"1.6\" d=\"")
            for ((pointIndex, point) in s.points.withIndex()) {
                out.append(if (pointIndex == 0) 'M' else 'L')
                out.append(fmt(px(point.x))).append(' ').append(fmt(py(point.value))).append(' ')
            }
            out.append("\"/>\n")
        }
        out.append("</svg>\n</figure>\n")
        return out.toString()
    }

    /** Кнопка «во весь экран» — одна на все графики отчёта, включая карту. */
    fun expandButton(id: String, labels: Labels = Labels()): String =
        "<button type=\"button\" class=\"expand\" onclick=\"rcExpand('" + escapeJs(id) + "')\">" +
            HtmlDocument.escape(labels.fullScreen) + "</button>"

    /** Цвета кривых: те же переменные темы, что и на остальной странице. */
    private fun seriesColor(index: Int): String {
        val palette = listOf("var(--data)", "var(--warn)", "var(--crit)", "var(--ink2)")
        return palette[index % palette.size]
    }

    /** Названная вертикальная отметка — пик спектра или момент события. */
    data class Mark(val x: Double, val label: String, val key: String)

    private fun appendPath(
        out: StringBuilder,
        mode: String,
        points: List<Point>,
        px: (Double) -> Double,
        py: (Double) -> Double,
        visible: Boolean,
    ) {
        out.append("<path data-mode=\"").append(mode).append('"')
        if (!visible) out.append(" style=\"display:none\"")
        out.append(" fill=\"none\" stroke=\"var(--data)\" stroke-width=\"1.6\" d=\"")
        for ((index, point) in points.withIndex()) {
            out.append(if (index == 0) 'M' else 'L')
            out.append(fmt(px(point.x))).append(' ').append(fmt(py(point.value))).append(' ')
        }
        out.append("\"/>\n")
    }

    private fun appendGrid(
        out: StringBuilder,
        mode: String,
        py: (Double) -> Double,
        minValue: Double,
        maxValue: Double,
        unit: String,
        log: Boolean = false,
    ) {
        val ticks = if (log) logTicks(minValue, maxValue) else linearTicks(minValue, maxValue)
        out.append("<g data-mode=\"").append(mode).append('"')
        if (mode == "log") out.append(" style=\"display:none\"")
        out.append(">\n")
        for (tick in ticks) {
            val y = py(tick)
            out.append("<line x1=\"$PAD_LEFT\" y1=\"").append(fmt(y)).append("\" x2=\"")
                .append(WIDTH - PAD_RIGHT).append("\" y2=\"").append(fmt(y))
                .append("\" stroke=\"var(--line)\" stroke-width=\"1\"/>\n")
            out.append("<text x=\"").append(PAD_LEFT - 6).append("\" y=\"").append(fmt(y + 4))
                .append("\" fill=\"var(--muted)\" font-size=\"11\" text-anchor=\"end\">")
                .append(HtmlDocument.escape(tickLabel(tick))).append("</text>\n")
        }
        out.append("<text x=\"").append(PAD_LEFT - 6).append("\" y=\"").append(PAD_TOP)
            .append("\" fill=\"var(--muted)\" font-size=\"10\" text-anchor=\"end\">")
            .append(HtmlDocument.escape(unit)).append("</text>\n")
        out.append("</g>\n")
    }

    private fun linearTicks(minValue: Double, maxValue: Double): List<Double> {
        val span = (maxValue - minValue).takeIf { it > 0 } ?: return listOf(minValue)
        val step = niceStep(span / 4)
        val first = Math.ceil(minValue / step) * step
        val out = mutableListOf<Double>()
        var value = first
        while (value <= maxValue + 1e-9 && out.size < 8) {
            out += value
            value += step
        }
        return out
    }

    private fun logTicks(minValue: Double, maxValue: Double): List<Double> {
        val out = mutableListOf<Double>()
        var decade = 1.0
        while (decade <= maxValue * 10 && out.size < 8) {
            if (decade >= minValue) out += decade
            decade *= 10
        }
        return out
    }

    private fun niceStep(raw: Double): Double {
        if (raw <= 0) return 1.0
        val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(raw)))
        val normalized = raw / magnitude
        val step = when {
            normalized <= 1 -> 1.0
            normalized <= 2 -> 2.0
            normalized <= 5 -> 5.0
            else -> 10.0
        }
        return step * magnitude
    }

    private fun tickLabel(value: Double): String = when {
        abs(value) >= 1000 -> (value / 1000).let {
            if (it == it.roundToInt().toDouble()) "${it.roundToInt()}k" else String.format(
                java.util.Locale.US, "%.1fk", it,
            )
        }
        value == value.roundToInt().toDouble() -> value.roundToInt().toString()
        value >= 1 -> String.format(java.util.Locale.US, "%.1f", value)
        else -> String.format(java.util.Locale.US, "%.2f", value)
    }

    private fun fmt(value: Double): String =
        String.format(java.util.Locale.US, "%.1f", value)

    private fun jsonNumbers(values: List<Double>): String =
        values.joinToString(",", "[", "]") { Json.number(it) }

    private fun jsonStrings(values: List<String>): String =
        values.joinToString(",", "[", "]") { Json.quote(it) }

    private fun escapeJs(value: String): String = value.replace("'", "")
}
