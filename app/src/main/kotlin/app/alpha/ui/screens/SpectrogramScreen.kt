package app.alpha.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.alpha.AppGraph
import app.alpha.analysis.Spectrogram
import app.alpha.analysis.SpectrogramColumn
import app.alpha.analysis.SpectrogramSlice
import app.alpha.data.DoseUnitSetting
import app.alpha.device.ConnectionState
import app.alpha.ui.components.AppCloseButton
import app.alpha.ui.components.Hint
import app.alpha.ui.components.Card
import app.alpha.ui.components.Chip
import app.alpha.ui.components.DisclosureRow
import app.alpha.ui.components.EntityHeader
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.StatGrid
import app.alpha.ui.components.WaterfallChart
import app.alpha.ui.components.WaterfallProbe
import app.alpha.ui.components.WaterfallSpec
import app.alpha.ui.components.waterfallLegendColors
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.InstrumentCapability
import app.alpha.ui.logic.SpectrumFormat
import app.alpha.ui.logic.TimeAxis
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SpectrogramCatalogue
import app.alpha.ui.text.SpectrogramRu
import app.alpha.ui.text.SpectrogramStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import kotlinx.coroutines.launch

/** Rendered column cap: slices merge beyond this (bitmap stays legible). */
private const val MAX_COLUMNS = 240

/**
 * Самое узкое окно картинки. **Инженерный параметр отображения**: при пяти
 * минутах пятисекундные столбцы ещё различимы по ширине.
 */
private const val MIN_WINDOW_MILLIS = 5L * 60_000L

/** Ступени окна; подпись собирается каталогом по числу и единице. */
private data class Window(val amount: Int, val hours: Boolean, val millis: Long)

private val WINDOWS: List<Window> = listOf(
    Window(1, hours = false, millis = 60_000L),
    Window(5, hours = false, millis = 5L * 60_000L),
    Window(15, hours = false, millis = 15L * 60_000L),
    Window(1, hours = true, millis = 3_600_000L),
    Window(2, hours = true, millis = 2L * 3_600_000L),
)

private fun Window.label(t: SpectrogramStrings): String =
    if (hours) t.windowHours(amount) else t.windowMinutes(amount)

/**
 * Вид картинки, переживающий переход в полный экран и обратно: окно времени и
 * режим принадлежат виду, а не месту отрисовки. Примитивы, потому что
 * состояние хранится в `rememberSaveable` выше по дереву; ноль = не выбрано.
 */
data class SpectrogramViewOptions(
    /** 0 = окно подбирается по длине записи. */
    val windowMillis: Long = 0L,
    val shapeMode: Boolean = false,
)

/**
 * Спектрограмма (SPEC «Spectrogram», Advanced): Energy × Time × Intensity.
 * Столбец = сумма опросов, попавших в ячейку сетки времени; строки — энергия
 * 20–3000 кэВ на выбранной оси, яркость — интенсивность.
 */
