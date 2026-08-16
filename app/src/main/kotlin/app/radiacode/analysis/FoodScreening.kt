package app.radiacode.analysis

import kotlin.math.sqrt

/**
 * Скрининг продукта: сравнение образца с фоном, снятым в той же геометрии.
 *
 * ## Что это и чего это не
 *
 * Сцинтилляционный спектрометр может увидеть ГАММА-излучающую добавку к фону и
 * спектральные отличия. Он не может доказать отсутствие радионуклидов и не
 * может назвать Бк/кг без валидированной эффективностной калибровки под
 * конкретную геометрию и матрицу (IAEA TRS-295; EPA MARLAP гл. 15). Поэтому
 * здесь нет ни слова «безопасно», ни числа в беккерелях: результат — сравнение
 * двух счётов и форм спектра, и он так и называется.
 *
 * ## Как считается
 *
 * Статистика — та же, что у A/B (`AbAnalysis`): вычитание фона с приведением
 * по времени и двухвыборочный пуассоновский критерий. Второго движка сравнения
 * счётов в приложении нет и не заводится.
 *
 * Сверх этого скрининг отвечает на два вопроса, которых у A/B не было:
 * **что вообще было бы заметно** при таком фоне и такой выдержке, и **сколько
 * копить**, чтобы заметить заданную добавку. Без них «отличий не найдено» —
 * пустая фраза: за минуту не найдётся почти ничего.
 */
object FoodScreening {

    /**
     * Во сколько σ считается заметной добавка при оценке чувствительности.
     *
     * **Инженерный параметр**: 3σ — обычный порог принятия решения в
     * низкофоновом счёте. Он НЕ участвует в самом выводе (там работает
     * критерий `AbAnalysis`), а отвечает на вопрос «что было бы видно», и
     * поэтому взят строже вердиктного 2σ: обещать чувствительность на грани
     * значимости нечестно.
     */
    const val SENSITIVITY_SIGMA = 3.0

    /** Ниже этого числа импульсов в любом из прогонов говорить не о чем. */
    const val MIN_USABLE_COUNTS = 100.0

    /** Что именно вышло. Порядок — от «ничего не увидели» к «есть признак». */
    enum class Verdict {
        /** Данных мало: вывод не делается вовсе. */
        NOT_ENOUGH_DATA,

        /** Значимого превышения над фоном не набралось. */
        NO_DIFFERENCE,

        /** Счёт устойчиво выше фонового, отдельной линии не выделено. */
        EXCESS_WITHOUT_LINE,

        /** Кроме превышения, в разностном спектре есть линия. */
        SPECTRAL_FEATURE,
    }

    /**
     * Найденная в разностном спектре линия — как её отдаёт движок пиков.
     *
     * Здесь она НЕ идентифицируется: совпадение энергии с известной линией это
     * гипотеза, и решение о нуклиде принимает движок доказательств (ADR 006),
     * а не скрининг продукта.
     */
    data class Line(val energyKev: Float, val significance: Double)

    /** Насколько тонкую добавку это измерение вообще способно заметить. */
    data class Sensitivity(
        /** Минимальная заметная добавка к скорости счёта, имп/с. */
        val detectableCps: Double,
        /** Она же — долей от фоновой скорости счёта (0,1 = 10 %). */
        val detectableFraction: Double?,
    )

    data class Result(
        val verdict: Verdict,
        val comparison: AbAnalysis.Comparison?,
        val sensitivity: Sensitivity?,
        val lines: List<Line>,
        /** Импульсов в прогонах — то, чем измерение обеспечено. */
        val backgroundCounts: Double,
        val sampleCounts: Double,
    ) {
        val enoughData: Boolean get() = verdict != Verdict.NOT_ENOUGH_DATA
    }

