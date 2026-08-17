package app.alpha.analysis

/**
 * Калибровочный набор из УЖЕ НАКОПЛЕННОГО — без единого действия человека.
 *
 * Приложение и так пишет снимок спектра раз в 10 минут (тот же опрос, что
 * кормит радоновый индикатор) и умеет считать почасовой радоновый ряд. Этого
 * достаточно, чтобы собрать материал для диагностики прибора: нужны долгие
 * накопления ради статистики на 1460,8 и 2614,5 кэВ и отдельно часы с
 * наибольшим радоном — промывка после дождя поднимает продукты распада радона
 * на порядок, и линии Bi-214 в такие часы измеримы там, где в среднем за
 * сутки они тонут в континууме.
 *
 * ## Почему интервалы, а не сами снимки
 *
 * Снимки прибора КУМУЛЯТИВНЫ: каждый следующий содержит предыдущий. Сложить
 * их значило бы посчитать одни и те же импульсы много раз. Поэтому сначала
 * берутся разности соседних снимков одной цепочки накопления (сброс
 * накопления рвёт цепочку), а уже непересекающиеся интервалы складываются
 * канальной суммой через [SpectrumMerge] — с его же допуском на расхождение
 * калибровок и без всякого ребиннинга (дробные счета перестали бы быть
 * пуассоновскими).
 *
 * ## Почему прореживание до одного снимка в час ничего не теряет
 *
 * Разность последнего снимка часа и последнего снимка предыдущего часа
 * покрывает весь час целиком: импульсы, попавшие в промежуточные снимки,
 * входят в неё полностью. Прореживание экономит чтение блобов, а не данные —
 * тот же приём, что у радонового экрана ([RadonTrend.selectHourlyIds]).
 */
object CalibrationDataset {

    /** Ключ длинного накопления в отчёте. */
    const val SOURCE_LONG = "long"

    /** Ключ накопления по радоновым часам. */
    const val SOURCE_RADON = "radon"

    /**
     * Сколько секунд радоновых часов нужно набрать — **инженерный параметр**.
     * Шесть часов промывки при типичном фоне дают в ROI 609 кэВ несколько
     * тысяч нетто-импульсов: этого хватает, чтобы линия 1120,3 кэВ (втрое
     * слабее) прошла порог значимости 8σ. Меньше — набор собирается, но
     * ничего нового по сравнению с длинной суммой не приносит.
     */
    const val MIN_RADON_SECONDS = 6L * 3_600L

    /**
     * Какую долю часов окна можно объявить радоновыми — **инженерный
     * параметр**. Если брать больше трети, «богатые» часы перестают
     * отличаться от среднего, и второе накопление становится копией первого.
     */
    const val MAX_RADON_HOUR_FRACTION = 1.0 / 3.0

    /**
     * Непересекающийся интервал накопления: разность двух соседних снимков.
     *
     * @param bi214Cps net-скорость в ROI 609,3 кэВ — по ней ранжируются часы;
     *   величина относительная, единиц активности у неё нет
     */
    data class Interval(
        val endMillis: Long,
        val deltaSeconds: Long,
        val counts: List<Int>,
        val calibration: EnergyCalibration,
        val bi214Cps: Float,
    )

    /** Сложенное накопление: то, что дальше разбирает движок. */
    data class Accumulation(
        val counts: List<Int>,
        val calibration: EnergyCalibration,
        val seconds: Long,
        val intervalCount: Int,
        val hoursCovered: Int,
        val fromMillis: Long,
        val toMillis: Long,
    )

    /**
     * Что удалось собрать. [radonRich] равен null, когда радоновых часов не
     * набралось на [MIN_RADON_SECONDS] — тогда экран так и говорит, вместо
     * того чтобы показать сумму двух случайных часов.
     */
    data class Selection(
        val long: Accumulation?,
        val radonRich: Accumulation?,
        /** Всего часов с измерениями в окне — «сколько материала есть вообще». */
        val hoursAvailable: Int,
        /** Часы, отобранные как радоновые, от самого богатого. */
        val radonHours: Int,
        /** Сколько секунд набралось в самых радоновых часах — и когда их мало тоже. */
        val radonSeconds: Long,
    )

