package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Один переключатель на три состояния: обычный → фон → −фон → обычный.
 *
 * Пара флагов «рисую фон» и «вычитаю фон» допускала состояние, в котором одни
 * и те же импульсы использовались дважды; состояние одно, и такого сочетания в
 * нём не существует.
 */
class SpectrumBackgroundViewTest {

    @Test
    fun `tapping walks the three states in order`() {
        var view = SpectrumBackgroundView.NONE
        view = view.next()
        assertEquals(SpectrumBackgroundView.OVERLAY, view)
        view = view.next()
        assertEquals(SpectrumBackgroundView.SUBTRACT, view)
        view = view.next()
        assertEquals(SpectrumBackgroundView.NONE, view)
    }

    @Test
    fun `overlay and subtraction never hold at once`() {
        for (view in SpectrumBackgroundView.entries) {
            assertTrue(!(view.overlay && view.subtract), "$view")
        }
    }

    /** Вид пересобирается из флагов, которые уезжают в полноэкранный режим. */
    @Test
    fun `the view survives the trip through the flags`() {
        for (view in SpectrumBackgroundView.entries) {
            assertEquals(
                view,
                SpectrumViewOptions(
                    minusBackground = view.subtract,
                    overlayBackground = view.overlay,
                ).backgroundView(),
            )
        }
    }
}
