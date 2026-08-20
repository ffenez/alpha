package app.alpha.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One real-time measurement (~1 Hz). Values are stored raw as decoded from
 * DATA_BUF (doseRate in device units, countRate in cps, errors in percent) —
 * unit conversion happens at display time only.
 *
 * `timestamp` is unique so that overlapping DATA_BUF reads after a reconnect
 * deduplicate on insert (OnConflictStrategy.IGNORE).
 */
@Entity(
    tableName = "samples",
    indices = [
        Index(value = ["timestamp"], unique = true),
        Index(value = ["placeId", "timestamp"]),
    ],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis (device base time + record offset). */
    val timestamp: Long,
    val doseRate: Float,
    val doseRateErr: Float,
    val countRate: Float,
    val countRateErr: Float,
    val flags: Int,
    val realTimeFlags: Int,
    /**
     * Measurement profile active when the sample was recorded; null for
     * samples measured before profiles existed or with no profile selected.
     * Deleting a profile detaches its samples (sets null) instead of deleting
     * measurements.
     *
     * The **column** is still called `placeId` (v2 name). Renaming it would
     * require a full table rebuild: SQLite gained `ALTER TABLE … RENAME
     * COLUMN` only in 3.25 (Android API 30) and this app runs from API 26, so
     * the portable path is copying every row — millions of them after a month
     * of 1 Hz recording. The mapping stays in the entity instead; see
     * MigrationSql.FROM_5_TO_6.
     */
    @ColumnInfo(name = "placeId")
    val profileId: Long? = null,
    /**
     * Baseline admission verdict at write time: null = admitted into baseline
     * statistics, otherwise [app.alpha.baseline.BaselineExclusion.storageKey]
     * of the first unmet condition (spec §4.2). Raw values are stored either
     * way — exclusion only keeps an anomaly from becoming «the new normal».
     */
    val baselineExcluded: String? = null,
)

/** Battery / temperature / accumulated dose status (every few minutes). */
@Entity(
    tableName = "rare_data",
    indices = [Index(value = ["timestamp"], unique = true)],
)
data class RareDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Accumulated dose, raw device units. */
    val dose: Float,
    /** Degrees Celsius. */
    val temperature: Float,
    /** Battery charge, percent 0..100. */
    val batteryPercent: Float,
    /** Measurement duration reported by the device, seconds. */
    val durationSeconds: Long,
    val flags: Int,
)

/**
 * Условия вокруг измерения: давление, магнитное поле и температура телефона.
 *
 * Пишется НЕ потоком датчика, а сводкой за окно (`samples` отсчётов): поток
 * магнитометра идёт десятками герц, и хранить его целиком — гигабайты ради
 * данных, которые всё равно смотрят усреднёнными. Разброс поля за окно нужен
 * не для красоты: он отличает настоящую аномалию от рывка рукой.
 *
 * Любое поле null, если датчика в телефоне нет: барометр есть не везде, и
 * пустая строка честнее нуля.
 */
@Entity(
    tableName = "environment",
    indices = [Index(value = ["timestamp"], unique = true)],
)
data class EnvironmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Конец окна усреднения, epoch millis. */
    val timestamp: Long,
    /** Атмосферное давление, гПа. 1 гПа ≈ 8 м высоты И погода одновременно. */
    val pressureHpa: Float? = null,
    /** Модуль магнитной индукции, мкТл: не зависит от поворота телефона. */
    val magneticUt: Float? = null,
    /** Разброс модуля за окно (SD), мкТл. */
    val magneticSd: Float? = null,
    /**
     * Температура БАТАРЕИ телефона, °C. Воздух ею мерить нельзя — это железка,
     * нагретая собственной электроникой; она годится только как вторая точка
     * для дрейфа относительно прибора.
     */
    val phoneTempC: Float? = null,
    /** Сколько отсчётов усреднено; 0 не пишется вовсе. */
    val samples: Int = 0,
)

