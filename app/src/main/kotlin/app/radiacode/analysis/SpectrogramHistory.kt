package app.radiacode.analysis

/**
 * Хранимая история спектрограммы (ADR 007).
 *
 * **История спектрограммы — постоянный продукт измерения, а не кэш интерфейса.**
 * Каждый срез представляет реально измеренный интервал и НИКОГДА не
 * подразумевает временнóго разрешения мельче своей экспозиции.
 *
 * Здесь живёт только то, что нужно ХРАНИЛИЩУ: схема полос, арифметика
 * объединения срезов и параметры прореживания. Математика самой картинки
 * (интервальные срезы, адаптивная нарезка полос, яркость) не меняется и
 * остаётся в [Spectrogram].
 *
 * ## Почему это отдельная сущность от снимка спектра
 *
 * Снимок спектра (`spectra`) — 1024 канала с калибровкой: по нему ищут пики,
 * называют нуклиды, экспортируют RC-XML/N42 и переанализируют позже. Срез
 * спектрограммы — [SpectrogramBinning.BAND_COUNT] полос ОТОБРАЖЕНИЯ,
 * просуммированных по каналам необратимо: калибровка в него уже впечатана, а
 * ширина полосы (≈3,7 % энергии) в разы шире аппаратного разрешения. Искать по
 * срезам пики нельзя — полоса не линия, и «пик» в ней это край полосы. Ровно
 * поэтому таблицы две, а не одна с флагом.
 *
 * JVM-tested; no Android dependencies.
 */
object SpectrogramBinning {

    /**
     * Идентификатор схемы разбиения полос. Записи РАЗНЫХ схем не складываются:
     * границы полос — часть смысла счёта, и молча сложить их значило бы
     * приписать импульсы чужой энергии.
     */
    const val SCHEME_96_V1 = "SPECTROGRAM_96_V1"

    /** Схема, которой пишутся новые срезы. */
    const val CURRENT_SCHEME = SCHEME_96_V1

    /** Число полос схемы; null — схема неизвестна этой версии приложения. */
    fun bandCount(scheme: String): Int? =
        if (scheme == SCHEME_96_V1) Spectrogram.BAND_COUNT else null

    /**
     * Границы полос схемы, кэВ: [bandCount] + 1 значение, геометрическая шкала
     * [Spectrogram.MIN_KEV]…[Spectrogram.MAX_KEV]. Одно место истины: картинка
     * и хранилище обязаны резать энергию одинаково.
     */
    fun edgesKeV(scheme: String): FloatArray? {
        val bands = bandCount(scheme) ?: return null
        val span = kotlin.math.ln(Spectrogram.MAX_KEV / Spectrogram.MIN_KEV)
        return FloatArray(bands + 1) { i ->
            Spectrogram.MIN_KEV * kotlin.math.exp(i.toFloat() / bands * span)
        }
    }
}

/**
 * Один хранимый срез: реально измеренный интервал времени и СЧЁТ по полосам.
 *
 * Хранится счёт, а не скорость: из счёта и длительности скорость
 * восстанавливается всегда (R = N/Δt), а из округлённой скорости пуассоновскую
 * статистику — нет.
 *
 * [durationMillis] ≠ `endMillis − startMillis`: первое — экспозиция (насколько
 * выросло накопление прибора), второе — настенное время между опросами. Они
 * расходятся, когда прибор не измерял часть интервала; делить надо на
 * экспозицию, а рисовать — на своём месте оси времени.
 *
 * **Для поиска пиков этот срез непригоден** — см. [SpectrogramBinning].
 */
class HistorySlice(
    val startMillis: Long,
    val endMillis: Long,
    val durationMillis: Long,
    val schemeId: String,
    /** Счёт по полосам схемы [schemeId], не скорость. */
    val bandCounts: IntArray,
    /**
     * Показания 1 Гц на интервале: у записанного среза — мгновенное показание в
     * момент опроса, у объединённого — среднее, взвешенное по экспозиции. Это
     * ОЦЕНКА среднего по интервалу, а не измеренное среднее, и в этом качестве
     * годится только для полосы под картинкой.
     */
    val cps: Float?,
    val doseMicroSvH: Float?,
    /** Сколько записанных срезов слито в этот; 1 — как записано. */
    val sliceCount: Int = 1,
) {
    val totalCounts: Long = bandCounts.sumOf { it.toLong() }

    /** Импульсы в секунду по всем полосам; null — экспозиции нет. */
    fun ratePerSecond(): Float? =
        if (durationMillis > 0L) totalCounts.toFloat() / (durationMillis / 1000f) else null
}

/** Почему срезы НЕ объединяются. Отказ называется причиной, а не молчанием. */
enum class SliceMergeRefusal {
    /** Нечего объединять. */
    EMPTY,

    /** Разные схемы полос: складывать импульсы разных границ энергии нельзя. */
    SCHEME_MISMATCH,

    /**
     * Между срезами пропуск записи. 18:00–18:02 + пропуск + 18:07–18:10 не
     * становятся срезом 18:00–18:10: такой срез утверждал бы, что измерение шло
     * все десять минут.
     */
    GAP,
}

/**
 * Объединение и прореживание хранимых срезов.
 *
 * Порядок арифметики обязателен: сначала складываются СЧЁТЫ и ЭКСПОЗИЦИИ, и
 * только потом, при отображении, считается скорость. Усреднять готовые
 * имп/с по полосам нельзя — у срезов разные длительности, и среднее
 * арифметическое отношений не равно отношению сумм.
 */
object SpectrogramHistory {

    /**
     * Допуск стыка срезов. Записанные подряд срезы стыкуются точно (конец
     * одного = момент следующего опроса), поэтому допуск нужен только на
     * округление времени. **Инженерный параметр хранения.**
     */
    const val MAX_JOIN_GAP_MILLIS = 1_000L

