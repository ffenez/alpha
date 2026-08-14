package app.radiacode.ui.logic

import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineExclusion
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.DeviationSnapshot
import app.radiacode.baseline.alarmThresholds
import app.radiacode.data.DoseUnitSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonitorStatusTest {

    private val thresholds = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f)
    private val baseline = Baseline(
        doseLowMicroSvH = 0.09f,
        doseMedianMicroSvH = 0.11f,
        doseHighMicroSvH = 0.14f,
        doseP25MicroSvH = 0.10f,
        doseP75MicroSvH = 0.13f,
        doseMadMicroSvH = 0.01f,
        cpsLow = 18f,
        cpsMedian = 22f,
        cpsHigh = 27f,
        accumulatedSeconds = 26 * 3600L,
        sampleCount = 26 * 3600L,
        bucketCount = 1560,
    )
    private val active = BaselineState.Active(baseline)
    private val calm = DeviationSnapshot()

    @Test
    fun `no reading - unknown regardless of baseline`() {
        val status = MonitorStatus.of(null, active, calm, thresholds, nowMillis = 0)
        assertEquals(MonitorStatus.Unknown, status)
        assertEquals("Нет измерений", statusHeadline(status))
        assertNull(statusDetail(status, DoseUnitSetting.MICRO_SIEVERT))
    }

    @Test
    fun `no baseline - fixed threshold fallback`() {
        val below = MonitorStatus.of(0.12f, null, calm, thresholds, 0)
        assertEquals(MonitorStatus.Fixed(above = false, thresholdMicroSvH = 0.30f), below)
        assertEquals("Ниже вашего порога", statusHeadline(below))
        // The reference is shown even before a baseline exists (spec §18).
        assertEquals(
            "ваш порог 0,30 мкЗв/ч · здесь пока мало измерений",
            statusDetail(below, DoseUnitSetting.MICRO_SIEVERT),
        )

        val above = MonitorStatus.of(0.35f, null, calm, thresholds, 0)
        assertEquals(MonitorStatus.Fixed(above = true, thresholdMicroSvH = 0.30f), above)
        assertEquals("Выше вашего порога", statusHeadline(above))
        assertTrue(
            statusDetail(above, DoseUnitSetting.MICRO_SIEVERT)!!.startsWith("ваш порог 0,30 мкЗв/ч"),
        )
    }

    @Test
    fun `learning baseline also falls back to fixed threshold`() {
        val learning = BaselineState.Learning(3600, 10800)
        val status = MonitorStatus.of(0.12f, learning, calm, thresholds, 0)
        assertIs<MonitorStatus.Fixed>(status)
    }

    @Test
    fun `active baseline - usual wording with the place band`() {
        val status = MonitorStatus.of(0.12f, active, calm, thresholds, 0)
        assertEquals(MonitorStatus.Usual(baseline), status)
        // 14.md §8: «обычны», а не «нормальны» — «норма» читается как
        // санитарная норма, а это статистика конкретного места.
        assertEquals("Обычно здесь", statusHeadline(status))
        assertEquals("Обычно здесь", statusHeadlineShort(status))
        assertEquals(
            "обычно здесь 0,09–0,14 мкЗв/ч",
            statusDetail(status, DoseUnitSetting.MICRO_SIEVERT),
        )
    }

    @Test
    fun `above usual needs dwell - short excursion still reads usual`() {
        val now = 1_000_000L
        val brief = DeviationSnapshot(aboveUsualSince = now - 30_000)
        val status = MonitorStatus.of(0.18f, active, brief, thresholds, now)
        assertIs<MonitorStatus.Usual>(status)
    }

    @Test
    fun `above usual after dwell carries the held duration`() {
        val now = 1_000_000L
        val held = DeviationSnapshot(aboveUsualSince = now - 4 * 60_000)
        val status = MonitorStatus.of(0.18f, active, held, thresholds, now)
        assertEquals(MonitorStatus.AboveUsual(baseline, heldSeconds = 240), status)
        assertEquals("Выше обычного", statusHeadline(status))
        assertEquals(
            "обычно здесь 0,09–0,14 мкЗв/ч · уже 4 мин",
            statusDetail(status, DoseUnitSetting.MICRO_SIEVERT),
        )
    }

    @Test
    fun `persistent alert wins over everything`() {
        val now = 1_000_000L
        val alert = DeviationSnapshot(aboveUsualSince = now - 300_000, alertSince = now - 240_000)
        val status = MonitorStatus.of(0.31f, active, alert, thresholds, now)
        assertEquals(
            MonitorStatus.Alert(baseline, heldSeconds = 240, thresholdMicroSvH = 0.30f),
            status,
        )
        assertEquals("Уровень изменился", statusHeadline(status))
        assertEquals(
            "обычно здесь 0,09–0,14 мкЗв/ч · уже 4 мин",
            statusDetail(status, DoseUnitSetting.MICRO_SIEVERT),
        )
    }

    @Test
    fun `alert without baseline names the threshold`() {
        val now = 1_000_000L
        val alert = DeviationSnapshot(alertSince = now - 130_000)
        val status = MonitorStatus.of(0.35f, null, alert, thresholds, now)
        assertEquals(
            "ваш порог 0,30 мкЗв/ч · уже 2 мин",
            statusDetail(status, DoseUnitSetting.MICRO_SIEVERT),
        )
    }

    @Test
    fun `held wording scales units`() {
        assertEquals("уже 45 с", heldWording(45))
        assertEquals("уже 4 мин", heldWording(255))
        assertEquals("уже 1 ч 12 мин", heldWording(4320))
    }

    @Test
    fun `learning and collected wording`() {
        assertEquals(
            "изучаю обычный фон — 1,5 ч из 3",
            learningWording(BaselineState.Learning(5400, 10800)),
        )
        assertEquals(
            "изучаю обычный фон — 0 ч из 3",
            learningWording(BaselineState.Learning(0, 10800)),
        )
        // «baseline» — имя движка, а не название величины на экране (§2).
        assertEquals("обычный фон собран за 26 ч наблюдений", baselineCollectedWording(baseline))
        assertEquals("26 ч", baselineCollectedShort(baseline))
    }

    /**
     * CHART SPEC §18/§39: the main status is descriptive, never normative.
     * These words must not appear in any status wording — a historical
     * percentile of one place is not a safety statement.
     */
    @Test
    fun `no status ever claims a norm or safety`() {
        val statuses = listOf(
            MonitorStatus.Unknown,
            MonitorStatus.Fixed(above = false, thresholdMicroSvH = 0.30f),
            MonitorStatus.Fixed(above = true, thresholdMicroSvH = 0.30f),
            MonitorStatus.Usual(baseline),
            MonitorStatus.AboveUsual(baseline, heldSeconds = 240),
            MonitorStatus.Alert(baseline, heldSeconds = 240, thresholdMicroSvH = 0.30f),
            MonitorStatus.Alert(null, heldSeconds = 240, thresholdMicroSvH = 0.30f),
        )
        val forbidden = listOf("норма", "норме", "норму", "безопас", "допустим", "привычн")
        for (status in statuses) {
            val texts = listOfNotNull(
                statusHeadline(status),
                statusHeadlineShort(status),
                statusDetail(status, DoseUnitSetting.MICRO_SIEVERT),
            )
            for (text in texts) {
                for (word in forbidden) {
                    assertTrue(!text.lowercase().contains(word), "«$word» in «$text»")
                }
            }
        }
    }

    @Test
    fun `the status with a baseline always shows its reference`() {
        for (status in listOf(
            MonitorStatus.Usual(baseline),
            MonitorStatus.AboveUsual(baseline, heldSeconds = 60),
            MonitorStatus.Alert(baseline, heldSeconds = 60, thresholdMicroSvH = 0.3f),
        )) {
            val detail = statusDetail(status, DoseUnitSetting.MICRO_SIEVERT)!!
            assertTrue(detail.contains("обычно здесь"), detail)
            assertTrue(detail.contains("мкЗв/ч"), detail)
        }
    }

    @Test
    fun `the engine's internal name never reaches the screen`() {
        // «baseline» — имя движка. У величины на экране есть человеческое
        // название («обычный фон», «обычный диапазон профиля»), и
        // техническое рядом с ним только мешает.
        val texts = listOf(
            statusHeadline(MonitorStatus.Usual(baseline)),
            statusDetail(MonitorStatus.Usual(baseline), DoseUnitSetting.MICRO_SIEVERT).orEmpty(),
        ) + BaselineExclusion.entries.map { it.label }
        for (text in texts) {
            assertTrue(!text.lowercase().contains("baseline"), text)
        }
    }

    /**
     * Когда всё как обычно, сказать нечего.
     *
     * «Обычно здесь» висело под каждым показанием каждый день, и строка,
     * которая никогда не меняется, перестаёт читаться. Проверяется, что
     * молчит РОВНО это состояние: любое другое обязано говорить словами, иначе
     * молчание становится утаиванием.
     */
    @Test
    fun `only the usual state has nothing to say`() {
        val quiet = MonitorStatus.Usual(baseline)
        val loud = listOf(
            MonitorStatus.Unknown,
            MonitorStatus.Fixed(above = true, thresholdMicroSvH = 0.30f),
            MonitorStatus.AboveUsual(baseline, heldSeconds = 240),
            MonitorStatus.AboveThreshold(baseline, 45, 120, 0.30f),
            MonitorStatus.Alert(baseline, heldSeconds = 300, thresholdMicroSvH = 0.30f),
        )

        assertTrue(quiet is MonitorStatus.Usual)
        for (status in loud) {
            assertTrue(status !is MonitorStatus.Usual, "$status")
            assertTrue(statusHeadline(status).isNotBlank(), "$status")
        }
    }
}
