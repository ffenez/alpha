package app.alpha.ui.logic

import androidx.compose.runtime.Immutable
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindow
import app.alpha.analysis.SpectrumDisplay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Отметка энергии на поле спектра: «вот где эта линия по калибровке».
 *
 * Ставится тапом по строке таблицы «Проверка по линиям» в справке о нуклиде.
 * Отметка НЕ является выводом о спектре: она не считает импульсы, не проверяет
 * пик и ничего не подтверждает — это перевод табличного числа в место на
 * картинке через ту же калибровку, которой подписана ось. Поэтому она:
 *
 *  - живёт [LIFETIME_MILLIS] и исчезает сама (указатель, а не состояние);
 *  - привязана к КАДРУ ([Anchor]): другой спектр, другой масштаб оси или
 *    другое окно зума — другая картинка, и указывать в ней старым числом
 *    нельзя;
 *  - рисуется отдельно и от курсора (тот говорит о СЫРОМ счёте в канале), и от
 *    маркера пика (тот стоит над найденным пиком).
 *
 * Вся геометрия — тот же путь, каким считается сам кадр: энергия → канал
 * (`EnergyCalibration.channelAt`) → колонка (`SpectrumDisplay.columnForChannel`)
 * → доля ширины поля ([SpectrumPlot.columnFraction]). Ни одного собственного
 * пересчёта пикселей здесь нет — иначе отметка со временем разъехалась бы с
 * кривой, которую она комментирует.
 */
object SpectrumHighlight {

    /**
     * Сколько отметка держится на поле.
     *
     * **Инженерный параметр**: пятнадцати секунд хватает перевести взгляд со
     * строки таблицы на картинку и разглядеть место. Бессрочная отметка
     * читалась бы как часть графика, то есть как вывод о спектре.
     */
    const val LIFETIME_MILLIS = 15_000L

    /**
     * Насколько глубоко внутрь окна заводится энергия, к которой окно едет.
     * Доля ПОЛУШИРИНЫ от края: у самой кромки поля отметка сливается с рамкой
     * и с подписями оси, и «доехали» выглядит как «не попали».
     */
    const val EDGE_MARGIN_FRACTION = 0.08f

    /** Допуск сравнения окон: кэВ дробные, а окно пересобирается из тех же чисел. */
    const val ANCHOR_TOLERANCE_KEV = 0.5f

    /**
     * Кадр, в котором отметка имеет смысл: что показано, каким масштабом и в
     * каком окне. Живое накопление сюда НЕ входит — счёт в каналах растёт
     * каждые несколько секунд, и от этого место линии по калибровке не меняется.
     */
    @Immutable
    data class Anchor(
        /** Что за спектр на поле: калибровка и число каналов. */
        val spectrumKey: String,
        /** Масштаб оси значений ([SpectrumScale.id]). */
        val scaleId: String,
        val startKeV: Float,
        val endKeV: Float,
    )

    @Immutable
    data class Mark(
        val energyKeV: Float,
        val anchor: Anchor,
        /** Момент появления: отметка временная и сама об этом знает. */
        val shownAtMillis: Long,
        /**
         * Что случилось с картинкой ради этой отметки: подпись обязана
         * сказать и «окно сдвинуто», и «этой линии на шкале прибора нет».
         */
        val outcome: Aim,
    )

    /** Что пришлось сделать с окном, чтобы энергия оказалась на поле. */
    enum class Aim {
        /** Энергия уже видна — картинка не двигается. */
        VISIBLE,

        /** Окно доехало до энергии; ширина (кратность зума) сохранена. */
        MOVED,

        /** Энергия вне шкалы прибора: показывать нечего, и это говорится словами. */
        OUT_OF_SCALE,
    }

    data class Aiming(val window: EnergyWindow, val outcome: Aim)

    /** Ключ спектра для [Anchor]: калибровка и число каналов, без счёта. */
    fun spectrumKey(calibration: EnergyCalibration, channelCount: Int): String =
        "${calibration.a0}/${calibration.a1}/${calibration.a2}/$channelCount"

    fun anchor(spectrumKey: String, scaleId: String, window: EnergyWindow): Anchor =
        Anchor(spectrumKey, scaleId, window.startKeV, window.endKeV)

    /**
     * Куда сдвинуть окно, чтобы энергия попала на поле.
     *
     * Ширина окна не меняется: кратность зума выбрана человеком. Окно едет
     * только тогда, когда энергия за краем или в его margin-полоске.
     *
     * Энергия за пределами шкалы прибора отметки не получает — вместо неё
     * [Aim.OUT_OF_SCALE].
     */
    fun aim(energyKeV: Float, visible: EnergyWindow, full: EnergyWindow): Aiming {
        if (energyKeV < full.startKeV || energyKeV > full.endKeV) {
            return Aiming(visible, Aim.OUT_OF_SCALE)
        }
        val margin = visible.widthKeV * EDGE_MARGIN_FRACTION
        if (energyKeV >= visible.startKeV + margin && energyKeV <= visible.endKeV - margin) {
            return Aiming(visible, Aim.VISIBLE)
        }
        val half = visible.widthKeV / 2f
        val moved = SpectrumDisplay.clampInto(
            EnergyWindow(energyKeV - half, energyKeV + half),
            full,
        )
        // У края шкалы окно упирается в границу и энергия остаётся у кромки —
        // это не отказ: ближе к центру её не поставить, и она видна.
        return Aiming(moved, if (sameWindow(moved, visible)) Aim.VISIBLE else Aim.MOVED)
    }

    /** Отметка жива: тот же кадр и время не вышло. */
    fun alive(mark: Mark?, anchor: Anchor, nowMillis: Long): Boolean {
        if (mark == null) return false
        if (mark.anchor.spectrumKey != anchor.spectrumKey) return false
        if (mark.anchor.scaleId != anchor.scaleId) return false
        if (!sameWindow(mark.anchor, anchor)) return false
        return nowMillis - mark.shownAtMillis < LIFETIME_MILLIS
    }

    /** Сколько отметке осталось — для таймера, снимающего её без нажатий. */
    fun remainingMillis(mark: Mark?, nowMillis: Long): Long {
        if (mark == null) return 0L
        return (mark.shownAtMillis + LIFETIME_MILLIS - nowMillis).coerceAtLeast(0L)
    }

    /**
     * Доля ширины ПОЛЯ под энергией; null — энергия не попала в показанные
     * каналы (окно не доехало или линия вне шкалы).
     */
    fun fraction(
        energyKeV: Float,
        calibration: EnergyCalibration,
        channels: IntRange,
        columnCount: Int,
    ): Float? {
        if (channels.isEmpty() || columnCount <= 0) return null
        val channel = calibration.channelAt(energyKeV).roundToInt()
        val column = SpectrumDisplay.columnForChannel(channel, channels, columnCount) ?: return null
        return SpectrumPlot.columnFraction(column, columnCount)
    }

    private fun sameWindow(a: EnergyWindow, b: EnergyWindow): Boolean =
        abs(a.startKeV - b.startKeV) <= ANCHOR_TOLERANCE_KEV &&
            abs(a.endKeV - b.endKeV) <= ANCHOR_TOLERANCE_KEV

    private fun sameWindow(a: Anchor, b: Anchor): Boolean =
        abs(a.startKeV - b.startKeV) <= ANCHOR_TOLERANCE_KEV &&
            abs(a.endKeV - b.endKeV) <= ANCHOR_TOLERANCE_KEV
}
