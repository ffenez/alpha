package app.alpha.ui.logic

import java.util.Locale
import kotlin.math.min

/**
 * Поиск по настройкам: слово, которое человек помнит, → место, где это лежит.
 *
 * ## Зачем
 *
 * Разделов семь, и это уже больше, чем держится в голове. Человек помнит не
 * структуру, а слово: «звук», «фон», «язык», «батарея». Пока поиска нет, он
 * открывает разделы по очереди — и половина попыток не туда.
 *
 * ## Почему синонимы, а не поиск по подписям
 *
 * Подпись настройки и слово, которым её называют про себя, — разные вещи:
 * «Отклик» никто не ищет словом «отклик», ищут «звук» и «вибрация». Индекс
 * поэтому хранит СЛОВА, по которым настройку ищут, а не только текст, которым
 * она подписана; подпись приходит из каталога строк и остаётся на языке
 * интерфейса.
 *
 * ## Почему находки взвешены, а не просто отфильтрованы
 *
 * Плоский фильтр возвращал находки в порядке индекса: точное «язык» стояло
 * ниже раздела, у которого «язык» просто оказался одним из десяти синонимов.
 * Теперь каждая находка получает вес — точное слово выше начала слова, начало
 * слова выше середины, найденное с опечаткой ниже всего, — и список
 * начинается с того, что человек искал.
 *
 * ## Три послабления к вводу
 *
 * Всё это — про один и тот же случай: человек уже знает, что ищет, и не должен
 * попасть в клавиатуру без промаха.
 *
 *  - **несколько слов** — «звук поиск» ищет то, что подходит ОБОИМ словам;
 *  - **опечатка в одну букву** — «вибрацыя» находит «вибрацию» (для слов от
 *    четырёх букв: на коротких одна буква меняет смысл, а не написание);
 *  - **не та раскладка** — «pder» находит «звук», потому что это оно и есть,
 *    просто набранное латиницей.
 */
object SettingsSearch {

    /** Одна находка: что нашли, где это лежит и насколько точно совпало. */
    data class Hit(
        /** Идентификатор раздела, куда ведёт находка. */
        val categoryId: String,
        /** Что именно нашли — подпись настройки на языке интерфейса. */
        val title: String,
        /** Где это лежит — название раздела. */
        val section: String,
        /** Вес совпадения; больше — точнее. Наружу нужен для порядка списка. */
        val score: Int = 0,
    )

    /**
     * Запись индекса: настройка, её раздел и слова, по которым её ищут.
     *
     * Слова хранятся уже приведёнными к нижнему регистру — приводить их на
     * каждый ввод символа незачем.
     */
    data class Entry(
        val categoryId: String,
        val title: String,
        val section: String,
        val keywords: List<String>,
    )

    /** Вес точного слова: искали ровно это. */
    const val SCORE_EXACT = 100

    /** Вес начала слова: «вибр» → «вибрация». */
    const val SCORE_PREFIX = 60

    /** Вес середины слова: «зерв» → «резервная»; годится от четырёх букв. */
    const val SCORE_INSIDE = 25

    /** Вес совпадения с одной опечаткой — последнее, что стоит показывать. */
    const val SCORE_TYPO = 10

    /** Совпадение по подписи слабее совпадения по слову-синониму. */
    const val TITLE_PENALTY = 5

    /**
     * Прибавка, когда слово нашлось И в синонимах, И в подписи.
     *
     * «Язык» лежит в синонимах у двух разделов, но подписан так только один —
     * он и есть тот, который искали. Без этой прибавки они шли вровень, и
     * первым оказывался тот, кто раньше в индексе.
     */
    const val SCORE_TITLE_AGREES = 20

    /**
     * Короче этого слово не ищется по середине и не прощает опечатку: на
     * двух-трёх буквах любая поблажка превращает список в «всё подряд».
     *
     * **Инженерный параметр**: четыре буквы — первая длина, на которой одна
     * замена ещё оставляет слово узнаваемым («фона» ≠ «фото», но «вибрацыя» —
     * это «вибрация»).
     */
    const val MIN_FUZZY_LENGTH = 4

