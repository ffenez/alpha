package app.alpha.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Сбор калибровочного набора из уже накопленных снимков: разности соседних
 * снимков, отдельный набор из самых радоновых часов и отказ, когда их мало.
 */
class CalibrationDatasetTest {

    private val calibration = EnergyCalibration(0f, 3f, 0f)
    private val channels = 512
    private val hour = RadonTrend.HOUR_MILLIS

    /** Кумулятивный снимок: [total] импульсов, равномерно по каналам. */
    private fun snapshot(index: Int, perChannel: Int, seconds: Long) = RadonTrend.Snapshot(
        timestampMillis = index * hour,
        durationSeconds = seconds,
        counts = List(channels) { perChannel },
        calibration = calibration,
    )

    @Test
    fun `intervals are differences, so counts are never added twice`() {
        val snapshots = (1..4).map { snapshot(it, perChannel = 100 * it, seconds = 3600L * it) }
        val intervals = CalibrationDataset.intervals(snapshots)
        assertEquals(3, intervals.size)
        assertTrue(intervals.all { it.deltaSeconds == 3600L })
        assertTrue(intervals.all { it.counts.all { c -> c == 100 } })
    }

    @Test
    fun `a reset breaks the chain instead of producing negative counts`() {
        val snapshots = listOf(
            snapshot(1, perChannel = 300, seconds = 10_800L),
            // Накопление сброшено: и время, и счёт упали.
            snapshot(2, perChannel = 50, seconds = 1_800L),
            snapshot(3, perChannel = 150, seconds = 5_400L),
        )
        val intervals = CalibrationDataset.intervals(snapshots)
        assertEquals(1, intervals.size)
        assertEquals(100, intervals.first().counts.first())
    }

    @Test
    fun `merging intervals adds counts and exposure`() {
        val snapshots = (1..4).map { snapshot(it, perChannel = 100 * it, seconds = 3600L * it) }
        val merged = CalibrationDataset.merge(CalibrationDataset.intervals(snapshots))
        assertNotNull(merged)
        assertEquals(3 * 3600L, merged.seconds)
        assertEquals(300, merged.counts.first())
        assertEquals(3, merged.intervalCount)
    }

    /** Часовой интервал с добавленным пиком Bi-214 заданной площади. */
    private fun radonInterval(hourIndex: Int, peakArea: Int): CalibrationDataset.Interval {
        val counts = IntArray(channels) { 100 }
        val center = calibration.channelAt(RadonTrend.BI214_KEV).toInt()
        for (ch in (center - 8)..(center + 8)) counts[ch] += peakArea / 17
        return CalibrationDataset.Interval(
            endMillis = hourIndex * hour,
            deltaSeconds = 3600L,
            counts = counts.toList(),
            calibration = calibration,
            bi214Cps = RadonTrend.roiNet(counts.toList(), calibration, RadonTrend.BI214_KEV)!!
                .netCounts / 3600f,
        )
    }

    @Test
    fun `the radon-rich set is refused while there are too few hours`() {
        val selection = CalibrationDataset.select((1..3).map { radonInterval(it, 0) })
        assertNull(selection.radonRich, "трёх часов на радоновый набор не хватает")
        assertEquals(0, selection.radonHours)
        assertTrue(selection.radonSeconds < CalibrationDataset.MIN_RADON_SECONDS)
        assertNotNull(selection.long, "длинное накопление всё равно собирается")
    }

    @Test
    fun `the washout hours are picked, not just any six`() {
        // Двадцать четыре часа, из них шесть — промывка после дождя.
        val quiet = (1..18).map { radonInterval(it, peakArea = 1_000) }
        val washout = (19..24).map { radonInterval(it, peakArea = 40_000) }
        val selection = CalibrationDataset.select(quiet + washout)
        val radon = selection.radonRich
        assertNotNull(radon)
        assertEquals(6, selection.radonHours)
        val long = selection.long!!
        val radonRate = RadonTrend.roiNet(radon.counts, calibration, RadonTrend.BI214_KEV)!!
            .netCounts / radon.seconds
        val longRate = RadonTrend.roiNet(long.counts, calibration, RadonTrend.BI214_KEV)!!
            .netCounts / long.seconds
        assertTrue(
            radonRate > 2f * longRate,
            "радоновый набор обязан быть богаче: $radonRate против $longRate",
        )
    }
}
