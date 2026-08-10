package app.radiacode.data

import app.radiacode.analysis.AlgorithmVersions
import app.radiacode.analysis.EnergyWindows
import app.radiacode.data.db.ExperimentDao
import app.radiacode.data.db.ExperimentEntity
import app.radiacode.data.db.ExperimentRunEntity
import app.radiacode.data.db.SampleEntity
import app.radiacode.device.DoseUnits
import app.radiacode.protocol.Spectrum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeExperimentDao : ExperimentDao {
    val experiments = mutableListOf<ExperimentEntity>()
    val runs = mutableListOf<ExperimentRunEntity>()

    override suspend fun insert(experiment: ExperimentEntity): Long {
        val id = experiments.size + 1L
        experiments += experiment.copy(id = id)
        return id
    }

    override suspend fun setNote(experimentId: Long, note: String) {
        replace(experimentId) { it.copy(note = note) }
    }

    override suspend fun setGeometry(experimentId: Long, geometry: String) {
        replace(experimentId) { it.copy(geometry = geometry) }
    }

    private fun replace(id: Long, transform: (ExperimentEntity) -> ExperimentEntity) {
        val index = experiments.indexOfFirst { it.id == id }
        if (index >= 0) experiments[index] = transform(experiments[index])
    }

    override suspend fun delete(experimentId: Long) {
        experiments.removeAll { it.id == experimentId }
        runs.removeAll { it.experimentId == experimentId }
    }

    override fun observeRecent(limit: Int): Flow<List<ExperimentEntity>> =
        flowOf(experiments.sortedByDescending { it.createdAt }.take(limit))

    override suspend fun byId(experimentId: Long): ExperimentEntity? =
        experiments.firstOrNull { it.id == experimentId }

    override suspend fun count(): Long = experiments.size.toLong()

    override suspend fun insertRun(run: ExperimentRunEntity): Long {
        val id = runs.size + 1L
        runs += run.copy(id = id)
        return id
    }

    override suspend fun updateRun(run: ExperimentRunEntity) {
        val index = runs.indexOfFirst { it.id == run.id }
        if (index >= 0) runs[index] = run
    }

    override suspend fun deleteRun(runId: Long) {
        runs.removeAll { it.id == runId }
    }

    override suspend fun runs(experimentId: Long): List<ExperimentRunEntity> =
        runs.filter { it.experimentId == experimentId }.sortedBy { it.startedAt }

    override fun observeRuns(experimentId: Long): Flow<List<ExperimentRunEntity>> =
        flowOf(runs.filter { it.experimentId == experimentId })

    override suspend fun run(runId: Long): ExperimentRunEntity? = runs.firstOrNull { it.id == runId }
}

/** Persistence of A/B experiments and their runs (spec §9, §22). */
class ExperimentRepositoryTest {

    private val experimentDao = FakeExperimentDao()
    private val sampleDao = FakeSampleDao()
    private val spectrumDao = FakeSpectrumDao()
    private var now = 1_000_000L
    private val repository = ExperimentRepository(
        experimentDao = experimentDao,
        sampleDao = sampleDao,
        spectrumDao = spectrumDao,
        clock = { now },
    )

    private fun sample(timestamp: Long, microSvH: Float) = SampleEntity(
        timestamp = timestamp,
        doseRate = microSvH / DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR,
        doseRateErr = 10f,
        countRate = 20f,
        countRateErr = 5f,
        flags = 0,
        realTimeFlags = 0,
    )

    @Test
    fun `a created experiment freezes the algorithm version and its parameters`() = runTest {
        val id = repository.create(
            kind = ExperimentEntity.KIND_BACKGROUND_VS_OBJECT,
            profileId = 3,
            geometry = "на столе, 5 см",
            note = "тарелка",
        )
        val experiment = assertNotNull(repository.byId(id))
        assertEquals(AlgorithmVersions.AB_ANALYSIS, experiment.algorithmVersion)
        assertEquals(1_000_000L, experiment.createdAt)
        assertEquals("на столе, 5 см", experiment.geometry)
        val params = JsonMap.decode(experiment.params)
        assertEquals(
            EnergyWindows.format(EnergyWindows.DEFAULTS),
            params["windowsKeV"],
        )
        assertEquals("25", params["normalApproxMinCounts"])
        assertTrue(params.containsKey("zChanged"))
        assertTrue(params.containsKey("zStrong"))
    }

