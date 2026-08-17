package app.alpha.ui.logic

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

    // --- «тон по энергии» ---

    @Test
    fun `energy tone bands split at 300 and 1000 keV, higher keV higher pitch`() {
        assertEquals(EnergyTone.Band.LOW, EnergyTone.bandForMeanEnergy(60f))
        assertEquals(EnergyTone.Band.LOW, EnergyTone.bandForMeanEnergy(299f))
        assertEquals(EnergyTone.Band.MID, EnergyTone.bandForMeanEnergy(300f))
        assertEquals(EnergyTone.Band.MID, EnergyTone.bandForMeanEnergy(662f))
        assertEquals(EnergyTone.Band.MID, EnergyTone.bandForMeanEnergy(1000f))
        assertEquals(EnergyTone.Band.HIGH, EnergyTone.bandForMeanEnergy(1461f))

        val low = EnergyTone.frequencyHz(EnergyTone.Band.LOW)
        val mid = EnergyTone.frequencyHz(EnergyTone.Band.MID)
        val high = EnergyTone.frequencyHz(EnergyTone.Band.HIGH)
        assertTrue(low < mid && mid < high)
        // MID is the classic default tick — plain clicks and mid-energy
        // clicks are indistinguishable by design.
        assertEquals(ClickWaveform.FREQUENCY_HZ, mid, 0f)
    }

    @Test
    fun `no or empty spectrum data yields no band - plain clicks`() {
        assertEquals(null, EnergyTone.bandForMeanEnergy(null))
        assertEquals(null, EnergyTone.bandForMeanEnergy(0f))
    }

    @Test
    fun `stale slices stop steering the pitch after 15 s`() {
        assertTrue(EnergyTone.isFresh(sliceAtMillis = 1_000L, nowMillis = 15_000L))
        assertFalse(EnergyTone.isFresh(sliceAtMillis = 1_000L, nowMillis = 17_000L))
    }

    @Test
    fun `band waveforms actually differ in pitch`() {
        val low = ClickWaveform.pcm16(44_100, EnergyTone.frequencyHz(EnergyTone.Band.LOW))
        val high = ClickWaveform.pcm16(44_100, EnergyTone.frequencyHz(EnergyTone.Band.HIGH))
        assertEquals(low.size, high.size) // same envelope, different tone
        assertTrue(zeroCrossings(high) > zeroCrossings(low))
    }

    private fun zeroCrossings(pcm: ShortArray): Int {
        var crossings = 0
        for (i in 1 until pcm.size) {
            if (pcm[i - 1] < 0 != pcm[i] < 0) crossings++
        }
        return crossings
    }
}

/**
 * The highest-value tests we were missing: the renderer's frame loop. A
 * silent Search screen is exactly what a broken state machine here looks
 * like, and nothing in the app or the field report could tell us apart.
 */
class ClickEngineTest {

    private val sampleRate = 44_100
    private val chunkFrames = 2_048

    /** Rectangular «click» so onsets are unambiguous to count. */
    private val click = ShortArray(4) { 1_000 }

    private fun render(rate: Float, seconds: Double, seed: Int = 42): ShortArray {
        val random = kotlin.random.Random(seed)
        val engine = ClickEngine(sampleRate) { random.nextFloat() }
        val chunks = (sampleRate * seconds / chunkFrames).toInt()
        val out = ShortArray(chunks * chunkFrames)
        val chunk = ShortArray(chunkFrames)
        for (index in 0 until chunks) {
            engine.fillChunk(chunk, rate, click)
            chunk.copyInto(out, destinationOffset = index * chunkFrames)
        }
        return out
    }

    private fun countClicks(pcm: ShortArray): Int {
        var count = 0
        for (i in pcm.indices) {
            if (pcm[i] != 0.toShort() && (i == 0 || pcm[i - 1] == 0.toShort())) count++
        }
        return count
    }

    @Test
    fun `a non-zero rate produces non-zero samples`() {
        val pcm = render(rate = 5f, seconds = 2.0)
        assertTrue(pcm.any { it != 0.toShort() })
    }

    @Test
    fun `zero rate renders pure silence`() {
        val pcm = render(rate = 0f, seconds = 3.0)
        assertTrue(pcm.all { it == 0.toShort() })
    }

    @Test
    fun `click count tracks the requested rate`() {
        val seconds = 20.0
        for (rate in listOf(2f, 5f, 20f, 40f)) {
            val clicks = countClicks(render(rate, seconds))
            val expected = rate * seconds
            assertTrue(
                "rate $rate: $clicks clicks in $seconds s, expected around $expected",
                clicks > expected * 0.5 && clicks < expected * 1.6,
            )
        }
    }

    /**
     * Realistic RadiaCode background is 3–25 cps and a hot spot pushes into
     * the hundreds; every one of those must be audible, not silence.
     */
    @Test
    fun `every realistic cps produces clicks`() {
        for (cps in listOf(1f, 3f, 12f, 25f, 60f, 200f)) {
            val rate = ClickRate.clicksPerSecond(cps)
            assertTrue("cps $cps mapped to rate $rate", rate > 0f)
            val clicks = countClicks(render(rate, seconds = 4.0))
            assertTrue("cps $cps produced no clicks", clicks > 0)
        }
    }

