package app.alpha.ui.logic

import app.alpha.baseline.AlarmThresholds
import app.alpha.data.DoseUnitSetting
import app.alpha.ui.screens.buildFrame
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Масштаб оси задаёт ВИДИМОЕ окно.
 *
 * Полевой дефект: при фоне 0,15 мкЗв/ч ось стояла до 2,00, а жёсткость при
 * 0,60 — до 5,00, и обе кривые лежали горизонтальной чертой. Причина не в
 * шкале, а в том, ЧТО ей отдавали: в снимок читается запас с обеих сторон окна
 * ради мгновенного перелистывания, и вчерашний всплеск 2,2 мкЗв/ч продолжал
 * держать верх кадра, хотя из окна давно ушёл.
 *
 * Здесь это воспроизводится буквально: час ровного фона, а за час до него —
 * всплеск, которого в окне нет.
 */
class ChartViewportScaleTest {

    private val now = 1_700_000_000_000L

    private fun aggregate(at: Long, value: Float) = ValueAggregate(
        startMillis = at,
        minMicroSvH = value,
        maxMicroSvH = value,
        sumMicroSvH = value.toDouble(),
        sumSqMicroSvH = value.toDouble() * value,
        sampleCount = 1,
    )

    /**
     * Снимок читается ТАК ЖЕ, как его читает экран: по загруженному диапазону
     * (окно плюс запас), а не по видимому окну.
     */
    private fun frameOf(
        window: ChartWindow,
        spikeValue: Float?,
        alarmLevel: Float = 0f,
    ): app.alpha.ui.screens.ChartFrame {
        val load = ChartWindows.loadRange(window, now)
        val bucketMillis = ChartSeriesModel.bucketMillis(load.spanMillis)
        val alignedFrom = ChartMapping.alignedFrom(load.toMillis, load.spanMillis, bucketMillis)
        val aggregates = buildList {
            var at = alignedFrom
            while (at < load.toMillis) {
                // Ровный фон 0,15 во всём загруженном диапазоне…
                add(aggregate(at, 0.15f))
                at += 1_000L
            }
            // …и всплеск ЗА левым краем видимого окна.
            if (spikeValue != null) {
                val spikeAt = window.fromMillis - 10L * 60_000L
                if (spikeAt >= alignedFrom) {
                    for (i in 0 until 120) add(aggregate(spikeAt + i * 1_000L, spikeValue))
                }
            }
        }.sortedBy { it.startMillis }

        return buildFrame(
            snapshot = ChartSeriesModel.snapshot(
                aggregates = aggregates,
                eventTimesMillis = emptyList(),
                alignedFromMillis = alignedFrom,
                toMillis = load.toMillis,
                bucketMillis = bucketMillis,
                subBucketMillis = QuantilePaths.exactSubBucketMillis(),
            ),
            window = window,
            unit = DoseUnitSetting.MICRO_SIEVERT,
            logScale = false,
            thresholds = AlarmThresholds(
                l1MicroSvH = alarmLevel,
                l2MicroSvH = 0f,
                relativeFactor = 2f,
                persistenceSeconds = 120,
            ),
            baseline = null,
            endpointAlert = false,
        )
    }

    private val window = ChartWindow(now - 30L * 60_000L, now)

    @Test
    fun `a spike outside the window no longer holds the axis`() {
        val withSpike = frameOf(window, spikeValue = 2.2f)
        val clean = frameOf(window, spikeValue = null)

        // Кадр вокруг фона 0,15, а не до 2,2.
        assertTrue(
            withSpike.spec.scale.maxValue < 0.5f,
            "верх оси ${withSpike.spec.scale.maxValue} — всплеск за окном всё ещё держит масштаб",
        )
        assertTrue(
            withSpike.spec.scale.maxValue == clean.spec.scale.maxValue,
            "кадр обязан совпадать с тем, где всплеска не было вовсе",
        )
    }

    /**
     * Практически постоянный фон не растягивается на весь экран: без
     * минимального размаха шум ±0,002 читался бы как размашистые скачки.
     */
    @Test
    fun `a flat background keeps a minimum span`() {
        val frame = frameOf(window, spikeValue = null)
        val span = frame.spec.scale.maxValue - frame.spec.scale.minValue
        assertTrue(
            span >= ChartMetrics.minAxisSpan(ChartMetric.DOSE) * 0.99f,
            "размах $span меньше минимального",
        )
        assertTrue(span < 1f, "размах $span — кадр незачем растягивать")
    }

    /**
     * Далёкий порог тревоги не растягивает ось: при фоне 0,15 кадр до 0,30
     * вдвое ухудшал бы вертикальное разрешение. Сам порог не теряется — его
     * показывает указатель у верхней кромки поля.
     */
    @Test
    fun `a distant alarm level does not stretch the axis`() {
        val frame = frameOf(window, spikeValue = null, alarmLevel = 0.30f)
        assertTrue(
            frame.spec.scale.maxValue < 0.30f,
            "верх оси ${frame.spec.scale.maxValue} подтянут к далёкому порогу",
        )
    }
}
