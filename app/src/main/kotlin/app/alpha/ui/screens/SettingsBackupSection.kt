package app.alpha.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.alpha.AppGraph
import app.alpha.data.BackupJob
import app.alpha.data.export.backup.BackupContent
import app.alpha.data.export.backup.BackupFormat
import app.alpha.data.export.backup.BackupInfo
import app.alpha.data.export.backup.BackupProblem
import app.alpha.data.export.backup.BackupStage
import app.alpha.data.export.backup.RestoreMode
import app.alpha.data.export.backup.RestoreSelection
import app.alpha.data.export.backup.RestoreSummary
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.Card
import app.alpha.ui.components.Hint
import app.alpha.ui.components.SettingRow
import app.alpha.ui.components.SettingsDivider
import app.alpha.ui.components.SettingsSection
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.SwitchSettingRow
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.text.BackupCatalogue
import app.alpha.ui.text.BackupStrings
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Настройки → Данные и резервные копии.
 *
 * Копия — один файл, который кладут системным диалогом: приложение не просит
 * доступ «ко всем файлам» и не выбирает место хранения.
 *
 * Восстановление идёт в два шага, и первый ничего не меняет: копия читается,
 * проверяется целиком и сообщает, когда снята, чем и что внутри. После этого
 * выбирается режим (объединить или заменить) и состав.
 */
@Composable
internal fun BackupSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = BackupCatalogue.of(strings.language)
    val context = LocalContext.current
    val manager = graph.backupManager
    val job by manager.state.collectAsState()

    var pendingSource by remember { mutableStateOf<android.net.Uri?>(null) }
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    var selection by remember { mutableStateOf(RestoreSelection()) }
    // Что и за какое время сохранять, решается до системного диалога: имя
    // файла задаётся уже зная состав.
    var planning by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf(BackupContent()) }
    var period by remember { mutableStateOf(BackupPeriod.ALL) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME),
    ) { uri ->
        if (uri != null) manager.save(uri, content, period.fromMillis())
    }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingSource = uri
            manager.inspect(uri)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        SettingsSection(title = t.sectionBackup) {
            SettingRow(
                title = t.createBackup,
                subtitle = t.createBackupNote,
                enabled = !manager.busy,
                onClick = { planning = !planning },
            )
            AnimatedVisibility(visible = planning) {
                SavePlan(
                    t = t,
                    content = content,
                    onContent = { content = it },
                    period = period,
                    onPeriod = { period = it },
                    onSave = {
                        planning = false
                        val today = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.now())
                        createLauncher.launch(BackupFormat.fileName(today))
                    },
                )
            }
            SettingsDivider()
            SettingRow(
                title = t.restoreBackup,
                subtitle = t.restoreBackupNote,
                enabled = !manager.busy,
                // Формат свой, системе он неизвестен: показываются все файлы,
                // разбор делает приложение и сообщает, если это не копия.
                onClick = { openLauncher.launch(arrayOf("*/*")) },
            )
            SettingsDivider()
            Hint(text = t.exportWhere)
        }

        JobCard(
            job = job,
            t = t,
            onRestore = { restoreMode, restoreSelection ->
                val source = pendingSource
                val info = (job as? BackupJob.Inspected)?.info
                if (source != null && info != null) {
                    manager.restore(source, info, restoreMode, restoreSelection)
                }
            },
            mode = mode,
            onMode = { mode = it },
            selection = selection,
            onSelection = { selection = it },
            onDismiss = { manager.clear() },
        )

        SettingsSection(title = t.sectionStorage) {
            RetentionRows(graph)
            SettingsDivider()
            val size = remember { databaseBytes(context) }
            SettingRow(title = t.dataSize, value = HistoryFormat.bytes(size))
        }
    }
}

