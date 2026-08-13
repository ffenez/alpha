package app.radiacode.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SpectrogramCatalogue
import app.radiacode.ui.text.SpectrogramRu
import app.radiacode.ui.text.SpectrogramStrings
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

private val HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Спектрограмма (SPEC «Spectrogram», Advanced): Energy × Time × Intensity —
 * когда во время прогулки или измерения появился спектральный компонент.
 * Столбец = интервальный спектр одного опроса (накопление минус предыдущее,
 * 5 с), строки — энергия 20–3000 кэВ на геометрической шкале, яркость —
 * лог-нормировка внутри столбца. Внизу — синхронная полоса мощности дозы;
 * нажатие на столбец показывает дозу/CPS того момента.
 */
@Composable
fun SpectrogramScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SpectrogramCatalogue.of(strings.language)
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
    // История переживает процесс: если служба ещё не поднимала окно из базы
    // (приложение открыли без запущенного измерения), это делает экран. Вызов
    // идемпотентен — при непустом кольце он ничего не трогает.
    var storedSlices by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        graph.spectrogramStore.restore(System.currentTimeMillis())
        storedSlices = graph.spectrogramRepository.count()
    }
    var paused by rememberSaveable { mutableStateOf(false) }
    // While paused the displayed history freezes; recording continues.
    var frozen by remember { mutableStateOf<List<SpectrogramSlice>?>(null) }
    val slices = if (paused) frozen ?: liveSlices else liveSlices

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }

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
    val scaleTop = remember(columnsData, bandGroups) {
        Spectrogram.scaleTop(columnsData, bandGroups)
    }
    // Курсор указывает на НОМЕР колонки; после смены окна номер означает
    // другой момент времени, поэтому выбор снимается.
    LaunchedEffect(windowMillis) { selectedIndex = null }
    val selected = selectedIndex?.let { columnsData.getOrNull(it) }

    // Экран существует ради одной картинки, поэтому он НЕ прокручивается:
    // высоту забирает поле, а всё остальное — тонкие полосы над и под ним.
    // Прокрутка возвращала бы картинке фиксированную высоту и оставляла под
    // ней пустое место.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
        ) {
            AppButton(text = "← ${strings.back}", onClick = onBack)
            Spacer(Modifier.weight(1f))
            // Состояние связи — чип, а не строка под картинкой: это статус
            // экрана, и он не должен отнимать высоту у поля.
            if (!connected && slices.isNotEmpty()) {
                Chip(text = t.offlineTag, color = colors.ink2)
            }
            Chip(text = "i", color = colors.ink2, onClick = { infoOpen = true })
        }

        when {
            // Пустые состояния — одна строка о том, что происходит сейчас.
            // Как устроена запись и где выбирается её частота, рассказывает
            // «i»: объяснение не становится содержимым экрана.
            slices.isEmpty() && !connected -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = t.noLink, style = type.bodySmall, color = colors.ink2)
            }
            slices.isEmpty() -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = t.warmingUp, style = type.bodySmall, color = colors.muted)
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = Dimens.space2,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                    ) {
                        // Управление живёт над самой картинкой: окно времени и
                        // пауза — это про неё, а не про экран.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
                        ) {
                            for (window in WINDOWS) {
                                val span = window.millis
                                Chip(
                                    text = window.label(t),
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
                            // Пауза называет себя сама: на паузе чип подписан
                            // словом, потому что «показ остановлен, а запись
                            // идёт» — факт, который нельзя оставить значку.
                            Chip(
                                text = if (paused) "▶ ${t.pausedTag}" else "Ⅱ",
                                color = if (paused) colors.dataText else colors.ink2,
                                selected = paused,
                                onClick = {
                                    if (!paused) frozen = liveSlices
                                    paused = !paused
                                    if (!paused) frozen = null
                                },
                            )
                        }
                        // Поле забирает всю высоту, которая осталась от полос
                        // управления: точную величину знает только раскладка,
                        // поэтому она измеряется, а не назначается долей экрана.
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        ) {
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
                                    stripLabel = t.doseStripLabel(
                                        DoseFormat.rateUnitLabel(unit, s = strings),
                                    ),
                                    energyUnit = t.energyUnit,
                                ),
                                height = maxHeight,
                                onTapColumn = { index ->
                                    selectedIndex = if (selectedIndex == index) null else index
                                },
                            )
                        }
                        // Режим — это РЕЖИМ, а не действие: два физически
                        // разных вопроса к одним данным, «сколько» и «какого
                        // состава».
                        Segmented(
                            options = listOf(t.modeIntensity, t.modeShape),
                            selectedIndex = if (shapeMode) 1 else 0,
                            onSelect = { shapeMode = it == 1 },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Шкала количественная: подписанные концы — это и есть
                        // объяснение цвета, отдельная фраза под ней была бы
                        // третьим повторением одного и того же.
                        LegendRamp(shapeMode, scaleTop, t)
                    }
                }

                SelectedMomentCard(selected, unit, t)
            }
        }

        // Частота записи живёт в Настройках → Прибор: это параметр опроса
        // прибора, а не способ смотреть картинку, и на экране он занимал место
        // постоянно ради выбора, который делают один раз.
    }

    // Справка лежит ПОВЕРХ экрана и доступна в любом состоянии: диалог не
    // двигает картинку, а объяснения на самом экране больше не живут.
    if (infoOpen) {
        InfoDialog(
            slices = slices,
            stepSeconds = stepSeconds,
            bandGroups = bandGroups,
            fromMillis = fromMillis,
            toMillis = toMillis,
            storedSlices = storedSlices,
            connected = connected,
            paused = paused,
            onClose = { infoOpen = false },
            t = t,
        )
    }
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

