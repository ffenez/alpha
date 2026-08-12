package app.radiacode.ui.text

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
         * английский: он читается большим числом людей, чем русский, и это
         * единственный честный дефолт для неизвестного случая.
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
 * Формулировки этого приложения — не подписи к кнопкам, а часть научной
 * честности: у нас десятки тестов пинят конкретные слова («не оценивалось» ≠
 * «не обнаружено», запрет «безопасно», «различие не выделено» вместо «в
 * пределах шума»). Эти правила живут в ЧИСТОМ JVM-коде (`ui/logic`,
 * `analysis`), который тестируется без Android — а `strings.xml` требует
 * `Context` и в такие тесты не приходит. Плюс половина строк собирается из
 * чисел, единиц и русских склонений кодом, а не подстановкой в шаблон.
 *
 * Поэтому каталог — обычный Kotlin-интерфейс: он доступен и композициям, и
 * чистой логике, и тестам, а компилятор не даёт забыть строку при добавлении
 * языка.
 *
 * ## Как переводится
 *
 * Перевод — не подстановка слов, а перенос ПРАВИЛА. Для каждого языка вместе
 * с текстом переносится и запрет: в английской реализации «safe», «normal» и
 * «dangerous» запрещены ровно так же, как «безопасно» и «норма» в русской, и
 * это проверяется тестом.
 */
interface Strings {

    val language: AppLanguage

    // --- вкладки и навигация ---
    val tabHome: String
    val tabSearch: String
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
    val hardness: String
    val trendPerHour: String
    val doseToday: String
    val whyThisConclusion: String
    val placeFingerprint: String

    // --- настройки: корень ---
    val groupMeasurement: String
    val groupApp: String
    val groupOther: String
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
    val settingsAbout: String
    val settingsAboutSub: String

    // --- настройки: язык ---
    val languageTitle: String
    val languageSystem: String

    // --- статус Монитора ---
    val statusNoData: String
    val statusAboveL1: String
    val statusBelowL1: String
    val statusUsual: String
    val statusUsualShort: String
    val statusAboveUsual: String
    val statusAboveUsualShort: String
    val statusAboveThreshold: String
    val statusAboveThresholdShort: String
    val statusAlert: String

    /** «порог L1 0,30 мкЗв/ч · исторический диапазон профиля ещё не собран». */
    fun detailNoBaseline(threshold: String): String

    /** «P10–P90: 0,09–0,14 мкЗв/ч · наблюдений: 26 ч». */
    fun detailUsual(range: String, unit: String, collected: String): String

    /** «P10–P90 профиля: … · держится 4 мин». */
    fun detailAboveUsual(range: String, unit: String, held: String): String

    /** «порог L1 … превышен · держится 40 с из 120 с до тревоги». */
    fun detailAboveThreshold(threshold: String, heldSeconds: Long, requiredSeconds: Long): String

    fun detailAlert(reference: String, held: String): String
    fun referenceThreshold(threshold: String): String
    fun referenceProfileBand(range: String, unit: String): String

    /** «держится 4 мин» — длительность вместе со словом. */
    fun held(text: String): String
    fun seconds(value: Long): String
    fun minutes(value: Long): String
    fun hoursMinutes(hours: Long, minutes: Long): String

    // --- свежесть потока ---
    fun agoSeconds(value: Long): String
    fun interruptedAgo(value: Long): String
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
    val spectrogramEntry: String
    val radonEntry: String
    val formatUnsupportedTitle: String
    fun formatUnsupportedBody(version: Int): String
    val spectrumReading: String
    val noInstrumentLink: String
    val spectrumAfterConnect: String
    val exportFailedTitle: String
    val exportFailedBody: String
    val importAction: String
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
    val spectrumInfoTitle: String
    val spectrumInfoAxes: String
    val spectrumInfoSignificance: String
    val spectrumInfoCandidate: String
    val spectrumInfoScales: String
    val spectrumInfoGestures: String
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
    val recordBackground: String
    val save: String
    val reset: String
    val resetSpectrumTitle: String
    val resetSpectrumBody: String
    val cancel: String
    fun edgeCounts(counts: String): String
    fun rangeWhole(range: String): String
    fun rangeDraggable(range: String): String
    val noSpectrumBackground: String


    // --- История ---
    fun sessionsCount(total: Long): String
    val selectAll: String
    val clearAll: String
    fun selectedCount(count: Int): String
    val readingJournal: String
    val noSessionsYet: String
    val sessionExplained: String
    val showMore: String
    val accumulatedDose: String
    val calculatedTag: String
    val partialDayNote: String
    fun todayWithUnit(unit: String): String
    val days7: String
    val days30: String
    val accumulatedDoseNote: String
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
    val merge: String
    val markForDeletion: String
    val pickTwoToCompare: String
    val pickTwoOrMoreToMerge: String
    val snapshotOpensActions: String
    fun mergeAction(count: Int): String
    fun mergedSaved(label: String): String
    val mergeImpossible: String
    val compareWithAnother: String
    val continueAccumulation: String
    val continueAccumulationNote: String
    val importedTag: String
    val backgroundTag: String
    val delete: String

