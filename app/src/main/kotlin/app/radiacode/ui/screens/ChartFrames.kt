package app.radiacode.ui.screens

import app.radiacode.AppGraph
import app.radiacode.analysis.Hardness
import app.radiacode.analysis.quantiles.KllSketch
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.Baseline
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.db.MinuteRollup
import app.radiacode.device.DoseUnits
import app.radiacode.ui.components.DoseChartSpec
import app.radiacode.ui.logic.ChartBackground
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.ChartMetric
import app.radiacode.ui.logic.ChartMetrics
import app.radiacode.ui.logic.ChartSeriesModel
import app.radiacode.ui.logic.ChartSnapshot
import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.ChartWindows
import app.radiacode.ui.logic.DoseEpisodes
import app.radiacode.ui.logic.DoseExtremes
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.DoseHistogram
import app.radiacode.ui.logic.DoseHistograms
import app.radiacode.ui.logic.DoseScales
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.HourSlice
import app.radiacode.ui.logic.QuantileMethod
import app.radiacode.ui.logic.QuantilePaths
import app.radiacode.ui.logic.TimeAxis
import app.radiacode.ui.logic.ValueAggregate
import app.radiacode.ui.logic.WindowRollup
import app.radiacode.ui.logic.WindowStats
import app.radiacode.ui.logic.referenceWording
import app.radiacode.ui.logic.referenceWordingShort

/**
 * Один кадр графика для всех экранов, которые его показывают.
 *
 * Главная и полноэкранный график раньше рисовали РАЗНЫЕ картинки одних и тех
 * же измерений: на карточке был усреднённый ряд по своим корзинам, на весь
 * экран — медиана с квантильными конвертами по модели ряда. Совпадать они не
 * могли в принципе, и тап по карточке подменял картинку вместо того, чтобы её
 * увеличить. Здесь живёт единственный путь «окно → снимок → кадр»: обе
 * стороны берут одни корзины, одни конверты, один фон и одни правила
 * честности, а различаются только размером поля и набором управления.
 */

// --- frame assembly -------------------------------------------------------

/** Everything one frame of the screen needs, derived from the snapshot. */
internal data class ChartFrame(
    val spec: DoseChartSpec,
    val stats: WindowStats?,
    val histogram: DoseHistogram?,
    val histogramLabels: List<Pair<Float, String>>,
    val logDropped: Int,
)

/**
 * Pure derivation of the visible frame from an immutable snapshot: no I/O, no
 * suspension, O(columns ≤ 200) plus one pass over the sub-buckets. This runs
 * on every gesture frame and must stay that cheap.
 *
 * The scale is fitted to the whole **loaded** range, not to the visible slice,
 * so panning does not make the axis jump under the finger; the debounced
 * reload refits it afterwards.
 */
/**
 * Длительность эпизода словами. Эпизод короче секунды существует (колонка на
 * минутном окне — одна секунда), но «0 с» — это не длительность, а ошибка
 * округления, и на графике она читается как «ничего не было».
 */
internal fun episodeDuration(durationMillis: Long): String =
    if (durationMillis < 1_000L) "<1 с" else HistoryFormat.duration(durationMillis / 1000)

