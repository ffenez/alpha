package app.alpha.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.alpha.AppGraph
import app.alpha.analysis.CalibrationDataset
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.RadonTrend
import app.alpha.analysis.evidence.BackgroundCalibration
import app.alpha.analysis.evidence.CalibrationAccumulation
import app.alpha.analysis.evidence.CalibrationReport
import app.alpha.analysis.evidence.SqrtResolution
import app.alpha.analysis.evidence.AcceptedResolution
import app.alpha.analysis.evidence.ResolutionFitOutcome
import app.alpha.analysis.evidence.ResolutionModel
import app.alpha.data.toSpectrum
import app.alpha.device.ConnectionState
import app.alpha.ui.components.ExplainInfoButton
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.Card
import app.alpha.ui.components.ChartNotesDialog
import app.alpha.ui.components.Chip
import app.alpha.ui.logic.CalibrationChart
import app.alpha.ui.logic.CalibrationView
import app.alpha.ui.text.CalibrationCatalogue
import app.alpha.ui.text.CalibrationStrings
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import app.alpha.ui.theme.chartField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Что экран показывает: собранный материал и разбор движка по нему.
 */
@Immutable
data class CalibrationModel(
    val selection: CalibrationDataset.Selection,
    val report: CalibrationReport,
    /** Разрешение прибора, от которого стартовал поиск линий. */
    val startResolution662: Float,
    val deviceSerial: String?,
)

/**
 * Сколько истории читается под диагностику — **инженерный параметр экрана**.
 * Тридцать суток при одном снимке в час это 720 строк: столько же, сколько
 * читает месячное окно графика (ADR 004), то есть заведомо посильный объём.
 * Длиннее не нужно — калибровка прибора за месяцы меняется, и сумма за
 * полгода описывала бы уже не тот прибор, что сейчас в руках.
 */
/** Шагов при отрисовке кривой: 25 кэВ на шаг при шкале 3000 кэВ. */
private const val CURVE_STEPS = 120

private const val WINDOW_DAYS = 30

/**
 * Читает уже накопленные снимки, складывает из них два накопления и отдаёт
 * разбор. Ничего не пишет.
 */
suspend fun loadCalibration(graph: AppGraph): CalibrationModel {
    val now = System.currentTimeMillis()
    val from = now - WINDOW_DAYS * 24L * RadonTrend.HOUR_MILLIS
    // Прореживание до одного снимка в час не теряет ни импульса: разность
    // последних снимков соседних часов покрывает час целиком.
    val metas = graph.measurementRepository
        .deviceSnapshotMeta(from - RadonTrend.HOUR_MILLIS, now)
        .map { RadonTrend.Meta(it.id, it.timestamp, it.durationSeconds) }
    val snapshots = RadonTrend.selectHourlyIds(metas).mapNotNull { id ->
        graph.measurementRepository.spectrumById(id)?.let { entity ->
            val s = entity.toSpectrum()
            RadonTrend.Snapshot(
                timestampMillis = entity.timestamp,
                durationSeconds = s.durationSeconds,
                counts = s.counts,
                calibration = EnergyCalibration(s.a0, s.a1, s.a2),
            )
        }
    }
    val selection = CalibrationDataset.select(CalibrationDataset.intervals(snapshots))
    val connected = graph.serviceStatus.connection.value as? ConnectionState.Connected
    val resolution662 = connected?.info?.model?.peakResolution662
        ?: app.alpha.analysis.PeakDetection.RESOLUTION_662
    val accumulations = buildList {
        selection.long?.let { add(it.toEngine(CalibrationDataset.SOURCE_LONG)) }
        selection.radonRich?.let { add(it.toEngine(CalibrationDataset.SOURCE_RADON)) }
    }
    return CalibrationModel(
        selection = selection,
        report = BackgroundCalibration.analyse(
            accumulations = accumulations,
            // Стартовая модель задаёт только РАЗМЕР окон поиска; измеренные
            // ширины от неё не зависят, иначе подгонка была бы тавтологией.
            startResolution = SqrtResolution(resolution662.toDouble()),
        ),
        startResolution662 = resolution662,
        deviceSerial = connected?.info?.serialNumber,
    )
}

/**
 * «Калибровка (диагностика)» — Настройки → Прибор.
 *
 * Экран отвечает на один вопрос: что приложение САМО измерило об этом приборе
 * по природному фону. Он ничего не просит сделать и ничего не предлагает
 * ввести: все числа берутся из уже накопленных снимков.
 */
