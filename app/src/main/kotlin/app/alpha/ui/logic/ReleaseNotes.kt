package app.alpha.ui.logic

import app.alpha.ui.text.ReleaseRu
import app.alpha.ui.text.ReleaseStrings

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
        ReleaseNote("0.50.0", s.v0500Title, s.v0500Summary),
        ReleaseNote("0.49.2", s.v0492Title, s.v0492Summary),
        ReleaseNote("0.49.1", s.v0491Title, s.v0491Summary),
        ReleaseNote("0.49.0", s.v0490Title, s.v0490Summary),
        ReleaseNote("0.48.2", s.v0482Title, s.v0482Summary),
        ReleaseNote("0.48.1", s.v0481Title, s.v0481Summary),
        ReleaseNote("0.48.0", s.v0480Title, s.v0480Summary),
        ReleaseNote("0.47.1", s.v0471Title, s.v0471Summary),
        ReleaseNote("0.47.0", s.v0470Title, s.v0470Summary),
        ReleaseNote("0.46.2", s.v0462Title, s.v0462Summary),
        ReleaseNote("0.46.1", s.v0461Title, s.v0461Summary),
        ReleaseNote("0.46.0", s.v0460Title, s.v0460Summary),
        ReleaseNote("0.45.1", s.v0451Title, s.v0451Summary),
        ReleaseNote("0.45.0", s.v0450Title, s.v0450Summary),
        ReleaseNote("0.44.3", s.v0443Title, s.v0443Summary),
        ReleaseNote("0.44.2", s.v0442Title, s.v0442Summary),
        ReleaseNote("0.44.1", s.v0441Title, s.v0441Summary),
        ReleaseNote("0.44.0", s.v0440Title, s.v0440Summary),
        ReleaseNote("0.43.0", s.v0430Title, s.v0430Summary),
        ReleaseNote("0.42.1", s.v0421Title, s.v0421Summary),
        ReleaseNote("0.42.0", s.v0420Title, s.v0420Summary),
        ReleaseNote("0.41.2", s.v0412Title, s.v0412Summary),
        ReleaseNote("0.41.1", s.v0411Title, s.v0411Summary),
        ReleaseNote("0.41.0", s.v0410Title, s.v0410Summary),
        ReleaseNote("0.40.0", s.v0400Title, s.v0400Summary),
        ReleaseNote("0.39.1", s.v0391Title, s.v0391Summary),
        ReleaseNote("0.39.0", s.v0390Title, s.v0390Summary),
        ReleaseNote("0.38.1", s.v0381Title, s.v0381Summary),
        ReleaseNote("0.38.0", s.v0380Title, s.v0380Summary),
        ReleaseNote("0.37.1", s.v0371Title, s.v0371Summary),
        ReleaseNote("0.37.0", s.v0370Title, s.v0370Summary),
        ReleaseNote("0.36.0", s.v0360Title, s.v0360Summary),
        ReleaseNote("0.35.1", s.v0351Title, s.v0351Summary),
        ReleaseNote("0.35.0", s.v0350Title, s.v0350Summary),
        ReleaseNote("0.34.1", s.v0341Title, s.v0341Summary),
        ReleaseNote("0.34.0", s.v0340Title, s.v0340Summary),
        ReleaseNote("0.33.0", s.v0330Title, s.v0330Summary),
        ReleaseNote("0.32.0", s.v0320Title, s.v0320Summary),
        ReleaseNote("0.31.0", s.v0310Title, s.v0310Summary),
        ReleaseNote("0.30.0", s.v0300Title, s.v0300Summary),
        ReleaseNote("0.29.1", s.v0291Title, s.v0291Summary),
        ReleaseNote("0.29.0", s.v0290Title, s.v0290Summary),
        ReleaseNote("0.28.0", s.v0280Title, s.v0280Summary),
        ReleaseNote("0.27.1", s.v0271Title, s.v0271Summary),
        ReleaseNote("0.27.0", s.v0270Title, s.v0270Summary),
        ReleaseNote("0.26.0", s.v0260Title, s.v0260Summary),
        ReleaseNote("0.25.0", s.v0250Title, s.v0250Summary),
        ReleaseNote("0.24.0", s.v0240Title, s.v0240Summary),
        ReleaseNote("0.23.0", s.v0230Title, s.v0230Summary),
        ReleaseNote("0.22.0", s.v0220Title, s.v0220Summary),
        ReleaseNote("0.21.0", s.v0210Title, s.v0210Summary),
        ReleaseNote("0.20.0", s.v0200Title, s.v0200Summary),
        ReleaseNote("0.19.0", s.v0190Title, s.v0190Summary),
        ReleaseNote("0.18.1", s.v0181Title, s.v0181Summary),
        ReleaseNote("0.18.0", s.v0180Title, s.v0180Summary),
        ReleaseNote("0.17.0", s.v0170Title, s.v0170Summary),
        ReleaseNote("0.16.0", s.v0160Title, s.v0160Summary),
        ReleaseNote("0.15.0", s.v0150Title, s.v0150Summary),
        ReleaseNote("0.14.0", s.v0140Title, s.v0140Summary),
        ReleaseNote("0.13.0", s.v0130Title, s.v0130Summary),
        ReleaseNote("0.12.1", s.v0121Title, s.v0121Summary),
        ReleaseNote("0.12.0", s.v0120Title, s.v0120Summary),
        ReleaseNote("0.11.0", s.v0110Title, s.v0110Summary),
        ReleaseNote("0.10.4", s.v0104Title, s.v0104Summary),
        ReleaseNote("0.10.3", s.v0103Title, s.v0103Summary),
        ReleaseNote("0.10.2", s.v0102Title, s.v0102Summary),
        ReleaseNote("0.10.1", s.v0101Title, s.v0101Summary),
        ReleaseNote("0.10.0", s.v0100Title, s.v0100Summary),
        ReleaseNote("0.9.9", s.v099Title, s.v099Summary),
        ReleaseNote("0.9.8", s.v098Title, s.v098Summary),
        ReleaseNote("0.9.7", s.v097Title, s.v097Summary),
        ReleaseNote("0.9.6", s.v096Title, s.v096Summary),
        ReleaseNote("0.9.5", s.v095Title, s.v095Summary),
        ReleaseNote("0.9.4", s.v094Title, s.v094Summary),
        ReleaseNote("0.9.3", s.v093Title, s.v093Summary),
        ReleaseNote("0.9.2", s.v092Title, s.v092Summary),
        ReleaseNote("0.9.1", s.v091Title, s.v091Summary),
        ReleaseNote("0.9.0", s.v090Title, s.v090Summary),
        ReleaseNote("0.8.3", s.v083Title, s.v083Summary),
        ReleaseNote("0.8.2", s.v082Title, s.v082Summary),
        ReleaseNote("0.8.1", s.v081Title, s.v081Summary),
        ReleaseNote("0.8.0", s.v080Title, s.v080Summary),
        ReleaseNote("0.7.9", s.v079Title, s.v079Summary),
        ReleaseNote("0.7.8", s.v078Title, s.v078Summary),
        ReleaseNote("0.7.7", s.v077Title, s.v077Summary),
        ReleaseNote("0.7.6", s.v076Title, s.v076Summary),
        ReleaseNote("0.7.5", s.v075Title, s.v075Summary),
        ReleaseNote("0.7.4", s.v074Title, s.v074Summary),
        ReleaseNote("0.7.3", s.v073Title, s.v073Summary),
        ReleaseNote("0.7.2", s.v072Title, s.v072Summary),
        ReleaseNote("0.7.1", s.v071Title, s.v071Summary),
        ReleaseNote("0.7.0", s.v070Title, s.v070Summary),
        ReleaseNote("0.6.9", s.v069Title, s.v069Summary),
        ReleaseNote("0.6.8", s.v068Title, s.v068Summary),
        ReleaseNote("0.6.7", s.v067Title, s.v067Summary),
        ReleaseNote("0.6.6", s.v066Title, s.v066Summary),
        ReleaseNote("0.6.5", s.v065Title, s.v065Summary),
        ReleaseNote("0.6.4", s.v064Title, s.v064Summary),
        ReleaseNote("0.6.3", s.v063Title, s.v063Summary),
        ReleaseNote("0.6.2", s.v062Title, s.v062Summary),
        ReleaseNote("0.6.1", s.v061Title, s.v061Summary),
        ReleaseNote("0.6.0", s.v060Title, s.v060Summary),
        ReleaseNote("0.5.9", s.v059Title, s.v059Summary),
        ReleaseNote("0.5.8", s.v058Title, s.v058Summary),
        ReleaseNote("0.5.7", s.v057Title, s.v057Summary),
        ReleaseNote("0.5.6", s.v056Title, s.v056Summary),
        ReleaseNote("0.5.5", s.v055Title, s.v055Summary),
        ReleaseNote("0.5.4", s.v054Title, s.v054Summary),
        ReleaseNote("0.5.3", s.v053Title, s.v053Summary),
        ReleaseNote("0.5.2", s.v052Title, s.v052Summary),
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
