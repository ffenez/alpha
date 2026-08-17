package app.alpha.ui.screens

import app.alpha.AppGraph
import app.alpha.analysis.Hardness
import app.alpha.analysis.quantiles.KllSketch
import app.alpha.baseline.AlarmThresholds
import app.alpha.baseline.Baseline
import app.alpha.data.DoseUnitSetting
import app.alpha.data.db.MinuteRollup
import app.alpha.device.DoseUnits
import app.alpha.ui.chart.ChartDataSource
import app.alpha.ui.chart.ChartDownsampler
import app.alpha.ui.chart.ReadPadding
import app.alpha.ui.chart.ChartYAxis
import app.alpha.ui.chart.ValueWindow
import app.alpha.ui.components.DoseChartSpec
import app.alpha.ui.logic.ChartBackground
import app.alpha.ui.logic.ChartDetailMode
import app.alpha.ui.logic.ChartMapping
import app.alpha.ui.logic.ChartMetric
import app.alpha.ui.logic.ChartMetrics
import app.alpha.ui.logic.ChartSeriesModel
import app.alpha.ui.logic.ChartSnapshot
import app.alpha.ui.logic.ChartWindow
import app.alpha.ui.logic.ChartWindows
import app.alpha.ui.logic.DoseEpisodes
import app.alpha.ui.logic.DoseExtremes
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.DoseHistogram
import app.alpha.ui.logic.DoseHistograms
import app.alpha.ui.logic.DoseScales
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.HourSlice
import app.alpha.ui.logic.QuantileMethod
import app.alpha.ui.logic.QuantilePaths
import app.alpha.ui.logic.TimeAxis
import app.alpha.ui.text.ChartAxisRu
import app.alpha.ui.text.ChartAxisStrings
import app.alpha.ui.logic.ValueAggregate
import app.alpha.ui.logic.WindowRollup
import app.alpha.ui.logic.WindowStats
import app.alpha.ui.logic.referenceWording
import app.alpha.ui.logic.referenceWordingShort

/**
 * Один кадр графика для всех экранов: единственный путь «окно → снимок →
 * кадр». Карточка Главной и полноэкранный график берут одни колонки, одни
 * конверты, один фон и одни правила отрисовки пропусков; различаются размером
 * поля и набором управления.
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
 * Кадр подгоняется к ВИДИМОМУ окну: видимые колонки → их устойчивые границы →
 * поля → ось. Построение по всему загруженному диапазону оставляло верх оси за
 * всплеском, ушедшим за левый край.
 */
