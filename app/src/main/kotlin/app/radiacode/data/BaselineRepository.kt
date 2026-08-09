package app.radiacode.data

import app.radiacode.baseline.BaselineBucket
import app.radiacode.baseline.BaselineComputer
import app.radiacode.baseline.BaselineConfig
import app.radiacode.baseline.BaselineState
import app.radiacode.data.db.SampleDao
import app.radiacode.device.DoseUnits

/**
 * Bridges Room to the pure baseline engine (docs/adr/002-baseline-method.md):
 * minute-bucketed samples of one place over the sliding window feed
 * [BaselineComputer]. Recomputed on demand — no cached statistics to migrate
 * or drift.
 */
class BaselineRepository(
    private val sampleDao: SampleDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun state(placeId: Long): BaselineState {
        val to = clock()
        val from = to - BaselineConfig.WINDOW_DAYS * 24L * 3600_000L
        val buckets = sampleDao.downsampledRangeForPlace(
            placeId = placeId,
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

    companion object {
        const val BUCKET_MILLIS = 60_000L
    }
}
