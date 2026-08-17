package app.alpha.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Автоматический срез — второе место, где данные исчезают, поэтому правило
 * держится тестом: умолчание «хранить всё», нижняя граница не проламывается.
 */
class RawRetentionTest {

    @Test
    fun `the default keeps everything`() {
        assertNull(RawRetention.cutoffMillis(1_000_000L, RawRetention.KEEP_ALL_DAYS))
        assertNull(RawRetention.cutoffMillis(1_000_000L, -5))
    }

    @Test
    fun `the cutoff cannot undercut the baseline window`() {
        // Обычный фон читает сырые корзины за 14 суток: срез короче MIN_DAYS
        // ломал бы статистику мест, поэтому любое меньшее значение поднимается.
        assertEquals(RawRetention.MIN_DAYS, RawRetention.sanitize(1))
        assertEquals(RawRetention.MIN_DAYS, RawRetention.sanitize(14))
        assertEquals(90, RawRetention.sanitize(90))
    }

    @Test
    fun `the cutoff is measured in whole days back from now`() {
        val now = 100L * 24 * 3_600_000L
        assertEquals(
            now - 30L * 24 * 3_600_000L,
            RawRetention.cutoffMillis(now, 30),
        )
    }
}
