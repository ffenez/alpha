package app.radiacode.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.radiacode.ui.theme.Motion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.radiacode.analysis.DoseProjection
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.SpectrumMerge
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.SessionSummary
import app.radiacode.service.SessionGate
import app.radiacode.ui.logic.SessionGroups
import app.radiacode.ui.logic.SessionGroup
import app.radiacode.data.toSpectrum
import app.radiacode.protocol.Spectrum
import app.radiacode.data.db.EventEntity
import app.radiacode.data.db.ProfileEntity
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.data.export.N42
import app.radiacode.data.export.ProcessingMetadata
import app.radiacode.data.export.RcXml
import app.radiacode.data.export.SpectrumExport
import app.radiacode.device.DoseUnits
import app.radiacode.ui.components.DisclosureArrow
import app.radiacode.ui.components.NavArrow
import app.radiacode.ui.components.Hint
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.BarChart
import app.radiacode.ui.components.BarChartSpec
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.CheckMark
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.RadioMark
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.DailyDose
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.DeletionPlan
import app.radiacode.ui.logic.HistoryDeletion
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.HistorySelection
import app.radiacode.ui.logic.ProfileTree
import app.radiacode.ui.logic.SpectrumFormat
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppMetrics
import app.radiacode.ui.theme.LocalAppTypography
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20
private const val REFRESH_MILLIS = 30_000L
/**
 * Глубина суточной истории дозы: самый длинный период графика.
 * Час — одна строка предагрегата, поэтому девяносто дней это ~2160 строк:
 * дешевле одного экрана списка.
 */
private const val DOSE_DAYS = 90

/** One chronological row of История. */
private sealed interface HistoryItem {
    val timestamp: Long

    data class Session(val group: SessionGroup) : HistoryItem {
        override val timestamp: Long get() = group.startedAt
    }

    data class Deviation(val event: EventEntity) : HistoryItem {
        override val timestamp: Long get() = event.timestamp
    }
}

@Immutable
private data class HistoryModel(
    val items: List<HistoryItem>,
    val totalSessions: Long,
)

/**
 * История (SPEC «History»): dense measurement-session rows newest-first with
 * their summaries, interleaved with deviation events and their «обычно здесь
 * X» context. Windowed pages keep months of data smooth; a session opens its
 * detail.
 *
 * Накопленной дозы здесь больше нет: её спрашивают с Главной и по конкретному
 * поводу, а верх Истории она занимала всегда — теперь у неё свой экран
 * ([DoseScreen]), вход на него с плитки «Набралось сегодня».
 */
