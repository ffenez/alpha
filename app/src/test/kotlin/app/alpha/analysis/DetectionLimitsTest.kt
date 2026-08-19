package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пределы обнаружения: что прибор МОГ БЫ заметить за это время.
 *
 * Проверяется не «работает ли код», а совпадение с формулами Кюри и
 * поведение на границах — там, где число легко превращается в обещание.
 */
class DetectionLimitsTest {

    private fun window(rate: Double, seconds: Double) = CountWindow(
        counts = rate * seconds,
        seconds = seconds,
        samples = seconds.toInt().coerceAtLeast(1),
    )

    @Test
    fun `the critical level matches Currie for equal exposures`() {
        // Равные выдержки: σ на нуле = √(2·R_b/t), L_C = k·σ.
        val rate = 20.0
        val seconds = 60.0
        val limits = DetectionLimitsMath.of(window(rate, seconds), window(rate, seconds))!!
        val expected = DetectionLimitsMath.DEFAULT_SIGMAS * sqrt(2.0 * rate / seconds)
        assertEquals(expected, limits.criticalRate, 1e-9)
    }

    @Test
    fun `the detection limit carries the signal's own statistics`() {
        val limits = DetectionLimitsMath.of(window(20.0, 60.0), window(20.0, 60.0))!!
        // L_D = k²/t + 2·L_C — строго больше удвоенного L_C.
        assertTrue(limits.detectableRate > 2.0 * limits.criticalRate)
        val quadratic = DetectionLimitsMath.DEFAULT_SIGMAS * DetectionLimitsMath.DEFAULT_SIGMAS / 60.0
        assertEquals(quadratic + 2.0 * limits.criticalRate, limits.detectableRate, 1e-9)
    }

    @Test
    fun `a longer measurement notices a smaller excess`() {
        val short = DetectionLimitsMath.of(window(20.0, 10.0), window(20.0, 10.0))!!
        val long = DetectionLimitsMath.of(window(20.0, 600.0), window(20.0, 600.0))!!
        assertTrue(
            "долгое измерение обязано быть чувствительнее: ${short.detectableRatio} → ${long.detectableRatio}",
            long.detectableRatio!! < short.detectableRatio!!,
        )
        // Вчетверо больше времени — примерно вдвое меньше порог (√t).
        val ratio = (short.detectableRate) / (long.detectableRate)
        assertTrue("$ratio", ratio > 4.0)
    }

    @Test
    fun `an excess above the critical level is called distinguishable`() {
        val limits = DetectionLimitsMath.of(window(40.0, 60.0), window(20.0, 60.0))!!
        assertTrue(limits.aboveCritical)
        assertEquals(20.0, limits.netRate, 1e-9)
        // Верхняя граница лежит выше наблюдённого нетто, а не вокруг нуля.
        assertTrue(limits.upperRate > limits.netRate)
    }

    @Test
    fun `no excess still yields an upper bound`() {
        // Ровный фон: различия нет, но «не больше чем» сказать можно.
        val limits = DetectionLimitsMath.of(window(20.0, 60.0), window(20.0, 60.0))!!
        assertTrue(!limits.aboveCritical)
        assertTrue(limits.upperRate > 0.0)
        assertTrue(limits.upperRatio!! > 1.0)
    }

    @Test
    fun `unusable windows have no limits at all`() {
        assertNull(DetectionLimitsMath.of(null, window(20.0, 60.0)))
        assertNull(DetectionLimitsMath.of(window(20.0, 60.0), null))
        assertNull(DetectionLimitsMath.of(window(20.0, 0.0), window(20.0, 60.0)))
        assertNull(
            DetectionLimitsMath.of(window(20.0, 60.0), window(20.0, 60.0), sigmas = 0.0),
        )
    }

    @Test
    fun `the required time inverts the detection limit`() {
        // Сколько нужно копить ради ×1,5 — и на этом времени предел
        // обнаружения обязан дойти ровно до этого превышения.
        val background = 20.0
        val ratio = 1.5
        val seconds = DetectionLimitsMath.secondsFor(background, ratio)!!
        val limits = DetectionLimitsMath.of(
            window(background * ratio, seconds),
            window(background, seconds),
        )!!
        assertTrue(
            "предел ${limits.detectableRatio} против запрошенного $ratio",
            abs(limits.detectableRatio!! - ratio) < 0.02,
        )
    }

    @Test
    fun `an unreachable excess has no time`() {
        assertNull(DetectionLimitsMath.secondsFor(20.0, 1.0))
        assertNull(DetectionLimitsMath.secondsFor(0.0, 2.0))
    }
}
