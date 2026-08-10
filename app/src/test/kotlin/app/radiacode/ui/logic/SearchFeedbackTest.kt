package app.radiacode.ui.logic

import kotlin.math.abs
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickRateTest {

    @Test
    fun `click rate follows cps one to one`() {
        assertEquals(24.3f, ClickRate.clicksPerSecond(24.3f), 1e-6f)
    }

    @Test
    fun `click rate is zero without a sample`() {
        assertEquals(0f, ClickRate.clicksPerSecond(null), 0f)
    }

    @Test
    fun `negative cps clamps to zero`() {
        assertEquals(0f, ClickRate.clicksPerSecond(-3f), 0f)
    }

    @Test
    fun `click rate caps at 40 per second`() {
        assertEquals(40f, ClickRate.clicksPerSecond(1000f), 0f)
    }

    @Test
    fun `interval is infinite at zero rate`() {
        assertEquals(Float.POSITIVE_INFINITY, ClickRate.nextIntervalSeconds(0f, 0.5f))
    }

    @Test
    fun `interval follows the exponential inverse cdf`() {
        // u = 1 - 1/e gives exactly the mean interval 1/rate.
        val u = (1.0 - 1.0 / Math.E).toFloat()
        assertEquals(0.1f, ClickRate.nextIntervalSeconds(10f, u), 1e-3f)
        val u2 = 0.5f
        assertEquals(ln(2.0).toFloat() / 10f, ClickRate.nextIntervalSeconds(10f, u2), 1e-3f)
    }

    @Test
    fun `interval clamps to the 40 per second minimum gap`() {
        assertEquals(
            ClickRate.MIN_INTERVAL_SECONDS,
            ClickRate.nextIntervalSeconds(1000f, 0.5f),
            0f,
        )
    }

    @Test
    fun `interval clamps to the maximum gap at tiny rates`() {
        assertEquals(
            ClickRate.MAX_INTERVAL_SECONDS,
            ClickRate.nextIntervalSeconds(0.001f, 0.9f),
            0f,
        )
    }

    @Test
    fun `intervals are monotonic in u`() {
        var prev = 0f
        for (i in 1..9) {
            val interval = ClickRate.nextIntervalSeconds(5f, i / 10f)
            assertTrue(interval >= prev)
            prev = interval
        }
    }
}

class VibrationPolicyTest {

    // Background 25 cps → σ = 5.
    private val bg = 25f

    @Test
    fun `no pulses without a background reference`() {
        val policy = VibrationPolicy()
        assertFalse(policy.onSample(1000f, null))
        assertFalse(policy.onSample(1000f, 0f))
    }

    @Test
    fun `no pulse inside normal fluctuation below two sigma`() {
        val policy = VibrationPolicy()
        assertFalse(policy.onSample(bg, bg))
        assertFalse(policy.onSample(bg + 5f, bg)) // +1σ
        assertFalse(policy.onSample(bg + 9.9f, bg)) // just under +2σ
    }

    @Test
    fun `one pulse per new sigma step starting at two sigma`() {
        val policy = VibrationPolicy()
        assertTrue(policy.onSample(bg + 10f, bg)) // +2σ
        assertFalse(policy.onSample(bg + 11f, bg)) // still step 2
        assertTrue(policy.onSample(bg + 15f, bg)) // +3σ
        assertTrue(policy.onSample(bg + 20f, bg)) // +4σ
    }

    @Test
    fun `boundary noise does not retrigger`() {
        val policy = VibrationPolicy()
        assertTrue(policy.onSample(bg + 15f, bg)) // step 3
        assertFalse(policy.onSample(bg + 12f, bg)) // step 2: within hysteresis
        assertFalse(policy.onSample(bg + 15f, bg)) // step 3 again: no new pulse
    }

    @Test
    fun `dropping a full sigma re-arms the step`() {
        val policy = VibrationPolicy()
        assertTrue(policy.onSample(bg + 15f, bg)) // step 3
        assertFalse(policy.onSample(bg + 5f, bg)) // step 1: level drops, silent
        assertTrue(policy.onSample(bg + 10f, bg)) // step 2 again: real climb
    }

    @Test
    fun `stationary high level stays silent`() {
        val policy = VibrationPolicy()
        assertTrue(policy.onSample(bg + 25f, bg)) // step 5
        repeat(30) {
            assertFalse(policy.onSample(bg + 25f, bg))
        }
    }

    @Test
    fun `reset re-arms from zero`() {
        val policy = VibrationPolicy()
        assertTrue(policy.onSample(bg + 10f, bg))
        policy.reset()
        assertTrue(policy.onSample(bg + 10f, bg))
    }
}

class ClickWaveformTest {

    @Test
    fun `waveform is short nonempty and decays`() {
        val pcm = ClickWaveform.pcm16(44_100)
        assertTrue(pcm.isNotEmpty())
        assertTrue(pcm.size < 44_100 / 100) // under 10 ms
        val head = pcm.take(pcm.size / 3).maxOf { abs(it.toInt()) }
        val tail = pcm.takeLast(pcm.size / 3).maxOf { abs(it.toInt()) }
        assertTrue(head > tail)
    }
}
