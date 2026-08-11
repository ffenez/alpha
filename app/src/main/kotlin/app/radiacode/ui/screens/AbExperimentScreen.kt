package app.radiacode.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.radiacode.service.ServiceStatus
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.AppTextField
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.EvidenceTag
import app.radiacode.ui.components.RadioMark
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.ExperimentFormat
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fixed run durations offered by the recorder; last option = manual stop. */
private val RUN_DURATION_SECONDS = listOf(120L, 300L, 600L, 0L)
private val RUN_DURATION_LABELS = listOf("2 мин", "5 мин", "10 мин", "вручную")

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
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val experiments by graph.experimentRepository.recent().collectAsState(initial = emptyList())

    Screen {
        Header(title = "A/B эксперимент", onBack = onBack)
        ExperimentalBanner()
        AppButton(
            text = "Новый эксперимент",
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
        )
        if (experiments.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(text = "экспериментов пока нет", style = type.bodySmall, color = colors.ink2)
                    Text(
                        text = "Эксперимент — два измерения в одинаковой геометрии: например, " +
                            "объект у прибора и тот же прибор без объекта. Приложение сравнит " +
                            "их по счёту, энергетическим окнам и спектру.",
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
                                    text = ExperimentFormat.kindLabel(experiment.kind),
                                    style = type.label,
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = HistoryFormat.dayTime(
                                        experiment.createdAt,
                                        System.currentTimeMillis(),
                                    ),
                                    style = type.footnote,
                                    color = colors.ink2,
                                )
                            }
                            Text(
                                text = experiment.geometry.ifBlank { "геометрия не описана" },
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
    val scope = rememberCoroutineScope()

    var kind by rememberSaveable { mutableStateOf(ExperimentEntity.KIND_BACKGROUND_VS_OBJECT) }
    var geometry by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val activeProfile by graph.profileRepository.activeProfile().collectAsState(initial = null)
    val windowsRaw by graph.settings.energyWindowsRaw.collectAsState(initial = null)

    Screen {
        Header(title = "Новый эксперимент", onBack = onCancel)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = "Сценарий", style = type.label, color = colors.ink)
                ExperimentEntity.KINDS.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Dimens.touchTarget)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { kind = option },
                            ),
                    ) {
                        RadioMark(kind == option)
                        Text(
                            text = ExperimentFormat.kindLabel(option),
                            style = type.label,
                            color = colors.ink,
                        )
                    }
                }
                Text(
                    text = ExperimentFormat.kindHint(kind),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = "Геометрия", style = type.label, color = colors.ink)
                Text(
                    text = ExperimentFormat.GEOMETRY_PROMPT,
                    style = type.footnote,
                    color = colors.muted,
                )
                AppTextField(
                    value = geometry,
                    onValueChange = { geometry = it },
                    placeholder = "например: образец на столе, прибор экраном вверх, 5 см",
                )
                Text(text = "Заметка", style = type.label, color = colors.ink)
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "что проверяем",
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            AppButton(
                text = "Создать",
                enabled = geometry.isNotBlank(),
                onClick = {
                    scope.launch {
                        val id = graph.experimentRepository.create(
                            kind = kind,
                            profileId = activeProfile?.id,
                            geometry = geometry.trim(),
                            note = note.trim(),
                            windowSpecs = EnergyWindows.parse(windowsRaw),
                        )
                        onCreated(id)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            AppButton(text = "Отмена", onClick = onCancel, modifier = Modifier.weight(1f))
        }
        Text(
            text = "Геометрия сохраняется вместе с экспериментом и показывается перед " +
                "каждым прогоном — без неё сравнение теряет смысл.",
            style = type.footnote,
            color = colors.muted,
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
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
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
            notice = it
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
                    "отчёт сохранён"
                } else {
                    "отчёт не записался — попробуйте другую папку"
                }
            }
        }
    }

    Screen {
        Header(title = "Эксперимент", onBack = onBack)
        if (current == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = "читаю эксперимент…", style = type.bodySmall, color = colors.muted)
            }
            return@Screen
        }

        ExperimentalBanner()

        // Geometry is shown on every run, not only when it was written.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(text = ExperimentFormat.kindLabel(current.kind), color = colors.dataText)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = HistoryFormat.dayTime(current.createdAt, nowMillis),
                        style = type.footnote,
                        color = colors.ink2,
                    )
                }
                Text(text = "Геометрия", style = type.labelSmall, color = colors.ink2)
                Text(
                    text = current.geometry.ifBlank { "не описана" },
                    style = type.body,
                    color = colors.ink,
                )
                if (current.note.isNotBlank()) {
                    Text(text = current.note, style = type.bodySmall, color = colors.ink2)
                }
                Text(
                    text = ExperimentFormat.kindHint(current.kind),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }

        // --- runs ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Прогоны".uppercase(), style = type.labelSmall, color = colors.ink2)
                if (runs.isEmpty()) {
                    Text(
                        text = "прогонов пока нет — начните первый",
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
                        text = "идёт прогон · " +
                            ExperimentFormat.duration((nowMillis - startedAt) / 1000L) +
                            if (plannedSeconds > 0) {
                                " из ${ExperimentFormat.duration(plannedSeconds)}"
                            } else {
                                ""
                            },
                        style = type.label,
                        color = colors.ink,
                    )
                    Text(
                        text = "Держите геометрию неизменной. Интервал исключён из обучения " +
                            "обычного фона — эксперимент не должен становиться «новой нормой».",
                        style = type.footnote,
                        color = colors.muted,
                    )
                    AppButton(
                        text = "Остановить прогон",
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
                        text = "Прогон $nextLetter · " +
                            ExperimentFormat.runRoleLabel(current.kind, nextIndex),
                        style = type.label,
                        color = colors.ink,
                    )
                    if (nextIndex > 0) {
                        Text(
                            text = "Повторите геометрию прогона A: " +
                                current.geometry.ifBlank { "она не описана" },
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                    }
                    if (current.kind == ExperimentEntity.KIND_DISTANCE) {
                        AppTextField(
                            value = distanceInput,
                            onValueChange = { distanceInput = it },
                            placeholder = "расстояние, см",
                            numeric = true,
                        )
                    }
                    if (current.kind == ExperimentEntity.KIND_SHIELDING) {
                        AppTextField(
                            value = shieldingInput,
                            onValueChange = { shieldingInput = it },
                            placeholder = "материал между объектом и прибором (пусто = без него)",
                        )
                    }
                    Segmented(
                        options = RUN_DURATION_LABELS,
                        selectedIndex = durationIndex,
                        onSelect = {
                            durationIndex = it
                            plannedSeconds = RUN_DURATION_SECONDS[it]
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = "Начать прогон $nextLetter",
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
                            text = "нет соединения с прибором — прогон записывать нечем",
                            style = type.footnote,
                            color = colors.warn,
                        )
                    }
                    if (openRun != null && recordingRunId == null) {
                        Text(
                            text = "прогон ${openRun.label} остался незавершённым (приложение " +
                                "закрывали?) — завершите или удалите его",
                            style = type.footnote,
                            color = colors.warn,
                        )
                        AppButton(
                            text = "Завершить прогон ${openRun.label}",
                            onClick = { graph.abRun.stop() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // --- comparison ---
        val comparison = remember(runData, windowSpecs) {
            val completed = runData.filter { it.endedAt != null }
            if (completed.size >= 2) {
                AbExperiment.compare(completed[0], completed[1], windowSpecs)
            } else {
                null
            }
        }
        if (comparison != null) {
            ComparisonCards(comparison)
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "для сравнения нужно два завершённых прогона",
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
            Text(
                text = ExperimentFormat.SHIELDING_WARNING,
                style = type.footnote,
                color = colors.muted,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            AppButton(
                text = "Отчёт в файл",
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
                text = "Удалить",
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
                    Text(text = "Удалить эксперимент?", style = type.title, color = colors.ink)
                    Text(
                        text = "Прогоны и их результаты удалятся вместе с ним. Сырые " +
                            "измерения и спектры прогонов останутся в базе.",
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        AppButton(
                            text = "Удалить",
                            onClick = {
                                confirmDelete = false
                                scope.launch {
                                    graph.experimentRepository.delete(experimentId)
                                    onDeleted()
                                }
                            },
                        )
                        AppButton(text = "Отмена", onClick = { confirmDelete = false })
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
private fun Header(title: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppButton(text = "← Назад", onClick = onBack)
        Spacer(Modifier.weight(1f))
        Chip(text = title, color = colors.ink)
    }
}

/** Спец §24: до валидации на реальных данных функция помечена как опытная. */
@Composable
private fun ExperimentalBanner() {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(text = ExperimentFormat.EXPERIMENTAL_BADGE, color = colors.warn)
            }
            Text(
                text = ExperimentFormat.EXPERIMENTAL_NOTE,
                style = type.footnote,
                color = colors.muted,
            )
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${run.label} · ${ExperimentFormat.runRoleLabel(kind, index)}",
                style = type.label,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (recording) {
                Text(
                    text = "запись " + ExperimentFormat.duration(elapsedSeconds ?: 0L) +
                        if (plannedSeconds > 0) {
                            "/${ExperimentFormat.duration(plannedSeconds)}"
                        } else {
                            ""
                        },
                    style = type.footnote,
                    color = colors.ok,
                )
            } else {
                Chip(text = "удалить", color = colors.ink2, onClick = onDelete)
            }
        }
        when {
            run.endedAt == null && !recording -> Text(
                text = "не завершён",
                style = type.valueSmall,
                color = colors.warn,
            )
            data != null -> {
                Text(
                    text = buildString {
                        append("Δt ").append(ExperimentFormat.duration(data.durationSeconds))
                        if (data.hasSpectrum) {
                            append(" · спектр ").append(data.totalCounts).append(" имп")
                        } else {
                            append(" · спектр не записан")
                        }
                        data.doseStats?.let {
                            append(" · доза ср ")
                            append(ExperimentFormat.decimal(it.meanMicroSvH))
                            append(" мкЗв/ч, n=").append(it.sampleCount)
                        }
                    },
                    style = type.valueSmall,
                    color = colors.ink2,
                )
                data.distanceCm?.let {
                    Text(
                        text = "расстояние " + ExperimentFormat.distance(it),
                        style = type.footnote,
                        color = colors.ink2,
                    )
                }
                data.shieldingNote?.takeIf { it.isNotBlank() }?.let {
                    Text(text = "материал: $it", style = type.footnote, color = colors.ink2)
                }
            }
        }
    }
}

@Composable
private fun ComparisonCards(comparison: AbExperiment.Comparison) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Вывод".uppercase(), style = type.labelSmall, color = colors.ink2)
                EvidenceTag(Evidence.STATISTICALLY_DETECTED, Modifier.padding(start = 6.dp))
            }
            Text(
                text = ExperimentFormat.verdictHeadline(
                    comparison.verdict,
                    comparison.a.label,
                    comparison.b.label,
                ),
                style = type.title,
                color = when (comparison.verdict) {
                    AbAnalysis.Verdict.CONSISTENT -> colors.ink
                    else -> colors.warn
                },
            )
            Text(
                text = "Вердикт относится к различию двух измерений, а не к опасности и " +
                    "не к тому, что именно найдено (спец §2).",
                style = type.footnote,
                color = colors.muted,
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
                AbHeader("показатель", 1.3f)
                AbHeader("A", 1f)
                AbHeader("B", 1f)
                AbHeader("z", 0.8f)
                AbHeader("вывод", 1.5f)
            }
            AppDivider()
            comparison.doseRate?.let { dose ->
                AbRow(
                    label = "доза, мкЗв/ч",
                    a = ExperimentFormat.decimal(dose.a.meanMicroSvH),
                    b = ExperimentFormat.decimal(dose.b.meanMicroSvH),
                    z = ExperimentFormat.zLabel(dose.z),
                    verdict = dose.verdict,
                )
                AppDivider()
            }
            comparison.totalCounts?.let { total ->
                AbRow(
                    label = "полный счёт, имп/с",
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
                    label = "полный спектр",
                    a = "χ² ${ExperimentFormat.decimal(spectrum.chiSquare)}",
                    b = "ν ${spectrum.degreesOfFreedom}",
                    z = ExperimentFormat.zLabel(spectrum.z),
                    verdict = spectrum.verdict,
                )
            }
            val method = comparison.totalCounts?.method ?: comparison.spectrum?.method
            if (method != null) {
                Text(
                    text = ExperimentFormat.methodExplanation(method),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            comparison.totalCounts?.let { total ->
                Text(
                    text = "нетто = G − B·(t_G/t_B) = " +
                        ExperimentFormat.signedCounts(total.net) +
                        " ±" + ExperimentFormat.decimal(total.netSigma) + " имп " +
                        "(σ по IAEA: √(G + B·(t_G/t_B)²))",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            comparison.doseRate?.let {
                Text(
                    text = "строка дозы — вспомогательная: секундные показания прибора " +
                        "коррелированы, её неопределённость — оценка снизу",
                    style = type.footnote,
                    color = colors.muted,
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Серия по расстоянию".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                EvidenceTag(Evidence.CALCULATED, Modifier.padding(start = 6.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                AbHeader("r", 1f)
                AbHeader("измерено, имп/с", 1.6f)
                AbHeader("1/r² от первой", 1.4f)
            }
            AppDivider()
            series.forEachIndexed { index, point ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                ) {
                    AbCell(ExperimentFormat.distance(point.distanceCm), 1f, colors.ink)
                    AbCell(
                        ExperimentFormat.cpsWithSigma(point.netRateCps, point.sigmaCps)
                            .removeSuffix(" имп/с"),
                        1.6f,
                        colors.ink,
                    )
                    AbCell(
                        point.inverseSquareCps?.let { ExperimentFormat.decimal(it) } ?: "опора",
                        1.4f,
                        colors.ink2,
                    )
                }
                if (index < series.size - 1) AppDivider()
            }
            Text(
                text = ExperimentFormat.DISTANCE_WARNING,
                style = type.footnote,
                color = colors.muted,
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
            ExperimentFormat.verdictLabel(verdict),
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
        maxLines = 1,
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
