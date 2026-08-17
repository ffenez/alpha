package app.alpha.ui.logic

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindow
import app.alpha.analysis.SpectrumDisplay
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Отметка линии из справки о нуклиде — УКАЗАТЕЛЬ, а не вывод: она обязана
 * стоять там же, где стоит колонка соответствующего канала, исчезать вместе с
 * кадром, в котором её поставили, и честно отказываться, когда линии на шкале
 * прибора нет.
 */
class SpectrumHighlightTest {

    private val calibration = EnergyCalibration(a0 = 0f, a1 = 3f, a2 = 0f)
    private val channelCount = 1024
    private val full = SpectrumDisplay.fullWindow(calibration, channelCount)
    private val columns = SpectrumFrames.COLUMN_COUNT

    private fun channelsOf(window: EnergyWindow) =
        SpectrumDisplay.channelRange(window, calibration, channelCount)

    private fun anchor(window: EnergyWindow, scaleId: String = SpectrumScale.Log.id) =
        SpectrumHighlight.anchor(
            spectrumKey = SpectrumHighlight.spectrumKey(calibration, channelCount),
            scaleId = scaleId,
            window = window,
        )

    private fun mark(
        energyKeV: Float,
        window: EnergyWindow,
        shownAtMillis: Long = 0L,
        outcome: SpectrumHighlight.Aim = SpectrumHighlight.Aim.VISIBLE,
    ) = SpectrumHighlight.Mark(energyKeV, anchor(window), shownAtMillis, outcome)

    // ------------------------------------------------------------- геометрия

    @Test
    fun `the mark stands on the column of its own channel`() {
        val window = EnergyWindow(0f, 3000f)
        val channels = channelsOf(window)
        // 662 кэВ при 3 кэВ/канал — канал 221 (та же калибровка, что у оси).
        val fraction = SpectrumHighlight.fraction(662f, calibration, channels, columns)
        assertNotNull(fraction)
        val expectedColumn = SpectrumDisplay.columnForChannel(221, channels, columns)
        assertEquals(expectedColumn, SpectrumPlot.columnAt(fraction, columns))
    }

    @Test
    fun `the fraction is the inverse of the column under the finger`() {
        // Одна геометрия с курсором: доля → колонка → доля возвращает себя.
        for (column in listOf(0, 1, 57, 120, columns - 1)) {
            val fraction = SpectrumPlot.columnFraction(column, columns)
            assertEquals(column, SpectrumPlot.columnAt(fraction, columns))
        }
        assertEquals(0f, SpectrumPlot.columnFraction(0, columns))
        assertEquals(1f, SpectrumPlot.columnFraction(columns - 1, columns))
    }

    @Test
    fun `an energy outside the shown channels has no place on the field`() {
        val window = EnergyWindow(1000f, 2000f)
        val channels = channelsOf(window)
        assertNull(SpectrumHighlight.fraction(300f, calibration, channels, columns))
        assertNull(SpectrumHighlight.fraction(2500f, calibration, channels, columns))
        assertNotNull(SpectrumHighlight.fraction(1500f, calibration, channels, columns))
    }

    // ----------------------------------------------------------- окно и зум

    @Test
    fun `a visible energy leaves the picture alone`() {
        val window = EnergyWindow(500f, 1500f)
        val aiming = SpectrumHighlight.aim(1000f, window, full)
        assertEquals(SpectrumHighlight.Aim.VISIBLE, aiming.outcome)
        assertEquals(window, aiming.window)
    }

    @Test
    fun `the window drives to the energy keeping its width`() {
        val window = EnergyWindow(500f, 1500f)
        val aiming = SpectrumHighlight.aim(2614f, window, full)
        assertEquals(SpectrumHighlight.Aim.MOVED, aiming.outcome)
        // Кратность зума выбрал человек — подсветка её не отбирает.
        assertTrue(abs(aiming.window.widthKeV - window.widthKeV) < 0.01f)
        assertTrue(2614f > aiming.window.startKeV && 2614f < aiming.window.endKeV)
        // Энергия оказалась в середине, а не у самой кромки.
        val fraction = (2614f - aiming.window.startKeV) / aiming.window.widthKeV
        assertTrue(fraction > 0.4f && fraction < 0.6f, "$fraction")
    }

