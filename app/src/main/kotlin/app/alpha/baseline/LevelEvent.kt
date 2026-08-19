package app.alpha.baseline

import kotlin.math.max
import kotlin.math.min

/**
 * Чем событие журнала оказалось: превышением ПОРОГА или изменением УРОВНЯ.
 *
 * Раньше и то и другое называлось «Отклонение», хотя это разные утверждения.
 * Порог — это число, которое человек назначил сам, и его пересечение — факт
 * относительно этого числа. Изменение уровня — утверждение относительно того,
 * что в этом месте обычно, и порога может не касаться вовсе.
 */
enum class LevelEventKind {
    /** Значение достигло назначенного порога L1. */
    THRESHOLD,

    /** Значение вышло за обычное для места, порога не достигнув. */
    LEVEL_CHANGE,
}

/**
 * Один эпизод: интервал, а не точка.
 *
 * Журнал обязан хранить эпизод целиком — когда начался, когда закончился, в
 * каких пределах шёл, — потому что именно это человек и хочет прочитать через
 * час. Точечная запись на каждое срабатывание превращала журнал в лог
 * детектора: за сутки набегали десятки одинаковых строк, между которыми не
 * было разницы.
 *
 * Значения в микрозивертах в час; [sampleCount] — сколько отсчётов вошло.
 */
data class LevelEvent(
    val kind: LevelEventKind,
    /** Начало эпизода — момент, когда условие выполнилось ВПЕРВЫЕ. */
    val startMillis: Long,
    /** Последний отсчёт эпизода. */
    val lastMillis: Long,
    /** Конец; null — эпизод ещё идёт. */
    val endMillis: Long? = null,
    val minMicroSvH: Float,
    val maxMicroSvH: Float,
    /** Сумма значений — из неё среднее; хранить среднее нельзя, его нечем обновлять. */
    val sumMicroSvH: Double,
    val sampleCount: Int,
    /** Обычный верх места на момент эпизода; null — фон не изучен. */
    val baselineHighMicroSvH: Float?,
    /** Назначенный порог L1 на момент эпизода. */
    val thresholdMicroSvH: Float,
) {

    /** Среднее по эпизоду — представительное значение. */
    val meanMicroSvH: Float
        get() = if (sampleCount > 0) (sumMicroSvH / sampleCount).toFloat() else maxMicroSvH

    /** Длительность, мс: до конца, а у идущего — до последнего отсчёта. */
    val durationMillis: Long get() = (endMillis ?: lastMillis) - startMillis

    /** Идёт ли эпизод прямо сейчас. */
    val active: Boolean get() = endMillis == null

    /**
     * Во сколько раз среднее эпизода выше обычного верха места; null — фон не
     * изучен, и отношению не от чего считаться.
     */
    val ratioToBaseline: Float?
        get() {
            val baseline = baselineHighMicroSvH ?: return null
            if (baseline <= 0f) return null
            return meanMicroSvH / baseline
        }

    internal fun withSample(nowMillis: Long, microSvH: Float, escalate: Boolean) = copy(
        kind = if (escalate) LevelEventKind.THRESHOLD else kind,
        lastMillis = nowMillis,
        minMicroSvH = min(minMicroSvH, microSvH),
        maxMicroSvH = max(maxMicroSvH, microSvH),
        sumMicroSvH = sumMicroSvH + microSvH,
        sampleCount = sampleCount + 1,
    )
}

/** Что случилось с эпизодом на этом отсчёте. */
sealed interface LevelEventTransition {

    /** Ничего: либо спокойно, либо эпизод ещё набирает выдержку. */
    data object None : LevelEventTransition

    /** Эпизод подтверждён и открыт. */
    data class Opened(val event: LevelEvent) : LevelEventTransition

    /** Эпизод продолжается: обновились пределы, длительность, иногда и вид. */
    data class Updated(val event: LevelEvent) : LevelEventTransition

    /** Возврат подтверждён, эпизод закрыт. */
    data class Closed(val event: LevelEvent) : LevelEventTransition
}

/**
 * Жизненный цикл эпизода: спокойно → кандидат → подтверждён → возврат → закрыт.
 *
 * ## Почему не хватало прежнего трекера
 *
 * [PersistenceTracker] уже стрелял ровно один раз за превышение — с этим
 * порядок. Не хватало ДРУГОГО конца: условие снятия совпадало с условием
 * срабатывания, и значение, гуляющее около порога, давало эпизод за эпизодом.
 * Отсюда десятки одинаковых строк в журнале при спокойном фоне.
 *
 * ## Гистерезис берётся из данных, а не из константы
 *
 * Эпизод закрывается, когда значение вернулось ВНУТРЬ обычного для этого места
 * ([Baseline.doseHighMicroSvH], P90 профиля) и продержалось там ту же выдержку,
 * какую требует подтверждение тревоги. Ширина петли гистерезиса, таким
 * образом, задана разбросом самого места, а не выдуманным процентом: там, где
 * фон спокойный, петля узкая, где гуляет — широкая. Без изученного фона
 * остаётся условие тревоги: закрываем, когда оно перестало выполняться на ту
 * же выдержку.
 *
 * ## Вид эпизода
 *
 * Решается тем же условием, что и тревога ([deviationMagnitude]): достигнут
 * назначенный порог — [LevelEventKind.THRESHOLD], сработало только отношение к
 * обычному — [LevelEventKind.LEVEL_CHANGE]. Эпизод, начавшийся как изменение
 * уровня и дошедший до порога, становится превышением порога: два события об
 * одном физическом эпизоде journal не пишет.
 *
 * @param persistenceMillis выдержка подтверждения — из настроек тревоги
 * @param recoveryMillis выдержка возврата; по умолчанию та же
 * @param gapToleranceMillis провал условия короче этого не рвёт эпизод
 */
