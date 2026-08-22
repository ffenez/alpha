package app.alpha.ui.text

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Язык интерфейса.
 *
 * `SYSTEM` — язык телефона; всё остальное выбирается человеком явно и живёт в
 * настройках. Список открытый: добавить язык = добавить реализацию [Strings]
 * и одну строку сюда.
 */
enum class AppLanguage(val id: String, val nativeName: String) {
    SYSTEM("system", "Системный"),
    RU("ru", "Русский"),
    EN("en", "English"),
    ;

    companion object {
        fun of(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: SYSTEM

        /**
         * Язык, которым рисуется интерфейс. Незнакомый язык телефона —
         * английский.
         */
        fun resolve(setting: AppLanguage, systemTag: String): AppLanguage = when (setting) {
            SYSTEM -> if (systemTag.startsWith("ru", ignoreCase = true)) RU else EN
            else -> setting
        }
    }
}

/**
 * Каталог строк интерфейса.
 *
 * ## Почему не `strings.xml`
 *
 * Формулировки приложения проверяются тестами («не оценивалось» ≠ «не
 * обнаружено», запрет «безопасно», «различие не выделено» вместо «в пределах
 * шума»), а правила живут в чистом JVM-коде (`ui/logic`, `analysis`), который
 * тестируется без Android; `strings.xml` требует `Context`. Часть строк
 * собирается из чисел, единиц и склонений кодом, а не подстановкой в шаблон.
 *
 * Каталог — обычный Kotlin-интерфейс: он доступен композициям, чистой логике
 * и тестам, а компилятор не даёт забыть строку при добавлении языка.
 *
 * ## Как переводится
 *
 * Перевод переносит ПРАВИЛО: в английской реализации «safe», «normal» и
 * «dangerous» запрещены так же, как «безопасно» и «норма» в русской, и это
 * проверяется тестом.
 */
interface Strings {

    val language: AppLanguage

    // --- вкладки и навигация ---
    val tabHome: String
    val tabSpectrum: String
    val tabMap: String
    val tabHistory: String
    val back: String
    val close: String
    val settings: String

    // --- состояние связи ---
    val connected: String
    val connecting: String
    val reconnecting: String
    val serviceOff: String
    val noLink: String
    val noData: String

    // --- Главная ---
    val doseRate: String
    val countRate: String


    /** Единица скорости счёта: «с⁻¹» / «s⁻¹» — она принадлежит языку. */
    val cpsUnit: String
    val hardness: String
    val trendPerHour: String

    /** Названия режимов прибора — по одному слову: это ряд, а не заголовки. */
    /** Вид шкалы прибора и его выбор в настройках. */
    val indicatorTitle: String
    val indicatorDial: String
    val indicatorBar: String
    val indicatorNote: String

    val modeObserve: String
    val modeSearchShort: String


    /**
     * Подписи трёх плиток под главным числом.
     *
     * Каждая называет ОКНО или ЗНАМЕНАТЕЛЬ величины, а не саму величину:
     * величина под ними одна и та же — доза, и плитки различаются именно тем,
     * за какой срок и относительно чего она посчитана.
     */
    val tilePlaceBackground: String
    val tilePerHour: String
    val tilePerDay: String

    /**
     * Имена готовых мест (spec §3.1) и двух служебных ролей.
     *
     * Их придумывает приложение, а не человек, поэтому они переводятся:
     * английский экран с местом «Дом» выглядит недоделанным. Имя, которое
     * человек ввёл сам, не трогается никогда.
     */
    /** Подпись ячейки «температура телефона» рядом с температурой прибора. */
    val phoneTemperature: String

    val presetHome: String
    val presetOffice: String
    val presetCottage: String
    val presetParents: String
    val presetTransit: String
    val presetNoPlace: String
    val doseToday: String

    /**
     * Подпись плитки Главной: «Набралось сегодня».
     *
     * «Сегодня» — это отрезок времени, а не величина, и рядом с мощностью
     * дозы читалось как «доза сейчас». Слово «набралось» говорит, что число
     * копится, а не измеряется в данный момент.
     */
    val doseAccumulatedToday: String
    val placeFingerprint: String

