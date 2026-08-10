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
