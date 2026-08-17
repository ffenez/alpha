package app.alpha.ui.logic

/**
 * Пора ли предложить «Проверить здесь».
 *
 * ## Что решается
 *
 * Наведение отвечает «теплее или холоднее» на ходу; у остановки вопрос
 * другой — «есть ли здесь подтверждаемое отличие от фона», и на него отвечает
 * Проверка.
 *
 * ## Почему предложение, а не автозапуск
 *
 * Остановка приложением не наблюдается: акселерометр не используется, GPS в
 * помещении молчит, и единственный доступный признак — стабильность самого
 * счёта. Но это та же величина, которую Проверка потом измеряет: запуск по
 * спокойному сигналу дал бы измерение с предрешённым результатом.
 */
object SearchStillness {

    /**
     * Сколько счёт должен держаться ровно, прежде чем предлагать проверку, мс.
     *
     * **Инженерный параметр**: восемь секунд. Меньше — предложение выскакивает
     * на каждой паузе шага; больше — человек успевает уйти дальше, чем стоял.
     */
    const val DWELL_MILLIS = 8_000L

    /**
     * Состояние предложения. Хранится между вызовами: «держится восемь секунд»
     * — это утверждение о прошлом, а не о текущем отсчёте.
     */
    data class State(
        /** С какого момента направление ровное; null — сейчас не ровное. */
        val steadySinceMillis: Long? = null,
        /** Предложение уже отклонили — второй раз не навязываемся. */
        val dismissed: Boolean = false,
    )

    fun step(
        state: State,
        direction: SearchDirection,
        nowMillis: Long,
    ): State = when (direction) {
        // Ровно — засекаем, если ещё не засекли.
        SearchDirection.STEADY ->
            if (state.steadySinceMillis == null) state.copy(steadySinceMillis = nowMillis) else state
        // Пошло вверх или вниз — отсчёт сбрасывается, отказ забывается.
        SearchDirection.RISING, SearchDirection.FALLING ->
            State(steadySinceMillis = null, dismissed = false)
        // Данных на суждение нет — не сбрасываем и не начинаем.
        SearchDirection.UNKNOWN -> state
    }

    /** Показывать ли предложение прямо сейчас. */
    fun offering(state: State, nowMillis: Long): Boolean {
        if (state.dismissed) return false
        val since = state.steadySinceMillis ?: return false
        return nowMillis - since >= DWELL_MILLIS
    }

    fun dismiss(state: State): State = state.copy(dismissed = true)
}
