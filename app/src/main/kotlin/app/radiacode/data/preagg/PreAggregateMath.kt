package app.radiacode.data.preagg

import app.radiacode.analysis.quantiles.KllSketch
import app.radiacode.data.db.HourSketchEntity
import app.radiacode.data.db.MinuteStatEntity
import app.radiacode.data.db.RawSampleRow

/**
 * Pure reduction of raw samples into the pre-aggregation of ADR 004: minute
 * scalars and one hourly quantile sketch. No database, no clock — so every
 * rule below is unit-tested on the JVM against hand-built sample lists.
 *
 * **Idempotence by construction.** Nothing here increments a stored counter:
 * a minute (or an hour) is always recomputed *from the raw samples it
 * contains*. Running the aggregation twice, or being killed halfway through a
 * minute and restarting, gives byte-identical rows — which is what makes the
 * writer in [PreAggregator] safe without transactions around every minute.
 */
object PreAggregateMath {

    const val MINUTE_MILLIS = 60_000L
    const val HOUR_MILLIS = 3_600_000L

    fun minuteStartOf(timestamp: Long): Long =
        Math.floorDiv(timestamp, MINUTE_MILLIS) * MINUTE_MILLIS

    fun hourStartOf(timestamp: Long): Long =
        Math.floorDiv(timestamp, HOUR_MILLIS) * HOUR_MILLIS

    /**
     * Minute rows for [rows] (any order; grouped by the minute boundary).
     *
     * The extremum timestamps are the **instants** of the samples that held
     * the extremes, not the interval they live in — that is what lets a
     * five-second transient keep its exact time on a 30-day chart (CHART SPEC
     * §21). Ties keep the earliest instant: the first time a level was reached
     * is the honest answer to «когда».
     */
    fun minutes(rows: List<RawSampleRow>): List<MinuteStatEntity> {
        if (rows.isEmpty()) return emptyList()
        val builders = LinkedHashMap<Long, MinuteBuilder>()
        for (row in rows) {
            if (!row.doseRate.isFinite()) continue
            builders.getOrPut(minuteStartOf(row.timestamp)) { MinuteBuilder() }.add(row)
        }
        return builders.entries
            .sortedBy { it.key }
            .mapNotNull { (start, builder) -> builder.build(start) }
    }

    /**
     * The hourly sketch of [rows] (all of which must belong to [hourStart]),
     * or null when the hour holds no usable sample.
     *
     * The sketch is built by feeding the samples **in time order**, so the
     * result is reproducible from the raw table alone; the scalars beside it
     * (count, extremes and their instants) stay exact.
     */
    fun hour(hourStart: Long, rows: List<RawSampleRow>, k: Int = KllSketch.DEFAULT_K): HourSketchEntity? {
        val sketch = KllSketch(k)
        var count = 0
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var minAt = hourStart
        var maxAt = hourStart
        for (row in rows.sortedBy { it.timestamp }) {
            val value = row.doseRate
            if (!value.isFinite()) continue
            count++
            sketch.update(value)
            if (value < min) {
                min = value
                minAt = row.timestamp
            }
            if (value > max) {
                max = value
                maxAt = row.timestamp
            }
        }
        if (count == 0) return null
        return HourSketchEntity(
            hourStart = hourStart,
            count = count,
            minDoseRate = min,
            maxDoseRate = max,
            minAtMillis = minAt,
            maxAtMillis = maxAt,
            sketch = sketch.toByteArray(),
            algorithmVersion = KllSketch.ALGORITHM_VERSION,
            sketchK = k,
        )
    }

    private class MinuteBuilder {
        private var count = 0
        private var admitted = 0
        private var sum = 0.0
        private var sumSq = 0.0
        private var min = Float.MAX_VALUE
        private var max = -Float.MAX_VALUE
        private var minAt = 0L
        private var maxAt = 0L
        private var first = Long.MAX_VALUE
        private var last = Long.MIN_VALUE
        private var profileId: Long? = null
        private var profileMixed = false

        fun add(row: RawSampleRow) {
            val value = row.doseRate
            count++
            if (row.admitted != 0) admitted++
            sum += value
            sumSq += value.toDouble() * value
            if (value < min || (value == min && row.timestamp < minAt)) {
                min = value
                minAt = row.timestamp
            }
            if (value > max || (value == max && row.timestamp < maxAt)) {
                max = value
                maxAt = row.timestamp
            }
            if (row.timestamp < first) first = row.timestamp
            if (row.timestamp > last) last = row.timestamp
            if (count == 1) {
                profileId = row.profileId
            } else if (row.profileId != profileId) {
                profileMixed = true
            }
        }

        fun build(start: Long): MinuteStatEntity? {
            if (count == 0) return null
            return MinuteStatEntity(
                minuteStart = start,
                count = count,
                minDoseRate = min,
                maxDoseRate = max,
                sumDoseRate = sum,
                sumSqDoseRate = sumSq,
                minAtMillis = minAt,
                maxAtMillis = maxAt,
                firstSampleTime = first,
                lastSampleTime = last,
                admittedCount = admitted,
                profileId = if (profileMixed) null else profileId,
            )
        }
    }
}
