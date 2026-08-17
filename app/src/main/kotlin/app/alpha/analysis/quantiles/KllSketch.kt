package app.alpha.analysis.quantiles

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Compact mergeable quantile sketch (KLL) — the long-window quantile path of
 * ADR 004 and CHART SPEC §30.
 *
 * ## What it is
 *
 * A KLL sketch (Karnin–Lang–Liberty, «Optimal Quantile Approximation in
 * Streams», FOCS 2016) is a hierarchy of sorted buffers («compactors»). The
 * buffer at height `h` stores items that each stand for `2^h` original
 * observations. When a buffer overflows it is sorted and *compacted*: the
 * items are taken in pairs and one of each pair — the even or the odd one,
 * decided by a coin flip — is promoted to the next height with double weight.
 * The discarded item is what makes the structure small; the coin flip is what
 * makes the induced rank error zero-mean instead of systematically biased.
 *
 * Buffer capacities grow towards the top:
 *
 * ```text
 * capacity(height) = max(MIN_LEVEL_CAPACITY, k · (2/3)^(topHeight − height))
 * ```
 *
 * so the total number of stored items stays ≈ 3·[k] regardless of how many
 * observations went in, and the compactions that hurt most (high weight) are
 * the rarest.
 *
 * ## Error behaviour
 *
 * The estimate is a **rank** estimate: for a queried value the returned
 * quantile has normalized rank within ±ε of the requested probability, where
 * ε ≈ c/[k] with a small constant (the reference implementation of the same
 * structure, Apache DataSketches, documents ε ≈ 2.296/k at 99 % confidence).
 * With the default [DEFAULT_K] that is ≈ 1.8 % of rank; the measured error on
 * synthetic data is pinned by `KllSketchTest` (CHART SPEC §37G). Two
 * properties matter for this app and are *exact*, not approximate:
 *
 *  - [min] and [max] are kept verbatim, so the extrema of a window never
 *    depend on the sketch (CHART SPEC §21 — a short transient must survive);
 *  - [count] is the true number of observations.
 *
 * The error is on rank, not on value: in a flat part of the distribution a
 * 1 % rank error can be an invisible value difference, and on a steep tail the
 * same rank error can be a large one. That is why the app reports the quantile
 * *method* next to any number it produced (CHART SPEC §32).
 *
 * ## Determinism
 *
 * The compaction coin must be random enough to keep the error unbiased, but a
 * test must be able to replay a sketch bit for bit. So there is **no wall
 * clock and no global RNG**: the coin comes from a SplitMix64 stream seeded by
 * [dataHash], a commutative (wrapping-sum) hash of the values that were fed
 * in, mixed with a compaction counter. Same data in — same sketch out, on any
 * device, in any process. [dataHash] is order-independent by construction, so
 * two sketches over the same multiset start their coin stream at the same
 * place whatever order the values arrived in.
 *
 * ## Why our own implementation
 *
 * ADR 004: Apache DataSketches would add a library to the APK for one
 * structure, and its compaction uses a global/threaded RNG that our
 * reproducibility rule (spec §22) forbids. This file is ~250 lines and is
 * validated against exact order statistics in the tests.
 *
 * ## Units
 *
 * The sketch is unit-agnostic: it stores whatever floats it is fed. The app
 * stores **raw device dose-rate units** (the same as `samples.doseRate`) and
 * converts on display via [scaled], which is exact because the conversion is a
 * multiplication by a positive constant and therefore order-preserving.
 *
 * Values must be finite and non-negative (dose rates are magnitudes); negative
 * inputs are clamped to 0 and non-finite ones are ignored, so a single bad
 * reading can never poison a whole hour.
 */
