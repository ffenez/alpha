package app.radiacode.ui.logic

import app.radiacode.ui.text.SearchRu
import app.radiacode.ui.text.SearchStrings
import app.radiacode.ui.text.Strings
import java.util.Locale

/** What the «i» card of Поиск is asked to explain at this moment. */
data class SearchInfoInput(
    /** True while «Наведение» is the selected mode. */
    val navigating: Boolean,
    val feedback: SearchFeedbackMode,
    /** Current window lengths, s; null while they are not known yet. */
    val fastSeconds: Double? = null,
    val localSeconds: Double? = null,
    /**
     * What the channel is doing at this instant («≈ 640 Гц», «0,4 с»), already
     * formatted; null when the channel has no such value. It is shown only
     * inside the card — a channel that announces its own pitch permanently is
     * describing itself, not the field.
     */
    val channelNow: String? = null,
)

/**
 * The «i» card of Поиск: everything that explains the screen but does not
 * measure anything.
 *
 * The screen used to carry three permanent explanations above the first number
 * — which question the mode answers, which feedback channel is chosen, why the
 * channel is silent — and they pushed the actual measurement below the fold.
 * They are still worth saying; they are just not worth saying **all the time**,
 * so they live here, one tap away.
 *
 * Two things are said here and nowhere else, and they are the reason this is a
 * pure function with a test rather than a block of Compose: the mode's own
 * limit («изменение счёта, а не расположение источника») is present in every
 * state of the screen, and the decision windows are named with their current
 * lengths — a direction that never names the windows it was decided on is a
 * word, not a measurement.
 */
object NavigateInfo {

    /** One explained thing: a short title and one body paragraph. */
    data class Row(val title: String, val body: String)

    fun rows(
        input: SearchInfoInput,
        strings: Strings,
        t: SearchStrings = SearchRu,
    ): List<Row> {
        val rows = ArrayList<Row>(6)
        rows += Row(
            title = if (input.navigating) t.modeNavigate else t.modeVerify,
            body = if (input.navigating) t.navHint else t.verifyHint,
        )
        if (input.navigating) {
            val fast = input.fastSeconds
            val local = input.localSeconds
            if (fast != null && local != null) {
                rows += Row(
                    title = t.infoWindowsTitle,
                    body = "${t.navWindows(num1(fast), num1(local))} · ${t.infoWindowsNote}",
                )
            } else {
                rows += Row(title = t.infoWindowsTitle, body = t.infoWindowsNote)
            }
            rows += Row(title = t.infoScaleTitle, body = t.navScaleTitle)
            rows += Row(title = t.infoTraceTitle, body = t.navTraceLegend)
        }
        if (!input.navigating) {
            // Подписи ленты уехали сюда с экрана: величина названа осью, а
            // «полоса — ожидаемые колебания фона» читается один раз.
            rows += Row(title = t.infoTapeTitle, body = "${t.tapeTitle} · ${t.bandNote}")
        }
        rows += Row(title = t.infoFeedbackTitle, body = channelBody(input, strings, t))
        rows += Row(title = t.infoLimitTitle, body = t.infoLimit)
        return rows
    }

    /**
     * Which channel is on and what its signal means — the line that used to sit
     * under the segment permanently, including the «канал выбирается в
     * Настройках» that belongs to a setting, not to a measurement.
     */
    private fun channelBody(
        input: SearchInfoInput,
        strings: Strings,
        t: SearchStrings,
    ): String {
        val hint = when (input.feedback) {
            SearchFeedbackMode.OFF -> t.feedbackOffNote
            SearchFeedbackMode.CLICKS -> strings.feedbackClicks
            SearchFeedbackMode.TONE -> if (input.navigating) t.navToneHint else t.toneHint
            SearchFeedbackMode.VIBRO -> if (input.navigating) t.navVibroHint else t.vibroHint
        }
        val now = input.channelNow ?: return hint
        return hint + t.currently(now)
    }

    private fun num1(value: Double): String =
        String.format(Locale.US, "%.1f", value).replace('.', ',')
}
