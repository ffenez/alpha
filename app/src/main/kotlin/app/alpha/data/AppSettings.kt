package app.alpha.data

import app.alpha.ui.logic.ChartDetailMode
import app.alpha.ui.logic.DoseTint
import app.alpha.ui.logic.MapAnchors
import app.alpha.ui.logic.MapColorScale
import app.alpha.ui.logic.TrackMap
import app.alpha.ui.text.RuStrings
import app.alpha.ui.theme.UiScale
import app.alpha.ui.text.Strings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.alpha.baseline.AlarmSensitivity
import app.alpha.baseline.AlarmThresholds
import app.alpha.baseline.alarmThresholds
import app.alpha.context.ContextConfig
import app.alpha.ui.text.AppLanguage
import app.alpha.ui.theme.AppSkin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import app.alpha.ui.logic.SearchFeedbackChannels

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The slice of the settings the profile layer needs: which profile the user
 * pinned by hand and how to release that pin. Narrow on purpose — profile
 * deletion has to clear the pin, and that rule is worth a JVM test without
 * dragging DataStore and an Android context into it.
 */
interface ActiveProfilePin {

    /** Last explicitly chosen profile; null = follow the automatic context. */
    val activeProfileId: Flow<Long?>

    /** Null releases the pin (the deleted profile must not stay selected). */
    suspend fun setActiveProfileId(profileId: Long?)

    suspend fun setContextManual(manual: Boolean)
}

/** App settings in Preferences DataStore. */
class AppSettings(private val dataStore: DataStore<Preferences>) : ActiveProfilePin {

    constructor(context: Context) : this(context.settingsDataStore)

    /**
     * Last connected device address; lets the foreground service resume after
     * a system restart (START_STICKY redelivers a null intent).
     */
    val lastDeviceAddress: Flow<String?> = dataStore.data.map { it[LAST_DEVICE_ADDRESS] }

    /**
     * Продолжать ли измерение после перезагрузки телефона. По умолчанию
     * выключено: служба и обмен по Bluetooth не поднимаются без явного выбора.
     */
    val startOnBoot: Flow<Boolean> = dataStore.data.map { it[START_ON_BOOT] ?: false }

    suspend fun setStartOnBoot(enabled: Boolean) {
        dataStore.edit { it[START_ON_BOOT] = enabled }
    }

    suspend fun setLastDeviceAddress(address: String) {
        dataStore.edit { it[LAST_DEVICE_ADDRESS] = address }
    }

    /**
     * Search-mode background reference with its metadata, as a flat JSON blob
     * (decoded by `ui/logic/BackgroundRecord`, which owns the format — this
     * layer stores bytes and never interprets them).
     *
     * Прежний ключ с одной средней CPS больше не читается: скорость без
     * выдержки, момента, места и качества нельзя ни взвесить статистическим
     * тестом, ни назвать устаревшей (redesign §6).
     */
    val searchBackgroundRaw: Flow<String?> = dataStore.data.map { it[SEARCH_BACKGROUND] }

    suspend fun setSearchBackgroundRaw(encoded: String) {
        dataStore.edit {
            it[SEARCH_BACKGROUND] = encoded
            it.remove(SEARCH_BACKGROUND_CPS)
        }
    }

    /**
     * Вибрация телефона при подтверждённом превышении НАШЕГО порога.
     *
     * Отдельно от системного канала уведомлений: у канала свои настройки
     * вибрации, и человек, поставивший порог в приложении, вправе ожидать
     * отклика от приложения, а не от настроек Android. По умолчанию включена —
     * порог ставят, чтобы о нём узнать.
     */
    val alarmVibration: Flow<Boolean> = dataStore.data.map { it[ALARM_VIBRATION] ?: true }

    suspend fun setAlarmVibration(on: Boolean) {
        dataStore.edit { it[ALARM_VIBRATION] = on }
    }

    /**
     * Записывать ли превышения в журнал Истории.
     *
     * Отдельно от самой тревоги: звук и вибрация нужны В МОМЕНТ превышения, а
     * запись — чтобы вернуться к нему потом, и это разные потребности. Кто
     * ходит с прибором по местам с высоким фоном, получает журнал из эпизодов,
     * которые ему не нужны; выключатель отвечает за журнал и НЕ трогает
     * тревогу. По умолчанию включено — превышение стоит того, чтобы остаться
     * записью.
     */
    val journalEpisodes: Flow<Boolean> = dataStore.data.map { it[JOURNAL_EPISODES] ?: true }

    suspend fun setJournalEpisodes(on: Boolean) {
        dataStore.edit { it[JOURNAL_EPISODES] = on }
    }

