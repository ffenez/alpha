package app.radiacode.ui.logic

import app.radiacode.baseline.BaselineExclusion
import app.radiacode.data.ExclusionSummary
import app.radiacode.data.SessionAdmission
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryFormatTest {

    @Test
    fun `duration scales units`() {
        assertEquals("45 с", HistoryFormat.duration(45))
        assertEquals("12 мин", HistoryFormat.duration(12 * 60 + 30))
        assertEquals("8 ч 12 мин", HistoryFormat.duration(8 * 3600 + 12 * 60))
        assertEquals("2 ч", HistoryFormat.duration(2 * 3600 + 5))
        assertEquals("0 с", HistoryFormat.duration(-10))
    }

    @Test
    fun `day time hides the current year and shows others`() {
        val utc = ZoneOffset.UTC
        // 2026-08-12 14:02 UTC.
        val millis = 1_786_543_320_000L
        assertEquals("12 авг 14:02", HistoryFormat.dayTime(millis, millis, utc))
        // Viewed a year later the year appears.
        val nextYear = millis + 365L * 24 * 3600_000
        assertEquals("12 авг 2026 14:02", HistoryFormat.dayTime(millis, nextYear, utc))
    }

    @Test
    fun `count groups thousands`() {
        assertEquals("0", HistoryFormat.count(0))
        assertEquals("999", HistoryFormat.count(999))
        assertEquals("29 520", HistoryFormat.count(29_520))
        assertEquals("1 234 567", HistoryFormat.count(1_234_567))
    }

    @Test
    fun `session journal always says whether it taught the baseline`() {
        assertEquals(
            "в обычный фон: да",
            HistoryFormat.admissionLine(SessionAdmission(3_600, emptyList())),
        )
        assertEquals(
            "в обычный фон: нет измерений",
            HistoryFormat.admissionLine(SessionAdmission.EMPTY),
        )
    }

    @Test
    fun `an excluded session names the dominating reason`() {
        val line = HistoryFormat.admissionLine(
            SessionAdmission(
                admittedSeconds = 0,
                exclusions = listOf(
                    ExclusionSummary(BaselineExclusion.EXPERIMENT, 1_200),
                    ExclusionSummary(BaselineExclusion.STREAM_STALE, 30),
                ),
            ),
        )
        assertEquals("в обычный фон: нет — идёт Поиск или эксперимент", line)
    }

    @Test
    fun `a partly excluded session reports how much was left out`() {
        val line = HistoryFormat.admissionLine(
            SessionAdmission(
                admittedSeconds = 3_000,
                exclusions = listOf(ExclusionSummary(BaselineExclusion.QUARANTINE, 600)),
            ),
        )
        assertEquals(
            "в обычный фон: частично · вне обучения 10 мин — карантин после отклонения",
            line,
        )
    }
    @Test
    fun `dose projection wording states the condition and refuses the annual-dose claim`() {
        val sentence = HistoryFormat.doseProjectionSentence("1 310 мкЗв")
        assertEquals(
            "если средняя измеренная внешняя фотонная мощность дозы останется такой же — " +
                "за год ≈ 1 310 мкЗв",
            sentence,
        )
        assertTrue(sentence.startsWith("если"), "the condition comes first, not the number")
        assertFalse(sentence.contains("годовая"), "it is not an annual dose")
        assertFalse(sentence.contains("эффективная"))
        assertFalse(sentence.contains("вы получите"))
    }

    @Test
    fun `dose projection basis names the mean rate and the measured time`() {
        assertEquals(
            "средняя измеренная мощность 0,13 мкЗв/ч за 26 ч измерений",
            HistoryFormat.doseProjectionBasis("0,13 мкЗв/ч", 26 * 3600L),
        )
    }

    @Test
    fun `dose projection caveat lists what is not included`() {
        val caveat = HistoryFormat.DOSE_PROJECTION_CAVEAT
        assertTrue(caveat.contains("не годовая эффективная доза"))
        assertTrue(caveat.contains("радон"))
        assertTrue(caveat.contains("внутреннее"))
    }

    @Test
    fun `too little measurement is said out loud`() {
        assertEquals(
            "измерений пока мало (12 мин) — за год пересчитывать не из чего",
            HistoryFormat.doseProjectionUnavailable(720),
        )
    }
}
