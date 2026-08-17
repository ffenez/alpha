package app.alpha.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.alpha.ui.logic.ChartBucket
import app.alpha.ui.logic.DataGap
import app.alpha.ui.logic.TimeBand
import app.alpha.ui.chart.ChartAxisLock
import app.alpha.ui.chart.ChartGestureInput
import app.alpha.ui.chart.ChartLabelLayout
import app.alpha.ui.chart.GestureAxis
import app.alpha.ui.chart.LabelPriority
import app.alpha.ui.chart.PreparedFrame
import app.alpha.ui.chart.ChartProjection
import app.alpha.ui.logic.ValueAggregate
import app.alpha.ui.logic.DoseEpisode
import app.alpha.ui.logic.DoseReference
import app.alpha.ui.logic.DoseScale
import app.alpha.ui.logic.ExtremeMarker
import app.alpha.ui.logic.MarkerClusters
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * Everything the dose chart draws, as one immutable value. Nothing here is
 * mutable state: an identical spec lets Compose skip the chart entirely —
 * which is what keeps the 1 Hz live value from repainting the plot.
 */
@Immutable
data class DoseChartSpec(
    /** Drawn columns, ordered; an absent column is a gap, never interpolated. */
    val buckets: List<ChartBucket>,
    /**
     * Подробный ряд вместо медианы с конвертами: линия ведётся по крайним
     * значениям колонок, поэтому пик и провал внутри колонки остаются видны.
     * Квантильные заливки при этом не рисуются — они описывают разброс внутри
     * колонки, а не сами измерения.
     */
    val detailed: Boolean = false,
    /**
     * Диапазон, для которого построена геометрия. Обычно ШИРЕ видимого окна:
     * запас с обеих сторон позволяет жесту двигать готовую картинку, а не
     * открывать пустое поле по краям (`ChartGesture`).
     */
    val fromMillis: Long,
    val toMillis: Long,
    /**
     * Окно, которое видно на экране, внутри [fromMillis]..[toMillis];
     * null — видно весь кадр.
     *
     * Во время жеста меняется только эта пара чисел: колонки, конверты и
     * подписи уже посчитаны и лишь перепроецируются.
     */
    val viewFromMillis: Long? = null,
    val viewToMillis: Long? = null,
    val scale: DoseScale,
    /** «Привычный фон места»: P10–P90 of the active baseline, µSv/h. */
    val baselineBand: ClosedFloatingPointRange<Float>? = null,
    val baselineMedian: Float? = null,
    val alarmLevel: Float? = null,
    val alarmLabel: String? = null,
    /**
     * Второй уровень тревоги из настроек, мкЗв/ч. Рисуется как L1, но остаётся
     * ЗАДАННЫМ уровнем: тревога считается по L1 (`AlarmThresholds`).
     */
    val alarmLevel2: Float? = null,
    val alarmLabel2: String? = null,
    val episodes: List<DoseEpisode> = emptyList(),
    /**
     * Episode index → label naming the reference and the duration («выше
     * порога L1 · 3 мин»). CHART SPEC §20: a band that does not say what it
     * is above is not a statement.
     */
    val episodeLabels: List<String> = emptyList(),
    /** Same, shortened for bands too narrow for the full wording. */
    val episodeShortLabels: List<String> = emptyList(),
    /**
     * Bins whose extremum is notable (`DoseExtremes`), drawn as discrete
     * markers above the plot — never as a filled min–max envelope (§7, §21).
     */
    val extremeMarkers: List<ExtremeMarker> = emptyList(),
    /** Value → label of the y gridlines. */
    val yLabels: List<Pair<Float, String>> = emptyList(),
    /** Fraction (0..1) → label of the time axis. */
    val xLabels: List<Pair<Float, String>> = emptyList(),
    val unitLabel: String = "",
    /** Промежутки без измерений — штриховка, а не пустое поле. */
    val gaps: List<DataGap> = emptyList(),
    /** Часть окна левее начала истории: «сюда данные не доходят». */
    val beforeHistory: DataGap? = null,
    /** Зебра времени на длинных окнах. */
    val timeBands: List<TimeBand> = emptyList(),
    /**
     * Individual measurements, drawn as dots only when the columns are short
     * enough that one aggregate ≈ one sample (see
     * [app.alpha.ui.logic.ChartSeriesModel.rawDotsVisible]).
     */
    val rawSamples: List<ValueAggregate> = emptyList(),
    val endpointAlert: Boolean = false,
    /**
     * Подпись последнего значения у правого края («0,16»); единица не
     * повторяется — она стоит в углу поля.
     */
    val endpointLabel: String? = null,
) {
    /** Левый край видимого окна. */
    val viewFrom: Long get() = viewFromMillis ?: fromMillis

    /** Правый край видимого окна. */
    val viewTo: Long get() = viewToMillis ?: toMillis

    /** Доля внутри нарисованного диапазона → доля внутри видимого окна. */
    fun viewFraction(frameFraction: Float): Float {
        val viewSpan = (viewTo - viewFrom).toFloat()
        if (viewSpan <= 0f) return frameFraction
        val at = fromMillis + (toMillis - fromMillis) * frameFraction.toDouble()
        return ((at - viewFrom) / viewSpan).toFloat()
    }
}

