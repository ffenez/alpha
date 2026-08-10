package app.radiacode.ui.logic

import kotlin.math.max

/**
 * What a highlighted event on the dose chart is measured **against**. The two
 * are different classes of event and the chart must never blur them (CHART
 * SPEC §10, §20): the alarm level is a product setting, the profile percentile
 * is a historical statistic of this place and is not a safety threshold.
 */
enum class DoseReference {
    /** Above the named alarm level «L1 …». */
    ALARM_L1,

    /** Above the historical P90 of the active profile's baseline. */
    BASELINE_P90,
}

/** «выше порога L1» / «выше исторического P90 профиля» — the reference, named. */
fun referenceWording(reference: DoseReference): String = when (reference) {
    DoseReference.ALARM_L1 -> "выше порога L1"
    DoseReference.BASELINE_P90 -> "выше исторического P90 профиля"
}

/** Short form for narrow labels; still names the denominator. */
fun referenceWordingShort(reference: DoseReference): String = when (reference) {
    DoseReference.ALARM_L1 -> "> L1"
    DoseReference.BASELINE_P90 -> "> P90 профиля"
}

/** A stretch of the window that stayed above a named reference (§20). */
data class DoseEpisode(
    val fromMillis: Long,
    val toMillis: Long,
    /** Peak bucket max inside the episode, µSv/h — extrema are preserved. */
    val peak: Float,
    /** When that peak was measured (sub-bucket resolution of its column). */
    val peakAtMillis: Long,
    val reference: DoseReference,
) {
    val durationMillis: Long get() = toMillis - fromMillis
}

/**
 * A bin whose extremum is worth a discrete marker above the plot (§7, §21).
 * [bucketIndex] indexes the bucket list the markers were built from.
 */
data class ExtremeMarker(
    val bucketIndex: Int,
    val valueMicroSvH: Float,
    val atMillis: Long,
    /** Honest width of [atMillis] — 1 s means «exactly at», wider is an interval. */
    val windowMillis: Long,
    val reference: DoseReference,
)

/**
 * Extremum markers: how a short transient survives a long time scale.
 *
 * The chart deliberately does **not** paint a min–max fill (§7): an extremum
 * grows with the number of samples in a bin, so a filled envelope widens with
 * the bin width alone and reads as a confidence interval it is not. But
 * extrema may not be hidden either, or a spike shorter than one bin would
 * vanish at 24 h / 7 d (§21). So each bin keeps its exact min/max with their
 * timestamps, and the bins whose maximum is *notable* get a discrete marker
 * that the user can tap.
 *
 * **The rule** — a bin is marked when both hold:
 *
 *  1. **It rises above a named external reference**: `max ≥ alarm L1`
 *     ([DoseReference.ALARM_L1]) or, failing that, `max ≥ baseline P90`
 *     ([DoseReference.BASELINE_P90]). Without a reference there is nothing to
 *     be notable *against*, so no markers are produced.
 *  2. **It stands out from the bin's own robust spread**:
 *     `max ≥ q90 + `[IQR_STEP]` · IQR` **and** `max > q90`. This is the Tukey
 *     fence step applied to the top of the outer envelope. Its job is to
 *     separate a *transient* from a *level*: a bin that merely sits high
 *     (a step change, an elevated place) has its maximum close to its own Q90
 *     and gets no marker — the median line and the episode band already tell
 *     that story. A bin whose top sample is far above its own bulk is exactly
 *     the spike aggregation would otherwise swallow.
 *
 * The strict `max > q90` also settles the degenerate cases honestly: a bin
 * with one sample (or with all values equal) has `max == q90`, so it is never
 * called a transient — with a single measurement there is no evidence of a
 * spike *inside* the bin, and the median line already carries that value at
 * full height.
 */
object DoseExtremes {

    /** Tukey-style step above Q90, in IQRs of the same bin. */
    const val IQR_STEP = 1.5f

    /** The reference a bin's extremum is notable against, or null. */
    fun classify(
        bucket: ChartBucket,
        alarmMicroSvH: Float?,
        baselineP90MicroSvH: Float?,
    ): DoseReference? {
        if (!standsOut(bucket)) return null
        val alarm = alarmMicroSvH?.takeIf { it > 0f }
        if (alarm != null && bucket.max >= alarm) return DoseReference.ALARM_L1
        val p90 = baselineP90MicroSvH?.takeIf { it > 0f }
        if (p90 != null && bucket.max >= p90) return DoseReference.BASELINE_P90
        return null
    }

