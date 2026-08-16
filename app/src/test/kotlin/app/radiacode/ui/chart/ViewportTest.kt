package app.radiacode.ui.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Окно графика — непрерывное состояние, а не ступень лестницы.
 *
 * Здесь проверяется то, что на приборе видно рукой, но нельзя доказать глазом:
 * точка под пальцами остаётся на месте при щипке, окно не уезжает в будущее и
 * не улетает за начало истории, слежение за живым краем включается и гаснет
 * само.
 */
class ViewportTest {

    private val now = 1_700_000_000_000L
    private val bounds = ViewportBounds(edgeMillis = now)

    @Test
    fun `окно у края следит за живым краем`() {
        val v = Viewports.atEdge(5 * 60_000L, bounds)
        assertEquals(now, v.endMillis)
        assertEquals(now - 5 * 60_000L, v.startMillis)
        assertTrue(v.followLiveEdge)
    }

    @Test
    fun `такт слежения двигает край и сохраняет длину`() {
        val v = Viewports.atEdge(5 * 60_000L, bounds)
        val later = ViewportBounds(edgeMillis = now + 7_000L)
        val next = Viewports.followTick(v, later)
        assertEquals(now + 7_000L, next.endMillis)
        assertEquals(v.spanMillis, next.spanMillis)
    }

    @Test
    fun `не следящее окно такт не трогает`() {
        val v = Viewports.atEdge(5 * 60_000L, bounds).copy(followLiveEdge = false)
        val next = Viewports.followTick(v, ViewportBounds(edgeMillis = now + 60_000L))
        assertEquals(v, next)
    }

    @Test
    fun `сдвиг в прошлое выключает слежение`() {
        val v = Viewports.atEdge(10 * 60_000L, bounds)
        val panned = Viewports.pan(v, -0.5f, bounds)
        assertFalse(panned.followLiveEdge)
        assertEquals(v.spanMillis, panned.spanMillis)
        assertEquals(now - 5 * 60_000L, panned.endMillis)
    }

    @Test
    fun `возврат к краю включает слежение сам`() {
        val v = Viewports.pan(Viewports.atEdge(10 * 60_000L, bounds), -0.5f, bounds)
        val back = Viewports.pan(v, 0.5f, bounds)
        assertTrue(back.followLiveEdge)
        assertEquals(now, back.endMillis)
    }

    @Test
    fun `окно не уезжает правее края`() {
        val v = Viewports.atEdge(10 * 60_000L, bounds)
        val forward = Viewports.pan(v, 3f, bounds)
        assertEquals(now, forward.endMillis)
        assertEquals(v.spanMillis, forward.spanMillis)
    }

    @Test
    fun `левее начала истории уезжает не дальше половины окна`() {
        val earliest = now - 60 * 60_000L
        val limited = ViewportBounds(edgeMillis = now, earliestMillis = earliest)
        val v = Viewports.atEdge(10 * 60_000L, limited)
        val far = Viewports.pan(v, -100f, limited)
        assertEquals(earliest - 5 * 60_000L, far.startMillis)
        assertEquals(v.spanMillis, far.spanMillis)
    }

    @Test
    fun `щипок оставляет время под пальцами на месте`() {
        val v = Viewports.atEdge(60 * 60_000L, bounds)
        val focus = 0.25f
        val under = v.timeAt(focus)
        val zoomed = Viewports.zoom(v, factor = 2f, focusFraction = focus, bounds = bounds)
        // Приблизили вдвое — окно вдвое короче, а точка под пальцами та же.
        assertEquals(30 * 60_000L, zoomed.spanMillis)
        assertEquals(under, zoomed.timeAt(focus))
    }

    @Test
    fun `щипок даёт любое окно, а не ступень лестницы`() {
        val v = Viewports.atEdge(5 * 60_000L, bounds)
        val zoomed = Viewports.zoom(v, factor = 1.13f, focusFraction = 0.5f, bounds = bounds)
        assertEquals((5 * 60_000L / 1.13f).toLong(), zoomed.spanMillis)
    }

    @Test
    fun `щипок не выводит окно за пределы величины`() {
        val limited = ViewportBounds(edgeMillis = now, maxSpanMillis = 6L * 3_600_000L)
        val v = Viewports.atEdge(3L * 3_600_000L, limited)
        val out = Viewports.zoom(v, factor = 0.01f, focusFraction = 0.5f, bounds = limited)
        assertEquals(6L * 3_600_000L, out.spanMillis)
        val deep = Viewports.zoom(v, factor = 1000f, focusFraction = 0.5f, bounds = limited)
        assertEquals(Viewports.MIN_SPAN_MILLIS, deep.spanMillis)
    }

    @Test
    fun `отдаление у живого края оставляет слежение включённым`() {
        val v = Viewports.atEdge(5 * 60_000L, bounds)
        val out = Viewports.zoom(v, factor = 0.5f, focusFraction = 0.5f, bounds = bounds)
        assertTrue(out.followLiveEdge)
        assertEquals(now, out.endMillis)
    }

    @Test
    fun `выбор пресета ставит окно у края`() {
        val v = Viewports.pan(Viewports.atEdge(5 * 60_000L, bounds), -2f, bounds)
        val preset = Viewports.withSpan(v, 3_600_000L, bounds)
        assertEquals(3_600_000L, preset.spanMillis)
        assertEquals(now, preset.endMillis)
        assertTrue(preset.followLiveEdge)
    }

    @Test
    fun `ручной масштаб оси переживает жесты по времени`() {
        val v = Viewports.atEdge(5 * 60_000L, bounds).copy(yMode = YMode.MANUAL)
        assertEquals(YMode.MANUAL, Viewports.pan(v, -0.3f, bounds).yMode)
        assertEquals(YMode.MANUAL, Viewports.zoom(v, 2f, 0.5f, bounds).yMode)
        assertEquals(YMode.MANUAL, Viewports.jumpToEdge(v, bounds).yMode)
    }

    @Test
    fun `исторический край — конец сессии, а не сейчас`() {
        val sessionEnd = now - 24 * 3_600_000L
        val session = ViewportBounds(edgeMillis = sessionEnd)
        val v = Viewports.atEdge(3_600_000L, session)
        assertEquals(sessionEnd, v.endMillis)
        assertEquals(sessionEnd, Viewports.pan(v, 5f, session).endMillis)
    }
}
