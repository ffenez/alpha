package app.alpha.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.alpha.analysis.Spectrogram
import app.alpha.analysis.SpectrogramColumn
import app.alpha.ui.theme.DoseRampColors
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * Спектрограмма-водопад («Научный терминал»): X = время, Y = энергия,
 * яркость ячейки = интенсивность ([Spectrogram.intensity]).
 *
 * ## Две картинки на одной оси времени
 *
 * Под водопадом — отдельная полоса мощности дозы. Она не «ещё один
 * энергетический ряд», поэтому у неё своя высота, своя подпись и своя единица,
 * а не тонкая линия поверх подписей. Ось времени у обеих одна и та же, и
 * курсор времени проходит через обе: сравнивать «когда появилась линия» и
 * «когда выросла мощность дозы» можно только так.
 *
 * ## Ось энергии объявлена
 *
 * Высота строки связана с энергией по [WaterfallSpec.energyScale]; растр
 * строится по долям высоты ([Spectrogram.bandOfFraction]), поэтому обе оси
 * рисуются одним кодом, а полосы не пересчитываются и не интерполируются.
 *
 * Цвет: проверенная янтарная рампа карты. На тёмной теме её порядок
 * переворачивается, чтобы яркость всегда РОСЛА с интенсивностью; нулевая
 * интенсивность — сама поверхность карточки, пропуск потока — своя плоскость.
 */
@Immutable
data class WaterfallSpec(
    /**
     * Колонки сетки времени, старые → новые; `null` = в этой ячейке измерений
     * не было. Пропуск обязан быть виден: пустая колонка и колонка с нулевым
     * счётом — разные факты.
     */
    val columns: List<SpectrogramColumn?>,
    /**
     * Верх общей цветовой шкалы, имп/с на полосу. Ноль или режим [shapeMode]
     * переключают яркость на нормировку внутри колонки.
     */
    val scaleTop: Float = 0f,
    /** Режим «форма»: нормировка внутри колонки вместо общей шкалы. */
    val shapeMode: Boolean = false,
    /**
     * Группы энергетических полос отображения: полосы объединяются, пока в них
     * не наберётся статистика. Пусто = каждая полоса сама по себе.
     */
    val bandGroups: List<IntRange> = emptyList(),
    /** Ось энергии: геометрическая или равномерная. */
    val energyScale: Spectrogram.EnergyScale = Spectrogram.EnergyScale.LOG,
    /** Cursor column index; null = no cursor. */
    val selectedIndex: Int? = null,
    /** Fraction of plot width → time label. */
    val timeLabels: List<Pair<Float, String>> = emptyList(),
    /** Dose rate per column for the synced strip, µSv/h; null = unknown. */
    val stripValues: List<Float?> = emptyList(),
    /** Подпись полосы: величина и её единица. */
    val stripTitle: String = "",
    /** Значение под курсором (или последнее) — справа в шапке полосы. */
    val stripValue: String? = null,
    /** Верх шкалы полосы: у линии обязан быть масштаб. */
    val stripMaxLabel: String? = null,
    /** Единица оси энергии: подпись приходит с экрана вместе с его языком. */
    val energyUnit: String = "кэВ",
    /**
     * Прицел: горизонтальный маркер энергии и подпись под пальцем. Живёт
     * только во время касания — постоянный маркер означал бы выбранную
     * энергию, а её никто не выбирал.
     */
    val probe: WaterfallProbe? = null,
)

/**
 * Прицел по энергии: доля высоты поля и то, что показывается рядом.
 *
 * Текст собирает экран: здесь нет ни языка, ни единиц, ни форматирования —
 * только геометрия и готовые строки.
 */
@Immutable
data class WaterfallProbe(
    /** 0 = низ поля ([Spectrogram.MIN_KEV]), 1 = верх. */
    val energyFraction: Float,
    /** Одна-две короткие строки: «14:54 · 146 кэВ», «0,37 имп/с». */
    val lines: List<String>,
)

private const val STRIP_GAP_DP = 6

/**
 * Полоса мощности дозы под картой — отдельный мини-график, а не часть heatmap.
 * Своя высота под саму линию: подпись живёт НАД ней и добавляет свою высоту,
 * иначе линия проходила бы сквозь буквы.
 */
private const val STRIP_PLOT_HEIGHT_DP = 54

/**
 * Строк растра. Сетка растра не совпадает с сеткой полос намеренно: строки
 * распределяются по выбранной оси энергии, а полоса занимает столько строк,
 * сколько ей отводит эта ось.
 */
private const val RENDER_ROWS = 192

