package app.alpha.data

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.PeakDetection
import app.alpha.analysis.SpectrumTemplate
import app.alpha.data.db.SpectrumTemplateDao
import app.alpha.data.db.SpectrumTemplateEntity
import app.alpha.device.DeviceModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Библиотека шаблонов для полноспектрального разложения.
 *
 * ## Персонально для каждого прибора
 *
 * Шаблон хранит серийник и разрешение того прибора, на котором снят. При
 * разложении [applicable] делит библиотеку на три части: снятые ЭТИМ прибором
 * (используются как есть), снятые другим (приводятся уширением и перекладкой,
 * о чём экран говорит) и непригодные (у цели разрешение лучше — сузить чужую
 * линию нечем).
 *
 * Разрешение шаблона берётся не из паспорта модели, а ИЗМЕРЯЕТСЯ по его же
 * спектру ([measuredResolution]): у 101 и 102 вендор его не публикует, а без
 * верного числа уширение либо не сработает, либо испортит форму.
 */
class TemplateRepository(private val dao: SpectrumTemplateDao) {

    fun templates(): Flow<List<SpectrumTemplateEntity>> = dao.observeAll()

    fun count(): Flow<Int> = dao.observeAll().map { it.size }

    suspend fun all(): List<SpectrumTemplateEntity> = dao.all()

    suspend fun delete(id: Long) = dao.delete(id)

    /**
     * Записать шаблон из измеренного спектра.
     *
     * @param resolution662 паспортное разрешение прибора; используется, только
     *   если по спектру измерить не удалось.
     */
    suspend fun record(
        name: String,
        counts: List<Int>,
        calibration: EnergyCalibration,
        seconds: Long,
        resolution662: Float,
        deviceSerial: String?,
        deviceName: String?,
        atMillis: Long,
        note: String? = null,
    ): Long = dao.insert(
        SpectrumTemplateEntity(
            name = name,
            createdAt = atMillis,
            deviceSerial = deviceSerial,
            deviceName = deviceName,
            a0 = calibration.a0,
            a1 = calibration.a1,
            a2 = calibration.a2,
            durationSeconds = seconds,
            resolution662 = measuredResolution(counts, calibration, resolution662),
            channelCount = counts.size,
            counts = SpectrumBlob.encode(counts),
            source = if (deviceSerial != null) {
                SpectrumTemplateEntity.SOURCE_MEASURED
            } else {
                SpectrumTemplateEntity.SOURCE_IMPORTED
            },
            note = note,
        ),
    )

    /** Как шаблон видит движок разложения. */
    fun template(entity: SpectrumTemplateEntity) = SpectrumTemplate(
        name = entity.name,
        counts = SpectrumBlob.decode(entity.counts),
        calibration = EnergyCalibration(entity.a0, entity.a1, entity.a2),
        seconds = entity.durationSeconds,
        resolution662 = entity.resolution662,
        deviceName = entity.deviceName,
    )

    /** Годность шаблона для прибора, на котором его собираются применить. */
    enum class Fitness {
        /** Снят этим же прибором: форма и разрешение свои. */
        OWN,

        /** Снят другим прибором: приводится уширением, форма чужая. */
        FOREIGN,

        /** Не приводится: у цели разрешение лучше, сузить линию нечем. */
        REFUSED,
    }

    fun fitness(entity: SpectrumTemplateEntity, serial: String?, resolution662: Float): Fitness =
        when {
            entity.deviceSerial != null && entity.deviceSerial.equals(serial, ignoreCase = true) ->
                Fitness.OWN
            resolution662 + SpectrumTemplate.RESOLUTION_TOLERANCE < entity.resolution662 ->
                Fitness.REFUSED
            else -> Fitness.FOREIGN
        }

    companion object {

        /**
         * Разрешение прибора, ИЗМЕРЕННОЕ по самому спектру шаблона.
         *
         * Берётся самая значимая линия с измеренной шириной и пересчитывается
         * в долю на 662 кэВ: FWHM(E) = R·√(662·E) ⇒ R = FWHM/√(662·E). Одна
         * линия — потому что шаблон снимается на источнике, где эта линия и
         * есть главная; при неудаче остаётся паспортное значение модели.
         */
        fun measuredResolution(
            counts: List<Int>,
            calibration: EnergyCalibration,
            fallback: Float,
        ): Float {
            val peaks = PeakDetection.detect(
                counts = counts,
                calibration = calibration,
                resolution662 = fallback,
                minEnergyKeV = DeviceModel.UNKNOWN.peakFloorKeV,
            )
            val measured = peaks
                .filter { it.fwhmKeV != null && it.energyKeV > MIN_RESOLUTION_ENERGY_KEV }
                .maxByOrNull { it.significance }
                ?: return fallback
            val fwhm = measured.fwhmKeV ?: return fallback
            val resolution = fwhm / kotlin.math.sqrt(662f * measured.energyKeV)
            return if (resolution in MIN_RESOLUTION..MAX_RESOLUTION) resolution else fallback
        }

        /**
         * Ниже этой энергии ширина линии определяется порогом регистрации, а
         * не разрешением кристалла, и пересчёт в «долю на 662» бессмыслен.
         */
        private const val MIN_RESOLUTION_ENERGY_KEV = 300f

        /** Границы правдоподобия: вне их измерение — это не разрешение. */
        private const val MIN_RESOLUTION = 0.03f
        private const val MAX_RESOLUTION = 0.20f
    }
}
