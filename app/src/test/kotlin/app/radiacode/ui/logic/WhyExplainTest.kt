package app.radiacode.ui.logic

import app.radiacode.baseline.Baseline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The verdict wording only — the body of the sheet is pinned by
 * `WhyReportTest`, which is where it is now assembled.
 */
class WhyExplainTest {

    private val baseline = Baseline(
        doseLowMicroSvH = 0.09f,
        doseMedianMicroSvH = 0.12f,
        doseHighMicroSvH = 0.16f,
        doseP25MicroSvH = 0.10f,
        doseP75MicroSvH = 0.14f,
        doseMadMicroSvH = 0.02f,
        cpsLow = 18f,
        cpsMedian = 22f,
        cpsHigh = 27f,
        accumulatedSeconds = 26L * 3600L,
        sampleCount = 26L * 3600L,
        bucketCount = 1560,
    )

    @Test
    fun `verdict repeats the main screen headline verbatim`() {
        assertEquals(
            "В обычном диапазоне этого профиля",
            WhyExplain.verdict(MonitorStatus.Usual(baseline)),
        )
        assertEquals(
            "Уровень радиации изменился",
            WhyExplain.verdict(MonitorStatus.Alert(baseline, 300, 0.3f)),
        )
    }

    @Test
    fun `the verdict says what it was compared with`() {
        assertEquals(
            "Текущая мощность дозы находится внутри исторического P10–P90 профиля.",
            WhyExplain.verdictExplanation(MonitorStatus.Usual(baseline)),
        )
        assertTrue(
            WhyExplain.verdictExplanation(MonitorStatus.AboveUsual(baseline, 300))
                .contains("P10–P90 профиля"),
        )
        val forbidden = listOf("норма", "безопас", "допустим")
        for (status in listOf(
            MonitorStatus.Unknown,
            MonitorStatus.Fixed(above = true, thresholdMicroSvH = 0.3f),
            MonitorStatus.Usual(baseline),
            MonitorStatus.AboveUsual(baseline, 300),
            MonitorStatus.Alert(baseline, 300, 0.3f),
        )) {
            val text = WhyExplain.verdictExplanation(status).lowercase()
            for (word in forbidden) assertTrue(!text.contains(word), "«$word» in «$text»")
        }
    }

}
