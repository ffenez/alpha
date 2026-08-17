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
 * Самое узкое окно картинки. **Инженерный параметр отображения**: пять минут
 * это тот масштаб, на котором пятисекундные столбцы ещё различимы, а короткая
 * запись честно выглядит короткой записью, а не длинной историей.
 */
private const val MIN_WINDOW_MILLIS = 5L * 60_000L

/**
 * Окна картинки: то же управление, что у графика дозы, только короче. Подпись
 * ступени собирается каталогом по числу и единице — «15м» и «15m» отличаются
 * только буквой, и держать их в двух списках значило бы разъезжание длин.
 */
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
 * Вид картинки, который переживает переход в полный экран и обратно.
 *
 * Человек тапнул по тому, что видел, и увидеть обязан то же самое, только
 * крупнее: окно времени, режим и верх шкалы принадлежат ВИДУ, а не месту, где
 * он нарисован. Примитивы, а не объекты, потому что состояние живёт в
 * `rememberSaveable` выше по дереву; ноль означает «не выбрано».
 */
data class SpectrogramViewOptions(
    /** 0 = окно подбирается по длине записи. */
    val windowMillis: Long = 0L,
    val shapeMode: Boolean = false,
    /** 0 = верх шкалы считается по видимому окну («Авто»). */
    val fixedTop: Float = 0f,
    val paused: Boolean = false,
)

/**
 * Спектрограмма (SPEC «Spectrogram», Advanced): Energy × Time × Intensity —
 * когда во время прогулки или измерения появился спектральный компонент.
 * Столбец = сумма опросов, попавших в ячейку сетки времени; строки — энергия
 * 20–3000 кэВ на выбранной оси, яркость — интенсивность. Под картинкой —
 * своя полоса мощности дозы на той же оси времени; курсор один на обе.
 */