internal fun buildFrame(
    snapshot: ChartSnapshot,
    window: ChartWindow,
    unit: DoseUnitSetting,
    logScale: Boolean,
    thresholds: AlarmThresholds,
    baseline: Baseline?,
    endpointAlert: Boolean,
    metric: ChartMetric = ChartMetric.DOSE,
    /** Подписей времени на оси: у миниатюры на карточке их меньше. */
    xLabelCount: Int = 4,
): ChartFrame {
    val visible = snapshot.buckets.filter {
        it.midMillis >= window.fromMillis && it.midMillis <= window.toMillis
    }
    // Порог L1 задан в единицах дозы: на счёте и на отношении его линии нет —
    // переносить туда дозовый порог было бы выдумкой.
    val alarm = thresholds.l1MicroSvH.takeIf { it > 0f && ChartMetrics.showsAlarmLevel(metric) }
    val band = baseline?.let { it.doseLowMicroSvH..it.doseHighMicroSvH }
    // The frame is fitted to what is actually drawn. With raw dots on screen
    // that is the true extremes; without them the envelopes stop at Q10–Q90,
    // and a single off-scale spike is carried by its marker and the cursor
    // card instead of stretching the whole axis (CHART SPEC §7 — an extremum
    // grows with N, so it must not define the frame).
    val dotsVisible = ChartSeriesModel.rawDotsVisible(snapshot.bucketMillis)
    val scale = DoseScales.of(
        logarithmic = logScale,
        dataMin = snapshot.buckets.minOfOrNull { if (dotsVisible) it.min else it.q10 },
        dataMax = snapshot.buckets.maxOfOrNull { if (dotsVisible) it.max else it.q90 },
        alarmLevel = alarm,
        baselineHigh = baseline?.doseHighMicroSvH,
    )
    val episodes = DoseEpisodes.around(
        buckets = visible,
        eventTimesMillis = snapshot.eventTimesMillis.filter {
            it >= window.fromMillis && it <= window.toMillis
        },
        alarmMicroSvH = alarm,
        baselineP90MicroSvH = baseline?.doseHighMicroSvH,
    )
    val markers = DoseExtremes.markers(
        buckets = visible,
        alarmMicroSvH = alarm,
        baselineP90MicroSvH = baseline?.doseHighMicroSvH,
    )
    val histogram = DoseHistograms.build(
        aggregates = snapshot.aggregates,
        fromMillis = window.fromMillis,
        toMillis = window.toMillis,
        baseline = band,
        alarmLevel = alarm,
    )
    val rawDots = if (dotsVisible) snapshot.aggregates else emptyList()
    return ChartFrame(
        spec = DoseChartSpec(
            buckets = visible,
            fromMillis = window.fromMillis,
            toMillis = window.toMillis,
            scale = scale,
            baselineBand = band,
            baselineMedian = baseline?.doseMedianMicroSvH,
            alarmLevel = alarm,
            alarmLabel = alarm?.let { "L1 ${DoseFormat.rate(it, unit)}" },
            episodes = episodes,
            // §20: a band must name the reference it is above, not only its
            // duration — «выше порога L1» and «выше исторического P90
            // профиля» are different events.
            episodeLabels = episodes.map {
                "${referenceWording(it.reference)} · " + episodeDuration(it.durationMillis)
            },
            episodeShortLabels = episodes.map {
                "${referenceWordingShort(it.reference)} · " + episodeDuration(it.durationMillis)
            },
            extremeMarkers = markers,
            yLabels = scale.ticks().map { it to ChartMetrics.format(metric, it, unit) },
            xLabels = TimeAxis.autoLabels(window.fromMillis, window.toMillis, count = xLabelCount),
            unitLabel = ChartMetrics.unitLabel(metric, unit),
            // Фон, который несёт данные: где прибор молчал, куда история не
            // доходит и где проходят сутки/часы (§2 ТЗ и правило «не
            // интерполировать пропуски»).
            gaps = ChartBackground.gaps(
                buckets = visible,
                fromMillis = window.fromMillis,
                toMillis = window.toMillis,
                bucketMillis = snapshot.bucketMillis,
            ),
            beforeHistory = ChartBackground.historyStart(
                earliestSampleMillis = snapshot.buckets.firstOrNull { it.sampleCount > 0 }
                    ?.startMillis,
                fromMillis = window.fromMillis,
                toMillis = window.toMillis,
            ),
            timeBands = ChartBackground.bands(window.fromMillis, window.toMillis),
            rawSamples = rawDots,
            endpointAlert = endpointAlert,
        ),
        // The long path computes the window statistics once per read (merging
        // sketches is far too expensive for a gesture frame); the exact path
        // recomputes them here from the sub-buckets.
        stats = snapshot.windowStats ?: ChartSeriesModel.windowStats(
            snapshot.aggregates,
            window.fromMillis,
            window.toMillis,
        ),
        histogram = histogram,
        histogramLabels = histogram
            ?.let { h ->
                DoseHistograms.labelValues(h).map { (fraction, value) ->
                    fraction to ChartMetrics.format(metric, value, unit)
                }
            }
            .orEmpty(),
        logDropped = if (logScale) DoseScales.logDroppedBuckets(visible) else 0,
    )
}

