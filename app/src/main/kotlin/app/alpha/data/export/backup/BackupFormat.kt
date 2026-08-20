package app.alpha.data.export.backup

/**
 * Формат резервной копии `.radbackup` — версия 1.
 *
 * ## Почему не копия файла базы
 *
 * Двоичная копия Room-базы привязана к её схеме: восстановить её можно только
 * приложением с той же версией схемы. У копии своя версия формата,
 * независимая от версии базы: она описывает данные (измерения, профили,
 * маршруты, спектры, эксперименты, настройки), а не их раскладку по таблицам.
 *
 * ## Что внутри
 *
 * ZIP с явным манифестом. Крупные ряды — построчный JSON (NDJSON): запись
 * пишется и читается по одной строке, поэтому ни экспорт, ни восстановление
 * не держат историю в памяти целиком.
 *
 * ```text
 * manifest.json      — что это, какой версии и что внутри
 * settings.json      — настройки приложения
 * profiles.json      — профили мест, их сети, эпохи фона, отпечатки
 * sessions.ndjson    — сессии измерений
 * measurements.ndjson— посекундные измерения
 * events.ndjson      — события журнала
 * rare.ndjson        — редкие данные прибора (батарея, температура)
 * environment.ndjson — условия: давление, магнитное поле, температура телефона
 * routes.json        — маршруты
 * points.ndjson      — точки маршрутов
 * spectra.ndjson     — спектры (счётчики каналов — base64)
 * spectrogram.ndjson — срезы спектрограммы
 * experiments.ndjson — эксперименты и их прогоны
 * checksums.json     — SHA-256 каждой записи архива
 * ```
 *
 * ## Чего внутри нет
 *
 * Производных таблиц (минутные скаляры и почасовые скетчи ADR 004): они
 * пересчитываются из измерений. Кэша карт, журналов сбоев и системных
 * разрешений — тоже.
 */
object BackupFormat {

    /** Что это за файл — первое, что читает импорт. */
    const val FORMAT = "alpha-backup"

    /**
     * Как копия называлась до переименования приложения.
     *
     * Читается наравне с нынешним именем и будет читаться дальше: файл,
     * созданный прежней версией, — это чья-то история за год, и отказать ему
     * из-за строки в заголовке значит потерять её на ровном месте. Пишется
     * при этом только новое имя.
     */
    const val LEGACY_FORMAT = "radiacode-backup"

    /** Имена, которые импорт признаёт своими. */
    val KNOWN_FORMATS = setOf(FORMAT, LEGACY_FORMAT)

    /**
     * Версия формата. Растёт при изменении СМЫСЛА полей, не при добавлении
     * новых: неизвестное поле читатель обязан пропустить, а не спотыкаться.
     */
    const val VERSION = 1

    /** Расширение файла копии. */
    const val EXTENSION = "radbackup"

    /** MIME для системного диалога сохранения: у формата своего типа нет. */
    const val MIME = "application/octet-stream"

    const val MANIFEST = "manifest.json"
    const val CHECKSUMS = "checksums.json"
    const val SETTINGS = "settings.json"
    const val PROFILES = "profiles.json"
    const val SESSIONS = "sessions.ndjson"
    const val MEASUREMENTS = "measurements.ndjson"
    const val EVENTS = "events.ndjson"
    const val RARE = "rare.ndjson"
    const val ENVIRONMENT = "environment.ndjson"
    const val ROUTES = "routes.json"
    const val POINTS = "points.ndjson"
    const val SPECTRA = "spectra.ndjson"
    const val SPECTROGRAM = "spectrogram.ndjson"
    const val EXPERIMENTS = "experiments.ndjson"

    /** Записи, без которых архив не копия, а что-то другое. */
    val REQUIRED = listOf(MANIFEST, CHECKSUMS)

    /** Имя файла копии: дата в имени, чтобы копии различались в списке. */
    fun fileName(dateIso: String): String = "Alpha-backup-$dateIso.$EXTENSION"
}

