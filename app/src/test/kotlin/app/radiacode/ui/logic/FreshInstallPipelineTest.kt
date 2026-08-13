package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Полевой случай после переустановки: экран статистики видит 34 измерения
 * («P10 0,13 · медиана 0,13 · P90 0,14 · n 34 · 6 ч»), а поле графика в тот же
 * момент говорит «в этом окне нет измерений».
 *
 * Это не косметика: статистика считается по [ValueAggregate], а рисуются
 * колонки [ChartBucket], и тест воспроизводит ровно тот путь, которым идёт
 * экран, — от подсекундных агрегатов SQL до отобранных к рисованию колонок.
 * Если два представления одних данных расходятся, расходятся они здесь.
 */
class FreshInstallPipelineTest {

    private val now = 1_700_000_000_000L

    /** Как их отдаёт SQL: одна строка на секунду записи. */
    private fun freshSamples(seconds: Int): List<ValueAggregate> =
        (0 until seconds).map { i ->
            val at = now - (seconds - i) * 1_000L
            ValueAggregate(
                startMillis = at,
                minMicroSvH = 0.13f,
                maxMicroSvH = 0.14f,
                sumMicroSvH = 0.135,
                sumSqMicroSvH = 0.135 * 0.135,
                sampleCount = 1,
            )
        }

    /**
     * Повторяет `loadExact` + фильтр `buildFrame` для окна на живом крае.
     * @return число колонок, которые дойдут до рисования.
     */
    private fun visibleColumns(windowSpanMillis: Long, aggregates: List<ValueAggregate>): Int {
        val window = ChartWindows.latest(windowSpanMillis, now)
        val load = ChartWindows.loadRange(window, now)
        val bucketMillis = ChartSeriesModel.bucketMillis(load.spanMillis)
        val alignedFrom = ChartMapping.alignedFrom(load.toMillis, load.spanMillis, bucketMillis)
        val snapshot = ChartSeriesModel.snapshot(
            aggregates = aggregates,
            eventTimesMillis = emptyList(),
            alignedFromMillis = alignedFrom,
            toMillis = load.toMillis,
            bucketMillis = bucketMillis,
            subBucketMillis = 1_000L,
        )
        return snapshot.buckets.count {
            it.endMillis > window.fromMillis && it.startMillis < window.toMillis
        }
    }

    @Test
    fun `34 seconds of measurements are visible in the six hour window`() {
        val aggregates = freshSamples(34)
        val stats = ChartSeriesModel.windowStats(
            aggregates,
            now - 6 * 3_600_000L,
            now,
        )

        assertEquals(34, stats?.sampleCount, "статистика обязана видеть их все")
        assertTrue(
            visibleColumns(6 * 3_600_000L, aggregates) > 0,
            "при n > 0 поле не имеет права быть пустым",
        )
    }

    @Test
    fun `the same measurements are visible in a one minute window`() {
        // Карточка Главной: то же расхождение выглядит как «накапливаем
        // измерения…» при живом крупном числе.
        val aggregates = freshSamples(34)

        assertTrue(visibleColumns(60_000L, aggregates) > 0)
    }

    @Test
    fun `every ladder step shows the data it has`() {
        val aggregates = freshSamples(34)
        for ((_, span) in ChartWindows.PERIODS) {
            assertTrue(
                visibleColumns(span, aggregates) > 0,
                "окно $span мс потеряло все колонки",
            )
        }
    }

    @Test
    fun `n greater than zero and an empty field cannot coexist`() {
        // Инвариант экрана, а не деталь реализации: если статистика окна
        // насчитала измерения, поле обязано их показать. Проверяется на самом
        // `buildFrame`, которым живут и карточка Главной, и полный экран.
        val aggregates = freshSamples(34)
        for ((_, span) in ChartWindows.PERIODS) {
            val window = ChartWindows.latest(span, now)
            val load = ChartWindows.loadRange(window, now)
            val bucketMillis = ChartSeriesModel.bucketMillis(load.spanMillis)
            val snapshot = ChartSeriesModel.snapshot(
                aggregates = aggregates,
                eventTimesMillis = emptyList(),
                alignedFromMillis = ChartMapping.alignedFrom(
                    load.toMillis,
                    load.spanMillis,
                    bucketMillis,
                ),
                toMillis = load.toMillis,
                bucketMillis = bucketMillis,
                subBucketMillis = 1_000L,
            )
            val frame = app.radiacode.ui.screens.buildFrame(
                snapshot = snapshot,
                window = window,
                unit = app.radiacode.data.DoseUnitSetting.MICRO_SIEVERT,
                logScale = false,
                thresholds = app.radiacode.baseline.AlarmThresholds(
                    l1MicroSvH = 0.3f,
                    l2MicroSvH = 1f,
                    relativeFactor = 2f,
                    persistenceSeconds = 120,
                ),
                baseline = null,
                endpointAlert = false,
            )
            if (frame.stats != null) {
                assertTrue(
                    frame.spec.buckets.isNotEmpty(),
                    "окно $span мс: статистика видит n=${frame.stats?.sampleCount}, поле пусто",
                )
            }
        }
    }
}
