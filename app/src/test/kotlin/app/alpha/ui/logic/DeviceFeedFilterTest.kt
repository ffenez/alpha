package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Отбор журнала по прибору: главное здесь — что делает фильтр с записями, у
 * которых прибор неизвестен.
 */
class DeviceFeedFilterTest {

    private data class Row(val name: String, val device: String?)

    private val rows = listOf(
        Row("снимок дачи", "RC-110-000002"),
        Row("сеанс дома", "RC-110-000001"),
        Row("старая запись", null),
    )

    @Test
    fun `без выбора прибора показаны все записи`() {
        assertEquals(rows, DeviceFeedFilter.select(rows, serial = null) { it.device })
    }

    @Test
    fun `выбранный прибор оставляет только свои записи`() {
        val shown = DeviceFeedFilter.select(rows, serial = "RC-110-000001") { it.device }
        assertEquals(listOf(rows[1]), shown)
    }

    @Test
    fun `запись без пометки прибора не приписывается никому`() {
        // Показать её при выбранном приборе значило бы сказать о данных
        // больше, чем о них известно.
        val shown = DeviceFeedFilter.select(rows, serial = "RC-110-000002") { it.device }
        assertEquals(listOf(rows[0]), shown)
    }

    @Test
    fun `прибор без записей даёт пустой список, а не все записи`() {
        val shown = DeviceFeedFilter.select(rows, serial = "RC-103-000009") { it.device }
        assertEquals(emptyList(), shown)
    }
}
