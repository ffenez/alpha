package app.alpha.analysis

import kotlin.math.ln
import kotlin.math.log10

/**
 * Pure math for the спектрограмма waterfall (SPEC «Spectrogram», Advanced):
 * Energy × Time × Intensity.
 *
 * ## Что рисуется
 *
 * Прибор опрашивается раз в 5 с, и один опрос даёт интервальный спектр
 * (накопление минус предыдущее). Но **колонка картинки ≠ один опрос**:
 * колонки строятся по СЕТКЕ ВРЕМЕНИ ([grid]) с шагом [displayStepSeconds], а
 * опросы в них суммируются. Причина статистическая: при фоне ≈25 имп/с
 * пятисекундный опрос это ≈125 импульсов на 96 полос, то есть ≈1 импульс на
 * полосу — картинка из такой колонки показывает пуассоновский шум, а глаз
 * читает его как «десятки устойчивых линий». Суммирование — это сложение
 * импульсов, ничего не выдумывающее; сырые 5-секундные срезы сохраняются в
 * кольце и остаются доступны на коротком окне.
 *
 * Сетка привязана к ВРЕМЕНИ, а не к порядку опросов: пропуск в потоке
 * остаётся пустой колонкой, а не схлопывается, как раньше, когда ось строилась
 * по индексу столбца и молча сжимала паузы.
 *
 * ## Яркость
 *
 * Основной режим — **общая шкала окна**: цвет = импульсы в секунду на полосу
 * в лог-шкале, верх шкалы один на всю картинку ([scaleTop]). Так слабый
 * спектр выглядит слабым, а сильный — сильным, и рост интенсивности виден.
 * Прежняя нормировка ВНУТРИ КОЛОНКИ ([shapeIntensity]) осталась отдельным
 * режимом «форма»: она удобна, когда сравнивают состав при разной
 * интенсивности, но по умолчанию она врёт про абсолютную яркость — один
 * импульс в пустой колонке светился как фотопик.
 *
 * JVM-tested; no Android dependencies.
 */
object Spectrogram {

    /** Waterfall rows. 96 keeps CsI(Tl) FWHM ≳ one band across the range. */
    const val BAND_COUNT = 96

    /**
     * Границы полос — ОБЩИЕ для всей серии, а не свойство подключённого
     * прибора: у 103G порог 25 кэВ, у Zero 30 кэВ, и если бы сетка полос
     * менялась вместе с прибором, срезы, снятые разными приборами, стало бы
     * нельзя класть на одну картинку. Прибор с более высоким порогом просто
     * оставляет нижние полосы пустыми — это честное «здесь нет отсчётов», а
     * не искажение.
     */
    const val MIN_KEV = 20f
    const val MAX_KEV = 3000f

    private val LOG_SPAN = ln(MAX_KEV / MIN_KEV)

    /**
     * Vertical position of an energy on the waterfall, 0 (=[MIN_KEV]) .. 1
     * (=[MAX_KEV]); null outside the plotted range. Geometric scale: equal
     * fractions are equal energy *ratios*, so the low-energy region where
     * scintillator spectra live gets its fair share of rows.
     */
    fun fractionOfEnergy(keV: Float): Float? {
        if (keV < MIN_KEV || keV > MAX_KEV) return null
        return (ln(keV / MIN_KEV) / LOG_SPAN)
    }

    /** Band row (0-based from [MIN_KEV]) for an energy; null out of range. */
    fun bandOfEnergy(keV: Float): Int? {
        val fraction = fractionOfEnergy(keV) ?: return null
        return (fraction * BAND_COUNT).toInt().coerceAtMost(BAND_COUNT - 1)
    }

    /** Geometric center energy of a band, keV. */
    fun bandCenterKeV(band: Int): Float {
        val t = (band + 0.5f) / BAND_COUNT
        return MIN_KEV * kotlin.math.exp(t * LOG_SPAN)
    }