@Composable
fun CalibrationScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val s = CalibrationCatalogue.of(strings.language)
    val h = HistoryCatalogue.of(strings.language)
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    var model by remember { mutableStateOf<CalibrationModel?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        model = withContext(Dispatchers.IO) { loadCalibration(graph) }
        loaded = true
    }
    val acceptedRaw by graph.settings.measuredResolutionRaw.collectAsState(initial = null)
    val accepted = remember(acceptedRaw) { AcceptedResolution.decode(acceptedRaw) }

    // Без собственной прокрутки: экран живёт внутри прокручиваемой колонки
    // Настроек, и вложенный verticalScroll получает бесконечную высоту —
    // Compose падает с «Vertically scrollable component was measured with an
    // infinity maximum height constraints». Прокручивает родитель.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.space1),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        // Своей кнопки «назад» здесь НЕТ: экран живёт внутри Настроек, у
        // которых она уже есть в шапке, и две одинаковые кнопки подряд
        // заставляли гадать, чем они отличаются. Системный жест по-прежнему
        // закрывает сначала этот экран — за это отвечает BackHandler выше.
        Text(text = s.screenTitle, style = type.title, color = colors.ink)
        Hint(text = s.intro, style = type.bodySmall, color = colors.ink2)

        val m = model
        when {
            !loaded -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = s.readingMaterial, style = type.bodySmall, color = colors.muted)
            }
            m == null || m.selection.long == null -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(text = s.noMaterial, style = type.bodySmall, color = colors.ink2)
                    Hint(
                        text = s.noMaterialExplained,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                }
            }
            else -> CalibrationContent(
                model = m,
                accepted = accepted,
                s = s,
                h = h,
                onAccept = { record ->
                    scope.launch { graph.settings.setMeasuredResolutionRaw(record.encode()) }
                },
                onRevert = {
                    scope.launch { graph.settings.setMeasuredResolutionRaw(null) }
                },
            )
        }
    }
}

// internal, а не private: Robolectric-регрессия рисует карточку с моделью
// разрешения из NaN-коэффициентов — тем самым входом, который в поле ронял
// канву (см. app/src/test/.../smoke/CalibrationNanRegressionTest).
@Composable
internal fun CalibrationContent(
    model: CalibrationModel,
    accepted: AcceptedResolution?,
    s: CalibrationStrings,
    h: HistoryStrings,
    onAccept: (AcceptedResolution) -> Unit,
    onRevert: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    Section(s.materialTitle) {
        for (row in CalibrationView.material(model.selection, s, h)) {
            Text(text = row, style = type.valueSmall, color = colors.ink)
        }
        Hint(text = s.radonExplained)
        Text(text = s.materialCollected, style = type.footnote, color = colors.muted)
    }

    Section(s.linesTitle) {
        val rows = CalibrationView.lineRows(model.report, s)
        if (rows.isEmpty()) {
            Text(text = s.noLinesFound, style = type.bodySmall, color = colors.ink2)
        } else {
            LineHeader(s)
            for (row in rows) LineRow(row)
            Text(
                text = "${s.colTable} · ${s.colObserved} · ${s.colWidth} — ${s.unitKeV}",
                style = type.footnote,
                color = colors.muted,
            )
        }
        CalibrationView.notFound(model.report, s)?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }
        blendNote(model.report, s)?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }
    }

    Section(s.rejectedTitle) {
        for (row in CalibrationView.rejected(model.report, s)) {
            Text(text = row, style = type.footnote, color = colors.muted)
        }
    }

    ResolutionSection(model, accepted, s, onAccept, onRevert)

    Section(s.scaleTitle) {
        for (row in CalibrationView.scale(model.report, s)) {
            Text(text = row, style = type.valueSmall, color = colors.ink)
        }
        Text(text = s.noCorrection, style = type.footnote, color = colors.muted)
    }

    Section(s.responseTitle) {
        for (row in CalibrationView.response(model.report, s)) {
            Text(text = row, style = type.valueSmall, color = colors.ink)
        }
        Hint(text = s.responseWhy)
        Text(text = s.responseCaveat, style = type.footnote, color = colors.warn)
        Text(text = s.responsePointGeometry)
    }

    val missing = CalibrationView.missing(model.report, s, h)
    if (missing.isNotEmpty()) {
        Section(s.missingTitle) {
            for (row in missing) {
                Text(text = row, style = type.bodySmall, color = colors.ink2)
            }
        }
    }
}