class LevelEventTracker(
    private val persistenceMillis: Long,
    private val recoveryMillis: Long = persistenceMillis,
    private val gapToleranceMillis: Long = PersistenceTracker.DEFAULT_GAP_TOLERANCE_MILLIS,
) {

    private val rise = PersistenceTracker(persistenceMillis, gapToleranceMillis)

    /** Копится с первого отсчёта над условием; выбрасывается, если не подтвердилось. */
    private var pending: LevelEvent? = null

    /** Подтверждённый эпизод; null — сейчас спокойно. */
    private var current: LevelEvent? = null

    /** С какого момента значение непрерывно внутри обычного. */
    private var calmSince: Long? = null

    /**
     * Каким эпизод был в момент возврата.
     *
     * Отсчёты выдержки возврата лежат УЖЕ ВНЕ эпизода, и их пределы в него не
     * входят: иначе минимум закрытого эпизода равнялся бы спокойному фону, а
     * не самому низкому значению превышения. Если значение снова поднялось, не
     * дождавшись конца выдержки, снимок выбрасывается — та впадина была внутри
     * эпизода.
     */
    private var calmSnapshot: LevelEvent? = null

    val active: LevelEvent? get() = current

    fun onSample(
        nowMillis: Long,
        microSvH: Float,
        baselineHighMicroSvH: Float?,
        thresholds: AlarmThresholds,
    ): LevelEventTransition {
        val magnitude = deviationMagnitude(microSvH, baselineHighMicroSvH, thresholds)
        val overThreshold = microSvH >= thresholds.l1MicroSvH

        val assessment = rise.onSample(nowMillis, magnitude)
        val open = current
        if (open != null) {
            val updated = open.withSample(nowMillis, microSvH, escalate = overThreshold)
            current = updated
            // Возврат: внутрь обычного (или ниже условия тревоги, если обычное
            // неизвестно) и продержаться выдержку возврата.
            val calm = recovered(microSvH, baselineHighMicroSvH, magnitude)
            if (!calm) {
                calmSince = null
                calmSnapshot = null
                return LevelEventTransition.Updated(updated)
            }
            val since = calmSince ?: nowMillis.also {
                calmSince = it
                calmSnapshot = open
            }
            if (nowMillis - since < recoveryMillis) return LevelEventTransition.Updated(updated)
            // Конец эпизода — момент, когда значение вернулось, а не когда это
            // подтвердилось: иначе выдержка возврата попадала бы внутрь
            // эпизода и завышала его длительность.
            val closed = (calmSnapshot ?: updated).copy(endMillis = since)
            current = null
            calmSince = null
            calmSnapshot = null
            pending = null
            rise.reset()
            return LevelEventTransition.Closed(closed)
        }

        if (!magnitude) {
            if (assessment.state is PersistenceTracker.State.Idle) pending = null
            return LevelEventTransition.None
        }

        val since = when (val state = assessment.state) {
            is PersistenceTracker.State.Building -> state.sinceMillis
            is PersistenceTracker.State.Confirmed -> state.sinceMillis
            PersistenceTracker.State.Idle -> nowMillis
        }
        val started = pending?.takeIf { it.startMillis == since }
        pending = started?.withSample(nowMillis, microSvH, escalate = overThreshold)
            ?: LevelEvent(
                kind = if (overThreshold) {
                    LevelEventKind.THRESHOLD
                } else {
                    LevelEventKind.LEVEL_CHANGE
                },
                startMillis = since,
                lastMillis = nowMillis,
                minMicroSvH = microSvH,
                maxMicroSvH = microSvH,
                sumMicroSvH = microSvH.toDouble(),
                sampleCount = 1,
                baselineHighMicroSvH = baselineHighMicroSvH,
                thresholdMicroSvH = thresholds.l1MicroSvH,
            )

        if (!assessment.fired) return LevelEventTransition.None
        val confirmed = pending ?: return LevelEventTransition.None
        current = confirmed
        calmSince = null
        calmSnapshot = null
        return LevelEventTransition.Opened(confirmed)
    }

    /**
     * Закрыть идущий эпизод принудительно — обрыв связи, остановка службы.
     *
     * Незакрытый эпизод в журнале означал бы «идёт до сих пор» для эпизода,
     * за которым никто больше не следит.
     */
    fun closeNow(endMillis: Long): LevelEventTransition {
        val open = current ?: return LevelEventTransition.None
        current = null
        pending = null
        calmSince = null
        calmSnapshot = null
        rise.reset()
        return LevelEventTransition.Closed(open.copy(endMillis = endMillis))
    }

    private fun recovered(
        microSvH: Float,
        baselineHighMicroSvH: Float?,
        magnitude: Boolean,
    ): Boolean {
        if (magnitude) return false
        if (baselineHighMicroSvH == null || baselineHighMicroSvH <= 0f) return true
        return microSvH <= baselineHighMicroSvH
    }
}
