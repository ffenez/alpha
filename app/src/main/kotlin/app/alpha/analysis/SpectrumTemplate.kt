package app.alpha.analysis

import app.alpha.analysis.evidence.MeasuredResolution
import app.alpha.analysis.evidence.ResolutionModel
import app.alpha.analysis.evidence.SqrtResolution
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Шаблон спектра: измеренная форма отклика ПРИБОРА на известный источник.
 *
 * ## Зачем шаблоны вообще
 *
 * Оконный анализ (три окна K, eU, eTh) использует единицы процентов набранных
 * импульсов: на реальной записи это 800 импульсов из 57 000. Остальное —
 * комптоновский континуум, физически принадлежащий тем же цепочкам, но для
 * окон он шум. Разложение по шаблонам объясняет спектр ЦЕЛИКОМ, и та же
 * экспозиция даёт статистику на порядок лучше.
 *
 * ## Почему шаблон привязан к прибору
 *
 * Форма отклика — это свойство кристалла: доля полного поглощения к комптону у
 * CsI 10×10×10 мм (RadiaCode 101–103) и у более крупного кристалла 110 разная,
 * а разрешение задаёт ширину каждой линии. Поэтому шаблон несёт СВОЮ
 * калибровку, СВОЁ разрешение и модель прибора, на котором снят, и приводится
 * к целевому прибору явно ([adaptTo]) — с отказом там, где приведение
 * невозможно.
 *
 * ## Что здесь НЕ делается
 *
 * Шаблон не превращается в активность. Он говорит, какая ДОЛЯ измеренного
 * спектра объясняется этой формой; переход к беккерелям требует известной
 * геометрии и эталонного источника.
 */