    // --- настройки: корень ---
    val groupMeasurement: String
    val groupApp: String
    val groupOther: String
    val groupDevice: String
    val groupSystem: String
    val settingsAlarms: String
    val settingsAlarmsSub: String
    val settingsProfiles: String
    val settingsProfilesSub: String
    val settingsNotifications: String
    val settingsNotificationsSub: String
    val settingsView: String
    val settingsViewSub: String
    val settingsDevice: String
    val settingsDeviceSub: String
    val settingsBackup: String
    val searchWordsBackup: List<String>
    val settingsData: String
    val settingsDataSub: String
    val settingsProfilesNone: String

    /** Подсказка в поле поиска по настройкам. */
    val settingsSearchPlaceholder: String

    /** Поиск ничего не нашёл. */
    val settingsSearchEmpty: String

    /** Пустая половина list-detail: что делать дальше. */
    val settingsPickSection: String

    /**
     * Слова, по которым ищут раздел. Свои для каждого языка: перевод подписи
     * не совпадает со словом запроса.
     */
    val searchWordsAlarms: List<String>
    val searchWordsProfiles: List<String>
    val searchWordsSound: List<String>
    val searchWordsView: List<String>
    val searchWordsDevice: List<String>
    val searchWordsData: List<String>
    val searchWordsAbout: List<String>
    val settingsAbout: String

    /** Слив накопленного прибором: «история · 2 ч». */
    fun historySync(depth: String): String

    /** Журнал сырых смещений прибора — диагностика привязки времени. */
    val rawOffsetsTitle: String
    val rawOffsetsToggle: String
    val rawOffsetsNote: String
    val rawOffsetsSave: String
    val rawOffsetsClear: String
    fun rawOffsetsCollected(lines: Int): String

    /** Смена прибора: приложение ведёт один прибор за раз. */
    val switchDevice: String
    val switchDeviceNote: String
    val switchDeviceMixNote: String
    fun deviceCurrent(name: String): String

    /** Фильтр журнала по прибору: появляется, когда приборов больше одного. */
    val allDevices: String
    val noRecordsForDevice: String
    val unmarkedRecordsNote: String

    /** Список приборов: известные, найденные рядом, когда виделись. */
    val knownDevices: String
    val foundNearby: String
    val renameDevice: String
    fun deviceLastSeen(moment: String): String

    /** Выход из приложения: связь с прибором и служба закрываются. */
    val exitApp: String
    val exitAppNote: String
    val exitAppConfirm: String
    val settingsAboutSub: String

    // --- настройки: язык ---
    val languageTitle: String
    val languageSystem: String

    // --- статус Монитора ---
    val statusNoData: String

    /**
     * Тихое состояние до того, как собран обычный фон места.
     *
     * Раньше здесь стояло «Ниже вашего порога». Это не наблюдение, а
     * сравнение с ЧИСЛОМ ИЗ НАСТРОЕК, и как вывод оно не работало: оно верно
     * почти всегда, а значит не сообщает ничего и приучает не читать строку
     * вывода вовсе. Сравнение с порогом никуда не делось — оно уехало в
     * пояснение, которое видно при включённых «Пояснениях на экранах».
     */
    val statusMeasuring: String
    val statusAboveL1: String
    val statusUsual: String
    val statusUsualShort: String
    val statusAboveUsual: String
    val statusAboveThreshold: String
    val statusAboveThresholdShort: String
    val statusAlert: String

    /** Пояснение к тихому состоянию: с чем сравнивали и чего ещё нет. */
    fun explainMeasuring(threshold: String): String

    /** «порог L1 0,30 мкЗв/ч · обычный диапазон профиля ещё не собран». */
    fun detailNoBaseline(threshold: String): String

    /** «P10–P90: 0,09–0,14 мкЗв/ч · наблюдений: 26 ч». */
    fun detailUsual(range: String, collected: String): String

    /** «P10–P90 профиля: … · держится 4 мин». */
    fun detailAboveUsual(range: String, held: String): String

    /** «порог L1 … превышен · держится 40 с из 120 с до тревоги». */
    fun detailAboveThreshold(threshold: String, held: String, required: String): String

    fun detailAlert(reference: String, held: String): String
    fun referenceThreshold(threshold: String): String
    fun referenceProfileBand(range: String): String

    /** «держится 4 мин» — длительность вместе со словом. */
    fun held(text: String): String
    fun seconds(value: Long): String
    fun minutes(value: Long): String
    fun hoursMinutes(hours: Long, minutes: Long): String

