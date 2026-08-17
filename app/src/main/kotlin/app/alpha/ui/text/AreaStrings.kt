package app.alpha.ui.text

/**
 * Каталог строк ОДНОЙ области экрана.
 *
 * ## Зачем отдельные каталоги, а не один общий
 *
 * Общий [Strings] хорош, пока его правит один человек: он даёт компилятору
 * проверить, что ни один язык не забыл строку. Но перевод оставшихся экранов
 * — работа, которую делают параллельно, и тогда единственный файл становится
 * узким местом: две правки в одно место затирают друг друга.
 *
 * Поэтому у каждой области — свой файл с тремя объявлениями: интерфейс,
 * русская реализация, английская. Файлы независимы, компилятор по-прежнему не
 * даёт языку забыть строку, а правки разных областей не пересекаются.
 *
 * ## Как пользоваться
 *
 * ```kotlin
 * interface MapStrings {
 *     val trackTitle: String
 *     fun cellSize(meters: String): String
 * }
 *
 * object MapRu : MapStrings { … }
 * object MapEn : MapStrings { … }
 *
 * val MapCatalogue = AreaCatalogue(ru = MapRu, en = MapEn)
 * ```
 *
 * На экране: `val m = MapCatalogue.of(LocalStrings.current.language)`.
 *
 * В чистой логике (`ui/logic`, `analysis`) каталог передаётся ПАРАМЕТРОМ со
 * значением по умолчанию — русским: эти функции вызывают и тесты, и отчёты,
 * куда композиционный `LocalStrings` не приходит.
 */
class AreaCatalogue<T>(private val ru: T, private val en: T) {

    fun of(language: AppLanguage): T = when (language) {
        AppLanguage.EN -> en
        else -> ru
    }

    /** Оба каталога — для проверок, действующих на каждый язык области. */
    val all: List<T> get() = listOf(ru, en)
}
