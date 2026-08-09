package app.radiacode.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.alarmThresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App settings in Preferences DataStore. */
class AppSettings(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.settingsDataStore)

    /**
     * Last connected device address; lets the foreground service resume after
     * a system restart (START_STICKY redelivers a null intent).
     */
    val lastDeviceAddress: Flow<String?> = dataStore.data.map { it[LAST_DEVICE_ADDRESS] }

    suspend fun setLastDeviceAddress(address: String) {
        dataStore.edit { it[LAST_DEVICE_ADDRESS] = address }
    }

    /** Search-mode local background reference, CPS; null until measured. */
    val searchBackgroundCps: Flow<Float?> = dataStore.data.map { it[SEARCH_BACKGROUND_CPS] }

    suspend fun setSearchBackgroundCps(cps: Float) {
        dataStore.edit { it[SEARCH_BACKGROUND_CPS] = cps }
    }

    /** Manually selected active place; null until the default place is created. */
    val activePlaceId: Flow<Long?> = dataStore.data.map { it[ACTIVE_PLACE_ID] }

    suspend fun setActivePlaceId(placeId: Long) {
        dataStore.edit { it[ACTIVE_PLACE_ID] = placeId }
    }

    /** Alarm sensitivity (SPEC Simple mode): Обычная / Высокая / Своя. */
    val alarmSensitivity: Flow<AlarmSensitivity> =
        dataStore.data.map { AlarmSensitivity.fromStorage(it[ALARM_SENSITIVITY]) }

    suspend fun setAlarmSensitivity(sensitivity: AlarmSensitivity) {
        dataStore.edit { it[ALARM_SENSITIVITY] = sensitivity.name }
    }

    val customAlarmL1MicroSvH: Flow<Float> =
        dataStore.data.map { it[CUSTOM_ALARM_L1] ?: DEFAULT_CUSTOM_L1_MICRO_SV_H }

    val customAlarmL2MicroSvH: Flow<Float> =
        dataStore.data.map { it[CUSTOM_ALARM_L2] ?: DEFAULT_CUSTOM_L2_MICRO_SV_H }

    suspend fun setCustomAlarmLevels(l1MicroSvH: Float, l2MicroSvH: Float) {
        dataStore.edit {
            it[CUSTOM_ALARM_L1] = l1MicroSvH
            it[CUSTOM_ALARM_L2] = l2MicroSvH
        }
    }

    /** Resolved alarm parameters — the single source for engine, chart and UI. */
    val alarmThresholds: Flow<AlarmThresholds> = dataStore.data.map { prefs ->
        alarmThresholds(
            sensitivity = AlarmSensitivity.fromStorage(prefs[ALARM_SENSITIVITY]),
            customL1MicroSvH = prefs[CUSTOM_ALARM_L1] ?: DEFAULT_CUSTOM_L1_MICRO_SV_H,
            customL2MicroSvH = prefs[CUSTOM_ALARM_L2] ?: DEFAULT_CUSTOM_L2_MICRO_SV_H,
        )
    }

    /** Display unit for dose values; raw stored values never change. */
    val doseUnit: Flow<DoseUnitSetting> =
        dataStore.data.map { DoseUnitSetting.fromStorage(it[DOSE_UNIT]) }

    suspend fun setDoseUnit(unit: DoseUnitSetting) {
        dataStore.edit { it[DOSE_UNIT] = unit.name }
    }

    companion object {
        const val DEFAULT_CUSTOM_L1_MICRO_SV_H = 0.30f
        const val DEFAULT_CUSTOM_L2_MICRO_SV_H = 1.00f
        private val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        private val SEARCH_BACKGROUND_CPS = floatPreferencesKey("search_background_cps")
        private val ACTIVE_PLACE_ID = longPreferencesKey("active_place_id")
        private val ALARM_SENSITIVITY = stringPreferencesKey("alarm_sensitivity")
        private val CUSTOM_ALARM_L1 = floatPreferencesKey("custom_alarm_l1_usvh")
        private val CUSTOM_ALARM_L2 = floatPreferencesKey("custom_alarm_l2_usvh")
        private val DOSE_UNIT = stringPreferencesKey("dose_unit")
    }
}

/** Stored dose display unit. */
enum class DoseUnitSetting {
    MICRO_SIEVERT,
    MICRO_ROENTGEN,
    ;

    companion object {
        fun fromStorage(value: String?): DoseUnitSetting =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MICRO_SIEVERT
    }
}
