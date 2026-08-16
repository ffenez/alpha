package app.radiacode.ui.chart

import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.ChartSeriesModel
import app.radiacode.ui.logic.ValueAggregate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Разрешение картинки задаёт ВИДИМОЕ окно и ширина поля — не прочитанный
 * диапазон.
 *
 * Дефект, ради которого это написано: на пятиминутном окне колонка получалась
 * в тридцать семь секунд (окно плюс час запаса с каждой стороны, делённые на
 * две сотни), и триста посекундных измерений превращались в восемь узлов.
 */
class ChartDownsamplerTest {

    private val phoneWidthPx = 1080f

    @Test
    fun `колонок столько, сколько видно пикселей`() {
        // Час при секундных агрегатах: данных хватает на любое разрешение,
        // поэтому решает ширина поля.
        val columns = ChartDownsampler.columnCount(
            widthPx = phoneWidthPx,
            spanMillis = 3_600_000L,
            subBucketMillis = 1_000L,
        )
        assertEquals((phoneWidthPx / ChartDownsampler.PIXELS_PER_COLUMN).toInt(), columns)
    }

    @Test
    fun `разрешения больше, чем у данных, картинка не выдумывает`() {
        // Минутное окно и секундные агрегаты — шестьдесят значений, и колонок
        // не может быть пятьсот, сколько бы пикселей ни было.
        val columns = ChartDownsampler.columnCount(
            widthPx = phoneWidthPx,
            spanMillis = 60_000L,
            subBucketMillis = 1_000L,
        )
        assertEquals(60, columns)
    }

    @Test
    fun `колонка никогда не уже агрегата`() {
        val millis = ChartDownsampler.columnMillis(
            widthPx = 4_000f,
            spanMillis = 60_000L,
            subBucketMillis = 1_000L,
        )
        assertEquals(1_000L, millis)
    }

    @Test
    fun `неизмеренное поле даёт прежнюю геометрию`() {
        val columns = ChartDownsampler.columnCount(
            widthPx = 0f,
            spanMillis = 6L * 3_600_000L,
            subBucketMillis = 1_000L,
        )
        assertEquals(ChartDownsampler.DEFAULT_COLUMNS, columns)
    }

    @Test
    fun `широкое поле даёт колонок не меньше узкого`() {
        val narrow = ChartDownsampler.columnCount(720f, 3_600_000L, 1_000L)
        val wide = ChartDownsampler.columnCount(2_000f, 3_600_000L, 1_000L)
        assertTrue(wide > narrow)
    }

    @Test
    fun `пятиминутное окно рисуется по измерениям, а не восемью узлами`() {
        // Так и читается снимок: диапазон с запасом, колонка в снимке широкая.
        val now = 1_700_000_000_000L
        val windowSpan = 5 * 60_000L
        val subBucket = 1_000L
        val aggregates = (0 until 300).map { i ->
            val value = 0.15f + (i % 7) * 0.001f
            ValueAggregate(
                startMillis = now - windowSpan + i * subBucket,
                minMicroSvH = value,
                maxMicroSvH = value,
                sumMicroSvH = value.toDouble(),
                sumSqMicroSvH = value.toDouble() * value,
                sampleCount = 1,
            )
        }
        val columnMillis = ChartDownsampler.columnMillis(phoneWidthPx, windowSpan, subBucket)
        val alignedFrom = ChartMapping.alignedFrom(now, windowSpan, columnMillis)
        val columns = ChartSeriesModel.fold(
            aggregates = aggregates,
            alignedFromMillis = alignedFrom,
            bucketMillis = columnMillis,
            bucketCount = ChartSeriesModel.bucketCount(
                windowSpan,
                columnMillis,
                maxColumns = ChartDownsampler.MAX_COLUMNS,
            ),
            subBucketMillis = subBucket,
        )
        assertTrue(columns.size >= 250, "колонок ${columns.size}, а измерений 300")
    }

    @Test
    fun `узкий всплеск переживает прореживание`() {
        val now = 1_700_000_000_000L
        val windowSpan = 6L * 3_600_000L
        val subBucket = 1_000L
        val spikeAt = now - windowSpan / 2
        val aggregates = (0 until 21_600).map { i ->
            val at = now - windowSpan + i * subBucket
            val value = if (at == spikeAt) 3.2f else 0.15f
            ValueAggregate(
                startMillis = at,
                minMicroSvH = value,
                maxMicroSvH = value,
                sumMicroSvH = value.toDouble(),
                sumSqMicroSvH = value.toDouble() * value,
                sampleCount = 1,
            )
        }
        val columnMillis = ChartDownsampler.columnMillis(phoneWidthPx, windowSpan, subBucket)
        val alignedFrom = ChartMapping.alignedFrom(now, windowSpan, columnMillis)
        val columns = ChartSeriesModel.fold(
            aggregates = aggregates,
            alignedFromMillis = alignedFrom,
            bucketMillis = columnMillis,
            bucketCount = ChartSeriesModel.bucketCount(
                windowSpan,
                columnMillis,
                maxColumns = ChartDownsampler.MAX_COLUMNS,
            ),
            subBucketMillis = subBucket,
        )
        // Колонка шире секунды — всплеск попал внутрь колонки и обязан
        // остаться её максимумом с точным моментом.
        assertTrue(columnMillis > subBucket)
        val spike = columns.firstOrNull { it.max > 3f }
        assertTrue(spike != null, "всплеск пропал после прореживания")
        assertEquals(spikeAt, spike.maxAtMillis)
    }

    @Test
    fun `сглаженный вид держит одно число колонок на любом окне`() {
        for (span in listOf(5L * 60_000L, 3_600_000L, 6L * 3_600_000L)) {
            val columnMillis = ChartDownsampler.columnMillis(
                widthPx = phoneWidthPx,
                spanMillis = span,
                subBucketMillis = 1_000L,
                smoothed = true,
            )
            val columns = span / columnMillis
            assertTrue(
                columns in 55..65,
                "окно $span мс дало $columns колонок сглаженного вида",
            )
        }
    }

    @Test
    fun `сглаживать нечего — сглаженный вид совпадает с подробным`() {
        // Минута секундной записи: колонка уже измерения быть не может, и
        // «сглаженный» вид не притворяется, что у него есть что усреднить.
        val fine = ChartDownsampler.columnMillis(phoneWidthPx, 60_000L, 1_000L)
        val smoothed = ChartDownsampler.columnMillis(
            widthPx = phoneWidthPx,
            spanMillis = 60_000L,
            subBucketMillis = 1_000L,
            smoothed = true,
        )
        assertEquals(fine, smoothed)
    }

    @Test
    fun `точки измерений показываются, только когда внутри колонки что-то есть`() {
        assertFalse(ChartDownsampler.rawDotsVisible(columnMillis = 1_000L, subBucketMillis = 1_000L))
        assertTrue(ChartDownsampler.rawDotsVisible(columnMillis = 3_000L, subBucketMillis = 1_000L))
        assertFalse(
            ChartDownsampler.rawDotsVisible(columnMillis = 60_000L, subBucketMillis = 1_000L),
        )
    }
}
