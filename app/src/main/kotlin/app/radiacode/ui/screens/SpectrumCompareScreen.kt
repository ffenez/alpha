package app.radiacode.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.SpectrumCompare
import app.radiacode.analysis.SpectrumDisplay
import app.radiacode.data.SpectrumBlob
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.data.export.RcResultData
import app.radiacode.data.export.RcSpectrum
import app.radiacode.data.export.ProcessingMetadata
import app.radiacode.data.export.RcXml
import app.radiacode.data.export.SpectrumExport
import app.radiacode.protocol.Spectrum
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.DiffChart
import app.radiacode.ui.components.DiffChartSpec
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.SpectrumChart
import app.radiacode.ui.components.SpectrumChartSpec
import app.radiacode.ui.logic.CompareFormat
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.SpectrumFormat
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.launch

/** Chart resolution, matches the Спектр screen. */
private const val COLUMN_COUNT = 240

/**
 * Сравнение двух сохранённых спектров (вход из Истории). Два режима:
 * «A−B интервал» — вычитание снимков одного накопления (спектр только за
 * промежуток между ними), «Скорости счёта» — честное сравнение независимых
 * измерений в имп/с с полосами ±1σ/±2σ и выводом по диапазонам энергий.
 */
@Composable
fun SpectrumCompareScreen(
    graph: AppGraph,
    firstId: Long,
    secondId: Long,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    BackHandler { onBack() }

    var pair by remember { mutableStateOf<Pair<SpectrumSnapshotEntity, SpectrumSnapshotEntity>?>(null) }
    var missing by remember { mutableStateOf(false) }
    LaunchedEffect(firstId, secondId) {
        val first = graph.measurementRepository.spectrumById(firstId)
        val second = graph.measurementRepository.spectrumById(secondId)
        if (first == null || second == null) missing = true else pair = first to second
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(text = "← Назад", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = "Сравнение", color = colors.ink)
        }

        val p = pair
        when {
            missing -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = "снимки не найдены", style = type.bodySmall, color = colors.muted)
            }
            p == null -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = "читаю снимки…", style = type.bodySmall, color = colors.muted)
            }
            else -> CompareContent(graph, p.first, p.second)
        }
    }
}

private fun SpectrumSnapshotEntity.toCompareInput() = SpectrumCompare.Input(
    counts = SpectrumBlob.decode(counts),
    durationSeconds = durationSeconds,
    calibration = EnergyCalibration(a0, a1, a2),
    timestampMillis = timestamp,
)

@Composable
private fun CompareContent(
    graph: AppGraph,
    first: SpectrumSnapshotEntity,
    second: SpectrumSnapshotEntity,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }

    PairCard(first, second)

    Segmented(
        options = listOf("A−B интервал", "Скорости счёта"),
        selectedIndex = mode,
        onSelect = { mode = it },
        modifier = Modifier.fillMaxWidth(),
    )

    if (mode == 0) {
        IntervalSection(graph, first, second)
    } else {
        RatesSection(first, second)
    }
}

@Composable
private fun PairCard(first: SpectrumSnapshotEntity, second: SpectrumSnapshotEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PairRow("A", first)
            AppDivider()
            PairRow("B", second)
        }
    }
}

@Composable
private fun PairRow(marker: String, entity: SpectrumSnapshotEntity) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Chip(text = marker, color = colors.dataText)
        Column(Modifier.weight(1f)) {
            Text(
                text = SpectrumExport.title(entity),
                style = type.label,
                color = colors.ink,
                maxLines = 1,
            )
            Text(
                text = HistoryFormat.dayTime(entity.timestamp, now) +
                    " · Δt " + SpectrumFormat.accumulationClock(entity.durationSeconds),
                style = type.footnote,
                color = colors.ink2,
            )
        }
    }
}

// --- mode 1: A−B interval extraction ---