/**
 * Fullscreen dose-rate chart («Научный терминал», design-language.md).
 *
 * **Anatomy, outside in** (CHART SPEC §6, §7, §40). A light teal fill is the
 * Q10–Q90 envelope of each column, a denser teal fill inside it Q25–Q75: both
 * are the **observed spread of the measurements** in that column — a robust
 * description of what the instrument saw, not a measurement uncertainty and
 * not a confidence interval. The solid teal line is the per-column median
 * (Q50). Extrema are **not** filled: a bin's min/max are kept as numbers and
 * the notable ones get a discrete marker above the plot, so a spike shorter
 * than a bin stays discoverable at 7 d without pretending to be an interval.
 * A grey band with a dashed centre is the historical P10–P90 of the profile.
 * A dashed red line is the named alarm level. Vertical bands are episodes,
 * red above the alarm level and amber above the profile's P90 — different
 * classes, drawn and labelled differently.
 *
 * **Why it does not lag.**
 *  - Three separate draw nodes: static (grid, axes, baseline, alarm,
 *    episodes), series, crosshair. Moving the crosshair invalidates only the
 *    third; a new live column only the second and third.
 *  - Both painted layers build their paths and text layouts inside
 *    `drawWithCache`, so a repaint replays prebuilt objects.
 *  - Column pixels are computed once per snapshot/window/size change into
 *    primitive arrays ([PreparedFrame]); the draw scope allocates nothing.
 *  - The crosshair is read through a [State] inside the draw lambda, so
 *    dragging it never re-runs composition or layout.
 */