/** Device-originated events and app-detected hotspots, one journal. */
@Entity(
    tableName = "events",
    indices = [Index("timestamp")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** [SOURCE_DEVICE] or [SOURCE_HOTSPOT]. */
    val source: String,
    /** Device event code ([app.alpha.protocol.EventId]) or 0 for hotspots. */
    val code: Int,
    val name: String,
    val param1: Int,
    val flags: Int,
    /** Dose rate at the moment of the event, raw device units (hotspots). */
    val doseRate: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /**
     * Конец эпизода, мс эпохи. null у точечных записей (событие прибора,
     * снимок) и у эпизода, который ИДЁТ прямо сейчас — их различает
     * [sampleCount]: у эпизода он не null.
     */
    val endTimestamp: Long? = null,
    /** Минимум за эпизод, мкЗв/ч. */
    val minMicroSvH: Float? = null,
    /** Максимум за эпизод, мкЗв/ч. */
    val maxMicroSvH: Float? = null,
    /** Среднее за эпизод, мкЗв/ч — представительное значение. */
    val meanMicroSvH: Float? = null,
    /** Сколько отсчётов вошло в эпизод; null — запись не интервальная. */
    val sampleCount: Int? = null,
    /** Назначенный порог L1 на момент эпизода, мкЗв/ч. */
    val thresholdMicroSvH: Float? = null,
) {

    /** Идёт ли эпизод: интервальная запись без конца. */
    val ongoing: Boolean get() = sampleCount != null && endTimestamp == null

    companion object {
        const val SOURCE_DEVICE = "device"

        /**
         * Track hotspot (threshold crossing while recording). Carries lat/lon;
         * [param1] stores the baseline typical high in nSv/h at event time
         * (0 = no baseline), same convention as [SOURCE_DEVIATION].
         */
        const val SOURCE_HOTSPOT = "hotspot"

        /**
         * Persistent baseline deviation confirmed by the alarm engine.
         * [doseRate] holds the raw dose rate; [param1] stores the baseline
         * typical high at that moment in nSv/h (µSv/h × 1000, 0 = no baseline)
         * so History can honestly say «обычно здесь X» as of the event time.
         */
        const val SOURCE_DEVIATION = "deviation"

        /** User saved a spectrum snapshot ([param1] = accumulation seconds). */
        const val SOURCE_SPECTRUM = "spectrum"

        /**
         * Подтверждённое изменение уровня — ИНТЕРВАЛ, а не точка
         * ([app.alpha.baseline.LevelEventKind.LEVEL_CHANGE]). Значение вышло
         * за обычное для места, назначенного порога не достигнув.
         */
        const val SOURCE_LEVEL_CHANGE = "level_change"

        /**
         * Превышение назначенного порога — тоже интервал
         * ([app.alpha.baseline.LevelEventKind.THRESHOLD]).
         */
        const val SOURCE_THRESHOLD = "threshold"

        /** Интервальные виды событий: у них есть начало, конец и пределы. */
        val EPISODE_SOURCES = listOf(SOURCE_LEVEL_CHANGE, SOURCE_THRESHOLD)
    }
}

/**
 * Measurement profile / context (spec §3): a user-named environment with its
 * own history and statistical baseline. Replaces the v2–v5 «place»: `Дом` is
 * not a special GPS mode, just a profile that may carry automatic activation
 * rules.
 *
 * Nesting is one level deep («Дом / Спальня»): [parentId] points at a root
 * profile and a profile that already has children can never become a child
 * itself (see `ui/logic/ProfileTree`). Deeper trees buy nothing here and make
 * the picker unreadable on a phone.
 */
@Entity(
    tableName = "profiles",
    indices = [Index("parentId")],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Single glyph shown before the name; empty = none. */
    val icon: String = "",
    /** Parent profile for nesting; null = root profile. */
    val parentId: Long? = null,
    /** Archived profiles stay in history but leave the picker. */
    val archived: Boolean = false,
    /** Wi-Fi bindings of this profile may activate it automatically. */
    val autoActivate: Boolean = true,
    /** Condition 1 of the baseline admission pipeline (spec §4.2). */
    val baselineLearning: Boolean = true,
    /** [ROLE_USER], [ROLE_TRANSIT] or [ROLE_NO_PLACE]. */
    val role: String = ROLE_USER,
    /**
     * Earliest instant this profile's baseline statistics may look at; null =
     * the whole sliding window (why-spec §7).
     *
     * It moves only when the **user** confirms that the place itself changed.
     * Raw measurements before it are never touched — they simply stop feeding
     * the historical range, and the period they described is kept in
     * [BaselineEpochEntity].
     */
    val baselineEpochMillis: Long? = null,
    /** When «оставить как есть» was last chosen, so the offer stops nagging. */
    val shiftDeclinedAtMillis: Long? = null,
    val createdAt: Long,
) {
    companion object {
        /** Ordinary user profile. */
        const val ROLE_USER = "user"

        /** Activated when no known network is around after the grace period. */
        const val ROLE_TRANSIT = "transit"

        /** Activated when the context cannot be determined at all. */
        const val ROLE_NO_PLACE = "no_place"
    }
}

/**
 * A Wi-Fi network bound to a profile, identified **without** the location
 * permission (spec §3.2, CLAUDE.md privacy invariant).
 *
 * [networkHash] is a local one-way hash of the network's gateway/DHCP-server
 * address taken from `LinkProperties` (see
 * [app.alpha.context.NetworkIdentity]) — not an SSID and not a BSSID.
 * [label] holds a human-readable SSID only when the user has granted fine
 * location and only for display; the binding never depends on it.
 */
@Entity(
    tableName = "profile_networks",
    indices = [
        Index(value = ["networkHash"], unique = true),
        Index("profileId"),
    ],
)
data class ProfileNetworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val networkHash: String,
    val label: String? = null,
    val createdAt: Long,
)