    @Test
    fun `an energy in the margin of the window still moves it`() {
        val window = EnergyWindow(500f, 1500f)
        // 520 кэВ формально видно, но стоит в 2 % от края — там отметка
        // сливается с рамкой поля и подписями оси.
        val aiming = SpectrumHighlight.aim(520f, window, full)
        assertEquals(SpectrumHighlight.Aim.MOVED, aiming.outcome)
        assertTrue(aiming.window.startKeV < 520f - window.widthKeV * 0.4f)
    }

    @Test
    fun `at the end of the scale the window stops at the border`() {
        val window = SpectrumDisplay.clampInto(
            EnergyWindow(full.endKeV - 600f, full.endKeV),
            full,
        )
        val aiming = SpectrumHighlight.aim(full.endKeV - 10f, window, full)
        // Двигать некуда: окно уже прижато к краю шкалы, и энергия видна.
        assertEquals(SpectrumHighlight.Aim.VISIBLE, aiming.outcome)
        assertEquals(window, aiming.window)
    }

    @Test
    fun `a line beyond the instrument scale is refused, not approximated`() {
        val window = EnergyWindow(500f, 1500f)
        val aiming = SpectrumHighlight.aim(full.endKeV + 500f, window, full)
        assertEquals(SpectrumHighlight.Aim.OUT_OF_SCALE, aiming.outcome)
        // Картинка не двигается: показывать всё равно нечего.
        assertEquals(window, aiming.window)
        val channels = channelsOf(aiming.window)
        assertNull(
            SpectrumHighlight.fraction(full.endKeV + 500f, calibration, channels, columns),
        )
    }

    // ------------------------------------------------------- когда снимается

    @Test
    fun `the mark lives its lifetime and no longer`() {
        val window = EnergyWindow(500f, 1500f)
        val anchor = anchor(window)
        val mark = mark(1000f, window, shownAtMillis = 1_000L)
        assertTrue(SpectrumHighlight.alive(mark, anchor, 1_000L))
        assertTrue(
            SpectrumHighlight.alive(mark, anchor, 1_000L + SpectrumHighlight.LIFETIME_MILLIS - 1),
        )
        assertTrue(
            !SpectrumHighlight.alive(mark, anchor, 1_000L + SpectrumHighlight.LIFETIME_MILLIS),
        )
        assertEquals(
            SpectrumHighlight.LIFETIME_MILLIS,
            SpectrumHighlight.remainingMillis(mark, 1_000L),
        )
        assertEquals(0L, SpectrumHighlight.remainingMillis(mark, 1_000_000L))
        assertEquals(0L, SpectrumHighlight.remainingMillis(null, 0L))
    }

    @Test
    fun `a changed frame drops the mark`() {
        val window = EnergyWindow(500f, 1500f)
        val mark = mark(1000f, window)
        // Зум и сдвиг — другая картинка: указывать в ней старым числом нельзя.
        assertTrue(!SpectrumHighlight.alive(mark, anchor(EnergyWindow(600f, 1600f)), 0L))
        assertTrue(!SpectrumHighlight.alive(mark, anchor(EnergyWindow(500f, 1400f)), 0L))
        // Смена масштаба оси значений — тоже другая картинка.
        assertTrue(!SpectrumHighlight.alive(mark, anchor(window, SpectrumScale.Linear.id), 0L))
        // Другой спектр (другая калибровка или другое число каналов).
        val otherSpectrum = SpectrumHighlight.Anchor(
            spectrumKey = SpectrumHighlight.spectrumKey(
                EnergyCalibration(0f, 2.4f, 0f),
                channelCount,
            ),
            scaleId = SpectrumScale.Log.id,
            startKeV = window.startKeV,
            endKeV = window.endKeV,
        )
        assertTrue(!SpectrumHighlight.alive(mark, otherSpectrum, 0L))
        assertTrue(!SpectrumHighlight.alive(null, anchor(window), 0L))
    }

    @Test
    fun `live accumulation does not drop the mark`() {
        // Ключ кадра — калибровка и число каналов: счёт в каналах растёт каждые
        // несколько секунд, и от этого место линии по калибровке не меняется.
        val window = EnergyWindow(500f, 1500f)
        val mark = mark(1000f, window)
        assertTrue(SpectrumHighlight.alive(mark, anchor(window), 0L))
        // Дробные кэВ одного и того же окна не считаются сменой кадра.
        val jittered = EnergyWindow(window.startKeV + 0.2f, window.endKeV - 0.2f)
        assertTrue(SpectrumHighlight.alive(mark, anchor(jittered), 0L))
    }
}
