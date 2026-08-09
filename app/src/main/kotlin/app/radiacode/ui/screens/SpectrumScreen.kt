package app.radiacode.ui.screens

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.radiacode.AppGraph
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.EnergyWindow
import app.radiacode.analysis.SpectrumDisplay
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.db.SampleEntity
import app.radiacode.device.ConnectionState
import app.radiacode.device.DoseUnits
import app.radiacode.protocol.Spectrum
import app.radiacode.service.SpectrumHub
import app.radiacode.ui.components.PixelBox
import app.radiacode.ui.components.PixelButton
import app.radiacode.ui.components.PixelTag
import app.radiacode.ui.components.SpectrumChart
import app.radiacode.ui.components.SpectrumChartSpec
import app.radiacode.ui.components.StatusLine
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.SpectrumFormat
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Chart resolution: 1024 channels aggregate into this many pixel columns. */
private const val COLUMN_COUNT = 120

/**
 * Спектр (SPEC «Spectrum», expert screen): накопление since the last reset,
 * counts/keV histogram with lin/log scale and energy-window zoom, background
 * overlay «минус фон», display-only smoothing and cautious isotope hints.
 */
@Composable
fun SpectrumScreen(graph: AppGraph) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val hub = graph.spectrumHub

    // Acquisition runs only while this tab is composed (watcher refcount).
    DisposableEffect(hub) {
        hub.attach()
        onDispose { hub.detach() }
    }

    val hubState by hub.state.collectAsState()
    val connection by graph.serviceStatus.connection.collectAsState()
    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    val spectrum = hubState.spectrum
    val connected = connection is ConnectionState.Connected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PixelDimens.space4),
        verticalArrangement = Arrangement.spacedBy(PixelDimens.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("СПЕКТР", style = type.heading, color = colors.text)
            Spacer(Modifier.weight(1f))
            PixelTag(
                text = spectrum?.let { SpectrumFormat.accumulationClock(it.durationSeconds) }
                    ?: "нет данных",
            )
        }

        val unsupported = hubState.unsupportedFormatVersion
        when {
            unsupported != null -> PixelBox(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                    Text("ФОРМАТ НЕ ПОДДЕРЖАН", style = type.label, color = colors.text)
                    Text(
                        text = "Прибор передаёт спектр в формате версии $unsupported, " +
                            "который это приложение пока не умеет читать. Остальные " +
                            "экраны работают как обычно.",
                        style = type.body,
                        color = colors.textSecondary,
                    )
                }
            }
            spectrum == null || spectrum.counts.isEmpty() -> PixelBox(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                    if (connected) {
                        StatusLine(text = "читаем спектр с прибора", cursor = true, color = colors.textSecondary)
                    } else {
                        StatusLine(text = "нет соединения с прибором", cursor = false, color = colors.textSecondary)
                        Text(
                            text = "Спектр появится после подключения — статус соединения " +
                                "виден на Главной.",
                            style = type.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
            else -> SpectrumContent(graph, spectrum, connected, hubState, sample, unit)
        }
    }
}

