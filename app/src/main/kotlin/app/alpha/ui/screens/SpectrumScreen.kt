package app.alpha.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.alpha.AppGraph
import app.alpha.device.DeviceModel
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindow
import app.alpha.analysis.NuclideInfoLibrary
import app.alpha.analysis.DecayFamilies
import app.alpha.analysis.PeakDetection
import app.alpha.analysis.SpectrumDisplay
import app.alpha.analysis.SpectrumEdge
import app.alpha.analysis.SpectrumMerge
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.data.export.N42
import app.alpha.data.export.RcXml
import app.alpha.data.export.SpectrumExport
import app.alpha.data.toEntity
import app.alpha.data.toSpectrum
import app.alpha.device.ConnectionState
import app.alpha.protocol.Spectrum
import app.alpha.service.SpectrumHub
import app.alpha.ui.components.NavArrow
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.Card
import app.alpha.ui.components.ConfirmDialog
import app.alpha.ui.components.EntityHeader
import app.alpha.ui.components.DisclosureArrow
import app.alpha.ui.components.EntityMenuItem
import app.alpha.ui.text.ExportStrings
import app.alpha.ui.text.Strings
import app.alpha.ui.components.RenameDialog
import app.alpha.ui.components.EvidenceTag
import app.alpha.ui.components.Chip
import app.alpha.ui.components.NeedBackgroundDialog
import app.alpha.ui.components.Segmented
import androidx.compose.material3.Slider
import app.alpha.ui.logic.SpectrumScale
import app.alpha.ui.logic.DeviceActionBlock
import app.alpha.ui.logic.SpectrumSource
import app.alpha.ui.logic.SpectrumSources
import app.alpha.ui.components.SpectrumChart
import app.alpha.ui.components.SpectrumChartSpec
import app.alpha.ui.components.NuclideInfoDialog
import app.alpha.ui.components.SpectrumLineMark
import app.alpha.ui.components.SpectrumPeakMark
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.PeakEvidenceBridge
import app.alpha.ui.logic.PeakMatch
import app.alpha.ui.logic.PeakRow
import app.alpha.ui.logic.involves
import app.alpha.ui.logic.primaryNuclide
import app.alpha.ui.logic.SpectrumHighlight
import app.alpha.ui.logic.Evidence
import app.alpha.ui.logic.SpectrumFormat
import app.alpha.ui.logic.SpectrumBackgroundView
import app.alpha.ui.logic.SpectrumFrames
import app.alpha.ui.logic.SpectrumPlot
import app.alpha.ui.logic.SpectrumInfo
import app.alpha.ui.logic.SpectrumInfoLevel
import app.alpha.ui.logic.SpectrumInfoSection
import app.alpha.ui.logic.SpectrumViewOptions
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.ExportCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SpectrumCatalogue
import app.alpha.ui.text.SpectrumStrings
import app.alpha.ui.text.NuclideCatalogue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.alpha.ui.theme.Motion
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Спектр (SPEC «Spectrum», expert screen): накопление since the last reset,
 * counts/keV line chart with lin/log scale and energy-window zoom, background
 * overlay «минус фон», display-only smoothing, and the peak table
 * (E | нетто | значимость | кандидат) with cautious isotope wording.
 */
