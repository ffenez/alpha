package app.radiacode.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.radiacode.ui.theme.Motion
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.radiacode.AppGraph
import app.radiacode.analysis.DoseProjection
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.SpectrumMerge
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.SessionSummary
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
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.BarChart
import app.radiacode.ui.components.BarChartSpec
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.CheckMark
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.EvidenceTag
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
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20
private const val REFRESH_MILLIS = 30_000L
private const val DOSE_DAYS = 30

/** One chronological row of История. */
private sealed interface HistoryItem {
    val timestamp: Long

    data class Session(val summary: SessionSummary) : HistoryItem {
        override val timestamp: Long get() = summary.startedAt
    }

    data class Deviation(val event: EventEntity) : HistoryItem {
        override val timestamp: Long get() = event.timestamp
    }
}

@Immutable
private data class HistoryModel(
    val doseTodayMicroSv: Double,
    val dose7dMicroSv: Double,
    val dose30dMicroSv: Double,
    /** µSv per local day, oldest first, [DOSE_DAYS] entries. */
    val dailyDoseMicroSv: List<Float>,
    /** Seconds of *actual measurement* behind the 7- and 30-day integrals. */
    val measured7dSeconds: Long,
    val measured30dSeconds: Long,
    val fromMillis: Long,
    val toMillis: Long,
    val items: List<HistoryItem>,
    val totalSessions: Long,
)

/**
 * История (SPEC «History»): accumulated dose with the 30-day bar mini-chart,
 * then dense measurement-session rows newest-first with full summaries,
 * interleaved with deviation events and their «обычно здесь X» context.
 * Windowed pages keep months of data smooth; a session opens its detail.
 */