@Composable
fun SpectrogramScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    options: SpectrogramViewOptions = SpectrogramViewOptions(),
    onOptionsChange: (SpectrogramViewOptions) -> Unit = {},
    /**
     * Полноэкранный режим: поле владеет дисплеем, всё остальное — узкие полосы
     * управления и карточка поверх поля.
     */
    fullscreen: Boolean = false,
    onOpenFullscreen: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SpectrogramCatalogue.of(strings.language)
    val hub = graph.spectrumHub

    BackHandler { onBack() }
    val scope = rememberCoroutineScope()

    // The waterfall needs the 5 s spectrum poll: attach as a hub watcher.
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
    // История переживает процесс: если служба ещё не поднимала окно из базы
    // (приложение открыли без запущенного измерения), это делает экран. Вызов
    // идемпотентен — при непустом кольце он ничего не трогает.
    var storedSlices by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        graph.spectrogramStore.restore(System.currentTimeMillis())
        storedSlices = graph.spectrogramRepository.count()
    }
    val paused = options.paused
    // While paused the displayed history freezes; recording continues.
    var frozen by remember { mutableStateOf<List<SpectrogramSlice>?>(null) }
    // Пауза приехала вместе с видом (переход в полный экран) — картинка
    // замирает здесь и сейчас: замороженного списка у нового поля ещё нет.
    LaunchedEffect(paused, liveSlices.isNotEmpty()) {
        if (paused && frozen == null) frozen = liveSlices
        if (!paused) frozen = null
    }
    val slices = if (paused) frozen ?: liveSlices else liveSlices

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    // Прицел по энергии живёт только под пальцем: постоянный маркер означал бы
    // выбранную энергию, а её никто не выбирал.
    var probeFraction by remember { mutableStateOf<Float?>(null) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }

    // Режим «форма» — нормировка внутри колонки. По умолчанию выключен: он
    // показывает состав, но уравнивает слабый спектр с сильным.
    val shapeMode = options.shapeMode
    // Верх цветовой шкалы, зафиксированный человеком: пока он задан, одинаковая
    // интенсивность красится одинаково при любом окне. Ноль = «Авто», верх
    // считается по видимым данным и меняется вместе с ними.
    val fixedTop = options.fixedTop.takeIf { it > 0f }

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
    val windowChoice = options.windowMillis.takeIf { it > 0L }
    val autoWindow = if (dataFromMillis != null && toMillis != null) {
        val span = (toMillis - dataFromMillis).coerceAtLeast(MIN_WINDOW_MILLIS)
        WINDOWS.firstOrNull { it.millis >= span }?.millis ?: WINDOWS.last().millis
    } else {
        MIN_WINDOW_MILLIS
    }
    val windowMillis = windowChoice ?: autoWindow
    val fromMillis = toMillis?.let { it - windowMillis }
    // Ключ памяти обязан включать ОКНО: без него выбор новой ступени менял
    // только ось, а шаг колонки и сами колонки оставались от прежнего окна —
    // картинка перестраивалась лишь со следующим срезом, то есть до пяти
    // секунд спустя, и это читалось как «ступень не работает».
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
    // Энергетические полосы объединяются, пока в них не наберётся статистика:
    // почти пустые полосы дают случайные светлые и тёмные строчки, а глаз
    // читает их как спектральную структуру. Нарезка одна на всё окно, иначе
    // она прыгала бы от столбца к столбцу.
    val bandGroups = remember(columnsData) { Spectrogram.bandGroups(columnsData) }
    val autoTop = remember(columnsData, bandGroups) {
        Spectrogram.scaleTop(columnsData, bandGroups)
    }
    val scaleTop = fixedTop ?: autoTop
    // Курсор указывает на НОМЕР колонки; после смены окна номер означает
    // другой момент времени, поэтому выбор снимается.
    LaunchedEffect(windowMillis) { selectedIndex = null }
    val selected = selectedIndex?.let { columnsData.getOrNull(it) }

    // Вместо меню редких действий — знак справки: единственное, что тут нужно
    // редко, это «как читать спектрограмму». Ось энергии переключается чипом
    // рядом со шкалой (состояние, а не команда в меню), а очистка записи
    // переехала в Настройки → Данные, к остальным решениям про хранение.
    val helpChip: @Composable () -> Unit = {
        Chip(text = "i", color = colors.ink2, onClick = { infoOpen = true })
    }
    // Чип называет ТЕКУЩУЮ шкалу, а не то, чем она станет: то же правило, что
    // у «лог/лин» на графике дозы.
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
        // Нажатие в ту же колонку снимает курсор; ведение пальцем — всегда
        // выбор.
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
            Spacer(Modifier.weight(1f))
            // Пауза называет себя сама: на паузе чип подписан словом, потому
            // что «показ остановлен, а запись идёт» — факт, который нельзя
            // оставить значку.
            Chip(
                text = if (paused) "▶ ${t.pausedTag}" else "Ⅱ",
                color = if (paused) colors.dataText else colors.ink2,
                selected = paused,
                onClick = { onOptionsChange(options.copy(paused = !paused)) },
            )
        }
    }

    if (fullscreen) {
        // Поле владеет экраном: картинка от края до края, узкие полосы
        // управления сверху и снизу, а всё, что говорит о выбранном моменте, —
        // карточкой ПОВЕРХ поля. Постоянных полос текста под полем нет.
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
                    Chip(text = "✕", color = colors.ink2, onClick = onBack)
                    if (!connected && slices.isNotEmpty()) {
                        Chip(text = t.offlineTag, color = colors.ink2)
                    }
                    Spacer(Modifier.weight(1f))
                    helpChip()
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.space2)) {
                    WaterfallChart(
                        spec = spec,
                        onCursor = onCursor,
                        onCursorEnd = { probeFraction = null },
                    )
                    // Карточка момента лежит поверх поля у верхнего края: под
                    // полем она забирала бы высоту всегда, а нужна только
                    // тогда, когда курсор поставлен.
                    if (selected != null) {
                        MomentOverlay(
                            selected = selected,
                            unit = unit,
                            t = t,
                            onDetails = { detailsOpen = true },
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
                        LegendRamp(
                            shapeMode = shapeMode,
                            scaleTop = scaleTop,
                            fixed = fixedTop != null,
                            onToggleFixed = {
                                onOptionsChange(
                                    options.copy(fixedTop = if (fixedTop == null) autoTop else 0f),
                                )
                            },
                            t = t,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    } else {
        // Обзор: картинка и то, без чего её не прочесть, — период и цвет.
        // Разбор момента, режимы и подробности живут в полном экране, потому
        // что там для них есть высота; здесь они были бы полосами текста над
        // маленькой картинкой.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.space3),
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            // Шапка записи — как у остальных записей: имя, «⋮» с редкими
            // действиями. Состояние связи остаётся чипом рядом: это статус
            // экрана, и он не должен отнимать высоту у поля.
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
                // Пустые состояния — одна строка о том, что происходит сейчас.
                // Как устроена запись и где выбирается её частота, рассказывает
                // справка: объяснение не становится содержимым экрана.
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
                        // Касание по картинке открывает её во весь экран —
                        // там же и разбирают момент. Курсор в обзоре не
                        // ставится: на этой высоте карточка момента съела бы
                        // саму картинку.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable(onClick = onOpenFullscreen),
                        ) {
                            WaterfallChart(spec = spec.copy(selectedIndex = null, probe = null))
                        }
                        // Одна строка о цвете: величина, единица и концы шкалы.
                        // Переключатель режима и фиксация верха — в полном
                        // экране, вместе с остальным разбором.
                        LegendLine(shapeMode = shapeMode, scaleTop = scaleTop, t = t)
                    }
                }
            }

            // Частота записи живёт в Настройках → Прибор: это параметр опроса
            // прибора, а не способ смотреть картинку, и на экране он занимал
            // место постоянно ради выбора, который делают один раз.
        }
    }

    // Справка лежит ПОВЕРХ экрана и доступна в любом состоянии: диалог не
    // двигает картинку, а объяснения на самом экране больше не живут.
    if (infoOpen) {
        HelpDialog(
            slices = slices,
            stepSeconds = stepSeconds,
            bandGroups = bandGroups,
            fromMillis = fromMillis,
            toMillis = toMillis,
            storedSlices = storedSlices,
            connected = connected,
            paused = paused,
            energyScale = energyScale,
            onClose = { infoOpen = false },
            t = t,
        )
    }
    if (detailsOpen && selected != null) {
        MomentDetailsDialog(
            column = selected,
            stepSeconds = stepSeconds,
            bandGroups = bandGroups,
            onClose = { detailsOpen = false },
            t = t,
        )
    }
}

