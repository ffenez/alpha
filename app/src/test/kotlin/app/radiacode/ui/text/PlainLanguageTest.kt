package app.radiacode.ui.text

import app.radiacode.baseline.Admission
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineExclusion
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.alarmThresholds
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.ExclusionSummary
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.MonitorStatus
import app.radiacode.ui.logic.WhyInput
import app.radiacode.ui.logic.WhyLevel
import app.radiacode.ui.logic.WhyReportBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Первый уровень интерфейса не говорит языком реализации алгоритма (ТЗ §2, §3,
 * §12, §21, §23).
 *
 * Запрет действует ИМЕННО НА ПЕРВОМ УРОВНЕ: «нетто-площадь», «минутные
 * корзины», «окно решения» и «экспозиция» никуда не делись — они лежат в
 * подробностях и в технических данных, где термин и есть предмет разговора.
 * Проверяется поэтому не весь проект подряд, а поимённо перечисленные
 * поверхности, которые человек видит, ничего не раскрывая.
 */
class PlainLanguageTest {

    /**
     * Слова из таблицы §3. Целыми конструкциями, а не буквами: «пригодн» ловит
     * и «пригодных данных», и «непригодно», а `\bкандидат` — самостоятельное
     * слово, но не «candidate» внутри имени ключа каталога.
     */
    private val jargon = listOf(
        Regex("""карантин"""),
        Regex("""\bнетто"""),
        Regex("""корзин"""),
        Regex("""пригодн"""),
        Regex("""окн\w* решени"""),
        Regex("""\bкандидат"""),
        Regex("""экспозиц"""),
        Regex("""baseline"""),
    )

    private val jargonEn = listOf(
        Regex("""quarantine"""),
        Regex("""\bnet\b"""),
        Regex("""bucket"""),
        Regex("""decision window"""),
        Regex("""\bcandidate\b"""),
        Regex("""exposure"""),
        Regex("""baseline"""),
    )

    private fun assertPlain(texts: List<String>, forbidden: List<Regex>) {
        for (text in texts) {
            for (word in forbidden) {
                assertTrue(
                    !word.containsMatchIn(text.lowercase()),
                    "«${word.pattern}» на первом уровне: $text",
                )
            }
        }
    }

    // ------------------------------------------------------------- Главная

    @Test
    fun `the main screen speaks no algorithm jargon`() {
        assertPlain(MonitorRu.allTexts(), jargon)
        assertPlain(MonitorEn.allTexts(), jargonEn)
    }

    // ------------------------------------ «Почему такой вывод», первый уровень

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

    /** Самый «жаргоноопасный» случай: статистика приостановлена, причин две. */
    private fun excludedInput() = WhyInput(
        status = MonitorStatus.Usual(baseline),
        baselineState = BaselineState.Active(baseline),
        doseRateMicroSvH = 0.16f,
        cps = 25f,
        freshness = Freshness.Fresh(1),
        thresholds = alarmThresholds(AlarmSensitivity.NORMAL, 0.30f, 0.60f),
        admission = Admission.Excluded(BaselineExclusion.QUARANTINE),
        exclusions = listOf(
            ExclusionSummary(BaselineExclusion.EXPERIMENT, 8 * 3600L),
            ExclusionSummary(BaselineExclusion.QUARANTINE, 44 * 60L),
        ),
        unit = DoseUnitSetting.MICRO_SIEVERT,
        profileName = "Дом",
        contextWording = "выбран автоматически по знакомой Wi-Fi сети",
        fingerprint = null,
    )

    @Test
    fun `the why sheet opens in human words, the terms wait one level deeper`() {
        for (strings in listOf<Strings>(RuStrings, EnStrings)) {
            val report = WhyReportBuilder.build(excludedInput(), strings)
            val plain = buildList {
                add(report.status)
                add(report.sentence)
                add(report.caveat)
                report.sections(WhyLevel.PLAIN).forEach { section ->
                    add(section.title)
                    section.note?.let { add(it) }
                    section.lines.forEach {
                        add(it.label)
                        add(it.value)
                        it.note?.let { note -> add(note) }
                    }
                }
            }
            assertPlain(plain, if (strings === RuStrings) jargon else jargonEn)
        }
    }

    /**
     * §21.9: постоянных меток «изм. · расч. · стат.» на первом уровне нет.
     * Глубже они остаются — там источник числа и есть предмет разговора.
     */
    @Test
    fun `the first level carries no certainty markers, the folded levels do`() {
        val report = WhyReportBuilder.build(excludedInput())
        assertTrue(
            report.sections(WhyLevel.PLAIN).flatMap { it.lines }.all { it.evidence == null },
            "метка достоверности на первом уровне",
        )
        assertTrue(
            report.sections(WhyLevel.METHOD).flatMap { it.lines }.any { it.evidence != null },
            "на втором уровне метки должны остаться",
        )
    }

    // ------------------------------------------------- Спектр и Поиск, экран

    @Test
    fun `the peak table and the search screen name things, not their algorithms`() {
        assertPlain(
            listOf(
                RuStrings.peakTableEnergy,
                RuStrings.peakTableNet,
                RuStrings.peakTableSignificance,
                RuStrings.peakTableCandidate,
                SpectrumRu.infoCandidateTitle,
                SearchRu.statDecisionWindow,
                SearchRu.navSpotExposure(30),
                SearchRu.backgroundDetail(45, 45, "хорошее", "Дом"),
                SearchRu.noReadingsInWindow,
            ),
            jargon,
        )
        assertPlain(
            listOf(
                EnStrings.peakTableNet,
                EnStrings.peakTableCandidate,
                SpectrumEn.infoCandidateTitle,
                SearchEn.statDecisionWindow,
                SearchEn.navSpotExposure(30),
                SearchEn.backgroundDetail(45, 45, "good", "Home"),
                SearchEn.noReadingsInWindow,
            ),
            jargonEn,
        )
    }

    /**
     * Отказы остаются отказами: короче — можно, тише — нет. Их пропажа была бы
     * не упрощением, а научной регрессией.
     */
    @Test
    fun `the caveats survive the simplification`() {
        assertTrue(RuStrings.peakTableCaveat.contains("возможное совпадение ≠ обнаружение"))
        assertTrue(RuStrings.notASafetyConclusion.contains("не является заключением"))
        assertTrue(RuStrings.bandExplained.contains("не норматив"))
        assertTrue(RuStrings.madNote.contains("не погрешность прибора"))
        assertTrue(RuStrings.thresholdIsNotSafety.contains("не граница безопасности"))
    }
}
