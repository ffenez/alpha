package app.alpha.analysis

import app.alpha.analysis.evidence.BackgroundCalibration
import app.alpha.analysis.evidence.CalibrationAccumulation
import app.alpha.analysis.evidence.SqrtResolution
import app.alpha.device.DeviceModel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Спектр с миллионами импульсов: при такой статистике видны дефекты, которые
 * малая статистика прячет за шумом.
 *
 * Файл держит два решения, каждое из которых появилось из-за конкретной
 * поломки:
 *
 *  1. Подгонка формы отбраковывала САМЫЙ СИЛЬНЫЙ пик. Континуум под окном в три
 *     полуширины выгнут, а прямая по боковым полосам проходит НАД ним: у
 *     выпуклой книзу подложки хорда лежит выше кривой. При миллионах импульсов
 *     это расхождение перестаёт быть мелочью. Отсюда подгонка континуума
 *     вместе с формой ([PeakShapeFit]).
 *  2. Поправка шкалы строилась по ОДНОЙ линии. Множитель, снятый с одной точки,
 *     экстраполирует куда угодно. Отсюда требование двух разнесённых линий
 *     ([ScaleCorrectionMath.MIN_REFERENCES]).
 *
 * Спектры построены ([SyntheticSpectra]): известны энергия каждой линии, её
 * площадь, доля площади в хвосте, форма континуума и его спад. Уход шкалы
 * задаётся ЯВНО — спектр собирается по растянутой калибровке, а разбирается по
 * номинальной, поэтому измеренное расхождение сравнивается с заложенным, а не
 * с числом, снятым однажды с одного прибора.
 */
class HighStatisticsSpectrumTest {

    private val calibration = SyntheticSpectra.CALIBRATION
    private val model = DeviceModel.UNKNOWN

    /**
     * Сильная линия 1460,8 кэВ и слабая 2614,5 кэВ на круто спадающем
     * континууме.
     *
     * Спад в 2000 раз выбран не для красоты: он даёт под сильной линией
     * ≈600 импульсов на канал (крутизна подложки в окне подгонки заметна) и
     * одновременно ≈28 на канал у верха шкалы, где иначе слабую линию нечем
     * было бы отделить от континуума.
     */
    private val counts: List<Int> by lazy {
        SyntheticSpectra.build(
            lines = listOf(
                SyntheticSpectra.Line(1460.8, counts = 1_400.0, tailFraction = 0.10),
                SyntheticSpectra.Line(2614.5, counts = 40.0, tailFraction = 0.10),
            ),
            continuum = 2_500.0,
            continuumSlope = 2_000.0,
            scale = 12.0,
            seed = 20260819,
        )
    }

    private fun peaks(): List<Peak> = PeakDetection.detect(
        counts = counts,
        calibration = calibration,
        resolution662 = model.peakResolution662,
        minEnergyKeV = model.peakFloorKeV,
    ).sortedBy { it.energyKeV }

    @Test
    fun `сильный пик получает форму, слабый остаётся на центре тяжести`() {
        val found = peaks()
        val strong = found.filter { it.netCounts >= PeakShapeFit.MIN_FIT_COUNTS }
        val weak = found.filter { it.netCounts < PeakShapeFit.MIN_FIT_COUNTS }
        assertTrue(strong.isNotEmpty(), "сильных пиков нет: ${found.map { it.netCounts }}")
        assertTrue(weak.isNotEmpty(), "слабых пиков нет: ${found.map { it.netCounts }}")
        for (peak in strong) {
            val where = "%.0f кэВ".format(peak.energyKeV)
            assertNotNull(peak.shape, "сильной линии $where форма не приписана")
        }
        for (peak in weak) {
            assertNull(peak.shape, "слабой линии %.0f кэВ приписана форма".format(peak.energyKeV))
            // У линии из сотен импульсов на 37 каналов высота центра сравнима
            // со своим шумом: ширина не измерена, а не «узкая».
            assertNull(peak.fwhmKeV, "слабой линии приписана измеренная ширина")
        }
    }

    @Test
    fun `самый сильный пик описывается формой, а не отбраковывается по континууму`() {
        // Без этой строки весь файл проверял бы малую статистику: дефект
        // проявляется там, где импульсов миллионы.
        assertTrue(counts.sum() > 4_000_000, "статистика ${counts.sum()} импульсов")

        val peak = peaks().maxBy { it.significance }
        assertEquals(1460.8f, peak.energyKeV, 15f, "сильнейший пик — не заложенная линия")
        val fit = assertNotNull(peak.shape, "форма не подогнана: net = ${peak.netCounts}")
        assertTrue(fit.reducedC < PeakShapeFit.MAX_REDUCED_C, "C/ndf = ${fit.reducedC}")

        // Континуум ПОДГОНЯЕТСЯ, а не берётся у боковых полос. Подложка спадает
        // экспоненциально, значит выпукла книзу, значит хорда по полосам идёт
        // над ней; подгонка обязана опустить уровень под центром. На этом
        // спектре — примерно на 10 % (664 → 597 импульсов на канал).
        val d = SpectrumValidationReport.diagnostics(
            counts, calibration, peak, model.peakResolution662,
        )
        assertTrue(
            fit.continuumAtCenter < d.continuum,
            "подогнанный континуум ${fit.continuumAtCenter} не ниже оценки полос ${d.continuum}",
        )
        assertTrue(
            fit.continuumAtCenter > 0.8 * d.continuum,
            "подгонка переложила пик в подложку: ${fit.continuumAtCenter} против ${d.continuum}",
        )
    }