    // --- свежесть потока ---
    fun agoSeconds(value: Long): String
    val streamRunning: String
    fun updatedAgo(value: Long): String
    val streamInterruptedFor: String

    // --- Поиск ---
    val searchNoBackground: String
    val searchWaiting: String
    val searchNoExcess: String
    val searchSmallChange: String
    val searchConfirmedExcess: String
    val searchConfirmedDeficit: String
    val countRising: String
    val countFalling: String
    val countSteady: String
    fun directionOverLast(seconds: Long): String
    val searchCannotCompare: String
    fun searchNotConfirmed(ratio: String?): String
    fun searchTooShort(confirmSeconds: String): String
    fun searchExcessExplained(confirmSeconds: String, ratio: String?): String
    fun searchDeficitExplained(confirmSeconds: String, ratio: String?): String
    fun ratioToBackground(ratio: String, interval: String?): String
    fun confidenceInterval(level: Int, low: String, high: String): String


    // --- первый запуск ---
    val onboardingBrand: String
    val onboardingConnectTitle: String
    val onboardingConnectBody: String
    val onboardingPermissions: String
    val onboardingBluetoothDenied: String
    val retry: String
    val start: String
    val onboardingBackgroundTitle: String
    val onboardingBackgroundBody: String
    val onboardingBatteryNote: String
    val later: String
    val allow: String
    val onboardingScanTitle: String
    val scanning: String
    val onboardingScanBody: String
    val onboardingScanFailed: String
    val connecting2: String
    val connect: String


    // --- Спектр ---
    val spectrumAccumulating: String
    val spectrumContinuation: String
    val formatUnsupportedTitle: String
    fun formatUnsupportedBody(version: Int): String
    val spectrumReading: String
    val noInstrumentLink: String
    val spectrumAfterConnect: String
    val exportFailedTitle: String
    val exportFailedBody: String
    val importAction: String
    val exportHtml: String
    val exportCsv: String
    val exportXml: String
    val exportN42: String
    val exportFormatsNote: String
    val savedToPrefix: String
    val continuationTitle: String
    val disable: String
    val snapshotDeltaPrefix: String
    fun sumImpossible(reason: String): String
    val sumShown: String
    val noLiveAccumulation: String
    val continuationWarning: String
    val scaleLinear: String
    val scalePower: String
    val scaleLog: String
    fun powerDegree(root: Int): String
    val spectrumModeRaw: String
    val spectrumModeMinusBackground: String
    val smoothing: String
    val energyRanges: String
    val peakTableEnergy: String
    val peakTableNet: String
    val peakTableSignificance: String
    val peakTableCandidate: String
    val notEnoughForPeaks: String
    val noPeaksFound: String
    val peakTableCaveat: String
    val reset: String
    val resetSpectrumTitle: String
    val resetSpectrumBody: String
    val cancel: String
    fun edgeCounts(counts: String): String
    val noSpectrumBackground: String


    // --- История ---
    fun sessionsCount(total: Long): String
    val selectAll: String
    val clearAll: String
    fun selectedCount(count: Int): String
    val readingJournal: String
    val noSessionsYet: String

    /** Пустая вкладка снимков: чем она наполнится и зачем. */
    val noSpectraYet: String
    val spectrumExplained: String

    /** Снимок, объявленный обычной обстановкой места. */
    val backgroundSpectrum: String
    val sessionExplained: String
    val showMore: String
    val accumulatedDose: String
    val days7: String
    val days30: String
    val doseProjection: String
    val noProfile: String
    val runningCannotDelete: String
    val running: String
    val avg: String
    val max: String
    val dose: String
    val track: String
    val spectrum: String
    val flight: String
    val noSamplesInSession: String
    val profileEllipsis: String
    val sessionProfileTitle: String
    fun sessionProfileBody(started: String): String
    val deviation: String
    val excursionPoint: String
    val usually: String
    val fileSaved: String
    val spectraTitle: String
    val compare: String
    val snapshotOpensActions: String

    /** История → снимок: открыть его на полном экране Спектра. */
    val openSnapshot: String

    /** Заголовок выбора второго снимка для сравнения. */
    val chooseSnapshotToCompare: String