/**
 * Что показывает прицел: момент и энергия под пальцем, а под ними — скорость
 * счёта в той энергетической группе, в которую он попал.
 *
 * Именно ГРУППЫ, а не одной полосы: картинка красит группу целиком, и число
 * обязано относиться к тому, что человек видит под маркером.
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

/**
 * Почему шаг именно такой — одной фразой и с числом, на котором он стоит.
 * Иначе укрупнение колонок выглядит как потеря разрешения без причины.
 */
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
 * Карточка выбранного момента ПОВЕРХ поля: время, реально измеренное окно и
 * физические величины с подписями.
 *
 * Поверх, а не под полем: она нужна только когда курсор поставлен, а под полем
 * забирала бы высоту всегда. «Импульсов в колонке» здесь нет — это техническая
 * величина отображения, и она осталась в подробностях: карточка нажимается.
 */
@Composable
private fun BoxScope.MomentOverlay(
    selected: SpectrogramColumn,
    unit: DoseUnitSetting,
    t: SpectrogramStrings,
    onDetails: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(Dimens.space2)
            .clickable(onClick = onDetails),
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
                // Окно — это реально измеренное время, а не ширина ячейки:
                // ячейка бывает покрыта не полностью, и делить счёт надо на
                // измеренное.
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
                            ?.let { DoseFormat.rateWithUnit(it, unit, s = strings) }
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
        }
    }
}

/**
 * Подробности момента: техническое, которое нужно редко и не имеет права
 * занимать основной уровень.
 */
