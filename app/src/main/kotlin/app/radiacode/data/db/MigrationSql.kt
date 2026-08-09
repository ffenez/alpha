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