    /**
     * Каналы отклика поиска — три независимых выключателя.
     *
     * Хранятся тремя булевыми, а не одним режимом: щелчки, тон и вибрация не
     * спорят друг с другом, и запрет их сочетать был ограничением модели
     * данных, а не прибора (`settings_ui_restructure.md` §3).
     *
     * Пока человек не трогал новые выключатели, значения выводятся из прежнего
     * одиночного режима, а до него — из двух ещё более старых булевых: настройка
     * не имеет права молча замолчать при обновлении.
     */
    val searchFeedbackChannels: Flow<SearchFeedbackChannels> = dataStore.data.map { prefs ->
        val stored = prefs[SEARCH_CLICKS] != null ||
            prefs[SEARCH_TONE] != null ||
            prefs[SEARCH_VIBRO] != null
        if (stored) {
            SearchFeedbackChannels(
                clicks = prefs[SEARCH_CLICKS] ?: false,
                tone = prefs[SEARCH_TONE] ?: false,
                vibro = prefs[SEARCH_VIBRO] ?: false,
            )
        } else {
            val legacy = prefs[SEARCH_FEEDBACK_MODE] ?: when {
                prefs[SEARCH_SOUND] == true -> "clicks"
                prefs[SEARCH_VIBRATION] == true -> "vibro"
                else -> "off"
            }
            SearchFeedbackChannels.ofLegacyMode(legacy)
        }
    }

    suspend fun setSearchFeedbackChannels(channels: SearchFeedbackChannels) {
        dataStore.edit {
            it[SEARCH_CLICKS] = channels.clicks
            it[SEARCH_TONE] = channels.tone
            it[SEARCH_VIBRO] = channels.vibro
            // Прежние ключи больше не читаются: один источник истины на диске.
            it.remove(SEARCH_FEEDBACK_MODE)
            it.remove(SEARCH_SOUND)
            it.remove(SEARCH_VIBRATION)
        }
    }

    /**
     * Какой звук включает кнопка «звук» на экране Поиска — клики или тон.
     * Экран Поиска только включает и выключает канал.
     */
    val searchSoundFlavour: Flow<String> = dataStore.data.map {
        it[SEARCH_SOUND_FLAVOUR] ?: "clicks"
    }


    /**
     * Вопрос экрана Поиска (`ui/logic/SearchMode`): «Наведение» или
     * «Проверка». Пустое значение = «Проверка».
     */
    val searchMode: Flow<String?> = dataStore.data.map { it[SEARCH_MODE] }

    /** Режим прибора: наблюдение, поиск или проверка ([InstrumentMode]). */
    val instrumentMode: Flow<String?> = dataStore.data.map { it[INSTRUMENT_MODE] }

    suspend fun setInstrumentMode(id: String) {
        dataStore.edit { it[INSTRUMENT_MODE] = id }
    }

    /** Вид шкалы прибора: циферблат или прямая ([InstrumentIndicator]). */
    val instrumentIndicator: Flow<String?> = dataStore.data.map { it[INSTRUMENT_INDICATOR] }

    suspend fun setInstrumentIndicator(id: String) {
        dataStore.edit { it[INSTRUMENT_INDICATOR] = id }
    }

    suspend fun setSearchMode(id: String) {
        dataStore.edit { it[SEARCH_MODE] = id }
    }

    /**
     * Чем «Наведение» рисует отношение к точке отсчёта
     * (`ui/logic/SearchIndicator`): стрелкой или прямой шкалой. Пустое
     * значение = стрелка.
     */


    /**
     * «Показать расчёты» in the «Почему такой вывод» sheet stays where the user
     * left it (why-spec §11): someone who always opens the numbers should stop
     * having to open them.
     */
    val whyCalculationsExpanded: Flow<Boolean> =
        dataStore.data.map { it[WHY_EXPANDED] ?: false }

    suspend fun setWhyCalculationsExpanded(expanded: Boolean) {
        dataStore.edit { it[WHY_EXPANDED] = expanded }
    }

    /**
     * Пользовательский масштаб интерфейса, %: отдельно текст, отдельно
     * элементы (см. [app.alpha.ui.theme.UiScale]). Значение с диска зажимается
     * в допустимые границы при чтении: границы могли измениться в новой версии.
     */
    val fontScalePercent: Flow<Int> =
        dataStore.data.map { UiScale.clampFont(it[FONT_SCALE] ?: UiScale.DEFAULT_PERCENT) }

    val elementScalePercent: Flow<Int> =
        dataStore.data.map { UiScale.clampElement(it[ELEMENT_SCALE] ?: UiScale.DEFAULT_PERCENT) }

    suspend fun setFontScalePercent(percent: Int) {
        dataStore.edit { it[FONT_SCALE] = UiScale.clampFont(percent) }
    }

    suspend fun setElementScalePercent(percent: Int) {
        dataStore.edit { it[ELEMENT_SCALE] = UiScale.clampElement(percent) }
    }

    /**
     * Блок «Спектральные диапазоны» на Спектре остаётся там, где его оставили;
     * свёрнут по умолчанию.
     */
    val spectralRangesExpanded: Flow<Boolean> =
        dataStore.data.map { it[SPECTRAL_RANGES_EXPANDED] ?: false }

    suspend fun setSpectralRangesExpanded(expanded: Boolean) {
        dataStore.edit { it[SPECTRAL_RANGES_EXPANDED] = expanded }
    }

    /**
     * Последнее выбранное окно графика, по величине: `dose=6ч` и т. п. Экран
     * открывается там, где его закрыли.
     */
    val chartSpans: Flow<Map<String, Long>> = dataStore.data.map { prefs ->
        prefs[CHART_SPANS]?.let(::parseSpans) ?: emptyMap()
    }

    suspend fun setChartSpan(metricId: String, spanMillis: Long) {
        dataStore.edit { prefs ->
            val current = prefs[CHART_SPANS]?.let(::parseSpans) ?: emptyMap()
            prefs[CHART_SPANS] = encodeSpans(current + (metricId to spanMillis))
        }
    }

