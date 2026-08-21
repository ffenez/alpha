package app.alpha.data.export.backup

import java.util.Base64

/**
 * Записи резервной копии — то, что человек считает своими данными.
 *
 * ## Почему не сущности Room
 *
 * Копия обязана пережить перестройку таблиц: смысл «измерение в такой-то
 * момент с такой-то мощностью дозы» не меняется от того, как оно разложено по
 * колонкам. Поэтому у формата свои записи и свои имена полей, а превращение
 * «сущность ↔ запись» живёт на стороне приложения, где сущности и известны.
 *
 * ## Ключ совпадения
 *
 * У каждой записи есть [BackupKey] — по нему повторный импорт той же копии
 * ничего не удваивает. Ключ строится из ЕСТЕСТВЕННЫХ полей, а не из
 * идентификатора строки: идентификаторы у двух телефонов свои, а момент
 * измерения — общий. Отдельного UUID в базе нет намеренно: колонку, добавленную
 * сегодня, пришлось бы заполнять случайными значениями для всего уже
 * записанного, и совпадение старых данных с копиями, снятыми до неё, всё равно
 * не заработало бы.
 */
object BackupKey {

    /** Разделитель частей ключа; в данных он не встречается. */
    private const val SEP = ""

    fun of(vararg parts: Any?): String = parts.joinToString(SEP) { it?.toString() ?: "" }
}

/** Профиль места. */
data class BackupProfile(
    val name: String,
    val icon: String,
    val parentName: String?,
    val archived: Boolean,
    val autoActivate: Boolean,
    val baselineLearning: Boolean,
    val role: String,
    val baselineEpochMillis: Long?,
    val createdAt: Long,
) {
    val key: String get() = BackupKey.of(name, createdAt)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("name", name)
            .field("icon", icon)
            .field("parent", parentName)
            .field("archived", archived)
            .field("autoActivate", autoActivate)
            .field("baselineLearning", baselineLearning)
            .field("role", role)
            .field("baselineEpochMillis", baselineEpochMillis)
            .field("createdAt", createdAt)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupProfile(
            name = o.str("name") ?: "",
            icon = o.str("icon") ?: "",
            parentName = o.str("parent"),
            archived = o.bool("archived") ?: false,
            autoActivate = o.bool("autoActivate") ?: true,
            baselineLearning = o.bool("baselineLearning") ?: true,
            role = o.str("role") ?: "user",
            baselineEpochMillis = o.long("baselineEpochMillis"),
            createdAt = o.long("createdAt") ?: 0L,
        )
    }
}

/** Сеть, по которой профиль включается сам. */
data class BackupNetwork(
    val profileName: String,
    val networkHash: String,
    val label: String?,
    val createdAt: Long,
) {
    val key: String get() = BackupKey.of(networkHash)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("profile", profileName)
            .field("networkHash", networkHash)
            .field("label", label)
            .field("createdAt", createdAt)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupNetwork(
            profileName = o.str("profile") ?: "",
            networkHash = o.str("networkHash") ?: "",
            label = o.str("label"),
            createdAt = o.long("createdAt") ?: 0L,
        )
    }
}

/** Прошлый период обычного фона профиля. */
data class BackupEpoch(
    val profileName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val stats: String,
    val reason: String,
    val createdAt: Long,
) {
    val key: String get() = BackupKey.of(profileName, startedAtMillis, endedAtMillis)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("profile", profileName)
            .field("startedAt", startedAtMillis)
            .field("endedAt", endedAtMillis)
            .field("stats", stats)
            .field("reason", reason)
            .field("createdAt", createdAt)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupEpoch(
            profileName = o.str("profile") ?: "",
            startedAtMillis = o.long("startedAt") ?: 0L,
            endedAtMillis = o.long("endedAt") ?: 0L,
            stats = o.str("stats") ?: "",
            reason = o.str("reason") ?: "",
            createdAt = o.long("createdAt") ?: 0L,
        )
    }
}

