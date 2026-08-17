package app.alpha.ui.logic

/**
 * Трасса конвейера графика: где именно исчезают точки.
 *
 * ## Зачем
 *
 * Конвейер объявлен архитектурой:
 *
 * ```
 * Room → loadSnapshot → ChartSeriesModel.snapshot → buildFrame → ChartProjection → DoseChart
 * ```
 *
 * Когда график «замирает», по картинке НЕЛЬЗЯ сказать, на каком этапе потеря, а
 * чинить последний этап (канву) по догадке — самый быстрый способ починить не
 * то. Здесь на каждый проход перечитывания записываются три среза одного и того
 * же окна: сколько строк в базе, сколько колонок дал снимок и сколько дошло до
 * кадра. Дальше вывод делается не рассуждением, а [verdict].
 *
 * Ничего, кроме счётчиков и границ времени: ни доз, ни счёта, ни координат.
 */
class ChartTrace {

    /**
     * @param metric какая величина перечитывалась.
     * @param windowStart/[windowEnd] окно, которое просили нарисовать.
     * @param roomCount строк в `samples` внутри окна (до всякой обработки).
     * @param snapshotBuckets колонок вернул снимок (после запроса и свёртки).
     * @param frameBuckets колонок дошло до кадра (после отбора по окну).
     */
    data class Pass(
        val atMillis: Long,
        val metric: String,
        val nowMillis: Long,
        val windowStart: Long,
        val windowEnd: Long,
        val roomCount: Int,
        val roomMin: Long?,
        val roomMax: Long?,
        val snapshotBuckets: Int,
        val snapshotMin: Long?,
        val snapshotMax: Long?,
        val frameBuckets: Int,
        val frameMin: Long?,
        val frameMax: Long?,
    )

    /** Этап, на котором данные пропали. */
    enum class Verdict {
        /** В базе окна пусто: вопрос к записи и меткам времени, а не к графику. */
        NO_DATA_IN_ROOM,

        /** База полна, снимок пуст: запрос, границы загрузки или путь чтения. */
        LOST_IN_SNAPSHOT,

        /** Снимок полон, кадр пуст: свёртка колонок или отбор по окну. */
        LOST_IN_FRAME,

        /** Кадр полон — потеря, если она есть, ниже: состояние Compose и канва. */
        FRAME_COMPLETE,
    }

    private val passes = ArrayDeque<Pass>()

    @Synchronized
    fun add(pass: Pass) {
        passes.addLast(pass)
        while (passes.size > CAPACITY) passes.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<Pass> = passes.toList()

    companion object {
        /**
         * Сколько проходов хранить.
         * **Инженерный параметр**: три величины × полсотни перечитываний —
         * это десятки минут наблюдения, дольше, чем человек ждёт, прежде чем
         * снять отчёт.
         */
        const val CAPACITY = 150

        /**
         * Вывод по одному проходу — без рассуждений и без «вероятно».
         *
         * Порядок проверок повторяет порядок конвейера: называется ПЕРВЫЙ
         * этап, на котором данные исчезли, потому что все последующие
         * пострадали бы по его вине.
         */
        fun verdict(pass: Pass): Verdict = when {
            pass.roomCount == 0 -> Verdict.NO_DATA_IN_ROOM
            pass.snapshotBuckets == 0 -> Verdict.LOST_IN_SNAPSHOT
            pass.frameBuckets == 0 -> Verdict.LOST_IN_FRAME
            else -> Verdict.FRAME_COMPLETE
        }

        /**
         * Отставание кадра от базы, мс: насколько последняя нарисованная
         * колонка старше последнего измерения в окне.
         *
         * Именно это число отвечает на «график обрывается до „сейчас“» в
         * случае, когда ни один этап не пуст и [verdict] говорит
         * [Verdict.FRAME_COMPLETE].
         */
        fun frameLagMillis(pass: Pass): Long? {
            val room = pass.roomMax ?: return null
            val frame = pass.frameMax ?: return null
            return (room - frame).coerceAtLeast(0L)
        }
    }
}
