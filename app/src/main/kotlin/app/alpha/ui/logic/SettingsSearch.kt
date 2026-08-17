package app.alpha.ui.logic

import java.util.Locale
import kotlin.math.min

/**
 * Поиск по настройкам: слово запроса → место, где настройка лежит.
 *
 * ## Индекс хранит слова, а не подписи
 *
 * Подпись настройки и слово, которым её ищут, — разные вещи («Отклик» ищут
 * словами «звук» и «вибрация»). Индекс хранит слова поиска; подпись приходит
 * из каталога строк и остаётся на языке интерфейса.
 *
 * ## Находки взвешены
 *
 * Каждая находка получает вес: точное слово выше начала слова, начало слова
 * выше середины, найденное с опечаткой — ниже всего.
 *
 * ## Послабления к вводу
 *
 *  - **несколько слов** — «звук поиск» ищет то, что подходит обоим;
 *  - **опечатка в одну букву** — «вибрацыя» находит «вибрацию» (для слов от
 *    четырёх букв: на коротких одна буква меняет смысл, а не написание);
 *  - **другая раскладка** — «pder» находит «звук».
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
     * Слова хранятся приведёнными к нижнему регистру.
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
     * Прибавка, когда слово нашлось и в синонимах, и в подписи: «язык» лежит в
     * синонимах у двух разделов, но подписан так один — без прибавки они шли
     * вровень, и порядок решал индекс.
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
     * Находки по запросу, лучшие первыми; пустой запрос — пустой список.
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
     * Отличается ли слово от запроса не больше чем на одну правку. Считается по
     * началу слова той же длины, что и запрос: «вибрацы» находит «вибрацию»
     * так же, как «вибрац».
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
     * То же слово, набранное в другой раскладке: «pder» — это «звук» на
     * латинской клавиатуре.
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
