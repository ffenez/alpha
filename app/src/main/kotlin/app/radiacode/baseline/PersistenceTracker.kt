package app.radiacode.baseline

/**
 * Deviation persistence: a status/alert requires BOTH magnitude and duration
 * (SPEC «Alarms»). The magnitude condition is computed by the caller (see
 * [deviationMagnitude]); this tracker answers "has it held long enough".
 *
 * Short dips below the condition within [gapToleranceMillis] do not reset the
 * excursion — 1 Hz dose rate is noisy around any boundary. [confirm] fires the
 * rising edge exactly once per excursion (event journal / alarm), and re-arms
 * only after the excursion fully ends.
 */
class PersistenceTracker(
    private val persistenceMillis: Long,
    private val gapToleranceMillis: Long = DEFAULT_GAP_TOLERANCE_MILLIS,
) {

    sealed interface State {
        /** Condition not met. */
        data object Idle : State

        /** Condition met, duration still below the persistence requirement. */
        data class Building(val sinceMillis: Long) : State

        /** Condition met for at least the persistence duration. */
        data class Confirmed(val sinceMillis: Long) : State
    }

    /** [fired] is true exactly once: on the transition into [State.Confirmed]. */
    data class Assessment(val state: State, val fired: Boolean)

    private var sinceMillis: Long? = null
    private var lastMetMillis: Long? = null
    private var confirmed = false

    fun onSample(nowMillis: Long, conditionMet: Boolean): Assessment {
        if (conditionMet) {
            if (sinceMillis == null) sinceMillis = nowMillis
            lastMetMillis = nowMillis
        } else {
            val lastMet = lastMetMillis
            if (lastMet == null || nowMillis - lastMet > gapToleranceMillis) {
                sinceMillis = null
                lastMetMillis = null
                confirmed = false
            }
        }

        val since = sinceMillis ?: return Assessment(State.Idle, fired = false)
        return if (nowMillis - since >= persistenceMillis) {
            val fired = !confirmed
            confirmed = true
            Assessment(State.Confirmed(since), fired)
        } else {
            Assessment(State.Building(since), fired = false)
        }
    }

    fun reset() {
        sinceMillis = null
        lastMetMillis = null
        confirmed = false
    }

    companion object {
        const val DEFAULT_GAP_TOLERANCE_MILLIS = 15_000L
    }
}

/**
 * Live deviation picture published by the measurement service for the UI:
 * epoch millis since the respective excursion started, or null when calm.
 * [alertSince] = confirmed persistent deviation («Уровень радиации изменился»).
 */
data class DeviationSnapshot(
    val aboveUsualSince: Long? = null,
    val alertSince: Long? = null,
    /**
     * Когда условие тревоги выполнилось ВПЕРВЫЕ, ещё до подтверждения
     * длительностью. Без него экран молчал всё время ожидания: пользователь
     * ставил порог 0,10, видел 0,17 — и приложение до конца выдержки не
     * показывало ничего, потому что значение оставалось внутри исторического
     * диапазона места. Магнитуда и длительность — разные вещи, и первая
     * заслуживает быть сказанной сразу.
     */
    val alarmConditionSince: Long? = null,
)
