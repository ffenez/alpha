package app.alpha.data.db

import app.alpha.ui.logic.TrackGrid
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

/**
 * The accumulated map is only honest if SQLite bins points into exactly the
 * cells `TrackGrid` computes in Kotlin — the overlay draws rectangles from the
 * pure math and the numbers come from the query.
 *
 * So this replays the **real** DAO statements ([TrackGridSql]) against a real
 * SQLite database built from the exported schema, without instrumentation.
 */
class TrackGridSqlTest {

    private val maxAccuracy = TrackGrid.MAX_ACCURACY_METERS

    private data class Fix(
        val latitude: Double,
        val longitude: Double,
        val doseRate: Double?,
        val countRate: Double?,
        val accuracy: Double = 8.0,
        val timestamp: Long = 1_000,
    )

    private fun createTrackTables(connection: Connection) {
        val file = File("schemas/app.alpha.data.db.AppDatabase/8.json")
        assertTrue(file.exists(), "exported schema missing: ${file.absolutePath}")
        val entities = JSONObject(file.readText())
            .getJSONObject("database")
            .getJSONArray("entities")
        connection.createStatement().use { statement ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                if (table != "track_points" && table != "track_sessions") continue
                statement.execute(
                    entity.getString("createSql").replace("\${TABLE_NAME}", table),
                )
            }
            statement.execute(
                "INSERT INTO track_sessions (id, name, startedAt) VALUES (1, 'test', 0)",
            )
        }
    }

    private fun insert(connection: Connection, fixes: List<Fix>) {
        connection.prepareStatement(
            "INSERT INTO track_points " +
                "(sessionId, timestamp, latitude, longitude, accuracyMeters, doseRate, countRate) " +
                "VALUES (1, ?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            for (fix in fixes) {
                statement.setLong(1, fix.timestamp)
                statement.setDouble(2, fix.latitude)
                statement.setDouble(3, fix.longitude)
                statement.setDouble(4, fix.accuracy)
                if (fix.doseRate == null) {
                    statement.setNull(5, java.sql.Types.REAL)
                } else {
                    statement.setDouble(5, fix.doseRate)
                }
                if (fix.countRate == null) {
                    statement.setNull(6, java.sql.Types.REAL)
                } else {
                    statement.setDouble(6, fix.countRate)
                }
                statement.executeUpdate()
            }
        }
    }

    /** Room binds named parameters; JDBC does not, so inline them verbatim. */
    private fun bind(sql: String, params: Map<String, Any>): String {
        var bound = sql
        for (name in params.keys.sortedByDescending { it.length }) {
            val value = params.getValue(name)
            val literal = when (value) {
                is Boolean -> if (value) "1" else "0"
                else -> value.toString()
            }
            bound = bound.replace(":$name", literal)
        }
        assertTrue(!bound.contains(':'), "unbound parameter in: $bound")
        return bound
    }

    private data class Row(
        val latKey: Int,
        val lonKey: Int,
        val valueKey: Int,
        val count: Int,
        val minValue: Double,
        val maxValue: Double,
        val minTime: Long,
        val maxTime: Long,
    )

    private fun histogram(
        connection: Connection,
        latStep: Double,
        lonStep: Double,
        valueMin: Double,
        valueStep: Double,
        useDose: Boolean = true,
    ): List<Row> {
        val sql = bind(
            TrackGridSql.GRID_HISTOGRAM,
            mapOf(
                "latStepDeg" to latStep,
                "lonStepDeg" to lonStep,
                "useDose" to useDose,
                "valueMin" to valueMin,
                "valueStep" to valueStep,
                "minLatitude" to -90.0,
                "maxLatitude" to 90.0,
                "minLongitude" to -180.0,
                "maxLongitude" to 180.0,
                "maxAccuracyMeters" to maxAccuracy,
                "limit" to TrackGrid.MAX_HISTOGRAM_ROWS,
            ),
        )
        val rows = mutableListOf<Row>()
        connection.createStatement().use { statement ->
            val result = statement.executeQuery(sql)
            while (result.next()) {
                rows += Row(
                    latKey = result.getInt("latKey"),
                    lonKey = result.getInt("lonKey"),
                    valueKey = result.getInt("valueKey"),
                    count = result.getInt("pointCount"),
                    minValue = result.getDouble("minValue"),
                    maxValue = result.getDouble("maxValue"),
                    minTime = result.getLong("minTime"),
                    maxTime = result.getLong("maxTime"),
                )
            }
        }
        return rows
    }

    private fun <T> withDatabase(fixes: List<Fix>, block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTrackTables(connection)
            insert(connection, fixes)
            block(connection)
        }

    @Test
    fun `SQLite bins points into exactly the cells TrackGrid computes`() {
        val step = 0.001
        val fixes = listOf(
            Fix(55.7500, 37.6000, doseRate = 0.00001, countRate = 10.0),
            Fix(55.7509, 37.6009, doseRate = 0.00001, countRate = 11.0),
            Fix(-33.4505, -70.6505, doseRate = 0.00002, countRate = 12.0),
            Fix(-33.4495, -70.6495, doseRate = 0.00002, countRate = 13.0),
        )
        val rows = withDatabase(fixes) { histogram(it, step, step, 0.0, 1.0) }

        val expectedKeys = fixes.map {
            TrackGrid.latKey(it.latitude, step) to TrackGrid.lonKey(it.longitude, step)
        }.toSet()
        assertEquals(expectedKeys, rows.map { it.latKey to it.lonKey }.toSet())
        assertEquals(fixes.size, rows.sumOf { it.count })
        assertEquals(expectedKeys.size, rows.size)
        // Two of the four fixes are less than one step apart, so they share a
        // cell — the grid merges neighbours, in SQL exactly as in Kotlin.
        assertEquals(3, expectedKeys.size)
    }

    @Test
    fun `points of one cell are aggregated with exact count and extremes`() {
        val step = 0.01
        val fixes = listOf(
            Fix(55.7501, 37.6001, doseRate = 0.00001, countRate = 10.0, timestamp = 500),
            Fix(55.7502, 37.6002, doseRate = 0.00003, countRate = 30.0, timestamp = 900),
            Fix(55.7503, 37.6003, doseRate = 0.00002, countRate = 20.0, timestamp = 700),
        )
        val rows = withDatabase(fixes) {
            // One value bin wide enough to hold every value: one row per cell.
            histogram(it, step, step, 0.0, 1.0)
        }
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(3, row.count)
        assertEquals(0.00001, row.minValue, 1e-12)
        assertEquals(0.00003, row.maxValue, 1e-12)
        assertEquals(500, row.minTime)
        assertEquals(900, row.maxTime)
    }

    @Test
    fun `value bins keep one cell ordered and grouped by value`() {
        val step = 0.01
        val bins = TrackGrid.valueBins(min = 0.1f, max = 0.5f, bins = 4)
        val fixes = listOf(0.10, 0.15, 0.30, 0.45).map {
            Fix(55.7501, 37.6001, doseRate = it, countRate = null)
        }
        val rows = withDatabase(fixes) {
            histogram(it, step, step, bins.min.toDouble(), bins.step.toDouble())
        }

        assertEquals(3, rows.size, "0.10 and 0.15 fall inside one bin width")
        assertEquals(fixes.size, rows.sumOf { it.count })
        // Order statistics are read off this histogram, so the bin index must
        // grow with the value — nothing else about its numbering matters.
        val byKey = rows.sortedBy { it.valueKey }
        for (i in 1 until byKey.size) {
            assertTrue(
                byKey[i - 1].maxValue <= byKey[i].minValue,
                "bins overlap in value: ${byKey[i - 1]} then ${byKey[i]}",
            )
        }
    }

    @Test
    fun `imprecise fixes are excluded from the picture and from its numbers`() {
        val fixes = listOf(
            Fix(55.7501, 37.6001, doseRate = 0.00001, countRate = 10.0, accuracy = 8.0),
            Fix(55.7502, 37.6002, doseRate = 0.09, countRate = 900.0, accuracy = 500.0),
        )
        withDatabase(fixes) { connection ->
            val rows = histogram(connection, 0.01, 0.01, 0.0, 1.0)
            assertEquals(1, rows.sumOf { it.count })
            assertEquals(0.00001, rows.single().maxValue, 1e-12)

            val summary = bind(
                TrackGridSql.AREA_SUMMARY,
                mapOf(
                    "useDose" to true,
                    "minLatitude" to -90.0,
                    "maxLatitude" to 90.0,
                    "minLongitude" to -180.0,
                    "maxLongitude" to 180.0,
                    "maxAccuracyMeters" to maxAccuracy,
                ),
            )
            connection.createStatement().use { statement ->
                val result = statement.executeQuery(summary)
                assertTrue(result.next())
                assertEquals(1, result.getInt("pointCount"))
                assertEquals(0.00001, result.getDouble("maxValue"), 1e-12)
            }
        }
    }

    @Test
    fun `the CPS metric switches the column and drops points without it`() {
        val fixes = listOf(
            Fix(55.7501, 37.6001, doseRate = 0.00001, countRate = 10.0),
            Fix(55.7502, 37.6002, doseRate = 0.00002, countRate = null),
        )
        val rows = withDatabase(fixes) {
            histogram(it, 0.01, 0.01, 0.0, 1_000.0, useDose = false)
        }
        assertEquals(1, rows.sumOf { it.count })
        assertEquals(10.0, rows.single().minValue, 1e-9)
    }

    @Test
    fun `the summary describes the full matching set, not a page of it`() {
        val fixes = (0 until 500).map {
            Fix(
                latitude = 55.75 + it * 0.0001,
                longitude = 37.60,
                doseRate = 0.00001 * (it + 1),
                countRate = null,
                timestamp = 1_000L + it,
            )
        }
        withDatabase(fixes) { connection ->
            val sql = bind(
                TrackGridSql.AREA_SUMMARY,
                mapOf(
                    "useDose" to true,
                    "minLatitude" to -90.0,
                    "maxLatitude" to 90.0,
                    "minLongitude" to -180.0,
                    "maxLongitude" to 180.0,
                    "maxAccuracyMeters" to maxAccuracy,
                ),
            )
            connection.createStatement().use { statement ->
                val result = statement.executeQuery(sql)
                assertTrue(result.next())
                assertEquals(500, result.getInt("pointCount"))
                assertEquals(500, result.getInt("valueCount"))
                assertEquals(0.00001, result.getDouble("minValue"), 1e-12)
                assertEquals(0.005, result.getDouble("maxValue"), 1e-12)
                assertEquals(1_000L, result.getLong("firstTime"))
                assertEquals(1_499L, result.getLong("lastTime"))
            }

            // The drawn grid stays a summary of the same set: every point is in.
            val rows = histogram(connection, 0.01, 0.01, 0.0, 1.0)
            assertEquals(500, rows.sumOf { it.count })
        }
    }
}
