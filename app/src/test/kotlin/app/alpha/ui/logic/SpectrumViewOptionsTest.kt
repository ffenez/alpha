package app.alpha.ui.logic

import app.alpha.analysis.EnergyWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Вход в полноэкранный режим не имеет права подменить картинку: то, что
 * человек видел на вкладке, обязано доехать до полного экрана целиком.
 */
class SpectrumViewOptionsTest {

    @Test
    fun `the view travels unchanged`() {
        val window = EnergyWindow(120f, 980f)
        val options = SpectrumViewOptions.of(
            minusBackground = true,
            smoothing = true,
            window = window,
        )
        assertEquals(window, options.window())
        assertEquals(true, options.minusBackground)
        assertEquals(true, options.smoothing)
    }

    @Test
    fun `no zoom means the whole scale, not a zero-wide window`() {
        assertNull(SpectrumViewOptions.of(minusBackground = false, smoothing = false, window = null)
                .window())
        assertNull(SpectrumViewOptions(startKeV = 500f, endKeV = 500f).window())
        assertNull(SpectrumViewOptions().window())
    }
}
