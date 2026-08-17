package app.alpha.service

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionGateTest {

    private val minute = 60_000L

    @Test
    fun `connect opens a session once`() {
        val gate = SessionGate()
        assertEquals(SessionGate.Action.Open, gate.onConnected(0, null))
        assertEquals(SessionGate.Action.None, gate.onConnected(1 * minute, 1 * minute))
    }

    @Test
    fun `disconnect closes at the last sample`() {
        val gate = SessionGate()
        gate.onConnected(0, null)
        assertEquals(
            SessionGate.Action.Close(closeAt = 9 * minute),
            gate.onDisconnected(10 * minute, lastSampleAt = 9 * minute),
        )
        // Nothing left to close.
        assertEquals(SessionGate.Action.None, gate.onDisconnected(11 * minute, 9 * minute))
    }

    @Test
    fun `disconnect without samples closes at now`() {
        val gate = SessionGate()
        gate.onConnected(0, null)
        assertEquals(
            SessionGate.Action.Close(closeAt = 5 * minute),
            gate.onDisconnected(5 * minute, lastSampleAt = null),
        )
    }

    @Test
    fun `brief reconnect keeps the session continuous`() {
        val gate = SessionGate(graceMillis = 5 * minute)
        gate.onConnected(0, null)
        assertEquals(SessionGate.Action.None, gate.onLinkLost(10 * minute))
        // Back within the grace period: same continuous measurement.
        assertEquals(SessionGate.Action.None, gate.onConnected(12 * minute, 10 * minute))
    }

    @Test
    fun `long outage splits sessions at the last sample`() {
        val gate = SessionGate(graceMillis = 5 * minute)
        gate.onConnected(0, null)
        gate.onLinkLost(10 * minute)
        assertEquals(
            SessionGate.Action.Reopen(closeAt = 10 * minute),
            gate.onConnected(60 * minute, lastSampleAt = 10 * minute),
        )
        // The reopened session behaves like a normal open one.
        assertEquals(
            SessionGate.Action.Close(closeAt = 70 * minute),
            gate.onDisconnected(71 * minute, lastSampleAt = 70 * minute),
        )
    }

    @Test
    fun `link lost timestamp is not overwritten by repeated reconnect states`() {
        val gate = SessionGate(graceMillis = 5 * minute)
        gate.onConnected(0, null)
        gate.onLinkLost(10 * minute)
        gate.onLinkLost(14 * minute) // second Reconnecting emission
        // 6 min after the FIRST loss -> outage exceeded grace, split.
        assertEquals(
            SessionGate.Action.Reopen(closeAt = 10 * minute),
            gate.onConnected(16 * minute, lastSampleAt = 10 * minute),
        )
    }

    @Test
    fun `link lost while closed does nothing`() {
        val gate = SessionGate()
        assertEquals(SessionGate.Action.None, gate.onLinkLost(0))
        assertEquals(SessionGate.Action.Open, gate.onConnected(1 * minute, null))
    }

    /**
     * Полевой отчёт: за три часа в одном месте журнал показал восемь записей.
     * Заминка связи — не конец измерения, и порог по умолчанию обязан быть
     * длиннее любой возни с переподключением.
     */
    @Test
    fun `the default grace outlasts a reconnect storm`() {
        val gate = SessionGate()
        gate.onConnected(0, null)

        gate.onLinkLost(10 * minute)
        // Двадцать минут переподключений — это всё ещё одно измерение.
        assertEquals(SessionGate.Action.None, gate.onConnected(30 * minute, 10 * minute))
        assertEquals(30L * minute, SessionGate.DEFAULT_GRACE_MILLIS)
    }

    /**
     * Смена места — настоящая граница записи, в отличие от разрыва связи.
     *
     * Полевой случай: человек ушёл из дома, контекст переключился на «В пути»,
     * карта писала след — а в журнале этой записи не было: она осталась внутри
     * записи «Дом», потому что профиль запоминается один раз, при открытии.
     * Гейт связи об этом не знает и знать не должен: он про СВЯЗЬ, а место
     * меняет служба. Тест держит границу ролей.
     */
    @Test
    fun `the link gate says nothing about the place`() {
        val gate = SessionGate()
        gate.onConnected(0, null)

        // Ни один сигнал связи не закрывает запись сам по себе, пока связь
        // держится: закрытие по смене места делает служба.
        assertEquals(SessionGate.Action.None, gate.onConnected(minute, 0))
        assertEquals(SessionGate.Action.None, gate.onLinkLost(2 * minute))
    }
}
