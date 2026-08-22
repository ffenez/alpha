package app.alpha.data

import androidx.compose.runtime.Immutable
import app.alpha.AppGraph
import app.alpha.analysis.CalibrationDataset
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.PeakDetection
import app.alpha.analysis.RadonTrend
import app.alpha.analysis.evidence.AcceptedResolution
import app.alpha.analysis.evidence.BackgroundCalibration
import app.alpha.analysis.evidence.CalibrationAccumulation
import app.alpha.analysis.evidence.CalibrationReport
import app.alpha.analysis.evidence.SqrtResolution
import app.alpha.analysis.evidence.ResolutionAdoption
import app.alpha.device.ConnectionState
import kotlinx.coroutines.flow.first

/**
 * Материал для диагностики прибора и разбор по нему — БЕЗ участия человека.
 *
 * Живёт вне экрана намеренно: то же самое считает фоновая задача службы
 * ([app.alpha.service.MeasurementService]), чтобы измеренная модель разрешения
 * появлялась сама, а экран калибровки открывался на готовое, а не заставлял
 * ждать разбор.
 */

/** Собранный материал и разбор движка по нему. */
@Immutable
data class CalibrationModel(
    val selection: CalibrationDataset.Selection,
    val report: CalibrationReport,
    /** Разрешение прибора, от которого стартовал поиск линий. */
    val startResolution662: Float,
    /**
     * Опубликовал ли вендор разрешение ЭТОГО прибора. Нет — стартовое число
     * консервативная оценка серии, и называть её вендорской нельзя.
     */
    val resolutionPublished: Boolean,
    /**
     * Считает ли прибор спектр по каналам. У пластикового сцинтиллятора
     * (Zero) фотопиков нет, значит опорных линий не будет никогда, и обещать
     * «материал соберётся сам» ему нельзя.
     */
    val spectrometer: Boolean,
    val deviceSerial: String?,
)

/**
 * Сколько истории читается под диагностику — **инженерный параметр экрана**.
 * Тридцать суток при одном снимке в час это 720 строк: столько же, сколько
 * читает месячное окно графика (ADR 004), то есть заведомо посильный объём.
 * Длиннее не нужно — калибровка прибора за месяцы меняется, и сумма за
 * полгода описывала бы уже не тот прибор, что сейчас в руках.
 */
internal const val WINDOW_DAYS = 30

/**
 * Читает уже накопленные снимки, складывает из них два накопления и отдаёт
 * разбор. Ничего не пишет.
 */
/**
 * Снимки прибора за окно, прорежённые до одного в час.
 *
 * Прореживание не теряет ни импульса: разность последних снимков соседних
 * часов покрывает час целиком, а чтение блобов экономит.
 */
internal suspend fun hourlySnapshots(
    graph: AppGraph,
    fromMillis: Long,
    toMillis: Long,
): List<RadonTrend.Snapshot> {
    val metas = graph.measurementRepository
        .deviceSnapshotMeta(fromMillis - RadonTrend.HOUR_MILLIS, toMillis)
        .map { RadonTrend.Meta(it.id, it.timestamp, it.durationSeconds) }
    return RadonTrend.selectHourlyIds(metas).mapNotNull { id ->
        graph.measurementRepository.spectrumById(id)?.let { entity ->
            val s = entity.toSpectrum()
            RadonTrend.Snapshot(
                timestampMillis = entity.timestamp,
                durationSeconds = s.durationSeconds,
                counts = s.counts,
                calibration = EnergyCalibration(s.a0, s.a1, s.a2),
            )
        }
    }
}

