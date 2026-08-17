package app.alpha.ui.logic

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.Peak
import app.alpha.analysis.SpectrumDisplay
import kotlin.math.roundToInt

/**
 * Чистая геометрия поля спектра: значение ↔ пиксель по вертикали, палец ↔
 * КАНАЛ по горизонтали.
 *
 * Живёт отдельно от рисования по той же причине, что и вся остальная
 * математика графиков: попадание курсора в канал — это утверждение о данных
 * («вы показываете на канал 316»), и оно обязано проверяться тестом, а не
 * глазами на приборе. Рисование только применяет эти числа.
 *
 * **Курсор отвечает про КАНАЛ, а не про «имп/кэВ».** Ширина канала по шкале
 * меняется (E = a0 + a1·ch + a2·ch²), поэтому деление счёта на неё показывало
 * бы не то, что показывает прибор — то же правило, что у подписи оси.
 */
object SpectrumPlot {

    /**
     * Доля высоты экрана под встроенное поле спектра на вкладке.
     *
     * Спектр — главная картинка вкладки, и его рассматривают дольше остальных:
     * фиксированные 170 dp занимали пятую часть экрана и на телефоне читались
     * как эскиз. Треть высоты — размер, на котором пик виден формой, а не
     * зазубриной, и при этом под полем остаётся начало таблицы пиков: не
     * прокрутив ни строки, человек видит и картинку, и первые кандидаты.
     */
    const val FIELD_HEIGHT_FRACTION = 0.34f

    /**
     * Высота поля в dp: доля экрана, зажатая границами
     * ([app.alpha.ui.theme.Dimens.spectrumFieldMin] /
     * `spectrumFieldMax`). Зажим и есть защита краёв — мелкого экрана, где
     * доля оставила бы полоску, и крупного, где поле съело бы страницу.
     */
    fun fieldHeightDp(screenHeightDp: Float, minDp: Float, maxDp: Float): Float {
        val top = kotlin.math.max(minDp, maxDp)
        return (screenHeightDp * FIELD_HEIGHT_FRACTION).coerceIn(minDp, top)
    }

    /**
     * Y-пиксель значения: верх поля [topPx], высота [heightPx].
     *
     * Ноль по вертикали — верх поля (как в Canvas), поэтому доля масштаба
     * вычитается из единицы.
     */
    fun yPx(
        value: Float,
        top: Float,
        scale: SpectrumScale,
        topPx: Float,
        heightPx: Float,
    ): Float = topPx + (1f - scale.fraction(value, top)) * heightPx