    @Test
    fun `clicks keep the waveform they started with`() {
        // u = 0 gives the shortest allowed gap, so the click starts inside
        // the first chunk and is still sounding in the second one.
        val engine = ClickEngine(sampleRate) { 0f }
        val positive = ShortArray(chunkFrames * 4) { 500 }
        val chunk = ShortArray(chunkFrames)
        engine.fillChunk(chunk, 40f, positive)
        assertTrue("the click must start in the first chunk", chunk.any { it > 0 })
        // The second chunk offers a different waveform («тон по энергии»
        // switching pitch); the sounding click must not swap mid-flight.
        val negative = ShortArray(chunkFrames * 4) { -500 }
        engine.fillChunk(chunk, 40f, negative)
        assertTrue("the click already sounding kept its waveform", chunk.all { it > 0 })
    }

    @Test
    fun `rate changes take effect within a chunk`() {
        val random = kotlin.random.Random(3)
        val engine = ClickEngine(sampleRate) { random.nextFloat() }
        val chunk = ShortArray(chunkFrames)
        repeat(20) { engine.fillChunk(chunk, 0f, click) }
        var heard = false
        repeat(20) {
            engine.fillChunk(chunk, 30f, click)
            if (chunk.any { it != 0.toShort() }) heard = true
        }
        assertTrue("going from silence to 30/s must start clicking", heard)
    }
}

class FeedbackReasonTest {

    private val running = FeedbackState(
        mode = SearchFeedbackMode.CLICKS,
        deviceConnected = true,
        dataFresh = true,
        dndBlocked = false,
        audioUnavailable = false,
        volumeZero = false,
        backgroundRecorded = true,
    )

    @Test
    fun `no reason when feedback really is running`() {
        assertEquals(null, FeedbackReason.line(running))
        assertEquals(null, FeedbackReason.line(running.copy(mode = SearchFeedbackMode.TONE)))
        assertEquals(null, FeedbackReason.line(running.copy(mode = SearchFeedbackMode.VIBRO)))
    }

    @Test
    fun `the off mode is stated plainly`() {
        val reason = FeedbackReason.line(running.copy(mode = SearchFeedbackMode.OFF))
        assertEquals("отклик выключен — сигнал виден только на экране", reason)
    }

    @Test
    fun `a disconnected device outranks every other reason`() {
        val reason = FeedbackReason.line(
            running.copy(deviceConnected = false, dndBlocked = true, volumeZero = true),
        )
        assertTrue(reason, reason!!.startsWith("прибор не подключён"))
    }

    @Test
    fun `a stalled stream is named`() {
        val reason = FeedbackReason.line(running.copy(dataFresh = false))
        assertTrue(reason, reason!!.startsWith("нет данных с прибора"))
    }

    @Test
    fun `do not disturb is named`() {
        val reason = FeedbackReason.line(running.copy(dndBlocked = true))
        assertTrue(reason, reason!!.contains("не беспокоить"))
    }

    @Test
    fun `a dead audio engine is named before volume`() {
        val reason = FeedbackReason.line(running.copy(audioUnavailable = true, volumeZero = true))
        assertTrue(reason, reason!!.contains("звук не запустился"))
    }

    @Test
    fun `zero media volume is named`() {
        val reason = FeedbackReason.line(running.copy(volumeZero = true))
        assertTrue(reason, reason!!.contains("громкость"))
    }

    /** Audio problems belong to the audio modes only. */
    @Test
    fun `a dead audio channel is irrelevant to the silent mode`() {
        val silent = running.copy(mode = SearchFeedbackMode.VIBRO, audioUnavailable = true, volumeZero = true)
        assertEquals(null, FeedbackReason.line(silent))
    }

    /** The silent-vibration trap: no background means nothing relative, ever. */
    @Test
    fun `the relative modes without a recorded background say so`() {
        val vibro = FeedbackReason.line(
            running.copy(mode = SearchFeedbackMode.VIBRO, backgroundRecorded = false),
        )
        assertEquals("фон не записан — вибрация включится после записи фона", vibro)

        val tone = FeedbackReason.line(
            running.copy(mode = SearchFeedbackMode.TONE, backgroundRecorded = false),
        )
        assertEquals("фон не записан — тон включится после записи фона", tone)
    }

    @Test
    fun `a missing background is irrelevant to clicks, which are absolute`() {
        assertEquals(null, FeedbackReason.line(running.copy(backgroundRecorded = false)))
    }

    /**
     * Designed silence is still silence: a tone that says nothing at
     * background level must say *why* it says nothing.
     */
    @Test
    fun `silence inside the background is explained, not left hanging`() {
        val tone = FeedbackReason.line(
            running.copy(mode = SearchFeedbackMode.TONE, insideBackground = true),
        )
        assertTrue(tone, tone!!.contains("в пределах записанного фона"))
        assertTrue(tone, tone.contains("тон"))

        // Clicks are absolute — they click at background level too.
        assertEquals(null, FeedbackReason.line(running.copy(insideBackground = true)))
    }
}
