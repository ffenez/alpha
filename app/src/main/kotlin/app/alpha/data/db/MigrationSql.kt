package app.alpha.data.db

/**
 * Raw migration SQL, kept as plain string lists so a JVM unit test can replay
 * them on a real SQLite database built from the exported schema JSON
 * (app/schemas) — migrations stay verified without instrumentation.
 *
 * Statements must produce exactly the schema Room expects for the target
 * version (see `app/schemas/app.alpha.data.db.AppDatabase/<v>.json`).
 */
object MigrationSql {

    /**
     * v11 → v12: условия A/B-опыта разложены на поля.
     *
     * Strategy — **add only, nothing parsed**: старая колонка `geometry` это
     * свободная фраза, и разбирать её на расстояние с ориентацией нельзя.
     * Существующие опыты остаются с фразой, поля заполняются у новых.
     */
    /**
     * v15 → v16: фото образца у опыта — ссылка на снимок в галерее.
     *
     * Только добавление: у прежних опытов фото нет и быть не может.
     */
    /**
     * v16 → v17: профиль снимка спектра.
     *
     * Только добавление и NULL для старых строк: профиль съёмки прошлогоднего
     * снимка неизвестен, и подставить сегодняшний нельзя.
     */
    /**
     * v17 → v18: событие журнала становится ИНТЕРВАЛОМ.
     *
     * Только добавление и только NULL для старых строк: у записи, сделанной
     * прежней версией, интервала не было, и выдумывать его нечем. Старые
     * `deviation` остаются как есть — это измерения, а не мусор
     * (`history_semantic_events_redesign.md`, раздел о миграции).
     */
    /**
     * 22 → 23: признак прибора у секундного потока.
     *
     * Приборы можно менять, а журнал измерений один. Без признака записи двух
     * кристаллов сливаются необратимо; столбец добавляется пустым — у старых
     * строк прибор неизвестен, и выдумывать его нельзя.
     */
    val FROM_22_TO_23: List<String> = listOf(
        "ALTER TABLE `samples` ADD COLUMN `deviceSerial` TEXT",
        "ALTER TABLE `rare_data` ADD COLUMN `deviceSerial` TEXT",
    )

