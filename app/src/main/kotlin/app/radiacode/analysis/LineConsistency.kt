package app.radiacode.analysis

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Согласованность НЕСКОЛЬКИХ линий нуклида — и её честные границы.
 *
 * ## Что это добавляет к совпадению энергии
 *
 * Совпадение энергии одного пика с библиотечной линией — самое слабое из
 * возможных свидетельств: линий в природе много, а разрешение сцинтиллятора
 * широкое. IAEA описывает идентификацию как последовательность: энергии
 * фотопиков → интегрирование областей с вычитанием континуума → нетто-площади
 * → совместное использование НЕСКОЛЬКИХ линий и их отношений.
 *
 * Здесь делается предпоследний шаг: для нуклида-кандидата проверяется, какие
 * из его линий вообще найдены и как соотносятся их **нетто-площади** (не
 * высоты каналов — высота зависит от ширины линии и от континуума под ней).
 *
 * ## Чего этот код СОЗНАТЕЛЬНО не делает
 *
 * **Не пересчитывает отношение через эффективность детектора.** Ожидаемое
 * отношение линий равно `(Iγ₁·ε(E₁)) / (Iγ₂·ε(E₂))`, где ε — эффективность
 * регистрации полного поглощения на данной энергии в данной геометрии. Для
 * RadiaCode такой кривой у нас нет, и придумывать её нельзя: разница между
 * 600 и 1500 кэВ у сцинтиллятора этого размера — разы, то есть выдуманная ε
 * превратила бы честную оговорку в уверенный неверный вывод.
 *
 * Поэтому наблюдаемое отношение и табличное показываются РЯДОМ и порознь, а
 * вердикт о их согласии не выносится вовсе, пока [DetectorEfficiency] не
 * получит измеренную кривую. Архитектурно место для неё оставлено.
 *
 * ## Отсутствие линии не опровергает нуклид
 *
 * Слабая линия может быть статистически неразличима: её ожидаемая площадь
 * сравнима с флуктуацией континуума. Поэтому ненайденная линия помечается как
 * «не различима на этой статистике», если ожидаемая площадь ниже порога
 * обнаружения, и как «не найдена» — только если её ждали заметной.
 *
 * СНЯТА С ЭКРАНА: справка нуклида читает вердикт движка доказательств
 * (ADR 006) через `PeakEvidenceBridge` — тот проверяет наблюдаемость линий
 * порогом Карри и односторонним доводом об ε(E), а не долей от сильнейшей.
 */
@Deprecated(
    "Проверку набора линий выполняет движок доказательств: " +
        "EvidenceEngine + PeakEvidenceBridge (ADR 006)",
)
object LineConsistency {

    /** Что известно про одну ожидаемую линию нуклида. */
    data class LineCheck(
        val energyKeV: Float,
        /** Выход линии, фотонов на 100 распадов. */
        val intensityPercent: Float,
        /** Найденный пик, если он есть. */
        val peak: Peak?,
        /**
         * Ожидалась ли линия РАЗЛИЧИМОЙ при этой статистике. Null — оценить
         * нельзя (нет опорной линии, из которой считается ожидание).
         */
        val expectedVisible: Boolean?,
    ) {
        val found: Boolean get() = peak != null
    }

    /** Отношение нетто-площадей двух линий с его неопределённостью. */
    data class Ratio(
        val fromKeV: Float,
        val toKeV: Float,
        val observed: Double,
        /** 1σ отношения по правилу частного пуассоновских площадей. */
        val sigma: Double,
        /** Отношение табличных выходов — БЕЗ поправки на эффективность. */
        val expectedByYield: Double,
    )

    /** Насколько кандидат подкреплён спектром. */
    enum class Support {
        /** Совпала одна энергия — самое слабое свидетельство. */
        SINGLE_LINE,

        /** Найдено несколько ожидаемых линий нуклида. */
        MULTI_LINE,

        /** Ожидались другие заметные линии, а их нет. */
        MISSING_STRONG_LINE,
    }

    data class Result(
        val isotope: String,
        val lines: List<LineCheck>,
        val ratio: Ratio?,
        val support: Support,
    ) {
        val foundLines: Int get() = lines.count { it.found }
    }

