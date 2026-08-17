package app.alpha.ui.chart

import app.alpha.ui.logic.ChartRange
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Контекст графика: единственное, чем отличаются четыре входа в один экран.
 *
 * Навигация переживает поворот экрана и смерть процесса, поэтому хранит СТРОКУ;
 * здесь проверяется, что из строки и диапазона восстанавливается ровно тот
 * контекст, из которого график открывали, — и что невозможное состояние
 * («сессия без отрезка времени») не притворяется возможным.
 */
class ChartContextTest {

    private val range = ChartRange(1_700_000_000_000L, 1_700_000_600_000L)

    @Test
    fun `идентификатор восстанавливает контекст`() {
        for (context in listOf(
            ChartContext.Live,
            ChartContext.Search,
            ChartContext.Session(range),
            ChartContext.Route(range),
        )) {
            val restored = ChartContexts.of(
                ChartContexts.id(context),
                context.range?.fromMillis,
                context.range?.toMillis,
            )
            assertEquals(context, restored)
        }
    }

    @Test
    fun `сессия без отрезка времени становится живым графиком`() {
        assertEquals(ChartContext.Live, ChartContexts.of(ChartContexts.SESSION, null, null))
        assertEquals(ChartContext.Live, ChartContexts.of(ChartContexts.ROUTE, null, null))
    }

    @Test
    fun `неизвестный идентификатор с диапазоном читается как сессия`() {
        assertEquals(
            ChartContext.Session(range),
            ChartContexts.of("что-то другое", range.fromMillis, range.toMillis),
        )
    }

    @Test
    fun `у живого края и Поиска диапазона нет`() {
        assertEquals(null, ChartContext.Live.range)
        assertEquals(null, ChartContext.Search.range)
    }

    @Test
    fun `маршрут и сессия — разные контексты на одном отрезке`() {
        // Один и тот же отрезок времени, но чип возврата обязан называть
        // разное: маршрут — не сессия, хотя оба стоят на прошлом.
        assertEquals(false, ChartContext.Route(range) == ChartContext.Session(range))
    }
}
