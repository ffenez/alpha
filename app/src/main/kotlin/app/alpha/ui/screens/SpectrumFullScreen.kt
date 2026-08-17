package app.alpha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.alpha.AppGraph
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.Peak
import app.alpha.analysis.PeakDetection
import app.alpha.analysis.SpectrumDisplay
import app.alpha.analysis.SpectrumEdge
import app.alpha.device.ConnectionState
import app.alpha.data.toSpectrum
import app.alpha.protocol.Spectrum
import app.alpha.ui.components.Card
import app.alpha.ui.components.ChartSheet
import app.alpha.ui.components.Chip
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.SpectrumChart
import app.alpha.ui.components.SpectrumChartSpec
import app.alpha.ui.components.SpectrumLineMark
import app.alpha.ui.components.SpectrumPeakMark
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.PeakEvidenceBridge
import app.alpha.ui.logic.SpectrumHighlight
import app.alpha.ui.logic.SpectrumFormat
import app.alpha.ui.logic.SpectrumFrames
import app.alpha.ui.logic.SpectrumPlot
import app.alpha.ui.logic.SpectrumScale
import app.alpha.ui.logic.SpectrumSources
import app.alpha.ui.logic.SpectrumViewOptions
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SpectrumCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Полноэкранный спектр — тот же режим просмотра, что у полноэкранного графика
 * ([LiveChartScreen]), и по тем же правилам.
 *
 * **Поле владеет экраном.** Спектр рисуется от края до края поверх таб-бара,
 * оси подписаны ВНУТРИ поля, а всё вторичное живёт панелями поверх
 * ([ChartSheet]): постоянных полос мелкого текста под полем нет ни одной — их
 * читают один раз, а высоту они забирают всегда. Спектр рассматривают дольше
 * всех остальных картинок приложения, поэтому именно ему нужна высота.
 *
 * **Курсор говорит о КАНАЛЕ.** Долгое нажатие ставит курсор, перетаскивание
 * ведёт его, одиночное касание снимает. Карточка называет энергию, номер
 * канала и сырой счёт в нём — не «имп/кэВ»: ширина канала по шкале меняется
 * (то же правило, что у подписи оси). Если центр найденного пика попал в ту
 * же колонку, рядом стоят его значимость и измеренная ширина.
 *
 * **Картинка не подменяется входом.** Режим («− фон»), сглаживание и окно
 * зума приходят с вкладки [SpectrumViewOptions]: человек тапнул по тому, что
 * видел, и увидеть обязан то же самое, только крупнее.
 *
 * Экран не гаснет, пока он открыт, — на спектр смотрят, а не листают его.
 */
