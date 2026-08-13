package app.radiacode.ui.screens

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
import app.radiacode.AppGraph
import app.radiacode.device.DeviceModel
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.EnergyWindow
import app.radiacode.analysis.NuclideInfoLibrary
import app.radiacode.analysis.DecayFamilies
import app.radiacode.analysis.PeakDetection
import app.radiacode.analysis.SpectrumDisplay
import app.radiacode.analysis.SpectrumEdge
import app.radiacode.analysis.SpectrumMerge
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.data.export.N42
import app.radiacode.data.export.RcXml
import app.radiacode.data.export.SpectrumExport
import app.radiacode.data.toEntity
import app.radiacode.data.toSpectrum
import app.radiacode.device.ConnectionState
import app.radiacode.protocol.Spectrum
import app.radiacode.service.SpectrumHub
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.EvidenceTag
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Segmented
import androidx.compose.material3.Slider
import app.radiacode.ui.logic.SpectrumScale
import app.radiacode.ui.logic.DeviceActionBlock
import app.radiacode.ui.logic.SpectrumSource
import app.radiacode.ui.logic.SpectrumSources
import app.radiacode.ui.components.SpectrumChart
import app.radiacode.ui.components.SpectrumChartSpec
import app.radiacode.ui.components.NuclideInfoDialog
import app.radiacode.ui.components.SpectrumLineMark
import app.radiacode.ui.components.SpectrumPeakMark
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.PeakEvidenceBridge
import app.radiacode.ui.logic.PeakMatch
import app.radiacode.ui.logic.PeakRow
import app.radiacode.ui.logic.involves
import app.radiacode.ui.logic.primaryNuclide
import app.radiacode.ui.logic.SpectrumHighlight
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.SpectrumFormat
import app.radiacode.ui.logic.SpectrumFrames
import app.radiacode.ui.logic.SpectrumPlot
import app.radiacode.ui.logic.SpectrumInfo
import app.radiacode.ui.logic.SpectrumInfoLevel
import app.radiacode.ui.logic.SpectrumInfoSection
import app.radiacode.ui.logic.SpectrumViewOptions
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SpectrumCatalogue
import app.radiacode.ui.text.SpectrumStrings
import app.radiacode.ui.text.NuclideCatalogue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.radiacode.ui.theme.Motion
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
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
    onOpenExperiments: () -> Unit = {},
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                AppButton(text = "← ${strings.back}", onClick = onBack)
                Spacer(Modifier.width(Dimens.space2))
            }
            // Имя вкладки в шапке не повторяется. Метка «Снимок» остаётся:
            // это не название экрана, а состояние — что показан не живой
            // спектр, а сохранённый.
            if (viewing) {
                Chip(text = t.snapshotViewTag, color = colors.ink)
            }
            Spacer(Modifier.weight(1f))
            Chip(
                text = spectrum?.let {
                    SpectrumFormat.accumulationChip(
                        it.durationSeconds,
                        it.counts.sumOf { c -> c.toLong() },
                        t,
                    )
                } ?: strings.noData,
            )
        }
        // Чей это снимок и когда снят — первое, что нужно знать о картинке из
        // прошлого; накопление стоит рядом с самим числом импульсов.
        snapshotEntity?.let { entity ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = SpectrumExport.title(entity),
                    style = type.title,
                    color = colors.ink,
                )
                Text(
                    text = t.snapshotTakenAt(
                        at = HistoryFormat.dayTime(
                            entity.timestamp,
                            System.currentTimeMillis(),
                            s = HistoryCatalogue.of(strings.language),
                        ),
                        accumulation = SpectrumFormat.accumulationClock(entity.durationSeconds),
                    ),
                    style = type.footnote,
                    color = colors.ink2,
                )
            }
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
                        Text(
                            text = strings.spectrumAfterConnect,
                            style = type.bodySmall,
                            color = colors.muted,
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
            )
        }

        // Спектрограмма, радон и A/B — отдельные ИНСТРУМЕНТЫ, а не способы
        // нарисовать текущий спектр: они стоят после результатов анализа, а
        // не тремя мелкими чипами среди переключателей графика. У снимка их
        // нет — все трое живут живым потоком.
        if (!viewing) {
            AnalysisToolsSection(
                onOpenExperiments = onOpenExperiments,
                onOpenSpectrogram = onOpenSpectrogram,
                onOpenRadon = onOpenRadon,
            )
        }

        // Нижние действия живут ВНЕ содержимого спектра: импорт чужого файла
        // должен работать и тогда, когда прибора рядом нет и показывать нечего.
        SpectrumActionsBar(
            graph = graph,
            spectrum = spectrum,
            connected = connected,
            hubState = hubState,
            serialNumber = (connection as? ConnectionState.Connected)?.info?.serialNumber,
            onSaveOverride = onSaveMerged,
            viewingSnapshot = viewing,
            snapshotEntity = snapshotEntity,
            onCompareWith = { compareWith = it },
        )
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
private fun AnalysisToolsSection(
    onOpenExperiments: () -> Unit,
    onOpenSpectrogram: () -> Unit,
    onOpenRadon: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
            Text(
                text = t.toolsTitle.uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )
            AnalysisToolRow(t.toolCompareTitle, t.toolCompareSubtitle, onOpenExperiments)
            AppDivider()
            AnalysisToolRow(t.toolSpectrogramTitle, t.toolSpectrogramSubtitle, onOpenSpectrogram)
            AppDivider()
            AnalysisToolRow(t.toolRadonTitle, t.toolRadonSubtitle, onOpenRadon)
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
            Text(text = subtitle, style = type.footnote, color = colors.muted)
        }
        Text(text = "›", style = type.value, color = colors.ink2)
    }
}

