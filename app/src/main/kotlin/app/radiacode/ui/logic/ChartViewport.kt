package app.radiacode.ui.logic

/**
 * Состояние окна графика: что видно и следит ли картинка за «сейчас».
 *
 * ## Зачем отдельный тип
 *
 * Карточка Главной и полноэкранный график показывают ОДНИ И ТЕ ЖЕ измерения,
 * но до сих пор вели своё окно каждый по-своему. Из-за этого возможна была
 * ситуация «большой живой, мелкий замерший»: данные общие, а край двигался
 * только у одного. Пока состояние окна живёт в двух местах, оно будет
 * расходиться снова — поэтому переходы собраны здесь, в чистом виде, и
 * проверяются тестами, а не глазами на приборе.
 *
 * ## Правила
 *
 * - **Зум ступенчатый.** Щипок переводит на соседнюю ступень лестницы, а не
 *   плодит произвольные интервалы: ширина колонки, метод квантилей и
 *   агрегация должны быть предсказуемы, иначе одно и то же место истории
 *   выглядит по-разному после каждого жеста.
 * - **Зум меняет ВРЕМЯ, а не картинку.** Из состояния получается окно, окно
 *   идёт в загрузку и в кадр; готовое изображение не растягивается.
 * - **Ушли от «сейчас» — слежение выключается.** Иначе график вырывался бы
 *   из-под пальца при каждом новом отсчёте.
 * - **Вернулись к правому краю — слежение включается само.** Отдельного
 *   действия для этого не нужно: возврат к «сейчас» и есть просьба следить.
 */
data class ChartViewport(
    /** Индекс ступени в [ChartWindows.PERIODS]. */
    val stepIndex: Int,
    /** Правый край окна; имеет смысл только когда [follow] == false. */
    val endMillis: Long,
    /** Держится ли окно живого края. */
    val follow: Boolean,
) {

    fun spanMillis(): Long = ChartWindows.PERIODS[stepIndex].second

    /** Окно для загрузки и кадра. Пока следим — правый край это «сейчас». */
    fun window(nowMillis: Long): ChartWindow =
        ChartWindows.latest(spanMillis(), if (follow) nowMillis else endMillis)

    /**
     * Накопитель щипка.
     *
     * `detectTransformGestures` отдаёт множитель ЗА СОБЫТИЕ — за кадр пальцы
     * расходятся на пару процентов, и порог «развести в полтора раза» не
     * срабатывал никогда: жест не масштабировал вовсе. Множители кадра
     * перемножаются здесь, и ступень переключается, когда произведение
     * перешагнуло порог; после переключения счёт начинается заново, иначе один
     * долгий щипок пролетел бы всю лестницу.
     */
    class PinchAccumulator(private val threshold: Float = STEP_ZOOM_FACTOR) {

        private var product = 1f

        /** @return −1 приблизить, +1 отдалить, 0 — порог ещё не перейден. */
        fun add(frameScale: Float): Int {
            if (frameScale <= 0f || !frameScale.isFinite()) return 0
            product *= frameScale
            return when {
                product >= threshold -> {
                    product = 1f
                    -1
                }
                product <= 1f / threshold -> {
                    product = 1f
                    1
                }
                else -> 0
            }
        }

        /** Палец оторвался — незавершённое движение не переносится на следующий жест. */
        fun reset() {
            product = 1f
        }
    }

    companion object {

        /**
         * Насколько щипок должен развести пальцы, чтобы перейти на ступень.
         * **Инженерный параметр**: полтора раза — заметное движение, которое
         * не срабатывает от дрожания руки и не требует растягивать экран.
         */
        const val STEP_ZOOM_FACTOR = 1.5f

        /**
         * Ближе этого к «сейчас» окно считается стоящим на живом крае.
         * **Инженерный параметр**: доля окна, а не константа — на минутном
         * окне полсекунды это много, на суточном ничто.
         */
        const val FOLLOW_SNAP_FRACTION = 0.02f

        /** Стартовое состояние: выбранная ступень у живого края. */
        fun atLiveEdge(stepIndex: Int, nowMillis: Long): ChartViewport =
            ChartViewport(stepIndex, nowMillis, follow = true)

        /**
         * Щипок: [scale] > 1 — пальцы разошлись (приблизить, окно короче).
         *
         * Ступень меняется на ОДНУ за жест; накопление дробных множителей
         * ведёт вызывающий, иначе один плавный щипок пролетал бы всю лестницу.
         */
        fun zoom(viewport: ChartViewport, scale: Float, nowMillis: Long): ChartViewport {
            val direction = when {
                scale >= STEP_ZOOM_FACTOR -> -1
                scale <= 1f / STEP_ZOOM_FACTOR -> 1
                else -> return viewport
            }
            return step(viewport, direction, nowMillis)
        }

        /** Переход на соседнюю ступень: −1 приблизить, +1 отдалить. */
        fun step(viewport: ChartViewport, direction: Int, nowMillis: Long): ChartViewport {
            if (direction == 0) return viewport
            val next = (viewport.stepIndex + direction)
                .coerceIn(0, ChartWindows.PERIODS.lastIndex)
            if (next == viewport.stepIndex) return viewport
            // Правый край жест не двигает: приближают, чтобы разглядеть ТО ЖЕ
            // место, а не чтобы уехать в другое время.
            val end = if (viewport.follow) nowMillis else viewport.endMillis
            return viewport.copy(stepIndex = next, endMillis = end)
        }

        /**
         * Сдвиг пальцем: [fractionOfWindow] > 0 — тянут вправо, то есть в
         * ПРОШЛОЕ (содержимое едет вслед за пальцем).
         *
         * Уехали от «сейчас» — слежение выключается; вернулись к правому краю
         * — включается снова.
         */
        fun pan(
            viewport: ChartViewport,
            fractionOfWindow: Float,
            nowMillis: Long,
        ): ChartViewport {
            val span = viewport.spanMillis()
            val from = if (viewport.follow) nowMillis else viewport.endMillis
            val end = (from - (fractionOfWindow * span).toLong()).coerceAtMost(nowMillis)
            return viewport.copy(
                endMillis = end,
                follow = nowMillis - end <= span * FOLLOW_SNAP_FRACTION,
            )
        }

        /** Возврат к живому краю: кнопка «Сейчас» и двойное нажатие. */
        fun jumpToNow(viewport: ChartViewport, nowMillis: Long): ChartViewport =
            viewport.copy(endMillis = nowMillis, follow = true)

        /** Явный выбор ступени с ленты — правый край не трогает. */
        fun withStep(viewport: ChartViewport, stepIndex: Int): ChartViewport =
            viewport.copy(stepIndex = stepIndex.coerceIn(0, ChartWindows.PERIODS.lastIndex))
    }
}
