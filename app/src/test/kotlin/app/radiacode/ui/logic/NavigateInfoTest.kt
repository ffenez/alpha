package app.radiacode.ui.logic

import app.radiacode.ui.text.EnStrings
import app.radiacode.ui.text.RuStrings
import app.radiacode.ui.text.SearchEn
import app.radiacode.ui.text.SearchRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Что именно уехало под «i».
 *
 * Пояснения убраны с экрана, но НЕ выброшены: тест держит границу между
 * «объяснение не стоит над первым числом» и «объяснения больше нет». Отдельно
 * проверяется, что окна решения названы своими длинами — направление без
 * названных окон это слово, а не измерение.
 */
class NavigateInfoTest {

    private fun rows(input: SearchInfoInput) = NavigateInfo.rows(input, RuStrings, SearchRu)

    @Test
    fun `the question of the mode is explained here and depends on the mode`() {
        val navigate = rows(SearchInfoInput(navigating = true, feedback = SearchFeedbackMode.OFF))
        assertEquals(SearchRu.modeNavigate, navigate.first().title)
        assertEquals(SearchRu.navHint, navigate.first().body)
        val verify = rows(SearchInfoInput(navigating = false, feedback = SearchFeedbackMode.OFF))
        assertEquals(SearchRu.modeVerify, verify.first().title)
        assertEquals(SearchRu.verifyHint, verify.first().body)
    }

    @Test
    fun `the decision windows are named with their current lengths`() {
        val rows = rows(
            SearchInfoInput(
                navigating = true,
                feedback = SearchFeedbackMode.CLICKS,
                fastSeconds = 1.8,
                localSeconds = 16.0,
            ),
        )
        val windows = rows.first { it.title == SearchRu.infoWindowsTitle }
        assertTrue(windows.body.contains("1,8"), windows.body)
        assertTrue(windows.body.contains("16,0"), windows.body)
    }

    /** Окна ещё не построены — строка остаётся, число не выдумывается. */
    @Test
    fun `unknown windows are not invented`() {
        val rows = rows(SearchInfoInput(navigating = true, feedback = SearchFeedbackMode.OFF))
        val windows = rows.first { it.title == SearchRu.infoWindowsTitle }
        assertEquals(SearchRu.infoWindowsNote, windows.body)
    }

    /** «Проверке» дуга и лента «Наведения» не принадлежат. */
    @Test
    fun `the guidance rows belong to the guidance mode only`() {
        val verify = rows(SearchInfoInput(navigating = false, feedback = SearchFeedbackMode.TONE))
        assertTrue(verify.none { it.title == SearchRu.infoScaleTitle })
        assertTrue(verify.none { it.title == SearchRu.infoTraceTitle })
        assertTrue(verify.none { it.title == SearchRu.infoWindowsTitle })
    }

    @Test
    fun `the feedback row follows the chosen channel and its current value`() {
        val off = rows(SearchInfoInput(navigating = true, feedback = SearchFeedbackMode.OFF))
            .first { it.title == SearchRu.infoFeedbackTitle }
        assertEquals(SearchRu.feedbackOffNote, off.body)
        val tone = rows(
            SearchInfoInput(
                navigating = true,
                feedback = SearchFeedbackMode.TONE,
                channelNow = "≈ 640 Гц",
            ),
        ).first { it.title == SearchRu.infoFeedbackTitle }
        assertTrue(tone.body.startsWith(SearchRu.navToneHint), tone.body)
        assertTrue(tone.body.contains("640"), tone.body)
    }

    /**
     * Граница режима стоит в КАЖДОМ состоянии карточки: она не зависит ни от
     * режима, ни от канала, ни от того, набралась ли статистика.
     */
    @Test
    fun `the limit of the mode is always present in both languages`() {
        for (navigating in listOf(true, false)) {
            for (feedback in SearchFeedbackMode.entries) {
                val ru = rows(SearchInfoInput(navigating, feedback))
                assertTrue(ru.any { it.body == SearchRu.infoLimit }, "$navigating $feedback")
                val en = NavigateInfo.rows(
                    SearchInfoInput(navigating, feedback),
                    EnStrings,
                    SearchEn,
                )
                assertTrue(en.any { it.body == SearchEn.infoLimit }, "$navigating $feedback")
            }
        }
        assertTrue(SearchRu.infoLimit.contains("не расположение источника"))
        assertTrue(SearchEn.infoLimit.contains("not the location of a source"))
    }
}