    /**
     * Отладочный отчёт: выключен по умолчанию, включается вручную.
     */
    val debugReportEnabled: Flow<Boolean> = dataStore.data.map { it[DEBUG_REPORT] ?: false }

    suspend fun setDebugReportEnabled(enabled: Boolean) {
        dataStore.edit { it[DEBUG_REPORT] = enabled }
    }

    /**
     * Тема оформления: системная (по умолчанию), тёмная или светлая.
     */
    /**
     * Принятая измеренная модель разрешения
     * ([app.alpha.analysis.evidence.AcceptedResolution]) — плоская строка
     * `a=…;b=…;c=…;serial=…`. Пусто = модель не принята, работает
     * √E-приближение. Хранится сырой: декодирует её чистый JVM-код, который
     * тестируется без Android.
     */
    val measuredResolutionRaw: Flow<String?> =
        dataStore.data.map { it[MEASURED_RESOLUTION] }

    /** `null` — вернуться к √E-приближению. */
    suspend fun setMeasuredResolutionRaw(encoded: String?) {
        dataStore.edit { prefs ->
            if (encoded == null) prefs.remove(MEASURED_RESOLUTION) else {
                prefs[MEASURED_RESOLUTION] = encoded
            }
        }
    }

    /**
     * Калибровка эффективности регистрации ([app.alpha.ui.logic.EfficiencyRecord]).
     *
     * Хранится сырой строкой по той же причине, что и измеренное разрешение:
     * разбирает её чистый JVM-код, проверяемый без Android.
     */
    val efficiencyRaw: Flow<String?> = dataStore.data.map { it[EFFICIENCY] }

    /** `null` — калибровки нет, и активность нигде не называется. */
    suspend fun setEfficiencyRaw(encoded: String?) {
        dataStore.edit { prefs ->
            if (encoded == null) prefs.remove(EFFICIENCY) else prefs[EFFICIENCY] = encoded
        }
    }

    /**
     * Принятая поправка энергетической шкалы
     * ([app.alpha.ui.logic.ScaleCorrectionRecord]).
     *
     * `null` — шкала показывается как её отдаёт прибор.
     */
    val scaleCorrectionRaw: Flow<String?> = dataStore.data.map { it[SCALE_CORRECTION] }

    suspend fun setScaleCorrectionRaw(encoded: String?) {
        dataStore.edit { prefs ->
            if (encoded == null) prefs.remove(SCALE_CORRECTION) else {
                prefs[SCALE_CORRECTION] = encoded
            }
        }
    }

