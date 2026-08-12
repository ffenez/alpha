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
)

val LocalStrings = staticCompositionLocalOf<Strings> { RuStrings }

/** Каталог по языку — единственная точка, где язык превращается в строки. */
fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.EN -> EnStrings
    else -> RuStrings
}
