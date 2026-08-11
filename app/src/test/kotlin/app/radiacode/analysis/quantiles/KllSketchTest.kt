package app.radiacode.analysis.quantiles

import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validation of the long-window quantile path (CHART SPEC §30, §37G, §38;
 * ADR 004).
 *
 * Everything here is deterministic by construction: the data comes from a
 * fixed linear congruential stream, and the sketch seeds its compaction coin
 * from the data itself (no wall clock, no global RNG). A rerun on another
 * machine must produce the same numbers, otherwise the error bounds below
 * would mean nothing.
 */
class KllSketchTest {

    /** Deterministic pseudo-random stream — the same on every machine. */
    private class Lcg(private var state: Long = 0x2545F4914F6CDD1DL) {
        fun nextUnit(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            return ((state ushr 11).toDouble() / (1L shl 53).toDouble())
        }
    }

    private val probabilities = doubleArrayOf(0.10, 0.25, 0.50, 0.75, 0.90)

    private fun uniform(n: Int, scale: Float = 1f): FloatArray {
        val rng = Lcg()
        return FloatArray(n) { (rng.nextUnit() * scale).toFloat() }
    }

    /** Skewed, heavy-ish tail — closer to real dose-rate data than uniform. */
    private fun lognormalish(n: Int): FloatArray {
        val rng = Lcg(0x9E3779B97F4A7C15uL.toLong())
        return FloatArray(n) {
            val u = rng.nextUnit().coerceIn(1e-9, 1.0 - 1e-9)
            (0.1 * exp(1.2 * (u - 0.5) * 2.0)).toFloat()
        }
    }