    fun mergeAction(count: Int): String
    fun mergedSaved(label: String): String
    val mergeImpossible: String
    val compareWithAnother: String
    val continueAccumulation: String
    val continueAccumulationNote: String
    val importedTag: String
    val backgroundTag: String
    val delete: String


    // --- «Почему такой вывод» ---
    val evidenceLegend: String
    val nowSection: String

    /** «Обычный диапазон здесь» — пара к «Сейчас» над шкалой P10–P90. */
    val usualRangeHere: String

    /** Обязательная оговорка первого уровня: это отличие от СВОЕГО фона. */
    val notASafetyConclusion: String

    /** Первый уровень: сколько данных стоит за сравнением. */
    val dataVolume: String
    val usedForComparison: String
    val suitableMeasurements: String

    /** «Измерений: 1 800» — честная подпись: это показания, а не секунды. */
    val measurementsCount: String
    val measurementsCountNote: String

    /** Третий уровень: MAD, минутные интервалы, формулы. */
    val calculationsSection: String
    val countIsNotDose: String

    /** Чья это погрешность рядом с дозой — и чем она не является. */
    val deviceErrorNote: String
    val deviceErrorBudget: String

    /** Положение человеческими словами (первый уровень). */
    val insideUsualRange: String
    val aboveUsualRange: String
    val belowUsualRange: String

    /** Спектральное сравнение одной фразой, без χ² и z. */
    val spectralComparedPlain: String
    val spectralTooLittlePlain: String
    val shapeStatistics: String
    val poissonNote: String
    val dataSection: String
    val profile: String
    val outsideProfile: String
    val comparisonSection: String
    val historicalRange: String
    val notCollectedYet: String
    val comparisonRuns: String
    fun withThresholdL1(value: String): String
    val thresholdIsNotSafety: String
    val currentValue: String
    val position: String
    val bandExplained: String
    val belowP10: String
    val aboveP90: String
    val insideBand: String
    val profileStatistics: String
    val median: String
    val madNote: String
    val usableData: String

    /** Критерии отбора — там же, где число (§3: «критерии — в подробностях»). */
    val usableDataNote: String
    val minuteBuckets: String
    val honestN: String
    val notEnoughData: String
    val updating: String
    val temporarilyNotUpdating: String
    val updatingNote: String
    val notUpdatingNote: String
    val state: String

    /** Второй уровень (§12): заголовок разбора исключений и его строки. */
    val excludedSection: String
    val excludedNow: String
    val excludedFromStatistics: String
    val statisticsState: String
    val quarantineNote: String
    val howDetected: String
    val absoluteThresholdL1: String
    val relativeCriterion: String
    fun timesProfileP90(factor: String): String
    val minimumDuration: String
    val shorterNotAnnounced: String
    val returnCriterion: String
    val backBelowThreshold: String
    val exclusionAfterEvent: String
    val fromEndOfDeviation: String
    val criteriaNote: String
    val notEvaluated: String
    val notEnoughStatistics: String
    val noChangeDetected: String
    val changeDetected: String
    val spectralNoReference: String
    val spectralComparison: String


    // --- настройки: разделы ---
    val searchFeedbackTitle: String

    /** Раздел выбора вида индикатора «Наведения». */
    val feedbackClicks: String
    val feedbackTone: String
    val feedbackVibro: String
    val energyTone: String
    val energyToneNote: String
    val alarmTitle: String

    /** Вибрация телефона по порогу, заданному в приложении. */
    val alarmVibration: String
    val alarmVibrationNote: String
    val journalEpisodes: String
    val journalEpisodesNote: String
    val archiveSaved: String
    val archiveFailed: String
    val debugTitle: String
    val stateReport: String
    val debugBundleNote: String
    val whatIsWrong: String
    val whatIsWrongHint: String
    val saveDebugArchive: String
    val notConnected: String
    fun excludedBecause(reason: String): String
    val measurementsCounted: String
    val no: String
    val notRecorded: String
    fun createdAt(stamp: String): String
    val notCreated: String
    val skinTitle: String
    val skinTerminal: String
    val skinEightBit: String
    val themeSystem: String
    val themeDark: String
    val themeLight: String

    /** Уровень сигнала найденного прибора: «−72 дБм». */
    fun signalDbm(value: Int): String

