package app.radiacode.analysis.evidence

/** Почему измеренная модель разрешения НЕ построена. */
enum class ResolutionFitRefusal {
    /** Меньше [ResolutionFitting.MIN_POINTS] измеренных линий. */
    NOT_ENOUGH_LINES,

    /** Точки лежат слишком тесно по энергии: кривая по ним ничего не описывает. */
    NARROW_ENERGY_SPAN,

    /** Подгонка дала убывающую с энергией ширину — такого детектора не бывает. */
    NOT_MONOTONE,

    /** Свободный член вышел отрицательным: FWHM² < 0 у нижнего края шкалы. */
    NEGATIVE_NOISE_TERM,
}

/**
 * Результат подгонки FWHM(E) = √(a + b·E + c·E²).
 *
 * @param points энергии, по которым подгонялось (в порядке возрастания)
 * @param quadratic использован ли член c·E²; при трёх точках он не берётся
 * @param extrapolatedBelowKeV ниже этой энергии модель ЭКСТРАПОЛИРУЕТСЯ
 * @param extrapolatedAboveKeV выше этой энергии — тоже
 */
data class ResolutionFitResult(
    val a: Double,
    val b: Double,
    val c: Double,
    val points: List<Double>,
    val quadratic: Boolean,
    val extrapolatedBelowKeV: Double,
    val extrapolatedAboveKeV: Double,
) {
    fun model(): MeasuredResolution = MeasuredResolution(a, b, c)
}

/** Подгонка удалась либо отказалась с названной причиной. */
sealed interface ResolutionFitOutcome {
    data class Fitted(val fit: ResolutionFitResult) : ResolutionFitOutcome

    data class Refused(
        val reason: ResolutionFitRefusal,
        /** Сколько точек реально есть — чтобы экран назвал, чего не хватает. */
        val points: Int,
        val spanKeV: Double,
    ) : ResolutionFitOutcome
}
