package app.alpha.ui.screens

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.alpha.AppGraph
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.Radioelements
import app.alpha.analysis.StrippingCalibration
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.device.ConnectionState
import app.alpha.device.DeviceModel
import app.alpha.ui.components.AppBackButton
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.Card
import app.alpha.ui.components.ChartNotesDialog
import app.alpha.ui.components.Chip
import app.alpha.ui.components.ExplainInfoButton
import app.alpha.ui.components.Hint
import app.alpha.ui.components.Segmented
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.StrippingRecord
import app.alpha.ui.logic.SurveyExport
import app.alpha.ui.logic.SurveyModel
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.map.rememberMyPosition
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SurveyCatalogue
import app.alpha.ui.text.SurveyStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import kotlinx.coroutines.launch

/**
 * Радиоэлементная съёмка по станциям: калий, уран и торий на точках, стоящих
 * в сотнях метров друг от друга.
 *
 * Экран отвечает на один вопрос — **чем эта точка отличается от остальных**.
 * Поэтому величина выбирается переключателем (элемент или отношение), а под
 * каждой станцией стоит её отличие от медианы съёмки в собственных σ. Ни
 * концентраций, ни названий пород: приложение сравнивает станции, вывод о
 * веществе делает человек.
 */
@Composable
fun SurveyScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    /** Открыть карту со станциями, окрашенными выбранной здесь величиной. */
    onShowOnMap: (SurveyModel.Quantity) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SurveyCatalogue.of(strings.language)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    val hasLocation = remember {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val fix = rememberMyPosition(hasPermission = hasLocation)

    // Прибор без энергетического разрешения (RadiaCode Zero, органический
    // пластик) линий не даёт вовсе: считать по такому спектру калий, уран и
    // торий нельзя, и экран говорит это, а не рисует числа из шума.
    val connection by graph.serviceStatus.connection.collectAsState()
    val connectedModel = (connection as? ConnectionState.Connected)?.info?.model
    val notSpectrometer = connectedModel?.isSpectrometer == false

    var quantityIndex by rememberSaveable { mutableIntStateOf(0) }
    val quantity = SurveyModel.Quantity.entries[
        quantityIndex.coerceIn(0, SurveyModel.Quantity.entries.lastIndex),
    ]

    val strippingRaw by graph.settings.strippingRaw.collectAsState(initial = null)
    val stripping = remember(strippingRaw) { StrippingRecord.decode(strippingRaw) }
    val connectedSerial = (connection as? ConnectionState.Connected)?.info?.serialNumber

    var stations by remember { mutableStateOf<List<SurveyModel.Station>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var methodOpen by remember { mutableStateOf(false) }

    LaunchedEffect(reload, strippingRaw) {
        stations = graph.surveyRepository.loaded(stripping)
        loaded = true
    }

    if (methodOpen) {
        ChartNotesDialog(
            title = t.methodTitle,
            notes = listOf(
                t.methodDwell,
                t.methodGeometry,
                t.methodRatios,
                t.methodRadon,
                t.methodStripping,
                t.methodNoUnits,
            ),
        ) { methodOpen = false }
    }

    val saver = rememberFileSaver { ok -> if (ok) message = t.exportSaved }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(onBack = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = t.title, color = colors.ink)
            ExplainInfoButton(
                onClick = { methodOpen = true },
                modifier = Modifier.padding(start = Dimens.space1),
            )
        }

        // Снять станцию можно всегда, когда есть что записывать: экран съёмки
        // и есть то место, где это делают.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                if (notSpectrometer) {
                    Text(
                        text = t.notSpectrometer(connectedModel?.displayName.orEmpty()),
                        style = type.bodySmall,
                        color = colors.warn,
                    )
                }
                AppButton(
                    text = t.recordStation,
                    enabled = !notSpectrometer,
                    onClick = {
                        scope.launch {
                            message = recordStation(graph, fix, t)
                            reload++
                        }
                    },
                )
                Hint(text = t.recordingHint)
                message?.let {
                    Text(text = it, style = type.footnote, color = colors.ink2)
                }
            }
        }

        StrippingCard(
            graph = graph,
            record = stripping,
            connectedSerial = connectedSerial,
            enabled = !notSpectrometer,
            t = t,
        )

        when {
            !loaded -> Unit
            stations.isEmpty() -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(text = t.emptyTitle, style = type.title, color = colors.ink)
                    Text(text = t.emptyBody, style = type.bodySmall, color = colors.ink2)
                }
            }

            else -> {
                Segmented(
                    options = listOf(
                        t.quantityPotassium,
                        t.quantityUranium,
                        t.quantityThorium,
                        t.quantityUraniumToThorium,
                        t.quantityThoriumToPotassium,
                    ),
                    selectedIndex = quantityIndex,
                    onSelect = { quantityIndex = it },
                )

                val deviations = stations.associate { station ->
                    station.entity.id to SurveyModel.deviation(station, stations, quantity)
                }
                Text(
                    text = t.stationsCount(
                        stations.size,
                        deviations.values.count { it?.notable == true },
                    ),
                    style = type.footnote,
                    color = colors.muted,
                )

                // Свежие станции сверху: только что снятую ищут первой.
                for (station in stations.sortedByDescending { it.entity.timestamp }) {
                    StationCard(
                        station = station,
                        quantity = quantity,
                        deviation = deviations[station.entity.id],
                        t = t,
                    )
                }

                AppButton(
                    text = t.showOnMap,
                    onClick = { onShowOnMap(quantity) },
                )
                AppButton(
                    text = t.exportCsv,
                    onClick = {
                        saver.save(
                            ExportFile.CSV,
                            SurveyExport.fileName(System.currentTimeMillis()),
                            SurveyExport.csv(stations),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Станция одной карточкой: величина с σ, отличие от съёмки и обстоятельства,
 * без которых числа несравнимы, — высота, точность места и давление.
 */
@Composable
private fun StationCard(
    station: SurveyModel.Station,
    quantity: SurveyModel.Quantity,
    deviation: SurveyModel.Deviation?,
    t: SurveyStrings,
) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var expanded by rememberSaveable(station.entity.id) { mutableStateOf(false) }

    val value = SurveyModel.value(station, quantity)
    val sigma = SurveyModel.sigma(station, quantity)
    val ratio = quantity == SurveyModel.Quantity.U_TH || quantity == SurveyModel.Quantity.TH_K

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = HistoryFormat.timeOfDay(station.entity.timestamp),
                    style = type.label,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        value == null || sigma == null -> t.belowLimit
                        ratio -> t.ratioWithSigma(
                            Uncertainty.num2(value),
                            Uncertainty.num2(sigma),
                        )
                        else -> t.valueWithSigma(
                            Uncertainty.num2(value),
                            Uncertainty.num2(sigma),
                            t.unitCps,
                        )
                    },
                    style = type.valueSmall,
                    color = if (value == null) colors.muted else colors.ink,
                )
            }

            // Отличие всегда называет знаменатель и своё σ: «×1,8» без них —
            // не результат сравнения, а просто число.
            Text(
                text = when {
                    deviation == null -> t.tooFewStations(SurveyModel.MIN_STATIONS)
                    !deviation.notable -> t.notDifferent(Uncertainty.num1(deviation.sigmas))
                    deviation.above -> t.aboveSurvey(
                        Uncertainty.num2(deviation.ratioToMedian),
                        Uncertainty.num1(deviation.sigmas),
                    )
                    else -> t.belowSurvey(
                        Uncertainty.num2(deviation.ratioToMedian),
                        Uncertainty.num1(deviation.sigmas),
                    )
                },
                style = type.footnote,
                color = if (deviation?.notable == true) colors.warn else colors.muted,
            )

            if (expanded) {
                for (element in Radioelements.Element.entries) {
                    val measure = station.measure(element)
                    Text(
                        // Линии, которой нет в списке, не измеряли вовсе: её
                        // окно не поместилось в шкалу прибора. Это другое, чем
                        // «не набрана», и говорится другими словами.
                        text = elementLabel(element, t) + ": " + when {
                            measure == null -> t.outOfScale
                            !measure.detected -> t.belowLimit
                            else -> t.valueWithSigma(
                                Uncertainty.num2(measure.cps),
                                Uncertainty.num2(measure.cpsSigma),
                                t.unitCps,
                            )
                        },
                        style = type.footnoteMono,
                        color = colors.ink2,
                    )
                }
                Text(
                    text = listOfNotNull(
                        t.accumulation(HistoryFormat.duration(station.seconds, h)),
                        t.accuracy(Uncertainty.num1(station.entity.accuracyMeters)),
                        station.entity.heightCm?.let { t.height(it.toString()) } ?: t.heightUnknown,
                        station.entity.pressureHpa?.let { t.pressure(Uncertainty.num1(it)) },
                        station.deviceName?.let {
                            if (station.tunedProfile) t.device(it) else t.deviceUntuned(it)
                        } ?: t.deviceUnknown,
                    ).joinToString(" · "),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

private fun elementLabel(element: Radioelements.Element, t: SurveyStrings): String =
    when (element) {
        Radioelements.Element.K -> t.quantityPotassium
        Radioelements.Element.U -> t.quantityUranium
        Radioelements.Element.TH -> t.quantityThorium
    }

/**
 * Записать станцию: накопленный спектр как снимок плюс место и обстоятельства.
 *
 * Отказ называет, чего не хватило, и ничего не пишет наполовину: станция без
 * координат или без спектра — не станция.
 */
private suspend fun recordStation(
    graph: AppGraph,
    fix: app.alpha.ui.logic.PositionFix?,
    t: SurveyStrings,
): String {
    val spectrum = graph.spectrumHub.state.value.spectrum ?: return t.needSpectrum
    if (spectrum.counts.isEmpty() || spectrum.durationSeconds <= 0L) return t.needSpectrum
    val position = fix ?: return t.needPosition

    val connection = graph.serviceStatus.connection.value as? ConnectionState.Connected
    val snapshot = graph.measurementRepository.saveSpectrum(
        spectrum = spectrum,
        accumulated = true,
        origin = SpectrumSnapshotEntity.ORIGIN_USER,
        deviceSerial = connection?.info?.serialNumber,
        firmware = connection?.info?.firmware?.toString(),
    )
    graph.surveyRepository.record(
        spectrumId = snapshot.id,
        timestamp = snapshot.timestamp,
        latitude = position.latitude,
        longitude = position.longitude,
        accuracyMeters = position.accuracyMeters,
        pressureHpa = graph.serviceStatus.environment.value?.pressureHpa,
    )
    return t.recorded
}

/**
 * Калибровка стриппинга: три измерения и коэффициенты, принадлежащие ПРИБОРУ.
 *
 * Каждый шаг берёт то, что накоплено сейчас, — тем же способом, что и станция.
 * Отдельного режима записи нет: человек сбрасывает накопление, кладёт источник
 * и ждёт, а экран лишь запоминает получившийся спектр.
 */
@Composable
private fun StrippingCard(
    graph: AppGraph,
    record: StrippingRecord?,
    connectedSerial: String?,
    enabled: Boolean,
    t: SurveyStrings,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val scope = rememberCoroutineScope()

    // Заход калибровки живёт в памяти экрана: это одна процедура за один раз,
    // и хранить её половину в настройках значило бы обещать продолжение.
    var background by remember { mutableStateOf<StrippingCalibration.Sample?>(null) }
    var thorium by remember { mutableStateOf<StrippingCalibration.Sample?>(null) }
    var uranium by remember { mutableStateOf<StrippingCalibration.Sample?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    fun take(): StrippingCalibration.Sample? {
        val spectrum = graph.spectrumHub.state.value.spectrum ?: return null
        if (spectrum.counts.isEmpty() || spectrum.durationSeconds <= 0L) return null
        val model = (graph.serviceStatus.connection.value as? ConnectionState.Connected)
            ?.info?.model ?: DeviceModel.UNKNOWN
        return StrippingCalibration.Sample(
            measures = Radioelements.measure(
                counts = spectrum.counts,
                calibration = EnergyCalibration(spectrum.a0, spectrum.a1, spectrum.a2),
                seconds = spectrum.durationSeconds,
                resolution662 = model.peakResolution662,
            ),
            seconds = spectrum.durationSeconds,
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(text = t.strippingTitle.uppercase(), style = type.labelSmall, color = colors.ink2)

            Text(
                text = when {
                    record == null -> t.strippingNone
                    !record.appliesTo(connectedSerial) && connectedSerial != null ->
                        t.strippingOtherDevice(record.serialNumber)
                    else -> t.strippingValues(
                        Uncertainty.num2(record.thoriumIntoUranium),
                        Uncertainty.num2(record.thoriumIntoPotassium),
                        Uncertainty.num2(record.uraniumIntoPotassium),
                        HistoryFormat.timeOfDay(record.measuredAtMillis),
                    )
                },
                style = type.bodySmall,
                color = if (record == null) colors.muted else colors.ink2,
            )

            if (enabled) {
                StrippingStep(
                    label = t.strippingMeasureBackground,
                    taken = background?.let { t.strippingTaken(HistoryFormat.duration(it.seconds, h)) },
                    onTake = { background = take(); if (background == null) note = t.needSpectrum },
                )
                StrippingStep(
                    label = t.strippingMeasureThorium,
                    taken = thorium?.let { t.strippingTaken(HistoryFormat.duration(it.seconds, h)) },
                    onTake = { thorium = take(); if (thorium == null) note = t.needSpectrum },
                )
                StrippingStep(
                    label = t.strippingMeasureUranium,
                    taken = uranium?.let { t.strippingTaken(HistoryFormat.duration(it.seconds, h)) },
                    onTake = { uranium = take(); if (uranium == null) note = t.needSpectrum },
                )
                AppButton(
                    text = t.strippingCompute,
                    onClick = {
                        val result = StrippingCalibration.of(background, thorium, uranium)
                        val serial = connectedSerial
                        note = when {
                            background == null -> t.strippingNeedBackground
                            thorium == null -> t.strippingNeedThorium
                            result.thoriumRefusal == StrippingCalibration.Refusal.SOURCE_TOO_WEAK ->
                                t.strippingSourceTooWeak
                            result.stripping == null || serial == null -> t.strippingNothingAbove
                            else -> {
                                scope.launch {
                                    graph.settings.setStripping(
                                        StrippingRecord(
                                            serialNumber = serial,
                                            thoriumIntoUranium =
                                                result.stripping.thoriumIntoUranium,
                                            thoriumIntoPotassium =
                                                result.stripping.thoriumIntoPotassium,
                                            uraniumIntoPotassium =
                                                result.stripping.uraniumIntoPotassium,
                                            measuredAtMillis = System.currentTimeMillis(),
                                        ).encode(),
                                    )
                                }
                                if (result.uraniumRefusal != null) {
                                    t.strippingNoUranium
                                } else {
                                    t.strippingSaved
                                }
                            }
                        }
                    },
                )
                if (record != null) {
                    AppButton(
                        text = t.strippingClear,
                        onClick = { scope.launch { graph.settings.setStripping(null) } },
                    )
                }
                Hint(text = t.strippingHint)
                note?.let { Text(text = it, style = type.footnote, color = colors.ink2) }
            }
        }
    }
}

@Composable
private fun StrippingStep(label: String, taken: String?, onTake: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppButton(text = label, onClick = onTake, modifier = Modifier.weight(1f))
        taken?.let {
            Text(
                text = it,
                style = type.footnote,
                color = colors.ok,
                modifier = Modifier.padding(start = Dimens.space2),
            )
        }
    }
}