    /** Rank of [value] in the exact sorted data, 0..1. */
    private fun exactRank(sorted: FloatArray, value: Float): Double {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (sorted[mid] <= value) lo = mid + 1 else hi = mid
        }
        return lo.toDouble() / sorted.size
    }

    private fun maxRankError(values: FloatArray, sketch: KllSketch, probes: DoubleArray): Double {
        val sorted = values.copyOf().also { it.sort() }
        val estimates = sketch.quantiles(probes)
        var worst = 0.0
        for (i in probes.indices) {
            val error = abs(exactRank(sorted, estimates[i]) - probes[i])
            if (error > worst) worst = error
        }
        return worst
    }

    // --- accuracy (§37G) ---------------------------------------------------

    @Test
    fun `rank error on 100k uniform samples stays inside the documented bound`() {
        val values = uniform(100_000)
        val sketch = KllSketch.of(values)
        val probes = DoubleArray(97) { (it + 2) / 100.0 }
        val error = maxRankError(values, sketch, probes)
        // Documented bound for k=128 (ADR 004). Measured value is far below;
        // the assertion is the contract, not the measurement.
        assertTrue(error <= 0.02, "max rank error $error exceeds the documented 2 %")
        assertEquals(100_000L, sketch.count)
    }

    @Test
    fun `rank error holds on a skewed distribution too`() {
        val values = lognormalish(120_000)
        val sketch = KllSketch.of(values)
        val probes = DoubleArray(97) { (it + 2) / 100.0 }
        assertTrue(maxRankError(values, sketch, probes) <= 0.02)
    }

    @Test
    fun `a bigger k is more accurate`() {
        val values = lognormalish(100_000)
        val probes = DoubleArray(97) { (it + 2) / 100.0 }
        val coarse = maxRankError(values, KllSketch.of(values, k = 32), probes)
        val fine = maxRankError(values, KllSketch.of(values, k = 512), probes)
        assertTrue(fine < coarse, "k=512 error $fine must beat k=32 error $coarse")
    }

    @Test
    fun `memory stays bounded while the data grows`() {
        val small = KllSketch.of(uniform(10_000))
        val large = KllSketch.of(uniform(1_000_000))
        assertTrue(large.storedItems <= 6 * KllSketch.DEFAULT_K, "stored ${large.storedItems}")
        assertTrue(large.storedItems < 2 * small.storedItems)
        assertEquals(1_000_000L, large.count)
    }

    @Test
    fun `extremes are exact whatever the compaction did`() {
        val values = uniform(50_000, scale = 3f)
        val sketch = KllSketch.of(values)
        assertEquals(values.min(), sketch.min)
        assertEquals(values.max(), sketch.max)
    }

    // --- merging (§30) -----------------------------------------------------

    @Test
    fun `merge estimates the quantiles of the union`() {
        val a = uniform(40_000)
        val b = lognormalish(40_000)
        val merged = KllSketch.of(a)
        merged.merge(KllSketch.of(b))
        assertEquals((a.size + b.size).toLong(), merged.count)
        val union = a + b
        val probes = DoubleArray(97) { (it + 2) / 100.0 }
        assertTrue(maxRankError(union, merged, probes) <= 0.03)
    }

    @Test
    fun `merge is commutative and associative within the documented tolerance`() {
        val parts = List(6) { index ->
            val rng = Lcg(1000L + index)
            FloatArray(5_000) { (0.1 + rng.nextUnit() * 0.05).toFloat() }
        }
        fun mergeIn(order: List<Int>): KllSketch {
            val result = KllSketch()
            for (i in order) result.merge(KllSketch.of(parts[i]))
            return result
        }
        val forward = mergeIn(parts.indices.toList())
        val backward = mergeIn(parts.indices.reversed().toList())
        // Pairwise tree merge: ((0+1)+(2+3))+((4+5))
        val tree = KllSketch()
        val left = KllSketch.of(parts[0]).also { it.merge(KllSketch.of(parts[1])) }
        val mid = KllSketch.of(parts[2]).also { it.merge(KllSketch.of(parts[3])) }
        val right = KllSketch.of(parts[4]).also { it.merge(KllSketch.of(parts[5])) }
        tree.merge(left)
        tree.merge(mid)
        tree.merge(right)

        val all = parts.reduce { acc, floats -> acc + floats }
        val sorted = all.copyOf().also { it.sort() }
        for (sketch in listOf(forward, backward, tree)) {
            assertEquals(all.size.toLong(), sketch.count)
            val estimates = sketch.quantiles(probabilities)
            for (i in probabilities.indices) {
                val error = abs(exactRank(sorted, estimates[i]) - probabilities[i])
                assertTrue(error <= 0.02, "order-dependent error $error at p=${probabilities[i]}")
            }
        }
        // Different orders may disagree — but only inside the error band, never
        // wildly. Compared on rank, because that is what the sketch bounds.
        val f = forward.quantiles(probabilities)
        val b = backward.quantiles(probabilities)
        for (i in probabilities.indices) {
            val delta = abs(exactRank(sorted, f[i]) - exactRank(sorted, b[i]))
            assertTrue(delta <= 0.02, "merge order changed rank by $delta")
        }
    }

    @Test
    fun `merging an empty sketch changes nothing`() {
        val sketch = KllSketch.of(uniform(5_000))
        val before = sketch.quantiles(probabilities).toList()
        sketch.merge(KllSketch())
        assertEquals(before, sketch.quantiles(probabilities).toList())
        assertEquals(5_000L, sketch.count)
    }

    @Test
    fun `mergeAll of nothing is null`() {
        assertNull(KllSketch.mergeAll(emptyList()))
    }

    // --- determinism and serialization -------------------------------------

    @Test
    fun `same data gives a bit-identical sketch`() {
        val values = lognormalish(30_000)
        val first = KllSketch.of(values).toByteArray()
        val second = KllSketch.of(values).toByteArray()
        assertTrue(first.contentEquals(second), "sketch must not depend on anything but the data")
    }

    @Test
    fun `serialization round-trip is an identity`() {
        val values = lognormalish(20_000)
        val sketch = KllSketch.of(values)
        val restored = assertNotNull(KllSketch.fromByteArray(sketch.toByteArray()))
        assertEquals(sketch.count, restored.count)
        assertEquals(sketch.min, restored.min)
        assertEquals(sketch.max, restored.max)
        assertEquals(sketch.k, restored.k)
        assertEquals(
            sketch.quantiles(probabilities).toList(),
            restored.quantiles(probabilities).toList(),
        )
        // Restoring also restores the compaction coin stream: feeding both the
        // same tail keeps them identical, so a rebuilt aggregate is the same
        // object as the one that was written.
        val tail = uniform(5_000, scale = 0.3f)
        sketch.update(tail)
        restored.update(tail)
        assertTrue(sketch.toByteArray().contentEquals(restored.toByteArray()))
    }

    @Test
    fun `blob size stays inside the storage budget of ADR 004`() {
        val hour = FloatArray(3_600) { 0.1f + (it % 17) * 0.001f }
        val bytes = KllSketch.of(hour).toByteArray()
        assertTrue(bytes.size <= 2_048, "hourly sketch is ${bytes.size} B")
    }

    @Test
    fun `garbage decodes to null instead of throwing`() {
        assertNull(KllSketch.fromByteArray(null))
        assertNull(KllSketch.fromByteArray(ByteArray(0)))
        assertNull(KllSketch.fromByteArray(ByteArray(64) { 7 }))
        val good = KllSketch.of(uniform(1_000)).toByteArray()
        assertNull(KllSketch.fromByteArray(good.copyOf(good.size / 2)))
    }

    // --- degenerate inputs (§38) -------------------------------------------

    @Test
    fun `empty sketch answers zero without failing`() {
        val sketch = KllSketch()
        assertTrue(sketch.isEmpty)
        assertEquals(0L, sketch.count)
        assertEquals(0f, sketch.quantile(0.5))
        assertEquals(0f, sketch.mad())
        assertEquals(0.0, sketch.rank(1f))
        assertTrue(sketch.min.isNaN())
        val restored = assertNotNull(KllSketch.fromByteArray(sketch.toByteArray()))
        assertEquals(0L, restored.count)
    }

    @Test
    fun `single value is reported exactly`() {
        val sketch = KllSketch().apply { update(0.137f) }
        assertEquals(0.137f, sketch.quantile(0.10))
        assertEquals(0.137f, sketch.quantile(0.90))
        assertEquals(0.137f, sketch.min)
        assertEquals(0.137f, sketch.max)
        assertEquals(0f, sketch.mad())
    }

    @Test
    fun `all-equal values keep every quantile equal and MAD zero`() {
        val sketch = KllSketch.of(FloatArray(50_000) { 0.42f })
        assertEquals(0.42f, sketch.quantile(0.10))
        assertEquals(0.42f, sketch.quantile(0.50))
        assertEquals(0.42f, sketch.quantile(0.90))
        assertEquals(0f, sketch.mad())
    }

    @Test
    fun `non-finite values are ignored and negatives are clamped`() {
        val sketch = KllSketch()
        sketch.update(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0.5f))
        assertEquals(2L, sketch.count, "only the clamped negative and the real value count")
        assertEquals(0f, sketch.min)
        assertEquals(0.5f, sketch.max)
    }

    @Test
    fun `scaling is exact because the conversion is a positive constant`() {
        val values = lognormalish(20_000)
        val sketch = KllSketch.of(values)
        val scaled = sketch.scaled(1000f)
        val direct = sketch.quantiles(probabilities)
        val viaScale = scaled.quantiles(probabilities)
        for (i in probabilities.indices) {
            assertEquals(direct[i] * 1000f, viaScale[i], abs(direct[i]) * 1e-3f)
        }
        assertEquals(sketch.count, scaled.count)
    }

    // --- helpers used by the rest of the app --------------------------------

    @Test
    fun `MAD over the sketch tracks the exact MAD`() {
        val rng = Lcg(77)
        val values = FloatArray(60_000) { (0.10 + rng.nextUnit() * 0.04).toFloat() }
        val sketch = KllSketch.of(values)
        val sorted = values.copyOf().also { it.sort() }
        val median = sorted[sorted.size / 2]
        val deviations = FloatArray(values.size) { abs(values[it] - median) }
        deviations.sort()
        val exactMad = deviations[deviations.size / 2]
        assertEquals(exactMad, sketch.mad(), 0.002f)
    }

    @Test
    fun `weighted items reproduce the sketch quantiles`() {
        val sketch = KllSketch.of(lognormalish(40_000))
        val items = sketch.weightedItems()
        var total = 0L
        for (w in items.weights) total += w
        assertTrue(abs(total - sketch.count) <= sketch.count / 100, "weights $total vs ${sketch.count}")
        for (i in 1 until items.values.size) {
            assertTrue(items.values[i - 1] <= items.values[i], "items must be ascending")
        }
    }

    @Test
    fun `exact quantiles helper matches the nearest-rank definition`() {
        val values = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)
        val q = KllSketch.exactQuantiles(values, doubleArrayOf(0.0, 0.1, 0.5, 0.9, 1.0))
        assertEquals(listOf(1f, 1f, 5f, 9f, 10f), q.toList())
    }
}
