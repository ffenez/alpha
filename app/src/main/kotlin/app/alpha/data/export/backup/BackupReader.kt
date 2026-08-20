package app.alpha.data.export.backup

import java.io.BufferedReader
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Что нашлось в копии — то, что показывается ДО восстановления. */
data class BackupInfo(
    val manifest: BackupManifest,
    /** Сколько записей в каждом ряду: «1 248 331 измерение, 42 сессии…». */
    val counts: BackupCounts,
    /** Файлы, которые есть в архиве. */
    val entries: Set<String>,
)

/** Почему копию нельзя восстановить. Каждая причина названа словами. */
sealed interface BackupProblem {
    /** Файл вообще не резервная копия приложения. */
    data object NotABackup : BackupProblem

    /** Файл пуст: так выглядит заглушка облака и оборванная запись. */
    data object EmptyFile : BackupProblem

    /** Копия новее, чем понимает это приложение. */
    data class TooNew(val formatVersion: Int, val supported: Int) : BackupProblem

    /** Обязательной части нет. */
    data class Missing(val entry: String) : BackupProblem

    /** Содержимое не совпало с контрольной суммой. */
    data class Corrupted(val entry: String) : BackupProblem

    /** Архив не читается вовсе. */
    data class Unreadable(val message: String) : BackupProblem
}

/** Как восстанавливать. */
enum class RestoreMode {
    /** Добавить недостающее, ничего не удаляя. Значение по умолчанию. */
    MERGE,

    /** Заменить данные приложения содержимым копии. */
    REPLACE,
}

/** Что человек согласился восстановить. */
data class RestoreSelection(
    val settings: Boolean = true,
    val profiles: Boolean = true,
    val measurements: Boolean = true,
    val routes: Boolean = true,
    val spectra: Boolean = true,
    val experiments: Boolean = true,
)

/** Итог восстановления — то, что показывается человеку в конце. */
data class RestoreSummary(
    val added: Map<BackupStage, Long> = emptyMap(),
    val skipped: Map<BackupStage, Long> = emptyMap(),
    val settingsRestored: Boolean = false,
    /** Части, которые не удалось восстановить, названные словами. */
    val notes: List<String> = emptyList(),
) {
    fun plusAdded(stage: BackupStage, count: Long): RestoreSummary =
        copy(added = added + (stage to (added[stage] ?: 0L) + count))

    fun plusSkipped(stage: BackupStage, count: Long): RestoreSummary =
        copy(skipped = skipped + (stage to (skipped[stage] ?: 0L) + count))
}

/** Куда читатель складывает восстановленное. Реализуется приложением. */
interface BackupSink {

    /** Начало восстановления: здесь решается судьба текущих данных. */
    suspend fun begin(mode: RestoreMode, selection: RestoreSelection)

    suspend fun settings(entries: List<Pair<String, String>>)
    suspend fun profiles(bundle: BackupProfiles): RestoreCount
    suspend fun sessions(batch: List<BackupSession>): RestoreCount
    suspend fun measurements(batch: List<BackupMeasurement>): RestoreCount
    suspend fun events(batch: List<BackupEvent>): RestoreCount
    suspend fun rare(batch: List<BackupRare>): RestoreCount
    suspend fun environment(batch: List<BackupEnvironment>): RestoreCount
    suspend fun routes(batch: List<BackupRoute>): RestoreCount
    suspend fun points(batch: List<BackupPoint>): RestoreCount
    suspend fun spectra(batch: List<BackupSpectrum>): RestoreCount
    suspend fun slices(batch: List<BackupSlice>): RestoreCount
    suspend fun experiments(batch: List<BackupExperiment>): RestoreCount

    /** Конец: здесь пересчитывается всё производное. */
    suspend fun finish()
}

/** Сколько записей добавлено и сколько пропущено как уже существующие. */
data class RestoreCount(val added: Long = 0, val skipped: Long = 0)

/**
 * Чтение резервной копии.
 *
 * ## Два прохода, и это не расточительство
 *
 * Сначала архив читается целиком ради проверки: манифест, версия формата,
 * обязательные части, контрольные суммы. Только потом — второй проход, который
 * восстанавливает. Иначе испорченная копия успела бы залить половину данных до
 * того, как обнаружится, что вторая половина не читается, — а частичное
 * молчаливое восстановление хуже честного отказа.
 *
 * Порядок проверки закреплён спецификацией: формат → версия → наличие частей →
 * суммы → и только затем запись.
 */
object BackupReader {

    /** Строк за одну транзакцию при восстановлении. */
    const val BATCH = 1_000

    /**
     * Имя части внутри архива без пути.
     *
     * Копия, прошедшая через распаковку и повторную упаковку (так делают
     * файловые менеджеры и облака), приезжает с папкой внутри: части те же,
     * лежат на уровень глубже. Отказывать такому файлу — наказывать человека
     * за чужой формат упаковки.
     */
    private fun shortName(entry: String): String = entry.substringAfterLast('/')