    /** Язык интерфейса; `system` = язык телефона. */
    val language: Flow<AppLanguage> =
        dataStore.data.map { AppLanguage.of(it[LANGUAGE]) }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[LANGUAGE] = language.id }
    }

    /**
     * Показывать ли пояснения — серые строки, объясняющие происходящее.
     * По умолчанию выключены.
     */
    /**
     * Красить ли главное число по отношению к обычному фону места. Включено по
     * умолчанию.
     */
    val doseTint: Flow<Boolean> =
        dataStore.data.map { it[DOSE_TINT] ?: true }

    suspend fun setDoseTint(enabled: Boolean) {
        dataStore.edit { it[DOSE_TINT] = enabled }
    }

    /**
     * Во сколько раз выше обычного цвет числа насыщается. Множитель обычного,
     * а не абсолютное значение: у каждого места свой уровень.
     */
    val doseTintFactor: Flow<Float> =
        dataStore.data.map { (it[DOSE_TINT_FACTOR] ?: DoseTint.DEFAULT_FACTOR) }

    suspend fun setDoseTintFactor(factor: Float) {
        dataStore.edit {
            it[DOSE_TINT_FACTOR] = factor.coerceIn(DoseTint.MIN_FACTOR, DoseTint.MAX_FACTOR)
        }
    }

    /**
     * Чем заданы границы цвета следа на карте. По умолчанию — обычным фоном
     * места: одно значение всегда одного цвета, и два маршрута сравнимы.
     * Растяжение по маршруту находит малые различия, но красит прогулку
     * 0,14–0,16 во всю шкалу — осознанный аналитический режим.
     */
    val mapColorScale: Flow<MapColorScale> = dataStore.data.map { preferences ->
        preferences[MAP_COLOR_SCALE]
            ?.let { name -> MapColorScale.entries.firstOrNull { it.name == name } }
            ?: MapColorScale.ABSOLUTE
    }

    suspend fun setMapColorScale(scale: MapColorScale) {
        dataStore.edit { it[MAP_COLOR_SCALE] = scale.name }
    }

    /**
     * Идущая запись маршрута — чтобы пережить гибель процесса: после
     * перезапуска службы запись продолжается в ту же строку журнала, а не
     * обрывается и не начинается второй.
     */
    val activeTrackSessionId: Flow<Long?> = dataStore.data.map { it[ACTIVE_TRACK] }

    suspend fun setActiveTrackSessionId(sessionId: Long?) {
        dataStore.edit {
            if (sessionId == null) it.remove(ACTIVE_TRACK) else it[ACTIVE_TRACK] = sessionId
        }
    }

    /**
     * Ручные границы шкалы следа — отдельно для дозы и для счёта: величины
     * физически разные, общая шкала означала бы для них разное.
     */
    val manualDoseAnchors: Flow<List<Float>> = dataStore.data.map {
        it[MANUAL_DOSE]?.let(MapAnchors::parse)?.takeIf { anchors -> anchors.size >= 2 }
            ?: TrackMap.DEFAULT_MANUAL_DOSE
    }

    val manualCpsAnchors: Flow<List<Float>> = dataStore.data.map {
        it[MANUAL_CPS]?.let(MapAnchors::parse)?.takeIf { anchors -> anchors.size >= 2 }
            ?: TrackMap.DEFAULT_MANUAL_CPS
    }

    suspend fun setManualDoseAnchors(text: String) {
        dataStore.edit { it[MANUAL_DOSE] = text }
    }

    suspend fun setManualCpsAnchors(text: String) {
        dataStore.edit { it[MANUAL_CPS] = text }
    }

    /**
     * Метка эпохи накопления спектра (ADR 008), строкой
     * `epochId|serial|duration|counts`. Переживает перезапуск процесса: иначе
     * сброс спектра при выключенном приложении остался бы незамеченным.
     */
    val spectrumEpochMark: Flow<String?> = dataStore.data.map { it[SPECTRUM_EPOCH] }

    suspend fun setSpectrumEpochMark(encoded: String) {
        dataStore.edit { it[SPECTRUM_EPOCH] = encoded }
    }

    /**
     * Идущее измерение продукта: прогон живёт в графе приложения, а экрану
     * нужно знать, какой опыт он ведёт, после ухода с вкладки.
     */
    val activeFoodExperimentId: Flow<Long?> = dataStore.data.map { it[ACTIVE_FOOD] }

    suspend fun setActiveFoodExperimentId(experimentId: Long?) {
        dataStore.edit {
            if (experimentId == null) it.remove(ACTIVE_FOOD) else it[ACTIVE_FOOD] = experimentId
        }
    }

    /**
     * Измеренный температурный ход шкалы ([GainDriftRecord]) — одна плоская
     * строка, как и принятая модель разрешения.
     */
    val gainDriftRaw: Flow<String?> = dataStore.data.map { it[GAIN_DRIFT] }

    suspend fun setGainDriftRaw(encoded: String?) {
        dataStore.edit { prefs ->
            if (encoded == null) prefs.remove(GAIN_DRIFT) else prefs[GAIN_DRIFT] = encoded
        }
    }

    /**
     * Отказ от собственного фона, который приложение собирает само: человек
     * удалил эту запись из библиотеки шаблонов, и возвращать её нельзя —
     * иначе удаление выглядело бы как поломка.
     */
    val autoBackgroundOff: Flow<Boolean> =
        dataStore.data.map { it[AUTO_BACKGROUND_OFF] ?: false }

    suspend fun setAutoBackgroundOff(off: Boolean) {
        dataStore.edit { it[AUTO_BACKGROUND_OFF] = off }
    }

    val hintsVisible: Flow<Boolean> =
        dataStore.data.map { it[HINTS_VISIBLE] ?: false }

    suspend fun setHintsVisible(visible: Boolean) {
        dataStore.edit { it[HINTS_VISIBLE] = visible }
    }

    /**
     * Как рисуется живой график: подробно или сглаженно. Настройка одна на
     * карточку Главной и на полноэкранный график — это два размера одной
     * картинки.
     */
    val chartDetailModeId: Flow<String> =
        dataStore.data.map { it[CHART_DETAIL] ?: ChartDetailMode.DEFAULT.id }

    suspend fun setChartDetailMode(id: String) {
        dataStore.edit { it[CHART_DETAIL] = id }
    }

    /** Масштаб оси спектра: режим и степень (для степенного). */
    val spectrumScaleId: Flow<String> =
        dataStore.data.map { it[SPECTRUM_SCALE] ?: "log" }

    val spectrumScaleRoot: Flow<Int> =
        dataStore.data.map { it[SPECTRUM_SCALE_ROOT] ?: 2 }

    /**
     * Что делает картинка спектра: вид фона, сглаживание, континуум.
     *
     * Живёт в настройках, а не в состоянии экрана, по одной причине: переключают
     * это на полном экране, а видно и на вкладке. Два состояния разошлись бы
     * при первом же переходе между ними.
     */
    val spectrumBackgroundView: Flow<String> =
        dataStore.data.map { it[SPECTRUM_BACKGROUND_VIEW] ?: "" }

    val spectrumSmoothing: Flow<Boolean> =
        dataStore.data.map { it[SPECTRUM_SMOOTHING] ?: false }

    val spectrumContinuum: Flow<Boolean> =
        dataStore.data.map { it[SPECTRUM_CONTINUUM] ?: false }

    /**
     * Коэффициенты стриппинга радиоэлементной съёмки, снятые на приборе
     * (`StrippingRecord`); пусто — не измерялись.
     */
    val strippingRaw: Flow<String?> =
        dataStore.data.map { it[SURVEY_STRIPPING] }

    suspend fun setStripping(raw: String?) {
        dataStore.edit { prefs ->
            if (raw == null) prefs.remove(SURVEY_STRIPPING) else prefs[SURVEY_STRIPPING] = raw
        }
    }

    suspend fun setSpectrumBackgroundView(id: String) {
        dataStore.edit { it[SPECTRUM_BACKGROUND_VIEW] = id }
    }

    suspend fun setSpectrumSmoothing(on: Boolean) {
        dataStore.edit { it[SPECTRUM_SMOOTHING] = on }
    }

    suspend fun setSpectrumContinuum(on: Boolean) {
        dataStore.edit { it[SPECTRUM_CONTINUUM] = on }
    }

    suspend fun setSpectrumScale(id: String) {
        dataStore.edit { it[SPECTRUM_SCALE] = id }
    }

    suspend fun setSpectrumScaleRoot(root: Int) {
        dataStore.edit { it[SPECTRUM_SCALE_ROOT] = root }
    }

    /**
     * Ось энергии спектрограммы: «log» (по умолчанию) или «linear». Хранится
     * строкой, как и масштаб спектра, и уезжает в копию настроек.
     */
    val spectrogramEnergyScale: Flow<String> =
        dataStore.data.map { it[SPECTROGRAM_ENERGY_SCALE] ?: "log" }

    suspend fun setSpectrogramEnergyScale(id: String) {
        dataStore.edit { it[SPECTROGRAM_ENERGY_SCALE] = id }
    }

    /** Вариант дизайн-языка: научный терминал или 8-bit. */
    val skin: Flow<AppSkin> = dataStore.data.map { AppSkin.of(it[SKIN]) }

    suspend fun setSkin(skin: AppSkin) {
        dataStore.edit { it[SKIN] = skin.id }
    }

    val themeSetting: Flow<ThemeSetting> =
        dataStore.data.map { ThemeSetting.of(it[THEME]) }

    suspend fun setThemeSetting(theme: ThemeSetting) {
        dataStore.edit { it[THEME] = theme.id }
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
    override val activeProfileId: Flow<Long?> =
        dataStore.data.map { it[ACTIVE_PROFILE_ID] ?: it[LEGACY_ACTIVE_PLACE_ID] }

    /**
     * A null id clears the pin, including the pre-v6 legacy key — otherwise
     * deleting the pinned profile would leave the old «active place» pointing
     * at a row that no longer exists.
     */
    override suspend fun setActiveProfileId(profileId: Long?) {
        dataStore.edit {
            if (profileId == null) {
                it.remove(ACTIVE_PROFILE_ID)
                it.remove(LEGACY_ACTIVE_PLACE_ID)
            } else {
                it[ACTIVE_PROFILE_ID] = profileId
            }
        }
    }

    /**
     * True while the user's explicit profile choice overrides the automatic
     * Wi-Fi context (spec §3.2). «Вернуться к авто» sets it back to false.
     */
    val contextManual: Flow<Boolean> = dataStore.data.map { it[CONTEXT_MANUAL] ?: false }

    override suspend fun setContextManual(manual: Boolean) {
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

    /**
     * Energy-window bounds (spec §7), opaque here: parsing, validation and the
     * fallback to the defaults live in
     * [app.alpha.analysis.EnergyWindows]. Null = defaults
     * (100–300 / 300–700 / 700–1500 keV). These are **analysis parameters**,
     * so they are stored next to the other analysis settings and exported with
     * every experiment.
     */
    val energyWindowsRaw: Flow<String?> = dataStore.data.map { it[ENERGY_WINDOWS] }

    suspend fun setEnergyWindowsRaw(value: String?) {
        dataStore.edit { if (value == null) it.remove(ENERGY_WINDOWS) else it[ENERGY_WINDOWS] = value }
    }

    /**
     * What the Карта tab shows: the current/selected recording or every
     * recording ever made. Null = the user never chose, and
     * [MapTrackScope.resolve] picks the default from what is actually stored.
     */
    val mapTrackScope: Flow<MapTrackScope?> =
        dataStore.data.map { MapTrackScope.fromStorage(it[MAP_TRACK_SCOPE]) }

    suspend fun setMapTrackScope(scope: MapTrackScope) {
        dataStore.edit { it[MAP_TRACK_SCOPE] = scope.name }
    }

    /**
     * Как часто служба спрашивает у прибора спектр, когда экран закрыт
     * (ADR 007). Открытый Спектр или Спектрограмма всегда дают 5 с.
     */
    /** Срез сырых измерений по возрасту, дней; 0 = хранить всё (умолчание). */
    val rawRetentionDays: Flow<Int> =
        dataStore.data.map { RawRetention.sanitize(it[RAW_RETENTION_DAYS] ?: RawRetention.KEEP_ALL_DAYS) }

    suspend fun setRawRetentionDays(days: Int) {
        dataStore.edit { it[RAW_RETENTION_DAYS] = RawRetention.sanitize(days) }
    }

    val spectrumPollPolicy: Flow<SpectrumPollPolicy> =
        dataStore.data.map { SpectrumPollPolicy.of(it[SPECTRUM_POLL_POLICY]) }

    suspend fun setSpectrumPollPolicy(policy: SpectrumPollPolicy) {
        dataStore.edit { it[SPECTRUM_POLL_POLICY] = policy.id }
    }

    /** Optional Монитор blocks; hero value, status and chart are fixed. */
    val monitorBlocks: Flow<MonitorBlocks> = dataStore.data.map { prefs ->
        MonitorBlocks(
            trend = prefs[MONITOR_SHOW_TREND] ?: true,
            doseToday = prefs[MONITOR_SHOW_DOSE_TODAY] ?: true,
            stats = prefs[MONITOR_SHOW_STATS] ?: true,
            countRateChart = prefs[MONITOR_SHOW_CPS_CHART] ?: false,
            hardnessChart = prefs[MONITOR_SHOW_HARDNESS_CHART] ?: false,
            doseChart = prefs[MONITOR_SHOW_DOSE_CHART] ?: true,
            hero = prefs[MONITOR_SHOW_HERO] ?: true,
        )
    }

    suspend fun setMonitorBlocks(blocks: MonitorBlocks) {
        dataStore.edit {
            it[MONITOR_SHOW_TREND] = blocks.trend
            it[MONITOR_SHOW_DOSE_TODAY] = blocks.doseToday
            it[MONITOR_SHOW_STATS] = blocks.stats
            it[MONITOR_SHOW_CPS_CHART] = blocks.countRateChart
            it[MONITOR_SHOW_HARDNESS_CHART] = blocks.hardnessChart
            it[MONITOR_SHOW_DOSE_CHART] = blocks.doseChart
            it[MONITOR_SHOW_HERO] = blocks.hero
        }
    }

    /** Настройки → Интерфейс → «сбросить»: nav order and Монитор blocks. */
    suspend fun resetInterfaceCustomization() {
        dataStore.edit {
            it.remove(NAV_TABS)
            it.remove(MONITOR_SHOW_TREND)
            it.remove(MONITOR_SHOW_DOSE_TODAY)
            it.remove(MONITOR_SHOW_STATS)
            it.remove(MONITOR_SHOW_CPS_CHART)
            it.remove(MONITOR_SHOW_HARDNESS_CHART)
            it.remove(MONITOR_SHOW_DOSE_CHART)
            it.remove(MONITOR_SHOW_HERO)
        }
    }

    // --- резервная копия настроек ----------------------------------------

    /**
     * Настройки для резервной копии: «ключ → значение с пометкой типа».
     *
     * Читается всё хранилище целиком, а не список известных ключей: новая
     * настройка попадает в копию сама. Тип пишется в значении, иначе при
     * чтении его не восстановить («true» и «"true"» — разные вещи).
     *
     * Из копии исключено то, что принадлежит этому телефону и этой базе:
     * адрес прибора, идентификаторы активной записи и профиля, оценка
     * разрешения детектора ([NOT_PORTABLE]).
     */
    suspend fun exportSettings(): List<Pair<String, String>> {
        val preferences = dataStore.data.first()
        return preferences.asMap()
            .filterKeys { it.name !in NOT_PORTABLE }
            .mapNotNull { (key, value) -> encodeSetting(value)?.let { key.name to it } }
            .sortedBy { it.first }
    }

    /**
     * Возвращает настройки из копии. Неизвестные ключи и значения с
     * непонятной пометкой типа пропускаются молча: копия, снятая более новой
     * версией, не должна ломать восстановление всего остального.
     *
     * @return сколько настроек не удалось применить.
     */
    suspend fun importSettings(entries: List<Pair<String, String>>): Int {
        var skipped = 0
        dataStore.edit { prefs ->
            for ((name, encoded) in entries) {
                if (name in NOT_PORTABLE) {
                    skipped++
                    continue
                }
                val applied = applySetting(prefs, name, encoded)
                if (!applied) skipped++
            }
        }
        return skipped
    }

    private fun encodeSetting(value: Any?): String? = when (value) {
        is String -> "s:$value"
        is Boolean -> "b:$value"
        is Int -> "i:$value"
        is Long -> "l:$value"
        is Float -> "f:$value"
        is Double -> "d:$value"
        else -> null
    }

    private fun applySetting(prefs: MutablePreferences, name: String, encoded: String): Boolean {
        val type = encoded.substringBefore(':', "")
        val raw = encoded.substringAfter(':', "")
        return when (type) {
            "s" -> { prefs[stringPreferencesKey(name)] = raw; true }
            "b" -> raw.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(name)] = it } != null
            "i" -> raw.toIntOrNull()?.let { prefs[intPreferencesKey(name)] = it } != null
            "l" -> raw.toLongOrNull()?.let { prefs[longPreferencesKey(name)] = it } != null
            "f" -> raw.toFloatOrNull()?.let { prefs[floatPreferencesKey(name)] = it } != null
            "d" -> raw.toDoubleOrNull()?.let { prefs[doublePreferencesKey(name)] = it } != null
            else -> false
        }
    }

    companion object {

        /**
         * Настройки, которые нельзя переносить между телефонами и базами.
         *
         * Адрес прибора — пара Bluetooth конкретного телефона; остальные
         * четыре — номера строк В ЭТОЙ базе. Оценка разрешения относится к
         * конкретному экземпляру прибора.
         */
        val NOT_PORTABLE = setOf(
            "last_device_address",
            "active_track_session",
            "active_food_experiment",
            "active_profile_id",
            "active_place_id",
            "measured_resolution",
        )

        const val DEFAULT_CUSTOM_L1_MICRO_SV_H = 0.30f
        const val DEFAULT_CUSTOM_L2_MICRO_SV_H = 1.00f
        private val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        private val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        private val AUTO_BACKGROUND_OFF = booleanPreferencesKey("auto_background_off")
        private val GAIN_DRIFT = stringPreferencesKey("gain_drift")
        /** Pre-metadata reference (bare CPS); only ever removed now. */
        private val SEARCH_BACKGROUND_CPS = floatPreferencesKey("search_background_cps")
        private val SEARCH_BACKGROUND = stringPreferencesKey("search_background")
        /** Pre-redesign toggles; read once for migration, then removed. */
        private val SEARCH_SOUND = booleanPreferencesKey("search_sound")
        private val SEARCH_VIBRATION = booleanPreferencesKey("search_vibration")
        private val ALARM_VIBRATION = booleanPreferencesKey("alarm_vibration")
        private val SEARCH_CLICKS = booleanPreferencesKey("search_clicks")
        private val SEARCH_TONE = booleanPreferencesKey("search_tone")
        private val SEARCH_VIBRO = booleanPreferencesKey("search_vibro")
        private val SEARCH_FEEDBACK_MODE = stringPreferencesKey("search_feedback_mode")
        private val SEARCH_SOUND_FLAVOUR = stringPreferencesKey("search_sound_flavour")
        private val SEARCH_ENERGY_TONE = booleanPreferencesKey("search_energy_tone")
        private val INSTRUMENT_INDICATOR = stringPreferencesKey("instrument_indicator")
        private val INSTRUMENT_MODE = stringPreferencesKey("instrument_mode")
        private val SEARCH_MODE = stringPreferencesKey("search_mode")
        private val WHY_EXPANDED = booleanPreferencesKey("why_calculations_expanded")
        private val FONT_SCALE = intPreferencesKey("ui_font_scale_pct")
        private val ELEMENT_SCALE = intPreferencesKey("ui_element_scale_pct")
        private val SPECTRAL_RANGES_EXPANDED =
            booleanPreferencesKey("spectral_ranges_expanded")
        private val THEME = stringPreferencesKey("theme")
        private val LANGUAGE = stringPreferencesKey("language")
        private val SKIN = stringPreferencesKey("skin")
        private val CHART_DETAIL = stringPreferencesKey("chart_detail")
        private val HINTS_VISIBLE = booleanPreferencesKey("hints_visible")
        private val DOSE_TINT = booleanPreferencesKey("dose_tint")
        private val DOSE_TINT_FACTOR = floatPreferencesKey("dose_tint_factor")
        private val MAP_COLOR_SCALE = stringPreferencesKey("map_color_scale")
        private val ACTIVE_TRACK = longPreferencesKey("active_track_session")
        private val ACTIVE_FOOD = longPreferencesKey("active_food_experiment")
        private val MANUAL_DOSE = stringPreferencesKey("map_manual_dose")
        private val MANUAL_CPS = stringPreferencesKey("map_manual_cps")
        private val SPECTRUM_EPOCH = stringPreferencesKey("spectrum_epoch_mark")
        private val SPECTRUM_SCALE = stringPreferencesKey("spectrum_scale")
        private val SPECTRUM_BACKGROUND_VIEW =
            stringPreferencesKey("spectrum_background_view")
        private val SPECTRUM_SMOOTHING = booleanPreferencesKey("spectrum_smoothing")
        private val SPECTRUM_CONTINUUM = booleanPreferencesKey("spectrum_continuum")
        private val SURVEY_STRIPPING = stringPreferencesKey("survey_stripping")
        private val SPECTROGRAM_ENERGY_SCALE =
            stringPreferencesKey("spectrogram_energy_scale")
        private val SPECTRUM_SCALE_ROOT = intPreferencesKey("spectrum_scale_root")
        private val DEBUG_REPORT = booleanPreferencesKey("debug_report")
        private val MEASURED_RESOLUTION = stringPreferencesKey("measured_resolution")
        private val EFFICIENCY = stringPreferencesKey("efficiency_curve")
        private val SCALE_CORRECTION = stringPreferencesKey("scale_correction")
        private val CHART_SPANS = stringPreferencesKey("chart_spans")

        /** «dose:21600000,cps:3600000» — плоский формат, читается тестом. */
        private fun parseSpans(raw: String): Map<String, Long> = raw.split(',')
            .mapNotNull { entry ->
                val parts = entry.split(':')
                val span = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                parts[0] to span
            }
            .toMap()

        private fun encodeSpans(spans: Map<String, Long>): String =
            spans.entries.joinToString(",") { "${it.key}:${it.value}" }
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
        private val ENERGY_WINDOWS = stringPreferencesKey("energy_windows_kev")
        private val MONITOR_SHOW_TREND = booleanPreferencesKey("monitor_show_trend")
        private val MONITOR_SHOW_DOSE_TODAY = booleanPreferencesKey("monitor_show_dose_today")
        private val MONITOR_SHOW_STATS = booleanPreferencesKey("monitor_show_stats")
        private val MONITOR_SHOW_CPS_CHART = booleanPreferencesKey("monitor_show_cps_chart")
        private val MONITOR_SHOW_DOSE_CHART = booleanPreferencesKey("monitor_show_dose_chart")
        private val MONITOR_SHOW_HERO = booleanPreferencesKey("monitor_show_hero")
        private val JOURNAL_EPISODES = booleanPreferencesKey("journal_episodes")
        private val MONITOR_SHOW_HARDNESS_CHART =
            booleanPreferencesKey("monitor_show_hardness_chart")
        private val MAP_TRACK_SCOPE = stringPreferencesKey("map_track_scope")
        private val SPECTRUM_POLL_POLICY = stringPreferencesKey("spectrum_poll_policy")
        private val RAW_RETENTION_DAYS = intPreferencesKey("raw_retention_days")
    }
}