    /**
     * Разности соседних снимков одной цепочки накопления. Пара пропускается
     * (и цепочка переармируется), если накопление сбросили, изменилась сетка
     * каналов или калибровки разошлись больше допуска — те же три условия,
     * что в [RadonTrend.intervals], потому что вопрос тот же: можно ли
     * считать два снимка продолжением одного измерения.
     */
    fun intervals(snapshots: List<RadonTrend.Snapshot>): List<Interval> {
        val sorted = snapshots.sortedBy { it.timestampMillis }
        val result = mutableListOf<Interval>()
        var previous: RadonTrend.Snapshot? = null
        for (current in sorted) {
            val prev = previous
            previous = current
            if (prev == null) continue
            if (current.counts.size != prev.counts.size) continue
            val delta = current.durationSeconds - prev.durationSeconds
            if (delta <= 0) continue
            val drift = SpectrumCompare.calibrationDeltaKeV(
                current.calibration,
                prev.calibration,
                current.counts.size,
            )
            if (drift > SpectrumCompare.CALIBRATION_TOLERANCE_KEV) continue
            val diff = IntArray(current.counts.size)
            var reset = false
            for (i in diff.indices) {
                val d = current.counts[i] - prev.counts[i]
                if (d < 0) {
                    reset = true
                    break
                }
                diff[i] = d
            }
            if (reset) continue
            val diffList = diff.toList()
            val bi = RadonTrend.roiNet(diffList, current.calibration, RadonTrend.BI214_KEV)
            result += Interval(
                endMillis = current.timestampMillis,
                deltaSeconds = delta,
                counts = diffList,
                calibration = current.calibration,
                bi214Cps = (bi?.netCounts ?: 0f) / delta.toFloat(),
            )
        }
        return result
    }

    /**
     * Два накопления из одного пула интервалов: всё подряд и отдельно самые
     * радоновые часы. Часы ранжируются по Δt-взвешенной net-скорости в ROI
     * Bi-214 — взвешенной, иначе короткий обрывок часа с одним ярким
     * интервалом обошёл бы полный час.
     */
    fun select(intervals: List<Interval>): Selection {
        if (intervals.isEmpty()) return Selection(null, null, 0, 0, 0L)
        val byHour = intervals.groupBy { it.endMillis / RadonTrend.HOUR_MILLIS }
        val ranked = byHour.entries
            .map { (hour, group) ->
                val seconds = group.sumOf { it.deltaSeconds }
                val counts = group.sumOf { (it.bi214Cps * it.deltaSeconds).toDouble() }
                Triple(hour, if (seconds > 0) counts / seconds else 0.0, seconds)
            }
            .sortedByDescending { it.second }
        val hourCap = maxOf(1, (byHour.size * MAX_RADON_HOUR_FRACTION).toInt())
        val chosen = mutableSetOf<Long>()
        var seconds = 0L
        for ((hour, _, hourSeconds) in ranked) {
            if (chosen.size >= hourCap) break
            chosen += hour
            seconds += hourSeconds
            if (seconds >= MIN_RADON_SECONDS) break
        }
        val radonIntervals = intervals.filter {
            it.endMillis / RadonTrend.HOUR_MILLIS in chosen
        }
        val enoughRadon = seconds >= MIN_RADON_SECONDS
        return Selection(
            long = merge(intervals),
            radonRich = if (enoughRadon) merge(radonIntervals) else null,
            hoursAvailable = byHour.size,
            radonHours = if (enoughRadon) chosen.size else 0,
            radonSeconds = seconds,
        )
    }

    /** Один интервал — уже накопление; складывать нечего. */
    private fun single(interval: Interval) = Accumulation(
        counts = interval.counts,
        calibration = interval.calibration,
        seconds = interval.deltaSeconds,
        intervalCount = 1,
        hoursCovered = 1,
        fromMillis = interval.endMillis - interval.deltaSeconds * 1000L,
        toMillis = interval.endMillis,
    )

    /** Канальная сумма интервалов через [SpectrumMerge]; null — сумма отказана. */
    fun merge(intervals: List<Interval>): Accumulation? {
        if (intervals.isEmpty()) return null
        if (intervals.size == 1) return single(intervals.first())
        // Интервалы с уехавшей калибровкой отбрасываются ДО суммы, а не роняют
        // её целиком: неделя записи может содержать снимок с другой сеткой, и
        // терять из-за него весь материал нельзя. Сколько отброшено, видно по
        // разнице [Accumulation.intervalCount] и числа поданных интервалов.
        val base = intervals.maxBy { it.deltaSeconds }
        val usable = intervals.filter {
            it.counts.size == base.counts.size &&
                SpectrumCompare.calibrationDeltaKeV(
                    it.calibration,
                    base.calibration,
                    base.counts.size,
                ) <= SpectrumCompare.CALIBRATION_TOLERANCE_KEV
        }
        if (usable.isEmpty()) return null
        if (usable.size == 1) return single(usable.first())
        val inputs = usable.mapIndexed { index, interval ->
            SpectrumMerge.Input(
                counts = interval.counts,
                durationSeconds = interval.deltaSeconds,
                calibration = interval.calibration,
                name = "$index",
            )
        }
        val merged = SpectrumMerge.merge(inputs) as? SpectrumMerge.Outcome.Ok ?: return null
        return Accumulation(
            counts = merged.counts,
            calibration = merged.calibration,
            seconds = merged.durationSeconds,
            intervalCount = usable.size,
            hoursCovered = usable.map { it.endMillis / RadonTrend.HOUR_MILLIS }.distinct().size,
            fromMillis = usable.minOf { it.endMillis - it.deltaSeconds * 1000L },
            toMillis = usable.maxOf { it.endMillis },
        )
    }
}
