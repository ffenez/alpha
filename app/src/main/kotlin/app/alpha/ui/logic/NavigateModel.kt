package app.alpha.ui.logic

import app.alpha.analysis.CountWindow

/**
 * Which question the Поиск screen is answering right now.
 *
 * The two modes are **not** «точный» and «быстрый»: the second one is no less
 * exact, it answers a different question. «Проверка» asks «есть ли устойчивое
 * превышение над записанным фоном» and pays for that answer with a dwell time;
 * «Наведение» asks «куда вести прибор прямо сейчас» and compares the newest
 * counting window with the one just before it.
 */
enum class SearchMode(val id: String) {
    NAVIGATE("navigate"),
    VERIFY("verify"),
    ;

    companion object {
        fun of(id: String?): SearchMode = entries.firstOrNull { it.id == id } ?: VERIFY
    }
}

/**
 * The four states of «Наведение» — and there are exactly four.
 *
 * There is deliberately no second rung («сильный рост»): the statement «счёт
 * растёт» has one threshold, the one the exact test resolves, and saying it
 * louder would not be saying anything more. The magnitude travels as a ratio
 * with its interval, never as a bigger word.
 */
enum class NavigateTrend {
    /** Not enough counts yet, or the stream is not delivering. */
    COLLECTING,

    /** The two windows differ by no more than counting statistics allows. */
    NO_CHANGE,
    RISING,
    FALLING,
}

/**
 * «Точка отсчёта» — the counting window frozen by the «Запомнить здесь» button.
 *
 * It is a **session** reference of the current sweep and touches neither the
 * profile nor its ordinary background: pressing it must not rewrite what the
 * app knows about the place, only what this sweep is measured against.
 */
data class NavigateReference(
    val window: CountWindow,
    /** Instrument-clock instant the reference was taken at. */
    val atMillis: Long,
    /**
     * Модуль магнитного поля в момент отсчёта, мкТл; null — магнитометра нет
     * или он ещё ничего не дал. Хранится ЗДЕСЬ, а не отдельным состоянием:
     * поле сравнивается с той же точкой, что и счёт, и снимается вместе с ней.
     */
    val magneticUt: Float? = null,
) {
    val ratePerSecond: Double get() = window.ratePerSecond
}

/** Peak hold: the highest short-window rate since the last reset. */
data class NavigatePeak(
    val ratePerSecond: Double,
    /** Instrument-clock instant of that maximum. */
    val atMillis: Long,
)

/**
 * What can honestly be said about the current rate **relative to the точка
 * отсчёта** — with a printed percentage as only one of the four answers.
 *
 * «+31 %» at 25 s⁻¹ over a one-second window is ordinary counting noise, so a
 * percentage is printed only once the exact test has actually resolved a
 * difference; until then the screen shows a dash and names the reason. That is
 * the whole point of this type: an unresolved difference and a resolved one
 * must not be able to reach the screen looking the same.
 */
sealed interface ReferenceDelta {

    /** Nobody pressed «Запомнить здесь» yet. */
    data object NoReference : ReferenceDelta

    /**
     * A reference exists, but the current window still overlaps the interval
     * the reference itself was measured over (or carries too few counts), so
     * the two windows are not independent and no test may be run on them.
     */
    data object Collecting : ReferenceDelta

    /** The interval for the ratio still contains 1: no difference resolved. */
    data class Unresolved(val low: Double, val high: Double) : ReferenceDelta

    /** The interval sits entirely on one side of 1. */
    data class Resolved(
        val percent: Int,
        val ratio: Double,
        val low: Double,
        val high: Double,
    ) : ReferenceDelta
}

/**
 * Window lengths chosen from a **target relative counting error**, not from a
 * fixed number of seconds (proposal §«Как должен работать алгоритм»).
 *
 * For a Poisson count σ_N/N ≈ 1/√N, so a target error ε needs N ≈ 1/ε² events
 * and therefore t ≈ N/R seconds at the current rate R. At 25 s⁻¹ the short
 * window is then ~1.8 s and at 100 s⁻¹ it hits its floor — a brighter field
 * genuinely needs less time for the same precision, and that is exactly the
 * adaptation the mode needs while the instrument is moving.
 *
 * **Every constant here is an engineering parameter**, and there are no
 * user-facing «sensitivity presets» on purpose: a preset would be a second
 * name for the same number, and the number that matters is named here once.
 */
object NavigateWindows {

    /**
     * Target relative error of the **short** window: 15 % ⇒ ~44 events. Fast
     * enough to follow a hand sweeping a surface, slow enough that the arrow
     * is not driven by single events.
     */
    const val FAST_RELATIVE_ERROR = 0.15

    /** Target relative error of the **local** window: 5 % ⇒ ~400 events. */
    const val LOCAL_RELATIVE_ERROR = 0.05

    /** Clamps, s. Below the floor the window stops being a measurement. */
    const val MIN_FAST_SECONDS = 1.0
    const val MAX_FAST_SECONDS = 6.0
    const val MIN_LOCAL_SECONDS = 6.0
    const val MAX_LOCAL_SECONDS = 30.0

    /** t = 1/(ε²·R), clamped to [min, max]; a dead stream gets [max]. */
    fun secondsFor(
        ratePerSecond: Double,
        relativeError: Double,
        minSeconds: Double,
        maxSeconds: Double,
    ): Double {
        if (!ratePerSecond.isFinite() || ratePerSecond <= 0.0) return maxSeconds
        val counts = 1.0 / (relativeError * relativeError)
        val seconds = counts / ratePerSecond
        if (!seconds.isFinite()) return maxSeconds
        return seconds.coerceIn(minSeconds, maxSeconds)
    }

    fun fastSeconds(ratePerSecond: Double): Double = secondsFor(
        ratePerSecond,
        FAST_RELATIVE_ERROR,
        MIN_FAST_SECONDS,
        MAX_FAST_SECONDS,
    )

    fun localSeconds(ratePerSecond: Double): Double = secondsFor(
        ratePerSecond,
        LOCAL_RELATIVE_ERROR,
        MIN_LOCAL_SECONDS,
        MAX_LOCAL_SECONDS,
    )
}
