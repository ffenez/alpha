@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.radiacode.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.radiacode.AppGraph
import app.radiacode.analysis.AbAnalysis
import app.radiacode.analysis.AbExperiment
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.EnergyWindows
import app.radiacode.analysis.SpectrumCompare
import app.radiacode.data.SpectrumBlob
import app.radiacode.data.db.ExperimentEntity
import app.radiacode.data.db.ExperimentRunEntity
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.data.export.ExperimentReport
import app.radiacode.data.export.ProcessingMetadata
import app.radiacode.data.toEntity
import app.radiacode.device.ConnectionState
import app.radiacode.protocol.Spectrum
import app.radiacode.service.AbRunRecorder
import app.radiacode.service.ServiceStatus
import app.radiacode.ui.components.Hint
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.AppTextField
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.ChartNotesDialog
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.EvidenceTag
import app.radiacode.ui.components.RadioMark
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.ExperimentConditions
import app.radiacode.ui.logic.ExperimentConditionsFormat
import app.radiacode.ui.logic.ExperimentScenario
import app.radiacode.ui.logic.ExperimentFormat
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.text.ExperimentCatalogue
import app.radiacode.ui.text.ExperimentStrings
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.HistoryRu
import app.radiacode.ui.text.HistoryStrings
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fixed run durations offered by the recorder; last option = manual stop. */
private val RUN_DURATION_SECONDS = listOf(120L, 300L, 600L, 0L)

/** Подписи считаются из самих длительностей — расходиться им негде. */
private fun runDurationLabels(t: ExperimentStrings): List<String> = RUN_DURATION_SECONDS.map {
    if (it == 0L) t.durationManual else t.minutes(it / 60)
}

/**
 * A/B эксперимент (спец §9, §16) — управляемый протокол: пользователь один
 * раз описывает геометрию, записывает прогон A, затем прогон B в той же
 * геометрии (описание показывается снова), после чего экран показывает
 * сравнение: мощность дозы, полный счёт, энергетические окна и полный спектр.
 *
 * Экран помечен как экспериментальная функция: математика проверена на
 * синтетике, но не валидирована на реальных измерениях RC-110 (спец §24).
 *
 * Навигация самодостаточна: список → создание → карточка эксперимента.
 * Вся математика — в `analysis/AbAnalysis` и `analysis/AbExperiment`,
 * весь текст — в `ui/logic/ExperimentFormat`; здесь только состояние экрана.
 */
@Composable
fun AbExperimentScreen(graph: AppGraph, onBack: () -> Unit) {
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    // Spectrum acquisition while the screen is open: runs need the 5 s poll.
    val hub = graph.spectrumHub
    DisposableEffect(hub) {
        hub.attach()
        onDispose { hub.detach() }
    }

    BackHandler {
        when {
            creating -> creating = false
            openId != null -> openId = null
            else -> onBack()
        }
    }

    val id = openId
    when {
        creating -> CreateExperiment(
            graph = graph,
            onCancel = { creating = false },
            onCreated = {
                creating = false
                openId = it
            },
        )
        id != null -> ExperimentDetail(
            graph = graph,
            experimentId = id,
            onBack = { openId = null },
            onDeleted = { openId = null },
        )
        else -> ExperimentList(
            graph = graph,
            onBack = onBack,
            onOpen = { openId = it },
            onCreate = { creating = true },
        )
    }
}

// --- list ---