/**
 * A measurement session: one continuous connected period of the measurement
 * service (opened on device connect, closed on disconnect/stop). Sessions
 * carry no measurements themselves — summaries aggregate `samples` by the
 * [startedAt, endedAt] range, so the raw data stays single-sourced.
 */
@Entity(
    tableName = "measurement_sessions",
    indices = [Index("startedAt")],
)
data class MeasurementSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Profile active at session start; null = no profile selected. The user
     * can correct it afterwards from История (spec §20). Column name kept from
     * v2 for the same reason as [SampleEntity.profileId].
     */
    @ColumnInfo(name = "placeId")
    val profileId: Long?,
    val startedAt: Long,
    /** Null while the session is still running. */
    val endedAt: Long? = null,
)

/** A recorded track (GPS walk with the dosimeter). */
@Entity(tableName = "track_sessions")
data class TrackSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Название маршрута; пустое — имя не задано, список подписывает датой. */
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    /**
     * Пройденное расстояние, м. Считается один раз по завершении записи по
     * полному списку точек. Null — ещё не посчитано (прежняя версия или
     * идущая запись).
     */
    val distanceMeters: Double? = null,
    /**
     * Запись оборвалась, а не была остановлена командой (процесс убит,
     * телефон выключен). Такой маршрут закрывается по последней точке при
     * следующем запуске и помечается прерванным.
     */
    val interrupted: Boolean = false,
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TrackSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "timestamp"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    /** Latest dose rate at this point, raw device units; null if not yet received. */
    val doseRate: Float?,
    val countRate: Float?,
    /**
     * GPS (ellipsoid) altitude, meters; null when the fix carries none or the
     * point predates v5. Feeds flight detection (sustained >3000 м) and the
     * dose-vs-altitude view of flight sessions.
     */
    val altitudeMeters: Double? = null,
    /**
     * Модуль магнитного поля в этой точке, мкТл; null — магнитометра нет или
     * точка снята до v20. Хранится В ТОЧКЕ, а не сшивается по времени со
     * сводками условий: слой карты рисуется одним запросом по сетке, и джойн
     * по времени на каждой перерисовке стоил бы дороже одной колонки.
     */
    val magneticUt: Float? = null,
)