@Composable
fun HistoryScreen(
    graph: AppGraph,
    onOpenSession: (Long) -> Unit,
    onContinueSpectrum: (Long) -> Unit = {},
    /** Снимок спектра открывается тем же экраном Спектра, что и живой. */
    onOpenSpectrum: (Long) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    val scope = rememberCoroutineScope()
    var pages by remember { mutableIntStateOf(1) }
    var model by remember { mutableStateOf<HistoryModel?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(pages, reload) {
        while (true) {
            model = loadHistory(graph, pages * PAGE_SIZE)
            delay(REFRESH_MILLIS)
        }
    }

    // Уборка журнала: один режим выбора на сессии и спектры — они лежат в
    // одном списке, и «убрать лишнее» это одна задача, а не две.
    var selection by remember { mutableStateOf(HistorySelection()) }
    // «Выбрать всё» обязано знать, что такое «всё»: id снимков живут в
    // карточке спектров, поэтому список поднят сюда и передаётся вниз.
    val savedSpectra by graph.measurementRepository.savedSpectra(SPECTRA_LIMIT)
        .collectAsState(initial = emptyList())
    var confirming by remember { mutableStateOf<DeletionPlan?>(null) }

    confirming?.let { plan ->
        DeleteConfirmDialog(
            plan = plan,
            onConfirm = {
                scope.launch {
                    graph.sessionRepository.delete(selection.sessions, selection.spectra)
                    selection = HistorySelection()
                    confirming = null
                    reload += 1
                }
            },
            onDismiss = { confirming = null },
        )
    }

    // Правка профиля сессии переехала в саму сессию (spec §20): чип «профиль…»
    // повторялся в КАЖДОЙ строке журнала и обрезался многоточием, хотя нужен
    // он редко и относится к одной конкретной записи.
    // Comparator flow is self-contained in История: picking two snapshots
    // swaps the screen for the comparator; back returns to the list.
    var comparePair by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    comparePair?.let { (firstId, secondId) ->
        SpectrumCompareScreen(
            graph = graph,
            firstId = firstId,
            secondId = secondId,
            onBack = { comparePair = null },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        // Название экрана в шапке не повторяется: оно и так подписано во
        // вкладке снизу.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (selection.active) {
                Chip(
                    text = strings.cancel,
                    color = colors.ink2,
                    onClick = { selection = selection.cancel() },
                )
            } else {
                // The counter is the way in: tapping «12 сессий» is asking to
                // do something with them.
                model?.let {
                    Chip(
                        text = strings.sessionsCount(it.totalSessions),
                        color = colors.ink2,
                        onClick = { selection = selection.start() },
                    )
                }
            }
        }
        if (selection.active) {
            // Идущая сессия не удаляется, поэтому и в «всё» не входит:
            // «выбрано 13» при двенадцати удаляемых было бы неправдой.
            val selectableSessions = model?.items.orEmpty()
                .filterIsInstance<HistoryItem.Session>()
                .filter { !it.group.running }
                .flatMap { it.group.ids }
            val selectableSpectra = savedSpectra.map { it.id }
            val allSelected = selection.isAllSelected(selectableSessions, selectableSpectra)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Chip(
                    text = if (allSelected) strings.clearAll else strings.selectAll,
                    color = if (allSelected) colors.dataText else colors.ink2,
                    selected = allSelected,
                    onClick = {
                        selection = selection.toggleAll(selectableSessions, selectableSpectra)
                    },
                )
                Text(
                    text = if (selection.isEmpty) {
                        HistoryDeletion.emptyHint(h)
                    } else {
                        strings.selectedCount(selection.count)
                    },
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }

        val m = model
        if (m == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = strings.readingJournal, style = type.bodySmall, color = colors.muted)
            }
        } else {
            SavedSpectraCard(
                graph = graph,
                spectra = savedSpectra,
                onCompare = { first, second -> comparePair = first to second },
                onContinue = onContinueSpectrum,
                onOpen = onOpenSpectrum,
                selectionActive = selection.active,
                selected = selection.spectra,
                onToggle = { id -> selection = selection.toggleSpectrum(id) },
            )

            if (m.items.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        Text(
                            text = strings.noSessionsYet,
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                        Hint(
                            text = strings.sessionExplained,
                            style = type.bodySmall,
                            color = colors.muted,
                        )
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        m.items.forEachIndexed { index, item ->
                            if (index > 0) AppDivider()
                            when (item) {
                                is HistoryItem.Session -> SessionRow(
                                    group = item.group,
                                    unit = unit,
                                    selectionActive = selection.active,
                                    // Склейка выбирается целиком: строка на
                                    // экране одна, и «выбрано наполовину» о
                                    // ней сказать нечего.
                                    selected = item.group.ids.all { it in selection.sessions },
                                    onClick = {
                                        if (selection.active) {
                                            // A session still being written to
                                            // cannot be deleted: the data is
                                            // arriving as we speak.
                                            if (!item.group.running) {
                                                selection = item.group.ids.fold(selection) {
                                                    acc, id ->
                                                    acc.toggleSession(id)
                                                }
                                            }
                                        } else {
                                            onOpenSession(item.group.ids.last())
                                        }
                                    },
                                )
                                is HistoryItem.Deviation -> DeviationRow(item.event, unit)
                            }
                        }
                    }
                }
            }

            if (m.totalSessions > m.items.count { it is HistoryItem.Session }) {
                AppButton(
                    text = strings.showMore,
                    onClick = { pages += 1 },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            AnimatedVisibility(
                visible = selection.active,
                enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
                exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        AppButton(
                            text = HistoryDeletion.actionLabel(selection, h),
                            onClick = {
                                scope.launch {
                                    confirming = graph.sessionRepository.deletionPlan(
                                        sessionIds = selection.sessions,
                                        spectrumIds = selection.spectra,
                                    )
                                }
                            },
                            primary = !selection.isEmpty,
                            enabled = !selection.isEmpty,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(
                            text = strings.cancel,
                            onClick = { selection = selection.cancel() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}





@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionRow(
    group: SessionGroup,
    unit: DoseUnitSetting,
    onClick: () -> Unit,
    selectionActive: Boolean = false,
    selected: Boolean = false,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    val endedAt = group.endedAt
    val durationSeconds = ((endedAt ?: now) - group.startedAt) / 1000L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionActive) {
                if (group.running) {
                    // Nothing to tick: a running session is still being written.
                    Spacer(Modifier.size(18.dp))
                } else {
                    CheckMark(selected = selected)
                }
                Spacer(Modifier.size(Dimens.space2))
            }
            Text(
                text = group.profileName ?: strings.noProfile,
                style = type.label,
                color = if (selectionActive && group.running) colors.muted else colors.ink,
            )
            if (group.running) {
                Text(
                    text = if (selectionActive) strings.runningCannotDelete else strings.running,
                    style = type.label,
                    color = if (selectionActive) colors.muted else colors.ok,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = HistoryFormat.dayTime(group.startedAt, now, s = h) +
                    " · " + HistoryFormat.duration(durationSeconds, h),
                style = type.footnote,
                color = colors.ink2,
            )
            // Строка открывается — и это видно, а не угадывается.
            if (!selectionActive) {
                NavArrow(
                                        modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        // Две величины вместо шести. «ср 0,15 макс 0,18 доза 0,01 мкЗв n 254
        // спектр» читалось как технический дамп: максимум, число измерений и
        // пометки о треке и спектре отвечают на вопросы, которые задают уже
        // ВНУТРИ записи, а строка списка отвечает на один — «сколько тут было
        // и сколько накопилось».
        val stats = group.stats
        val avgMicroSvH = stats.avgDoseRateMicroSvH
        if (stats.sampleCount > 0 && avgMicroSvH != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DataItem(strings.avg, DoseFormat.rate(avgMicroSvH, unit))
                DataItem(
                    strings.dose,
                    DoseFormat.doseWithUnit(group.doseMicroSv, unit, s = strings),
                )
            }
        } else {
            Text(
                text = strings.noSamplesInSession,
                style = type.valueSmall,
                color = colors.muted,
            )
        }

        // Склейка не бывает незаметной: если запись шла с перерывами, это
        // сказано — иначе числа не сойдутся с длительностью.
        if (group.pieces > 1) {
            Text(
                text = h.mergedPieces(group.pieces, HistoryFormat.duration(group.gapSeconds, h)),
                style = type.footnote,
                color = colors.muted,
            )
        }

        // Спец §20: журнал обязан говорить, учила ли запись обычный фон, —
        // но это ПОЯСНЕНИЕ, и оно уходит вместе с остальными.
        Hint(
            text = HistoryFormat.admissionLine(group.admission, h),
            color = if (group.admission.included) colors.muted else colors.ink2,
        )
    }
}


@Composable
private fun DataItem(label: String, value: String, valueColor: Color? = null) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = type.valueSmall, color = colors.ink2)
        Text(
            text = value,
            style = type.valueSmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = valueColor ?: colors.ink,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviationRow(event: EventEntity, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    val kind = when (event.source) {
        EventEntity.SOURCE_DEVIATION -> strings.deviation
        else -> strings.excursionPoint
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "⚠ $kind", style = type.label, color = colors.warn)
            Spacer(Modifier.weight(1f))
            Text(
                text = HistoryFormat.dayTime(event.timestamp, now, s = h),
                style = type.footnote,
                color = colors.ink2,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            event.doseRate?.let {
                DataItem(
                    label = DoseFormat.rateUnitLabel(unit, s = strings),
                    value = DoseFormat.rate(DoseUnits.rawToMicroSievertPerHour(it), unit),
                    valueColor = colors.warn,
                )
            }
            // param1 of a deviation stores the baseline typical high, nSv/h.
            if (event.source == EventEntity.SOURCE_DEVIATION && event.param1 > 0) {
                DataItem(strings.usually, DoseFormat.rate(event.param1 / 1000f, unit))
            }
        }
    }
}

// --- saved spectra: export + comparator entry ---

private const val SPECTRA_LIMIT = 30

/**
 * Сохранённые и импортированные спектры: экспорт в файл, вход в сравнение
 * («Сравнить» → выбрать два снимка → экран сравнения), объединение 2+
 * снимков в один (каналы складываются, Δt суммируется; расходящиеся
 * калибровки честно отклоняются) и «продолжить накопление» на Спектре.
 * Автоснимки раз в минуту сюда не попадают — только явные сохранения,
 * фоны и импорт.
 */
@Composable
private fun SavedSpectraCard(
    graph: AppGraph,
    spectra: List<SpectrumSnapshotEntity>,
    onCompare: (Long, Long) -> Unit,
    onContinue: (Long) -> Unit,
    /** Открыть снимок полноценным экраном Спектра. */
    onOpen: (Long) -> Unit,
    selectionActive: Boolean = false,
    selected: Set<Long> = emptySet(),
    onToggle: (Long) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (spectra.isEmpty()) return

    var compareMode by remember { mutableStateOf(false) }
    var firstPickId by remember { mutableStateOf<Long?>(null) }
    var mergeMode by remember { mutableStateOf(false) }
    var mergeIds by remember { mutableStateOf(setOf<Long>()) }
    var actionsFor by remember { mutableStateOf<SpectrumSnapshotEntity?>(null) }
    var notice by remember { mutableStateOf<SpectrumFileNotice?>(null) }
    var exportedNote by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    fun handleExportResult(uri: android.net.Uri?) {
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            scope.launch {
                if (writeTextToUri(context, uri, content)) {
                    exportedNote = strings.fileSaved
                } else {
                    notice = SpectrumFileNotice(
                        title = strings.exportFailedTitle,
                        lines = listOf(strings.exportFailedBody),
                        isError = true,
                    )
                }
            }
        }
    }

    val exportXmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri -> handleExportResult(uri) }
    val exportN42Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> handleExportResult(uri) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Text(
                    text = strings.spectraTitle.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                if (spectra.size >= 2 && !mergeMode && !selectionActive) {
                    Chip(
                        text = if (compareMode) strings.cancel.lowercase() else strings.compare,
                        color = if (compareMode) colors.ink2 else colors.dataText,
                        onClick = {
                            compareMode = !compareMode
                            firstPickId = null
                        },
                    )
                }
                if (spectra.size >= 2 && !compareMode && !selectionActive) {
                    Chip(
                        text = if (mergeMode) strings.cancel.lowercase() else strings.merge,
                        color = if (mergeMode) colors.ink2 else colors.dataText,
                        onClick = {
                            mergeMode = !mergeMode
                            mergeIds = emptySet()
                        },
                    )
                }
            }
            Text(
                text = when {
                    selectionActive -> strings.markForDeletion
                    compareMode -> strings.pickTwoToCompare
                    mergeMode -> strings.pickTwoOrMoreToMerge
                    else -> strings.snapshotOpensActions
                },
                style = type.footnote,
                color = colors.muted,
                modifier = Modifier.padding(top = 3.dp, bottom = 5.dp),
            )
            spectra.forEachIndexed { index, entity ->
                if (index > 0) AppDivider()
                SavedSpectrumRow(
                    entity = entity,
                    marker = when {
                        selectionActive -> null
                        compareMode && firstPickId == entity.id -> "A"
                        mergeMode && entity.id in mergeIds -> "✓"
                        else -> null
                    },
                    check = if (selectionActive) entity.id in selected else null,
                    onClick = {
                        when {
                            selectionActive -> onToggle(entity.id)
                            compareMode -> {
                                val first = firstPickId
                                when {
                                    first == null -> firstPickId = entity.id
                                    first == entity.id -> firstPickId = null
                                    else -> {
                                        compareMode = false
                                        firstPickId = null
                                        onCompare(first, entity.id)
                                    }
                                }
                            }
                            mergeMode -> mergeIds = if (entity.id in mergeIds) {
                                mergeIds - entity.id
                            } else {
                                mergeIds + entity.id
                            }
                            else -> actionsFor = entity
                        }
                    },
                )
            }
            if (mergeMode && !selectionActive) {
                AppButton(
                    text = strings.mergeAction(mergeIds.size),
                    enabled = mergeIds.size >= 2,
                    onClick = {
                        val chosen = spectra.filter { it.id in mergeIds }
                        scope.launch {
                            when (val saved = mergeSnapshots(graph, chosen)) {
                                is MergeResult.Saved -> {
                                    mergeMode = false
                                    mergeIds = emptySet()
                                    exportedNote = strings.mergedSaved(saved.label.orEmpty())
                                }
                                is MergeResult.Refused -> notice = SpectrumFileNotice(
                                    title = strings.mergeImpossible,
                                    lines = listOf(saved.reason),
                                    isError = true,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.space2),
                )
            }
            exportedNote?.let {
                Text(text = it, style = type.footnote, color = colors.muted)
            }
        }
    }

    actionsFor?.let { entity ->
        Dialog(onDismissRequest = { actionsFor = null }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(
                        text = SpectrumExport.title(entity),
                        style = type.title,
                        color = colors.ink,
                    )
                    Text(
                        text = HistoryFormat.dayTime(entity.timestamp, System.currentTimeMillis(), s = h) +
                            " · Δt " + SpectrumFormat.accumulationClock(entity.durationSeconds),
                        style = type.footnote,
                        color = colors.ink2,
                    )
                    // Первое действие — посмотреть сам спектр: кривая, пики,
                    // нуклиды. Остальное — то, что делают с уже увиденным.
                    AppButton(
                        text = strings.openSnapshot,
                        primary = true,
                        onClick = {
                            actionsFor = null
                            onOpen(entity.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = strings.exportXml,
                        onClick = {
                            pendingExport = RcXml.write(
                                SpectrumExport.toResultData(
                                    entity = entity,
                                    background = null,
                                    serialNumber = null,
                                    appVersion = appVersionName(context),
                                ),
                            )
                            exportXmlLauncher.launch(
                                SpectrumExport.fileName(entity.timestamp, "xml"),
                            )
                            actionsFor = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = strings.exportN42,
                        onClick = {
                            pendingExport = N42.write(
                                foreground = SpectrumExport.toN42Measurement(
                                    entity,
                                    N42.CLASS_FOREGROUND,
                                ),
                                softwareVersion = appVersionName(context),
                                remarks = SpectrumExport.metadataLines(
                                    entity,
                                    appVersionName(context),
                                ),
                            )
                            exportN42Launcher.launch(
                                SpectrumExport.fileName(entity.timestamp, "n42"),
                            )
                            actionsFor = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = strings.compareWithAnother,
                        onClick = {
                            compareMode = true
                            firstPickId = entity.id
                            actionsFor = null
                        },
                        enabled = spectra.size >= 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = strings.continueAccumulation,
                        onClick = {
                            actionsFor = null
                            onContinue(entity.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint(
                        text = strings.continueAccumulationNote,
                    )
                    AppButton(
                        text = strings.close,
                        onClick = { actionsFor = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    notice?.let { current ->
        SpectrumFileNoticeDialog(notice = current, onDismiss = { notice = null })
    }
}

@Composable
private fun SavedSpectrumRow(
    entity: SpectrumSnapshotEntity,
    marker: String?,
    onClick: () -> Unit,
    /** Non-null while the list is in selection mode: the tick of this row. */
    check: Boolean? = null,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (check != null) {
                CheckMark(selected = check, modifier = Modifier.padding(end = Dimens.space2))
            }
            if (marker != null) {
                Text(
                    text = "$marker ▸",
                    style = type.label,
                    color = colors.dataText,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text(
                text = SpectrumExport.title(entity),
                style = type.label,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = HistoryFormat.dayTime(entity.timestamp, now, s = h),
                style = type.footnote,
                color = colors.ink2,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space3)) {
            DataItem("Δt", SpectrumFormat.accumulationClock(entity.durationSeconds))
            val badges = listOfNotNull(
                strings.importedTag.takeIf { entity.origin == SpectrumSnapshotEntity.ORIGIN_IMPORT },
                strings.backgroundTag.takeIf { entity.isBackgroundReference },
            )
            if (badges.isNotEmpty()) {
                Text(
                    text = badges.joinToString(" · "),
                    style = type.valueSmall,
                    color = colors.ink2,
                )
            }
        }
    }
}

/** Outcome of the История merge action. */
private sealed interface MergeResult {
    data class Saved(val label: String) : MergeResult
    data class Refused(val reason: String) : MergeResult
}

/**
 * Channel-wise merge of the chosen snapshots ([SpectrumMerge]) saved as a new
 * user snapshot labeled «merge». Refusals (calibration/grid mismatch) come
 * back verbatim — the math layer words them honestly.
 */
private suspend fun mergeSnapshots(
    graph: AppGraph,
    chosen: List<SpectrumSnapshotEntity>,
): MergeResult {
    val inputs = chosen.map { entity ->
        val s = entity.toSpectrum()
        SpectrumMerge.Input(
            counts = s.counts,
            durationSeconds = s.durationSeconds,
            calibration = EnergyCalibration(s.a0, s.a1, s.a2),
            name = SpectrumExport.title(entity),
        )
    }
    return when (val outcome = SpectrumMerge.merge(inputs)) {
        is SpectrumMerge.Outcome.Invalid -> MergeResult.Refused(outcome.reason)
        is SpectrumMerge.Outcome.Ok -> {
            // Метка ХРАНИТСЯ в базе, поэтому она не зависит от языка
            // интерфейса: иначе снимок, объединённый по-русски, так и остался
            // бы русским после переключения языка.
            val label = "merge · ${chosen.size}"
            graph.measurementRepository.saveSpectrum(
                Spectrum(
                    durationSeconds = outcome.durationSeconds,
                    a0 = outcome.calibration.a0,
                    a1 = outcome.calibration.a1,
                    a2 = outcome.calibration.a2,
                    counts = outcome.counts,
                ),
                accumulated = false,
                origin = SpectrumSnapshotEntity.ORIGIN_USER,
                label = label,
                // Спец §22: сумма — производный результат, метод и версии
                // алгоритмов едут вместе со снимком и в экспорт.
                analysisMeta = ProcessingMetadata.stamp(
                    method = "channel_sum (merge)",
                    algorithms = listOf("spectrum_merge"),
                    extra = mapOf(
                        "sourceIds" to chosen.joinToString(",") { it.id.toString() },
                        "durationSeconds" to outcome.durationSeconds.toString(),
                    ),
                ),
            )
            MergeResult.Saved(label)
        }
    }
}


private suspend fun loadHistory(graph: AppGraph, sessionLimit: Int): HistoryModel {
    val now = System.currentTimeMillis()
    val repo = graph.sessionRepository

    val sessions = repo.page(offset = 0, limit = sessionLimit)
    val totalSessions = repo.count()

    // Deviations across the visible span (down to the oldest loaded session).
    val eventsFrom = sessions.lastOrNull()?.startedAt ?: (now - 24L * 3600_000)
    val events = repo.deviationEvents(from = eventsFrom, to = now)

    // Подряд идущие записи одного места показываются одной строкой: рвали их
    // разрывы связи и перезапуски службы, а не решение человека. Журнал в базе
    // при этом не переписывается — склейка живёт только в показе.
    val groups = SessionGroups.merge(
        sessions = sessions,
        graceMillis = SessionGate.DEFAULT_GRACE_MILLIS,
        nowMillis = now,
    )
    val items = (
        groups.map { HistoryItem.Session(it) } + events.map { HistoryItem.Deviation(it) }
        ).sortedByDescending { it.timestamp }

    return HistoryModel(
        items = items,
        totalSessions = totalSessions,
    )
}


/**
 * Deleting measurements is the one place in the app where data really goes
 * away, so the dialog reads like an account, not like a warning: what exactly
 * disappears, what stays, and the fact that it cannot be undone.
 */
@Composable
private fun DeleteConfirmDialog(
    plan: DeletionPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = HistoryDeletion.title(plan, h), style = type.title, color = colors.ink)
                Text(
                    text = HistoryDeletion.body(plan, h),
                    style = type.bodySmall,
                    color = colors.ink2,
                )
                Text(
                    text = HistoryDeletion.keepsWording(plan, h),
                    style = type.footnote,
                    color = colors.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = strings.delete,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = strings.cancel,
                        onClick = onDismiss,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