@Composable
fun SpectrumScreen(
    graph: AppGraph,
    onOpenSpectrogram: () -> Unit = {},
    onOpenRadon: () -> Unit = {},
    onOpenLineTrend: () -> Unit = {},
    onOpenExperiments: () -> Unit = {},
    onOpenFood: () -> Unit = {},
    /** Snapshot id to continue accumulating on top of (История → снимок). */
    continueSnapshotId: Long? = null,
    onStopContinuation: () -> Unit = {},
    /**
     * Снимок из Истории, открытый на просмотр: экран показывает его вместо
     * живого накопления. Второго экрана спектра нет — кривая, масштабы,
     * энергетические диапазоны, таблица пиков и карточки нуклидов общие.
     */
    snapshotId: Long? = null,
    onBack: (() -> Unit)? = null,
    /** Продолжить накопление поверх открытого снимка (действие из «⋮»). */
    onContinueSnapshot: ((Long) -> Unit)? = null,
    /**
     * Не null — экран показывает только спектр во весь экран, в том виде, в
     * каком по нему тапнули (режим, сглаживание, окно зума). Источник данных
     * выбирается здесь одним правилом ([SpectrumSources]).
     */
    fullscreenOptions: SpectrumViewOptions? = null,
    /** Тап по графику: открыть его во весь экран поверх таб-бара. */
    onOpenFullscreen: ((SpectrumViewOptions) -> Unit)? = null,
    onCloseFullscreen: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val hub = graph.spectrumHub
    val scope = rememberCoroutineScope()

    // Режим просмотра снимка: прибор здесь ни при чём — ни опроса, ни команд.
    val viewing = snapshotId != null

    // Acquisition runs only while this tab is composed (watcher refcount).
    // У открытого снимка опрашивать нечего: частый запрос спектра делит
    // однозапросный канал с секундными показаниями.
    DisposableEffect(hub, viewing) {
        if (!viewing) hub.attach()
        onDispose { if (!viewing) hub.detach() }
    }

    var snapshotEntity by remember { mutableStateOf<SpectrumSnapshotEntity?>(null) }
    var snapshotMissing by remember { mutableStateOf(false) }
    LaunchedEffect(snapshotId) {
        val loaded = snapshotId?.let { graph.measurementRepository.spectrumById(it) }
        snapshotEntity = loaded
        snapshotMissing = snapshotId != null && loaded == null
    }
    // Действия живого спектра открываются из «⋮» шапки.
    var exportingLive by remember { mutableStateOf(false) }
    var importingLive by remember { mutableStateOf(false) }
    var technicalOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var fileNotice by remember { mutableStateOf<SpectrumFileNotice?>(null) }

    // Действия над открытым снимком: экспорт, сравнение, имя, удаление.
    var exportingSnapshot by remember { mutableStateOf(false) }
    var renamingSnapshot by remember { mutableStateOf(false) }
    var deletingSnapshot by remember { mutableStateOf(false) }
    var profileForSnapshot by remember { mutableStateOf(false) }
    var comparePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var exportNote by remember { mutableStateOf<String?>(null) }
    val exportStrings = ExportCatalogue.of(strings.language)
    val fileSaver = rememberFileSaver { ok ->
        exportNote = if (ok) exportStrings.saved else exportStrings.failed
    }

    // Сравнение снимка с другим — тот же компаратор, что в Истории.
    var compareWith by remember { mutableStateOf<Long?>(null) }
    val comparedId = compareWith
    if (snapshotId != null && comparedId != null) {
        SpectrumCompareScreen(
            graph = graph,
            firstId = snapshotId,
            secondId = comparedId,
            onBack = { compareWith = null },
        )
        return
    }

    val hubState by hub.state.collectAsState()
    val connection by graph.serviceStatus.connection.collectAsState()

    // «Продолжить накопление»: сохранённый снимок плюс живой поток,
    // поканально. Прибор продолжает накапливать сам; сумма существует для
    // показа и сохранения.
    var continuationEntity by remember { mutableStateOf<SpectrumSnapshotEntity?>(null) }
    LaunchedEffect(continueSnapshotId) {
        continuationEntity = continueSnapshotId?.let { graph.measurementRepository.spectrumById(it) }
    }
    // Открытый снимок не смешивается с потоком ни при каких условиях.
    val contEntity = if (viewing) null else continuationEntity
    val liveSpectrum = hubState.spectrum
    val mergeOutcome = remember(contEntity, liveSpectrum) {
        if (contEntity == null || liveSpectrum == null || liveSpectrum.counts.isEmpty()) {
            null
        } else {
            val saved = contEntity.toSpectrum()
            SpectrumMerge.merge(
                inputs = listOf(
                    SpectrumMerge.Input(
                        counts = saved.counts,
                        durationSeconds = saved.durationSeconds,
                        calibration = EnergyCalibration(saved.a0, saved.a1, saved.a2),
                        name = SpectrumExport.title(contEntity),
                    ),
                    SpectrumMerge.Input(
                        counts = liveSpectrum.counts,
                        durationSeconds = liveSpectrum.durationSeconds,
                        calibration = EnergyCalibration(
                            liveSpectrum.a0,
                            liveSpectrum.a1,
                            liveSpectrum.a2,
                        ),
                        name = strings.spectrumAccumulating,
                    ),
                ),
                s = t,
            )
        }
    }
    val mergedSpectrum = (mergeOutcome as? SpectrumMerge.Outcome.Ok)?.let { ok ->
        Spectrum(
            durationSeconds = ok.durationSeconds,
            a0 = ok.calibration.a0,
            a1 = ok.calibration.a1,
            a2 = ok.calibration.a2,
            counts = ok.counts,
        )
    }
    // Чей это спектр — решает чистое правило (`SpectrumSources.choose`), а не
    // цепочка элвисов на экране: приоритеты источников проверяются тестом.
    val snapshotSpectrum = remember(snapshotEntity) { snapshotEntity?.toSpectrum() }
    val source = SpectrumSources.choose(
        viewingSnapshot = viewing,
        hasSnapshot = snapshotSpectrum != null,
        hasMerged = mergedSpectrum != null,
        hasLive = liveSpectrum != null,
        hasContinuation = contEntity != null,
    )
    val spectrum = when (source) {
        SpectrumSource.SNAPSHOT -> snapshotSpectrum
        SpectrumSource.MERGED_CONTINUATION -> mergedSpectrum
        SpectrumSource.LIVE -> liveSpectrum
        SpectrumSource.CONTINUATION_ONLY -> contEntity?.toSpectrum()
        SpectrumSource.NONE -> null
    }
    val connected = connection is ConnectionState.Connected

    // Полноэкранный режим: поле владеет экраном, остальное — панелями поверх.
    // Показывать нечего — режим закрывает сам себя.
    if (fullscreenOptions != null) {
        if (spectrum != null && spectrum.counts.isNotEmpty()) {
            SpectrumFullScreen(
                graph = graph,
                spectrum = spectrum,
                options = fullscreenOptions,
                viewingSnapshot = viewing,
                onBack = onCloseFullscreen,
            )
        } else {
            LaunchedEffect(Unit) { onCloseFullscreen() }
        }
        return
    }

    val onSaveMerged: (() -> Unit)? =
        if (contEntity != null && mergedSpectrum != null) {
            {
                scope.launch {
                    val now = System.currentTimeMillis()
                    graph.measurementRepository.saveSpectrum(
                        mergedSpectrum,
                        accumulated = false,
                        origin = SpectrumSnapshotEntity.ORIGIN_USER,
                        label = strings.spectrumContinuation + SpectrumExport.title(contEntity),
                    )
                    graph.measurementRepository.recordSpectrumSaved(
                        now,
                        mergedSpectrum.durationSeconds,
                    )
                    hub.onSaved(now)
                }
            }
        } else {
            null
        }

    val openEntity = snapshotEntity
    if (openEntity != null) {
        if (exportingSnapshot) {
            EntityExportSheet(
                title = exportStrings.export,
                groups = spectrumExportGroups(
                    entity = openEntity,
                    e = exportStrings,
                    appVersion = appVersionName(context),
                    language = strings.language,
                    saver = fileSaver,
                    onPicked = { exportingSnapshot = false },
                ),
                onDismiss = { exportingSnapshot = false },
            )
        }
        if (renamingSnapshot) {
            val h = HistoryCatalogue.of(strings.language)
            RenameDialog(
                title = h.routeRename,
                initial = openEntity.label.orEmpty(),
                placeholder = h.routeNameHint,
                onSave = { name ->
                    renamingSnapshot = false
                    scope.launch {
                        graph.measurementRepository.renameSpectrum(openEntity.id, name)
                        snapshotEntity = graph.measurementRepository.spectrumById(openEntity.id)
                    }
                },
                onDismiss = { renamingSnapshot = false },
            )
        }
        if (deletingSnapshot) {
            val h = HistoryCatalogue.of(strings.language)
            ConfirmDialog(
                title = h.routeDeleteTitle(1),
                body = h.routeDeleteBody,
                confirmText = strings.delete,
                onConfirm = {
                    deletingSnapshot = false
                    scope.launch {
                        graph.sessionRepository.delete(emptySet(), setOf(openEntity.id))
                        onBack?.invoke()
                    }
                },
                onDismiss = { deletingSnapshot = false },
            )
        }
        if (profileForSnapshot) {
            val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
            SessionProfileDialog(
                startedAt = openEntity.timestamp,
                profileId = openEntity.profileId,
                profiles = profiles,
                onPick = { profileId ->
                    profileForSnapshot = false
                    scope.launch {
                        graph.measurementRepository.setSpectrumProfile(
                            id = openEntity.id,
                            profileId = profileId,
                            profileName = profiles.firstOrNull { it.id == profileId }?.name,
                        )
                        snapshotEntity = graph.measurementRepository.spectrumById(openEntity.id)
                    }
                },
                onDismiss = { profileForSnapshot = false },
            )
        }
        if (comparePicker) {
            val others by graph.measurementRepository.savedSpectra()
                .collectAsState(initial = emptyList())
            SnapshotPickerDialog(
                spectra = others.filter { it.id != openEntity.id },
                onPick = { id ->
                    comparePicker = false
                    compareWith = id
                },
                onDismiss = { comparePicker = false },
            )
        }
    }

    // Импорт чужого файла работает и без прибора: это чтение, а не измерение.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch { fileNotice = importRcXmlFile(graph, context, uri, s = t) }
        }
    }
    LaunchedEffect(importingLive) {
        if (importingLive) {
            importingLive = false
            importLauncher.launch(arrayOf("*/*"))
        }
    }
    if (exportingLive && spectrum != null) {
        val now = System.currentTimeMillis()
        val liveEntity = spectrum.toEntity(timestamp = now, accumulated = false)
        EntityExportSheet(
            title = exportStrings.export,
            groups = spectrumExportGroups(
                entity = liveEntity,
                e = exportStrings,
                appVersion = appVersionName(context),
                language = strings.language,
                saver = fileSaver,
                onPicked = { exportingLive = false },
            ),
            onDismiss = { exportingLive = false },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = t.resetConfirmTitle,
            body = t.resetConfirmBody,
            confirmText = strings.reset,
            onConfirm = {
                confirmReset = false
                hub.request(SpectrumHub.Command.RESET)
            },
            onDismiss = { confirmReset = false },
        )
    }
    fileNotice?.let { current ->
        SpectrumFileNoticeDialog(notice = current, onDismiss = { fileNotice = null })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3)
            .padding(top = 0.dp),
        // Шаг между блоками уменьшен на ступень: главный вес у графика.
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        // Одна шапка на снимок и на живой спектр: имя, сводка одной строкой и
        // «⋮». Сводка отвечает, чей это спектр, сколько он копился и сколько в
        // нём импульсов.
        val entity = snapshotEntity
        if (entity != null) {
            EntityHeader(
                title = SpectrumExport.title(entity),
                subtitle = t.spectrumSummary(
                    profile = entity.profileName?.takeIf { it.isNotBlank() }
                        ?: t.noProfileShort,
                    accumulation = SpectrumFormat.accumulationClock(entity.durationSeconds),
                    counts = SpectrumFormat.countsShort(
                        spectrum?.counts?.sumOf { c -> c.toLong() } ?: 0L,
                        t,
                    ),
                ),
                onBack = onBack,
                menu = EntityMenus.spectrum(
                    strings = strings,
                    export = ExportCatalogue.of(strings.language),
                    history = HistoryCatalogue.of(strings.language),
                    canCompare = true,
                    onExport = { exportingSnapshot = true },
                    onCompare = { comparePicker = true },
                    onContinue = { onContinueSnapshot?.invoke(entity.id) },
                    onRename = { renamingSnapshot = true },
                    onProfile = { profileForSnapshot = true },
                    onDelete = { deletingSnapshot = true },
                ),
            )
        } else {
            val activeProfile by graph.profileRepository.activeProfile()
                .collectAsState(initial = null)
            EntityHeader(
                title = strings.tabSpectrum,
                subtitle = spectrum?.let {
                    t.spectrumSummary(
                        profile = activeProfile?.name ?: t.noProfileShort,
                        accumulation = SpectrumFormat.accumulationClock(it.durationSeconds),
                        counts = SpectrumFormat.countsShort(
                            it.counts.sumOf { c -> c.toLong() },
                            t,
                        ),
                    )
                } ?: strings.noData,
                onBack = onBack,
                menu = liveSpectrumMenu(
                    t = t,
                    strings = strings,
                    export = exportStrings,
                    hasSpectrum = spectrum != null,
                    connected = connected,
                    onSnapshot = { onSaveMerged?.invoke() ?: hub.request(SpectrumHub.Command.SAVE_SNAPSHOT) },
                    onBackground = { hub.request(SpectrumHub.Command.RECORD_BACKGROUND) },
                    onExport = { exportingLive = true },
                    onImport = { importingLive = true },
                    onTechnical = { technicalOpen = true },
                    onHelp = { helpOpen = true },
                    onReset = { confirmReset = true },
                ),
            )
        }
        if (snapshotMissing) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = strings.noData, style = type.bodySmall, color = colors.muted)
            }
        }
        if (contEntity != null) {
            ContinuationBanner(
                entity = contEntity,
                merging = mergedSpectrum != null,
                invalidReason = (mergeOutcome as? SpectrumMerge.Outcome.Invalid)?.reason,
                onStop = onStopContinuation,
            )
        }

        val unsupported = hubState.unsupportedFormatVersion.takeIf { !viewing }
        when {
            unsupported != null -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(strings.formatUnsupportedTitle, style = type.title, color = colors.ink)
                    Text(
                        text = strings.formatUnsupportedBody(unsupported),
                        style = type.body,
                        color = colors.ink2,
                    )
                }
            }
            spectrum == null || spectrum.counts.isEmpty() -> Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    if (viewing) {
                        // Снимок ещё читается или его нет: про прибор здесь
                        // говорить нечего.
                        Text(
                            text = if (snapshotMissing) strings.noData else t.spectrumLoading,
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                    } else if (connected) {
                        Text(
                            text = strings.spectrumReading,
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                    } else {
                        Text(
                            text = strings.noInstrumentLink,
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                        Hint(
                            text = strings.spectrumAfterConnect,
                            style = type.bodySmall,
                        )
                    }
                }
            }
            else -> SpectrumContent(
                graph = graph,
                spectrum = spectrum,
                connected = connected,
                viewingSnapshot = viewing,
                onOpenFullscreen = onOpenFullscreen,
                onOpenFood = onOpenFood,
                onOpenExperiments = onOpenExperiments,
                onOpenSpectrogram = onOpenSpectrogram,
                onOpenRadon = onOpenRadon,
                onOpenLineTrend = onOpenLineTrend,
                technicalOpen = technicalOpen,
                onCloseTechnical = { technicalOpen = false },
                helpOpen = helpOpen,
                onCloseHelp = { helpOpen = false },
            )
        }

        // Нижние действия живут вне содержимого спектра: импорт чужого файла
        // работает и без прибора. У открытого снимка своих кнопок внизу нет —
        // сравнение, экспорт, имя и удаление живут в «⋮» шапки.
        if (!viewing) {
            SpectrumActionsBar(
                graph = graph,
                spectrum = spectrum,
                connected = connected,
                hubState = hubState,
                serialNumber = (connection as? ConnectionState.Connected)?.info?.serialNumber,
                onSaveOverride = onSaveMerged,
            )
        }
        exportNote?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }
    }
}

