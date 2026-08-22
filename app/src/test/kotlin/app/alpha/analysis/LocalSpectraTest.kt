package app.alpha.analysis

import app.alpha.device.DeviceModel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Проверки на НАСТОЯЩИХ спектрах прибора.
 *
 * Запускаются только там, где эти спектры лежат ([LocalSpectra]): у владельца
 * прибора. У всех остальных каталог пуст, и проверка печатает причину и
 * заканчивается — потому что «данных нет» и «движок сломан» это разные вещи.
 *
 * Здесь проверяется то, чего построенный спектр не покажет: как движок ведёт
 * себя на форме, которую никто не задумывал, — с настоящими хвостами линий,
 * настоящим наклоном подложки и настоящим уходом шкалы.
 */
class LocalSpectraTest {

    private fun skip(name: String): Boolean {
        if (LocalSpectra.file(name) != null) return false
        println("пропуск: нет local/spectra/$name — проверка на настоящем спектре не запускалась")
        return true
    }

    @Test
    fun `на настоящем фоне находятся линии природных рядов`() {
        val name = "background-71h.csv"
        if (skip(name)) return
        val (counts, calibration) = assertNotNull(LocalSpectra.csv(name))

        val peaks = PeakDetection.detect(
            counts = counts,
            calibration = calibration,
            resolution662 = DeviceModel.UNKNOWN.peakResolution662,
            minEnergyKeV = DeviceModel.UNKNOWN.peakFloorKeV,
        )

        // Фон, набранный десятками часов, обязан показать хотя бы несколько
        // линий: если движок не находит их на таком материале, порог значимости
        // задран.
        assertTrue(peaks.size >= 3, "на 71 часе фона найдено линий: ${peaks.size}")
        // И измеренную ширину хотя бы у одной: по ней меряется разрешение.
        assertTrue(peaks.any { it.fwhmKeV != null }, "ни у одной линии не измерена ширина")
    }

    @Test
    fun `разложение по настоящему источнику сходится`() {
        val source = "th232-source.csv"
        val background = "background-71h.csv"
        if (skip(source) || skip(background)) return
        val (sourceCounts, sourceCalibration) = assertNotNull(LocalSpectra.csv(source))
        val (backgroundCounts, backgroundCalibration) = assertNotNull(LocalSpectra.csv(background))

        val template = SpectrumTemplate(
            name = "Th-232",
            counts = sourceCounts,
            calibration = sourceCalibration,
            seconds = 27_714L,
            resolution662 = 0.084f,
        )
        val result = assertNotNull(
            SpectrumUnmix.of(
                counts = backgroundCounts,
                calibration = backgroundCalibration,
                resolution662 = 0.084f,
                templates = listOf(template),
            ),
            "разложение настоящего фона по настоящему шаблону не получилось",
        )

        assertTrue(result.converged, "подгонка на настоящих формах не сошлась")
        // Один ториевый шаблон не описывает фон целиком: там ещё калий и
        // урановый ряд. Статистика обязана это видеть.
        assertTrue(
            !result.consistent,
            "неполный состав признан согласованным: ${result.cashDeviation}σ",
        )
    }
}
