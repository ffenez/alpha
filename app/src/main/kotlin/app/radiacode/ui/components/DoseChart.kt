package app.radiacode.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import app.radiacode.ui.logic.ChartBucket
import app.radiacode.ui.logic.DataGap
import app.radiacode.ui.logic.TimeBand
import app.radiacode.ui.logic.ChartPixels
import app.radiacode.ui.logic.ChartProjection
import app.radiacode.ui.logic.ValueAggregate
import app.radiacode.ui.logic.DoseEpisode
import app.radiacode.ui.logic.DoseReference
import app.radiacode.ui.logic.DoseScale
import app.radiacode.ui.logic.ExtremeMarker
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Everything the dose chart draws, as one immutable value. Nothing here is
 * mutable state: an identical spec lets Compose skip the chart entirely —
 * which is what keeps the 1 Hz live value from repainting the plot.
 */
@Immutable
data class DoseChartSpec(
    /** Drawn columns, ordered; an absent column is a gap, never interpolated. */
    val buckets: List<ChartBucket>,
    /** Visible time range; columns are placed by wall-clock time inside it. */
    val fromMillis: Long,
    val toMillis: Long,
    val scale: DoseScale,
    /** «Привычный фон места»: P10–P90 of the active baseline, µSv/h. */
    val baselineBand: ClosedFloatingPointRange<Float>? = null,
    val baselineMedian: Float? = null,
    val alarmLevel: Float? = null,
    val alarmLabel: String? = null,
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
     * [app.radiacode.ui.logic.ChartSeriesModel.rawDotsVisible]).
     */
    val rawSamples: List<ValueAggregate> = emptyList(),
    val endpointAlert: Boolean = false,
)

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
 *    primitive arrays ([ChartPixels]); the draw scope allocates nothing.
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
    onTransform: ((panFraction: Float, zoomFactor: Float, focusFraction: Float) -> Unit)? = null,
    /**
     * Жесты. На Главной график — миниатюра: он показывает ту же картинку, но
     * принадлежит карточке, и единственное действие над ним — открыть его во
     * весь экран. Обработчики там не просто бесполезны, а вредны: они
     * перехватывают касание у карточки.
     */
    interactive: Boolean = true,
) {
    val appColors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()
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
            spec.fromMillis,
            spec.toMillis,
            spec.scale,
            widthPx,
            heightPx,
        ) {
            ChartProjection.project(
                buckets = spec.buckets,
                fromMillis = spec.fromMillis,
                toMillis = spec.toMillis,
                scale = spec.scale,
                leftPx = 0f,
                widthPx = widthPx,
                topPx = padTop,
                heightPx = plotHeight,
            )
        }

        StaticChartLayer(spec, widthPx, heightPx, padTop, plotHeight, textMeasurer, axisStyle, palette)
        SeriesLayer(spec, pixels, widthPx, padTop, plotHeight, palette)
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
        val transform = rememberUpdatedState(onTransform)
        // Маркер экстремума — не украшение, а указание «здесь что-то было»:
        // по нему должно открываться то же, что по любому месту графика.
        // Порог попадания — палец, а не размер треугольника.
        val markerHitPx = with(density) { 24.dp.toPx() }
        val markerBandPx = padTop + markerHitPx
        val markers = remember(spec.extremeMarkers, pixels, widthPx) {
            spec.extremeMarkers.mapNotNull { marker ->
                pixels.indexOfBucket(marker.bucketIndex)?.let { pixels.x[it] }
            }
        }
        Spacer(
            Modifier
                .fillMaxSize()
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
                            transform.value?.invoke(
                                pan.x / widthPx.coerceAtLeast(1f),
                                zoom,
                                fractionOf(centroid.x, widthPx),
                            )
                        }
                    }
                },
        )
    }
}

