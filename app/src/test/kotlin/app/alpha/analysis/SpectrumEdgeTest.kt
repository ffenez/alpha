package app.alpha.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Крайний канал не должен становиться «пиком около 2,8 МэВ» — ни на кривой,
 * ни в поиске пиков, ни в масштабе оси.
 */
class SpectrumEdgeTest {

    private val calibration = EnergyCalibration(a0 = -10f, a1 = 2.4f, a2 = 0.0003f)

    /** Гладкий спадающий континуум и «стена» в последнем канале. */
    private fun spectrumWithEdgeSpike(channels: Int = 1024, spike: Int = 8_000): List<Int> =
        List(channels) { i ->
            when {
                i == channels - 1 -> spike
                i < 20 -> 0
                else -> (2_000.0 / (1.0 + i * 0.05)).toInt()
            }
        }

    @Test
    fun `the edge channel is not a spectral point`() {
        assertEquals(1022, SpectrumEdge.lastAnalysableChannel(1024))
        assertEquals(0..1022, SpectrumEdge.analysable(1024))
        assertEquals(8_000L, SpectrumEdge.edgeCounts(spectrumWithEdgeSpike()))
    }

    @Test
    fun `a one-channel wall never becomes a peak`() {
        val peaks = PeakDetection.detect(spectrumWithEdgeSpike(), calibration)
        val edgeEnergy = calibration.energyAt(1023f)
        assertTrue(
            peaks.none { it.channel >= 1022 || it.energyKeV >= edgeEnergy - 20f },
            "край попал в пики: $peaks",
        )
    }

    @Test
    fun `the axis is not scaled by the edge channel`() {
        // Окно и диапазон каналов заканчиваются ДО края, поэтому «стена» не
        // участвует в агрегации колонок и не сжимает всю картинку.
        val full = SpectrumDisplay.fullWindow(calibration, 1024)
        assertTrue(full.endKeV < calibration.energyAt(1023f))
        val range = SpectrumDisplay.channelRange(full, calibration, 1024)
        assertTrue(range.last <= 1022, "${range.last}")

        val counts = spectrumWithEdgeSpike()
        val columns = SpectrumDisplay.aggregateMax(
            counts.map { it.toFloat() },
            range,
            columnCount = 120,
        )
        assertTrue(columns.max() < 8_000f, "стена попала в колонки: ${columns.max()}")
    }

    @Test
    fun `a short spectrum degrades instead of throwing`() {
        assertEquals(0, SpectrumEdge.lastAnalysableChannel(1))
        assertEquals(0L, SpectrumEdge.edgeCounts(listOf(5)))
        assertEquals(1, SpectrumEdge.withoutEdge(DoubleArray(1) { 5.0 }).size)
    }

    @Test
    fun `the explanation states the boundary, not an unproven mechanism`() {
        val text = SpectrumEdge.EXPLANATION.lowercase()
        assertTrue(text.contains("граница"), text)
        // Механизм («прошивка складывает сюда всё, что вышло за диапазон») не
        // подтверждён первичной документацией — утверждать его нельзя.
        assertTrue(!text.contains("переполнен"), text)
        assertTrue(!text.contains("прошивка"), text)
    }
}
