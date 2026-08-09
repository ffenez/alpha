package app.radiacode.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundReferenceTest {

    @Test
    fun `default averaging window is within the 30-60 s the spec allows`() {
        assertTrue(BackgroundRef.DEFAULT_TARGET_SAMPLES in 30..60)
    }

    @Test
    fun `averaging completes at the target with the mean cps`() {
        var state: BackgroundRef = BackgroundRef.startMeasuring(targetSamples = 4)
        val samples = listOf(20f, 22f, 18f, 24f)
        for (cps in samples) {
            state = (state as BackgroundRef.Measuring).onSample(cps)
        }
        val ready = assertIs<BackgroundRef.Ready>(state)
        assertTrue(abs(ready.cps - 21f) < 1e-5f)
    }

    @Test
    fun `progress grows monotonically and stays below one until done`() {
        var state = BackgroundRef.startMeasuring(targetSamples = 10)
        var last = -1f
        repeat(9) {
            state = state.onSample(20f) as BackgroundRef.Measuring
            assertTrue(state.progress > last)
            assertTrue(state.progress < 1f)
            last = state.progress
        }
    }

    @Test
    fun `delta percent against background`() {
        assertEquals(81, deltaPercent(cps = 38f, backgroundCps = 21f))
        assertEquals(0, deltaPercent(cps = 21f, backgroundCps = 21f))
        assertEquals(-50, deltaPercent(cps = 10.5f, backgroundCps = 21f))
        assertNull(deltaPercent(cps = 38f, backgroundCps = null))
        assertNull(deltaPercent(cps = 38f, backgroundCps = 0f))
    }

    @Test
    fun `led level scales from background to five times background`() {
        assertEquals(0.2f, ledLevel(cps = 21f, backgroundCps = 21f))
        assertEquals(1f, ledLevel(cps = 105f, backgroundCps = 21f))
        assertEquals(1f, ledLevel(cps = 500f, backgroundCps = 21f))
        assertEquals(0f, ledLevel(cps = 30f, backgroundCps = null))
    }

    @Test
    fun `poisson band is bg plus-minus two sigma clamped at zero`() {
        val band = backgroundBand(25f)
        assertEquals(15f, band.start)
        assertEquals(35f, band.endInclusive)
        assertEquals(0f, backgroundBand(1f).start)
    }
}
