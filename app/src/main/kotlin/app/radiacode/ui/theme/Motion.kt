package app.radiacode.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize

/**
 * Движение как часть дизайн-языка — токены, а не числа в экранах.
 *
 * ## Что анимируется
 *
 * Переходы **состояния интерфейса**: раскрытие блока, смена экрана, цвет
 * статуса, положение выбранного чипа, заполнение индикатора. Всё это
 * рассказывает, что одно превратилось в другое, и без движения читается как
 * подмена картинки.
 *
 * ## Что не анимируется никогда
 *
 * **Измеренные значения.** Ни главное число, ни точки графика, ни статистика
 * окна не «доезжают» до нового значения: промежуточные кадры такой анимации
 * показывают числа, которых прибор не измерял. Это ровно тот вид красивой
 * лжи, против которого написана вся остальная часть этого приложения. Меняться
 * плавно может **кадр** (ось, масштаб, цвет), но не данные внутри него.
 *
 * Длительности выбраны короткими намеренно: прибор обновляется раз в секунду,
 * и анимация, сравнимая с этим периодом, превратилась бы в постоянное
 * шевеление экрана.
 */
object Motion {

    /** Мгновенная реакция на касание: цвет чипа, нажатие кнопки. */
    const val FAST_MILLIS = 150

    /** Обычный переход: раскрытие блока, смена статуса, скролл ленты. */
    const val NORMAL_MILLIS = 280

    /** Смена экрана: уходящее гаснет быстро, приходящее приезжает мягко. */
    const val SCREEN_MILLIS = 360

    /** Часть перехода экрана, за которую уходящий кадр успевает исчезнуть. */
    const val SCREEN_EXIT_MILLIS = 110

    /**
     * Стандартная кривая: быстрый старт, мягкое торможение. Материаловская
     * «standard» — она не привлекает внимания к себе, а этого от движения
     * здесь и требуется.
     */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Появление: чуть медленнее в конце, чтобы глаз успел поймать элемент. */
    val Enter: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Исчезновение: быстрее, чем появление — уходящее не должно задерживать. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST_MILLIS, easing = Standard)

    fun <T> normal(): FiniteAnimationSpec<T> = tween(NORMAL_MILLIS, easing = Standard)

    fun <T> screen(): FiniteAnimationSpec<T> = tween(SCREEN_MILLIS, easing = Enter)

    /** Спек для [androidx.compose.animation.animateContentSize]. */
    fun contentSize(): FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = DAMPING,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntSize.VisibilityThreshold,
    )

    /**
     * Пружина для величин, которые «доезжают»: заполнение индикатора,
     * положение курсора, размер раскрывшегося блока.
     *
     * Пружина, а не кривая с фиксированной длительностью, потому что она
     * подхватывает движение с текущей скорости: быстрые повторные изменения
     * не начинают анимацию заново с нуля, а перенаправляют уже идущую — это и
     * читается как «плавно», в отличие от серии дёрганых перезапусков.
     */
    fun <T> springy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = DAMPING,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * Затухание почти без отскока: прибор — не игрушка, и пружина здесь нужна
     * ради подхвата скорости, а не ради пружинистости.
     */
    const val DAMPING = 0.9f
}