    /**
     * Sums per-channel interval counts into the [BAND_COUNT] energy bands
     * using the spectrum's own calibration. Channels outside 20–3000 keV are
     * dropped (threshold noise below, empty overflow above).
     */
    fun bandCounts(counts: IntArray, calibration: EnergyCalibration): FloatArray {
        val bands = FloatArray(BAND_COUNT)
        // Крайний канал не относится ни к одной полосе: он граница шкалы, а
        // не энергия ([SpectrumEdge]).
        for (channel in SpectrumEdge.analysable(counts.size)) {
            val n = counts[channel]
            if (n <= 0) continue
            val band = bandOfEnergy(calibration.energyAt(channel.toFloat())) ?: continue
            bands[band] += n
        }
        return bands
    }

    /**
     * Interval spectrum: [current] accumulated-since-reset minus [previous].
     * Null when there is no valid interval — first poll, channel-grid change,
     * or a reset between polls (accumulation time did not grow). Small
     * negative per-channel diffs (device-side rebinning jitter) clamp to 0.
     */
    fun intervalCounts(
        currentCounts: List<Int>,
        currentSeconds: Long,
        previousCounts: List<Int>?,
        previousSeconds: Long,
    ): IntArray? {
        if (previousCounts == null) return null
        if (currentCounts.size != previousCounts.size) return null
        if (currentSeconds <= previousSeconds) return null
        return IntArray(currentCounts.size) { i ->
            (currentCounts[i] - previousCounts[i]).coerceAtLeast(0)
        }
    }

    /**
     * Яркость ячейки в ОСНОВНОМ режиме, 0..1: log10(1+r)/log10(1+top), где r —
     * импульсы в секунду на полосу, а [top] — верх общей шкалы окна.
     *
     * Скорость, а не счёт: колонки сетки могут покрывать разное измеренное
     * время (пропуск потока, край окна), и сравнивать их по сырым суммам
     * значило бы красить дырку в потоке как спад интенсивности.
     */
    fun intensity(ratePerSecond: Float, top: Float): Float {
        if (ratePerSecond <= 0f || top <= 0f) return 0f
        return (log10(1f + ratePerSecond) / log10(1f + top)).coerceIn(0f, 1f)
    }

    /**
     * Яркость в режиме «форма»: нормировка внутри колонки. Показывает СОСТАВ
     * спектра независимо от интенсивности — и поэтому не годится как основной
     * режим: колонка с одним импульсом выглядит так же, как колонка с пиком.
     */
    fun shapeIntensity(value: Float, columnMax: Float): Float {
        if (value <= 0f || columnMax <= 0f) return 0f
        return (log10(1f + value) / log10(1f + columnMax)).coerceIn(0f, 1f)
    }

    /**
     * Верх общей цветовой шкалы, имп/с на полосу: 98-й процентиль ненулевых
     * ячеек. Не максимум — одна яркая ячейка (например, край сильного
     * источника) прижала бы весь остальной фон к нулю; и не среднее — оно
     * пересветило бы половину картинки.
     */
    fun scaleTop(columns: List<SpectrogramColumn?>, groups: List<IntRange>): Float {
        val rates = ArrayList<Float>()
        for (column in columns) {
            if (column == null) continue
            for (group in groups) {
                val r = column.groupRate(group)
                if (r > 0f) rates.add(r)
            }
        }
        if (rates.isEmpty()) return 0f
        rates.sort()
        // Округление к ближайшему рангу, а не усечение: на паре ячеек
        // усечение выбрало бы нижнюю и пересветило бы всю картинку.
        val index = Math.round((rates.size - 1) * SCALE_TOP_QUANTILE)
            .coerceIn(0, rates.size - 1)
        return rates[index]
    }

    /** Квантиль верха шкалы — **инженерный параметр отображения**. */
    const val SCALE_TOP_QUANTILE = 0.98f