@Composable
private fun ResolutionSection(
    model: CalibrationModel,
    accepted: AcceptedResolution?,
    s: CalibrationStrings,
    onAccept: (AcceptedResolution) -> Unit,
    onRevert: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val fit = (model.report.fit as? ResolutionFitOutcome.Fitted)?.fit

    Section(s.resolutionTitle, note = s.extrapolationNote) {
        for (row in CalibrationView.resolution(model.report, s)) {
            Text(text = row, style = type.valueSmall, color = colors.ink)
        }
        // Модель может не построиться из сохранённого результата подгонки
        // (не-числа или отрицательный свободный член отвергаются
        // конструктором MeasuredResolution). Экран отказывается от кривой, а
        // не роняет композицию (смоук CalibrationNanRegressionTest).
        val fittedModel = fit?.let { runCatching { it.model() }.getOrNull() }
        if (fit != null && fittedModel != null) {
            ResolutionChart(
                fitted = fittedModel,
                approximation = SqrtResolution(model.startResolution662.toDouble()),
                points = model.report.measurements.map {
                    it.line.energyKeV to it.fwhmKeV
                },
                measuredFromKeV = fit.extrapolatedBelowKeV,
                measuredToKeV = fit.extrapolatedAboveKeV,
                s = s,
            )
        }

        // Состояние: что действует ПРЯМО СЕЙЧАС — до кнопок, потому что это
        // ответ на вопрос, с которым сюда приходят.
        val state = when {
            accepted == null -> s.approximationState
            model.deviceSerial != null && accepted.deviceSerial != null &&
                model.deviceSerial != accepted.deviceSerial ->
                s.otherDevice(accepted.deviceSerial)
            else -> s.acceptedState(
                date = app.alpha.ui.logic.HistoryFormat.day(accepted.acceptedAtMillis),
                points = accepted.points,
            )
        }
        Text(text = state, style = type.bodySmall, color = colors.ink2)
        Hint(text = s.acceptedNote)

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            if (fit != null) {
                AppButton(
                    text = s.accept,
                    onClick = {
                        onAccept(
                            AcceptedResolution(
                                a = fit.a,
                                b = fit.b,
                                c = fit.c,
                                deviceSerial = model.deviceSerial,
                                acceptedAtMillis = System.currentTimeMillis(),
                                points = fit.points.size,
                                lowestKeV = fit.extrapolatedBelowKeV,
                                highestKeV = fit.extrapolatedAboveKeV,
                                algorithmVersion = CalibrationView.ALGORITHM_VERSION,
                            ),
                        )
                    },
                )
            }
            if (accepted != null) AppButton(text = s.revert, onClick = onRevert)
        }
    }
}

/**
 * FWHM(E): измеренные точки, подогнанная кривая и прежнее приближение.
 *
 * Область, где кривая ЭКСТРАПОЛИРУЕТСЯ, залита той же краской
 * `chartBeyondData`, что «сюда данные не доходят» на графике дозы: одно и то
 * же значение обязано выглядеть одинаково на всех экранах.
 */
@Composable
private fun ResolutionChart(
    fitted: ResolutionModel,
    approximation: ResolutionModel,
    points: List<Pair<Double, Double>>,
    measuredFromKeV: Double,
    measuredToKeV: Double,
    s: CalibrationStrings,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val maxEnergy = 3000.0
    // Верх шкалы считает чистая функция: подгонка может вернуть неопределённые
    // коэффициенты, и `NaN` в координате роняет канву УЖЕ НА ТЕЛЕФОНЕ — в
    // модульных тестах Canvas не выполняется (тот же урок, что с разбором XML).
    val axisTop = CalibrationChart.axisTop(
        fittedAtTop = fitted.fwhmKeV(maxEnergy),
        approximationAtTop = approximation.fwhmKeV(maxEnergy),
        measuredWidths = points.map { it.second },
    )
    if (axisTop == null) {
        // Рисовать нечего — и это ответ, а не пустое поле без объяснения.
        Text(text = s.chartUnavailable, style = type.footnote, color = colors.muted)
        return
    }

    val approximationCurve = CalibrationChart.curveFractions(maxEnergy, axisTop, CURVE_STEPS) {
        approximation.fwhmKeV(it)
    }
    val fittedCurve = CalibrationChart.curveFractions(maxEnergy, axisTop, CURVE_STEPS) {
        fitted.fwhmKeV(it)
    }
    val bands = CalibrationChart.extrapolationBands(measuredFromKeV, measuredToKeV, maxEnergy)
    val measuredPoints = points.mapNotNull { (energy, width) ->
        val x = CalibrationChart.fraction(energy, maxEnergy) ?: return@mapNotNull null
        val y = CalibrationChart.fraction(width, axisTop) ?: return@mapNotNull null
        x to y
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .chartField()
            .padding(Dimens.space2),
    ) {
        fun px(fraction: Float) = fraction * size.width
        fun py(fraction: Float) = size.height - fraction * size.height

        bands?.let { (left, right) ->
            if (left > 0f) {
                drawRect(
                    color = colors.chartBeyondData,
                    topLeft = Offset.Zero,
                    size = Size(px(left), size.height),
                )
            }
            if (right > 0f) {
                drawRect(
                    color = colors.chartBeyondData,
                    topLeft = Offset(size.width - px(right), 0f),
                    size = Size(px(right), size.height),
                )
            }
        }

        fun path(curve: List<Pair<Float, Float>>): Path {
            val result = Path()
            curve.forEachIndexed { index, (x, y) ->
                if (index == 0) result.moveTo(px(x), py(y)) else result.lineTo(px(x), py(y))
            }
            return result
        }

        if (approximationCurve.isNotEmpty()) {
            drawPath(
                path = path(approximationCurve),
                color = colors.muted,
                style = Stroke(width = 1.5f, cap = StrokeCap.Round),
            )
        }
        if (fittedCurve.isNotEmpty()) {
            drawPath(
                path = path(fittedCurve),
                color = colors.data,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round),
            )
        }
        for ((x, y) in measuredPoints) {
            drawCircle(color = colors.data, radius = 4f, center = Offset(px(x), py(y)))
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(s.axisEnergy, style = type.axis, color = colors.muted)
        Spacer(Modifier.weight(1f))
        Text(s.axisFwhm, style = type.axis, color = colors.muted)
    }
    Text(
        text = "${s.legendMeasured} · ${s.legendFitted} · ${s.legendApproximation} · " +
            s.legendExtrapolated,
        style = type.footnote,
        color = colors.muted,
    )
}