    /**
     * @param background счёт фона в той же геометрии без образца.
     * @param sample счёт с образцом.
     * @param lines значимые линии РАЗНОСТНОГО спектра, если движок пиков их
     *   нашёл; пустой список — не нашёл или спектра не было.
     */
    fun screen(
        background: AbAnalysis.Counting,
        sample: AbAnalysis.Counting,
        lines: List<Line> = emptyList(),
    ): Result {
        val comparison = AbAnalysis.compareCounts(
            label = "total",
            a = sample,
            b = background,
        )
        val sensitivity = sensitivity(background, sample)
        val enough = background.counts >= MIN_USABLE_COUNTS &&
            sample.counts >= MIN_USABLE_COUNTS &&
            comparison != null
        val verdict = when {
            !enough -> Verdict.NOT_ENOUGH_DATA
            comparison!!.verdict == AbAnalysis.Verdict.CONSISTENT -> Verdict.NO_DIFFERENCE
            // Превышение вниз — это не «продукт чище фона», а признак того,
            // что условия между прогонами изменились: фон снят не там же или
            // не так же. Различие названо, но линией оно не бывает.
            comparison.net <= 0.0 -> Verdict.EXCESS_WITHOUT_LINE
            lines.isNotEmpty() -> Verdict.SPECTRAL_FEATURE
            else -> Verdict.EXCESS_WITHOUT_LINE
        }
        return Result(
            verdict = verdict,
            comparison = comparison,
            sensitivity = sensitivity,
            lines = lines,
            backgroundCounts = background.counts,
            sampleCounts = sample.counts,
        )
    }

    /**
     * Минимальная добавка, которую эта пара прогонов способна показать.
     *
     * При нулевой добавке ожидаемый счёт образца равен фоновому с поправкой на
     * время: G ≈ B·r, r = t_G/t_B. Тогда σ разности ≈ √(B·r·(1+r)), и заметной
     * считается добавка в [SENSITIVITY_SIGMA] таких σ, пересчитанная в
     * скорость счёта образца.
     */
    fun sensitivity(
        background: AbAnalysis.Counting,
        sample: AbAnalysis.Counting,
    ): Sensitivity? {
        if (background.seconds <= 0.0 || sample.seconds <= 0.0) return null
        if (background.counts <= 0.0) return null
        val ratio = sample.seconds / background.seconds
        val sigmaCounts = sqrt(background.counts * ratio * (1.0 + ratio))
        val detectableCps = SENSITIVITY_SIGMA * sigmaCounts / sample.seconds
        val backgroundRate = background.rateCps
        return Sensitivity(
            detectableCps = detectableCps,
            detectableFraction = if (backgroundRate > 0.0) {
                detectableCps / backgroundRate
            } else {
                null
            },
        )
    }

    /**
     * Сколько копить, чтобы заметить добавку в [fraction] от фона.
     *
     * Из того же выражения при равных выдержках (t_B = t_G = t):
     * σ_R = √(2·R_B/t), заметное — k·σ_R, приравниваем к p·R_B и получаем
     * t = 2·k²/(p²·R_B). Отсюда и «минимум 20 минут» на экране: это не
     * произвольное круглое число, а следствие фоновой скорости счёта.
     *
     * @return секунды НА КАЖДЫЙ прогон; null — фон неизвестен.
     */
    fun recommendedSeconds(
        backgroundRateCps: Double,
        fraction: Double,
        sigma: Double = SENSITIVITY_SIGMA,
    ): Long? {
        if (backgroundRateCps <= 0.0 || fraction <= 0.0) return null
        val seconds = 2.0 * sigma * sigma / (fraction * fraction * backgroundRateCps)
        if (!seconds.isFinite()) return null
        // Вверх, а не отбрасыванием дробной части: это рекомендованный
        // МИНИМУМ, и «копите 287 с» вместо 288 — обещание на секунду короче
        // того, что нужно. Заодно уходит и дрожание последнего разряда.
        return kotlin.math.ceil(seconds).toLong().coerceAtLeast(1L)
    }
}
