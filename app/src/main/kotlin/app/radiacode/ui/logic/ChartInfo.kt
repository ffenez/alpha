package app.radiacode.ui.logic

import app.radiacode.analysis.quantiles.KllSketch
import app.radiacode.ui.text.ChartAxisRu
import app.radiacode.ui.text.ChartAxisStrings
import app.radiacode.ui.text.ChartTextRu
import app.radiacode.ui.text.ChartTextStrings

/**
 * Раздел справки о графике: заголовок, короткие абзацы и второй уровень.
 *
 * [lines] отвечают на вопрос «что я вижу и что это значит» — их читают все.
 * [details] раскрываются по «Подробнее» и называют статистику своими именами
 * (P50, P25–P75, метод квантилей, оговорки об экспозиции): математика не
 * исчезает, она лежит на шаг глубже.
 */
data class ChartInfoSection(
    val title: String,
    val lines: List<String>,
    val details: List<String> = emptyList(),
)

/**
 * Справка «как читать этот график» — то, что раньше лежало полосой мелкого
 * текста под полем.
 *
 * Текст объяснял график ПРАВИЛЬНО, но занимал место постоянно и читался ровно
 * один раз: дальше он превращался в серую полосу, которую взгляд пропускает,
 * а на маленьком экране ещё и отнимал высоту у самих данных. Объяснение нужно
 * по требованию — поэтому оно уехало под кнопку «i» в шапке и стало
 * структурированным: разделы вместо одной строки через «·».
 *
 * Содержимое зависит от того, что сейчас на экране: про полосу профиля не
 * пишется, когда её нет, про маркеры — когда их нет, а про метод квантилей
 * говорится тот, которым посчитано ЭТО окно.
 *
 * Справка построена ДВУМЯ УРОВНЯМИ (14.md): [ChartInfoSection.lines] отвечают
 * «что я вижу и что это значит», [ChartInfoSection.details] — «как это
 * называется и как посчитано». Математика не убрана, она на шаг глубже:
 * P50 и P25–P75 не исчезли из справки, они просто не первое, что человек
 * читает про линию на графике.
 */
object ChartInfo {

    fun sections(
        metric: ChartMetric,
        hasBaselineBand: Boolean,
        hasExtremeMarkers: Boolean,
        hasEpisodes: Boolean,
        method: QuantileMethod,
        logScale: Boolean,
        logDropped: Int,
        /** Каким видом нарисована картинка — подробным или сглаженным. */
        detail: ChartDetailMode = ChartDetailMode.DEFAULT,
        /** Открыт фиксированный диапазон из Истории, а не живой край. */
        historical: Boolean = false,
        s: ChartTextStrings = ChartTextRu,
        axis: ChartAxisStrings = ChartAxisRu,
    ): List<ChartInfoSection> {
        val sections = mutableListOf<ChartInfoSection>()

        // Порядок — от того, на что человек смотрит, к тому, чем это
        // нарисовано: линия → полосы → пропуски → устройство оси.
        // Заливки не остаются без определения: что именно значат линия и
        // полосы, зависит от вида, и вид называется первым.
        val anatomy = mutableListOf(
            if (detail == ChartDetailMode.DETAILED) s.detailNote else s.smoothedNote,
        )
        if (detail == ChartDetailMode.SMOOTHED) {
            anatomy += s.anatomyMedianLine
            anatomy += s.anatomyEnvelopes
        }
        if (metric == ChartMetric.HARDNESS) {
            anatomy += s.anatomyHardnessRatio
        }
        anatomy += s.anatomyGaps
        anatomy += s.anatomyAxis
        // Оговорки самой ВЕЛИЧИНЫ переехали сюда с карточки Главной: они
        // обязаны существовать там, где величину показывают, но постоянного
        // места под миниатюрой не заслуживают — их читают один раз.
        anatomy += ChartMetrics.footnotes(metric, axis)
        sections += ChartInfoSection(
            title = s.sectionAnatomy,
            lines = anatomy,
            details = listOf(s.anatomyMedianDetail, s.anatomyEnvelopesDetail),
        )

        val references = mutableListOf<String>()
        references += if (hasBaselineBand) s.referenceProfileBand else s.referenceProfileBandMissing
        references += if (ChartMetrics.showsAlarmLevel(metric)) {
            s.referenceAlarmLine
        } else {
            s.referenceAlarmAbsent
        }
        if (hasExtremeMarkers) references += s.referenceMarkers
        if (hasEpisodes) references += s.referenceEpisodes
        sections += ChartInfoSection(s.sectionReferences, references)

        val numbers = mutableListOf<String>()
        val numberDetails = mutableListOf<String>()
        val rankError = QuantileMetadata.errorPercentLabel(KllSketch.DEFAULT_K)
        // Первый уровень говорит, ОТКУДА взяты числа и насколько им можно
        // верить; как именно они посчитаны — под «Подробнее».
        when (method) {
            QuantileMethod.EXACT_RAW -> {
                numbers += s.quantilesExact
                numberDetails += s.quantilesExactDetail
            }
            QuantileMethod.KLL_SKETCH -> {
                numbers += s.quantilesSketch(rankError)
                numberDetails += s.quantilesSketchDetail(rankError)
            }
            QuantileMethod.SUB_BUCKET_MEANS -> {
                numbers += s.quantilesSubBucketMeans
                numberDetails += s.quantilesSubBucketMeansDetail
            }
        }
        numbers += s.sampleCountNote
        numberDetails += s.sampleCountDetail
        // Исторический кадр не догоняет «сейчас»: об этом говорится там же, где
        // объясняется, откуда взяты числа.
        if (historical) numbers += s.historicalRangeNote
        if (logScale) {
            numbers += if (logDropped > 0) s.logScaleDroppedNote(logDropped) else s.logScaleNote
        }
        ChartMetrics.spanLimitNote(metric, axis)?.let { numbers += it }
        sections += ChartInfoSection(s.sectionNumbers, numbers, numberDetails)

        val gestures = mutableListOf(s.gestureZoomPan, s.gestureCursor)
        if (hasExtremeMarkers) gestures += s.gestureMarkerTap
        gestures += if (historical) s.gestureDoubleTapRange else s.gestureDoubleTap
        sections += ChartInfoSection(s.sectionGestures, gestures)
        return sections
    }
}
