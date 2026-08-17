package app.radiacode.data

import android.content.ContentResolver
import android.net.Uri
import app.radiacode.data.export.backup.BackupContent
import app.radiacode.data.export.backup.BackupException
import app.radiacode.data.export.backup.BackupInfo
import app.radiacode.data.export.backup.BackupManifest
import app.radiacode.data.export.backup.BackupProblem
import app.radiacode.data.export.backup.BackupProgress
import app.radiacode.data.export.backup.BackupReader
import app.radiacode.data.export.backup.BackupWriter
import app.radiacode.data.export.backup.RestoreMode
import app.radiacode.data.export.backup.RestoreSelection
import app.radiacode.data.export.backup.RestoreSummary
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Что сейчас происходит с копией — то, что видно на экране. */
sealed interface BackupJob {

    data object Idle : BackupJob

    /** Копия пишется. [progress] обновляется по ходу. */
    data class Saving(val progress: BackupProgress) : BackupJob

    /** Копия читается и проверяется. */
    data object Inspecting : BackupJob

    /** Копия проверена: вот что в ней, и можно решать, как восстанавливать. */
    data class Inspected(val info: BackupInfo) : BackupJob

    /** Идёт восстановление. */
    data class Restoring(val progress: BackupProgress) : BackupJob

    /** Копия записана. */
    data class Saved(val bytes: Long) : BackupJob

    /** Восстановление закончено — вот что изменилось. */
    data class Restored(val summary: RestoreSummary) : BackupJob

    /** Не получилось, и причина названа. */
    data class Failed(val problem: BackupProblem) : BackupJob
}

/**
 * Резервные копии: создание, проверка, восстановление.
 *
 * ## Почему не в экране
 *
 * Копия большой истории идёт минутами. Операция, живущая в композиции, умерла
 * бы от поворота экрана и от ухода на другую вкладку — а человек в это время
 * ждёт. Работа идёт в области жизни приложения, экран лишь смотрит на
 * [state]; вернувшись, он застаёт ту же операцию, а не пустой экран.
 *
 * ## Порядок восстановления
 *
 * Файл сначала ЧИТАЕТСЯ ЦЕЛИКОМ ради проверки — манифест, версия формата,
 * контрольные суммы, — и только потом второй проход что-то записывает. Так
 * испорченная копия не успевает залить половину данных (§6, §45 ТЗ).
 */
class BackupManager(
    private val contentResolver: ContentResolver,
    private val repository: BackupRepository,
    private val appVersion: String,
    private val databaseSchemaVersion: Int,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow<BackupJob>(BackupJob.Idle)
    val state: StateFlow<BackupJob> = _state.asStateFlow()

    private var running: Job? = null

    /** Идёт ли сейчас длинная работа: пока идёт, вторую начинать нельзя. */
    val busy: Boolean
        get() = when (_state.value) {
            is BackupJob.Saving, is BackupJob.Restoring, BackupJob.Inspecting -> true
            else -> false
        }

    /**
     * Создать копию в выбранный человеком файл.
     *
     * @param content что человек согласился включить.
     * @param fromMillis начало периода; null — вся история.
     */
    fun save(
        target: Uri,
        content: BackupContent = BackupContent(),
        fromMillis: Long? = null,
        deviceModel: String? = null,
    ) {
        if (busy) return
        running = scope.launch {
            _state.value = BackupJob.Saving(
                BackupProgress(app.radiacode.data.export.backup.BackupStage.PROFILES, 0, null),
            )
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val manifest = BackupManifest(
                        createdAt = java.time.Instant.ofEpochMilli(clock())
                            .atZone(java.time.ZoneId.systemDefault())
                            .toOffsetDateTime()
                            .toString(),
                        appVersion = appVersion,
                        databaseSchemaVersion = databaseSchemaVersion,
                        deviceModel = deviceModel,
                        fromMillis = fromMillis,
                        content = content,
                    )
                    contentResolver.openOutputStream(target, "wt")?.use { out ->
                        val source = repository.scopedTo(fromMillis)
                        BackupWriter(out).write(source, manifest, content) { progress ->
                            _state.value = BackupJob.Saving(progress)
                        }
                    } ?: error("нет доступа к выбранному файлу")
                    fileSize(target)
                }
            }
            _state.value = result.fold(
                onSuccess = { BackupJob.Saved(it) },
                onFailure = { BackupJob.Failed(problemOf(it)) },
            )
        }
    }

    /** Прочитать и проверить копию, ничего не меняя. */
    fun inspect(source: Uri) {
        if (busy) return
        running = scope.launch {
            _state.value = BackupJob.Inspecting
            val result = withContext(Dispatchers.IO) {
                BackupReader.inspect { openInput(source) }
            }
            _state.value = result.fold(
                onSuccess = { BackupJob.Inspected(it) },
                onFailure = { BackupJob.Failed(problemOf(it)) },
            )
        }
    }

    /** Восстановить проверенную копию. */
    fun restore(
        source: Uri,
        info: BackupInfo,
        mode: RestoreMode,
        selection: RestoreSelection,
    ) {
        if (busy) return
        running = scope.launch {
            _state.value = BackupJob.Restoring(
                BackupProgress(app.radiacode.data.export.backup.BackupStage.PROFILES, 0, null),
            )
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    BackupReader.restore(
                        open = { openInput(source) },
                        info = info,
                        mode = mode,
                        selection = selection,
                        sink = repository,
                    ) { progress -> _state.value = BackupJob.Restoring(progress) }
                }
            }
            _state.value = result.fold(
                onSuccess = { BackupJob.Restored(it) },
                onFailure = { BackupJob.Failed(problemOf(it)) },
            )
        }
    }

    /** Убрать с экрана итог прошлой операции. */
    fun clear() {
        if (busy) return
        _state.value = BackupJob.Idle
    }

    private fun openInput(source: Uri): InputStream =
        contentResolver.openInputStream(source) ?: error("файл не открывается")

    private fun fileSize(uri: Uri): Long =
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull() ?: 0L

    private fun problemOf(error: Throwable): BackupProblem = when (error) {
        is BackupException -> error.problem
        else -> BackupProblem.Unreadable(error.message ?: error::class.java.simpleName)
    }
}