    /**
     * Минимум импульсов в одной энергетической полосе колонки, ниже которого
     * полосы объединяются.
     *
     * **Инженерный параметр отображения.** При 10 импульсах относительная
     * флуктуация ≈1/√10 ≈ 32 %, при одном — 100 %: почти пустые полосы дают
     * случайные светлые и тёмные строчки, которые глаз читает как спектральную
     * структуру. Особенно выше ~600 кэВ, где событий мало. Тот же порог, что у
     * χ²-сравнения формы ([ShapeChange.MIN_BIN_COUNTS]) — там он нужен, чтобы
     * тест был тестом, здесь — чтобы картинка была картинкой.
     */
    const val MIN_BAND_COUNTS = 10f

    /**
     * Группы энергетических полос для ОТОБРАЖЕНИЯ: соседние полосы
     * объединяются, пока на колонку не наберётся [MIN_BAND_COUNTS] импульсов.
     *
     * Считается ОДИН раз по суммарному спектру всего окна, а не по каждой
     * колонке: иначе вертикальная нарезка прыгала бы от столбца к столбцу и
     * сравнивать соседние моменты стало бы нельзя. Исходные каналы и полосы не
     * трогаются — объединение живёт только в отрисовке.
     */
    fun bandGroups(columns: List<SpectrogramColumn?>): List<IntRange> {
        val present = columns.count { it != null }.coerceAtLeast(1)
        val totals = DoubleArray(BAND_COUNT)
        for (column in columns) {
            if (column == null) continue
            for (b in 0 until BAND_COUNT) totals[b] += column.bandCounts[b].toDouble()
        }
        val need = MIN_BAND_COUNTS.toDouble() * present
        val groups = mutableListOf<IntRange>()
        var start = 0
        var sum = 0.0
        for (band in 0 until BAND_COUNT) {
            sum += totals[band]
            if (sum >= need) {
                groups += start..band
                start = band + 1
                sum = 0.0
            }
        }
        if (start <= BAND_COUNT - 1) {
            // Хвост не набрал порога: он присоединяется к последней группе,
            // иначе у верхнего края осталась бы самая шумная полоса.
            if (groups.isEmpty()) {
                groups += 0..(BAND_COUNT - 1)
            } else {
                val last = groups.removeAt(groups.size - 1)
                groups += last.first..(BAND_COUNT - 1)
            }
        }
        return groups
    }

    /**
     * Лестница шагов ОТОБРАЖЕНИЯ. Запись всегда идёт по 5 с; шаг влияет
     * только на то, по сколько опросов складывается одна колонка.
     */
    val DISPLAY_STEPS_SECONDS = listOf(5L, 15L, 30L, 60L, 300L)

    /**
     * Сколько импульсов на полосу должно приходиться в колонке, чтобы она была
     * спектром, а не пуассоновским шумом.
     *
     * **Инженерный параметр отображения, не научная константа.** При 4
     * импульсах относительная флуктуация полосы ≈ 1/√4 = 50 %: структура ещё
     * грубая, но устойчивая линия уже отличима от «то есть, то нет». Ниже
     * этого картинка показывает случайность, а глаз читает её как линии.
     */
    const val MIN_COUNTS_PER_BAND = 4f