/**
 * «Дополнительный анализ» — переходы к отдельным инструментам: строка с
 * названием, фразой о том, что она делает, и шевроном; нажимается вся строка.
 * Стоит после результатов анализа, а не среди переключателей вида.
 */
@Composable
private fun AnalysisToolsSheet(
    onOpenFood: () -> Unit,
    onOpenExperiments: () -> Unit,
    onOpenSpectrogram: () -> Unit,
    onOpenRadon: () -> Unit,
    onOpenLineTrend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                Text(text = t.toolsTitle, style = type.title, color = colors.ink)
                AnalysisToolRow(t.toolFoodTitle, t.toolFoodSubtitle, onOpenFood)
                AppDivider()
                AnalysisToolRow(t.toolCompareTitle, t.toolCompareSubtitle, onOpenExperiments)
                AppDivider()
                AnalysisToolRow(t.toolSpectrogramTitle, t.toolSpectrogramSubtitle, onOpenSpectrogram)
                AppDivider()
                AnalysisToolRow(t.toolRadonTitle, t.toolRadonSubtitle, onOpenRadon)
                AppDivider()
                AnalysisToolRow(t.toolLineTitle, t.toolLineSubtitle, onOpenLineTrend)
            }
        }
    }
}

@Composable
private fun AnalysisToolRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = type.label, color = colors.ink)
            // Подпись объясняет, что делает инструмент, и уходит вместе с
            // пояснениями.
            Hint(text = subtitle)
        }
        NavArrow()
    }
}