@Composable
fun DoseChart(
    spec: DoseChartSpec,
    cursorFraction: State<Float?>,
    modifier: Modifier = Modifier,
    cursorActive: Boolean = false,
    onCursorFraction: (Float) -> Unit = {},
    onCursorDismiss: () -> Unit = {},
    /** Double tap: back to the chosen window at the live edge (spec §10). */
    onResetScale: (() -> Unit)? = null,
    onTransform: ((ChartGestureInput) -> Unit)? = null,
    /**
     * Одиночное нажатие по полю. Задан — курсор по тапу не ставится (у
     * миниатюры одно действие).
     */
    onTap: (() -> Unit)? = null,
    /**
     * Жесты. У миниатюры на Главной выключены: обработчики перехватывают
     * касание у карточки.
     */
    interactive: Boolean = true,
    /**
     * Вертикальные жесты — движение и масштаб оси значений. На карточке
     * Главной выключены: вертикаль принадлежит прокрутке страницы. Ручной кадр
     * оси карточка при этом показывает — он общий с полноэкранным графиком.
     */
    verticalGestures: Boolean = true,
) {
    val appColors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    // Кэш измерений текста на весь набор подписей кадра (около двадцати):
    // по умолчанию кэш держит восемь, и половина подписей мерилась заново на
    // каждом кадре жеста.
    val textMeasurer = rememberTextMeasurer(cacheSize = TEXT_CACHE_SIZE)
    val density = LocalDensity.current
    val palette = remember(appColors) {
        ChartPalette(
            data = appColors.data,
            dataText = appColors.dataText,
            ink2 = appColors.ink2,
            muted = appColors.muted,
            warn = appColors.warn,
            crit = appColors.crit,
            bg = appColors.bg,
            field = appColors.chartField,
            grid = appColors.chartGrid,
            zebra = appColors.chartZebra,
            beyondData = appColors.chartBeyondData,
        )
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val labelHeightPx = remember(axisStyle, textMeasurer) {
            textMeasurer.measure("00:00", axisStyle).size.height.toFloat()
        }
        val padTop = with(density) { 12.dp.toPx() }
        val padBottom = labelHeightPx + with(density) { 5.dp.toPx() }
        val plotHeight = (heightPx - padTop - padBottom).coerceAtLeast(1f)

        // Column → pixel arrays: recomputed on snapshot/window/size change,
        // never inside the draw scope.
        val pixels = remember(
            spec.buckets,
            spec.viewFrom,
            spec.viewTo,
            spec.scale,
            widthPx,
            heightPx,
        ) {
            ChartProjection.project(
                buckets = spec.buckets,
                fromMillis = spec.viewFrom,
                toMillis = spec.viewTo,
                scale = spec.scale,
                leftPx = 0f,
                widthPx = widthPx,
                topPx = padTop,
                heightPx = plotHeight,
            )
        }

        StaticChartLayer(spec, widthPx, heightPx, padTop, plotHeight, textMeasurer, axisStyle, palette)
        SeriesLayer(
            spec, pixels, widthPx, padTop, plotHeight, palette, textMeasurer, axisStyle,
            cursorFraction,
        )
        CursorLayer(pixels, cursorFraction, widthPx, padTop, plotHeight, palette)

        // Gestures. The handlers are keyed only on the plot width, so a state
        // change in the middle of a gesture (the long press arming the
        // crosshair) never restarts them — the finger keeps its grip. The
        // meaning of a drag depends on the crosshair, read through
        // `rememberUpdatedState` inside the running handler:
        //  - crosshair down → the drag scrubs it, a tap dismisses it;
        //  - crosshair up   → the drag pans, a pinch zooms, a long press puts
        //    the crosshair down and keeps scrubbing in the same gesture.
        if (!interactive) return@BoxWithConstraints
        val active = rememberUpdatedState(cursorActive)
        val setCursor = rememberUpdatedState(onCursorFraction)
        val dismissCursor = rememberUpdatedState(onCursorDismiss)
        val resetScale = rememberUpdatedState(onResetScale)
        val tapAction = rememberUpdatedState(onTap)
        val transform = rememberUpdatedState(onTransform)
        // Порог попадания в маркер экстремума — палец, а не размер
        // треугольника.
        val markerHitPx = with(density) { 24.dp.toPx() }
        val markerBandPx = padTop + markerHitPx
        val markers = remember(spec.extremeMarkers, pixels, widthPx) {
            spec.extremeMarkers.mapNotNull { marker ->
                pixels.indexOfBucket(marker.bucketIndex)?.let { pixels.x[it] }
            }
        }
        // Инерция: наблюдающий слой, а не ещё один обработчик жестов. События
        // читаются на первом проходе и не поглощаются, поэтому перемещение,
        // щипок и курсор работают как прежде; отсюда берутся только скорость в
        // момент отрыва и сам факт отрыва.
        val flingScope = rememberCoroutineScope()
        var flingJob by remember { mutableStateOf<Job?>(null) }
        // За какую ось держится палец: решается один раз за жест.
        val touchSlopPx = with(density) { 12.dp.toPx() }
        val gutterPx = with(density) { VALUE_SCALE_GUTTER.toPx() }
        val axisLock = remember(widthPx, verticalGestures) {
            ChartAxisLock(slopPx = touchSlopPx, gutterPx = gutterPx)
        }
        DisposableEffect(Unit) { onDispose { flingJob?.cancel() } }
        Spacer(
            Modifier
                .fillMaxSize()
                .pointerInput(widthPx, interactive) {
                    if (!interactive) return@pointerInput
                    val tracker = VelocityTracker()
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isNotEmpty()) {
                                // Касание во время броска забирает управление.
                                flingJob?.cancel()
                                flingJob = null
                                // Скорость снимается по одному пальцу: у щипка
                                // своя геометрия.
                                if (pressed.size == 1) {
                                    tracker.addPosition(
                                        pressed[0].uptimeMillis,
                                        pressed[0].position,
                                    )
                                } else {
                                    tracker.resetTracking()
                                }
                            } else {
                                val velocityX = tracker.calculateVelocity().x
                                tracker.resetTracking()
                                // Палец оторвался — следующий жест выбирает ось
                                // заново.
                                val axis = axisLock.axis
                                axisLock.reset()
                                if (axis == GestureAxis.VALUE ||
                                    axis == GestureAxis.VALUE_SCALE
                                ) {
                                    // Инерция принадлежит оси времени.
                                    continue
                                }
                                val transform = onTransform
                                if (transform != null && abs(velocityX) >= MIN_FLING_VELOCITY) {
                                    flingJob = flingScope.launch {
                                        var previous = 0f
                                        androidx.compose.animation.core.Animatable(0f)
                                            .animateDecay(
                                                initialVelocity = velocityX,
                                                animationSpec = exponentialDecay(
                                                    frictionMultiplier = FLING_FRICTION,
                                                ),
                                            ) {
                                                val delta = value - previous
                                                previous = value
                                                // Окно двигает та же функция,
                                                // что и палец: границы истории
                                                // и «сейчас» соблюдаются.
                                                transform(
                                                    ChartGestureInput(
                                                        axis = GestureAxis.TIME,
                                                        panXFraction = delta /
                                                            widthPx.coerceAtLeast(1f),
                                                        panYFraction = 0f,
                                                        zoom = 1f,
                                                        focusXFraction = 0.5f,
                                                    ),
                                                )
                                            }
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(widthPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { setCursor.value(fractionOf(it.x, widthPx)) },
                        onDrag = { change, _ ->
                            change.consume()
                            setCursor.value(fractionOf(change.position.x, widthPx))
                        },
                    )
                }
                .pointerInput(widthPx) {
                    detectTapGestures(
                        onTap = { offset ->
                            val open = tapAction.value
                            if (open != null) {
                                open()
                                return@detectTapGestures
                            }
                            val marker = markers
                                .filter { offset.y <= markerBandPx }
                                .minByOrNull { kotlin.math.abs(it - offset.x) }
                                ?.takeIf { kotlin.math.abs(it - offset.x) <= markerHitPx }
                            when {
                                marker != null -> setCursor.value(fractionOf(marker, widthPx))
                                active.value -> dismissCursor.value()
                            }
                        },
                        onDoubleTap = { resetScale.value?.invoke() },
                    )
                }
                .pointerInput(widthPx) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (active.value) {
                            setCursor.value(fractionOf(centroid.x, widthPx))
                        } else {
                            val axis = axisLock.update(
                                positionXPx = centroid.x,
                                widthPx = widthPx,
                                panXPx = pan.x,
                                panYPx = pan.y,
                                zoom = zoom,
                                vertical = verticalGestures,
                            )
                            if (axis != GestureAxis.UNDECIDED) {
                                transform.value?.invoke(
                                    ChartGestureInput(
                                        axis = axis,
                                        panXFraction = pan.x / widthPx.coerceAtLeast(1f),
                                        panYFraction = pan.y / heightPx.coerceAtLeast(1f),
                                        zoom = zoom,
                                        focusXFraction = fractionOf(centroid.x, widthPx),
                                    ),
                                )
                            }
                        }
                    }
                },
        )
    }
}

private fun fractionOf(xPx: Float, widthPx: Float): Float =
    (xPx / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)


/** Resolved chart palette — one value for the draw lambdas to capture. */
@Immutable
internal data class ChartPalette(
    val data: Color,
    val dataText: Color,
    val ink2: Color,
    val muted: Color,
    val warn: Color,
    val crit: Color,
    val bg: Color,
    val field: Color,
    val grid: Color,
    val zebra: Color,
    val beyondData: Color,
)

/**
 * Grid, axis labels, baseline band, alarm line and deviation episodes — the
 * layer that changes only with the window, the scale or the settings.
 */
