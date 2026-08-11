package app.radiacode.ui.logic

import app.radiacode.baseline.Admission
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineAdmission
import app.radiacode.baseline.BaselineExclusion
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.ExclusionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The «Почему такой вывод» sheet is the audit trail of a human conclusion
 * (why-spec §18), so its checklist (§17) is a test, not a review item.
 */
class WhyReportTest {

    private val baseline = Baseline(
        doseLowMicroSvH = 0.14f,
        doseMedianMicroSvH = 0.15f,
        doseHighMicroSvH = 0.17f,
        doseP25MicroSvH = 0.15f,
        doseP75MicroSvH = 0.16f,
        doseMadMicroSvH = 0.01f,
        cpsLow = 20f,
        cpsMedian = 25f,
        cpsHigh = 30f,
        accumulatedSeconds = 23 * 3600L,
        sampleCount = 82_800L,
        bucketCount = 1_441,
    )

    private val thresholds = alarmThresholds(
        sensitivity = AlarmSensitivity.NORMAL,
        customL1MicroSvH = 0.30f,
        customL2MicroSvH = 0.60f,
    )

    private fun input(
        status: MonitorStatus = MonitorStatus.Usual(baseline),
        baselineState: BaselineState? = BaselineState.Active(baseline),
        dose: Float? = 0.16f,
        admission: Admission = Admission.Admitted,
        exclusions: List<ExclusionSummary> = emptyList(),
    ) = WhyInput(
        status = status,
        baselineState = baselineState,
        doseRateMicroSvH = dose,
        cps = 25.3f,
        freshness = Freshness.Fresh(1),
        thresholds = thresholds,
        admission = admission,
        exclusions = exclusions,
        unit = DoseUnitSetting.MICRO_SIEVERT,
        profileName = "Дом",
        contextWording = "выбран автоматически по знакомой Wi-Fi сети",
    )

    private fun allText(report: WhyReport): List<String> = buildList {
        add(report.status)
        add(report.sentence)
        report.nowValue?.let { add(it) }
        report.usualValue?.let { add(it) }
        add(report.legend)
        report.sections.forEach { section ->
            add(section.title)
            section.note?.let { add(it) }
            section.lines.forEach { line ->
                add(line.label)
                add(line.value)
                line.note?.let { add(it) }
            }
        }
    }

    // ---------------------------------------------------- the answer first

    @Test
    fun `the sheet opens with the verdict, its sentence and the evidence`() {
        val report = WhyReportBuilder.build(input())
        assertEquals("В обычном диапазоне этого профиля", report.status)
        assertEquals(WhyTone.OK, report.tone)
        assertTrue(report.sentence.contains("P10–P90"), report.sentence)
        assertEquals("0,16 мкЗв/ч", report.nowValue)
        assertEquals("0,14–0,17 мкЗв/ч", report.usualValue)

        // ...and the first section is «Сейчас», not MAD or quarantine (§1).
        assertEquals("Сейчас", report.sections.first().title)
        assertTrue(
            report.sections.first().lines.none { it.label == "MAD" },
            "the sheet must not open with the spread",
        )
    }

    @Test
    fun `the mini scale places the value inside its band`() {
        val scale = assertNotNull(WhyReportBuilder.build(input()).scale)
        assertEquals("0,14", scale.lowLabel)
        assertEquals("0,17", scale.highLabel)
        assertEquals("0,16", scale.currentLabel)
        assertTrue(scale.position in 0.6f..0.7f, "${scale.position}")
        assertTrue(!scale.outside)

        val above = assertNotNull(WhyReportBuilder.build(input(dose = 0.40f)).scale)
        assertEquals(1f, above.position)
        assertTrue(above.outside)
    }

    @Test
    fun `a degenerate band still puts the dot on the right side`() {
        val flat = baseline.copy(doseLowMicroSvH = 0.15f, doseHighMicroSvH = 0.15f)
        val above = assertNotNull(
            WhyScale.of(0.40f, flat, DoseUnitSetting.MICRO_SIEVERT),
        )
        assertEquals(1f, above.position)
        assertTrue(above.outside)

        val below = WhyScale.of(0.01f, flat, DoseUnitSetting.MICRO_SIEVERT)
        assertEquals(0f, below.position)
        assertTrue(below.outside)
    }

