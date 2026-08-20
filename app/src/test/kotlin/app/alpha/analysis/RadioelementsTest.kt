package app.alpha.analysis

import app.alpha.device.DeviceModel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Радиоэлементный разбор на РЕАЛЬНОМ спектре прибора (10 ч, 4,16 млн
 * импульсов) — на синтетике такую задачу проверять бессмысленно: весь вопрос
 * в том, как ведёт себя континуум под слабыми линиями настоящего фона.
 */
class RadioelementsTest {

    private val counts: List<Int> by lazy {
        javaClass.classLoader
            .getResourceAsStream("spectra/alpha-20260819-143354.csv")
            ?.bufferedReader()?.use { reader ->
                reader.readLines().drop(1).filter { it.isNotBlank() }
                    .map { it.split(",")[2].trim().toInt() }
            }
            ?: error("fixture не найден")
    }

    private val calibration = EnergyCalibration(6.8822712f, 2.3377426f, 3.8714259e-4f)
    private val seconds = 36_059L
    private val resolution = DeviceModel.UNKNOWN.peakResolution662

    private fun measures() = Radioelements.measure(counts, calibration, seconds, resolution)

    private fun of(element: Radioelements.Element) =
        measures().firstOrNull { it.element == element }

    @Test
    fun `все три линии измеримы и площади положительны`() {
        // Оценка континуума по боковым полосам давала на этом же спектре
        // ОТРИЦАТЕЛЬНЫЕ площади урана и тория: полоса выше 2615 кэВ за краем
        // шкалы, а сам континуум падает круто. Ради этого движок и переведён
        // на SNIP.
        val all = measures()
        assertEquals(3, all.size, "линии: ${all.map { it.element }}")
        for (measure in all) {
            assertTrue(
                measure.netCounts > 0f,
                "${measure.element}: площадь ${measure.netCounts}",
            )
            assertTrue(
                measure.detected,
                "${measure.element}: ${measure.netCounts} ниже предела ${measure.criticalCounts}",
            )
        }
    }

    @Test
    fun `окно строится от разрешения прибора, а не из таблицы`() {
        val k = assertNotNull(of(Radioelements.Element.K))
        val half = Radioelements.WINDOW_HALF_FWHM *
            PeakDetection.fwhmKeV(Radioelements.K40_KEV, resolution)
        assertEquals(Radioelements.K40_KEV - half, k.fromKeV, 0.1f)
        assertEquals(Radioelements.K40_KEV + half, k.toKeV, 0.1f)

        // У прибора с ЛУЧШИМ разрешением окно уже: иначе оно захватывало бы
        // лишний континуум и теряло статистику.
        val sharper = Radioelements.measureLine(
            element = Radioelements.Element.K,
            energyKeV = Radioelements.K40_KEV,
            counts = counts,
            continuum = SnipContinuum.of(counts, calibration, resolution),
            calibration = calibration,
            seconds = seconds,
            resolution662 = resolution / 2f,
        )
        assertNotNull(sharper)
        assertTrue(
            (sharper.toKeV - sharper.fromKeV) < (k.toKeV - k.fromKeV),
            "окно не сузилось: ${sharper.toKeV - sharper.fromKeV}",
        )
    }

    @Test
    fun `калий этого фона набирается быстрее урана и тория`() {
        // Порядок скоростей — свойство природного фона, а не реализации: калий
        // даёт больше всего, торий меньше урана в этой записи. Числа названы,
        // чтобы падение теста означало изменение движка, а не «что-то не так».
        val k = assertNotNull(of(Radioelements.Element.K)).cps
        val u = assertNotNull(of(Radioelements.Element.U)).cps
        val th = assertNotNull(of(Radioelements.Element.TH)).cps
        assertTrue(k > u && u > th, "K $k · eU $u · eTh $th")
        assertTrue(k in 0.2f..0.6f, "калий $k c⁻¹")
        assertTrue(u in 0.02f..0.15f, "уран $u c⁻¹")
        assertTrue(th in 0.01f..0.10f, "торий $th c⁻¹")
    }

