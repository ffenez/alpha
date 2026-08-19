package app.alpha.analysis

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Континуум спектра по SNIP — гладкая подложка, из-под которой торчат линии.
 *
 * ## Что делает алгоритм
 *
 * SNIP (Statistics-sensitive Non-linear Iterative Peak-clipping, Ryan et al.,
 * Nucl. Instr. Meth. B34 (1988) 396) многократно применяет к спектру правило
 *
 *     v[i] ← min(v[i], (v[i−p] + v[i+p]) / 2)
 *
 * при убывающей ширине p. Гауссова линия шириной FWHM стирается, когда p
 * превышает её полуширину, а плавный континуум остаётся: у выпуклой вниз
 * подложки полусумма соседей не меньше центра, и минимум её не трогает.
 *
 * Работа идёт не с самими отсчётами, а в LLS-представлении
 * ([lls]/[inverseLls]) — log-log-sqrt, которое сжимает динамический диапазон
 * спектра. Без него шаг min в области сотен тысяч импульсов на низких энергиях
 * съедал бы структуру там, где на высоких она уже неразличима.
 *
 * Ширина окна берётся не постоянной, а по ожидаемой ширине линии прибора:
 * p_max(i) = [WIDTH_FWHM] · FWHM(E_i) в каналах. У сцинтиллятора FWHM растёт
 * как √E, и фиксированное окно либо не стирает широкие линии вверху шкалы,
 * либо срезает узкие внизу.
 *
 * ## Чем это НЕ является
 *
 * Континуум SNIP — **оценка формы, а не измерение**: у него нет дисперсии, и
 * значимость линии по нему считать нельзя. Поэтому критерий пика
 * ([PeakDetection]) по-прежнему строится на боковых полосах, у которых
 * дисперсия оценки известна и входит в σ(нетто). SNIP отвечает на другой
 * вопрос — «как выглядит подложка», и служит показу спектра и вычитанию
 * подложки на картинке.
 *
 * Алгоритм реализован по опубликованному описанию; чужой код не копировался.
 */
object SnipContinuum {

    /**
     * Во сколько ширин линии берётся окно отсечения. **Инженерный параметр**:
     * 1,5 — окно должно надёжно перекрывать полуширину пика (0,5 FWHM) с
     * запасом на асимметрию хвоста, но не настолько, чтобы срезать реальные
     * изгибы континуума (комптоновский край). Значение из практики SNIP
     * (Ryan et al. рекомендуют 1–2 FWHM).
     */
    const val WIDTH_FWHM = 1.5f

    /**
     * Сколько раз повторяется весь проход. **Инженерный параметр**: 2 — второй
     * проход по уже очищенной подложке убирает остаточные «полки» под широкими
     * линиями; третий на спектрах сцинтиллятора уже ничего не меняет (разница
     * ниже статистического шума канала).
     */
    const val PASSES = 2

    /**
     * Континуум под спектром, канал в канал.
     *
     * @param counts отсчёты по каналам; отрицательные считаются нулём
     * @param calibration шкала — нужна, чтобы перевести FWHM(E) в каналы
     * @param resolution662 разрешение ЭТОГО прибора на 662 кэВ, доля
     * @return подложка той же длины; пустой список для спектра короче
     *   [MIN_CHANNELS] — на нём окно отсечения не помещается
     */
    fun of(
        counts: List<Int>,
        calibration: EnergyCalibration,
        resolution662: Float = PeakDetection.RESOLUTION_662,
    ): List<Float> = ofValues(counts.map { it.toFloat() }, calibration, resolution662)

    /**
     * То же для уже подготовленного ряда — спектра после вычитания фона или
     * сглаживания, каким его видно на экране.
     */
    fun ofValues(
        values: List<Float>,
        calibration: EnergyCalibration,
        resolution662: Float = PeakDetection.RESOLUTION_662,
    ): List<Float> {
        val n = values.size
        if (n < MIN_CHANNELS) return emptyList()
        return of(values, widths(n, calibration, resolution662))
    }

    /**
     * Ширина окна отсечения на канал: [WIDTH_FWHM] · FWHM(E) в каналах,
     * зажатая между одним каналом и четвертью шкалы.
     */
    fun widths(
        channels: Int,
        calibration: EnergyCalibration,
        resolution662: Float = PeakDetection.RESOLUTION_662,
    ): IntArray = IntArray(channels) { i ->
        val energy = calibration.energyAt(i.toFloat())
        val keVPerChannel = max(calibration.a1 + 2f * calibration.a2 * i, 0.1f)
        val fwhmChannels = PeakDetection.expectedFwhmKeV(energy, resolution662) / keVPerChannel
        (WIDTH_FWHM * fwhmChannels).toInt().coerceIn(1, max(channels / 4, 1))
    }

    /**
     * Континуум при заданной ширине окна на канал — ядро алгоритма.
     *
     * @param values отсчёты по каналам
     * @param maxWidths ширина окна отсечения для каждого канала, в каналах
     * @return подложка той же длины
     */
    fun of(values: List<Float>, maxWidths: IntArray): List<Float> {
        val n = values.size
        require(maxWidths.size == n) { "ширин ${maxWidths.size} на $n каналов" }
        if (n < MIN_CHANNELS) return emptyList()

        var v = DoubleArray(n) { lls(max(values[it], 0f).toDouble()) }
        val globalMax = maxWidths.max()
        repeat(PASSES) {
            // Ширина убывает: широкое окно сперва сносит крупные линии, узкое
            // затем подчищает мелкие. Обратный порядок оставляет под широкой
            // линией ступеньку — узкое окно успевает «принять» её вершину за
            // континуум.
            for (p in globalMax downTo 1) {
                val next = v.copyOf()
                for (i in p until n - p) {
                    if (p > maxWidths[i]) continue
                    val neighbours = (v[i - p] + v[i + p]) / 2.0
                    if (neighbours < v[i]) next[i] = neighbours
                }
                v = next
            }
        }
        // Континуум не может быть выше самих отсчётов: LLS и обратное к нему
        // точны до округления double, но на нулевых каналах разница видна.
        return List(n) { i -> min(inverseLls(v[i]), max(values[i], 0f).toDouble()).toFloat() }
    }

    /**
     * Спектр за вычетом континуума: то, что остаётся от линий.
     *
     * Отрицательные значения обрезаются нулём — там, где подложка выше
     * отсчёта, разность целиком статистическая, и рисовать её ниже нуля
     * означало бы показывать шум как провал.
     */
    fun subtract(counts: List<Int>, continuum: List<Float>): List<Float> {
        if (continuum.size != counts.size) return counts.map { it.toFloat() }
        return List(counts.size) { i -> max(counts[i] - continuum[i], 0f) }
    }

    /**
     * LLS-преобразование: v = ln(ln(√(y+1) + 1) + 1).
     *
     * Сжимает динамический диапазон спектра так, что шаг отсечения одинаково
     * работает и на 10⁵ импульсов, и на 10.
     */
    fun lls(y: Double): Double = ln(ln(sqrt(y + 1.0) + 1.0) + 1.0)

    /** Обратное к [lls]: y = (exp(exp(v) − 1) − 1)² − 1. */
    fun inverseLls(v: Double): Double {
        val inner = exp(exp(v) - 1.0) - 1.0
        return max(inner * inner - 1.0, 0.0)
    }

    /**
     * Ниже этой длины окно отсечения не помещается: SNIP требует p соседей с
     * обеих сторон, а на коротком массиве это вырождается в копию входа.
     */
    const val MIN_CHANNELS = 32
}
