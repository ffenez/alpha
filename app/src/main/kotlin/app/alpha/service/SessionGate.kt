package app.alpha.service

/**
 * Pure decision logic for measurement-session boundaries (SPEC «History»:
 * sessions are continuous measurement periods).
 *
 * ## Что заканчивает сессию
 *
 * Сессию заканчивает РЕШЕНИЕ ЧЕЛОВЕКА — остановка записи — или разрыв,
 * который уже нельзя назвать заминкой. Всё остальное (потеря связи с
 * переподключением, сворачивание приложения, перезапуск службы системой)
 * происходит не по его воле, и превращать это в новую запись журнала значит
 * рассказывать про его день то, чего он не делал.
 *
 * Полевой отчёт, из-за которого это переписано: за три часа в одном месте
 * журнал показал восемь записей «Дом» — 4, 8, 58, 17, 12, 32, 33 и 19 минут.
 * Внутри каждой измерения шли ровно раз в секунду, то есть терялись не
 * данные, а границы.
 *
 * Короткий разрыв остаётся ДЫРОЙ ВНУТРИ сессии: график и статистика уже умеют
 * показывать пропуск как пропуск, и это честнее, чем восемь почти одинаковых
 * карточек подряд.
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
         * **Инженерный параметр.** Пять минут не работали: переподключение с
         * нарастающей паузой, уход прибора из радиуса и возвращение, перезапуск
         * службы системой — всё это укладывается в них редко, и журнал
         * рассыпался на куски по несколько минут. Полчаса — это уже не заминка
         * связи, а перерыв: человек ушёл, отложил телефон, сменил занятие.
         * Внутри одной записи получасовая дыра видна и на графике, и в числе
         * измерений, поэтому склейка ничего не скрывает.
         */
        const val DEFAULT_GRACE_MILLIS = 30L * 60_000L
    }
}
