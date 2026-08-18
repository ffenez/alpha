package app.alpha.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun `zero duration scale means do not move`() {
        // «Отключение анимации» в спец-возможностях и режим экономии ставят
        // ноль. Для бесконечного цикла ноль — это не «быстро», а «мигать».
        assertFalse(Motion.animationsEnabled(0f))
    }

    @Test
    fun `ordinary and slowed scales keep the motion`() {
        assertTrue(Motion.animationsEnabled(1f))
        assertTrue(Motion.animationsEnabled(0.5f))
        assertTrue(Motion.animationsEnabled(10f))
    }

    @Test
    fun `a broken scale is not allowed to switch the instrument off`() {
        // Отрицательный или нечисловой масштаб — это сбой чтения настройки,
        // а не согласие погасить движение.
        assertTrue(Motion.animationsEnabled(Float.NaN))
        assertFalse(Motion.animationsEnabled(-1f))
    }
}
