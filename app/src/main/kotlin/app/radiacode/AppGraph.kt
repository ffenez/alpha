package app.radiacode

import android.content.Context
import app.radiacode.data.AppSettings
import app.radiacode.data.MeasurementRepository
import app.radiacode.data.TrackRepository
import app.radiacode.data.db.AppDatabase
import app.radiacode.device.DeviceLinkFactory
import app.radiacode.device.KableLinkFactory
import app.radiacode.device.RadiaCodeScanner

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

    val linkFactory: DeviceLinkFactory by lazy { KableLinkFactory() }

    val scanner: RadiaCodeScanner by lazy { RadiaCodeScanner() }

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
