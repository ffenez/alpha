package app.radiacode.data.db

/**
 * Raw migration SQL, kept as plain string lists so a JVM unit test can replay
 * them on a real SQLite database built from the exported schema JSON
 * (app/schemas) — migrations stay verified without instrumentation.
 *
 * Statements must produce exactly the schema Room expects for the target
 * version (see `app/schemas/app.radiacode.data.db.AppDatabase/<v>.json`).
 */
object MigrationSql {

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
