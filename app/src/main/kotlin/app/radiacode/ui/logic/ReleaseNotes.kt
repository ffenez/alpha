package app.radiacode.ui.logic

import app.radiacode.ui.text.ReleaseRu
import app.radiacode.ui.text.ReleaseStrings

/** Одно обновление: чем эта версия отличается от предыдущей. */
data class ReleaseNote(
    /**
     * Номер версии, в которой изменение вышло.
     *
     * Номер, а не дата: дата отвечает на вопрос «когда я это собрал», а
     * человеку нужен ответ «та ли у меня версия, в которой это исправлено».
     * Самый верхний номер обязан совпадать с версией сборки — это запинено
     * тестом, иначе список обновлений начал бы описывать не то, что стоит на
     * телефоне.
     */
    val version: String,
    val title: String,
    /**
     * Что изменилось — ОДНОЙ-ДВУМЯ фразами, без внутренних имён.
     *
     * Список пунктов на два десятка строк был отчётом о проделанной работе:
     * человек открывает «О приложении», чтобы за несколько секунд понять, что
     * поменялось, а не читать журнал изменений. Полная история — в
     * репозитории, и она никуда не делась.
     */
    val summary: String,
)

/**
 * Краткая история последних обновлений — то, что открывается по нажатию на
 * версию в «О приложении».
 *
 * Список ведётся руками и НЕ выводится из git: сообщения коммитов написаны для
 * тех, кто читает код, а здесь нужен ответ на другой вопрос — «что изменилось
 * у меня на экране». Новая запись добавляется сверху, старые уходят за
 * [SHOWN]; полная история живёт в репозитории.
 *
 * Порядок — от новой версии к старой; первая запись описывает то, что стоит
 * на телефоне прямо сейчас.
 */
object ReleaseNotes {

    /** Сколько записей показывается. */
    const val SHOWN = 5

    /**
     * Версия, которая стоит на телефоне: она же — первая запись списка.
     *
     * От языка не зависит: номер версии — факт сборки, а не текст. Поэтому он
     * задан здесь, а не в каталоге строк.
     */
    val current: String get() = notes().first().version

    /**
     * Записи на выбранном языке. Каталог приходит ПАРАМЕТРОМ: эту функцию
     * зовут и композиции (там язык известен), и тесты, куда `LocalStrings` не
     * приходит.
     *
     * Номера версий и их порядок живут здесь, в одном месте на все языки.
     */
    fun notes(s: ReleaseStrings = ReleaseRu): List<ReleaseNote> = listOf(
        ReleaseNote("0.5.1", s.v051Title, s.v051Summary),
        ReleaseNote("0.5.0", s.v050Title, s.v050Summary),
        ReleaseNote("0.4.9", s.v049Title, s.v049Summary),
        ReleaseNote("0.4.8", s.v048Title, s.v048Summary),
        ReleaseNote("0.4.7", s.v047Title, s.v047Summary),
        ReleaseNote("0.4.6", s.v046Title, s.v046Summary),
        ReleaseNote("0.4.5", s.v045Title, s.v045Summary),
        ReleaseNote("0.4.4", s.v044Title, s.v044Summary),
        ReleaseNote("0.4.3", s.v043Title, s.v043Summary),
        ReleaseNote("0.4.2", s.v042Title, s.v042Summary),
        ReleaseNote("0.4.1", s.v041Title, s.v041Summary),
        ReleaseNote("0.4.0", s.v040Title, s.v040Summary),
        ReleaseNote("0.3.9", s.v039Title, s.v039Summary),
        ReleaseNote("0.3.8", s.v038Title, s.v038Summary),
        ReleaseNote("0.3.7", s.v037Title, s.v037Summary),
        ReleaseNote("0.3.6", s.v036Title, s.v036Summary),
        ReleaseNote("0.3.5", s.v035Title, s.v035Summary),
        ReleaseNote("0.3.4", s.v034Title, s.v034Summary),
        ReleaseNote("0.3.3", s.v033Title, s.v033Summary),
        ReleaseNote("0.3.2", s.v032Title, s.v032Summary),
        ReleaseNote("0.3.1", s.v031Title, s.v031Summary),
        ReleaseNote("0.3.0", s.v030Title, s.v030Summary),
        ReleaseNote("0.2.1", s.v021Title, s.v021Summary),
        ReleaseNote("0.2.0", s.v020Title, s.v020Summary),
        ReleaseNote("0.1.0", s.v010Title, s.v010Summary),
        ReleaseNote("0.0.9", s.v009Title, s.v009Summary),
        ReleaseNote("0.0.8", s.v008Title, s.v008Summary),
        ReleaseNote("0.0.7", s.v007Title, s.v007Summary),
        ReleaseNote("0.0.6", s.v006Title, s.v006Summary),
        ReleaseNote("0.0.5", s.v005Title, s.v005Summary),
        ReleaseNote("0.0.1", s.v001Title, s.v001Summary),
    )

    /** Все записи по-русски — значение по умолчанию для тестов и отчётов. */
    val all: List<ReleaseNote> get() = notes()

    /** Записи, которые показывает экран, на выбранном языке. */
    fun shownIn(s: ReleaseStrings): List<ReleaseNote> = notes(s).take(SHOWN)

    /** То же по-русски. */
    val shown: List<ReleaseNote> get() = shownIn(ReleaseRu)
}