/** Optional Монитор blocks (Настройки → Интерфейс); defaults all on. */
data class MonitorBlocks(
    val trend: Boolean = true,
    val doseToday: Boolean = true,
    val stats: Boolean = true,
    /**
     * Отдельный график скорости счёта. Off by default: the product spec keeps
     * Главная to one chart, and CPS is a detection signal rather than the
     * headline quantity — but it is the user's screen, so it can be turned on.
     */
    val countRateChart: Boolean = false,
    /**
     * График жёсткости. Off by default for the same reason, and because it
     * needs accumulated spectra before it can draw anything.
     */
    val hardnessChart: Boolean = false,
    /**
     * График мощности дозы. Включён по умолчанию: это главная величина экрана.
     * Выключатель есть по той же причине, что и у остальных блоков — экран
     * принадлежит человеку, и «главная величина» не значит «обязательная
     * картинка»: рядом стоит само число, а график нужен не всем.
     */
    val doseChart: Boolean = true,
    /**
     * Число мощности дозы со шкалой и плитками под ним.
     *
     * Выключается целым блоком, а не по частям: плитки поясняют главное
     * число, и оставлять их без него значило бы оставить подписи без подписи.
     * Предупреждение об уровне при этом никуда не девается — оно критическое
     * и живёт независимо от того, показано ли само число.
     */
    val hero: Boolean = true,
)

