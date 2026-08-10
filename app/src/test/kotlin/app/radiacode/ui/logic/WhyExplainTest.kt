package app.radiacode.ui.logic

import app.radiacode.baseline.Admission
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineExclusion
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.ExclusionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    private fun input(
        status: MonitorStatus = MonitorStatus.Usual(baseline),
        baselineState: BaselineState? = BaselineState.Active(baseline),
        admission: Admission = Admission.Admitted,
        exclusions: List<ExclusionSummary> = emptyList(),
        freshness: Freshness = Freshness.Fresh(1),
    ) = WhyInput(
        status = status,
        baselineState = baselineState,
        doseRateMicroSvH = 0.15f,
        cps = 24.6f,
        freshness = freshness,
        thresholds = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f),
        admission = admission,
        exclusions = exclusions,
        unit = DoseUnitSetting.MICRO_SIEVERT,
        profileName = "Дом / Спальня",
        contextWording = "выбран автоматически по знакомой сети",
    )

    private fun line(input: WhyInput, label: String) =
        WhyExplain.lines(input).first { it.label == label }

    @Test
    fun `verdict repeats the main screen headline verbatim`() {
        assertEquals("Обычный фон", WhyExplain.verdict(MonitorStatus.Usual(baseline)))
        assertEquals(
            "Уровень радиации изменился",
            WhyExplain.verdict(MonitorStatus.Alert(baseline, 300, 0.3f)),
        )
    }

    @Test
    fun `every level of certainty is present and never merged`() {
        val lines = WhyExplain.lines(input())
        val levels = lines.map { it.evidence }.toSet()
        assertTrue(Evidence.MEASURED in levels)
        assertTrue(Evidence.CALCULATED in levels)
        assertTrue(Evidence.STATISTICALLY_DETECTED in levels)
        // A single line carries exactly one level — that is what «не объединять
        // уровни в одной фразе» means structurally.
        assertTrue(lines.all { it.label.isNotBlank() && it.value.isNotBlank() })
    }

    @Test
    fun `measured values are marked as measured, integrals as calculated`() {
        val input = input()
        assertEquals(Evidence.MEASURED, line(input, "Сейчас").evidence)
        assertEquals(Evidence.MEASURED, line(input, "Счёт").evidence)
        assertEquals(Evidence.CALCULATED, line(input, "Порог тревоги").evidence)
        assertEquals(Evidence.STATISTICALLY_DETECTED, line(input, "Обычный диапазон").evidence)
    }

    @Test
    fun `baseline lines quote the band, the quartiles and the MAD`() {
        val input = input()
        assertEquals("0,09–0,16 мкЗв/ч", line(input, "Обычный диапазон").value)
        assertEquals("P10–P90 профиля", line(input, "Обычный диапазон").note)
        assertTrue(line(input, "Медиана · P25–P75").value.contains("0,10–0,14"))
        assertTrue(line(input, "MAD").value.startsWith("0,02"))
        assertTrue(line(input, "MAD").note!!.contains("median(|xᵢ − медиана|)"))
    }

    @Test
    fun `collected line reports duration and the honest n`() {
        val value = line(input(), "Собрано").value
        assertEquals("26 ч · 1560 минутных корзин", value)
    }

    @Test
    fun `learning baseline says so instead of inventing a band`() {
        val input = input(baselineState = BaselineState.Learning(3_600, 10_800))
        assertEquals("ещё не собран", line(input, "Обычный фон").value)
        assertTrue(line(input, "Обычный фон").note!!.contains("изучаю обычный фон"))
    }

    @Test
    fun `admission line names the reason learning stopped`() {
        val excluded = input(admission = Admission.Excluded(BaselineExclusion.EXPERIMENT))
        val row = line(excluded, "Сейчас учится")
        assertEquals("нет", row.value)
        assertTrue(row.note!!.contains("идёт Поиск или эксперимент"))
        assertTrue(row.note.contains("записываются"), "raw data is always kept — say it")

        assertEquals("да", line(input(), "Сейчас учится").value)
    }

    @Test
    fun `excluded intervals are summarised with the top reasons`() {
        val input = input(
            exclusions = listOf(
                ExclusionSummary(BaselineExclusion.EXPERIMENT, 3_600),
                ExclusionSummary(BaselineExclusion.QUARANTINE, 1_800),
                ExclusionSummary(BaselineExclusion.STREAM_STALE, 120),
                ExclusionSummary(BaselineExclusion.MANUAL_FREEZE, 60),
            ),
        )
        val row = line(input, "Исключено из обучения")
        // 3600 + 1800 + 120 + 60 = 5580 с
        assertEquals("1,6 ч", row.value)
        assertTrue(row.note!!.startsWith("идёт Поиск или эксперимент (1 ч)"), row.note)
        assertTrue(row.note.contains("карантин после отклонения (30 мин)"))
        assertTrue(!row.note.contains("заморожен вручную"), "only the top three reasons")
    }

    @Test
    fun `no exclusions says so explicitly`() {
        assertEquals("нет интервалов", line(input(), "Исключено из обучения").value)
    }

    @Test
    fun `spectral anomaly is honestly reported as not evaluated`() {
        val row = line(input(), "Спектральная аномалия")
        assertEquals("не оценивается", row.value)
        assertEquals(Evidence.STATISTICALLY_DETECTED, row.evidence)
    }

    @Test
    fun `stale stream is visible in the explanation`() {
        val row = line(input(freshness = Freshness.Stale(38)), "Поток данных")
        assertEquals("поток прерван 38 с назад", row.value)
    }

    @Test
    fun `duration wording covers seconds, minutes and hours`() {
        assertEquals("45 с", durationWording(45))
        assertEquals("12 мин", durationWording(12 * 60))
        assertEquals("3,5 ч", durationWording(3 * 3600 + 1800))
        assertEquals("1 ч", durationWording(3600))
        assertEquals("26 ч", durationWording(26 * 3600))
    }
}
