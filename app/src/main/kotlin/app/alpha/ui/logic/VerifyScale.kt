package app.alpha.ui.logic

/**
 * Шкала прибора на «Проверке»: та же дуга, что в «Наведении», но с ДРУГИМ
 * знаменателем.
 *
 * В «Наведении» отношение считается от точки отсчёта, которую человек поставил
 * рукой; здесь — от записанного фона места, и это разные вопросы. Общего у них
 * ровно одно: положение на логарифмической шкале отношений, поэтому рисунок и
 * его геометрия ([NavigateArc]) переиспользуются целиком, а знаменатель
 * называется подписью под ×1.
 *
 * Здесь только арифметика кадра и выбор направления: цвет, размеры и текст
 * живут в компоненте и каталогах.
 */
object VerifyScale {

    /**
     * Кадр шкалы: наименьшая ступень лестницы, в которую помещаются и само
     * отношение, и оба конца его интервала.
     *
     * Интервал участвует наравне с оценкой: кадр, в который влезла стрелка, но
     * не влезла её неопределённость, показывал бы измерение точнее, чем оно
     * есть. Нечего показывать — начальная ступень: шкала стоит пустой.
     */
    fun requiredFactor(ratio: Double?, low: Double? = null, high: Double? = null): Double {
        val ratios = listOfNotNull(ratio, low, high).filter { it.isFinite() && it > 0.0 }
        if (ratios.isEmpty()) return NavigateArc.LADDER.first()
        return NavigateArc.requiredFactor(ratios)
    }

    /**
     * Направление, которым красится стрелка.
     *
     * Красит только ПОДТВЕРЖДЁННОЕ отличие: пока лестница не дошла до
     * подтверждения, различие есть на картинке, но приговор ему не вынесен, и
     * цвет тревоги был бы этим приговором. Набор подтверждения показывает
     * отдельная полоска, а не стрелка.
     */
    fun trend(level: SearchLevel): NavigateTrend = when (level) {
        SearchLevel.UNKNOWN -> NavigateTrend.COLLECTING
        SearchLevel.BACKGROUND, SearchLevel.POSSIBLE_CHANGE -> NavigateTrend.NO_CHANGE
        SearchLevel.CONFIRMED_EXCESS -> NavigateTrend.RISING
        SearchLevel.CONFIRMED_DEFICIT -> NavigateTrend.FALLING
    }
}
