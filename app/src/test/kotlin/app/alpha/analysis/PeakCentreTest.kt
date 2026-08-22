package app.alpha.analysis

import app.alpha.device.DeviceModel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Подпись пика и самый высокий канал — РАЗНЫЕ числа, и приложение обязано
 * показывать оба.
 *
 * Полевое замечание, с которого началась проверка: «в 610 больше всего
 * импульсов, а приложение выбирает 602». Это не ошибка расчёта. Подпись —
 * центр тяжести чистой площади (при достаточной статистике уточнённый
 * подгонкой формы), максимум — ОДИН канал со своим пуассоновским шумом. У
 * линии с низкоэнергетическим хвостом центр тяжести смещён в хвост, и эти два
 * числа расходятся систематически. Молчать об этом нельзя, поэтому пик несёт и
 * максимум.
 *
 * Спектр построен ([SyntheticSpectra]), поэтому известна истина: энергия
 * линии, её площадь и доля площади в хвосте. Хвост измеряется РАЗНОСТЬЮ с
 * близнецом, у которого он выключен: у обеих сборок одно зерно, а все средние
 * выше 30 импульсов на канал, поэтому генератор берёт для каждого канала одно
 * и то же нормальное отклонение ([SyntheticSpectra.poisson]) и разница подписей
 * — систематика хвоста, а не другой шум.
 */
class PeakCentreTest {

    /** Энергии заложенных линий, кэВ. */
    private val lineEnergies = listOf(609.3, 1460.8)

    /**
     * Доля площади в хвосте.
     *
     * 0,10 — не «поменьше на всякий случай»: хвост генератора обрывается
     * ступенькой на самом центре линии, и при доле выше ≈0,28 его плотность в
     * центре превышает гауссово ядро. Тогда половина высоты оказывается выше
     * ядра, наблюдаемая ширина падает ниже [PeakDetection] MIN_WIDTH_RATIO, и
     * линия отбраковывается гейтом формы — проверять на такой линии нечего.
     */
    private val tailFraction = 0.10

    private fun spectrum(tail: Double): List<Int> = SyntheticSpectra.build(
        lines = lineEnergies.map {
            SyntheticSpectra.Line(it, counts = 2_400.0, tailFraction = tail)
        },
        continuum = 600.0,
        // Спад в 40 раз держит континуум выше 30 импульсов на канал по всей
        // шкале — условие одинакового шума у обеих сборок.
        continuumSlope = 40.0,
        scale = 3.0,
        seed = 424242,
    )

    private val calibration = SyntheticSpectra.CALIBRATION
    private val model = DeviceModel.UNKNOWN

    private fun peaks(counts: List<Int>): List<Peak> = PeakDetection.detect(
        counts = counts,
        calibration = calibration,
        resolution662 = model.peakResolution662,
        minEnergyKeV = model.peakFloorKeV,
    ).sortedBy { it.energyKeV }

    private val tailed: List<Int> by lazy { spectrum(tailFraction) }

    /** Ближайшая заложенная линия к найденному пику, кэВ. */
    private fun trueLineOf(peak: Peak): Double =
        lineEnergies.minBy { abs(it - peak.energyKeV) }

    @Test
    fun `максимум — это канал с наибольшим НЕТТО-счётом, а не что-то ещё`() {
        val found = peaks(tailed)
        assertEquals(lineEnergies.size, found.size, "найдено: ${found.map { it.energyKeV }}")
        for (peak in found) {
            val d = SpectrumValidationReport.diagnostics(
                tailed, calibration, peak, model.peakResolution662,
            )
            // Континуум под окном наклонный: максимум ищется над наклонной,
            // иначе на склоне побеждал бы более высокий край окна.
            val expected = (d.from..d.to).maxBy {
                tailed[it] - (d.continuum + d.slope * (it - peak.channel))
            }
            assertEquals(
                calibration.energyAt(expected.toFloat()),
                peak.maxEnergyKeV,
                0.01f,
                "пик ${peak.energyKeV}: максимум не в канале с наибольшим нетто",
            )
        }
    }

    @Test
    fun `максимум лежит внутри окна пика и указывает на свою линию`() {
        // Иначе «самый высокий канал» описывал бы соседнюю структуру, и
        // объяснение вводило бы в заблуждение сильнее, чем молчание.
        for (peak in peaks(tailed)) {
            val fwhm = PeakDetection.fwhmKeV(peak.energyKeV, model.peakResolution662)
            assertTrue(
                abs(peak.maxEnergyKeV - calibration.energyAt(peak.channel.toFloat())) <= fwhm,
                "пик ${peak.energyKeV}: максимум ${peak.maxEnergyKeV} вне своего окна",
            )
            // Вершина широкой линии плоская: на ±0,3 σ профиль падает на
            // проценты, и пуассоновский шум канала двигает максимум на
            // несколько каналов. Половина ширины — граница, за которой это уже
            // не разброс вершины, а другая структура.
            assertTrue(
                abs(peak.maxEnergyKeV - trueLineOf(peak)) <= fwhm / 2f,
                "максимум ${peak.maxEnergyKeV} дальше полуширины от линии ${trueLineOf(peak)}",
            )
        }
    }

    @Test
    fun `подпись и максимум расходятся — ради этого и заведено второе число`() {
        val differing = peaks(tailed).count { abs(it.energyKeV - it.maxEnergyKeV) >= 1f }
        assertTrue(differing > 0, "расхождения нет ни у одного пика — объяснять нечего")
    }

    @Test
    fun `хвост линии уводит подпись вниз, а канал максимума за ней не идёт`() {
        val withTail = peaks(tailed)
        val symmetric = peaks(spectrum(tail = 0.0))
        assertEquals(
            lineEnergies.size,
            symmetric.size,
            "симметричный близнец: ${symmetric.size} пиков",
        )

        for (energy in lineEnergies) {
            val a = withTail.first { abs(trueLineOf(it) - energy) < 0.1 }
            val b = symmetric.first { abs(trueLineOf(it) - energy) < 0.1 }
            val sigma = PeakDetection.fwhmKeV(energy.toFloat(), model.peakResolution662) / 2.3548f
            val shift = (a.energyKeV - b.energyKeV) / sigma
            // Заложенная систематика: доля 0,10 площади лежит в экспоненте с
            // масштабом σ влево, внутри окна ±1,18 σ её центр стоит на −0,48 σ,
            // и центр тяжести окна сдвигается на −0,044 σ. Подгонка формы это
            // смещение НЕ снимает: модель ExpGaussExp не описывает ступеньку
            // генератора и уводит центр дальше — измерено −0,075…−0,085 σ.
            // Границы держат сам факт и порядок смещения, а не его точное число.
            assertTrue(
                shift in -0.25f..-0.02f,
                "линия $energy: хвост сдвинул подпись на $shift σ " +
                    "(${a.energyKeV} против ${b.energyKeV})",
            )
        }
    }
}