@Composable
private fun ExperimentList(
    graph: AppGraph,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ExperimentCatalogue.of(LocalStrings.current.language)
    val experiments by graph.experimentRepository.recent().collectAsState(initial = emptyList())

    Screen {
        Header(title = t.listTitle, back = t.back, onBack = onBack)
        ExperimentalBanner()
        AppButton(
            text = t.newExperiment,
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
        )
        if (experiments.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(text = t.emptyList, style = type.bodySmall, color = colors.ink2)
                    Hint(
                        text = t.emptyHint,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    experiments.forEachIndexed { index, experiment ->
                        if (index > 0) AppDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onOpen(experiment.id) },
                                )
                                .padding(vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ExperimentFormat.kindLabel(experiment.kind, t),
                                    style = type.label,
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = HistoryFormat.dayTime(
                                        experiment.createdAt,
                                        System.currentTimeMillis(),
                                        s = h,
                            ),
                                    style = type.footnote,
                                    color = colors.ink2,
                                )
                            }
                            Text(
                                text = experiment.geometry.ifBlank { t.geometryUndescribedInList },
                                style = type.valueSmall,
                                color = colors.ink2,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- creation ---

@Composable
private fun CreateExperiment(
    graph: AppGraph,
    onCancel: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ExperimentCatalogue.of(LocalStrings.current.language)
    val scope = rememberCoroutineScope()

    var scenario by rememberSaveable { mutableStateOf(ExperimentScenario.OBJECT) }
    var template by rememberSaveable { mutableStateOf(ExperimentEntity.KIND_DISTANCE) }
    var distance by rememberSaveable { mutableStateOf("") }
    var placement by rememberSaveable { mutableStateOf("") }
    var orientation by rememberSaveable { mutableStateOf("") }
    var plannedSeconds by rememberSaveable { mutableLongStateOf(RUN_DURATION_SECONDS[1]) }
    var note by rememberSaveable { mutableStateOf("") }
    val kind = if (scenario == ExperimentScenario.CUSTOM) template else scenario.kind
    val conditions = ExperimentConditions(
        distanceCm = distance.trim().toIntOrNull(),
        placement = placement,
        orientation = orientation,
        plannedSeconds = plannedSeconds,
    )
    val activeProfile by graph.profileRepository.activeProfile().collectAsState(initial = null)
    val windowsRaw by graph.settings.energyWindowsRaw.collectAsState(initial = null)

    Screen {
        Header(title = t.newExperiment, back = t.back, onBack = onCancel)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.scenario, style = type.label, color = colors.ink)
                // Сценарий отвечает на вопрос «что с чем», шаблон — на вопрос
                // «что меняется между прогонами». Это два разных вопроса, и
                // одним списком из четырёх пунктов они не задаются.
                ExperimentScenario.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Dimens.touchTarget)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { scenario = option },
                            ),
                    ) {
                        RadioMark(scenario == option)
                        Text(
                            text = ExperimentConditionsFormat.scenarioLabel(option, t),
                            style = type.label,
                            color = colors.ink,
                        )
                    }
                }
                Hint(
                    text = ExperimentConditionsFormat.scenarioHint(scenario, t),
                )
                if (scenario == ExperimentScenario.CUSTOM) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                        ExperimentScenario.TEMPLATES.forEach { option ->
                            Chip(
                                text = ExperimentConditionsFormat.templateLabel(option, t),
                                color = if (template == option) colors.dataText else colors.ink2,
                                selected = template == option,
                                onClick = { template = option },
                            )
                        }
                    }
                    Hint(
                        text = ExperimentFormat.kindHint(template, t),
                    )
                }
            }
        }

        // Условия разложены на части, которые можно повторить буквально.
        // Одна фраза «геометрия» описывала постановку один раз и по-своему, а
        // повторить её через неделю по памяти нельзя — при том что весь смысл
        // A/B в том, что между прогонами меняется РОВНО ОДНО.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.conditionsTitle, style = type.label, color = colors.ink)
                Hint(text = t.geometryPrompt)

                Text(text = t.conditionDistance, style = type.labelSmall, color = colors.ink2)
                AppTextField(
                    value = distance,
                    onValueChange = { value -> distance = value.filter { it.isDigit() }.take(4) },
                    placeholder = t.centimeters(5),
                )

                Text(text = t.conditionPlacement, style = type.labelSmall, color = colors.ink2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                    ExperimentConditions.PLACEMENTS.forEach { code ->
                        Chip(
                            text = ExperimentConditionsFormat.placementLabel(code, t),
                            color = if (placement == code) colors.dataText else colors.ink2,
                            selected = placement == code,
                            // Повторное нажатие снимает выбор: условие может
                            // быть и не задано, и это честнее подставленного.
                            onClick = { placement = if (placement == code) "" else code },
                        )
                    }
                }

                Text(text = t.conditionOrientation, style = type.labelSmall, color = colors.ink2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                    ExperimentConditions.ORIENTATIONS.forEach { code ->
                        Chip(
                            text = ExperimentConditionsFormat.orientationLabel(code, t),
                            color = if (orientation == code) colors.dataText else colors.ink2,
                            selected = orientation == code,
                            onClick = { orientation = if (orientation == code) "" else code },
                        )
                    }
                }

                Text(text = t.conditionDuration, style = type.labelSmall, color = colors.ink2)
                Segmented(
                    options = RUN_DURATION_SECONDS.map { ExperimentFormat.duration(it, t) },
                    selectedIndex = RUN_DURATION_SECONDS.indexOf(plannedSeconds)
                        .coerceAtLeast(0),
                    onSelect = { plannedSeconds = RUN_DURATION_SECONDS[it] },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(text = t.note, style = type.label, color = colors.ink)
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = t.notePlaceholder,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            AppButton(
                text = t.create,
                // Хотя бы одно условие названо: опыт без описанной постановки
                // невоспроизводим, и обещать обратное нечестно.
                enabled = !conditions.isEmpty,
                onClick = {
                    scope.launch {
                        val id = graph.experimentRepository.create(
                            kind = kind,
                            profileId = activeProfile?.id,
                            // Строка-изложение остаётся: её читают список и
                            // отчёт, и она не должна зависеть от того, умеет
                            // ли читатель разбирать поля.
                            geometry = ExperimentConditionsFormat.summary(conditions, t),
                            note = note.trim(),
                            distanceCm = conditions.distanceCm,
                            placement = conditions.placement,
                            orientation = conditions.orientation,
                            plannedSeconds = conditions.plannedSeconds,
                            windowSpecs = EnergyWindows.parse(windowsRaw),
                        )
                        onCreated(id)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            AppButton(text = t.cancel, onClick = onCancel, modifier = Modifier.weight(1f))
        }
        Hint(
            text = t.geometryKeptNote,
        )
    }
}

// --- detail ---

@Composable
private fun ExperimentDetail(
    graph: AppGraph,
    experimentId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ExperimentCatalogue.of(LocalStrings.current.language)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var experiment by remember(experimentId) { mutableStateOf<ExperimentEntity?>(null) }
    LaunchedEffect(experimentId) {
        experiment = graph.experimentRepository.byId(experimentId)
    }
    val runs by graph.experimentRepository.observeRuns(experimentId)
        .collectAsState(initial = emptyList())
    var runData by remember(experimentId) { mutableStateOf<List<AbExperiment.RunData>>(emptyList()) }
    LaunchedEffect(runs) {
        runData = graph.experimentRepository.runData(runs.filter { it.endedAt != null })
    }

    val windowsRaw by graph.settings.energyWindowsRaw.collectAsState(initial = null)
    val windowSpecs = remember(windowsRaw) { EnergyWindows.parse(windowsRaw) }
    val connection by graph.serviceStatus.connection.collectAsState()
    val hubState by graph.spectrumHub.state.collectAsState()

    // Прогон живёт в графе приложения: переход по вкладкам и сворачивание
    // приложения его больше не убивают (владелец — service/AbRunRecorder).
    val activeRun by graph.abRun.state.collectAsState()
    val recorderNotice by graph.abRun.notice.collectAsState()
    val recordingRunId = activeRun?.takeIf { it.experimentId == experimentId }?.runId
    var plannedSeconds by rememberSaveable(experimentId) { mutableLongStateOf(RUN_DURATION_SECONDS[1]) }
    var durationIndex by rememberSaveable(experimentId) { mutableIntStateOf(1) }
    val startedAt = activeRun?.startedAtMillis ?: 0L
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var distanceInput by rememberSaveable(experimentId) { mutableStateOf("") }
    var shieldingInput by rememberSaveable(experimentId) { mutableStateOf("") }
    var pendingReport by remember { mutableStateOf<String?>(null) }

    val current = experiment
    // Длительность прогона — ЧАСТЬ условий опыта, а не настройка момента:
    // прогоны разной длины сравнимы по скорости счёта, но повторяют условия
    // хуже, и человеку не приходится вспоминать, сколько шёл первый.
    LaunchedEffect(current?.plannedSeconds) {
        val stored = current?.plannedSeconds ?: 0L
        val index = RUN_DURATION_SECONDS.indexOf(stored)
        if (stored > 0L && index >= 0) {
            plannedSeconds = stored
            durationIndex = index
        }
    }
    val openRun = runs.firstOrNull { it.endedAt == null }

    // Флаг эксперимента, таймер, автостоп и снимок спектра прогона — всё это
    // делает владелец прогона. Экран только тикает часами для отрисовки.
    LaunchedEffect(recordingRunId) {
        while (recordingRunId != null) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(recorderNotice) {
        recorderNotice?.let {
            notice = when (it) {
                AbRunRecorder.Notice.RUN_WITHOUT_SPECTRUM -> t.runWithoutSpectrum
            }
            graph.abRun.dismissNotice()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val content = pendingReport
        pendingReport = null
        if (uri != null && content != null) {
            scope.launch {
                notice = if (writeTextToUri(context, uri, content)) {
                    t.reportSaved
                } else {
                    t.reportFailed
                }
            }
        }
    }

    Screen {
        Header(title = t.detailTitle, back = t.back, onBack = onBack)
        if (current == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = t.loading, style = type.bodySmall, color = colors.muted)
            }
            return@Screen
        }

        ExperimentalBanner()

        // Geometry is shown on every run, not only when it was written.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(
                        text = ExperimentFormat.kindLabel(current.kind, t),
                        color = colors.dataText,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = HistoryFormat.dayTime(current.createdAt, nowMillis, s = h),
                        style = type.footnote,
                        color = colors.ink2,
                    )
                }
                Text(text = t.geometry, style = type.labelSmall, color = colors.ink2)
                Text(
                    text = current.geometry.ifBlank { t.geometryUndescribed },
                    style = type.body,
                    color = colors.ink,
                )
                if (current.note.isNotBlank()) {
                    Text(text = current.note, style = type.bodySmall, color = colors.ink2)
                }
                Hint(
                    text = ExperimentFormat.kindHint(current.kind, t),
                )
            }
        }

        // --- runs ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = t.runs.uppercase(), style = type.labelSmall, color = colors.ink2)
                if (runs.isEmpty()) {
                    Text(
                        text = t.runsEmpty,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                }
                runs.forEachIndexed { index, run ->
                    if (index > 0) AppDivider()
                    RunRow(
                        kind = current.kind,
                        index = index,
                        run = run,
                        data = runData.firstOrNull { it.id == run.id },
                        recording = run.id == recordingRunId,
                        elapsedSeconds = if (run.id == recordingRunId) {
                            (nowMillis - startedAt) / 1000L
                        } else {
                            null
                        },
                        plannedSeconds = plannedSeconds,
                        onDelete = {
                            scope.launch {
                                if (run.id == recordingRunId) graph.abRun.stop()
                                graph.experimentRepository.deleteRun(run.id)
                            }
                        },
                    )
                }
            }
        }

        // --- recorder ---
        val connected = connection is ConnectionState.Connected
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                if (recordingRunId != null) {
                    Text(
                        text = t.runInProgress(
                            elapsed = ExperimentFormat.duration((nowMillis - startedAt) / 1000L, t),
                            planned = plannedSeconds
                                .takeIf { it > 0 }
                                ?.let { ExperimentFormat.duration(it, t) },
                        ),
                        style = type.label,
                        color = colors.ink,
                    )
                    Hint(
                        text = t.holdGeometryNote,
                    )
                    AppButton(
                        text = t.stopRun,
                        onClick = {
                            val runId = recordingRunId
                            if (runId != null) graph.abRun.stop()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    val nextIndex = runs.size
                    val nextLetter = ExperimentFormat.runLetter(nextIndex)
                    Text(
                        text = t.runHeadline(
                            letter = nextLetter,
                            role = ExperimentFormat.runRoleLabel(current.kind, nextIndex, t),
                        ),
                        style = type.label,
                        color = colors.ink,
                    )
                    if (nextIndex > 0) {
                        // Условия первого прогона — списком, а не по памяти.
                        // У опытов, созданных до разложения условий, поля
                        // пусты, и тогда показывается их прежняя фраза.
                        val conditions = ExperimentConditions.of(current)
                        val repeat = ExperimentConditionsFormat.repeatLine(
                            letter = ExperimentFormat.runLetter(0),
                            conditions = conditions,
                            s = t,
                        ) ?: t.repeatGeometry(
                            letter = ExperimentFormat.runLetter(0),
                            geometry = current.geometry.ifBlank { t.geometryUndescribedInline },
                        )
                        Text(text = repeat, style = type.bodySmall, color = colors.ink2)
                    }
                    if (current.kind == ExperimentEntity.KIND_DISTANCE) {
                        AppTextField(
                            value = distanceInput,
                            onValueChange = { distanceInput = it },
                            placeholder = t.distancePlaceholder,
                            numeric = true,
                        )
                    }
                    if (current.kind == ExperimentEntity.KIND_SHIELDING) {
                        AppTextField(
                            value = shieldingInput,
                            onValueChange = { shieldingInput = it },
                            placeholder = t.shieldingPlaceholder,
                        )
                    }
                    Segmented(
                        options = runDurationLabels(t),
                        selectedIndex = durationIndex,
                        onSelect = {
                            durationIndex = it
                            plannedSeconds = RUN_DURATION_SECONDS[it]
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = t.startRun(nextLetter),
                        enabled = connected && openRun == null,
                        onClick = {
                            scope.launch {
                                val now = System.currentTimeMillis()
                                val startSpectrum = hubState.spectrum
                                val startId = startSpectrum?.let {
                                    graph.measurementRepository.saveSpectrum(
                                        spectrum = it,
                                        accumulated = false,
                                        origin = SpectrumSnapshotEntity.ORIGIN_DERIVED,
                                        // Метка ХРАНИТСЯ в базе и языку интерфейса не
                                        // подчиняется: снимок, сделанный по-русски, иначе
                                        // остался бы русским после смены языка.
                                        label = "A/B $nextLetter · старт",
                                        analysisMeta = startStamp(experimentId, nextLetter),
                                    ).id
                                }
                                nowMillis = now
                                val runId = graph.experimentRepository.startRun(
                                    experimentId = experimentId,
                                    label = nextLetter,
                                    startedAt = now,
                                    startSpectrumId = startId,
                                    distanceCm = distanceInput.replace(',', '.')
                                        .trim().toFloatOrNull(),
                                    shieldingNote = shieldingInput.trim().ifBlank { null },
                                )
                                graph.abRun.start(
                                    experimentId = experimentId,
                                    runId = runId,
                                    label = nextLetter,
                                    plannedSeconds = plannedSeconds,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!connected) {
                        Text(
                            text = t.notConnected,
                            style = type.footnote,
                            color = colors.warn,
                        )
                    }
                    if (openRun != null && recordingRunId == null) {
                        Text(
                            text = t.runUnfinished(openRun.label),
                            style = type.footnote,
                            color = colors.warn,
                        )
                        AppButton(
                            text = t.finishRun(openRun.label),
                            onClick = { graph.abRun.stop() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // --- comparison ---
        // Каталог в ключе: после смены языка предупреждения обязаны пересобраться.
        val comparison = remember(runData, windowSpecs, t) {
            val completed = runData.filter { it.endedAt != null }
            if (completed.size >= 2) {
                AbExperiment.compare(completed[0], completed[1], windowSpecs, t)
            } else {
                null
            }
        }
        if (comparison != null) {
            ComparisonCards(comparison)
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = t.needTwoRuns,
                    style = type.bodySmall,
                    color = colors.muted,
                )
            }
        }

        if (current.kind == ExperimentEntity.KIND_DISTANCE) {
            val series = remember(runData) { AbExperiment.distanceSeries(runData) }
            if (series.isNotEmpty()) DistanceCard(series)
        }
        if (current.kind == ExperimentEntity.KIND_SHIELDING) {
            Hint(
                text = t.shieldingWarning,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            AppButton(
                text = t.reportToFile,
                enabled = runs.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val profileName = current.profileId
                            ?.let { graph.profileRepository.byId(it)?.name }
                        pendingReport = ExperimentReport.render(
                            experiment = current,
                            profileName = profileName,
                            runs = runData,
                            comparison = comparison,
                            windowSpecs = windowSpecs,
                            distance = if (current.kind == ExperimentEntity.KIND_DISTANCE) {
                                AbExperiment.distanceSeries(runData)
                            } else {
                                emptyList()
                            },
                            appVersion = appVersionName(context),
                        )
                        exportLauncher.launch(ExperimentReport.fileName(current))
                    }
                },
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = t.delete,
                onClick = { confirmDelete = true },
                modifier = Modifier.weight(1f),
            )
        }
        notice?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }
    }

    if (confirmDelete) {
        Dialog(onDismissRequest = { confirmDelete = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(text = t.deleteTitle, style = type.title, color = colors.ink)
                    Text(
                        text = t.deleteBody,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        AppButton(
                            text = t.delete,
                            onClick = {
                                confirmDelete = false
                                scope.launch {
                                    graph.experimentRepository.delete(experimentId)
                                    onDeleted()
                                }
                            },
                        )
                        AppButton(text = t.cancel, onClick = { confirmDelete = false })
                    }
                }
            }
        }
    }
}

// --- pieces ---

@Composable
private fun Screen(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
        content = content,
    )
}

@Composable
private fun Header(title: String, back: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppButton(text = back, onClick = onBack)
        Spacer(Modifier.weight(1f))
        Chip(text = title, color = colors.ink)
    }
}

/**
 * Спец §24: до валидации на реальных данных функция помечена как опытная.
 *
 * На экране остаются метка и одна фраза о том, что здесь делают. Абзац про
 * синтетическую проверку — под «i»: он о зрелости функции, а не о том, как ею
 * пользоваться, и стоял ровно на месте первого шага.
 */
@Composable
private fun ExperimentalBanner() {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ExperimentCatalogue.of(LocalStrings.current.language)
    var info by remember { mutableStateOf(false) }
    if (info) ChartNotesDialog(notes = listOf(t.experimentalNote)) { info = false }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Chip(text = t.experimentalBadge, color = colors.warn)
            Text(
                text = t.experimentalLead,
                style = type.bodySmall,
                color = colors.ink2,
                modifier = Modifier.weight(1f),
            )
            Chip(text = "i", color = colors.ink2, onClick = { info = true })
        }
    }
}

