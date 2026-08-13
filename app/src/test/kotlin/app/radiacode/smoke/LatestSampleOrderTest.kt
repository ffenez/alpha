package app.radiacode.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.radiacode.data.db.SampleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Полевой дефект: «нет новых данных · 30 с · 31 с» при зелёном кружке связи, и
 * живые значения, обновляющиеся рывками.
 *
 * Корень — определение «последнего показания». База времени прибора ИЗМЕРЯЕТСЯ
 * по ходу сеанса и может уехать назад; после такого сдвига свежие записи несут
 * метки МЕНЬШЕ уже лежащих в таблице, и запрос `ORDER BY timestamp DESC`
 * продолжает отдавать давнюю строку — рекорд по метке вместо последнего
 * показания. Экран честно считал её возраст и говорил «нет новых данных»,
 * пока текущее время не догоняло старую метку.
 *
 * Тест держит инвариант на уровне БД: последнее показание — последнее
 * ЗАПИСАННОЕ, при любой поправке часов.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LatestSampleOrderTest {

    private fun sample(timestamp: Long, countRate: Float) = SampleEntity(
        timestamp = timestamp,
        doseRate = 0.0004f,
        doseRateErr = 15f,
        countRate = countRate,
        countRateErr = 10f,
        flags = 0,
        realTimeFlags = 0,
    )

    @Test
    fun `the latest sample follows insertion order, not the largest timestamp`() = runBlocking {
        val graph = Smoke.graph()
        val dao = graph.database.sampleDao()
        val now = 1_700_000_000_000L

        // Записано с завышенной базой: метка уехала на минуту в будущее.
        dao.insertAll(listOf(sample(now + 60_000L, countRate = 10f)))
        // Поправка опустила базу — следующее показание встало ПЕРЕД предыдущим.
        dao.insertAll(listOf(sample(now, countRate = 25f)))

        val latest = graph.measurementRepository.latestSample().first()
        assertEquals(25f, latest?.countRate)
        graph.database.close()
    }
}