/**
 * «Как читать спектр» — методика по требованию: край шкалы, агрегация
 * колонок, жесты, калибровка и правила идентификации.
 */
@Composable
private fun SpectrumInfoCard(
    calibrationLine: String,
    edgeLine: String?,
    subtracted: Boolean,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t.infoTitle.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Chip(text = "✕", color = colors.ink2, onClick = onClose)
            }
            SpectrumInfoLines(
                calibrationLine = calibrationLine,
                edgeLine = edgeLine,
                fullscreenEntry = true,
                subtracted = subtracted,
            )
            // Кнопка внизу: длинный текст прокручен до конца, и крестик
            // наверху означал бы возврат пальцем через весь экран.
            AppButton(
                text = strings.close,
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Содержание справки «Как читать спектр» — один текст на оба места, где её
 * открывают: карточка под кнопкой «i» на вкладке и панель поверх поля в
 * полноэкранном режиме.
 */
@Composable
internal fun ColumnScope.SpectrumInfoLines(
    calibrationLine: String,
    /** «у верхней границы шкалы: N имп.» — диагностика, а не вывод. */
    edgeLine: String? = null,
    /** Полноэкранный режим: у поля есть курсор, и о нём надо сказать. */
    withCursor: Boolean = false,
    /** Вкладка: тап по графику открывает полный экран. */
    fullscreenEntry: Boolean = false,
    /** Включён режим «− фон». */
    subtracted: Boolean = false,
) {
    val colors = LocalAppColors.current
    val t = SpectrumCatalogue.of(LocalStrings.current.language)
    val type = LocalAppTypography.current
    val sections = remember(t, calibrationLine, edgeLine, withCursor, fullscreenEntry, subtracted) {
        SpectrumInfo.sections(
            s = t,
            calibrationLine = calibrationLine,
            edgeLine = edgeLine,
            cursor = withCursor,
            fullscreenEntry = fullscreenEntry,
            subtracted = subtracted,
        )
    }
    // Третий уровень свёрнут: «как посчитано» отвечает на вопрос, который
    // возникает после первых двух.
    var technicalOpen by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun section(section: SpectrumInfoSection) {
        // Диагностика набирается моно: это числа прибора, а не объяснение.
        val diagnostics = section.title == t.infoTechnicalTitle
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = section.title, style = type.label, color = colors.ink)
            for (line in section.lines) {
                Text(
                    text = line,
                    style = if (diagnostics) type.footnoteMono else type.bodySmall,
                    color = if (diagnostics) colors.muted else colors.ink2,
                )
            }
        }
    }

    for (s in sections) {
        if (s.level != SpectrumInfoLevel.HOW) section(s)
    }
    // Кнопка стоит над третьим уровнем: раскрытие добавляет текст под ней.
    Chip(
        text = if (technicalOpen) "${t.infoHowToggle} ▴" else "${t.infoHowToggle} ▾",
        color = colors.ink2,
        selected = technicalOpen,
        onClick = { technicalOpen = !technicalOpen },
    )
    if (technicalOpen) {
        for (s in sections) {
            if (s.level == SpectrumInfoLevel.HOW) section(s)
        }
    }
}