/** A saved 1024-channel spectrum with its energy calibration. */
@Entity(
    tableName = "spectra",
    indices = [Index("timestamp")],
)
data class SpectrumSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** true = accumulated (lifetime) spectrum, false = current since last reset. */
    val accumulated: Boolean,
    /**
     * true = the user recorded this snapshot as the background reference for
     * overlay/subtraction on the Спектр screen. The newest flagged row wins;
     * older references stay as ordinary history snapshots.
     */
    val isBackgroundReference: Boolean = false,
    /**
     * How the row appeared: [ORIGIN_AUTO] (periodic autosave), [ORIGIN_USER]
     * (explicit «Сохранить»/«Записать фон»/comparator result) or
     * [ORIGIN_IMPORT] (RC-XML file). History lists user+import rows; imported
     * rows are excluded from device-data queries (latest spectrum, background
     * reference, session badges) so foreign files never mix into device data.
     * Rows saved before v4 are all 'auto' — they were indistinguishable.
     */
    @ColumnInfo(defaultValue = ORIGIN_AUTO)
    val origin: String = ORIGIN_AUTO,
    /** Display name: RC-XML sample name for imports, user label otherwise. */
    val label: String? = null,
    /**
     * Профиль, при котором снят снимок, и его имя НА МОМЕНТ СЪЁМКИ: профиль
     * можно переименовать, а снимок относится к прежнему имени. Экран
     * показывает нынешнее имя, прежнее восстановимо.
     *
     * NULL у снимков до появления колонок и у импортированных файлов.
     */
    @ColumnInfo(defaultValue = "NULL")
    val profileId: Long? = null,
    @ColumnInfo(defaultValue = "NULL")
    val profileName: String? = null,
    /**
     * Reproducibility stamp of a *derived* snapshot (spec §22): flat JSON
     * ([app.alpha.data.JsonMap]) naming the method that produced these
     * counts and its parameters — e.g. `method=interval_subtraction`,
     * `algorithmVersion`, the sources it came from. NULL for spectra that came
     * straight off the device or из файла: nothing was computed, so there is
     * nothing to reproduce.
     */
    val analysisMeta: String? = null,
    val durationSeconds: Long,
    val a0: Float,
    val a1: Float,
    val a2: Float,
    val channelCount: Int,
    /** Channel counts encoded as i32 LE array, see [app.alpha.data.SpectrumBlob]. */
    val counts: ByteArray,
    /**
     * Провенанс (ADR 008): серийный номер прибора и прошивка. Null у строк
     * прежних версий и у импорта; задним числом не восстанавливается.
     * Вычитать снимки без провенанса нельзя.
     */
    val deviceSerial: String? = null,
    val firmware: String? = null,
    /**
     * Эпоха накопления: непрерывный отрезок между сбросами спектра.
     *
     * Разность двух снимков имеет смысл только внутри одной эпохи — иначе
     * вычитание перескакивает через сброс и даёт отрицательные каналы или, что
     * хуже, правдоподобную чепуху.
     */
    val epochId: Long? = null,
    /** Чем вызван снимок: [TRIGGER_PERIODIC], [TRIGGER_MANUAL] и т. д. */
    val trigger: String? = null,
) {
    // ByteArray needs manual equality; identity by id is enough for entities.
    override fun equals(other: Any?): Boolean = other is SpectrumSnapshotEntity && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val ORIGIN_AUTO = "auto"
        const val ORIGIN_USER = "user"
        const val ORIGIN_IMPORT = "import"

        /**
         * Computed by the app for another entity that owns it (an A/B run's
         * interval spectrum). Such rows carry [analysisMeta], never appear in
         * the История list, and are excluded from device-data queries — they
         * are not a snapshot of the device state at their timestamp, so a
         * trend built from consecutive device snapshots must not see them.
         */
        const val ORIGIN_DERIVED = "derived"

        // Чем вызван снимок (ADR 008). Это НЕ то же, что `origin`: origin
        // отвечает «кто его сделал», trigger — «по какому поводу».
        const val TRIGGER_PERIODIC = "periodic"
        const val TRIGGER_MANUAL = "manual"
        const val TRIGGER_BACKGROUND = "background"
        const val TRIGGER_EXPERIMENT = "experiment"
        const val TRIGGER_FOOD = "food"
    }
}

/**
 * One A/B research experiment (spec §9, §16): a named protocol with a fixed,
 * user-described geometry and two or more comparable runs.
 *
 * The experiment row carries everything needed to reproduce the conclusion
 * later (spec §22): the geometry as the user described it, the algorithm
 * version that produced the verdicts, and the analysis parameters (energy
 * windows, thresholds) as flat JSON.
 */
