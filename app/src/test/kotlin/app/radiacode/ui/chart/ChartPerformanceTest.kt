package app.radiacode.ui.chart

import app.radiacode.baseline.AlarmThresholds
import app.radiacode.data.DoseUnitSetting
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.ChartSeriesModel
import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.QuantilePaths
import app.radiacode.ui.logic.ValueAggregate
import app.radiacode.ui.screens.buildFrame
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Порог стоимости кадра (ТЗ §28, §29).
 *
 * ## Что здесь проверяется и чего здесь нет
 *
 * Плавность на телефоне меряют на телефоне — Macrobenchmark и Perfetto, с
 * записью p95 времени кадра; этого здесь нет и быть не может. Зато здесь
 * проверяется то, из-за чего плавность теряют: сколько геометрии кадр
 * СОБИРАЕТСЯ нарисовать и во что обходится его пересборка. Именно эти два
 * числа ломались раньше молча — картинка выглядела так же, а работа росла.
 *
 * Числа порогов заведомо щедрые: тест должен ловить потерю ПОРЯДКА (рисуем
 * миллион вершин; пересобираем кадр на каждом кадре жеста), а не колебания
 * машины, на которой он запущен.
 */
class ChartPerformanceTest {

    private val now = 1_700_000_000_000L
    private val phoneWidthPx = 1080f

    private fun aggregates(seconds: Int, from: Long): List<ValueAggregate> =
        (0 until seconds).map { i ->
            val value = 0.15f + (i % 13) * 0.001f
            ValueAggregate(
                startMillis = from + i * 1_000L,
                minMicroSvH = value,
                maxMicroSvH = value,
                sumMicroSvH = value.toDouble(),
                sumSqMicroSvH = value.toDouble() * value,
                sampleCount = 1,
            )
        }

    private fun thresholds() = AlarmThresholds(
        l1MicroSvH = 0.30f,
        l2MicroSvH = 1.0f,
        relativeFactor = 2f,
        persistenceSeconds = 120,
    )

    private fun frameOf(
        windowSpan: Long,
        gesture: ChartGesture,
        seconds: Int,
        withStats: Boolean = true,
        withHistogram: Boolean = true,
    ) = buildFrame(
        snapshot = ChartSeriesModel.snapshot(
            aggregates = aggregates(seconds, now - seconds * 1_000L),
            eventTimesMillis = emptyList(),
            alignedFromMillis = ChartMapping.alignedFrom(
                now,
                seconds * 1_000L,
                ChartSeriesModel.bucketMillis(seconds * 1_000L),
            ),
            toMillis = now,
            bucketMillis = ChartSeriesModel.bucketMillis(seconds * 1_000L),
            subBucketMillis = QuantilePaths.exactSubBucketMillis(),
        ),
        window = ChartWindow(now - windowSpan, now),
        unit = DoseUnitSetting.MICRO_SIEVERT,
        logScale = false,
        thresholds = thresholds(),
        baseline = null,
        endpointAlert = false,
        plotWidthPx = phoneWidthPx,
        renderWindow = gesture.rendered,
        withHistogram = withHistogram,
        withStats = withStats,
    )

    @Test
    fun `число колонок связано с шириной экрана, а не с числом измерений`() {
        // Шесть часов секундной записи — 21 600 измерений. Нарисовать их все
        // на тысяче пикселей невозможно и незачем.
        val windowSpan = 6L * 3_600_000L
        val gesture = ChartGesture.of(
            Viewports.atEdge(windowSpan, ViewportBounds(edgeMillis = now)),
            ViewportBounds(edgeMillis = now),
        )
        val frame = frameOf(windowSpan, gesture, seconds = 21_600)
        val renderedSpanFactor = gesture.rendered.spanMillis.toDouble() / windowSpan
        val budget = (phoneWidthPx / ChartDownsampler.PIXELS_PER_COLUMN * renderedSpanFactor).toInt()
        assertTrue(
            frame.spec.buckets.size <= budget + 4,
            "колонок ${frame.spec.buckets.size}, бюджет $budget",
        )
        assertTrue(frame.spec.buckets.isNotEmpty())
    }

    @Test
    fun `жест не пересобирает кадр`() {
        // Кадр строится для ОДНОГО окна; движение пальца меняет видимое окно и
        // преобразование, а ключи кадра при этом обязаны остаться прежними.
        val bounds = ViewportBounds(edgeMillis = now)
        val start = ChartGesture.of(Viewports.atEdge(5L * 60_000L, bounds), bounds)
        var moved = start
        repeat(60) { moved = moved.pan(-0.005f, bounds) }
        assertTrue(moved.moved)
        assertTrue(moved.covered(), "шестьдесят кадров жеста обязаны уложиться в запас")
        assertTrue(
            moved.frame == start.frame && moved.rendered == start.rendered,
            "окно кадра изменилось во время жеста — значит кадр пересобирался",
        )
    }

    @Test
    fun `пересборка кадра укладывается в бюджет`() {
        val windowSpan = 6L * 3_600_000L
        val bounds = ViewportBounds(edgeMillis = now)
        val gesture = ChartGesture.of(Viewports.atEdge(windowSpan, bounds), bounds)
        // Прогрев: первый проход платит за загрузку классов.
        repeat(3) { frameOf(windowSpan, gesture, seconds = 21_600) }
        val startedAt = System.nanoTime()
        repeat(REBUILDS) { frameOf(windowSpan, gesture, seconds = 21_600) }
        val perRebuildMillis = (System.nanoTime() - startedAt) / 1_000_000.0 / REBUILDS
        assertTrue(
            perRebuildMillis < REBUILD_BUDGET_MILLIS,
            "пересборка кадра $perRebuildMillis мс при бюджете $REBUILD_BUDGET_MILLIS мс",
        )
    }

    @Test
    fun `карточка не платит за то, чего не показывает`() {
        // Мини-график не рисует ни распределения, ни статистики окна, а
        // считались они всё равно — на каждый новый снимок, трижды по числу
        // карточек. Самое дорогое здесь — сортировка тысяч значений в
        // перцентилях.
        val windowSpan = 6L * 3_600_000L
        val bounds = ViewportBounds(edgeMillis = now)
        val gesture = ChartGesture.of(Viewports.atEdge(windowSpan, bounds), bounds)
        val card = frameOf(
            windowSpan,
            gesture,
            seconds = 21_600,
            withStats = false,
            withHistogram = false,
        )
        assertTrue(card.stats == null)
        assertTrue(card.histogram == null)
        assertTrue(card.histogramLabels.isEmpty())
        // Картинка при этом та же: колонки, конверты и фон не зависят от того,
        // показаны ли числа под графиком.
        val full = frameOf(windowSpan, gesture, seconds = 21_600)
        assertTrue(card.spec.buckets.size == full.spec.buckets.size)
    }

    private companion object {
        const val REBUILDS = 10

        /**
         * Сколько может стоить ОДНА пересборка кадра шестичасового окна.
         *
         * **Инженерный параметр**: 150 мс на машине сборки. Кадр пересобирается
         * раз в жест (после паузы 120 мс), а не шестьдесят раз в секунду,
         * поэтому порог ловит потерю порядка — возврат к пересборке на каждом
         * кадре жеста или квадратичный проход по агрегатам, — а не разницу
         * между быстрым и очень быстрым.
         */
        const val REBUILD_BUDGET_MILLIS = 150.0
    }
}
