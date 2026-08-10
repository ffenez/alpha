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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.radiacode.ui.logic.ChartBucket
import app.radiacode.ui.logic.ChartPixels
import app.radiacode.ui.logic.ChartProjection
import app.radiacode.ui.logic.DoseAggregate
import app.radiacode.ui.logic.DoseEpisode
import app.radiacode.ui.logic.DoseScale
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
    /** Episode index → duration label drawn inside its band. */
    val episodeLabels: List<String> = emptyList(),
    /** Value → label of the y gridlines. */
    val yLabels: List<Pair<Float, String>> = emptyList(),
    /** Fraction (0..1) → label of the time axis. */
    val xLabels: List<Pair<Float, String>> = emptyList(),
    val unitLabel: String = "",
    /**
     * Individual measurements, drawn as dots only when the columns are short
     * enough that one aggregate ≈ one sample (see
     * [app.radiacode.ui.logic.DoseChartModel.rawDotsVisible]).
     */
    val rawSamples: List<DoseAggregate> = emptyList(),
    val endpointAlert: Boolean = false,
)

/**
 * Fullscreen dose-rate chart («Научный терминал», design-language.md).
 *
 * **Anatomy, outside in.** A light teal fill is the min–max envelope of each
 * column — the full spread of what was actually measured, nothing hidden. A
 * denser teal fill inside it is mean ± σ — the typical spread of the counting
 * statistics. The solid teal line is the per-column median: a robust level,
 * never presented as the measurement itself. A grey band with a dashed centre
 * is the usual background of the place (baseline P10–P90). A dashed red line
 * is the named alarm level. Amber vertical bands are deviation episodes with
 * their duration.
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
    onTransform: ((panFraction: Float, zoomFactor: Float, focusFraction: Float) -> Unit)? = null,
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
        val active = rememberUpdatedState(cursorActive)
        val setCursor = rememberUpdatedState(onCursorFraction)
        val dismissCursor = rememberUpdatedState(onCursorDismiss)
        val transform = rememberUpdatedState(onTransform)
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
                    detectTapGestures(onTap = { if (active.value) dismissCursor.value() })
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
                val gridColor = colors.ink2.copy(alpha = 0.13f)
                val bandColor = colors.ink2.copy(alpha = 0.13f)
                val bandLineColor = colors.ink2.copy(alpha = 0.42f)
                val episodeFill = colors.warn.copy(alpha = 0.13f)
                val episodeEdge = colors.warn.copy(alpha = 0.5f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                val alarmDash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                val alarmStroke = 1.dp.toPx()
                val baselineStroke = 1.5.dp.toPx()
                val labelInset = 4.dp.toPx()

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
                val alarmText = spec.alarmLabel?.let { textMeasurer.measure(it, axisStyle) }
                val alarmY = spec.alarmLevel?.let { yOf(it) }
                val bandTop = spec.baselineBand?.let { yOf(it.endInclusive) }
                val bandBottom = spec.baselineBand?.let { yOf(it.start) }
                val baselineMedianY = spec.baselineMedian?.let { yOf(it) }
                val span = (spec.toMillis - spec.fromMillis).coerceAtLeast(1L)
                val episodeRects = spec.episodes.mapIndexed { index, episode ->
                    val a = widthPx * (episode.fromMillis - spec.fromMillis).toFloat() / span
                    val b = widthPx * (episode.toMillis - spec.fromMillis).toFloat() / span
                    EpisodeRect(
                        left = a.coerceIn(0f, widthPx),
                        right = b.coerceIn(0f, widthPx),
                        label = spec.episodeLabels.getOrNull(index)
                            ?.let { textMeasurer.measure(it, axisStyle) },
                    )
                }

                onDrawBehind {
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

                    // 2. Deviation episodes: amber tint with the duration.
                    for (rect in episodeRects) {
                        if (rect.right <= rect.left) continue
                        drawRect(
                            color = episodeFill,
                            topLeft = Offset(rect.left, plotTop),
                            size = Size(rect.right - rect.left, plotHeight),
                        )
                        drawLine(
                            episodeEdge,
                            Offset(rect.left, plotTop),
                            Offset(rect.left, plotTop + plotHeight),
                        )
                        drawLine(
                            episodeEdge,
                            Offset(rect.right, plotTop),
                            Offset(rect.right, plotTop + plotHeight),
                        )
                        val text = rect.label
                        if (text != null && rect.right - rect.left > text.size.width) {
                            drawText(
                                textLayoutResult = text,
                                color = colors.warn,
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

                    // 4. Named alarm level.
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
    val label: androidx.compose.ui.text.TextLayoutResult?,
)

/** Envelope, σ band, median line, raw dots and the live endpoint. */
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
                val envelope = bandPath(pixels, pixels.maxY, pixels.minY)
                val sigma = bandPath(pixels, pixels.sigmaHiY, pixels.sigmaLoY)
                val median = linePath(pixels)
                val dots = rawDotOffsets(spec, widthPx, plotTop, plotHeight)
                var endpoint: Offset? = null
                for (i in pixels.count - 1 downTo 0) {
                    if (pixels.plottable[i]) {
                        endpoint = Offset(pixels.x[i], pixels.medianY[i])
                        break
                    }
                }
                val envelopeColor = colors.data.copy(alpha = 0.14f)
                val sigmaColor = colors.data.copy(alpha = 0.28f)
                val dotColor = colors.muted.copy(alpha = 0.55f)
                val endpointColor = if (spec.endpointAlert) colors.crit else colors.data
                val lineStroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                val dotWidth = 3.dp.toPx()
                val endpointRadius = 4.dp.toPx()
                val ringStroke = Stroke(width = 2.dp.toPx())

                onDrawBehind {
                    drawPath(envelope, envelopeColor)
                    drawPath(sigma, sigmaColor)
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