@Composable
private fun SpectrumContent(
    graph: AppGraph,
    spectrum: Spectrum,
    connected: Boolean,
    /** Снимок из Истории: прибор, которым он снят, не записан. */
    viewingSnapshot: Boolean = false,
    /** Тап по графику открывает его во весь экран — в том же виде. */
    onOpenFullscreen: ((SpectrumViewOptions) -> Unit)? = null,
    onOpenFood: () -> Unit = {},
    onOpenExperiments: () -> Unit = {},
    onOpenSpectrogram: () -> Unit = {},
    onOpenRadon: () -> Unit = {},
    onOpenLineTrend: () -> Unit = {},
    /** Технические данные и справка открываются из «⋮» шапки. */
    technicalOpen: Boolean = false,
    onCloseTechnical: () -> Unit = {},
    helpOpen: Boolean = false,
    onCloseHelp: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    val type = LocalAppTypography.current

    // Масштаб оси — настройка просмотра и запоминается.
    val scaleId by graph.settings.spectrumScaleId.collectAsState(initial = SpectrumScale.Log.id)
    val scaleRoot by graph.settings.spectrumScaleRoot.collectAsState(initial = 2)
    val scale = remember(scaleId, scaleRoot) { SpectrumScale.of(scaleId, scaleRoot) }
    val settingsScope = rememberCoroutineScope()
    // Один переключатель на три состояния: обычный → фон → −фон → обычный
    // ([SpectrumBackgroundView]). Наложение и вычитание — ответы на один
    // вопрос, и вместе они означали бы использование одних импульсов дважды.
    var backgroundView: SpectrumBackgroundView by rememberSaveable {
        mutableStateOf(SpectrumBackgroundView.NONE)
    }

    // Что именно нажали без записанного фона; null — ничего не нажимали.
    var needBackground by remember { mutableStateOf<String?>(null) }
    needBackground?.let { what ->
        NeedBackgroundDialog(
            what = what,
            // Записать фон можно только живым прибором.
            onRecord = if (connected && !viewingSnapshot) {
                { graph.spectrumHub.request(SpectrumHub.Command.RECORD_BACKGROUND) }
            } else {
                null
            },
            onDismiss = { needBackground = null },
        )
    }
    var smoothing by rememberSaveable { mutableStateOf(false) }
    var window by remember { mutableStateOf<EnergyWindow?>(null) }

    val backgroundEntity by graph.measurementRepository.backgroundReference()
        .collectAsState(initial = null)
    val background = remember(backgroundEntity) { backgroundEntity?.toSpectrum() }
    val hasBackground = background != null
    val subtractOn = backgroundView.subtract && hasBackground
    val overlayOn = backgroundView.overlay && hasBackground

    val calibration = remember(spectrum.a0, spectrum.a1, spectrum.a2) {
        EnergyCalibration(spectrum.a0, spectrum.a1, spectrum.a2)
    }
    // Разрешение — свойство кристалла прибора: у 103G 7,4 %, у 103 и 110
    // 8,4 %. Ширина окна поиска пиков и допуск на совпадение линии
    // пропорциональны ему.
    val connection by graph.serviceStatus.connection.collectAsState()
    // У снимка прибор не хранится, подключённый сейчас о нём ничего не
    // говорит: снимок разбирается как неопознанный прибор
    // (`SpectrumSources.analysisModel`), и это сказано словами ниже.
    val model = SpectrumSources.analysisModel(
        connectedModel = (connection as? ConnectionState.Connected)?.info?.model,
        viewingSnapshot = viewingSnapshot,
    )
    val resolution662 = model.peakResolution662

    // Ползунок степени: 1/1 совпадает с линейным, 1/2 — корень, дальше вид
    // приближается к логарифму, не становясь им. Только в своём режиме.
    if (scale is SpectrumScale.Power) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            modifier = Modifier.padding(horizontal = Dimens.space1),
        ) {
            Text(
                text = strings.powerDegree(scaleRoot),
                style = type.footnote,
                color = colors.ink2,
            )
            Slider(
                value = scaleRoot.toFloat(),
                onValueChange = { value ->
                    settingsScope.launch {
                        graph.settings.setSpectrumScaleRoot(value.roundToInt())
                    }
                },
                valueRange = SpectrumScale.MIN_ROOT.toFloat()..SpectrumScale.MAX_ROOT.toFloat(),
                steps = SpectrumScale.MAX_ROOT - SpectrumScale.MIN_ROOT - 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
    // Крайний канал — граница шкалы, а не точка спектра. Число всегда лежит в
    // технических данных справки, под графиком появляется только при заметной
    // доле импульсов за краем.
    val edgeCounts = remember(spectrum) { SpectrumEdge.edgeCounts(spectrum.counts) }
    val totalCounts = remember(spectrum) { spectrum.counts.sumOf { it.toLong() } }
    val edgeLine = if (edgeCounts > 0) {
        strings.edgeCounts(
            HistoryFormat.count(edgeCounts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
        )
    } else {
        null
    }
    // Справка раскрывается тем же движением, что на Поиске.
    AnimatedVisibility(
        visible = helpOpen,
        enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
        exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
    ) {
        SpectrumInfoCard(
            calibrationLine = SpectrumFormat.calibrationLine(
                spectrum.a0,
                spectrum.a1,
                spectrum.a2,
                spectrum.counts.size,
                t,
            ),
            edgeLine = edgeLine,
            subtracted = subtractOn,
            onClose = onCloseHelp,
        )
    }

    // Кадр — та же чистая сборка, что на полном экране ([SpectrumFrames]):
    // окно, каналы, колонки, наложение фона и верх оси. Сырые импульсы не
    // меняются: «минус фон» с нормировкой по времени и сглаживание —
    // преобразования показа.
    val frame = remember(
        spectrum, background, subtractOn, smoothing, window, scale, overlayOn,
    ) {
        SpectrumFrames.build(
            counts = spectrum.counts,
            durationSeconds = spectrum.durationSeconds,
            calibration = calibration,
            background = background?.counts,
            backgroundSeconds = background?.durationSeconds ?: 0L,
            window = window,
            subtract = subtractOn,
            overlayBackground = overlayOn,
            smoothing = smoothing,
            scale = scale,
        )
    }
    val full = frame.full
    val visible = frame.visible
    val range = frame.channels
    // Отметка энергии из справки о нуклиде: временный указатель линии по
    // калибровке, привязанный к кадру (`SpectrumHighlight`).
    var lineMark by remember { mutableStateOf<SpectrumHighlight.Mark?>(null) }
    val markAnchor = SpectrumHighlight.anchor(
        spectrumKey = SpectrumHighlight.spectrumKey(calibration, spectrum.counts.size),
        scaleId = scale.id,
        window = visible,
    )
    LaunchedEffect(lineMark, markAnchor) {
        val mark = lineMark ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        // Смена кадра (зум, сдвиг, масштаб оси, другой спектр) снимает отметку
        // сразу; иначе её снимает собственный таймаут.
        if (!SpectrumHighlight.alive(mark, markAnchor, now)) {
            lineMark = null
            return@LaunchedEffect
        }
        delay(SpectrumHighlight.remainingMillis(mark, now))
        lineMark = null
    }
    val aliveMark = lineMark?.takeIf {
        SpectrumHighlight.alive(it, markAnchor, System.currentTimeMillis())
    }
    val columns = frame.columns
    val overlayColumns = frame.overlay
    val yTop = frame.yTop

    // --- cautious isotope analysis (always on raw counts, never display data) ---
    val analysisReady = model.isSpectrometer &&
        spectrum.durationSeconds >= PeakEvidenceBridge.MIN_ANALYSIS_SECONDS
    val peaks = remember(spectrum, calibration, analysisReady) {
        if (analysisReady) {
            PeakDetection.detect(
                counts = spectrum.counts,
                calibration = calibration,
                resolution662 = resolution662,
                minEnergyKeV = model.peakFloorKeV,
            ).sortedBy { it.energyKeV }
        } else {
            emptyList()
        }
    }
    // Единственный источник вердиктов о кандидатах — движок доказательств
    // (ADR 006) через мост: таблица и справка нуклида читают один результат.
    val peakVerdict = remember(peaks, spectrum, calibration) {
        PeakEvidenceBridge.analyse(
            peaks = peaks,
            counts = spectrum.counts,
            calibration = calibration,
            resolution662 = resolution662,
        )
    }
    var highlightedIsotope by remember { mutableStateOf<String?>(null) }
    // Tapping a candidate row opens its offline reference card (спец §12).
    var infoIsotope by remember { mutableStateOf<String?>(null) }
    infoIsotope?.let { symbol ->
        // Карточка собирается на языке интерфейса; символ, энергии и выходы
        // от языка не зависят.
        val nuclideTexts = NuclideCatalogue.of(LocalStrings.current.language)
        NuclideInfoLibrary.of(symbol, nuclideTexts)?.let { nuclide ->
            NuclideInfoDialog(
                nuclide = nuclide,
                // Карточка печатает результат того же разбора, что наполнил
                // таблицу.
                check = peakVerdict.checks[symbol],
                // Тап по строке линии: лист закрывается, окно доезжает до
                // энергии, на поле появляется отметка.
                onShowOnSpectrum = { energyKeV ->
                    val aiming = SpectrumHighlight.aim(energyKeV, visible, full)
                    window = aiming.window
                    lineMark = SpectrumHighlight.Mark(
                        energyKeV = energyKeV,
                        anchor = SpectrumHighlight.anchor(
                            spectrumKey = SpectrumHighlight.spectrumKey(
                                calibration,
                                spectrum.counts.size,
                            ),
                            scaleId = scale.id,
                            window = aiming.window,
                        ),
                        shownAtMillis = System.currentTimeMillis(),
                        outcome = aiming.outcome,
                    )
                    infoIsotope = null
                },
                onDismiss = { infoIsotope = null },
            )
        }
    }
    // Подсвеченный нуклид: выбранный тапом, иначе первый искусственный
    // кандидат, иначе первый кандидат вообще.
    val highlightedNuclide = highlightedIsotope
        ?.takeIf { name -> peakVerdict.rows.any { it.match.involves(name) } }
        ?: peakVerdict.rows.firstNotNullOfOrNull { row ->
            (row.match as? PeakMatch.Candidate)?.takeIf { !it.natural }?.nuclide
        }
        ?: peakVerdict.rows.firstNotNullOfOrNull { it.match.primaryNuclide }
    val peakMarks = remember(peakVerdict, highlightedNuclide, range, frame.columnCount) {
        peakVerdict.rows.mapNotNull { row ->
            val column = SpectrumDisplay.columnForChannel(
                row.peak.channel,
                range,
                frame.columnCount,
            ) ?: return@mapNotNull null
            SpectrumPeakMark(
                columnIndex = column,
                label = "${row.peak.energyKeV.roundToInt()}",
                highlighted = highlightedNuclide != null &&
                    row.match.involves(highlightedNuclide),
            )
        }
    }

    // Высота поля — доля высоты экрана; границы зажима держат края
    // (`SpectrumPlot.fieldHeightDp`, токены `Dimens`).
    val fieldHeight = SpectrumPlot.fieldHeightDp(
        screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat(),
        minDp = Dimens.spectrumFieldMin.value,
        maxDp = Dimens.spectrumFieldMax.value,
    ).dp
    Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space2) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SpectrumChart(
                spec = SpectrumChartSpec(
                    columns = columns,
                    overlay = overlayColumns,
                    scale = scale,
                    yTop = yTop,
                    peaks = peakMarks,
                    energyTicks = SpectrumDisplay.energyTicks(visible),
                    lineMark = aliveMark?.let { mark ->
                        SpectrumHighlight.fraction(
                            energyKeV = mark.energyKeV,
                            calibration = calibration,
                            channels = range,
                            columnCount = frame.columnCount,
                        )?.let { fraction ->
                            SpectrumLineMark(
                                fraction = fraction,
                                label = t.lineMarkLabel(
                                    SpectrumFormat.energyCell(mark.energyKeV),
                                ),
                            )
                        }
                    },
                ),
                onGesture = { factor, pan, focus ->
                    var next = SpectrumDisplay.pinch(visible, full, factor, focus)
                    next = SpectrumDisplay.pan(next, full, pan)
                    window = next
                },
                // Тап открывает график во весь экран в том же виде: режим,
                // сглаживание и окно уезжают вместе с ним.
                onTap = onOpenFullscreen?.let { open ->
                    {
                        open(
                            SpectrumViewOptions.of(
                                minusBackground = subtractOn,
                                overlayBackground = overlayOn,
                                smoothing = smoothing,
                                window = visible,
                                // Энергию вне шкалы прибора нести некуда.
                                highlightKeV = aliveMark
                                    ?.takeIf { it.outcome != SpectrumHighlight.Aim.OUT_OF_SCALE }
                                    ?.energyKeV,
                            ),
                        )
                    }
                },
                height = fieldHeight,
                fieldControls = {
                    // Все три переключателя — свойства КАРТИНКИ и живут на ней
                    // одним рядом: вид оси, работа с фоном, сглаживание.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Dimens.space1),
                    ) {
                        ScaleChips(
                            scale = scale,
                            scaleRoot = scaleRoot,
                            onSelect = { picked ->
                                settingsScope.launch {
                                    graph.settings.setSpectrumScale(picked.id)
                                }
                            },
                        )
                        // Один чип на три состояния: он называет то, что
                        // сейчас нарисовано, а нажатие ведёт по кругу.
                        Chip(
                            text = if (subtractOn) {
                                strings.spectrumModeMinusBackground
                            } else {
                                t.showBackgroundCurve
                            },
                            color = if (subtractOn || overlayOn) {
                                colors.dataText
                            } else {
                                colors.ink2
                            },
                            selected = subtractOn || overlayOn,
                            onClick = {
                                if (!hasBackground) {
                                    needBackground = t.needBackgroundCurve
                                } else {
                                    backgroundView = backgroundView.next()
                                }
                            },
                        )
                        Chip(
                            text = strings.smoothing,
                            color = if (smoothing) colors.dataText else colors.ink2,
                            selected = smoothing,
                            onClick = { smoothing = !smoothing },
                        )
                    }
                },
            )
            // Легенды под полем нет: нарисованное названо переключателями над
            // графиком. Масштаб меняется щипком; двойной тап на полном экране
            // возвращает всю шкалу.
            Column(modifier = Modifier.padding(horizontal = Dimens.space1)) {
                // Отметка объясняет себя строкой: пунктир без подписи читался
                // бы как вывод о спектре. Строка живёт столько же, сколько
                // отметка.
                aliveMark?.let { mark ->
                    Text(
                        text = when (mark.outcome) {
                            SpectrumHighlight.Aim.OUT_OF_SCALE -> t.lineMarkOutOfScale
                            SpectrumHighlight.Aim.MOVED ->
                                "${t.lineMarkNote} ${t.lineMarkWindowMoved}"
                            SpectrumHighlight.Aim.VISIBLE -> t.lineMarkNote
                        },
                        style = type.footnote,
                        color = colors.ink2,
                    )
                }
                if (!connected && !viewingSnapshot) {
                    Text(
                        text = t.noLinkLastSpectrum,
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                // Край шкалы и оговорка режима «− фон» живут в «i»: под
                // графиком остаются только состояния.
            }
        }
    }

    // --- peak table (E | нетто | значимость | кандидат) ---
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                // У прибора без энергетического разрешения (органический
                // пластик, RadiaCode Zero) пики и совпадения линий смысла не
                // имеют: спектр там не разделяет энергии.
                !model.isSpectrometer -> Text(
                    text = t.noPeakAnalysis(
                        model.displayName,
                        model.crystalTitle(strings) ?: t.unknownScintillator,
                    ),
                    style = type.bodySmall,
                    color = colors.muted,
                )
                !analysisReady -> Text(
                    text = strings.notEnoughForPeaks,
                    style = type.bodySmall,
                    color = colors.muted,
                )
                peaks.isEmpty() -> Text(
                    text = strings.noPeaksFound,
                    style = type.bodySmall,
                    color = colors.muted,
                )
                else -> PeakTable(
                    rows = peakVerdict.rows,
                    highlightedNuclide = highlightedNuclide,
                    // Тап по строке открывает справку о нуклиде, если он у
                    // строки есть.
                    onSelect = { symbol ->
                        highlightedIsotope = symbol
                        infoIsotope = symbol
                    },
                )
            }
            // Родство кандидатов: Pb-214 и Bi-214 — соседи по одному ряду.
            // Строка появляется только при наличии родни и говорит о родстве,
            // ни о родителе, ни об активности.
            for (family in DecayFamilies.of(peakVerdict.evidence.candidates)) {
                val members = family.members.joinToString(", ")
                Text(
                    text = if (family.radonProgeny) {
                        t.decayFamilyRadon(members)
                    } else {
                        t.decayFamilyChain(members, family.chain)
                    },
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            // Чем считали пики — часть их прочтения, а не примечание к экрану.
            if (viewingSnapshot) {
                Text(
                    text = t.snapshotDeviceUnknown,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            // Отказ остаётся на картинке — заголовком колонки «возможное
            // совпадение»; полная оговорка живёт в справке «i».
        }
    }

    // Спектральные диапазоны (спец §7): состав спектра, не мера опасности.
    // Карточка помнит своё состояние.
    SpectralRangesCard(
        graph = graph,
        counts = spectrum.counts,
        durationSeconds = spectrum.durationSeconds,
        calibration = calibration,
        technicalOpen = technicalOpen,
        onCloseTechnical = onCloseTechnical,
    )

    // Анализ: строка называет, что за ней, список приезжает по нажатию.
    if (!viewingSnapshot) {
        var toolsOpen by remember { mutableStateOf(false) }
        Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space2) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = Dimens.touchTarget)
                    .clickable { toolsOpen = true },
            ) {
                Text(
                    text = t.analysisRow.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                DisclosureArrow(expanded = false)
            }
        }
        if (toolsOpen) {
            AnalysisToolsSheet(
                onOpenFood = { toolsOpen = false; onOpenFood() },
                onOpenExperiments = { toolsOpen = false; onOpenExperiments() },
                onOpenSpectrogram = { toolsOpen = false; onOpenSpectrogram() },
                onOpenRadon = { toolsOpen = false; onOpenRadon() },
                onOpenLineTrend = { toolsOpen = false; onOpenLineTrend() },
                onDismiss = { toolsOpen = false },
            )
        }
    }
}


