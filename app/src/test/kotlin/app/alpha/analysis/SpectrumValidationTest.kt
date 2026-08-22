package app.alpha.analysis

import app.alpha.device.DeviceModel
import app.alpha.ui.logic.PeakEvidenceBridge
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Научная валидация цепочки спектрального анализа: сырые отсчёты → калибровка →
 * пик → площадь/континуум → неопределённость → значимость.
 *
 * Спектр ПОСТРОЕН ([SyntheticSpectra]), и это меняет характер проверки к
 * лучшему: раньше числа сверялись с независимым расчётом по тем же отсчётам,
 * теперь — с истиной, заложенной в спектр (энергия линии, её площадь, доля
 * площади в хвосте, форма континуума). Там, где независимый пересчёт по сырым
 * отсчётам возможен, он остался: тест считает величину своей арифметикой и
 * сверяет с тем, что вернул анализ.
 *
 * Граница метода: спектр содержит ровно те особенности, которые в него
 * заложены. Разбор реального файла прибора (XML, калибровка, время накопления)
 * проверяется отдельно — в `RcXmlTest`.
 */
class SpectrumValidationTest {

    /**
     * Три линии разной силы на спадающем континууме: сильная 238,6 кэВ,
     * средняя 1460,8 и слабая 2614,5 у верха шкалы.
     */
    private val lines = listOf(
        SyntheticSpectra.Line(238.6, counts = 6_000.0, tailFraction = 0.10),
        SyntheticSpectra.Line(1460.8, counts = 2_600.0, tailFraction = 0.10),
        SyntheticSpectra.Line(2614.5, counts = 350.0, tailFraction = 0.10),
    )

    private val counts: List<Int> by lazy {
        SyntheticSpectra.build(
            lines = lines,
            continuum = 900.0,
            continuumSlope = 120.0,
            scale = 1.0,
            seed = 20260102,
        )
    }

    private val liveSeconds = 36_059L
    private val calibration = SyntheticSpectra.CALIBRATION

    /** Профиль неопознанного прибора: у построенного спектра модели нет. */
    private val model = DeviceModel.UNKNOWN

    private fun peaks(source: List<Int> = counts): List<Peak> =
        PeakDetection.detect(
            counts = source,
            calibration = calibration,
            resolution662 = model.peakResolution662,
            minEnergyKeV = model.peakFloorKeV,
        ).sortedBy { it.energyKeV }

    @Test
    fun `channel to energy follows the polynomial of the calibration`() {
        val c = calibration
        // E = a0 + a1·ch + a2·ch², индексация с нуля, значение в ЦЕНТРЕ канала.
        for (ch in listOf(0, 32, 33, 100, 557, 951, 1022)) {
            val manual = c.a0.toDouble() + c.a1.toDouble() * ch + c.a2.toDouble() * ch * ch
            assertEquals(manual, c.energyAt(ch.toFloat()).toDouble(), 0.01)
        }
        // Верх шкалы: 1024 канала кончаются около 2,8 МэВ — это и есть тот
        // край, за которым у линии 2614,5 кэВ нет верхней боковой полосы.
        assertEquals(2803.46, c.energyAt(1023f).toDouble(), 0.05)
    }

    @Test
    fun `the analysis never touches the raw counts`() {
        val original = counts
        val copy = original.toList()
        val found = peaks(original)
        PeakEvidenceBridge.analyse(found, original, calibration, model.peakResolution662)
        assertEquals(copy, original, "анализ изменил сырые отсчёты")
    }

    @Test
    fun `найдены ровно заложенные линии и ни одной лишней`() {
        val found = peaks()
        assertEquals(lines.size, found.size, "найдено: ${found.map { it.energyKeV }}")

        for (i in lines.indices) {
            val line = lines[i]
            val peak = found[i]
            val fwhm = PeakDetection.fwhmKeV(line.energyKeV.toFloat(), model.peakResolution662)
            // Допуск — полуширина линии. Смещение подписи вниз ожидаемо: центр
            // тяжести уезжает в низкоэнергетический хвост, а у слабой линии к
            // этому добавляется пуассоновский разброс центроиды.
            assertTrue(
                abs(peak.energyKeV - line.energyKeV) < fwhm / 2f,
                "линия ${line.energyKeV}: подпись ${peak.energyKeV}",
            )
            // Нетто-площадь. В окно ±FWHM/2 попадает 0,76 гауссова ядра, ещё
            // 10–20 % съедает прямая по боковым полосам: она проходит НАД
            // выпуклым континуумом. Итого 0,55…0,80 заложенной площади.
            val share = peak.netCounts / line.counts
            assertTrue(
                share in 0.55f..0.80f,
                "линия ${line.energyKeV}: нетто ${peak.netCounts} из ${line.counts} (×$share)",
            )
        }

        val weak = found.last()
        assertTrue(
            weak.netCounts < PeakShapeFit.MIN_FIT_COUNTS,
            "линия 2614,5 перестала быть слабой: ${weak.netCounts}",
        )
        // Семь свободных параметров на такой площади уводят центр ОТ истины, а
        // не к ней, поэтому форма слабой линии не приписывается.
        assertNull(weak.shape, "слабой линии приписана форма")
        // У линии из сотен импульсов на несколько десятков каналов высота
        // центра неизмерима: ширина НЕ измерена, а не «узкая».
        assertNull(weak.fwhmKeV)
        for (peak in found.dropLast(1)) {
            assertNotNull(peak.shape, "сильной линии ${peak.energyKeV} форма не приписана")
            assertNotNull(peak.fwhmKeV, "сильной линии ${peak.energyKeV} ширина не измерена")
        }
    }

    @Test
    fun `significance recomputed by hand from the raw counts agrees`() {
        val c = calibration
        for (peak in peaks()) {
            val i = peak.channel
            val half = PeakDetection.halfWidthChannels(c, i, model.peakResolution662)
            val left = (i - 3 * half)..(i - half - 1)
            val right = (i + half + 1)..(i + 3 * half)
            val m = left.count() + right.count()
            val leftMean = left.sumOf { counts[it].toDouble() } / left.count()
            val rightMean = right.sumOf { counts[it].toDouble() } / right.count()
            val b = (leftMean + rightMean) / 2.0
            val gross = (i - half..i + half).sumOf { counts[it].toDouble() }
            val width = 2 * half + 1
            val net = gross - b * width
            // Var(net) = Var(gross) + width²·Var(B̂), Var(B̂) = B/m.
            val sigma = sqrt(gross + width.toDouble() * width * b / m)
            assertEquals(net, peak.netCounts.toDouble(), 1.0)
            assertEquals(net / sigma, peak.significance.toDouble(), 0.05)
        }
    }

    @Test
    fun `a region without a peak yields nothing`() {
        // Участок 600–1200 кэВ: линий туда не закладывали, там чистый континуум.
        val found = peaks()
        assertTrue(
            found.none { it.energyKeV in 600f..1200f },
            "пик найден там, где линии нет: ${found.map { it.energyKeV }}",
        )
    }

    @Test
    fun `the golden report is written for a fresh checkout`() {
        val report = SpectrumValidationReport.of(
            counts = counts,
            calibration = calibration,
            liveSeconds = liveSeconds,
            resolution662 = model.peakResolution662,
            minEnergyKeV = model.peakFloorKeV,
        )
        val file = File("build/reports/spectrum_validation.json")
        file.parentFile?.mkdirs()
        file.writeText(report)
        assertTrue(report.contains("\"peaks\""), report.take(200))
        assertTrue(!report.contains("SerialNumber"), "в отчёте не должно быть идентификаторов")
    }
}