    /**
     * Находки по запросу, лучшие первыми; пустой запрос — пустой список (не
     * «всё подряд»: список всех настроек человек и так видит на экране).
     */
    fun find(query: String, index: List<Entry> = emptyList()): List<Hit> {
        val terms = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return index
            .mapNotNull { entry ->
                // Каждое слово запроса обязано найтись: «звук поиск» — это
                // сужение, а не «или».
                var total = 0
                for (term in terms) {
                    val score = score(term, entry)
                    if (score == 0) return@mapNotNull null
                    total += score
                }
                Hit(entry.categoryId, entry.title, entry.section, total)
            }
            .sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.title })
            .distinctBy { it.categoryId to it.title }
    }

    /** Насколько хорошо одно слово запроса совпало с записью индекса. */
    private fun score(term: String, entry: Entry): Int {
        // Слово, набранное не в той раскладке, — то же слово; отдельного веса
        // ему не нужно, оно уже найдено ровно тем, чем является.
        val variants = listOf(term, switchLayout(term)).distinct().filter { it.isNotEmpty() }
        var best = 0
        for (variant in variants) {
            val keywordScore = entry.keywords.maxOfOrNull { match(variant, it) } ?: 0
            val titleScore = normalize(entry.title).split(' ')
                .maxOfOrNull { match(variant, it) }
                ?.let { if (it > 0) it - TITLE_PENALTY else 0 } ?: 0
            val agreement = if (keywordScore > 0 && titleScore > 0) SCORE_TITLE_AGREES else 0
            best = maxOf(best, maxOf(keywordScore, titleScore) + agreement)
        }
        return best
    }

    private fun match(term: String, word: String): Int = when {
        word == term -> SCORE_EXACT
        word.startsWith(term) -> SCORE_PREFIX
        term.length >= MIN_FUZZY_LENGTH && word.contains(term) -> SCORE_INSIDE
        term.length >= MIN_FUZZY_LENGTH && withinOneEdit(term, word) -> SCORE_TYPO
        else -> 0
    }

    /**
     * Отличается ли слово от запроса не больше чем на одну правку.
     *
     * Считается по началу слова той же длины, что и запрос: человек набирает
     * начало настройки, а не её целиком, и «вибрацы» должно находить
     * «вибрацию» так же, как «вибрац».
     */
    fun withinOneEdit(term: String, word: String): Boolean {
        if (word.length + 1 < term.length) return false
        val head = word.take(min(word.length, term.length + 1))
        return editDistanceAtMostOne(term, head) || editDistanceAtMostOne(term, word)
    }

    /** Расстояние Дамерау — Левенштейна, обрезанное на единице. */
    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (a == b) return true
        val diff = a.length - b.length
        if (diff !in -1..1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
                continue
            }
            if (edits == 1) return false
            edits++
            when {
                // Перестановка соседних букв — одна ошибка, а не две.
                i + 1 < a.length && j + 1 < b.length &&
                    a[i] == b[j + 1] && a[i + 1] == b[j] -> {
                    i += 2
                    j += 2
                }
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    /**
     * То же слово, набранное в другой раскладке.
     *
     * «pder» — это «звук» на латинской клавиатуре: человек начал печатать, не
     * заметив языка ввода, и предлагать ему исправлять раскладку вместо того,
     * чтобы понять слово, — работа не для человека.
     */
    fun switchLayout(value: String): String {
        val out = StringBuilder(value.length)
        var changed = false
        for (ch in value) {
            val mapped = LATIN_TO_CYRILLIC[ch] ?: CYRILLIC_TO_LATIN[ch]
            if (mapped != null) changed = true
            out.append(mapped ?: ch)
        }
        return if (changed) out.toString() else ""
    }

    /** Регистр и лишние пробелы значения не имеют, «ё» пишут и так и так. */
    fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("\\s+"), " ")

    /** ЙЦУКЕН ↔ QWERTY: одна и та же клавиша в двух алфавитах. */
    private val QWERTY = "qwertyuiop[]asdfghjkl;'zxcvbnm,."
    private val JCUKEN = "йцукенгшщзхъфывапролджэячсмитьбю"

    private val LATIN_TO_CYRILLIC: Map<Char, Char> =
        QWERTY.indices.associate { QWERTY[it] to JCUKEN[it] }

    private val CYRILLIC_TO_LATIN: Map<Char, Char> =
        JCUKEN.indices.associate { JCUKEN[it] to QWERTY[it] }
}