@Entity(
    tableName = "experiments",
    indices = [Index("createdAt")],
)
data class ExperimentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [KIND_BACKGROUND_VS_OBJECT], [KIND_PLACE_VS_PLACE], [KIND_DISTANCE], [KIND_SHIELDING]. */
    val kind: String,
    /** Profile active when the experiment was created; null = none selected. */
    val profileId: Long? = null,
    val createdAt: Long,
    /** Free-form note by the user. */
    val note: String = "",
    /**
     * Geometry as one human sentence, shown again during every later run so
     * «одинаково документированная геометрия» (spec §16) is something the user
     * can actually reproduce. The app cannot verify it.
     *
     * Для новых опытов — изложение полей ниже, собранное при создании; у
     * прежних опытов — текст, написанный человеком.
     */
    val geometry: String = "",
    /**
     * Расстояние до объекта, см; null — не задано. Разложенные условия
     * показываются перед каждым следующим прогоном.
     */
    val distanceCm: Int? = null,
    /** Код положения прибора (`table`, `hand`, …); «» — не задано. */
    val placement: String = "",
    /** Код ориентации прибора (`screen_up`, …); «» — не задано. */
    val orientation: String = "",
    /** Плановая длительность прогона, с; 0 — не задана. */
    val plannedSeconds: Long = 0,
    /** [app.alpha.analysis.AlgorithmVersions.AB_ANALYSIS] at creation time. */
    val algorithmVersion: Int,
    /** Analysis parameters as flat JSON ([app.alpha.data.JsonMap]). */
    val params: String = "",
    /**
     * Фото образца — ссылка на снимок в галерее (content URI), а не копия
     * внутри приложения. Null — фото не выбирали.
     */
    val photoUri: String? = null,
) {
    companion object {
        /** Фон vs объект: run A = object, run B = background, same geometry. */
        const val KIND_BACKGROUND_VS_OBJECT = "background_vs_object"

        /** Место vs место: two environments compared as measured. */
        const val KIND_PLACE_VS_PLACE = "place_vs_place"

        /** Серия измерений на известных расстояниях (spec §16 Distance). */
        const val KIND_DISTANCE = "distance"

        /** Одна конфигурация без/с материалом (spec §16 Shielding). */
        const val KIND_SHIELDING = "shielding"

        /** Свои условия: что такое A и B, задаётся при создании опыта. */
        const val KIND_CUSTOM = "custom"

        /**
         * Скрининг продукта: прогон «Фон» и прогон «Продукт» в одной
         * геометрии. Отдельной сущности у него нет намеренно — это тот же
         * опыт с двумя прогонами, и анализ у него тот же (`AbAnalysis`).
         */
        const val KIND_FOOD = "food"

        val KINDS = listOf(
            KIND_BACKGROUND_VS_OBJECT,
            KIND_PLACE_VS_PLACE,
            KIND_DISTANCE,
            KIND_SHIELDING,
            KIND_CUSTOM,
            KIND_FOOD,
        )
    }
}

/**
 * One run of an experiment: a bracketed measurement interval with its own
 * spectrum and dose statistics. Runs of one experiment are compared to each
 * other, so the live time of every run is stored explicitly
 * ([startedAt], [endedAt]) instead of being inferred.
 */