@Composable
fun HistoryScreen(
    graph: AppGraph,
    onOpenSession: (Long) -> Unit,
    onContinueSpectrum: (Long) -> Unit = {},
) {
    val colors = LocalAppColors.current
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

    // Спец §20: профиль сессии можно поправить задним числом; вместе с ним
    // пересчитывается участие сессии в обучении обычного фона.
    var reassigning by remember { mutableStateOf<SessionSummary?>(null) }
    val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
    reassigning?.let { session ->
        SessionProfileDialog(
            session = session,
            profiles = profiles,
            onPick = { profileId ->
                scope.launch {
                    graph.sessionRepository.reassignProfile(session.id, profileId)
                    reassigning = null
                    reload += 1
                }
            },
            onDismiss = { reassigning = null },
        )
    }

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(text = "История", color = colors.ink)
            Spacer(Modifier.weight(1f))
            if (selection.active) {
                Chip(
                    text = "Отмена",
                    color = colors.ink2,
                    onClick = { selection = selection.cancel() },
                )
            } else {
                // The counter is the way in: tapping «12 сессий» is asking to
                // do something with them.
                model?.let {
                    Chip(
                        text = "${it.totalSessions} сессий",
                        color = colors.ink2,
                        onClick = { selection = selection.start() },
                    )
                }
            }
        }
        if (selection.active) {
            Text(
                text = if (selection.isEmpty) {
                    HistoryDeletion.emptyHint()
                } else {
                    "выбрано: ${selection.count}"
                },
                style = type.footnote,
                color = colors.muted,
            )
        }

        val m = model
        if (m == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = "читаю журнал…", style = type.bodySmall, color = colors.muted)
            }
        } else {
            AccumulatedDoseCard(m, unit)

            SavedSpectraCard(
                graph = graph,
                onCompare = { first, second -> comparePair = first to second },
                onContinue = onContinueSpectrum,
                selectionActive = selection.active,
                selected = selection.spectra,
                onToggle = { id -> selection = selection.toggleSpectrum(id) },
            )

            if (m.items.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        Text(
                            text = "сессий пока нет",
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                        Text(
                            text = "Сессия — непрерывный период измерения: она начинается " +
                                "при подключении прибора и закрывается при отключении.",
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
                                    summary = item.summary,
                                    unit = unit,
                                    selectionActive = selection.active,
                                    selected = item.summary.id in selection.sessions,
                                    onClick = {
                                        if (selection.active) {
                                            // A session still being written to
                                            // cannot be deleted: the data is
                                            // arriving as we speak.
                                            if (item.summary.endedAt != null) {
                                                selection = selection.toggleSession(
                                                    item.summary.id,
                                                )
                                            }
                                        } else {
                                            onOpenSession(item.summary.id)
                                        }
                                    },
                                    onReassign = { reassigning = item.summary },
                                )
                                is HistoryItem.Deviation -> DeviationRow(item.event, unit)
                            }
                        }
                    }
                }
            }

            if (m.totalSessions > m.items.count { it is HistoryItem.Session }) {
                AppButton(
                    text = "Показать ещё",
                    onClick = { pages += 1 },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            AnimatedVisibility(
                visible = selection.active,
                enter = expandVertically(Motion.normal()) + fadeIn(Motion.normal()),
                exit = shrinkVertically(Motion.normal()) + fadeOut(Motion.fast()),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        AppButton(
                            text = HistoryDeletion.actionLabel(selection),
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
                            text = "Отмена",
                            onClick = { selection = selection.cancel() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccumulatedDoseCard(model: HistoryModel, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Накопленная доза".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Text(text = "расчёт", style = type.footnote, color = colors.muted)
            }
            val dailyMax = model.dailyDoseMicroSv.maxOrNull() ?: 0f
            if (dailyMax > 0f) {
                BarChart(
                    spec = BarChartSpec(
                        values = model.dailyDoseMicroSv.map { if (it > 0f) it else null },
                        yMax = dailyMax * 1.15f,
                        emphasizeLast = true,
                        xStartLabel = HistoryFormat.day(model.fromMillis),
                        xEndLabel = HistoryFormat.day(model.toMillis),
                    ),
                    height = 55.dp,
                )
            }
            StatGrid(
                cells = listOf(
                    StatCell(
                        DoseFormat.dose(model.doseTodayMicroSv, unit),
                        "сегодня, ${DoseFormat.doseUnitLabel(unit)}",
                    ),
                    StatCell(DoseFormat.dose(model.dose7dMicroSv, unit), "7 дней"),
                    StatCell(DoseFormat.dose(model.dose30dMicroSv, unit), "30 дней"),
                ),
            )
            Text(
                text = "Сумма мощности дозы по секундам измерения — не путать " +
                    "с текущей мощностью дозы.",
                style = type.footnote,
                color = colors.muted,
            )
            AppDivider()
            DoseProjectionBlock(model, unit)
        }
    }
}

/**
 * Проекция дозы (спец §6): D ≈ Ḋ·t как ЧИСТАЯ экстраполяция измеренной
 * средней мощности дозы. Формулировка задана спецификацией и не подлежит
 * упрощению: это не «годовая доза человека», а ответ на вопрос «сколько
 * набежит, если мощность останется такой же».
 */
@Composable
private fun DoseProjectionBlock(model: HistoryModel, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var windowIndex by rememberSaveable { mutableIntStateOf(1) }

    val doseMicroSv = if (windowIndex == 0) model.dose7dMicroSv else model.dose30dMicroSv
    val measuredSeconds =
        if (windowIndex == 0) model.measured7dSeconds else model.measured30dSeconds
    val projection = remember(doseMicroSv, measuredSeconds) {
        DoseProjection.fromIntegral(doseMicroSv, measuredSeconds)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Проекция дозы".uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )
            EvidenceTag(Evidence.CALCULATED, Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            Segmented(
                options = listOf("7 дней", "30 дней"),
                selectedIndex = windowIndex,
                onSelect = { windowIndex = it },
                modifier = Modifier.weight(1.1f),
            )
        }
        if (projection == null) {
            Text(
                text = HistoryFormat.doseProjectionUnavailable(measuredSeconds),
                style = type.bodySmall,
                color = colors.muted,
            )
        } else {
            Text(
                text = HistoryFormat.doseProjectionSentence(
                    DoseFormat.doseCoarseWithUnit(projection.doseMicroSv, unit),
                ),
                style = type.body,
                color = colors.ink,
            )
            Text(
                text = HistoryFormat.doseProjectionBasis(
                    DoseFormat.rateWithUnit(
                        projection.meanRateMicroSvPerHour.toFloat(),
                        unit,
                    ),
                    projection.measuredSeconds,
                ),
                style = type.footnote,
                color = colors.ink2,
            )
        }
        Text(
            text = HistoryFormat.DOSE_PROJECTION_CAVEAT,
            style = type.footnote,
            color = colors.muted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionRow(
    summary: SessionSummary,
    unit: DoseUnitSetting,
    onClick: () -> Unit,
    onReassign: () -> Unit,
    selectionActive: Boolean = false,
    selected: Boolean = false,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    val endedAt = summary.endedAt
    val durationSeconds = ((endedAt ?: now) - summary.startedAt) / 1000L

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
                if (endedAt == null) {
                    // Nothing to tick: a running session is still being written.
                    Spacer(Modifier.size(18.dp))
                } else {
                    CheckMark(selected = selected)
                }
                Spacer(Modifier.size(Dimens.space2))
            }
            Text(
                text = summary.profileName ?: "Без профиля",
                style = type.label,
                color = if (selectionActive && endedAt == null) colors.muted else colors.ink,
            )
            if (endedAt == null) {
                Text(
                    text = if (selectionActive) "· идёт, нельзя удалить" else "· идёт",
                    style = type.label,
                    color = if (selectionActive) colors.muted else colors.ok,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = HistoryFormat.dayTime(summary.startedAt, now) +
                    " · " + HistoryFormat.duration(durationSeconds),
                style = type.footnote,
                color = colors.ink2,
            )
        }

        val stats = summary.stats
        if (stats.sampleCount > 0 && stats.avgDoseRate != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DataItem("ср", DoseFormat.rate(stats.avgDoseRate, unit))
                DataItem("макс", DoseFormat.rate(stats.maxDoseRate ?: 0f, unit))
                DataItem("доза", DoseFormat.doseWithUnit(summary.doseMicroSv, unit))
                DataItem("n", HistoryFormat.count(stats.sampleCount))
                val badges = listOfNotNull(
                    "трек".takeIf { summary.hasTrack },
                    "спектр".takeIf { summary.hasSpectrum },
                    "полёт".takeIf { summary.hasFlight },
                )
                if (badges.isNotEmpty()) {
                    Text(
                        text = badges.joinToString(" · "),
                        style = type.valueSmall,
                        color = colors.ink2,
                    )
                }
            }
        } else {
            Text(
                text = "измерений в этой сессии не записано",
                style = type.valueSmall,
                color = colors.muted,
            )
        }

        // Спец §20: журнал обязан говорить, учила ли сессия обычный фон.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Text(
                text = HistoryFormat.admissionLine(summary.admission),
                style = type.footnote,
                color = if (summary.admission.included) colors.muted else colors.ink2,
                modifier = Modifier.weight(1f),
            )
            Chip(text = "профиль…", color = colors.ink2, onClick = onReassign)
        }
    }
}