/** Карточка раздела: заголовок + содержимое одной колонкой. */
@Composable
private fun Section(title: String, note: String? = null, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    // Оговорка раздела живёт под «i»: она объясняет, ЧЕМУ верить на картинке
    // (где ширина измерена, а где продолжена), и это ответ на вопрос, а не
    // строка, которую читают каждый раз.
    var info by remember { mutableStateOf(false) }
    if (info && note != null) {
        ChartNotesDialog(notes = listOf(note)) { info = false }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                if (note != null) {
                    ExplainInfoButton(onClick = { info = true })
                }
            }
            content()
        }
    }
}

@Composable
private fun LineHeader(s: CalibrationStrings) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(s.colLine, style = type.footnote, color = colors.muted, modifier = Modifier.weight(1.2f))
        Text(s.colTable, style = type.footnote, color = colors.muted, modifier = Modifier.weight(1f))
        Text(s.colDelta, style = type.footnote, color = colors.muted, modifier = Modifier.weight(1f))
        Text(s.colWidth, style = type.footnote, color = colors.muted, modifier = Modifier.weight(1f))
        Text(
            s.colSignificance,
            style = type.footnote,
            color = colors.muted,
            modifier = Modifier.weight(0.8f),
        )
    }
}

@Composable
private fun LineRow(row: app.alpha.ui.logic.CalibrationLineRow) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1.2f)) {
            Text(row.nuclide, style = type.valueSmall, color = colors.ink)
            Text(row.source, style = type.footnote, color = colors.muted)
        }
        Text(
            row.tableKeV,
            style = type.footnoteMono,
            color = colors.ink2,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.deltaKeV,
            style = type.footnoteMono,
            // Остаток крупнее двух своих σ — единственное место таблицы, где
            // цвет что-то значит: он показывает, где шкала расходится заметно.
            color = if (row.deltaStandsOut) colors.warn else colors.ink2,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.widthKeV,
            style = type.footnoteMono,
            color = colors.ink2,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.significance,
            style = type.footnoteMono,
            color = colors.muted,
            modifier = Modifier.weight(0.8f),
        )
    }
}

/** Оговорка о предсказанном вкладе соседей — только у линий, где он есть. */
private fun blendNote(report: CalibrationReport, s: CalibrationStrings): String? {
    val blended = report.measurements
        .filter { kotlin.math.abs(it.blendBiasKeV) >= 0.5 }
        .maxByOrNull { kotlin.math.abs(it.blendBiasKeV) } ?: return null
    return s.blendNote(
        line = CalibrationView.number(blended.line.energyKeV, 1),
        shift = CalibrationView.signed(blended.blendBiasKeV, 1) + " " + s.unitKeV,
        nuclide = blended.line.nuclide,
    )
}

private fun CalibrationDataset.Accumulation.toEngine(id: String) = CalibrationAccumulation(
    id = id,
    counts = counts,
    calibration = calibration,
    seconds = seconds,
    intervalCount = intervalCount,
    hoursCovered = hoursCovered,
    fromMillis = fromMillis,
    toMillis = toMillis,
)
