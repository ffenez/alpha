package app.radiacode.data.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

/**
 * Migration verification without instrumentation: build a real SQLite database
 * from the exported v1 schema JSON (app/schemas), replay [MigrationSql], then
 * assert the result matches the exported v2 schema — tables, columns, NOT
 * NULL, and index names — and that existing rows survive.
 */
class MigrationTest {

    private fun schema(version: Int): JSONObject {
        val file = File("schemas/app.radiacode.data.db.AppDatabase/$version.json")
        assertTrue(file.exists(), "exported schema missing: ${file.absolutePath}")
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun createFromSchema(connection: Connection, schema: JSONObject) {
        val entities = schema.getJSONArray("entities")
        connection.createStatement().use { statement ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                statement.execute(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    statement.execute(
                        indices.getJSONObject(j).getString("createSql")
                            .replace("\${TABLE_NAME}", tableName),
                    )
                }
            }
        }
    }

    private data class Column(val type: String, val notNull: Boolean)

    private fun actualColumns(connection: Connection, table: String): Map<String, Column> {
        val result = mutableMapOf<String, Column>()
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery("PRAGMA table_info(`$table`)")
            while (rows.next()) {
                result[rows.getString("name")] =
                    Column(type = rows.getString("type"), notNull = rows.getInt("notnull") == 1)
            }
        }
        return result
    }

    private fun actualIndexNames(connection: Connection, table: String): Set<String> {
        val result = mutableSetOf<String>()
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery("PRAGMA index_list(`$table`)")
            while (rows.next()) {
                val name = rows.getString("name")
                if (!name.startsWith("sqlite_autoindex")) result.add(name)
            }
        }
        return result
    }

    private fun assertMatchesSchema(connection: Connection, expected: JSONObject) {
        val entities = expected.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                val actual = actualColumns(connection, table)
                assertTrue(actual.isNotEmpty(), "table $table missing after migration")

                val fields = entity.getJSONArray("fields")
                for (j in 0 until fields.length()) {
                    val field = fields.getJSONObject(j)
                    val column = field.getString("columnName")
                    val expectedType = field.getString("affinity")
                    // Nullable columns omit the notNull key in the export.
                    val expectedNotNull = field.optBoolean("notNull", false)
                    val actualColumn = actual[column]
                    assertTrue(actualColumn != null, "$table.$column missing after migration")
                    assertEquals(expectedType, actualColumn.type, "$table.$column type")
                    assertEquals(expectedNotNull, actualColumn.notNull, "$table.$column notNull")
                }
                assertEquals(fields.length(), actual.size, "$table extra/missing columns")

                val expectedIndices = mutableSetOf<String>()
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) {
                        expectedIndices.add(indices.getJSONObject(j).getString("name"))
                    }
                }
                assertEquals(expectedIndices, actualIndexNames(connection, table), "$table indices")
        }
    }

    @Test
    fun `migration 1 to 2 produces exactly the exported v2 schema`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(1))
            MigrationSql.FROM_1_TO_2.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            assertMatchesSchema(connection, schema(2))
        }
    }

    @Test
    fun `migration 2 to 3 produces exactly the exported v3 schema`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(2))
            MigrationSql.FROM_2_TO_3.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            assertMatchesSchema(connection, schema(3))
        }
    }

    @Test
    fun `migration 3 to 4 produces exactly the exported v4 schema`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(3))
            MigrationSql.FROM_3_TO_4.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            assertMatchesSchema(connection, schema(4))
        }
    }

    @Test
    fun `migration 4 to 5 produces exactly the exported v5 schema`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(4))
            MigrationSql.FROM_4_TO_5.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            assertMatchesSchema(connection, schema(5))
        }
    }

    @Test
    fun `migration 5 to 6 produces exactly the exported v6 schema`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(5))
            MigrationSql.FROM_5_TO_6.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            assertMatchesSchema(connection, schema(6))
        }
    }

    @Test
    fun `migration 6 to 7 produces exactly the exported v7 schema`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(6))
            MigrationSql.FROM_6_TO_7.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            assertMatchesSchema(connection, schema(7))
        }
    }

    @Test
    fun `migration 7 to 8 produces exactly the exported v8 schema`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(7))
            MigrationSql.FROM_7_TO_8.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            assertMatchesSchema(connection, schema(8))
        }
    }

    @Test
    fun `migration 8 to 9 produces exactly the exported v9 schema`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(8))
            MigrationSql.FROM_8_TO_9.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            assertMatchesSchema(connection, schema(9))
        }
    }

    @Test
    fun `migration 10 to 11 produces exactly the exported v11 schema`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(10))
            MigrationSql.FROM_10_TO_11.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            assertMatchesSchema(connection, schema(11))
        }
    }

    @Test
    fun `migration 10 to 11 adds an empty slice table and keeps the spectra`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(10))
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO spectra (timestamp, accumulated, isBackgroundReference, " +
                        "origin, label, durationSeconds, a0, a1, a2, channelCount, counts) " +
                        "VALUES (3000, 0, 0, 'user', 'Проба', 600, -5.5, 2.4, 0.0004, 4, " +
                        "x'01000000020000000300000004000000')",
                )
            }

            MigrationSql.FROM_10_TO_11.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            // Снимки спектра — другой вид данных, миграция их не трогает и
            // ничего из них не пересчитывает: срез спектрограммы это разность
            // двух ПОСЛЕДОВАТЕЛЬНЫХ опросов, а не сохранённый снимок.
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT label FROM spectra")
                assertTrue(rows.next())
                assertEquals("Проба", rows.getString("label"))
            }
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT COUNT(*) AS n FROM spectrogram_slices")
                rows.next()
                assertEquals(0, rows.getInt("n"), "история наполняется вперёд, а не миграцией")
            }
            // Начало интервала — ключ: повторная запись заменяет строку, а не
            // удваивает историю.
            connection.createStatement().use { statement ->
                for (counts in listOf(1, 2)) {
                    statement.execute(
                        "INSERT OR REPLACE INTO spectrogram_slices (startMillis, endMillis, " +
                            "durationMillis, schemeId, bandCount, counts, cps, doseMicroSvH, " +
                            "sliceCount) VALUES (1000, 6000, 5000, 'SPECTROGRAM_96_V1', 96, " +
                            "x'0100000002000000', NULL, NULL, $counts)",
                    )
                }
                val rows = statement.executeQuery(
                    "SELECT COUNT(*) AS n, MAX(sliceCount) AS c FROM spectrogram_slices",
                )
                rows.next()
                assertEquals(1, rows.getInt("n"))
                assertEquals(2, rows.getInt("c"))
            }
        }
    }

    @Test
    fun `migration 9 to 10 produces exactly the exported v10 schema`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(9))
            MigrationSql.FROM_9_TO_10.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            assertMatchesSchema(connection, schema(10))
        }
    }

    @Test
    fun `migration 9 to 10 adds an empty table and touches nothing else`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(9))
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO profiles " +
                        "(id, name, icon, parentId, archived, autoActivate, " +
                        "baselineLearning, role, createdAt) " +
                        "VALUES (3, 'Дача', '', NULL, 0, 1, 1, 'user', 100)",
                )
            }

            MigrationSql.FROM_9_TO_10.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT COUNT(*) AS n FROM profile_fingerprints")
                rows.next()
                // Никакой эталон миграцией не выдумывается: приложение создаст
                // его само, когда у места наберётся статистика.
                assertEquals(0, rows.getInt("n"))
            }
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT name FROM profiles")
                assertTrue(rows.next())
                assertEquals("Дача", rows.getString("name"))
            }
        }
    }

    @Test
    fun `migration 8 to 9 keeps every profile and starts every epoch open`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(8))
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO profiles " +
                        "(id, name, icon, parentId, archived, autoActivate, " +
                        "baselineLearning, role, createdAt) " +
                        "VALUES (7, 'Дом', '', NULL, 0, 1, 1, 'user', 100)",
                )
            }

            MigrationSql.FROM_8_TO_9.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT name, baselineEpochMillis, shiftDeclinedAtMillis FROM profiles",
                )
                assertTrue(rows.next())
                assertEquals("Дом", rows.getString("name"))
                // NULL epoch = «the whole sliding window», i.e. exactly the
                // behaviour of every version before this one.
                rows.getLong("baselineEpochMillis")
                assertTrue(rows.wasNull(), "an existing profile keeps its whole history")
                rows.getLong("shiftDeclinedAtMillis")
                assertTrue(rows.wasNull())
                assertTrue(!rows.next())
            }
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT COUNT(*) AS n FROM baseline_epochs")
                rows.next()
                assertEquals(0, rows.getInt("n"), "no period is closed by a migration")
            }
        }
    }

    @Test
    fun `migration 7 to 8 keeps samples and starts the pre-aggregation empty`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(7))
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO samples " +
                        "(timestamp, doseRate, doseRateErr, countRate, countRateErr, " +
                        "flags, realTimeFlags, placeId, baselineExcluded) " +
                        "VALUES (1000, 0.00001, 1.5, 21.0, 2.0, 0, 0, 3, NULL)",
                )
            }

            MigrationSql.FROM_7_TO_8.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            // Raw data are untouched — the pre-aggregation is a derived cache.
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT timestamp, doseRate FROM samples")
                assertTrue(rows.next())
                assertEquals(1000L, rows.getLong("timestamp"))
                assertEquals(0.00001, rows.getDouble("doseRate"), 1e-9)
                assertTrue(!rows.next())
            }
            // Both derived tables exist and start empty: nothing is computed
            // inside a migration, the background backfill does it afterwards.
            for (table in listOf("minute_stats", "hour_sketches")) {
                connection.createStatement().use { statement ->
                    val rows = statement.executeQuery("SELECT COUNT(*) AS n FROM `$table`")
                    rows.next()
                    assertEquals(0, rows.getInt("n"), "$table must start empty")
                }
            }
            // The minute key really is a key: a rebuild replaces, never doubles.
            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT OR REPLACE INTO minute_stats (minuteStart, count, minDoseRate, " +
                        "maxDoseRate, sumDoseRate, sumSqDoseRate, minAtMillis, maxAtMillis, " +
                        "firstSampleTime, lastSampleTime, admittedCount, profileId) " +
                        "VALUES (60000, 60, 0.1, 0.2, 9.0, 1.5, 60000, 60030, 60000, 60059, 60, 3)",
                )
                statement.execute(
                    "INSERT OR REPLACE INTO minute_stats (minuteStart, count, minDoseRate, " +
                        "maxDoseRate, sumDoseRate, sumSqDoseRate, minAtMillis, maxAtMillis, " +
                        "firstSampleTime, lastSampleTime, admittedCount, profileId) " +
                        "VALUES (60000, 61, 0.1, 0.2, 9.0, 1.5, 60000, 60030, 60000, 60059, 61, 3)",
                )
                val rows = statement.executeQuery(
                    "SELECT COUNT(*) AS n, MAX(count) AS c FROM minute_stats",
                )
                rows.next()
                assertEquals(1, rows.getInt("n"))
                assertEquals(61, rows.getInt("c"))
            }
        }
    }

    @Test
    fun `migration 6 to 7 keeps spectra and adds empty experiment tables`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(6))
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO spectra (timestamp, accumulated, isBackgroundReference, " +
                        "origin, label, durationSeconds, a0, a1, a2, channelCount, counts) " +
                        "VALUES (3000, 0, 0, 'user', 'Проба', 600, -5.5, 2.4, 0.0004, 4, " +
                        "x'01000000020000000300000004000000')",
                )
            }

            MigrationSql.FROM_6_TO_7.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT timestamp, label, durationSeconds, analysisMeta FROM spectra",
                )
                assertTrue(rows.next())
                assertEquals(3000L, rows.getLong("timestamp"))
                assertEquals("Проба", rows.getString("label"))
                assertEquals(600L, rows.getLong("durationSeconds"))
                rows.getString("analysisMeta")
                assertTrue(rows.wasNull(), "pre-migration snapshots carry no processing metadata")
                assertTrue(!rows.next(), "exactly one row expected")
            }

            // The new tables exist, are empty, and runs cascade with their experiment.
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute(
                    "INSERT INTO experiments (id, kind, profileId, createdAt, note, geometry, " +
                        "algorithmVersion, params) " +
                        "VALUES (1, 'background_vs_object', NULL, 5000, '', 'на столе', 1, '{}')",
                )
                statement.execute(
                    "INSERT INTO experiment_runs " +
                        "(experimentId, label, startedAt, endedAt, spectrumId, doseStats) " +
                        "VALUES (1, 'A', 5100, 5400, NULL, '{}')",
                )
                statement.execute("DELETE FROM experiments WHERE id = 1")
                val rows = statement.executeQuery("SELECT COUNT(*) AS n FROM experiment_runs")
                rows.next()
                assertEquals(0, rows.getInt("n"), "runs must cascade with their experiment")
            }
        }
    }

    @Test
    fun `migration 5 to 6 turns places into profiles keeping sample linkage`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(5))
            connection.createStatement().use {
                it.execute("INSERT INTO places (id, name, createdAt) VALUES (7, 'Дача', 900)")
                it.execute("INSERT INTO places (id, name, createdAt) VALUES (8, 'Офис', 950)")
                it.execute(
                    "INSERT INTO samples " +
                        "(timestamp, doseRate, doseRateErr, countRate, countRateErr, " +
                        "flags, realTimeFlags, placeId) " +
                        "VALUES (1000, 0.00001, 1.5, 21.0, 2.0, 0, 0, 7)",
                )
                it.execute(
                    "INSERT INTO measurement_sessions (placeId, startedAt, endedAt) " +
                        "VALUES (8, 500, 600)",
                )
            }

            MigrationSql.FROM_5_TO_6.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            connection.createStatement().use { statement ->
                val profiles = statement.executeQuery(
                    "SELECT id, name, role, archived, autoActivate, baselineLearning, " +
                        "parentId, icon FROM profiles ORDER BY id",
                )
                assertTrue(profiles.next())
                assertEquals(7L, profiles.getLong("id"))
                assertEquals("Дача", profiles.getString("name"))
                assertEquals("user", profiles.getString("role"))
                assertEquals(0, profiles.getInt("archived"))
                assertEquals(1, profiles.getInt("autoActivate"))
                assertEquals(1, profiles.getInt("baselineLearning"))
                assertEquals("", profiles.getString("icon"))
                profiles.getObject("parentId")
                assertTrue(profiles.wasNull(), "migrated profiles start as roots")
                assertTrue(profiles.next())
                assertEquals(8L, profiles.getLong("id"))
                assertTrue(!profiles.next(), "exactly two profiles expected")
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT placeId, baselineExcluded FROM samples",
                )
                assertTrue(rows.next())
                assertEquals(7L, rows.getLong("placeId"), )
                rows.getString("baselineExcluded")
                assertTrue(rows.wasNull(), "existing samples stay admitted")
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT placeId FROM measurement_sessions")
                assertTrue(rows.next())
                assertEquals(8L, rows.getLong("placeId"))
            }

            // The old table is gone, and nothing else lost its rows.
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT COUNT(*) AS n FROM sqlite_master " +
                        "WHERE type = 'table' AND name = 'places'",
                )
                rows.next()
                assertEquals(0, rows.getInt("n"), "places must be dropped")
            }
        }
    }

    @Test
    fun `migration 4 to 5 keeps existing track points with NULL altitude`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(4))
            connection.createStatement().use {
                it.execute("INSERT INTO track_sessions (id, name, startedAt) VALUES (1, 't', 500)")
                it.execute(
                    "INSERT INTO track_points " +
                        "(sessionId, timestamp, latitude, longitude, accuracyMeters, doseRate, countRate) " +
                        "VALUES (1, 1000, 55.75, 37.61, 4.5, 0.0001, 12.0)",
                )
            }

            MigrationSql.FROM_4_TO_5.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT timestamp, latitude, altitudeMeters FROM track_points",
                )
                assertTrue(rows.next())
                assertEquals(1000L, rows.getLong("timestamp"))
                assertEquals(55.75, rows.getDouble("latitude"), 1e-9)
                rows.getObject("altitudeMeters")
                assertTrue(rows.wasNull(), "pre-migration points must have NULL altitude")
                assertTrue(!rows.next(), "exactly one row expected")
            }
        }
    }

    @Test
    fun `migration 3 to 4 keeps existing snapshots as auto-origin unlabeled rows`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(3))
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO spectra (timestamp, accumulated, isBackgroundReference, " +
                        "durationSeconds, a0, a1, a2, channelCount, counts) " +
                        "VALUES (2000, 0, 1, 300, -5.5, 2.4, 0.0004, 4, " +
                        "x'01000000020000000300000004000000')",
                )
            }

            MigrationSql.FROM_3_TO_4.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT timestamp, isBackgroundReference, origin, label FROM spectra",
                )
                assertTrue(rows.next())
                assertEquals(2000L, rows.getLong("timestamp"))
                assertEquals(1, rows.getInt("isBackgroundReference"))
                assertEquals("auto", rows.getString("origin"))
                rows.getString("label")
                assertTrue(rows.wasNull(), "pre-migration snapshots must have NULL label")
                assertTrue(!rows.next(), "exactly one row expected")
            }
        }
    }

    @Test
    fun `migration 2 to 3 keeps saved spectra as non-background snapshots`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(2))
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO spectra " +
                        "(timestamp, accumulated, durationSeconds, a0, a1, a2, channelCount, counts) " +
                        "VALUES (1000, 0, 120, -5.5, 2.4, 0.0004, 4, x'01000000020000000300000004000000')",
                )
            }

            MigrationSql.FROM_2_TO_3.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT timestamp, durationSeconds, isBackgroundReference FROM spectra",
                )
                assertTrue(rows.next())
                assertEquals(1000L, rows.getLong("timestamp"))
                assertEquals(120L, rows.getLong("durationSeconds"))
                assertEquals(0, rows.getInt("isBackgroundReference"))
                assertTrue(!rows.next(), "exactly one row expected")
            }
        }
    }

    @Test
    fun `migration keeps existing measurement rows and detaches them from places`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createFromSchema(connection, schema(1))
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO samples " +
                        "(timestamp, doseRate, doseRateErr, countRate, countRateErr, flags, realTimeFlags) " +
                        "VALUES (1000, 0.00001, 1.5, 21.0, 2.0, 0, 0)",
                )
            }

            MigrationSql.FROM_1_TO_2.forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT timestamp, doseRate, placeId FROM samples",
                )
                assertTrue(rows.next())
                assertEquals(1000L, rows.getLong("timestamp"))
                assertEquals(0.00001, rows.getDouble("doseRate"), 1e-9)
                rows.getObject("placeId")
                assertTrue(rows.wasNull(), "pre-migration samples must have NULL placeId")
                assertTrue(!rows.next(), "exactly one row expected")
            }
        }
    }
}
