package app.alpha.ui.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Режим прибора — единственный источник ответа «что сейчас спрашивают».
 * Раньше это были вкладка и переключатель внутри неё; их расхождение и
 * ловит этот тест.
 */
class InstrumentModeTest {

    @Test
    fun `an unknown or missing mode opens observation`() {
        // Наблюдение ничего не требует от человека — безопасное состояние
        // после сбоя настройки.
        assertEquals(InstrumentMode.OBSERVE, InstrumentMode.of(null))
        assertEquals(InstrumentMode.OBSERVE, InstrumentMode.of("navigate"))
        assertEquals(InstrumentMode.OBSERVE, InstrumentMode.of(""))
    }

    @Test
    fun `the id survives a round trip through storage`() {
        for (mode in InstrumentMode.entries) {
            assertEquals(mode, InstrumentMode.of(mode.id))
        }
    }

    @Test
    fun `the order follows the work, not the alphabet`() {
        // Сначала смотрят на место, потом ищут в нём источник. Третьего режима
        // нет: проверка — фаза поиска, а не отдельный вопрос.
        assertEquals(
            listOf(InstrumentMode.OBSERVE, InstrumentMode.SEARCH),
            InstrumentMode.entries,
        )
    }

    @Test
    fun `the retired verify mode falls back to observation`() {
        // Настройка, сохранённая до объединения, не должна ни падать, ни
        // открывать режим, которого больше нет.
        assertEquals(InstrumentMode.OBSERVE, InstrumentMode.of("verify"))
    }
}