/**
 * Длительность эпизода словами. Эпизод короче секунды существует (колонка
 * минутного окна — одна секунда), но «0 с» — ошибка округления, а не
 * длительность.
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
    /**
     * «Сейчас» для живого окна; null — окно историческое (экран Истории).
     * Живая ось подписывается относительно текущего момента, пока окно
     * короткое.
     */
    nowMillis: Long? = null,
    /** Каталог подписей оси — «сейчас · −4 мин». */
    axisStrings: ChartAxisStrings = ChartAxisRu,
    /**
     * Показывать ли единицу в углу поля. Везде выключено: на карточке величина
     * названа заголовком, на полноэкранном единица стоит в шапке, а в углу поля
     * она сталкивается с указателем «↑ L1 0,30».
     */
    showUnit: Boolean = false,
    /** Как из одних и тех же измерений собирается картинка. */
    detail: ChartDetailMode = ChartDetailMode.DEFAULT,
    /**
     * Показывать ли метки кратковременных отклонений над полем. На карточке
     * Главной выключено; события остаются в полноэкранном графике и в карточке
     * курсора.
     */
    showEvents: Boolean = false,
    /**
     * Показывать ли указатель «↑ L1 0,30», когда порог ушёл за кадр. На
     * карточке Главной выключено: порог вне кадра ничего не говорит о
     * нарисованном. Полноэкранный график указатель сохраняет.
     */
    showDistantAlarm: Boolean = true,
    /**
     * Готовый кадр оси на время жеста: пока идёт движение, ось замирает, новый
     * масштаб считается после остановки (V2 §7).
     */
    /**
     * Кадр по значениям, заданный рукой; null — ось подбирается по данным.
     * Автоподбор не растягивается до далёкого порога (`ChartYAxis`).
     */
    values: ValueWindow? = null,
    /**
     * Ширина поля графика в пикселях; 0 — ещё не измерена. Задаёт число
     * колонок ([ChartDownsampler]).
     */
    plotWidthPx: Float = 0f,
    /**
     * Диапазон, для которого строится ГЕОМЕТРИЯ; null — только видимое окно с
     * воздухом справа. Полноэкранный график просит запас с обеих сторон
     * (`ChartGesture`); масштаб оси, статистика и подпись последней точки
     * считаются по видимому окну.
     */
    renderWindow: ChartWindow? = null,
    /**
     * Считать ли распределение значений окна. Нужно панели разбора на
     * полноэкранном графике; карточка Главной его не показывает.
     */
    withHistogram: Boolean = true,
    /**
     * Считать ли статистику окна (квантили, MAD, SD) — самая дорогая часть
     * кадра: взвешенные перцентили сортируют тысячи значений.
     */
    withStats: Boolean = true,
): ChartFrame {
    // Колонка — ИНТЕРВАЛ и попадает в окно пересечением, а не серединой: у
    // длинного окна колонка широкая (у 12 ч — четыре с половиной минуты), и
    // единственная колонка со свежими данными имеет середину позже «сейчас».
    //
    // Колонки пересобираются по ВИДИМОМУ окну и ширине поля: запас чтения
    // (окно плюс час с каждой стороны) — решение о производительности и
    // разрешение картинки менять не должен. Второго запроса нет — складываются
    // уже прочитанные подсекундные агрегаты.
    //
    // Длинные окна читаются слиянием почасовых скетчей (ADR 004), и колонка
    // там — целое число хранимых часов: распределение известно только по часам.
    val detailed = detail == ChartDetailMode.DETAILED
    val refoldable = snapshot.method != QuantileMethod.KLL_SKETCH
    // Геометрия шире видимого окна, чтобы жест двигал готовую картинку
    // (`ChartGesture`); разрешение считается по видимому окну.
    val geometry = renderWindow ?: ChartWindows.withRightPadding(window)
    val columnMillis = if (refoldable) {
        ChartDownsampler.columnMillis(
            widthPx = plotWidthPx,
            spanMillis = window.spanMillis,
            subBucketMillis = snapshot.subBucketMillis,
            // Сглаженный вид — медиана колонки с конвертами разброса, и
            // колонка обязана быть шире одного измерения.
            smoothed = !detailed,
        )
    } else {
        snapshot.bucketMillis
    }
    val columns = if (refoldable) {
        val alignedFrom = ChartMapping.alignedFrom(
            geometry.toMillis,
            geometry.spanMillis,
            columnMillis,
        )
        ChartSeriesModel.fold(
            aggregates = snapshot.aggregates,
            alignedFromMillis = alignedFrom,
            bucketMillis = columnMillis,
            bucketCount = ChartSeriesModel.bucketCount(
                geometry.spanMillis,
                columnMillis,
                maxColumns = ChartDownsampler.MAX_RENDERED_COLUMNS,
            ),
            subBucketMillis = snapshot.subBucketMillis,
        ).ifEmpty { snapshot.buckets }
    } else {
        snapshot.buckets
    }
    // Колонки для рисования — по всему нарисованному диапазону; для масштаба
    // оси — только видимые.
    val rendered = columns.filter {
        it.endMillis > geometry.fromMillis && it.startMillis < geometry.toMillis
    }
    val visible = columns.filter {
        it.endMillis > window.fromMillis && it.startMillis < window.toMillis
    }
    // Порог L1 задан в единицах дозы: на счёте и на отношении его линии нет.
    val alarm = thresholds.l1MicroSvH.takeIf { it > 0f && ChartMetrics.showsAlarmLevel(metric) }
    // Второй уровень рисуется рядом с первым; при совпадении линия одна.
    val alarm2 = thresholds.l2MicroSvH
        .takeIf { it > 0f && it != thresholds.l1MicroSvH && ChartMetrics.showsAlarmLevel(metric) }
    // Полоса профиля задана в единицах дозы: на счёте и на отношении её нет.
    val band = baseline
        ?.takeIf { ChartMetrics.showsProfileBand(metric) }
        ?.let { it.doseLowMicroSvH..it.doseHighMicroSvH }
    // Кадр подгоняется к наблюдаемым значениям по устойчивым границам колонок
    // (Q10/Q90): один всплеск не сжимает весь ряд, а далёкий порог не
    // растягивает ось (CHART SPEC §7 + `DoseScales`). Выброс не теряется — его
    // несут маркер над полем и карточка курсора.
    // Точки отдельных измерений — только у сглаженного вида: в подробном сама
    // линия идёт по измерениям, и точки дублировали бы её. И только пока
    // колонка ШИРЕ агрегата: когда они равны, точки легли бы на узлы линии.
    val dotsVisible = !detailed &&
        ChartDownsampler.rawDotsVisible(columnMillis, snapshot.subBucketMillis)
    // Кадр считается по ВИДИМЫМ колонкам, а не по загруженному снимку.
    //
    // Полевой дефект: при фоне 0,15 ось стояла до 2,00, а жёсткость при 0,60 —
    // до 5,00, и обе линии превращались в горизонтальную черту. Причина: в
    // снимок читается запас с обеих сторон окна ради мгновенного
    // перелистывания, и вчерашний всплеск 2,2 мкЗв/ч продолжал задавать
    // верх кадра, хотя из окна он уже ушёл. Запас чтения — решение о
    // производительности, и определять масштаб картинки он не имеет права:
    // видимое окно → значения в нём → поля → кадр.
    val scale = values?.let { ChartYAxis.scaleOf(it, logScale) } ?: DoseScales.of(
        logarithmic = logScale,
        lows = visible.map { it.q10 },
        highs = visible.map { it.q90 },
        minSpan = ChartMetrics.minAxisSpan(metric),
        alarmLevel = alarm,
        baselineBand = band,
    )
    val episodes = DoseEpisodes.around(
        buckets = rendered,
        eventTimesMillis = snapshot.eventTimesMillis.filter {
            it >= geometry.fromMillis && it <= geometry.toMillis
        },
        alarmMicroSvH = alarm,
        baselineP90MicroSvH = baseline?.doseHighMicroSvH,
    )
    val markers = if (showEvents) {
        DoseExtremes.markers(
            buckets = rendered,
            alarmMicroSvH = alarm,
            baselineP90MicroSvH = baseline?.doseHighMicroSvH,
        )
    } else {
        emptyList()
    }
    val histogram = if (withHistogram) {
        DoseHistograms.build(
            aggregates = snapshot.aggregates,
            fromMillis = window.fromMillis,
            toMillis = window.toMillis,
            baseline = band,
            alarmLevel = alarm,
        )
    } else {
        null
    }
    val rawDots = if (dotsVisible) snapshot.aggregates else emptyList()
    // Правый край кадра — «сейчас» плюс постоянный ВРЕМЕННОЙ отступ: он не
    // растягивает данные и ничего не рисует в будущем. Метки времени
    // считаются по всему нарисованному диапазону, поэтому их берётся больше во
    // столько раз, во сколько он шире окна.
    val geometryLabelCount = (
        xLabelCount * geometry.spanMillis / window.spanMillis.coerceAtLeast(1L)
        ).toInt().coerceIn(xLabelCount, xLabelCount * 4)
    return ChartFrame(
        spec = DoseChartSpec(
            buckets = rendered,
            detailed = detailed,
            fromMillis = geometry.fromMillis,
            toMillis = geometry.toMillis,
            scale = scale,
            baselineBand = band,
            baselineMedian = baseline?.doseMedianMicroSvH,
            alarmLevel = alarm,
            alarmLabel = alarm
                ?.takeIf { showDistantAlarm || it <= scale.maxValue }
                ?.let { "L1 ${DoseFormat.rate(it, unit)}" },
            alarmLevel2 = alarm2,
            alarmLabel2 = alarm2
                ?.takeIf { showDistantAlarm || it <= scale.maxValue }
                ?.let { "L2 ${DoseFormat.rate(it, unit)}" },
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
            // Метки считаются по тому же окну, в котором рисуется поле
            // (`padded`). Живое окно подписывается относительно «сейчас» при
            // любой длине: стенные часы на короткой ступени не говорят ни о
            // масштабе, ни о правом крае, и переход через полночь не читается.
            xLabels = if (nowMillis != null) {
                TimeAxis.relativeLabels(
                    fromMillis = geometry.fromMillis,
                    toMillis = geometry.toMillis,
                    nowMillis = nowMillis,
                    s = axisStrings,
                    count = geometryLabelCount,
                )
            } else {
                TimeAxis.autoLabels(
                    geometry.fromMillis,
                    geometry.toMillis,
                    count = geometryLabelCount,
                )
            },
            unitLabel = if (showUnit) ChartMetrics.unitLabel(metric, unit) else "",
            // Фон, несущий данные: где прибор молчал, куда история не доходит
            // и где проходят сутки/часы (§2 ТЗ).
            gaps = ChartBackground.gaps(
                buckets = rendered,
                fromMillis = geometry.fromMillis,
                toMillis = geometry.toMillis,
                // Пропуск меряется НАРИСОВАННОЙ колонкой: она и есть шаг ряда
                // на экране.
                bucketMillis = columnMillis,
            ),
            beforeHistory = ChartBackground.historyStart(
                earliestSampleMillis = snapshot.buckets.firstOrNull { it.sampleCount > 0 }
                    ?.startMillis,
                fromMillis = geometry.fromMillis,
                toMillis = geometry.toMillis,
            ),
            // Зебра — опора для глаза внутри измеренного времени: на пустом
            // окне её нет, за пределы данных она не идёт.
            timeBands = if (rendered.isEmpty()) {
                emptyList()
            } else {
                ChartBackground.bands(
                    fromMillis = maxOf(geometry.fromMillis, rendered.first().startMillis),
                    toMillis = minOf(geometry.toMillis, rendered.last().endMillis),
                )
            },
            rawSamples = rawDots,
            endpointAlert = endpointAlert,
            // Значение последней точки показывается без курсора.
            endpointLabel = visible.lastOrNull { it.sampleCount > 0 }
                ?.let { ChartMetrics.format(metric, it.median, unit) },
        ),
        // The long path computes the window statistics once per read (merging
        // sketches is far too expensive for a gesture frame); the exact path
        // recomputes them here from the sub-buckets.
        stats = if (withStats) {
            snapshot.windowStats ?: ChartSeriesModel.windowStats(
                snapshot.aggregates,
                window.fromMillis,
                window.toMillis,
            )
        } else {
            null
        },
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
    /**
     * Сколько читать про запас; у карточки Главной запас скромнее
     * (`ReadPadding`).
     */
    padding: ReadPadding = ReadPadding.Full,
): ChartSnapshot {
    val now = System.currentTimeMillis()
    val load = ChartDataSource.readRange(window, now, padding)
    // Предагрегация (ADR 004) посчитана для дозы; счёт и жёсткость читаются
    // точным путём, и длинные окна им не предлагаются.
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
    // Жёсткость определена в (мкрем/ч)/(имп/с); в базе доза лежит в сырых
    // единицах прибора — множитель тот же линейный, что у дозы.
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
        // События журнала относятся к дозе: на других величинах их маркеры
        // молчат.
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
