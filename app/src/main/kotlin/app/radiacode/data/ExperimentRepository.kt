package app.radiacode.data

import app.radiacode.analysis.AbAnalysis
import app.radiacode.analysis.AbExperiment
import app.radiacode.analysis.AlgorithmVersions
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.EnergyWindowSpec
import app.radiacode.analysis.EnergyWindows
import app.radiacode.data.db.ExperimentDao
import app.radiacode.data.db.ExperimentEntity
import app.radiacode.data.db.ExperimentRunEntity
import app.radiacode.data.db.SampleDao
import app.radiacode.data.db.SpectrumDao
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.device.DoseUnits
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
        windowSpecs: List<EnergyWindowSpec> = EnergyWindows.DEFAULTS,
    ): Long = experimentDao.insert(
        ExperimentEntity(
            kind = kind,
            profileId = profileId,
            createdAt = clock(),
            note = note,
            geometry = geometry,
            algorithmVersion = AlgorithmVersions.AB_ANALYSIS,
            params = paramsJson(windowSpecs),
        ),
    )

    fun recent(limit: Int = 50): Flow<List<ExperimentEntity>> = experimentDao.observeRecent(limit)

    suspend fun byId(experimentId: Long): ExperimentEntity? = experimentDao.byId(experimentId)

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
}
