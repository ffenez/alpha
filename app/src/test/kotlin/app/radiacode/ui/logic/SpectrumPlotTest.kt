package app.radiacode.ui.logic

import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.Peak
import app.radiacode.analysis.SpectrumDisplay
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Курсор спектра: попадание пальца в КАНАЛ и обратимость геометрии поля.
 *
 * Если эта математика ошибается, экран уверенно называет не тот канал и не ту
 * энергию — самый дорогой класс ошибки в приложении, где числа читают как
 * измерение.
 */
class SpectrumPlotTest {

    private val calibration = EnergyCalibration(a0 = -5.6f, a1 = 2.41f, a2 = 0f)

    @Test
    fun `column pixel and fraction are inverse`() {
        val left = 24f
        val width = 480f
        for (index in listOf(0, 1, 57, 119, 239)) {
            val x = SpectrumPlot.columnXPx(index, 240, left, width)
            val fraction = SpectrumPlot.plotFraction(x, left, width)
            assertEquals(index, SpectrumPlot.columnAt(fraction, 240))
        }
    }

    @Test
    fun `axis labels stand inside the field and shift the fraction`() {
        // Палец у левого края УЗЛА стоит на подписях оси, а не на данных:
        // доля обязана быть нулевой, а не отрицательной.
        assertEquals(0f, SpectrumPlot.plotFraction(0f, leftPx = 24f, widthPx = 480f))
        assertEquals(1f, SpectrumPlot.plotFraction(999f, leftPx = 24f, widthPx = 480f))
        // Середина ПОЛЯ, а не середина узла.
        assertEquals(0.5f, SpectrumPlot.plotFraction(264f, leftPx = 24f, widthPx = 480f), 1e-4f)
    }

    @Test
    fun `value to pixel follows the chosen scale`() {
        val top = 1000f
        val linear = SpectrumPlot.yPx(500f, top, SpectrumScale.Linear, topPx = 10f, heightPx = 100f)
        assertEquals(60f, linear, 1e-3f)
        // Логарифм: 10 из 1000 — треть высоты снизу.
        val log = SpectrumPlot.yPx(10f, top, SpectrumScale.Log, topPx = 0f, heightPx = 300f)
        assertEquals(200f, log, 1e-3f)
        // Верх шкалы — верх поля, ноль — низ, в любом масштабе.
        for (scale in listOf(SpectrumScale.Linear, SpectrumScale.Log, SpectrumScale.Power(2))) {
            assertEquals(0f, SpectrumPlot.yPx(top, top, scale, 0f, 100f), 1e-3f)
            assertEquals(100f, SpectrumPlot.yPx(0f, top, scale, 0f, 100f), 1e-3f)
        }
    }

    @Test
    fun `channels of a column invert the aggregation`() {
        val range = 100..600
        val columns = 240
        for (channel in range) {
            val column = SpectrumDisplay.columnForChannel(channel, range, columns)
            assertNotNull(column)
            val back = SpectrumPlot.channelsOfColumn(column, range, columns)
            assertTrue(channel in back, "канал $channel не вернулся из колонки $column")
        }
    }

    @Test
    fun `a column never comes back empty when there are more columns than channels`() {
        // Глубокий зум: каналов в окне меньше, чем колонок на экране.
        val range = 10..60
        for (column in 0 until 240) {
            val channels = SpectrumPlot.channelsOfColumn(column, range, 240)
            assertTrue(!channels.isEmpty(), "пустая колонка $column")
            assertTrue(channels.first >= range.first && channels.last <= range.last)
        }
    }

    @Test
    fun `readout names the channel that is actually drawn`() {
        // Колонка рисуется МАКСИМУМОМ своих каналов — курсор обязан называть
        // тот же канал, иначе число под пальцем не объясняет высоту столбца.
        val counts = IntArray(1024) { 5 }
        counts[313] = 900
        val range = 0..1023
        val column = SpectrumDisplay.columnForChannel(313, range, 240)!!
        val fraction = column.toFloat() / 239f
        val readout = SpectrumPlot.readout(
            fraction = fraction,
            range = range,
            columnCount = 240,
            counts = counts.toList(),
            calibration = calibration,
        )
        assertNotNull(readout)
        assertEquals(313, readout.channel)
        assertEquals(900, readout.counts)
        assertTrue(readout.merged, "в колонке больше одного канала — это надо сказать")
        assertTrue(abs(readout.energyKeV - calibration.energyAt(313f)) < 1e-3f)
    }

    @Test
    fun `readout carries the peak only where the peak is`() {
        val counts = List(1024) { if (it == 313) 900 else 5 }
        val peak = Peak(channel = 313, energyKeV = 748.7f, netCounts = 880f, significance = 8.2f)
        val range = 0..1023
        val hit = SpectrumPlot.readout(
            fraction = SpectrumDisplay.columnForChannel(313, range, 240)!! / 239f,
            range = range,
            columnCount = 240,
            counts = counts,
            calibration = calibration,
            peaks = listOf(peak),
        )
        assertEquals(peak, hit?.peak)
        val miss = SpectrumPlot.readout(
            fraction = 0f,
            range = range,
            columnCount = 240,
            counts = counts,
            calibration = calibration,
            peaks = listOf(peak),
        )
        assertNull(miss?.peak)
    }

    @Test
    fun `the field takes a share of the screen and stops at both edges`() {
        // Границы — токены `Dimens.spectrumFieldMin/Max`; тест держит правило:
        // доля экрана посередине, зажим по краям.
        val min = 200f
        val max = 320f
        // Телефон 900 dp: треть экрана, а не полоска в 170 dp.
        assertEquals(306f, SpectrumPlot.fieldHeightDp(900f, min, max), 0.5f)
        // Мелкий экран: доля дала бы 150 dp — поле не опускается ниже нижней
        // границы, иначе на нём не разглядеть форму пика.
        assertEquals(min, SpectrumPlot.fieldHeightDp(440f, min, max))
        // Планшет: поле не растягивается на всю страницу.
        assertEquals(max, SpectrumPlot.fieldHeightDp(1600f, min, max))
        // Перепутанные границы не дают пустого диапазона.
        assertEquals(max, SpectrumPlot.fieldHeightDp(1600f, max, min))
    }

    @Test
    fun `empty spectrum has nothing to say`() {
        assertNull(
            SpectrumPlot.readout(0.5f, 0..1023, 240, emptyList(), calibration),
        )
    }
}
