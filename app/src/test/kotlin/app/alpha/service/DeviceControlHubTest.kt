package app.alpha.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Тумблер сигналов прибора не имеет права выглядеть знающим то, чего не знает.
 */
class DeviceControlHubTest {

    @Test
    fun `until something was sent, the device state is unknown`() {
        val hub = DeviceControlHub()
        assertNull(hub.applied.value.sound)
        assertNull(hub.applied.value.vibro)
    }

    @Test
    fun `state changes only after the device confirmed the write`() {
        val hub = DeviceControlHub()
        hub.request(DeviceControlHub.Command.Sound(true))
        // Запрос отправлен, но подтверждения не было — состояние прежнее.
        assertNull(hub.applied.value.sound)

        hub.onApplied(DeviceControlHub.Command.Sound(true))
        assertEquals(true, hub.applied.value.sound)
        // Вторая величина от этого известной не становится.
        assertNull(hub.applied.value.vibro)

        hub.onApplied(DeviceControlHub.Command.Vibro(false))
        assertEquals(false, hub.applied.value.vibro)
        assertEquals(true, hub.applied.value.sound)
    }

    @Test
    fun `a lost link makes the state unknown again`() {
        val hub = DeviceControlHub()
        hub.onApplied(DeviceControlHub.Command.Sound(true))
        hub.onApplied(DeviceControlHub.Command.Vibro(true))
        hub.onDisconnected()
        // Прибор мог быть перенастроен своими кнопками, пока связи не было.
        assertNull(hub.applied.value.sound)
        assertNull(hub.applied.value.vibro)
    }
}
