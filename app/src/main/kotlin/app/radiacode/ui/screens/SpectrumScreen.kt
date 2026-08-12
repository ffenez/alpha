package app.radiacode.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import app.radiacode.ui.theme.Motion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.radiacode.AppGraph
import app.radiacode.device.DeviceModel
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.EnergyWindow
import app.radiacode.analysis.IsotopeHint
import app.radiacode.analysis.IsotopeMatcher
import app.radiacode.analysis.NuclideInfoLibrary
import app.radiacode.analysis.Peak
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
import app.radiacode.ui.components.SpectrumChart
import app.radiacode.ui.components.SpectrumChartSpec
import app.radiacode.ui.components.NuclideInfoDialog
import app.radiacode.ui.components.SpectrumPeakMark
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.SpectrumFormat
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Chart resolution: 1024 channels aggregate into this many line points. */
private const val COLUMN_COUNT = 240

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
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val hub = graph.spectrumHub
    val scope = rememberCoroutineScope()

    // Acquisition runs only while this tab is composed (watcher refcount).
    DisposableEffect(hub) {
        hub.attach()
        onDispose { hub.detach() }
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
    val contEntity = continuationEntity
    val liveSpectrum = hubState.spectrum
    val mergeOutcome = remember(contEntity, liveSpectrum) {
        if (contEntity == null || liveSpectrum == null || liveSpectrum.counts.isEmpty()) {
            null
        } else {
            val saved = contEntity.toSpectrum()
            SpectrumMerge.merge(
                listOf(
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
                        name = "текущее накопление",
                    ),
                ),
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
    // Display priority: merged sum → live stream → the saved snapshot alone
    // (continuation chosen while offline = an honest snapshot viewer).
    val spectrum = mergedSpectrum
        ?: liveSpectrum
        ?: contEntity?.toSpectrum()
    val connected = connection is ConnectionState.Connected

    val onSaveMerged: (() -> Unit)? =
        if (contEntity != null && mergedSpectrum != null) {
            {
                scope.launch {
                    val now = System.currentTimeMillis()
                    graph.measurementRepository.saveSpectrum(
                        mergedSpectrum,
                        accumulated = false,
                        origin = SpectrumSnapshotEntity.ORIGIN_USER,
                        label = "продолжение: " + SpectrumExport.title(contEntity),
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
            Chip(text = "Спектр", color = colors.ink)
            Spacer(Modifier.weight(1f))
            Chip(
                text = spectrum?.let {
                    SpectrumFormat.accumulationChip(
                        it.durationSeconds,
                        it.counts.sumOf { c -> c.toLong() },
                    )
                } ?: "нет данных",
            )
        }
        // Advanced-режимы поверх вкладки; в нижнее меню они не выносятся.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Spacer(Modifier.weight(1f))
            Chip(
                text = "A/B ▸",
                color = colors.dataText,
                onClick = onOpenExperiments,
            )
            Chip(
                text = "Спектрограмма ▸",
                color = colors.dataText,
                onClick = onOpenSpectrogram,
            )
            Chip(
                text = "Радон ▸",
                color = colors.dataText,
                onClick = onOpenRadon,
            )
        }

        if (contEntity != null) {
            ContinuationBanner(
                entity = contEntity,
                merging = mergedSpectrum != null,
                invalidReason = (mergeOutcome as? SpectrumMerge.Outcome.Invalid)?.reason,
                onStop = onStopContinuation,
            )
        }

        val unsupported = hubState.unsupportedFormatVersion
        when {
            unsupported != null -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text("Формат не поддержан", style = type.title, color = colors.ink)
                    Text(
                        text = "Прибор передаёт спектр в формате версии $unsupported, " +
                            "который это приложение пока не умеет читать. Остальные " +
                            "экраны работают как обычно.",
                        style = type.body,
                        color = colors.ink2,
                    )
                }
            }
            spectrum == null || spectrum.counts.isEmpty() -> Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    if (connected) {
                        Text(
                            text = "читаем спектр с прибора…",
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                    } else {
                        Text(
                            text = "нет соединения с прибором",
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                        Text(
                            text = "Спектр появится после подключения — статус соединения " +
                                "виден на Главной.",
                            style = type.bodySmall,
                            color = colors.muted,
                        )
                    }
                }
            }
            else -> SpectrumContent(graph, spectrum, connected, hubState, onSaveMerged)
        }

        FileActionsSection(
            graph = graph,
            spectrum = spectrum,
            serialNumber = (connection as? ConnectionState.Connected)?.info?.serialNumber,
        )
    }
}

/**
 * Обмен файлами спектров: импорт RC-XML через SAF и экспорт текущего
 * накопления (с записанным фоном, если он есть). Доступен и без прибора —
 * чужой файл можно изучать в Истории и сравнении.
 */
@Composable
private fun FileActionsSection(
    graph: AppGraph,
    spectrum: Spectrum?,
    serialNumber: String?,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backgroundEntity by graph.measurementRepository.backgroundReference()
        .collectAsState(initial = null)

    var notice by remember { mutableStateOf<SpectrumFileNotice?>(null) }
    var savedAtMillis by remember { mutableStateOf<Long?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    fun onWritten(ok: Boolean) {
        if (ok) {
            savedAtMillis = System.currentTimeMillis()
        } else {
            notice = SpectrumFileNotice(
                title = "Экспорт не удался",
                lines = listOf("Файл не записался — попробуйте другую папку."),
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
            scope.launch { notice = importRcXmlFile(graph, context, uri) }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        AppButton(
            text = "Импорт",
            onClick = { importLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = "Экспорт XML",
            onClick = {
                if (spectrum == null) return@AppButton
                val now = System.currentTimeMillis()
                val entity = spectrum.toEntity(timestamp = now, accumulated = false)
                pendingExport = RcXml.write(
                    SpectrumExport.toResultData(
                        entity = entity,
                        background = backgroundEntity,
                        serialNumber = serialNumber,
                        appVersion = appVersionName(context),
                    ),
                )
                exportXmlLauncher.launch(SpectrumExport.fileName(now, "xml"))
            },
            enabled = spectrum != null,
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = "Экспорт N42",
            onClick = {
                if (spectrum == null) return@AppButton
                val now = System.currentTimeMillis()
                val entity = spectrum.toEntity(timestamp = now, accumulated = false)
                pendingExport = N42.write(
                    foreground = SpectrumExport.toN42Measurement(entity, N42.CLASS_FOREGROUND),
                    background = backgroundEntity?.let {
                        SpectrumExport.toN42Measurement(it, N42.CLASS_BACKGROUND)
                    },
                    serialNumber = serialNumber,
                    model = SpectrumExport.modelFromSerial(serialNumber),
                    softwareVersion = appVersionName(context),
                    // Спец §22: метод, нормализация, калибровка и версии
                    // алгоритмов едут вместе с файлом.
                    remarks = SpectrumExport.metadataLines(entity, appVersionName(context)),
                )
                exportN42Launcher.launch(SpectrumExport.fileName(now, "n42"))
            },
            enabled = spectrum != null,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = buildString {
            append("XML — формат приложения RadiaCode · N42 — стандарт программ анализа · ")
            append("импортированный снимок появится в Истории")
            savedAtMillis?.let { append(" · файл сохранён в ").append(timeOfDay(it)) }
        },
        style = type.footnote,
        color = colors.muted,
        modifier = Modifier.padding(horizontal = Dimens.space1),
    )

    notice?.let { current ->
        SpectrumFileNoticeDialog(notice = current, onDismiss = { notice = null })
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
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Продолжение накопления",
                    style = type.label,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Chip(text = "отключить", color = colors.ink2, onClick = onStop)
            }
            Text(
                text = SpectrumExport.title(entity) +
                    " · Δt снимка " + SpectrumFormat.accumulationClock(entity.durationSeconds),
                style = type.valueSmall,
                color = colors.ink2,
            )
            when {
                invalidReason != null -> Text(
                    text = "сумма невозможна: $invalidReason — показано текущее накопление",
                    style = type.footnote,
                    color = colors.warn,
                )
                merging -> Text(
                    text = "показана сумма снимка и текущего накопления (каналы " +
                        "складываются, Δt суммируется); «Сохранить» сохранит сумму",
                    style = type.footnote,
                    color = colors.muted,
                )
                else -> Text(
                    text = "живого накопления пока нет — показан сохранённый снимок",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            Text(
                text = "Прибор копит спектр независимо от приложения. Если снимок " +
                    "сделан из текущего накопления без сброса, импульсы посчитаются " +
                    "дважды — сначала сбросьте спектр.",
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
private fun SpectrumInfoCard(calibrationLine: String, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Как читать спектр".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Chip(text = "✕", color = colors.ink2, onClick = onClose)
            }
            Text(
                text = "По горизонтали энергия в кэВ, по вертикали импульсы в канале за всё " +
                    "накопление. В одну колонку экрана попадает несколько каналов, и " +
                    "берётся их максимум: узкий пик не теряется при отдалении, но линия " +
                    "континуума проходит по верхней огибающей.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            Text(
                text = SpectrumEdge.EXPLANATION,
                style = type.bodySmall,
                color = colors.ink2,
            )
            Text(
                text = "Значимость пика — это его нетто-площадь, делённая на собственную " +
                    "стандартную неопределённость: в неё входит и статистика окна пика, и " +
                    "неопределённость оценки континуума под ним. Структура принимается за " +
                    "пик, только если её ширина согласуется с разрешением детектора.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            Text(
                text = "Кандидат нуклида — это совпадение энергии, а не обнаружение: " +
                    "надёжная идентификация требует накопленной статистики и, как правило, " +
                    "нескольких линий одного нуклида.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            Text(
                text = "Масштаб оси импульсов: линейный передаёт отношение площадей, но " +
                    "прижимает всё, кроме самого высокого, к нулю; логарифмический " +
                    "показывает и одиночные отсчёты, и фотопик, но зрительно уравнивает " +
                    "величины, различающиеся в разы; степенной 1/n — промежуточный (1/2 — " +
                    "привычный корень). Все три — монотонные преобразования одного числа: " +
                    "меняется распределение высоты, а не данные.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            Text(
                text = "Щипок по графику — масштаб, перетаскивание — сдвиг. Сглаживание " +
                    "меняет только отображение: исходные данные не трогаются.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            Text(text = calibrationLine, style = type.footnoteMono, color = colors.muted)
        }
    }
}

@Composable
private fun SpectrumContent(
    graph: AppGraph,
    spectrum: Spectrum,
    connected: Boolean,
    hubState: SpectrumHub.State,
    /** Continuation mode: «Сохранить» persists the merged sum instead. */
    onSaveOverride: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val hub = graph.spectrumHub

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
    var confirmReset by remember { mutableStateOf(false) }
    var windowsOpen by rememberSaveable { mutableStateOf(false) }
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
    val model = (connection as? ConnectionState.Connected)?.info?.model ?: DeviceModel.UNKNOWN
    val resolution662 = model.peakResolution662
    val full = remember(calibration, spectrum.counts.size) {
        SpectrumDisplay.fullWindow(calibration, spectrum.counts.size)
    }
    val visible = window?.let { SpectrumDisplay.clampInto(it, full) } ?: full

    // --- controls: mode + scale ---
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // «Накопл. | −фон» не говорило, ЧТО вычитается: теперь режим назван
        // целиком, спектр и спектр минус записанный фон.
        Segmented(
            options = listOf("Спектр", "− фон"),
            selectedIndex = if (subtractOn) 1 else 0,
            onSelect = { minusBackground = it == 1 },
            enabled = { it == 0 || background != null },
            modifier = Modifier.weight(1.7f),
        )
        Segmented(
            options = listOf("Лин", "Степень", "Лог"),
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
        Chip(text = "i", color = colors.ink2, onClick = { infoOpen = true })
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
                text = "степень 1/$scaleRoot",
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
    if (infoOpen) {
        SpectrumInfoCard(
            calibrationLine = SpectrumFormat.calibrationLine(
                spectrum.a0,
                spectrum.a1,
                spectrum.a2,
                spectrum.counts.size,
            ),
            onClose = { infoOpen = false },
        )
    }

    // --- display pipeline (raw counts never change): optional «минус фон»
    // with time-ratio normalization, then optional display-only smoothing ---
    val range = remember(visible, calibration, spectrum.counts.size) {
        SpectrumDisplay.channelRange(visible, calibration, spectrum.counts.size)
    }
    val baseSeries = remember(spectrum, background, subtractOn) {
        if (subtractOn && background != null) {
            SpectrumDisplay.subtractBackground(
                current = spectrum.counts,
                currentSeconds = spectrum.durationSeconds,
                background = background.counts,
                backgroundSeconds = background.durationSeconds,
            )
        } else {
            spectrum.counts.map { it.toFloat() }
        }
    }
    val series = remember(baseSeries, smoothing) {
        if (smoothing) SpectrumDisplay.movingAverage(baseSeries) else baseSeries
    }
    val columns = remember(series, range) {
        SpectrumDisplay.aggregateMax(series, range, COLUMN_COUNT)
    }
    // Overlay: the reference spectrum scaled to the current live time, shown
    // only in the plain mode (subtracting it and overlaying it is double use).
    val overlayColumns = remember(background, subtractOn, spectrum.durationSeconds, range) {
        if (background == null || subtractOn) {
            null
        } else {
            SpectrumDisplay.aggregateMax(
                SpectrumDisplay.scaleToDuration(
                    background.counts,
                    backgroundSeconds = background.durationSeconds,
                    currentSeconds = spectrum.durationSeconds,
                ),
                range,
                COLUMN_COUNT,
            )
        }
    }
    val dataMax = maxOf(columns.maxOrNull() ?: 0f, overlayColumns?.maxOrNull() ?: 0f)
    val yTop = if (scale is SpectrumScale.Log) SpectrumDisplay.logTop(dataMax) else maxOf(dataMax * 1.15f, 10f)

    // --- cautious isotope analysis (always on raw counts, never display data) ---
    val analysisReady = model.isSpectrometer &&
        spectrum.durationSeconds >= IsotopeMatcher.MIN_ANALYSIS_SECONDS
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
    val hints = remember(peaks) { IsotopeMatcher.match(peaks, resolution662) }
    var highlightedIsotope by remember { mutableStateOf<String?>(null) }
    // Tapping a candidate row opens its offline reference card (спец §12).
    var infoIsotope by remember { mutableStateOf<String?>(null) }
    infoIsotope?.let { symbol ->
        NuclideInfoLibrary.of(symbol)?.let { nuclide ->
            NuclideInfoDialog(nuclide = nuclide, onDismiss = { infoIsotope = null })
        }
    }
    val highlightedHint = hints.firstOrNull { it.isotope == highlightedIsotope }
        ?: hints.firstOrNull { !it.natural }
        ?: hints.firstOrNull()
    val peakMarks = remember(peaks, hints, highlightedHint, range) {
        peaks.mapNotNull { peak ->
            val column = SpectrumDisplay.columnForChannel(peak.channel, range, COLUMN_COUNT)
                ?: return@mapNotNull null
            SpectrumPeakMark(
                columnIndex = column,
                label = "${peak.energyKeV.roundToInt()}",
                highlighted = highlightedHint != null && highlightedHint.peak == peak,
            )
        }
    }

    // --- chart card ---
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
                ),
                onGesture = { scale, pan, focus ->
                    var next = SpectrumDisplay.pinch(visible, full, scale, focus)
                    next = SpectrumDisplay.pan(next, full, pan)
                    window = next
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Dimens.space1),
            ) {
                // Ось называет ОБЕ величины: по горизонтали кэВ, по вертикали
                // импульсы В КАНАЛЕ (не имп/кэВ — ширина канала по шкале
                // немного меняется, и делить на неё значило бы показывать не
                // то, что показывает прибор).
                Text(
                    text = "кэВ → · имп в канале ↑".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                LegendItem(
                    color = colors.data,
                    label = if (subtractOn) "−фон" else "накопл.",
                )
                if (overlayColumns != null && backgroundEntity != null) {
                    Spacer(Modifier.size(10.dp))
                    LegendItem(
                        color = colors.muted,
                        label = "фон ${HistoryFormat.day(backgroundEntity!!.timestamp)}",
                    )
                }
            }
            // Крайний канал не нарисован — но и не выброшен молча: прибор в
            // нём что-то регистрировал, и это отдельный факт, а не пик.
            val edgeCounts = SpectrumEdge.edgeCounts(spectrum.counts)
            if (edgeCounts > 0) {
                Text(
                    text = "у верхней границы шкалы: " +
                        HistoryFormat.count(
                            edgeCounts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        ) + " имп.",
                    style = type.footnote,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = Dimens.space1),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier.padding(horizontal = Dimens.space1),
            ) {
                Text(
                    text = "диапазон ${SpectrumFormat.windowLabel(visible)}",
                    style = type.footnote,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                Chip(
                    text = "сглаж.",
                    color = if (smoothing) colors.dataText else colors.ink2,
                    onClick = { smoothing = !smoothing },
                )
                AppButton(
                    text = "−",
                    onClick = { window = SpectrumDisplay.zoomOut(visible, full) },
                    enabled = visible.widthKeV < full.widthKeV,
                )
                AppButton(
                    text = "+",
                    onClick = { window = SpectrumDisplay.zoomIn(visible, full) },
                    enabled = visible.widthKeV > SpectrumDisplay.MIN_WINDOW_KEV,
                )
            }
            Column(modifier = Modifier.padding(horizontal = Dimens.space1)) {
                if (smoothing) {
                    Text(
                        text = "сглаживание — только отображение, исходные данные не меняются",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                if (!connected) {
                    Text(
                        text = "нет соединения — показан последний прочитанный спектр",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
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
                    text = "${model.displayName}: детектор без энергетического разрешения " +
                        "(${model.crystal ?: "неизвестный сцинтиллятор"}) — поиск пиков и " +
                        "совпадения с линиями нуклидов для него не считаются.",
                    style = type.bodySmall,
                    color = colors.muted,
                )
                !analysisReady -> Text(
                    text = "мало данных для анализа пиков — накопите хотя бы минуту",
                    style = type.bodySmall,
                    color = colors.muted,
                )
                peaks.isEmpty() -> Text(
                    text = "выраженных пиков над континуумом не найдено",
                    style = type.bodySmall,
                    color = colors.muted,
                )
                else -> PeakTable(
                    peaks = peaks,
                    hints = hints,
                    highlightedHint = highlightedHint,
                    onSelect = { isotope ->
                        highlightedIsotope = isotope
                        infoIsotope = isotope
                    },
                )
            }
            highlightedHint?.let { hint ->
                SpectrumFormat.hintAlternatives(hint)?.let { alternatives ->
                    Text(text = alternatives, style = type.footnote, color = colors.muted)
                }
            }
            Text(
                text = "возможное совпадение ≠ обнаружение · нужно подтверждение: " +
                    "копите дольше · нажмите строку — справка о нуклиде",
                style = type.footnote,
                color = colors.muted,
            )
        }
    }

    // --- energy windows (спец §7): состав спектра, не мера опасности ---
    // Свёрнуты по умолчанию: границы окон — ПАРАМЕТР АНАЛИЗА, выбранный нами,
    // и стоять сразу под настоящим спектром с настоящими пиками они не должны
    // — это выглядит фундаментальнее, чем есть.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = Dimens.space1),
    ) {
        Chip(
            text = if (windowsOpen) "энергетические диапазоны ▴" else "энергетические диапазоны ▾",
            color = colors.ink2,
            onClick = { windowsOpen = !windowsOpen },
        )
    }
    AnimatedVisibility(
        visible = windowsOpen,
        enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
        exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
    ) {
        EnergyWindowsCard(
            graph = graph,
            counts = spectrum.counts,
            durationSeconds = spectrum.durationSeconds,
            calibration = calibration,
        )
    }

    // --- actions ---
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        AppButton(
            text = "Записать фон",
            onClick = { hub.request(SpectrumHub.Command.RECORD_BACKGROUND) },
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = "Сохранить",
            onClick = onSaveOverride ?: { hub.request(SpectrumHub.Command.SAVE_SNAPSHOT) },
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = "Сброс",
            onClick = { confirmReset = true },
            enabled = connected,
            modifier = Modifier.weight(1f),
        )
    }

    val bgEntity = backgroundEntity
    Text(
        text = if (background != null && bgEntity != null) {
            "фон записан " +
                HistoryFormat.dayTime(bgEntity.timestamp, System.currentTimeMillis()) +
                " · накопление " + HistoryFormat.duration(background.durationSeconds) +
                if (subtractOn) " · показана разница, не меньше нуля" else ""
        } else {
            "фон не записан — запишите спектр обычной обстановки, появятся " +
                "наложение и «минус фон»"
        },
        style = type.footnote,
        color = colors.muted,
        modifier = Modifier.padding(horizontal = Dimens.space1),
    )
    hubState.lastSavedAtMillis?.let { savedAt ->
        Text(
            text = "снимок сохранён в ${timeOfDay(savedAt)} — он виден в Истории",
            style = type.footnote,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = Dimens.space1),
        )
    }

    if (confirmReset) {
        Dialog(onDismissRequest = { confirmReset = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text("Сбросить спектр?", style = type.title, color = colors.ink)
                    Text(
                        text = "Накопление начнётся заново — на приборе спектр " +
                            "тоже очистится. Сохранённые снимки останутся в Истории.",
                        style = type.body,
                        color = colors.ink2,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        AppButton(
                            text = "Сброс",
                            onClick = {
                                confirmReset = false
                                hub.request(SpectrumHub.Command.RESET)
                            },
                        )
                        AppButton(text = "Отмена", onClick = { confirmReset = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(width = 9.dp, height = 3.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Text(text = label, style = type.axis, color = LocalAppColors.current.ink2)
    }
}

@Composable
private fun PeakTable(
    peaks: List<Peak>,
    hints: List<IsotopeHint>,
    highlightedHint: IsotopeHint?,
    onSelect: (String?) -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
            TableHeader("E, кэВ", 0.9f)
            TableHeader("нетто", 0.9f)
            TableHeader("значимость", 0.9f)
            // Спец §2: колонка кандидата — интерпретация, а не измерение;
            // соседние колонки этого уровня не наследуют.
            Row(Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "кандидат", style = type.labelSmall, color = colors.ink2)
                EvidenceTag(Evidence.INTERPRETATION, Modifier.padding(start = 5.dp))
            }
        }
        AppDivider()
        peaks.forEachIndexed { index, peak ->
            val hint = hints.firstOrNull { it.peak == peak }
            val isHighlighted = hint != null && hint == highlightedHint
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hint != null) { onSelect(hint?.isotope) }
                    .padding(vertical = 6.dp),
            ) {
                TableCell(SpectrumFormat.energyCell(peak.energyKeV), 0.9f, colors.ink)
                TableCell(SpectrumFormat.netCell(peak.netCounts), 0.9f, colors.ink)
                TableCell(SpectrumFormat.significanceCell(peak.significance), 0.9f, colors.ink)
                val candidate = hint?.let { SpectrumFormat.candidateCell(it) } ?: "—"
                Text(
                    text = (if (isHighlighted) "▸ " else "") + candidate,
                    style = if (hint != null && !hint.natural) {
                        type.valueSmall.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        type.valueSmall
                    },
                    color = when {
                        hint == null -> colors.muted
                        hint.natural -> colors.ink2
                        else -> colors.warn
                    },
                    maxLines = 1,
                    modifier = Modifier.weight(1.6f),
                )
            }
            if (index < peaks.size - 1) AppDivider()
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