    @Test
    fun `without a current reading there is no scale to draw`() {
        val report = WhyReportBuilder.build(input(status = MonitorStatus.Unknown, dose = null))
        assertNull(report.scale)
        assertNull(report.nowValue)
        assertEquals(WhyTone.UNKNOWN, report.tone)
    }

    // ------------------------------------------------- §17 checklist as tests

    @Test
    fun `no wording in the sheet turns a historical range into a norm`() {
        val reports = listOf(
            WhyReportBuilder.build(input()),
            WhyReportBuilder.build(
                input(
                    status = MonitorStatus.AboveUsual(baseline, heldSeconds = 300),
                    admission = Admission.Excluded(BaselineExclusion.QUARANTINE),
                    exclusions = listOf(
                        ExclusionSummary(BaselineExclusion.EXPERIMENT, 8 * 3600L),
                        ExclusionSummary(BaselineExclusion.QUARANTINE, 44 * 60L),
                    ),
                ),
            ),
            WhyReportBuilder.build(
                input(
                    status = MonitorStatus.Fixed(above = false, thresholdMicroSvH = 0.30f),
                    baselineState = BaselineState.Learning(2 * 3600L + 840L, 3 * 3600L),
                ),
            ),
        )
        // Whole words, not substrings: the spec itself requires the sentence
        // «это не норматив радиационной безопасности», which is a denial of a
        // safety claim, not one.
        val forbidden = listOf(
            Regex("\\bбезопасн(о|ый|ая|ое)\\b"),
            Regex("\\bопасн(о|ый|ая|ое)\\b"),
            Regex("\\bдопустим\\w*\\b"),
            // «нормальное распределение» is a statistical term the MAD note
            // needs; «нормальный уровень» is the claim that is banned.
            Regex("\\bнормальн\\w*\\b(?! распределени)"),
            Regex("\\bнорма\\b"),
        )
        for (report in reports) {
            for (text in allText(report)) {
                for (word in forbidden) {
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word» in: $text")
                }
            }
        }
    }

    @Test
    fun `the band is named as this profile's own history, not as a limit`() {
        val comparison = WhyReportBuilder.build(input())
            .sections.single { it.title == "Сравнение с профилем" }
        val note = assertNotNull(comparison.note)
        assertTrue(note.contains("80 %"), note)
        assertTrue(note.contains("не норматив"), note)
        assertEquals("внутри P10–P90", comparison.lines.single { it.label == "Положение" }.value)
    }

    @Test
    fun `MAD is never called an instrument error`() {
        val statistics = WhyReportBuilder.build(input())
            .sections.single { it.title == "Статистика профиля" && it.advanced }
        val mad = statistics.lines.single { it.label == "MAD" }
        val note = assertNotNull(mad.note)
        assertTrue(note.contains("не погрешность прибора"), note)
        assertTrue(note.contains("median(|xᵢ"), note)
    }

    @Test
    fun `each heading appears once, so two blocks are never one`() {
        val titles = WhyReportBuilder.build(input()).sections.map { it.title }
        assertEquals(titles.distinct(), titles, "$titles")
    }

    @Test
    fun `the profile statistics state never speaks of training`() {
        val states = listOf(
            WhyReportBuilder.build(input()),
            WhyReportBuilder.build(
                input(admission = Admission.Excluded(BaselineExclusion.QUARANTINE)),
            ),
            WhyReportBuilder.build(
                input(baselineState = BaselineState.Learning(2 * 3600L, 3 * 3600L)),
            ),
        ).map { report -> report.sections.single { it.title == "Состояние статистики" } }

        assertEquals("Обновляется", states[0].lines.first().value)
        assertEquals("Временно не обновляется", states[1].lines.first().value)
        assertEquals("Недостаточно данных", states[2].lines.first().value)

        for (section in states) {
            val text = (section.note.orEmpty() + section.lines.joinToString { it.value })
            for (word in listOf("обучен", "учится", "модель обучается")) {
                assertTrue(!text.lowercase().contains(word), "«$word» in: $text")
            }
        }
    }