    /**
     * Ниже этой доли от самой сильной ожидаемой линии линия считается
     * заведомо неразличимой на текущей статистике.
     *
     * **Инженерный параметр отображения**: строгий порог обнаружения (Currie)
     * требует знания континуума под каждой ожидаемой линией, а его мы считаем
     * только там, где пик найден. Доля 0,15 отсекает случаи вроде «у Bi-214
     * не видно линии 1764 кэВ» — при выходе 15 % против 46 % у 609 кэВ это
     * ожидаемо, а не противоречие.
     */
    const val WEAK_LINE_FRACTION = 0.15f

    /**
     * Проверяет кандидата по всем его известным линиям.
     *
     * @param peaks найденные пики спектра
     * @param tolerance допуск совпадения энергии (FWHM-зависимый)
     */
    fun check(
        isotope: String,
        peaks: List<Peak>,
        tolerance: (Float) -> Float,
    ): Result? {
        val info = NuclideInfoLibrary.of(isotope) ?: return null
        val known = info.lines.sortedByDescending { it.intensityPercent }
        if (known.isEmpty()) return null

        val strongest = known.first().intensityPercent
        val checks = known.map { line ->
            val peak = peaks
                .filter { abs(it.energyKeV - line.energyKeV) <= tolerance(line.energyKeV) }
                .maxByOrNull { it.netCounts }
            LineCheck(
                energyKeV = line.energyKeV,
                intensityPercent = line.intensityPercent,
                peak = peak,
                expectedVisible = if (strongest <= 0f) {
                    null
                } else {
                    line.intensityPercent / strongest >= WEAK_LINE_FRACTION
                },
            )
        }

        val found = checks.filter { it.found }
        val ratio = if (found.size >= 2) {
            val a = found[0]
            val b = found[1]
            ratioOf(a, b)
        } else {
            null
        }

        val support = when {
            found.size >= 2 -> Support.MULTI_LINE
            // Отсутствие линии само по себе нуклид не опровергает: значение
            // имеет только пропущенная ЗАМЕТНАЯ линия.
            checks.any { !it.found && it.expectedVisible == true } &&
                found.isNotEmpty() -> Support.MISSING_STRONG_LINE
            else -> Support.SINGLE_LINE
        }
        return Result(isotope, checks, ratio, support)
    }

    /**
     * Отношение нетто-площадей и его 1σ.
     *
     * σ(A₁/A₂) = |A₁/A₂|·√(σ₁²/A₁² + σ₂²/A₂²); для пуассоновской площади
     * σ ≈ √A — это оценка снизу, потому что вычитание континуума добавляет
     * свою неопределённость, и она здесь не учитывается.
     */
    private fun ratioOf(a: LineCheck, b: LineCheck): Ratio? {
        val netA = a.peak?.netCounts?.toDouble() ?: return null
        val netB = b.peak?.netCounts?.toDouble() ?: return null
        if (netA <= 0.0 || netB <= 0.0) return null
        val observed = netA / netB
        val sigma = observed * sqrt(1.0 / netA + 1.0 / netB)
        val expected = if (b.intensityPercent > 0f) {
            a.intensityPercent.toDouble() / b.intensityPercent
        } else {
            Double.NaN
        }
        return Ratio(a.energyKeV, b.energyKeV, observed, sigma, expected)
    }
}

/**
 * Кривая эффективности детектора — место, которого пока нет.
 *
 * Отношение линий можно сравнивать с табличным только после поправки на
 * энергетическую зависимость эффективности регистрации полного поглощения.
 * Для RadiaCode такой измеренной кривой у нас нет, и приложение об этом
 * говорит прямо, вместо того чтобы подставить правдоподобную формулу.
 *
 * Когда кривая появится (измерение на источниках с известной активностью в
 * фиксированной геометрии), она подключается сюда, а [LineConsistency] начнёт
 * возвращать ожидаемое отношение с поправкой. До тех пор [available] = false,
 * и UI обязан показывать наблюдаемое и табличное отношения раздельно.
 */
object DetectorEfficiency {

    /** Есть ли обоснованная кривая эффективности. */
    const val AVAILABLE = false

    const val UNAVAILABLE_NOTE =
        "Точное сравнение отношения линий ограничено: относительная эффективность " +
            "детектора для этих энергий не откалибрована, поэтому наблюдаемое и " +
            "табличное отношения показаны раздельно."
}
