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
