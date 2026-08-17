package app.alpha.ui.logic

import app.alpha.analysis.EnergyWindowSpec
import app.alpha.analysis.EnergyWindows
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Границы спектральных диапазонов как ЦЕПОЧКА ЧИСЕЛ, а не набор пар.
 *
 * Три диапазона — это четыре границы. В цепочке невозможны ни пересечения
 * (импульс в двух окнах сразу), ни разрывы (кусок спектра вне анализа), и
 * проверять остаётся ширину и попадание в шкалу.
 *
 * Здесь же арифметика перетаскивания ручки на спектре: перевод доли ширины
 * поля в кэВ и обратно, захват ближайшей границы и её движение между
 * соседями. Чистый JVM — жест в Compose только передаёт числа.
 */
object EnergyBounds {

    /** Минимальная ширина диапазона — та же, что в [EnergyWindows.validate]. */
    const val MIN_SPAN_KEV = EnergyWindows.MIN_WIDTH_KEV

    /** Пресет, которым сейчас заданы границы. */
    enum class Preset { DEFAULT, FULL_SCALE, CUSTOM }

    /** Границы диапазонов по умолчанию (спец §7): 100 / 300 / 700 / 1500 кэВ. */
    fun defaults(): List<Float> = boundsOf(EnergyWindows.DEFAULTS)

    /**
     * Цепочка границ по набору окон. Соседние окна, стыкующиеся встык, дают
     * одну общую границу; у окон с разрывом (так можно было задать их старым
     * редактором) в цепочку попадают все края — разрыв становится видимым
     * диапазоном, а не молча исчезает.
     */
    fun boundsOf(specs: List<EnergyWindowSpec>): List<Float> {
        if (specs.isEmpty()) return defaults()
        val sorted = specs.sortedBy { it.startKeV }
        val bounds = mutableListOf(sorted.first().startKeV)
        for (spec in sorted) {
            if (spec.startKeV > bounds.last()) bounds += spec.startKeV
            if (spec.endKeV > bounds.last()) bounds += spec.endKeV
        }
        return bounds
    }

    /** Обратное преобразование: N границ → N−1 стыкующихся диапазонов. */
    fun toSpecs(bounds: List<Float>): List<EnergyWindowSpec> =
        bounds.zipWithNext { start, end -> EnergyWindowSpec(start, end) }

    /**
     * Чтение и запись хранимой настройки (`energy_windows_kev`).
     *
     * Формат на диске прежний — пары «100:300,300:700,700:1500». Цепочка
     * границ это способ правки, а не единица хранения, поэтому настройка
     * прошлых версий читается без изменений, как и в отчётах эксперимента.
     */
    fun parseStored(raw: String?): List<Float> = boundsOf(EnergyWindows.parse(raw))

    fun formatStored(bounds: List<Float>): String = EnergyWindows.format(toSpecs(bounds))

    /** Энергия под точкой поля, заданной долей его ширины. */
    fun keVAt(fraction: Float, viewStartKeV: Float, viewEndKeV: Float): Float =
        viewStartKeV + fraction.coerceIn(0f, 1f) * (viewEndKeV - viewStartKeV)

    /** Доля ширины поля, на которой стоит энергия (за полем — прижата к краю). */
    fun fractionOf(keV: Float, viewStartKeV: Float, viewEndKeV: Float): Float {
        val span = viewEndKeV - viewStartKeV
        if (span <= 0f) return 0f
        return ((keV - viewStartKeV) / span).coerceIn(0f, 1f)
    }

    /**
     * Какую границу взял палец: ближайшая в пределах [toleranceKeV], иначе
     * null (касание мимо ручек ничего не двигает — молчаливый захват соседней
     * границы был бы правкой чужого числа).
     */
    fun grab(bounds: List<Float>, keV: Float, toleranceKeV: Float): Int? {
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        bounds.forEachIndexed { index, bound ->
            val distance = abs(bound - keV)
            if (distance <= toleranceKeV && distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * Двигает одну границу. Соседние границы — жёсткие стены: граница не
     * обгоняет соседнюю и не подходит ближе [MIN_SPAN_KEV], крайние упираются
     * в [minKeV]/[maxKeV].
     *
     * Значение округляется до целых кэВ: ширина канала у приборов серии —
     * единицы кэВ, и доли кэВ в границе анализа смысла не несут.
     */
    fun move(
        bounds: List<Float>,
        index: Int,
        keV: Float,
        minKeV: Float = EnergyWindows.MIN_BOUND_KEV,
        maxKeV: Float = EnergyWindows.MAX_BOUND_KEV,
    ): List<Float> {
        if (index !in bounds.indices || !keV.isFinite()) return bounds
        val lower = if (index == 0) minKeV else bounds[index - 1] + MIN_SPAN_KEV
        val upper = if (index == bounds.lastIndex) maxKeV else bounds[index + 1] - MIN_SPAN_KEV
        if (upper < lower) return bounds
        val value = keV.roundToInt().toFloat().coerceIn(lower, upper)
        return bounds.toMutableList().also { it[index] = value }
    }

    /**
     * «Весь диапазон прибора»: цепочка от начала до конца текущей шкалы,
     * поделённая на [count] равных частей. Равные части — сознательно
     * НЕЙТРАЛЬНОЕ деление: любое другое (например «где обычно линии») было бы
     * физической категорией, которой у границ анализа нет.
     */
    fun fullScale(startKeV: Float, endKeV: Float, count: Int = 3): List<Float> {
        val from = startKeV.coerceAtLeast(EnergyWindows.MIN_BOUND_KEV)
        val to = endKeV.coerceAtMost(EnergyWindows.MAX_BOUND_KEV)
        if (count < 1 || to - from < MIN_SPAN_KEV * count) return defaults()
        val step = (to - from) / count
        val bounds = (0..count).map { i ->
            when (i) {
                0 -> from.roundToInt().toFloat()
                count -> to.roundToInt().toFloat()
                // Внутренние границы округляются до десятков: «938,7» —
                // результат деления, а не выбранное значение.
                else -> (((from + step * i) / 10f).roundToInt() * 10).toFloat()
            }
        }
        return bounds
    }

    /** Каким пресетом заданы границы (для подсветки выбранного). */
    fun presetOf(bounds: List<Float>, fullStartKeV: Float, fullEndKeV: Float): Preset = when {
        same(bounds, defaults()) -> Preset.DEFAULT
        same(bounds, fullScale(fullStartKeV, fullEndKeV, bounds.size - 1)) -> Preset.FULL_SCALE
        else -> Preset.CUSTOM
    }

    private fun same(a: List<Float>, b: List<Float>): Boolean =
        a.size == b.size && a.indices.all { abs(a[it] - b[it]) < 0.5f }
}
