package app.alpha.data

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.Peak
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

    /**
     * Досъёмка: новое накопление складывается с шаблоном.
     *
     * Шкала новой записи выравнивается по шаблону подобранными [gain] и
     * [offsetKeV] — их даёт то же разложение, которым потом пользуются доли.
     * Разрешение измеряется заново: сложенный спектр статистически лучше, и
     * ширина линии в нём определена точнее.
     *
     * @return сложенное время накопления, с; null — шкалы не пересеклись.
     */
    suspend fun accumulate(
        entity: SpectrumTemplateEntity,
        counts: List<Int>,
        calibration: EnergyCalibration,
        seconds: Long,
        gain: Double,
        offsetKeV: Double,
    ): Long? {
        val merged = SpectrumTemplate.accumulate(
            template = template(entity),
            counts = counts,
            calibration = calibration,
            seconds = seconds,
            gain = gain,
            offsetKeV = offsetKeV,
        ) ?: return null
        dao.update(
            entity.copy(
                counts = SpectrumBlob.encode(merged.counts),
                durationSeconds = merged.seconds,
                channelCount = merged.counts.size,
                resolution662 = measuredResolution(
                    counts = merged.counts,
                    calibration = merged.calibration,
                    fallback = entity.resolution662,
                ),
            ),
        )
        return merged.seconds
    }

    /**
     * Собственный фон прибора, собранный из уже накопленных снимков.
     *
     * Без единого шаблона разложение показать нечего, а первый шаблон, который
     * есть у всякого прибора, — это его собственный фон: он и так копится
     * снимками каждые десять минут. Поэтому фон заводится и обновляется сам.
     *
     * Обновление не «докладывает», а ЗАМЕНЯЕТ: суммы соседних окон истории
     * пересекаются, и сложение посчитало бы одни и те же импульсы дважды.
     *
     * @return true, если запись появилась или обновилась.
     */
    suspend fun refreshAutoBackground(
        counts: List<Int>,
        calibration: EnergyCalibration,
        seconds: Long,
        resolution662: Float,
        deviceSerial: String?,
        deviceName: String?,
        atMillis: Long,
    ): Boolean {
        if (counts.isEmpty() || seconds <= 0L) return false
        val existing = dao.all().firstOrNull {
            it.source == SpectrumTemplateEntity.SOURCE_AUTO &&
                it.deviceSerial?.equals(deviceSerial, ignoreCase = true) == true
        }
        // Прежняя запись заменяется только заметно лучшей: иначе библиотека
        // переписывалась бы каждые шесть часов ради тех же самых чисел.
        if (existing != null && seconds < AUTO_GROWTH * existing.durationSeconds) return false
        val entity = SpectrumTemplateEntity(
            id = existing?.id ?: 0L,
            name = AUTO_BACKGROUND_NAME,
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
            source = SpectrumTemplateEntity.SOURCE_AUTO,
        )
        if (existing == null) dao.insert(entity) else dao.update(entity)
        return true
    }

    /** Как шаблон видит движок разложения. */
    fun template(entity: SpectrumTemplateEntity): SpectrumTemplate {
        val counts = SpectrumBlob.decode(entity.counts)
        val calibration = EnergyCalibration(entity.a0, entity.a1, entity.a2)
        return SpectrumTemplate(
            name = entity.name,
            counts = counts,
            calibration = calibration,
            seconds = entity.durationSeconds,
            resolution662 = entity.resolution662,
            deviceName = entity.deviceName,
        )
    }

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
         * Имя записи собственного фона. В базе оно не переводится (как и
         * прочие метки), а на экране подписывается языком интерфейса по
         * признаку [SpectrumTemplateEntity.SOURCE_AUTO].
         */
        const val AUTO_BACKGROUND_NAME = "auto-background"

        /**
         * Во сколько раз новое накопление должно быть длиннее прежнего, чтобы
         * заменить его, — **инженерный параметр**. Полтора: неопределённость
         * формы падает как 1/√t, то есть выигрыш около 20 %; ради меньшего
         * переписывать библиотеку незачем.
         */
        const val AUTO_GROWTH = 1.5

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
            val measured = lines(counts, calibration, fallback)
                .maxByOrNull { it.significance }
                ?: return fallback
            val fwhm = measured.fwhmKeV ?: return fallback
            val resolution = fwhm / kotlin.math.sqrt(REFERENCE_KEV * measured.energyKeV)
            return if (resolution in MIN_RESOLUTION..MAX_RESOLUTION) resolution else fallback
        }

        /**
         * Линии спектра с ИЗМЕРЕННОЙ шириной — те, по которым вообще можно
         * судить о разрешении прибора.
         */
        fun lines(
            counts: List<Int>,
            calibration: EnergyCalibration,
            fallback: Float,
        ): List<Peak> = PeakDetection.detect(
            counts = counts,
            calibration = calibration,
            resolution662 = fallback,
            minEnergyKeV = DeviceModel.UNKNOWN.peakFloorKeV,
        ).filter { it.fwhmKeV != null && it.energyKeV > MIN_RESOLUTION_ENERGY_KEV }

        /** Энергия Cs-137, к которой приведено «разрешение в процентах». */
        const val REFERENCE_KEV = 662f

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