    // --- сигналы прибора ---
    val deviceSignals: String
    val deviceSignalsNote: String
    val deviceSound: String
    val deviceVibro: String
    val stateUnknown: String
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
    tabHome, tabSearch, tabSpectrum, tabMap, tabHistory, back, close, settings,
    connected, connecting, reconnecting, serviceOff, noLink, noData,
    doseRate, countRate, hardness, trendPerHour, doseToday, whyThisConclusion, placeFingerprint,
    groupMeasurement, groupApp, groupOther,
    settingsAlarms, settingsAlarmsSub, settingsProfiles, settingsProfilesSub,
    settingsNotifications, settingsNotificationsSub, settingsView, settingsViewSub,
    settingsDevice, settingsDeviceSub, settingsAbout, settingsAboutSub,
    languageTitle, languageSystem,
    statusNoData, statusAboveL1, statusBelowL1, statusUsual, statusUsualShort,
    statusAboveUsual, statusAboveUsualShort, statusAboveThreshold, statusAboveThresholdShort,
    statusAlert, streamRunning, streamInterruptedFor,
    detailNoBaseline("0,30"), detailUsual("0,09–0,14", "мкЗв/ч", "26 ч"),
    detailAboveUsual("0,09–0,14", "мкЗв/ч", held(minutes(4))),
    detailAboveThreshold("0,30", 40L, 120),
    detailAlert(referenceThreshold("0,30"), held(seconds(45))),
    referenceProfileBand("0,09–0,14", "мкЗв/ч"),
    held(seconds(45)), seconds(45), minutes(4), hoursMinutes(1, 12),
    agoSeconds(5), interruptedAgo(30), updatedAgo(7),
    searchNoBackground, searchWaiting, searchNoExcess, searchSmallChange,
    searchConfirmedExcess, searchConfirmedDeficit,
    countRising, countFalling, countSteady, directionOverLast(10),
    searchCannotCompare, searchNotConfirmed(null), searchTooShort("4 с"),
    searchExcessExplained("4 с", null), searchDeficitExplained("4 с", null),
    ratioToBackground("1,8", null), confidenceInterval(95, "1,5", "2,2"),
    deviceSignals, deviceSignalsNote, deviceSound, deviceVibro,
    stateUnknown, stateOnByApp, stateOffByApp, on, off,
    onboardingBrand, onboardingConnectTitle, onboardingConnectBody, onboardingPermissions,
    onboardingBluetoothDenied, retry, start, onboardingBackgroundTitle, onboardingBackgroundBody,
    onboardingBatteryNote, later, allow, onboardingScanTitle, scanning, onboardingScanBody,
    onboardingScanFailed, connecting2, connect,
    spectrumAccumulating, spectrumContinuation, spectrogramEntry, radonEntry,
    formatUnsupportedTitle, formatUnsupportedBody(2), spectrumReading, noInstrumentLink,
    spectrumAfterConnect, exportFailedTitle, exportFailedBody, importAction, exportXml,
    exportN42, exportFormatsNote, savedToPrefix, continuationTitle, disable,
    snapshotDeltaPrefix, sumImpossible("—"), sumShown, noLiveAccumulation,
    continuationWarning, spectrumInfoTitle, spectrumInfoAxes, spectrumInfoSignificance,
    spectrumInfoCandidate, spectrumInfoScales, spectrumInfoGestures,
    scaleLinear, scalePower, scaleLog, powerDegree(2), spectrumModeRaw,
    spectrumModeMinusBackground, smoothing, energyRanges, peakTableEnergy, peakTableNet,
    peakTableSignificance, peakTableCandidate, notEnoughForPeaks, noPeaksFound,
    peakTableCaveat, recordBackground, save, reset, resetSpectrumTitle, resetSpectrumBody,
    cancel, edgeCounts("8 421"), rangeWhole("20–3000"), rangeDraggable("100–1500"),
    noSpectrumBackground,
    sessionsCount(12), selectAll, clearAll, selectedCount(3), readingJournal, noSessionsYet,
    sessionExplained, showMore, accumulatedDose, calculatedTag, partialDayNote,
    todayWithUnit("мкЗв"), days7, days30, accumulatedDoseNote, doseProjection, noProfile,
    runningCannotDelete, running, avg, max, dose, track, spectrum, flight,
    noSamplesInSession, profileEllipsis, sessionProfileTitle, sessionProfileBody("12:00"),
    deviation, excursionPoint, usually, fileSaved, spectraTitle, compare, merge,
    markForDeletion, pickTwoToCompare, pickTwoOrMoreToMerge, snapshotOpensActions,
    mergeAction(2), mergedSaved("x"), mergeImpossible, compareWithAnother,
    continueAccumulation, continueAccumulationNote, importedTag, backgroundTag, delete,



)

val LocalStrings = staticCompositionLocalOf<Strings> { RuStrings }

/** Каталог по языку — единственная точка, где язык превращается в строки. */
fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.EN -> EnStrings
    else -> RuStrings
}
