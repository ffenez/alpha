package app.radiacode.ui.logic

import androidx.compose.runtime.Immutable
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.EnergyWindow
import app.radiacode.analysis.SpectrumDisplay
import kotlin.math.max

/**
 * Кадр спектра: от сырых импульсов до колонок экрана.
 *
 * Собран отдельно от экранов, потому что экранов ДВА — вкладка «Спектр» и
 * полноэкранный режим, — а картинка обязана быть одна. Две реализации одного
 * графика уже однажды разошлись математикой между Главной и полным экраном, и
 * человек искал на большом графике то, что видел на маленьком.
 *
 * Сырые данные не меняются: вычитание фона нормируется по времени накопления,
 * сглаживание — только отображение.
 */
object SpectrumFrames {

    /** Разрешение экрана: 1024 канала сливаются в столько колонок. */
    const val COLUMN_COUNT = 240

    /**
     * Доля импульсов в крайнем канале, начиная с которой это СОСТОЯНИЕ, а не
     * постоянная статистика.
     *
     * У RC-110 в крайнем канале почти всегда что-то есть, поэтому строка «у
     * верхней границы шкалы: N имп.» стояла под графиком всегда и перестала
     * читаться. Порог ИНЖЕНЕРНЫЙ: пока за краем меньше сотой доли всех
     * импульсов, картинка ничего заметного не прячет, и число живёт в
     * технических данных справки. Сам факт исключения крайнего канала не
     * зависит от порога — он исключён всегда ([app.radiacode.analysis.SpectrumEdge]).
     */
    const val EDGE_NOTICE_FRACTION = 0.01f

    /** Стоит ли говорить о крае шкалы прямо под графиком. */
    fun edgeNoticeVisible(edgeCounts: Long, totalCounts: Long): Boolean {
        if (edgeCounts <= 0L) return false
        if (totalCounts <= 0L) return true
        return edgeCounts.toDouble() / totalCounts >= EDGE_NOTICE_FRACTION
    }

    @Immutable
    data class Frame(
        /** Вся шкала прибора без крайнего канала ([SpectrumDisplay.fullWindow]). */
        val full: EnergyWindow,
        /** Показанное окно: заданное зумом и зажатое внутрь [full]. */
        val visible: EnergyWindow,
        /** Каналы, попавшие в окно. */
        val channels: IntRange,
        /** Значения колонок (максимум каналов колонки). */
        val columns: List<Float>,
        /** Записанный фон, приведённый к времени накопления; null — не рисуется. */
        val overlay: List<Float>?,
        /** Верх оси значений для выбранного масштаба. */
        val yTop: Float,
    ) {
        /** Окно уже включает всю шкалу — сдвигать его некуда. */
        val wholeRange: Boolean get() = visible.widthKeV >= full.widthKeV - 1f
    }

    fun build(
        counts: List<Int>,
        durationSeconds: Long,
        calibration: EnergyCalibration,
        background: List<Int>? = null,
        backgroundSeconds: Long = 0L,
        window: EnergyWindow? = null,
        subtract: Boolean = false,
        /** Рисовать ли записанный фон серой кривой поверх спектра. */
        overlayBackground: Boolean = true,
        smoothing: Boolean = false,
        scale: SpectrumScale = SpectrumScale.Log,
        columnCount: Int = COLUMN_COUNT,
    ): Frame {
        val full = SpectrumDisplay.fullWindow(calibration, counts.size)
        val visible = window?.let { SpectrumDisplay.clampInto(it, full) } ?: full
        val channels = SpectrumDisplay.channelRange(visible, calibration, counts.size)

        val base = if (subtract && background != null) {
            SpectrumDisplay.subtractBackground(
                current = counts,
                currentSeconds = durationSeconds,
                background = background,
                backgroundSeconds = backgroundSeconds,
            )
        } else {
            counts.map { it.toFloat() }
        }
        val series = if (smoothing) SpectrumDisplay.movingAverage(base) else base
        val columns = SpectrumDisplay.aggregateMax(series, channels, columnCount)

        // Фон показывается наложением ТОЛЬКО в обычном режиме: вычитать его и
        // одновременно рисовать — использовать одни и те же импульсы дважды.
        // И только по просьбе: серая кривая поверх данных это сравнение, а
        // сравнение человек начинает сам, когда оно ему нужно.
        val overlay = if (background == null || subtract || !overlayBackground) {
            null
        } else {
            SpectrumDisplay.aggregateMax(
                SpectrumDisplay.scaleToDuration(
                    background,
                    backgroundSeconds = backgroundSeconds,
                    currentSeconds = durationSeconds,
                ),
                channels,
                columnCount,
            )
        }

        val dataMax = max(columns.maxOrNull() ?: 0f, overlay?.maxOrNull() ?: 0f)
        // Верх оси принадлежит МАСШТАБУ: у логарифма это степень десяти (иначе
        // декадные линии не попадают на ровные доли высоты), у остальных —
        // сам максимум с запасом, но не ниже десяти импульсов.
        val yTop = if (scale is SpectrumScale.Log) {
            SpectrumDisplay.logTop(dataMax)
        } else {
            max(dataMax * 1.15f, 10f)
        }
        return Frame(full, visible, channels, columns, overlay, yTop)
    }
}
