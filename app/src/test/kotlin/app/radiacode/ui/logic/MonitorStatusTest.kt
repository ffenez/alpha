package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class MonitorStatusTest {

    @Test
    fun `below threshold is normal`() {
        assertEquals(MonitorStatus.NORMAL, MonitorStatus.of(0.12f, 0.30f))
    }

    @Test
    fun `at and above threshold is elevated`() {
        assertEquals(MonitorStatus.ABOVE_THRESHOLD, MonitorStatus.of(0.30f, 0.30f))
        assertEquals(MonitorStatus.ABOVE_THRESHOLD, MonitorStatus.of(1.5f, 0.30f))
    }

    @Test
    fun `no reading is unknown`() {
        assertEquals(MonitorStatus.UNKNOWN, MonitorStatus.of(null, 0.30f))
    }

    @Test
    fun `wording never claims safety and names the threshold`() {
        assertEquals("Фон в норме", statusWording(MonitorStatus.NORMAL, 0.30f))
        assertEquals(
            "Выше порога 0.30 мкЗв/ч",
            statusWording(MonitorStatus.ABOVE_THRESHOLD, 0.30f),
        )
        assertEquals("Нет данных", statusWording(MonitorStatus.UNKNOWN, 0.30f))
    }

    @Test
    fun `readings format with two stable decimals`() {
        assertEquals("0.12", formatMicroSv(0.1234f))
        assertEquals("1.80", formatMicroSv(1.8f))
        assertEquals("0.00", formatMicroSv(0f))
    }
}
