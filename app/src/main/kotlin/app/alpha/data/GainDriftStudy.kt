package app.alpha.data

import app.alpha.AppGraph
import app.alpha.analysis.CalibrationDataset
import app.alpha.analysis.GainDrift
import app.alpha.analysis.GainDriftFit
import app.alpha.analysis.PeakDetection
import app.alpha.analysis.RadonTrend
import app.alpha.analysis.evidence.BackgroundLineInventory
import app.alpha.analysis.evidence.CalibrationLineCandidate
import app.alpha.analysis.evidence.LineMeasurement
import app.alpha.analysis.evidence.ResolutionModel
import app.alpha.analysis.evidence.ResolutionSource
import app.alpha.analysis.evidence.SqrtResolution
import app.alpha.device.ConnectionState

/**
 * Уход шкалы прибора от его температуры — по уже накопленному, без действий
 * человека.
 *
 * ## Из чего это собирается
 *
 * Приложение и так пишет снимок спектра каждые десять минут и температуру
 * ПРИБОРА (её присылает сам прибор в редких данных). Значит, история уже
 * содержит десятки пар «спектр — температура»; остаётся измерить в каждом
 * положение опорной линии.
 *
 * Опорная — K-40 (1460,8 кэВ): единственная линия природного фона, которая на
 * бытовых накоплениях всегда есть, стоит одна (ближайшие соседи дальше
 * разрешения прибора) и лежит в верхней половине шкалы, где сдвиг усиления
 * заметнее всего.
 *
 * ## Почему накопления по несколько часов
 *
 * Центроид линии определяется её статистикой: FWHM/(2,355·√N). На часовом
 * накоплении в линии K-40 сотни импульсов, и центроид гуляет на уровне самого
 * ожидаемого хода. Поэтому соседние интервалы складываются в блоки по
 * [BLOCK_SECONDS], а температура блока — среднее показаний прибора за то же
 * время.
 */
object GainDriftStudy {

    /** Опорная линия: K-40, 1460,8 кэВ. */
    private val ANCHOR = BackgroundLineInventory.LINES.first { it.nuclide == "K-40" }

    /**
     * Сколько секунд копится один блок — **инженерный параметр**. Три часа при
     * бытовом фоне дают в линии K-40 порядка тысячи импульсов: центроид тогда
     * определён точнее одного кэВ, то есть лучше 0,1 % шкалы, а ожидаемый ход
     * — доли процента на градус.
     */
    const val BLOCK_SECONDS = 3L * 3_600L

    /**
     * Максимальный разброс температуры внутри блока — **инженерный параметр**,
     * °C. Блок с большим разбросом описывает не одну температуру, а несколько,
     * и приписывать ему среднее значит смазывать искомый наклон.
     */
    const val MAX_BLOCK_SPREAD_C = 3.0

    /**
     * Измерить дрейф по истории.
     *
     * @param snapshots снимки, уже прорежённые до одного в час (их читает
     *   общий фоновый проход).
     * @return null, если материала не хватило: мало блоков с измеримой линией
     *   или температура в них почти не менялась.
     */
    suspend fun measure(graph: AppGraph, snapshots: List<RadonTrend.Snapshot>): GainDrift? {
        val intervals = CalibrationDataset.intervals(snapshots)
        if (intervals.isEmpty()) return null

        val connected = graph.serviceStatus.connection.value as? ConnectionState.Connected
        val resolution: ResolutionModel = ResolutionSource.active?.model()
            ?: SqrtResolution(
                (connected?.info?.model?.peakResolution662 ?: PeakDetection.RESOLUTION_662)
                    .toDouble(),
            )
        val candidate = CalibrationLineCandidate(
            line = ANCHOR,
            expectedFwhmKeV = resolution.fwhmKeV(ANCHOR.energyKeV),
            usable = true,
            rejection = null,
            blendBiasKeV = 0.0,
            neighbours = emptyList(),
            blockers = emptyList(),
        )

        val points = mutableListOf<GainDriftFit.Point>()
        for (block in blocks(intervals)) {
            val measured = LineMeasurement.measure(
                counts = block.counts,
                calibration = block.calibration,
                candidate = candidate,
                startResolution = resolution,
                sourceId = "drift",
            ) ?: continue
            val temperature = temperature(graph, block.fromMillis, block.toMillis) ?: continue
            points += GainDriftFit.Point(
                temperatureC = temperature,
                relative = measured.observedKeV / ANCHOR.energyKeV,
                sigma = measured.observedSigmaKeV / ANCHOR.energyKeV,
            )
        }
        return GainDriftFit.fit(points, ANCHOR.energyKeV)
    }

    /** Сложенный блок соседних интервалов: спектр и его временные границы. */
    private data class Block(
        val counts: List<Int>,
        val calibration: app.alpha.analysis.EnergyCalibration,
        val fromMillis: Long,
        val toMillis: Long,
    )

    /**
     * Соседние интервалы складываются, пока не наберётся [BLOCK_SECONDS].
     *
     * Складывается канальный счёт напрямую: интервалы идут подряд и сняты тем
     * же прибором с той же шкалой, а перекладка на общую сетку сделала бы
     * счёт дробным и перестала бы быть пуассоновской. Блок, где калибровка
     * успела измениться, отбрасывается — именно его сдвиг мы и измеряем, и
     * складывать разные шкалы нельзя.
     */
    private fun blocks(intervals: List<CalibrationDataset.Interval>): List<Block> {
        val result = mutableListOf<Block>()
        var counts: IntArray? = null
        var seconds = 0L
        var startMillis = 0L
        var endMillis = 0L
        var calibration: app.alpha.analysis.EnergyCalibration? = null
        for (interval in intervals.sortedBy { it.endMillis }) {
            val current = counts
            if (current == null || calibration != interval.calibration ||
                current.size != interval.counts.size
            ) {
                counts = interval.counts.toIntArray()
                calibration = interval.calibration
                seconds = interval.deltaSeconds
                startMillis = interval.endMillis - interval.deltaSeconds * 1_000L
                endMillis = interval.endMillis
            } else {
                for (channel in current.indices) current[channel] += interval.counts[channel]
                seconds += interval.deltaSeconds
                endMillis = interval.endMillis
            }
            if (seconds >= BLOCK_SECONDS) {
                result += Block(
                    counts = counts!!.toList(),
                    calibration = calibration!!,
                    fromMillis = startMillis,
                    toMillis = endMillis,
                )
                counts = null
                calibration = null
                seconds = 0L
            }
        }
        return result
    }

    /**
     * Средняя температура прибора за время блока; null — показаний нет или
     * температура внутри блока менялась слишком сильно, чтобы одно число её
     * описывало.
     */
    private suspend fun temperature(graph: AppGraph, fromMillis: Long, toMillis: Long): Double? {
        val values = graph.measurementRepository.rareData(fromMillis, toMillis)
            .map { it.temperature.toDouble() }
            .filter { it.isFinite() }
        if (values.isEmpty()) return null
        val spread = values.max() - values.min()
        if (spread > MAX_BLOCK_SPREAD_C) return null
        return values.average()
    }
}
