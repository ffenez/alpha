package app.alpha.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Poll-cadence bridge between the UI and [MeasurementService], in the same
 * refcount shape as [SpectrumHub]: экран на переднем плане вызывает [attach],
 * служба выбирает период опроса DATA_BUF по числу наблюдателей. UI никогда не
 * трогает слой прибора напрямую.
 *
 * **Что даёт частый опрос — и чего он не даёт.** RadiaCode сам пишет примерно
 * одну запись RealTimeData в секунду. Опрос четыре раза в секунду НЕ даёт
 * четырёх измерений в секунду; он сокращает ЗАДЕРЖКУ ПОДБОРА — время, которое
 * готовая запись лежит в буфере прибора, пока мы её не прочли, — со средних
 * ~0,5 с до ~0,125 с. Это и есть «данные сразу»: быстрее прибора приложение
 * показать не может, но и ждать своей очереди запись больше не будет. Любая
 * формулировка в UI обязана говорить именно это и не обещать «4 измерения в
 * секунду».
 *
 * **Быстро — пока на приложение смотрят.** Прежде частый опрос просил только
 * Поиск, а на Главной значение обновлялось с задержкой до секунды сверх
 * приборной. Теперь наблюдателя ставит само окно приложения: экран виден —
 * опрашиваем часто, ушли в фон или погасили экран — возвращаемся к 1 Гц.
 * Расходы ограничены по построению: цикл опроса строго последовательный на
 * однозапросном `ProtocolClient` (тик не начинается, пока не вернулся
 * предыдущий, поэтому запросы не копятся), а пустой ответ DATA_BUF — норма
 * при частом опросе, а не сбой и не пропуск seq.
 */
class FastPollHub {

    private val _watchers = MutableStateFlow(0)
    val watchers: StateFlow<Int> = _watchers.asStateFlow()

    fun attach() {
        _watchers.update { it + 1 }
    }

    fun detach() {
        _watchers.update { (it - 1).coerceAtLeast(0) }
    }

    companion object {
        /** Ordinary cadence: one poll per produced record. */
        const val NORMAL_INTERVAL_MILLIS = 1_000L

        /**
         * Foreground cadence: те же записи, задержка подбора вчетверо меньше.
         * Совпадает с полом `RadiaCodeDevice.MIN_POLL_INTERVAL_MILLIS`: чаще
         * опрашивать бессмысленно, запись всё равно появляется раз в секунду.
         */
        const val FAST_INTERVAL_MILLIS = 250L

        /** Pure: any watcher means fast, none means back to 1 Hz. */
        fun intervalMillis(watchers: Int): Long =
            if (watchers > 0) FAST_INTERVAL_MILLIS else NORMAL_INTERVAL_MILLIS
    }
}