/**
 * The single database read of a window change (ADR 004). Runs on the IO
 * dispatcher and picks one of the two paths by the span:
 *
 *  - **≤ 6 h — exact.** SQL is asked for 1-second buckets, i.e. one row per
 *    raw sample (≤ 21 600), and every column carries the true order
 *    statistics of the measurements (CHART SPEC §29).
 *  - **longer — merged hourly sketches.** One row per stored hour (720 for 30
 *    days) instead of millions of raw ones (§30, §34). Columns are whole
 *    hours, because a column may only be given the distribution of the hours
 *    it fully covers.
 *
 * When the long path finds no pre-aggregation at all (fresh install, backfill
 * still running) it degrades to the coarse sub-bucket estimate and says so
 * everywhere the numbers appear.
 */
internal suspend fun loadSnapshot(
    graph: AppGraph,
    window: ChartWindow,
    metric: ChartMetric,
): ChartSnapshot {
    val now = System.currentTimeMillis()
    val load = ChartWindows.loadRange(window, now)
    // Предагрегация (ADR 004) посчитана для дозы; счёт и жёсткость читаются
    // точным путём, и длиннее его окна им не предлагаются вовсе.
    if (metric != ChartMetric.DOSE) {
        return loadExactValue(graph, load, QuantilePaths.exactSubBucketMillis(), metric)
    }
    return when (QuantilePaths.methodFor(load.spanMillis)) {
        QuantileMethod.EXACT_RAW -> loadExact(graph, load, QuantilePaths.exactSubBucketMillis())
        else -> loadSketched(graph, load, window)
    }
}

/**
 * Точный путь для скорости счёта и жёсткости: тот же SQL-агрегат по корзинам,
 * та же модель ряда, те же конверты и экстремумы — отличается только колонка,
 * из которой берутся числа.
 */
private suspend fun loadExactValue(
    graph: AppGraph,
    load: ChartWindow,
    subMillis: Long,
    metric: ChartMetric,
): ChartSnapshot {
    val bucketMillis = ChartSeriesModel.bucketMillis(load.spanMillis)
    val alignedFrom = ChartMapping.alignedFrom(load.toMillis, load.spanMillis, bucketMillis)
    val rows = when (metric) {
        ChartMetric.COUNT_RATE ->
            graph.measurementRepository.countRateBuckets(alignedFrom, load.toMillis, subMillis)
        else -> graph.measurementRepository.hardnessBuckets(
            from = alignedFrom,
            to = load.toMillis,
            bucketMillis = subMillis,
            minCountRate = Hardness.MIN_COUNT_RATE.toFloat(),
        )
    }
    // Жёсткость определена в (мкрем/ч)/(имп/с), а в базе доза лежит в сырых
    // единицах прибора — множитель тот же линейный, что и у самой дозы.
    val scale = when (metric) {
        ChartMetric.HARDNESS ->
            DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR.toDouble() * Hardness.MICRO_REM_PER_MICRO_SV
        else -> 1.0
    }
    val aggregates = rows.map { row ->
        ValueAggregate(
            startMillis = row.bucketStart,
            minMicroSvH = (row.minValue * scale).toFloat(),
            maxMicroSvH = (row.maxValue * scale).toFloat(),
            sumMicroSvH = row.sumValue * scale,
            sumSqMicroSvH = row.sumSqValue * scale * scale,
            sampleCount = row.sampleCount,
        )
    }
    return ChartSeriesModel.snapshot(
        aggregates = aggregates,
        // События журнала — про дозу; на других величинах их маркеры молчат,
        // чтобы не приписывать эпизод ряду, из которого он не выводился.
        eventTimesMillis = emptyList(),
        alignedFromMillis = alignedFrom,
        toMillis = load.toMillis,
        bucketMillis = bucketMillis,
        subBucketMillis = subMillis,
    )
}

