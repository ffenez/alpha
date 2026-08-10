package app.radiacode

import android.content.Context
import app.radiacode.data.AppSettings
import app.radiacode.data.BaselineRepository
import app.radiacode.data.MeasurementRepository
import app.radiacode.data.PlaceRepository
import app.radiacode.data.SessionRepository
import app.radiacode.data.TrackRepository
import app.radiacode.data.db.AppDatabase
import app.radiacode.device.DeviceLinkFactory
import app.radiacode.device.KableLinkFactory
import app.radiacode.device.RadiaCodeScanner
import app.radiacode.service.ServiceStatus
import app.radiacode.service.SpectrogramStore
import app.radiacode.service.SpectrumHub

/**
 * Manual dependency graph (no DI framework, ADR 001). One instance per process,
 * shared by the service and (later) UI.
 */
class AppGraph private constructor(context: Context) {

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val settings: AppSettings by lazy { AppSettings(context) }

    val measurementRepository: MeasurementRepository by lazy {
        MeasurementRepository(
            sampleDao = database.sampleDao(),
            rareDataDao = database.rareDataDao(),
            eventDao = database.eventDao(),
            spectrumDao = database.spectrumDao(),
        )
    }

    val trackRepository: TrackRepository by lazy { TrackRepository(database.trackDao()) }

    val placeRepository: PlaceRepository by lazy {
        PlaceRepository(
            placeDao = database.placeDao(),
            sampleDao = database.sampleDao(),
            settings = settings,
        )
    }

    val baselineRepository: BaselineRepository by lazy { BaselineRepository(database.sampleDao()) }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(
            sessionDao = database.sessionDao(),
            sampleDao = database.sampleDao(),
            placeDao = database.placeDao(),
            spectrumDao = database.spectrumDao(),
            trackDao = database.trackDao(),
            eventDao = database.eventDao(),
        )
    }

    val linkFactory: DeviceLinkFactory by lazy { KableLinkFactory() }

    val scanner: RadiaCodeScanner by lazy { RadiaCodeScanner() }

    /** Live service/connection state for the UI (service is unbound). */
    val serviceStatus: ServiceStatus = ServiceStatus()

    /** Spectrum acquisition bridge: UI attaches, service polls and executes commands. */
    val spectrumHub: SpectrumHub = SpectrumHub()

    /** In-memory спектрограмма ring (~2 ч), fed by the service's spectrum poll. */
    val spectrogramStore: SpectrogramStore = SpectrogramStore()

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