/** Отпечаток места: замороженный снимок распределений и опорный спектр. */
data class BackupFingerprint(
    val profileName: String,
    val createdAt: Long,
    val accumulatedSeconds: Long,
    val sampleCount: Long,
    val doseLow: Float,
    val doseMedian: Float,
    val doseHigh: Float,
    val doseP25: Float,
    val doseP75: Float,
    val doseMad: Float,
    val cpsLow: Float,
    val cpsMedian: Float,
    val cpsHigh: Float,
    val spectrumSeconds: Long,
    val a0: Float,
    val a1: Float,
    val a2: Float,
    val channelCount: Int,
    val spectrumBase64: String,
    val origin: String,
    val algorithmVersion: Int,
) {
    val key: String get() = BackupKey.of(profileName, createdAt)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("profile", profileName)
            .field("createdAt", createdAt)
            .field("accumulatedSeconds", accumulatedSeconds)
            .field("sampleCount", sampleCount)
            .field("doseLow", doseLow)
            .field("doseMedian", doseMedian)
            .field("doseHigh", doseHigh)
            .field("doseP25", doseP25)
            .field("doseP75", doseP75)
            .field("doseMad", doseMad)
            .field("cpsLow", cpsLow)
            .field("cpsMedian", cpsMedian)
            .field("cpsHigh", cpsHigh)
            .field("spectrumSeconds", spectrumSeconds)
            .field("a0", a0)
            .field("a1", a1)
            .field("a2", a2)
            .field("channelCount", channelCount)
            .field("spectrum", spectrumBase64)
            .field("origin", origin)
            .field("algorithmVersion", algorithmVersion)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupFingerprint(
            profileName = o.str("profile") ?: "",
            createdAt = o.long("createdAt") ?: 0L,
            accumulatedSeconds = o.long("accumulatedSeconds") ?: 0L,
            sampleCount = o.long("sampleCount") ?: 0L,
            doseLow = o.float("doseLow") ?: 0f,
            doseMedian = o.float("doseMedian") ?: 0f,
            doseHigh = o.float("doseHigh") ?: 0f,
            doseP25 = o.float("doseP25") ?: 0f,
            doseP75 = o.float("doseP75") ?: 0f,
            doseMad = o.float("doseMad") ?: 0f,
            cpsLow = o.float("cpsLow") ?: 0f,
            cpsMedian = o.float("cpsMedian") ?: 0f,
            cpsHigh = o.float("cpsHigh") ?: 0f,
            spectrumSeconds = o.long("spectrumSeconds") ?: 0L,
            a0 = o.float("a0") ?: 0f,
            a1 = o.float("a1") ?: 0f,
            a2 = o.float("a2") ?: 0f,
            channelCount = o.int("channelCount") ?: 0,
            spectrumBase64 = o.str("spectrum") ?: "",
            origin = o.str("origin") ?: "auto",
            algorithmVersion = o.int("algorithmVersion") ?: 0,
        )
    }
}

/** Сессия измерений — отрезок времени, когда прибор был на связи. */
data class BackupSession(
    val startedAt: Long,
    val endedAt: Long?,
    val profileName: String?,
) {
    val key: String get() = BackupKey.of(startedAt)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("startedAt", startedAt)
            .field("endedAt", endedAt)
            .field("profile", profileName)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupSession(
            startedAt = o.long("startedAt") ?: 0L,
            endedAt = o.long("endedAt"),
            profileName = o.str("profile"),
        )
    }
}

/**
 * Одно измерение прибора.
 *
 * Значения хранятся В ЕДИНИЦАХ ПРИБОРА, как они пришли по BLE и лежат в базе:
 * копия обязана вернуть ровно то, что было записано, а не результат
 * преобразования. Перевод в мкЗв/ч — дело показа и экспорта данных.
 */
data class BackupMeasurement(
    val timestamp: Long,
    val doseRate: Float,
    val doseRateErr: Float,
    val countRate: Float,
    val countRateErr: Float,
    val flags: Int,
    val realTimeFlags: Int,
    val profileName: String?,
    val baselineExcluded: String?,
) {
    val key: String get() = BackupKey.of(timestamp)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("t", timestamp)
            .field("d", doseRate)
            .field("de", doseRateErr)
            .field("c", countRate)
            .field("ce", countRateErr)
            .field("f", flags)
            .field("rf", realTimeFlags)
            .field("p", profileName)
            .field("x", baselineExcluded)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupMeasurement(
            timestamp = o.long("t") ?: 0L,
            doseRate = o.float("d") ?: 0f,
            doseRateErr = o.float("de") ?: 0f,
            countRate = o.float("c") ?: 0f,
            countRateErr = o.float("ce") ?: 0f,
            flags = o.int("f") ?: 0,
            realTimeFlags = o.int("rf") ?: 0,
            profileName = o.str("p"),
            baselineExcluded = o.str("x"),
        )
    }
}

