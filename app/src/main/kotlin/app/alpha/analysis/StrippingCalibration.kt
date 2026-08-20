package app.alpha.analysis

import kotlin.math.sqrt

/**
 * Измерение коэффициентов стриппинга на КОНКРЕТНОМ приборе.
 *
 * ## Зачем
 *
 * Комптоновский хвост тория попадает в окно урана, а хвосты обоих — в окно
 * калия. Универсальных поправок не существует: доля протечки зависит от
 * кристалла, его размера и разрешения. Классически их снимают на эталонных
 * площадках; здесь — на источниках, которые есть у человека: ториевая
 * калильная сетка, пачка калийной соли, урановое стекло.
 *
 * ## Как считается
 *
 * По ториевому источнику: α = (площадь в окне урана) / (площадь в окне тория),
 * β = (площадь в окне калия) / (площадь в окне тория). По урановому:
 * γ = (площадь в окне калия) / (площадь в окне урана). Каждая площадь —
 * ЧИСТАЯ, то есть за вычетом собственного фона места, приведённого ко времени
 * измерения источника.
 *
 * Порядок вычитания при применении обратный порядку измерения: сначала торий
 * (его окно самое верхнее, сыпаться в него нечему), затем уран, затем калий.
 *
 * ## Чего этот способ не даёт
 *
 * Он не превращает счёт в проценты и ppm: для этого нужны источники ИЗВЕСТНОЙ
 * активности в известной геометрии. Коэффициенты снимают только протечку между
 * окнами — и этого достаточно, чтобы «уран» перестал наполовину состоять из
 * тория.
 */
object StrippingCalibration {

    /**
     * Одно измерение: три площади и время. Приходит от [Radioelements.measure]
     * тем же путём, что и станция.
     */
    data class Sample(
        val measures: List<Radioelements.Measure>,
        val seconds: Long,
    ) {
        fun rate(element: Radioelements.Element): Float? =
            measures.firstOrNull { it.element == element }?.takeIf { it.seconds > 0 }?.cps

        fun sigma(element: Radioelements.Element): Float? =
            measures.firstOrNull { it.element == element }?.cpsSigma

        fun detected(element: Radioelements.Element): Boolean =
            measures.firstOrNull { it.element == element }?.detected == true
    }

    /** Почему коэффициент не посчитан — это ответ, а не пустое место. */
    enum class Refusal {
        /** Источник не отличается от фона в своём собственном окне. */
        SOURCE_TOO_WEAK,

        /** Нет измерения фона или источника. */
        MISSING_MEASUREMENT,

        /** Разность «источник минус фон» вышла нулевой или отрицательной. */
        NOTHING_ABOVE_BACKGROUND,
    }

    data class Result(
        val stripping: Radioelements.Stripping?,
        val thoriumRefusal: Refusal? = null,
        val uraniumRefusal: Refusal? = null,
    )

    /**
     * Коэффициенты по двум источникам и фону.
     *
     * @param background собственный фон места — его вычитают из обоих
     *   источников; без него счёт источника содержит и то, что было бы и так.
     * @param thorium измерение ториевого источника: даёт α и β.
     * @param uranium измерение уранового источника: даёт γ. Без него α и β
     *   остаются, а γ считается нулём — калий тогда очищен только от тория, и
     *   это лучше, чем ничего.
     */
    fun of(
        background: Sample?,
        thorium: Sample?,
        uranium: Sample?,
    ): Result {
        if (background == null || thorium == null) {
            return Result(null, thoriumRefusal = Refusal.MISSING_MEASUREMENT)
        }
        val thNet = excess(thorium, background, Radioelements.Element.TH)
            ?: return Result(null, thoriumRefusal = Refusal.NOTHING_ABOVE_BACKGROUND)
        if (!thorium.detected(Radioelements.Element.TH)) {
            return Result(null, thoriumRefusal = Refusal.SOURCE_TOO_WEAK)
        }
        val uInTh = excess(thorium, background, Radioelements.Element.U) ?: 0f
        val kInTh = excess(thorium, background, Radioelements.Element.K) ?: 0f

        val alpha = (uInTh / thNet).coerceAtLeast(0f)
        val beta = (kInTh / thNet).coerceAtLeast(0f)

        var gamma = 0f
        var uraniumRefusal: Refusal? = null
        when {
            uranium == null -> uraniumRefusal = Refusal.MISSING_MEASUREMENT
            !uranium.detected(Radioelements.Element.U) ->
                uraniumRefusal = Refusal.SOURCE_TOO_WEAK
            else -> {
                // Урановый источник сам содержит торий: его вклад в окно урана
                // снимается уже измеренной α, иначе γ считалась бы от
                // загрязнённого числа.
                val uNet = excess(uranium, background, Radioelements.Element.U)
                val thInU = excess(uranium, background, Radioelements.Element.TH) ?: 0f
                val cleanU = uNet?.minus(alpha * thInU)
                val kInU = excess(uranium, background, Radioelements.Element.K) ?: 0f
                if (cleanU == null || cleanU <= 0f) {
                    uraniumRefusal = Refusal.NOTHING_ABOVE_BACKGROUND
                } else {
                    gamma = ((kInU - beta * thInU) / cleanU).coerceAtLeast(0f)
                }
            }
        }

        return Result(
            stripping = Radioelements.Stripping(
                thoriumIntoUranium = alpha,
                thoriumIntoPotassium = beta,
                uraniumIntoPotassium = gamma,
            ),
            uraniumRefusal = uraniumRefusal,
        )
    }

    /**
     * Превышение источника над фоном в окне, с⁻¹.
     *
     * Скорости, а не импульсы: измерения источника и фона длятся разное время,
     * и вычитать их счёт напрямую значило бы вычитать разные экспозиции.
     */
    private fun excess(
        source: Sample,
        background: Sample,
        element: Radioelements.Element,
    ): Float? {
        val sourceRate = source.rate(element) ?: return null
        val backgroundRate = background.rate(element) ?: return null
        val difference = sourceRate - backgroundRate
        return if (difference > 0f) difference else null
    }
}