    /**
     * Шаг сетки времени: самый мелкий из [DISPLAY_STEPS_SECONDS], при котором
     * колонка несёт [MIN_COUNTS_PER_BAND] импульсов на полосу И колонок не
     * больше [maxColumns].
     *
     * Считается по фактической скорости счёта самих срезов, поэтому у горячего
     * места шаг остаётся мелким (событий много), а у обычного фона
     * укрупняется — без ручного переключателя и без неравномерной оси времени
     * (равная ширина колонки = равное время, иначе картинку нельзя читать).
     */
    fun displayStepSeconds(
        slices: List<SpectrogramSlice>,
        spanMillis: Long,
        maxColumns: Int,
    ): Long {
        val seconds = slices.sumOf { it.intervalSeconds }
        val counts = slices.sumOf { it.totalCounts.toDouble() }
        val rate = if (seconds > 0) counts / seconds else 0.0
        val neededForStatistics = if (rate > 0.0) {
            MIN_COUNTS_PER_BAND * BAND_COUNT / rate
        } else {
            DISPLAY_STEPS_SECONDS.last().toDouble()
        }
        val neededForWidth = if (maxColumns > 0) spanMillis / 1000.0 / maxColumns else 0.0
        val needed = maxOf(neededForStatistics, neededForWidth)
        val fromLadder = DISPLAY_STEPS_SECONDS.firstOrNull { it >= needed }
            ?: DISPLAY_STEPS_SECONDS.last()
        // Шаг не может быть мельче самого длинного среза в окне. В фоне спектр
        // опрашивается раз в 10 минут, и такой срез — это ОДНО измерение за
        // десять минут: разложив его в пятиминутную ячейку, картинка нарисовала
        // бы рядом пустую и соврала бы, что прибор молчал. Растягивать его на
        // несколько ячеек тоже нельзя — это интерполяция, которой у нас нигде
        // нет: внутри интервала распределение импульсов во времени неизвестно.
        val coarsest = slices.maxOfOrNull { it.intervalSeconds } ?: 0L
        return maxOf(fromLadder, coarsest)
    }

    /**
     * Колонки по СЕТКЕ ВРЕМЕНИ: срезы складываются в ячейку, которой
     * принадлежит их момент; ячейка без срезов остаётся `null` — это пропуск
     * потока, и он обязан быть виден, а не схлопнут.
     */
    fun grid(
        slices: List<SpectrogramSlice>,
        fromMillis: Long,
        toMillis: Long,
        stepMillis: Long,
    ): List<SpectrogramColumn?> {
        if (stepMillis <= 0L || toMillis <= fromMillis) return emptyList()
        val count = (((toMillis - fromMillis) + stepMillis - 1) / stepMillis).toInt()
            .coerceIn(1, MAX_GRID_COLUMNS)
        val sums = arrayOfNulls<FloatArray>(count)
        val seconds = LongArray(count)
        val cps = arrayOfNulls<Float>(count)
        val dose = arrayOfNulls<Float>(count)
        for (slice in slices) {
            if (slice.timestampMillis < fromMillis || slice.timestampMillis > toMillis) continue
            val index = ((slice.timestampMillis - fromMillis) / stepMillis).toInt()
                .coerceIn(0, count - 1)
            val target = sums[index] ?: FloatArray(BAND_COUNT).also { sums[index] = it }
            for (b in 0 until BAND_COUNT) target[b] += slice.bandCounts[b]
            seconds[index] += slice.intervalSeconds
            slice.cps?.let { cps[index] = it }
            slice.doseMicroSvH?.let { dose[index] = it }
        }
        return (0 until count).map { i ->
            val bands = sums[i] ?: return@map null
            SpectrogramColumn(
                startMillis = fromMillis + i * stepMillis,
                endMillis = fromMillis + (i + 1) * stepMillis,
                bandCounts = bands,
                seconds = seconds[i],
                cps = cps[i],
                doseMicroSvH = dose[i],
            )
        }
    }

    /** Потолок числа ячеек сетки — защита от абсурдного окна. */
    const val MAX_GRID_COLUMNS = 2_000

    /** Count-weighted mean photon energy of a banded slice; null if empty. */
    fun meanEnergyKeV(bandCounts: FloatArray): Float? {
        var total = 0.0
        var weighted = 0.0
        for (band in bandCounts.indices) {
            val n = bandCounts[band]
            if (n <= 0f) continue
            total += n
            weighted += n.toDouble() * bandCenterKeV(band)
        }
        if (total <= 0.0) return null
        return (weighted / total).toFloat()
    }

