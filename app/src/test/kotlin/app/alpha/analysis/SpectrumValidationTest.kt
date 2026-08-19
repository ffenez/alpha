package app.alpha.analysis

import app.alpha.data.export.RcXml
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
 * Научная валидация спектрального анализа на реальном спектре
 * (`SPECTRUM_VALIDATION.md`).
 *
 * Файл fixture обезличен: в нём остались каналы, калибровка и время
 * накопления — ничего, что опознаёт прибор. Цепочка проверяется целиком:
 * XML → сырые отсчёты → калибровка → пик → площадь/континуум →
 * неопределённость → значимость.
 *
 * Числа в проверках получены НЕЗАВИСИМЫМ расчётом (см.
 * `SPECTRUM_VALIDATION_REPORT.md`), а не снятием с этой же реализации: там,
 * где это возможно, тест пересчитывает величину своей арифметикой из сырых
 * отсчётов и сверяет с тем, что вернул анализ.
 */
class SpectrumValidationTest {

    private val fixture: String by lazy {
        val stream = javaClass.classLoader
            .getResourceAsStream("spectra/natural-background-1024ch.xml")
            ?: error("fixture не найден")
        stream.bufferedReader().use { it.readText() }
    }

    private fun spectrum() = RcXml.parse(fixture).data.spectrum

    private fun calibration(): EnergyCalibration =
        spectrum().let { EnergyCalibration(it.a0, it.a1, it.a2) }

    /** Профиль неопознанного прибора: снимок не хранит модель. */
    private val model = DeviceModel.UNKNOWN

    private fun peaks(counts: List<Int> = spectrum().counts): List<Peak> =
        PeakDetection.detect(
            counts = counts,
            calibration = calibration(),
            resolution662 = model.peakResolution662,
            minEnergyKeV = model.peakFloorKeV,
        ).sortedBy { it.energyKeV }

    @Test
    fun `the file gives exactly the fields the analysis needs`() {
        val s = spectrum()
        assertEquals(1024, s.counts.size)
        assertEquals(36_059L, s.measurementSeconds)
        assertEquals(877_160L, s.counts.sumOf { it.toLong() })
        assertEquals(6.8821607f, s.a0, 1e-6f)
        assertEquals(2.3377438f, s.a1, 1e-6f)
        assertEquals(3.8714128e-4f, s.a2, 1e-9f)
        // Фона в файле нет — и выдумывать его нельзя.
        assertNull(RcXml.parse(fixture).data.background)
    }

    @Test
    fun `channel to energy follows the polynomial of the file`() {
        val c = calibration()
        // E = a0 + a1·ch + a2·ch², индексация с нуля, значение в ЦЕНТРЕ канала.
        for (ch in listOf(0, 32, 33, 100, 557, 951, 1022)) {
            val manual = 6.8821607 + 2.3377438 * ch + 3.8714128e-4 * ch * ch
            assertEquals(manual, c.energyAt(ch.toFloat()).toDouble(), 0.01)
        }
        assertEquals(2803.55, c.energyAt(1023f).toDouble(), 0.01)
    }

    @Test
    fun `the analysis never touches the raw counts`() {
        val original = spectrum().counts
        val copy = original.toList()
        val found = peaks(original)
        PeakEvidenceBridge.analyse(found, original, calibration(), model.peakResolution662)
        assertEquals(copy, original, "анализ изменил сырые отсчёты")
    }

    @Test
    fun `both real peaks of this spectrum are found and stay reproducible`() {
        val found = peaks()
        // 84,5 кэВ — комплекс K-серии Pb/Bi; 1429 кэВ — область K-40;
        // 2583 кэВ — область 2614,5 кэВ Tl-208.
        assertEquals(3, found.size, "найдено: ${found.map { it.energyKeV }}")
        assertEquals(83.66f, found[0].energyKeV, 0.2f)
        // 1431,77, а не прежние 1430,44: у линии из 1032 нетто-импульсов центр
        // берётся из подогнанной формы ([PeakShapeFit]), а не из центра тяжести.
        // Центр тяжести смещён в низкоэнергетический хвост, и поправка идёт К
        // истинным 1460,8 кэВ. Остаток в 29 кэВ — уход шкалы самого прибора,
        // форма его не лечит.
        assertEquals(1431.77f, found[1].energyKeV, 0.5f)
        assertTrue(
            abs(found[1].energyKeV - 1460.8f) < abs(1430.44f - 1460.8f),
            "подгонка формы увела центр от 1460,8: ${found[1].energyKeV}",
        )
        // 2584,55 — центр тяжести: линия из 85 импульсов слабее
        // [PeakShapeFit.MIN_FIT_COUNTS], и форма ей не приписывается. Семь
        // свободных параметров на такой площади уводили центр ОТ истинных
        // 2614,5, а не к ним.
        assertEquals(2584.55f, found[2].energyKeV, 1.0f)
        assertNull(found[2].shape, "слабой линии приписана форма")
        assertNotNull(found[1].shape, "сильной линии форма не приписана")
        assertEquals(43.38f, found[0].significance, 0.3f)
        assertEquals(20.03f, found[1].significance, 0.3f)
        assertEquals(5.76f, found[2].significance, 0.3f)
        // У линии 2614,5 кэВ на этих отсчётах несколько импульсов на канал:
        // высота центра неизмерима, поэтому ширина НЕ измерена, а не «узкая».
        assertNull(found[2].fwhmKeV)
        assertNotNull(found[1].fwhmKeV)
    }

    @Test
    fun `significance recomputed by hand from the raw counts agrees`() {
        val counts = spectrum().counts
        val c = calibration()
        for (peak in peaks(counts)) {
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
        // Участок 1550–1750 кэВ (каналы 600–680) — чистый континуум.
        val counts = spectrum().counts
        val c = calibration()
        assertTrue(
            peaks(counts).none { c.channelAt(it.energyKeV) in 600f..680f },
            "пик найден там, где структуры нет: ${peaks(counts).map { it.energyKeV }}",
        )
    }

    @Test
    fun `the golden report is written for a fresh checkout`() {
        val report = SpectrumValidationReport.of(
            counts = spectrum().counts,
            calibration = calibration(),
            liveSeconds = spectrum().measurementSeconds,
            resolution662 = model.peakResolution662,
            minEnergyKeV = model.peakFloorKeV,
        )
        val file = File("build/reports/spectrum_validation.json")
        file.parentFile.mkdirs()
        file.writeText(report)
        assertTrue(report.contains("\"peaks\""), report.take(200))
        assertTrue(!report.contains("SerialNumber"), "в отчёте не должно быть идентификаторов")
    }

    @Test
    fun `the energy scale of this instrument reads low at the top of the range`() {
        // Внешняя сверка по известным линиям: K-40 1460,8 и Tl-208 2614,5.
        // Числа НЕ подгоняются — отклонение фиксируется как свойство прибора.
        val found = peaks()
        val k40 = found[1].energyKeV
        val tl208 = found[2].energyKeV
        assertTrue(k40 < 1460.8f && abs(k40 - 1460.8f) < 40f, "K-40: $k40")
        assertTrue(tl208 < 2614.5f && abs(tl208 - 2614.5f) < 50f, "Tl-208: $tl208")
    }
}
