package app.alpha.ui.chart

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Договор, который нарушала инерция мини-графика.
 *
 * Сдвиг считается ОТ ТЕКУЩЕГО окна, а не от того, каким оно было в начале
 * жеста. Инерция вызывала обработчик уже после отпускания пальца, держа
 * захваченное «дожестовое» окно, и каждый её шаг возвращал картинку почти
 * туда, откуда её увели: график сдвигался и отскакивал назад.
 *
 * Тест закрепляет само правило: последовательные сдвиги СКЛАДЫВАЮТСЯ.
 */
class PanAccumulationTest {

    private val now = 1_700_000_000_000L
    private val span = 5 * 60_000L
    private val bounds = ViewportBounds(
        edgeMillis = now,
        earliestMillis = now - 24L * 3_600_000L,
        maxSpanMillis = Viewports.MAX_SPAN_MILLIS,
    )

    private fun atEdge() = ChartGesture.of(Viewports.atEdge(span, bounds), bounds)

    @Test
    fun `последовательные сдвиги складываются`() {
        var gesture = atEdge()
        val steps = 6
        val before = gesture.visible.endMillis
        repeat(steps) { gesture = gesture.pan(-0.1f, bounds) }
        val moved = before - gesture.visible.endMillis
        // Шесть шагов по десятой доле окна — примерно шесть десятых окна.
        assertTrue(
            moved >= (span * 0.5).toLong(),
            "накопилось всего $moved мс из ожидаемых ~${(span * 0.6).toLong()}",
        )
    }

    @Test
    fun `сдвиг от устаревшего окна теряет накопленное`() {
        // Тот самый дефект: инерция считала от захваченного окна.
        val start = atEdge()
        var live = start
        repeat(6) { live = live.pan(-0.1f, bounds) }
        val stale = start.pan(-0.1f, bounds)
        assertTrue(
            stale.visible.endMillis > live.visible.endMillis,
            "устаревшее окно должно оказаться правее живого",
        )
    }

    @Test
    fun `сдвиг влево снимает слежение и не возвращает его сам`() {
        var gesture = atEdge().pan(-0.5f, bounds)
        assertTrue(!gesture.visible.followLiveEdge, "слежение осталось включённым")
        var edge = now
        repeat(60) {
            edge += 1_000L
            gesture = gesture.followTick(bounds.copy(edgeMillis = edge))
        }
        assertTrue(
            !gesture.visible.followLiveEdge,
            "окно вернулось к живому краю само",
        )
        assertTrue(
            edge - gesture.visible.endMillis >= 60_000L,
            "окно уехало за живым краем",
        )
    }
}