@Composable
private fun SelectedMomentCard(
    selected: SpectrogramColumn?,
    unit: DoseUnitSetting,
    t: SpectrogramStrings,
) {
    val strings = LocalStrings.current
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    // Курсора нет — карточки нет: пустая карточка с инструкцией занимала место
    // постоянно ради подсказки, которую читают один раз.
    if (selected == null) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeOfDay(selected.startMillis),
                    style = type.value,
                    color = colors.ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = t.measuredSeconds(selected.seconds),
                    style = type.footnote,
                    color = colors.ink2,
                )
            }
            Text(
                text = listOfNotNull(
                    selected.doseMicroSvH?.let { DoseFormat.rateWithUnit(it, unit, s = strings) },
                    selected.cps?.let { t.countsPerSecond(Uncertainty.num1(it)) },
                    t.countsInColumn(selected.totalCounts.toInt()),
                    selected.meanEnergyKeV?.let { t.meanEnergy(it.toInt()) },
                ).joinToString(" · "),
                style = type.valueSmall,
                color = colors.ink2,
            )
        }
    }
}

/**
 * «Как это устроено» — под кнопкой «i», а не полосой мелкого текста под
 * картинкой: параметры агрегации нужны один раз, а высоту отнимали всегда.
 *
 * Диалог, а не карточка в потоке: картинка занимает весь экран, и объяснение
 * не имеет права её двигать.
 */
@Composable
private fun InfoDialog(
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
    onClose: () -> Unit,
    t: SpectrogramStrings,
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
                    text = t.infoTitle.uppercase(),
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
            StatGrid(
                cells = listOfNotNull(
                    StatCell("${slices.size}", t.statIntervals),
                    // Строки в базе — видимое доказательство, что история не
                    // исчезает вместе с процессом.
                    storedSlices?.let { StatCell("$it", t.statStored) },
                    StatCell(SpectrumFormat.accumulationClock(measuredSeconds), t.statRecorded),
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
            Text(
                text = t.stepNote(stepReason(slices, stepSeconds, t)),
                style = type.footnote,
                color = colors.muted,
            )
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
            Text(
                text = t.intensityNote,
                style = type.footnote,
                color = colors.muted,
            )
            Text(
                text = t.shapeNote,
                style = type.footnote,
                color = colors.muted,
            )
            Text(
                text = t.energyRangeNote(
                    Spectrogram.MIN_KEV.toInt(),
                    Spectrogram.MAX_KEV.toInt(),
                ),
                style = type.footnote,
                color = colors.muted,
            )
            Text(text = t.backgroundNote, style = type.footnote, color = colors.muted)
        }
        }
    }
}

@Composable
private fun LegendRamp(shapeMode: Boolean, scaleTop: Float, t: SpectrogramStrings) {
    val type = LocalAppTypography.current
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Шкала называет ВЕЛИЧИНУ и её концы. «Меньше ■■■ больше» в режиме
        // формы означало бы «меньше излучения», хотя это доля внутри столбца.
        Text(text = t.legendZero, style = type.axis, color = colors.muted)
        // Непрерывная полоса вместо отдельных квадратиков: шкала интенсивности
        // непрерывна, и разрывы в легенде подсказывали бы ступени, которых нет.
        Box(
            Modifier
                .size(width = 72.dp, height = 8.dp)
                .background(
                    Brush.horizontalGradient(waterfallLegendColors()),
                    RoundedCornerShape(2.dp),
                ),
        )
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

private fun timeOfDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(HH_MM_SS)