/**
 * Which track data the Карта tab draws.
 *
 * [CURRENT] is the recording being made (or the newest finished one) — the
 * behaviour the screen always had. [ALL] is the accumulated radiation map:
 * every fix of every recording, aggregated into grid cells.
 */
enum class MapTrackScope {
    CURRENT,
    ALL,
    ;

    companion object {
        fun fromStorage(value: String?): MapTrackScope? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

        /**
         * Default when the user never chose: the accumulated map as soon as
         * anything was ever recorded (that is the question «строится ли карта
         * следа» answers itself), and the single-recording view otherwise, so
         * an empty install lands on the state that teaches recording.
         */
        fun resolve(stored: MapTrackScope?, hasRecordings: Boolean): MapTrackScope =
            stored ?: if (hasRecordings) ALL else CURRENT
    }
}

/** Stored dose display unit. */
/**
 * Тема оформления. «Системная» — значение по умолчанию.
 */
enum class ThemeSetting(val id: String, val label: String) {
    SYSTEM("system", "Системная"),
    DARK("dark", "Тёмная"),
    LIGHT("light", "Светлая"),
    ;

    /**
     * Подпись на экране — из каталога. Поле [label] осталось русским
     * НАМЕРЕННО: его печатает отладочный отчёт, который не зависит от языка
     * интерфейса того, кто его снял.
     */
    fun title(s: Strings = RuStrings): String = when (this) {
        SYSTEM -> s.themeSystem
        DARK -> s.themeDark
        LIGHT -> s.themeLight
    }

