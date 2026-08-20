package app.alpha.ui.logic

import app.alpha.data.db.EnvironmentEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnvironmentSeriesTest {

    private val from = 1_000_000L
    private val bucket = 60_000L
    private val columns = 10

    private fun row(
        atMillis: Long,
        pressure: Float? = null,
        field: Float? = null,
        temp: Float? = null,
    ) = EnvironmentEntity(
        timestamp = atMillis,
        pressureHpa = pressure,
        magneticUt = field,
        phoneTempC = temp,
        samples = 6,
    )

    private fun of(
        rows: List<EnvironmentEntity>,
        deviceTemperature: List<Pair<Long, Float>> = emptyList(),
    ) = EnvironmentSeries.of(rows, deviceTemperature, from, bucket, columns)

    @Test
    fun `одна точка — не ряд`() {
        // Линия из одного значения показала бы движение, которого никто не мерил.
        assertTrue(of(listOf(row(from, pressure = 1013f))).isEmpty())
    }

    @Test
    fun `в колонку попадает среднее, а не последняя сводка`() {
        val series = assertNotNull(
            of(
                listOf(
                    row(from + 1_000, pressure = 1000f),
                    row(from + 2_000, pressure = 1002f),
                    row(from + bucket + 1_000, pressure = 1010f),
                ),
            ).firstOrNull { it.kind == EnvironmentSeries.Kind.PRESSURE },
        )
        assertEquals(1001f, series.min, 1e-3f)
        assertEquals(1010f, series.max, 1e-3f)
        assertEquals(1010f, series.last, 1e-3f)
    }

    @Test
    fun `шкала идёт от собственного минимума ряда, а не от нуля`() {
        // Иначе давление около 1000 гПа с размахом 4 гПа рисуется прямой.
        val series = assertNotNull(
            of(
                listOf(
                    row(from, pressure = 1008f),
                    row(from + bucket, pressure = 1012f),
                ),
            ).firstOrNull { it.kind == EnvironmentSeries.Kind.PRESSURE },
        )
        assertTrue(series.base > 1000f, "база ряда ${series.base} — шкала всё ещё от нуля")
        assertTrue(series.span < 10f, "размах шкалы ${series.span} слишком велик для 4 гПа")
        // Подписи делений — настоящие значения, а не смещённые.
        val ticks = series.ticks()
        assertEquals(1008f, ticks.first().second, 1e-3f)
        assertEquals(1012f, ticks.last().second, 1e-3f)
        // Смещение и подпись описывают одну точку.
        assertEquals(ticks.first().second - series.base, ticks.first().first, 1e-3f)
    }

    @Test
    fun `ряд без изменений не схлопывается в нулевую шкалу`() {
        val series = assertNotNull(
            of(
                listOf(
                    row(from, field = 48f),
                    row(from + bucket, field = 48f),
                ),
            ).firstOrNull { it.kind == EnvironmentSeries.Kind.FIELD },
        )
        assertTrue(series.span > 0f, "деление на нулевой размах")
    }

    @Test
    fun `датчика нет — ряда нет`() {
        val series = of(listOf(row(from, field = 48f), row(from + bucket, field = 49f)))
        assertNull(series.firstOrNull { it.kind == EnvironmentSeries.Kind.PRESSURE })
        assertEquals(1, series.size)
    }

    @Test
    fun `температура берётся с прибора, а не с телефона`() {
        // Телефон меряет свою батарею; в ряду среды ей не место, и записанное
        // значение не должно всплывать в графике под видом температуры.
        val series = of(
            rows = listOf(row(from, temp = 31f), row(from + bucket, temp = 32f)),
            deviceTemperature = listOf((from) to 23.6f, (from + bucket) to 23.9f),
        ).single()
        assertEquals(EnvironmentSeries.Kind.DEVICE_TEMPERATURE, series.kind)
        assertEquals(23.6f, series.min, 1e-3f)
        assertEquals(23.9f, series.max, 1e-3f)
    }

    @Test
    fun `сводки вне окна сессии не попадают в ряд`() {
        val series = of(
            listOf(
                row(from - bucket, pressure = 900f),
                row(from, pressure = 1010f),
                row(from + bucket, pressure = 1011f),
                row(from + columns * bucket + 1, pressure = 1200f),
            ),
        ).first { it.kind == EnvironmentSeries.Kind.PRESSURE }
        assertEquals(1010f, series.min, 1e-3f)
        assertEquals(1011f, series.max, 1e-3f)
    }
}