    @Test
    fun `получасовая станция даёт заявленную точность`() {
        // То, ради чего съёмка вообще возможна этим прибором: за 30 минут
        // калий набирается процентов на пять, торий — на десяток-полтора.
        val station = 1_800L
        for (measure in measures()) {
            val counts = measure.cps * station
            val relative = 1f / kotlin.math.sqrt(counts)
            assertTrue(
                relative < 0.25f,
                "${measure.element}: за 30 мин ${counts.toInt()} имп, ошибка ${relative * 100} %",
            )
        }
    }

    @Test
    fun `отношения считаются с переносом ошибки`() {
        val u = assertNotNull(of(Radioelements.Element.U))
        val th = assertNotNull(of(Radioelements.Element.TH))
        val ratio = assertNotNull(Radioelements.ratio(u, th))
        assertEquals(u.netCounts / th.netCounts, ratio.value, 1e-3f)
        // Относительная ошибка отношения не меньше каждой из своих.
        val relative = ratio.sigma / ratio.value
        assertTrue(relative >= u.sigmaCounts / u.netCounts, "перенос ошибки потерян: $relative")
        assertTrue(relative >= th.sigmaCounts / th.netCounts, "перенос ошибки потерян: $relative")
    }

    @Test
    fun `отношение к неотличимой от нуля линии не выдаётся`() {
        val u = assertNotNull(of(Radioelements.Element.U))
        val empty = u.copy(netCounts = 0f, criticalCounts = 100f)
        assertNull(Radioelements.ratio(u, empty), "деление на шум выдано за число")
    }

    @Test
    fun `стриппинг вычитает торий из урана и растит его неопределённость`() {
        val all = measures()
        val stripping = Radioelements.Stripping(
            thoriumIntoUranium = 0.4f,
            thoriumIntoPotassium = 0.7f,
            uraniumIntoPotassium = 0.8f,
        )
        val stripped = Radioelements.strip(all, stripping)
        val th = all.first { it.element == Radioelements.Element.TH }
        val u = all.first { it.element == Radioelements.Element.U }
        val strippedU = stripped.first { it.element == Radioelements.Element.U }

        assertEquals(u.netCounts - 0.4f * th.netCounts, strippedU.netCounts, 1f)
        assertTrue(strippedU.sigmaCounts > u.sigmaCounts, "σ не выросла при вычитании")
        // Торий не трогают: его окно самое верхнее, сыпаться в него нечему.
        assertEquals(
            th.netCounts,
            stripped.first { it.element == Radioelements.Element.TH }.netCounts,
        )
    }

    @Test
    fun `без коэффициентов стриппинг ничего не меняет`() {
        val all = measures()
        assertEquals(all, Radioelements.strip(all, Radioelements.Stripping.NONE))
    }

    @Test
    fun `предел Карри не зависит от того, есть линия или нет`() {
        // L_C описывает КОНТИНУУМ: он отвечает на вопрос «какую площадь можно
        // было бы заметить», и присутствие линии его менять не должно.
        val k = assertNotNull(of(Radioelements.Element.K))
        assertTrue(k.criticalCounts > 0f)
        assertTrue(
            abs(k.criticalCounts - Radioelements.SIGMAS * kotlin.math.sqrt(2f)
                * kotlin.math.sqrt((k.netCounts - k.netCounts) + baseOf(k))) < 1f,
            "предел ${k.criticalCounts} не совпал с σ₀ по подложке",
        )
    }

    /** Подложка в окне линии — из тех же чисел, что и в движке. */
    private fun baseOf(measure: Radioelements.Measure): Float {
        val continuum = SnipContinuum.of(counts, calibration, resolution)
        val lo = calibration.channelAt(measure.fromKeV).toInt()
        val hi = kotlin.math.ceil(calibration.channelAt(measure.toKeV)).toInt()
        var base = 0f
        for (ch in lo..hi) base += continuum[ch]
        return base
    }
}
