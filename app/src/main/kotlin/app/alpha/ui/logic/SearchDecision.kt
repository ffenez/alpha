package app.alpha.ui.logic

import app.alpha.analysis.DecisionTime
import kotlin.math.abs

/**
 * Сколько копить ЭТО решение.
 *
 * Одинаковое окно для всех случаев неверно с обеих сторон: явное превышение
 * оно заставляет подтверждать так же долго, как еле заметное, а еле заметное
 * объявляет неразрешимым раньше, чем набралось хоть что-то. Время должно
 * следовать из того, что видно: чем меньше отличие и чем ниже счёт, тем
 * дольше.
 *
 * Считает это общая формула `t = 2k²/(p²·R)` ([DecisionTime]) — та же, что у
 * скрининга продукта. Здесь только границы и то, что из них следует для
 * экрана: сколько ещё ждать и стоит ли вообще ждать дальше.
 *
 * Чего окно НЕ делает: оно не решает, есть ли отличие. Это по-прежнему дело
 * критерия и лестницы подтверждений — окно лишь говорит, на скольких секундах
 * этот вопрос имеет смысл задавать.
 */
object SearchDecision {

    /**
     * Минимальное окно, с. **Инженерный параметр**: за пять секунд при 25 с⁻¹
     * набирается около 125 импульсов — этого хватает, чтобы заметить кратное
     * превышение, ради которого человек и водит прибором.
     */
    const val MIN_SECONDS = 5L

    /**
     * Предел ожидания, с. Дальше окно не растёт: отличие тоньше того, что за
     * это время различимо, — не событие поиска, и держать человека дольше
     * значит обещать чувствительность, которой у ходьбы с прибором нет.
     */
    const val MAX_SECONDS = 120L

    /**
     * Насколько тонкую добавку окно вообще берётся ловить, долей от фона.
     * Ниже этого отличие считается неразрешимым за разумное время.
     */
    const val MIN_FRACTION = 0.02

    /** Во сколько σ отличие считается различимым при подборе окна. */
    const val SIGMA = 3.0

    data class Window(
        /** Сколько нужно копить при таком фоне и таком отличии, с. */
        val targetSeconds: Long,
        /** Сколько уже набрано, с. */
        val collectedSeconds: Long,
        /** Упёрлось ли окно в предел ожидания. */
        val atLimit: Boolean,
    ) {
        val remainingSeconds: Long get() = (targetSeconds - collectedSeconds).coerceAtLeast(0L)
        val ready: Boolean get() = collectedSeconds >= targetSeconds
        val progress: Float
            get() = if (targetSeconds <= 0L) {
                1f
            } else {
                (collectedSeconds.toFloat() / targetSeconds).coerceIn(0f, 1f)
            }
    }

    /**
     * @param backgroundCps фоновая скорость счёта, с⁻¹.
     * @param observedFraction наблюдаемое отличие долей от фона; null — пока
     *   не видно ничего, и окно берётся по минимальной ловимой добавке.
     * @param collectedSeconds сколько уже набрано в текущем окне.
     */
    fun of(
        backgroundCps: Double,
        observedFraction: Double?,
        collectedSeconds: Long,
    ): Window {
        val fraction = observedFraction
            ?.let { abs(it) }
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: MIN_FRACTION
        // Тоньше порога не ловим: считать «нужно ещё 40 минут» и держать
        // человека на месте — это не помощь, а вид отказа, выданный за работу.
        val target = DecisionTime
            .secondsFor(backgroundCps, fraction.coerceAtLeast(MIN_FRACTION), SIGMA)
            ?: MAX_SECONDS
        val clamped = target.coerceIn(MIN_SECONDS, MAX_SECONDS)
        return Window(
            targetSeconds = clamped,
            collectedSeconds = collectedSeconds.coerceAtLeast(0L),
            atLimit = target > MAX_SECONDS,
        )
    }
}
