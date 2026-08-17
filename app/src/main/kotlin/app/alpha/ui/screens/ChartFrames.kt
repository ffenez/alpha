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
 * Кадр подгоняется к ВИДИМОМУ окну: видимые колонки → их устойчивые границы →
 * поля → ось. Прежде он строился по всему загруженному диапазону, чтобы ось не
 * шевелилась под пальцем при перелистывании, — и ценой этого удобства
 * оказалась нечитаемая картинка: ушедший за левый край всплеск продолжал
 * держать верх оси, а фон 0,15 мкЗв/ч лежал плоской чертой на шкале до 2,00.
 * Ось, которая подстраивается по ходу жеста, — меньшая беда, чем график,
 * который ничего не показывает.
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
    /**
     * «Сейчас» для живого окна; null — окно историческое (экран Истории).
     *
     * Живая ось подписывается ОТНОСИТЕЛЬНО текущего момента, пока окно
     * короткое: одиночная стенная метка «23:42» посреди пятиминутного графика
     * не говорит ни о масштабе, ни о том, где правый край.
     */
    nowMillis: Long? = null,
    /** Каталог подписей оси — «сейчас · −4 мин». */
    axisStrings: ChartAxisStrings = ChartAxisRu,
    /**
     * Показывать ли единицу в углу поля.
     *
     * Везде — НЕТ. На карточке Главной величина названа заголовком, на
     * полноэкранном единица стоит рядом с живым значением в шапке; а в углу
     * поля она сталкивалась с указателем «↑ L1 0,30», и два текста
     * пересекались до нечитаемости. Параметр оставлен ради явности вызова: у
     * поля есть право на подпись, и отказ от неё — решение, а не умолчание.
     */
    showUnit: Boolean = false,
    /** Как из одних и тех же измерений собирается картинка. */
    detail: ChartDetailMode = ChartDetailMode.DEFAULT,
    /**
     * Показывать ли метки кратковременных отклонений над полем.
     *
     * На карточке Главной — НЕТ. Ряд «△3 △ △2 △5» шёл над кривой второй
     * строкой данных и выглядел важнее её самой, особенно у жёсткости, где
     * сама линия почти горизонтальна. События при этом не теряются: они
     * остаются в полноэкранном графике, где их включают отдельно, и в
     * карточке курсора, которая называет их временем и числом.
     */
    showEvents: Boolean = false,
    /**
     * Показывать ли указатель «↑ L1 0,30», когда порог ушёл за кадр.
     *
     * На карточке Главной — НЕТ. Красная отметка порога висела над графиком
     * постоянно, хотя при фоне 0,13 и пороге 0,30 она не говорит ничего о
     * нарисованном: до порога вдвое, и сам он в кадр не попадает. Карточка
     * отвечает на вопрос «что сейчас», а не «где-то там есть порог»; когда
     * порог действительно рядом — он внутри кадра, и тогда его видно линией.
     * Полноэкранный график указатель сохраняет: туда приходят разбираться.
     */
    showDistantAlarm: Boolean = true,
    /**
     * Готовый кадр оси — на время жеста.
     *
     * Пока палец двигает график, ось пересчитывалась каждый кадр: в окно
     * входили и выходили колонки, границы дёргались, и весь график «дышал»
     * под рукой. Во время жеста ось замирает, а новый масштаб считается,
     * когда движение остановилось (V2 §7).
     */
    /**
     * Кадр по значениям, заданный рукой; null — ось подбирается по данным.
     *
     * Автоподбор намеренно не растягивается до далёкого порога, иначе фон
     * 0,15 при пороге 0,30 лёг бы плоской чертой. Когда человек хочет увидеть,
     * ГДЕ проходят пороги, он задаёт кадр сам — и тогда решает он, а не
     * правило (`ChartYAxis`).
     */
    values: ValueWindow? = null,
    /**
     * Ширина поля графика в пикселях; 0 — ещё не измерена.
     *
     * От неё зависит число колонок: рисовать их больше, чем видно пикселей,
     * значит тратить кадр жеста на геометрию, которую невозможно разглядеть, а
     * меньше — показывать грубую картинку там, где данные подробнее
     * ([ChartDownsampler]).
     */
    plotWidthPx: Float = 0f,
    /**
     * Диапазон, для которого строится ГЕОМЕТРИЯ; null — только видимое окно с
     * воздухом справа (так рисуют неподвижные картинки: миниатюра, снимок).
     *
     * Полноэкранный график просит запас с обеих сторон, чтобы жест двигал
     * готовую картинку, а не открывал пустое поле по краям (`ChartGesture`).
     * Масштаб оси, статистика и подпись последней точки при этом считаются по
     * ВИДИМОМУ окну: запас — про плавность, а не про то, что показано.
     */
    renderWindow: ChartWindow? = null,
    /**
     * Считать ли распределение значений окна.
     *
     * Гистограмма нужна панели разбора на полноэкранном графике; карточка
     * Главной её не показывает НИКОГДА — а считалась она всё равно, проходом по
     * всем подсекундным агрегатам на каждый новый снимок. Кадр карточки
     * пересобирается раз в секунду, пока идёт поток, и эта работа выбрасывалась
     * целиком.
     */
    withHistogram: Boolean = true,
    /**
     * Считать ли статистику окна (квантили, MAD, SD).
     *
     * Это САМАЯ дорогая часть кадра: взвешенные перцентили сортируют тысячи
     * значений. На карточке блок чисел выключается в настройках, и тогда
     * считать их незачем.
     */
    withStats: Boolean = true,
): ChartFrame {
    // Колонка — это ИНТЕРВАЛ, и в окно она попадает пересечением, а не
    // серединой.
    //
    // Полевой дефект после переустановки: экран статистики видел 34 измерения
    // («n 34 · 6 ч»), а поле в тот же момент говорило «в этом окне нет
    // измерений». Статистика считается по подсекундным агрегатам, а рисуются
    // колонки; у длинного окна колонка широкая (у 12 ч — четыре с половиной
    // минуты), и единственная колонка со свежими данными имеет середину ПОЗЖЕ
    // «сейчас» — фильтр по середине выбрасывал её целиком. Чем длиннее окно,
    // тем вернее пропадала вся картинка.
    // Колонки пересобираются по ВИДИМОМУ окну и ширине поля.
    //
    // Ширину колонки задавал загруженный диапазон — окно плюс час запаса с
    // каждой стороны, который читается ради мгновенного перелистывания. На
    // пяти минутах это давало колонку в 37 секунд: триста измерений
    // превращались в восемь узлов, хотя лежали в том же снимке посекундно.
    // Запас чтения — решение о производительности, и менять из-за него
    // разрешение картинки он не имеет права. Второго запроса здесь нет:
    // складываются те же подсекундные агрегаты, что уже прочитаны.
    //
    // Длинные окна читаются слиянием почасовых скетчей (ADR 004), и колонка
    // там — целое число хранимых часов: пересобрать её тоньше нельзя, потому
    // что распределение известно только по часам целиком.
    val detailed = detail == ChartDetailMode.DETAILED
    val refoldable = snapshot.method != QuantileMethod.KLL_SKETCH
    // Геометрия строится ШИРЕ видимого окна, чтобы жест двигал готовую
    // картинку, а не открывал пустое поле по краям (`ChartGesture`). Разрешение
    // при этом считается по ВИДИМОМУ окну: запас под жест не имеет права
    // огрублять то, что на экране, — ровно как и запас чтения.
    val geometry = renderWindow ?: ChartWindows.withRightPadding(window)
    val columnMillis = if (refoldable) {
        ChartDownsampler.columnMillis(
            widthPx = plotWidthPx,
            spanMillis = window.spanMillis,
            subBucketMillis = snapshot.subBucketMillis,
            // Сглаженный вид — это медиана колонки с конвертами разброса, и
            // колонка обязана быть шире одного измерения, иначе конверт
            // схлопывается на линию и «сглаженность» становится словом.
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
    // Колонки для РИСОВАНИЯ — по всему нарисованному диапазону; колонки для
    // МАСШТАБА оси — только те, что видно. Иначе ушедший в запас всплеск
    // продолжал бы держать верх кадра, хотя на экране его нет.
    val rendered = columns.filter {
        it.endMillis > geometry.fromMillis && it.startMillis < geometry.toMillis
    }
    val visible = columns.filter {
        it.endMillis > window.fromMillis && it.startMillis < window.toMillis
    }
    // Порог L1 задан в единицах дозы: на счёте и на отношении его линии нет —
    // переносить туда дозовый порог было бы выдумкой.
    val alarm = thresholds.l1MicroSvH.takeIf { it > 0f && ChartMetrics.showsAlarmLevel(metric) }
    // Второй уровень из настроек рисуется рядом с первым: он там задан, а
    // увидеть, где он проходит, было нечем. Совпал с L1 — линия одна: две
    // подписи на одной высоте не сообщают ничего, кроме шума.
    val alarm2 = thresholds.l2MicroSvH
        .takeIf { it > 0f && it != thresholds.l1MicroSvH && ChartMetrics.showsAlarmLevel(metric) }
    // Полоса профиля задана в единицах ДОЗЫ — на счёте и на отношении её нет
    // по той же причине, что и порога L1.
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
    // Правый край кадра — «сейчас» плюс небольшой постоянный отступ.
    //
    // Свежая точка, приклеенная к самой кромке, читается как обрыв графика, а
    // не как его край; тот же приём — у биржевых графиков. Отступ ВРЕМЕННОЙ, а
    // не пиксельный: он не растягивает данные и не рисует в будущем ничего —
    // просто оставляет воздух справа. Пропуск в конце (поток встал) выглядит
    // так же честно: линия кончается на последнем измерении, дальше пусто.
    // Метки времени считаются по всему нарисованному диапазону, поэтому их
    // берётся больше — ровно во столько раз, во сколько он шире окна: иначе
    // после сдвига на экране оставалась бы одна подпись.
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
            // Метки считаются по ТОМУ ЖЕ окну, в котором рисуется поле
            // (`padded`): раньше доли брались от неподтянутого окна, и каждая
            // подпись стояла на пару процентов левее своего времени.
            // Живое окно подписывается ОТНОСИТЕЛЬНО «сейчас» при любой длине.
            // Стенные часы на живой карточке читались как данные из прошлого
            // сеанса: в 00:13 окно счёта в полчаса подписано «23:45 · 00:00»,
            // и понять, что это последние тридцать минут, невозможно —
            // особенно рядом с карточкой дозы, у которой окно минутное.
            // Заодно исчезает вопрос перехода через полночь.
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
            // Фон, который несёт данные: где прибор молчал, куда история не
            // доходит и где проходят сутки/часы (§2 ТЗ и правило «не
            // интерполировать пропуски»).
            gaps = ChartBackground.gaps(
                buckets = rendered,
                fromMillis = geometry.fromMillis,
                toMillis = geometry.toMillis,
                // Пропуск меряется НАРИСОВАННОЙ колонкой: она и есть шаг ряда
                // на экране, а ширина колонки в снимке относится к
                // прочитанному диапазону.
                bucketMillis = columnMillis,
            ),
            beforeHistory = ChartBackground.historyStart(
                earliestSampleMillis = snapshot.buckets.firstOrNull { it.sampleCount > 0 }
                    ?.startMillis,
                fromMillis = geometry.fromMillis,
                toMillis = geometry.toMillis,
            ),
            // На пустом окне фона нет вовсе: зебра — это опора для глаза
            // ВНУТРИ данных, а на чистом поле она читается как ошибка рендера
            // и спорит с самим сообщением «измерений нет».
            // Зебра — опора для глаза ВНУТРИ измеренного времени, и дальше него
            // не идёт: залитые часы там, где прибор не писал, выглядели как
            // полноценная часть истории, то есть маскировали её отсутствие.
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
            // Значение последней точки — то, ради чего график открывают чаще
            // всего; курсор ради него ставить не нужно.
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
     * Сколько читать про запас. У карточки Главной запас скромнее: там он
     * стоит шести тысяч строк на каждое новое измерение ради трёхсот
     * нарисованных (`ReadPadding`).
     */
    padding: ReadPadding = ReadPadding.Full,
): ChartSnapshot {
    val now = System.currentTimeMillis()
    val load = ChartDataSource.readRange(window, now, padding)
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
