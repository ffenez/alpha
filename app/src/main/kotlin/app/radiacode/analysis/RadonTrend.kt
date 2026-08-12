package app.radiacode.analysis

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Радоновый индикатор: net-скорости счёта в ROI дочерних продуктов распада
 * радона — Bi-214 (609,3 кэВ) и Pb-214 (351,9 кэВ) — по интервальным
 * спектрам (разности последовательных снимков одного накопления), сведённые
 * в часовые точки.
 *
 * Это *относительный* индикатор: без калибровки объёмной активности и
 * геометрии он принципиально не является концентрацией в Бк/м³ — вся
 * формулировка в UI обязана это повторять. Физика, на которую он опирается:
 * продукты распада радона в воздухе приходят в равновесие за десятки минут,
 * поэтому «проветрите и наблюдайте спад» — честная проверка.
 *
 * ROI: линия ± FWHM(E) (модель разрешения [PeakDetection.fwhmKeV]),
 * континуум — среднее двух боковых окон той же ширины, σ — Пуассон с
 * пропагацией вычитания континуума. JVM-тесты на синтетике.
 */
object RadonTrend {

    /** Bi-214 principal line, keV (Ra-226 chain). */
    const val BI214_KEV = 609.3f

    /** Pb-214 principal line, keV (Ra-226 chain). */
    const val PB214_KEV = 351.9f

    // --- ROI net extraction ---

    data class RoiNet(val netCounts: Float, val sigmaCounts: Float)

    /**
     * Net counts above the local continuum in the ROI [energyKeV] ± FWHM.
     * Continuum = mean of two side windows one ROI-width away on each side.
     * σ² = gross + (w/side)²·sideSum (Poisson + continuum-estimate variance).
     * Null when the ROI or its side windows fall off the spectrum.
     */
    fun roiNet(counts: List<Int>, calibration: EnergyCalibration, energyKeV: Float): RoiNet? {
        val fwhm = PeakDetection.fwhmKeV(energyKeV)
        val lo = calibration.channelAt(energyKeV - fwhm).toInt()
        val hi = ceil(calibration.channelAt(energyKeV + fwhm)).toInt()
        if (lo <= 0 || hi >= counts.size || hi <= lo) return null
        val width = hi - lo + 1
        val sideLo1 = lo - width
        val sideHi2 = hi + width
        if (sideLo1 < 0 || sideHi2 >= counts.size) return null

        var gross = 0.0
        for (ch in lo..hi) gross += counts[ch]
        var side = 0.0
        var sideChannels = 0
        for (ch in sideLo1 until lo) {
            side += counts[ch]; sideChannels++
        }
        for (ch in (hi + 1)..sideHi2) {
            side += counts[ch]; sideChannels++
        }
        val scale = width.toDouble() / sideChannels
        val net = gross - side * scale
        val sigma = sqrt(gross + scale * scale * side)
        return RoiNet(net.toFloat(), sigma.toFloat())
    }

    // --- interval extraction from consecutive snapshots ---

    data class Snapshot(
        val timestampMillis: Long,
        val durationSeconds: Long,
        val counts: List<Int>,
        val calibration: EnergyCalibration,
    )

    data class IntervalPoint(
        /** Wall time of the later snapshot. */
        val endMillis: Long,
        /** Accumulation Δt of the pair, seconds. */
        val deltaSeconds: Long,
        val bi214Cps: Float,
        val pb214Cps: Float,
        /** Combined radon-progeny index: (net609 + net352) / Δt, counts/s. */
        val indexCps: Float,
        val sigmaCps: Float,
    )

    /**
     * Diffs consecutive snapshots of the same accumulation into ROI interval
     * rates. A pair is skipped (and the chain re-anchored) when the
     * accumulation was reset (duration did not grow or any channel decreased),
     * the channel grid changed, or the calibrations drifted apart beyond
     * [SpectrumCompare.CALIBRATION_TOLERANCE_KEV].
     */
    fun intervals(snapshots: List<Snapshot>): List<IntervalPoint> {
        val sorted = snapshots.sortedBy { it.timestampMillis }
        val result = mutableListOf<IntervalPoint>()
        var previous: Snapshot? = null
        for (current in sorted) {
            val prev = previous
            previous = current
            if (prev == null) continue
            if (current.counts.size != prev.counts.size) continue
            val delta = current.durationSeconds - prev.durationSeconds
            if (delta <= 0) continue
            if (SpectrumCompare.calibrationDeltaKeV(
                    current.calibration,
                    prev.calibration,
                    current.counts.size,
                ) > SpectrumCompare.CALIBRATION_TOLERANCE_KEV
            ) {
                continue
            }
            var reset = false
            val diff = IntArray(current.counts.size)
            for (i in diff.indices) {
                val d = current.counts[i] - prev.counts[i]
                if (d < 0) {
                    reset = true
                    break
                }
                diff[i] = d
            }
            if (reset) continue

            val diffList = diff.toList()
            val bi = roiNet(diffList, current.calibration, BI214_KEV) ?: continue
            val pb = roiNet(diffList, current.calibration, PB214_KEV) ?: continue
            val seconds = delta.toFloat()
            result += IntervalPoint(
                endMillis = current.timestampMillis,
                deltaSeconds = delta,
                bi214Cps = bi.netCounts / seconds,
                pb214Cps = pb.netCounts / seconds,
                indexCps = (bi.netCounts + pb.netCounts) / seconds,
                sigmaCps = sqrt(
                    bi.sigmaCounts.toDouble() * bi.sigmaCounts +
                        pb.sigmaCounts.toDouble() * pb.sigmaCounts,
                ).toFloat() / seconds,
            )
        }
        return result
    }

