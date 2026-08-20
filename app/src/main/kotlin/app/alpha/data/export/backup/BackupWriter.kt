package app.alpha.data.export.backup

import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Страница потока: сколько-то записей и метка, с которой читать дальше. */
data class BackupPage<T>(val items: List<T>, val nextCursor: Long?)

/** Поток записей копии: читается порциями, целиком в память не попадает. */
fun interface BackupStream<T> {
    suspend fun page(cursor: Long, limit: Int): BackupPage<T>
}

/** Профили и всё, что к ним привязано: их немного, читаются разом. */
data class BackupProfiles(
    val profiles: List<BackupProfile> = emptyList(),
    val networks: List<BackupNetwork> = emptyList(),
    val epochs: List<BackupEpoch> = emptyList(),
    val fingerprints: List<BackupFingerprint> = emptyList(),
)

/** Сколько чего в копии — для честного прогресса, а не «крутится колесо». */
data class BackupCounts(
    val measurements: Long = 0,
    val events: Long = 0,
    val rare: Long = 0,
    val environment: Long = 0,
    val stations: Long = 0,
    val sessions: Long = 0,
    val routes: Long = 0,
    val points: Long = 0,
    val spectra: Long = 0,
    val slices: Long = 0,
    val experiments: Long = 0,
)

/** Откуда писатель берёт данные. Реализуется на стороне приложения. */
interface BackupSource {
    suspend fun counts(): BackupCounts
    suspend fun profiles(): BackupProfiles

    /** Настройки приложения парами «ключ → значение с типом»; null — не включать. */
    suspend fun settings(): List<Pair<String, String>>?

    fun sessions(): BackupStream<BackupSession>
    fun measurements(): BackupStream<BackupMeasurement>
    fun events(): BackupStream<BackupEvent>
    fun rare(): BackupStream<BackupRare>
    fun environment(): BackupStream<BackupEnvironment>
    fun stations(): BackupStream<BackupStation>
    fun routes(): BackupStream<BackupRoute>
    fun points(): BackupStream<BackupPoint>
    fun spectra(): BackupStream<BackupSpectrum>
    fun slices(): BackupStream<BackupSlice>
    fun experiments(): BackupStream<BackupExperiment>
}

/** Что сейчас делает копия — то, что видит человек на экране. */
data class BackupProgress(
    val stage: BackupStage,
    val done: Long,
    val total: Long?,
)

enum class BackupStage {
    PROFILES,
    SETTINGS,
    SESSIONS,
    MEASUREMENTS,
    EVENTS,
    RARE,
    ENVIRONMENT,
    ROUTES,
    POINTS,
    SPECTRA,
    STATIONS,
    SPECTROGRAM,
    EXPERIMENTS,
    FINISHING,

    /** Копия перечитывается с диска: файл, который не читается, — не копия. */
    VERIFYING,
}

/**
 * Запись резервной копии потоком.
 *
 * ## Почему потоком
 *
 * История на год — это десятки миллионов измерений. Собрать их в один список,
 * превратить в одну строку и сжать значило бы потребовать сотни мегабайт
 * памяти и упасть ровно у того, у кого данных больше всех. Здесь запись идёт
 * страницами: страница прочитана из базы, записана строками в архив и забыта.
 *
 * ## Контрольные суммы
 *
 * Считаются ПО ХОДУ записи, от несжатого содержимого: читателю важно, что
 * распакованные данные те же, а не что совпали байты сжатия, которое зависит
 * от версии библиотеки. Список сумм пишется последней записью — к этому
 * моменту он готов.
 */