    companion object {
        fun of(id: String?): ThemeSetting = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Частота опроса спектра в фоне — ЯВНАЯ политика (ADR 007), а не догадка
 * приложения о том, нужна ли сейчас история.
 *
 * Ступень названа своим ЧИСЛОМ: «5 с», «30 с», «10 мин». Прилагательное рядом
 * (подробно/обычно/экономно) — подпись, а не имя: от «сбалансированного» режима
 * нельзя узнать, какой интервал получит история.
 *
 * Открытый экран Спектра или Спектрограммы всегда даёт 5 с независимо от
 * политики: наблюдатель объявлен явно ([app.alpha.service.SpectrumHub]),
 * и это единственное исключение — скрытых правил вида «если экран давно не
 * открывали» здесь нет.
 */
enum class SpectrumPollPolicy(val id: String, val intervalMillis: Long) {
    EVERY_5_S("5s", 5_000L),
    EVERY_30_S("30s", 30_000L),
    EVERY_10_MIN("10m", 600_000L),
    ;

    companion object {
        /**
         * По умолчанию 30 с: пятнадцатиминутное окно картинки получает
         * ≈30 колонок вместо одной-двух, и это в 20 раз меньше запросов, чем
         * подробный режим.
         */
        val DEFAULT = EVERY_30_S

        fun of(id: String?): SpectrumPollPolicy = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** Интервал опроса при данной политике и числе наблюдателей экрана. */
        fun intervalMillis(policy: SpectrumPollPolicy, watchers: Int): Long =
            if (watchers > 0) EVERY_5_S.intervalMillis else policy.intervalMillis
    }
}

enum class DoseUnitSetting {
    MICRO_SIEVERT,
    MICRO_ROENTGEN,
    ;

    companion object {
        fun fromStorage(value: String?): DoseUnitSetting =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MICRO_SIEVERT
    }
}
