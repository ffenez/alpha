package app.alpha.ui.logic

/**
 * Итог за выбранный период — то, что экран накопленной дозы показывает главным
 * числом.
 *
 * ## Почему период считается по суткам графика
 *
 * Раньше карточка сверху складывала скользящее окно «последние 7×24 ч», а
 * столбики рисовались по КАЛЕНДАРНЫМ суткам. Два разных определения одного
 * периода на одном экране: сумма столбиков не сходилась с числом над ними, и
 * переключатель под графиком не менял верхнюю карточку вовсе. Здесь период —
 * это ровно те сутки, что нарисованы, поэтому число и картинка сходятся по
 * построению.
 *
 * ## Чего в этом числе нет
 *
 * Дней без измерений. Пропущенные сутки не добавляют нуля — их просто нет в
 * сумме, и поэтому число нельзя читать как «доза за месяц»: это доза за
 * измеренное время внутри месяца. Отсюда [measuredSeconds] и [coverage] стоят
 * рядом с ним, а не в справке.
 */
data class DosePeriod(
    /** Длина периода в сутках: 7, 30, 90. */
    val days: Int,
    /** Сумма по суткам периода, мкЗв. */
    val microSv: Double,
    /** Сколько секунд периода реально измерено. */
    val measuredSeconds: Long,
    /** Сколько секунд периода уже прошло — знаменатель покрытия. */
    val elapsedSeconds: Long,
    /** Сутки периода, старые первыми — ровно те, что нарисованы. */
    val daily: List<DailyDose.Day>,
) {

    /**
     * Доля периода, покрытая измерениями, 0..1.
     *
     * Знаменатель — ПРОШЕДШАЯ часть периода, а не его номинальная длина: у
     * сегодняшнего дня прошли не все сутки, и делить на полные значило бы
     * занижать покрытие каждый день.
     */
    val coverage: Float
        get() = if (elapsedSeconds > 0) {
            (measuredSeconds.toDouble() / elapsedSeconds).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

    /** Сутки с измерениями — их столько, сколько столбиков на картинке. */
    val measuredDays: Int get() = daily.count { it.measuredSeconds > 0 }

    /**
     * Средняя доза за ПОЛНОСТЬЮ измеренные сутки; null — таких суток нет.
     *
     * Считается только по полным дням ([DailyDose.Day.full]) намеренно: день,
     * записанный двадцать минут, даёт крошечную дозу не потому, что уровень
     * был низким, и в среднем он занижал бы результат тем сильнее, чем чаще
     * прибор выключали.
     */
    val averageFullDayMicroSv: Float?
        get() {
            val full = daily.filter { it.full }
            if (full.isEmpty()) return null
            return (full.sumOf { it.microSv.toDouble() } / full.size).toFloat()
        }

    /** Максимум за сутки среди измеренных; null — измерений нет. */
    val maxDayMicroSv: Float?
        get() = daily.filter { it.measuredSeconds > 0 }.maxOfOrNull { it.microSv }

    /**
     * Короче ли история самого периода.
     *
     * Пока измерений всего за пару суток, «за 7», «за 30» и «за 90 дней» дают
     * ОДНО И ТО ЖЕ число — и это верно: складывать больше нечего. Но три
     * одинаковых значения читаются как поломка, поэтому экран обязан сказать,
     * за сколько суток измерения вообще есть.
     */
    val shorterThanPeriod: Boolean get() = measuredDays in 1 until days

    /**
     * Примерная доза за год при таком же уровне; null — считать не из чего.
     *
     * Экстраполяция средних ПОЛНЫХ суток на 365 дней. Это не измеренная доза
     * и не прогноз: утверждение здесь одно — «если уровень останется таким и
     * прибор будет с вами всё время, за год наберётся столько». Без хотя бы
     * одних полных суток числа нет: растягивать на год день, записанный
     * двадцать минут, значит выдавать за оценку случайность.
     */
    val projectedYearMicroSv: Float?
        get() = averageFullDayMicroSv?.let { it * DAYS_PER_YEAR }

    private companion object {
        /** Суток в году для пересчёта средних суток в годовую оценку. */
        const val DAYS_PER_YEAR = 365f
    }
}

object DosePeriods {

    /** Периоды переключателя, сутки. */
    val LENGTHS = listOf(7, 30, 90)

    /**
     * Итог по последним [days] суткам из полного ряда.
     *
     * @param allDays сутки, СТАРЫЕ ПЕРВЫМИ, как их отдаёт [DailyDose.perDay]
     * @param days длина периода
     * @param todayElapsedSeconds сколько секунд сегодняшних суток уже прошло —
     *   от него зависит знаменатель покрытия
     */
    fun of(
        allDays: List<DailyDose.Day>,
        days: Int,
        todayElapsedSeconds: Long,
    ): DosePeriod {
        val window = allDays.takeLast(days)
        val elapsed = if (window.isEmpty()) {
            0L
        } else {
            // Полные сутки для всех дней периода, кроме сегодняшнего.
            (window.size - 1).toLong() * SECONDS_PER_DAY +
                todayElapsedSeconds.coerceIn(0L, SECONDS_PER_DAY)
        }
        return DosePeriod(
            days = days,
            microSv = window.sumOf { it.microSv.toDouble() },
            measuredSeconds = window.sumOf { it.measuredSeconds },
            elapsedSeconds = elapsed,
            daily = window,
        )
    }

    const val SECONDS_PER_DAY = 86_400L
}
