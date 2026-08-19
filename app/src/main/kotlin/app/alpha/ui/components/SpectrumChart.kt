package app.alpha.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.alpha.analysis.SpectrumDisplay
import androidx.compose.ui.platform.LocalDensity
import app.alpha.ui.logic.SpectrumPlot
import app.alpha.ui.logic.SpectrumScale
import app.alpha.ui.text.uiDecimal
import java.util.Locale
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.chartField
import app.alpha.ui.theme.LocalAppTypography

/**
 * Spectrum chart («Научный терминал», design-language.md): counts/keV as a
 * line with an area fill, log decades labeled 1/10/10²/10³/10⁴ (linear:
 * labeled quarter lines), keV ticks on the bottom axis, the recorded
 * background as a dimmed overlay line, and labeled tick markers above
 * detected peaks — amber only for the highlighted isotope candidate.
 */
@Immutable
data class SpectrumPeakMark(
    /** Column index of the peak center in [SpectrumChartSpec.columns]. */
    val columnIndex: Int,
    /** Marker label, e.g. «662». */
    val label: String,
    /** Amber highlighted candidate (matches the selected table row). */
    val highlighted: Boolean = false,
)

/**
 * Временная отметка энергии на поле: пунктирная линия во всё поле
 * приглушённым цветом с подписью у оси энергии. Сплошная линия цвета данных
 * занята курсором, короткий штрих над кривой — маркером найденного пика.
 */
@Immutable
data class SpectrumLineMark(
    /** Доля ширины ПОЛЯ ([app.alpha.ui.logic.SpectrumHighlight.fraction]). */
    val fraction: Float,
    /** «линия 661,7 кэВ». */
    val label: String,
)

@Immutable
data class SpectrumChartSpec(
    /** Aggregated counts per column (linear values; the chart maps them). */
    val columns: List<Float>,
    /** Background overlay series in the same columns; null = no overlay. */
    val overlay: List<Float>? = null,
    /**
     * Континуум SNIP в тех же колонках; null — не показан.
     *
     * Это ОЦЕНКА ФОРМЫ подложки, а не измерение: у неё нет дисперсии, и
     * значимость линии по ней не считается ([app.alpha.analysis.SnipContinuum]).
     * Поэтому она рисуется тонким пунктиром — линией другого рода, чем данные.
     */
    val continuum: List<Float>? = null,
    /** Как высота столбца получается из числа импульсов. */
    val scale: SpectrumScale = SpectrumScale.Log,
    /** Scale top: linear max or a power of ten for log (see [SpectrumDisplay.logTop]). */
    val yTop: Float,
    val peaks: List<SpectrumPeakMark> = emptyList(),
    val energyTicks: List<SpectrumDisplay.EnergyTick> = emptyList(),
    /** Отметка линии из справки о нуклиде; null — её нет. */
    val lineMark: SpectrumLineMark? = null,
)

/** Bottom of the log scale (mirrors the mockup: fractions of a count clamp here). */
private const val LOG_FLOOR = 0.6f

