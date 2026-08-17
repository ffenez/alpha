package app.alpha.data

import app.alpha.analysis.AbAnalysis
import app.alpha.analysis.FoodScreening
import app.alpha.analysis.PeakDetection
import app.alpha.analysis.AbExperiment
import app.alpha.analysis.AlgorithmVersions
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindowSpec
import app.alpha.analysis.EnergyWindows
import app.alpha.data.db.ExperimentDao
import app.alpha.data.db.ExperimentEntity
import app.alpha.data.db.ExperimentRunEntity
import app.alpha.data.db.SampleDao
import app.alpha.data.export.ProcessingMetadata
import app.alpha.analysis.SpectrumCompare
import app.alpha.protocol.Spectrum
import app.alpha.data.db.SpectrumDao
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.device.DoseUnits
import kotlinx.coroutines.flow.Flow

/**
 * Dose-rate statistics of a run ↔ the `experiment_runs.doseStats` column.
 * Keys are a **disk contract**: they may be added to, never renamed.
 */
object DoseStatsCodec {

    const val KEY_SAMPLES = "n"
    const val KEY_MEAN = "meanMicroSvH"
    const val KEY_SD = "sdMicroSvH"
    const val KEY_MIN = "minMicroSvH"
    const val KEY_MAX = "maxMicroSvH"

    /**
     * Snapshot taken when the run started. The run's own spectrum is the
     * *difference* between the snapshot at the end and this one, so the id is
     * kept as provenance (spec §22) — and it is what lets a run finish after
     * the app was killed mid-recording.
     */
    const val KEY_START_SPECTRUM = "startSpectrumId"

    fun encode(stats: AbAnalysis.DoseStats?, extra: Map<String, String> = emptyMap()): String {
        val values = LinkedHashMap<String, String>()
        if (stats != null) {
            values[KEY_SAMPLES] = stats.sampleCount.toString()
            JsonMap.format(stats.meanMicroSvH)?.let { values[KEY_MEAN] = it }
            JsonMap.format(stats.sdMicroSvH)?.let { values[KEY_SD] = it }
            JsonMap.format(stats.minMicroSvH)?.let { values[KEY_MIN] = it }
            JsonMap.format(stats.maxMicroSvH)?.let { values[KEY_MAX] = it }
        }
        values.putAll(extra)
        return if (values.isEmpty()) "" else JsonMap.encode(values)
    }

    fun startSpectrumId(raw: String?): Long? =
        JsonMap.decode(raw)[KEY_START_SPECTRUM]?.toLongOrNull()

    fun decode(raw: String?): AbAnalysis.DoseStats? {
        val map = JsonMap.decode(raw)
        val n = map[KEY_SAMPLES]?.toIntOrNull() ?: return null
        val mean = map[KEY_MEAN]?.toDoubleOrNull() ?: return null
        return AbAnalysis.DoseStats(
            sampleCount = n,
            meanMicroSvH = mean,
            sdMicroSvH = map[KEY_SD]?.toDoubleOrNull() ?: 0.0,
            minMicroSvH = map[KEY_MIN]?.toDoubleOrNull() ?: mean,
            maxMicroSvH = map[KEY_MAX]?.toDoubleOrNull() ?: mean,
        )
    }
}

/**
 * Persistence for A/B research experiments (spec §9, §16, §22).
 *
 * Every stored conclusion carries the version of the math that produced it and
 * the parameters it used: [ExperimentEntity.algorithmVersion] and
 * [ExperimentEntity.params] are written once at creation and never rewritten,
 * so re-analysing old raw data later stays an honest comparison instead of a
 * silent overwrite.
 */
