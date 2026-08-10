package app.radiacode.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.alarmThresholds
import app.radiacode.context.ContextConfig
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

    /** Search mode: Geiger-style click feedback. Off by default — sound is opt-in. */
    val searchSoundEnabled: Flow<Boolean> = dataStore.data.map { it[SEARCH_SOUND] ?: false }

    suspend fun setSearchSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[SEARCH_SOUND] = enabled }
    }

    /** Search mode: σ-step vibration pulses. Off by default. */
    val searchVibrationEnabled: Flow<Boolean> =
        dataStore.data.map { it[SEARCH_VIBRATION] ?: false }

    suspend fun setSearchVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[SEARCH_VIBRATION] = enabled }
    }

    /** Search mode: click pitch follows the mean photon energy. Off by default. */
    val searchEnergyToneEnabled: Flow<Boolean> =
        dataStore.data.map { it[SEARCH_ENERGY_TONE] ?: false }

    suspend fun setSearchEnergyToneEnabled(enabled: Boolean) {
        dataStore.edit { it[SEARCH_ENERGY_TONE] = enabled }
    }

    /**
     * Last explicitly chosen profile; null until the first profiles exist.
     * Falls back to the pre-v6 «active place» key so an update keeps the
     * user's selection.
     */
    val activeProfileId: Flow<Long?> =
        dataStore.data.map { it[ACTIVE_PROFILE_ID] ?: it[LEGACY_ACTIVE_PLACE_ID] }

    suspend fun setActiveProfileId(profileId: Long) {
        dataStore.edit { it[ACTIVE_PROFILE_ID] = profileId }
    }

    /**
     * True while the user's explicit profile choice overrides the automatic
     * Wi-Fi context (spec §3.2). «Вернуться к авто» sets it back to false.
     */
    val contextManual: Flow<Boolean> = dataStore.data.map { it[CONTEXT_MANUAL] ?: false }

    suspend fun setContextManual(manual: Boolean) {
        dataStore.edit { it[CONTEXT_MANUAL] = manual }
    }

    /** Grace period before the automatic context is given up, ms (spec §3.4). */
    val contextGraceMillis: Flow<Long> = dataStore.data.map {
        it[CONTEXT_GRACE_MILLIS] ?: ContextConfig.DEFAULT_GRACE_MILLIS
    }

    suspend fun setContextGraceMillis(millis: Long) {
        dataStore.edit { it[CONTEXT_GRACE_MILLIS] = millis }
    }

    /**
     * Manual baseline freeze — condition 7 of the admission pipeline
     * (spec §4.2). Measurements keep being recorded; they simply do not join
     * the statistics of any profile while this is on.
     */
    val baselineFrozen: Flow<Boolean> = dataStore.data.map { it[BASELINE_FROZEN] ?: false }

    suspend fun setBaselineFrozen(frozen: Boolean) {
        dataStore.edit { it[BASELINE_FROZEN] = frozen }
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

    /**
     * Bottom-nav customization, opaque to this layer: parsing and the
     * «Главная + минимум одна вкладка» guard live in `ui/logic/NavConfig`.
     * Null = defaults (all tabs visible, canonical order).
     */
    val navTabsRaw: Flow<String?> = dataStore.data.map { it[NAV_TABS] }

    suspend fun setNavTabsRaw(value: String) {
        dataStore.edit { it[NAV_TABS] = value }
    }

    /** Optional Монитор blocks; hero value, status and chart are fixed. */
    val monitorBlocks: Flow<MonitorBlocks> = dataStore.data.map { prefs ->
        MonitorBlocks(
            trend = prefs[MONITOR_SHOW_TREND] ?: true,
            doseToday = prefs[MONITOR_SHOW_DOSE_TODAY] ?: true,
            stats = prefs[MONITOR_SHOW_STATS] ?: true,
            cpsHint = prefs[MONITOR_SHOW_CPS_HINT] ?: true,
        )
    }

    suspend fun setMonitorBlocks(blocks: MonitorBlocks) {
        dataStore.edit {
            it[MONITOR_SHOW_TREND] = blocks.trend
            it[MONITOR_SHOW_DOSE_TODAY] = blocks.doseToday
            it[MONITOR_SHOW_STATS] = blocks.stats
            it[MONITOR_SHOW_CPS_HINT] = blocks.cpsHint
        }
    }

    /** Настройки → Интерфейс → «сбросить»: nav order and Монитор blocks. */
    suspend fun resetInterfaceCustomization() {
        dataStore.edit {
            it.remove(NAV_TABS)
            it.remove(MONITOR_SHOW_TREND)
            it.remove(MONITOR_SHOW_DOSE_TODAY)
            it.remove(MONITOR_SHOW_STATS)
            it.remove(MONITOR_SHOW_CPS_HINT)
        }
    }

    companion object {
        const val DEFAULT_CUSTOM_L1_MICRO_SV_H = 0.30f
        const val DEFAULT_CUSTOM_L2_MICRO_SV_H = 1.00f
        private val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        private val SEARCH_BACKGROUND_CPS = floatPreferencesKey("search_background_cps")
        private val SEARCH_SOUND = booleanPreferencesKey("search_sound")
        private val SEARCH_VIBRATION = booleanPreferencesKey("search_vibration")
        private val SEARCH_ENERGY_TONE = booleanPreferencesKey("search_energy_tone")
        private val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")

        /** Pre-v6 key; read-only fallback so an update keeps the selection. */
        private val LEGACY_ACTIVE_PLACE_ID = longPreferencesKey("active_place_id")
        private val CONTEXT_MANUAL = booleanPreferencesKey("context_manual")
        private val CONTEXT_GRACE_MILLIS = longPreferencesKey("context_grace_millis")
        private val BASELINE_FROZEN = booleanPreferencesKey("baseline_frozen")
        private val ALARM_SENSITIVITY = stringPreferencesKey("alarm_sensitivity")
        private val CUSTOM_ALARM_L1 = floatPreferencesKey("custom_alarm_l1_usvh")
        private val CUSTOM_ALARM_L2 = floatPreferencesKey("custom_alarm_l2_usvh")
        private val DOSE_UNIT = stringPreferencesKey("dose_unit")
        private val NAV_TABS = stringPreferencesKey("nav_tabs")
        private val MONITOR_SHOW_TREND = booleanPreferencesKey("monitor_show_trend")
        private val MONITOR_SHOW_DOSE_TODAY = booleanPreferencesKey("monitor_show_dose_today")
        private val MONITOR_SHOW_STATS = booleanPreferencesKey("monitor_show_stats")
        private val MONITOR_SHOW_CPS_HINT = booleanPreferencesKey("monitor_show_cps_hint")
    }
}

/** Optional Монитор blocks (Настройки → Интерфейс); defaults all on. */
data class MonitorBlocks(
    val trend: Boolean = true,
    val doseToday: Boolean = true,
    val stats: Boolean = true,
    val cpsHint: Boolean = true,
)

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
