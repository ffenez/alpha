package app.alpha.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
     * Снимок из Истории, открытый на просмотр: экран показывает ЕГО вместо
     * живого накопления.
     *
     * Второго экрана спектра не существует намеренно — кривая, три масштаба,
     * энергетические диапазоны, таблица пиков со значимостью, подсказки
     * нуклидов и их карточки обязаны быть теми же самыми, иначе снимок
     * разбирался бы по другим правилам, чем то, что видно вживую.
     */
    snapshotId: Long? = null,
    onBack: (() -> Unit)? = null,
    /** Продолжить накопление поверх открытого снимка (действие из «⋮»). */
    onContinueSnapshot: ((Long) -> Unit)? = null,
    /**
     * Не null — экран показывает ТОЛЬКО спектр во весь экран, в том виде, в
     * каком по нему тапнули (режим, сглаживание, окно зума).
     *
     * Второго экрана спектра снова не заводится: источник данных (живое
     * накопление, продолженное накопление, снимок из Истории) выбирается
     * здесь одним правилом, а полноэкранный режим — способ на него смотреть.
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
    // однозапросный канал с секундными показаниями, и тратить его ради
    // картинки из прошлого нельзя.
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
    // Действия живого спектра: всё, что раньше стояло кнопками и полосами
    // переключателей, теперь открывается из «⋮» шапки.
    var exportingLive by remember { mutableStateOf(false) }
    var importingLive by remember { mutableStateOf(false) }
    var scaleMenuOpen by remember { mutableStateOf(false) }
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

    // --- «продолжить накопление»: saved snapshot + live stream, channel-wise.
    // The device keeps accumulating on its own regardless; the sum exists for
    // display and saving only (documented in the banner below).
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

    // Полноэкранный режим: поле владеет экраном, всё остальное — панелями
    // поверх. Показывать нечего — режим сам себя закрывает, а не остаётся
    // пустым чёрным полем без выхода.
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
    if (scaleMenuOpen) {
        SpectrumScaleDialog(graph = graph, onDismiss = { scaleMenuOpen = false })
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
        // Плотнее: главный вес на экране — у графика, а не у промежутков между
        // разделами. Шаг между блоками уменьшен на ступень.
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        // Открытый снимок — такая же запись, как сессия и маршрут, и шапка у
        // него та же: имя, время съёмки с накоплением и «⋮» с действиями.
        // Живой спектр — не запись, у него шапки нет: он всегда «сейчас».
        // Одна шапка на снимок и на живой спектр: имя, сводка одной строкой и
        // «⋮». Сводка отвечает на вопросы, которые задают о спектре в первую
        // очередь — ЧЕЙ он, СКОЛЬКО копился и сколько в нём импульсов, — а
        // переключатели вида уехали туда, где их ищут по надобности.
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
                    onScale = { scaleMenuOpen = true },
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
                        // Снимок либо ещё читается, либо его нет — про прибор
                        // здесь говорить нечего.
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

        // Нижние действия живут ВНЕ содержимого спектра: импорт чужого файла
        // должен работать и тогда, когда прибора рядом нет и показывать нечего.
        // У открытого снимка своих кнопок внизу нет: сохранять его второй раз
        // незачем, а всё остальное — сравнение, экспорт, имя, удаление —
        // живёт в «⋮» шапки, как у любой другой записи журнала.
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
 * «Дополнительный анализ» — переходы к отдельным инструментам.
 *
 * Раньше это были три мелких чипа в самом верху, между переключателями
 * текущего графика: сверху экрана человек читает, КАК показан спектр, и
 * «Радон» среди «Лин · Степень · Лог» выглядел таким же переключателем вида.
 * Инструменты отвечают на свои вопросы и стоят после результатов анализа —
 * строкой с названием, одной фразой о том, что она делает, и шевроном; вся
 * строка нажимается.
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
            // Подпись инструмента объясняет, что он делает; название и так
            // называет его, поэтому подпись уходит вместе с пояснениями.
            Hint(text = subtitle)
        }
        NavArrow()
    }
}

/**
 * «Как читать спектр» — методика по требованию.
 *
 * Абзацы про край шкалы, агрегацию колонок, жесты, калибровку и правила
 * идентификации объясняли всё верно, но занимали место постоянно и читались
 * один раз. Научность обеспечивают однозначные величины и доступность
 * методики, а не её присутствие на экране в каждый момент.
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
            // Кнопка внизу — как в справке Поиска: длинный текст прокручен до
            // конца, и закрывать его крестиком наверху значит возвращаться
            // пальцем через весь экран.
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
 * полноэкранном режиме. Второй копии этих объяснений не существует
 * намеренно: разойдясь, они рассказали бы об одной картинке разное.
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
    // возникает после первых двух, и открытым он стоит между человеком и
    // ответом «что это за горб».
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
    // Кнопка стоит НАД третьим уровнем: раскрытие добавляет текст под ней, а
    // не уводит саму кнопку вниз из-под пальца.
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

    // Масштаб оси — настройка ПРОСМОТРА, и она запоминается: человек выбирает
    // её под задачу (искать пик, смотреть форму континуума), а не заново при
    // каждом открытии.
    val scaleId by graph.settings.spectrumScaleId.collectAsState(initial = SpectrumScale.Log.id)
    val scaleRoot by graph.settings.spectrumScaleRoot.collectAsState(initial = 2)
    val scale = remember(scaleId, scaleRoot) { SpectrumScale.of(scaleId, scaleRoot) }
    val settingsScope = rememberCoroutineScope()
    var minusBackground by rememberSaveable { mutableStateOf(false) }

    // Серая кривая записанного фона — по просьбе, а не всегда: наложение это
    // СРАВНЕНИЕ, и начинает его человек, когда оно ему нужно. Постоянная
    // вторая кривая поверх данных читается как часть измерения.
    var showBackground by rememberSaveable { mutableStateOf(false) }

    // Что именно нажали без записанного фона; null — ничего не нажимали.
    // Выключенная кнопка молчала, и «почему не работает» человеку было
    // неоткуда узнать.
    var needBackground by remember { mutableStateOf<String?>(null) }
    needBackground?.let { what ->
        NeedBackgroundDialog(
            what = what,
            // Записать фон можно только живым прибором: у снимка из Истории и
            // без соединения предлагать это действие было бы обманом.
            onRecord = if (connected && !viewingSnapshot) {
                { graph.spectrumHub.request(SpectrumHub.Command.RECORD_BACKGROUND) }
            } else {
                null
            },
            onDismiss = { needBackground = null },
        )
    }
    var smoothing by rememberSaveable { mutableStateOf(false) }
    var scalePicker by remember { mutableStateOf(false) }
    var window by remember { mutableStateOf<EnergyWindow?>(null) }

    val backgroundEntity by graph.measurementRepository.backgroundReference()
        .collectAsState(initial = null)
    val background = remember(backgroundEntity) { backgroundEntity?.toSpectrum() }
    val subtractOn = minusBackground && background != null

    val calibration = remember(spectrum.a0, spectrum.a1, spectrum.a2) {
        EnergyCalibration(spectrum.a0, spectrum.a1, spectrum.a2)
    }
    // Разрешение — свойство КРИСТАЛЛА этого прибора: у 103G оно 7,4 %, у 103
    // и 110 — 8,4 %. Ширина окна поиска пиков и допуск на совпадение линии
    // пропорциональны ему.
    val connection by graph.serviceStatus.connection.collectAsState()
    // У снимка прибор не хранится, а подключённый сейчас ничего о нём не
    // говорит: снимок разбирается как НЕОПОЗНАННЫЙ прибор (правило и его
    // причина — в `SpectrumSources.analysisModel`), и это сказано словами
    // ниже, а не подставлено молча.
    val model = SpectrumSources.analysisModel(
        connectedModel = (connection as? ConnectionState.Connected)?.info?.model,
        viewingSnapshot = viewingSnapshot,
    )
    val resolution662 = model.peakResolution662

    // --- две кнопки вместо двух рядов переключателей ---
    //
    // Раньше здесь стояли шесть сегментов («Спектр | − фон» и «Лин | Степень |
    // Лог») плюс «i». Лин/Степень/Лог — выбор ВИДА, который делают один раз
    // под задачу, и держать его постоянной панелью над графиком значит
    // отбирать высоту у самого графика: масштаб уехал в «⋮ → Масштаб Y», а
    // здесь остался чип с текущим видом (он же открывает выбор) и «− фон».
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(
            text = when (scale) {
                SpectrumScale.Linear -> strings.scaleLinear
                is SpectrumScale.Power -> strings.scalePower
                SpectrumScale.Log -> strings.scaleLog
            },
            color = colors.ink2,
            onClick = { scalePicker = true },
        )
        Chip(
            text = strings.spectrumModeMinusBackground,
            color = if (subtractOn) colors.dataText else colors.ink2,
            selected = subtractOn,
            onClick = {
                when {
                    subtractOn -> minusBackground = false
                    background != null -> minusBackground = true
                    else -> needBackground = t.needBackgroundSubtract
                }
            },
        )
    }
    if (scalePicker) {
        SpectrumScaleDialog(graph = graph, onDismiss = { scalePicker = false })
    }
    // Ползунок степени: 1/1 совпадает с линейным, 1/2 — привычный в
    // гамма-спектрометрии корень, дальше вид приближается к логарифму, не
    // становясь им. Показывается только в своём режиме.
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
    // технических данных справки, а под графиком появляется ТОЛЬКО когда за
    // краем оказалась заметная доля импульсов: у RC-110 там почти всегда
    // что-то есть, и постоянная строка перестала читаться.
    val edgeCounts = remember(spectrum) { SpectrumEdge.edgeCounts(spectrum.counts) }
    val totalCounts = remember(spectrum) { spectrum.counts.sumOf { it.toLong() } }
    val edgeLine = if (edgeCounts > 0) {
        strings.edgeCounts(
            HistoryFormat.count(edgeCounts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
        )
    } else {
        null
    }
    // Справка раскрывается тем же движением, что на Поиске: одно и то же
    // действие обязано выглядеть одинаково на всех экранах.
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

    // --- display pipeline (raw counts never change): optional «минус фон»
    // with time-ratio normalization, then optional display-only smoothing ---
    // Кадр — та же чистая сборка, что и на полном экране ([SpectrumFrames]):
    // окно, каналы, колонки, наложение фона и верх оси. Две картинки одного
    // спектра обязаны считаться одним кодом.
    val frame = remember(
        spectrum, background, subtractOn, smoothing, window, scale, showBackground,
    ) {
        SpectrumFrames.build(
            counts = spectrum.counts,
            durationSeconds = spectrum.durationSeconds,
            calibration = calibration,
            background = background?.counts,
            backgroundSeconds = background?.durationSeconds ?: 0L,
            window = window,
            subtract = subtractOn,
            overlayBackground = showBackground,
            smoothing = smoothing,
            scale = scale,
        )
    }
    val full = frame.full
    val visible = frame.visible
    val range = frame.channels
    // Отметка энергии из справки о нуклиде: временный указатель «вот где эта
    // линия по калибровке», привязанный к КАДРУ (см. `SpectrumHighlight`).
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
            ).sortedBy { it.energyKeV }
        } else {
            emptyList()
        }
    }
    // Единственный источник вердиктов о кандидатах — движок доказательств
    // (ADR 006) через мост: и колонка таблицы, и справка нуклида читают ЭТОТ
    // результат, поэтому два ответа на один вопрос разойтись не могут.
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
        // Справка о нуклиде — тексты области, поэтому карточка собирается на
        // языке интерфейса; символ, энергии и выходы от языка не зависят.
        val nuclideTexts = NuclideCatalogue.of(LocalStrings.current.language)
        NuclideInfoLibrary.of(symbol, nuclideTexts)?.let { nuclide ->
            NuclideInfoDialog(
                nuclide = nuclide,
                // Карточка печатает результат ТОГО ЖЕ разбора, что наполнил
                // таблицу: ничего не пересчитывается.
                check = peakVerdict.checks[symbol],
                // Тап по строке линии: лист закрывается, окно при необходимости
                // доезжает до энергии, и на поле появляется отметка.
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
    // кандидат, иначе первый кандидат вообще — тот же порядок, что был у
    // подсказок матчера.
    val highlightedNuclide = highlightedIsotope
        ?.takeIf { name -> peakVerdict.rows.any { it.match.involves(name) } }
        ?: peakVerdict.rows.firstNotNullOfOrNull { row ->
            (row.match as? PeakMatch.Candidate)?.takeIf { !it.natural }?.nuclide
        }
        ?: peakVerdict.rows.firstNotNullOfOrNull { it.match.primaryNuclide }
    val peakMarks = remember(peakVerdict, highlightedNuclide, range) {
        peakVerdict.rows.mapNotNull { row ->
            val column = SpectrumDisplay.columnForChannel(
                row.peak.channel,
                range,
                SpectrumFrames.COLUMN_COUNT,
            ) ?: return@mapNotNull null
            SpectrumPeakMark(
                columnIndex = column,
                label = "${row.peak.energyKeV.roundToInt()}",
                highlighted = highlightedNuclide != null &&
                    row.match.involves(highlightedNuclide),
            )
        }
    }

    // --- chart card ---
    // Поле — доля высоты экрана: спектр это главная картинка вкладки, и на
    // телефоне ей положено больше, чем полоска в 170 dp. Границы зажима держат
    // края (см. `SpectrumPlot.fieldHeightDp` и токены `Dimens`).
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
                            columnCount = SpectrumFrames.COLUMN_COUNT,
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
                // Тап по графику открывает его во весь экран — ровно в том
                // виде, в каком по нему тапнули: режим, сглаживание и окно
                // уезжают вместе с ним, картинка не подменяется.
                onTap = onOpenFullscreen?.let { open ->
                    {
                        open(
                            SpectrumViewOptions.of(
                                minusBackground = subtractOn,
                                smoothing = smoothing,
                                window = visible,
                                // Энергию вне шкалы прибора нести некуда:
                                // на большом поле её место так же не существует.
                                highlightKeV = aliveMark
                                    ?.takeIf { it.outcome != SpectrumHighlight.Aim.OUT_OF_SCALE }
                                    ?.energyKeV,
                            ),
                        )
                    }
                },
                height = fieldHeight,
                // Сглаживание — свойство КАРТИНКИ, поэтому переключатель живёт
                // на самой картинке, компактной кнопкой в углу поля: строка
                // кнопок под графиком отнимала у него высоту ради одного
                // нажатия. Обведён = сглаживание включено (правило чипов).
                fieldControls = {
                    // Оба переключателя — свойства КАРТИНКИ, поэтому живут на
                    // ней: сглаживание и показ записанного фона. В строке над
                    // графиком чип фона стоял среди сегментов режима и читался
                    // как ещё один режим, хотя он про то, что нарисовано.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Dimens.space1),
                    ) {
                        if (!subtractOn) {
                            Chip(
                                text = t.showBackgroundCurve,
                                color = if (showBackground) colors.dataText else colors.ink2,
                                selected = showBackground,
                                onClick = {
                                    if (background == null) {
                                        needBackground = t.needBackgroundCurve
                                    } else {
                                        showBackground = !showBackground
                                    }
                                },
                            )
                        }
                        Chip(
                            text = strings.smoothing,
                            color = if (smoothing) colors.dataText else colors.ink2,
                            selected = smoothing,
                            onClick = { smoothing = !smoothing },
                        )
                    }
                },
            )
            // Легенды под полем нет: что нарисовано, названо переключателями
            // НАД графиком («Спектр | − фон» и сам фон), а строка внизу
            // повторяла их третий раз и забирала высоту у картинки.
            // Полосы кнопок под графиком больше нет: масштаб меняется щипком —
            // тем же жестом, что и везде, — а кнопки «−» и «+» дублировали его
            // и забирали у поля высоту. Двойной тап на полном экране
            // по-прежнему возвращает всю шкалу.
            Column(modifier = Modifier.padding(horizontal = Dimens.space1)) {
                // Отметка обязана объяснить себя: вертикальный пунктир на поле
                // без подписи читался бы как вывод о спектре. Строка живёт
                // ровно столько же, сколько сама отметка.
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
                // Ни края шкалы, ни оговорки режима «− фон» под полем больше
                // нет: оба живут в «i» — край в технических данных, кламп
                // нулём в разделе «как построена картинка». Под графиком
                // остаются только состояния, а не объяснения.
            }
        }
    }

    // --- peak table (E | нетто | значимость | кандидат) ---
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                // У прибора без энергетического разрешения (органический
                // пластик, RadiaCode Zero) пики и совпадения линий не имеют
                // смысла: спектр там не разделяет энергии.
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
                    // Тап по строке — справка о нуклиде, если он у строки
                    // есть, и ничего, если нет: маленькое окно с площадью
                    // открывалось на каждое нажатие и мешало.
                    onSelect = { symbol ->
                        highlightedIsotope = symbol
                        infoIsotope = symbol
                    },
                )
            }
            // Родство кандидатов: Pb-214 рядом с Bi-214 читается как две
            // независимые находки, хотя это соседи по одному ряду и вместе они
            // и встречаются. Строка появляется, только когда родня реально
            // есть, и говорит РОВНО о родстве — ни родителя, ни активности.
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
            // Оговорка под таблицей убрана: она целиком есть в справке «i».
            // Отказ остаётся НА КАРТИНКЕ — заголовком колонки: там написано
            // «возможное совпадение», поэтому имя нуклида ни в одной строке не
            // выглядит как найденный нуклид.
        }
    }

    // --- спектральные диапазоны (спец §7): состав спектра, не мера опасности.
    // Карточка сама себя сворачивает и помнит состояние — блок открывает тот,
    // кому нужны границы анализа, а не каждый, кто открыл спектр.
    SpectralRangesCard(
        graph = graph,
        counts = spectrum.counts,
        durationSeconds = spectrum.durationSeconds,
        calibration = calibration,
        technicalOpen = technicalOpen,
        onCloseTechnical = onCloseTechnical,
    )

    // --- анализ: одна строка вместо карточки-лаунчера ---
    //
    // Пять инструментов списком занимали пол-экрана под спектром, хотя
    // открывают их редко и по конкретному поводу. Строка называет, что за
    // ней, а сам список приезжает по нажатию.
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
 * Выбор масштаба оси значений.
 *
 * Три вида смотрят на один спектр по-разному: линейный показывает, где счёт
 * действительно велик, степенной вытягивает середину, логарифм уравнивает
 * декады. Это выбор ПОД ЗАДАЧУ, который делают один раз, — поэтому он живёт в
 * «⋮», а не полосой сегментов над графиком.
 */
