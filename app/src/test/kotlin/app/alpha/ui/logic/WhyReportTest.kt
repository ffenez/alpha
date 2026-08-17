package app.alpha.ui.logic

import app.alpha.analysis.Fingerprint
import app.alpha.analysis.FingerprintComparison
import app.alpha.analysis.FingerprintReference
import app.alpha.analysis.FingerprintWindow
import app.alpha.baseline.Admission
import app.alpha.baseline.Baseline
import app.alpha.baseline.BaselineAdmission
import app.alpha.baseline.BaselineExclusion
import app.alpha.baseline.BaselineState
import app.alpha.baseline.alarmThresholds
import app.alpha.baseline.AlarmSensitivity
import app.alpha.data.DoseUnitSetting
import app.alpha.data.ExclusionSummary
import app.alpha.ui.text.MonitorRu
import app.alpha.baseline.wording
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
        fingerprint: FingerprintComparison? = null,
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
        fingerprint = fingerprint,
    )

    private fun allText(report: WhyReport): List<String> = buildList {
        add(report.status)
        add(report.sentence)
        report.nowValue?.let { add(it) }
        report.usualValue?.let { add(it) }
        add(report.legend)
        add(report.caveat)
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
        assertEquals("Обычно здесь", report.status)
        assertEquals(WhyTone.OK, report.tone)
        // 14.md: первая фраза человеческая. Нотация P10–P90 не исчезла — она
        // подписывает шкалу и живёт во втором уровне вместе с объяснением.
        assertTrue(report.sentence.contains("обычно находятся измерения"), report.sentence)
        assertTrue(!report.sentence.contains("P10–P90"), report.sentence)
        assertEquals("0,16 мкЗв/ч", report.nowValue)
        assertEquals("0,14–0,17 мкЗв/ч", report.usualValue)
        // Обязательная оговорка стоит на первом уровне.
        assertTrue(report.caveat.contains("отличие от вашего обычного фона"), report.caveat)
        assertTrue(report.caveat.contains("не является заключением"), report.caveat)

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
        // Средняя засечка шкалы: «P10 · медиана · P90» (14.md §5).
        assertEquals("0,15", scale.medianLabel)
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
        // Положение — человеческими словами; P-нотация стоит на шкале рядом.
        assertEquals(
            "внутри обычного диапазона",
            comparison.lines.single { it.label == "Положение" }.value,
        )
    }

    /** MAD — экспертный уровень (14.md §6), но оговорка при нём осталась. */
    @Test
    fun `MAD is never called an instrument error`() {
        val calculations = WhyReportBuilder.build(input())
            .sections.single { it.title == "Расчёты и формулы" }
        assertEquals(WhyLevel.EXPERT, calculations.level)
        val mad = calculations.lines.single { it.label == "MAD" }
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
        ).map { report -> report.sections.single { it.title == "Обычный фон" } }

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
        // §12: первый уровень называет ОДНО состояние и одно «зачем» —
        // «карантин после отклонения 36 ч» выглядел там основным показателем.
        val state = report.sections.single { it.title == "Обычный фон" }
        assertEquals(WhyLevel.PLAIN, state.level)
        assertEquals(listOf("Сейчас"), state.lines.map { it.label })
        assertEquals("Временно не обновляется", state.lines.single().value)
        val plainNote = assertNotNull(state.note)
        // Первый уровень объясняет ПОСЛЕДСТВИЕ человеческими словами и прямо
        // говорит, что измерения не теряются.
        assertTrue(plainNote.contains("не стал считаться обычным"), plainNote)
        assertTrue(plainNote.contains("сохраняются"), plainNote)

        // Ни одна причина и ни одна длительность не потеряны — они уровнем
        // глубже, где их и ищут.
        val details = report.sections.single { it.title == "Какие измерения не использовались для обычного фона" }
        assertEquals(WhyLevel.METHOD, details.level)
        assertEquals(
            BaselineExclusion.QUARANTINE.label,
            details.lines.single { it.label == "Почему сейчас" }.value,
        )
        assertEquals("8,7 ч", details.lines.single { it.label == "Не пошло в обычный фон" }.value)
        // Both reasons appear as their own lines, largest first.
        val reasons = details.lines.drop(2).map { it.label }
        assertEquals(
            listOf(BaselineExclusion.EXPERIMENT.label, BaselineExclusion.QUARANTINE.label),
            reasons,
        )
        // «baseline» — имя движка, на экране его нет: оговорка объясняет, что
        // отклонение не становится новым ОБЫЧНЫМ ФОНОМ.
        assertTrue(assertNotNull(details.note).contains("обычно"), details.note!!)
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
            criteria.lines.single { it.label == "Порог относительно обычного диапазона" }.value,
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

    /**
     * Четыре состояния спектрального сравнения — принципиально разные
     * утверждения (why-spec §9), и подмена первого вторым и есть та ошибка,
     * ради которой их четыре.
     */
    @Test
    fun `not evaluated never reads as not detected`() {
        fun state(comparison: FingerprintComparison?) = WhyReportBuilder
            .build(input(fingerprint = comparison))
            .sections.single { it.title == "Спектральное сравнение" }

        val none = state(null)
        assertEquals("не оценивалось", none.lines.single().value)
        assertTrue(assertNotNull(none.note).contains("это не «изменений нет»"), none.note!!)

        val noReference = state(Fingerprint.compare(window = null, reference = null))
        assertEquals("не оценивалось", noReference.lines.single().value)

        val thin = state(
            Fingerprint.compare(
                window = FingerprintWindow(
                    doseMedianMicroSvH = 0.16f,
                    cpsMedian = 25f,
                    spectrum = emptyList(),
                    spectrumSeconds = 0,
                    seconds = 60,
                ),
                reference = fingerprintReference,
            ),
        )
        assertEquals("недостаточно статистики", thin.lines.single().value)

        val same = state(
            Fingerprint.compare(
                window = FingerprintWindow(
                    doseMedianMicroSvH = 0.16f,
                    cpsMedian = 25f,
                    spectrum = flatSpectrum,
                    spectrumSeconds = 3600,
                    seconds = 3600,
                ),
                reference = fingerprintReference,
            ),
        )
        assertEquals("изменение не обнаружено", same.lines.single().value)
    }

    private val flatSpectrum: List<Int> = List(1024) { i -> (2_000.0 / (1.0 + i * 0.02)).toInt() }

    private val fingerprintReference = FingerprintReference(
        doseLowMicroSvH = 0.14f,
        doseMedianMicroSvH = 0.15f,
        doseHighMicroSvH = 0.17f,
        cpsLow = 20f,
        cpsMedian = 25f,
        cpsHigh = 30f,
        spectrum = flatSpectrum,
        spectrumSeconds = 72 * 3600L,
        createdAtMillis = 0L,
        accumulatedSeconds = 72 * 3600L,
    )

    // --------------------------------------------- 14.md: три уровня глубины

    private fun textOf(sections: List<WhySection>): List<String> = sections.flatMap { section ->
        buildList {
            add(section.title)
            section.note?.let { add(it) }
            section.lines.forEach {
                add(it.label)
                add(it.value)
                it.note?.let { note -> add(note) }
            }
        }
    }

    /**
     * Первый экран отвечает на вопрос «что это значит», а не «как это
     * посчитано»: ни χ², ни z, ни MAD, ни формул, ни числа корзин.
     */
    @Test
    fun `the first level carries no formulas`() {
        val report = WhyReportBuilder.build(
            input(fingerprint = changedFingerprint),
        )
        val plain = textOf(report.sections(WhyLevel.PLAIN))
        val forbidden = listOf("χ²", "z =", "MAD", "median(|", "√", "корзин", "P25", "1σ")
        for (text in plain) {
            for (token in forbidden) {
                assertTrue(!text.contains(token), "«$token» на первом уровне: $text")
            }
        }
        // Зато на нём есть ответ, объём данных и спектр одной фразой.
        val titles = report.sections(WhyLevel.PLAIN).map { it.title }
        assertEquals(
            listOf(
                "Сейчас",
                "Сравнение с профилем",
                "Сколько данных",
                "Обычный фон",
                "Спектральное сравнение",
            ),
            titles,
        )
    }

    /**
     * Ни одно число не исчезло — оно переехало глубже. Числа второго и
     * третьего уровня перечислены поимённо: молчаливая пропажа величины и
     * есть та регрессия, ради которой этот тест написан.
     */
    @Test
    fun `nothing is lost on the way down`() {
        val report = WhyReportBuilder.build(input(fingerprint = changedFingerprint))

        val method = textOf(report.sections(WhyLevel.METHOD))
        assertTrue(method.contains("Медиана"), "$method")
        assertTrue(method.contains("P25–P75"), "$method")
        assertTrue(method.contains("P10–P90"), "$method")
        assertTrue(method.contains("Абсолютный порог L1"), "$method")
        // Точная нотация положения не пропала — она на научном уровне.
        assertTrue(method.contains("внутри P10–P90"), "$method")
        // «Измерений: 82 800» с честной подписью (14.md §3).
        val measurements = report.sections(WhyLevel.METHOD)
            .flatMap { it.lines }.single { it.label == "Измерений" }
        assertEquals("82 800", measurements.value)
        assertTrue(
            assertNotNull(measurements.note).startsWith("показаний прибора"),
            measurements.note!!,
        )
        assertTrue(assertNotNull(measurements.note).contains("при пропусках"), measurements.note!!)

        val expert = report.sections(WhyLevel.EXPERT).single()
        val labels = expert.lines.map { it.label }
        assertTrue(labels.contains("MAD"), "$labels")
        // «корзина» — структура хранения; на экране интервал называется
        // тем, чем он является для человека, — минутой (§3).
        assertTrue(labels.contains("Минутных интервалов"), "$labels")
        // χ² и z со спектрального сравнения — тоже здесь, а не на первом.
        val shape = expert.lines.single { it.label == "Статистика сравнения формы" }
        assertTrue(shape.value.contains("χ²"), shape.value)
        assertTrue(shape.value.contains("z ="), shape.value)
        // Пуассоновская формула и бюджет неопределённости дозы.
        val note = assertNotNull(expert.note)
        assertTrue(note.contains("√(N/τ)"), note)
        assertTrue(note.contains("не полная неопределённость измерения"), note)
    }

    /** ±% рядом с дозой обязана называть, ЧЬЯ она (14.md §7). */
    @Test
    fun `the plus-minus next to the dose names the instrument`() {
        val now = WhyReportBuilder.build(input()).sections.first { it.title == "Сейчас" }
        val dose = now.lines.first()
        val note = assertNotNull(dose.note)
        assertTrue(note.contains("собственная оценка прибора"), note)
        assertTrue(note.contains("для этого показания"), note)
        // Полный бюджет неопределённости назван отдельно и глубже — выдавать
        // одну составляющую за всю неопределённость нельзя.
        assertTrue(!note.contains("калибровка"), note)
    }

    /** Счёт объясняется физическим смыслом, а не геометрией источника. */
    @Test
    fun `the count rate is explained by what it measures`() {
        val now = WhyReportBuilder.build(input()).sections.first { it.title == "Сейчас" }
        val note = assertNotNull(now.lines.single { it.label == "Скорость счёта" }.note)
        assertTrue(note.contains("не показывает дозу"), note)
        assertTrue(note.contains("энергии"), note)
        assertTrue(!note.contains("далёк"), note)
    }

    private val changedFingerprint: FingerprintComparison
        get() = Fingerprint.compare(
            window = FingerprintWindow(
                doseMedianMicroSvH = 0.16f,
                cpsMedian = 25f,
                spectrum = flatSpectrum.mapIndexed { i, v -> if (i in 300..340) v * 6 else v },
                spectrumSeconds = 3600,
                seconds = 3600,
            ),
            reference = fingerprintReference,
        )

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

    // ------------------------------------------------- первый уровень справки

    /**
     * Справка отвечает на три вопроса раньше, чем начинает объяснять.
     *
     * Ход сбора фона ушёл сюда с Главной, и он обязан быть здесь: иначе строка
     * просто исчезла бы из приложения. «Недостаточно данных» — это состояние
     * СРАВНЕНИЯ, а не значение измерения, поэтому у него свой блок.
     */
    @Test
    fun `справка показывает сбор фона места`() {
        val report = WhyReportBuilder.build(
            input(
                status = MonitorStatus.Fixed(above = false, thresholdMicroSvH = 0.30f),
                baselineState = BaselineState.Learning(5_400, 10_800),
            ),
        )
        val learning = assertNotNull(report.learning)
        assertEquals("1,5 ч", learning.collected)
        assertEquals("3 ч", learning.required)
        assertEquals(0.5f, learning.fraction)
        // Пока фона нет, сравнение честно говорит, что его не с чем делать.
        assertEquals(MonitorRu.comparisonNotEnough, report.comparison)
    }

    @Test
    fun `с собранным фоном сравнение называет вывод, а не отсутствие данных`() {
        val report = WhyReportBuilder.build(input())
        assertNull(report.learning)
        assertEquals(statusHeadline(MonitorStatus.Usual(baseline)), report.comparison)
    }

    @Test
    fun `исключённое время — одна строка, а не три повтора`() {
        val report = WhyReportBuilder.build(
            input(
                admission = Admission.Excluded(BaselineExclusion.EXPERIMENT),
                exclusions = listOf(
                    ExclusionSummary(BaselineExclusion.EXPERIMENT, seconds = 720),
                ),
            ),
        )
        val line = assertNotNull(report.excluded)
        assertTrue(line.contains("12 мин"), line)
        // Причина названа один раз: раньше она стояла и заголовком, и строкой,
        // и значением.
        val reason = BaselineExclusion.EXPERIMENT.wording(MonitorRu)
        assertEquals(1, line.split(reason).size - 1, line)
    }
}
