package app.alpha.ui.logic

import app.alpha.baseline.AlarmThresholds
import app.alpha.data.DoseUnitSetting
import app.alpha.ui.screens.buildFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Подробный и сглаженный вид — два представления ОДНИХ измерений.
 *
 * Проверяется именно то, что было сломано: ширину колонки задавал загруженный
 * диапазон (окно плюс час запаса с каждой стороны, который читается ради
 * мгновенного перелистывания), и на пятиминутном окне триста измерений
 * превращались в горстку узлов. Запас чтения — решение о производительности, и
 * менять разрешение картинки он не имеет права.
 */
class ChartDetailModeTest {

    private val now = 1_700_000_000_000L

    /** Секундные агрегаты, как их отдаёт SQL на точном пути. */
    private fun samples(seconds: Int, from: Long): List<ValueAggregate> =
        (0 until seconds).map { i ->
            val at = from + i * 1_000L
            // Пила: значение зависит от секунды, поэтому усреднение видно.
            val value = if (i % 2 == 0) 0.12f else 0.18f
            ValueAggregate(
                startMillis = at,
                minMicroSvH = value,
                maxMicroSvH = value,
                sumMicroSvH = value.toDouble(),
                sumSqMicroSvH = value.toDouble() * value,
                sampleCount = 1,
            )
        }

    /**
     * Снимок, прочитанный ТАК ЖЕ, как его читает экран: по загруженному
     * диапазону, а не по видимому окну.
     */
    private fun snapshotOf(window: ChartWindow): ChartSnapshot {
        val load = ChartWindows.loadRange(window, now)
        val bucketMillis = ChartSeriesModel.bucketMillis(load.spanMillis)
        val alignedFrom = ChartMapping.alignedFrom(load.toMillis, load.spanMillis, bucketMillis)
        return ChartSeriesModel.snapshot(
            aggregates = samples(
                seconds = ((load.toMillis - alignedFrom) / 1000L).toInt(),
                from = alignedFrom,
            ),
            eventTimesMillis = emptyList(),
            alignedFromMillis = alignedFrom,
            toMillis = load.toMillis,
            bucketMillis = bucketMillis,
            subBucketMillis = QuantilePaths.exactSubBucketMillis(),
        )
    }

    private fun frame(window: ChartWindow, detail: ChartDetailMode) = buildFrame(
        snapshot = snapshotOf(window),
        window = window,
        unit = DoseUnitSetting.MICRO_SIEVERT,
        logScale = false,
        thresholds = AlarmThresholds(
            l1MicroSvH = 0f,
            l2MicroSvH = 0f,
            relativeFactor = 2f,
            persistenceSeconds = 120,
        ),
        baseline = null,
        endpointAlert = false,
        detail = detail,
    )

    private val fiveMinutes = ChartWindow(now - 5L * 60_000L, now)

    /**
     * Пять минут секундной записи — это триста измерений, а не десяток узлов.
     * Порог намеренно грубый: он ловит потерю ПОРЯДКА разрешения, а не
     * конкретное число колонок.
     */
    @Test
    fun `a five minute window keeps the detail of its measurements`() {
        val detailed = frame(fiveMinutes, ChartDetailMode.DETAILED)
        val smoothed = frame(fiveMinutes, ChartDetailMode.SMOOTHED)

        assertTrue(
            detailed.spec.buckets.size > 100,
            "подробный вид дал ${detailed.spec.buckets.size} колонок",
        )
        // Сглаженный вид грубее — но по ПРАВИЛУ, а не по случайности запаса
        // чтения: шестьдесят колонок на любом окне, чтобы в колонке было чему
        // разбрасываться. Раньше здесь выходил десяток колонок, потому что
        // ширину задавал прочитанный диапазон (окно плюс час с каждой стороны).
        assertTrue(
            smoothed.spec.buckets.size in 40..80,
            "сглаженный вид дал ${smoothed.spec.buckets.size} колонок",
        )
        assertTrue(smoothed.spec.buckets.size < detailed.spec.buckets.size)
        assertTrue(detailed.spec.detailed)
        assertTrue(!smoothed.spec.detailed)
    }

    /** Переключение вида не трогает данные: те же измерения, другая картинка. */
    @Test
    fun `the window statistics do not depend on the view`() {
        val detailed = frame(fiveMinutes, ChartDetailMode.DETAILED)
        val smoothed = frame(fiveMinutes, ChartDetailMode.SMOOTHED)

        assertEquals(smoothed.stats?.sampleCount, detailed.stats?.sampleCount)
        assertEquals(smoothed.stats?.median, detailed.stats?.median)
        assertEquals(smoothed.stats?.p10, detailed.stats?.p10)
        assertEquals(smoothed.stats?.p90, detailed.stats?.p90)
    }

    /**
     * Подробный вид не рисует квантильные заливки и не дублирует линию
     * точками: две картинки сразу означали бы два разных утверждения об одних
     * измерениях.
     */
    @Test
    fun `the detailed view drops the envelopes and the dots`() {
        val detailed = frame(fiveMinutes, ChartDetailMode.DETAILED)

        assertTrue(detailed.spec.rawSamples.isEmpty())
    }

    /** Ступень лестницы не должна терять картинку ни в одном виде. */
    @Test
    fun `every ladder step draws something in both views`() {
        for ((_, span) in ChartWindows.PERIODS) {
            if (span > 6L * 3_600_000L) continue // длинные окна идут путём эскизов
            val window = ChartWindow(now - span, now)
            for (mode in ChartDetailMode.entries) {
                assertTrue(
                    frame(window, mode).spec.buckets.isNotEmpty(),
                    "окно $span мс, вид $mode: колонок нет",
                )
            }
        }
    }

    @Test
    fun `the stored id round-trips and the default is the detailed view`() {
        assertEquals(ChartDetailMode.DETAILED, ChartDetailMode.DEFAULT)
        for (mode in ChartDetailMode.entries) {
            assertEquals(mode, ChartDetailMode.of(mode.id))
        }
        assertEquals(ChartDetailMode.DEFAULT, ChartDetailMode.of(null))
        assertEquals(ChartDetailMode.DEFAULT, ChartDetailMode.of("что-то другое"))
    }
}