@Composable
private fun RunRow(
    kind: String,
    index: Int,
    run: ExperimentRunEntity,
    data: AbExperiment.RunData?,
    recording: Boolean,
    elapsedSeconds: Long?,
    plannedSeconds: Long,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ExperimentCatalogue.of(LocalStrings.current.language)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${run.label} · ${ExperimentFormat.runRoleLabel(kind, index, t)}",
                style = type.label,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (recording) {
                Text(
                    text = t.recording(
                        elapsed = ExperimentFormat.duration(elapsedSeconds ?: 0L, t),
                        planned = plannedSeconds
                            .takeIf { it > 0 }
                            ?.let { ExperimentFormat.duration(it, t) },
                    ),
                    style = type.footnote,
                    color = colors.ok,
                )
            } else {
                Chip(text = t.deleteRun, color = colors.ink2, onClick = onDelete)
            }
        }
        when {
            run.endedAt == null && !recording -> Text(
                text = t.runUnfinishedShort,
                style = type.valueSmall,
                color = colors.warn,
            )
            data != null -> {
                Text(
                    text = buildString {
                        append("Δt ").append(ExperimentFormat.duration(data.durationSeconds, t))
                        append(" · ")
                        if (data.hasSpectrum) {
                            append(t.spectrumCounts(data.totalCounts.toString()))
                        } else {
                            append(t.spectrumMissing)
                        }
                        data.doseStats?.let {
                            append(" · ")
                            append(
                                t.doseMean(
                                    value = ExperimentFormat.decimal(it.meanMicroSvH),
                                    samples = it.sampleCount,
                                ),
                            )
                        }
                    },
                    style = type.valueSmall,
                    color = colors.ink2,
                )
                data.distanceCm?.let {
                    Text(
                        text = t.distanceRow(ExperimentFormat.distance(it, t)),
                        style = type.footnote,
                        color = colors.ink2,
                    )
                }
                data.shieldingNote?.takeIf { it.isNotBlank() }?.let {
                    Text(text = t.shieldingRow(it), style = type.footnote, color = colors.ink2)
                }
            }
        }
    }
}