@Composable
private fun StaticChartLayer(
    spec: DoseChartSpec,
    widthPx: Float,
    heightPx: Float,
    plotTop: Float,
    plotHeight: Float,
    textMeasurer: TextMeasurer,
    axisStyle: TextStyle,
    colors: ChartPalette,
) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val gridColor = colors.grid
                val labelPad = 2.dp.toPx()
                // Обычный диапазон места — контекст: вес меньше, чем у сетки
                // и у самих измерений.
                val bandColor = colors.ink2.copy(alpha = 0.07f)
                val bandLineColor = colors.ink2.copy(alpha = 0.26f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                val alarmDash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                // L2 отличается длиной штриха и плотностью линии; цвет общий —
                // это одна и та же величина.
                val alarm2Dash = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 4.dp.toPx()))
                val alarmStroke = 1.dp.toPx()
                val baselineStroke = 1.5.dp.toPx()
                val labelInset = 4.dp.toPx()
                val spanMillis = (spec.viewTo - spec.viewFrom).coerceAtLeast(1L)
                fun xOfTime(millis: Long): Float =
                    (widthPx * (millis - spec.viewFrom).toFloat() / spanMillis)
                        .coerceIn(0f, widthPx)

                fun yOf(value: Float): Float? = spec.scale.fractionOrNull(value)
                    ?.let { ChartProjection.yOf(it, plotTop, plotHeight) }

                // Text is laid out once here, not on every frame.
                val allYTexts = spec.yLabels.mapNotNull { (value, label) ->
                    yOf(value)?.let { it to textMeasurer.measure(label, axisStyle) }
                }
                // Подписи времени посчитаны в долях нарисованного диапазона и
                // ложатся на экран по долям видимого окна.
                val xTexts = spec.xLabels
                    .map { (fraction, label) ->
                        spec.viewFraction(fraction) to textMeasurer.measure(label, axisStyle)
                    }
                    .filter { (fraction, _) -> fraction >= -0.05f && fraction <= 1.05f }
                val unitText = spec.unitLabel.takeIf { it.isNotEmpty() }
                    ?.let { textMeasurer.measure(it, axisStyle) }
                // §3: далёкий порог не растягивает ось (это делает
                // `DoseScales`); когда он выше кадра, вместо линии рисуется
                // указатель «↑ L1 0,30» у верхней кромки. Указатель
                // показывается, только пока расстояние до порога меньше высоты
                // кадра. Порогов два: L1 ведёт тревогу, L2 — второй уровень из
                // настроек.
                val frameSpan = (spec.scale.maxValue - spec.scale.minValue).coerceAtLeast(0f)
                fun alarmLine(level: Float?, label: String?): AlarmLine? {
                    if (level == null) return null
                    val above = level > spec.scale.maxValue &&
                        level <= spec.scale.maxValue + frameSpan
                    // Симметрично: кадр может уйти выше порога — тогда
                    // указатель «↓ L1 0,30» стоит у нижней кромки.
                    val below = level < spec.scale.minValue
                    // Линия рисуется, только когда порог внутри кадра: `yOf`
                    // зажимает долю в 0..1, и порог за кадром лёг бы на кромку.
                    val y = level.takeIf { it in spec.scale.minValue..spec.scale.maxValue }
                        ?.let { yOf(it) }
                    val text = label
                        ?.let {
                            when {
                                above -> "↑ $it"
                                below -> "↓ $it"
                                else -> it
                            }
                        }
                        ?.let { textMeasurer.measure(it, axisStyle) }
                    if (y == null && !above && !below) return null
                    return AlarmLine(y = y, text = text, above = above, below = below)
                }
                val alarmLines = listOfNotNull(
                    alarmLine(spec.alarmLevel, spec.alarmLabel),
                    alarmLine(spec.alarmLevel2, spec.alarmLabel2),
                )
                val bandTop = spec.baselineBand?.let { yOf(it.endInclusive) }
                val bandBottom = spec.baselineBand?.let { yOf(it.start) }
                val baselineMedianY = spec.baselineMedian?.let { yOf(it) }
                // Подписи порогов и подписи оси делят одну колонку пикселей:
                // при наложении уступает подпись оси — её значение
                // восстанавливается по соседним делениям (V2 §25).
                val alarmLabelTops = alarmLines.map { line ->
                    val y = line.y
                    val text = line.text
                    if (y != null && text != null) {
                        (y - 2f - text.size.height).coerceAtLeast(0f)
                    } else {
                        null
                    }
                }
                val labelBoxes = buildList {
                    for ((index, line) in alarmLines.withIndex()) {
                        val top = alarmLabelTops[index] ?: continue
                        val text = line.text ?: continue
                        add(
                            ChartLabelLayout.Label(
                                topPx = top,
                                heightPx = text.size.height.toFloat(),
                                priority = LabelPriority.ALARM_THRESHOLD,
                            ),
                        )
                    }
                    for ((y, text) in allYTexts) {
                        add(
                            ChartLabelLayout.Label(
                                topPx = y - text.size.height - 1f,
                                heightPx = text.size.height.toFloat(),
                                priority = LabelPriority.AXIS_TICK,
                            ),
                        )
                    }
                }
                val visibleLabels = ChartLabelLayout.visible(labelBoxes)
                val alarmLabelCount = alarmLabelTops.count { it != null }
                val yTexts = allYTexts.filterIndexed { index, _ ->
                    (index + alarmLabelCount) in visibleLabels
                }
                val span = spanMillis
                val episodeRects = spec.episodes.mapIndexed { index, episode ->
                    val a = widthPx * (episode.fromMillis - spec.viewFrom).toFloat() / span
                    val b = widthPx * (episode.toMillis - spec.viewFrom).toFloat() / span
                    // An episode above the alarm level and an episode above
                    // the profile's historical P90 are different classes of
                    // event (§20) — different colour AND different edge, so
                    // colour is never the only carrier of the difference.
                    val alarmClass = episode.reference == DoseReference.ALARM_L1
                    val hue = if (alarmClass) colors.crit else colors.warn
                    EpisodeRect(
                        left = a.coerceIn(0f, widthPx),
                        right = b.coerceIn(0f, widthPx),
                        fill = hue.copy(alpha = 0.13f),
                        edge = hue.copy(alpha = 0.5f),
                        text = hue,
                        dashedEdge = !alarmClass,
                        label = spec.episodeLabels.getOrNull(index)
                            ?.let { textMeasurer.measure(it, axisStyle) },
                        shortLabel = spec.episodeShortLabels.getOrNull(index)
                            ?.let { textMeasurer.measure(it, axisStyle) },
                    )
                }

                onDrawBehind {
                    // 0. Поле графика — своя плоскость: в светлой теме данные
                    // иначе лежат на белом без видимой границы.
                    drawRect(
                        color = colors.field,
                        topLeft = Offset(0f, plotTop),
                        size = Size(widthPx, plotHeight),
                    )
                    // Зебра времени привязана к стенным часам, поэтому не
                    // дёргается при прокрутке.
                    for (band in spec.timeBands) {
                        if (!band.shaded) continue
                        val left = xOfTime(band.fromMillis)
                        val right = xOfTime(band.toMillis)
                        if (right <= left) continue
                        drawRect(
                            color = colors.zebra,
                            topLeft = Offset(left, plotTop),
                            size = Size(right - left, plotHeight),
                        )
                    }
                    // Пропуск данных рисуется отсутствием: линия обрывается.
                    // У низкого уровня линия есть и идёт понизу, у пропуска её
                    // нет; курсор в пропуске называет причину словами.
                    //
                    // Затенение левее начала истории остаётся: это область,
                    // куда данные не доходят в принципе.
                    spec.beforeHistory?.let { before ->
                        val left = xOfTime(before.fromMillis)
                        val right = xOfTime(before.toMillis)
                        if (right > left) {
                            drawRect(
                                color = colors.beyondData,
                                topLeft = Offset(left, plotTop),
                                size = Size(right - left, plotHeight),
                            )
                        }
                    }

                    // Вертикальные линии времени по тем же подписям, что снизу.
                    for ((fraction, _) in xTexts) {
                        val x = widthPx * fraction
                        if (x > 0.5f && x < widthPx - 0.5f) {
                            drawLine(
                                color = gridColor,
                                start = Offset(x, plotTop),
                                end = Offset(x, plotTop + plotHeight),
                                strokeWidth = 1f,
                            )
                        }
                    }

                    // 1. Usual-background band of the place.
                    if (bandTop != null && bandBottom != null && bandBottom > bandTop) {
                        drawRect(
                            color = bandColor,
                            topLeft = Offset(0f, bandTop),
                            size = Size(widthPx, bandBottom - bandTop),
                        )
                    }
                    if (baselineMedianY != null) {
                        drawLine(
                            color = bandLineColor,
                            start = Offset(0f, baselineMedianY),
                            end = Offset(widthPx, baselineMedianY),
                            strokeWidth = baselineStroke,
                            pathEffect = dash,
                        )
                    }

                    // 2. Episodes: tinted band naming what it is above.
                    for (rect in episodeRects) {
                        if (rect.right <= rect.left) continue
                        drawRect(
                            color = rect.fill,
                            topLeft = Offset(rect.left, plotTop),
                            size = Size(rect.right - rect.left, plotHeight),
                        )
                        val edgeDash = if (rect.dashedEdge) dash else null
                        drawLine(
                            color = rect.edge,
                            start = Offset(rect.left, plotTop),
                            end = Offset(rect.left, plotTop + plotHeight),
                            pathEffect = edgeDash,
                        )
                        drawLine(
                            color = rect.edge,
                            start = Offset(rect.right, plotTop),
                            end = Offset(rect.right, plotTop + plotHeight),
                            pathEffect = edgeDash,
                        )
                        val width = rect.right - rect.left
                        val text = rect.label?.takeIf { width > it.size.width }
                            ?: rect.shortLabel?.takeIf { width > it.size.width }
                        if (text != null) {
                            drawText(
                                textLayoutResult = text,
                                color = rect.text,
                                topLeft = Offset(
                                    (rect.left + rect.right) / 2f - text.size.width / 2f,
                                    plotTop + 2f,
                                ),
                            )
                        }
                    }

                    // 3. Gridlines, values labelled inside the plot (edge-to-edge).
                    for ((y, text) in yTexts) {
                        drawLine(gridColor, Offset(0f, y), Offset(widthPx, y), 1f)
                        // Подложка цвета поля: на плотном ряду линия проходит
                        // сквозь цифры, и подпись оси перестаёт читаться.
                        drawRect(
                            color = colors.field,
                            topLeft = Offset(
                                labelInset - labelPad,
                                y - text.size.height - 1f,
                            ),
                            size = Size(
                                text.size.width + labelPad * 2f,
                                text.size.height.toFloat(),
                            ),
                        )
                        drawText(
                            textLayoutResult = text,
                            color = colors.muted,
                            topLeft = Offset(labelInset, y - text.size.height - 1f),
                        )
                    }

                    // 4. Названные уровни тревоги: линия внутри кадра,
                    // закреплённый указатель — когда уровень за кадром.
                    for ((index, line) in alarmLines.withIndex()) {
                        val text = line.text
                        val y = line.y
                        if (y == null && text != null) {
                            // Указатель прижат к правому краю поля: подписи оси
                            // значений стоят у левого.
                            val alarmX = (widthPx - text.size.width - labelInset)
                                .coerceAtLeast(0f)
                            if (line.above) {
                                // Указатель стоит над полем, в полосе маркеров:
                                // у верхней кромки он совпадал по высоте с
                                // верхней подписью оси.
                                drawText(
                                    textLayoutResult = text,
                                    color = colors.crit,
                                    topLeft = Offset(
                                        alarmX,
                                        (plotTop - text.size.height - 1f).coerceAtLeast(0f) +
                                            index * (text.size.height + 1f),
                                    ),
                                )
                            } else if (line.below) {
                                drawText(
                                    textLayoutResult = text,
                                    color = colors.crit,
                                    topLeft = Offset(
                                        alarmX,
                                        plotTop + plotHeight - text.size.height - 1f -
                                            index * (text.size.height + 1f),
                                    ),
                                )
                            }
                        }
                        if (y != null) {
                            drawLine(
                                color = colors.crit.copy(alpha = if (index == 0) 0.7f else 0.9f),
                                start = Offset(0f, y),
                                end = Offset(widthPx, y),
                                strokeWidth = alarmStroke,
                                pathEffect = if (index == 0) alarmDash else alarm2Dash,
                            )
                            val top = alarmLabelTops[index]
                            if (text != null && top != null && index in visibleLabels) {
                                drawText(
                                    textLayoutResult = text,
                                    color = colors.crit,
                                    topLeft = Offset(labelInset, top),
                                )
                            }
                        }
                    }

                    // 5. Time labels in the bottom strip, unit in the corner.
                    for ((fraction, text) in xTexts) {
                        val x = (widthPx * fraction - text.size.width / 2f)
                            .coerceIn(0f, (widthPx - text.size.width).coerceAtLeast(0f))
                        drawText(
                            textLayoutResult = text,
                            color = colors.muted,
                            topLeft = Offset(x, heightPx - text.size.height - 1f),
                        )
                    }
                    if (unitText != null) {
                        drawText(
                            textLayoutResult = unitText,
                            color = colors.muted,
                            topLeft = Offset(widthPx - unitText.size.width - labelInset, 1f),
                        )
                    }
                }
            },
    )
}

