package app.radiacode.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.analysis.Spectrogram
import app.radiacode.analysis.SpectrogramColumn
import app.radiacode.analysis.SpectrogramSlice
import app.radiacode.data.DoseUnitSetting
import app.radiacode.device.ConnectionState
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.components.WaterfallChart
import app.radiacode.ui.components.WaterfallSpec
import app.radiacode.ui.components.waterfallLegendColors
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.SpectrumFormat
import app.radiacode.ui.logic.TimeAxis
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Rendered column cap: slices merge beyond this (bitmap stays legible). */
private const val MAX_COLUMNS = 240

private val HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Спектрограмма (SPEC «Spectrogram», Advanced): Energy × Time × Intensity —
 * когда во время прогулки или измерения появился спектральный компонент.
 * Столбец = интервальный спектр одного опроса (накопление минус предыдущее,
 * 5 с), строки — энергия 20–3000 кэВ на геометрической шкале, яркость —
 * лог-нормировка внутри столбца. Внизу — синхронная полоса мощности дозы;
 * тап по столбцу показывает дозу/CPS того момента.
 */
@Composable
fun SpectrogramScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val hub = graph.spectrumHub

    BackHandler { onBack() }

    // The waterfall needs the 5 s spectrum poll: attach as a hub watcher.
    DisposableEffect(hub) {
        hub.attach()
        onDispose { hub.detach() }
    }

    val connection by graph.serviceStatus.connection.collectAsState()
    val connected = connection is ConnectionState.Connected
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    val liveSlices by graph.spectrogramStore.slices.collectAsState()
    var paused by rememberSaveable { mutableStateOf(false) }
    // While paused the displayed history freezes; recording continues.
    var frozen by remember { mutableStateOf<List<SpectrogramSlice>?>(null) }
    val slices = if (paused) frozen ?: liveSlices else liveSlices

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // Режим «форма» — нормировка внутри колонки. По умолчанию выключен: он
    // показывает состав, но уравнивает слабый спектр с сильным.
    var shapeMode by rememberSaveable { mutableStateOf(false) }

    // Колонки строятся по СЕТКЕ ВРЕМЕНИ с шагом, который выбирается по
    // статистике: при фоне пятисекундная колонка это ≈1 импульс на полосу, то
    // есть шум, который глаз читает как линии.
    val fromMillis = slices.firstOrNull()?.let { it.timestampMillis - it.intervalSeconds * 1000L }
    val toMillis = slices.lastOrNull()?.timestampMillis
    val stepSeconds = remember(slices) {
        if (fromMillis == null || toMillis == null) {
            Spectrogram.DISPLAY_STEPS_SECONDS.first()
        } else {
            Spectrogram.displayStepSeconds(slices, toMillis - fromMillis, MAX_COLUMNS)
        }
    }
    val columnsData = remember(slices, stepSeconds) {
        if (fromMillis == null || toMillis == null) {
            emptyList()
        } else {
            Spectrogram.grid(slices, fromMillis, toMillis, stepSeconds * 1000L)
        }
    }
    val scaleTop = remember(columnsData) { Spectrogram.scaleTop(columnsData) }
    val selected = selectedIndex?.let { columnsData.getOrNull(it) }

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
            Chip(text = "Спектрограмма", color = colors.ink)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Chip(
                text = if (paused) "▶ продолжить" else "⏸ пауза",
                color = if (paused) colors.dataText else colors.ink2,
                onClick = {
                    if (!paused) frozen = liveSlices
                    paused = !paused
                    if (!paused) frozen = null
                },
            )
            if (paused) {
                Text(
                    text = "показ остановлен · запись продолжается",
                    style = type.footnote,
                    color = colors.ink2,
                )
            }
            Spacer(Modifier.weight(1f))
            Chip(
                text = spanLabel(slices),
                color = colors.ink2,
            )
        }

        when {
            slices.isEmpty() && !connected -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(
                        text = "нет соединения с прибором",
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                    Text(
                        text = "Спектрограмма пишется, пока прибор подключён и открыт " +
                            "экран Спектра или Спектрограммы.",
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                }
            }
            slices.isEmpty() -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "накапливаем первые интервалы… столбцы появятся через ~10 с",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            }
            else -> {
                Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space2) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        WaterfallChart(
                            spec = WaterfallSpec(
                                columns = columnsData,
                                scaleTop = scaleTop,
                                shapeMode = shapeMode,
                                selectedIndex = selectedIndex,
                                timeLabels = TimeAxis.labels(
                                    fromMillis ?: 0L,
                                    toMillis ?: 0L,
                                ),
                                stripValues = columnsData.map { it?.doseMicroSvH },
                            ),
                            onTapColumn = { index ->
                                selectedIndex = if (selectedIndex == index) null else index
                            },
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = Dimens.space1),
                        ) {
                            Text(
                                text = "кэВ ↑ · время →".uppercase(),
                                style = type.overline,
                                color = colors.muted,
                            )
                            Spacer(Modifier.weight(1f))
                            LegendRamp()
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                            modifier = Modifier.padding(horizontal = Dimens.space1),
                        ) {
                            Chip(
                                text = "форма",
                                color = if (shapeMode) colors.dataText else colors.ink2,
                                selected = shapeMode,
                                onClick = { shapeMode = !shapeMode },
                            )
                            Text(
                                text = if (shapeMode) {
                                    "яркость нормирована ВНУТРИ столбца: виден состав, но " +
                                        "слабый спектр выглядит как сильный"
                                } else {
                                    "яркость — имп/с на полосу, общая лог-шкала окна до " +
                                        "${Uncertainty.num1(scaleTop)} имп/с"
                                },
                                style = type.footnote,
                                color = colors.muted,
                            )
                        }
                        Text(
                            text = "шаг ${SpectrumFormat.accumulationClock(stepSeconds)} · " +
                                stepReason(slices, stepSeconds) +
                                " · пустая колонка — прибор молчал · полоса внизу — мощность " +
                                "дозы, та же ось времени · тап — курсор момента",
                            style = type.footnote,
                            color = colors.muted,
                            modifier = Modifier.padding(horizontal = Dimens.space1),
                        )
                    }
                }

                SelectedMomentCard(selected, unit)

                StatGrid(
                    cells = listOf(
                        StatCell("${slices.size}", "интервалов"),
                        StatCell(
                            SpectrumFormat.accumulationClock(slices.sumOf { it.intervalSeconds }),
                            "охвачено",
                        ),
                        StatCell("${stepSeconds} с", "шаг колонки"),
                        StatCell("≈2 ч", "в памяти"),
                    ),
                )
                if (!connected) {
                    Text(
                        text = "нет соединения — показана записанная история",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                Text(
                    text = "история живёт в памяти приложения (~2 ч) и не сохраняется — " +
                        "долговременная запись это снимки спектра в Истории",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

/**
 * Почему шаг именно такой — одной фразой и с числом, на котором он стоит.
 * Иначе укрупнение колонок выглядит как потеря разрешения без причины.
 */
private fun stepReason(slices: List<SpectrogramSlice>, stepSeconds: Long): String {
    val seconds = slices.sumOf { it.intervalSeconds }
    val counts = slices.sumOf { it.totalCounts.toDouble() }
    if (seconds <= 0L || counts <= 0.0) return "накапливаем статистику"
    val perBand = counts / seconds * stepSeconds / Spectrogram.BAND_COUNT
    val atPoll = counts / seconds * Spectrogram.DISPLAY_STEPS_SECONDS.first() /
        Spectrogram.BAND_COUNT
    return if (stepSeconds <= Spectrogram.DISPLAY_STEPS_SECONDS.first()) {
        "≈${Uncertainty.num1(perBand.toFloat())} имп на полосу в колонке"
    } else {
        "≈${Uncertainty.num1(perBand.toFloat())} имп на полосу вместо " +
            "${Uncertainty.num1(atPoll.toFloat())} при шаге опроса"
    }
}

@Composable
private fun SelectedMomentCard(selected: SpectrogramColumn?, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        if (selected == null) {
            Text(
                text = "тапните по столбцу — здесь появятся доза и счёт того момента",
                style = type.footnote,
                color = colors.muted,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeOfDay(selected.startMillis),
                        style = type.value,
                        color = colors.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "измерено ${selected.seconds} с",
                        style = type.footnote,
                        color = colors.ink2,
                    )
                }
                Text(
                    text = listOfNotNull(
                        selected.doseMicroSvH?.let { DoseFormat.rateWithUnit(it, unit) },
                        selected.cps?.let { "${Uncertainty.num1(it)} с⁻¹" },
                        "${selected.totalCounts.toInt()} имп в колонке",
                        selected.meanEnergyKeV?.let { "ср. энергия ${it.toInt()} кэВ" },
                    ).joinToString(" · "),
                    style = type.valueSmall,
                    color = colors.ink2,
                )
            }
        }
    }
}

@Composable
private fun LegendRamp() {
    val type = LocalAppTypography.current
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = "меньше", style = type.axis, color = colors.muted)
        waterfallLegendColors().forEach { color ->
            Box(
                Modifier
                    .size(width = 12.dp, height = 7.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
        Text(text = "больше", style = type.axis, color = colors.muted)
    }
}

private fun spanLabel(slices: List<SpectrogramSlice>): String {
    if (slices.isEmpty()) return "нет данных"
    val from = slices.first().timestampMillis
    val to = slices.last().timestampMillis
    val minutes = ((to - from) / 60_000L).coerceAtLeast(0)
    return if (minutes < 1) "< 1 мин" else "окно $minutes мин"
}

private fun timeOfDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(HH_MM_SS)