    /** «от 0,30 мкЗв/ч или 2× к P90 профиля, держится 2 мин». */
    fun alarmPreset(level: String, level2: String, factor: String, held: String): String

    /** Подпись под двумя порогами: какой из них какой. */
    val thresholdLevelsNote: String

    /** Режим отклика Поиска в сегменте Настроек: нет · клики · тон · вибро. */
    /** Материал сцинтиллятора, у которого нет химической формулы. */
    /** Масштаб интерфейса: заголовок раздела, два ползунка и подпись. */
    /** Хранение сырых измерений: заголовок, варианты и пояснение. */
    val retentionTitle: String
    val retentionKeepAll: String
    fun retentionDays(days: Int): String
    val retentionNote: String

    val scaleTitle: String
    val scaleFont: String
    val scaleElements: String
    fun scalePercent(percent: Int): String
    val scaleReset: String

    val crystalOrganicPlastic: String

    val modeOff: String
    val modeClicks: String
    val modeTone: String
    val modeVibro: String
    val themeTitle: String
    val alarmsIntro: String
    val nowLabel: String
    val usuallyHere: String
    val thresholdL1: String
    val noBandToCompare: String
    val sensitivityNormal: String
    val sensitivityHigh: String
    val sensitivityCustom: String
    val sensitivityCustomNote: String

    /** Заголовок выбора режима тревоги. */
    val alarmModeTitle: String

    /** Строка со значением действующего порога. */
    val thresholdNow: String

    /** Второй критерий тревоги: «или 2× обычного уровня профиля». */
    fun relativeCriterion(factor: String): String

    /** Строка-дверь в объяснение: формулы живут за ней. */
    val howItWorks: String

    fun sensitivityNormalNote(held: String): String
    fun sensitivityHighNote(held: String): String
    val alarmSoundElsewhere: String
    val alarmSoundTitle: String
    val alarmSoundNote: String

    /** Канал тревоги выключен в системе — приложение включить его не может. */
    val alarmChannelBlocked: String
    val alarmNotificationsBlocked: String
    fun level1WithUnit(unit: String): String
    fun level2WithUnit(unit: String): String
    val saveLevels: String
    val enterNumbers: String
    val level1MustBePositive: String
    val level2BelowLevel1: String
    val levelsNote: String
    val profilesTitle: String
    val profilesIntro: String
    val profileNameHint: String
    val add: String
    val ownProfile: String
    val presets: String
    val active: String
    val archived: String
    val hiddenFromPicker: String
    val saveName: String
    val icon: String
    val autoByWifi: String
    val learnBackground: String
    val wifiNote: String
    val unbind: String
    val notOnWifi: String
    val networkAlreadyBound: String
    val bindCurrentNetwork: String
    val nestInProfile: String
    val standalone: String
    val unarchive: String
    val archiveAction: String
    val deleteProfile: String
    val deleteProfileQuestion: String
    val usualBackgroundTitle: String
    val usualBackgroundIntro: String
    val updateBackground: String
    val updateBackgroundNote: String
    val graceNote: String
    val instrumentTitle: String
    val modelLabel: String
    val serialNumber: String
    val firmware: String
    val bluetoothConnected: String
    val bluetoothConnecting: String
    fun bluetoothReconnecting(attempt: Int): String
    val bluetoothNoLink: String
    val serviceStopped: String
    val instrumentBattery: String
    val temperature: String
    val stream: String
    val streamActive: String

    /** Состояние потока (`ui/logic/StreamState`): одна строка на состояние. */
    fun streamNoNewData(seconds: Long): String
    val streamLost: String
    val streamNoDataYet: String
    fun lastMeasurementAgo(seconds: Long): String
    val unitsTitle: String
    val unitMicroSv: String
    val unitMicroR: String

    /**
     * Накопленная доза — своя единица, а не «мкЗв/ч» без «/ч»: обрезать
     * суффикс у переведённой строки нельзя, у «µSv/h» и «мкЗв/ч» он разный.
     */
    val unitDoseMicroSv: String
    val unitDoseMicroR: String
    val interfaceTitle: String

    /** Группа настроек цвета: подсветка отклонений и цвет следа. */
    val colorsTitle: String

    /** Группа настроек Главной: вкладки и блоки. */
    val homeLayoutTitle: String

    /** Одна строка о том, что делают пояснения на экранах. */
    val hintsNote: String

