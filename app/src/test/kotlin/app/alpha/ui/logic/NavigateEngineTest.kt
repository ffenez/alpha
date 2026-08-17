package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Четыре состояния «Наведения», их гистерезис, точка отсчёта и зафиксированный
 * максимум — как машина состояний. Всё детерминировано: ряды синтетические,
 * время задаётся явно, и вывод не зависит от того, когда перерисовался экран.
 */
class NavigateEngineTest {

    private val start = 1_000_000L

    /** Ровный поток [cps] с секундным шагом, [seconds] отсчётов. */
    private fun feed(
        state: NavigateState,
        fromMillis: Long,
        seconds: Int,
        cps: Float,
    ): Pair<NavigateState, Long> {
        var current = state
        var at = fromMillis
        repeat(seconds) {
            current = NavigateEngine.onReading(current, at, cps)
            at += 1_000L
        }
        return current to at - 1_000L
    }

    @Test
    fun `a stationary stream never leaves «без явного изменения»`() {
        var (state, _) = feed(NavigateState(), start, 40, 25f)
        assertEquals(NavigateTrend.NO_CHANGE, state.trend)
        // …и остаётся там, сколько бы окон ни прошло.
        state = feed(state, start + 40_000L, 30, 25f).first
        assertEquals(NavigateTrend.NO_CHANGE, state.trend)
    }

    @Test
    fun `a step up reaches «растёт» and a step back down reaches «падает»`() {
        val (settled, last) = feed(NavigateState(), start, 40, 25f)
        val (risen, afterRise) = feed(settled, last + 1_000L, 6, 80f)
        assertEquals(NavigateTrend.RISING, risen.trend)
        val (fallen, _) = feed(risen, afterRise + 1_000L, 8, 20f)
        assertEquals(NavigateTrend.FALLING, fallen.trend)
    }

    /**
     * Гистерезис: показанное направление переживает окно, которое различия не
     * разрешило. Без этого стрелка мигала бы ровно на краю поля источника —
     * там, где на неё смотрят внимательнее всего.
     */
    @Test
    fun `a shown direction survives a single quiet window`() {
        val (settled, last) = feed(NavigateState(), start, 40, 25f)
        val (risen, afterRise) = feed(settled, last + 1_000L, 3, 80f)
        assertEquals(NavigateTrend.RISING, risen.trend)
        // Стоя на месте, локальное окно догоняет короткое: различия больше нет,
        // но показанное направление держится выдержку отпускания.
        val (held, afterHeld) = feed(risen, afterRise + 1_000L, 4, 80f)
        assertEquals(NavigateTrend.RISING, held.trend)
        assertEquals(NavigateTrend.NO_CHANGE, held.pendingTrend)
        val (released, _) = feed(held, afterHeld + 1_000L, 3, 80f)
        assertEquals(NavigateTrend.NO_CHANGE, released.trend)
    }

    /** Пропавший поток — это факт о данных, и он виден сразу. */
    @Test
    fun `a lost stream falls back to «набираю статистику»`() {
        val (state, last) = feed(NavigateState(), start, 40, 25f)
        assertEquals(NavigateTrend.NO_CHANGE, state.trend)
        val silent = NavigateEngine.onTick(state, last + 60_000L)
        assertEquals(NavigateTrend.COLLECTING, silent.trend)
        assertTrue(silent.trendComparison == null)
    }

    /** Частота перерисовки экрана не имеет права менять вывод. */
    @Test
    fun `extra ticks between readings change nothing`() {
        val (plain, last) = feed(NavigateState(), start, 40, 25f)
        var ticked = NavigateState()
        var at = start
        repeat(40) {
            ticked = NavigateEngine.onReading(ticked, at, 25f)
            ticked = NavigateEngine.onTick(ticked, at + 200L)
            ticked = NavigateEngine.onTick(ticked, at + 400L)
            at += 1_000L
        }
        ticked = NavigateEngine.onTick(ticked, last)
        assertEquals(plain.trend, ticked.trend)
        assertEquals(plain.fast?.counts, ticked.fast?.counts)
    }

    /** Максимум держится после ухода с места и снимается только вручную. */
    @Test
    fun `peak hold keeps the maximum and its instant until it is reset`() {
        val (settled, last) = feed(NavigateState(), start, 40, 25f)
        val (risen, afterRise) = feed(settled, last + 1_000L, 5, 80f)
        val peak = risen.peak
        assertTrue(peak != null && peak.ratePerSecond > 60.0, "$peak")
        val (walkedAway, _) = feed(risen, afterRise + 1_000L, 10, 25f)
        assertEquals(peak, walkedAway.peak)
        assertTrue(NavigateEngine.resetPeak(walkedAway).peak == null)
    }

    /**
     * Точка отсчёта: временная опора текущего прохода. До неё процента нет
     * вовсе, сразу после — нет тоже (окна пересекаются), и он появляется
     * ТОЛЬКО когда тест разрешил различие.
     */
    @Test
    fun `a percentage is printed only once the test resolves a difference`() {
        val (settled, last) = feed(NavigateState(), start, 40, 25f)
        assertTrue(NavigateEngine.referenceDelta(settled) is ReferenceDelta.NoReference)

        val marked = NavigateEngine.mark(settled, last)
        assertTrue(marked.reference != null)
        assertTrue(NavigateEngine.referenceDelta(marked) is ReferenceDelta.Collecting)

        val (same, afterSame) = feed(marked, last + 1_000L, 3, 25f)
        val unresolved = NavigateEngine.referenceDelta(same)
        assertTrue(unresolved is ReferenceDelta.Unresolved, "$unresolved")
        assertTrue(unresolved.low < 1.0 && unresolved.high > 1.0)

        val (louder, _) = feed(same, afterSame + 1_000L, 3, 80f)
        val resolved = NavigateEngine.referenceDelta(louder)
        assertTrue(resolved is ReferenceDelta.Resolved, "$resolved")
        assertTrue(resolved.percent > 100)
        assertTrue(resolved.low > 1.0)
    }

    /** «Запомнить здесь» не трогает ни профиль, ни его обычный фон. */
    @Test
    fun `marking touches nothing but the sweep`() {
        val (settled, last) = feed(NavigateState(), start, 40, 25f)
        val marked = NavigateEngine.mark(settled, last)
        assertEquals(settled.points, marked.points)
        assertEquals(settled.peak, marked.peak)
        assertTrue(marked.reference!!.window.seconds >= NavigateWindows.MIN_LOCAL_SECONDS)
    }
}