@Entity(
    tableName = "experiment_runs",
    foreignKeys = [
        ForeignKey(
            entity = ExperimentEntity::class,
            parentColumns = ["id"],
            childColumns = ["experimentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["experimentId", "startedAt"])],
)
data class ExperimentRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val experimentId: Long,
    /** «A», «B», «C»… — the label the UI and the report use. */
    val label: String,
    val startedAt: Long,
    /** Null while the run is still recording. */
    val endedAt: Long? = null,
    /**
     * Spectrum accumulated *during this run only* (later snapshot minus the
     * one taken at the start, see
     * [app.alpha.analysis.SpectrumCompare.extractInterval]); null when the
     * device gave no spectrum for the interval.
     */
    val spectrumId: Long? = null,
    /** Dose-rate statistics over the run as flat JSON ([app.alpha.data.JsonMap]). */
    val doseStats: String = "",
    /** Distance scenario: distance to the object, cm. */
    val distanceCm: Float? = null,
    /** Shielding scenario: what was between the object and the detector. */
    val shieldingNote: String? = null,
)

/**
 * One closed minute of measurement, reduced to **scalars only** (ADR 004,
 * CHART SPEC §31). Values are in raw device units, exactly as in
 * [SampleEntity.doseRate] — raw data stay the source of truth and the
 * conversion to µSv/h happens on display.
 *
 * Why scalars and no per-minute quantile sketch: a minute holds 60 samples
 * (240 bytes); a serialized sketch of usable accuracy is around a kilobyte, so
 * per-minute sketches would cost *more* than the raw data they summarize and
 * still could not be combined into long-window quantiles any better than the
 * hourly ones (CHART SPEC §28 forbids quantiles-of-quantiles either way).
 * What these scalars do give, at ~100 bytes per minute, is an exact
 * n/Σx/Σx²/min/max rollup of any window without reading a single raw row, plus
 * the extremum timestamps a short transient needs to stay discoverable (§21).
 *
 * Rebuildable at any time from `samples`: every row is computed from raw and
 * written with REPLACE, so re-running the aggregation is a no-op and a
 * mid-minute restart cannot double-count (ADR 004).
 */
@Entity(
    tableName = "minute_stats",
    indices = [Index("profileId")],
)
data class MinuteStatEntity(
    /** Epoch millis of the minute boundary, `timestamp / 60000 * 60000`. */
    @PrimaryKey val minuteStart: Long,
    /** Raw samples inside the minute — the honest n. */
    val count: Int,
    val minDoseRate: Float,
    val maxDoseRate: Float,
    /** Σx over the raw samples (pooled mean of any range stays exact). */
    val sumDoseRate: Double,
    /** Σx² over the raw samples (pooled SD of any range stays exact). */
    val sumSqDoseRate: Double,
    /** Exact instant of [minDoseRate] — a timestamp, not an interval. */
    val minAtMillis: Long,
    /** Exact instant of [maxDoseRate] — a timestamp, not an interval. */
    val maxAtMillis: Long,
    val firstSampleTime: Long,
    val lastSampleTime: Long,
    /** Samples with `baselineExcluded IS NULL`, i.e. admitted to the baseline. */
    val admittedCount: Int,
    /**
     * Profile the minute belongs to, or null when the samples of this minute
     * belonged to **different** profiles (or to none). A minute in which the
     * context switched is attributed to nobody on purpose: giving it to one of
     * the two would feed a place's statistics with another place's readings.
     */
    val profileId: Long?,
)

/**
 * One closed hour as a mergeable quantile sketch (ADR 004, CHART SPEC §30) —
 * the long-window quantile path.
 *
 * The blob is an [app.alpha.analysis.quantiles.KllSketch] in raw device
 * units. Merging the sketches of the hours a chart column covers gives that
 * column's Q10/Q25/Q50/Q75/Q90 with a bounded, documented error, instead of
 * the forbidden «quantiles of quantiles» (§28).
 *
 * The scalars beside the blob are **exact**: [count] is the true number of
 * samples, and the extremes carry the exact instant they happened at, so a
 * five-second spike stays visible — and tappable — on a 30-day window (§21).
 *
 * There is deliberately no `profileId` here: an hour may span a context
 * switch, and unlike a minute an hour is long enough that dropping it for that
 * reason would leave holes. Per-profile statistics come from `minute_stats`.
 */
@Entity(tableName = "hour_sketches")
data class HourSketchEntity(
    /** Epoch millis of the hour boundary, `timestamp / 3600000 * 3600000`. */
    @PrimaryKey val hourStart: Long,
    /** Raw samples the sketch was built from — exact. */
    val count: Int,
    val minDoseRate: Float,
    val maxDoseRate: Float,
    /** Exact instant of [minDoseRate]. */
    val minAtMillis: Long,
    /** Exact instant of [maxDoseRate]. */
    val maxAtMillis: Long,
    /** Serialized KLL sketch, raw device units. */
    val sketch: ByteArray,
    /** [app.alpha.analysis.quantiles.KllSketch.ALGORITHM_VERSION] at build time. */
    val algorithmVersion: Int,
    /** Accuracy parameter `k` of the stored sketch (§32: parameters are recorded). */
    val sketchK: Int,
) {
    // ByteArray needs manual equality; the hour boundary identifies the row.
    override fun equals(other: Any?): Boolean =
        other is HourSketchEntity && other.hourStart == hourStart
    override fun hashCode(): Int = hourStart.hashCode()
}


/**
 * A closed baseline period of a profile (why-spec §7).
 *
 * When the user confirms that the situation itself changed, the historical
 * range that was in force is written here and a new period begins. Nothing is
 * recalculated and nothing is deleted: this row is the record of what «обычно
 * здесь» used to mean and when that stopped being true.
 */
@Entity(
    tableName = "baseline_epochs",
    indices = [Index(value = ["profileId", "endedAtMillis"])],
)
data class BaselineEpochEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    /** Start of the closed period; equals the previous epoch or the first data. */
    val startedAtMillis: Long,
    /** The instant the user started the new period. */
    val endedAtMillis: Long,
    /** Flat JSON snapshot of the band that was in force ([app.alpha.data.JsonMap]). */
    val stats: String,
    /** Why the period ended; [REASON_USER_SHIFT] is the only one today. */
    val reason: String,
    val createdAt: Long,
) {
    companion object {
        /** The user confirmed «Уровень изменился надолго → Обновить профиль». */
        const val REASON_USER_SHIFT = "user_shift"
    }
}


/**
 * Эталон места — **reference fingerprint** профиля (ADR 005).
 *
 * Снимок того, как место выглядело в момент зрелости профиля: распределения
 * мощности дозы и скорости счёта плюс опорный спектр, накопленный только из
 * ДОПУЩЕННЫХ интервалов. Создаётся автоматически и дальше не меняется:
 * скользящий baseline отвечает на «что обычно здесь сейчас», эталон — на «как
 * здесь было тогда», и расхождение между ними означает устойчивое изменение
 * фона места.
 *
 * Строки не переписываются: «Обновить эталон» добавляет новую.
 */
@Entity(
    tableName = "profile_fingerprints",
    indices = [Index(value = ["profileId", "createdAt"])],
)
data class ProfileFingerprintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val createdAt: Long,
    /** Допущенное время измерений за распределениями, секунды. */
    val accumulatedSeconds: Long,
    /** Сырые допущенные отсчёты за ними же. */
    val sampleCount: Long,
    val doseLowMicroSvH: Float,
    val doseMedianMicroSvH: Float,
    val doseHighMicroSvH: Float,
    val doseP25MicroSvH: Float,
    val doseP75MicroSvH: Float,
    val doseMadMicroSvH: Float,
    val cpsLow: Float,
    val cpsMedian: Float,
    val cpsHigh: Float,
    /** Экспозиция опорного спектра, секунды (сумма интервалов). */
    val spectrumSeconds: Long,
    val a0: Float,
    val a1: Float,
    val a2: Float,
    val channelCount: Int,
    /** Опорный спектр, i32 LE ([app.alpha.data.SpectrumBlob]). */
    val spectrum: ByteArray,
    /** Кем создан: [ORIGIN_AUTO] по зрелости или [ORIGIN_USER] кнопкой. */
    val origin: String,
    val algorithmVersion: Int,
) {
    override fun equals(other: Any?): Boolean = other is ProfileFingerprintEntity && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object {
        /** Создан приложением по достижении зрелости профиля. */
        const val ORIGIN_AUTO = "auto"

        /** Создан по команде «Обновить эталон» — например, после ремонта. */
        const val ORIGIN_USER = "user"
    }
}

