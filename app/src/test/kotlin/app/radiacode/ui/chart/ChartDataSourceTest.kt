package app.radiacode.ui.chart

import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.QuantileMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Решение «читать или перепроецировать». Ошибка в одну сторону — запрос в базу
 * на каждом кадре жеста, в другую — картинка не того разрешения или, хуже,
 * числа, посчитанные другим путём, без единого слова об этом на экране.
 */
class ChartDataSourceTest {

    private val now = 1_700_000_000_000L

    private fun window(spanMillis: Long, endMillis: Long = now) =
        ChartWindow(endMillis - spanMillis, endMillis)

    @Test
    fun `сдвиг внутри прочитанного не требует чтения`() {
        val w = window(5 * 60_000L)
        val load = ChartDataSource.readRange(w, now)
        val bucket = ChartDataSource.expectedBucketMillis(w, now)
        val moved = ChartWindow(w.fromMillis - 60_000L, w.toMillis - 60_000L)
        assertTrue(ChartDataSource.reusable(load, bucket, moved, now))
    }

    @Test
    fun `выход за прочитанный диапазон требует чтения`() {
        val w = window(5 * 60_000L)
        val load = ChartDataSource.readRange(w, now)
        val bucket = ChartDataSource.expectedBucketMillis(w, now)
        val far = ChartWindow(w.fromMillis - 6 * 3_600_000L, w.toMillis - 6 * 3_600_000L)
        assertFalse(ChartDataSource.reusable(load, bucket, far, now))
    }

    @Test
    fun `небольшой щипок переиспользует снимок`() {
        val w = window(5 * 60_000L)
        val load = ChartDataSource.readRange(w, now)
        val bucket = ChartDataSource.expectedBucketMillis(w, now)
        // Пальцы разошлись на четверть — окно короче, колонка расчётно уже,
        // но разрешение картинки то же.
        val zoomed = window(4 * 60_000L)
        assertTrue(ChartDataSource.reusable(load, bucket, zoomed, now))
    }

    @Test
    fun `заметно другое разрешение требует чтения`() {
        // Месячный снимок покрывает полусуточное окно, и путь квантилей у обоих
        // один — но колонка месяца это одиннадцать часов, а полусуток час.
        // Нарисовать полусутки одиннадцатичасовыми колонками значит показать
        // одну точку вместо ряда.
        val month = window(30L * 24 * 3_600_000L)
        val load = ChartDataSource.readRange(month, now)
        val bucket = ChartDataSource.expectedBucketMillis(month, now)
        val half = window(12L * 3_600_000L)
        assertEquals(ChartDataSource.methodFor(month, now), ChartDataSource.methodFor(half, now))
        assertTrue(app.radiacode.ui.logic.ChartWindows.covers(load, half))
        assertFalse(ChartDataSource.reusable(load, bucket, half, now))
    }

    @Test
    fun `смена пути квантилей требует чтения при любом покрытии`() {
        // Длинное окно читается слиянием почасовых скетчей; короткое внутри
        // него — точными порядковыми статистиками. Диапазон покрывает, но
        // числа получаются другим способом, и подменять его молча нельзя.
        val long = window(24 * 3_600_000L)
        val load = ChartDataSource.readRange(long, now)
        val bucket = ChartDataSource.expectedBucketMillis(long, now)
        assertEquals(QuantileMethod.KLL_SKETCH, ChartDataSource.methodFor(long, now))
        val short = window(30 * 60_000L)
        assertEquals(QuantileMethod.EXACT_RAW, ChartDataSource.methodFor(short, now))
        assertFalse(ChartDataSource.reusable(load, bucket, short, now))
    }

    @Test
    fun `пустого снимка не бывает достаточно`() {
        val w = window(5 * 60_000L)
        assertFalse(ChartDataSource.reusable(null, null, w, now))
        assertFalse(ChartDataSource.reusable(ChartDataSource.readRange(w, now), 0L, w, now))
    }
}