@Composable
private fun SpectrumContent(
    graph: AppGraph,
    spectrum: Spectrum,
    connected: Boolean,
    hubState: SpectrumHub.State,
    sample: SampleEntity?,
    unit: DoseUnitSetting,
) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val hub = graph.spectrumHub

    var logScale by rememberSaveable { mutableStateOf(true) }
    var window by remember { mutableStateOf<EnergyWindow?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val calibration = remember(spectrum.a0, spectrum.a1, spectrum.a2) {
        EnergyCalibration(spectrum.a0, spectrum.a1, spectrum.a2)
    }
    val full = remember(calibration, spectrum.counts.size) {
        SpectrumDisplay.fullWindow(calibration, spectrum.counts.size)
    }
    val visible = window?.let { SpectrumDisplay.clampInto(it, full) } ?: full

    // --- summary: имп · CPS · доза (live CPS/dose from the 1 Hz stream) ---
    val totalCounts = remember(spectrum) { spectrum.counts.sumOf { it.toLong() } }
    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Text(
                text = "имп " + HistoryFormat.count(totalCounts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) +
                    " · CPS " + (sample?.countRate?.toInt()?.toString() ?: "—") +
                    " · " + (sample?.let {
                        DoseFormat.rateWithUnit(DoseUnits.rawToMicroSievertPerHour(it.doseRate), unit)
                    } ?: "— ${DoseFormat.rateUnitLabel(unit)}"),
                style = type.value,
                color = colors.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                PixelButton(
                    text = "СБРОС",
                    onClick = { confirmReset = true },
                    enabled = connected,
                )
                PixelButton(
                    text = "СОХРАНИТЬ",
                    onClick = { hub.request(SpectrumHub.Command.SAVE_SNAPSHOT) },
                )
            }
            val savedAt = hubState.lastSavedAtMillis
            if (savedAt != null) {
                Text(
                    text = "снимок сохранён в ${timeOfDay(savedAt)} — он виден в Истории",
                    style = type.labelSmall,
                    color = colors.textMuted,
                )
            }
            if (!connected) {
                Text(
                    text = "нет соединения — показан последний прочитанный спектр",
                    style = type.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
    }

    // --- chart ---
    val range = remember(visible, calibration, spectrum.counts.size) {
        SpectrumDisplay.channelRange(visible, calibration, spectrum.counts.size)
    }
    val series = remember(spectrum) { spectrum.counts.map { it.toFloat() } }
    val columns = remember(series, range) {
        SpectrumDisplay.aggregateMax(series, range, COLUMN_COUNT)
    }
    val dataMax = columns.maxOrNull() ?: 0f
    val yTop = if (logScale) SpectrumDisplay.logTop(dataMax) else maxOf(dataMax * 1.15f, 10f)

    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("СЧЁТ / КЭВ", style = type.label, color = colors.text)
                Spacer(Modifier.weight(1f))
                PixelButton(
                    text = "ЛИН",
                    onClick = { logScale = false },
                    selected = !logScale,
                )
                PixelButton(
                    text = "ЛОГ",
                    onClick = { logScale = true },
                    selected = logScale,
                )
            }
            SpectrumChart(
                spec = SpectrumChartSpec(
                    columns = columns,
                    logScale = logScale,
                    yTop = yTop,
                    energyTicks = SpectrumDisplay.energyTicks(visible),
                ),
                onGesture = { scale, pan, focus ->
                    var next = SpectrumDisplay.pinch(visible, full, scale, focus)
                    next = SpectrumDisplay.pan(next, full, pan)
                    window = next
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "диапазон ${SpectrumFormat.windowLabel(visible)}",
                    style = type.labelSmall,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                PixelButton(
                    text = "−",
                    onClick = { window = SpectrumDisplay.zoomOut(visible, full) },
                    enabled = visible.widthKeV < full.widthKeV,
                )
                PixelButton(
                    text = "+",
                    onClick = { window = SpectrumDisplay.zoomIn(visible, full) },
                    enabled = visible.widthKeV > SpectrumDisplay.MIN_WINDOW_KEV,
                )
            }
            Text(
                text = "щипок по графику — масштаб, перетаскивание — сдвиг",
                style = type.labelSmall,
                color = colors.textMuted,
            )
        }
    }

    if (confirmReset) {
        Dialog(onDismissRequest = { confirmReset = false }) {
            PixelBox(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                    Text("СБРОСИТЬ СПЕКТР?", style = type.label, color = colors.text)
                    Text(
                        text = "Накопление начнётся заново — на приборе спектр " +
                            "тоже очистится. Сохранённые снимки останутся в Истории.",
                        style = type.body,
                        color = colors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                        PixelButton(
                            text = "СБРОС",
                            onClick = {
                                confirmReset = false
                                hub.request(SpectrumHub.Command.RESET)
                            },
                        )
                        PixelButton(text = "ОТМЕНА", onClick = { confirmReset = false })
                    }
                }
            }
        }
    }
}

private val TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm")

private fun timeOfDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_OF_DAY)
