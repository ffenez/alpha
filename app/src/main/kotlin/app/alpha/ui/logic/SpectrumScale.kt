package app.alpha.ui.logic

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Как вертикальная ось спектра превращает импульсы в высоту.
 *
 * ## Зачем три режима
 *
 * У спектра сцинтиллятора континуум на низких энергиях на порядки выше пиков
 * на высоких, и один масштаб не показывает обе вещи сразу:
 *
 *  - **линейный** честно передаёт отношение площадей, но прижимает всё, кроме
 *    самой высокой части, к нулю;
 *  - **логарифмический** показывает и одиночные отсчёты, и фотопик, но
 *    зрительно уравнивает величины, различающиеся в разы;
 *  - **степенной** (`y = (v/top)^(1/n)`) — промежуточный: при n = 2 это
 *    привычный в гамма-спектрометрии корень, при n = 1 он совпадает с
 *    линейным, а при больших n приближается по виду к логарифмическому, не
 *    становясь им.
 *
 * Все три — **монотонные преобразования одного и того же числа**: ни один не
 * добавляет и не убирает данных, меняется только распределение высоты. Именно
 * поэтому режим называется на экране: без имени высота столбца ничего не
 * значит.
 *
 * Чистый JVM, тестируется.
 */
sealed interface SpectrumScale {

    /** Доля высоты поля для значения, 0..1. */
    fun fraction(value: Float, top: Float): Float

    /** Значение → подпись оси; значения выбирает [ticks]. */
    fun ticks(top: Float): List<Float>

    /** Короткое имя режима для экрана. */
    val id: String

    data object Linear : SpectrumScale {
        override val id = "linear"

        override fun fraction(value: Float, top: Float): Float =
            if (top <= 0f) 0f else (value / top).coerceIn(0f, 1f)

        /** Четверти: три линии внутри поля, края подписаны самим полем. */
        override fun ticks(top: Float): List<Float> = (1..3).map { top * it / 4f }
    }

    /**
     * Степенной масштаб `(v/top)^(1/root)`.
     *
     * [root] = 1 совпадает с линейным, поэтому лестница начинается с единицы:
     * человек, двигая ползунок от края, видит непрерывный переход, а не
     * скачок в другой режим.
     */
    data class Power(val root: Int) : SpectrumScale {
        override val id = "power"

        private val exponent: Float get() = 1f / root.coerceIn(MIN_ROOT, MAX_ROOT)

        override fun fraction(value: Float, top: Float): Float {
            if (top <= 0f || value <= 0f) return 0f
            return (value / top).coerceIn(0f, 1f).pow(exponent)
        }

        /**
         * Подписи стоят на РАВНЫХ расстояниях по высоте, а значения при этом
         * получаются неравномерными — так и должно быть: неравномерна сама
         * шкала, и подписи обязаны это показывать.
         */
        override fun ticks(top: Float): List<Float> =
            (1..3).map { top * (it / 4f).pow(root.coerceIn(MIN_ROOT, MAX_ROOT).toFloat()) }
    }

    /** Декадный: 1, 10, 100 … до [top]. */
    data object Log : SpectrumScale {
        override val id = "log"

        override fun fraction(value: Float, top: Float): Float {
            if (top <= 0f) return 0f
            val logTop = log10(max(top, 10f).toDouble()).toFloat()
            if (logTop <= 0f) return 0f
            return (log10(max(value, FLOOR).toDouble()).toFloat() / logTop).coerceIn(0f, 1f)
        }

        override fun ticks(top: Float): List<Float> {
            val decades = max(1, log10(max(top, 10f).toDouble()).toInt())
            return (0..decades).map { 10f.pow(it) }
        }

        /**
         * Тонкая сетка внутри декады: 2·10ᵏ … 9·10ᵏ.
         *
         * Без неё логарифмическая ось читается как линейная: расстояние между
         * 1 и 10 такое же, как между 10 и 100, и глазу не за что зацепиться
         * внутри декады — 30 и 80 выглядят одинаково «где-то посередине».
         * Промежуточные линии рисуются тоньше основных: они помогают читать
         * положение, но не спорят с подписанными делениями.
         */
        fun minorTicks(top: Float): List<Float> {
            val decades = max(1, log10(max(top, 10f).toDouble()).toInt())
            val out = ArrayList<Float>(decades * 8)
            for (decade in 0 until decades) {
                val base = 10f.pow(decade)
                for (mantissa in 2..9) {
                    val value = base * mantissa
                    if (value < top) out += value
                }
            }
            return out
        }

        /** Ниже этого значения лог-ось не опускается (один отсчёт = низ). */
        const val FLOOR = 1f
    }

    companion object {
        const val MIN_ROOT = 1
        const val MAX_ROOT = 10

        /** Восстановление режима из настроек; неизвестное — логарифм. */
        fun of(id: String?, root: Int): SpectrumScale = when (id) {
            Linear.id -> Linear
            Power(root).id -> Power(root.coerceIn(MIN_ROOT, MAX_ROOT))
            else -> Log
        }
    }
}