    /** Одна строка о том, что делает подсветка главного числа. */
    val doseTintNote: String
    val atLeastOneTab: String
    val monitorBlocksNote: String
    val blockTrend: String
    val blockDoseToday: String
    val blockHero: String
    val blockDoseChart: String
    val blockCountChart: String
    val blockHardnessChart: String
    val blockStats: String
    val resetInterface: String
    val visible: String
    val hidden: String
    val onShort: String
    val offShort: String
    val licencesUnreadable: String
    val licencesTitle: String
    val licencesBody: String
    val hideLicences: String
    val showLicences: String
    val reading: String
    val recentUpdates: String
    val whatChanged: String

    // --- сигналы прибора ---
    /** Автозапуск после перезагрузки — явная настройка, по умолчанию выкл. */
    val startOnBootTitle: String
    val startOnBootNote: String

    val deviceSignals: String
    val deviceSignalsNote: String
    val deviceSound: String
    val deviceVibro: String
    val deviceSignalsUnknownNote: String
    val deviceSignalsOfflineNote: String
    /** «медиана 0,12 · P25–P75 … · MAD … · n 26 минутных интервалов». */
    fun baselineStats(median: String, iqr: String, mad: String, buckets: Int): String
    /** Выключатель серых поясняющих строк (Настройки → Вид → Интерфейс). */
    val hintsTitle: String

    /** Выключатель окраски главного числа (Настройки → Вид → Интерфейс). */
    val doseTintTitle: String

    /** «Багровый при ×2 от обычного» — где цвет числа насыщается. */
    val doseTintFactorTitle: String
    fun doseTintFactorLabel(factor: String): String

    /** Чем заданы границы цвета следа на карте. */
    val mapScaleTitle: String
    val mapScaleAbsolute: String
    val mapScaleContrast: String
    val mapScaleManual: String
    val mapScaleDoseAnchors: String
    val mapScaleCpsAnchors: String
    val mapScaleManualHint: String

    val stateUnknown: String