@Composable
private fun IntervalSection(
    graph: AppGraph,
    first: SpectrumSnapshotEntity,
    second: SpectrumSnapshotEntity,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val outcome = remember(first.id, second.id) {
        SpectrumCompare.extractInterval(first.toCompareInput(), second.toCompareInput())
    }

    when (outcome) {
        is SpectrumCompare.IntervalOutcome.Invalid -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text("Интервал вычесть нельзя", style = type.title, color = colors.ink)
                Text(text = outcome.reason, style = type.body, color = colors.ink2)
                Text(
                    text = "Этот режим — для двух снимков одного непрерывного накопления: " +
                        "позднее минус раннее даёт спектр только за промежуток между ними.",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
        is SpectrumCompare.IntervalOutcome.Ok -> {
            var logScale by rememberSaveable { mutableStateOf(true) }
            var savedNote by remember { mutableStateOf<String?>(null) }
            var pendingExport by remember { mutableStateOf<String?>(null) }
            var exportNote by remember { mutableStateOf<String?>(null) }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/xml"),
            ) { uri ->
                val content = pendingExport
                pendingExport = null
                if (uri != null && content != null) {
                    scope.launch {
                        exportNote = if (writeTextToUri(context, uri, content)) {
                            "файл сохранён"
                        } else {
                            "файл не записался — попробуйте другую папку"
                        }
                    }
                }
            }

            val label = "Интервал A−B · Δt " +
                SpectrumFormat.accumulationClock(outcome.durationSeconds)
            val totalCounts = outcome.counts.sumOf { it.toLong() }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(text = SpectrumFormat.accumulationChip(outcome.durationSeconds, totalCounts))
                Spacer(Modifier.weight(1f))
                Segmented(
                    options = listOf("Лог", "Лин"),
                    selectedIndex = if (logScale) 0 else 1,
                    onSelect = { logScale = it == 0 },
                    modifier = Modifier.weight(0.9f),
                )
            }

            val full = remember(outcome) {
                SpectrumDisplay.fullWindow(outcome.calibration, outcome.counts.size)
            }
            val columns = remember(outcome) {
                SpectrumDisplay.aggregateMax(
                    outcome.counts.map { it.toFloat() },
                    0..outcome.counts.size - 1,
                    COLUMN_COUNT,
                )
            }
            val dataMax = columns.maxOrNull() ?: 0f
            Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space2) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    SpectrumChart(
                        spec = SpectrumChartSpec(
                            columns = columns,
                            logScale = logScale,
                            yTop = if (logScale) {
                                SpectrumDisplay.logTop(dataMax)
                            } else {
                                maxOf(dataMax * 1.15f, 10f)
                            },
                            energyTicks = SpectrumDisplay.energyTicks(full),
                        ),
                    )
                    Text(
                        text = "спектр за интервал между снимками · кэВ по горизонтали",
                        style = type.footnote,
                        color = colors.muted,
                        modifier = Modifier.padding(horizontal = Dimens.space1),
                    )
                }
            }

            outcome.warnings.forEach { warning ->
                Text(
                    text = "⚠ $warning",
                    style = type.footnote,
                    color = colors.warn,
                    modifier = Modifier.padding(horizontal = Dimens.space1),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                AppButton(
                    text = "Экспорт XML",
                    onClick = {
                        pendingExport = RcXml.write(intervalResultData(outcome, label))
                        exportLauncher.launch(
                            SpectrumExport.fileName(outcome.endMillis, "xml"),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "Сохранить снимок",
                    onClick = {
                        scope.launch {
                            graph.measurementRepository.saveSpectrum(
                                spectrum = Spectrum(
                                    durationSeconds = outcome.durationSeconds,
                                    a0 = outcome.calibration.a0,
                                    a1 = outcome.calibration.a1,
                                    a2 = outcome.calibration.a2,
                                    counts = outcome.counts,
                                ),
                                accumulated = false,
                                origin = SpectrumSnapshotEntity.ORIGIN_USER,
                                label = label,
                                // Спец §22: производный снимок хранит метод и
                                // версии алгоритмов, которыми получен.
                                analysisMeta = ProcessingMetadata.stamp(
                                    method = "interval_subtraction (A−B)",
                                    algorithms = listOf("spectrum_compare"),
                                    extra = mapOf(
                                        "sourceIds" to "${first.id},${second.id}",
                                        "intervalSeconds" to outcome.durationSeconds.toString(),
                                        "calibrationToleranceKeV" to
                                            SpectrumCompare.CALIBRATION_TOLERANCE_KEV.toString(),
                                    ),
                                ),
                            )
                            savedNote = "снимок «$label» сохранён — он появился в списке спектров"
                        }
                    },
                    enabled = savedNote == null,
                    modifier = Modifier.weight(1f),
                )
            }
            listOfNotNull(exportNote, savedNote).forEach { note ->
                Text(
                    text = note,
                    style = type.footnote,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = Dimens.space1),
                )
            }
        }
    }
}

private fun intervalResultData(
    outcome: SpectrumCompare.IntervalOutcome.Ok,
    label: String,
): RcResultData = RcResultData(
    deviceModel = "RadiaCode",
    sampleName = label,
    sampleNote = null,
    startMillis = outcome.startMillis,
    endMillis = outcome.endMillis,
    spectrum = RcSpectrum(
        name = label,
        serialNumber = null,
        a0 = outcome.calibration.a0,
        a1 = outcome.calibration.a1,
        a2 = outcome.calibration.a2,
        measurementSeconds = outcome.durationSeconds,
        counts = outcome.counts,
    ),
    background = null,
)

// --- mode 2: independent rate comparison ---