@Composable
private fun ComparisonCards(comparison: AbExperiment.Comparison) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ExperimentCatalogue.of(LocalStrings.current.language)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = t.conclusion.uppercase(), style = type.labelSmall, color = colors.ink2)
                EvidenceTag(Evidence.STATISTICALLY_DETECTED, Modifier.padding(start = 6.dp))
            }
            Text(
                text = ExperimentFormat.verdictHeadline(
                    comparison.verdict,
                    comparison.a.label,
                    comparison.b.label,
                    t,
                ),
                style = type.title,
                color = when (comparison.verdict) {
                    AbAnalysis.Verdict.CONSISTENT -> colors.ink
                    else -> colors.warn
                },
            )
            Hint(
                text = t.verdictScopeNote,
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
                AbHeader(t.columnMetric, 1.3f)
                AbHeader("A", 1f)
                AbHeader("B", 1f)
                AbHeader("z", 0.8f)
                AbHeader(t.columnVerdict, 1.5f)
            }
            AppDivider()
            comparison.doseRate?.let { dose ->
                AbRow(
                    label = t.rowDose,
                    a = ExperimentFormat.decimal(dose.a.meanMicroSvH),
                    b = ExperimentFormat.decimal(dose.b.meanMicroSvH),
                    z = ExperimentFormat.zLabel(dose.z),
                    verdict = dose.verdict,
                )
                AppDivider()
            }
            comparison.totalCounts?.let { total ->
                AbRow(
                    label = t.rowTotalCounts,
                    a = ExperimentFormat.decimal(total.rateA),
                    b = ExperimentFormat.decimal(total.rateB),
                    z = ExperimentFormat.zLabel(total.z),
                    verdict = total.verdict,
                )
                AppDivider()
            }
            comparison.windows.forEach { window ->
                AbRow(
                    label = window.label,
                    a = ExperimentFormat.decimal(window.rateA),
                    b = ExperimentFormat.decimal(window.rateB),
                    z = ExperimentFormat.zLabel(window.z),
                    verdict = window.verdict,
                )
                AppDivider()
            }
            comparison.spectrum?.let { spectrum ->
                AbRow(
                    label = t.rowSpectrum,
                    a = "χ² ${ExperimentFormat.decimal(spectrum.chiSquare)}",
                    b = "ν ${spectrum.degreesOfFreedom}",
                    z = ExperimentFormat.zLabel(spectrum.z),
                    verdict = spectrum.verdict,
                )
            }
            val method = comparison.totalCounts?.method ?: comparison.spectrum?.method
            if (method != null) {
                Hint(
                    text = ExperimentFormat.methodExplanation(method, t),
                )
            }
            comparison.totalCounts?.let { total ->
                Text(
                    text = t.netLine(
                        net = ExperimentFormat.signedCounts(total.net),
                        sigma = ExperimentFormat.decimal(total.netSigma),
                    ),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            comparison.doseRate?.let {
                Hint(
                    text = t.doseAuxNote,
                )
            }
            comparison.warnings.forEach { warning ->
                Text(text = "⚠ $warning", style = type.footnote, color = colors.warn)
            }
        }
    }
}