    val FROM_21_TO_22: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `spectrum_templates` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`deviceSerial` TEXT, " +
            "`deviceName` TEXT, " +
            "`a0` REAL NOT NULL, `a1` REAL NOT NULL, `a2` REAL NOT NULL, " +
            "`durationSeconds` INTEGER NOT NULL, " +
            "`resolution662` REAL NOT NULL, " +
            "`channelCount` INTEGER NOT NULL, " +
            "`counts` BLOB NOT NULL, " +
            "`source` TEXT NOT NULL, " +
            "`note` TEXT)",
        "CREATE INDEX IF NOT EXISTS `index_spectrum_templates_deviceSerial` " +
            "ON `spectrum_templates` (`deviceSerial`)",
        "CREATE INDEX IF NOT EXISTS `index_spectrum_templates_name` " +
            "ON `spectrum_templates` (`name`)",
    )

    val FROM_20_TO_21: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `survey_stations` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`spectrumId` INTEGER NOT NULL, " +
            "`timestamp` INTEGER NOT NULL, " +
            "`latitude` REAL NOT NULL, " +
            "`longitude` REAL NOT NULL, " +
            "`accuracyMeters` REAL NOT NULL, " +
            "`heightCm` INTEGER, " +
            "`pressureHpa` REAL, " +
            "`note` TEXT, " +
            "FOREIGN KEY(`spectrumId`) REFERENCES `spectra`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_survey_stations_spectrumId` " +
            "ON `survey_stations` (`spectrumId`)",
        "CREATE INDEX IF NOT EXISTS `index_survey_stations_timestamp` " +
            "ON `survey_stations` (`timestamp`)",
    )

    val FROM_19_TO_20: List<String> = listOf(
        "ALTER TABLE `track_points` ADD COLUMN `magneticUt` REAL DEFAULT NULL",
    )

    val FROM_18_TO_19: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `environment` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`timestamp` INTEGER NOT NULL, " +
            "`pressureHpa` REAL, " +
            "`magneticUt` REAL, " +
            "`magneticSd` REAL, " +
            "`phoneTempC` REAL, " +
            "`samples` INTEGER NOT NULL DEFAULT 0)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_environment_timestamp` " +
            "ON `environment` (`timestamp`)",
    )

    val FROM_17_TO_18: List<String> = listOf(
        "ALTER TABLE `events` ADD COLUMN `endTimestamp` INTEGER DEFAULT NULL",
        "ALTER TABLE `events` ADD COLUMN `minMicroSvH` REAL DEFAULT NULL",
        "ALTER TABLE `events` ADD COLUMN `maxMicroSvH` REAL DEFAULT NULL",
        "ALTER TABLE `events` ADD COLUMN `meanMicroSvH` REAL DEFAULT NULL",
        "ALTER TABLE `events` ADD COLUMN `sampleCount` INTEGER DEFAULT NULL",
        "ALTER TABLE `events` ADD COLUMN `thresholdMicroSvH` REAL DEFAULT NULL",
    )

    val FROM_16_TO_17: List<String> = listOf(
        "ALTER TABLE `spectra` ADD COLUMN `profileId` INTEGER DEFAULT NULL",
        "ALTER TABLE `spectra` ADD COLUMN `profileName` TEXT DEFAULT NULL",
    )

    val FROM_15_TO_16: List<String> = listOf(
        "ALTER TABLE `experiments` ADD COLUMN `photoUri` TEXT",
    )

    /**
     * v14 → v15: провенанс снимка спектра и эпоха накопления (ADR 008).
     *
     * Только добавление и только NULL для старых строк: какой прибор и какая
     * эпоха стоят за снимком, записанным прежней версией, неизвестно, и
     * подставить туда сегодняшний прибор значило бы придумать наблюдение.
     * Практическое следствие: старые снимки можно смотреть, но нельзя
     * вычитать друг из друга.
     */
    val FROM_14_TO_15: List<String> = listOf(
        "ALTER TABLE `spectra` ADD COLUMN `deviceSerial` TEXT",
        "ALTER TABLE `spectra` ADD COLUMN `firmware` TEXT",
        "ALTER TABLE `spectra` ADD COLUMN `epochId` INTEGER",
        "ALTER TABLE `spectra` ADD COLUMN `trigger` TEXT",
    )

    /**
     * v13 → v14: у маршрута появляется признак «оборвалась».
     *
     * Только добавление, значение по умолчанию — «нет»: о записях, сделанных
     * прежней версией, неизвестно, останавливал их человек или система, и
     * назвать их прерванными задним числом значило бы придумать событие.
     */
    val FROM_13_TO_14: List<String> = listOf(
        "ALTER TABLE `track_sessions` ADD COLUMN `interrupted` INTEGER NOT NULL DEFAULT 0",
    )

    /**
     * v12 → v13: у маршрута появляется своё место в Истории, а значит и свои
     * данные — пройденное расстояние, посчитанное один раз.
     *
     * Только добавление: старым маршрутам расстояние не выдумывается, оно
     * остаётся `NULL` и считается при первом открытии списка по их точкам.
     */
    val FROM_12_TO_13: List<String> = listOf(
        "ALTER TABLE `track_sessions` ADD COLUMN `distanceMeters` REAL",
    )

    val FROM_11_TO_12: List<String> = listOf(
        "ALTER TABLE `experiments` ADD COLUMN `distanceCm` INTEGER",
        "ALTER TABLE `experiments` ADD COLUMN `placement` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `experiments` ADD COLUMN `orientation` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `experiments` ADD COLUMN `plannedSeconds` INTEGER NOT NULL DEFAULT 0",
    )

    /**
     * v10 → v11: постоянная история спектрограммы (ADR 007) — `spectrogram_slices`.
     *
     * Strategy — add only, and **start empty**: пересчитать историю в миграции
     * не из чего. Срез спектрограммы это разность двух последовательных
     * приборных снимков, а в `spectra` лежат снимки, снятые раз в минуту и
     * только пока шла запись, — восстановленная из них «история» была бы не
     * теми интервалами, которые прибор действительно измерял. Таблица
     * наполняется вперёд, с первого же опроса после обновления.
     */
    val FROM_10_TO_11: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `spectrogram_slices` (
            `startMillis` INTEGER NOT NULL,
            `endMillis` INTEGER NOT NULL,
            `durationMillis` INTEGER NOT NULL,
            `schemeId` TEXT NOT NULL,
            `bandCount` INTEGER NOT NULL,
            `counts` BLOB NOT NULL,
            `cps` REAL,
            `doseMicroSvH` REAL,
            `sliceCount` INTEGER NOT NULL,
            PRIMARY KEY(`startMillis`)
        )
        """.trimIndent(),
    )

    /**
     * v7 → v8: the versioned pre-aggregation of ADR 004 — minute scalars
     * (`minute_stats`) and hourly mergeable quantile sketches
     * (`hour_sketches`).
     *
     * Strategy — add only, and **start empty**:
     *  - both tables are derived data. Nothing is computed inside the
     *    migration: filling them here would mean grinding through months of
     *    `samples` while the user waits for the app to open. The background
     *    backfill ([app.alpha.data.preagg.PreAggregator]) rebuilds them
     *    from raw afterwards, hour by hour, resumably;
     *  - until an hour is built, the chart says so and falls back to the
     *    coarser path instead of pretending (CHART SPEC §32);
     *  - nothing in `samples` is touched. The pre-aggregation is a cache with
     *    a version, and raw data stay the source of truth (§2).
     */
    val FROM_7_TO_8: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `minute_stats` (
            `minuteStart` INTEGER NOT NULL,
            `count` INTEGER NOT NULL,
            `minDoseRate` REAL NOT NULL,
            `maxDoseRate` REAL NOT NULL,
            `sumDoseRate` REAL NOT NULL,
            `sumSqDoseRate` REAL NOT NULL,
            `minAtMillis` INTEGER NOT NULL,
            `maxAtMillis` INTEGER NOT NULL,
            `firstSampleTime` INTEGER NOT NULL,
            `lastSampleTime` INTEGER NOT NULL,
            `admittedCount` INTEGER NOT NULL,
            `profileId` INTEGER,
            PRIMARY KEY(`minuteStart`)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_minute_stats_profileId` " +
            "ON `minute_stats` (`profileId`)",
        """
        CREATE TABLE IF NOT EXISTS `hour_sketches` (
            `hourStart` INTEGER NOT NULL,
            `count` INTEGER NOT NULL,
            `minDoseRate` REAL NOT NULL,
            `maxDoseRate` REAL NOT NULL,
            `minAtMillis` INTEGER NOT NULL,
            `maxAtMillis` INTEGER NOT NULL,
            `sketch` BLOB NOT NULL,
            `algorithmVersion` INTEGER NOT NULL,
            `sketchK` INTEGER NOT NULL,
            PRIMARY KEY(`hourStart`)
        )
        """.trimIndent(),
    )

    /**
     * v9 → v10: эталон места — reference fingerprint профиля (ADR 005).
     *
     * Strategy — add only: a new table, nothing touched. Existing profiles get
     * no reference row at all, which is the honest state — the app has not
     * accumulated one yet and will create it when the profile matures.
     */
    val FROM_9_TO_10: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `profile_fingerprints` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `profileId` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `accumulatedSeconds` INTEGER NOT NULL,
            `sampleCount` INTEGER NOT NULL,
            `doseLowMicroSvH` REAL NOT NULL,
            `doseMedianMicroSvH` REAL NOT NULL,
            `doseHighMicroSvH` REAL NOT NULL,
            `doseP25MicroSvH` REAL NOT NULL,
            `doseP75MicroSvH` REAL NOT NULL,
            `doseMadMicroSvH` REAL NOT NULL,
            `cpsLow` REAL NOT NULL,
            `cpsMedian` REAL NOT NULL,
            `cpsHigh` REAL NOT NULL,
            `spectrumSeconds` INTEGER NOT NULL,
            `a0` REAL NOT NULL,
            `a1` REAL NOT NULL,
            `a2` REAL NOT NULL,
            `channelCount` INTEGER NOT NULL,
            `spectrum` BLOB NOT NULL,
            `origin` TEXT NOT NULL,
            `algorithmVersion` INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_profile_fingerprints_profileId_createdAt` " +
            "ON `profile_fingerprints` (`profileId`, `createdAt`)",
    )

    /**
     * v8 → v9: a profile's baseline may be **started over** by the user
     * (why-spec §7), and the old period is kept.
     *
     * Strategy — add only:
     *  - `profiles.baselineEpochMillis` is the earliest instant the statistics
     *    of that profile may look at; NULL (every existing row) means «the
     *    whole sliding window», which is exactly today's behaviour;
     *  - `profiles.shiftDeclinedAtMillis` remembers «оставить как есть», so the
     *    offer does not come back every time the sheet is opened;
     *  - `baseline_epochs` keeps the closed period with a snapshot of the band
     *    it had. Raw measurements are **never** touched by any of this: the
     *    epoch moves what the statistics read, not what the app stored.
     */
    val FROM_8_TO_9: List<String> = listOf(
        "ALTER TABLE `profiles` ADD COLUMN `baselineEpochMillis` INTEGER",
        "ALTER TABLE `profiles` ADD COLUMN `shiftDeclinedAtMillis` INTEGER",
        """
        CREATE TABLE IF NOT EXISTS `baseline_epochs` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `profileId` INTEGER NOT NULL,
            `startedAtMillis` INTEGER NOT NULL,
            `endedAtMillis` INTEGER NOT NULL,
            `stats` TEXT NOT NULL,
            `reason` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_baseline_epochs_profileId_endedAtMillis` " +
            "ON `baseline_epochs` (`profileId`, `endedAtMillis`)",
    )

    /**
     * v6 → v7: A/B research experiments (spec §9, §16) and the reproducibility
     * stamp of derived spectra (spec §22).
     *
     * Strategy — add only:
     *  - `experiments` + `experiment_runs` are new; runs cascade with their
     *    experiment (a run without its protocol is meaningless);
     *  - `spectra.analysisMeta` starts NULL for every existing row. That is the
     *    honest value: those snapshots were stored before derived spectra
     *    carried processing metadata, and inventing one now would claim
     *    knowledge the app never had. Raw device/import snapshots keep NULL
     *    forever — nothing was computed on them.
     */
    val FROM_6_TO_7: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `experiments` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `kind` TEXT NOT NULL,
            `profileId` INTEGER,
            `createdAt` INTEGER NOT NULL,
            `note` TEXT NOT NULL,
            `geometry` TEXT NOT NULL,
            `algorithmVersion` INTEGER NOT NULL,
            `params` TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_experiments_createdAt` ON `experiments` (`createdAt`)",
        """
        CREATE TABLE IF NOT EXISTS `experiment_runs` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `experimentId` INTEGER NOT NULL,
            `label` TEXT NOT NULL,
            `startedAt` INTEGER NOT NULL,
            `endedAt` INTEGER,
            `spectrumId` INTEGER,
            `doseStats` TEXT NOT NULL,
            `distanceCm` REAL,
            `shieldingNote` TEXT,
            FOREIGN KEY(`experimentId`) REFERENCES `experiments`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_experiment_runs_experimentId_startedAt` " +
            "ON `experiment_runs` (`experimentId`, `startedAt`)",
        "ALTER TABLE `spectra` ADD COLUMN `analysisMeta` TEXT",
    )

    /**
     * v5 → v6: places become measurement profiles (spec §3) and every sample
     * carries its baseline-admission verdict (spec §4.2).
     *
     * Strategy — migrate, never drop:
     *  - `profiles` is created and filled from `places` **keeping the primary
     *    keys**, so `samples.placeId` / `measurement_sessions.placeId` keep
     *    pointing at the same environment; `places` is then dropped;
     *  - the two `placeId` **columns keep their name**. Renaming them needs
     *    `ALTER TABLE … RENAME COLUMN` (SQLite 3.25 = Android API 30) or a full
     *    table rebuild, and `samples` holds ~86 400 rows per recorded day —
     *    rebuilding it on a phone is a real risk for zero user value. The
     *    Kotlin entities map the old columns to `profileId` instead;
     *  - migrated profiles get `role='user'`, learning and auto-activation on,
     *    no icon, no parent — exactly the behaviour places had;
     *  - `profile_networks` starts empty: nothing in v5 knew about Wi-Fi;
     *  - `samples.baselineExcluded` starts NULL for existing rows, i.e.
     *    «admitted».
     *    That matches how those samples were actually used before this version
     *    (the whole place history fed the baseline), so no statistic changes
     *    silently under the user.
     */
    val FROM_5_TO_6: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `profiles` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `icon` TEXT NOT NULL,
            `parentId` INTEGER,
            `archived` INTEGER NOT NULL,
            `autoActivate` INTEGER NOT NULL,
            `baselineLearning` INTEGER NOT NULL,
            `role` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_profiles_parentId` ON `profiles` (`parentId`)",
        """
        INSERT INTO `profiles`
            (`id`, `name`, `icon`, `parentId`, `archived`, `autoActivate`,
             `baselineLearning`, `role`, `createdAt`)
        SELECT `id`, `name`, '', NULL, 0, 1, 1, 'user', `createdAt` FROM `places`
        """.trimIndent(),
        "DROP TABLE `places`",
        """
        CREATE TABLE IF NOT EXISTS `profile_networks` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `profileId` INTEGER NOT NULL,
            `networkHash` TEXT NOT NULL,
            `label` TEXT,
            `createdAt` INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_profile_networks_networkHash` " +
            "ON `profile_networks` (`networkHash`)",
        "CREATE INDEX IF NOT EXISTS `index_profile_networks_profileId` " +
            "ON `profile_networks` (`profileId`)",
        "ALTER TABLE `samples` ADD COLUMN `baselineExcluded` TEXT",
    )

    /**
     * v4 → v5: track_points.altitudeMeters — GPS altitude for flight-mode
     * detection and the dose-vs-altitude chart. Pre-v5 points stay NULL
     * (altitude was never recorded, no honest backfill exists).
     */
    val FROM_4_TO_5: List<String> = listOf(
        "ALTER TABLE `track_points` ADD COLUMN `altitudeMeters` REAL",
    )

    /**
     * v3 → v4: spectra.origin (auto/user/import) + spectra.label — RC-XML
     * import and the История snapshot list. Pre-v4 rows become 'auto': user
     * saves were not distinguishable from autosaves before this version.
     */
    val FROM_3_TO_4: List<String> = listOf(
        "ALTER TABLE `spectra` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'auto'",
        "ALTER TABLE `spectra` ADD COLUMN `label` TEXT",
    )

    /** v2 → v3: spectra.isBackgroundReference (Спектр background overlay/subtraction). */
    val FROM_2_TO_3: List<String> = listOf(
        "ALTER TABLE `spectra` ADD COLUMN `isBackgroundReference` INTEGER NOT NULL DEFAULT 0",
    )

    /** v1 → v2: places, measurement sessions, samples.placeId. */
    val FROM_1_TO_2: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `places` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS `measurement_sessions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `placeId` INTEGER,
            `startedAt` INTEGER NOT NULL,
            `endedAt` INTEGER
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_measurement_sessions_startedAt` " +
            "ON `measurement_sessions` (`startedAt`)",
        "ALTER TABLE `samples` ADD COLUMN `placeId` INTEGER",
        "CREATE INDEX IF NOT EXISTS `index_samples_placeId_timestamp` " +
            "ON `samples` (`placeId`, `timestamp`)",
    )
}