/** Событие журнала: отклонение, находка, событие прибора. */
data class BackupEvent(
    val timestamp: Long,
    val source: String,
    val code: Int,
    val name: String,
    val param1: Int,
    val flags: Int,
    val doseRate: Float?,
    val latitude: Double?,
    val longitude: Double?,
) {
    val key: String get() = BackupKey.of(timestamp, source, code, name)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("t", timestamp)
            .field("source", source)
            .field("code", code)
            .field("name", name)
            .field("param1", param1)
            .field("flags", flags)
            .field("dose", doseRate)
            .field("lat", latitude)
            .field("lon", longitude)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupEvent(
            timestamp = o.long("t") ?: 0L,
            source = o.str("source") ?: "",
            code = o.int("code") ?: 0,
            name = o.str("name") ?: "",
            param1 = o.int("param1") ?: 0,
            flags = o.int("flags") ?: 0,
            doseRate = o.float("dose"),
            latitude = o.double("lat"),
            longitude = o.double("lon"),
        )
    }
}

/**
 * Условия вокруг измерения. Поля необязательны: в телефоне может не быть
 * барометра, и старая копия этой записи не содержит вовсе.
 */
data class BackupEnvironment(
    val timestamp: Long,
    val pressureHpa: Float?,
    val magneticUt: Float?,
    val magneticSd: Float?,
    val phoneTempC: Float?,
    val samples: Int,
) {
    val key: String get() = BackupKey.of(timestamp)

    fun write(w: Json.Writer) {
        w.beginObject().field("t", timestamp)
        pressureHpa?.let { w.field("p", it) }
        magneticUt?.let { w.field("b", it) }
        magneticSd?.let { w.field("bsd", it) }
        phoneTempC?.let { w.field("phoneTemp", it) }
        w.field("n", samples).endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupEnvironment(
            timestamp = o.long("t") ?: 0L,
            pressureHpa = o.float("p"),
            magneticUt = o.float("b"),
            magneticSd = o.float("bsd"),
            phoneTempC = o.float("phoneTemp"),
            samples = o.int("n") ?: 0,
        )
    }
}

/**
 * Станция радиоэлементной съёмки.
 *
 * Ссылка на спектр хранится НЕ идентификатором, а меткой времени снимка:
 * идентификаторы при восстановлении новые, а метка та же. Станция, чей снимок
 * в копию не попал, при восстановлении пропускается — станция без спектра не
 * станция.
 */
data class BackupStation(
    val timestamp: Long,
    val spectrumTimestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val heightCm: Int?,
    val pressureHpa: Float?,
    val note: String?,
) {
    val key: String get() = BackupKey.of(timestamp, spectrumTimestamp)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("t", timestamp)
            .field("spectrum", spectrumTimestamp)
            .field("lat", latitude)
            .field("lon", longitude)
            .field("acc", accuracyMeters)
        heightCm?.let { w.field("height", it) }
        pressureHpa?.let { w.field("p", it) }
        note?.let { w.field("note", it) }
        w.endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupStation(
            timestamp = o.long("t") ?: 0L,
            spectrumTimestamp = o.long("spectrum") ?: 0L,
            latitude = o.double("lat") ?: 0.0,
            longitude = o.double("lon") ?: 0.0,
            accuracyMeters = o.float("acc") ?: 0f,
            heightCm = o.int("height"),
            pressureHpa = o.float("p"),
            note = o.str("note"),
        )
    }
}

/** Редкие данные прибора: доза, температура, батарея. */
data class BackupRare(
    val timestamp: Long,
    val dose: Float,
    val temperature: Float,
    val batteryPercent: Float,
    val durationSeconds: Long,
    val flags: Int,
) {
    val key: String get() = BackupKey.of(timestamp)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("t", timestamp)
            .field("dose", dose)
            .field("temp", temperature)
            .field("battery", batteryPercent)
            .field("duration", durationSeconds)
            .field("flags", flags)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupRare(
            timestamp = o.long("t") ?: 0L,
            dose = o.float("dose") ?: 0f,
            temperature = o.float("temp") ?: 0f,
            batteryPercent = o.float("battery") ?: 0f,
            durationSeconds = o.long("duration") ?: 0L,
            flags = o.int("flags") ?: 0,
        )
    }
}

/** Записанный маршрут. */
data class BackupRoute(
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceMeters: Double?,
    val interrupted: Boolean,
) {
    val key: String get() = BackupKey.of(startedAt, name)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("name", name)
            .field("startedAt", startedAt)
            .field("endedAt", endedAt)
            .field("distance", distanceMeters)
            .field("interrupted", interrupted)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupRoute(
            name = o.str("name") ?: "",
            startedAt = o.long("startedAt") ?: 0L,
            endedAt = o.long("endedAt"),
            distanceMeters = o.double("distance"),
            interrupted = o.bool("interrupted") ?: false,
        )
    }
}

