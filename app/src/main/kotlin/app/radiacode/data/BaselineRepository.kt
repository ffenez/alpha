package app.radiacode.data

import app.radiacode.baseline.BaselineBucket
import app.radiacode.baseline.BaselineComputer
import app.radiacode.baseline.BaselineConfig
import app.radiacode.baseline.BaselineExclusion
import app.radiacode.baseline.BaselineState
import app.radiacode.data.db.SampleDao
import app.radiacode.device.DoseUnits

/** One exclusion reason with how much measurement time it cost, seconds. */
data class ExclusionSummary(val reason: BaselineExclusion, val seconds: Long)

/**
 * Bridges Room to the pure baseline engine (docs/adr/002-baseline-method.md):
 * minute-bucketed **admitted** samples of one profile over the sliding window
 * feed [BaselineComputer]. Recomputed on demand — no cached statistics to
 * migrate or drift.
 *
 * Excluded samples are never deleted: they stay in `samples` with their reason
 * and are reported separately by [exclusions], which is what the «Почему?»
 * sheet shows (spec §17).
 */
class BaselineRepository(
    private val sampleDao: SampleDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun state(profileId: Long): BaselineState {
        val to = clock()
        val from = to - windowMillis()
        val buckets = sampleDao.downsampledRangeForProfile(
            profileId = profileId,
            from = from,
            to = to,
            bucketMillis = BUCKET_MILLIS,
        )
        return BaselineComputer.compute(
            buckets.map {
                BaselineBucket(
                    avgDoseRateMicroSvH = DoseUnits.rawToMicroSievertPerHour(it.avgDoseRate),
                    avgCps = it.avgCountRate,
                    sampleCount = it.sampleCount,
                )
            },
        )
    }

    /**
     * What the admission pipeline kept out of this profile's window, biggest
     * first. Sample counts are reported as seconds — the device records at
     * 1 Hz, so one sample is one second of measurement.
     */
    suspend fun exclusions(profileId: Long): List<ExclusionSummary> {
        val to = clock()
        val from = to - windowMillis()
        return sampleDao.exclusionCountsForProfile(profileId, from, to).mapNotNull { row ->
            BaselineExclusion.fromStorage(row.reason)?.let {
                ExclusionSummary(it, row.samples.toLong())
            }
        }
    }

    private fun windowMillis(): Long = BaselineConfig.WINDOW_DAYS * 24L * 3600_000L

    companion object {
        const val BUCKET_MILLIS = 60_000L
    }
}