    /** Condition 2 of the rule: the extremum is not just the bin's level. */
    fun standsOut(bucket: ChartBucket): Boolean =
        bucket.max > bucket.q90 && bucket.max >= bucket.q90 + IQR_STEP * max(0f, bucket.iqr)

    fun markers(
        buckets: List<ChartBucket>,
        alarmMicroSvH: Float?,
        baselineP90MicroSvH: Float?,
    ): List<ExtremeMarker> {
        if (buckets.isEmpty()) return emptyList()
        val out = ArrayList<ExtremeMarker>()
        buckets.forEachIndexed { index, bucket ->
            val reference = classify(bucket, alarmMicroSvH, baselineP90MicroSvH) ?: return@forEachIndexed
            out += ExtremeMarker(
                bucketIndex = index,
                valueMicroSvH = bucket.max,
                atMillis = bucket.maxAtMillis,
                windowMillis = bucket.extremeWindowMillis,
                reference = reference,
            )
        }
        return out
    }
}

/**
 * Deviation episodes drawn as vertical bands (§20).
 *
 * The **anchor is the journal**: every band starts from a recorded event of
 * the `events` table (a confirmed persistent deviation or a track hotspot).
 * Its extent is then CALCULATED by walking the visible columns outward while
 * they stay above the **same reference the episode is classified by**,
 * because the journal stores one row per episode and not its end.
 *
 * The classification is what the data shows, so it can be checked on screen:
 * a column that reaches the alarm level is an [DoseReference.ALARM_L1]
 * episode, otherwise one that reaches the profile's historical P90 is a
 * [DoseReference.BASELINE_P90] episode. The two are drawn and labelled
 * differently — they are different statements, and merging them would turn a
 * historical percentile into a threshold (§8, §10).
 */
object DoseEpisodes {

    fun around(
        buckets: List<ChartBucket>,
        eventTimesMillis: List<Long>,
        alarmMicroSvH: Float?,
        baselineP90MicroSvH: Float? = null,
    ): List<DoseEpisode> {
        if (buckets.isEmpty() || eventTimesMillis.isEmpty()) return emptyList()
        val alarm = alarmMicroSvH?.takeIf { it > 0f && it.isFinite() }
        val p90 = baselineP90MicroSvH?.takeIf { it > 0f && it.isFinite() }
        val result = ArrayList<DoseEpisode>()
        for (time in eventTimesMillis.sorted()) {
            val anchor = indexAt(buckets, time) ?: continue
            if (result.any { time in it.fromMillis..it.toMillis }) continue
            val anchorMax = buckets[anchor].max
            val reference = when {
                alarm != null && anchorMax >= alarm -> DoseReference.ALARM_L1
                p90 != null && anchorMax >= p90 -> DoseReference.BASELINE_P90
                // The journal recorded an event the visible columns do not
                // reach (a shorter spike than the column, a different
                // aggregation). It still gets a one-column band, named after
                // the reference the app actually has.
                p90 != null -> DoseReference.BASELINE_P90
                alarm != null -> DoseReference.ALARM_L1
                else -> continue
            }
            val level = if (reference == DoseReference.ALARM_L1) alarm!! else p90!!
            var lo = anchor
            var hi = anchor
            if (anchorMax >= level) {
                while (lo > 0 && buckets[lo - 1].max >= level) lo--
                while (hi < buckets.size - 1 && buckets[hi + 1].max >= level) hi++
            }
            var peak = 0f
            var peakAt = buckets[lo].maxAtMillis
            for (i in lo..hi) {
                if (buckets[i].max > peak) {
                    peak = buckets[i].max
                    peakAt = buckets[i].maxAtMillis
                }
            }
            result += DoseEpisode(
                fromMillis = buckets[lo].startMillis,
                toMillis = buckets[hi].endMillis,
                peak = peak,
                peakAtMillis = peakAt,
                reference = reference,
            )
        }
        return result
    }

    /** Index of the column containing [timeMillis], or null when outside. */
    fun indexAt(buckets: List<ChartBucket>, timeMillis: Long): Int? {
        if (buckets.isEmpty()) return null
        if (timeMillis < buckets.first().startMillis) return null
        if (timeMillis > buckets.last().endMillis) return null
        var lo = 0
        var hi = buckets.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val b = buckets[mid]
            when {
                timeMillis < b.startMillis -> hi = mid - 1
                timeMillis >= b.endMillis -> lo = mid + 1
                else -> return mid
            }
        }
        return null
    }
}