suspend fun loadCalibration(
    graph: AppGraph,
    prepared: List<RadonTrend.Snapshot>? = null,
): CalibrationModel {
    val now = System.currentTimeMillis()
    val from = now - WINDOW_DAYS * 24L * RadonTrend.HOUR_MILLIS
    val snapshots = prepared ?: hourlySnapshots(graph, from, now)
    val selection = CalibrationDataset.select(CalibrationDataset.intervals(snapshots))
    val connected = graph.serviceStatus.connection.value as? ConnectionState.Connected
    val model = connected?.info?.model
    val resolution662 = model?.peakResolution662
        ?: PeakDetection.RESOLUTION_662
    val accumulations = buildList {
        selection.long?.let { add(it.toEngine(CalibrationDataset.SOURCE_LONG)) }
        selection.radonRich?.let { add(it.toEngine(CalibrationDataset.SOURCE_RADON)) }
    }
    return CalibrationModel(
        selection = selection,
        report = BackgroundCalibration.analyse(
            accumulations = accumulations,
            // Стартовая модель задаёт только РАЗМЕР окон поиска; измеренные
            // ширины от неё не зависят, иначе подгонка была бы тавтологией.
            startResolution = SqrtResolution(resolution662.toDouble()),
        ),
        startResolution662 = resolution662,
        resolutionPublished = model?.resolution662 != null,
        // Прибор не подключён — модель неизвестна, и запрещать проверку по
        // догадке нельзя: спектрометром считается всё, кроме опознанного Zero.
        spectrometer = model?.isSpectrometer ?: true,
        deviceSerial = connected?.info?.serialNumber,
    )
}


internal fun CalibrationDataset.Accumulation.toEngine(id: String) = CalibrationAccumulation(
    id = id,
    counts = counts,
    calibration = calibration,
    seconds = seconds,
    intervalCount = intervalCount,
    hoursCovered = hoursCovered,
    fromMillis = fromMillis,
    toMillis = toMillis,
)


/**
 * Фоновый разбор: собрать материал, подогнать модель разрешения и принять её,
 * если она проходит собственные проверки.
 *
 * Возвращает `true`, если принятая модель изменилась. Ничего не спрашивает и
 * ничего не показывает: человек видит результат на экране калибровки, где
 * сказано, что модель снята автоматически, и где её можно снять.
 */
suspend fun studyInstrument(graph: AppGraph, nowMillis: Long): Boolean {
    // Снимки читаются ОДИН раз на весь проход: это сотни блобов, и второе
    // чтение ради того же материала было бы чистой тратой.
    val snapshots = hourlySnapshots(
        graph = graph,
        fromMillis = nowMillis - WINDOW_DAYS * 24L * RadonTrend.HOUR_MILLIS,
        toMillis = nowMillis,
    )
    val model = loadCalibration(graph, snapshots)
    refreshAutoBackground(graph, model, nowMillis)
    measureDrift(graph, model.deviceSerial, snapshots, nowMillis)
    val stored = AcceptedResolution.decode(graph.settings.measuredResolutionRaw.first())
    val next = ResolutionAdoption.decide(
        fit = model.report.fit,
        serial = model.deviceSerial,
        stored = stored,
        nowMillis = nowMillis,
        algorithmVersion = BackgroundCalibration.ALGORITHM_VERSION,
    ) ?: return false
    graph.settings.setMeasuredResolutionRaw(next.encode())
    return true
}

/**
 * Собственный фон прибора в библиотеке шаблонов — тем же материалом и тем же
 * фоновым проходом.
 *
 * Без единого шаблона разложение показывать нечего, а фон есть у любого
 * прибора и уже накоплен снимками. Человек, удаливший эту запись, получает
 * отказ навсегда ([AppSettings.autoBackgroundOff]): возвращаться она не имеет
 * права.
 */
suspend fun refreshAutoBackground(graph: AppGraph, model: CalibrationModel, nowMillis: Long): Boolean {
    if (graph.settings.autoBackgroundOff.first()) return false
    val long = model.selection.long ?: return false
    return graph.templateRepository.refreshAutoBackground(
        counts = long.counts,
        calibration = long.calibration,
        seconds = long.seconds,
        resolution662 = model.startResolution662,
        deviceSerial = model.deviceSerial,
        deviceName = null,
        atMillis = nowMillis,
    )
}

/**
 * Температурный ход шкалы — тем же проходом и тем же материалом.
 *
 * Результат только СОХРАНЯЕТСЯ и показывается: подставлять его в показанные
 * энергии нельзя по той же причине, по которой поправка шкалы включается
 * человеком — иначе подпись зависела бы от температуры в момент измерения.
 */
internal suspend fun measureDrift(
    graph: AppGraph,
    serial: String?,
    snapshots: List<RadonTrend.Snapshot>,
    nowMillis: Long,
): Boolean {
    val drift = GainDriftStudy.measure(graph, snapshots) ?: return false
    graph.settings.setGainDriftRaw(
        GainDriftRecord(drift = drift, deviceSerial = serial, measuredAtMillis = nowMillis)
            .encode(),
    )
    return true
}
