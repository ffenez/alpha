package app.radiacode.analysis.quantiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The exact-vs-approximate diagnostic (CHART SPEC §34, §37G): it must report
 * the error that was actually observed, and it must notice when the two sides
 * are not describing the same data.
 */
class QuantileDiagnosticsTest {

    private class Lcg(private var state: Long = 999L) {
        fun nextUnit(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            return (state ushr 11).toDouble() / (1L shl 53).toDouble()
        }
    }

    private fun values(n: Int, seed: Long = 999L): FloatArray {
        val rng = Lcg(seed)
        return FloatArray(n) { (0.08 + rng.nextUnit() * 0.06).toFloat() }
    }

    @Test
    fun `a sketch of the same data reports a small measured error`() {
        val raw = values(200_000)
        val comparison = QuantileDiagnostics.compare(raw, KllSketch.of(raw))
        assertEquals(200_000, comparison.sampleCount)
        assertEquals(200_000L, comparison.sketchCount)
        assertTrue(comparison.countsAgree)
        assertTrue(comparison.maxRankError <= 0.02, "measured ${comparison.maxRankError}")
        assertTrue(comparison.maxValueError < 0.005f, "measured ${comparison.maxValueError}")
    }

    @Test
    fun `the exact side is a true order statistic`() {
        val raw = floatArrayOf(5f, 1f, 4f, 2f, 3f)
        val comparison = QuantileDiagnostics.compare(
            raw,
            KllSketch.of(raw),
            doubleArrayOf(0.2, 0.5, 1.0),
        )
        assertEquals(listOf(1f, 3f, 5f), comparison.exactValues.toList())
    }

    @Test
    fun `comparing different data is reported, not hidden`() {
        val raw = values(50_000)
        val other = KllSketch.of(values(50_000, seed = 12L).map { it + 1f }.toFloatArray())
        val comparison = QuantileDiagnostics.compare(raw.copyOf(40_000), other)
        assertTrue(!comparison.countsAgree, "sample counts must be compared, not assumed")
        assertTrue(comparison.maxRankError > 0.5, "a shifted distribution must show a big error")
    }

    @Test
    fun `an empty window does not divide by zero`() {
        val comparison = QuantileDiagnostics.compare(FloatArray(0), KllSketch())
        assertEquals(0, comparison.sampleCount)
        assertEquals(0.0, comparison.maxRankError)
        assertEquals(0f, comparison.maxValueError)
    }

    @Test
    fun `rank of a value is the fraction of samples at or below it`() {
        val sorted = floatArrayOf(1f, 2f, 3f, 4f)
        assertEquals(0.25, QuantileDiagnostics.rankOf(sorted, 1f))
        assertEquals(1.0, QuantileDiagnostics.rankOf(sorted, 4f))
        assertEquals(0.0, QuantileDiagnostics.rankOf(sorted, 0f))
    }
}
