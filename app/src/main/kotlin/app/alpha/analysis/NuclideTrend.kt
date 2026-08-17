package app.alpha.analysis

import kotlin.math.sqrt

/**
 * Ряд НЕТТО-скорости счёта в окне линии выбранного нуклида.
 *
 * ## Зачем
 *
 * Полная скорость счёта отвечает на вопрос «сколько событий всего», и по ней
 * нельзя отличить, ЧЕМ вызван подъём. Окно вокруг конкретной линии отвечает на
 * другой вопрос: «сколько событий приходится на энергию, характерную для этого
 * нуклида, сверх подпирающего континуума». По такому ряду видно то, ради чего
 * прибор и носят: где цезия больше, чем рядом, и растёт ли радон к утру.
 *
 * Механика ровно та же, что у радонового индикатора ([RadonTrend]) — окно
 * ±FWHM, боковой континуум, разности соседних снимков, — но нуклид выбирается,
 * а не зашит.
 *
 * ## Чего этот ряд НЕ означает
 *
 * - **Это не активность и не концентрация.** Без измеренной кривой
 *   эффективности детектора (`DetectorEfficiency.AVAILABLE = false`) перевод в
 *   Бк невозможен, и приложение его не делает.
 * - **Сравнивать можно только сравнимое**: место с местом и время со временем
 *   ОДНИМ прибором. Два разных прибора дадут разные числа при одном и том же
 *   поле.
 * - **Окно не «принадлежит» нуклиду.** В нём считается всё, что попало в
 *   энергию: соседняя линия другого ряда даст такой же вклад. Поэтому это
 *   индикатор ЛИНИИ, а не доказательство присутствия нуклида — им занимается
 *   движок доказательств.
 * - При низкой скорости счёта на отдельной энергии погрешность велика, и она
 *   всегда идёт рядом с числом, а не прячется.
 */
object NuclideTrend {

    /**
     * Линия, по которой строится ряд.
     *
     * @param nuclide имя нуклида как в библиотеке ([GammaLines]).
     * @param energyKeV энергия линии.
     * @param label подпись для экрана: «Cs-137 · 661,7 кэВ».
     */
    data class Line(val nuclide: String, val energyKeV: Float, val label: String)

    /**
     * Точка ряда.
     *
     * @param netCps нетто-скорость счёта в окне линии, имп/с.
     * @param sigmaCps её стандартная неопределённость (та же формула нетто, что
     *   в поиске пиков: статистика окна плюс неопределённость оценки континуума).
     * @param significance нетто/σ — во сколько своих неопределённостей нетто
     *   отличается от нуля. Ниже 3 говорить о превышении над континуумом
     *   нельзя, и экран обязан это показывать.
     */
    data class Point(
        val atMillis: Long,
        val seconds: Long,
        val netCps: Float,
        val sigmaCps: Float,
        val significance: Float,
    )

    /**
     * Линии, которые имеет смысл предлагать: сильные и разделимые
     * сцинтиллятором. Числа — из библиотеки [GammaLines], а не отдельным
     * списком: расхождение двух таблиц рано или поздно превращается в
     * расхождение чисел на экране.
     */
    val OFFERED: List<Line> = listOf(
        Line("Cs-137", 661.7f, "Cs-137 · 661,7"),
        Line("K-40", 1460.8f, "K-40 · 1460,8"),
        Line("Bi-214", 609.3f, "Bi-214 · 609,3"),
        Line("Pb-214", 351.9f, "Pb-214 · 351,9"),
        Line("Tl-208", 583.2f, "Tl-208 · 583,2"),
        Line("Tl-208", 2614.5f, "Tl-208 · 2614,5"),
    )

    /**
     * Ниже этой значимости нетто не отличимо от подпирающего континуума.
     * **Инженерный параметр**: три σ — тот же порядок, что у порога поиска
     * пиков (4σ), но здесь считается ОДНО заранее названное окно, а не
     * перебираются все каналы, поэтому поправка на множественность не нужна.
     */
    const val MIN_SIGNIFICANCE = 3f

    /**
     * Ряд из последовательных снимков накопленного спектра.
     *
     * Снимки НАКОПИТЕЛЬНЫЕ, поэтому берутся разности соседних: интервал между
     * ними и есть время экспозиции точки. Пара пропускается, если разность
     * получилась отрицательной (прибор сбросили) или интервал слишком короток —
     * иначе точка была бы шумом с гигантской погрешностью, поданным наравне с
     * измерением.
     *
     * @param snapshots пары «момент — спектр», по возрастанию времени.
     */
    fun series(
        snapshots: List<Snapshot>,
        line: Line,
        minSeconds: Long = MIN_INTERVAL_SECONDS,
    ): List<Point> {
        val out = mutableListOf<Point>()
        for (i in 1 until snapshots.size) {
            val previous = snapshots[i - 1]
            val current = snapshots[i]
            val seconds = current.durationSeconds - previous.durationSeconds
            if (seconds < minSeconds) continue
            if (current.counts.size != previous.counts.size) continue
            val difference = IntArray(current.counts.size) { ch ->
                (current.counts[ch] - previous.counts[ch]).coerceAtLeast(0)
            }
            val net = RadonTrend.roiNet(
                difference.toList(),
                current.calibration,
                line.energyKeV,
            ) ?: continue
            val netCps = net.netCounts / seconds
            val sigmaCps = net.sigmaCounts / seconds
            out += Point(
                atMillis = current.atMillis,
                seconds = seconds,
                netCps = netCps,
                sigmaCps = sigmaCps,
                significance = if (sigmaCps > 0f) netCps / sigmaCps else 0f,
            )
        }
        return out
    }

    /**
     * Сводка ряда для экрана: среднее с неопределённостью и вердикт о том,
     * выделяется ли линия над континуумом вообще.
     *
     * Среднее взвешено экспозицией: точка за десять минут несёт больше
     * статистики, чем точка за минуту.
     */
    fun summary(points: List<Point>): Summary? {
        if (points.isEmpty()) return null
        var weight = 0.0
        var sum = 0.0
        var variance = 0.0
        for (p in points) {
            val w = p.seconds.toDouble()
            weight += w
            sum += p.netCps * w
            variance += (p.sigmaCps * w).toDouble() * (p.sigmaCps * w)
        }
        if (weight <= 0.0) return null
        val mean = (sum / weight).toFloat()
        val sigma = (sqrt(variance) / weight).toFloat()
        return Summary(
            netCps = mean,
            sigmaCps = sigma,
            significance = if (sigma > 0f) mean / sigma else 0f,
            points = points.size,
            seconds = points.sumOf { it.seconds },
        )
    }

    data class Summary(
        val netCps: Float,
        val sigmaCps: Float,
        val significance: Float,
        val points: Int,
        val seconds: Long,
    ) {
        /** Выделяется ли линия над континуумом за всё накопленное время. */
        val resolved: Boolean get() = significance >= MIN_SIGNIFICANCE
    }

    /** Снимок накопленного спектра — вход ряда. */
    data class Snapshot(
        val atMillis: Long,
        val durationSeconds: Long,
        val counts: List<Int>,
        val calibration: EnergyCalibration,
    )

    /**
     * Короче этого интервала точка не строится.
     * **Инженерный параметр**: полминуты — при фоновых единицах импульсов в
     * окне линии меньшая экспозиция даёт нетто, неотличимое от нуля, и ряд
     * превращался бы в частокол шума.
     */
    const val MIN_INTERVAL_SECONDS = 30L
}
