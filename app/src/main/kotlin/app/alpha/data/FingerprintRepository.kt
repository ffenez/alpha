package app.alpha.data

import app.alpha.analysis.AlgorithmVersions
import app.alpha.analysis.Fingerprint
import app.alpha.analysis.FingerprintReference
import app.alpha.analysis.FingerprintWindow
import app.alpha.ui.text.FingerprintRu
import app.alpha.ui.text.FingerprintStrings
import app.alpha.baseline.Baseline
import app.alpha.baseline.BaselineConfig
import app.alpha.baseline.BaselineState
import app.alpha.data.db.ProfileDao
import app.alpha.data.db.ProfileFingerprintEntity
import app.alpha.data.db.SampleDao
import app.alpha.data.db.SpectrumDao
import app.alpha.device.DoseUnits

/** Накопленный спектр интервала: отсчёты, экспозиция и калибровка. */
data class AccumulatedSpectrum(
    val counts: List<Int>,
    val seconds: Long,
    val a0: Float,
    val a1: Float,
    val a2: Float,
) {
    val totalCounts: Long get() = counts.sumOf { it.toLong() }
}

/**
 * Эталон места (ADR 005): его создание, чтение и сборка того, с чем он
 * сравнивается.
 *
 * Ключевое правило здесь одно: и в эталон, и в текущее окно попадают **только
 * допущенные** измерения (пайплайн допуска §4.2). Иначе отклонение впиталось
 * бы в то, с чем его же и сравнивают, — ровно та ошибка, от которой защищают
 * карантин и заморозка обучения.
 */
