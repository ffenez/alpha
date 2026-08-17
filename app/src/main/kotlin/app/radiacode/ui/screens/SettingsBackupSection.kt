package app.radiacode.ui.screens

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
import app.radiacode.AppGraph
import app.radiacode.data.BackupJob
import app.radiacode.data.export.backup.BackupFormat
import app.radiacode.data.export.backup.BackupInfo
import app.radiacode.data.export.backup.BackupProblem
import app.radiacode.data.export.backup.BackupStage
import app.radiacode.data.export.backup.RestoreMode
import app.radiacode.data.export.backup.RestoreSelection
import app.radiacode.data.export.backup.RestoreSummary
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.SettingRow
import app.radiacode.ui.components.SettingsDivider
import app.radiacode.ui.components.SettingsSection
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.SwitchSettingRow
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.text.BackupCatalogue
import app.radiacode.ui.text.BackupStrings
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Настройки → Данные и резервные копии.
 *
 * ## Что здесь происходит
 *
 * Копия — это ОДИН файл, который человек кладёт куда хочет системным
 * диалогом: в загрузки, на карту, в облачное хранилище. Приложение не просит
 * доступ «ко всем файлам» и не решает за человека, где хранить его данные.
 *
 * Восстановление идёт в два шага, и первый ничего не меняет: копия читается,
 * проверяется целиком и рассказывает о себе — когда снята, чем и что внутри.
 * Только после этого человек выбирает, объединить её с текущими данными или
 * заменить их, и что именно восстанавливать.
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

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME),
    ) { uri ->
        if (uri != null) manager.save(uri)
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
                onClick = {
                    val today = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.now())
                    createLauncher.launch(BackupFormat.fileName(today))
                },
            )
            SettingsDivider()
            SettingRow(
                title = t.restoreBackup,
                subtitle = t.restoreBackupNote,
                enabled = !manager.busy,
                // Формат свой, поэтому системе он неизвестен: показываем все
                // файлы, а разбираемся сами — и честно говорим, если это не
                // копия.
                onClick = { openLauncher.launch(arrayOf("*/*")) },
            )
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

/** Ход работы, разбор копии и итог — одной карточкой, которая ведёт человека. */
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

        // Выбор частей появляется ПОСЛЕ чтения файла: до него человеку
        // нечего выбирать — он ещё не знает, что в копии.
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

/** Дата создания копии человеческим видом; если не разобралась — как есть. */
private fun readableDate(iso: String): String = runCatching {
    val instant = java.time.OffsetDateTime.parse(iso).toInstant()
    DateTimeFormatter.ofPattern("d MMM yyyy")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault(iso)

/** Сколько места занимает база вместе с журналами записи. */
private fun databaseBytes(context: android.content.Context): Long {
    val main = context.getDatabasePath(app.radiacode.data.db.AppDatabase.NAME)
    val parts = listOf(main, java.io.File(main.path + "-wal"), java.io.File(main.path + "-shm"))
    return parts.filter { it.exists() }.sumOf { it.length() }
}