    /**
     * Непрерывные куски кривой: индексы колонок, которые можно соединять.
     *
     * Колонка бывает трёх видов, и смешивать их нельзя:
     *
     *  - число больше нуля — точка кривой;
     *  - `NaN` — в колонку не попал ни один канал: данных нет;
     *  - ноль или меньше — измеренный ноль, у которого на логарифмической оси
     *    нет места (log 0 не существует).
     *
     * Разрыв в обоих последних случаях — единственный честный способ показать
     * отсутствие: попытка нарисовать их у нижней границы соединяла пустое
     * место с соседними каналами, и при увеличении спектр превращался в
     * частокол вертикальных линий до низа поля. Увеличение обязано менять
     * только горизонтальную подробность, а не топологию кривой.
     */
    fun segments(values: List<Float>, logScale: Boolean): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        for (index in values.indices) {
            val value = values[index]
            val drawable = !value.isNaN() && (!logScale || value > 0f)
            if (drawable) {
                current.add(index)
            } else if (current.isNotEmpty()) {
                out += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    /** X-пиксель колонки: первая — у левого края поля, последняя — у правого. */
    fun columnXPx(index: Int, columnCount: Int, leftPx: Float, widthPx: Float): Float =
        leftPx + if (columnCount <= 1) 0f else index.toFloat() * widthPx / (columnCount - 1)

    /**
     * Доля ширины ПОЛЯ по пикселю касания.
     *
     * Оси спектра подписаны внутри поля, и подписи забирают у него левый край:
     * доля, посчитанная от всей ширины узла, сдвинула бы курсор на ширину этих
     * подписей — курсор указывал бы не туда, куда смотрит палец.
     */
    fun plotFraction(xPx: Float, leftPx: Float, widthPx: Float): Float =
        if (widthPx <= 0f) 0f else ((xPx - leftPx) / widthPx).coerceIn(0f, 1f)

    /** Колонка под долей ширины — обратное к [columnXPx]. */
    fun columnAt(fraction: Float, columnCount: Int): Int {
        if (columnCount <= 1) return 0
        return (fraction.coerceIn(0f, 1f) * (columnCount - 1)).roundToInt()
            .coerceIn(0, columnCount - 1)
    }

    /**
     * Доля ширины поля, на которой стоит колонка — обратное к [columnAt] и та
     * же геометрия, что у [columnXPx]: первая колонка у левого края, последняя
     * у правого.
     */
    fun columnFraction(index: Int, columnCount: Int): Float {
        if (columnCount <= 1) return 0f
        return index.coerceIn(0, columnCount - 1).toFloat() / (columnCount - 1)
    }

    /**
     * Каналы, слитые в одну колонку — обратное к
     * [SpectrumDisplay.columnForChannel].
     *
     * Колонок может быть больше, чем каналов в окне (глубокий зум): такая
     * колонка показывает ровно один канал, и вернуть пустой диапазон нельзя —
     * курсору не о чем было бы говорить.
     */
    fun channelsOfColumn(column: Int, range: IntRange, columnCount: Int): IntRange {
        val span = (range.last - range.first + 1).coerceAtLeast(1)
        val count = columnCount.coerceAtLeast(1)
        val index = column.coerceIn(0, count - 1)
        val start = range.first + ceilDiv(index.toLong() * span, count)
        val endExclusive = range.first + ceilDiv((index + 1).toLong() * span, count)
        if (endExclusive <= start) {
            val single = (range.first + (index.toLong() * span / count).toInt())
                .coerceIn(range.first, range.last)
            return single..single
        }
        return start.coerceAtMost(range.last)..(endExclusive - 1).coerceAtMost(range.last)
    }

    private fun ceilDiv(value: Long, divisor: Int): Int =
        ((value + divisor - 1) / divisor).toInt()

    /**
     * Что курсор говорит о точке спектра.
     *
     * Счёт — СЫРОЙ счёт канала, а не высота нарисованной кривой: сглаживание
     * и «− фон» это способы посмотреть, а измерил прибор именно это число.
     * По сырым же импульсам считаются пики, поэтому значимость рядом с числом
     * относится к тем же данным.
     */
    data class Readout(
        /** Канал, о котором говорит карточка: самый населённый в колонке. */
        val channel: Int,
        /** Все каналы, слитые в эту колонку экрана. */
        val channels: IntRange,
        val energyKeV: Float,
        val counts: Int,
        /** Пик, центр которого попал в эту колонку; иначе null. */
        val peak: Peak?,
    ) {
        /** В колонке больше одного канала — на экране показан их максимум. */
        val merged: Boolean get() = channels.last > channels.first
    }

    /**
     * Карточка курсора по доле ширины поля.
     *
     * Колонка на экране — максимум нескольких каналов ([SpectrumDisplay.aggregateMax]),
     * поэтому и курсор называет канал с МАКСИМУМОМ: он и нарисован. Все
     * слитые каналы возвращаются рядом — иначе «канал 316» выглядел бы точнее,
     * чем картинка на самом деле.
     */
    fun readout(
        fraction: Float,
        range: IntRange,
        columnCount: Int,
        counts: List<Int>,
        calibration: EnergyCalibration,
        peaks: List<Peak> = emptyList(),
    ): Readout? {
        if (counts.isEmpty() || range.isEmpty()) return null
        val channels = channelsOfColumn(columnAt(fraction, columnCount), range, columnCount)
        var channel = channels.first
        var best = -1
        for (c in channels) {
            val value = counts.getOrNull(c) ?: continue
            if (value > best) {
                best = value
                channel = c
            }
        }
        if (best < 0) return null
        return Readout(
            channel = channel,
            channels = channels,
            energyKeV = calibration.energyAt(channel.toFloat()),
            counts = best,
            peak = peaks.firstOrNull { it.channel in channels },
        )
    }
}