@Composable
fun WaterfallChart(
    spec: WaterfallSpec,
    modifier: Modifier = Modifier,
    /** null — высоту задаёт родитель: в полноэкранном режиме поле и есть экран. */
    height: Dp? = null,
    /**
     * Курсор времени: индекс колонки и доля высоты, если палец ведут по полю.
     * `null` во втором параметре — прицела нет (нажатие, а не ведение).
     */
    onCursor: ((Int, Float?) -> Unit)? = null,
    /** Палец отпущен: прицел исчезает, курсор времени остаётся. */
    onCursorEnd: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Ramp lookup: 256 ARGB steps. Dark theme reverses the map ramp so higher
    // intensity is always brighter than the ground it sits on.
    val rampLut = remember(colors.isDark) {
        val stops = if (colors.isDark) DoseRampColors.reversed() else DoseRampColors
        IntArray(256) { i ->
            val t = i / 255f
            val scaled = t * (stops.size - 1)
            val idx = scaled.toInt().coerceAtMost(stops.size - 2)
            lerp(stops[idx], stops[idx + 1], scaled - idx).toArgb()
        }
    }
    val surfaceArgb = colors.surface.toArgb()

    // The waterfall as a columns×rows bitmap, scaled up with nearest-neighbor
    // sampling at draw time — thousands of cells without per-frame rects.
    val gapArgb = colors.chartBeyondData.toArgb()
    val bitmap: ImageBitmap? = remember(
        spec.columns,
        spec.scaleTop,
        spec.shapeMode,
        spec.bandGroups,
        spec.energyScale,
        rampLut,
        surfaceArgb,
    ) {
        if (spec.columns.isEmpty()) return@remember null
        val w = spec.columns.size
        val h = RENDER_ROWS
        val pixels = IntArray(w * h)
        val groups = spec.bandGroups.ifEmpty {
            (0 until Spectrogram.BAND_COUNT).map { it..it }
        }
        // Строка растра → номер группы полос. Считается один раз: внутри
        // цикла по колонкам это был бы поиск на каждый пиксель.
        val rowGroup = IntArray(h) { row ->
            // Row 0 of the bitmap is the top = highest energy.
            val fraction = (h - 0.5f - row) / h
            val band = Spectrogram.bandOfFraction(fraction, spec.energyScale) ?: -1
            groups.indexOfFirst { band in it }
        }
        val values = FloatArray(groups.size)
        for (x in 0 until w) {
            val column = spec.columns[x]
            if (column == null) {
                // Пропуск потока: своя плоскость, а не «нулевая интенсивность».
                for (row in 0 until h) pixels[row * w + x] = gapArgb
                continue
            }
            // Значение считается по ГРУППЕ полос (адаптивная энергетическая
            // нарезка), и все строки группы красятся одинаково: это честная
            // запись «на таком энергетическом разрешении столько-то».
            var columnMax = 0f
            for (i in groups.indices) {
                val v = column.groupCounts(groups[i])
                if (v > columnMax) columnMax = v
            }
            for (i in groups.indices) {
                values[i] = if (spec.shapeMode || spec.scaleTop <= 0f) {
                    Spectrogram.shapeIntensity(column.groupCounts(groups[i]), columnMax)
                } else {
                    Spectrogram.intensity(column.groupRate(groups[i]), spec.scaleTop)
                }
            }
            for (row in 0 until h) {
                val g = rowGroup[row]
                val t = if (g < 0) 0f else values[g]
                pixels[row * w + x] = if (t <= 0f) {
                    surfaceArgb
                } else {
                    rampLut[(t * 255f).toInt().coerceIn(0, 255)]
                }
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    // Left pad fits the widest energy label; shared by drawing and tapping.
    val energyTicks = remember(spec.energyScale) {
        Spectrogram.ticksKeV(spec.energyScale).mapNotNull { keV ->
            Spectrogram.fractionOfEnergy(keV, spec.energyScale)?.let { it to "${keV.toInt()}" }
        }
    }
    val padLpx = remember(axisStyle, textMeasurer, density, energyTicks) {
        energyTicks.maxOf { textMeasurer.measure(it.second, axisStyle).size.width } +
            with(density) { 5.dp.toPx() }
    }
    val labelHeightPx = remember(axisStyle, textMeasurer) {
        textMeasurer.measure("00:00", axisStyle).size.height
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier.fillMaxSize())
            .then(
                if (onCursor == null || spec.columns.isEmpty()) {
                    Modifier
                } else {
                    // Геометрия поля повторяется здесь ровно потому, что жест
                    // приходит раньше отрисовки: обе стороны считают её из
                    // одних и тех же величин.
                    val padR = with(density) { 4.dp.toPx() }
                    val padT = with(density) { 2.dp.toPx() }
                    val stripBlock = with(density) {
                        (STRIP_PLOT_HEIGHT_DP + STRIP_GAP_DP).dp.toPx()
                    } + labelHeightPx
                    val padB = labelHeightPx + with(density) { 3.dp.toPx() }
                    fun columnAt(x: Float, width: Int): Int {
                        val plotW = (width - padLpx - padR).coerceAtLeast(1f)
                        val fraction = ((x - padLpx) / plotW).coerceIn(0f, 1f)
                        return (fraction * spec.columns.size).toInt()
                            .coerceIn(0, spec.columns.size - 1)
                    }
                    fun energyAt(y: Float, heightPx: Int): Float {
                        val plotH = (heightPx - padT - padB - stripBlock).coerceAtLeast(1f)
                        return ((padT + plotH - y) / plotH).coerceIn(0f, 1f)
                    }
                    Modifier
                        .pointerInput(spec.columns.size, padLpx) {
                            detectTapGestures { offset ->
                                onCursor(columnAt(offset.x, size.width), null)
                            }
                        }
                        .pointerInput(spec.columns.size, padLpx) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    onCursor(
                                        columnAt(offset.x, size.width),
                                        energyAt(offset.y, size.height),
                                    )
                                },
                                onDragEnd = { onCursorEnd?.invoke() },
                                onDragCancel = { onCursorEnd?.invoke() },
                            ) { change, _ ->
                                onCursor(
                                    columnAt(change.position.x, size.width),
                                    energyAt(change.position.y, size.height),
                                )
                                change.consume()
                            }
                        }
                },
            ),
    ) {
        val labelHeight = labelHeightPx
        val padL = padLpx
        val padR = 4.dp.toPx()
        val padT = 2.dp.toPx()
        val padB = labelHeight + 3.dp.toPx()
        val stripPlotH = STRIP_PLOT_HEIGHT_DP.dp.toPx()
        val stripGap = STRIP_GAP_DP.dp.toPx()
        // Полоса дозы = подпись + собственное поле: подпись занимает свою
        // высоту, а не место линии.
        val stripBlock = stripGap + labelHeight + stripPlotH
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB - stripBlock
        if (plotW <= 0 || plotH <= 0) return@Canvas
        val plotBottom = padT + plotH
        val stripHeaderTop = plotBottom + stripGap
        val stripTop = stripHeaderTop + labelHeight
        val stripBottom = stripTop + stripPlotH

        // 1. Waterfall bitmap, nearest-neighbor so cells stay crisp.
        if (bitmap != null) {
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(padL.toInt(), padT.toInt()),
                dstSize = IntSize(plotW.toInt(), plotH.toInt()),
                filterQuality = FilterQuality.None,
            )
        }

        // 2. Energy gridlines + labels (fraction 0 = MIN_KEV at the bottom).
        val grid = colors.ink2.copy(alpha = 0.18f)
        // Единица оси — внутри поля, у первой подписи: отдельная строка
        // «кэВ ↑ · время →» под графиком повторяла то, что и так видно по
        // числам слева и по времени снизу.
        drawText(
            textLayoutResult = textMeasurer.measure(spec.energyUnit, axisStyle),
            color = colors.muted,
            topLeft = Offset(0f, 0f),
        )
        for ((fraction, label) in energyTicks) {
            val yy = plotBottom - fraction * plotH
            drawLine(grid, Offset(padL, yy), Offset(size.width - padR, yy), 1f)
            val measured = textMeasurer.measure(label, axisStyle)
            drawText(
                textLayoutResult = measured,
                color = colors.muted,
                topLeft = Offset(
                    padL - 5.dp.toPx() - measured.size.width,
                    (yy - measured.size.height / 2f).coerceIn(0f, plotBottom),
                ),
            )
        }

        // 3. Полоса мощности дозы: та же ось времени, своя подпись, свой верх.
        val strip = spec.stripValues
        val stripMax = strip.filterNotNull().maxOrNull()?.coerceAtLeast(1e-6f)
        if (spec.stripTitle.isNotEmpty()) {
            val title = textMeasurer.measure(spec.stripTitle, axisStyle)
            drawText(
                textLayoutResult = title,
                color = colors.muted,
                topLeft = Offset(padL, stripHeaderTop),
            )
            // Значение под курсором — справа в той же строке: число читается
            // там же, где названа величина.
            spec.stripValue?.let { value ->
                val measured = textMeasurer.measure(value, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = colors.ink2,
                    topLeft = Offset(
                        size.width - padR - measured.size.width,
                        stripHeaderTop,
                    ),
                )
            }
        }
        if (stripMax != null) {
            drawLine(
                color = colors.line,
                start = Offset(padL, stripBottom),
                end = Offset(size.width - padR, stripBottom),
                strokeWidth = 1f,
            )
            val n = strip.size
            val topInset = 2.dp.toPx()
            fun x(i: Int): Float = padL + (i + 0.5f) * plotW / n
            fun y(v: Float): Float =
                stripBottom - (v / stripMax).coerceIn(0f, 1f) * (stripPlotH - topInset)
            val path = Path()
            var penDown = false
            strip.forEachIndexed { i, v ->
                if (v == null) {
                    penDown = false
                } else {
                    if (penDown) path.lineTo(x(i), y(v)) else path.moveTo(x(i), y(v))
                    penDown = true
                }
            }
            drawPath(path, colors.data, style = Stroke(width = 1.6.dp.toPx()))
            // Верх шкалы подписан у самой линии: без него подъём и спад
            // читались бы, а величина подъёма — нет.
            spec.stripMaxLabel?.let { label ->
                val measured = textMeasurer.measure(label, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = colors.muted,
                    topLeft = Offset(padL + 2.dp.toPx(), stripTop),
                )
            }
        }

        // 4. Time labels along the shared axis.
        for ((fraction, label) in spec.timeLabels) {
            val measured = textMeasurer.measure(label, axisStyle)
            val xx = (padL + fraction * plotW - measured.size.width / 2f)
                .coerceIn(0f, size.width - measured.size.width)
            drawText(
                textLayoutResult = measured,
                color = colors.muted,
                topLeft = Offset(xx, size.height - labelHeight),
            )
        }

        // 5. Общий курсор времени: одна вертикаль через обе картинки.
        val selected = spec.selectedIndex
        val cursorX = if (selected != null && selected in spec.columns.indices) {
            padL + (selected + 0.5f) * plotW / spec.columns.size
        } else {
            null
        }
        if (cursorX != null) {
            drawLine(
                color = colors.ink,
                start = Offset(cursorX, padT),
                end = Offset(cursorX, stripBottom),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // 6. Прицел по энергии — только пока палец на поле.
        val probe = spec.probe
        if (probe != null) {
            val yy = plotBottom - probe.energyFraction.coerceIn(0f, 1f) * plotH
            drawLine(
                color = colors.ink,
                start = Offset(padL, yy),
                end = Offset(size.width - padR, yy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                ),
            )
            val lines = probe.lines.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                val measured = lines.map { textMeasurer.measure(it, axisStyle) }
                val boxW = measured.maxOf { it.size.width }.toFloat() + 8.dp.toPx()
                val boxH = measured.sumOf { it.size.height }.toFloat() + 6.dp.toPx()
                // Подпись уходит от края и от самого пальца: под пальцем её
                // не видно, а за краем поля она была бы обрезана.
                val anchorX = cursorX ?: padL
                val left = if (anchorX + 8.dp.toPx() + boxW > size.width - padR) {
                    anchorX - 8.dp.toPx() - boxW
                } else {
                    anchorX + 8.dp.toPx()
                }.coerceIn(padL, (size.width - padR - boxW).coerceAtLeast(padL))
                val top = (yy - boxH - 6.dp.toPx()).coerceIn(padT, plotBottom - boxH)
                drawRoundRect(
                    color = colors.surface,
                    topLeft = Offset(left, top),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                drawRoundRect(
                    color = colors.line,
                    topLeft = Offset(left, top),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 1f),
                )
                var textY = top + 3.dp.toPx()
                measured.forEach { line ->
                    drawText(
                        textLayoutResult = line,
                        color = colors.ink,
                        topLeft = Offset(left + 4.dp.toPx(), textY),
                    )
                    textY += line.size.height
                }
            }
        }

        // Hairline frame around the waterfall plot.
        drawRect(
            color = colors.line,
            topLeft = Offset(padL, padT),
            size = Size(plotW, plotH),
            style = Stroke(width = 1f),
        )
    }
}

/** Legend swatch row data: 4 ramp steps from «фон» to «макс», theme-ordered. */
@Composable
fun waterfallLegendColors(): List<Color> {
    val dark = LocalAppColors.current.isDark
    return if (dark) DoseRampColors.reversed() else DoseRampColors
}
