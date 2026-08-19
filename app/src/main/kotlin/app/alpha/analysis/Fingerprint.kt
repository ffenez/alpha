package app.alpha.analysis

import app.alpha.ui.text.FingerprintRu
import app.alpha.ui.text.FingerprintStrings
import app.alpha.ui.text.uiDecimal
import kotlin.math.abs
import kotlin.math.roundToInt

/** Одно измерение отпечатка и его состояние. */
enum class FingerprintDimension {
    DOSE,
    COUNT_RATE,
    SPECTRUM,
    ;

    /**
     * Название измерения берётся из каталога, а не из самой константы: имя
     * enum уезжает в базу и в отчёты, а подпись на экране обязана следовать
     * языку интерфейса.
     */
    fun title(s: FingerprintStrings = FingerprintRu): String = when (this) {
        DOSE -> s.doseDimension
        COUNT_RATE -> s.countDimension
        SPECTRUM -> s.shapeDimension
    }
}

/** Что можно сказать про одно измерение. Четыре состояния, не два. */
enum class FingerprintState {
    /** Внутри того, чем место было в момент эталона. */
    SAME,

    /** Отличается от эталона больше, чем объясняет разброс самого эталона. */
    CHANGED,

    /** Данных на вывод не хватает — это НЕ «изменений нет». */
    NOT_ENOUGH_DATA,

    /** Нечего сравнивать: эталона нет вовсе. */
    NOT_EVALUATED,
}

/** Вердикт по одному измерению вместе с числами, на которых он стоит. */
data class DimensionVerdict(
    val dimension: FingerprintDimension,
    val state: FingerprintState,
    /** Числа под вердиктом; пусто, когда сравнивать было нечего. */
    val detail: String,
    /** Относительное изменение к эталону, %; null там, где оно не определено. */
    val changePercent: Int? = null,
) {
    val changed: Boolean get() = state == FingerprintState.CHANGED
}

/** Полное сравнение текущего окна с эталоном места. */
data class FingerprintComparison(
    val verdicts: List<DimensionVerdict>,
    /** Жёсткость сейчас и в эталоне — интерпретатор, не голос. */
    val hardnessNow: Double?,
    val hardnessReference: Double?,
    val hardnessChangePercent: Int?,
) {
    fun of(dimension: FingerprintDimension): DimensionVerdict? =
        verdicts.firstOrNull { it.dimension == dimension }

    val evaluated: Boolean
        get() = verdicts.any { it.state != FingerprintState.NOT_EVALUATED }

    val anyChanged: Boolean get() = verdicts.any { it.changed }
}

/** Сводка окна измерений: то, что сравнивается с эталоном. */
data class FingerprintWindow(
    val doseMedianMicroSvH: Float,
    val cpsMedian: Float,
    /** Спектр окна (сумма допущенных интервалов) и его экспозиция. */
    val spectrum: List<Int>,
    val spectrumSeconds: Long,
    /** Допущенное время измерений в окне, секунды. */
    val seconds: Long,
    /** Собственная погрешность дозы прибора, %; null = неизвестна. */
    val doseErrPercent: Double? = null,
)

/** Эталон места — те же величины, зафиксированные в момент создания. */
data class FingerprintReference(
    val doseLowMicroSvH: Float,
    val doseMedianMicroSvH: Float,
    val doseHighMicroSvH: Float,
    val cpsLow: Float,
    val cpsMedian: Float,
    val cpsHigh: Float,
    val spectrum: List<Int>,
    val spectrumSeconds: Long,
    val createdAtMillis: Long,
    val accumulatedSeconds: Long,
)