class BackupWriter(
    output: OutputStream,
    private val pageSize: Int = DEFAULT_PAGE,
) {

    private val zip = ZipOutputStream(output.buffered()).apply {
        setLevel(Deflater.BEST_SPEED)
    }
    private val digests = LinkedHashMap<String, String>()

    /**
     * Пишет копию целиком.
     *
     * @param content что человек согласился включить.
     * @param onProgress вызывается по ходу; вызывающий решает, как часто
     *   обновлять экран.
     */
    suspend fun write(
        source: BackupSource,
        manifest: BackupManifest,
        content: BackupContent = manifest.content,
        onProgress: (BackupProgress) -> Unit = {},
    ) {
        val counts = source.counts()
        entry(BackupFormat.MANIFEST) { it.append(manifest.toJson()) }

        if (content.profiles) {
            onProgress(BackupProgress(BackupStage.PROFILES, 0, null))
            val bundle = source.profiles()
            entry(BackupFormat.PROFILES) { out -> writeProfiles(out, bundle) }
        }
        if (content.settings) {
            onProgress(BackupProgress(BackupStage.SETTINGS, 0, null))
            source.settings()?.let { settings ->
                entry(BackupFormat.SETTINGS) { out -> writeSettings(out, settings) }
            }
        }
        if (content.sessions) {
            stream(
                BackupFormat.SESSIONS,
                BackupStage.SESSIONS,
                counts.sessions,
                source.sessions(),
                onProgress,
            ) { w, item -> item.write(w) }
        }
        if (content.measurements) {
            stream(
                BackupFormat.MEASUREMENTS,
                BackupStage.MEASUREMENTS,
                counts.measurements,
                source.measurements(),
                onProgress,
            ) { w, item -> item.write(w) }
            stream(
                BackupFormat.EVENTS,
                BackupStage.EVENTS,
                counts.events,
                source.events(),
                onProgress,
            ) { w, item -> item.write(w) }
            stream(
                BackupFormat.RARE,
                BackupStage.RARE,
                counts.rare,
                source.rare(),
                onProgress,
            ) { w, item -> item.write(w) }
            stream(
                BackupFormat.ENVIRONMENT,
                BackupStage.ENVIRONMENT,
                counts.environment,
                source.environment(),
                onProgress,
            ) { w, item -> item.write(w) }
        }
        if (content.routes) {
            stream(
                BackupFormat.ROUTES,
                BackupStage.ROUTES,
                counts.routes,
                source.routes(),
                onProgress,
            ) { w, item -> item.write(w) }
            stream(
                BackupFormat.POINTS,
                BackupStage.POINTS,
                counts.points,
                source.points(),
                onProgress,
            ) { w, item -> item.write(w) }
        }
        if (content.spectra) {
            stream(
                BackupFormat.SPECTRA,
                BackupStage.SPECTRA,
                counts.spectra,
                source.spectra(),
                onProgress,
            ) { w, item -> item.write(w) }
            // Станции пишутся ПОСЛЕ спектров: они ссылаются на снимок меткой
            // времени, и при восстановлении снимок должен лечь в базу раньше.
            stream(
                BackupFormat.STATIONS,
                BackupStage.STATIONS,
                counts.stations,
                source.stations(),
                onProgress,
            ) { w, item -> item.write(w) }
        }
        if (content.spectrogram) {
            stream(
                BackupFormat.SPECTROGRAM,
                BackupStage.SPECTROGRAM,
                counts.slices,
                source.slices(),
                onProgress,
            ) { w, item -> item.write(w) }
        }
        if (content.experiments) {
            stream(
                BackupFormat.EXPERIMENTS,
                BackupStage.EXPERIMENTS,
                counts.experiments,
                source.experiments(),
                onProgress,
            ) { w, item -> item.write(w) }
        }

        onProgress(BackupProgress(BackupStage.FINISHING, 0, null))
        entry(BackupFormat.CHECKSUMS) { it.append(BackupChecksums(digests.toMap()).toJson()) }
        zip.finish()
        zip.flush()
    }

    private fun writeProfiles(out: Appendable, bundle: BackupProfiles) {
        val w = Json.Writer(out)
        w.beginObject().name("profiles")
        w.beginArray()
        for (item in bundle.profiles) item.write(w)
        w.endArray().name("networks")
        w.beginArray()
        for (item in bundle.networks) item.write(w)
        w.endArray().name("epochs")
        w.beginArray()
        for (item in bundle.epochs) item.write(w)
        w.endArray().name("fingerprints")
        w.beginArray()
        for (item in bundle.fingerprints) item.write(w)
        w.endArray()
        w.endObject()
    }

    private fun writeSettings(out: Appendable, settings: List<Pair<String, String>>) {
        val w = Json.Writer(out)
        w.beginObject()
            .field("schemaVersion", SETTINGS_SCHEMA.toLong())
            .name("values")
        w.beginObject()
        for ((key, value) in settings) w.field(key, value)
        w.endObject()
        w.endObject()
    }

    private suspend fun <T> stream(
        name: String,
        stage: BackupStage,
        total: Long,
        source: BackupStream<T>,
        onProgress: (BackupProgress) -> Unit,
        write: (Json.Writer, T) -> Unit,
    ) {
        var cursor = 0L
        var done = 0L
        onProgress(BackupProgress(stage, 0, total))
        entry(name) { out ->
            while (true) {
                val page = source.page(cursor, pageSize)
                for (item in page.items) {
                    val line = StringBuilder()
                    write(Json.Writer(line), item)
                    out.append(line).append('\n')
                }
                done += page.items.size
                onProgress(BackupProgress(stage, done, total))
                cursor = page.nextCursor ?: break
                if (page.items.isEmpty()) break
            }
        }
    }

    /**
     * Одна запись архива. Содержимое пишется через [Appendable], который
     * складывает байты и в архив, и в счётчик контрольной суммы: второй проход
     * по уже записанному стоил бы времени и памяти на ровном месте.
     */
    private inline fun entry(name: String, body: (Appendable) -> Unit) {
        zip.putNextEntry(ZipEntry(name))
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = DigestingAppendable(zip, digest)
        body(sink)
        sink.flush()
        zip.closeEntry()
        digests[name] = digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Текст → UTF-8 → и в архив, и в контрольную сумму. */
    private class DigestingAppendable(
        private val out: OutputStream,
        private val digest: MessageDigest,
    ) : Appendable {

        private val buffer = StringBuilder(BUFFER_CHARS)

        override fun append(csq: CharSequence?): Appendable = apply {
            buffer.append(csq ?: "null")
            if (buffer.length >= BUFFER_CHARS) flush()
        }

        override fun append(csq: CharSequence?, start: Int, end: Int): Appendable =
            append(csq?.subSequence(start, end))

        override fun append(c: Char): Appendable = apply {
            buffer.append(c)
            if (buffer.length >= BUFFER_CHARS) flush()
        }

        fun flush() {
            if (buffer.isEmpty()) return
            val bytes = buffer.toString().toByteArray(Charsets.UTF_8)
            out.write(bytes)
            digest.update(bytes)
            buffer.setLength(0)
        }

        private companion object {
            const val BUFFER_CHARS = 64 * 1024
        }
    }

    companion object {

        /**
         * Записей за одно чтение базы.
         * **Инженерный параметр**: две тысячи строк — это около четверти
         * мегабайта текста: достаточно, чтобы запрос окупался, и достаточно
         * мало, чтобы память не росла.
         */
        const val DEFAULT_PAGE = 2_000

        /** Версия набора настроек; неизвестные ключи читатель пропускает. */
        const val SETTINGS_SCHEMA = 1
    }
}