data class SpectrumTemplate(
    /** Что это за форма: «Th-232», «фон», «K-40». */
    val name: String,
    /** Счёт по каналам, как измерено. */
    val counts: List<Int>,
    val calibration: EnergyCalibration,
    /** Живое время накопления шаблона, с. */
    val seconds: Long,
    /**
     * Разрешение прибора шаблона на 662 кэВ, доля. Нужно, чтобы привести
     * шаблон к прибору с ХУДШИМ разрешением: узкие линии уширяются, широкие
     * сузить нельзя.
     */
    val resolution662: Float,
    /** Модель прибора, на котором снят шаблон; null — не указана. */
    val deviceName: String? = null,
) {
    val totalCounts: Long get() = counts.sumOf { it.toLong() }

    /** Скорость счёта шаблона, с⁻¹ — им нормируются доли при разложении. */
    val ratePerSecond: Double
        get() = if (seconds > 0) totalCounts.toDouble() / seconds else 0.0

    companion object {

        /**
         * Приведение шаблона к чужой шкале и разрешению.
         *
         * Два действия, оба обязательны и оба в этом порядке:
         *
         * 1. **Уширение.** Если целевой прибор различает линии хуже, каждая
         *    линия шаблона должна стать шире ровно на недостающую дисперсию:
         *    σ²(доп) = σ²(цель) − σ²(шаблона). Свёртка идёт по энергии, потому
         *    что ширина линии задана энергией, а не номером канала.
         * 2. **Перекладка на каналы цели.** Счёт переносится по перекрытию
         *    энергетических границ каналов: канал шаблона может лечь между
         *    каналами цели, и делить его пополам «на глаз» нельзя — потеряется
         *    площадь.
         *
         * @return null, когда приведение НЕВОЗМОЖНО: у цели разрешение ЛУЧШЕ
         *   (сузить измеренную линию нечем — данные о её форме утеряны) или
         *   шкалы не пересекаются. Отказ здесь честнее подгонки: чужая форма,
         *   натянутая на другой прибор, молча перераспределит счёт между
         *   нуклидами.
         */
        fun adapt(
            template: SpectrumTemplate,
            targetCalibration: EnergyCalibration,
            targetChannels: Int,
            targetResolution662: Float,
            targetResolution: ResolutionModel? = null,
        ): List<Double>? {
            if (targetChannels < MIN_CHANNELS || template.counts.size < MIN_CHANNELS) return null
            // Разрешение цели ХУЖЕ — значит уширяем. Небольшой запас: шаблон и
            // цель одной модели дают равные числа, и требовать строгого
            // неравенства значило бы отказывать самому частому случаю.
            if (targetResolution662 + RESOLUTION_TOLERANCE < template.resolution662) return null

            val widened = widen(
                template = template,
                target = targetResolution ?: SqrtResolution(targetResolution662.toDouble()),
            )
            return rebin(widened, template.calibration, targetCalibration, targetChannels)
        }

        /**
         * Досъёмка шаблона: новое накопление того же прибора складывается со
         * старым.
         *
         * Шаблон — измерение, и его собственный шум падает как 1/√N: час поверх
         * получаса делает форму заметно точнее, а вместе с ней и доли при
         * разложении. Складывать при этом можно только выровненное:
         *
         *  - шкала прибора между сеансами уезжает (на 110 линия K-40 стоит на
         *    ≈2 % ниже), поэтому [gain] и [offsetKeV] — это ИЗМЕРЕННОЕ смещение
         *    новой записи относительно шаблона, а не поправка «на глаз»;
         *  - счёт переносится на каналы шаблона по перекрытию энергий, иначе
         *    сложение размажет линии и разрешение шаблона ухудшится.
         *
         * Уширения здесь нет: прибор тот же, разрешение то же.
         *
         * @param gain усиление шкалы новой записи относительно её же
         *   калибровки (1 — калибровка верна).
         * @param offsetKeV смещение шкалы новой записи, кэВ.
         * @return шаблон со сложенным счётом и сложенным временем; null, если
         *   шкалы не пересекаются или новое накопление слишком короткое.
         */
        fun accumulate(
            template: SpectrumTemplate,
            counts: List<Int>,
            calibration: EnergyCalibration,
            seconds: Long,
            gain: Double = 1.0,
            offsetKeV: Double = 0.0,
        ): SpectrumTemplate? {
            if (counts.size < MIN_CHANNELS || seconds <= 0L) return null
            val corrected = EnergyCalibration(
                a0 = (calibration.a0 * gain + offsetKeV).toFloat(),
                a1 = (calibration.a1 * gain).toFloat(),
                a2 = (calibration.a2 * gain).toFloat(),
            )
            val values = DoubleArray(counts.size) { counts[it].toDouble() }
            // Тот же прибор в том же режиме даёт ту же шкалу: перекладывать
            // нечего, а округление после неё только теряло бы импульсы.
            val moved = if (corrected == template.calibration && counts.size == template.counts.size) {
                values.toList()
            } else {
                rebin(values, corrected, template.calibration, template.counts.size) ?: return null
            }
            // Округление к ближайшему, а не отбрасывание дробной части:
            // отбрасывание теряло бы в среднем полимпульса на канал, то есть
            // сотни импульсов на шкалу. Ошибка округления в канале ≤ 0,5 —
            // много меньше его пуассоновского √N.
            val summed = List(template.counts.size) { index ->
                template.counts[index] + moved[index].roundToInt()
            }
            return template.copy(counts = summed, seconds = template.seconds + seconds)
        }

        /**
         * Уширение линий шаблона до разрешения цели.
         *
         * Ширина зависит от энергии, поэтому ядро свёртки своё для каждого
         * канала. Ширина цели берётся из действующей модели разрешения
         * ([ResolutionModel]): если приложение измерило её по линиям прибора
         * ([MeasuredResolution]), уширение идёт по измеренному ходу, а не по
         * паспортному √E, который у верхнего края шкалы расходится с
         * измеренным на десятки процентов ширины. Шаблон описывается своим
         * измеренным числом на 662 кэВ: многолинейной модели чужого прибора у
         * нас нет.
         */
        private fun widen(template: SpectrumTemplate, target: ResolutionModel): DoubleArray {
            val n = template.counts.size
            val out = DoubleArray(n)
            val cal = template.calibration
            val own = SqrtResolution(template.resolution662.toDouble())
            for (i in 0 until n) {
                val value = template.counts[i].toDouble()
                if (value <= 0.0) continue
                val energy = cal.energyAt(i.toFloat())
                val targetFwhm = target.fwhmKeV(energy.toDouble()).toFloat()
                val ownFwhm = own.fwhmKeV(energy.toDouble()).toFloat()
                val extra = targetFwhm * targetFwhm - ownFwhm * ownFwhm
                if (extra <= 0f) {
                    out[i] += value
                    continue
                }
                val sigmaKeV = sqrt(extra.toDouble()) / FWHM_TO_SIGMA
                val keVPerChannel = max(cal.a1 + 2f * cal.a2 * i, 0.01f).toDouble()
                val sigmaChannels = sigmaKeV / keVPerChannel
                if (sigmaChannels < MIN_SIGMA_CHANNELS) {
                    out[i] += value
                    continue
                }
                val reach = (KERNEL_SIGMAS * sigmaChannels).toInt().coerceAtLeast(1)
                var norm = 0.0
                for (d in -reach..reach) {
                    val j = i + d
                    if (j < 0 || j >= n) continue
                    norm += gauss(d.toDouble(), sigmaChannels)
                }
                if (norm <= 0.0) {
                    out[i] += value
                    continue
                }
                for (d in -reach..reach) {
                    val j = i + d
                    if (j < 0 || j >= n) continue
                    out[j] += value * gauss(d.toDouble(), sigmaChannels) / norm
                }
            }
            return out
        }

        /**
         * Перекладка на каналы цели по перекрытию энергетических границ.
         *
         * Границей канала считается середина между соседними центрами: канал
         * прибора — это интервал энергий, а не точка.
         */
        private fun rebin(
            values: DoubleArray,
            from: EnergyCalibration,
            to: EnergyCalibration,
            targetChannels: Int,
        ): List<Double>? {
            val out = DoubleArray(targetChannels)
            val targetEdges = DoubleArray(targetChannels + 1) { edge(to, it, targetChannels) }
            var overlapped = false
            for (i in values.indices) {
                val value = values[i]
                if (value <= 0.0) continue
                val lo = edge(from, i, values.size)
                val hi = edge(from, i + 1, values.size)
                if (hi <= lo) continue
                // Каналы цели упорядочены по энергии, поэтому достаточно
                // пройти от первого пересечения до последнего.
                var j = targetEdges.indexOfFirst { it > lo } - 1
                if (j < 0) j = 0
                while (j < targetChannels && targetEdges[j] < hi) {
                    val left = max(lo, targetEdges[j])
                    val right = minOf(hi, targetEdges[j + 1])
                    if (right > left) {
                        out[j] += value * (right - left) / (hi - lo)
                        overlapped = true
                    }
                    j++
                }
            }
            return if (overlapped) out.toList() else null
        }

        /** Граница канала [index] по энергии: середина между центрами. */
        private fun edge(calibration: EnergyCalibration, index: Int, channels: Int): Double {
            val position = (index - 0.5f).coerceIn(-0.5f, channels - 0.5f)
            return calibration.energyAt(position).toDouble()
        }

        private fun gauss(distance: Double, sigma: Double): Double =
            exp(-0.5 * (distance / sigma) * (distance / sigma))

        /** Меньше — уже не спектр, а несколько чисел. */
        const val MIN_CHANNELS = 64

        /**
         * Насколько разрешение цели может быть ЛУЧШЕ шаблона, чтобы приведение
         * всё же считалось возможным. Ноль означал бы отказ для двух приборов
         * одной модели из-за округления паспортного числа.
         */
        const val RESOLUTION_TOLERANCE = 0.002f

        /** Ниже этой ширины ядра свёртка ничего не меняет, кроме времени. */
        private const val MIN_SIGMA_CHANNELS = 0.05

        /** Ядро обрезается на этом числе σ: дальше вклад ниже 1e-3. */
        private const val KERNEL_SIGMAS = 3.5

        private const val FWHM_TO_SIGMA = 2.354820045
    }
}

/** Насколько две калибровки расходятся по энергии на середине шкалы, кэВ. */
fun EnergyCalibration.divergenceKeV(other: EnergyCalibration, channels: Int): Float =
    abs(energyAt(channels / 2f) - other.energyAt(channels / 2f))
