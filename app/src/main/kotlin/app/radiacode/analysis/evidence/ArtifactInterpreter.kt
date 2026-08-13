package app.radiacode.analysis.evidence

import kotlin.math.abs

/**
 * Кандидат-объяснение пика БЕЗ введения нового нуклида.
 *
 * Формулировка важна: артефакт не «обнаружен». Движок утверждает ровно одно —
 * существует физический механизм, при котором этот пик появляется от уже
 * наблюдаемого излучения, поэтому вводить ради него отдельный нуклид не
 * обязательно. Проверить механизм по одному спектру нельзя (аннигиляционный
 * пик и линия Tl-208 510,8 кэВ для RC-110 неразличимы в принципе), и движок
 * этого не делает.
 */
sealed interface PeakExplanation {

    /** Объясняемый пик. */
    val peak: ObservedPeak

    /** Пик совместим с аннигиляционной линией 511 кэВ. */
    data class Annihilation(
        override val peak: ObservedPeak,
        val deltaKeV: Double,
    ) : PeakExplanation

    /** Пик вылета одного аннигиляционного фотона: E_parent − 511. */
    data class SingleEscape(
        override val peak: ObservedPeak,
        val parent: ObservedPeak,
        val deltaKeV: Double,
    ) : PeakExplanation

    /** Пик вылета обоих аннигиляционных фотонов: E_parent − 1022. */
    data class DoubleEscape(
        override val peak: ObservedPeak,
        val parent: ObservedPeak,
        val deltaKeV: Double,
    ) : PeakExplanation

    /** Сумма двух совпадающих во времени линий известного каскада. */
    data class SumPeak(
        override val peak: ObservedPeak,
        val cascade: Cascade,
        val first: ObservedPeak,
        val second: ObservedPeak,
        val deltaKeV: Double,
    ) : PeakExplanation

    /**
     * Пик обратного рассеяния. [parent] — пик, из которого он мог бы
     * получиться по формуле Комптона; null, если пик просто лежит в области
     * 200–255 кэВ, где скапливается обратное рассеяние от всего спектра.
     */
    data class Backscatter(
        override val peak: ObservedPeak,
        val parent: ObservedPeak?,
        val predictedKeV: Double,
        val deltaKeV: Double,
    ) : PeakExplanation
}

/**
 * Известный совпадающий каскад: два фотона, испускаемые в ОДНОМ распаде почти
 * одновременно, поэтому детектор может зарегистрировать их как одно событие.
 *
 * Список намеренно короткий и составлен по схемам распада ENSDF:
 *  - Co-60: 1173,2 и 1332,5 кэВ идут последовательно в каждом распаде
 *    (учебниковый пример суммирования);
 *  - Bi-214: уровни 1729,6 и 2373,8 кэВ Po-214 разряжаются на уровень
 *    609,3 кэВ, поэтому 1120,3 и 1764,5 кэВ приходят в каскаде с 609,3 кэВ.
 *
 * Вероятность суммирования зависит от геометрии, телесного угла и
 * эффективности — ничего из этого у нас не измерено, поэтому сумма-пик здесь
 * ТОЛЬКО объяснение уже увиденного пика и никогда не предсказание.
 */
data class Cascade(
    val nuclide: String,
    val firstKeV: Double,
    val secondKeV: Double,
) {
    val sumKeV: Double get() = firstKeV + secondKeV
}

/**
 * Интерпретатор артефактов — работает ДО сопоставления нуклидов.
 *
 * Порядок не случаен: пик 1592 кэВ в спектре тория это двойной вылет от
 * 2614,5 кэВ, а не «неизвестный нуклид с линией 1592». Если сначала запустить
 * сопоставление, движок начнёт плодить кандидатов на объяснимые пики, и
 * дальнейшая арифметика (сколько линий совпало, сколько осталось необъяснённых)
 * будет считать одно и то же событие дважды.
 */
object ArtifactInterpreter {

    /** Энергия покоя электрона, кэВ — константа физики (CODATA 510,999). */
    const val ELECTRON_REST_KEV = 511.0

    /**
     * Порог рождения пары — 2·m_e·c² = 1022 кэВ. Ниже него вылета
     * аннигиляционных фотонов не бывает вовсе, поэтому escape-пики ищутся
     * только у родителей выше порога.
     */
    const val PAIR_PRODUCTION_THRESHOLD_KEV = 2 * ELECTRON_REST_KEV

    /**
     * Нижняя граница энергии родителя для поиска escape-пиков — **инженерный
     * параметр**. Формально порог 1022 кэВ, но у самого порога сечение
     * рождения пар исчезающе мало, и «объяснение» вылетом было бы натяжкой.
     * 1500 кэВ — общепринятая в спектрометрии граница, с которой escape-пики
     * начинают быть заметными; точное значение зависит от размера кристалла,
     * которого мы не измеряли.
     */
    const val ESCAPE_MIN_PARENT_KEV = 1500.0

    /**
     * Область обратного рассеяния. Верхний предел — физический: энергия
     * рассеянного назад (180°) фотона E' = E/(1 + 2E/m_e c²) стремится к
     * m_e c²/2 ≈ 255 кэВ при большой E. Нижняя граница 200 кэВ — практическая
     * (**инженерный параметр**): ниже неё пик от рассеяния уже неотличим от
     * структур края шкалы.
     */
    const val BACKSCATTER_MIN_KEV = 200.0
    const val BACKSCATTER_MAX_KEV = 255.0

