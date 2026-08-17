package app.alpha.ui.logic

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Свободная высота Главной достаётся главной карточке.
 *
 * До этого страница строилась только сверху вниз, и на телефоне, где содержимое
 * ниже экрана, под ним оставалась пустая полоса — она читалась как незагруженный
 * блок, а не как воздух.
 */
class MonitorLayoutTest {

    @Test
    fun `free height goes to the hero card`() {
        // 891 − 48 − 220 − 6 × 16 = 527 dp.
        assertEquals(
            527.dp,
            MonitorLayout.heroContentMin(
                viewport = 891.dp,
                header = 48.dp,
                below = 220.dp,
                gap = 16.dp,
            ),
        )
    }

    @Test
    fun `a page taller than the screen keeps the card by its content`() {
        // Ноль, а не отрицательная высота: карточка не сжимается, страница
        // прокручивается.
        assertEquals(
            0.dp,
            MonitorLayout.heroContentMin(
                viewport = 600.dp,
                header = 48.dp,
                below = 620.dp,
                gap = 16.dp,
            ),
        )
    }
}
