package app.radiacode.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The search tone exists to be followed with the phone in a pocket, so its two
 * failure modes are «поёт на шуме» and «дёргается на каждом импульсе»
 * (redesign §7). Both are tested here.
 */
class SearchToneTest {

    @Test
    fun `inside the background there is no tone at all`() {
        assertNull(SearchTone.frequencyHz(null))
        assertNull(SearchTone.frequencyHz(1.0))
        assertNull(SearchTone.frequencyHz(SearchTone.MIN_RATIO - 0.01))
        assertNull(SearchTone.frequencyHz(Double.NaN))
        assertNotNull(SearchTone.frequencyHz(SearchTone.MIN_RATIO))
    }

    @Test
    fun `pitch rises with the ratio and saturates at the top`() {
        val low = assertNotNull(SearchTone.frequencyHz(1.2))
        val mid = assertNotNull(SearchTone.frequencyHz(3.0))
        val high = assertNotNull(SearchTone.frequencyHz(SearchTone.MAX_RATIO))
        assertTrue(low < mid && mid < high, "$low $mid $high")
        assertEquals(SearchTone.MIN_HZ, assertNotNull(SearchTone.frequencyHz(SearchTone.MIN_RATIO)), 1f)
        assertEquals(SearchTone.MAX_HZ, high, 1f)
        assertEquals(high, assertNotNull(SearchTone.frequencyHz(SearchTone.MAX_RATIO * 10)), 1f)
    }

    @Test
    fun `equal factors of the ratio are equal musical intervals`() {
        // The mapping is logarithmic: the same multiplication of R must move
        // the pitch by the same number of octaves anywhere in the range.
        val a = assertNotNull(SearchTone.frequencyHz(1.2))
        val b = assertNotNull(SearchTone.frequencyHz(2.4))
        val c = assertNotNull(SearchTone.frequencyHz(4.8))
        assertEquals(b / a, c / b, 0.01f)
    }

    @Test
    fun `the pitch label speaks hertz or nothing`() {
        assertNull(SearchTone.pitchLabel(1.0))
        assertTrue(assertNotNull(SearchTone.pitchLabel(3.0)).endsWith("Гц"))
    }
}

class ToneEngineTest {

    private val sampleRate = 44_100
    private val chunkFrames = 2_048

    /** Largest jump between neighbouring samples, as a fraction of full scale. */
    private fun maxStep(vararg chunks: ShortArray): Float {
        var worst = 0f
        val all = chunks.toList().flatMap { it.toList() }
        for (i in 1 until all.size) {
            val step = abs(all[i].toInt() - all[i - 1].toInt()) / Short.MAX_VALUE.toFloat()
            if (step > worst) worst = step
        }
        return worst
    }

    @Test
    fun `silence stays silence`() {
        val engine = ToneEngine(sampleRate)
        val chunk = ShortArray(chunkFrames)
        engine.fillChunk(chunk, null)
        assertTrue(chunk.all { it.toInt() == 0 })
        assertEquals(0f, engine.frequencyHz)
    }

    @Test
    fun `a steady target produces a continuous waveform across chunks`() {
        val engine = ToneEngine(sampleRate)
        val first = ShortArray(chunkFrames)
        val second = ShortArray(chunkFrames)
        // Let the glide arrive first, then measure two settled chunks.
        repeat(60) { engine.fillChunk(ShortArray(chunkFrames), 640f) }
        engine.fillChunk(first, 640f)
        engine.fillChunk(second, 640f)

        assertEquals(640f, engine.frequencyHz, 1f)
        assertTrue(first.any { it.toInt() != 0 })
        // A phase reset at the chunk boundary would show up as a step far
        // larger than one sample of a 640 Hz sine (≈ 0,09 of full scale).
        assertTrue(maxStep(first, second) < 0.2f, "${maxStep(first, second)}")
    }

