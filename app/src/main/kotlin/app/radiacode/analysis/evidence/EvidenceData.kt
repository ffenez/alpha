package app.radiacode.analysis.evidence

import app.radiacode.analysis.GammaLineLibrary
import app.radiacode.analysis.Peak
import kotlin.math.sqrt

/**
 * Откуда взято число ядерных данных.
 *
 * Разделение не косметическое: DDEP (LNHB) публикует ОЦЕНЁННЫЕ данные распада
 * вместе с методикой и неопределённостями, ENSDF/NuDat даёт энергии и выходы
 * без того, чтобы неопределённость доходила до нашей таблицы. Пока в
 * приложении лежат числа второго рода, движок обязан это знать и не выдавать
 * отсутствующую неопределённость за нулевую.
 */
enum class DataSource {
    /** Decay Data Evaluation Project (LNHB) — оценённые данные с неопределённостями. */
    DDEP,

    /** ENSDF через IAEA Live Chart / NNDC NuDat 3 — то, что лежит в библиотеке сейчас. */
    ENSDF,
}

/**
 * Оценка величины с неопределённостью. `uncertainty == null` означает
 * «неизвестна», и это НЕ то же самое, что ноль: нулевая неопределённость
 * сделала бы совпадение бесконечно точным.
 */
data class Estimate(val value: Double, val uncertainty: Double? = null)

/**
 * Библиотечная гамма-линия для движка доказательств.
 *
 * Отличается от [app.radiacode.analysis.GammaLine] тем, что несёт provenance и
 * неопределённости. Значения [energyUncertaintyKeV] и
 * [intensityUncertaintyPercent] сейчас `null` у ВСЕХ линий — таблица собрана из
 * ENSDF-выборок, где неопределённости до нас не дошли. Придумывать им числа
 * нельзя: неопределённость библиотечной энергии входит в знаменатель z-оценки
 * ([EnergyMatching]), и выдуманное значение прямо управляло бы тем, какие
 * совпадения движок объявит приемлемыми.
 */
data class LibraryLine(
    val nuclide: String,
    /** Родительская цепочка для дочерних нуклидов (Ra-226 / Th-232), иначе null. */
    val chain: String?,
    val energyKeV: Double,
    /** 1σ табличной энергии; null — источник её не дал. */
    val energyUncertaintyKeV: Double?,
    /** Фотонов на 100 распадов ЭТОГО нуклида. */
    val intensityPercent: Double,
    /** 1σ выхода в тех же процентных единицах; null — источник её не дал. */
    val intensityUncertaintyPercent: Double?,
    val source: DataSource,
    val natural: Boolean,
)

/**
 * Наблюдённый пик — сущность, отдельная от библиотечной линии.
 *
 * Библиотечная линия это свойство ядра, наблюдённый пик — свойство измерения:
 * у него есть своя ширина, своя площадь и своя неопределённость положения.
 * Смешение этих двух вещей и есть корень вопроса «какая известная линия
 * оказалась рядом».
 */
