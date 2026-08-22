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
import app.alpha.analysis.ScaleCorrection
import app.alpha.analysis.ScaleCorrectionMath
import app.alpha.analysis.evidence.BackgroundCalibration
import app.alpha.analysis.evidence.CalibrationAccumulation
import app.alpha.analysis.evidence.CalibrationReport
import app.alpha.analysis.evidence.SqrtResolution
import app.alpha.analysis.evidence.AcceptedResolution
import app.alpha.data.CalibrationModel
import app.alpha.data.GainDriftRecord
import app.alpha.data.loadCalibration
import app.alpha.analysis.evidence.ResolutionFitOutcome
import app.alpha.analysis.evidence.ResolutionModel
import app.alpha.data.toSpectrum
import app.alpha.device.ConnectionState
import app.alpha.ui.components.Explain
import app.alpha.ui.components.ExplainInfoButton
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.Card
import app.alpha.ui.components.ChartNotesDialog
import app.alpha.ui.components.Chip
import app.alpha.ui.logic.CalibrationChart
import app.alpha.ui.logic.CalibrationView
import app.alpha.ui.logic.ScaleCorrectionRecord
import app.alpha.ui.text.CalibrationCatalogue
import app.alpha.ui.text.CalibrationStrings
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.uiDecimal
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import app.alpha.ui.theme.chartField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Шагов при отрисовке кривой: 25 кэВ на шаг при шкале 3000 кэВ. */
private const val CURVE_STEPS = 120

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
    val correctionRaw by graph.settings.scaleCorrectionRaw.collectAsState(initial = null)
    val correctionRecord = remember(correctionRaw) { ScaleCorrectionRecord.decode(correctionRaw) }
    val accepted = remember(acceptedRaw) { AcceptedResolution.decode(acceptedRaw) }
    val driftRaw by graph.settings.gainDriftRaw.collectAsState(initial = null)
    val driftRecord = remember(driftRaw) { GainDriftRecord.decode(driftRaw) }
    val rare by graph.measurementRepository.latestRareData().collectAsState(initial = null)

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
            // Прибор без спектрометрии (пластиковый сцинтиллятор): фотопиков
            // он не даёт, значит опорных линий не будет никогда. Обещать, что
            // «материал соберётся сам», такому прибору нельзя — это обещание
            // не сбудется ни за какое время.
            m != null && !m.spectrometer -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(
                        text = s.notASpectrometer,
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                    Hint(
                        text = s.notASpectrometerWhy,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                }
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
                correction = correctionRecord,
                drift = driftRecord,
                instrumentTemperatureC = rare?.temperature?.toDouble(),
                onAcceptCorrection = { offer ->
                    scope.launch {
                        graph.settings.setScaleCorrectionRaw(
                            ScaleCorrectionRecord
                                .of(offer, System.currentTimeMillis())
                                .encode(),
                        )
                    }
                },
                onRevertCorrection = {
                    scope.launch { graph.settings.setScaleCorrectionRaw(null) }
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
    /** Принятая поправка шкалы; null — шкала показывается как у прибора. */
    correction: ScaleCorrectionRecord? = null,
    /** Измеренный температурный ход шкалы; null — ещё не измерен. */
    drift: GainDriftRecord? = null,
    /** Температура прибора прямо сейчас, °C; null — прибор не подключён. */
    instrumentTemperatureC: Double? = null,
    onAcceptCorrection: (ScaleCorrection) -> Unit = {},
    onRevertCorrection: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    Section(s.materialTitle) {
        for (row in CalibrationView.material(model.selection, s, h)) {
            Text(text = row, style = type.valueSmall, color = colors.ink)
        }
        Hint(text = s.radonExplained)
        Hint(text = s.materialCollected, style = type.footnote, color = colors.muted)
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
        // Список ненайденных линий живёт в разделе «Чего не хватает» — там он
        // отвечает на вопрос «что сделать, чтобы стало больше». Здесь он
        // повторял сам себя слово в слово.
        blendNote(model.report, s)?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }
    }

    // Температурный ход шкалы: приложение измеряет его само и только
    // показывает — подставлять его в подписи энергий нельзя по той же
    // причине, по которой поправка шкалы включается человеком.
    Section(s.driftTitle) {
        val record = drift?.takeIf { row ->
            // Ход принадлежит кристаллу: чужой показывать нельзя.
            row.deviceSerial == null || model.deviceSerial == null ||
                row.deviceSerial == model.deviceSerial
        }
        if (record == null) {
            Text(text = s.driftNone, style = type.bodySmall, color = colors.ink2)
        } else {
            val value = record.drift
            Text(
                text = s.driftLine(
                    energyKeV = CalibrationView.number(value.lineKeV, 1),
                    shift = CalibrationView.signed(100.0 * (value.atReference - 1.0), 1),
                    temperature = CalibrationView.number(value.referenceC, 0),
                ),
                style = type.valueSmall,
                color = colors.ink,
            )
            Text(
                text = if (value.slopeResolved) {
                    s.driftSlope(
                        perDegree = CalibrationView.signed(100.0 * value.perDegree, 2),
                        sigma = CalibrationView.number(100.0 * value.perDegreeSigma, 2),
                        points = value.points,
                        from = CalibrationView.number(value.minC, 0),
                        to = CalibrationView.number(value.maxC, 0),
                    )
                } else {
                    s.driftNotResolved
                },
                style = type.bodySmall,
                color = colors.ink2,
            )
            // Предсказание на текущую температуру — то, ради чего ход и
            // меряется: оно говорит, насколько шкала уехала ПРЯМО СЕЙЧАС.
            if (instrumentTemperatureC != null && value.slopeResolved) {
                Text(
                    text = s.driftNow(
                        temperature = CalibrationView.number(instrumentTemperatureC, 0),
                        shift = CalibrationView.signed(
                            100.0 * (value.at(instrumentTemperatureC) - 1.0),
                            1,
                        ),
                        sigma = CalibrationView.number(
                            100.0 * value.sigmaAt(instrumentTemperatureC),
                            1,
                        ),
                    ),
                    style = type.bodySmall,
                    color = colors.ink,
                )
            }
        }
        Hint(text = s.driftNote)
    }

    // Поправка шкалы — ЯВНОЕ действие: предложение с числами «до и после»
    // и кнопка. Пока её не нажали, ничего не меняется.
    Section(s.correctionTitle) {
        val offer = remember(model.report) {
            ScaleCorrectionMath.of(
                model.report.measurements.map {
                    ScaleCorrection.Reference(
                        tableKeV = it.line.energyKeV,
                        measuredKeV = it.observedKeV,
                        nuclide = it.line.nuclide,
                    )
                },
            )
        }
        // Что такое поправка и чего она не делает — обучающий текст:
        // прячется вместе с пояснениями. Само предложение с числами и
        // состояние принятой поправки остаются всегда.
        Hint(text = s.correctionNote)
        if (correction != null) {
            Text(
                text = s.correctionAcceptedState(
                    date = app.alpha.ui.logic.HistoryFormat.day(correction.acceptedAtMillis),
                    lines = correction.referenceCount,
                ),
                style = type.bodySmall,
                color = colors.ink,
            )
            Text(text = s.correctionAcceptedNote, style = type.footnote, color = colors.muted)
            Chip(text = s.correctionRevert, color = colors.ink2, onClick = onRevertCorrection)
        } else if (offer != null) {
            Text(
                text = s.correctionOffer(
                    before = oneDecimal(offer.residualBeforeKeV),
                    after = oneDecimal(offer.residualAfterKeV),
                    lines = offer.references.size,
                ),
                style = type.bodySmall,
                color = colors.ink,
            )
            // Сдвиг называется на конкретной энергии: «множитель 1,021» не
            // говорит человеку ничего, «на 1460,8 кэВ сдвиг +30,8» — говорит.
            offer.references.maxByOrNull { it.tableKeV }?.let { reference ->
                Text(
                    text = s.correctionShift(
                        energyKeV = oneDecimal(reference.measuredKeV),
                        shift = signedOneDecimal(offer.shiftAt(reference.measuredKeV)),
                    ),
                    style = type.footnoteMono,
                    color = colors.ink2,
                )
            }
            Chip(
                text = s.correctionAccept,
                color = colors.dataText,
                selected = true,
                onClick = { onAcceptCorrection(offer) },
            )
        } else {
            Text(text = s.correctionNotOffered, style = type.bodySmall, color = colors.ink2)
        }
    }

    // Почему линия не взята — разбор метода: он объясняет отбор, а не
    // сообщает результат.
    Explain {
        Section(s.rejectedTitle) {
            for (row in CalibrationView.rejected(model.report, s)) {
                Text(text = row, style = type.footnote, color = colors.muted)
            }
        }
    }

    ResolutionSection(model, accepted, s, onAccept, onRevert)

    Section(s.scaleTitle) {
        for (row in CalibrationView.scale(model.report, s)) {
            Text(text = row, style = type.valueSmall, color = colors.ink)
        }
        // Почему приложение не правит шкалу само — пояснение, а не отказ
        // метода: сами измеренные сдвиги выше остаются на экране.
        Hint(text = s.noCorrection)
    }

    // Заголовок называет СОСТОЯНИЕ раздела, а не обещание: пока пар линий
    // одного нуклида не измерено, отклик не считается вовсе, и слово
    // «частично» в заголовке спорило с отказом в теле.
    Section(
        if (model.report.response.isEmpty()) s.responseTitleNone else s.responseTitle,
    ) {
        for (row in CalibrationView.response(model.report, s)) {
            Text(text = row, style = type.valueSmall, color = colors.ink)
        }
        Hint(text = s.responseWhy)
        Text(text = s.responseCaveat, style = type.footnote, color = colors.warn)
        // Оговорка о точечной геометрии — такая же тихая строка, как и
        // остальные: крупным шрифтом она читалась как заголовок раздела.
        Text(text = s.responsePointGeometry, style = type.footnote, color = colors.muted)
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
            accepted == null -> s.approximationState(
                percent = CalibrationView.number(model.startResolution662 * 100.0, 1),
                vendorPublished = model.resolutionPublished,
            )
            model.deviceSerial != null && accepted.deviceSerial != null &&
                model.deviceSerial != accepted.deviceSerial ->
                s.otherDevice(accepted.deviceSerial)
            accepted.automatic -> s.acceptedAutoState(
                date = app.alpha.ui.logic.HistoryFormat.day(accepted.acceptedAtMillis),
                points = accepted.points,
            )
            else -> s.acceptedState(
                date = app.alpha.ui.logic.HistoryFormat.day(accepted.acceptedAtMillis),
                points = accepted.points,
            )
        }
        Text(text = state, style = type.bodySmall, color = colors.ink2)
        Hint(text = if (accepted?.automatic == true) s.acceptedAutoNote else s.acceptedNote)

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

/** Одна десятая — общий вид чисел этого экрана. */
private fun oneDecimal(value: Double): String =
    String.format(java.util.Locale.US, "%.1f", value).uiDecimal()

/** То же со знаком: сдвиг без знака не читается. */
private fun signedOneDecimal(value: Double): String =
    (if (value >= 0.0) "+" else "") + oneDecimal(value)