/**
 * Выбор масштаба оси значений прямо над графиком.
 *
 * Чип называет ТЕКУЩИЙ вид; по нажатию рядом с ним выезжают два остальных, и
 * выбор их закрывает. Ряд из трёх сегментов стоял бы над графиком постоянно и
 * отнимал у него высоту ради выбора, который делают под задачу: линейный
 * показывает, где счёт велик, степенной вытягивает середину, логарифм
 * уравнивает декады.
 */
@Composable
private fun ScaleChips(
    scale: SpectrumScale,
    scaleRoot: Int,
    onSelect: (SpectrumScale) -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    var open by rememberSaveable { mutableStateOf(false) }

    fun label(value: SpectrumScale): String = when (value) {
        SpectrumScale.Linear -> strings.scaleLinear
        is SpectrumScale.Power -> strings.scalePower
        SpectrumScale.Log -> strings.scaleLog
    }

    val others = listOf(SpectrumScale.Linear, SpectrumScale.Power(scaleRoot), SpectrumScale.Log)
        .filter { it.id != scale.id }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
    ) {
        Chip(
            text = label(scale),
            color = if (open) colors.dataText else colors.ink2,
            selected = open,
            onClick = { open = !open },
        )
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(Motion.fast()) + expandHorizontally(Motion.springy()),
            exit = fadeOut(Motion.fast()) + shrinkHorizontally(Motion.fast()),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                for (option in others) {
                    Chip(
                        text = label(option),
                        color = colors.ink2,
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/**
 * «⋮» живого спектра: действия над спектром в порядке частоты. Сброс
 * накопления стоит последним и спрашивает подтверждение.
 */
internal fun liveSpectrumMenu(
    t: SpectrumStrings,
    strings: Strings,
    export: ExportStrings,
    hasSpectrum: Boolean,
    connected: Boolean,
    onSnapshot: () -> Unit,
    onBackground: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onTechnical: () -> Unit,
    onHelp: () -> Unit,
    onReset: () -> Unit,
): List<EntityMenuItem> = listOf(
    EntityMenuItem(t.makeSnapshot, enabled = hasSpectrum, onClick = onSnapshot),
    EntityMenuItem(t.setAsBackground, enabled = hasSpectrum && connected, onClick = onBackground),
    EntityMenuItem(export.export, enabled = hasSpectrum, onClick = onExport),
    EntityMenuItem(strings.importAction, onClick = onImport),
    EntityMenuItem(t.technicalTitle, enabled = hasSpectrum, onClick = onTechnical),
    EntityMenuItem(t.infoTitle, onClick = onHelp),
    EntityMenuItem(t.resetAccumulation, enabled = connected, onClick = onReset),
)
