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
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.rememberCoroutineScope
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
import app.radiacode.ui.components.Segmented
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

/**
 * Самое узкое окно картинки. **Инженерный параметр отображения**: пять минут
 * это тот масштаб, на котором пятисекундные столбцы ещё различимы, а короткая
 * запись честно выглядит короткой записью, а не длинной историей.
 */
private const val MIN_WINDOW_MILLIS = 5L * 60_000L

/** Окна картинки: то же управление, что у графика дозы, только короче. */
private val WINDOWS: List<Pair<String, Long>> = listOf(
    "1м" to 60_000L,
    "5м" to 5L * 60_000L,
    "15м" to 15L * 60_000L,
    "1ч" to 3_600_000L,
    "2ч" to 2L * 3_600_000L,
)

/**
 * Честный ответ на «почему в фоне почти ничего»: спектр в фоне опрашивается
 * раз в 10 минут — тем же опросом, который кормит радоновый индикатор. Чаще
 * нельзя дёшево: запрос спектра идёт по тому же однозапросному каналу, что и
 * секундные показания, и его учащение отнимает пропускную способность у самих
 * измерений. Частая запись включается, когда экран открыт.
 */
private const val BACKGROUND_NOTE =
    "В фоне спектр опрашивается раз в 10 минут — этим же опросом живёт радоновый " +
        "индикатор. Пока открыт экран Спектра или Спектрограммы, запись идёт раз в 5 с. " +
        "История спектрограммы хранится только в памяти приложения."

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
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hintSeen by graph.settings.spectrogramHintSeen.collectAsState(initial = true)
    // Экран существует ради одной картинки — она и должна занимать его.
    val chartHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
        .coerceIn(260.dp, 520.dp)

    // Режим «форма» — нормировка внутри колонки. По умолчанию выключен: он
    // показывает состав, но уравнивает слабый спектр с сильным.
    var shapeMode by rememberSaveable { mutableStateOf(false) }

    // Колонки строятся по СЕТКЕ ВРЕМЕНИ с шагом, который выбирается по
    // статистике: при фоне пятисекундная колонка это ≈1 импульс на полосу, то
    // есть шум, который глаз читает как линии.
    // Картинка имеет МИНИМАЛЬНОЕ окно: четыре пятисекундных столбца, растянутых
    // на весь экран, читаются как «длинная запись с четырьмя состояниями», хотя
    // это двадцать секунд. Недостающее время остаётся пустым — ровно так же,
    // как затенённая область «сюда данные не доходят» на графике дозы.
    val dataFromMillis =
        slices.firstOrNull()?.let { it.timestampMillis - it.intervalSeconds * 1000L }
    val toMillis = slices.lastOrNull()?.timestampMillis
    // null = окно подбирается само: самая узкая ступень, которая покрывает
    // запись, но не уже пяти минут — двадцать секунд, растянутые на экран,
    // читаются как длинная история.
    var windowChoice by rememberSaveable { mutableStateOf<Long?>(null) }
    val autoWindow = if (dataFromMillis != null && toMillis != null) {
        val span = (toMillis - dataFromMillis).coerceAtLeast(MIN_WINDOW_MILLIS)
        WINDOWS.firstOrNull { it.second >= span }?.second ?: WINDOWS.last().second
    } else {
        MIN_WINDOW_MILLIS
    }
    val windowMillis = windowChoice ?: autoWindow
    val fromMillis = toMillis?.let { it - windowMillis }
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
    // Энергетические полосы объединяются, пока в них не наберётся статистика:
    // почти пустые полосы дают случайные светлые и тёмные строчки, а глаз
    // читает их как спектральную структуру. Нарезка одна на всё окно, иначе
    // она прыгала бы от столбца к столбцу.
    val bandGroups = remember(columnsData) { Spectrogram.bandGroups(columnsData) }
    val scaleTop = remember(columnsData, bandGroups) {
        Spectrogram.scaleTop(columnsData, bandGroups)
    }
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
            Chip(text = "i", color = colors.ink2, onClick = { infoOpen = true })
        }

        if (paused) {
            Text(
                text = "показ остановлен · запись продолжается",
                style = type.footnote,
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
                        text = BACKGROUND_NOTE,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                }
            }
            slices.isEmpty() -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(
                        text = "накапливаем первые интервалы… столбцы появятся через ~10 с",
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                    Text(text = BACKGROUND_NOTE, style = type.footnote, color = colors.muted)
                }
            }
            else -> {
                Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space2) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        // Управление живёт над самой картинкой: окно времени и
                        // пауза — это про неё, а не про экран.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
                        ) {
                            for ((label, span) in WINDOWS) {
                                Chip(
                                    text = label,
                                    color = if (span == windowMillis) {
                                        colors.dataText
                                    } else {
                                        colors.ink2
                                    },
                                    selected = span == windowMillis,
                                    onClick = { windowChoice = span },
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Chip(
                                text = if (paused) "▶" else "Ⅱ",
                                color = if (paused) colors.dataText else colors.ink2,
                                selected = paused,
                                onClick = {
                                    if (!paused) frozen = liveSlices
                                    paused = !paused
                                    if (!paused) frozen = null
                                },
                            )
                        }
                        WaterfallChart(
                            spec = WaterfallSpec(
                                columns = columnsData,
                                scaleTop = scaleTop,
                                shapeMode = shapeMode,
                                bandGroups = bandGroups,
                                selectedIndex = selectedIndex,
                                timeLabels = TimeAxis.labels(
                                    fromMillis ?: 0L,
                                    toMillis ?: 0L,
                                ),
                                stripValues = columnsData.map { it?.doseMicroSvH },
                            ),
                            height = chartHeight,
                            onTapColumn = { index ->
                                selectedIndex = if (selectedIndex == index) null else index
                                if (!hintSeen) scope.launch { graph.settings.setSpectrogramHintSeen() }
                            },
                        )
                        // Режим — это РЕЖИМ, а не действие: два физически
                        // разных вопроса к одним данным, «сколько» и «какого
                        // состава».
                        Segmented(
                            options = listOf("Интенсивность", "Форма"),
                            selectedIndex = if (shapeMode) 1 else 0,
                            onSelect = { shapeMode = it == 1 },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Шкала количественная: подписанные концы — это и есть
                        // объяснение цвета, отдельная фраза под ней была бы
                        // третьим повторением одного и того же.
                        LegendRamp(shapeMode, scaleTop)
                    }
                }

                SelectedMomentCard(selected, unit, showHint = !hintSeen)

                if (infoOpen) {
                    InfoCard(
                        slices = slices,
                        stepSeconds = stepSeconds,
                        bandGroups = bandGroups,
                        fromMillis = fromMillis,
                        toMillis = toMillis,
                        onClose = { infoOpen = false },
                    )
                }
                if (!connected) {
                    Text(
                        text = "нет соединения — показана записанная история",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
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
private fun SelectedMomentCard(
    selected: SpectrogramColumn?,
    unit: DoseUnitSetting,
    showHint: Boolean,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    // Подсказка «тапните по столбцу» — обучение, а не элемент интерфейса: она
    // живёт до первого касания и больше не возвращается. Пока курсора нет и
    // подсказка уже показана, карточки нет вовсе.
    if (selected == null && !showHint) return
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

/**
 * «Как это устроено» — под кнопкой «i», а не полосой мелкого текста под
 * картинкой: параметры агрегации нужны один раз, а высоту отнимали всегда.
 */
@Composable
private fun InfoCard(
    slices: List<SpectrogramSlice>,
    stepSeconds: Long,
    bandGroups: List<IntRange>,
    fromMillis: Long?,
    toMillis: Long?,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val measuredSeconds = slices.sumOf { it.intervalSeconds }
    val windowSeconds = ((toMillis ?: 0L) - (fromMillis ?: 0L)) / 1000L
    val bandsPerGroup = if (bandGroups.isEmpty()) {
        1
    } else {
        Spectrogram.BAND_COUNT / bandGroups.size
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Как построена картинка".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Chip(text = "✕", color = colors.ink2, onClick = onClose)
            }
            StatGrid(
                cells = listOf(
                    StatCell("${slices.size}", "интервалов"),
                    StatCell(SpectrumFormat.accumulationClock(measuredSeconds), "записи"),
                    StatCell("$stepSeconds с", "шаг колонки"),
                    StatCell("${bandGroups.size}", "полос"),
                ),
            )
            if (windowSeconds > 0 && measuredSeconds < windowSeconds * 95 / 100) {
                Text(
                    text = "записи ${SpectrumFormat.accumulationClock(measuredSeconds)} из " +
                        "${SpectrumFormat.accumulationClock(windowSeconds)} окна — остальное " +
                        "время прибор не писал, такие колонки пустые",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            Text(
                text = "Шаг колонки подобран по статистике: " + stepReason(slices, stepSeconds) +
                    ". Прибор опрашивается раз в 5 с, колонка складывает несколько опросов — " +
                    "импульсы суммируются, ничего не додумывается.",
                style = type.footnote,
                color = colors.muted,
            )
            if (bandsPerGroup > 1) {
                Text(
                    text = "Энергетические полосы объединены по $bandsPerGroup: при " +
                        "${Spectrogram.BAND_COUNT} полосах на них приходилось меньше " +
                        "${Spectrogram.MIN_BAND_COUNTS.toInt()} импульсов, и случайные " +
                        "светлые строчки читались бы как спектральные линии. Исходные " +
                        "каналы не меняются.",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            Text(
                text = "Интенсивность. Цвет соответствует скорости регистрации событий в " +
                    "энергетической полосе, имп/с. Для всего отображаемого окна — единая " +
                    "логарифмическая шкала, поэтому столбцы сравнимы между собой.",
                style = type.footnote,
                color = colors.muted,
            )
            Text(
                text = "Форма. Каждый временной спектр нормируется независимо. Режим " +
                    "предназначен для сравнения энергетического распределения и не " +
                    "показывает абсолютную интенсивность.",
                style = type.footnote,
                color = colors.muted,
            )
            Text(
                text = "Диапазон энергий ${Spectrogram.MIN_KEV.toInt()}–" +
                    "${Spectrogram.MAX_KEV.toInt()} кэВ, шкала полос геометрическая. " +
                    "Пустая колонка — измерений в этой ячейке не было; пропуски не " +
                    "заполняются.",
                style = type.footnote,
                color = colors.muted,
            )
            Text(text = BACKGROUND_NOTE, style = type.footnote, color = colors.muted)
        }
    }
}

@Composable
private fun LegendRamp(shapeMode: Boolean, scaleTop: Float) {
    val type = LocalAppTypography.current
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Шкала называет ВЕЛИЧИНУ и её концы. «Меньше ■■■ больше» в режиме
        // формы означало бы «меньше излучения», хотя это доля внутри столбца.
        Text(text = "0", style = type.axis, color = colors.muted)
        waterfallLegendColors().forEach { color ->
            Box(
                Modifier
                    .size(width = 12.dp, height = 7.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
        Text(
            text = if (shapeMode) {
                "макс. столбца"
            } else {
                "${Uncertainty.num1(scaleTop)} имп/с"
            },
            style = type.axis,
            color = colors.muted,
        )
    }
}

private fun timeOfDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(HH_MM_SS)