    /** Прибор отказал в записи: молчащая кнопка неотличима от сломанной. */
    val stateRejected: String
    val stateOnByApp: String
    val stateOffByApp: String
    val on: String
    val off: String
}

/**
 * Все строки каталога одним списком — для проверок, которые обязаны
 * действовать на КАЖДУЮ формулировку языка, а не на выборочные.
 *
 * Список ведётся вручную вместе с интерфейсом: рефлексии в тестовом
 * classpath нет, а забытая строка означала бы непроверенный текст.
 */
fun Strings.allTexts(): List<String> = listOf(
    exitApp, exitAppNote, exitAppConfirm,
    switchDevice, switchDeviceNote, switchDeviceMixNote, deviceCurrent("RadiaCode"),
    allDevices, noRecordsForDevice, unmarkedRecordsNote, knownDevices, foundNearby, renameDevice, deviceLastSeen("вчера 18:42"),
    historySync("2 ч"),
    rawOffsetsTitle, rawOffsetsToggle, rawOffsetsNote, rawOffsetsSave, rawOffsetsClear,
    rawOffsetsCollected(42),
    tabHome, tabSpectrum, tabMap, tabHistory, back, close, settings,
    connected, connecting, reconnecting, serviceOff, noLink, noData,
    doseRate, countRate, hardness, trendPerHour, doseToday, placeFingerprint,
    groupMeasurement, groupApp, groupOther, groupDevice, groupSystem,
    settingsData, settingsDataSub, settingsBackup, settingsProfilesNone,
    settingsSearchPlaceholder, settingsSearchEmpty, settingsPickSection,
    settingsAlarms, settingsAlarmsSub, settingsProfiles, settingsProfilesSub,
    settingsNotifications, settingsNotificationsSub, settingsView, settingsViewSub,
    settingsDevice, settingsDeviceSub, settingsAbout, settingsAboutSub,
    cpsUnit, languageTitle, languageSystem,
    presetHome, presetOffice, presetCottage, presetParents, presetTransit, presetNoPlace,
    phoneTemperature,
    statusNoData, statusMeasuring, statusAboveL1, statusUsual, statusUsualShort,
    explainMeasuring("0,30 мкЗв/ч"),
    statusAboveUsual, statusAboveThreshold, statusAboveThresholdShort,
    statusAlert, streamRunning, streamInterruptedFor,
    detailNoBaseline("0,30"), detailUsual("0,09–0,14", "26 ч"),
    detailAboveUsual("0,09–0,14", held(minutes(4))),
    detailAboveThreshold("0,30", held(seconds(40)), minutes(2)),
    detailAlert(referenceThreshold("0,30"), held(seconds(45))),
    referenceProfileBand("0,09–0,14"),
    held(seconds(45)), seconds(45), minutes(4), hoursMinutes(1, 12),
    agoSeconds(5), updatedAgo(7),
    searchNoBackground, searchWaiting, searchNoExcess, searchSmallChange,
    searchConfirmedExcess, searchConfirmedDeficit,
    countRising, countFalling, countSteady, directionOverLast(10),
    searchCannotCompare, searchNotConfirmed(null), searchTooShort("4 с"),
    searchExcessExplained("4 с", null), searchDeficitExplained("4 с", null),
    ratioToBackground("1,8", null), confidenceInterval(95, "1,5", "2,2"),
    deviceSignals, deviceSignalsNote, deviceSound, deviceVibro,
    deviceSignalsUnknownNote, deviceSignalsOfflineNote,
    baselineStats("0,12", "0,11–0,14", "0,01", 26),
    hintsTitle, doseTintTitle, doseTintFactorTitle, doseTintFactorLabel("2"),
    mapScaleTitle, mapScaleAbsolute, mapScaleContrast, mapScaleManual,
    mapScaleDoseAnchors, mapScaleCpsAnchors, mapScaleManualHint, stateUnknown, stateRejected, stateOnByApp, stateOffByApp, on, off,
    onboardingBrand, onboardingConnectTitle, onboardingConnectBody, onboardingPermissions,
    onboardingBluetoothDenied, retry, start, onboardingBackgroundTitle, onboardingBackgroundBody,
    onboardingBatteryNote, later, allow, onboardingScanTitle, scanning, onboardingScanBody,
    onboardingScanFailed, connecting2, connect,
    spectrumAccumulating, spectrumContinuation,
    formatUnsupportedTitle, formatUnsupportedBody(2), spectrumReading, noInstrumentLink,
    spectrumAfterConnect, exportFailedTitle, exportFailedBody, importAction, exportXml,
    exportN42, exportFormatsNote, savedToPrefix, continuationTitle, disable,
    snapshotDeltaPrefix, sumImpossible("—"), sumShown, noLiveAccumulation,
    continuationWarning,
    scaleLinear, scalePower, scaleLog, powerDegree(2), spectrumModeRaw,
    spectrumModeMinusBackground, smoothing, energyRanges, peakTableEnergy, peakTableNet,
    peakTableSignificance, peakTableCandidate, notEnoughForPeaks, noPeaksFound,
    peakTableCaveat, reset, resetSpectrumTitle, resetSpectrumBody,
    cancel, edgeCounts("8 421"),
    noSpectrumBackground,
    sessionsCount(12), selectAll, clearAll, selectedCount(3), readingJournal, noSessionsYet,
    sessionExplained, showMore, journalEpisodes, journalEpisodesNote, accumulatedDose, days7, days30, doseProjection, noProfile,
    runningCannotDelete, running, avg, max, dose, track, spectrum, flight,
    noSamplesInSession, profileEllipsis, sessionProfileTitle, sessionProfileBody("12:00"),
    deviation, excursionPoint, usually, fileSaved, spectraTitle, compare,
    snapshotOpensActions, noSpectraYet, spectrumExplained, backgroundSpectrum,
    openSnapshot, chooseSnapshotToCompare,
    mergeAction(2), mergedSaved("x"), mergeImpossible, compareWithAnother,
    continueAccumulation, continueAccumulationNote, importedTag, backgroundTag, delete,
    evidenceLegend, nowSection, usualRangeHere, notASafetyConclusion,
    dataVolume, usedForComparison, suitableMeasurements,
    measurementsCount, measurementsCountNote, calculationsSection, countIsNotDose,
    deviceErrorNote, deviceErrorBudget,
    insideUsualRange, aboveUsualRange, belowUsualRange,
    spectralComparedPlain, spectralTooLittlePlain, shapeStatistics,
    poissonNote, dataSection, profile, outsideProfile,
    comparisonSection, historicalRange, notCollectedYet, comparisonRuns,
    withThresholdL1("0,30"), thresholdIsNotSafety, currentValue, position, bandExplained,
    belowP10, aboveP90, insideBand, profileStatistics, median, madNote, usableData,
    usableDataNote,
    minuteBuckets, honestN, notEnoughData, updating, temporarilyNotUpdating, updatingNote,
    notUpdatingNote, state, excludedSection, excludedNow, excludedFromStatistics,
    statisticsState, quarantineNote,
    howDetected, absoluteThresholdL1, relativeCriterion, timesProfileP90("×2"),
    minimumDuration, shorterNotAnnounced, returnCriterion, backBelowThreshold,
    exclusionAfterEvent, fromEndOfDeviation, criteriaNote, notEvaluated,
    notEnoughStatistics, noChangeDetected, changeDetected, spectralNoReference,
    spectralComparison,
    searchFeedbackTitle, feedbackClicks,
    feedbackTone, feedbackVibro, energyTone,
    energyToneNote, alarmTitle, archiveSaved,
    archiveFailed, debugTitle, stateReport,
    debugBundleNote, whatIsWrong, whatIsWrongHint,
    saveDebugArchive, notConnected, measurementsCounted,
    no, notRecorded, notCreated,
    skinTitle, skinTerminal, skinEightBit,
    themeSystem, themeDark, themeLight,
    signalDbm(-72), alarmPreset("0,30", "1,00", "2", held(minutes(2))), thresholdLevelsNote,
    retentionTitle, retentionKeepAll, retentionDays(90), retentionNote,
    scaleTitle, scaleFont, scaleElements, scalePercent(100), scaleReset,
    crystalOrganicPlastic, modeOff, modeClicks, modeTone, modeVibro,
    themeTitle, alarmsIntro,
    nowLabel, usuallyHere, thresholdL1,
    noBandToCompare, sensitivityNormal, sensitivityHigh,
    sensitivityCustom, sensitivityCustomNote, alarmSoundElsewhere,
    alarmModeTitle, thresholdNow, relativeCriterion("2"), howItWorks,
    sensitivityNormalNote("2 мин"), sensitivityHighNote("1 мин"),
    alarmSoundTitle, alarmSoundNote, alarmChannelBlocked, alarmNotificationsBlocked, saveLevels,
    enterNumbers, level1MustBePositive, level2BelowLevel1,
    levelsNote, profilesTitle, profilesIntro,
    profileNameHint, add, ownProfile,
    presets, active, archived,
    hiddenFromPicker, saveName, icon,
    autoByWifi, learnBackground, wifiNote,
    unbind, notOnWifi, networkAlreadyBound,
    bindCurrentNetwork, nestInProfile, standalone,
    unarchive, archiveAction, deleteProfile,
    deleteProfileQuestion, usualBackgroundTitle, usualBackgroundIntro,
    updateBackground, updateBackgroundNote, graceNote, instrumentTitle,
    modelLabel, serialNumber, firmware,
    bluetoothConnected, bluetoothConnecting, bluetoothNoLink,
    startOnBootTitle, startOnBootNote,
    serviceStopped, instrumentBattery, temperature,
    stream, streamActive, streamNoNewData(8), streamLost,
    streamNoDataYet, lastMeasurementAgo(120), unitsTitle,
    unitMicroSv, unitMicroR,
    unitDoseMicroSv, unitDoseMicroR, interfaceTitle,
    atLeastOneTab,
    colorsTitle, homeLayoutTitle, hintsNote, doseTintNote,
    monitorBlocksNote, blockTrend, blockDoseToday,
    blockHero, blockDoseChart, blockCountChart, blockHardnessChart, blockStats,
    resetInterface, visible, hidden,
    onShort, offShort, licencesUnreadable,
    licencesTitle, licencesBody, hideLicences,
    showLicences, reading, recentUpdates,
    whatChanged, excludedBecause("x"), createdAt("x"),
    level1WithUnit("x"), level2WithUnit("x"), bluetoothReconnecting(1),





)

val LocalStrings = staticCompositionLocalOf<Strings> { RuStrings }

/** Каталог по языку — единственная точка, где язык превращается в строки. */
fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.EN -> EnStrings
    else -> RuStrings
}
