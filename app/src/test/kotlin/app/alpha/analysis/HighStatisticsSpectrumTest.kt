package app.alpha.analysis

import app.alpha.analysis.evidence.BackgroundCalibration
import app.alpha.analysis.evidence.CalibrationAccumulation
import app.alpha.analysis.evidence.SqrtResolution
import app.alpha.device.DeviceModel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Спектр природного фона с 4,2 млн импульсов — вдвое больше каналов заполнено,
 * чем у `natural-background-1024ch`, и все дефекты, которые прячет малая
 * статистика, здесь видны.
 *
 * Этот файл поймал две вещи, которых не поймали синтетика и слабый спектр:
 *
 *  1. Подгонка формы отбраковывала САМЫЙ СИЛЬНЫЙ пик. Континуум под окном в три
 *     полуширины выгнут, а модель считала его прямой; при миллионах импульсов
 *     это расхождение даёт C/ndf ≈ 3,7 при идеальной в остальном форме. Отсюда
 *     подгонка континуума вместе с формой.
 *  2. Поправка шкалы строилась по ОДНОЙ линии. На этом приборе уверенно
 *     меряется только K-40, и множитель из неё предсказывал на 2614,5 кэВ
 *     поправку +52 кэВ при настоящем расхождении −31. Отсюда требование двух
 *     разнесённых линий.
 *
 * Файл обезличен по построению: в нём только номер канала, энергия и счёт.
 */
class HighStatisticsSpectrumTest {

    private val counts: List<Int> by lazy {
        javaClass.classLoader
            .getResourceAsStream("spectra/alpha-20260819-143354.csv")
            ?.bufferedReader()?.use { reader ->
                reader.readLines().drop(1).filter { it.isNotBlank() }
                    .map { it.split(",")[2].trim().toInt() }
            }
            ?: error("fixture не найден")
    }

    /** Калибровка восстановлена из колонки энергий файла (остаток < 0,01 кэВ). */
    private val calibration = EnergyCalibration(6.8822712f, 2.3377426f, 3.8714259e-4f)

    private val model = DeviceModel.UNKNOWN

    private fun peaks(): List<Peak> = PeakDetection.detect(
        counts = counts,
        calibration = calibration,
        resolution662 = model.peakResolution662,
        minEnergyKeV = model.peakFloorKeV,
    ).sortedBy { it.energyKeV }

    @Test
    fun `файл читается целиком и край шкалы не потерян`() {
        assertTrue(counts.size == 1024, "каналов ${counts.size}")
        assertTrue(counts.sum().toLong() == 4_164_395L, "импульсов ${counts.sum()}")
        // В крайнем канале лежит всё, что вышло за верх шкалы, и в анализ он
        // не входит ([SpectrumEdge]).
        assertTrue(counts.last() > 5_000, "край шкалы пуст: ${counts.last()}")
    }

    @Test
    fun `сильный пик получает форму, слабый остаётся на центре тяжести`() {
        val found = peaks()
        val strong = found.filter { it.netCounts >= PeakShapeFit.MIN_FIT_COUNTS }
        val weak = found.filter { it.netCounts < PeakShapeFit.MIN_FIT_COUNTS }
        assertTrue(strong.isNotEmpty(), "сильных пиков нет: ${found.map { it.netCounts }}")
        for (peak in weak) {
            assertNull(peak.shape, "слабой линии %.0f кэВ приписана форма".format(peak.energyKeV))
        }
    }

    @Test
    fun `самый сильный пик описывается формой, а не отбраковывается по континууму`() {
        // Пик 1426 кэВ (область K-40, 5 259 нетто-импульсов) — ровно тот, что
        // отбраковывался, пока континуум считался прямой.
        val peak = peaks().first { abs(it.energyKeV - 1426f) < 20f }
        val fit = peak.shape
        assertNotNull(fit, "форма не подогнана: net = ${peak.netCounts}")
        assertTrue(fit.reducedC < PeakShapeFit.MAX_REDUCED_C, "C/ndf = ${fit.reducedC}")
        // Хвост со стороны низких энергий — то, чего симметричная модель не
        // видит: точка сшивки слева ближе к ядру, чем справа.
        assertTrue(fit.shape.asymmetry > 1.0, "асимметрия ${fit.shape.asymmetry}")
    }

    @Test
    fun `по одной линии K-40 поправка шкалы не предлагается`() {
        val report = BackgroundCalibration.analyse(
            accumulations = listOf(
                CalibrationAccumulation(
                    id = "long",
                    counts = counts,
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
        // Остальные опорные линии этого фона слабее порога надёжности, и
        // единственной измеренной остаётся K-40.
        assertTrue(
            report.measurements.size < ScaleCorrectionMath.MIN_REFERENCES,
            "измерено линий: ${report.measurements.map { it.line.nuclide }}",
        )
        val correction = ScaleCorrectionMath.of(
            report.measurements.map {
                ScaleCorrection.Reference(it.line.energyKeV, it.observedKeV, it.line.nuclide)
            },
        )
        assertNull(correction, "поправка предложена по ${report.measurements.size} линии")
    }

    @Test
    fun `шкала этого прибора уходит вниз на высоких энергиях`() {
        // Факт об измерении, а не о коде: K-40 стоит примерно на 1432 вместо
        // 1460,8. Тест держит его, чтобы будущая правка анализа не «исправила»
        // расхождение молча — оно принадлежит прибору, а не алгоритму.
        val report = BackgroundCalibration.analyse(
            accumulations = listOf(
                CalibrationAccumulation(
                    id = "long",
                    counts = counts,
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
        val potassium = report.measurements.firstOrNull { it.line.nuclide == "K-40" }
        assertNotNull(potassium, "K-40 не измерен")
        assertTrue(potassium.deltaKeV < -20.0, "Δ = ${potassium.deltaKeV}")
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
}