    @Test
    fun `finishing a run stores the dose statistics of its own interval only`() = runTest {
        val experimentId = repository.create(
            kind = ExperimentEntity.KIND_BACKGROUND_VS_OBJECT,
            profileId = null,
            geometry = "g",
        )
        // Samples: three inside the run bracket, one before and one after.
        sampleDao.insertAll(
            listOf(
                sample(999_000L, 0.50f),
                sample(1_000_100L, 0.10f),
                sample(1_000_200L, 0.12f),
                sample(1_000_300L, 0.14f),
                sample(1_001_000L, 0.90f),
            ),
        )
        val runId = repository.startRun(
            experimentId = experimentId,
            label = "A",
            startedAt = 1_000_050L,
            startSpectrumId = 7L,
        )
        val finished = assertNotNull(repository.finishRun(runId, endedAt = 1_000_400L, spectrumId = 9L))

        assertEquals(1_000_400L, finished.endedAt)
        assertEquals(9L, finished.spectrumId)
        val stats = assertNotNull(DoseStatsCodec.decode(finished.doseStats))
        assertEquals(3, stats.sampleCount)
        assertEquals(0.12, stats.meanMicroSvH, 1e-3)
        assertEquals(0.10, stats.minMicroSvH, 1e-3)
        assertEquals(0.14, stats.maxMicroSvH, 1e-3)
        // Provenance of the interval spectrum survives finishing.
        assertEquals(7L, DoseStatsCodec.startSpectrumId(finished.doseStats))
    }

    @Test
    fun `a run without samples finishes without inventing statistics`() = runTest {
        val experimentId = repository.create(
            kind = ExperimentEntity.KIND_PLACE_VS_PLACE,
            profileId = null,
            geometry = "g",
        )
        val runId = repository.startRun(experimentId, "A", startedAt = 5_000L)
        val finished = assertNotNull(repository.finishRun(runId, endedAt = 6_000L))
        assertNull(DoseStatsCodec.decode(finished.doseStats))
        assertNull(finished.spectrumId)
    }

    @Test
    fun `run data takes the live time from the run spectrum, not the wall clock`() = runTest {
        val experimentId = repository.create(
            kind = ExperimentEntity.KIND_BACKGROUND_VS_OBJECT,
            profileId = null,
            geometry = "g",
        )
        // Interval spectrum of 280 s inside a 300 s wall bracket.
        val spectrum = Spectrum(280, -5.5f, 2.4f, 4.0E-4f, List(1024) { 2 })
        spectrumDao.insert(spectrum.toEntity(timestamp = 1_000_400L, accumulated = false))
        val runId = repository.startRun(experimentId, "A", startedAt = 1_000_000L)
        repository.finishRun(runId, endedAt = 1_000_300L, spectrumId = 1L)

        val data = repository.runData(repository.runs(experimentId)).single()
        assertEquals("A", data.label)
        assertEquals(280L, data.durationSeconds)
        assertEquals(2048L, data.totalCounts)
        assertTrue(data.hasSpectrum)
        assertNotNull(data.calibration)
    }

    @Test
    fun `run data falls back to the wall bracket when no spectrum was captured`() = runTest {
        val experimentId = repository.create(
            kind = ExperimentEntity.KIND_BACKGROUND_VS_OBJECT,
            profileId = null,
            geometry = "g",
        )
        val runId = repository.startRun(experimentId, "A", startedAt = 1_000_000L)
        repository.finishRun(runId, endedAt = 1_000_000L + 120_000L)

        val data = repository.runData(repository.runs(experimentId)).single()
        assertEquals(120L, data.durationSeconds)
        assertTrue(!data.hasSpectrum)
    }

    @Test
    fun `deleting an experiment removes its runs`() = runTest {
        val experimentId = repository.create(
            kind = ExperimentEntity.KIND_DISTANCE,
            profileId = null,
            geometry = "g",
        )
        repository.startRun(experimentId, "A", distanceCm = 10f)
        repository.startRun(experimentId, "B", distanceCm = 20f)
        assertEquals(2, repository.runs(experimentId).size)

        repository.delete(experimentId)
        assertNull(repository.byId(experimentId))
        assertTrue(repository.runs(experimentId).isEmpty())
    }
}