@Composable
fun SpectrumFullScreen(
    graph: AppGraph,
    spectrum: Spectrum,
    options: SpectrumViewOptions,
    /** Снимок из Истории: прибор, которым он снят, не записан. */
    viewingSnapshot: Boolean,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    val type = LocalAppTypography.current

    // Пока открыт полный экран, дисплей не гаснет: системный таймаут гасил его
    // ровно посреди разглядывания пика. Флаг живёт на View этого экрана и
    // снимается, как только экран закрыт, — никогда на приложение целиком.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val scaleId by graph.settings.spectrumScaleId.collectAsState(initial = SpectrumScale.Log.id)
    val scaleRoot by graph.settings.spectrumScaleRoot.collectAsState(initial = 2)
    val scale = remember(scaleId, scaleRoot) { SpectrumScale.of(scaleId, scaleRoot) }

    val backgroundEntity by graph.measurementRepository.backgroundReference()
        .collectAsState(initial = null)
    val background = remember(backgroundEntity) { backgroundEntity?.toSpectrum() }
    val subtractOn = options.minusBackground && background != null

    val calibration = remember(spectrum.a0, spectrum.a1, spectrum.a2) {
        EnergyCalibration(spectrum.a0, spectrum.a1, spectrum.a2)
    }
    // Крайний канал не нарисован и не выброшен молча: число живёт в
    // технических данных справки, как и на вкладке.
    val edgeCounts = remember(spectrum) { SpectrumEdge.edgeCounts(spectrum.counts) }
    val connection by graph.serviceStatus.connection.collectAsState()
    val model = SpectrumSources.analysisModel(
        connectedModel = (connection as? ConnectionState.Connected)?.info?.model,
        viewingSnapshot = viewingSnapshot,
    )

    var window by remember { mutableStateOf(options.window()) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    var cursorActive by rememberSaveable { mutableStateOf(false) }
    // Положение курсора живёт в своём State: его читает слой рисования и
    // карточка, поэтому скраб не пересобирает экран.
    val cursorFraction = remember { mutableStateOf<Float?>(null) }

    val frame = remember(spectrum, background, subtractOn, options.smoothing, window, scale) {
        SpectrumFrames.build(
            counts = spectrum.counts,
            durationSeconds = spectrum.durationSeconds,
            calibration = calibration,
            background = background?.counts,
            backgroundSeconds = background?.durationSeconds ?: 0L,
            window = window,
            subtract = subtractOn,
            smoothing = options.smoothing,
            scale = scale,
        )
    }
    // Пики считаются по СЫРЫМ импульсам — тем же путём и тем же разрешением,
    // что и в таблице на вкладке.
    val peaks = remember(spectrum, calibration, model) {
        if (model.isSpectrometer &&
            spectrum.durationSeconds >= PeakEvidenceBridge.MIN_ANALYSIS_SECONDS
        ) {
            PeakDetection.detect(
                counts = spectrum.counts,
                calibration = calibration,
                resolution662 = model.peakResolution662,
                minEnergyKeV = model.peakFloorKeV,
            ).sortedBy { it.energyKeV }
        } else {
            emptyList()
        }
    }
    // Отметка линии приезжает с вкладки вместе с режимом и окном: её поставили,
    // чтобы рассмотреть место линии, и полный экран открывают ровно за этим.
    // Время жизни отсчитывается заново — это новое поле, и просьба свежая.
    var lineMark by remember {
        mutableStateOf(
            options.highlight()?.let { energyKeV ->
                SpectrumHighlight.Mark(
                    energyKeV = energyKeV,
                    anchor = SpectrumHighlight.anchor(
                        spectrumKey = SpectrumHighlight.spectrumKey(
                            calibration,
                            spectrum.counts.size,
                        ),
                        scaleId = scale.id,
                        window = frame.visible,
                    ),
                    shownAtMillis = System.currentTimeMillis(),
                    outcome = SpectrumHighlight.Aim.VISIBLE,
                )
            },
        )
    }
    val markAnchor = SpectrumHighlight.anchor(
        spectrumKey = SpectrumHighlight.spectrumKey(calibration, spectrum.counts.size),
        scaleId = scale.id,
        window = frame.visible,
    )
    LaunchedEffect(lineMark, markAnchor) {
        val mark = lineMark ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (!SpectrumHighlight.alive(mark, markAnchor, now)) {
            lineMark = null
            return@LaunchedEffect
        }
        delay(SpectrumHighlight.remainingMillis(mark, now))
        lineMark = null
    }
    val aliveMark = lineMark?.takeIf {
        SpectrumHighlight.alive(it, markAnchor, System.currentTimeMillis())
    }
    val markFraction = aliveMark?.let { mark ->
        SpectrumHighlight.fraction(
            energyKeV = mark.energyKeV,
            calibration = calibration,
            channels = frame.channels,
            columnCount = frame.columnCount,
        )
    }
    val peakMarks = remember(peaks, frame.channels, frame.columnCount) {
        peaks.mapNotNull { peak ->
            val column = SpectrumDisplay.columnForChannel(
                peak.channel,
                frame.channels,
                frame.columnCount,
            ) ?: return@mapNotNull null
            SpectrumPeakMark(columnIndex = column, label = "${peak.energyKeV.roundToInt()}")
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.space2, vertical = Dimens.space1),
            ) {
                Chip(text = "✕", color = colors.ink2, onClick = onBack)
                // Название экрана не повторяется; остаётся только состояние —
                // что показан сохранённый снимок, а не живое накопление.
                if (viewingSnapshot) {
                    Text(
                        text = t.snapshotViewTag,
                        style = type.label,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtractOn) {
                    Chip(text = t.legendMinusBackground, color = colors.dataText)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = SpectrumFormat.accumulationChip(
                        spectrum.durationSeconds,
                        spectrum.counts.sumOf { it.toLong() },
                        t,
                    ),
                    style = type.footnoteMono,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Chip(text = "i", color = colors.ink2, onClick = { infoOpen = true })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SpectrumChart(
                    spec = SpectrumChartSpec(
                        columns = frame.columns,
                        overlay = frame.overlay,
                        scale = scale,
                        yTop = frame.yTop,
                        peaks = peakMarks,
                        energyTicks = SpectrumDisplay.energyTicks(frame.visible),
                        lineMark = markFraction?.let { fraction ->
                            SpectrumLineMark(
                                fraction = fraction,
                                label = t.lineMarkLabel(
                                    SpectrumFormat.energyCell(aliveMark!!.energyKeV),
                                ),
                            )
                        },
                    ),
                    modifier = Modifier.fillMaxSize(),
                    // Высоту задаёт поле, а не компонент: полноэкранный режим —
                    // это режим ПРОСМОТРА, поле здесь единственный вес.
                    height = null,
                    onGesture = { factor, pan, focus ->
                        var next = SpectrumDisplay.pinch(frame.visible, frame.full, factor, focus)
                        next = SpectrumDisplay.pan(next, frame.full, pan)
                        window = next
                    },
                    cursorFraction = cursorFraction,
                    cursorActive = cursorActive,
                    onCursorFraction = { fraction ->
                        cursorActive = true
                        cursorFraction.value = fraction
                    },
                    // Одиночное касание убирает всё, что положено поверх поля:
                    // и курсор, и отметку линии — «тап в стороне» не должен
                    // работать выборочно.
                    onCursorDismiss = {
                        cursorActive = false
                        cursorFraction.value = null
                        lineMark = null
                    },
                    onResetZoom = {
                        window = null
                        cursorActive = false
                        cursorFraction.value = null
                        lineMark = null
                    },
                )
                // Отметка объясняет себя панелью у нижнего края поля: у
                // верхнего стоит карточка курсора, и две карточки не должны
                // спорить за одно место.
                if (markFraction != null) {
                    LineMarkNote(outcome = aliveMark!!.outcome)
                }
                CursorCard(
                    cursorFraction = cursorFraction,
                    frame = frame,
                    counts = spectrum.counts,
                    calibration = calibration,
                    peaks = peaks,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.space2, vertical = Dimens.space1),
            ) {
                ScaleSegment(
                    graph = graph,
                    scale = scale,
                    scaleRoot = scaleRoot,
                    modifier = Modifier.weight(1f),
                )
                // Легенды под полем нет: фон включает сам человек, и чип
                // режима стоит в шапке — под графиком строка повторяла его.
            }
            // Ползунок степени — своей строкой и только в своём режиме: втроём
            // с сегментом в одной строке ему остаётся полоска, на которой шаг
            // не выбрать пальцем.
            if (scale is SpectrumScale.Power) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.space2)
                        .padding(bottom = Dimens.space1),
                ) {
                    Text(
                        text = strings.powerDegree(scaleRoot),
                        style = type.footnote,
                        color = colors.ink2,
                    )
                    PowerRootSlider(
                        graph = graph,
                        scaleRoot = scaleRoot,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        ChartSheet(
            open = infoOpen,
            title = t.infoTitle,
            onClose = { infoOpen = false },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier.padding(horizontal = Dimens.space3),
            ) {
                SpectrumInfoLines(
                    calibrationLine = SpectrumFormat.calibrationLine(
                        spectrum.a0,
                        spectrum.a1,
                        spectrum.a2,
                        spectrum.counts.size,
                        t,
                    ),
                    edgeLine = edgeCounts.takeIf { it > 0 }?.let {
                        strings.edgeCounts(
                            HistoryFormat.count(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                        )
                    },
                    withCursor = true,
                )
            }
        }
    }
}

/**
 * Подпись отметки линии: пунктир на поле обязан объяснить, ЧТО он означает.
 *
 * Панель временная — она живёт ровно столько же, сколько сама отметка, и стоит
 * у нижнего края поля, рядом с осью энергии, о которой и говорит.
 */
@Composable
private fun BoxScope.LineMarkNote(outcome: SpectrumHighlight.Aim) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = SpectrumCatalogue.of(LocalStrings.current.language)
    Card(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(Dimens.space2),
        contentPadding = Dimens.space2,
    ) {
        Text(
            text = if (outcome == SpectrumHighlight.Aim.MOVED) {
                "${t.lineMarkNote} ${t.lineMarkWindowMoved}"
            } else {
                t.lineMarkNote
            },
            style = type.footnote,
            color = colors.ink2,
        )
    }
}

/**
 * Карточка курсора: что за точка спектра под пальцем.
 *
 * Число импульсов — СЫРОЙ счёт канала: сглаживание и «− фон» это способы
 * посмотреть, а прибор зарегистрировал именно его. По тем же сырым импульсам
 * посчитан пик, поэтому значимость рядом относится к тем же данным.
 *
 * Карточка уходит на противоположную от курсора сторону — иначе она
 * закрывала бы ровно то место, на которое смотрят.
 */
@Composable
private fun BoxScope.CursorCard(
    cursorFraction: State<Float?>,
    frame: SpectrumFrames.Frame,
    counts: List<Int>,
    calibration: EnergyCalibration,
    peaks: List<Peak>,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = SpectrumCatalogue.of(LocalStrings.current.language)
    val fraction = cursorFraction.value ?: return
    val readout = SpectrumPlot.readout(
        fraction = fraction,
        range = frame.channels,
        columnCount = frame.columnCount,
        counts = counts,
        calibration = calibration,
        peaks = peaks,
    ) ?: return
    Card(
        modifier = Modifier
            .align(if (fraction < 0.5f) Alignment.TopEnd else Alignment.TopStart)
            .padding(Dimens.space2),
        contentPadding = Dimens.space2,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = t.cursorEnergy(SpectrumFormat.energyCell(readout.energyKeV)),
                style = type.value,
                color = colors.ink,
            )
            Text(
                text = t.cursorChannel(readout.channel),
                style = type.footnoteMono,
                color = colors.ink2,
            )
            Text(
                text = t.cursorCounts(SpectrumFormat.groupThousands(readout.counts.toLong())),
                style = type.footnoteMono,
                color = colors.ink2,
            )
            if (readout.merged) {
                Text(
                    text = t.cursorMergedChannels(
                        readout.channels.first,
                        readout.channels.last,
                    ),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            readout.peak?.let { peak ->
                val significance = SpectrumFormat.significanceCell(peak.significance)
                Text(
                    text = peak.fwhmKeV?.let { width ->
                        t.cursorPeak(significance, "${width.roundToInt()}")
                    } ?: t.cursorPeakNoWidth(significance),
                    style = type.footnote,
                    color = colors.dataText,
                )
            }
        }
    }
}

/**
 * Переключатель масштаба оси — один на вкладку и на полный экран: выбор
 * запоминается настройкой, и обе картинки обязаны показывать одно.
 */
@Composable
internal fun ScaleSegment(
    graph: AppGraph,
    scale: SpectrumScale,
    scaleRoot: Int,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    Segmented(
        options = listOf(strings.scaleLinear, strings.scalePower, strings.scaleLog),
        selectedIndex = when (scale) {
            SpectrumScale.Linear -> 0
            is SpectrumScale.Power -> 1
            SpectrumScale.Log -> 2
        },
        onSelect = { index ->
            scope.launch {
                graph.settings.setSpectrumScale(
                    when (index) {
                        0 -> SpectrumScale.Linear.id
                        1 -> SpectrumScale.Power(scaleRoot).id
                        else -> SpectrumScale.Log.id
                    },
                )
            }
        },
        modifier = modifier,
    )
}

/**
 * Ползунок степени: 1/1 совпадает с линейным, 1/2 — привычный в
 * гамма-спектрометрии корень, дальше вид приближается к логарифму, не
 * становясь им.
 */
@Composable
internal fun PowerRootSlider(
    graph: AppGraph,
    scaleRoot: Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Slider(
        value = scaleRoot.toFloat(),
        onValueChange = { value -> scope.launch { graph.settings.setSpectrumScaleRoot(value.roundToInt()) } },
        valueRange = SpectrumScale.MIN_ROOT.toFloat()..SpectrumScale.MAX_ROOT.toFloat(),
        steps = SpectrumScale.MAX_ROOT - SpectrumScale.MIN_ROOT - 1,
        modifier = modifier,
    )
}