/** Один названный уровень тревоги, подготовленный к рисованию. */
private class AlarmLine(
    /** Строка поля, где проходит уровень; null — он за пределами кадра. */
    val y: Float?,
    val text: androidx.compose.ui.text.TextLayoutResult?,
    /** Уровень выше кадра — указатель у верхней кромки. */
    val above: Boolean,
    /** Уровень ниже кадра — указатель у нижней. */
    val below: Boolean,
)

private class EpisodeRect(
    val left: Float,
    val right: Float,
    val fill: Color,
    val edge: Color,
    val text: Color,
    val dashedEdge: Boolean,
    val label: androidx.compose.ui.text.TextLayoutResult?,
    val shortLabel: androidx.compose.ui.text.TextLayoutResult?,
)

/**
 * Quantile envelopes, median line, raw dots, extremum markers and the live
 * endpoint.
 */
@Composable
private fun SeriesLayer(
    spec: DoseChartSpec,
    pixels: PreparedFrame,
    widthPx: Float,
    plotTop: Float,
    plotHeight: Float,
    colors: ChartPalette,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    axisStyle: androidx.compose.ui.text.TextStyle,
    /**
     * Курсор: число слипшихся маркеров показывается у выбранной группы, а не
     * у всех сразу (V2 §15).
     */
    cursorFraction: State<Float?>,
) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val outer = if (spec.detailed) Path() else bandPath(pixels, pixels.q90Y, pixels.q10Y)
                val inner = if (spec.detailed) Path() else bandPath(pixels, pixels.q75Y, pixels.q25Y)
                val median = if (spec.detailed) detailPath(pixels) else linePath(pixels)
                val dots = rawDotOffsets(spec, widthPx, plotTop, plotHeight)
                var endpoint: Offset? = null
                for (i in pixels.count - 1 downTo 0) {
                    if (pixels.plottable[i]) {
                        endpoint = Offset(pixels.x[i], pixels.medianY[i])
                        break
                    }
                }
                val outerColor = colors.data.copy(alpha = 0.14f)
                val innerColor = colors.data.copy(alpha = 0.28f)
                val dotColor = colors.muted.copy(alpha = 0.55f)
                val endpointColor = if (spec.endpointAlert) colors.crit else colors.data
                val lineStroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                val dotWidth = 3.dp.toPx()
                val endpointRadius = 4.dp.toPx()
                val ringStroke = Stroke(width = 2.dp.toPx())
                val loneDots = lonePoints(pixels)
                val loneRadius = 2.dp.toPx()
                val markerSize = 6.dp.toPx()
                val markers = extremeMarks(spec, pixels, plotTop, markerSize)
                val markerStroke = Stroke(width = 1.2.dp.toPx())
                // Число слипшихся маркеров измеряется здесь, рисование ничего
                // не считает. Одиночный маркер числа не носит; счётчик виден
                // только у группы под курсором.
                val markerCounts = markers.map { mark ->
                    mark.takeIf { it.count > 1 }
                        ?.let { textMeasurer.measure(it.count.toString(), axisStyle) }
                }
                // Подпись последней точки измеряется здесь: рисование ничего
                // не считает.
                val endpointText = spec.endpointLabel
                    ?.takeIf { it.isNotBlank() && endpoint != null }
                    ?.let { textMeasurer.measure(it, axisStyle) }
                val labelPadding = 3.dp.toPx()
                val labelRadius = CornerRadius(3.dp.toPx())

                onDrawBehind {
                    drawPath(outer, outerColor)
                    drawPath(inner, innerColor)
                    if (dots.isNotEmpty()) {
                        drawPoints(
                            points = dots,
                            pointMode = PointMode.Points,
                            color = dotColor,
                            strokeWidth = dotWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                    drawPath(median, colors.data, style = lineStroke)
                    // Одиночные колонки между пропусками: линии из одной точки
                    // не бывает.
                    for (dot in loneDots) drawCircle(colors.data, loneRadius, dot)
                    // Extrema as discrete marks above the plot, filled above
                    // the alarm level and hollow above the profile's P90 —
                    // shape carries the class, not colour alone.
                    val cursorX = cursorFraction.value?.let { it * widthPx }
                    val selected = cursorX?.let { x ->
                        markers.indices.minByOrNull { kotlin.math.abs(markers[it].x - x) }
                            ?.takeIf { kotlin.math.abs(markers[it].x - x) <= markerSize * 2f }
                    }
                    for ((index, mark) in markers.withIndex()) {
                        val hue = if (mark.alarmClass) colors.crit else colors.warn
                        if (mark.alarmClass) drawPath(mark.path, hue)
                        else drawPath(mark.path, hue, style = markerStroke)
                        markerCounts[index]?.takeIf { index == selected }?.let { label ->
                            drawText(
                                textLayoutResult = label,
                                color = hue,
                                topLeft = Offset(
                                    (mark.x + markerSize * 0.75f)
                                        .coerceAtMost(widthPx - label.size.width),
                                    // По центру треугольника: прижатое к
                                    // верхушке число читается как надстрочный
                                    // знак.
                                    plotTop - 1f - markerSize / 2f - label.size.height / 2f,
                                ),
                            )
                        }
                    }
                    endpoint?.let {
                        drawCircle(endpointColor, endpointRadius, it)
                        drawCircle(colors.bg, endpointRadius, it, style = ringStroke)
                    }
                    // Значение последней точки — непрозрачной плашкой у правого
                    // края, на высоте самой точки.
                    if (endpointText != null && endpoint != null) {
                        val boxW = endpointText.size.width + labelPadding * 2
                        val boxH = endpointText.size.height + labelPadding
                        val left = (endpoint.x + endpointRadius + labelPadding)
                            .coerceAtMost(widthPx - boxW)
                        val top = (endpoint.y - boxH / 2f)
                            .coerceIn(plotTop, plotTop + plotHeight - boxH)
                        drawRoundRect(
                            color = colors.bg,
                            topLeft = Offset(left, top),
                            size = Size(boxW, boxH),
                            cornerRadius = labelRadius,
                        )
                        drawRoundRect(
                            color = endpointColor,
                            topLeft = Offset(left, top),
                            size = Size(boxW, boxH),
                            cornerRadius = labelRadius,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        drawText(
                            textLayoutResult = endpointText,
                            color = endpointColor,
                            topLeft = Offset(left + labelPadding, top + labelPadding / 2f),
                        )
                    }
                }
            },
    )
}

