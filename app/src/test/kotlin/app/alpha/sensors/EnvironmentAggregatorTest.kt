package app.alpha.sensors

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnvironmentAggregatorTest {

    private fun aggregator() = EnvironmentAggregator(windowMillis = 10_000L)

    @Test
    fun `окно не закрывается раньше срока`() {
        val a = aggregator()
        a.addMagnetic(30f, 40f, 0f, 1_000L)
        assertNull(a.poll(5_000L))
        assertNotNull(a.poll(11_000L))
    }

    @Test
    fun `модуль поля не зависит от поворота телефона`() {
        // Один и тот же вектор, разложенный по осям по-разному, обязан дать
        // одно число — иначе поворот в руке читался бы как аномалия.
        val a = aggregator()
        a.addMagnetic(30f, 40f, 0f, 0L)
        val first = assertNotNull(a.poll(10_000L)).magneticUt

        val b = aggregator()
        b.addMagnetic(0f, 30f, 40f, 0L)
        val second = assertNotNull(b.poll(10_000L)).magneticUt

        assertEquals(50f, first!!, 1e-3f)
        assertEquals(first, second!!, 1e-3f)
    }

    @Test
    fun `разброса при одном отсчёте не существует`() {
        val a = aggregator()
        a.addMagnetic(0f, 0f, 50f, 0L)
        assertNull(assertNotNull(a.poll(10_000L)).magneticSd, "ноль соврал бы про устойчивость")
    }

    @Test
    fun `разброс считается по отсчётам окна`() {
        val a = aggregator()
        // Модули 48, 50, 52 → среднее 50, SD (несмещённая) = 2.
        a.addMagnetic(0f, 0f, 48f, 0L)
        a.addMagnetic(0f, 0f, 50f, 1_000L)
        a.addMagnetic(0f, 0f, 52f, 2_000L)
        val w = assertNotNull(a.poll(10_000L))
        assertEquals(50f, w.magneticUt!!, 1e-3f)
        assertEquals(2f, w.magneticSd!!, 1e-3f)
        assertEquals(3, w.samples)
    }

    @Test
    fun `окно без единого значения не превращается в строку`() {
        val a = aggregator()
        assertNull(a.poll(100_000L), "пустых строк в базе быть не должно")
        assertNull(a.flush(100_000L))
    }

    @Test
    fun `давление усредняется, а не берётся последним`() {
        val a = aggregator()
        a.addPressure(1000f, 0L)
        a.addPressure(1002f, 1_000L)
        assertEquals(1001f, assertNotNull(a.poll(10_000L)).pressureHpa!!, 1e-3f)
    }

    @Test
    fun `остановка службы отдаёт незакрытый хвост`() {
        val a = aggregator()
        a.addPressure(1013f, 0L)
        val tail = assertNotNull(a.flush(3_000L), "хвост окна теряться не должен")
        assertEquals(3_000L, tail.endMillis)
    }

    @Test
    fun `температура батареи держится между окнами`() {
        // Система присылает её редко: обнулять между окнами значило бы терять
        // единственное известное значение.
        val a = aggregator()
        a.setPhoneTemperature(31.4f, 0L)
        a.addMagnetic(0f, 0f, 50f, 1_000L)
        assertEquals(31.4f, assertNotNull(a.poll(10_000L)).phoneTempC!!, 1e-3f)

        a.addMagnetic(0f, 0f, 50f, 11_000L)
        val second = assertNotNull(a.poll(21_000L))
        assertEquals(31.4f, second.phoneTempC!!, 1e-3f)
    }

    @Test
    fun `следующее окно считается с нуля`() {
        val a = aggregator()
        a.addPressure(1000f, 0L)
        a.poll(10_000L)
        a.addPressure(1010f, 11_000L)
        val second = assertNotNull(a.poll(21_000L))
        assertTrue(abs(second.pressureHpa!! - 1010f) < 1e-3f, "старые отсчёты не переносятся")
    }
}