@Composable
fun SpectrogramScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    options: SpectrogramViewOptions = SpectrogramViewOptions(),
    onOptionsChange: (SpectrogramViewOptions) -> Unit = {},
    /** Полноэкранный режим: поле занимает дисплей, карточка момента — поверх. */
    fullscreen: Boolean = false,
    onOpenFullscreen: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SpectrogramCatalogue.of(strings.language)
    val hub = graph.spectrumHub

    val connectionState by graph.serviceStatus.connection.collectAsState()

    BackHandler { onBack() }
    val scope = rememberCoroutineScope()

    // Картинке нужен пятисекундный опрос спектра: экран подписывается на хаб.
    DisposableEffect(hub) {
        hub.attach()
        onDispose { hub.detach() }
    }

    val connection by graph.serviceStatus.connection.collectAsState()
    val connected = connection is ConnectionState.Connected
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val energyScaleId by graph.settings.spectrogramEnergyScale.collectAsState(initial = "log")
    val energyScale = if (energyScaleId == "linear") {
        Spectrogram.EnergyScale.LINEAR
    } else {
        Spectrogram.EnergyScale.LOG
    }

    val liveSlices by graph.spectrogramStore.slices.collectAsState()
    // Если служба не поднимала окно из базы, это делает экран. Вызов
    // идемпотентен: при непустом кольце он ничего не трогает.
    var storedSlices by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        graph.spectrogramStore.restore(System.currentTimeMillis())
        storedSlices = graph.spectrogramRepository.count()
    }
    // Паузы нет: окно всегда кончается «сейчас», момент разбирается курсором.
    val slices = liveSlices

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    // Прицел по энергии существует только во время касания.
    var probeFraction by remember { mutableStateOf<Float?>(null) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }

    // Режим «форма» — нормировка внутри колонки: показывает состав, но не
    // абсолютную интенсивность.
    val shapeMode = options.shapeMode

    // Колонки строятся по СЕТКЕ ВРЕМЕНИ с шагом из статистики: при фоне
    // пятисекундная колонка это ≈1 импульс на полосу, то есть пуассоновский
    // шум. Окно картинки не уже MIN_WINDOW_MILLIS; недостающее время
    // остаётся пустым.
    val dataFromMillis =
        slices.firstOrNull()?.let { it.timestampMillis - it.intervalSeconds * 1000L }
    val toMillis = slices.lastOrNull()?.timestampMillis
    // null = ступень подбирается по длине записи, но не уже пяти минут.
    val windowChoice = options.windowMillis.takeIf { it > 0L }
    val autoWindow = if (dataFromMillis != null && toMillis != null) {
        val span = (toMillis - dataFromMillis).coerceAtLeast(MIN_WINDOW_MILLIS)
        WINDOWS.firstOrNull { it.millis >= span }?.millis ?: WINDOWS.last().millis
    } else {
        MIN_WINDOW_MILLIS
    }
    val windowMillis = windowChoice ?: autoWindow
    val fromMillis = toMillis?.let { it - windowMillis }
    // Ключ памяти включает ОКНО: иначе шаг и колонки остаются от прежнего
    // окна до прихода следующего среза.
    val stepSeconds = remember(slices, windowMillis) {
        if (fromMillis == null || toMillis == null) {
            Spectrogram.DISPLAY_STEPS_SECONDS.first()
        } else {
            Spectrogram.displayStepSeconds(slices, toMillis - fromMillis, MAX_COLUMNS)
        }
    }
    val columnsData = remember(slices, windowMillis, stepSeconds) {
        if (fromMillis == null || toMillis == null) {
            emptyList()
        } else {
            Spectrogram.grid(slices, fromMillis, toMillis, stepSeconds * 1000L)
        }
    }
    // Полосы объединяются, пока в них не наберётся статистика; нарезка одна
    // на всё окно, иначе она меняется от столбца к столбцу.
    val bandGroups = remember(columnsData) { Spectrogram.bandGroups(columnsData) }
    // Верх шкалы считается по видимым данным и подписан числом у самой шкалы.
    val scaleTop = remember(columnsData, bandGroups) {
        Spectrogram.scaleTop(columnsData, bandGroups)
    }
    // Курсор указывает на НОМЕР колонки: после смены окна номер означает
    // другой момент времени.
    LaunchedEffect(windowMillis) { selectedIndex = null }
    val selected = selectedIndex?.let { columnsData.getOrNull(it) }

    // Ось энергии переключается чипом (состояние), очистка записи живёт в
    // Настройках → Данные.
    val helpChip: @Composable () -> Unit = {
        Chip(text = "i", color = colors.ink2, onClick = { infoOpen = true })
    }
    // Чип называет ТЕКУЩУЮ шкалу, нажатие переключает.
    val scaleChip: @Composable () -> Unit = {
        Chip(
            text = if (energyScale == Spectrogram.EnergyScale.LOG) t.axisLog else t.axisLinear,
            color = colors.ink2,
            onClick = {
                scope.launch {
                    graph.settings.setSpectrogramEnergyScale(
                        if (energyScale == Spectrogram.EnergyScale.LOG) "linear" else "log",
                    )
                }
            },
        )
    }

    val spec = WaterfallSpec(
        columns = columnsData,
        scaleTop = scaleTop,
        shapeMode = shapeMode,
        bandGroups = bandGroups,
        energyScale = energyScale,
        selectedIndex = selectedIndex,
        timeLabels = TimeAxis.labels(fromMillis ?: 0L, toMillis ?: 0L),
        energyUnit = t.energyUnit,
        probe = probeFraction?.let { fraction ->
            WaterfallProbe(
                energyFraction = fraction,
                lines = probeLines(
                    column = selected,
                    fraction = fraction,
                    scale = energyScale,
                    bandGroups = bandGroups,
                    t = t,
                ),
            )
        },
    )
    val onCursor: (Int, Float?) -> Unit = { index, fraction ->
        // Нажатие в ту же колонку снимает курсор; ведение пальцем — выбор.
        selectedIndex = if (fraction == null && selectedIndex == index) null else index
        probeFraction = fraction
    }
    val windowChips: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
        ) {
            for (window in WINDOWS) {
                val span = window.millis
                Chip(
                    text = window.label(t),
                    color = if (span == windowMillis) colors.dataText else colors.ink2,
                    selected = span == windowMillis,
                    onClick = { onOptionsChange(options.copy(windowMillis = span)) },
                )
            }
        }
    }

    if (fullscreen) {
        // Поле занимает дисплей; выбранный момент показывается карточкой
        // поверх поля.
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
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
                    AppCloseButton(onClose = onBack)
                    if (!connected && slices.isNotEmpty()) {
                        Chip(text = t.offlineTag, color = colors.ink2)
                    }
                    Spacer(Modifier.weight(1f))
                    helpChip()
                }
                // Картинка на приборе без спектрометрии остаётся честной как
                // «интенсивность во времени», но её полосы — не энергии, и
                // читать её как спектр нельзя. Ограничение стоит НА картинке.
                val connectedModel =
                    (connectionState as? ConnectionState.Connected)?.info?.model
                if (!InstrumentCapability.spectral(connectedModel)) {
                    Text(
                        text = t.notSpectrometer(connectedModel?.displayName.orEmpty()),
                        style = type.footnote,
                        color = colors.warn,
                        modifier = Modifier.padding(horizontal = Dimens.space2),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.space2)) {
                    WaterfallChart(
                        spec = spec,
                        onCursor = onCursor,
                        onCursorEnd = { probeFraction = null },
                    )
                    if (selected != null) {
                        MomentOverlay(
                            selected = selected,
                            stepSeconds = stepSeconds,
                            unit = unit,
                            t = t,
                            onDismiss = { selectedIndex = null },
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.space2, vertical = Dimens.space1),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space1),
                ) {
                    windowChips()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    ) {
                        Segmented(
                            options = listOf(t.modeIntensity, t.modeShape),
                            selectedIndex = if (shapeMode) 1 else 0,
                            onSelect = { onOptionsChange(options.copy(shapeMode = it == 1)) },
                            scrollable = true,
                        )
                        scaleChip()
                        LegendLine(
                            shapeMode = shapeMode,
                            scaleTop = scaleTop,
                            t = t,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    } else {
        // Обзор: картинка, период и шкала цвета. Разбор момента — в полном
        // экране.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.space3),
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            EntityHeader(
                title = t.title,
                onBack = onBack,
                trailing = {
                    if (!connected && slices.isNotEmpty()) {
                        Chip(text = t.offlineTag, color = colors.ink2)
                    }
                    helpChip()
                },
            )

            when {
                // Пустые состояния — одна строка о текущем положении дел.
                slices.isEmpty() && !connected -> Card(modifier = Modifier.fillMaxWidth()) {
                    Text(text = t.noLink, style = type.bodySmall, color = colors.ink2)
                }
                slices.isEmpty() -> Card(modifier = Modifier.fillMaxWidth()) {
                    Text(text = t.warmingUp, style = type.bodySmall, color = colors.muted)
                }
                else -> Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = Dimens.space2,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                    ) {
                        windowChips()
                        // Касание открывает картинку во весь экран; курсор в
                        // обзоре не ставится.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable(onClick = onOpenFullscreen),
                        ) {
                            WaterfallChart(spec = spec.copy(selectedIndex = null, probe = null))
                        }
                        LegendLine(shapeMode = shapeMode, scaleTop = scaleTop, t = t)
                    }
                }
            }

            // Частота записи — параметр опроса прибора, живёт в Настройках.
        }
    }

    // Справка — диалогом поверх экрана, чтобы не двигать картинку.
    if (infoOpen) {
        HelpDialog(
            slices = slices,
            stepSeconds = stepSeconds,
            bandGroups = bandGroups,
            fromMillis = fromMillis,
            toMillis = toMillis,
            storedSlices = storedSlices,
            connected = connected,
            energyScale = energyScale,
            onClose = { infoOpen = false },
            t = t,
        )
    }
}