/**
 * Один срез спектрограммы — ПОСТОЯННЫЙ продукт измерения (ADR 007), а не кэш
 * интерфейса. Строка представляет реально измеренный интервал и не
 * подразумевает временнóго разрешения мельче своей экспозиции.
 *
 * **Это не снимок спектра.** В `spectra` лежат 1024 канала с калибровкой — по
 * ним ищут пики, называют нуклиды и экспортируют. Здесь лежит
 * [app.alpha.analysis.SpectrogramBinning.CURRENT_SCHEME] полос
 * ОТОБРАЖЕНИЯ: каналы просуммированы необратимо, калибровка впечатана, ширина
 * полосы в разы больше аппаратного разрешения. Искать по срезам пики нельзя.
 *
 * Хранится СЧЁТ, а не скорость: R = N/Δt восстанавливается всегда, обратно
 * пуассоновская статистика — нет.
 */
@Entity(tableName = "spectrogram_slices")
data class SpectrogramSliceEntity(
    /** Начало интервала, epoch millis; оно же ключ — двух срезов с одним
     *  началом не бывает, поэтому перезапись идемпотентна. */
    @PrimaryKey val startMillis: Long,
    /** Конец интервала (момент опроса, давшего этот срез). */
    val endMillis: Long,
    /** Экспозиция: насколько выросло накопление прибора. Не `end − start`. */
    val durationMillis: Long,
    /** Версия схемы полос; записи разных схем не складываются. */
    val schemeId: String,
    /** Число полос в [counts] — читается без знания схемы. */
    val bandCount: Int,
    /** Счёт по полосам, i32 LE ([app.alpha.data.SpectrumBlob]). */
    val counts: ByteArray,
    /** Показание 1 Гц (у слитого среза — среднее по экспозиции); null — нет. */
    val cps: Float?,
    val doseMicroSvH: Float?,
    /** Сколько записанных срезов слито в этот; 1 — как записано. */
    val sliceCount: Int,
) {
    // ByteArray needs manual equality; начало интервала идентифицирует строку.
    override fun equals(other: Any?): Boolean =
        other is SpectrogramSliceEntity && other.startMillis == startMillis
    override fun hashCode(): Int = startMillis.hashCode()
}