class KllSketch private constructor(
    val k: Int,
    private val levels: MutableList<FloatBuffer>,
    count: Long,
    minValue: Float,
    maxValue: Float,
    dataHash: Long,
    compactions: Long,
) {

    constructor(k: Int = DEFAULT_K) : this(
        k = validK(k),
        levels = mutableListOf(FloatBuffer()),
        count = 0L,
        minValue = Float.NaN,
        maxValue = Float.NaN,
        dataHash = 0L,
        compactions = 0L,
    )

    /** Observations fed in — exact, not an estimate. */
    var count: Long = count
        private set

    private var minValue: Float = minValue
    private var maxValue: Float = maxValue

    /** Commutative hash of the ingested values; seeds the compaction coin. */
    private var dataHash: Long = dataHash

    private var compactions: Long = compactions

    val isEmpty: Boolean get() = count == 0L

    /** Smallest observation, exact; NaN when empty. */
    val min: Float get() = minValue

    /** Largest observation, exact; NaN when empty. */
    val max: Float get() = maxValue

    /** Items currently stored — the memory the sketch actually costs. */
    val storedItems: Int get() = levels.sumOf { it.size }

    // --- ingestion ---------------------------------------------------------

    fun update(value: Float) {
        if (!value.isFinite()) return
        val v = max(0f, value)
        count++
        minValue = if (minValue.isNaN()) v else min(minValue, v)
        maxValue = if (maxValue.isNaN()) v else max(maxValue, v)
        dataHash += mix64(java.lang.Float.floatToIntBits(v).toLong() * GOLDEN)
        levels[0].add(v)
        compactWhileNeeded()
    }

    fun update(values: FloatArray) {
        for (v in values) update(v)
    }

    /**
     * Absorbs [other]. Mergeable in the sense CHART SPEC §30 asks for: the
     * result estimates the quantiles of the *union* of both inputs, with the
     * same error guarantee. Merging is commutative and associative **within
     * the documented tolerance** — the compaction coins fall differently
     * depending on the order, so the answers differ by at most the sketch's
     * own error, not by nothing (pinned by `KllSketchTest`).
     */
    fun merge(other: KllSketch) {
        if (other.count == 0L) return
        while (levels.size < other.levels.size) levels.add(FloatBuffer())
        for (h in other.levels.indices) levels[h].addAll(other.levels[h])
        count += other.count
        minValue = if (minValue.isNaN()) other.minValue else min(minValue, other.minValue)
        maxValue = if (maxValue.isNaN()) other.maxValue else max(maxValue, other.maxValue)
        dataHash += other.dataHash
        compactWhileNeeded()
    }

    /** A copy with every stored value multiplied by [factor] (must be > 0). */
    fun scaled(factor: Float): KllSketch {
        require(factor > 0f && factor.isFinite()) { "scale factor must be positive" }
        return KllSketch(
            k = k,
            levels = levels.mapTo(mutableListOf()) { it.scaledCopy(factor) },
            count = count,
            minValue = if (minValue.isNaN()) minValue else minValue * factor,
            maxValue = if (maxValue.isNaN()) maxValue else maxValue * factor,
            dataHash = dataHash,
            compactions = compactions,
        )
    }

    fun copy(): KllSketch = scaled(1f)

    // --- queries -----------------------------------------------------------

    /**
     * Nearest-rank quantile: the smallest stored value whose cumulative weight
     * reaches `p·n`. Same definition the exact path and the baseline engine
     * use, so the two paths are comparable without a definition mismatch (no
     * interpolation, always a value that was really measured).
     */
    fun quantile(p: Double): Float = quantiles(doubleArrayOf(p))[0]

    /** Several ascending probabilities in one pass over the sorted items. */
    fun quantiles(probabilities: DoubleArray): FloatArray {
        val out = FloatArray(probabilities.size)
        if (count == 0L) return out
        val sorted = packedSorted()
        var total = 0L
        for (packed in sorted) total += weightOf(packed)
        var qi = 0
        var cumulative = 0L
        for (packed in sorted) {
            cumulative += weightOf(packed)
            val value = valueOf(packed)
            while (qi < probabilities.size && cumulative >= probabilities[qi] * total) {
                out[qi] = value
                qi++
            }
            if (qi >= probabilities.size) break
        }
        val last = valueOf(sorted.last())
        while (qi < probabilities.size) {
            out[qi] = last
            qi++
        }
        return out
    }

    /** Estimated fraction of observations ≤ [value], 0..1. */
    fun rank(value: Float): Double {
        if (count == 0L) return 0.0
        var total = 0L
        var below = 0L
        for (h in levels.indices) {
            val weight = 1L shl h
            val buffer = levels[h]
            for (i in 0 until buffer.size) {
                total += weight
                if (buffer[i] <= value) below += weight
            }
        }
        return if (total == 0L) 0.0 else below.toDouble() / total
    }

    /**
     * Approximate MAD = median(|xᵢ − median|), **without** the 1.4826 factor
     * (that factor assumes normality, which the scientific instruction forbids
     * assuming). Computed over the sketch's weighted items, i.e. it inherits
     * the sketch's error and is reported as approximate like the quantiles.
     */
    fun mad(): Float {
        if (count == 0L) return 0f
        val median = quantile(0.5)
        val values = FloatArray(storedItems)
        val weights = IntArray(values.size)
        var i = 0
        for (h in levels.indices) {
            val weight = 1 shl h
            val buffer = levels[h]
            for (j in 0 until buffer.size) {
                values[i] = abs(buffer[j] - median)
                weights[i] = weight
                i++
            }
        }
        return weightedNearestRank(values, weights, 0.5)
    }

    /**
     * The stored items with their weights, ascending by value — the sketch's
     * view of the distribution. Feeds the distribution strip of the chart,
     * which is then an approximation of the same nature as the quantiles.
     */
    fun weightedItems(): WeightedItems {
        val sorted = packedSorted()
        val values = FloatArray(sorted.size)
        val weights = IntArray(sorted.size)
        for (i in sorted.indices) {
            values[i] = valueOf(sorted[i])
            weights[i] = weightOf(sorted[i]).toInt()
        }
        return WeightedItems(values, weights)
    }

    /** Ascending (value, weight) pairs; weights sum to ≈ [count]. */
    class WeightedItems(val values: FloatArray, val weights: IntArray)

    // --- serialization -----------------------------------------------------

    /**
     * Little-endian blob stored in `hour_sketches.sketch`. The RNG state
     * ([dataHash], [compactions]) is part of the payload, so a deserialized
     * sketch that is fed more data behaves exactly like the original — a
     * round trip is a true identity, which is what makes the pre-aggregation
     * rebuildable and the tests bit-reproducible.
     */
    fun toByteArray(): ByteArray {
        val size = HEADER_BYTES + levels.size * 4 + storedItems * 4
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(MAGIC)
        buffer.put(FORMAT_VERSION)
        buffer.put(levels.size.toByte())
        buffer.putInt(k)
        buffer.putLong(count)
        buffer.putLong(dataHash)
        buffer.putLong(compactions)
        buffer.putFloat(minValue)
        buffer.putFloat(maxValue)
        for (level in levels) {
            buffer.putInt(level.size)
            for (i in 0 until level.size) buffer.putFloat(level[i])
        }
        return buffer.array()
    }

    // --- internals ---------------------------------------------------------

    private fun capacityOf(level: Int, levelCount: Int): Int {
        var capacity = k.toDouble()
        repeat(levelCount - level - 1) { capacity *= SHRINK }
        val rounded = max(MIN_LEVEL_CAPACITY, ceil(capacity).toInt())
        return if (rounded % 2 == 0) rounded else rounded + 1
    }

    private fun totalCapacity(): Int {
        var total = 0
        for (level in levels.indices) total += capacityOf(level, levels.size)
        return total
    }

    private fun compactWhileNeeded() {
        while (storedItems > totalCapacity()) {
            var level = 0
            while (level < levels.size && levels[level].size < capacityOf(level, levels.size)) {
                level++
            }
            // Unreachable unless every level is under capacity, which would
            // contradict the loop condition; the guard keeps a future change
            // from turning an invariant break into an infinite loop.
            if (level >= levels.size) return
            compact(level)
        }
    }

    private fun compact(level: Int) {
        if (level + 1 == levels.size) levels.add(FloatBuffer())
        val buffer = levels[level]
        buffer.sort()
        val n = buffer.size
        if (n < 2) return
        val coin = nextCoin()
        val odd = n % 2 == 1
        // Odd buffers keep one item at this level. Which one is decided by the
        // same coin, so keeping the leftover never biases the estimate up or
        // down systematically. [start, end) always spans an even count.
        val keepLast = odd && (coin and 2L) != 0L
        val start = if (odd && !keepLast) 1 else 0
        val end = if (odd && keepLast) n - 1 else n
        val offset = (coin and 1L).toInt()
        val target = levels[level + 1]
        var i = start
        while (i + 1 < end) {
            target.add(buffer[i + offset])
            i += 2
        }
        val keptValue = when {
            !odd -> null
            keepLast -> buffer[n - 1]
            else -> buffer[0]
        }
        buffer.clear()
        if (keptValue != null) buffer.add(keptValue)
    }

    /** SplitMix64 over (data hash, compaction index) — no global state. */
    private fun nextCoin(): Long {
        compactions++
        return mix64(dataHash + compactions * GOLDEN)
    }

    private fun packedSorted(): LongArray {
        val packed = LongArray(storedItems)
        var i = 0
        for (h in levels.indices) {
            val weight = (1L shl h) and 0xFFFF_FFFFL
            val buffer = levels[h]
            for (j in 0 until buffer.size) {
                val bits = java.lang.Float.floatToIntBits(max(0f, buffer[j])).toLong()
                packed[i] = (bits shl 32) or weight
                i++
            }
        }
        Arrays.sort(packed)
        return packed
    }

    companion object {

        /**
         * Accuracy parameter: capacity of the topmost buffer. Rank error
         * scales as ≈ 2.3/k, memory as ≈ 3·k floats. 128 gives ≈ 1.8 % rank
         * error for ≈ 1.5 KB per stored hour — see ADR 004 for the arithmetic
         * that picked it and `KllSketchTest` for the measured error.
         */
        const val DEFAULT_K = 128

        /** Smallest buffer capacity; must be even so pairing is exact. */
        const val MIN_LEVEL_CAPACITY = 8

        /**
         * Version of *this* structure and its parameters, stored beside every
         * blob (`hour_sketches.algorithmVersion`) and mirrored in
         * [app.alpha.analysis.AlgorithmVersions.QUANTILE_SKETCH]. A change
         * here means stored sketches must be rebuilt from raw samples.
         */
        const val ALGORITHM_VERSION = 1

        /** Human name of the method for metadata and Research details (§32). */
        const val METHOD = "kll"

        private const val SHRINK = 2.0 / 3.0
        private const val GOLDEN = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        private const val MAGIC: Short = 0x4B4C // "KL"
        private const val FORMAT_VERSION: Byte = 1
        private const val HEADER_BYTES = 2 + 1 + 1 + 4 + 8 + 8 + 8 + 4 + 4

        fun of(values: FloatArray, k: Int = DEFAULT_K): KllSketch =
            KllSketch(k).apply { update(values) }

        /**
         * Reads a blob written by [toByteArray]. Returns null for anything it
         * does not recognise — a stored sketch that cannot be read must degrade
         * to «no pre-aggregate for this hour», never take a screen down.
         */
        fun fromByteArray(bytes: ByteArray?): KllSketch? {
            if (bytes == null || bytes.size < HEADER_BYTES) return null
            return try {
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                if (buffer.short != MAGIC) return null
                if (buffer.get() != FORMAT_VERSION) return null
                val levelCount = buffer.get().toInt()
                if (levelCount <= 0 || levelCount > 64) return null
                val k = buffer.int
                if (k < MIN_LEVEL_CAPACITY || k > 1 shl 16) return null
                val count = buffer.long
                val dataHash = buffer.long
                val compactions = buffer.long
                val minValue = buffer.float
                val maxValue = buffer.float
                val levels = ArrayList<FloatBuffer>(levelCount)
                for (h in 0 until levelCount) {
                    val size = buffer.int
                    if (size < 0 || size > buffer.remaining() / 4) return null
                    val level = FloatBuffer(max(8, size))
                    for (i in 0 until size) level.add(buffer.float)
                    levels.add(level)
                }
                KllSketch(
                    k = k,
                    levels = levels.toMutableList(),
                    count = count,
                    minValue = minValue,
                    maxValue = maxValue,
                    dataHash = dataHash,
                    compactions = compactions,
                )
            } catch (_: RuntimeException) {
                null
            }
        }

        /** Merges many sketches into one; null when the list is empty. */
        fun mergeAll(sketches: List<KllSketch>): KllSketch? {
            if (sketches.isEmpty()) return null
            val result = sketches.first().copy()
            for (i in 1 until sketches.size) result.merge(sketches[i])
            return result
        }

        /** Exact nearest-rank percentile of raw values — the reference path. */
        fun exactQuantiles(values: FloatArray, probabilities: DoubleArray): FloatArray {
            val out = FloatArray(probabilities.size)
            if (values.isEmpty()) return out
            val sorted = values.copyOf()
            Arrays.sort(sorted)
            for (i in probabilities.indices) {
                val rank = ceil(probabilities[i] * sorted.size).toInt().coerceIn(1, sorted.size)
                out[i] = sorted[rank - 1]
            }
            return out
        }

        private fun validK(k: Int): Int = k.coerceIn(MIN_LEVEL_CAPACITY, 1 shl 16)

        private fun weightOf(packed: Long): Long = packed and 0xFFFF_FFFFL

        private fun valueOf(packed: Long): Float =
            java.lang.Float.intBitsToFloat((packed ushr 32).toInt())

        private fun mix64(value: Long): Long {
            var z = value + GOLDEN
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        private fun weightedNearestRank(values: FloatArray, weights: IntArray, p: Double): Float {
            if (values.isEmpty()) return 0f
            val packed = LongArray(values.size)
            for (i in values.indices) {
                val bits = java.lang.Float.floatToIntBits(max(0f, values[i])).toLong()
                packed[i] = (bits shl 32) or (weights[i].toLong() and 0xFFFF_FFFFL)
            }
            Arrays.sort(packed)
            var total = 0L
            for (item in packed) total += weightOf(item)
            var cumulative = 0L
            for (item in packed) {
                cumulative += weightOf(item)
                if (cumulative >= p * total) return valueOf(item)
            }
            return valueOf(packed.last())
        }
    }
}

/** Growable primitive float buffer — one compactor level. */
internal class FloatBuffer(capacity: Int = 16) {
    private var items = FloatArray(max(1, capacity))
    var size: Int = 0
        private set

    operator fun get(index: Int): Float = items[index]

    fun add(value: Float) {
        if (size == items.size) items = items.copyOf(max(4, items.size * 2))
        items[size++] = value
    }

    fun addAll(other: FloatBuffer) {
        for (i in 0 until other.size) add(other[i])
    }

    fun clear() {
        size = 0
    }

    fun sort() {
        Arrays.sort(items, 0, size)
    }

    fun scaledCopy(factor: Float): FloatBuffer {
        val copy = FloatBuffer(max(1, size))
        for (i in 0 until size) copy.add(items[i] * factor)
        return copy
    }
}