/** Ход работы, разбор копии и итог — одной карточкой. */
@Composable
private fun JobCard(
    job: BackupJob,
    t: BackupStrings,
    mode: RestoreMode,
    onMode: (RestoreMode) -> Unit,
    selection: RestoreSelection,
    onSelection: (RestoreSelection) -> Unit,
    onRestore: (RestoreMode, RestoreSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    AnimatedVisibility(visible = job !is BackupJob.Idle) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                when (job) {
                    BackupJob.Idle -> Unit
                    is BackupJob.Saving -> {
                        Text(text = t.saving, style = type.label, color = colors.ink)
                        ProgressLine(t.stageName(job.progress.stage), job.progress.done, job.progress.total)
                    }
                    BackupJob.Inspecting -> Text(
                        text = t.checking,
                        style = type.label,
                        color = colors.ink,
                    )
                    is BackupJob.Inspected -> RestorePlan(
                        info = job.info,
                        t = t,
                        mode = mode,
                        onMode = onMode,
                        selection = selection,
                        onSelection = onSelection,
                        onRestore = onRestore,
                        onDismiss = onDismiss,
                    )
                    is BackupJob.Restoring -> {
                        Text(text = t.restoring, style = type.label, color = colors.ink)
                        ProgressLine(t.stageName(job.progress.stage), job.progress.done, job.progress.total)
                    }
                    is BackupJob.Saved -> {
                        Text(text = t.saved, style = type.label, color = colors.dataText)
                        Text(
                            text = HistoryFormat.bytes(job.bytes),
                            style = type.footnoteMono,
                            color = colors.ink2,
                        )
                        AppButton(text = t.close, onClick = onDismiss)
                    }
                    is BackupJob.Restored -> {
                        Text(text = t.restored, style = type.label, color = colors.dataText)
                        SummaryLines(job.summary, t)
                        AppButton(text = t.close, onClick = onDismiss)
                    }
                    is BackupJob.Failed -> {
                        Text(text = t.failed, style = type.label, color = colors.crit)
                        Text(
                            text = t.problem(job.problem),
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                        AppButton(text = t.close, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

/** Что в копии и как её восстанавливать. Ничего ещё не изменено. */
/** Период копии. Границы — «сейчас минус столько-то», не календарные месяцы. */
internal enum class BackupPeriod(val days: Long?) {
    ALL(null),
    YEAR(365),
    MONTH(30),
    WEEK(7),
    ;

    fun fromMillis(now: Long = System.currentTimeMillis()): Long? =
        days?.let { now - it * MILLIS_PER_DAY }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/**
 * Что и за какое время сохранять.
 *
 * История за год — гигабайты, и переносят её не всегда целиком: на новый
 * телефон нужны настройки и профили, в архив — месяц измерений.
 *
 * Период не трогает настройки и профили: у профиля нет времени, а копия за
 * неделю без профилей восстановилась бы историей, привязанной в никуда. Об
 * этом сказано строкой под выбором.
 */
@Composable
private fun SavePlan(
    t: BackupStrings,
    content: BackupContent,
    onContent: (BackupContent) -> Unit,
    period: BackupPeriod,
    onPeriod: (BackupPeriod) -> Unit,
    onSave: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val periods = listOf(
        BackupPeriod.ALL to t.periodAll,
        BackupPeriod.YEAR to t.periodYear,
        BackupPeriod.MONTH to t.periodMonth,
        BackupPeriod.WEEK to t.periodWeek,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        Text(text = t.whatToSave, style = type.label, color = colors.ink)
        SwitchSettingRow(
            title = t.partSettings,
            checked = content.settings,
            onChange = { onContent(content.copy(settings = it)) },
        )
        SwitchSettingRow(
            title = t.partProfiles,
            checked = content.profiles,
            onChange = { onContent(content.copy(profiles = it)) },
        )
        // Сессии идут с измерениями: сессия без измерений — пустой отрезок
        // времени, а измерения без сессий теряют, чем они были.
        SwitchSettingRow(
            title = t.partMeasurements,
            checked = content.measurements,
            onChange = { onContent(content.copy(measurements = it, sessions = it)) },
        )
        SwitchSettingRow(
            title = t.partRoutes,
            checked = content.routes,
            onChange = { onContent(content.copy(routes = it)) },
        )
        // Спектрограмма едет со спектрами — так же, как их восстанавливают.
        SwitchSettingRow(
            title = t.partSpectra,
            checked = content.spectra,
            onChange = { onContent(content.copy(spectra = it, spectrogram = it)) },
        )
        SwitchSettingRow(
            title = t.partExperiments,
            checked = content.experiments,
            onChange = { onContent(content.copy(experiments = it)) },
        )

        Text(text = t.periodTitle, style = type.label, color = colors.ink)
        Segmented(
            options = periods.map { it.second },
            selectedIndex = periods.indexOfFirst { it.first == period }.coerceAtLeast(0),
            onSelect = { onPeriod(periods[it].first) },
            modifier = Modifier.fillMaxWidth(),
        )
        val from = period.fromMillis()
        Text(
            text = if (from == null) {
                t.periodEverything
            } else {
                t.periodSince(
                    DateTimeFormatter.ofPattern("d MMMM yyyy")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(from)),
                )
            },
            style = type.footnote,
            color = colors.muted,
        )

        val anything = content.settings || content.profiles || content.measurements ||
            content.routes || content.spectra || content.experiments
        AppButton(text = t.saveAction, onClick = onSave, enabled = anything)
        if (!anything) {
            Text(text = t.nothingChosen, style = type.footnote, color = colors.warn)
        }
    }
}

@Composable
private fun RestorePlan(
    info: BackupInfo,
    t: BackupStrings,
    mode: RestoreMode,
    onMode: (RestoreMode) -> Unit,
    selection: RestoreSelection,
    onSelection: (RestoreSelection) -> Unit,
    onRestore: (RestoreMode, RestoreSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        Text(text = t.backupFound, style = type.label, color = colors.ink)
        Text(
            text = listOfNotNull(
                readableDate(info.manifest.createdAt),
                info.manifest.deviceModel,
                info.manifest.appVersion,
                // Копия за период говорит об этом сама: иначе «мало записей»
                // читается как потеря данных.
                info.manifest.fromMillis?.let { t.savedPeriod(dayText(it)) },
            ).joinToString(" · "),
            style = type.footnote,
            color = colors.ink2,
        )
        Text(text = t.contains, style = type.footnote, color = colors.muted)
        for (line in t.contentLines(info.counts)) {
            Text(text = "• $line", style = type.bodySmall, color = colors.ink2)
        }

        Text(text = t.howToRestore, style = type.label, color = colors.ink)
        Segmented(
            options = listOf(t.merge, t.replace),
            selectedIndex = if (mode == RestoreMode.MERGE) 0 else 1,
            onSelect = { onMode(if (it == 0) RestoreMode.MERGE else RestoreMode.REPLACE) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (mode == RestoreMode.MERGE) t.mergeNote else t.replaceNote,
            style = type.footnote,
            color = if (mode == RestoreMode.MERGE) colors.muted else colors.warn,
        )

        // Выбор частей появляется после чтения файла: до него состав копии
        // неизвестен.
        SwitchSettingRow(
            title = t.partSettings,
            checked = selection.settings,
            onChange = { onSelection(selection.copy(settings = it)) },
        )
        SwitchSettingRow(
            title = t.partProfiles,
            checked = selection.profiles,
            onChange = { onSelection(selection.copy(profiles = it)) },
        )
        SwitchSettingRow(
            title = t.partMeasurements,
            checked = selection.measurements,
            onChange = { onSelection(selection.copy(measurements = it)) },
        )
        SwitchSettingRow(
            title = t.partRoutes,
            checked = selection.routes,
            onChange = { onSelection(selection.copy(routes = it)) },
        )
        SwitchSettingRow(
            title = t.partSpectra,
            checked = selection.spectra,
            onChange = { onSelection(selection.copy(spectra = it)) },
        )
        SwitchSettingRow(
            title = t.partExperiments,
            checked = selection.experiments,
            onChange = { onSelection(selection.copy(experiments = it)) },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton(text = t.restoreAction, onClick = { onRestore(mode, selection) })
            AppButton(text = t.cancel, onClick = onDismiss)
        }
    }
}

@Composable
private fun ProgressLine(stage: String, done: Long, total: Long?) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
        Text(text = stage, style = type.bodySmall, color = colors.ink2)
        val text = if (total != null && total > 0) {
            "${HistoryFormat.count(done.toInt())} / ${HistoryFormat.count(total.toInt())}"
        } else {
            HistoryFormat.count(done.toInt())
        }
        Text(text = text, style = type.footnoteMono, color = colors.ink)
    }
}

@Composable
private fun SummaryLines(summary: RestoreSummary, t: BackupStrings) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
        for (line in t.summaryAdded(summary)) {
            Text(text = line, style = type.bodySmall, color = colors.ink2)
        }
        for (line in t.summarySkipped(summary)) {
            Text(text = line, style = type.footnote, color = colors.muted)
        }
        if (summary.settingsRestored) {
            Text(text = t.settingsRestored, style = type.footnote, color = colors.muted)
        }
    }
}

/** День человеческим видом — для периода копии. */
private fun dayText(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))

/** Дата создания копии человеческим видом; если не разобралась — как есть. */
private fun readableDate(iso: String): String = runCatching {
    val instant = java.time.OffsetDateTime.parse(iso).toInstant()
    DateTimeFormatter.ofPattern("d MMM yyyy")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault(iso)

/** Сколько места занимает база вместе с журналами записи. */
private fun databaseBytes(context: android.content.Context): Long {
    val main = context.getDatabasePath(app.alpha.data.db.AppDatabase.NAME)
    val parts = listOf(main, java.io.File(main.path + "-wal"), java.io.File(main.path + "-shm"))
    return parts.filter { it.exists() }.sumOf { it.length() }
}