/**
 * **Radiation fingerprint** — обнаружение изменения МЕСТА, а не превышения
 * порога (ADR 005, спец §13).
 *
 * ## Что здесь голосует, и почему не всё
 *
 * Голосуют три измерения: распределение мощности дозы, распределение скорости
 * счёта и **форма спектра**. Жёсткость — четвёртое число на экране, но
 * **голоса у неё нет**: по определению вендора H = Ḋ/R ([Hardness]), то есть
 * частное первых двух. Своей степени свободы у неё нет, и если бы она
 * голосовала наравне, одно и то же событие считалось бы дважды, а сводный
 * вывод завышал бы уверенность. Её роль — объяснять, ПОЧЕМУ доза и счёт
 * разошлись: «интенсивность выросла, характер тот же» против «изменился и
 * характер».
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Формула.** Доза и счёт: текущая медиана против полосы эталона
 *    [FingerprintReference.doseLowMicroSvH]…`doseHigh` (P10–P90 места на момент
 *    эталона) — внутри полосы это SAME, снаружи CHANGED. Форма спектра:
 *    [ShapeChange] — двухвыборочный χ² однородности по слитым энергетическим
 *    полосам, в котором тотальные суммы сокращаются, поэтому «то же самое, но
 *    ярче» сигнала не даёт. Жёсткость считается для обеих сторон и сравнивается
 *    только как процент.
 * 2. **Допущения.** В эталон и в окно попадают ТОЛЬКО допущенные измерения
 *    (пайплайн §4.2), иначе отклонение впиталось бы в то, с чем сравнивают.
 *    Полоса эталона — порядковые статистики, а не σ: нормальность длинного
 *    фона спецификация запрещает предполагать.
 * 3. **Единицы.** Доза мкЗв/ч, счёт с⁻¹, спектр — отсчёты по каналам,
 *    жёсткость (мкрем/ч)/(имп/с), изменение в процентах.
 * 4. **Источник.** [ShapeChange] (Пирсон, двухвыборочный χ² однородности);
 *    полоса места — ADR 002.
 * 5. **Данные валидации.** `FingerprintTest`: то же место даёт SAME по всем
 *    измерениям; в k раз более яркое поле той же формы двигает дозу и счёт, но
 *    НЕ форму спектра; добавленная линия двигает форму; тонкое окно даёт
 *    NOT_ENOUGH_DATA, а не SAME; отсутствие эталона — NOT_EVALUATED.
 * 6. **Ограничения.** Совпадение отпечатка НЕ доказывает, что прибор в том же
 *    месте (спец §13: похожий спектр — не доказательство), а расхождение не
 *    называет причину. Сводного балла нет и не будет: спецификация §15 прямо
 *    запрещает непрозрачную оценку.
 * 7. **Тесты.** `app/src/test/.../analysis/FingerprintTest.kt`.
 * 8. **Версия алгоритма.** [AlgorithmVersions.FINGERPRINT].
 * 9. **Смысл для пользователя.** «Отличается от эталона этого места» — и
 *    ничего сверх этого: ни «опасно», ни «источник», ни нуклид.
 */
object Fingerprint {

    const val ALGORITHM_VERSION = AlgorithmVersions.FINGERPRINT

    /**
     * Сколько допущенных измерений нужно, чтобы вообще сравнивать окно.
     * **Инженерный параметр**: на пяти минутах медиана окна ещё гуляет.
     */
    const val MIN_WINDOW_SECONDS = 15L * 60L

    /** Зрелость профиля для АВТОМАТИЧЕСКОГО создания эталона. */
    const val MATURITY_SECONDS = 24L * 3600L

    /** …и минимальный опорный спектр: без него форма ничего не скажет. */
    const val MATURITY_SPECTRUM_COUNTS = 20_000L

