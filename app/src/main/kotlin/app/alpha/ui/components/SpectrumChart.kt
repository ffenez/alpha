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
 * Временная отметка энергии на поле — «вот где эта линия по калибровке».
 *
 * Ставится из справки о нуклиде и рисуется НЕ как пик и НЕ как курсор:
 * пунктирная линия во всё поле приглушённым цветом с подписью у оси энергии.
 * Сплошная линия цвета данных занята курсором (сырой счёт в канале), короткий
 * штрих над кривой — маркером найденного пика.
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
     * Управление, живущее НА поле (компактная кнопка в углу).
     *
     * Слот, а не постоянная полоса под графиком: переключатель вида относится
     * к самой картинке, и на маленьком экране строка кнопок под полем отнимала
     * у него высоту ради двух нажатий. Слот рисуется последним — поверх поля и
     * поверх обработчиков жестов, поэтому нажатие на кнопку не читается как
     * жест по графику.
     */
    fieldControls: @Composable BoxScope.() -> Unit = {},
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Жесты живут дольше одной композиции — обработчики читаются ПО ССЫЛКЕ.
    // Ключ `Unit` у `pointerInput` означает, что блок запускается ровно раз за
    // жизнь узла: без [rememberUpdatedState] он захватил бы ПЕРВУЮ версию
    // лямбды — ту, что видела окно во всю шкалу, — и каждый жест считался бы
    // от исходного окна (щипок как будто работал, а сдвиг молча не делал
    // ничего, потому что двигать полное окно некуда).
    val gesture = rememberUpdatedState(onGesture)
    val setCursor = rememberUpdatedState(onCursorFraction)
    val dismissCursor = rememberUpdatedState(onCursorDismiss)
    val cursorDown = rememberUpdatedState(cursorActive)
    val tap = rememberUpdatedState(onTap)
    // Одиночное касание снимает ВСЁ, что положено поверх поля: и курсор, и
    // отметку линии. Иначе «тап в стороне» работал бы только для курсора, а
    // пунктир пришлось бы пережидать.
    val markPresent = rememberUpdatedState(spec.lineMark != null)
    val resetZoom = rememberUpdatedState(onResetZoom)

    // Поля осей считаются ОДИН раз и служат и рисованию, и жестам: подписи
    // стоят ВНУТРИ поля и забирают у него левый край, поэтому курсор, взятый
    // от ширины узла, указывал бы мимо канала, на который смотрит палец.
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
    // Пиксель касания → доля ширины ПОЛЯ: подписи оси стоят внутри поля, и без
    // этой поправки курсор указывал бы левее того канала, куда смотрит палец.
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
                        // ОДИН обработчик: щипок двумя пальцами и сдвиг одним.
                        //
                        // Ключ `Unit` означает, что блок запускается ровно раз за
                        // жизнь узла, поэтому лямбда обязана читаться через
                        // [rememberUpdatedState]. Без этого захватывалась ПЕРВАЯ
                        // версия обработчика — та, что видела окно во всю шкалу, —
                        // и каждый жест пересчитывался от исходного окна: щипок
                        // как будто срабатывал, а сдвиг не делал ничего вовсе,
                        // потому что двигать полное окно некуда.
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
                        // Тап по графику открывает его во весь экран — тем же
                        // движением, каким карточка величины на Главной открывает
                        // свой полноэкранный график.
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(onTap = { tap.value?.invoke() })
                        }
                    },
                ),
        ) {
            if (spec.columns.isEmpty() || spec.yTop <= 0f) return@Canvas

            // Подписи оси задаёт сам масштаб: декады у логарифма, четверти у
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

            // 1a. Тонкая сетка внутри декады (только логарифм): без неё
            // положение между 10 и 100 нечитаемо — 30 и 80 выглядят одинаково.
            if (spec.scale is SpectrumScale.Log) {
                val minor = colors.ink2.copy(alpha = 0.06f)
                for (value in SpectrumScale.Log.minorTicks(spec.yTop)) {
                    val yy = y(value)
                    drawLine(minor, Offset(padL, yy), Offset(size.width - padR, yy), 1f)
                }
            }

            // 1. Horizontal gridlines + y labels.
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

            // 2. Energy ticks: vertical gridlines + keV labels below.
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

            // 3. Background overlay: dimmed muted line.
            spec.overlay?.let { overlay ->
                val path = Path()
                overlay.forEachIndexed { index, value ->
                    if (index >= n) return@forEachIndexed
                    val point = Offset(x(index), y(value))
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(
                    path = path,
                    color = colors.muted.copy(alpha = 0.7f),
                    style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round),
                )
            }

            // 4. Data line + area fill.
            val line = Path()
            spec.columns.forEachIndexed { index, value ->
                val point = Offset(x(index), y(value))
                if (index == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(n - 1), bottom)
                lineTo(x(0), bottom)
                close()
            }
            drawPath(path = area, color = colors.data.copy(alpha = 0.16f))
            drawPath(
                path = line,
                color = colors.data,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // 5. Peak markers: tick + label above the local line top; amber only
            // for the highlighted candidate (color + the table carry the meaning).
            for (peak in spec.peaks) {
                if (peak.columnIndex !in spec.columns.indices) continue
                var top = Float.MAX_VALUE
                for (j in (peak.columnIndex - 3)..(peak.columnIndex + 3)) {
                    val v = spec.columns.getOrNull(j) ?: continue
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
            // верхней полосе поля. Пунктир и приглушённый цвет — чтобы её
            // нельзя было прочитать ни как найденный пик (короткий штрих над
            // кривой), ни как курсор (сплошная линия цвета данных).
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
                // Верхняя полоса поля отведена под подписи: внизу подпись
                // спорила бы с подписями оси кэВ и с панелью, которая
                // объясняет саму отметку.
                val top = pad
                // Подложка цветом фона: подпись стоит поверх кривой и сетки, и
                // без неё читается через них.
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

        // 6. Курсор — свой слой поверх поля: он читается прямо в рисовании
        // через [State], поэтому перетаскивание не пересобирает экран.
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

        // 7. Жесты, когда у поля есть курсор: долгое нажатие опускает его и в
        // том же жесте продолжает вести, одиночное касание — снимает, щипок и
        // сдвиг двигают окно. Ключи — `Unit`: смена состояния посреди жеста не
        // должна перезапускать обработчик, палец не отпускали.
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
                            // Двойной тап отменяет зум — как на полноэкранном
                            // графике, где он возвращает выбранное окно.
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

        // 8. Управление на поле — последним слоем: кнопка обязана получать
        // нажатие раньше, чем его увидят обработчики жестов графика.
        fieldControls()
    }
}


/** 1234 → «1,2k», 250 → «250» — compact linear-axis count label. */
private fun compactCount(value: Float): String = when {
    value >= 10_000f -> "${(value / 1000f).toInt()}k"
    value >= 1_000f ->
        String.format(Locale.US, "%.1f", value / 1000f).replace('.', ',') + "k"
    else -> "${value.toInt()}"
}