/** Точка маршрута; принадлежность маршруту — по его ключу. */
data class BackupPoint(
    val routeKey: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val doseRate: Float?,
    val countRate: Float?,
    val altitudeMeters: Double?,
) {
    val key: String get() = BackupKey.of(routeKey, timestamp)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("route", routeKey)
            .field("t", timestamp)
            .field("lat", latitude)
            .field("lon", longitude)
            .field("acc", accuracyMeters)
            .field("d", doseRate)
            .field("c", countRate)
            .field("alt", altitudeMeters)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupPoint(
            routeKey = o.str("route") ?: "",
            timestamp = o.long("t") ?: 0L,
            latitude = o.double("lat") ?: 0.0,
            longitude = o.double("lon") ?: 0.0,
            accuracyMeters = o.float("acc") ?: 0f,
            doseRate = o.float("d"),
            countRate = o.float("c"),
            altitudeMeters = o.double("alt"),
        )
    }
}

/**
 * Спектр. Счётчики каналов — base64 того же двоичного вида, в котором они
 * лежат в базе: перекодировать миллионы отсчётов в текст значило бы раздуть
 * копию и потерять точность на ровном месте.
 */
data class BackupSpectrum(
    val timestamp: Long,
    val accumulated: Boolean,
    val isBackgroundReference: Boolean,
    val origin: String,
    val label: String?,
    val analysisMeta: String?,
    val durationSeconds: Long,
    val a0: Float,
    val a1: Float,
    val a2: Float,
    val channelCount: Int,
    val countsBase64: String,
    val deviceSerial: String?,
    val firmware: String?,
    val epochId: Long?,
    val trigger: String?,
) {
    val key: String get() = BackupKey.of(timestamp, durationSeconds, channelCount, deviceSerial)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("t", timestamp)
            .field("accumulated", accumulated)
            .field("background", isBackgroundReference)
            .field("origin", origin)
            .field("label", label)
            .field("meta", analysisMeta)
            .field("duration", durationSeconds)
            .field("a0", a0)
            .field("a1", a1)
            .field("a2", a2)
            .field("channels", channelCount)
            .field("counts", countsBase64)
            .field("serial", deviceSerial)
            .field("firmware", firmware)
            .field("epoch", epochId)
            .field("trigger", trigger)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupSpectrum(
            timestamp = o.long("t") ?: 0L,
            accumulated = o.bool("accumulated") ?: false,
            isBackgroundReference = o.bool("background") ?: false,
            origin = o.str("origin") ?: "auto",
            label = o.str("label"),
            analysisMeta = o.str("meta"),
            durationSeconds = o.long("duration") ?: 0L,
            a0 = o.float("a0") ?: 0f,
            a1 = o.float("a1") ?: 0f,
            a2 = o.float("a2") ?: 0f,
            channelCount = o.int("channels") ?: 0,
            countsBase64 = o.str("counts") ?: "",
            deviceSerial = o.str("serial"),
            firmware = o.str("firmware"),
            epochId = o.long("epoch"),
            trigger = o.str("trigger"),
        )
    }
}

/**
 * Шаблон спектра для полноспектрального разложения.
 *
 * Счёт по каналам — base64 того же двоичного вида (i32 LE), что и у снимка:
 * шаблон набирается часами, и пересчёт в текст стоил бы размера копии на ровном
 * месте. [resolution662] и серийник переносятся как есть: без них шаблон нельзя
 * ни применить к своему прибору, ни привести уширением к чужому.
 */
data class BackupTemplate(
    val name: String,
    val createdAt: Long,
    val deviceSerial: String?,
    val deviceName: String?,
    val a0: Float,
    val a1: Float,
    val a2: Float,
    val durationSeconds: Long,
    val resolution662: Float,
    val channelCount: Int,
    val countsBase64: String,
    val source: String,
    val note: String?,
) {
    val key: String get() = BackupKey.of(name, createdAt)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("name", name)
            .field("createdAt", createdAt)
            .field("serial", deviceSerial)
            .field("device", deviceName)
            .field("a0", a0)
            .field("a1", a1)
            .field("a2", a2)
            .field("duration", durationSeconds)
            .field("resolution662", resolution662)
            .field("channels", channelCount)
            .field("counts", countsBase64)
            .field("source", source)
            .field("note", note)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupTemplate(
            name = o.str("name") ?: "",
            createdAt = o.long("createdAt") ?: 0L,
            deviceSerial = o.str("serial"),
            deviceName = o.str("device"),
            a0 = o.float("a0") ?: 0f,
            a1 = o.float("a1") ?: 0f,
            a2 = o.float("a2") ?: 0f,
            durationSeconds = o.long("duration") ?: 0L,
            resolution662 = o.float("resolution662") ?: 0f,
            channelCount = o.int("channels") ?: 0,
            countsBase64 = o.str("counts") ?: "",
            source = o.str("source") ?: "imported",
            note = o.str("note"),
        )
    }
}

