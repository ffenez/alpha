package app.radiacode.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App settings in Preferences DataStore. */
class AppSettings(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.settingsDataStore)

    /** Dose-rate threshold for automatic hotspot events, uSv/h. */
    val hotspotThresholdMicroSvH: Flow<Float> =
        dataStore.data.map { it[HOTSPOT_THRESHOLD] ?: DEFAULT_HOTSPOT_THRESHOLD_MICRO_SV_H }

    suspend fun setHotspotThresholdMicroSvH(value: Float) {
        dataStore.edit { it[HOTSPOT_THRESHOLD] = value }
    }

    /**
     * Last connected device address; lets the foreground service resume after
     * a system restart (START_STICKY redelivers a null intent).
     */
    val lastDeviceAddress: Flow<String?> = dataStore.data.map { it[LAST_DEVICE_ADDRESS] }

    suspend fun setLastDeviceAddress(address: String) {
        dataStore.edit { it[LAST_DEVICE_ADDRESS] = address }
    }

    companion object {
        const val DEFAULT_HOTSPOT_THRESHOLD_MICRO_SV_H = 0.30f
        private val HOTSPOT_THRESHOLD = floatPreferencesKey("hotspot_threshold_usvh")
        private val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
    }
}