    @Test
    fun `excluded time is broken down by reason, never left as a bare word`() {
        val report = WhyReportBuilder.build(
            input(
                admission = Admission.Excluded(BaselineExclusion.QUARANTINE),
                exclusions = listOf(
                    ExclusionSummary(BaselineExclusion.EXPERIMENT, 8 * 3600L),
                    ExclusionSummary(BaselineExclusion.QUARANTINE, 44 * 60L),
                ),
            ),
        )
        val state = report.sections.single { it.title == "Состояние статистики" }
        assertEquals("8,7 ч", state.lines.single { it.label == "Не учтено в статистике" }.value)
        // Both reasons appear as their own lines, largest first.
        val reasons = state.lines.drop(2).map { it.label }
        assertEquals(
            listOf(BaselineExclusion.EXPERIMENT.label, BaselineExclusion.QUARANTINE.label),
            reasons,
        )
        assertTrue(assertNotNull(state.note).contains("новый baseline"), state.note!!)
    }

    @Test
    fun `the detection criteria come from the same source the engine uses`() {
        val criteria = WhyReportBuilder.build(input())
            .sections.single { it.title == "Как обнаруживается отклонение" }
        assertTrue(criteria.advanced, "the algorithm block is folded by default (§8)")
        assertEquals(
            "0,30 мкЗв/ч",
            criteria.lines.single { it.label == "Абсолютный порог L1" }.value,
        )
        assertEquals(
            "${thresholds.relativeFactor.toInt()} × P90 профиля",
            criteria.lines.single { it.label == "Относительный критерий" }.value,
        )
        assertEquals(
            durationWording(thresholds.persistenceSeconds.toLong()),
            criteria.lines.single { it.label == "Минимальная длительность" }.value,
        )
        assertEquals(
            durationWording(BaselineAdmission.QUARANTINE_MILLIS / 1000),
            criteria.lines.single { it.label == "Исключение после события" }.value,
        )
        assertTrue(assertNotNull(criteria.note).contains("не научные границы"), criteria.note!!)
    }

    @Test
    fun `not evaluated never reads as not detected`() {
        val spectral = WhyReportBuilder.build(input())
            .sections.single { it.title == "Спектральное сравнение" }
        assertEquals("пока недоступно", spectral.lines.single().value)
        val note = assertNotNull(spectral.note)
        assertTrue(note.contains("не входит"), note)
        assertTrue(!note.lowercase().contains("не обнаружен"), note)
    }

    @Test
    fun `research numbers stay folded, the answer never does`() {
        val report = WhyReportBuilder.build(input())
        assertTrue(report.hasAdvanced)
        val visible = report.sections.filter { !it.advanced }.map { it.title }
        assertTrue(visible.contains("Сейчас"), "$visible")
        assertTrue(visible.contains("Сравнение с профилем"), "$visible")
        val folded = report.sections.filter { it.advanced }.map { it.title }
        assertTrue(folded.contains("Как обнаруживается отклонение"), "$folded")
    }

    @Test
    fun `without a baseline the sheet says what it does compare with`() {
        val report = WhyReportBuilder.build(
            input(
                status = MonitorStatus.Fixed(above = false, thresholdMicroSvH = 0.30f),
                baselineState = BaselineState.Learning(2 * 3600L + 840L, 3 * 3600L),
            ),
        )
        val comparison = report.sections.single { it.title == "Сравнение с профилем" }
        assertEquals("ещё не собран", comparison.lines.first().value)
        assertTrue(
            assertNotNull(comparison.lines.first().note).contains("минимально необходимых"),
        )
        assertTrue(comparison.lines.any { it.value.contains("L1") })
        assertNull(report.usualValue)
    }

    @Test
    fun `the legend names a statistical model of the profile, not a trained one`() {
        val legend = WhyReportBuilder.build(input()).legend
        assertTrue(legend.contains("статистической модели профиля"), legend)
        assertTrue(!legend.lowercase().contains("обучен"), legend)
    }
}