/** Exact path: raw samples, aggregated by SQL at [subMillis] granularity. */
private suspend fun loadExact(
    graph: AppGraph,
    load: ChartWindow,
    subMillis: Long,
    bucketMillis: Long = ChartSeriesModel.bucketMillis(load.spanMillis),
): ChartSnapshot {
    val alignedFrom = ChartMapping.alignedFrom(load.toMillis, load.spanMillis, bucketMillis)
    val rows = graph.measurementRepository.doseBuckets(alignedFrom, load.toMillis, subMillis)
    val aggregates = rows.map { row ->
        ValueAggregate(
            startMillis = row.bucketStart,
            minMicroSvH = DoseUnits.rawToMicroSievertPerHour(row.minDoseRate),
            maxMicroSvH = DoseUnits.rawToMicroSievertPerHour(row.maxDoseRate),
            sumMicroSvH = row.sumDoseRate * DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR,
            sumSqMicroSvH = row.sumSqDoseRate *
                DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR.toDouble() *
                DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR,
            sampleCount = row.sampleCount,
        )
    }
    val events = graph.measurementRepository
        .deviationEvents(alignedFrom, load.toMillis)
        .map { it.timestamp }
    return ChartSeriesModel.snapshot(
        aggregates = aggregates,
        eventTimesMillis = events,
        alignedFromMillis = alignedFrom,
        toMillis = load.toMillis,
        bucketMillis = bucketMillis,
        subBucketMillis = subMillis,
    )
}

/** Long path: merged hourly KLL sketches, with the coarse estimate as fallback. */
private suspend fun loadSketched(
    graph: AppGraph,
    load: ChartWindow,
    window: ChartWindow,
): ChartSnapshot {
    val bucketMillis = QuantilePaths.bucketMillis(load.spanMillis, QuantileMethod.KLL_SKETCH)
    val alignedFrom = ChartMapping.alignedFrom(load.toMillis, load.spanMillis, bucketMillis)
    val hours = graph.preAggregateRepository.hourSketchesWithLiveTail(alignedFrom, load.toMillis)
    val factor = DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR
    val slices = hours.mapNotNull { row ->
        val sketch = KllSketch.fromByteArray(row.sketch) ?: return@mapNotNull null
        HourSlice(
            startMillis = row.hourStart,
            sampleCount = row.count,
            min = DoseUnits.rawToMicroSievertPerHour(row.minDoseRate),
            max = DoseUnits.rawToMicroSievertPerHour(row.maxDoseRate),
            minAtMillis = row.minAtMillis,
            maxAtMillis = row.maxAtMillis,
            sketch = sketch.scaled(factor),
        )
    }
    if (slices.isEmpty()) {
        // Nothing pre-aggregated for this range yet: fall back to the coarse
        // sub-bucket estimate, which the UI names as such (§32).
        return loadExact(graph, load, ChartSeriesModel.subBucketMillis(
            ChartSeriesModel.bucketMillis(load.spanMillis),
        ))
    }
    val events = graph.measurementRepository
        .deviationEvents(alignedFrom, load.toMillis)
        .map { it.timestamp }
    val rollup = graph.preAggregateRepository.rollup(window.fromMillis, window.toMillis)
    return ChartSeriesModel.snapshotFromSketches(
        slices = slices,
        eventTimesMillis = events,
        alignedFromMillis = alignedFrom,
        toMillis = load.toMillis,
        bucketMillis = bucketMillis,
        visibleFromMillis = window.fromMillis,
        visibleToMillis = window.toMillis,
        rollup = rollup.toWindowRollup(factor),
    )
}

/** Minute-scalar rollup → window moments in µSv/h; null when nothing is built. */
private fun MinuteRollup.toWindowRollup(factor: Float): WindowRollup? {
    val n = sampleCount ?: return null
    if (n <= 0) return null
    return WindowRollup(
        sampleCount = n,
        sumMicroSvH = (sumDoseRate ?: 0.0) * factor,
        sumSqMicroSvH = (sumSqDoseRate ?: 0.0) * factor.toDouble() * factor,
        min = DoseUnits.rawToMicroSievertPerHour(minDoseRate ?: 0f),
        max = DoseUnits.rawToMicroSievertPerHour(maxDoseRate ?: 0f),
        admittedCount = admittedCount ?: n,
    )
}
