package app.radiacode.ui.logic

import app.radiacode.device.ConnectionState
import app.radiacode.device.DeviceInfo
import app.radiacode.device.FwVersion
import app.radiacode.ui.text.RuStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Полевое требование: «прервано N с назад» не имеет права жить бесконечно как
 * основной статус. После первой минуты важно, что связи нет, а не что её нет
 * уже семьсот сорок три секунды.
 */
class StreamStateTest {

    private val now = 1_700_000_000_000L

    private val connected = ConnectionState.Connected(
        DeviceInfo(
            address = "AA:BB:CC:DD:EE:FF",
            serialNumber = "RC-110-000042",
            firmware = FwVersion(4, 9, "", 4, 8, ""),
            spectrumFormatVersion = 1,
            configurationLines = emptyList(),
        ),
    )

    private fun state(ageSeconds: Long?, connection: ConnectionState = connected) =
        StreamState.of(ageSeconds?.let { now - it * 1000L }, now, connection)

    @Test
    fun `a running stream says nothing at all`() {
        assertEquals(StreamState.Live, state(0))
        assertEquals(StreamState.Live, state(StreamState.LIVE_AGE_SECONDS))
        // Молчание — это и есть сообщение «всё идёт»; надпись в живом
        // состоянии человек учится не замечать.
        assertNull(streamStatusLine(StreamState.Live, RuStrings))
        assertNull(streamAgeLine(StreamState.Live, RuStrings))
    }

    @Test
    fun `a short stumble names its seconds`() {
        val stale = state(8)
        assertEquals(StreamState.Stale(8), stale)
        assertEquals("нет новых данных · 8 с", streamStatusLine(stale, RuStrings))
        // Возраст уже назван главной строкой — второй раз мелким шрифтом не
        // повторяется.
        assertNull(streamAgeLine(stale, RuStrings))
    }

    @Test
    fun `after the timeout the state becomes stable and the age steps aside`() {
        val lost = state(StreamState.LOST_AFTER_SECONDS + 1)
        assertTrue(lost is StreamState.Disconnected)
        val line = streamStatusLine(lost, RuStrings)
        // Главная строка — состояние, а не счётчик: секунд в ней нет.
        assertTrue(line != null && line.none { it.isDigit() }, "$line")
        assertEquals("последнее измерение 1 мин назад", streamAgeLine(lost, RuStrings))

        // И через час она та же самая, а не «прервано 3600 с назад».
        val later = state(3_600)
        assertEquals(line, streamStatusLine(later, RuStrings))
    }

    @Test
    fun `reconnecting outranks the age`() {
        // Данные вот-вот пойдут — ругаться на разрыв в этот момент незачем.
        assertEquals(StreamState.Reconnecting, state(500, ConnectionState.Connecting(attempt = 1)))
        assertEquals(
            StreamState.Reconnecting,
            state(500, ConnectionState.Reconnecting(attempt = 2, delayMillis = 1_000L)),
        )
        // …и молчит об этом: переподключение — работа приложения, а не
        // состояние прибора, и человеку от неё ничего не требуется. О том, что
        // связи сейчас нет, говорит цвет точки в шапке.
        assertNull(streamStatusLine(StreamState.Reconnecting, RuStrings))
    }

    @Test
    fun `a lost link is not called an interrupted stream when nothing was measured`() {
        // «Данные прервались» — утверждение о том, что они шли; если измерений
        // не было вовсе, это неправда.
        val never = state(null, ConnectionState.Disconnected)
        assertEquals(StreamState.Disconnected(null), never)
        assertEquals("нет текущих данных", streamStatusLine(never, RuStrings))
        assertNull(streamAgeLine(never, RuStrings))
    }

    @Test
    fun `recovery returns to live by itself`() {
        // Возврат в Live — следствие свежего отсчёта, а не отдельного действия:
        // никакого «обновить» и никакого сворачивания приложения.
        assertTrue(state(120) is StreamState.Disconnected)
        assertEquals(StreamState.Live, state(0))
    }

    @Test
    fun `a timestamp slightly ahead of the phone clock is not a negative age`() {
        // База времени прибора может опережать часы телефона на доли секунды.
        assertEquals(StreamState.Live, StreamState.of(now + 900L, now, connected))
    }
}
