package app.radiacode.ui.logic

import app.radiacode.device.ConnectionState

/**
 * Состояние ПОТОКА измерений — один ответ на вопрос «идут ли данные сейчас».
 *
 * ## Зачем отдельная машина, если есть [Freshness]
 *
 * [Freshness] знает только возраст последнего отсчёта и поэтому не различает
 * две разные вещи: прибор отвалился — или он рядом, а поток на секунду
 * запнулся. Из-за этого «прервано N с назад» жило на экране бесконечно и росло
 * как основной статус, хотя после первой минуты возраст перестаёт что-либо
 * решать: важно, что связи нет, а не что её нет уже 743 секунды.
 *
 * И вторая беда, которую лечит именно ОДИН источник: главное число, плитка
 * счёта, строка статуса и графики брали свежесть каждый по-своему, и экран мог
 * одновременно говорить «поток прерван» вверху и выглядеть живым в карточках.
 *
 * ## Состояния
 *
 * - [Live] — данные идут; о разрыве не говорится ничего;
 * - [Stale] — короткая запинка: возраст назван, потому что он ещё информация;
 * - [Reconnecting] — прибор переподключается; на экране это только цвет точки
 *   связи: словами приложение о своей работе не отчитывается;
 * - [Disconnected] — устойчивое состояние: связи нет либо данные давно не
 *   приходят. Возраст здесь ВТОРИЧЕН и живёт отдельной подписью.
 *
 * Чистая логика, JVM-тесты.
 */
sealed interface StreamState {

    data object Live : StreamState

    data class Stale(val ageSeconds: Long) : StreamState

    data object Reconnecting : StreamState

    /**
     * @param ageSeconds возраст последнего отсчёта; null — измерений не было
     *   вовсе (тогда говорить «данные прервались» нельзя: они не начинались).
     */
    data class Disconnected(val ageSeconds: Long?) : StreamState

    /** Показывает ли состояние живые числа как текущие. */
    val live: Boolean get() = this is Live

    companion object {

        /**
         * До этого возраста поток считается идущим.
         * **Инженерный параметр**: прибор пишет раз в секунду, пара секунд
         * задержки переноса по BLE — это то же самое «сейчас».
         */
        const val LIVE_AGE_SECONDS = 3L

        /**
         * После этого возраста запинка перестаёт быть запинкой.
         * **Инженерный параметр**: минута без данных при секундной записи —
         * это уже не «подождите», а состояние, и счётчик секунд в нём только
         * растёт, ничего не добавляя.
         */
        const val LOST_AFTER_SECONDS = 60L

        /**
         * @param lastSampleAtMillis момент последнего отсчёта (база времени
         *   прибора; она может слегка опережать часы телефона, поэтому
         *   отрицательный возраст зажимается нулём).
         */
        fun of(
            lastSampleAtMillis: Long?,
            nowMillis: Long,
            connection: ConnectionState,
        ): StreamState {
            val age = lastSampleAtMillis?.let { ((nowMillis - it) / 1000L).coerceAtLeast(0L) }
            return when {
                // Подключение в процессе — это ответ сам по себе, и он важнее
                // возраста: данные вот-вот пойдут, ругаться на разрыв незачем.
                connection is ConnectionState.Connecting ||
                    connection is ConnectionState.Reconnecting -> Reconnecting
                connection !is ConnectionState.Connected -> Disconnected(age)
                age == null -> Disconnected(null)
                age <= LIVE_AGE_SECONDS -> Live
                age <= LOST_AFTER_SECONDS -> Stale(age)
                else -> Disconnected(age)
            }
        }
    }
}

/**
 * Главная строка о состоянии потока — или `null`, когда говорить нечего.
 *
 * В [StreamState.Live] возвращается null НАМЕРЕННО: молчание и есть сообщение
 * «всё идёт», а любая надпись в этом состоянии — шум, который человек учится
 * не замечать (и перестаёт замечать её же в момент, когда она важна).
 */
fun streamStatusLine(state: StreamState, s: app.radiacode.ui.text.Strings): String? = when (state) {
    StreamState.Live -> null
    is StreamState.Stale -> s.streamNoNewData(state.ageSeconds)
    // Переподключение не называется словами: это НЕ состояние прибора, а
    // работа приложения, и человеку от неё ничего не требуется. Точка связи в
    // шапке уже показывает, что связи сейчас нет, а надпись про неё появлялась
    // и исчезала сама по себе — и оставалась в памяти как мигание.
    StreamState.Reconnecting -> null
    is StreamState.Disconnected ->
        if (state.ageSeconds == null) s.streamNoDataYet else s.streamLost
}

/**
 * Вторичная подпись: возраст последнего измерения.
 *
 * Существует только в устойчивом состоянии — там, где основная строка уже не
 * называет секунды. В [StreamState.Stale] возраст стоит в главной строке, и
 * повторять его вторым шрифтом незачем.
 */
fun streamAgeLine(state: StreamState, s: app.radiacode.ui.text.Strings): String? =
    when (state) {
        is StreamState.Disconnected -> state.ageSeconds?.let { s.lastMeasurementAgo(it) }
        else -> null
    }
