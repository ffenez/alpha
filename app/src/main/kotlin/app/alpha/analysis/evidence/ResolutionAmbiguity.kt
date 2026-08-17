package app.alpha.analysis.evidence

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
        /**
         * Пики, реально найденные в спектре. Пустой список — отбор соперников
         * по флагману не делается (например, при проверке калибровки, где
         * важна сама неразличимость энергий).
         */
        observedPeaks: List<ObservedPeak> = emptyList(),
    ): ResolutionAmbiguity? {
        val rivals = unresolvableWith(matched.energyKeV, resolution, lines)
            .filter { it.nuclide != matched.nuclide }
            .filter { survivesFlagshipTest(it, resolution, lines, observedPeaks) }
        if (rivals.isEmpty()) return null
        return ResolutionAmbiguity(peak, listOf(matched) + rivals)
    }

    /**
     * Соперник остаётся в группе, только если его собственные линии не
     * опровергают его молчанием.
     *
     * Проверок две, и обе консервативны.
     *
     * 1. **Заметно более яркая линия молчит.** Одиночная 661,7 кэВ (Cs-137)
     *    попадала в группу с 637,0 кэВ (I-131) — по энергии прибор их не
     *    разводит. Но у иода есть 364,5 кэВ с выходом 81 % против 7 % у 637,0:
     *    была бы видна слабая, сильная была бы заведомо. Её нет — соперник
     *    отпадает.
     * 2. **Ярчайшая из сопоставимых по выходу линий молчит.** Если соперник
     *    претендует на пик СВОЕЙ САМОЙ ЯРКОЙ линией, первая проверка молчит по
     *    построению. Тогда смотрится вторая линия: La-138 при 1436 кэВ обязан
     *    дать и 789 кэВ (33,6 % против 66,4 %), Cs-134 при 605 — и 796 кэВ
     *    (85,5 % против 97,6 %), Eu-152 при 1112 — и 344 кэВ (26,6 % против
     *    13,7 %). Проверяется именно ярчайшая: у многолинейного нуклида всегда
     *    найдётся слабая линия под каким-нибудь пиком, и требования «хоть одна
     *    видна» не хватает.
     *
     * Ограничение по энергии во второй проверке существует из-за неизвестной
     * кривой эффективности и несимметрично — см. [comparableEfficiency].
     */
    private fun survivesFlagshipTest(
        rival: LibraryLine,
        resolution: ResolutionModel,
        lines: List<LibraryLine>,
        observedPeaks: List<ObservedPeak>,
    ): Boolean {
        if (observedPeaks.isEmpty()) return true
        val siblings = lines
            .filter { it.nuclide == rival.nuclide && it.energyKeV != rival.energyKeV }
        if (siblings.isEmpty()) return true

        fun seen(line: LibraryLine): Boolean = observedPeaks.any {
            !resolvable(line.energyKeV, it.centroidKeV, resolution)
        }

        val flagship = siblings.maxByOrNull { it.intensityPercent }
        if (
            flagship != null &&
            flagship.intensityPercent >= rival.intensityPercent * FLAGSHIP_RATIO &&
            !seen(flagship)
        ) {
            return false
        }

        val comparable = siblings.filter {
            it.intensityPercent * FLAGSHIP_RATIO >= rival.intensityPercent &&
                resolvable(it.energyKeV, rival.energyKeV, resolution) &&
                comparableEfficiency(it.energyKeV, rival.energyKeV)
        }
        val brightest = comparable.maxByOrNull { it.intensityPercent }
        if (brightest != null && !seen(brightest)) return false

        return true
    }

    /**
     * Можно ли сравнивать выходы двух линий без кривой эффективности:
     * [siblingKeV] — линия, молчание которой оценивается, [claimKeV] — линия,
     * которой соперник претендует на пик.
     *
     * Правило несимметрично, потому что несимметрична сама эффективность
     * полного поглощения: выше ~[EFFICIENCY_FLOOR_KEV] она монотонно падает с
     * энергией. Линия НИЖЕ претендующей регистрируется не хуже неё на любом
     * расстоянии по шкале — её молчание значимо (Eu-152 при 1112 кэВ обязан
     * дать 344 кэВ с выходом 26,6 % против 13,7 %). Линия ВЫШЕ регистрируется
     * хуже, и сравнивать выходы можно лишь в пределах
     * [EFFICIENCY_SPAN_RATIO]. Ниже порога сравнение не работает вовсе:
     * поглощение в корпусе и в самом кристалле съедает мягкие линии.
     */
    private fun comparableEfficiency(siblingKeV: Double, claimKeV: Double): Boolean =
        if (siblingKeV <= claimKeV) {
            siblingKeV >= EFFICIENCY_FLOOR_KEV
        } else {
            siblingKeV <= EFFICIENCY_SPAN_RATIO * claimKeV
        }

    /**
     * Во сколько раз линия может быть ВЫШЕ претендующей, чтобы их выходы ещё
     * можно было сравнивать без кривой эффективности. **Инженерный параметр**:
     * вдвое — на таком интервале эффективность полного поглощения малого
     * сцинтиллятора меняется в разы меньше, чем на всей шкале.
     */
    const val EFFICIENCY_SPAN_RATIO = 2.0

    /**
     * Ниже какой энергии молчание линии ничего не доказывает, кэВ.
     * **Инженерный параметр**: до ~150 кэВ ход эффективности определяется уже
     * не кристаллом, а поглощением в корпусе и самопоглощением в образце, и
     * выход из таблицы не переводится в ожидаемую площадь пика.
     */
    const val EFFICIENCY_FLOOR_KEV = 150.0

    const val FLAGSHIP_RATIO = 3.0
}