class ExperimentRepository(
    private val experimentDao: ExperimentDao,
    private val sampleDao: SampleDao,
    private val spectrumDao: SpectrumDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Analysis parameters frozen into the experiment row (spec §22). */
    fun paramsJson(windowSpecs: List<EnergyWindowSpec>): String = JsonMap.of(
        "windowsKeV" to EnergyWindows.format(windowSpecs),
        "normalApproxMinCounts" to AbAnalysis.NORMAL_APPROX_MIN_COUNTS.toInt(),
        "zChanged" to AbAnalysis.Z_CHANGED,
        "zStrong" to AbAnalysis.Z_STRONG,
        "energyWindowsVersion" to AlgorithmVersions.ENERGY_WINDOWS,
        "spectrumCompareVersion" to AlgorithmVersions.SPECTRUM_COMPARE,
    )

    suspend fun create(
        kind: String,
        profileId: Long?,
        geometry: String,
        note: String = "",
        distanceCm: Int? = null,
        placement: String = "",
        orientation: String = "",
        plannedSeconds: Long = 0,
        windowSpecs: List<EnergyWindowSpec> = EnergyWindows.DEFAULTS,
        photoUri: String? = null,
    ): Long = experimentDao.insert(
        ExperimentEntity(
            kind = kind,
            profileId = profileId,
            createdAt = clock(),
            note = note,
            geometry = geometry,
            distanceCm = distanceCm,
            placement = placement,
            orientation = orientation,
            plannedSeconds = plannedSeconds,
            algorithmVersion = AlgorithmVersions.AB_ANALYSIS,
            params = paramsJson(windowSpecs),
            photoUri = photoUri,
        ),
    )

    fun recent(limit: Int = 50): Flow<List<ExperimentEntity>> = experimentDao.observeRecent(limit)

    suspend fun byId(experimentId: Long): ExperimentEntity? = experimentDao.byId(experimentId)

    /** Измерения продуктов — тот же журнал опытов, отфильтрованный по виду. */
    fun foodMeasurements(limit: Int = 50): Flow<List<ExperimentEntity>> =
        experimentDao.observeByKind(ExperimentEntity.KIND_FOOD, limit)

    /**
     * Итог скрининга продукта.
     *
     * Считается ЗДЕСЬ, а не на экране: тот же результат нужен и строке
     * журнала, и экрану измерения, и два вычисления одного вывода рано или
     * поздно разошлись бы. Ничего не кэшируется — источником истины остаются
     * сами прогоны.
     */
    suspend fun foodResult(experimentId: Long): FoodScreening.Result? {
        val runs = runs(experimentId)
        val backgroundRun = runs.firstOrNull { it.label == FOOD_LABEL_BACKGROUND }
        val sampleRun = runs.firstOrNull { it.label == FOOD_LABEL_SAMPLE }
        val background = backgroundRun?.spectrumId?.let { spectrum(it) }?.toSpectrum()
        val sample = sampleRun?.spectrumId?.let { spectrum(it) }?.toSpectrum()
        if (background == null || sample == null) return null

        val backgroundCounting = AbAnalysis.Counting(
            counts = background.counts.sumOf { it.toDouble() },
            seconds = background.durationSeconds.toDouble(),
        )
        val sampleCounting = AbAnalysis.Counting(
            counts = sample.counts.sumOf { it.toDouble() },
            seconds = sample.durationSeconds.toDouble(),
        )
        // Линии ищутся в РАЗНОСТИ образца и приведённого по времени фона.
        // Отрицательные каналы разности — шум вычитания, и в поиск пиков они
        // идут нулями: пика из отрицательной площади не бывает.
        val lines = if (
            background.counts.size == sample.counts.size && backgroundCounting.seconds > 0.0
        ) {
            val ratio = sampleCounting.seconds / backgroundCounting.seconds
            val net = sample.counts.mapIndexed { index, value ->
                (value - background.counts[index] * ratio).toInt().coerceAtLeast(0)
            }
            PeakDetection.detect(
                counts = net,
                calibration = EnergyCalibration(sample.a0, sample.a1, sample.a2),
            ).map { FoodScreening.Line(it.energyKeV, it.significance.toDouble()) }
        } else {
            emptyList()
        }
        return FoodScreening.screen(backgroundCounting, sampleCounting, lines)
    }

    suspend fun count(): Long = experimentDao.count()

    fun observeRuns(experimentId: Long): Flow<List<ExperimentRunEntity>> =
        experimentDao.observeRuns(experimentId)

    suspend fun runs(experimentId: Long): List<ExperimentRunEntity> =
        experimentDao.runs(experimentId)

    suspend fun setGeometry(experimentId: Long, geometry: String) =
        experimentDao.setGeometry(experimentId, geometry)

    suspend fun setNote(experimentId: Long, note: String) = experimentDao.setNote(experimentId, note)

    suspend fun delete(experimentId: Long) = experimentDao.delete(experimentId)

    suspend fun deleteRun(runId: Long) = experimentDao.deleteRun(runId)

    /**
     * Opens a run; [endedAt] stays null until [finishRun]. [startSpectrumId]
     * is the snapshot taken at the start — the run's own spectrum is the
     * difference against it, and storing the id survives an app restart.
     */
    suspend fun startRun(
        experimentId: Long,
        label: String,
        startedAt: Long = clock(),
        startSpectrumId: Long? = null,
        distanceCm: Float? = null,
        shieldingNote: String? = null,
    ): Long = experimentDao.insertRun(
        ExperimentRunEntity(
            experimentId = experimentId,
            label = label,
            startedAt = startedAt,
            doseStats = DoseStatsCodec.encode(
                stats = null,
                extra = startSpectrumId
                    ?.let { mapOf(DoseStatsCodec.KEY_START_SPECTRUM to it.toString()) }
                    ?: emptyMap(),
            ),
            distanceCm = distanceCm,
            shieldingNote = shieldingNote,
        ),
    )

    suspend fun run(runId: Long): ExperimentRunEntity? = experimentDao.run(runId)

    /**
     * Closes a run with its own interval spectrum and the dose statistics of
     * the samples recorded inside the bracket. The start-snapshot provenance
     * is preserved.
     */
    suspend fun finishRun(
        runId: Long,
        endedAt: Long = clock(),
        spectrumId: Long? = null,
    ): ExperimentRunEntity? {
        val run = experimentDao.run(runId) ?: return null
        val stats = doseStats(run.startedAt, endedAt)
        val startSpectrumId = DoseStatsCodec.startSpectrumId(run.doseStats)
        val updated = run.copy(
            endedAt = endedAt,
            spectrumId = spectrumId,
            doseStats = DoseStatsCodec.encode(
                stats = stats,
                extra = startSpectrumId
                    ?.let { mapOf(DoseStatsCodec.KEY_START_SPECTRUM to it.toString()) }
                    ?: emptyMap(),
            ),
        )
        experimentDao.updateRun(updated)
        return updated
    }

    /**
     * Dose-rate statistics over a time bracket, µSv/h. Raw device values are
     * converted through [DoseUnits] — the app never invents its own CPS→dose
     * factor (spec §1, §23).
     */
    suspend fun doseStats(from: Long, to: Long): AbAnalysis.DoseStats? {
        if (to <= from) return null
        val readings = sampleDao.rangeList(from, to)
            .map { DoseUnits.rawToMicroSievertPerHour(it.doseRate).toDouble() }
        return AbAnalysis.doseStats(readings)
    }

    /** Loads runs into the pure analysis model (spectra included). */
    suspend fun runData(runs: List<ExperimentRunEntity>): List<AbExperiment.RunData> =
        runs.map { run ->
            val snapshot = run.spectrumId?.let { spectrumDao.byId(it) }
            AbExperiment.RunData(
                id = run.id,
                label = run.label,
                startedAt = run.startedAt,
                endedAt = run.endedAt,
                durationSeconds = snapshot?.durationSeconds
                    ?: (((run.endedAt ?: run.startedAt) - run.startedAt) / 1000L),
                counts = snapshot?.let { SpectrumBlob.decode(it.counts) },
                calibration = snapshot?.let { EnergyCalibration(it.a0, it.a1, it.a2) },
                doseStats = DoseStatsCodec.decode(run.doseStats),
                distanceCm = run.distanceCm,
                shieldingNote = run.shieldingNote,
            )
        }

    suspend fun spectrum(spectrumId: Long): SpectrumSnapshotEntity? = spectrumDao.byId(spectrumId)

    /**
     * Спектр прогона — разность накоплений его конца и начала.
     *
     * Жил в экране A/B и вместе с ним умирал при переходе на другую вкладку;
     * теперь его снимает владелец прогона ([app.alpha.service.AbRunRecorder]),
     * поэтому прогон заканчивается одинаково независимо от того, смотрит ли
     * кто-нибудь на экран. Сохранение делегируется наружу: репозиторий
     * экспериментов не должен знать про то, как хранятся спектры.
     */
    suspend fun captureIntervalSpectrum(
        runId: Long,
        liveSpectrum: Spectrum?,
        nowMillis: Long,
        saveSpectrum: suspend (spectrum: Spectrum, label: String, analysisMeta: String) -> Long,
    ): Long? {
        val run = run(runId) ?: return null
        val startId = DoseStatsCodec.startSpectrumId(run.doseStats) ?: return null
        val start = spectrum(startId) ?: return null
        if (liveSpectrum == null || liveSpectrum.counts.isEmpty()) return null

        val outcome = SpectrumCompare.extractInterval(
            first = SpectrumCompare.Input(
                counts = SpectrumBlob.decode(start.counts),
                durationSeconds = start.durationSeconds,
                calibration = EnergyCalibration(start.a0, start.a1, start.a2),
                timestampMillis = start.timestamp,
            ),
            second = SpectrumCompare.Input(
                counts = liveSpectrum.counts,
                durationSeconds = liveSpectrum.durationSeconds,
                calibration = EnergyCalibration(
                    liveSpectrum.a0,
                    liveSpectrum.a1,
                    liveSpectrum.a2,
                ),
                timestampMillis = nowMillis,
            ),
        )
        val ok = outcome as? SpectrumCompare.IntervalOutcome.Ok ?: return null
        return saveSpectrum(
            Spectrum(
                durationSeconds = ok.durationSeconds,
                a0 = ok.calibration.a0,
                a1 = ok.calibration.a1,
                a2 = ok.calibration.a2,
                counts = ok.counts,
            ),
            "A/B ${run.label} · интервал",
            ProcessingMetadata.stamp(
                method = "interval_subtraction (A/B run)",
                algorithms = listOf("spectrum_compare", "ab_analysis"),
                extra = mapOf(
                    "experimentId" to run.experimentId.toString(),
                    "run" to run.label,
                    "startSpectrumId" to startId.toString(),
                    "intervalSeconds" to ok.durationSeconds.toString(),
                ),
            ),
        )
    }
}

/**
 * Метки прогонов измерения продукта.
 *
 * Живут рядом с репозиторием, а не на экране: по ним ищутся прогоны при
 * подсчёте итога, и разъехаться эти две стороны не имеют права.
 */
const val FOOD_LABEL_BACKGROUND = "Фон"
const val FOOD_LABEL_SAMPLE = "Продукт"
