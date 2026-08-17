package app.alpha.ui.chart

import app.alpha.ui.logic.ChartRange

/**
 * Откуда открыт график — и чем из-за этого отличается его поведение.
 *
 * ## Зачем называть это типом
 *
 * Отличие всегда было одно и то же — КРАЙ ВРЕМЕНИ, — но записывалось оно
 * «диапазон есть или его нет». Из такого признака не следует ни подпись чипа
 * возврата, ни то, что показывать курсору: маршрут и сессия — оба «диапазон
 * есть», а возвращают они к разным вещам. Поэтому контекст назван, и каждое
 * различие висит на нём явно (ТЗ §22).
 *
 * ## Чего здесь нет
 *
 * Второй математики. Окно, жесты, конверты, курсор и статистика у всех
 * контекстов одни: две реализации одного графика однажды уже разошлись между
 * Главной и полным экраном, и человек искал на большом то, что видел на
 * маленьком.
 */
sealed interface ChartContext {

    /** Диапазон, к которому привязан график; null — окно едет за «сейчас». */
    val range: ChartRange?

    /** Живой график: Главная, полноэкранный по тапу с карточки. */
    data object Live : ChartContext {
        override val range: ChartRange? get() = null
    }

    /** Сессия из Истории: неизменный отрезок времени, «сейчас» к нему не относится. */
    data class Session(override val range: ChartRange) : ChartContext

    /** Записанный маршрут: тот же отрезок, но возвращает к маршруту, а не к сессии. */
    data class Route(override val range: ChartRange) : ChartContext

    /**
     * Экран Поиска: живая скорость счёта, у которой ЕСТЬ записанный фон.
     *
     * Курсор здесь называет отношение к фону поиска — вопрос, ради которого
     * график и открывают из Поиска: «во сколько раз здесь больше, чем там, где
     * я мерил фон». Само число берётся из движка Поиска, а не из графика:
     * второй фон, посчитанный по-своему, спорил бы с тем, что говорит экран.
     */
    data object Search : ChartContext {
        override val range: ChartRange? get() = null
    }
}

/** Разбор и сборка контекста для навигации: она хранит строку, а не тип. */
object ChartContexts {

    const val LIVE = "live"
    const val SESSION = "session"
    const val ROUTE = "route"
    const val SEARCH = "search"

    fun id(context: ChartContext): String = when (context) {
        ChartContext.Live -> LIVE
        is ChartContext.Session -> SESSION
        is ChartContext.Route -> ROUTE
        ChartContext.Search -> SEARCH
    }

    /**
     * Контекст по сохранённому идентификатору и диапазону.
     *
     * Диапазона нет — контекст живой, чем бы он ни назывался: рисовать сессию
     * без её отрезка времени нечем, и притворяться, что он есть, хуже, чем
     * честно показать живой край.
     */
    fun of(id: String?, fromMillis: Long?, toMillis: Long?): ChartContext {
        val range = if (fromMillis != null && toMillis != null) {
            ChartRange(fromMillis, toMillis)
        } else {
            null
        }
        return when {
            range == null && id == SEARCH -> ChartContext.Search
            range == null -> ChartContext.Live
            id == ROUTE -> ChartContext.Route(range)
            else -> ChartContext.Session(range)
        }
    }
}