/**
 * Crosshair. Its own draw node reading [cursorFraction] through a [State]: a
 * drag invalidates the draw phase of this node only — no recomposition, no
 * relayout, nothing else repainted. The line snaps to the nearest column, so
 * the readout always names a real measurement interval.
 */
@Composable
private fun CursorLayer(
    pixels: PreparedFrame,
    cursorFraction: State<Float?>,
    widthPx: Float,
    plotTop: Float,
    plotHeight: Float,
    colors: ChartPalette,
) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val fraction = cursorFraction.value ?: return@drawBehind
                val index = pixels.nearestIndex(fraction * widthPx) ?: return@drawBehind
                val x = pixels.x[index]
                drawLine(
                    color = colors.dataText.copy(alpha = 0.85f),
                    start = Offset(x, plotTop),
                    end = Offset(x, plotTop + plotHeight),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                    ),
                )
                if (pixels.plottable[index]) {
                    val center = Offset(x, pixels.medianY[index])
                    drawCircle(colors.dataText, 4.5.dp.toPx(), center)
                    drawCircle(colors.bg, 4.5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                }
            },
    )
}

/** Closed polygon between two y arrays, restarted at every gap. */
private fun bandPath(pixels: PreparedFrame, high: FloatArray, low: FloatArray): Path {
    val path = Path()
    var start = -1
    fun flush(end: Int) {
        if (start < 0 || end < start) {
            start = -1
            return
        }
        path.moveTo(pixels.x[start], high[start])
        for (i in start + 1..end) path.lineTo(pixels.x[i], high[i])
        for (i in end downTo start) path.lineTo(pixels.x[i], low[i])
        path.close()
        start = -1
    }
    for (i in 0 until pixels.count) {
        if (pixels.plottable[i]) {
            // Полоса разброса рвётся там же, где линия: конверт через пропуск
            // описывал бы разброс измерений, которых не было.
            if (pixels.segmentStart[i]) flush(i - 1)
            if (start < 0) start = i
        } else {
            flush(i - 1)
        }
    }
    flush(pixels.count - 1)
    return path
}

