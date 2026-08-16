package app.radiacode.ui.components

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
import app.radiacode.ui.logic.MarkerClusters
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
    /**
     * Подробный ряд вместо медианы с конвертами.
     *
     * Линия ведётся по крайним значениям колонок: на коротком окне в колонке
     * одно измерение, и линия идёт ровно по измерениям; на длинном она
     * сохраняет форму — пик и провал внутри колонки остаются видны, а не
     * усредняются. Квантильные заливки при этом не рисуются: они описывают
     * разброс ВНУТРИ колонки, а подробный вид показывает сами измерения, и
     * две картинки одновременно означали бы два разных утверждения.
     */
    val detailed: Boolean = false,
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
    /**
     * Подпись последнего значения у правого края («0,16»).
     *
     * Последняя точка — то, ради чего график открывают чаще всего, и её
     * значение не должно требовать курсора. Единица не повторяется: она уже
     * стоит в углу поля.
     */
    val endpointLabel: String? = null,
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
     * Одиночное нажатие по полю. Задан — курсор по тапу не ставится: у
     * миниатюры одно действие, и это «открыть во весь экран».
     */
    onTap: (() -> Unit)? = null,
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
        SeriesLayer(spec, pixels, widthPx, padTop, plotHeight, palette, textMeasurer, axisStyle)
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
        // Инерция: бросок пальцем продолжает движение с затуханием.
        //
        // Наблюдающий слой, а не ещё один обработчик жестов: события читаются
        // на ПЕРВОМ проходе и НЕ поглощаются, поэтому перемещение, щипок,
        // курсор и нажатия работают ровно как раньше. Здесь только две вещи,
        // которых у `detectTransformGestures` нет вовсе, — скорость в момент
        // отрыва и сам факт отрыва.
        val flingScope = rememberCoroutineScope()
        var flingJob by remember { mutableStateOf<Job?>(null) }
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
                                // Касание во время броска немедленно забирает
                                // управление: график обязан слушаться пальца,
                                // а не доезжать по своим делам.
                                flingJob?.cancel()
                                flingJob = null
                                // Скорость снимается по ОДНОМУ пальцу: у щипка
                                // своя геометрия, и бросок после него означал
                                // бы движение, которого человек не делал.
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
                                                // что и палец: экран сам не
                                                // пускает дальше доступной
                                                // истории и края «сейчас».
                                                transform(
                                                    delta / widthPx.coerceAtLeast(1f),
                                                    1f,
                                                    0.5f,
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
                // Обычный диапазон места — КОНТЕКСТ, а не главный герой: он
                // занимал большую часть карточки и читался сильнее самой
                // сетки, из-за чего измерение приходилось искать глазами.
                // Смысл данных не меняется, меняется вес.
                val bandColor = colors.ink2.copy(alpha = 0.07f)
                val bandLineColor = colors.ink2.copy(alpha = 0.26f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                val alarmDash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                val alarmStroke = 1.dp.toPx()
                val baselineStroke = 1.5.dp.toPx()
                val labelInset = 4.dp.toPx()
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
                // Указатель показывается, только пока порог РЯДОМ с кадром: при
                // фоне 0,15 и пороге 0,30 он ещё говорит «до порога вдвое», а
                // при фоне 0,15 и пороге 3 — уже нет, и красная строка висела
                // бы над каждым графиком, ничего не сообщая. Мера близости —
                // высота самого кадра: дальше неё порог перестаёт быть
                // ориентиром для того, что нарисовано.
                val frameSpan = (spec.scale.maxValue - spec.scale.minValue).coerceAtLeast(0f)
                val alarmNear = spec.alarmLevel != null &&
                    spec.alarmLevel <= spec.scale.maxValue + frameSpan
                val alarmAbove = alarmNear && spec.alarmLevel != null &&
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
                // Линия порога рисуется, ТОЛЬКО когда порог лежит внутри
                // кадра.
                //
                // Полевой дефект: при фоне 0,13 и пороге 0,30 красная
                // пунктирная линия висела у верхней кромки. Причина —
                // «далёкий» и «очень далёкий» порог различались: у далёкого
                // (в пределах одной высоты кадра) взводился `alarmAbove` и
                // рисовался указатель, а у очень далёкого не взводилось
                // ничего, и линия шла через `yOf`, где доля зажимается в
                // 0..1 — то есть ложилась ровно на верхний край кадра и
                // читалась как «порог здесь».
                val alarmY = spec.alarmLevel
                    ?.takeIf { it in spec.scale.minValue..spec.scale.maxValue }
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
                    // Пропуск данных — ОТСУТСТВИЕ, и рисуется он отсутствием:
                    // линия просто обрывается. Заливка плоскостью читалась как
                    // «серые полосы поперёк графика» — вещь на картинке, хотя
                    // никакой вещи там нет. Двусмысленности «прибор молчал» ↔
                    // «уровень был низкий» не возникает: у низкого уровня линия
                    // ЕСТЬ и идёт понизу, у пропуска её нет вовсе, а курсор,
                    // поставленный в пропуск, называет причину словами.
                    //
                    // Затенение левее начала истории остаётся: это не пропуск
                    // внутри записи, а область, куда данные не доходят в
                    // принципе, — про неё картинка обязана сказать.
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
                        // Подложка цвета поля: на плотном ряду (счёт, жёсткость)
                        // линия проходила прямо сквозь цифры, и подпись оси
                        // переставала читаться — а это единственное место, где
                        // сказано, в каких числах график.
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

                    // 4. Named alarm level — a line inside the frame, a pinned
                    // pointer above it.
                    if (alarmY == null && alarmText != null) {
                        // Указатель прижат к ПРАВОМУ краю поля, а подписи оси
                        // значений стоят у левого: у верхней кромки они иначе
                        // накладывались друг на друга — «↑ L1 0,30» садилось
                        // ровно на верхнюю подпись сетки. Единица (если она
                        // показана) занимает правый угол НАД полем, а
                        // указатель живёт ВНУТРИ поля, поэтому не спорит и с
                        // ней.
                        val alarmX = (widthPx - alarmText.size.width - labelInset)
                            .coerceAtLeast(0f)
                        if (alarmAbove) {
                            // Указатель стоит НАД полем, в полосе маркеров, а
                            // не внутри шкалы: у верхней кромки он вставал
                            // почти на одну высоту с верхней подписью оси и
                            // читался как её значение, хотя порог лежит далеко
                            // за пределами кадра.
                            drawText(
                                textLayoutResult = alarmText,
                                color = colors.crit,
                                topLeft = Offset(
                                    alarmX,
                                    (plotTop - alarmText.size.height - 1f).coerceAtLeast(0f),
                                ),
                            )
                        } else if (alarmBelow) {
                            drawText(
                                textLayoutResult = alarmText,
                                color = colors.crit,
                                topLeft = Offset(
                                    alarmX,
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
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    axisStyle: androidx.compose.ui.text.TextStyle,
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
                // Число слипшихся маркеров измеряется здесь: рисование ничего
                // не считает. Одиночный маркер числа не носит — «1» рядом с
                // треугольником означала бы, что бывает и «не один».
                val markerCounts = markers.map { mark ->
                    mark.takeIf { it.count > 1 }
                        ?.let { textMeasurer.measure(it.count.toString(), axisStyle) }
                }
                // Подпись последней точки: измеряется здесь, чтобы рисование
                // ничего не считало.
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
                    // не бывает, а измерение было.
                    for (dot in loneDots) drawCircle(colors.data, loneRadius, dot)
                    // Extrema as discrete marks above the plot, filled above
                    // the alarm level and hollow above the profile's P90 —
                    // shape carries the class, not colour alone.
                    for ((index, mark) in markers.withIndex()) {
                        val hue = if (mark.alarmClass) colors.crit else colors.warn
                        if (mark.alarmClass) drawPath(mark.path, hue)
                        else drawPath(mark.path, hue, style = markerStroke)
                        markerCounts[index]?.let { label ->
                            drawText(
                                textLayoutResult = label,
                                color = hue,
                                topLeft = Offset(
                                    (mark.x + markerSize * 0.75f)
                                        .coerceAtMost(widthPx - label.size.width),
                                    // По центру треугольника: прижатое к его
                                    // верхушке число читалось как надстрочный
                                    // знак, а не как счётчик событий.
                                    plotTop - 1f - markerSize / 2f - label.size.height / 2f,
                                ),
                            )
                        }
                    }
                    endpoint?.let {
                        drawCircle(endpointColor, endpointRadius, it)
                        drawCircle(colors.bg, endpointRadius, it, style = ringStroke)
                    }
                    // Значение последней точки — плашкой у правого края, на
                    // высоте самой точки. Плашка непрозрачна: подпись поверх
                    // конверта иначе читается как часть данных.
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
            // Полоса разброса рвётся ТАМ ЖЕ, где линия: конверт, протянутый
            // через пропуск, — это утверждение о разбросе измерений, которых
            // не было, и выглядит оно убедительнее самой линии.
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
 * Подробный ряд: линия по крайним значениям колонок.
 *
 * В каждой колонке перо проходит от её максимума к минимуму, а к следующей
 * колонке идёт от того значения, на котором остановилось. Когда колонка
 * узкая — а в подробном виде она равна видимому окну, делённому на число
 * колонок, — минимум и максимум совпадают с самим измерением, и получается
 * линия ровно по измерениям. Когда колонка широкая, это прореживание,
 * сохраняющее форму: пик и провал внутри колонки остаются на картинке, а не
 * усредняются в ровную линию. Простое «каждое N-е измерение» именно их и
 * теряет — то есть ровно то, ради чего график открывают.
 */
private fun detailPath(pixels: ChartPixels): Path {
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

private fun linePath(pixels: ChartPixels): Path {
    val path = Path()
    var penDown = false
    for (i in 0 until pixels.count) {
        if (!pixels.plottable[i]) {
            penDown = false
            continue
        }
        // Пустые колонки в снимок не попадают вовсе, поэтому соседство по
        // индексу не означает соседства во времени: перо поднимается по
        // ВРЕМЕННОМУ разрыву.
        if (pixels.segmentStart[i]) penDown = false
        if (penDown) path.lineTo(pixels.x[i], pixels.medianY[i])
        else path.moveTo(pixels.x[i], pixels.medianY[i])
        penDown = true
    }
    return path
}

/**
 * Колонки, стоящие в одиночестве между двумя пропусками.
 *
 * Отрезок из одной точки не рисуется НИЧЕМ: `moveTo` без `lineTo` не даёт
 * пикселей. На минутном окне это выглядело как обрыв графика при живом
 * потоке — линия кончалась в середине поля, а справа висела плашка со
 * значением, к которой ничего не вело. Одиночное измерение — это точка, и
 * рисовать её надо точкой.
 */
private fun lonePoints(pixels: ChartPixels): List<Offset> {
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
 * **Инженерный параметр**: три размера. Полутора хватало самим треугольникам,
 * но у группы справа стоит счётчик, и он налезал на следующий маркер —
 * получалось «△2△», где число читается как часть соседа. Шаг считается по
 * ПОЛНОЙ ширине маркера со счётчиком, а не по одному треугольнику.
 */
private const val MARKER_SPACING_FACTOR = 3f

private fun extremeMarks(
    spec: DoseChartSpec,
    pixels: ChartPixels,
    plotTop: Float,
    sizePx: Float,
): List<ExtremeMark> {
    if (spec.extremeMarkers.isEmpty()) return emptyList()
    val positions = ArrayList<Pair<Float, Boolean>>(spec.extremeMarkers.size)
    for (marker in spec.extremeMarkers) {
        val k = pixels.indexOfBucket(marker.bucketIndex) ?: continue
        positions += pixels.x[k] to (marker.reference == DoseReference.ALARM_L1)
    }
    // Слипшиеся маркеры собираются в один с числом: стена почти наложенных
    // треугольников перестаёт указывать на что-либо конкретное.
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

/**
 * Ниже этой скорости бросок не считается броском, px/с.
 * **Инженерный параметр**: медленное отпускание пальца — это остановка, и
 * доезжать после неё значит не слушаться руки.
 */
private const val MIN_FLING_VELOCITY = 200f

/** Трение затухания: больше — короче выбег. */
private const val FLING_FRICTION = 1.6f
