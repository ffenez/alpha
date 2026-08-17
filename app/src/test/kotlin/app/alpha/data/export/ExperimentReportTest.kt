package app.alpha.data.export

import app.alpha.analysis.AbAnalysis
import app.alpha.analysis.AbExperiment
import app.alpha.analysis.AlgorithmVersions
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindows
import app.alpha.data.JsonMap
import app.alpha.data.db.ExperimentEntity
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The A/B report must be reproducible on its own (spec §22, §16). */
class ExperimentReportTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val createdAt =
        ZonedDateTime.of(2026, 8, 10, 14, 22, 3, 0, zone).toInstant().toEpochMilli()
    private val calibration = EnergyCalibration(0f, 1f, 0f)

    private fun experiment(kind: String = ExperimentEntity.KIND_BACKGROUND_VS_OBJECT) =
        ExperimentEntity(
            id = 1,
            kind = kind,
            profileId = 3,
            createdAt = createdAt,
            note = "керамическая тарелка",
            geometry = "тарелка на столе, прибор экраном вверх, 5 см",
            algorithmVersion = AlgorithmVersions.AB_ANALYSIS,
            params = JsonMap.of("windowsKeV" to "100:300,300:700,700:1500"),
        )

    private fun run(
        label: String,
        counts: List<Int>,
        seconds: Long,
        distanceCm: Float? = null,
        dose: List<Double> = List(seconds.toInt()) { 0.12 },
    ) = AbExperiment.RunData(
        id = label.hashCode().toLong(),
        label = label,
        startedAt = createdAt + 60_000L,
        endedAt = createdAt + 60_000L + seconds * 1000L,
        durationSeconds = seconds,
        counts = counts,
        calibration = calibration,
        doseStats = AbAnalysis.doseStats(dose),
        distanceCm = distanceCm,
    )

    @Test
    fun `report states geometry, runs, verdicts and reproducibility metadata`() {
        val objectRun = run("A", List(2000) { if (it in 400..500) 60 else 5 }, 300)
        val backgroundRun = run("B", List(2000) { 5 }, 300)
        val comparison = AbExperiment.compare(objectRun, backgroundRun)

        val report = ExperimentReport.render(
            experiment = experiment(),
            profileName = "Дом",
            runs = listOf(objectRun, backgroundRun),
            comparison = comparison,
            windowSpecs = EnergyWindows.DEFAULTS,
            appVersion = "0.1.0-alpha",
            zone = zone,
        )

        assertTrue(report.contains("A/B ЭКСПЕРИМЕНТ · Фон и объект"), report)
        assertTrue(report.contains("создан: 10.08.2026 14:22:03"), report)
        assertTrue(report.contains("профиль: Дом"), report)
        assertTrue(report.contains("геометрия: тарелка на столе"), report)
        assertTrue(report.contains("статус: Экспериментально"), report)

        // §22: normalization, background method, algorithm versions, parameters.
        assertTrue(report.contains("нормализация: ${ProcessingMetadata.NORMALIZATION_RATE}"), report)
        assertTrue(report.contains("σ_net = √(G + B·(t_G/t_B)²)"), report)
        assertTrue(report.contains("ab_analysis v${AlgorithmVersions.AB_ANALYSIS}"), report)
        assertTrue(report.contains("energy_windows v${AlgorithmVersions.ENERGY_WINDOWS}"), report)
        assertTrue(report.contains("энергетические окна, кэВ: 100–300, 300–700, 700–1500"), report)
        assertTrue(report.contains("windowsKeV=100:300,300:700,700:1500"), report)
        assertTrue(
            report.contains("переключение статистики") &&
                report.contains(AbAnalysis.NORMAL_APPROX_MIN_COUNTS.toInt().toString()),
            report,
        )

        // Runs with their live times and statistics.
        assertTrue(report.contains("A · объект · начало"), report)
        assertTrue(report.contains("B · фон · начало"), report)
        assertTrue(report.contains("длительность 5 мин"), report)
        assertTrue(report.contains("мощность дозы 0,12"), report)

        // Verdicts with both the Russian label and the canonical token.
        assertTrue(report.contains("strong evidence of change"), report)
        assertTrue(report.contains("полный счёт:"), report)
        assertTrue(report.contains("100–300 кэВ:"), report)
        assertTrue(report.contains("полный спектр: каналов"), report)
        assertTrue(report.contains("приложение: 0.1.0-alpha"), report)
    }

    @Test
    fun `report never contains a similarity percentage`() {
        val a = run("A", List(2000) { 20 }, 300)
        val b = run("B", List(2000) { 20 }, 300)
        val report = ExperimentReport.render(
            experiment = experiment(),
            profileName = null,
            runs = listOf(a, b),
            comparison = AbExperiment.compare(a, b),
            windowSpecs = EnergyWindows.DEFAULTS,
            zone = zone,
        )
        assertFalse(report.contains("похожест"), report)
        assertTrue(report.contains("consistent"), report)
        assertTrue(report.contains("профиль: не выбран"), report)
    }

    @Test
    fun `distance report carries the series and the mandated warning`() {
        val runs = listOf(
            run("A", List(2000) { 40 }, 100, distanceCm = 10f),
            run("B", List(2000) { 10 }, 100, distanceCm = 20f),
        )
        val report = ExperimentReport.render(
            experiment = experiment(ExperimentEntity.KIND_DISTANCE),
            profileName = "Дом",
            runs = runs,
            comparison = AbExperiment.compare(runs[0], runs[1]),
            windowSpecs = EnergyWindows.DEFAULTS,
            distance = AbExperiment.distanceSeries(runs),
            zone = zone,
        )
        assertTrue(report.contains("СЕРИЯ ПО РАССТОЯНИЮ"), report)
        assertTrue(report.contains("10 см:"), report)
        assertTrue(report.contains("20 см:"), report)
        assertTrue(report.contains("1/r²"), report)
        assertTrue(report.contains("рассеива"), report)
    }

    @Test
    fun `shielding report refuses attenuation coefficients`() {
        val runs = listOf(run("A", List(2000) { 20 }, 300), run("B", List(2000) { 15 }, 300))
        val report = ExperimentReport.render(
            experiment = experiment(ExperimentEntity.KIND_SHIELDING),
            profileName = null,
            runs = runs,
            comparison = AbExperiment.compare(runs[0], runs[1]),
            windowSpecs = EnergyWindows.DEFAULTS,
            zone = zone,
        )
        assertTrue(report.contains("не выводятся коэффициенты ослабления"), report)
    }

    @Test
    fun `an experiment without a second run says so instead of comparing`() {
        val runs = listOf(run("A", List(2000) { 20 }, 300))
        val report = ExperimentReport.render(
            experiment = experiment(),
            profileName = null,
            runs = runs,
            comparison = null,
            windowSpecs = EnergyWindows.DEFAULTS,
            zone = zone,
        )
        assertTrue(report.contains("СРАВНЕНИЕ: нужно как минимум два завершённых прогона"), report)
    }

    @Test
    fun `file name is sortable and carries the creation stamp`() {
        assertEquals(
            "alpha-ab-20260810-142203.txt",
            ExperimentReport.fileName(experiment(), zone),
        )
    }
}
