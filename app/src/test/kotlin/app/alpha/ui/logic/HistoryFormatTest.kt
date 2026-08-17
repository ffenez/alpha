package app.alpha.ui.logic

import app.alpha.baseline.BaselineExclusion
import app.alpha.data.ExclusionSummary
import app.alpha.data.SessionAdmission
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
            "учтено в обычном фоне",
            HistoryFormat.admissionLine(SessionAdmission(3_600, emptyList())),
        )
        assertEquals(
            "измерений нет",
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
        assertEquals("не учтено в обычном фоне: идёт Поиск или эксперимент", line)
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
            "в обычный фон: не всё (10 мин — недавно было отклонение уровня)",
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
    fun `dose projection basis puts the observation time first`() {
        assertEquals(
            "по 26 ч измерений · средняя 0,130 мкЗв/ч",
            HistoryFormat.doseProjectionBasis("0,130 мкЗв/ч", 26 * 3600L),
        )
    }

    /**
     * ТЗ §13: под проекцией остаётся ОДНО короткое предупреждение. Сокращается
     * перечисление того, что не входит, а не сам отказ — и отказ обязан
     * оставаться отказом на первом уровне, без «i».
     */
    @Test
    fun `the short caveat keeps the refusal on the first level`() {
        val short = HistoryFormat.doseProjectionCaveatShort()
        assertTrue(short.contains("не годовая эффективная доза"), short)
        assertTrue(short.contains("внешнего гамма-фона"), short)
        assertFalse(short.contains("радон"), "перечень уехал в справку: $short")
        assertTrue(short.length < HistoryFormat.doseProjectionCaveat().length, short)
    }

    @Test
    fun `dose projection caveat lists what is not included`() {
        val caveat = HistoryFormat.doseProjectionCaveat()
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

    /**
     * «2,36 сегодня · 2,36 за 7 д · 2,36 за 30 д» — три одинаковых числа
     * читаются как поломка, хотя всё верно: истории пятнадцать часов. Период
     * показывается, только если ДО него измерения были.
     */
    @Test
    fun `a period is offered only when there is history behind it`() {
        fun days(measured: List<Long>) = measured.map { DailyDose.Day(1f, it) }

        // Измерения только сегодня: недели и месяца ещё нет.
        assertEquals(0, DailyDose.measuredDepthDays(days(listOf(0, 0, 0, 0, 0, 0, 3_600))))
        // Вчера и сегодня: неделя уже что-то добавляет, месяц — ещё нет.
        assertEquals(1, DailyDose.measuredDepthDays(days(listOf(0, 0, 0, 0, 0, 3_600, 3_600))))
        assertEquals(6, DailyDose.measuredDepthDays(days(List(7) { 3_600L })))
        // Пустая история ничего не предлагает.
        assertEquals(0, DailyDose.measuredDepthDays(emptyList()))
        assertEquals(0, DailyDose.measuredDepthDays(days(listOf(0, 0, 0))))
    }
}
