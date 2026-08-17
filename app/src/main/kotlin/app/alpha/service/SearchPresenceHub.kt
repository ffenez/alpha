package app.alpha.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Открыт ли Поиск — refcount той же формы, что [FastPollHub] и [SpectrumHub].
 *
 * Существует ради ОДНОГО решения: измерения, снятые во время Поиска, не имеют
 * права учить обычный фон места (спец §18). Человек с Поиском на экране водит
 * прибором по поверхностям и подносит его к предметам — это эксперимент, а не
 * наблюдение за местом, и попав в статистику, он поднял бы «обычный фон» дома
 * до уровня, которого там нет.
 *
 * **Почему не переиспользуется [FastPollHub].** Раньше это был один счётчик:
 * Поиск просил частый опрос, и та же единица означала «эксперимент». Как
 * только частый опрос стал общим для всего приложения (данные без задержки
 * нужны и на Главной), совмещённый счётчик пометил бы экспериментом каждую
 * минуту с открытым экраном. Два разных вопроса — два разных счётчика.
 */
class SearchPresenceHub {

    private val _watchers = MutableStateFlow(0)
    val watchers: StateFlow<Int> = _watchers.asStateFlow()

    fun attach() {
        _watchers.update { it + 1 }
    }

    fun detach() {
        _watchers.update { (it - 1).coerceAtLeast(0) }
    }
}