data class ObservedPeak(
    val centroidKeV: Double,
    /** 1σ положения центроида, см. [centroidUncertaintyKeV]. */
    val centroidUncertaintyKeV: Double,
    /** Ширина пика на половине высоты, кэВ. */
    val fwhmKeV: Double,
    /** Нетто-площадь (валовые импульсы минус континуум). */
    val netArea: Double,
    /** 1σ нетто-площади. */
    val netAreaUncertainty: Double,
    /** Нетто / σ(нетто) — то же определение, что в [app.radiacode.analysis.PeakDetection]. */
    val significance: Double,
) {
    companion object {

        /**
         * σ центроида ≈ FWHM / (2,355·√N).
         *
         * **Вывод.** Центроид считается как взвешенное среднее по каналам пика,
         * поэтому его стандартная ошибка = σ формы / √N, где N — число
         * нетто-импульсов, а σ формы связана с шириной как FWHM = 2√(2 ln 2)·σ
         * ≈ 2,355·σ. Это стандартная ошибка среднего, применённая к
         * распределению импульсов внутри пика.
         *
         * Что этот вывод НЕ учитывает: неопределённость вычитания континуума
         * (при слабом пике на высоком фоне центроид «тянет» наклон подложки) и
         * неопределённость самой энергетической калибровки — последняя входит
         * отдельным членом σ_cal в [EnergyMatching]. Поэтому число — оценка
         * снизу, и называть его полной погрешностью положения нельзя.
         */
        fun centroidUncertaintyKeV(fwhmKeV: Double, netArea: Double): Double =
            if (netArea <= 0.0 || fwhmKeV <= 0.0) {
                Double.NaN
            } else {
                fwhmKeV / (2.3548 * sqrt(netArea))
            }

        /**
         * Перевод найденного [Peak] в наблюдённый пик.
         *
         * σ(нетто) восстанавливается точно: детектор кладёт в пик значимость =
         * нетто/σ(нетто), поэтому σ = нетто/значимость — это ровно то число,
         * которое посчитала [app.radiacode.analysis.PeakDetection] по формуле
         * IAEA, а не его приближение √N.
         *
         * FWHM берётся из [resolution]: детектор измеряет ширину структуры, но
         * наружу её не отдаёт, а ожидаемая ширина на этой энергии — законная
         * оценка для пика, который УЖЕ прошёл гейт «0,5–2,5 ожидаемой».
         */
        fun from(peak: Peak, resolution: ResolutionModel): ObservedPeak {
            val energy = peak.energyKeV.toDouble()
            val net = peak.netCounts.toDouble()
            val significance = peak.significance.toDouble()
            val fwhm = resolution.fwhmKeV(energy)
            val sigmaNet = if (significance > 0.0) net / significance else Double.NaN
            return ObservedPeak(
                centroidKeV = energy,
                centroidUncertaintyKeV = centroidUncertaintyKeV(fwhm, net),
                fwhmKeV = fwhm,
                netArea = net,
                netAreaUncertainty = sigmaNet,
                significance = significance,
            )
        }
    }
}

/**
 * Та же библиотека линий, что и у подсказок, но с provenance.
 *
 * Библиотека здесь СОЗНАТЕЛЬНО не расширяется: движок дискриминации сначала
 * должен доказать, что он умеет отличать гипотезы друг от друга на десятке
 * хорошо изученных нуклидов; расширение таблицы до этого только увеличивает
 * шанс, что любой пик во что-нибудь «попадёт».
 */
object EvidenceLineLibrary {

    /**
     * Все линии с честной пометкой источника. Неопределённости `null` —
     * см. KDoc [LibraryLine]: в ENSDF-выборке, из которой собрана таблица, их
     * нет, и движок обязан обращаться с ними как с неизвестными.
     */
    val LINES: List<LibraryLine> = GammaLineLibrary.LINES.map { line ->
        LibraryLine(
            nuclide = line.isotope,
            chain = line.chain,
            energyKeV = toTenth(line.energyKeV),
            energyUncertaintyKeV = null,
            intensityPercent = toTenth(line.intensityPercent),
            intensityUncertaintyPercent = null,
            source = DataSource.ENSDF,
            natural = line.natural,
        )
    }

    /**
     * Float → Double с округлением до 0,1.
     *
     * Прямое `toDouble()` дало бы 609,2999877929688 из `609.3f` — мусорные
     * цифры разрядности Float, которых в источнике нет: и энергии, и выходы
     * даны в таблицах с точностью 0,1. Округление возвращает ровно то число,
     * которое опубликовано, и заодно делает сравнения предсказуемыми.
     */
    private fun toTenth(value: Float): Double = Math.round(value * 10.0) / 10.0

    val NUCLIDES: List<String> = LINES.map { it.nuclide }.distinct()

    fun linesOf(nuclide: String): List<LibraryLine> = LINES.filter { it.nuclide == nuclide }

    /** Самая яркая линия нуклида — та, отсутствие которой вообще может что-то значить. */
    fun strongestLineOf(nuclide: String): LibraryLine? =
        linesOf(nuclide).maxByOrNull { it.intensityPercent }
}