/** Median polyline; a gap breaks the pen, nothing is interpolated across it. */
/**
 * Подробный ряд: линия по крайним значениям колонок. В каждой колонке перо
 * идёт от максимума к минимуму и продолжает со значения, на котором
 * остановилось. При узкой колонке (окно / число колонок) минимум и максимум
 * совпадают с самим измерением; при широкой это прореживание, сохраняющее
 * форму, — пик и провал внутри колонки остаются на картинке.
 */
private fun detailPath(pixels: PreparedFrame): Path {
    val path = Path()
    var penDown = false
    for (i in 0 until pixels.count) {
        if (!pixels.plottable[i]) {
            penDown = false
            continue
        }
        if (pixels.segmentStart[i]) penDown = false
        val high = pixels.maxY[i]
        val low = pixels.minY[i]
        if (penDown) path.lineTo(pixels.x[i], high) else path.moveTo(pixels.x[i], high)
        if (low != high) path.lineTo(pixels.x[i], low)
        penDown = true
    }
    return path
}

private fun linePath(pixels: PreparedFrame): Path {
    val path = Path()
    var penDown = false
    for (i in 0 until pixels.count) {
        if (!pixels.plottable[i]) {
            penDown = false
            continue
        }
        // Пустые колонки в снимок не попадают, поэтому соседство по индексу
        // не означает соседства во времени: перо поднимается по временному
        // разрыву.
        if (pixels.segmentStart[i]) penDown = false
        if (penDown) path.lineTo(pixels.x[i], pixels.medianY[i])
        else path.moveTo(pixels.x[i], pixels.medianY[i])
        penDown = true
    }
    return path
}

