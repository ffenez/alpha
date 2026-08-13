package app.radiacode.ui.logic

import app.radiacode.ui.text.HistoryRu
import app.radiacode.ui.text.HistoryStrings

/**
 * What the user has ticked in История while the selection mode is on.
 *
 * Sessions and spectra are selected in one mode on purpose: they live in the
 * same list, and «убрать лишнее» is one job, not two.
 */
data class HistorySelection(
    val active: Boolean = false,
    val sessions: Set<Long> = emptySet(),
    val spectra: Set<Long> = emptySet(),
) {
    val count: Int get() = sessions.size + spectra.size
    val isEmpty: Boolean get() = count == 0

    fun toggleSession(id: Long): HistorySelection =
        copy(sessions = if (id in sessions) sessions - id else sessions + id)

    fun toggleSpectrum(id: Long): HistorySelection =
        copy(spectra = if (id in spectra) spectra - id else spectra + id)

    fun start(): HistorySelection = HistorySelection(active = true)

    fun cancel(): HistorySelection = HistorySelection()

    /**
     * «Выбрать всё» и «снять всё» одним действием.
     *
     * Отмечать двадцать сессий по одной, чтобы удалить их все, — работа,
     * которую человек делать не обязан. Кнопка одна и переключается по тому,
     * выбрано ли уже всё: отдельные «выбрать всё» и «снять всё» рядом
     * заставляли бы читать, какая из них сейчас нужна.
     *
     * Идущая сессия в список не попадает — её нельзя удалить, и «выбрано 13»
     * при двенадцати удаляемых было бы неправдой.
     */
    fun toggleAll(sessionIds: Collection<Long>, spectrumIds: Collection<Long>): HistorySelection {
        val allSelected = sessions.containsAll(sessionIds) && spectra.containsAll(spectrumIds) &&
            count == sessionIds.size + spectrumIds.size
        return if (allSelected) {
            copy(sessions = emptySet(), spectra = emptySet())
        } else {
            copy(sessions = sessionIds.toSet(), spectra = spectrumIds.toSet())
        }
    }

    /** Выбрано ли всё, что вообще можно выбрать. */
    fun isAllSelected(sessionIds: Collection<Long>, spectrumIds: Collection<Long>): Boolean =
        count == sessionIds.size + spectrumIds.size &&
            sessions.containsAll(sessionIds) &&
            spectra.containsAll(spectrumIds)
}

/**
 * Exactly what a deletion will take away, counted **before** it happens.
 *
 * The point of counting first is the confirmation: deleting a session deletes
 * the measurements inside it, and «3 сессии» hides that far better than
 * «3 сессии · 41 200 измерений» does.
 */
data class DeletionPlan(
    val sessions: Int,
    /** Raw 1 Hz measurements inside the selected sessions. */
    val samples: Long,
    /** Deviation events inside them — records about data that is going away. */
    val events: Int,
    val spectra: Int,
    /** Total measurement time of the selected sessions, seconds. */
    val seconds: Long,
) {
    val isEmpty: Boolean get() = sessions == 0 && spectra == 0
}

/**
 * The wording of deletion (design language: a button is named by its action,
 * a confirmation says what will actually happen).
 *
 * Deleting measurements is the one place in the app where data really does
 * disappear — everywhere else «исключено» means «не участвует в статистике, но
 * записано». So the confirmation names the numbers, says what is **not**
 * touched, and never softens it into «очистить».
 */
object HistoryDeletion {

    fun actionLabel(selection: HistorySelection, s: HistoryStrings = HistoryRu): String =
        if (selection.isEmpty) s.delete else s.deleteCount(selection.count)

    fun title(plan: DeletionPlan, s: HistoryStrings = HistoryRu): String = when {
        plan.sessions > 0 && plan.spectra > 0 -> s.deleteSelectedTitle
        plan.spectra > 0 -> s.deleteSpectraTitle(plan.spectra)
        else -> s.deleteSessionsTitle(plan.sessions)
    }

    /** The full account of what goes, in the order of how much it matters. */
    fun body(plan: DeletionPlan, s: HistoryStrings = HistoryRu): String {
        val parts = ArrayList<String>(4)
        if (plan.sessions > 0) {
            parts += s.sessionsWithDuration(
                sessions = s.sessions(plan.sessions),
                duration = durationWording(plan.seconds),
            )
            parts += s.samplesGone(count(plan.samples))
            if (plan.events > 0) parts += s.eventsInside(s.events(plan.events))
        }
        if (plan.spectra > 0) parts += s.spectraFromList(s.spectra(plan.spectra))
        return parts.joinToString("\n") { "· $it" }
    }

    /**
     * What survives. Said out loud because the alternative is the user
     * discovering it later and not knowing which is true.
     */
    fun keepsWording(plan: DeletionPlan, s: HistoryStrings = HistoryRu): String = buildString {
        append(s.cannotUndo)
        if (plan.sessions > 0) append(s.tracksAndSpectraStay) else append(s.measurementsUntouched)
    }

    fun emptyHint(s: HistoryStrings = HistoryRu): String = s.markWhatToDelete

    /**
     * Digit-group separator: a **no-break** space, so «41 203 записей» can
     * never wrap between the thousands. A plain space would let the line break
     * inside a number, which is exactly where a reader must not stumble.
     */
    const val GROUP_SEPARATOR = '\u00A0'

    /** «41 203» — grouped, so a big number is read rather than counted. */
    fun count(value: Long): String {
        val text = value.toString()
        val out = StringBuilder()
        for ((index, ch) in text.withIndex()) {
            if (index > 0 && (text.length - index) % 3 == 0) out.append(GROUP_SEPARATOR)
            out.append(ch)
        }
        return out.toString()
    }
}