    /** Energy gridlines for the waterfall y-axis (fraction 0..1 → keV label). */
    val ENERGY_TICKS_KEV = listOf(50f, 100f, 300f, 600f, 1000f, 2000f)

}

/**
 * Одна колонка картинки: сумма срезов, попавших в ячейку сетки времени.
 *
 * [seconds] — реально измеренное время внутри ячейки: оно может быть меньше
 * шага сетки (поток прерывался), и именно на него делится счёт, когда
 * считается скорость. Красить неполную ячейку как спад интенсивности было бы
 * тем же враньём, что соединять линией пропуск на графике дозы.
 */
class SpectrogramColumn(
    val startMillis: Long,
    val endMillis: Long,
    val bandCounts: FloatArray,
    val seconds: Long,
    val cps: Float?,
    val doseMicroSvH: Float?,
) {
    val totalCounts: Float = bandCounts.sum()
    val meanEnergyKeV: Float? = Spectrogram.meanEnergyKeV(bandCounts)

    /** Импульсы в секунду в полосе — то, что красится. */
    fun rate(band: Int): Float =
        if (seconds > 0L) bandCounts[band] / seconds else 0f

    /**
     * Средняя скорость на полосу внутри группы полос. Именно СРЕДНЯЯ, а не
     * сумма: группы бывают разной ширины, и сумма красила бы широкую группу
     * ярче узкой при одинаковом спектре.
     */
    fun groupRate(group: IntRange): Float {
        if (seconds <= 0L) return 0f
        var sum = 0f
        for (b in group) sum += bandCounts.getOrElse(b) { 0f }
        val width = (group.last - group.first + 1).coerceAtLeast(1)
        return sum / seconds / width
    }

    /** Сумма импульсов группы — для режима «форма». */
    fun groupCounts(group: IntRange): Float {
        var sum = 0f
        for (b in group) sum += bandCounts.getOrElse(b) { 0f }
        return sum / (group.last - group.first + 1).coerceAtLeast(1)
    }

    /** Доля шага, реально покрытая измерениями, 0..1. */
    fun coverage(stepSeconds: Long): Float =
        if (stepSeconds > 0L) (seconds.toFloat() / stepSeconds).coerceIn(0f, 1f) else 0f
}

/** One waterfall column: an interval spectrum banded into energy rows. */
class SpectrogramSlice(
    val timestampMillis: Long,
    /** Accumulation Δt covered by this slice, seconds. */
    val intervalSeconds: Long,
    /** Counts per energy band (row 0 = [Spectrogram.MIN_KEV]). */
    val bandCounts: FloatArray,
    /** Latest 1 Hz count rate at slice time; null if the stream was silent. */
    val cps: Float?,
    /** Latest dose rate at slice time, µSv/h; null if unknown. */
    val doseMicroSvH: Float?,
) {
    val totalCounts: Float = bandCounts.sum()
    val meanEnergyKeV: Float? = Spectrogram.meanEnergyKeV(bandCounts)
}

/**
 * Fixed-capacity ring of waterfall slices, oldest dropped first. At the
 * 5 s poll cadence [DEFAULT_CAPACITY] covers the last ~2 hours in memory
 * (~0.6 MB); nothing is persisted — saved spectrum snapshots are the
 * durable record.
 */
class SpectrogramRing(private val capacity: Int = DEFAULT_CAPACITY) {

    private val slices = ArrayDeque<SpectrogramSlice>()

    @Synchronized
    fun add(slice: SpectrogramSlice) {
        slices.addLast(slice)
        while (slices.size > capacity) slices.removeFirst()
    }

    /** Oldest → newest. */
    @Synchronized
    fun snapshot(): List<SpectrogramSlice> = slices.toList()

    @Synchronized
    fun latest(): SpectrogramSlice? = slices.lastOrNull()

    @Synchronized
    fun clear() = slices.clear()

    companion object {
        /** 2 h × 60 min × 12 slices/min (5 s cadence). */
        const val DEFAULT_CAPACITY = 1440
    }
}
