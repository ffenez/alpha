package app.alpha.analysis

import app.alpha.device.DeviceModel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Подпись пика и самый высокий канал — РАЗНЫЕ числа, и приложение обязано
 * показывать оба.
 *
 * Полевое замечание, с которого началась проверка: «в 610 больше всего
 * импульсов, а приложение выбирает 602». Это не ошибка расчёта: подпись —
 * центр тяжести чистой площади, а у линии с низкоэнергетическим хвостом он
 * смещён в хвост. Молчать об этом нельзя, поэтому пик несёт и максимум.
 */
class PeakCentreTest {

    private val counts: List<Int> by lazy {
        javaClass.classLoader!!
            .getResourceAsStream("spectra/alpha-20260819-143354.csv")!!
            .bufferedReader().use { reader ->
                reader.readLines().drop(1).filter { it.isNotBlank() }
                    .map { it.split(",")[2].trim().toInt() }
            }
    }

    private val calibration = EnergyCalibration(6.8822712f, 2.3377426f, 3.8714259e-4f)
    private val model = DeviceModel.UNKNOWN

    private fun peaks() = PeakDetection.detect(
        counts = counts,
        calibration = calibration,
        resolution662 = model.peakResolution662,
        minEnergyKeV = model.peakFloorKeV,
    )

    @Test
    fun `у каждого пика есть энергия самого высокого канала`() {
        val found = peaks()
        assertTrue(found.isNotEmpty(), "на реальном спектре не найдено ни одного пика")
        for (peak in found) {
            assertTrue(peak.maxEnergyKeV > 0f, "${peak.energyKeV}: максимум не заполнен")
        }
    }

    @Test
    fun `максимум лежит внутри окна пика, а не где угодно`() {
        // Иначе «самый высокий канал» описывал бы соседнюю структуру, и
        // объяснение вводило бы в заблуждение сильнее, чем молчание.
        for (peak in peaks()) {
            val fwhm = PeakDetection.fwhmKeV(peak.energyKeV, model.peakResolution662)
            assertTrue(
                abs(peak.maxEnergyKeV - calibration.energyAt(peak.channel.toFloat())) <= fwhm,
                "пик ${peak.energyKeV}: максимум ${peak.maxEnergyKeV} вне своего окна",
            )
        }
    }

    @Test
    fun `подпись и максимум расходятся — на реальном фоне это видно`() {
        // Ради этого и заведено второе число: на настоящем спектре расхождение
        // есть, и человек его замечает раньше, чем разработчик.
        val differing = peaks().count { abs(it.energyKeV - it.maxEnergyKeV) >= 1f }
        assertTrue(differing > 0, "расхождения нет ни у одного пика — объяснять нечего")
    }
}
