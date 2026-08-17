package app.alpha.ui.logic

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindow
import app.alpha.analysis.SpectrumDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Кадр спектра — одна сборка на вкладку и на полный экран. Тесты держат
 * ровно то, что расходится первым, когда картинок становится две: границы
 * окна, верх оси при смене масштаба и нормировка фона по времени.
 */
class SpectrumFrameTest {

    private val calibration = EnergyCalibration(a0 = 0f, a1 = 3f, a2 = 0f)
    private val counts = List(1024) { channel -> if (channel in 300..310) 400 else 20 }

    private fun frame(
        window: EnergyWindow? = null,
        scale: SpectrumScale = SpectrumScale.Log,
        background: List<Int>? = null,
        backgroundSeconds: Long = 0L,
        subtract: Boolean = false,
        smoothing: Boolean = false,
    ) = SpectrumFrames.build(
        counts = counts,
        durationSeconds = 600,
        calibration = calibration,
        background = background,
        backgroundSeconds = backgroundSeconds,
        window = window,
        subtract = subtract,
        smoothing = smoothing,
        scale = scale,
    )

    @Test
    fun `no window means the whole scale`() {
        val f = frame()
        assertEquals(f.full, f.visible)
        assertTrue(f.wholeRange)
        assertEquals(SpectrumFrames.COLUMN_COUNT, f.columns.size)
    }

    @Test
    fun `a window is clamped into the scale and never widens past it`() {
        val f = frame(window = EnergyWindow(-500f, 99_000f))
        assertEquals(f.full.startKeV, f.visible.startKeV, 1e-3f)
        assertEquals(f.full.endKeV, f.visible.endKeV, 1e-3f)
        // Окно уже шкалы остаётся своим, но не уходит за её край.
        val zoomed = frame(window = EnergyWindow(600f, 1200f))
        assertEquals(600f, zoomed.visible.startKeV, 1e-3f)
        assertEquals(1200f, zoomed.visible.endKeV, 1e-3f)
        assertTrue(!zoomed.wholeRange)
        assertTrue(zoomed.channels.first >= 199 && zoomed.channels.last <= 401)
    }

    @Test
    fun `the top of the axis belongs to the scale, not to the data`() {
        val log = frame(scale = SpectrumScale.Log)
        assertEquals(SpectrumDisplay.logTop(400f), log.yTop)
        // Смена масштаба меняет ТОЛЬКО верх оси: окно и колонки те же числа.
        val linear = frame(scale = SpectrumScale.Linear)
        assertEquals(log.columns, linear.columns)
        assertEquals(log.visible, linear.visible)
        assertEquals(400f * 1.15f, linear.yTop, 1e-3f)
        // Пустой спектр не даёт нулевой оси — иначе поле схлопывается.
        val empty = SpectrumFrames.build(
            counts = List(1024) { 0 },
            durationSeconds = 10,
            calibration = calibration,
            scale = SpectrumScale.Linear,
        )
        assertTrue(empty.yTop >= 10f)
    }

    @Test
    fun `background is an overlay or a subtraction, never both`() {
        val background = List(1024) { 10 }
        val overlaid = frame(background = background, backgroundSeconds = 300)
        assertNotNull(overlaid.overlay)
        // Фон приведён ко времени накопления: 600 с против 300 с — вдвое.
        assertEquals(20f, overlaid.overlay!!.max(), 1e-3f)

        val subtracted = frame(
            background = background,
            backgroundSeconds = 300,
            subtract = true,
        )
        assertNull(subtracted.overlay)
        assertEquals(380f, subtracted.columns.max(), 1e-3f)
    }

    @Test
    fun `the edge of the scale is a state, not permanent statistics`() {
        // У RC-110 в крайнем канале почти всегда что-то есть: пока это доли
        // процента, строка под графиком не появляется — число живёт в
        // технических данных справки.
        assertTrue(!SpectrumFrames.edgeNoticeVisible(edgeCounts = 40, totalCounts = 100_000))
        assertTrue(SpectrumFrames.edgeNoticeVisible(edgeCounts = 1_200, totalCounts = 100_000))
        assertTrue(!SpectrumFrames.edgeNoticeVisible(edgeCounts = 0, totalCounts = 100_000))
        // Ровно на пороге — уже состояние: порог инженерный и не должен
        // зависеть от того, с какой стороны к нему подошли.
        assertTrue(SpectrumFrames.edgeNoticeVisible(edgeCounts = 1_000, totalCounts = 100_000))
        // Импульсы есть, а суммы нет (снимок ещё читается) — молчать нельзя.
        assertTrue(SpectrumFrames.edgeNoticeVisible(edgeCounts = 5, totalCounts = 0))
    }

    @Test
    fun `smoothing changes the picture, never the raw counts`() {
        // Одноканальный выброс — то, что сглаживание обязано размазать; на
        // широком пике среднее по пяти каналам ничего не меняет.
        val spike = List(1024) { channel -> if (channel == 305) 400 else 20 }
        fun build(smoothing: Boolean) = SpectrumFrames.build(
            counts = spike,
            durationSeconds = 600,
            calibration = calibration,
            smoothing = smoothing,
            scale = SpectrumScale.Linear,
        )
        assertTrue(build(smoothing = true).columns.max() < build(smoothing = false).columns.max())
        assertEquals(400, spike[305], "сырые импульсы не трогаются")
    }
}