    /** Почему список не объединяется; null — объединяется. */
    fun refusal(
        slices: List<HistorySlice>,
        maxGapMillis: Long = MAX_JOIN_GAP_MILLIS,
    ): SliceMergeRefusal? {
        if (slices.isEmpty()) return SliceMergeRefusal.EMPTY
        val scheme = slices.first().schemeId
        for (slice in slices) {
            if (slice.schemeId != scheme) return SliceMergeRefusal.SCHEME_MISMATCH
        }
        val ordered = slices.sortedBy { it.startMillis }
        for (i in 1 until ordered.size) {
            val gap = ordered[i].startMillis - ordered[i - 1].endMillis
            if (gap > maxGapMillis) return SliceMergeRefusal.GAP
        }
        return null
    }

    /**
     * Один срез из нескольких; null — объединение отвергнуто ([refusal]).
     * Счёты и экспозиции суммируются, границы берутся крайние, показания
     * 1 Гц усредняются с весом экспозиции.
     */
    fun merge(
        slices: List<HistorySlice>,
        maxGapMillis: Long = MAX_JOIN_GAP_MILLIS,
    ): HistorySlice? {
        if (refusal(slices, maxGapMillis) != null) return null
        val ordered = slices.sortedBy { it.startMillis }
        if (ordered.size == 1) return ordered.first()
        val bands = IntArray(ordered.first().bandCounts.size)
        var duration = 0L
        var cpsWeighted = 0.0
        var cpsWeight = 0L
        var doseWeighted = 0.0
        var doseWeight = 0L
        var merged = 0
        for (slice in ordered) {
            for (b in bands.indices) bands[b] += slice.bandCounts.getOrElse(b) { 0 }
            duration += slice.durationMillis
            merged += slice.sliceCount
            // Вес — экспозиция: десятиминутный срез не имеет права весить
            // столько же, сколько пятисекундный.
            val weight = slice.durationMillis.coerceAtLeast(1L)
            slice.cps?.let { cpsWeighted += it * weight; cpsWeight += weight }
            slice.doseMicroSvH?.let { doseWeighted += it * weight; doseWeight += weight }
        }
        return HistorySlice(
            startMillis = ordered.first().startMillis,
            endMillis = ordered.last().endMillis,
            durationMillis = duration,
            schemeId = ordered.first().schemeId,
            bandCounts = bands,
            cps = if (cpsWeight > 0L) (cpsWeighted / cpsWeight).toFloat() else null,
            doseMicroSvH = if (doseWeight > 0L) (doseWeighted / doseWeight).toFloat() else null,
            sliceCount = merged,
        )
    }

    /**
     * Прореживание: подряд идущие срезы сливаются в корзины длиной
     * [targetMillis], ВЫРОВНЕННЫЕ по эпохе. Выравнивание, а не «жадно от
     * первого», делает операцию идемпотентной: повторный проход по уже
     * прореженной истории не пересобирает её заново и не двигает границы.
     *
     * Срез никогда не режется и никогда не сливается через пропуск или через
     * смену схемы полос — такая пара просто остаётся двумя срезами.
     */
    fun compact(
        slices: List<HistorySlice>,
        targetMillis: Long,
        maxGapMillis: Long = MAX_JOIN_GAP_MILLIS,
    ): List<HistorySlice> {
        if (targetMillis <= 0L || slices.size <= 1) return slices
        val ordered = slices.sortedBy { it.startMillis }
        val result = ArrayList<HistorySlice>()
        var group = ArrayList<HistorySlice>()
        fun flush() {
            if (group.isEmpty()) return
            // Отказ объединить = срезы остаются как есть. Потерять их вместо
            // объединения нельзя ни при какой причине отказа.
            val merged = merge(group, maxGapMillis)
            if (merged != null) result += merged else result += group
            group = ArrayList()
        }
        for (slice in ordered) {
            val previous = group.lastOrNull()
            val sameBucket = previous != null &&
                previous.startMillis.floorDiv(targetMillis) ==
                slice.startMillis.floorDiv(targetMillis)
            val joinable = previous != null &&
                previous.schemeId == slice.schemeId &&
                slice.startMillis - previous.endMillis <= maxGapMillis
            if (previous != null && !(sameBucket && joinable)) flush()
            group.add(slice)
        }
        flush()
        return result
    }

    /**
     * Сколько времени история хранится КАК ЗАПИСАНА, прежде чем прореживается.
     * **Инженерный параметр хранения**: неделя — тот срок, на котором ещё
     * спрашивают «что было в тот вечер», и одновременно предел, за которым
     * пятисекундные срезы стоят дороже, чем отвечают.
     */
    const val AS_RECORDED_MILLIS = 7L * 24 * 3_600_000L

    /**
     * Длина среза после прореживания. **Инженерный параметр хранения**:
     * пять минут — колонка, на которой картинка суток остаётся читаемой
     * (288 колонок в сутки), а объём падает на порядок.
     */
    const val COMPACTED_SLICE_MILLIS = 5L * 60_000L

    /**
     * Оценка места одного среза, байт: 96 полос × 4 Б счёта + границы,
     * длительность, схема и показания 1 Гц со служебными байтами строки
     * SQLite ≈ 70 Б.
     */
    const val BYTES_PER_SLICE = 460L

    /** Оценка объёма истории при заданном шаге записи, МБ в сутки. */
    fun megabytesPerDay(intervalMillis: Long): Float {
        if (intervalMillis <= 0L) return 0f
        val slices = 86_400_000.0 / intervalMillis
        return (slices * BYTES_PER_SLICE / 1_000_000.0).toFloat()
    }
}