    @Test
    fun `pitch travels at the glide rate, never in a jump`() {
        val engine = ToneEngine(sampleRate)
        val chunk = ShortArray(chunkFrames)
        repeat(60) { engine.fillChunk(chunk, SearchTone.MIN_HZ) }
        assertEquals(SearchTone.MIN_HZ, engine.frequencyHz, 1f)

        // One chunk is ~46 ms; at 2 octaves per second that is ~0,09 octave,
        // i.e. about 7 % — nowhere near the eightfold jump asked for.
        engine.fillChunk(chunk, SearchTone.MAX_HZ)
        val afterOneChunk = engine.frequencyHz
        assertTrue(
            afterOneChunk < SearchTone.MIN_HZ * 1.15f,
            "jumped to $afterOneChunk in one chunk",
        )

        // ...and it does arrive: 3 octaves at 2 octaves/s ≈ 1,5 s ≈ 33 chunks.
        repeat(60) { engine.fillChunk(chunk, SearchTone.MAX_HZ) }
        assertEquals(SearchTone.MAX_HZ, engine.frequencyHz, 10f)
    }

    @Test
    fun `dropping the target fades down instead of cutting off`() {
        val engine = ToneEngine(sampleRate)
        val chunk = ShortArray(chunkFrames)
        repeat(60) { engine.fillChunk(chunk, 1_000f) }
        engine.fillChunk(chunk, null)
        assertTrue(engine.frequencyHz in 1f..1_000f, "${engine.frequencyHz}")

        repeat(60) { engine.fillChunk(chunk, null) }
        assertEquals(0f, engine.frequencyHz)
        assertTrue(chunk.all { it.toInt() == 0 })
    }

    @Test
    fun `the waveform stays inside the declared amplitude`() {
        val engine = ToneEngine(sampleRate)
        val chunk = ShortArray(chunkFrames)
        repeat(60) { engine.fillChunk(chunk, 900f) }
        val peak = chunk.maxOf { abs(it.toInt()) } / Short.MAX_VALUE.toFloat()
        assertTrue(peak <= SearchTone.AMPLITUDE + 0.01f, "peak $peak")
        assertTrue(peak > SearchTone.AMPLITUDE * 0.8f, "peak $peak — the tone must be audible")
    }
}

class SearchVibroTest {

    @Test
    fun `inside the background nothing pulses`() {
        assertNull(SearchVibro.intervalMillis(null))
        assertNull(SearchVibro.intervalMillis(1.0))
        assertNull(SearchVibro.cadenceLabel(1.0))
    }

    @Test
    fun `the cadence speeds up towards the source and then saturates`() {
        val slow = assertNotNull(SearchVibro.intervalMillis(SearchVibro.MIN_RATIO))
        val faster = assertNotNull(SearchVibro.intervalMillis(3.0))
        val fastest = assertNotNull(SearchVibro.intervalMillis(SearchVibro.MAX_RATIO))
        assertTrue(slow > faster && faster > fastest, "$slow $faster $fastest")
        assertEquals(SearchVibro.SLOW_INTERVAL_MILLIS, slow)
        assertEquals(SearchVibro.FAST_INTERVAL_MILLIS, fastest)
        assertEquals(fastest, SearchVibro.intervalMillis(SearchVibro.MAX_RATIO * 5))
    }

    @Test
    fun `the cadence label is human and carries its unit`() {
        val label = assertNotNull(SearchVibro.cadenceLabel(4.0))
        assertTrue(label.contains("пульс") && label.endsWith("с"), label)
    }
}

class SearchFeedbackModeTest {

    @Test
    fun `ids round trip and unknown ids resolve to nothing`() {
        for (mode in SearchFeedbackMode.entries) {
            assertEquals(mode, SearchFeedbackMode.of(mode.id))
        }
        assertNull(SearchFeedbackMode.of("something-else"))
        assertNull(SearchFeedbackMode.of(null))
    }

    @Test
    fun `the four modes of the redesign are exactly these`() {
        assertEquals(
            listOf("off", "clicks", "tone", "vibro"),
            SearchFeedbackMode.entries.map { it.id },
        )
    }
}