@Composable
private fun DistanceCard(series: List<AbExperiment.DistancePoint>) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ExperimentCatalogue.of(LocalStrings.current.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t.distanceSeries.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                EvidenceTag(Evidence.CALCULATED, Modifier.padding(start = 6.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                AbHeader("r", 1f)
                AbHeader(t.columnMeasured, 1.6f)
                AbHeader(t.columnInverseSquare, 1.4f)
            }
            AppDivider()
            series.forEachIndexed { index, point ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                ) {
                    AbCell(ExperimentFormat.distance(point.distanceCm, t), 1f, colors.ink)
                    // Единица уже стоит в заголовке колонки — в ячейке она лишняя.
                    AbCell(
                        ExperimentFormat.rateWithSigma(point.netRateCps, point.sigmaCps),
                        1.6f,
                        colors.ink,
                    )
                    AbCell(
                        point.inverseSquareCps?.let { ExperimentFormat.decimal(it) }
                            ?: t.referencePoint,
                        1.4f,
                        colors.ink2,
                    )
                }
                if (index < series.size - 1) AppDivider()
            }
            Hint(
                text = t.distanceWarning,
            )
        }
    }
}

@Composable
private fun AbRow(label: String, a: String, b: String, z: String, verdict: AbAnalysis.Verdict) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        AbCell(label, 1.3f, colors.ink2)
        AbCell(a, 1f, colors.ink)
        AbCell(b, 1f, colors.ink)
        AbCell(z, 0.8f, colors.ink)
        AbCell(
            ExperimentFormat.verdictLabel(
                verdict,
                ExperimentCatalogue.of(LocalStrings.current.language),
            ),
            1.5f,
            when (verdict) {
                AbAnalysis.Verdict.CONSISTENT -> colors.muted
                AbAnalysis.Verdict.CHANGED -> colors.ink2
                AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE -> colors.warn
            },
        )
    }
}

@Composable
private fun RowScope.AbHeader(text: String, weight: Float) {
    Text(
        text = text.uppercase(),
        style = LocalAppTypography.current.overline,
        color = LocalAppColors.current.muted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.AbCell(text: String, weight: Float, color: Color) {
    Text(
        text = text,
        style = LocalAppTypography.current.valueSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

// --- run spectrum capture ---

/** Reproducibility stamp of the snapshot taken when a run starts (spec §22). */
private fun startStamp(experimentId: Long, label: String): String = ProcessingMetadata.stamp(
    method = "ab_run_start_snapshot",
    algorithms = listOf("ab_analysis"),
    extra = mapOf("experimentId" to experimentId.toString(), "run" to label),
)

/**
 * The run's own spectrum: the accumulation at the end minus the snapshot taken
 * at the start ([SpectrumCompare.extractInterval] — same math the История
 * comparator uses). Saved as a `derived` snapshot with its processing
 * metadata; null when the device gave nothing comparable (no live spectrum,
 * a reset in between, equal accumulation times).
 */
