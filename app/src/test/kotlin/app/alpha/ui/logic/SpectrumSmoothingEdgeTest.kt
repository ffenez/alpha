package app.alpha.ui.logic

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.SpectrumDisplay
import app.alpha.analysis.SpectrumEdge
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Сглаживание не затягивает крайний канал в кривую.
 *
 * В крайнем канале лежит всё, что вышло за верхнюю границу шкалы, и его счёт
 * бывает на порядки больше соседних. Пока усреднение шло по всему массиву, при
 * включённом сглаживании последние нарисованные каналы поднимались, и кривая
 * на правом краю уходила вверх.
 */
class SpectrumSmoothingEdgeTest {

    /** Ровный континуум и огромный счёт в самом последнем канале. */
    private val counts = List(1024) { channel ->
        if (channel == 1023) 50_000 else 100
    }

    @Test
    fun `edge channel does not leak into the smoothed curve`() {
        val smoothed = SpectrumDisplay.movingAverage(
            counts.map { it.toFloat() },
            range = SpectrumEdge.analysable(counts.size),
        )
        val last = SpectrumEdge.lastAnalysableChannel(counts.size)
        for (channel in (last - SpectrumDisplay.SMOOTH_RADIUS)..last) {
            assertTrue(
                smoothed[channel] <= 100f,
                "канал $channel поднялся до ${smoothed[channel]}",
            )
        }
        // Сам крайний канал не тронут: он остаётся диагностикой, а не точкой.
        assertTrue(smoothed[1023] == 50_000f)
    }

    @Test
    fun `the smoothed frame keeps its right edge flat`() {
        val calibration = EnergyCalibration(0f, 3f, 0f)
        val frame = SpectrumFrames.build(
            counts = counts,
            durationSeconds = 600,
            calibration = calibration,
            smoothing = true,
        )
        val tail = frame.columns.takeLast(3).filter { !it.isNaN() }
        assertTrue(tail.isNotEmpty())
        assertTrue(tail.all { it <= 100f }, "хвост кривой поднялся: $tail")
    }
}
