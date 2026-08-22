package app.alpha.analysis

import app.alpha.data.GainDriftRecord
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Температурный ход шкалы: подгонка обязана находить заложенный наклон и
 * отказываться там, где данных на него нет.
 */
class GainDriftTest {

    /** Наблюдения с известным ходом: −0,15 % на градус от 1,0 при 20 °C. */
    private fun observations(
        slopePerC: Double = -0.0015,
        noise: Double = 0.0,
        temperatures: List<Double> = listOf(15.0, 18.0, 21.0, 24.0, 27.0, 30.0),
    ) = temperatures.mapIndexed { index, t ->
        // Шум знакопеременный и детерминированный: тест обязан быть повторяем.
        val wobble = if (index % 2 == 0) noise else -noise
        GainDriftFit.Point(
            temperatureC = t,
            relative = 1.0 + slopePerC * (t - 20.0) + wobble,
            sigma = 0.0005,
        )
    }

    @Test
    fun `наклон восстанавливается по наблюдениям`() {
        val drift = assertNotNull(GainDriftFit.fit(observations(), 1460.8))
        assertTrue(
            abs(drift.perDegree - (-0.0015)) < 1e-6,
            "наклон найден как ${drift.perDegree}",
        )
        assertTrue(drift.slopeResolved, "заложенный ход обязан отличаться от нуля")
        // Отсчёт идёт от средней температуры наблюдений, а не от нуля.
        assertTrue(abs(drift.referenceC - 22.5) < 1e-6, "опора ${drift.referenceC}")
        assertTrue(
            abs(drift.at(20.0) - 1.0) < 1e-6,
            "при 20 °C ожидалась единица, вышло ${drift.at(20.0)}",
        )
    }

    @Test
    fun `узкий диапазон температур не даёт наклона`() {
        // Три градуса — меньше порога: на таком размахе ход тонет в шуме
        // центроида, и «ноль ± много» лучше не показывать вовсе.
        assertNull(
            GainDriftFit.fit(
                observations(temperatures = listOf(20.0, 21.0, 22.0, 23.0)),
                1460.8,
            ),
        )
    }

    @Test
    fun `трёх наблюдений мало`() {
        assertNull(
            GainDriftFit.fit(observations(temperatures = listOf(15.0, 22.0, 30.0)), 1460.8),
        )
    }

    @Test
    fun `разброс больше заявленных ошибок расширяет неопределённость наклона`() {
        val clean = assertNotNull(GainDriftFit.fit(observations(), 1460.8))
        val scattered = assertNotNull(GainDriftFit.fit(observations(noise = 0.002), 1460.8))
        assertTrue(
            scattered.perDegreeSigma > 2.0 * clean.perDegreeSigma,
            "разброс не отразился: ${scattered.perDegreeSigma} против ${clean.perDegreeSigma}",
        )
    }

    @Test
    fun `нулевой ход честно называется неразличимым`() {
        val drift = assertNotNull(GainDriftFit.fit(observations(slopePerC = 0.0), 1460.8))
        assertTrue(!drift.slopeResolved, "нулевой наклон объявлен различимым")
    }

    @Test
    fun `запись переживает сохранение и чтение`() {
        val drift = assertNotNull(GainDriftFit.fit(observations(), 1460.8))
        val record = GainDriftRecord(drift, deviceSerial = "RC-110-000000", measuredAtMillis = 42L)
        val back = assertNotNull(GainDriftRecord.decode(record.encode()))
        assertEquals(record.deviceSerial, back.deviceSerial)
        assertEquals(record.measuredAtMillis, back.measuredAtMillis)
        assertTrue(abs(back.drift.perDegree - drift.perDegree) < 1e-12)
        assertEquals(drift.points, back.drift.points)
        // Поломанная строка — это «не измерено», а не падение.
        assertNull(GainDriftRecord.decode("k=нет;ref=20"))
    }
}
