package app.alpha.analysis.evidence

import kotlin.math.pow

/**
 * Временнáя правдоподобность кандидата — ОТДЕЛЬНО от спектрального вывода.
 */
enum class TemporalPlausibility {
    /** Значение по умолчанию: контекста нет, вопрос не задавался. */
    NOT_EVALUATED,

    /** Столько времени нуклид прожить мог. */
    PLAUSIBLE,

    /** За прошедшее время активность упала настолько, что видеть его странно. */
    IMPLAUSIBLE,
}

/**
 * Контекстные доказательства, живущие рядом со спектральными и НИКОГДА не
 * смешиваемые с ними.
 *
 * ## Почему период полураспада не входит в спектральный вывод
 *
 * Период полураспада — свойство нуклида, а не измерения. Включив его в общий
 * вердикт, движок начал бы отвечать «Tc-99m маловероятен» на вопрос «что
 * видно в спектре», хотя в спектре может быть отличное совпадение по линии
 * 140,5 кэВ. Правильный ответ состоит из двух независимых утверждений:
 * спектральное совпадение сильное, а временнáя правдоподобность низкая — и
 * второе целиком зависит от контекста, которого у приложения обычно нет.
 *
 * Поэтому по умолчанию здесь стоит [TemporalPlausibility.NOT_EVALUATED], и это
 * НЕ то же самое, что «правдоподобно».
 */
data class ContextEvidence(
    val temporal: TemporalPlausibility = TemporalPlausibility.NOT_EVALUATED,
    /** Сколько периодов полураспада прошло, если событие-начало известно. */
    val elapsedHalfLives: Double? = null,
) {
    companion object {

        /**
         * Сколько периодов полураспада делают присутствие нуклида
         * неправдоподобным — **инженерный параметр**. Через 10 периодов
         * остаётся 2⁻¹⁰ ≈ 0,1 % исходной активности; это соглашение об
         * «практически исчез», а не физическая граница (при большой исходной
         * активности видно и через двадцать).
         */
        const val IMPLAUSIBLE_HALF_LIVES = 10.0

        /**
         * Оценка по известному моменту появления нуклида (введение препарата,
         * авария, дата поставки источника). Без такого момента функция не
         * вызывается вовсе — [ContextEvidence] остаётся значением по умолчанию.
         */
        fun fromElapsed(elapsedSeconds: Double, halfLifeSeconds: Double): ContextEvidence {
            if (halfLifeSeconds <= 0.0 || elapsedSeconds < 0.0) return ContextEvidence()
            val halfLives = elapsedSeconds / halfLifeSeconds
            val plausibility = if (halfLives > IMPLAUSIBLE_HALF_LIVES) {
                TemporalPlausibility.IMPLAUSIBLE
            } else {
                TemporalPlausibility.PLAUSIBLE
            }
            return ContextEvidence(plausibility, halfLives)
        }

        /** Доля оставшейся активности — 2^(−t/T½); нужна для объяснения вывода. */
        fun remainingFraction(elapsedHalfLives: Double): Double = 0.5.pow(elapsedHalfLives)
    }
}
