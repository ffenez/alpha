package app.alpha.ui.logic

import app.alpha.analysis.ShapeChange
import app.alpha.analysis.SpectrumEdge
import app.alpha.analysis.ShapeComparison
import app.alpha.analysis.ShapeVerdict
import app.alpha.analysis.SpectrogramSlice
import app.alpha.ui.text.SearchRu
import app.alpha.ui.text.SearchStrings

/**
 * «Изменился не только счёт, но и форма спектра — открыть спектр?»
 * (search redesign §13).
 *
 * A separate, research-level observation that is deliberately kept apart from
 * the count-rate verdict: it is computed only **while an excursion is
 * confirmed**, it compares the spectrum accumulated during that excursion with
 * the spectrum of the couple of minutes before it, and the most it can produce
 * is an invitation to open the Спектр tab. No nuclide, no «источник найден» —
 * that would need peaks and statistics this comparison does not have (§12).
 *
 * The slices come from the waterfall ring the service already fills at the 5 s
 * spectrum poll, so nothing extra is measured for this — the Поиск screen only
 * has to keep that poll attached while it is open.
 */
object SearchSpectrumHint {

    /**
     * How much «before» is compared with the excursion.
     *
     * **Engineering parameter.** Two minutes is long enough to accumulate a
     * usable reference at ordinary background rates and short enough that it
     * still describes the same place — a longer window would quietly average
     * over the walk that led here.
     */
    const val REFERENCE_MILLIS = 120_000L

    /**
     * Shape comparison for the excursion that started at
     * [excursionStartMillis] (wall clock), or null when there is nothing to
     * compare yet — no excursion, or no spectrum slices on one of the sides.
     */
    fun compare(
        slices: List<SpectrogramSlice>,
        excursionStartMillis: Long?,
        nowMillis: Long,
    ): ShapeComparison? {
        if (excursionStartMillis == null || slices.isEmpty()) return null
        val reference = sum(
            slices.filter {
                it.timestampMillis < excursionStartMillis &&
                    it.timestampMillis >= excursionStartMillis - REFERENCE_MILLIS
            },
        ) ?: return null
        val excursion = sum(
            slices.filter {
                it.timestampMillis >= excursionStartMillis && it.timestampMillis <= nowMillis
            },
        ) ?: return null
        if (reference.size != excursion.size) return null
        // Крайний канал — граница шкалы, а не форма спектра ([SpectrumEdge]):
        // всплеск в нём не является изменением формы.
        return ShapeChange.compare(
            SpectrumEdge.withoutEdge(reference),
            SpectrumEdge.withoutEdge(excursion),
        )
    }

    /** The invitation itself; null unless the shapes really differ. */
    fun invitation(comparison: ShapeComparison?, t: SearchStrings = SearchRu): String? {
        if (comparison == null || comparison.verdict != ShapeVerdict.CHANGED) return null
        return t.shapeInvitation
    }

    /**
     * The quiet line under it. Present for every state of the comparison,
     * including «данных мало» — a section that appears and disappears without
     * saying why is worse than one that admits it is waiting.
     */
    fun note(comparison: ShapeComparison?, t: SearchStrings = SearchRu): String? =
        when (comparison?.verdict) {
            null -> null
            ShapeVerdict.NOT_ENOUGH_DATA -> t.shapeNotEnough(ShapeChange.detail(comparison))
            ShapeVerdict.CONSISTENT -> t.shapeConsistent(ShapeChange.detail(comparison))
            ShapeVerdict.CHANGED -> t.shapeChanged(ShapeChange.detail(comparison))
        }

    private fun sum(slices: List<SpectrogramSlice>): DoubleArray? {
        if (slices.isEmpty()) return null
        val bands = slices.first().bandCounts.size
        if (slices.any { it.bandCounts.size != bands }) return null
        val total = DoubleArray(bands)
        for (slice in slices) {
            for (i in 0 until bands) total[i] += slice.bandCounts[i].toDouble()
        }
        return total
    }
}