@Composable
internal fun SpectrumScaleDialog(graph: AppGraph, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    val scope = rememberCoroutineScope()
    val scaleId by graph.settings.spectrumScaleId.collectAsState(initial = SpectrumScale.Log.id)
    val scaleRoot by graph.settings.spectrumScaleRoot.collectAsState(initial = 2)
    val scale = remember(scaleId, scaleRoot) { SpectrumScale.of(scaleId, scaleRoot) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.scaleMenuTitle, style = type.title, color = colors.ink)
                Segmented(
                    options = listOf(strings.scaleLinear, strings.scalePower, strings.scaleLog),
                    selectedIndex = when (scale) {
                        SpectrumScale.Linear -> 0
                        is SpectrumScale.Power -> 1
                        SpectrumScale.Log -> 2
                    },
                    onSelect = { index ->
                        scope.launch {
                            graph.settings.setSpectrumScale(
                                when (index) {
                                    0 -> SpectrumScale.Linear.id
                                    1 -> SpectrumScale.Power(scaleRoot).id
                                    else -> SpectrumScale.Log.id
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Степень — параметр выбранного вида, поэтому ползунок стоит
                // рядом с ним и только тогда, когда вид выбран.
                if (scale is SpectrumScale.Power) {
                    Text(
                        text = strings.powerDegree(scaleRoot),
                        style = type.footnote,
                        color = colors.ink2,
                    )
                    Slider(
                        value = scaleRoot.toFloat(),
                        onValueChange = { value ->
                            scope.launch {
                                graph.settings.setSpectrumScaleRoot(value.roundToInt())
                            }
                        },
                        valueRange = SpectrumScale.MIN_ROOT.toFloat()..
                            SpectrumScale.MAX_ROOT.toFloat(),
                        steps = SpectrumScale.MAX_ROOT - SpectrumScale.MIN_ROOT - 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppButton(
                    text = strings.close,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * «⋮» живого спектра: всё, что делают со спектром, в одном месте и в порядке
 * частоты. Сброс накопления стоит последним и спрашивает подтверждение —
 * разрушающее действие не должно быть первым, куда попадает палец.
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
    onScale: () -> Unit,
    onTechnical: () -> Unit,
    onHelp: () -> Unit,
    onReset: () -> Unit,
): List<EntityMenuItem> = listOf(
    EntityMenuItem(t.makeSnapshot, enabled = hasSpectrum, onClick = onSnapshot),
    EntityMenuItem(t.setAsBackground, enabled = hasSpectrum && connected, onClick = onBackground),
    EntityMenuItem(export.export, enabled = hasSpectrum, onClick = onExport),
    EntityMenuItem(strings.importAction, onClick = onImport),
    EntityMenuItem(t.scaleMenuTitle, onClick = onScale),
    EntityMenuItem(t.technicalTitle, enabled = hasSpectrum, onClick = onTechnical),
    EntityMenuItem(t.infoTitle, onClick = onHelp),
    EntityMenuItem(t.resetAccumulation, enabled = connected, onClick = onReset),
)