/**
 * Подпись прицела: момент и энергия под пальцем, ниже — скорость счёта в той
 * энергетической группе, в которую он попал (картинка красит группу целиком).
 */
private fun probeLines(
    column: SpectrogramColumn?,
    fraction: Float,
    scale: Spectrogram.EnergyScale,
    bandGroups: List<IntRange>,
    t: SpectrogramStrings,
): List<String> {
    val keV = Spectrogram.energyAtFraction(fraction, scale)
    val time = column?.let { timeOfDay(it.startMillis) }
    val head = listOfNotNull(time, t.energyValue(keV.toInt())).joinToString(" · ")
    val band = Spectrogram.bandOfEnergy(keV)
    val group = band?.let { b -> bandGroups.firstOrNull { b in it } }
    val rate = if (column != null && group != null) column.groupRate(group) else null
    return listOfNotNull(head, rate?.let { t.legendRate(Uncertainty.num2(it)) })
}

/** Шаг колонки одной фразой с числом, на котором он стоит. */
private fun stepReason(
    slices: List<SpectrogramSlice>,
    stepSeconds: Long,
    s: SpectrogramStrings = SpectrogramRu,
): String {
    val seconds = slices.sumOf { it.intervalSeconds }
    val counts = slices.sumOf { it.totalCounts.toDouble() }
    if (seconds <= 0L || counts <= 0.0) return s.stepCollecting
    val perBand = counts / seconds * stepSeconds / Spectrogram.BAND_COUNT
    val atPoll = counts / seconds * Spectrogram.DISPLAY_STEPS_SECONDS.first() /
        Spectrogram.BAND_COUNT
    return if (stepSeconds <= Spectrogram.DISPLAY_STEPS_SECONDS.first()) {
        s.stepPerBand(Uncertainty.num1(perBand.toFloat()))
    } else {
        s.stepPerBandInstead(
            Uncertainty.num1(perBand.toFloat()),
            Uncertainty.num1(atPoll.toFloat()),
        )
    }
}