    fun compare(
        window: FingerprintWindow?,
        reference: FingerprintReference?,
        s: FingerprintStrings = FingerprintRu,
    ): FingerprintComparison {
        if (reference == null) {
            return FingerprintComparison(
                verdicts = FingerprintDimension.entries.map {
                    DimensionVerdict(it, FingerprintState.NOT_EVALUATED, s.referenceMissing)
                },
                hardnessNow = null,
                hardnessReference = null,
                hardnessChangePercent = null,
            )
        }
        // Готовность измерений РАЗНАЯ, и общего «мало данных» на всех больше
        // нет: доза и счёт ждут своего окна ([MIN_WINDOW_SECONDS]), форма
        // спектра решает сама — ей нужна экспозиция, а не минуты стояния.
        // Одно «мало данных» на все три скрывало, что часть сравнения уже
        // сделана, и противоречило накопленному эталону на том же экране.
        val intensityReady = window != null && window.seconds >= MIN_WINDOW_SECONDS
        val progress = s.windowProgress(
            (window?.seconds ?: 0L) / 60L,
            MIN_WINDOW_SECONDS / 60,
        )
        if (window == null) {
            return FingerprintComparison(
                verdicts = FingerprintDimension.entries.map {
                    DimensionVerdict(it, FingerprintState.NOT_ENOUGH_DATA, progress)
                },
                hardnessNow = null,
                hardnessReference = null,
                hardnessChangePercent = null,
            )
        }

        val dose = if (!intensityReady) {
            DimensionVerdict(FingerprintDimension.DOSE, FingerprintState.NOT_ENOUGH_DATA, progress)
        } else band(
            dimension = FingerprintDimension.DOSE,
            now = window.doseMedianMicroSvH,
            low = reference.doseLowMicroSvH,
            median = reference.doseMedianMicroSvH,
            high = reference.doseHighMicroSvH,
            decimals = 2,
            s = s,
        )
        val counts = if (!intensityReady) {
            DimensionVerdict(
                FingerprintDimension.COUNT_RATE,
                FingerprintState.NOT_ENOUGH_DATA,
                progress,
            )
        } else band(
            dimension = FingerprintDimension.COUNT_RATE,
            now = window.cpsMedian,
            low = reference.cpsLow,
            median = reference.cpsMedian,
            high = reference.cpsHigh,
            decimals = 1,
            s = s,
        )
        val shape = shape(window, reference, s)

        val hardnessNow = if (!intensityReady) null else Hardness.of(
            doseRateMicroSvH = window.doseMedianMicroSvH.toDouble(),
            countRate = window.cpsMedian.toDouble(),
            seconds = window.seconds.toDouble(),
            doseErrPercent = window.doseErrPercent,
        )?.value
        val hardnessReference = Hardness.of(
            doseRateMicroSvH = reference.doseMedianMicroSvH.toDouble(),
            countRate = reference.cpsMedian.toDouble(),
            seconds = reference.accumulatedSeconds.toDouble(),
        )?.value

        return FingerprintComparison(
            verdicts = listOf(dose, counts, shape),
            hardnessNow = hardnessNow,
            hardnessReference = hardnessReference,
            hardnessChangePercent = percent(hardnessNow, hardnessReference),
        )
    }

    /** Голос по величине с полосой: внутри — SAME, снаружи — CHANGED. */
    private fun band(
        dimension: FingerprintDimension,
        now: Float,
        low: Float,
        median: Float,
        high: Float,
        decimals: Int,
        s: FingerprintStrings,
    ): DimensionVerdict {
        val inside = now in low..high
        return DimensionVerdict(
            dimension = dimension,
            state = if (inside) FingerprintState.SAME else FingerprintState.CHANGED,
            detail = s.nowVsReference(
                now = number(now, decimals),
                low = number(low, decimals),
                high = number(high, decimals),
            ),
            changePercent = percent(now.toDouble(), median.toDouble()),
        )
    }

    private fun shape(
        window: FingerprintWindow,
        reference: FingerprintReference,
        s: FingerprintStrings,
    ): DimensionVerdict {
        if (window.spectrum.size != reference.spectrum.size || window.spectrum.isEmpty()) {
            return DimensionVerdict(
                FingerprintDimension.SPECTRUM,
                FingerprintState.NOT_ENOUGH_DATA,
                s.differentChannelGrid,
            )
        }
        // Крайний канал — граница шкалы, а не форма спектра ([SpectrumEdge]).
        val result = ShapeChange.compare(
            reference = SpectrumEdge.withoutEdge(
                DoubleArray(reference.spectrum.size) { reference.spectrum[it].toDouble() },
            ),
            excursion = SpectrumEdge.withoutEdge(
                DoubleArray(window.spectrum.size) { window.spectrum[it].toDouble() },
            ),
        )
        val state = when (result.verdict) {
            ShapeVerdict.NOT_ENOUGH_DATA -> FingerprintState.NOT_ENOUGH_DATA
            ShapeVerdict.CONSISTENT -> FingerprintState.SAME
            ShapeVerdict.CHANGED -> FingerprintState.CHANGED
        }
        return DimensionVerdict(
            dimension = FingerprintDimension.SPECTRUM,
            state = state,
            detail = ShapeChange.detail(result),
        )
    }

