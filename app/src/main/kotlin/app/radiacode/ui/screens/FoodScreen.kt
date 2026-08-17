package app.radiacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.radiacode.AppGraph
import app.radiacode.analysis.FoodScreening
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import app.radiacode.data.db.ExperimentEntity
import app.radiacode.data.export.N42
import app.radiacode.data.export.SpectrumExport
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.device.ConnectionState
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.AppTextField
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Hint
import app.radiacode.ui.components.StatusRow
import app.radiacode.ui.logic.FoodGeometry
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.text.FoodCatalogue
import androidx.compose.ui.unit.dp
import app.radiacode.ui.text.HistoryStrings
import app.radiacode.ui.text.FoodStrings
import app.radiacode.ui.text.ExperimentCatalogue
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import app.radiacode.data.FOOD_LABEL_BACKGROUND
import app.radiacode.data.FOOD_LABEL_SAMPLE
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Шаг измерения продукта. Два прогона и вывод — больше здесь ничего нет. */
private enum class FoodStep { SETUP, BACKGROUND, SAMPLE, RESULT }

/**
 * «Проверить продукт» — сравнительный гамма-скрининг.
 *
 * Отдельного движка у этого экрана нет: он ставит обычный опыт (`experiments`,
 * вид [ExperimentEntity.KIND_FOOD]) с двумя прогонами — фон и образец, — и оба
 * прогона пишет тот же рекордер, что и A/B. Значит, спектр каждого прогона это
 * РАЗНОСТЬ снимков за его интервал, а не всё накопление прибора: 126 часов
 * фона не заслоняют полчаса измерения продукта.
 *
 * Чего экран не делает: не называет продукт безопасным, не считает беккерели и
 * не объявляет нуклид по совпадению энергии. Что он умеет — сказано словами в
 * справке, и она открывается прямо отсюда.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    /** Не null — открыта запись из журнала: экран показывает её итог. */
    openMeasurementId: Long? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = FoodCatalogue.of(strings.language)
    val h = HistoryCatalogue.of(strings.language)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val connection by graph.serviceStatus.connection.collectAsState()
    val connected = connection is ConnectionState.Connected
    val hubState by graph.spectrumHub.state.collectAsState()
    val run by graph.abRun.state.collectAsState()
    // Живая скорость счёта: во время прогона она — единственный признак, что
    // измерение действительно идёт, а не замерло.
    val live by graph.serviceStatus.lastSample.collectAsState()
    var step by rememberSaveable {
        mutableStateOf(if (openMeasurementId != null) FoodStep.RESULT else FoodStep.SETUP)
    }
    var experimentId by rememberSaveable { mutableLongStateOf(openMeasurementId ?: 0L) }
    // Что именно измеряется: имя и условия видны на каждом шаге, а не только
    // в форме создания — иначе через полчаса непонятно, чей это прогон.
    var study by remember { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId, step) {
        study = experimentId.takeIf { it != 0L }?.let { graph.experimentRepository.byId(it) }
    }
    var name by rememberSaveable { mutableStateOf("") }
    var mass by rememberSaveable { mutableStateOf("") }
    var geometry by rememberSaveable { mutableStateOf(FoodGeometry.JAR_LITRE) }
    // Фото образца — ссылка на снимок в галерее телефона, а не копия внутри
    // приложения: копировать чужие файлы к себе ради строки в журнале
    // значило бы заводить своё хранилище картинок. Выбор идёт системным
    // диалогом (`PickVisualMedia`), и он не требует НИ ОДНОГО нового
    // разрешения: доступ выдаётся ровно к выбранному файлу.
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var container by rememberSaveable { mutableStateOf("") }
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<FoodScreening.Result?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(run) {
        while (run != null) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // Пока прогон идёт, экран только смотрит: владеет прогоном рекордер, и
    // уход с экрана его не прерывает.
    suspend fun startRun(label: String) {
        val now = System.currentTimeMillis()
        val startId = hubState.spectrum?.let {
            graph.measurementRepository.saveSpectrum(
                spectrum = it,
                accumulated = false,
                origin = SpectrumSnapshotEntity.ORIGIN_DERIVED,
                trigger = SpectrumSnapshotEntity.TRIGGER_FOOD,
            ).id
        }
        val runId = graph.experimentRepository.startRun(
            experimentId = experimentId,
            label = label,
            startedAt = now,
            startSpectrumId = startId,
        )
        graph.abRun.start(experimentId, runId, label, plannedSeconds = 0L)
    }

    suspend fun computeResult() {
        result = graph.experimentRepository.foodResult(experimentId)
    }

    // Экран не владеет измерением: он находит его по метке и восстанавливает
    // свой шаг по тому, какие прогоны уже записаны.
    LaunchedEffect(openMeasurementId) {
        if (openMeasurementId != null) return@LaunchedEffect
        val active = graph.settings.activeFoodExperimentId.first() ?: return@LaunchedEffect
        val runs = graph.experimentRepository.runs(active)
        if (runs.isEmpty() && graph.abRun.state.value?.experimentId != active) {
            // Опыт заведён, но ни один прогон не начат: продолжать нечего.
            return@LaunchedEffect
        }
        experimentId = active
        val hasBackground = runs.any { it.label == FOOD_LABEL_BACKGROUND && it.endedAt != null }
        val hasSample = runs.any { it.label == FOOD_LABEL_SAMPLE && it.endedAt != null }
        step = when {
            hasSample -> FoodStep.RESULT
            hasBackground -> FoodStep.SAMPLE
            else -> FoodStep.BACKGROUND
        }
        if (step == FoodStep.RESULT) computeResult()
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            // Разрешение на чтение переживает перезапуск: иначе снимок,
            // выбранный сегодня, завтра открывался бы ошибкой.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            photoUri = uri.toString()
        }
    }

    // Экспорт измерения — N42: это стандарт гамма-спектрометрии, и он один
    // умеет то, чем измерение продукта и является, — образец ВМЕСТЕ с фоном,
    // каждый со своей выдержкой и калибровкой. CSV из двух спектров такого не
    // выражает: он оставил бы связь фона с образцом на честном слове.
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            scope.launch { writeTextToUri(context, uri, content) }
        }
    }

    suspend fun exportMeasurement() {
        val runs = graph.experimentRepository.runs(experimentId)
        val backgroundId = runs.firstOrNull { it.label == FOOD_LABEL_BACKGROUND }?.spectrumId
        val sampleId = runs.firstOrNull { it.label == FOOD_LABEL_SAMPLE }?.spectrumId
        val background = backgroundId?.let { graph.experimentRepository.spectrum(it) }
        val sample = sampleId?.let { graph.experimentRepository.spectrum(it) } ?: return
        val experiment = graph.experimentRepository.byId(experimentId)
        pendingExport = N42.write(
            foreground = SpectrumExport.toN42Measurement(sample, N42.CLASS_FOREGROUND),
            background = background?.let {
                SpectrumExport.toN42Measurement(it, N42.CLASS_BACKGROUND)
            },
            // Условия измерения едут вместе с данными: без геометрии эти
            // спектры сравнимы только сами с собой.
            remarks = listOfNotNull(
                experiment?.note?.ifBlank { null },
                experiment?.geometry?.ifBlank { null },
            ),
        )
        exportLauncher.launch(SpectrumExport.fileName(sample.timestamp, "n42"))
    }

    if (guideOpen) {
        Dialog(onDismissRequest = { guideOpen = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(text = t.guideTitle, style = type.label, color = colors.ink)
                    t.guide().forEachIndexed { index, (heading, body) ->
                        if (index > 0) AppDivider()
                        Text(text = heading, style = type.labelSmall, color = colors.ink2)
                        Text(text = body, style = type.bodySmall, color = colors.ink)
                    }
                    AppButton(
                        text = strings.close,
                        onClick = { guideOpen = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(text = strings.back, onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = "i", color = colors.ink2, onClick = { guideOpen = true })
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.title, style = type.title, color = colors.ink)
                Hint(text = t.subtitle, style = type.bodySmall, color = colors.ink2)

                when (step) {
                    FoodStep.SETUP -> {
                        AppTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = t.sampleName,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextField(
                            value = mass,
                            onValueChange = { mass = it },
                            placeholder = t.sampleMass,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(text = t.container, style = type.labelSmall, color = colors.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                            FoodGeometry.entries.forEach { option ->
                                Chip(
                                    text = option.label(t),
                                    color = if (option == geometry) {
                                        colors.dataText
                                    } else {
                                        colors.ink2
                                    },
                                    selected = option == geometry,
                                    onClick = { geometry = option },
                                )
                            }
                        }
                        Text(
                            text = geometry.hint(t),
                            style = type.footnote,
                            color = colors.muted,
                        )
                        AppTextField(
                            value = container,
                            onValueChange = { container = it },
                            placeholder = t.note,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                        ) {
                            Chip(
                                text = if (photoUri == null) t.addPhoto else t.changePhoto,
                                color = colors.ink2,
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                            )
                            photoUri?.let {
                                Text(
                                    text = t.photoAttached,
                                    style = type.footnote,
                                    color = colors.muted,
                                )
                            }
                        }
                        AppButton(
                            text = t.start,
                            primary = true,
                            enabled = connected,
                            onClick = {
                                scope.launch {
                                    experimentId = graph.experimentRepository.create(
                                        kind = ExperimentEntity.KIND_FOOD,
                                        profileId = null,
                                        // Геометрия — пресет плюс уточнение: пресет
                                        // повторяется буквально, уточнение хранит
                                        // то, чего в пресете нет.
                                        geometry = listOfNotNull(
                                            geometry.label(t),
                                            container.trim().ifBlank { null },
                                        ).joinToString(" · "),
                                        placement = geometry.code,
                                        note = listOfNotNull(
                                            name.trim().ifBlank { null },
                                            mass.trim().ifBlank { null }?.let { "$it г" },
                                        ).joinToString(" · "),
                                        photoUri = photoUri,
                                    )
                                    graph.settings.setActiveFoodExperimentId(experimentId)
                                    step = FoodStep.BACKGROUND
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    FoodStep.BACKGROUND, FoodStep.SAMPLE -> {
                        val isBackground = step == FoodStep.BACKGROUND
                        StudyHeadline(study, t, h)
                        StatusRow(
                            text = if (isBackground) t.stepBackground else t.stepSample,
                            color = colors.ink,
                        )
                        Text(
                            text = if (isBackground) t.backgroundHint else t.sampleHint,
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                        val active = run
                        if (active == null) {
                            // Причина, по которой кнопка не нажимается, стоит
                            // рядом с кнопкой: «недоступно» без причины хуже
                            // отсутствия.
                            if (!connected) {
                                Text(
                                    text = ExperimentCatalogue.of(strings.language).notConnected,
                                    style = type.footnote,
                                    color = colors.warn,
                                )
                            }
                            AppButton(
                                text = t.start,
                                primary = true,
                                enabled = connected,
                                onClick = {
                                    scope.launch {
                                        startRun(
                                            if (isBackground) FOOD_LABEL_BACKGROUND else FOOD_LABEL_SAMPLE,
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = HistoryFormat.duration(
                                    active.elapsedSeconds(nowMillis),
                                    s = h,
                                ),
                                style = type.valueLarge,
                                color = colors.ink,
                            )
                            live?.let { sample ->
                                Text(
                                    text = t.countRateNow(Uncertainty.num1(sample.countRate)),
                                    style = type.valueSmall,
                                    color = colors.ink2,
                                )
                            }
                            AppButton(
                                text = ExperimentCatalogue.of(strings.language).stopRun,
                                onClick = {
                                    graph.abRun.stop()
                                    scope.launch {
                                        if (isBackground) {
                                            step = FoodStep.SAMPLE
                                        } else {
                                            step = FoodStep.RESULT
                                            computeResult()
                                            // Измерение закончено: метка снята,
                                            // и следующий вход начинает новое.
                                            graph.settings.setActiveFoodExperimentId(null)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    FoodStep.RESULT -> {
                        StudyHeadline(study, t, h)
                        FoodResult(
                        result = result,
                        onContinue = { step = FoodStep.SAMPLE },
                        onRecompute = { scope.launch { computeResult() } },
                        onExport = { scope.launch { exportMeasurement() } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodResult(
    result: FoodScreening.Result?,
    onContinue: () -> Unit,
    onRecompute: () -> Unit,
    onExport: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = FoodCatalogue.of(strings.language)

    LaunchedEffect(result) { if (result == null) onRecompute() }
    val current = result ?: return

    val (title, body) = when (current.verdict) {
        FoodScreening.Verdict.NOT_ENOUGH_DATA -> t.verdictNotEnough to t.verdictNotEnoughBody
        FoodScreening.Verdict.NO_DIFFERENCE -> t.verdictNoDifference to t.verdictNoDifferenceBody
        FoodScreening.Verdict.EXCESS_WITHOUT_LINE -> t.verdictExcess to t.verdictExcessBody
        FoodScreening.Verdict.SPECTRAL_FEATURE -> t.verdictLine to t.verdictLineBody(
            Uncertainty.num1(current.lines.first().energyKev) + " кэВ",
        )
    }

    StatusRow(text = title, color = colors.ink)
    Text(text = body, style = type.bodySmall, color = colors.ink2)

    // Чувствительность — то, что превращает «отличий не найдено» из пустой
    // фразы в утверждение с границей.
    current.sensitivity?.let { sensitivity ->
        sensitivity.detectableFraction?.let { fraction ->
            Text(
                text = t.sensitivityLine(
                    Uncertainty.num1((fraction * 100).toFloat()) + " %",
                    Uncertainty.num2(sensitivity.detectableCps.toFloat()),
                ),
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
    Hint(text = t.screeningDisclaimer, style = type.footnote, color = colors.muted)
    AppButton(text = t.continueMeasuring, onClick = onContinue, modifier = Modifier.fillMaxWidth())
    AppButton(text = t.exportMeasurement, onClick = onExport, modifier = Modifier.fillMaxWidth())
}

/**
 * Что именно измеряется — одной строкой над ходом измерения.
 *
 * Прогон идёт десятки минут, и всё это время экран показывал только таймер:
 * чей это прогон и в чём образец, вспоминал сам человек.
 */
@Composable
private fun StudyHeadline(study: ExperimentEntity?, t: FoodStrings, h: HistoryStrings) {
    if (study == null) return
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = study.note.ifBlank { t.title },
            style = type.label,
            color = colors.ink,
        )
        val details = listOfNotNull(
            HistoryFormat.dayTime(study.createdAt, System.currentTimeMillis(), s = h),
            study.geometry.ifBlank { null },
        ).joinToString(" · ")
        if (details.isNotBlank()) {
            Text(text = details, style = type.footnote, color = colors.ink2)
        }
    }
}