/**
 * Карточка выбранного момента поверх поля: время, измеренное окно и величины
 * с подписями. Нажатие снимает курсор. Последняя приглушённая строка несёт
 * сумму импульсов и неполное покрытие ячейки.
 */
@Composable
private fun BoxScope.MomentOverlay(
    selected: SpectrogramColumn,
    stepSeconds: Long,
    unit: DoseUnitSetting,
    t: SpectrogramStrings,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(Dimens.space2)
            .clickable(onClick = onDismiss),
        contentPadding = Dimens.space2,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeOfDay(selected.startMillis),
                    style = type.value,
                    color = colors.ink,
                )
                Spacer(Modifier.weight(1f))
                // Окно — измеренное время, а не ширина ячейки сетки.
                Text(
                    text = t.windowSeconds(selected.seconds),
                    style = type.footnote,
                    color = colors.ink2,
                )
            }
            StatGrid(
                cells = listOf(
                    StatCell(
                        value = selected.doseMicroSvH
                            ?.let { DoseFormat.rate(it, unit) }
                            ?: "—",
                        key = t.keyDoseRate,
                    ),
                    StatCell(
                        value = selected.cps?.let { t.countsPerSecond(Uncertainty.num1(it)) }
                            ?: "—",
                        key = t.keyCount,
                    ),
                    StatCell(
                        value = selected.meanEnergyKeV?.let { t.energyValue(it.toInt()) } ?: "—",
                        key = t.keyMeanEnergy,
                    ),
                ),
            )
            Text(
                text = listOfNotNull(
                    t.countsInColumn(selected.totalCounts.toInt()),
                    t.partialColumn(selected.seconds, stepSeconds)
                        .takeIf { selected.seconds < stepSeconds },
                ).joinToString(" · "),
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}

/**
 * Справка: первый уровень — как читать картинку; параметры агрегации,
 * нормировки и хранения раскрываются отдельной строкой.
 */