    /**
     * Первый проход: что это за файл и цел ли он.
     *
     * @param open открывает НОВЫЙ поток архива — читатель проходит его дважды.
     */
    fun inspect(open: () -> InputStream): Result<BackupInfo> = runCatching {
        var manifest: BackupManifest? = null
        var checksums: BackupChecksums? = null
        val digests = LinkedHashMap<String, String>()
        val counts = LinkedHashMap<String, Long>()
        val entries = LinkedHashSet<String>()

        open().use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = shortName(entry.name)
                    entries += name
                    val digest = MessageDigest.getInstance("SHA-256")
                    val text = StringBuilder()
                    val keepText = name.endsWith(".json")
                    var lines = 0L
                    val buffer = ByteArray(64 * 1024)
                    var pending = StringBuilder()
                    while (true) {
                        val read = zip.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                        val chunk = String(buffer, 0, read, Charsets.UTF_8)
                        if (keepText) text.append(chunk) else pending.append(chunk)
                        if (!keepText) {
                            var index = pending.indexOf("\n")
                            while (index >= 0) {
                                if (index > 0) lines++
                                pending = StringBuilder(pending.substring(index + 1))
                                index = pending.indexOf("\n")
                            }
                        }
                    }
                    if (!keepText && pending.isNotBlank()) lines++
                    counts[name] = lines
                    digests[name] = digest.digest().joinToString("") { "%02x".format(it) }
                    when (name) {
                        BackupFormat.MANIFEST -> manifest = BackupManifest.parse(text.toString())
                        BackupFormat.CHECKSUMS -> checksums = BackupChecksums.parse(text.toString())
                    }
                }
            }
        }

        // Пустой файл — отдельный разговор: так выглядит копия, которую
        // облачное хранилище отдало заглушкой, и «это не копия» тут не
        // подсказывает, что делать.
        if (entries.isEmpty()) throw BackupException(BackupProblem.EmptyFile)
        val found = manifest ?: throw BackupException(BackupProblem.NotABackup)
        if (found.format !in BackupFormat.KNOWN_FORMATS) {
            throw BackupException(BackupProblem.NotABackup)
        }
        if (found.formatVersion > BackupFormat.VERSION) {
            throw BackupException(
                BackupProblem.TooNew(found.formatVersion, BackupFormat.VERSION),
            )
        }
        for (required in BackupFormat.REQUIRED) {
            if (required !in entries) throw BackupException(BackupProblem.Missing(required))
        }
        val expected = checksums ?: throw BackupException(
            BackupProblem.Missing(BackupFormat.CHECKSUMS),
        )
        for ((name, digest) in digests) {
            if (name == BackupFormat.CHECKSUMS) continue
            val want = expected.sha256[name] ?: throw BackupException(BackupProblem.Missing(name))
            if (want != digest) throw BackupException(BackupProblem.Corrupted(name))
        }

        BackupInfo(
            manifest = found,
            counts = BackupCounts(
                measurements = counts[BackupFormat.MEASUREMENTS] ?: 0,
                events = counts[BackupFormat.EVENTS] ?: 0,
                rare = counts[BackupFormat.RARE] ?: 0,
                environment = counts[BackupFormat.ENVIRONMENT] ?: 0,
                sessions = counts[BackupFormat.SESSIONS] ?: 0,
                routes = counts[BackupFormat.ROUTES] ?: 0,
                points = counts[BackupFormat.POINTS] ?: 0,
                spectra = counts[BackupFormat.SPECTRA] ?: 0,
                slices = counts[BackupFormat.SPECTROGRAM] ?: 0,
                experiments = counts[BackupFormat.EXPERIMENTS] ?: 0,
            ),
            entries = entries,
        )
    }

    /**
     * Второй проход: восстановление. Вызывать только после успешной [inspect].
     */
    suspend fun restore(
        open: () -> InputStream,
        info: BackupInfo,
        mode: RestoreMode,
        selection: RestoreSelection,
        sink: BackupSink,
        onProgress: (BackupProgress) -> Unit = {},
    ): RestoreSummary {
        var summary = RestoreSummary()
        sink.begin(mode, selection)
        open().use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    when (shortName(entry.name)) {
                        BackupFormat.SETTINGS -> if (selection.settings) {
                            val values = parseSettings(readText(zip))
                            sink.settings(values)
                            summary = summary.copy(settingsRestored = true)
                        }
                        BackupFormat.PROFILES -> if (selection.profiles) {
                            val bundle = parseProfiles(readText(zip))
                            val count = sink.profiles(bundle)
                            summary = summary
                                .plusAdded(BackupStage.PROFILES, count.added)
                                .plusSkipped(BackupStage.PROFILES, count.skipped)
                        }
                        BackupFormat.SESSIONS -> if (selection.measurements) {
                            summary = consume(
                                zip, BackupStage.SESSIONS, info.counts.sessions, summary,
                                onProgress, BackupSession::parse,
                            ) { sink.sessions(it) }
                        }
                        BackupFormat.MEASUREMENTS -> if (selection.measurements) {
                            summary = consume(
                                zip, BackupStage.MEASUREMENTS, info.counts.measurements, summary,
                                onProgress, BackupMeasurement::parse,
                            ) { sink.measurements(it) }
                        }
                        BackupFormat.EVENTS -> if (selection.measurements) {
                            summary = consume(
                                zip, BackupStage.EVENTS, info.counts.events, summary,
                                onProgress, BackupEvent::parse,
                            ) { sink.events(it) }
                        }
                        BackupFormat.RARE -> if (selection.measurements) {
                            summary = consume(
                                zip, BackupStage.RARE, info.counts.rare, summary,
                                onProgress, BackupRare::parse,
                            ) { sink.rare(it) }
                        }
                        BackupFormat.ENVIRONMENT -> if (selection.measurements) {
                            summary = consume(
                                zip, BackupStage.ENVIRONMENT, info.counts.environment, summary,
                                onProgress, BackupEnvironment::parse,
                            ) { sink.environment(it) }
                        }
                        BackupFormat.ROUTES -> if (selection.routes) {
                            summary = consume(
                                zip, BackupStage.ROUTES, info.counts.routes, summary,
                                onProgress, BackupRoute::parse,
                            ) { sink.routes(it) }
                        }
                        BackupFormat.POINTS -> if (selection.routes) {
                            summary = consume(
                                zip, BackupStage.POINTS, info.counts.points, summary,
                                onProgress, BackupPoint::parse,
                            ) { sink.points(it) }
                        }
                        BackupFormat.SPECTRA -> if (selection.spectra) {
                            summary = consume(
                                zip, BackupStage.SPECTRA, info.counts.spectra, summary,
                                onProgress, BackupSpectrum::parse,
                            ) { sink.spectra(it) }
                        }
                        BackupFormat.SPECTROGRAM -> if (selection.spectra) {
                            summary = consume(
                                zip, BackupStage.SPECTROGRAM, info.counts.slices, summary,
                                onProgress, BackupSlice::parse,
                            ) { sink.slices(it) }
                        }
                        BackupFormat.EXPERIMENTS -> if (selection.experiments) {
                            summary = consume(
                                zip, BackupStage.EXPERIMENTS, info.counts.experiments, summary,
                                onProgress, BackupExperiment::parse,
                            ) { sink.experiments(it) }
                        }
                    }
                }
            }
        }
        sink.finish()
        return summary
    }

    private suspend fun <T> consume(
        zip: ZipInputStream,
        stage: BackupStage,
        total: Long,
        summary: RestoreSummary,
        onProgress: (BackupProgress) -> Unit,
        parse: (Json.Value.Obj) -> T,
        write: suspend (List<T>) -> RestoreCount,
    ): RestoreSummary {
        var result = summary
        var done = 0L
        val batch = ArrayList<T>(BATCH)
        // Поток архива закрывать нельзя: за этой записью идут следующие.
        val reader = BufferedReader(zip.reader(Charsets.UTF_8))
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            val parsed = runCatching { parse(Json.parseObject(line)) }.getOrNull() ?: continue
            batch += parsed
            if (batch.size >= BATCH) {
                val count = write(batch)
                result = result
                    .plusAdded(stage, count.added)
                    .plusSkipped(stage, count.skipped)
                done += batch.size
                onProgress(BackupProgress(stage, done, total))
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            val count = write(batch)
            result = result
                .plusAdded(stage, count.added)
                .plusSkipped(stage, count.skipped)
            done += batch.size
            onProgress(BackupProgress(stage, done, total))
        }
        return result
    }

    fun parseProfiles(text: String): BackupProfiles {
        val root = Json.parseObject(text)
        fun array(name: String): List<Json.Value.Obj> =
            (root.fields[name] as? Json.Value.Arr)?.items?.filterIsInstance<Json.Value.Obj>()
                .orEmpty()
        return BackupProfiles(
            profiles = array("profiles").map { BackupProfile.parse(it) },
            networks = array("networks").map { BackupNetwork.parse(it) },
            epochs = array("epochs").map { BackupEpoch.parse(it) },
            fingerprints = array("fingerprints").map { BackupFingerprint.parse(it) },
        )
    }

    fun parseSettings(text: String): List<Pair<String, String>> {
        val root = Json.parseObject(text)
        val values = root.obj("values")?.fields.orEmpty()
        return values.mapNotNull { (key, value) ->
            (value as? Json.Value.Str)?.let { key to it.value }
        }
    }

    private fun readText(zip: ZipInputStream): String {
        val out = StringBuilder()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            out.append(String(buffer, 0, read, Charsets.UTF_8))
        }
        return out.toString()
    }
}

/** Копию восстановить нельзя, и причина названа. */
class BackupException(val problem: BackupProblem) : Exception(problem.toString())
