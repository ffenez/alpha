package app.radiacode.data.db

import app.radiacode.data.SpectrumBlob
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

/**
 * Восстановление картинки из базы (ADR 007) проверяется на НАСТОЯЩЕМ SQLite и
 * на том самом SQL, который стоит в аннотации DAO ([SpectrogramSql]) — иначе
 * тест проверял бы пересказ запроса, а не запрос.
 */
class SpectrogramSqlTest {

    private fun createTable(connection: Connection) {
        val file = File("schemas/app.radiacode.data.db.AppDatabase/11.json")
        assertTrue(file.exists(), "exported schema missing: ${file.absolutePath}")
        val entities = JSONObject(file.readText())
            .getJSONObject("database")
            .getJSONArray("entities")
        connection.createStatement().use { statement ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                if (table != "spectrogram_slices") continue
                statement.execute(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
    }

    private fun insert(
        connection: Connection,
        startMillis: Long,
        durationMillis: Long,
        counts: Int,
        scheme: String = "SPECTROGRAM_96_V1",
    ) {
        connection.prepareStatement(
            "INSERT INTO spectrogram_slices (startMillis, endMillis, durationMillis, schemeId, " +
                "bandCount, counts, cps, doseMicroSvH, sliceCount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)",
        ).use { statement ->
            statement.setLong(1, startMillis)
            statement.setLong(2, startMillis + durationMillis)
            statement.setLong(3, durationMillis)
            statement.setString(4, scheme)
            statement.setInt(5, 2)
            statement.setBytes(6, SpectrumBlob.encode(listOf(counts, counts * 2)))
            statement.setNull(7, java.sql.Types.REAL)
            statement.setNull(8, java.sql.Types.REAL)
            statement.executeUpdate()
        }
    }

    /** Room binds named parameters; JDBC does not, so inline them verbatim. */
    private fun bind(sql: String, params: Map<String, Long>): String {
        var bound = sql
        for (name in params.keys.sortedByDescending { it.length }) {
            bound = bound.replace(":$name", params.getValue(name).toString())
        }
        assertTrue(!bound.contains(':'), "unbound parameter in: $bound")
        return bound
    }

    private fun window(
        connection: Connection,
        from: Long,
        to: Long,
        limit: Long = 100,
    ): List<Pair<Long, List<Int>>> {
        val sql = bind(SpectrogramSql.WINDOW, mapOf("from" to from, "to" to to, "limit" to limit))
        val result = mutableListOf<Pair<Long, List<Int>>>()
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery(sql)
            while (rows.next()) {
                result += rows.getLong("startMillis") to
                    SpectrumBlob.decode(rows.getBytes("counts"))
            }
        }
        return result
    }

    @Test
    fun `the window returns every slice that overlaps it, newest first`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTable(connection)
            // Срез, НАЧАВШИЙСЯ до окна, но закончившийся внутри, — часть
            // картинки: обрезав его, экран показал бы пустоту там, где
            // измерение шло.
            insert(connection, startMillis = 0L, durationMillis = 30_000L, counts = 7)
            insert(connection, startMillis = 30_000L, durationMillis = 30_000L, counts = 8)
            insert(connection, startMillis = 60_000L, durationMillis = 30_000L, counts = 9)
            // Совсем старый срез в окно не попадает.
            insert(connection, startMillis = -600_000L, durationMillis = 30_000L, counts = 1)

            val rows = window(connection, from = 20_000L, to = 90_000L)
            assertEquals(listOf(60_000L, 30_000L, 0L), rows.map { it.first })
            // Счёт возвращается как записан: блоб — тот же кодек, что у снимков.
            assertEquals(listOf(9, 18), rows.first().second)
        }
    }

    @Test
    fun `the row cap keeps the newest slices, not the oldest`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTable(connection)
            for (i in 0 until 10) {
                insert(connection, startMillis = i * 5_000L, durationMillis = 5_000L, counts = i)
            }
            val rows = window(connection, from = 0L, to = 60_000L, limit = 3)
            // Окно смотрят от «сейчас» назад: крышка обязана срезать дальний
            // край, иначе на экране была бы старая история без живого края.
            assertEquals(listOf(45_000L, 40_000L, 35_000L), rows.map { it.first })
        }
    }

    @Test
    fun `the range query feeds compaction in ascending order`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTable(connection)
            for (i in 0 until 4) {
                insert(connection, startMillis = i * 30_000L, durationMillis = 30_000L, counts = 1)
            }
            val sql = bind(
                SpectrogramSql.RANGE,
                mapOf("from" to 0L, "to" to 90_000L, "limit" to 100L),
            )
            val starts = mutableListOf<Long>()
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(sql)
                while (rows.next()) starts += rows.getLong("startMillis")
            }
            // Полуинтервал: срез, начинающийся ровно в `to`, принадлежит
            // следующему куску, иначе он попал бы в оба и слился дважды.
            assertEquals(listOf(0L, 30_000L, 60_000L), starts)
        }
    }
}