@Composable
fun SpectrumChart(
    spec: SpectrumChartSpec,
    modifier: Modifier = Modifier,
    /** Высота поля; null — высоту задаёт [modifier] (полноэкранный режим). */
    height: Dp? = 170.dp,
    /** Pinch/drag: scale factor (>1 = zoom in), pan and focus as width fractions. */
    onGesture: ((scale: Float, panFraction: Float, focusFraction: Float) -> Unit)? = null,
    /**
     * Курсор канала: доля ширины ПОЛЯ (не узла). Отдельное [State] — чтобы
     * перетаскивание перерисовывало слой курсора, а не пересобирало экран.
     */
    cursorFraction: State<Float?>? = null,
    /** Курсор опущен: одиночное касание его снимает, а не двигает окно. */
    cursorActive: Boolean = false,
    onCursorFraction: ((Float) -> Unit)? = null,
    onCursorDismiss: (() -> Unit)? = null,
    /**
     * Тап по самому полю — вход в полноэкранный режим (у поля с курсором тап
     * занят: он снимает курсор).
     */
    onTap: (() -> Unit)? = null,
    /** Двойной тап у поля с курсором: вернуть окно ко всей шкале. */
    onResetZoom: (() -> Unit)? = null,
    /**
     * Управление, живущее на поле (компактная кнопка в углу). Рисуется
     * последним — поверх поля и поверх обработчиков жестов, поэтому нажатие на
     * кнопку не читается как жест по графику.
     */
    fieldControls: @Composable BoxScope.() -> Unit = {},
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Ключ `Unit` у `pointerInput` означает, что блок запускается один раз за
    // жизнь узла, поэтому лямбда читается через [rememberUpdatedState]: иначе
    // захватывается первая версия обработчика и жест считается от исходного
    // окна.
    val gesture = rememberUpdatedState(onGesture)
    val setCursor = rememberUpdatedState(onCursorFraction)
    val dismissCursor = rememberUpdatedState(onCursorDismiss)
    val cursorDown = rememberUpdatedState(cursorActive)
    val tap = rememberUpdatedState(onTap)
    // Одиночное касание снимает всё, что положено поверх поля: и курсор, и
    // отметку линии.
    val markPresent = rememberUpdatedState(spec.lineMark != null)
    val resetZoom = rememberUpdatedState(onResetZoom)

    // Поля осей считаются один раз и служат рисованию и жестам: подписи стоят
    // внутри поля и забирают левый край, поэтому доля от ширины узла указывала
    // бы мимо канала под пальцем.
    val yLabels = remember(spec.scale, spec.yTop) {
        spec.scale.ticks(spec.yTop).map { value -> value to compactCount(value) }
    }
    val labelHeightPx = remember(axisStyle) {
        textMeasurer.measure("0", axisStyle).size.height.toFloat()
    }
    val padLeftPx = remember(yLabels, axisStyle, density) {
        (yLabels.maxOfOrNull { textMeasurer.measure(it.second, axisStyle).size.width } ?: 0) +
            with(density) { 4.dp.toPx() }
    }
    val padRightPx = with(density) { 4.dp.toPx() }
    // Сверху остаётся место под подписи пиков, снизу — под подписи кэВ.
    val padTopPx = with(density) { 18.dp.toPx() }
    val padBottomPx = labelHeightPx + with(density) { 3.dp.toPx() }
    // Пиксель касания → доля ширины ПОЛЯ (подписи оси стоят внутри поля).
    val fractionOf: (Float, Int) -> Float = { xPx, nodeWidthPx ->
        SpectrumPlot.plotFraction(xPx, padLeftPx, nodeWidthPx - padLeftPx - padRightPx)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier)
            .chartField(),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onGesture == null || cursorFraction != null) {
                        // Курсор есть — жесты живут отдельным узлом ниже: их
                        // смысл зависит от того, опущен ли курсор.
                        Modifier
                    } else {
                        // Один обработчик: щипок двумя пальцами и сдвиг одним.
                        // Ключ `Unit` — блок запускается раз за жизнь узла,
                        // поэтому лямбда читается через [rememberUpdatedState].
                        Modifier.pointerInput(Unit) {
                            detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                gesture.value?.invoke(
                                    zoom,
                                    pan.x / w,
                                    fractionOf(centroid.x, size.width),
                                )
                            }
                        }
                    },
                )
                .then(
                    if (onTap == null || cursorFraction != null) {
                        Modifier
                    } else {
                        // Тап по графику открывает полноэкранный режим.
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(onTap = { tap.value?.invoke() })
                        }
                    },
                ),
        ) {
            if (spec.columns.isEmpty() || spec.yTop <= 0f) return@Canvas

            // Подписи оси задаёт масштаб: декады у логарифма, четверти у
            // линейного, неравномерные значения у степенного. Поля посчитаны в
            // композиции — теми же числами живут жесты и курсор.
            val labelHeight = labelHeightPx
            val padL = padLeftPx
            val padR = padRightPx
            val padT = padTopPx
            val padB = padBottomPx
            val plotW = size.width - padL - padR
            val plotH = size.height - padT - padB
            if (plotW <= 0 || plotH <= 0) return@Canvas
            val bottom = padT + plotH

            fun y(value: Float): Float =
                SpectrumPlot.yPx(value, spec.yTop, spec.scale, padT, plotH)

            val n = spec.columns.size
            fun x(index: Int): Float = SpectrumPlot.columnXPx(index, n, padL, plotW)

            val grid = colors.ink2.copy(alpha = 0.14f)

            // 1a. Тонкая сетка внутри декады (только логарифм): без неё 30 и
            // 80 между 10 и 100 неразличимы.
            if (spec.scale is SpectrumScale.Log) {
                val minor = colors.ink2.copy(alpha = 0.06f)
                for (value in SpectrumScale.Log.minorTicks(spec.yTop)) {
                    val yy = y(value)
                    drawLine(minor, Offset(padL, yy), Offset(size.width - padR, yy), 1f)
                }
            }

            for ((value, label) in yLabels) {
                val yy = y(value)
                drawLine(grid, Offset(padL, yy), Offset(size.width - padR, yy), 1f)
                val measured = textMeasurer.measure(label, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = colors.muted,
                    topLeft = Offset(
                        padL - 4.dp.toPx() - measured.size.width,
                        yy - measured.size.height / 2f,
                    ),
                )
            }

            for (tick in spec.energyTicks) {
                val xx = padL + tick.fraction * plotW
                drawLine(grid, Offset(xx, padT), Offset(xx, bottom), 1f)
                val measured = textMeasurer.measure("${tick.keV}", axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = colors.muted,
                    topLeft = Offset(
                        (xx - measured.size.width / 2f)
                            .coerceIn(0f, size.width - measured.size.width),
                        size.height - labelHeight,
                    ),
                )
            }

            // Линия рисуется отрезками, а не одним путём через все колонки.
            // Колонка бывает двух видов: «нет данных» (NaN — в колонку не попал
            // ни один канал) и «измерен ноль». На логарифмической оси ноля на
            // шкале нет, и оба случая рвут линию: между соседними каналами с
            // данными она непрерывна, на месте пустых — разрыв.
            val logScale = spec.scale is SpectrumScale.Log
            fun segmentsOf(values: List<Float>): List<List<Int>> =
                SpectrumPlot.segments(values.take(n), logScale)

            spec.overlay?.let { overlay ->
                val path = Path()
                for (segment in segmentsOf(overlay)) {
                    segment.forEachIndexed { position, index ->
                        val point = Offset(x(index), y(overlay[index]))
                        if (position == 0) path.moveTo(point.x, point.y) else {
                            path.lineTo(point.x, point.y)
                        }
                    }
                }
                drawPath(
                    path = path,
                    color = colors.muted.copy(alpha = 0.7f),
                    style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round),
                )
            }

            spec.continuum?.let { continuum ->
                val path = Path()
                for (segment in segmentsOf(continuum)) {
                    segment.forEachIndexed { position, index ->
                        val point = Offset(x(index), y(continuum[index]))
                        if (position == 0) path.moveTo(point.x, point.y) else {
                            path.lineTo(point.x, point.y)
                        }
                    }
                }
                drawPath(
                    path = path,
                    color = colors.dataText.copy(alpha = 0.6f),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                        ),
                    ),
                )
            }

            val line = Path()
            val area = Path()
            for (segment in segmentsOf(spec.columns)) {
                segment.forEachIndexed { position, index ->
                    val point = Offset(x(index), y(spec.columns[index]))
                    if (position == 0) line.moveTo(point.x, point.y) else {
                        line.lineTo(point.x, point.y)
                    }
                }
                // Заливка посегментная: общий путь соединил бы концы разрывов
                // по низу поля.
                area.moveTo(x(segment.first()), bottom)
                for (index in segment) area.lineTo(x(index), y(spec.columns[index]))
                area.lineTo(x(segment.last()), bottom)
                area.close()
            }
            drawPath(path = area, color = colors.data.copy(alpha = 0.16f))
            drawPath(
                path = line,
                color = colors.data,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // 5. Метки пиков: штрих и подпись над вершиной; янтарный — только
            // у выделенного кандидата.
            for (peak in spec.peaks) {
                if (peak.columnIndex !in spec.columns.indices) continue
                var top = Float.MAX_VALUE
                for (j in (peak.columnIndex - 3)..(peak.columnIndex + 3)) {
                    val v = spec.columns.getOrNull(j) ?: continue
                    // Колонка без данных в поиске вершины не участвует: NaN
                    // отравил бы сравнение.
                    if (v.isNaN()) continue
                    top = minOf(top, y(v))
                }
                if (top == Float.MAX_VALUE) continue
                val xx = x(peak.columnIndex)
                val color = if (peak.highlighted) colors.warn else colors.ink2
                drawLine(
                    color = color,
                    start = Offset(xx, (top - 3.dp.toPx()).coerceAtLeast(labelHeight + 2.dp.toPx())),
                    end = Offset(xx, (top - 9.dp.toPx()).coerceAtLeast(labelHeight + 2.dp.toPx())),
                    strokeWidth = 2.dp.toPx(),
                )
                val measured = textMeasurer.measure(peak.label, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = color,
                    topLeft = Offset(
                        (xx - measured.size.width / 2f)
                            .coerceIn(0f, size.width - measured.size.width),
                        (top - 11.dp.toPx() - measured.size.height).coerceAtLeast(0f),
                    ),
                )
            }

            // 5b. Отметка линии из справки: пунктир во всё поле и подпись в
            // верхней полосе. Пунктир и приглушённый цвет отличают её от пика
            // (короткий штрих) и от курсора (сплошная линия цвета данных).
            spec.lineMark?.let { mark ->
                val xx = padL + mark.fraction.coerceIn(0f, 1f) * plotW
                val dashPx = 4.dp.toPx()
                drawLine(
                    color = colors.ink2.copy(alpha = 0.75f),
                    start = Offset(xx, padT),
                    end = Offset(xx, bottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx)),
                )
                val measured = textMeasurer.measure(mark.label, axisStyle)
                val pad = 2.dp.toPx()
                val left = (xx + 3.dp.toPx())
                    .coerceAtMost(size.width - padR - measured.size.width - pad)
                    .coerceAtLeast(padL + pad)
                // Подпись стоит в верхней полосе поля: внизу она спорит с
                // подписями оси кэВ.
                val top = pad
                // Подложка цветом фона: подпись стоит поверх кривой и сетки.
                drawRect(
                    color = colors.bg.copy(alpha = 0.85f),
                    topLeft = Offset(left - pad, top - pad / 2f),
                    size = Size(measured.size.width + 2f * pad, measured.size.height + pad),
                )
                drawText(
                    textLayoutResult = measured,
                    color = colors.ink2,
                    topLeft = Offset(left, top),
                )
            }
        }

        // 6. Курсор — свой слой: читается в рисовании через [State], поэтому
        // перетаскивание не пересобирает экран.
        if (cursorFraction != null) {
            Canvas(Modifier.fillMaxSize()) {
                val fraction = cursorFraction.value ?: return@Canvas
                val plotW = size.width - padLeftPx - padRightPx
                if (plotW <= 0f) return@Canvas
                val xx = padLeftPx + fraction.coerceIn(0f, 1f) * plotW
                drawLine(
                    color = colors.dataText,
                    start = Offset(xx, padTopPx),
                    end = Offset(xx, size.height - padBottomPx),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        // 7. Жесты при опущенном курсоре: долгое нажатие опускает его и в том
        // же жесте ведёт, одиночное касание снимает, щипок и сдвиг двигают
        // окно. Ключи — `Unit`: смена состояния посреди жеста не перезапускает
        // обработчик.
        if (cursorFraction != null) {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { setCursor.value?.invoke(fractionOf(it.x, size.width)) },
                            onDrag = { change, _ ->
                                change.consume()
                                setCursor.value?.invoke(fractionOf(change.position.x, size.width))
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (cursorDown.value || markPresent.value) {
                                    dismissCursor.value?.invoke()
                                }
                            },
                            // Двойной тап отменяет зум, как на полноэкранном
                            // графике.
                            onDoubleTap = { resetZoom.value?.invoke() },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                            if (cursorDown.value) {
                                setCursor.value?.invoke(fractionOf(centroid.x, size.width))
                            } else {
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                gesture.value?.invoke(
                                    zoom,
                                    pan.x / w,
                                    fractionOf(centroid.x, size.width),
                                )
                            }
                        }
                    },
            )
        }

        // 8. Управление на поле — последним слоем: кнопка получает нажатие
        // раньше обработчиков жестов.
        fieldControls()
    }
}


/** 1234 → «1,2k», 250 → «250» — compact linear-axis count label. */
private fun compactCount(value: Float): String = when {
    value >= 10_000f -> "${(value / 1000f).toInt()}k"
    value >= 1_000f ->
        String.format(Locale.US, "%.1f", value / 1000f).uiDecimal() + "k"
    else -> "${value.toInt()}"
}