    /** Относительное изменение к эталону; null там, где делить не на что. */
    fun percent(now: Double?, reference: Double?): Int? {
        if (now == null || reference == null || reference <= 0.0) return null
        return (((now - reference) / reference) * 100.0).roundToInt()
    }

    private fun number(value: Float, decimals: Int): String =
        String.format(java.util.Locale.US, "%.${decimals}f", value).uiDecimal()

    // ------------------------------------------------------------- wording

    /**
     * Одна фраза по итогу — описание наблюдения, а не причина: называется,
     * что именно изменилось; жёсткость стоит рядом как объяснение расхождения
     * дозы и счёта.
     */
    fun headline(
        comparison: FingerprintComparison,
        s: FingerprintStrings = FingerprintRu,
    ): String {
        val dose = comparison.of(FingerprintDimension.DOSE)
        val counts = comparison.of(FingerprintDimension.COUNT_RATE)
        val shape = comparison.of(FingerprintDimension.SPECTRUM)

        if (dose?.state == FingerprintState.NOT_EVALUATED) {
            return s.headlineNoReference
        }
        val intensityChanged = dose?.changed == true || counts?.changed == true
        val shapeChanged = shape?.changed == true
        // Найденное отличие сообщается, даже если проверено не всё: оно уже
        // найдено. А вот «отличий не найдено» при непроверенном измерении
        // сказать нельзя — это утверждение о том, чего никто не смотрел.
        if (!intensityChanged && !shapeChanged &&
            comparison.verdicts.any { it.state == FingerprintState.NOT_ENOUGH_DATA }
        ) {
            return s.headlineNotEnough
        }
        // «Совпадает» и «как в эталоне» — утверждения о равенстве, которых
        // сравнение не делало: каждое измерение проверяло ОТЛИЧИЕ и его не
        // нашло. Формулировки говорят ровно это.
        return when {
            intensityChanged && shapeChanged -> s.headlineBothChanged
            intensityChanged -> s.headlineIntensityChanged
            shapeChanged -> s.headlineShapeChanged
            else -> s.headlineNoDifference
        }
    }

    /** Строка-интерпретатор про жёсткость: она объясняет, а не голосует. */
    fun hardnessLine(
        comparison: FingerprintComparison,
        s: FingerprintStrings = FingerprintRu,
    ): String? {
        val now = comparison.hardnessNow ?: return null
        val reference = comparison.hardnessReference ?: return null
        val change = comparison.hardnessChangePercent ?: return null
        val direction = when {
            abs(change) <= HARDNESS_FLAT_PERCENT -> s.hardnessFlat
            change > 0 -> s.hardnessAbove(change)
            else -> s.hardnessBelow(abs(change))
        }
        return s.hardnessExplains(
            now = Hardness.format(now),
            reference = Hardness.format(reference),
            direction = direction,
        )
    }

    /**
     * Ниже этого про изменение жёсткости говорится «отличий не найдено».
     * **Инженерный параметр**: у частного двух шумных величин несколько
     * процентов — это шум, а не характер.
     */
    const val HARDNESS_FLAT_PERCENT = 5

    /**
     * Обязательная оговорка под выводом (спец §13).
     *
     * Функция, а не константа: `const val` не умеет зависеть от языка, а
     * оговорка обязана звучать на языке интерфейса — иначе главное
     * ограничение функции читал бы не тот, кому оно адресовано.
     */
    fun caveat(s: FingerprintStrings = FingerprintRu): String = s.caveat
}