    val CASCADES: List<Cascade> = listOf(
        Cascade("Co-60", 1173.2, 1332.5),
        Cascade("Bi-214", 609.3, 1120.3),
        Cascade("Bi-214", 609.3, 1764.5),
    )

    /**
     * Допуск совпадения артефакта с предсказанной энергией — половина
     * ожидаемой FWHM. **Инженерный параметр**: у артефактов нет табличной
     * энергии со своей неопределённостью, а положение предсказано из другого
     * ИЗМЕРЕННОГО пика, поэтому z-подход здесь неприменим — сравниваются две
     * величины, обе с шириной детектора.
     */
    private fun tolerance(resolution: ResolutionModel, energyKeV: Double): Double =
        0.5 * resolution.fwhmKeV(energyKeV)

    /** Энергия фотона, рассеянного на 180°, по формуле Комптона. */
    fun backscatterKeV(parentKeV: Double): Double =
        parentKeV / (1.0 + 2.0 * parentKeV / ELECTRON_REST_KEV)

    /**
     * Все объяснения-кандидаты для набора пиков. Один пик может получить
     * несколько объяснений — выбирать между ними по одному спектру нечем.
     */
    fun explain(
        peaks: List<ObservedPeak>,
        resolution: ResolutionModel,
    ): List<PeakExplanation> {
        val out = mutableListOf<PeakExplanation>()
        for (peak in peaks) {
            annihilation(peak, resolution)?.let { out += it }
            out += escapes(peak, peaks, resolution)
            out += sums(peak, peaks, resolution)
            out += backscatter(peak, peaks, resolution)
        }
        return out
    }

    private fun annihilation(
        peak: ObservedPeak,
        resolution: ResolutionModel,
    ): PeakExplanation.Annihilation? {
        val delta = peak.centroidKeV - ELECTRON_REST_KEV
        return if (abs(delta) <= tolerance(resolution, ELECTRON_REST_KEV)) {
            PeakExplanation.Annihilation(peak, delta)
        } else {
            null
        }
    }

    private fun escapes(
        peak: ObservedPeak,
        peaks: List<ObservedPeak>,
        resolution: ResolutionModel,
    ): List<PeakExplanation> {
        val out = mutableListOf<PeakExplanation>()
        for (parent in peaks) {
            if (parent === peak) continue
            if (parent.centroidKeV < ESCAPE_MIN_PARENT_KEV) continue
            val single = parent.centroidKeV - ELECTRON_REST_KEV
            val double = parent.centroidKeV - PAIR_PRODUCTION_THRESHOLD_KEV
            val dSingle = peak.centroidKeV - single
            if (abs(dSingle) <= tolerance(resolution, single)) {
                out += PeakExplanation.SingleEscape(peak, parent, dSingle)
            }
            if (double > 0.0) {
                val dDouble = peak.centroidKeV - double
                if (abs(dDouble) <= tolerance(resolution, double)) {
                    out += PeakExplanation.DoubleEscape(peak, parent, dDouble)
                }
            }
        }
        return out
    }

    private fun sums(
        peak: ObservedPeak,
        peaks: List<ObservedPeak>,
        resolution: ResolutionModel,
    ): List<PeakExplanation> {
        val out = mutableListOf<PeakExplanation>()
        for (cascade in CASCADES) {
            val sum = cascade.sumKeV
            val delta = peak.centroidKeV - sum
            if (abs(delta) > tolerance(resolution, sum)) continue
            // Сумма объясняет пик, только если ОБА слагаемых сами видны:
            // иначе это предсказание из ничего.
            val first = nearest(peaks, cascade.firstKeV, resolution) ?: continue
            val second = nearest(peaks, cascade.secondKeV, resolution) ?: continue
            if (first === peak || second === peak) continue
            out += PeakExplanation.SumPeak(peak, cascade, first, second, delta)
        }
        return out
    }

    private fun backscatter(
        peak: ObservedPeak,
        peaks: List<ObservedPeak>,
        resolution: ResolutionModel,
    ): List<PeakExplanation> {
        if (peak.centroidKeV < BACKSCATTER_MIN_KEV || peak.centroidKeV > BACKSCATTER_MAX_KEV) {
            return emptyList()
        }
        val out = mutableListOf<PeakExplanation>()
        for (parent in peaks) {
            if (parent === peak) continue
            if (parent.centroidKeV <= peak.centroidKeV) continue
            val predicted = backscatterKeV(parent.centroidKeV)
            val delta = peak.centroidKeV - predicted
            if (abs(delta) <= tolerance(resolution, predicted)) {
                out += PeakExplanation.Backscatter(peak, parent, predicted, delta)
            }
        }
        // Обратное рассеяние собирается от ВСЕГО спектра, а не от одной линии,
        // поэтому пик в этой области объясним и без конкретного родителя.
        if (out.isEmpty()) {
            out += PeakExplanation.Backscatter(peak, null, peak.centroidKeV, 0.0)
        }
        return out
    }

    private fun nearest(
        peaks: List<ObservedPeak>,
        energyKeV: Double,
        resolution: ResolutionModel,
    ): ObservedPeak? = peaks
        .filter { abs(it.centroidKeV - energyKeV) <= tolerance(resolution, energyKeV) }
        .minByOrNull { abs(it.centroidKeV - energyKeV) }
}
