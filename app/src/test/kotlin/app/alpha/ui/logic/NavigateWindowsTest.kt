package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Окна «Наведения» выбираются из ЦЕЛЕВОЙ ОТНОСИТЕЛЬНОЙ ОШИБКИ, а не из
 * фиксированных секунд: у пуассоновского счёта σ_N/N ≈ 1/√N, поэтому яркому
 * полю для той же точности нужно меньше времени. Пресетов «быстрая/стабильная»
 * нет — параметр назван один раз здесь.
 */
class NavigateWindowsTest {

    @Test
    fun `a target error of 15 percent asks for about 44 events`() {
        // 25 с⁻¹: 44 события — это ≈1,8 с.
        val seconds = NavigateWindows.fastSeconds(25.0)
        assertTrue(seconds > 1.7 && seconds < 1.8, "$seconds")
    }

    @Test
    fun `a brighter field gets a shorter window`() {
        assertTrue(NavigateWindows.fastSeconds(100.0) < NavigateWindows.fastSeconds(10.0))
        assertTrue(NavigateWindows.localSeconds(100.0) < NavigateWindows.localSeconds(10.0))
    }

    @Test
    fun `the window never leaves its clamps, and a dead stream gets the longest`() {
        assertEquals(NavigateWindows.MIN_FAST_SECONDS, NavigateWindows.fastSeconds(10_000.0))
        assertEquals(NavigateWindows.MAX_FAST_SECONDS, NavigateWindows.fastSeconds(0.1))
        assertEquals(NavigateWindows.MAX_FAST_SECONDS, NavigateWindows.fastSeconds(0.0))
        assertEquals(NavigateWindows.MAX_LOCAL_SECONDS, NavigateWindows.localSeconds(-1.0))
    }

    /** Локальное окно всегда длиннее короткого — иначе это одно и то же окно. */
    @Test
    fun `the local window is always the longer one`() {
        for (rate in listOf(0.5, 5.0, 25.0, 120.0, 900.0)) {
            assertTrue(
                NavigateWindows.localSeconds(rate) > NavigateWindows.fastSeconds(rate),
                "rate = $rate",
            )
        }
    }

    /** Кадр ленты не нулевой: иначе спокойный участок рисуется прямой линией. */
    @Test
    fun `the trace frame never starts at zero and never collapses`() {
        val steady = NavigateTraceScale.of(listOf(25f, 25f, 25f), 25f)
        assertTrue(steady.start > 0f)
        assertTrue(steady.endInclusive > steady.start)
        val moving = NavigateTraceScale.of(listOf(20f, 40f), 25f)
        assertTrue(moving.start < 20f && moving.endInclusive > 40f)
        assertTrue(NavigateTraceScale.of(emptyList(), null).endInclusive > 0f)
    }
}