/**
 * Колонки, стоящие в одиночестве между двумя пропусками. Отрезок из одной
 * точки не даёт пикселей (`moveTo` без `lineTo`), поэтому одиночное измерение
 * рисуется точкой.
 */
private fun lonePoints(pixels: PreparedFrame): List<Offset> {
    if (pixels.count == 0) return emptyList()
    val out = mutableListOf<Offset>()
    for (i in 0 until pixels.count) {
        if (!pixels.plottable[i]) continue
        val breaksBefore = i == 0 || pixels.segmentStart[i] || !pixels.plottable[i - 1]
        val breaksAfter = i == pixels.count - 1 ||
            pixels.segmentStart[i + 1] || !pixels.plottable[i + 1]
        if (breaksBefore && breaksAfter) out += Offset(pixels.x[i], pixels.medianY[i])
    }
    return out
}

/** One extremum mark: a triangle above the plot at its column. */
private class ExtremeMark(val path: Path, val alarmClass: Boolean, val count: Int, val x: Float)

/**
 * Triangles for the notable extrema (§7, §21). They sit in the top padding
 * strip, above the data, so they never overlap an envelope and cannot be read
 * as part of it.
 */
/**
 * Во сколько размеров маркера должны разойтись два треугольника, чтобы
 * считаться разными.
 *
 * **Инженерный параметр**: три размера. Шаг считается по полной ширине
 * маркера со счётчиком: при полутора счётчик группы налезал на соседний
 * маркер.
 */
private const val MARKER_SPACING_FACTOR = 3f

private fun extremeMarks(
    spec: DoseChartSpec,
    pixels: PreparedFrame,
    plotTop: Float,
    sizePx: Float,
): List<ExtremeMark> {
    if (spec.extremeMarkers.isEmpty()) return emptyList()
    val positions = ArrayList<Pair<Float, Boolean>>(spec.extremeMarkers.size)
    for (marker in spec.extremeMarkers) {
        val k = pixels.indexOfBucket(marker.bucketIndex) ?: continue
        positions += pixels.x[k] to (marker.reference == DoseReference.ALARM_L1)
    }
    // Слипшиеся маркеры собираются в один со счётчиком.
    val clusters = MarkerClusters.of(positions, minSpacingPx = sizePx * MARKER_SPACING_FACTOR)
    val out = ArrayList<ExtremeMark>(clusters.size)
    for (cluster in clusters) {
        val x = cluster.x
        val bottom = plotTop - 1f
        val top = (bottom - sizePx).coerceAtLeast(0f)
        val path = Path().apply {
            moveTo(x, top)
            lineTo(x + sizePx / 2f, bottom)
            lineTo(x - sizePx / 2f, bottom)
            close()
        }
        out += ExtremeMark(path, cluster.alarmClass, cluster.count, x)
    }
    return out
}

/** Individual measurements as one batched point list (single draw call). */
private fun rawDotOffsets(
    spec: DoseChartSpec,
    widthPx: Float,
    plotTop: Float,
    plotHeight: Float,
): List<Offset> {
    if (spec.rawSamples.isEmpty()) return emptyList()
    val span = (spec.viewTo - spec.viewFrom).coerceAtLeast(1L)
    val out = ArrayList<Offset>(spec.rawSamples.size)
    for (a in spec.rawSamples) {
        if (a.startMillis < spec.viewFrom || a.startMillis > spec.viewTo) continue
        val fraction = spec.scale.fractionOrNull(a.meanMicroSvH) ?: continue
        out += Offset(
            widthPx * (a.startMillis - spec.viewFrom).toFloat() / span,
            ChartProjection.yOf(fraction, plotTop, plotHeight),
        )
    }
    return out
}

/**
 * Ниже этой скорости бросок не считается броском, px/с.
 * **Инженерный параметр**: медленное отпускание пальца — остановка.
 */
/**
 * Полоса у правого края, жест по которой масштабирует ось значений.
 * **Инженерный параметр**: 44 dp — рекомендованный размер цели нажатия; шире
 * полоса отбирает перемещение у поля.
 */
private val VALUE_SCALE_GUTTER = 44.dp

/**
 * Сколько разметок текста держать в кэше.
 * **Инженерный параметр**: 64 — вчетверо больше, чем подписей в самом плотном
 * кадре, поэтому попадание в кэш не зависит от порядка отрисовки.
 */
private const val TEXT_CACHE_SIZE = 64

private const val MIN_FLING_VELOCITY = 200f

/** Трение затухания: больше — короче выбег. */
private const val FLING_FRICTION = 1.6f
