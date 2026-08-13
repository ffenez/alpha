package app.radiacode.analysis.evidence

import kotlin.math.abs

/** Почему линия природного фона НЕ годится в опорные для этого прибора. */
enum class LineRejection {
    /** Ниже [CalibrationLineSelection.MIN_ENERGY_KEV] — область порога и утечек. */
    TOO_LOW,

    /** Выход слишком мал, чтобы линия вообще поднялась над континуумом. */
    TOO_WEAK,

    /**
     * Внутри ожидаемой FWHM стоит сопоставимая по яркости линия ДРУГОГО ряда
     * или нуклида: доля каждой в слитой структуре зависит от местного
     * соотношения активностей, поэтому энергия слияния неизвестна.
     */
    BLENDED_WITH_OTHER_ACTIVITY,

    /**
     * Соседи из ТОГО ЖЕ ряда, отношение известно — но предсказанный сдвиг
     * центроида слишком велик, чтобы считать структуру этой линией.
     */
    BLEND_SHIFTS_CENTROID,
}

/**
 * Опорная линия: либо пригодная (с предсказанным вкладом соседей), либо
 * отклонённая с названной причиной.
 *
 * @param blendBiasKeV насколько центроид СЛИЯНИЯ смещён от табличной энергии
 *   по ядерным данным. Ноль — соседей внутри окна нет. Это не измеренная
 *   величина и не поправка: она печатается рядом с остатком, чтобы известный
 *   систематический сдвиг не читался как уход энергетической шкалы.
 * @param neighbours линии внутри окна, кроме самой опорной
 */
data class CalibrationLineCandidate(
    val line: LibraryLine,
    val expectedFwhmKeV: Double,
    val usable: Boolean,
    val rejection: LineRejection?,
    val blendBiasKeV: Double,
    val neighbours: List<LibraryLine>,
    /** Соседи, из-за которых линия отклонена; пусто у пригодной. */
    val blockers: List<LibraryLine>,
)

/**
 * Какие линии природного фона ЭТОТ прибор действительно разделяет.
 *
 * Правило вычисляется, а не перечисляется списком: список пришлось бы
 * переписывать при каждом изменении модели разрешения, а на приборе с другим
 * кристаллом (GAGG у RC-103G) он был бы просто неверен.
 *
 * ## Окно
 *
 * Соседи ищутся внутри ±FWHM/2 — то есть внутри самого интервала полной
 * ширины на половине высоты. Это ровно тот интервал, из которого собирается
 * центроид: линия на расстоянии FWHM/2 сидит в половине высоты соседки, и
 * измеренный центроид становится центроидом слияния, а не линии. Пара
 * 583,2/609,3 расходится на 26,1 кэВ при FWHM ≈ 53 кэВ — то есть на
 * FWHM/2 без малого, и именно поэтому обе линии из отбора выпадают.
 *
 * ## Два независимых условия
 *
 * 1. **Предсказуемость.** Сосед сопоставимой яркости должен принадлежать тому
 *    же нуклиду или тому же ряду. Тогда его доля задана ядерными данными и
 *    смещение центроида можно вычислить. Сосед из чужого ряда (238,6 кэВ
 *    Pb-212 из Th-232 и 242,0 кэВ Pb-214 из Ra-226) делает состав слияния
 *    функцией местного соотношения тория и урана — величины, которой у нас
 *    нет и которая меняется от стены к стене.
 * 2. **Малость предсказанного сдвига.** Даже у своих соседей слияние
 *    смещает центроид; сдвиг больше [MAX_BLEND_BIAS_FRACTION] от FWHM
 *    означает, что структура — это уже не линия, а группа (1377,7 + 1401,5 +
 *    1408,0 кэВ Bi-214).
 *
 * Неизвестный выход соседа (`NaN`, см. [BackgroundLineInventory]) считается
 * сопоставимым: доказать, что им можно пренебречь, нечем.
 */
object CalibrationLineSelection {

    /**
     * Ниже этой энергии линии в опорные не берутся — **инженерный параметр**.
     * У RC-110 в этой области ход эффективности задаётся поглощением в
     * корпусе, а заводская калибровка наименее точна (KDoc
     * [app.radiacode.analysis.GammaLineLibrary]); измеренная там ширина
     * описывала бы не разрешение, а край рабочего диапазона.
     */
    const val MIN_ENERGY_KEV = 100.0

    /**
     * Минимальный выход опорной линии, % на распад родителя ряда —
     * **инженерный параметр**. Линия слабее почти никогда не поднимается над
     * континуумом сцинтиллятора за разумное время, а в отборе она создаёт
     * видимость выбора там, где выбора нет.
     */
    const val MIN_INTENSITY_PERCENT = 3.0

    /**
     * Во сколько раз сосед может быть слабее опорной линии, чтобы им ещё
     * можно было пренебречь — **инженерный параметр**. При 10 % сосед сдвигает
     * центроид не больше чем на десятую часть расстояния до него; при
     * расстоянии в полширины это уже заметно меньше собственной погрешности
     * центроида сильного пика.
     */
    const val COMPARABLE_INTENSITY_FRACTION = 0.10