/**
 * Манифест копии: что это, кто и когда её создал и что внутри.
 *
 * [formatVersion] — главное поле совместимости: копию более новой версии
 * приложение НЕ пытается угадать (см. `BackupReader`).
 */
data class BackupManifest(
    val format: String = BackupFormat.FORMAT,
    val formatVersion: Int = BackupFormat.VERSION,
    /** Момент создания, ISO-8601 с зоной. */
    val createdAt: String,
    val appVersion: String,
    /** Версия схемы базы на момент создания — для разбора, не для миграций. */
    val databaseSchemaVersion: Int,
    /** Модель прибора, если она известна приложению. */
    val deviceModel: String? = null,
    /**
     * Начало периода копии (мс), если выбрана не вся история. Стоит в
     * манифесте: копия за месяц и копия за всё время внешне одинаковы, и
     * читатель показывает период до восстановления.
     */
    val fromMillis: Long? = null,
    val content: BackupContent,
) {

    fun toJson(): String {
        val out = StringBuilder()
        val w = Json.Writer(out)
        w.beginObject()
            .field("format", format)
            .field("formatVersion", formatVersion.toLong())
            .field("createdAt", createdAt)
            .field("appVersion", appVersion)
            .field("databaseSchemaVersion", databaseSchemaVersion.toLong())
            .field("deviceModel", deviceModel)
            .field("fromMillis", fromMillis)
            .name("content")
        w.beginObject()
            .field("measurements", content.measurements)
            .field("sessions", content.sessions)
            .field("routes", content.routes)
            .field("spectra", content.spectra)
            .field("spectrogram", content.spectrogram)
            .field("experiments", content.experiments)
            .field("profiles", content.profiles)
            .field("settings", content.settings)
            .endObject()
        w.endObject()
        return out.toString()
    }

    companion object {

        fun parse(text: String): BackupManifest {
            val root = Json.parseObject(text)
            val content = root.obj("content")
            return BackupManifest(
                format = root.str("format") ?: "",
                formatVersion = root.int("formatVersion") ?: 0,
                createdAt = root.str("createdAt") ?: "",
                appVersion = root.str("appVersion") ?: "",
                databaseSchemaVersion = root.int("databaseSchemaVersion") ?: 0,
                deviceModel = root.str("deviceModel"),
                // Копии прежних версий поля не имеют — и это «вся история».
                fromMillis = root.long("fromMillis"),
                content = BackupContent(
                    measurements = content?.bool("measurements") ?: false,
                    sessions = content?.bool("sessions") ?: false,
                    routes = content?.bool("routes") ?: false,
                    spectra = content?.bool("spectra") ?: false,
                    spectrogram = content?.bool("spectrogram") ?: false,
                    experiments = content?.bool("experiments") ?: false,
                    profiles = content?.bool("profiles") ?: false,
                    settings = content?.bool("settings") ?: false,
                ),
            )
        }
    }
}

/** Состав копии: что в неё согласились положить и что в ней есть. */
data class BackupContent(
    val measurements: Boolean = true,
    val sessions: Boolean = true,
    val routes: Boolean = true,
    val spectra: Boolean = true,
    val spectrogram: Boolean = true,
    val experiments: Boolean = true,
    val profiles: Boolean = true,
    val settings: Boolean = true,
)

/**
 * Контрольные суммы записей архива. Проверяются до восстановления и все
 * сразу: частичное молчаливое восстановление испорченной копии хуже отказа.
 */
data class BackupChecksums(val sha256: Map<String, String>) {

    fun toJson(): String {
        val out = StringBuilder()
        val w = Json.Writer(out)
        w.beginObject().name("sha256")
        w.beginObject()
        for ((name, digest) in sha256.entries.sortedBy { it.key }) w.field(name, digest)
        w.endObject()
        w.endObject()
        return out.toString()
    }

    companion object {
        fun parse(text: String): BackupChecksums {
            val root = Json.parseObject(text)
            val map = root.obj("sha256")?.fields.orEmpty()
                .mapNotNull { (name, value) ->
                    (value as? Json.Value.Str)?.let { name to it.value }
                }
                .toMap()
            return BackupChecksums(map)
        }
    }
}