/** Срез спектрограммы. */
data class BackupSlice(
    val startMillis: Long,
    val endMillis: Long,
    val durationMillis: Long,
    val schemeId: String,
    val bandCount: Int,
    val countsBase64: String,
    val cps: Float?,
    val doseMicroSvH: Float?,
    val sliceCount: Int,
) {
    val key: String get() = BackupKey.of(startMillis)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("start", startMillis)
            .field("end", endMillis)
            .field("duration", durationMillis)
            .field("scheme", schemeId)
            .field("bands", bandCount)
            .field("counts", countsBase64)
            .field("cps", cps)
            .field("dose", doseMicroSvH)
            .field("slices", sliceCount)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupSlice(
            startMillis = o.long("start") ?: 0L,
            endMillis = o.long("end") ?: 0L,
            durationMillis = o.long("duration") ?: 0L,
            schemeId = o.str("scheme") ?: "",
            bandCount = o.int("bands") ?: 0,
            countsBase64 = o.str("counts") ?: "",
            cps = o.float("cps"),
            doseMicroSvH = o.float("dose"),
            sliceCount = o.int("slices") ?: 0,
        )
    }
}

/** Эксперимент A/B и его прогоны. */
data class BackupExperiment(
    val kind: String,
    val profileName: String?,
    val createdAt: Long,
    val note: String,
    val geometry: String,
    val distanceCm: Int?,
    val placement: String,
    val orientation: String,
    val plannedSeconds: Long,
    val algorithmVersion: Int,
    val params: String,
    val runs: List<BackupRun>,
) {
    val key: String get() = BackupKey.of(createdAt, kind)

    fun write(w: Json.Writer) {
        w.beginObject()
            .field("kind", kind)
            .field("profile", profileName)
            .field("createdAt", createdAt)
            .field("note", note)
            .field("geometry", geometry)
            .field("distanceCm", distanceCm)
            .field("placement", placement)
            .field("orientation", orientation)
            .field("plannedSeconds", plannedSeconds)
            .field("algorithmVersion", algorithmVersion)
            .field("params", params)
            .name("runs")
        w.beginArray()
        for (run in runs) run.write(w)
        w.endArray()
        w.endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupExperiment(
            kind = o.str("kind") ?: "",
            profileName = o.str("profile"),
            createdAt = o.long("createdAt") ?: 0L,
            note = o.str("note") ?: "",
            geometry = o.str("geometry") ?: "",
            distanceCm = o.int("distanceCm"),
            placement = o.str("placement") ?: "",
            orientation = o.str("orientation") ?: "",
            plannedSeconds = o.long("plannedSeconds") ?: 0L,
            algorithmVersion = o.int("algorithmVersion") ?: 0,
            params = o.str("params") ?: "",
            runs = (o.fields["runs"] as? Json.Value.Arr)?.items
                ?.filterIsInstance<Json.Value.Obj>()
                ?.map { BackupRun.parse(it) }
                .orEmpty(),
        )
    }
}

/** Прогон эксперимента; связь со спектром — по ключу спектра. */
data class BackupRun(
    val label: String,
    val startedAt: Long,
    val endedAt: Long?,
    val spectrumKey: String?,
    val doseStats: String,
    val distanceCm: Float?,
    val shieldingNote: String?,
) {
    fun write(w: Json.Writer) {
        w.beginObject()
            .field("label", label)
            .field("startedAt", startedAt)
            .field("endedAt", endedAt)
            .field("spectrum", spectrumKey)
            .field("doseStats", doseStats)
            .field("distanceCm", distanceCm)
            .field("shielding", shieldingNote)
            .endObject()
    }

    companion object {
        fun parse(o: Json.Value.Obj) = BackupRun(
            label = o.str("label") ?: "",
            startedAt = o.long("startedAt") ?: 0L,
            endedAt = o.long("endedAt"),
            spectrumKey = o.str("spectrum"),
            doseStats = o.str("doseStats") ?: "",
            distanceCm = o.float("distanceCm"),
            shieldingNote = o.str("shielding"),
        )
    }
}

/** Двоичные данные в копии — base64: JSON двоичного вида не знает. */
object BackupBinary {
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(text: String): ByteArray = runCatching { Base64.getDecoder().decode(text) }
        .getOrElse { ByteArray(0) }
}
