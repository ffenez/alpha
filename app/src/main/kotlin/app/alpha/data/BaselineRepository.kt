package app.alpha.data

import app.alpha.baseline.BaselineBucket
import app.alpha.baseline.BaselineComputer
import app.alpha.baseline.BaselineConfig
import app.alpha.baseline.BaselineExclusion
import app.alpha.baseline.BaselineState
import app.alpha.data.db.BaselineEpochEntity
import app.alpha.data.db.ProfileDao
import app.alpha.data.db.SampleDao
import app.alpha.device.DoseUnits

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
    private val profileDao: ProfileDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * @param deviceSerial чей прибор описывает «обычно здесь»; null — все.
     *
     * Разделение по прибору не косметика: чувствительность моделей отличается
     * в два с половиной раза, и фон места, снятый одним прибором, для другого
     * означал бы другой уровень — а на этом уровне стоят пороги тревог.
     */
    suspend fun state(profileId: Long, deviceSerial: String? = null): BaselineState {
        val to = clock()
        val from = windowStart(profileId, to)
        val buckets = sampleDao.downsampledRangeForProfile(
            profileId = profileId,
            from = from,
            to = to,
            bucketMillis = BUCKET_MILLIS,
            deviceSerial = deviceSerial,
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
    suspend fun exclusions(profileId: Long, deviceSerial: String? = null): List<ExclusionSummary> {
        val to = clock()
        val from = windowStart(profileId, to)
        return sampleDao.exclusionCountsForProfile(profileId, from, to, deviceSerial).mapNotNull { row ->
            BaselineExclusion.fromStorage(row.reason)?.let {
                ExclusionSummary(it, row.samples.toLong())
            }
        }
    }

    /**
     * Start of the window the statistics may read: the sliding window, cut at
     * the profile's baseline epoch if the user started a new period
     * (why-spec §7). The samples before it stay in the database untouched —
     * they simply stop describing «обычно здесь».
     */
    private suspend fun windowStart(profileId: Long, nowMillis: Long): Long {
        val sliding = nowMillis - windowMillis()
        val epoch = profileDao.byId(profileId)?.baselineEpochMillis ?: return sliding
        return maxOf(sliding, epoch)
    }

    /**
     * Starts a new baseline period for a profile, keeping the old one.
     *
     * Called **only** from the user's explicit «Обновить профиль» (why-spec
     * §7): a long deviation may never become the new usual by itself, because
     * then a source that stays put would quietly redefine the place it is in.
     */
    suspend fun startNewPeriod(profileId: Long, stats: String) {
        val now = clock()
        val previous = profileDao.byId(profileId)?.baselineEpochMillis
        profileDao.insertEpoch(
            BaselineEpochEntity(
                profileId = profileId,
                startedAtMillis = previous ?: (now - windowMillis()),
                endedAtMillis = now,
                stats = stats,
                reason = BaselineEpochEntity.REASON_USER_SHIFT,
                createdAt = now,
            ),
        )
        profileDao.setBaselineEpoch(profileId, now)
    }

    /** «Оставить как есть» — remembered so the offer stops coming back. */
    suspend fun declineShift(profileId: Long) {
        profileDao.setShiftDeclined(profileId, clock())
    }

    /**
     * Есть ли у места измерения ДРУГИХ приборов в окне.
     *
     * Отвечает на вопрос экрана: «здесь ещё не измеряли» или «здесь измерял
     * другой прибор, и его фон этому прибору не подходит».
     */
    suspend fun measuredByOtherDevice(profileId: Long, deviceSerial: String): Boolean {
        val to = clock()
        val from = windowStart(profileId, to)
        return sampleDao.otherDeviceSamples(profileId, from, to, deviceSerial) > 0L
    }

    private fun windowMillis(): Long = BaselineConfig.WINDOW_DAYS * 24L * 3600_000L

    companion object {
        const val BUCKET_MILLIS = 60_000L
    }
}
