package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Окно графика как состояние, а не как поведение двух экранов по отдельности.
 *
 * Карточка Главной и полноэкранный график показывают одни и те же измерения, и
 * пока каждый вёл своё окно сам, возможна была картина «большой живой, мелкий
 * замерший»: данные общие, край двигался у одного.
 */
class ChartViewportTest {

    private val now = 1_700_000_000_000L
    private val fiveMinutes = ChartWindows.PERIODS.indexOfFirst { it.second == 5 * 60_000L }

    @Test
    fun `while following, the right edge is now`() {
        val viewport = ChartViewport.atLiveEdge(fiveMinutes, now)

        val window = viewport.window(now + 30_000)

        assertEquals(now + 30_000, window.toMillis)
        assertEquals(5 * 60_000L, window.spanMillis)
    }

    @Test
    fun `a pinch moves one step at a time`() {
        // Один плавный щипок не имеет права пролететь всю лестницу: ширина
        // колонки и метод квантилей должны меняться предсказуемо.
        val viewport = ChartViewport.atLiveEdge(fiveMinutes, now)

        val closer = ChartViewport.zoom(viewport, scale = 4f, nowMillis = now)

        assertEquals(fiveMinutes - 1, closer.stepIndex)
    }

    @Test
    fun `a small pinch changes nothing`() {
        // Дрожание руки не должно переключать ступень.
        val viewport = ChartViewport.atLiveEdge(fiveMinutes, now)

        assertEquals(viewport, ChartViewport.zoom(viewport, scale = 1.1f, nowMillis = now))
        assertEquals(viewport, ChartViewport.zoom(viewport, scale = 0.95f, nowMillis = now))
    }

    @Test
    fun `zoom keeps the moment, not the place in the ladder`() {
        // Приближают, чтобы разглядеть ТО ЖЕ время, а не чтобы уехать.
        val panned = ChartViewport.pan(
            ChartViewport.atLiveEdge(fiveMinutes, now),
            fractionOfWindow = 2f,
            nowMillis = now,
        )

        val zoomed = ChartViewport.zoom(panned, scale = 4f, nowMillis = now)

        assertEquals(panned.endMillis, zoomed.endMillis)
        assertFalse(zoomed.follow)
    }

    @Test
    fun `panning into the past switches following off`() {
        val viewport = ChartViewport.atLiveEdge(fiveMinutes, now)

        val panned = ChartViewport.pan(viewport, fractionOfWindow = 0.5f, nowMillis = now)

        assertFalse(panned.follow, "график вырывался бы из-под пальца")
        assertEquals(now - 150_000L, panned.endMillis)
    }

    @Test
    fun `coming back to the edge switches following on again`() {
        // Отдельного действия для этого не нужно: возврат к «сейчас» и есть
        // просьба следить.
        val panned = ChartViewport.pan(
            ChartViewport.atLiveEdge(fiveMinutes, now),
            fractionOfWindow = 0.5f,
            nowMillis = now,
        )

        val back = ChartViewport.pan(panned, fractionOfWindow = -0.5f, nowMillis = now)

        assertTrue(back.follow)
    }

    @Test
    fun `the window never runs past now`() {
        // Будущее нельзя посмотреть даже жестом.
        val viewport = ChartViewport.atLiveEdge(fiveMinutes, now)

        val forward = ChartViewport.pan(viewport, fractionOfWindow = -5f, nowMillis = now)

        assertEquals(now, forward.endMillis)
        assertTrue(forward.follow)
    }

    @Test
    fun `jumping to now restores following`() {
        val panned = ChartViewport.pan(
            ChartViewport.atLiveEdge(fiveMinutes, now),
            fractionOfWindow = 3f,
            nowMillis = now,
        )

        val live = ChartViewport.jumpToNow(panned, now)

        assertTrue(live.follow)
        assertEquals(now, live.window(now).toMillis)
    }

    @Test
    fun `the ladder cannot be walked off either end`() {
        val shortest = ChartViewport.atLiveEdge(0, now)
        val longest = ChartViewport.atLiveEdge(ChartWindows.PERIODS.lastIndex, now)

        assertEquals(0, ChartViewport.zoom(shortest, scale = 4f, nowMillis = now).stepIndex)
        assertEquals(
            ChartWindows.PERIODS.lastIndex,
            ChartViewport.zoom(longest, scale = 0.1f, nowMillis = now).stepIndex,
        )
    }

    @Test
    fun `a panned window is what the loader must ask the database for`() {
        // Полевой дефект: карточка грузила ЖИВОЕ окно, а рисовала то, куда его
        // увели пальцем. Сдвиг уводил картинку в диапазон, который никто не
        // читал, и карточка пустела с надписью «накапливаем измерения» при
        // полной базе. Окно кадра и окно загрузки — одно и то же число.
        val viewport = ChartViewport.pan(
            ChartViewport.atLiveEdge(fiveMinutes, now),
            fractionOfWindow = 4f,
            nowMillis = now,
        )

        val window = viewport.window(now)

        assertFalse(viewport.follow)
        assertEquals(now - 4 * 5 * 60_000L, window.toMillis)
        assertEquals(5 * 60_000L, window.spanMillis)
        // И «сейчас» на этом окне уже не правый край: подпись обязана это знать.
        assertTrue(window.toMillis < now)
    }

    @Test
    fun `a pinch arrives frame by frame and still moves a step`() {
        // Полевой дефект: большой график не масштабировался пальцами вовсе.
        // `detectTransformGestures` отдаёт множитель ЗА КАДР — за событие
        // пальцы расходятся на проценты, — и порог «в полтора раза» не
        // срабатывал ни разу.
        val pinch = ChartViewport.PinchAccumulator()

        val beforeThreshold = (1..8).map { pinch.add(1.02f) }
        assertTrue(beforeThreshold.all { it == 0 }, "$beforeThreshold")

        // 1,02^21 ≈ 1,52 — на этом кадре порог перейден.
        var step = 0
        repeat(13) { if (step == 0) step = pinch.add(1.02f) }
        assertEquals(-1, step)
    }

    @Test
    fun `after a step the count starts again`() {
        // Иначе один долгий щипок пролетел бы всю лестницу.
        val pinch = ChartViewport.PinchAccumulator()

        assertEquals(-1, pinch.add(2f))
        assertEquals(0, pinch.add(1.2f))
        assertEquals(-1, pinch.add(1.4f))
    }

    @Test
    fun `pinching the other way steps the other way`() {
        val pinch = ChartViewport.PinchAccumulator()

        assertEquals(1, pinch.add(0.5f))
    }

    @Test
    fun `a released finger does not carry its motion into the next gesture`() {
        val pinch = ChartViewport.PinchAccumulator()

        pinch.add(1.4f)
        pinch.reset()

        assertEquals(0, pinch.add(1.2f))
    }
}
