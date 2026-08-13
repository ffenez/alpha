package app.radiacode.analysis.validation

import app.radiacode.analysis.PeakDetection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Измерение поиска пиков на данных с известной истиной.
 *
 * До этого стенда о `PeakDetection` было известно только то, что он находит
 * горб, нарисованный руками в тесте. Два числа, которых не было: сколько пиков
 * он находит на ЧИСТОМ фоне и с какой площади начинает находить настоящие.
 * Первое — цена ложной тревоги, второе — предел чувствительности; обоими
 * определяется, можно ли верить таблице пиков на экране.
 *
 * ## Что измерено (этот стенд, RC-110, часовой фон)
 *
 * - **ложных пиков нет**: 0 находок на 30 спектрах чистого фона — гейт по
 *   ширине вместе с порогом значимости 4σ отсекает пуассоновские выбросы
 *   полностью;
 * - **порог чувствительности** на 661,7 кэВ по площади фотопика: 200 импульсов
 *   — 0 %, 500 — 42 %, 1000 — 92 %, 3000 — 100 % прогонов.
 *
 * Ограничение стенда названо в [SyntheticSpectra]: пики рисуются той же
 * моделью разрешения, которой потом ищутся, поэтому систематическая ошибка
 * модели прибора здесь невидима.
 */
class PeakDetectionValidationTest {

    private val calibration = SyntheticSpectra.RC110_CALIBRATION

    /** Часовая запись природного фона: ни одной линии, только континуум и шум. */
    private fun background(seed: Long) = SyntheticSpectra.build(
        lines = emptyList(),
        calibration = calibration,
        seed = seed,
    )

    @Test
    fun `pure background almost never produces a peak`() {
        // Ложный пик на экране — это приглашение искать источник там, где его
        // нет, поэтому цена ошибки здесь несимметрична: лучше пропустить
        // слабую линию, чем показать несуществующую.
        var withPeaks = 0
        var total = 0
        for (seed in 1L..30L) {
            val peaks = PeakDetection.detect(background(seed), calibration)
            total += peaks.size
            if (peaks.isNotEmpty()) withPeaks += 1
        }

        assertTrue(
            withPeaks <= 3,
            "ложные пики в $withPeaks из 30 спектров чистого фона (всего $total)",
        )
    }

    @Test
    fun `a caesium line of a realistic area is found where it was put`() {
        // 3000 импульсов в фотопике за час — порядок, который даёт слабый, но
        // реальный источник рядом с прибором.
        val spectrum = SyntheticSpectra.build(
            lines = listOf(SyntheticSpectra.Line(energyKeV = 661.7, netCounts = 3_000.0)),
            calibration = calibration,
            seed = 7L,
        )

        val peaks = PeakDetection.detect(spectrum, calibration)
        val fwhm = PeakDetection.fwhmKeV(661.7f)
        val found = peaks.minByOrNull { abs(it.energyKeV - 661.7f) }

        assertTrue(found != null, "линия 661,7 кэВ не найдена вовсе")
        assertTrue(
            abs(found.energyKeV - 661.7f) <= fwhm / 2f,
            "центроида уехала на ${abs(found.energyKeV - 661.7f)} кэВ при полуширине $fwhm",
        )
        // Значимость — нетто/σ(нетто); при трёх тысячах импульсов она обязана
        // быть заметно выше порога, иначе порог отсекал бы настоящие линии.
        assertTrue(found.significance > 10f, "значимость ${found.significance}")
    }

    @Test
    fun `detection improves with area, and the threshold is where it should be`() {
        // Лестница чувствительности: доля прогонов, в которых линия найдена,
        // обязана расти с площадью. Абсолютные пороги здесь не пиним — они
        // зависят от модели фона; пиним МОНОТОННОСТЬ и то, что тысяча
        // импульсов уже уверенно видна.
        val areas = listOf(200.0, 500.0, 1_000.0, 3_000.0)
        val rates = areas.map { area ->
            var hits = 0
            for (seed in 1L..12L) {
                val spectrum = SyntheticSpectra.build(
                    lines = listOf(SyntheticSpectra.Line(661.7, area)),
                    calibration = calibration,
                    seed = seed,
                )
                val fwhm = PeakDetection.fwhmKeV(661.7f)
                if (PeakDetection.detect(spectrum, calibration).any {
                        abs(it.energyKeV - 661.7f) <= fwhm / 2f
                    }
                ) {
                    hits += 1
                }
            }
            hits / 12.0
        }

        assertTrue(
            rates.zipWithNext().all { (weaker, stronger) -> stronger >= weaker },
            "доля находок не растёт с площадью: $rates",
        )
        // Измерено этим стендом: 200 импульсов — 0 %, 500 — 42 %, 1000 — 92 %,
        // 3000 — 100 %. Пинится не точка, а форма кривой: слабая линия не
        // обязана находиться, сильная обязана.
        assertTrue(rates.first() <= 0.5, "слабейшая линия находится слишком охотно: $rates")
        assertTrue(rates.last() >= 0.9, "сильная линия найдена лишь в ${rates.last()}: $rates")
    }

    @Test
    fun `the measured width of a found peak matches the model that drew it`() {
        // Гейт по ширине — главный фильтр одноканальных выбросов, и он обязан
        // ПРОПУСКАТЬ настоящий пик: если измеренная ширина систематически
        // разойдётся с ожидаемой, отсеиваться начнут именно линии.
        val spectrum = SyntheticSpectra.build(
            lines = listOf(SyntheticSpectra.Line(661.7, 5_000.0)),
            calibration = calibration,
            seed = 3L,
        )
        val peak = PeakDetection.detect(spectrum, calibration)
            .minByOrNull { abs(it.energyKeV - 661.7f) }
        val expected = PeakDetection.fwhmKeV(661.7f)
        val measured = peak?.fwhmKeV

        assertTrue(measured != null, "ширина не измерена")
        assertTrue(
            measured in expected * 0.6f..expected * 1.6f,
            "измеренная ширина $measured против ожидаемой $expected",
        )
    }

    @Test
    fun `a second line does not hide the first`() {
        // Две линии в одном спектре: K-40 и Cs-137. Разъезд по энергии заведомо
        // больше полуширины, поэтому обе обязаны найтись — иначе поиск пиков
        // «залипает» на сильнейшей структуре.
        val spectrum = SyntheticSpectra.build(
            lines = listOf(
                SyntheticSpectra.Line(661.7, 4_000.0),
                SyntheticSpectra.Line(1_460.8, 4_000.0),
            ),
            calibration = calibration,
            seed = 11L,
        )

        val peaks = PeakDetection.detect(spectrum, calibration)
        for (energy in listOf(661.7f, 1_460.8f)) {
            val fwhm = PeakDetection.fwhmKeV(energy)
            assertTrue(
                peaks.any { abs(it.energyKeV - energy) <= fwhm / 2f },
                "линия $energy кэВ потеряна; найдено: ${peaks.map { it.energyKeV }}",
            )
        }
    }
}