    /**
     * Допустимый предсказанный сдвиг центроида слияния, в долях FWHM —
     * **инженерный параметр**. Десятая часть ширины на энергии 1764,5 кэВ это
     * около 9 кэВ, то есть 0,5 % шкалы: столько же, сколько инженерная оценка
     * σ_cal, которую эта диагностика и должна заменить измерением.
     */
    const val MAX_BLEND_BIAS_FRACTION = 0.10

    /** Разбор одной линии инвентаря на этом приборе. */
    fun evaluate(
        line: LibraryLine,
        resolution: ResolutionModel,
        inventory: List<LibraryLine> = BackgroundLineInventory.LINES,
    ): CalibrationLineCandidate {
        val fwhm = resolution.fwhmKeV(line.energyKeV)
        val neighbours = inventory.filter { it !== line && blends(line, it, resolution) }
        fun rejected(reason: LineRejection, blockers: List<LibraryLine>) =
            CalibrationLineCandidate(
                line = line,
                expectedFwhmKeV = fwhm,
                usable = false,
                rejection = reason,
                blendBiasKeV = 0.0,
                neighbours = neighbours,
                blockers = blockers,
            )

        if (line.energyKeV < MIN_ENERGY_KEV) return rejected(LineRejection.TOO_LOW, emptyList())
        if (!line.intensityPercent.isFinite() ||
            line.intensityPercent < MIN_INTENSITY_PERCENT
        ) {
            return rejected(LineRejection.TOO_WEAK, emptyList())
        }

        val comparable = neighbours.filter { comparable(line, it) }
        val foreign = comparable.filterNot { BackgroundLineInventory.sameActivity(line, it) }
        if (foreign.isNotEmpty()) {
            return rejected(LineRejection.BLENDED_WITH_OTHER_ACTIVITY, foreign)
        }

        val bias = blendBiasKeV(line, neighbours)
        if (abs(bias) > MAX_BLEND_BIAS_FRACTION * fwhm) {
            return rejected(LineRejection.BLEND_SHIFTS_CENTROID, comparable)
        }
        return CalibrationLineCandidate(
            line = line,
            expectedFwhmKeV = fwhm,
            usable = true,
            rejection = null,
            blendBiasKeV = bias,
            neighbours = neighbours,
            blockers = emptyList(),
        )
    }

    /** Все линии инвентаря, разобранные на этом приборе; порядок — по энергии. */
    fun evaluateAll(
        resolution: ResolutionModel,
        inventory: List<LibraryLine> = BackgroundLineInventory.LINES,
    ): List<CalibrationLineCandidate> =
        inventory.map { evaluate(it, resolution, inventory) }

    /** Только пригодные линии, от низкой энергии к высокой. */
    fun usable(
        resolution: ResolutionModel,
        inventory: List<LibraryLine> = BackgroundLineInventory.LINES,
    ): List<CalibrationLineCandidate> = evaluateAll(resolution, inventory).filter { it.usable }

    /**
     * Две линии сливаются, если расстояние между ними не больше ПОЛОВИНЫ
     * ожидаемой ширины на энергии их середины.
     *
     * Ширина берётся посередине, а не у одной из линий, по той же причине,
     * что и в [ResolutionAmbiguities.resolvable]: «сливаются» — свойство
     * ПАРЫ, и ответ не должен зависеть от того, с какой линии начали. У пары
     * 583,2/609,3 обе ширины отличаются на доли процента, и без этого правила
     * вердикт для одной линии оказывался противоположным вердикту для другой.
     *
     * Половина ширины, а не полная: это и есть интервал, из которого
     * собирается центроид. Линия на расстоянии FWHM/2 сидит в половине высоты
     * соседки, и измеряется уже центроид слияния.
     */
    fun blends(first: LibraryLine, second: LibraryLine, resolution: ResolutionModel): Boolean {
        val separation = abs(first.energyKeV - second.energyKeV)
        val middle = (first.energyKeV + second.energyKeV) / 2.0
        return separation <= resolution.fwhmKeV(middle) / 2.0
    }

    /**
     * Сосед сопоставим, если его выход не меньше [COMPARABLE_INTENSITY_FRACTION]
     * от выхода опорной линии. Неизвестный выход сопоставим всегда.
     */
    fun comparable(line: LibraryLine, neighbour: LibraryLine): Boolean {
        if (!neighbour.intensityPercent.isFinite()) return true
        return neighbour.intensityPercent >= COMPARABLE_INTENSITY_FRACTION * line.intensityPercent
    }

    /**
     * Смещение центроида слияния от табличной энергии по ядерным данным:
     * взвешенное выходами среднее энергий внутри окна минус энергия линии.
     *
     * Отношение эффективностей внутри окна принято единичным — окно уже
     * FWHM, и на таком интервале ε(E) меняется на доли процента, тогда как
     * выходы соседей различаются в разы.
     */
    fun blendBiasKeV(line: LibraryLine, neighbours: List<LibraryLine>): Double {
        var weight = line.intensityPercent
        var weighted = line.intensityPercent * line.energyKeV
        for (n in neighbours) {
            if (!n.intensityPercent.isFinite()) continue
            weight += n.intensityPercent
            weighted += n.intensityPercent * n.energyKeV
        }
        if (weight <= 0.0) return 0.0
        return weighted / weight - line.energyKeV
    }
}