@Composable
private fun HelpDialog(
    slices: List<SpectrogramSlice>,
    stepSeconds: Long,
    bandGroups: List<IntRange>,
    fromMillis: Long?,
    toMillis: Long?,
    /** Строк в базе; null — ещё не посчитано. */
    storedSlices: Int?,
    /** Связь с прибором; без неё показана записанная история. */
    connected: Boolean,
    energyScale: Spectrogram.EnergyScale,
    onClose: () -> Unit,
    t: SpectrogramStrings,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var technical by rememberSaveable { mutableStateOf(false) }
    val measuredSeconds = slices.sumOf { it.intervalSeconds }
    val windowSeconds = ((toMillis ?: 0L) - (fromMillis ?: 0L)) / 1000L
    val bandsPerGroup = if (bandGroups.isEmpty()) {
        1
    } else {
        Spectrogram.BAND_COUNT / bandGroups.size
    }
    Dialog(onDismissRequest = onClose) {
        Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t.helpTitle.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                AppCloseButton(onClose = onClose)
            }
            if (!connected) {
                Text(text = t.offlineHistory, style = type.bodySmall, color = colors.ink2)
            }
            for (line in t.howToRead) {
                Text(text = "· $line", style = type.bodySmall, color = colors.ink)
            }
            DisclosureRow(
                title = t.technicalTitle,
                expanded = technical,
                onToggle = { technical = !technical },
            ) {
                StatGrid(
                    cells = listOfNotNull(
                        StatCell("${slices.size}", t.statIntervals),
                        storedSlices?.let { StatCell("$it", t.statStored) },
                        StatCell(
                            SpectrumFormat.accumulationClock(measuredSeconds),
                            t.statRecorded,
                        ),
                        StatCell(t.secondsValue(stepSeconds), t.statColumnStep),
                        StatCell("${bandGroups.size}", t.statBands),
                    ),
                )
                if (windowSeconds > 0 && measuredSeconds < windowSeconds * 95 / 100) {
                    Text(
                        text = t.coverageNote(
                            SpectrumFormat.accumulationClock(measuredSeconds),
                            SpectrumFormat.accumulationClock(windowSeconds),
                        ),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                Hint(text = t.stepNote(stepReason(slices, stepSeconds, t)))
                if (bandsPerGroup > 1) {
                    Text(
                        text = t.bandsMerged(
                            bandsPerGroup,
                            Spectrogram.BAND_COUNT,
                            Spectrogram.MIN_BAND_COUNTS.toInt(),
                        ),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                Hint(text = t.intensityNote)
                Hint(text = t.shapeNote)
                Text(
                    text = t.energyRangeNote(
                        Spectrogram.MIN_KEV.toInt(),
                        Spectrogram.MAX_KEV.toInt(),
                    ),
                    style = type.footnote,
                    color = colors.muted,
                )
                Text(
                    text = if (energyScale == Spectrogram.EnergyScale.LOG) {
                        t.energyScaleLogNote
                    } else {
                        t.energyScaleLinearNote
                    },
                    style = type.footnote,
                    color = colors.muted,
                )
                // Отказ метода: показывать нечего, потому что нет спектра.
                Text(text = t.noStoredSpectrum, style = type.footnote, color = colors.muted)
                // Сброс накопления обнуляет сумму прибора, записанное здесь
                // остаётся.
                Hint(text = t.recordNote)
                // Частота записи и её стойкость — данные о том, что именно
                // сохранено.
                Text(text = t.backgroundNote, style = type.footnote, color = colors.muted)
            }
        }
        }
    }
}

/** Строка о цвете для обзора: величина, единица и концы шкалы. */
@Composable
private fun LegendLine(
    shapeMode: Boolean,
    scaleTop: Float,
    t: SpectrogramStrings,
    modifier: Modifier = Modifier,
) {
    val type = LocalAppTypography.current
    val colors = LocalAppColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = if (shapeMode) t.legendShapeTitle else t.legendIntensityTitle,
            style = type.axis,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = t.legendZero, style = type.axis, color = colors.muted)
        ColorRamp()
        Text(
            text = if (shapeMode) t.legendColumnMax else t.legendRate(Uncertainty.num1(scaleTop)),
            style = type.axis,
            color = colors.muted,
        )
    }
}

/** Непрерывная полоса: шкала интенсивности непрерывна, ступеней нет. */
@Composable
private fun ColorRamp() {
    Box(
        Modifier
            .size(width = 56.dp, height = 8.dp)
            .background(
                Brush.horizontalGradient(waterfallLegendColors()),
                RoundedCornerShape(2.dp),
            ),
    )
}
