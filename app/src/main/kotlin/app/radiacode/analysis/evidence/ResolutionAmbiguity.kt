package app.radiacode.analysis.evidence

import kotlin.math.abs

/**
 * Группа линий, которые ЭТОТ прибор физически не разделяет.
 *
 * Классический пример — область ≈186 кэВ, где линия U-235 (185,7) и линия
 * Ra-226 (186,2) для сцинтиллятора неразличимы в принципе (в нашей библиотеке
 * этих нуклидов пока нет, но механика от этого не зависит). В имеющейся
 * библиотеке так же неразличимы 351,9 кэВ Pb-214 и 364,5 кэВ I-131 при FWHM
 * около 40 кэВ, и 510,8 кэВ Tl-208 с аннигиляционной линией 511 кэВ.
 *
 * Смысл группы: движок НЕ выбирает победителя там, где прибор не даёт
 * информации для выбора. Он называет альтернативы и говорит, что нужно
 * дополнительное свидетельство (другая линия того же нуклида, повторное
 * измерение, прибор с лучшим разрешением).
 */
data class ResolutionAmbiguity(
    /** Пик, для которого альтернативы неразличимы. */
    val peak: ObservedPeak,
    /** Линии-претенденты, ближайшая по энергии первой. */
    val lines: List<LibraryLine>,
) {
    /** Нуклиды-претенденты; их больше одного — иначе группы бы не было. */
    val nuclides: List<String> get() = lines.map { it.nuclide }.distinct()
}

/**
 * Разрешимость библиотечных линий и группы неразрешимости.
 */
object ResolutionAmbiguities {

    /**
     * Критерий разрешимости: две линии разделимы, если расстояние между ними
     * не меньше ожидаемой FWHM на этой энергии.
     *
     * Это стандартное для спектрометрии соглашение (аналог критерия Рэлея):
     * при ΔE = FWHM два гауссовых пика равной площади ещё дают провал между
     * максимумами, при меньшем расстоянии сливаются в одну структуру, ширина
     * которой и есть единственное, что можно измерить.
     */
    fun resolvable(
        firstKeV: Double,
        secondKeV: Double,
        resolution: ResolutionModel,
    ): Boolean {
        val separation = abs(firstKeV - secondKeV)
        val fwhm = resolution.fwhmKeV((firstKeV + secondKeV) / 2.0)
        return separation >= fwhm
    }

    /** Линии библиотеки, неразличимые с [energyKeV] на этом приборе. */
    fun unresolvableWith(
        energyKeV: Double,
        resolution: ResolutionModel,
        lines: List<LibraryLine> = EvidenceLineLibrary.LINES,
    ): List<LibraryLine> = lines
        .filter { !resolvable(energyKeV, it.energyKeV, resolution) }
        .sortedBy { abs(it.energyKeV - energyKeV) }

    /**
     * Группа неразрешимости для пика: все линии РАЗНЫХ нуклидов, которые
     * невозможно развести на его энергии. Пустой список — альтернатив нет,
     * линия для этого прибора уникальна.
     */
    fun ambiguityFor(
        peak: ObservedPeak,
        matched: LibraryLine,
        resolution: ResolutionModel,
        lines: List<LibraryLine> = EvidenceLineLibrary.LINES,
    ): ResolutionAmbiguity? {
        val rivals = unresolvableWith(matched.energyKeV, resolution, lines)
            .filter { it.nuclide != matched.nuclide }
        if (rivals.isEmpty()) return null
        return ResolutionAmbiguity(peak, listOf(matched) + rivals)
    }
}