/**
 * Поздняя правка профиля сессии (spec §20). Диалог честно предупреждает, что
 * меняется не подпись, а принадлежность измерений: сессия перейдёт в
 * статистику другого профиля.
 */
@Composable
private fun SessionProfileDialog(
    session: SessionSummary,
    profiles: List<ProfileEntity>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = "Профиль сессии", style = type.title, color = colors.ink)
                Text(
                    text = "Сессия от ${HistoryFormat.dayTime(session.startedAt, System.currentTimeMillis())}. " +
                        "Измерения перейдут в статистику выбранного профиля.",
                    style = type.bodySmall,
                    color = colors.muted,
                )
                ProfileTree.visible(profiles).forEach { profile ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Dimens.touchTarget)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onPick(profile.id) },
                            ),
                    ) {
                        RadioMark(profile.id == session.profileId)
                        Text(
                            text = ProfileTree.displayName(profile, profiles),
                            style = type.label,
                            color = colors.ink,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Dimens.touchTarget)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onPick(null) },
                        ),
                ) {
                    RadioMark(session.profileId == null)
                    Text(text = "Без профиля", style = type.label, color = colors.ink)
                }
                AppButton(text = "Отмена", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DataItem(label: String, value: String, valueColor: Color? = null) {
    val colors = LocalAppColors.current
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
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    val kind = when (event.source) {
        EventEntity.SOURCE_DEVIATION -> "Отклонение"
        else -> "Точка превышения"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "⚠ $kind", style = type.label, color = colors.warn)
            Spacer(Modifier.weight(1f))
            Text(
                text = HistoryFormat.dayTime(event.timestamp, now),
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
                    label = DoseFormat.rateUnitLabel(unit),
                    value = DoseFormat.rate(DoseUnits.rawToMicroSievertPerHour(it), unit),
                    valueColor = colors.warn,
                )
            }
            // param1 of a deviation stores the baseline typical high, nSv/h.
            if (event.source == EventEntity.SOURCE_DEVIATION && event.param1 > 0) {
                DataItem("обычно", DoseFormat.rate(event.param1 / 1000f, unit))
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
    onCompare: (Long, Long) -> Unit,
    onContinue: (Long) -> Unit,
    selectionActive: Boolean = false,
    selected: Set<Long> = emptySet(),
    onToggle: (Long) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val spectra by graph.measurementRepository.savedSpectra(SPECTRA_LIMIT)
        .collectAsState(initial = emptyList())
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
                    exportedNote = "файл сохранён"
                } else {
                    notice = SpectrumFileNotice(
                        title = "Экспорт не удался",
                        lines = listOf("Файл не записался — попробуйте другую папку."),
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
                    text = "Спектры".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                if (spectra.size >= 2 && !mergeMode && !selectionActive) {
                    Chip(
                        text = if (compareMode) "отмена" else "сравнить",
                        color = if (compareMode) colors.ink2 else colors.dataText,
                        onClick = {
                            compareMode = !compareMode
                            firstPickId = null
                        },
                    )
                }
                if (spectra.size >= 2 && !compareMode && !selectionActive) {
                    Chip(
                        text = if (mergeMode) "отмена" else "объединить",
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
                    selectionActive -> "отметьте снимки, которые нужно удалить"
                    compareMode -> "выберите два снимка — откроется сравнение"
                    mergeMode -> "отметьте два и более снимков — каналы сложатся, " +
                        "время накопления просуммируется"
                    else -> "снимок открывает экспорт, сравнение и продолжение"
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
                    text = "Объединить (${mergeIds.size})",
                    enabled = mergeIds.size >= 2,
                    onClick = {
                        val chosen = spectra.filter { it.id in mergeIds }
                        scope.launch {
                            when (val saved = mergeSnapshots(graph, chosen)) {
                                is MergeResult.Saved -> {
                                    mergeMode = false
                                    mergeIds = emptySet()
                                    exportedNote = "объединённый снимок «${saved.label}» " +
                                        "сохранён — он появился в списке"
                                }
                                is MergeResult.Refused -> notice = SpectrumFileNotice(
                                    title = "Объединить нельзя",
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
                        text = HistoryFormat.dayTime(entity.timestamp, System.currentTimeMillis()) +
                            " · Δt " + SpectrumFormat.accumulationClock(entity.durationSeconds),
                        style = type.footnote,
                        color = colors.ink2,
                    )
                    AppButton(
                        text = "Экспорт XML",
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
                        text = "Экспорт N42",
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
                        text = "Сравнить с другим…",
                        onClick = {
                            compareMode = true
                            firstPickId = entity.id
                            actionsFor = null
                        },
                        enabled = spectra.size >= 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = "Продолжить накопление",
                        onClick = {
                            actionsFor = null
                            onContinue(entity.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "снимок сложится с текущим накоплением на экране " +
                            "Спектр — прибор при этом копит независимо",
                        style = type.footnote,
                        color = colors.muted,
                    )
                    AppButton(
                        text = "Закрыть",
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
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = HistoryFormat.dayTime(entity.timestamp, now),
                style = type.footnote,
                color = colors.ink2,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space3)) {
            DataItem("Δt", SpectrumFormat.accumulationClock(entity.durationSeconds))
            val badges = listOfNotNull(
                "импорт".takeIf { entity.origin == SpectrumSnapshotEntity.ORIGIN_IMPORT },
                "фон".takeIf { entity.isBackgroundReference },
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
            val label = "merge · ${chosen.size} ${snapshotsPlural(chosen.size)}"
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

private fun snapshotsPlural(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod100 in 11..14 -> "снимков"
        mod10 == 1 -> "снимок"
        mod10 in 2..4 -> "снимка"
        else -> "снимков"
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

    val items = (
        sessions.map { HistoryItem.Session(it) } + events.map { HistoryItem.Deviation(it) }
        ).sortedByDescending { it.timestamp }

    val zone = ZoneId.systemDefault()
    val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val from30d = now - DOSE_DAYS.toLong() * 24 * 3600_000
    // Hour buckets across the 30-day span feed both the totals and the
    // per-day bars (AVG×COUNT integration is exact for any bucket width).
    val buckets30 = graph.measurementRepository.downsampledSamples(
        from = from30d,
        to = now,
        bucketMillis = 3_600_000L,
    )

    // «Сегодня» starts at local midnight, which epoch-hour buckets straddle —
    // minute buckets keep it exact (same as the Монитор figure).
    val todayBuckets = graph.measurementRepository.downsampledSamples(
        from = startOfDay,
        to = now,
        bucketMillis = 60_000L,
    )

    val buckets7 = buckets30.filter { it.bucketStart >= now - 7L * 24 * 3600_000 }
    return HistoryModel(
        doseTodayMicroSv = ChartMapping.integrateDoseMicroSv(todayBuckets),
        dose7dMicroSv = ChartMapping.integrateDoseMicroSv(buckets7),
        dose30dMicroSv = ChartMapping.integrateDoseMicroSv(buckets30),
        dailyDoseMicroSv = DailyDose.perDay(buckets30, now, zone, DOSE_DAYS),
        // 1 Hz sampling: one sample ≈ one measured second, the same assumption
        // the dose integral itself uses (ChartMapping.integrateDoseMicroSv).
        measured7dSeconds = buckets7.sumOf { it.sampleCount.toLong() },
        measured30dSeconds = buckets30.sumOf { it.sampleCount.toLong() },
        fromMillis = from30d,
        toMillis = now,
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
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = HistoryDeletion.title(plan), style = type.title, color = colors.ink)
                Text(
                    text = HistoryDeletion.body(plan),
                    style = type.bodySmall,
                    color = colors.ink2,
                )
                Text(
                    text = HistoryDeletion.keepsWording(plan),
                    style = type.footnote,
                    color = colors.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = "Удалить",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = "Отмена",
                        onClick = onDismiss,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