@Composable
private fun RatesSection(first: SpectrumSnapshotEntity, second: SpectrumSnapshotEntity) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    val aInput = remember(first.id) { first.toCompareInput() }
    val outcome = remember(first.id, second.id) {
        SpectrumCompare.compareRates(aInput, second.toCompareInput())
    }

    when (outcome) {
        is SpectrumCompare.RateOutcome.Invalid -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text("Сравнить скорости нельзя", style = type.title, color = colors.ink)
                Text(text = outcome.reason, style = type.body, color = colors.ink2)
            }
        }
        is SpectrumCompare.RateOutcome.Ok -> {
            var logScale by rememberSaveable { mutableStateOf(true) }

            val full = remember(outcome) {
                SpectrumDisplay.fullWindow(outcome.calibration, aInput.counts.size)
            }
            val range = 0..aInput.counts.size - 1
            val ticks = remember(full) { SpectrumDisplay.energyTicks(full) }

            // Chart 1: both spectra in counts, B normalized to A's live time.
            val columnsA = remember(outcome) {
                SpectrumDisplay.aggregateMax(
                    aInput.counts.map { it.toFloat() },
                    range,
                    COLUMN_COUNT,
                )
            }
            val timeRatio =
                aInput.durationSeconds.toFloat() / second.durationSeconds.toFloat()
            val columnsB = remember(outcome) {
                SpectrumDisplay.aggregateMax(
                    outcome.bCountsOnGrid.map { it * timeRatio },
                    range,
                    COLUMN_COUNT,
                )
            }
            val dataMax = maxOf(columnsA.maxOrNull() ?: 0f, columnsB.maxOrNull() ?: 0f)

            Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space2) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = Dimens.space1),
                    ) {
                        Text(
                            text = "A и B к одному времени накопления".uppercase(),
                            style = type.labelSmall,
                            color = colors.ink2,
                            modifier = Modifier.weight(1f),
                        )
                        Segmented(
                            options = listOf("Лог", "Лин"),
                            selectedIndex = if (logScale) 0 else 1,
                            onSelect = { logScale = it == 0 },
                            modifier = Modifier.weight(0.6f),
                        )
                    }
                    SpectrumChart(
                        spec = SpectrumChartSpec(
                            columns = columnsA,
                            overlay = columnsB,
                            logScale = logScale,
                            yTop = if (logScale) {
                                SpectrumDisplay.logTop(dataMax)
                            } else {
                                maxOf(dataMax * 1.15f, 10f)
                            },
                            energyTicks = ticks,
                        ),
                    )
                    Text(
                        text = "бирюзовая линия — A · серая — B, приведённый к времени A",
                        style = type.footnote,
                        color = colors.muted,
                        modifier = Modifier.padding(horizontal = Dimens.space1),
                    )
                }
            }

            // Chart 2: the rate difference with its Poisson bands.
            val diffColumns = remember(outcome) {
                SpectrumCompare.aggregateDiff(
                    outcome.diffCps,
                    outcome.sigmaCps,
                    range,
                    COLUMN_COUNT,
                )
            }
            Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space2) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(
                        text = "Разность скоростей A−B, имп/с".uppercase(),
                        style = type.labelSmall,
                        color = colors.ink2,
                        modifier = Modifier.padding(horizontal = Dimens.space1),
                    )
                    DiffChart(
                        spec = DiffChartSpec(
                            diff = diffColumns.diff,
                            sigma = diffColumns.sigma,
                            energyTicks = ticks,
                        ),
                    )
                    Text(
                        text = "полосы — ±1σ и ±2σ Пуассона (σ = √N с приведением к имп/с): " +
                            "линия внутри полос — различие не отличимо от шума",
                        style = type.footnote,
                        color = colors.muted,
                        modifier = Modifier.padding(horizontal = Dimens.space1),
                    )
                }
            }

            // Verdicts per energy region.
            val verdicts = remember(outcome) {
                SpectrumCompare.regionVerdicts(outcome, aInput.counts.size)
            }
            if (verdicts.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                            CompareHeader("кэВ", 1.1f)
                            CompareHeader("Δ имп/с", 1f)
                            CompareHeader("z", 0.8f)
                            CompareHeader("вывод", 1.6f)
                        }
                        AppDivider()
                        verdicts.forEachIndexed { index, verdict ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            ) {
                                CompareCell(
                                    CompareFormat.regionLabel(verdict.startKeV, verdict.endKeV),
                                    1.1f,
                                    colors.ink,
                                )
                                CompareCell(CompareFormat.cps(verdict.diffCps), 1f, colors.ink)
                                CompareCell(CompareFormat.zLabel(verdict.z), 0.8f, colors.ink)
                                CompareCell(
                                    CompareFormat.verdictLabel(verdict.verdict),
                                    1.6f,
                                    when (verdict.verdict) {
                                        SpectrumCompare.Verdict.NOISE -> colors.muted
                                        SpectrumCompare.Verdict.EXCESS -> colors.warn
                                        else -> colors.ink2
                                    },
                                )
                            }
                            if (index < verdicts.size - 1) AppDivider()
                        }
                        Text(
                            text = "z — значимость суммарной разности диапазона в единицах σ; " +
                                "|z| < 2 — шум, ≥ 4 — значимо",
                            style = type.footnote,
                            color = colors.muted,
                        )
                    }
                }
            }

            outcome.warnings.forEach { warning ->
                Text(
                    text = "⚠ $warning",
                    style = type.footnote,
                    color = colors.warn,
                    modifier = Modifier.padding(horizontal = Dimens.space1),
                )
            }
        }
    }
}

@Composable
private fun RowScope.CompareHeader(text: String, weight: Float) {
    Text(
        text = text.uppercase(),
        style = LocalAppTypography.current.overline,
        color = LocalAppColors.current.muted,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.CompareCell(text: String, weight: Float, color: Color) {
    Text(
        text = text,
        style = LocalAppTypography.current.valueSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}