/**
 * Нижние действия экрана Спектр: `[Сохранить снимок] [Сделать фоном] [⋯]`.
 *
 * Раньше здесь стояли две строки по три кнопки (запись фона, сохранение,
 * сброс + импорт и два экспорта) и полоса пояснений под ними — шесть
 * равновеликих кнопок на экране, где обычных действий два. Частые действия
 * остались снаружи, редкие (сброс накопления, обмен файлами) уехали в «⋯»,
 * а объяснение форматов — туда же, к самим кнопкам экспорта.
 *
 * Строка живёт ВНЕ содержимого спектра: импорт чужого файла работает и без
 * прибора, поэтому кнопки просто гаснут, когда показывать нечего.
 */
@Composable
private fun SpectrumActionsBar(
    graph: AppGraph,
    spectrum: Spectrum?,
    connected: Boolean,
    hubState: SpectrumHub.State,
    serialNumber: String?,
    /** Continuation mode: «Сохранить» persists the merged sum instead. */
    onSaveOverride: (() -> Unit)? = null,
    /** Просмотр снимка: приборных действий у него нет, и это сказано словами. */
    viewingSnapshot: Boolean = false,
    snapshotEntity: SpectrumSnapshotEntity? = null,
    onCompareWith: (Long) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hub = graph.spectrumHub

    val backgroundEntity by graph.measurementRepository.backgroundReference()
        .collectAsState(initial = null)

    var notice by remember { mutableStateOf<SpectrumFileNotice?>(null) }
    var savedAtMillis by remember { mutableStateOf<Long?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var comparePickerOpen by remember { mutableStateOf(false) }
    // Сравнивать снимок есть с чем только тогда, когда снимков больше одного.
    val otherSpectra by graph.measurementRepository.savedSpectra()
        .collectAsState(initial = emptyList())

    fun onWritten(ok: Boolean) {
        if (ok) {
            savedAtMillis = System.currentTimeMillis()
        } else {
            notice = SpectrumFileNotice(
                title = strings.exportFailedTitle,
                lines = listOf(strings.exportFailedBody),
                isError = true,
            )
        }
    }

    val exportXmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            scope.launch { onWritten(writeTextToUri(context, uri, content)) }
        }
    }
    val exportN42Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            scope.launch { onWritten(writeTextToUri(context, uri, content)) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch { notice = importRcXmlFile(graph, context, uri, s = t) }
        }
    }

    // Экспорт и импорт готовятся здесь, а вызываются из меню «⋯».
    // У снимка экспортируется ОН САМ — со своим временем, меткой и без
    // серийника подключённого сейчас прибора: этот прибор его не снимал.
    val exportSerial = if (viewingSnapshot) null else serialNumber
    val onExportXml: () -> Unit = {
        if (spectrum != null) {
            val now = snapshotEntity?.timestamp ?: System.currentTimeMillis()
            val entity = snapshotEntity ?: spectrum.toEntity(timestamp = now, accumulated = false)
            pendingExport = RcXml.write(
                SpectrumExport.toResultData(
                    entity = entity,
                    background = if (viewingSnapshot) null else backgroundEntity,
                    serialNumber = exportSerial,
                    appVersion = appVersionName(context),
                ),
            )
            exportXmlLauncher.launch(SpectrumExport.fileName(now, "xml"))
        }
    }
    val onExportN42: () -> Unit = {
        if (spectrum != null) {
            val now = snapshotEntity?.timestamp ?: System.currentTimeMillis()
            val entity = snapshotEntity ?: spectrum.toEntity(timestamp = now, accumulated = false)
            pendingExport = N42.write(
                foreground = SpectrumExport.toN42Measurement(entity, N42.CLASS_FOREGROUND),
                background = if (viewingSnapshot) null else backgroundEntity?.let {
                    SpectrumExport.toN42Measurement(it, N42.CLASS_BACKGROUND)
                },
                serialNumber = exportSerial,
                model = SpectrumExport.modelFromSerial(exportSerial),
                softwareVersion = appVersionName(context),
                // Спец §22: метод, нормализация, калибровка и версии
                // алгоритмов едут вместе с файлом.
                remarks = SpectrumExport.metadataLines(entity, appVersionName(context)),
            )
            exportN42Launcher.launch(SpectrumExport.fileName(now, "n42"))
        }
    }

    // Приборные действия остаются на своих местах и просто гаснут: исчезнув,
    // они заставили бы искать, куда делась кнопка. Причина названа строкой
    // ниже — «недоступно» без причины хуже отсутствия.
    val deviceBlock = SpectrumSources.deviceActionBlock(viewingSnapshot, connected)
    val deviceActionsEnabled = deviceBlock == DeviceActionBlock.NONE
    // Кнопки НАЗЫВАЮТ РЕЗУЛЬТАТ и объясняют себя одной строкой: «Записать
    // фон» и «Сохранить» звучали как одно и то же действие, и разницу
    // приходилось спрашивать. Снимок уходит в журнал; фон объявляет спектр
    // обычной обстановкой — именно его вычитает режим «− фон».
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            AppButton(
                text = t.saveSnapshot,
                onClick = onSaveOverride ?: { hub.request(SpectrumHub.Command.SAVE_SNAPSHOT) },
                // Снимок уже сохранён — сохранять его во второй раз незачем.
                enabled = spectrum != null && !viewingSnapshot,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(text = t.saveSnapshotNote, style = type.footnote, color = colors.muted)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            AppButton(
                text = t.setAsBackground,
                onClick = { hub.request(SpectrumHub.Command.RECORD_BACKGROUND) },
                enabled = deviceActionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(text = t.setAsBackgroundNote, style = type.footnote, color = colors.muted)
        }
        // Цель нажатия не меньше пальца: у кнопки из одного символа ширина
        // иначе получается вдвое меньше высоты.
        AppButton(
            text = "⋯",
            onClick = { moreOpen = true },
            modifier = Modifier.defaultMinSize(minWidth = Dimens.touchTarget),
        )
    }

    // Статус фона — компактной строкой у своей кнопки: «фон: 11 авг · 51 ч».
    // Пока фона нет, на его месте стоит объяснение, что он даёт: пустое
    // состояние учит первому действию, а не молчит.
    val background = backgroundEntity
    val h = HistoryCatalogue.of(strings.language)
    if (viewingSnapshot) {
        Text(
            text = t.snapshotNoDevice,
            style = type.footnote,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = Dimens.space1),
        )
    } else Text(
        text = if (background != null) {
            t.backgroundRecorded(
                at = HistoryFormat.day(background.timestamp, s = h),
                accumulation = HistoryFormat.duration(background.durationSeconds, h),
            )
        } else {
            strings.noSpectrumBackground
        },
        style = type.footnote,
        color = colors.muted,
        modifier = Modifier.padding(horizontal = Dimens.space1),
    )
    // Подтверждения последнего действия — отдельной строкой и только когда
    // они есть: в строке состояния фона им места нет.
    val confirmation = buildString {
        hubState.lastSavedAtMillis?.let { append(t.snapshotSavedAt(timeOfDay(it))) }
        savedAtMillis?.let { append(strings.savedToPrefix).append(timeOfDay(it)) }
    }.trim().trimStart('·').trim()
    if (confirmation.isNotBlank()) {
        Text(
            text = confirmation,
            style = type.footnote,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = Dimens.space1),
        )
    }

    if (moreOpen) {
        SpectrumMoreDialog(
            t = t,
            hasSpectrum = spectrum != null,
            connected = deviceActionsEnabled,
            viewingSnapshot = viewingSnapshot,
            onCompare = { moreOpen = false; comparePickerOpen = true },
            onReset = { moreOpen = false; confirmReset = true },
            onImport = { moreOpen = false; importLauncher.launch(arrayOf("*/*")) },
            onExportXml = { moreOpen = false; onExportXml() },
            onExportN42 = { moreOpen = false; onExportN42() },
            onDismiss = { moreOpen = false },
        )
    }

    if (confirmReset) {
        Dialog(onDismissRequest = { confirmReset = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(strings.resetSpectrumTitle, style = type.title, color = colors.ink)
                    Text(text = strings.resetSpectrumBody, style = type.body, color = colors.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        AppButton(
                            text = strings.reset,
                            onClick = {
                                confirmReset = false
                                hub.request(SpectrumHub.Command.RESET)
                            },
                        )
                        AppButton(text = strings.cancel, onClick = { confirmReset = false })
                    }
                }
            }
        }
    }

    if (comparePickerOpen) {
        SnapshotPickerDialog(
            spectra = otherSpectra.filter { it.id != snapshotEntity?.id },
            onPick = { id ->
                comparePickerOpen = false
                onCompareWith(id)
            },
            onDismiss = { comparePickerOpen = false },
        )
    }

    notice?.let { current ->
        SpectrumFileNoticeDialog(notice = current, onDismiss = { notice = null })
    }
}