private fun fractionOf(xPx: Float, widthPx: Float): Float =
    (xPx / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

/**
 * Диагональная штриховка промежутка без измерений: рисуется линиями, а не
 * заливкой, чтобы её нельзя было прочитать как данные — заливка на графике
 * измерений всегда что-то означает.
 */
private fun DrawScope.hatch(
    fromX: Float,
    toX: Float,
    top: Float,
    height: Float,
    color: Color,
    step: Float,
) {
    if (toX <= fromX || height <= 0f || step <= 0f) return
    clipRect(left = fromX, top = top, right = toX, bottom = top + height) {
        var x = fromX - height
        while (x < toX + height) {
            drawLine(
                color = color,
                start = Offset(x, top + height),
                end = Offset(x + height, top),
                strokeWidth = 1f,
            )
            x += step
        }
    }
}

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
                val bandColor = colors.ink2.copy(alpha = 0.13f)
                val bandLineColor = colors.ink2.copy(alpha = 0.42f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                val alarmDash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                val alarmStroke = 1.dp.toPx()
                val baselineStroke = 1.5.dp.toPx()
                val labelInset = 4.dp.toPx()
                val hatchStep = 7.dp.toPx()
                val spanMillis = (spec.toMillis - spec.fromMillis).coerceAtLeast(1L)
                fun xOfTime(millis: Long): Float =
                    (widthPx * (millis - spec.fromMillis).toFloat() / spanMillis)
                        .coerceIn(0f, widthPx)

                fun yOf(value: Float): Float? = spec.scale.fractionOrNull(value)
                    ?.let { ChartProjection.yOf(it, plotTop, plotHeight) }

                // Text is laid out once here, not on every frame.
                val yTexts = spec.yLabels.mapNotNull { (value, label) ->
                    yOf(value)?.let { it to textMeasurer.measure(label, axisStyle) }
                }
                val xTexts = spec.xLabels.map { (fraction, label) ->
                    fraction to textMeasurer.measure(label, axisStyle)
                }
                val unitText = spec.unitLabel.takeIf { it.isNotEmpty() }
                    ?.let { textMeasurer.measure(it, axisStyle) }
                // §3: далёкий L1 НЕ растягивает ось (это делает ChartMapping),
                // но и не исчезает — когда он выше кадра, вместо линии
                // рисуется закреплённый указатель «↑ L1 0,30» у верхней
                // кромки. Порог, о котором забыли, — это порог, которого нет.
                val alarmAbove = spec.alarmLevel != null &&
                    spec.alarmLevel > spec.scale.maxValue
                // Симметрично: кадр подогнан к данным и может целиком уйти
                // ВЫШЕ порога — тогда указатель «↓ L1 0,30» стоит у нижней
                // кромки. Иначе на графике превышения не было бы видно самой
                // величины, относительно которой оно превышение.
                val alarmBelow = spec.alarmLevel != null &&
                    spec.alarmLevel < spec.scale.minValue
                val alarmText = spec.alarmLabel
                    ?.let {
                        when {
                            alarmAbove -> "↑ $it"
                            alarmBelow -> "↓ $it"
                            else -> it
                        }
                    }
                    ?.let { textMeasurer.measure(it, axisStyle) }
                val alarmY = spec.alarmLevel
                    ?.takeIf { !alarmAbove && !alarmBelow }
                    ?.let { yOf(it) }
                val bandTop = spec.baselineBand?.let { yOf(it.endInclusive) }
                val bandBottom = spec.baselineBand?.let { yOf(it.start) }
                val baselineMedianY = spec.baselineMedian?.let { yOf(it) }
                val span = (spec.toMillis - spec.fromMillis).coerceAtLeast(1L)
                val episodeRects = spec.episodes.mapIndexed { index, episode ->
                    val a = widthPx * (episode.fromMillis - spec.fromMillis).toFloat() / span
                    val b = widthPx * (episode.toMillis - spec.fromMillis).toFloat() / span
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
                    // 0. Поле графика — своя плоскость, а не карточка под
                    // ним: в светлой теме данные иначе лежат на белом листе
                    // без видимой границы.
                    drawRect(
                        color = colors.field,
                        topLeft = Offset(0f, plotTop),
                        size = Size(widthPx, plotHeight),
                    )
                    // Зебра времени: опора для глаза на длинных окнах. Полосы
                    // привязаны к стенным часам, поэтому не дёргаются при
                    // прокрутке.
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

                    // Пропуски потока и область до начала истории: «прибор
                    // молчал» и «сюда данные не доходят» — не то же самое, что
                    // «уровень был низкий», и на пустом поле это неразличимо.
                    for (gap in spec.gaps) {
                        hatch(
                            fromX = xOfTime(gap.fromMillis),
                            toX = xOfTime(gap.toMillis),
                            top = plotTop,
                            height = plotHeight,
                            color = colors.grid,
                            step = hatchStep,
                        )
                    }
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

                    // Вертикальные линии времени по тем же подписям, что и
                    // снизу: на суточном окне без них глазу не за что
                    // зацепиться по горизонтали.
                    for ((fraction, _) in spec.xLabels) {
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
                        drawText(
                            textLayoutResult = text,
                            color = colors.muted,
                            topLeft = Offset(labelInset, y - text.size.height - 1f),
                        )
                    }

                    // 4. Named alarm level — a line inside the frame, a pinned
                    // pointer above it.
                    if (alarmY == null && alarmText != null) {
                        if (alarmAbove) {
                            drawText(
                                textLayoutResult = alarmText,
                                color = colors.crit,
                                topLeft = Offset(labelInset, plotTop + 1f),
                            )
                        } else if (alarmBelow) {
                            drawText(
                                textLayoutResult = alarmText,
                                color = colors.crit,
                                topLeft = Offset(
                                    labelInset,
                                    plotTop + plotHeight - alarmText.size.height - 1f,
                                ),
                            )
                        }
                    }
                    if (alarmY != null) {
                        drawLine(
                            color = colors.crit.copy(alpha = 0.7f),
                            start = Offset(0f, alarmY),
                            end = Offset(widthPx, alarmY),
                            strokeWidth = alarmStroke,
                            pathEffect = alarmDash,
                        )
                        if (alarmText != null) {
                            drawText(
                                textLayoutResult = alarmText,
                                color = colors.crit,
                                topLeft = Offset(
                                    labelInset,
                                    (alarmY - 2f - alarmText.size.height).coerceAtLeast(0f),
                                ),
                            )
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
    pixels: ChartPixels,
    widthPx: Float,
    plotTop: Float,
    plotHeight: Float,
    colors: ChartPalette,
) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val outer = bandPath(pixels, pixels.q90Y, pixels.q10Y)
                val inner = bandPath(pixels, pixels.q75Y, pixels.q25Y)
                val median = linePath(pixels)
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
                val markers = extremeMarks(spec, pixels, plotTop, 6.dp.toPx())
                val markerStroke = Stroke(width = 1.2.dp.toPx())

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
                    // Extrema as discrete marks above the plot, filled above
                    // the alarm level and hollow above the profile's P90 —
                    // shape carries the class, not colour alone.
                    for (mark in markers) {
                        val hue = if (mark.alarmClass) colors.crit else colors.warn
                        if (mark.alarmClass) drawPath(mark.path, hue)
                        else drawPath(mark.path, hue, style = markerStroke)
                    }
                    endpoint?.let {
                        drawCircle(endpointColor, endpointRadius, it)
                        drawCircle(colors.bg, endpointRadius, it, style = ringStroke)
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
    pixels: ChartPixels,
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
private fun bandPath(pixels: ChartPixels, high: FloatArray, low: FloatArray): Path {
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
            if (start < 0) start = i
        } else {
            flush(i - 1)
        }
    }
    flush(pixels.count - 1)
    return path
}

/** Median polyline; a gap breaks the pen, nothing is interpolated across it. */
private fun linePath(pixels: ChartPixels): Path {
    val path = Path()
    var penDown = false
    for (i in 0 until pixels.count) {
        if (!pixels.plottable[i]) {
            penDown = false
            continue
        }
        if (penDown) path.lineTo(pixels.x[i], pixels.medianY[i])
        else path.moveTo(pixels.x[i], pixels.medianY[i])
        penDown = true
    }
    return path
}

/** One extremum mark: a triangle above the plot at its column. */
private class ExtremeMark(val path: Path, val alarmClass: Boolean)

/**
 * Triangles for the notable extrema (§7, §21). They sit in the top padding
 * strip, above the data, so they never overlap an envelope and cannot be read
 * as part of it.
 */
private fun extremeMarks(
    spec: DoseChartSpec,
    pixels: ChartPixels,
    plotTop: Float,
    sizePx: Float,
): List<ExtremeMark> {
    if (spec.extremeMarkers.isEmpty()) return emptyList()
    val out = ArrayList<ExtremeMark>(spec.extremeMarkers.size)
    for (marker in spec.extremeMarkers) {
        val k = pixels.indexOfBucket(marker.bucketIndex) ?: continue
        val x = pixels.x[k]
        val bottom = plotTop - 1f
        val top = (bottom - sizePx).coerceAtLeast(0f)
        val path = Path().apply {
            moveTo(x, top)
            lineTo(x + sizePx / 2f, bottom)
            lineTo(x - sizePx / 2f, bottom)
            close()
        }
        out += ExtremeMark(path, marker.reference == DoseReference.ALARM_L1)
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
    val span = (spec.toMillis - spec.fromMillis).coerceAtLeast(1L)
    val out = ArrayList<Offset>(spec.rawSamples.size)
    for (a in spec.rawSamples) {
        if (a.startMillis < spec.fromMillis || a.startMillis > spec.toMillis) continue
        val fraction = spec.scale.fractionOrNull(a.meanMicroSvH) ?: continue
        out += Offset(
            widthPx * (a.startMillis - spec.fromMillis).toFloat() / span,
            ChartProjection.yOf(fraction, plotTop, plotHeight),
        )
    }
    return out
}