class FingerprintRepository(
    private val profileDao: ProfileDao,
    private val sampleDao: SampleDao,
    private val spectrumDao: SpectrumDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Действующий эталон профиля как строка БД (для показа «создан …»). */
    suspend fun entity(profileId: Long): ProfileFingerprintEntity? =
        profileDao.newestFingerprint(profileId)

    /** Он же в виде, который сравнивает [Fingerprint]. */
    suspend fun reference(profileId: Long): FingerprintReference? =
        entity(profileId)?.toReference()

    /**
     * Текущее окно профиля: медианы дозы и счёта плюс спектр, накопленный за
     * то же время. Null, когда допущенных измерений в окне нет вовсе.
     */
    suspend fun window(profileId: Long, windowMillis: Long = DEFAULT_WINDOW_MILLIS): FingerprintWindow? {
        val to = clock()
        val from = to - windowMillis
        val buckets = sampleDao.downsampledRangeForProfile(
            profileId = profileId,
            from = from,
            to = to,
            bucketMillis = BUCKET_MILLIS,
        )
        if (buckets.isEmpty()) return null
        val admittedSeconds = sampleDao.admittedCountForProfile(profileId, from, to).toLong()
        if (admittedSeconds <= 0L) return null

        val doses = buckets.map { DoseUnits.rawToMicroSievertPerHour(it.avgDoseRate) }.sorted()
        val rates = buckets.map { it.avgCountRate }.sorted()
        val spectrum = accumulate(profileId, from, to, thinHourly = false)
        return FingerprintWindow(
            doseMedianMicroSvH = median(doses),
            cpsMedian = median(rates),
            spectrum = spectrum?.counts.orEmpty(),
            spectrumSeconds = spectrum?.seconds ?: 0L,
            seconds = admittedSeconds,
        )
    }

    /**
     * Готов ли профиль к автоматическому созданию эталона: достаточно
     * допущенного времени И достаточно накопленного спектра. Второе условие
     * важнее первого — без спектра отпечаток теряет единственное измерение,
     * которого нет больше нигде.
     */
    suspend fun maturity(
        profileId: Long,
        baseline: BaselineState?,
        s: FingerprintStrings = FingerprintRu,
    ): Maturity {
        val active = (baseline as? BaselineState.Active)?.baseline
            ?: return Maturity(ready = false, reason = s.maturityNoBaseline)
        if (active.accumulatedSeconds < Fingerprint.MATURITY_SECONDS) {
            return Maturity(
                ready = false,
                reason = s.maturityNeedsHours(
                    needHours = Fingerprint.MATURITY_SECONDS / 3600,
                    haveHours = active.accumulatedSeconds / 3600,
                ),
            )
        }
        val spectrum = referenceSpectrum(profileId)
        val counts = spectrum?.totalCounts ?: 0L
        if (counts < Fingerprint.MATURITY_SPECTRUM_COUNTS) {
            return Maturity(
                ready = false,
                reason = s.maturityThinSpectrum(
                    counts = counts,
                    needCounts = Fingerprint.MATURITY_SPECTRUM_COUNTS,
                ),
            )
        }
        return Maturity(ready = true, reason = null)
    }

    /**
     * Создаёт эталон места. Прежний не переписывается — он остаётся историей
     * места, а действующим считается самый свежий.
     */
    suspend fun create(
        profileId: Long,
        baseline: Baseline,
        origin: String = ProfileFingerprintEntity.ORIGIN_AUTO,
    ): Boolean {
        val spectrum = referenceSpectrum(profileId) ?: return false
        if (spectrum.totalCounts < Fingerprint.MATURITY_SPECTRUM_COUNTS) return false
        profileDao.insertFingerprint(
            ProfileFingerprintEntity(
                profileId = profileId,
                createdAt = clock(),
                accumulatedSeconds = baseline.accumulatedSeconds,
                sampleCount = baseline.sampleCount,
                doseLowMicroSvH = baseline.doseLowMicroSvH,
                doseMedianMicroSvH = baseline.doseMedianMicroSvH,
                doseHighMicroSvH = baseline.doseHighMicroSvH,
                doseP25MicroSvH = baseline.doseP25MicroSvH,
                doseP75MicroSvH = baseline.doseP75MicroSvH,
                doseMadMicroSvH = baseline.doseMadMicroSvH,
                cpsLow = baseline.cpsLow,
                cpsMedian = baseline.cpsMedian,
                cpsHigh = baseline.cpsHigh,
                spectrumSeconds = spectrum.seconds,
                a0 = spectrum.a0,
                a1 = spectrum.a1,
                a2 = spectrum.a2,
                channelCount = spectrum.counts.size,
                spectrum = SpectrumBlob.encode(spectrum.counts),
                origin = origin,
                algorithmVersion = AlgorithmVersions.FINGERPRINT,
            ),
        )
        return true
    }

    /** Опорный спектр профиля за окно baseline — то, из чего создаётся эталон. */
    suspend fun referenceSpectrum(profileId: Long): AccumulatedSpectrum? {
        val to = clock()
        val from = to - BaselineConfig.WINDOW_DAYS * 24L * 3600_000L
        return accumulate(profileId, from, to, thinHourly = true)
    }

    /**
     * Складывает интервальные разности соседних приборных снимков за период,
     * оставляя только интервалы, которые целиком лежат в допущенных
     * измерениях этого профиля.
     *
     * Почему разности, а не сам накопительный спектр: прибор копит спектр «с
     * последнего сброса», и его абсолютное содержимое рассказывает про всю
     * историю, а не про место. Почему «целиком в допущенных»: одна минута
     * Поиска или карантина внутри интервала — и в эталон попадёт то, ради
     * исключения чего пайплайн допуска и существует. Допущенность оценивается
     * по числу допущенных отсчётов против длительности интервала: прибор
     * пишет раз в секунду, поэтому это сравнение секунд с секундами
     * ([ADMITTED_FRACTION] — инженерный параметр, оставляет запас на
     * пропуски BLE).
     */
    private suspend fun accumulate(
        profileId: Long,
        from: Long,
        to: Long,
        thinHourly: Boolean,
    ): AccumulatedSpectrum? {
        val metas = spectrumDao.deviceSnapshotMeta(from, to)
        if (metas.size < 2) return null
        val ids = if (thinHourly) {
            metas.sortedBy { it.timestamp }
                .groupBy { it.timestamp / 3_600_000L }
                .toSortedMap()
                .map { (_, group) -> group.last().id }
        } else {
            metas.sortedBy { it.timestamp }.map { it.id }
        }

        var total: IntArray? = null
        var seconds = 0L
        var a0 = 0f
        var a1 = 0f
        var a2 = 0f
        var previous: SnapshotCounts? = null
        for (id in ids) {
            val entity = spectrumDao.byId(id) ?: continue
            val current = SnapshotCounts(
                timestamp = entity.timestamp,
                durationSeconds = entity.durationSeconds,
                counts = SpectrumBlob.decode(entity.counts),
                a0 = entity.a0,
                a1 = entity.a1,
                a2 = entity.a2,
            )
            val prev = previous
            previous = current
            if (prev == null) continue
            if (prev.counts.size != current.counts.size) continue
            if (prev.a0 != current.a0 || prev.a1 != current.a1 || prev.a2 != current.a2) continue
            val deltaSeconds = current.durationSeconds - prev.durationSeconds
            if (deltaSeconds <= 0) continue

            val spanSeconds = (current.timestamp - prev.timestamp) / 1000L
            if (spanSeconds <= 0) continue
            val admitted = sampleDao.admittedCountForProfile(
                profileId = profileId,
                from = prev.timestamp,
                to = current.timestamp,
            )
            if (admitted < spanSeconds * ADMITTED_FRACTION) continue

            val sink = total ?: IntArray(current.counts.size).also {
                total = it
                a0 = current.a0
                a1 = current.a1
                a2 = current.a2
            }
            if (sink.size != current.counts.size) continue
            for (channel in sink.indices) {
                val delta = current.counts[channel] - prev.counts[channel]
                if (delta > 0) sink[channel] += delta
            }
            seconds += deltaSeconds
        }
        val counts = total ?: return null
        return AccumulatedSpectrum(counts.toList(), seconds, a0, a1, a2)
    }

    private data class SnapshotCounts(
        val timestamp: Long,
        val durationSeconds: Long,
        val counts: List<Int>,
        val a0: Float,
        val a1: Float,
        val a2: Float,
    )

    /** Готовность профиля к эталону вместе с причиной, если ещё нет. */
    data class Maturity(val ready: Boolean, val reason: String?)

    companion object {
        /** Окно, которое сравнивается с эталоном по умолчанию. */
        const val DEFAULT_WINDOW_MILLIS = 60L * 60_000L

        const val BUCKET_MILLIS = 60_000L

        /**
         * Какая доля интервала должна быть допущенными измерениями, чтобы он
         * попал в эталон. **Инженерный параметр**: запас на пропуски BLE.
         */
        const val ADMITTED_FRACTION = 0.8
    }
}

private fun ProfileFingerprintEntity.toReference(): FingerprintReference = FingerprintReference(
    doseLowMicroSvH = doseLowMicroSvH,
    doseMedianMicroSvH = doseMedianMicroSvH,
    doseHighMicroSvH = doseHighMicroSvH,
    cpsLow = cpsLow,
    cpsMedian = cpsMedian,
    cpsHigh = cpsHigh,
    spectrum = SpectrumBlob.decode(spectrum),
    spectrumSeconds = spectrumSeconds,
    createdAtMillis = createdAt,
    accumulatedSeconds = accumulatedSeconds,
)

private fun median(sorted: List<Float>): Float =
    if (sorted.isEmpty()) 0f else sorted[sorted.size / 2]