/**
 * Выбор второго снимка для сравнения — тот же список, что в Истории, но без
 * самого открытого снимка: сравнивать спектр с самим собой нечего.
 */
@Composable
private fun SnapshotPickerDialog(
    spectra: List<SpectrumSnapshotEntity>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val h = HistoryCatalogue.of(strings.language)
    val now = System.currentTimeMillis()
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = strings.chooseSnapshotToCompare,
                    style = type.title,
                    color = colors.ink,
                )
                if (spectra.isEmpty()) {
                    Text(text = strings.noData, style = type.bodySmall, color = colors.muted)
                }
                for (entity in spectra) {
                    AppButton(
                        text = SpectrumExport.title(entity) + " · " +
                            HistoryFormat.dayTime(entity.timestamp, now, s = h),
                        onClick = { onPick(entity.id) },
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
 * Меню «⋯»: редкие операции экрана. Пояснение о форматах стоит здесь, рядом
 * с кнопками экспорта, а не полосой мелкого текста под всем экраном — его
 * читают один раз, ровно в момент выбора формата.
 */
@Composable
private fun SpectrumMoreDialog(
    t: SpectrumStrings,
    hasSpectrum: Boolean,
    connected: Boolean,
    /** Просмотр снимка: импорта чужого файла отсюда нет, зато есть сравнение. */
    viewingSnapshot: Boolean = false,
    onCompare: () -> Unit = {},
    onReset: () -> Unit,
    onImport: () -> Unit,
    onExportXml: () -> Unit,
    onExportN42: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.moreActions, style = type.title, color = colors.ink)
                if (viewingSnapshot) {
                    AppButton(
                        text = strings.compareWithAnother,
                        onClick = onCompare,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppButton(
                    text = t.resetAccumulation,
                    onClick = onReset,
                    enabled = connected,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!viewingSnapshot) {
                    AppButton(
                        text = strings.importAction,
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppButton(
                    text = strings.exportXml,
                    onClick = onExportXml,
                    enabled = hasSpectrum,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = strings.exportN42,
                    onClick = onExportN42,
                    enabled = hasSpectrum,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = strings.exportFormatsNote,
                    style = type.footnote,
                    color = colors.muted,
                )
                if (viewingSnapshot) {
                    Text(
                        text = t.snapshotNoDevice,
                        style = type.footnote,
                        color = colors.muted,
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
 * Плашка режима «продолжить накопление»: честно объясняет семантику — прибор
 * копит независимо, сумма существует только для показа и сохранения.
 */
@Composable
private fun ContinuationBanner(
    entity: SpectrumSnapshotEntity,
    merging: Boolean,
    invalidReason: String?,
    onStop: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.continuationTitle,
                    style = type.label,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Chip(text = strings.disable, color = colors.ink2, onClick = onStop)
            }
            Text(
                text = SpectrumExport.title(entity) +
                    strings.snapshotDeltaPrefix + SpectrumFormat.accumulationClock(entity.durationSeconds),
                style = type.valueSmall,
                color = colors.ink2,
            )
            when {
                invalidReason != null -> Text(
                    text = strings.sumImpossible(invalidReason),
                    style = type.footnote,
                    color = colors.warn,
                )
                merging -> Text(
                    text = strings.sumShown,
                    style = type.footnote,
                    color = colors.muted,
                )
                else -> Text(
                    text = strings.noLiveAccumulation,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            Text(
                text = strings.continuationWarning,
                style = type.footnote,
                color = colors.muted,
            )
        }
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
    var smoothing by rememberSaveable { mutableStateOf(false) }
    var window by remember { mutableStateOf<EnergyWindow?>(null) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }

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

    // --- controls: mode + scale ---
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // «Накопл. | −фон» не говорило, ЧТО вычитается: теперь режим назван
        // целиком, спектр и спектр минус записанный фон.
        Segmented(
            options = listOf(strings.spectrumModeRaw, strings.spectrumModeMinusBackground),
            selectedIndex = if (subtractOn) 1 else 0,
            onSelect = { minusBackground = it == 1 },
            enabled = { it == 0 || background != null },
            modifier = Modifier.weight(1.7f),
        )
        Segmented(
            options = listOf(strings.scaleLinear, strings.scalePower, strings.scaleLog),
            selectedIndex = when (scale) {
                SpectrumScale.Linear -> 0
                is SpectrumScale.Power -> 1
                SpectrumScale.Log -> 2
            },
            onSelect = { index ->
                settingsScope.launch {
                    graph.settings.setSpectrumScale(
                        when (index) {
                            0 -> SpectrumScale.Linear.id
                            1 -> SpectrumScale.Power(scaleRoot).id
                            else -> SpectrumScale.Log.id
                        },
                    )
                }
            },
            modifier = Modifier.weight(1.6f),
        )
        Chip(text = "i", color = colors.ink2, onClick = { infoOpen = !infoOpen })
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
        visible = infoOpen,
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
            onClose = { infoOpen = false },
        )
    }

    // --- display pipeline (raw counts never change): optional «минус фон»
    // with time-ratio normalization, then optional display-only smoothing ---
    // Кадр — та же чистая сборка, что и на полном экране ([SpectrumFrames]):
    // окно, каналы, колонки, наложение фона и верх оси. Две картинки одного
    // спектра обязаны считаться одним кодом.
    val frame = remember(spectrum, background, subtractOn, smoothing, window, scale) {
        SpectrumFrames.build(
            counts = spectrum.counts,
            durationSeconds = spectrum.durationSeconds,
            calibration = calibration,
            background = background?.counts,
            backgroundSeconds = background?.durationSeconds ?: 0L,
            window = window,
            subtract = subtractOn,
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
                    Chip(
                        text = strings.smoothing,
                        color = if (smoothing) colors.dataText else colors.ink2,
                        selected = smoothing,
                        onClick = { smoothing = !smoothing },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Dimens.space1),
                    )
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
                    onSelect = { isotope ->
                        highlightedIsotope = isotope
                        infoIsotope = isotope
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
    )
}


@Composable
private fun PeakTable(
    rows: List<PeakRow>,
    highlightedNuclide: String?,
    onSelect: (String?) -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    val type = LocalAppTypography.current

    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
            TableHeader(strings.peakTableEnergy, 0.9f)
            TableHeader(strings.peakTableNet, 0.9f)
            TableHeader(strings.peakTableSignificance, 0.9f)
            // Четвёртый заголовок — такой же, как остальные три.
            //
            // Он был собран иначе: своим стилем, без ограничения строк и в
            // своей `Row` с центрированием по вертикали. Пока текст был
            // коротким, разницы не было видно; от слова «ВОЗМОЖНОЕ» он
            // переносился, растил высоту всей строки заголовков, а соседи
            // оставались прижатыми к верху — между заголовками и первой
            // строкой появлялась пустота во весь перенос.
            //
            // Метка «гипотеза» рядом больше не нужна: слово «возможное» в
            // самом заголовке говорит ровно то же и не занимает ширины.
            TableHeader(strings.peakTableCandidate, 1.6f)
        }
        AppDivider()
        rows.forEachIndexed { index, row ->
            val match = row.match
            val target = match.primaryNuclide
            val isHighlighted = highlightedNuclide != null && match.involves(highlightedNuclide)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = target != null) { onSelect(target) }
                    .padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TableCell(SpectrumFormat.energyCell(row.peak.energyKeV), 0.9f, colors.ink)
                    TableCell(SpectrumFormat.netCell(row.peak.netCounts), 0.9f, colors.ink)
                    TableCell(
                        SpectrumFormat.significanceCell(row.peak.significance),
                        0.9f,
                        colors.ink,
                    )
                    // Искусственный кандидат — единственное, что выделяется
                    // весом и цветом внимания; артефакты и прочерки приглушены.
                    val artificial = match is PeakMatch.Candidate && !match.natural ||
                        match is PeakMatch.AmbiguousGroup && !match.natural
                    Text(
                        text = (if (isHighlighted) "▸ " else "") +
                            SpectrumFormat.matchCell(match, t),
                        style = if (artificial) {
                            type.valueSmall.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            type.valueSmall
                        },
                        color = when {
                            artificial -> colors.warn
                            match is PeakMatch.Candidate ||
                                match is PeakMatch.AmbiguousGroup -> colors.ink2
                            else -> colors.muted
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.6f),
                    )
                }
                // Детали строки: противоречие, группа неразрешимости или
                // механизм артефакта — тихой строкой под самим пиком.
                SpectrumFormat.matchNotes(match, t).forEach { note ->
                    Text(
                        text = note,
                        style = type.footnote,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            if (index < rows.size - 1) AppDivider()
        }
    }
}

@Composable
private fun RowScope.TableHeader(
    text: String,
    weight: Float,
) {
    Text(
        text = text.uppercase(),
        style = LocalAppTypography.current.overline,
        color = LocalAppColors.current.muted,
        maxLines = 1,
        // Заголовок никогда не переносится и не растит строку: на узком экране
        // он усечётся многоточием, а таблица останется таблицей.
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    color: Color,
) {
    Text(
        text = text,
        style = LocalAppTypography.current.valueSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

private val TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm")

private fun timeOfDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_OF_DAY)