    // --- hourly aggregation ---

    data class HourPoint(
        val hourStartMillis: Long,
        /** Δt-weighted mean index rate over the hour, counts/s. */
        val rateCps: Float,
        val sigmaCps: Float,
        /** Measured accumulation seconds inside the hour. */
        val seconds: Long,
    )

    /** Buckets interval points into wall-clock hours; counts over seconds. */
    fun hourly(points: List<IntervalPoint>): List<HourPoint> =
        points.groupBy { it.endMillis / HOUR_MILLIS }
            .toSortedMap()
            .map { (hour, group) ->
                val seconds = group.sumOf { it.deltaSeconds }
                val netCounts = group.sumOf { (it.indexCps * it.deltaSeconds).toDouble() }
                val varCounts = group.sumOf {
                    val s = (it.sigmaCps * it.deltaSeconds).toDouble()
                    s * s
                }
                HourPoint(
                    hourStartMillis = hour * HOUR_MILLIS,
                    rateCps = (netCounts / seconds).toFloat(),
                    sigmaCps = (sqrt(varCounts) / seconds).toFloat(),
                    seconds = seconds,
                )
            }

    /** Median of hourly rates; null until [minHours] hours are measured. */
    fun medianRate(hours: List<HourPoint>, minHours: Int = 3): Float? {
        if (hours.size < minHours) return null
        val sorted = hours.map { it.rateCps }.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }

    // --- trend classification ---

    enum class Trend { RISING, FALLING, FLAT, UNKNOWN }

    /**
     * Least-squares slope over the last [window] hourly points, classified
     * against the series scale: the projected change across the window must
     * exceed [RELATIVE_CHANGE] of the median (or of σ when the median is ~0)
     * to count as a trend — Poisson jitter leaves the direction unresolved.
     */
    fun trend(hours: List<HourPoint>, window: Int = 6): Trend {
        val tail = hours.takeLast(window)
        if (tail.size < 3) return Trend.UNKNOWN
        val n = tail.size
        val meanX = (n - 1) / 2.0
        val meanY = tail.sumOf { it.rateCps.toDouble() } / n
        var num = 0.0
        var den = 0.0
        tail.forEachIndexed { i, p ->
            num += (i - meanX) * (p.rateCps - meanY)
            den += (i - meanX) * (i - meanX)
        }
        if (den == 0.0) return Trend.UNKNOWN
        val slopePerHour = num / den
        val projected = slopePerHour * (n - 1)
        val median = medianRate(hours, minHours = 3) ?: return Trend.UNKNOWN
        val sigma = tail.map { it.sigmaCps.toDouble() }.average()
        val scale = maxOf(kotlin.math.abs(median).toDouble(), sigma, 1e-4)
        return when {
            projected > RELATIVE_CHANGE * scale -> Trend.RISING
            projected < -RELATIVE_CHANGE * scale -> Trend.FALLING
            else -> Trend.FLAT
        }
    }

    // --- snapshot thinning for the 7-day query ---

    data class Meta(val id: Long, val timestampMillis: Long, val durationSeconds: Long)

    /**
     * Picks the last snapshot of every wall-clock hour — hourly resolution
     * needs only these rows, so the screen never loads a week of per-minute
     * autosave blobs.
     */
    fun selectHourlyIds(metas: List<Meta>): List<Long> =
        metas.sortedBy { it.timestampMillis }
            .groupBy { it.timestampMillis / HOUR_MILLIS }
            .toSortedMap()
            .map { (_, group) -> group.last().id }

    const val HOUR_MILLIS = 3_600_000L
    private const val RELATIVE_CHANGE = 0.3
}