    /**
     * Растянутая шкала: спектр собран по калибровке в [GAIN] раз выше
     * номинальной, поэтому линия 1460,8 кэВ встаёт в канал, который номинальная
     * шкала называет 1460,8 / [GAIN] = 1432,2 кэВ.
     */
    private val driftedCounts: List<Int> by lazy {
        SyntheticSpectra.build(
            lines = listOf(SyntheticSpectra.Line(1460.8, counts = 6_000.0, tailFraction = 0.10)),
            continuum = 900.0,
            scale = 12.0,
            seed = 777,
            calibration = EnergyCalibration(
                calibration.a0 * GAIN,
                calibration.a1 * GAIN,
                calibration.a2 * GAIN,
            ),
        )
    }

    private fun driftReport() = BackgroundCalibration.analyse(
        accumulations = listOf(
            CalibrationAccumulation(
                id = "long",
                counts = driftedCounts,
                calibration = calibration,
                seconds = 36_000L,
                intervalCount = 1,
                hoursCovered = 10,
                fromMillis = 0L,
                toMillis = 0L,
            ),
        ),
        startResolution = SqrtResolution(model.peakResolution662.toDouble()),
    )

    @Test
    fun `по одной линии K-40 поправка шкалы не предлагается`() {
        val report = driftReport()
        // В спектре заложена ровно одна линия из инвентаря опорных, поэтому
        // измеренной остаётся только она.
        assertEquals(
            listOf("K-40"),
            report.measurements.map { it.line.nuclide },
            "измерено линий: ${report.measurements.map { it.line.nuclide }}",
        )
        assertTrue(report.measurements.size < ScaleCorrectionMath.MIN_REFERENCES)
        val correction = ScaleCorrectionMath.of(
            report.measurements.map {
                ScaleCorrection.Reference(it.line.energyKeV, it.observedKeV, it.line.nuclide)
            },
        )
        assertNull(correction, "поправка предложена по ${report.measurements.size} линии")
    }

    @Test
    fun `уход шкалы движок измеряет, а не исправляет молча`() {
        val potassium = assertNotNull(
            driftReport().measurements.firstOrNull { it.line.nuclide == "K-40" },
            "K-40 не измерен",
        )
        val expected = 1460.8 / GAIN
        // Остаток сверх заложенного ухода — смещение центра тяжести в хвост:
        // при доле хвоста 0,10 оно составляет около 0,1 σ, то есть ≈3,5 кэВ при
        // σ = 35 кэВ на этой энергии. Допуск 6 кэВ покрывает его с запасом на
        // пуассоновский разброс центроиды.
        assertEquals(expected, potassium.observedKeV, 6.0, "измерено ${potassium.observedKeV}")
        // Расхождение с таблицей остаётся расхождением: движок обязан его
        // ПОКАЗАТЬ, а не подвинуть шкалу.
        assertTrue(
            abs(potassium.deltaKeV - (expected - 1460.8)) < 6.0,
            "Δ = ${potassium.deltaKeV} при заложенном ${expected - 1460.8}",
        )
        assertTrue(potassium.significance > 20.0, "значимость ${potassium.significance}")
    }

    @Test
    fun `континуум SNIP не превышает самих отсчётов и оставляет линии`() {
        val continuum = SnipContinuum.of(counts, calibration, model.peakResolution662)
        assertTrue(continuum.isNotEmpty())
        for (i in counts.indices) {
            assertTrue(
                continuum[i] <= counts[i].toFloat() + 0.001f,
                "канал $i: континуум ${continuum[i]} выше отсчёта ${counts[i]}",
            )
        }
        // Под самым сильным пиком подложка заметно ниже отсчёта: линия из неё
        // не съедена.
        val peak = peaks().maxBy { it.significance }
        val channel = peak.channel
        assertTrue(
            continuum[channel] < counts[channel] * 0.95f,
            "континуум ${continuum[channel]} при отсчёте ${counts[channel]}",
        )
    }

    private companion object {
        /** Во сколько раз растянута шкала сборки против номинальной. */
        const val GAIN = 1.02f
    }
}
