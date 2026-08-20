package app.alpha.ui.logic

import app.alpha.analysis.CountWindow
import app.alpha.ui.text.SearchRu
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Справка Поиска: что видит человек, нажавший на число.
 *
 * Проверяется не вёрстка, а два свойства, которых ей не хватало: у каждого
 * числа есть единица, и разбор БЕЗ снятой точки отсчёта остаётся разбором, а
 * не двумя строками.
 */
class NavigateWhyLinesTest {

    private fun window(rate: Double, seconds: Int): CountWindow {
        val times = LongArray(seconds) { it * 1_000L }
        val rates = DoubleArray(seconds) { rate }
        return CountWindow.reconstruct(times, rates)
    }

    private fun state(reference: NavigateReference? = null) = NavigateState(
        fast = window(30.0, 18),
        local = window(25.0, 120),
        reference = reference,
    )

    private fun lines(reference: NavigateReference? = null) = NavigateVerdict.whyLines(
        state = state(reference),
        delta = ReferenceDelta.Collecting,
        cps = 30.0f,
    )

    @Test
    fun `у каждого числа есть единица или знак отношения`() {
        // «12,3» без единицы читается как что угодно; правило проекта требует
        // единицу у любого числа на экране, и справка не исключение.
        for (line in lines()) {
            val value = line.value
            // Единица стоит либо при числе, либо числом является само
            // отношение («×1,45»). Фраза окон расчёта называет секунды
            // внутри себя — это тоже единица при числе.
            val unitAtNumber = Regex("[0-9]\\s?(с⁻¹|s⁻¹|с|s|%)")
            val named = value.startsWith("×") ||
                value == SearchRu.navWhyNoReference ||
                unitAtNumber.containsMatchIn(value)
            assertTrue(named, "«${line.label}: $value» — число без единицы")
        }
    }

    @Test
    fun `без точки отсчёта справка объясняет, а не пустеет`() {
        val without = lines()
        val labels = without.map { it.label }
        assertTrue(labels.contains(SearchRu.navWhyReference), "$labels")
        val reference = without.first { it.label == SearchRu.navWhyReference }
        assertTrue(reference.value == SearchRu.navWhyNoReference, reference.value)
        // Пояснение говорит, ЧТО делает кнопка, — иначе строка «не снята»
        // только сообщает об отсутствии.
        assertTrue(assertNotNull(reference.note).contains("запомнить"), "${reference.note}")
        assertTrue(without.size >= 4, "строк ${without.size}: разбор выглядит сломанным")
    }

    @Test
    fun `набранное время стоит рядом с показанием`() {
        // Одна секунда и минута дают одно и то же «30,0»; без набранного
        // времени число выглядит точнее, чем оно есть.
        val collected = lines().first { it.label == SearchRu.navWhyCollected }
        assertTrue(collected.value.endsWith(" с"), collected.value)
    }

    @Test
    fun `строки разложены по группам, а не идут списком чисел`() {
        val sections = lines().mapNotNull { it.section }
        assertTrue(sections.contains(SearchRu.navWhySectionReading), "$sections")
        assertTrue(sections.contains(SearchRu.navWhySectionAgainst), "$sections")
        // Заголовок не повторяется у каждой строки своей же группы.
        assertTrue(sections.size == sections.distinct().size, "$sections")
    }
}
