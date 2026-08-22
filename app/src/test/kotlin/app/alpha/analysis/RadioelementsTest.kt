package app.alpha.analysis

import app.alpha.device.DeviceModel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Радиоэлементный разбор: калий, уран и торий по трём линиям.
 *
 * Весь вопрос здесь в КОНТИНУУМЕ под слабыми линиями, поэтому спектр построен
 * ([SyntheticSpectra]) с тремя заданными свойствами:
 *
 *  - площади линий K-40, Bi-214 и Tl-208 известны точно — есть с чем сравнивать
 *    измеренные;
 *  - континуум спадает круто (в 120 раз по шкале), как комптоновский подпор
 *    настоящего фона;
 *  - линия Tl-208 стоит у самого верха шкалы, где ВЕРХНЕЙ боковой полосы не
 *    существует.
 *
 * Именно на этом движок и переведён с боковых полос на [SnipContinuum]:
 * проверка ниже пересчитывает оба варианта и показывает, что полосы дают
 * отрицательную площадь урана и никакой — тория.
 */
class RadioelementsTest {

    /** Заложенные линии: элемент → (энергия, кэВ; площадь, импульсы). */
    private val trueLines = mapOf(
        Radioelements.Element.K to (Radioelements.K40_KEV.toDouble() to 12_000.0),
        Radioelements.Element.U to (Radioelements.BI214_KEV.toDouble() to 3_000.0),
        Radioelements.Element.TH to (Radioelements.TL208_KEV.toDouble() to 2_000.0),
    )

    private val counts: List<Int> by lazy {
        SyntheticSpectra.build(
            lines = trueLines.values.map { (energyKeV, area) ->
                SyntheticSpectra.Line(energyKeV, counts = area, tailFraction = 0.10)
            },
            continuum = 900.0,
            continuumSlope = 120.0,
            scale = 1.0,
            seed = 31337,
        )
    }

    private val calibration = SyntheticSpectra.CALIBRATION
    private val seconds = 36_059L
    private val resolution = DeviceModel.UNKNOWN.peakResolution662

    private fun measures() = Radioelements.measure(counts, calibration, seconds, resolution)

    private fun of(element: Radioelements.Element) =
        measures().firstOrNull { it.element == element }

    /**
     * Площадь в окне по классической оценке «среднее двух боковых полос» —
     * тот вариант, от которого движок отказался. NaN, если полоса не помещается
     * в шкалу.
     */
    private fun sidebandNet(measure: Radioelements.Measure): Double {
        val lo = calibration.channelAt(measure.fromKeV).toInt()
        val hi = ceil(calibration.channelAt(measure.toKeV)).toInt()
        val width = hi - lo + 1
        val left = (lo - width) until lo
        val right = (hi + 1)..(hi + width)
        if (left.first < 0 || right.last >= counts.size) return Double.NaN
        val leftMean = left.sumOf { counts[it].toDouble() } / left.count()
        val rightMean = right.sumOf { counts[it].toDouble() } / right.count()
        return (lo..hi).sumOf { counts[it].toDouble() } - (leftMean + rightMean) / 2.0 * width
    }

    @Test
    fun `все три линии измеримы и площади положительны`() {
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
    fun `боковые полосы на этом континууме дали бы отрицательный уран и никакой торий`() {
        // Ради этого движок и переведён на SNIP. Уран: в нижнюю полосу окна
        // попадает соседняя структура, а континуум падает круто, поэтому хорда
        // по полосам проходит выше подложки и съедает всю линию. Торий: полоса
        // выше 2769 кэВ за краем шкалы, и оценки просто нет.
        val uranium = assertNotNull(of(Radioelements.Element.U))
        val thorium = assertNotNull(of(Radioelements.Element.TH))
        assertTrue(sidebandNet(uranium) < 0.0, "полосы дали урану ${sidebandNet(uranium)}")
        assertTrue(sidebandNet(thorium).isNaN(), "у тория нашлась верхняя полоса")
        assertTrue(uranium.netCounts > 0f && thorium.netCounts > 0f)
    }

    @Test
    fun `измеренная площадь восстанавливает заложенную с известным смещением`() {
        // В окно ±1,4 FWHM попадает 99 % площади линии, поэтому сравнивать
        // можно прямо с заложенным числом. Смещение вверх — свойство подложки
        // SNIP: она проходит НИЖЕ истинного континуума под линией, и её остаток
        // приписывается линии. На этом спектре превышение 17 % (K-40), 29 %
        // (Tl-208) и 59 % (Bi-214, у которого подложка между двумя линиями
        // проседает сильнее всего). Границы держат порядок смещения, чтобы оно
        // не выросло молча.
        for (measure in measures()) {
            val expected = trueLines.getValue(measure.element).second
            val ratio = measure.netCounts / expected
            assertTrue(
                ratio in 0.95..1.75,
                "${measure.element}: ${measure.netCounts} против заложенных $expected (×$ratio)",
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
        val hi = ceil(calibration.channelAt(measure.toKeV)).toInt()
        var base = 0f
        for (ch in lo..hi) base += continuum[ch]
        return base
    }
}
