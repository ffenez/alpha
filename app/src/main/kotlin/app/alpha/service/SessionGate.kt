package app.alpha.service

/**
 * Pure decision logic for measurement-session boundaries (SPEC «History»:
 * sessions are continuous measurement periods).
 *
 * Сессию заканчивает остановка записи или разрыв длиннее [DEFAULT_GRACE_MILLIS].
 * Потеря связи с переподключением, сворачивание приложения и перезапуск службы
 * системой новой записи не создают: короткий разрыв остаётся дырой ВНУТРИ
 * сессии, а график и статистика показывают пропуск как пропуск.
 */
class SessionGate(private val graceMillis: Long = DEFAULT_GRACE_MILLIS) {

    sealed interface Action {
        data object None : Action

        /** Open a new session now. */
        data object Open : Action

        /** Close the stale session at [closeAt], then open a new one. */
        data class Reopen(val closeAt: Long) : Action

        /** Close the current session at [closeAt]. */
        data class Close(val closeAt: Long) : Action
    }

    private var open = false
    private var lostAtMillis: Long? = null

    /** [lastSampleAt] = newest recorded sample, the honest close timestamp. */
    fun onConnected(nowMillis: Long, lastSampleAt: Long?): Action {
        val lostAt = lostAtMillis
        lostAtMillis = null
        return when {
            !open -> {
                open = true
                Action.Open
            }
            lostAt != null && nowMillis - lostAt > graceMillis ->
                Action.Reopen(closeAt = lastSampleAt ?: lostAt)
            else -> Action.None
        }
    }

    /** Link lost, reconnect attempts running. */
    fun onLinkLost(nowMillis: Long): Action {
        if (open && lostAtMillis == null) lostAtMillis = nowMillis
        return Action.None
    }

    /** Deliberate stop or terminal disconnect. */
    fun onDisconnected(nowMillis: Long, lastSampleAt: Long?): Action {
        lostAtMillis = null
        if (!open) return Action.None
        open = false
        return Action.Close(closeAt = lastSampleAt ?: nowMillis)
    }

    companion object {
        /**
         * Насколько долгим должен быть разрыв, чтобы считаться новой записью.
         *
         * **Инженерный параметр**: полчаса. В пять минут укладываются
         * переподключение с нарастающей паузой, уход прибора из радиуса и
         * перезапуск службы системой — журнал рассыпался на куски по
         * несколько минут. Получасовая дыра внутри записи видна и на графике,
         * и в числе измерений.
         */
        const val DEFAULT_GRACE_MILLIS = 30L * 60_000L
    }
}
