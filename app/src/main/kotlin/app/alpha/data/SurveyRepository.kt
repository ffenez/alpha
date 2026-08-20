package app.alpha.data

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.Radioelements
import app.alpha.data.db.SpectrumDao
import app.alpha.data.db.SurveyDao
import app.alpha.data.db.SurveyStationEntity
import app.alpha.device.DeviceModel
import app.alpha.ui.logic.StrippingRecord
import app.alpha.ui.logic.SurveyModel
import kotlinx.coroutines.flow.Flow

/**
 * Станции радиоэлементной съёмки.
 *
 * Разбор спектра идёт окнами ПРИБОРА, которым станция снята: разрешение
 * берётся из модели, опознанной по серийному номеру самого снимка. Станция,
 * снятая неопознанным прибором, считается по осторожному профилю, и экран об
 * этом говорит — молча подставить чужое разрешение значит сдвинуть границы
 * окон и все площади вместе с ними.
 */
class SurveyRepository(
    private val surveyDao: SurveyDao,
    private val spectrumDao: SpectrumDao,
) {

    fun stations(): Flow<List<SurveyStationEntity>> = surveyDao.observeAll()

    suspend fun count(): Long = surveyDao.count()

    suspend fun record(
        spectrumId: Long,
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        pressureHpa: Float?,
        heightCm: Int? = null,
        note: String? = null,
    ): Long = surveyDao.insert(
        SurveyStationEntity(
            spectrumId = spectrumId,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            pressureHpa = pressureHpa,
            heightCm = heightCm,
            note = note,
        ),
    )

    suspend fun describe(id: Long, note: String?, heightCm: Int?) =
        surveyDao.describe(id, note, heightCm)

    suspend fun delete(id: Long) = surveyDao.delete(id)

    /**
     * Все станции с разобранными спектрами, старые первыми.
     *
     * Станция, чей снимок исчез, пропускается: строка без спектра — не
     * станция. Внешний ключ уносит такие записи сам, это защита от порчи базы.
     */
    suspend fun loaded(
        stripping: StrippingRecord? = null,
    ): List<SurveyModel.Station> = surveyDao.all().mapNotNull { station ->
        val snapshot = spectrumDao.byId(station.spectrumId) ?: return@mapNotNull null
        val model = DeviceModel.fromSerial(snapshot.deviceSerial)
        // Коэффициенты принадлежат ПРИБОРУ: к станции, снятой другим, они не
        // применяются — доля протечки зависит от кристалла.
        val corrections = if (stripping?.appliesTo(snapshot.deviceSerial) == true) {
            stripping.stripping()
        } else {
            Radioelements.Stripping.NONE
        }
        SurveyModel.station(
            entity = station,
            counts = SpectrumBlob.decode(snapshot.counts),
            calibration = EnergyCalibration(snapshot.a0, snapshot.a1, snapshot.a2),
            seconds = snapshot.durationSeconds,
            resolution662 = model.peakResolution662,
            stripping = corrections,
            deviceName = model.takeIf { it != DeviceModel.UNKNOWN }?.displayName,
            tunedProfile = model.resolution662 != null,
        )
    }
}
