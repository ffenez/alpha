package app.radiacode.ui.chart

import app.radiacode.ui.chart.ChartLabelLayout.Label
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Столкновение подписей: остаётся старшая, младшая исчезает целиком.
 *
 * Текст поверх текста хуже отсутствующего текста — пропавшую подпись видно, а
 * слипшуюся человек пытается прочесть.
 */
class ChartLabelLayoutTest {

    @Test
    fun `порог вытесняет подпись оси, а не наоборот`() {
        val labels = listOf(
            Label(topPx = 100f, heightPx = 12f, priority = LabelPriority.AXIS_TICK),
            Label(topPx = 104f, heightPx = 12f, priority = LabelPriority.ALARM_THRESHOLD),
        )
        val visible = ChartLabelLayout.visible(labels)
        assertTrue(1 in visible)
        assertFalse(0 in visible)
    }

    @Test
    fun `непересекающиеся подписи остаются все`() {
        val labels = listOf(
            Label(0f, 12f, LabelPriority.AXIS_TICK),
            Label(40f, 12f, LabelPriority.AXIS_TICK),
            Label(80f, 12f, LabelPriority.ALARM_THRESHOLD),
        )
        assertEquals(setOf(0, 1, 2), ChartLabelLayout.visible(labels))
    }

    @Test
    fun `курсор старше всех`() {
        val labels = listOf(
            Label(50f, 12f, LabelPriority.ALARM_THRESHOLD),
            Label(52f, 12f, LabelPriority.CURRENT_VALUE),
            Label(54f, 12f, LabelPriority.CURSOR_VALUE),
        )
        assertEquals(setOf(2), ChartLabelLayout.visible(labels))
    }

    @Test
    fun `исход не зависит от порядка в списке`() {
        val tick = Label(100f, 12f, LabelPriority.AXIS_TICK)
        val alarm = Label(104f, 12f, LabelPriority.ALARM_THRESHOLD)
        val direct = ChartLabelLayout.visible(listOf(tick, alarm))
        val reversed = ChartLabelLayout.visible(listOf(alarm, tick))
        assertEquals(setOf(1), direct)
        assertEquals(setOf(0), reversed)
    }

    @Test
    fun `подписи в паре пикселей друг от друга считаются слипшимися`() {
        val labels = listOf(
            Label(0f, 12f, LabelPriority.AXIS_TICK),
            Label(13f, 12f, LabelPriority.ALARM_THRESHOLD),
        )
        assertEquals(setOf(1), ChartLabelLayout.visible(labels))
    }
}
