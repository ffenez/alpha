package app.alpha.ui.logic

import app.alpha.ui.text.RuStrings
import app.alpha.ui.text.Strings

/**
 * Data honesty (SPEC): when the stream stops, the app must say so instead of
 * presenting the last value as current. Pure state machine, JVM-tested.
 */
sealed interface Freshness {
    /** Nothing was ever measured. */
    data object NoData : Freshness

    /** Stream alive: last sample is at most [STALE_AFTER_SECONDS] old. */
    data class Fresh(val ageSeconds: Long) : Freshness

    /** Stream stopped: last sample is older than [STALE_AFTER_SECONDS]. */
    data class Stale(val ageSeconds: Long) : Freshness

    companion object {
        const val STALE_AFTER_SECONDS = 10L

        /**
         * [lastSampleAtMillis] is the newest sample timestamp (device time base;
         * may run slightly ahead of the phone clock, so negative ages clamp to 0).
         */
        fun of(lastSampleAtMillis: Long?, nowMillis: Long): Freshness {
            if (lastSampleAtMillis == null) return NoData
            val age = ((nowMillis - lastSampleAtMillis) / 1000L).coerceAtLeast(0L)
            return if (age > STALE_AFTER_SECONDS) Stale(age) else Fresh(age)
        }
    }
}

/**
 * Компактная подпись свежести для чипа — или `null`, когда чипу нечего сказать.
 *
 * Возраст данных — это сообщение об отставании, и оно осмысленно только тогда,
 * когда отставание есть. Идущий поток обновляется раз в секунду, и чип при нём
 * либо показывал голое «0 с» (число без существительного, загадка на экране),
 * либо повторял словами то, что и так видно по живому значению. Поэтому пока
 * поток идёт, чипа нет вовсе; он появляется ровно в тот момент, когда данные
 * начали отставать, — и это появление само по себе информация.
 */
fun freshnessChipLabel(freshness: Freshness, s: Strings = RuStrings): String? = when (freshness) {
    Freshness.NoData -> s.noData
    is Freshness.Fresh ->
        if (freshness.ageSeconds <= FRESH_NOW_SECONDS) null
        else s.agoSeconds(freshness.ageSeconds)
    // «Прервано» звучит как аварийный обрыв сессии и для обычной задержки
    // телеметрии слишком драматично. Экран говорит то же, что Главная: данных
    // нет столько-то. Одна формулировка на всё приложение.
    is Freshness.Stale -> s.streamNoNewData(freshness.ageSeconds)
}

/**
 * До этого возраста поток считается идущим и чип не показывается.
 * **Инженерный параметр**: прибор пишет раз в секунду, и разница между нулём и
 * двумя секундами для человека — это одно и то же «сейчас».
 */
const val FRESH_NOW_SECONDS = 2L

/** UI wording for the staleness indicator; amber only in the stale state. */
fun freshnessLabel(freshness: Freshness, s: Strings = RuStrings): String = when (freshness) {
    Freshness.NoData -> s.noData
    is Freshness.Fresh ->
        if (freshness.ageSeconds <= FRESH_NOW_SECONDS) s.streamRunning
        else s.updatedAgo(freshness.ageSeconds)
    is Freshness.Stale -> "${s.streamInterruptedFor} ${s.agoSeconds(freshness.ageSeconds)}"
}
