package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class FreshnessTest {

    @Test
    fun `no samples ever means NoData`() {
        assertEquals(Freshness.NoData, Freshness.of(null, nowMillis = 1_000_000))
    }

    @Test
    fun `fresh up to the 10 s boundary inclusive`() {
        assertEquals(Freshness.Fresh(0), Freshness.of(1_000_000, 1_000_000))
        assertEquals(Freshness.Fresh(10), Freshness.of(1_000_000, 1_010_000))
    }

    @Test
    fun `stale strictly after 10 s`() {
        assertEquals(Freshness.Stale(11), Freshness.of(1_000_000, 1_011_000))
        assertEquals(Freshness.Stale(34), Freshness.of(1_000_000, 1_034_000))
    }

    @Test
    fun `device time base ahead of phone clock clamps to zero age`() {
        // DATA_BUF timestamps derive from the device base time and can run
        // ahead of the wall clock; a negative age must not look stale.
        assertEquals(Freshness.Fresh(0), Freshness.of(1_005_000, 1_000_000))
    }

    @Test
    fun `labels are honest about the stream state`() {
        assertEquals("данных ещё нет", freshnessLabel(Freshness.NoData))
        assertEquals("поток идёт", freshnessLabel(Freshness.Fresh(1)))
        assertEquals("обновлено 7 с назад", freshnessLabel(Freshness.Fresh(7)))
        assertEquals("поток прерван 34 с назад", freshnessLabel(Freshness.Stale(34)))
    }

    /**
     * Голое «0 с» в чипе было загадкой: число секунд без существительного не
     * читается никак. Возраст интересен только когда он заметен.
     */
    @Test
    fun `the chip is silent while the stream is running`() {
        // Ни «0 с», ни слов о том, что и так видно по живому значению: чип
        // появляется только когда данные начали отставать.
        assertEquals(null, freshnessChipLabel(Freshness.Fresh(0)))
        assertEquals(null, freshnessChipLabel(Freshness.Fresh(FRESH_NOW_SECONDS)))
        assertEquals("5 с назад", freshnessChipLabel(Freshness.Fresh(5)))
        assertEquals("прервано 30 с назад", freshnessChipLabel(Freshness.Stale(30)))
        assertEquals("нет данных", freshnessChipLabel(Freshness.NoData))
    }
}
