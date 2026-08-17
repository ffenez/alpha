package app.alpha.ui.logic

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindow
import app.alpha.analysis.SpectrumDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Увеличение меняет ПОДРОБНОСТЬ, а не форму спектра.
 *
 * Полевая жалоба: на логарифмической оси при увеличении между соседними
 * каналами появлялись вертикальные линии до самого низа поля — спектр
 * превращался в частокол. Причин было две, и обе проверяются здесь: кадр
 * растягивал десяток видимых каналов на двести сорок колонок (пустые колонки
 * приходили нулём и рисовались у нижней границы), а измеренный ноль на
 * логарифмической оси прижимался к полу и соединялся с соседями.
 */
class SpectrumZoomTest {

    /** Линейная шкала 3 кэВ/канал — как у приборов серии с 1024 каналами. */
    private val calibration = EnergyCalibration(0f, 3f, 0f)

    /** Спектр с провалами: между пиками есть каналы с нулём импульсов. */
    private val counts: List<Int> = List(1024) { channel ->
        when {
            channel % 50 == 0 -> 500
            channel % 7 == 0 -> 0
            else -> 40
        }
    }

    private fun frame(window: EnergyWindow?) = SpectrumFrames.build(
        counts = counts,
        durationSeconds = 600,
        calibration = calibration,
        window = window,
        scale = SpectrumScale.Log,
    )

    @Test
    fun `при увеличении колонок не больше, чем видимых каналов`() {
        // Самое сильное увеличение — окно в 300 кэВ (`MIN_WINDOW_KEV`), то
        // есть около сотни каналов при 3 кэВ на канал. Двести сорок колонок
        // означали бы больше сотни ПУСТЫХ колонок между ними — тот самый
        // частокол.
        val zoomed = frame(EnergyWindow(300f, 330f))
        val channels = zoomed.channels.last - zoomed.channels.first + 1
        assertTrue(channels < SpectrumFrames.COLUMN_COUNT, "видимых каналов $channels")
        assertEquals(channels, zoomed.columns.size)
    }

    @Test
    fun `в увеличенном кадре каждая колонка — реальный канал`() {
        val zoomed = frame(EnergyWindow(300f, 330f))
        // Ни одной колонки «без данных»: их и не может быть, когда колонок
        // ровно столько же, сколько каналов.
        assertTrue(zoomed.columns.none { it.isNaN() }, "${zoomed.columns}")
        // Порядок колонок — порядок каналов, то есть возрастание энергии.
        for (index in zoomed.columns.indices) {
            val channel = zoomed.channels.first + index
            assertEquals(counts[channel].toFloat(), zoomed.columns[index], "колонка $index")
        }
    }

    @Test
    fun `нулевой канал не превращается в точку у нижней границы`() {
        val zoomed = frame(EnergyWindow(300f, 330f))
        val segments = SpectrumPlot.segments(zoomed.columns, logScale = true)
        // Нули рвут линию, а не тянут её вниз: сегментов больше одного там,
        // где в окне есть пустые каналы.
        val zeros = zoomed.columns.count { it <= 0f }
        if (zeros > 0) assertTrue(segments.size > 1, "нули не разорвали линию")
        // И ни один нулевой канал не попал в рисуемые точки.
        for (segment in segments) {
            for (index in segment) assertTrue(zoomed.columns[index] > 0f, "ноль в сегменте")
        }
    }

    @Test
    fun `на линейной оси ноль остаётся точкой кривой`() {
        // Ноль имеет место на линейной шкале — там разрыва быть не должно.
        val values = listOf(5f, 0f, 7f)
        assertEquals(listOf(listOf(0, 1, 2)), SpectrumPlot.segments(values, logScale = false))
        assertEquals(listOf(listOf(0), listOf(2)), SpectrumPlot.segments(values, logScale = true))
    }

    @Test
    fun `колонка без каналов отличается от измеренного нуля`() {
        // Пустая колонка приходит как «нет данных», а не как ноль импульсов:
        // иначе отсутствие данных рисовалось бы измерением.
        val columns = SpectrumDisplay.aggregateMax(
            values = listOf(1f, 2f, 3f),
            range = 0..2,
            columnCount = 10,
        )
        assertTrue(columns.count { it.isNaN() } > 0)
        assertEquals(3f, SpectrumDisplay.columnsMax(columns))
    }

    @Test
    fun `полная шкала и увеличение дают одну и ту же форму`() {
        // Топология не меняется: и там, и там кривая идёт по реальным каналам
        // в порядке возрастания энергии, просто с разной подробностью.
        val whole = frame(null)
        val zoomed = frame(EnergyWindow(300f, 600f))
        assertTrue(whole.columns.size <= SpectrumFrames.COLUMN_COUNT)
        assertTrue(zoomed.columns.size <= SpectrumFrames.COLUMN_COUNT)
        for (segment in SpectrumPlot.segments(zoomed.columns, logScale = true)) {
            assertTrue(segment.zipWithNext().all { (a, b) -> b == a + 1 }, "разрыв внутри сегмента")
        }
    }
}