@Composable
private fun MomentDetailsDialog(
    column: SpectrogramColumn,
    stepSeconds: Long,
    bandGroups: List<IntRange>,
    onClose: () -> Unit,
    t: SpectrogramStrings,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val bandsPerGroup = if (bandGroups.isEmpty()) 1 else Spectrogram.BAND_COUNT / bandGroups.size
    Dialog(onDismissRequest = onClose) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeOfDayWithSeconds(column.startMillis),
                        style = type.value,
                        color = colors.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Chip(text = "✕", color = colors.ink2, onClick = onClose)
                }
                StatGrid(
                    cells = listOf(
                        StatCell("${column.totalCounts.toInt()}", t.statColumnCounts),
                        StatCell(t.secondsValue(column.seconds), t.statMeasured),
                        StatCell(t.secondsValue(stepSeconds), t.statColumnStep),
                    ),
                )
                if (column.seconds < stepSeconds) {
                    Text(
                        text = t.partialColumn(column.seconds, stepSeconds),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                Text(
                    text = t.groupResolution(bandsPerGroup, bandGroups.size),
                    style = type.footnote,
                    color = colors.muted,
                )
                // Почему отсюда нельзя открыть полный спектр момента: его
                // просто нет — в истории лежат полосы, а не каналы.
                Hint(text = t.noStoredSpectrum)
            }
        }
    }
}

/**
 * Справка: сначала как читать картинку, потом — по желанию — как она устроена.
 *
 * Первый уровень отвечает на вопрос человека, который открыл экран впервые;
 * параметры агрегации, нормировки и хранения нужны редко и потому спрятаны за
 * одну строку раскрытия, а не вываливаются списком.
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
    /** Связь с прибором: без неё видна записанная история, и это надо сказать. */
    connected: Boolean,
    /** Показ остановлен — но запись при этом продолжается. */
    paused: Boolean,
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
                Chip(text = "✕", color = colors.ink2, onClick = onClose)
            }
            if (!connected) {
                Text(text = t.offlineHistory, style = type.bodySmall, color = colors.ink2)
            }
            if (paused) {
                Text(text = t.paused, style = type.bodySmall, color = colors.ink2)
            }
            // Первый уровень: как читать. Одно правило — одна строка.
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
                        // Строки в базе — видимое доказательство, что история
                        // не исчезает вместе с процессом.
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
                Hint(text = t.scaleModeNote)
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
                Hint(text = t.noStoredSpectrum)
                // Почему сброс накопления не стирает эту картинку:
                // спектрограмма — запись прошедшего, а не сумма, которую
                // держит прибор.
                Hint(text = t.recordNote)
                Hint(text = t.backgroundNote)
            }
        }
        }
    }
}

/**
 * Одна строка о цвете для обзора: величина, единица и концы шкалы.
 *
 * Обзор объясняет картинку, а не управляет ею: режим и фиксация верха живут в
 * полном экране, где для них есть место.
 */
@Composable
private fun LegendLine(shapeMode: Boolean, scaleTop: Float, t: SpectrogramStrings) {
    val type = LocalAppTypography.current
    val colors = LocalAppColors.current
    Row(
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

/** Непрерывная полоса цвета: шкала интенсивности непрерывна, ступеней нет. */
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

@Composable
private fun LegendRamp(
    shapeMode: Boolean,
    scaleTop: Float,
    fixed: Boolean,
    onToggleFixed: () -> Unit,
    t: SpectrogramStrings,
    modifier: Modifier = Modifier,
) {
    val type = LocalAppTypography.current
    val colors = LocalAppColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Шкала называет ВЕЛИЧИНУ и единицу; в режиме формы величины нет,
            // и подпись говорит именно это, а не «имп/с».
            Text(
                text = if (shapeMode) t.legendShapeTitle else t.legendIntensityTitle,
                style = type.axis,
                color = colors.muted,
                modifier = Modifier.weight(1f),
            )
            // Верх шкалы: «Авто» пересчитывается по видимому окну, «Фикс»
            // держит его, чтобы одинаковый цвет означал одинаковую
            // интенсивность и после смены окна.
            if (!shapeMode) {
                Chip(
                    text = if (fixed) t.scaleFixed else t.scaleAuto,
                    color = if (fixed) colors.dataText else colors.ink2,
                    selected = fixed,
                    onClick = onToggleFixed,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(text = t.legendZero, style = type.axis, color = colors.muted)
            ColorRamp()
            Text(
                text = if (shapeMode) {
                    t.legendColumnMax
                } else {
                    t.legendRate(Uncertainty.num1(scaleTop))
                },
                style = type.axis,
                color = colors.muted,
            )
        }
    }
}
