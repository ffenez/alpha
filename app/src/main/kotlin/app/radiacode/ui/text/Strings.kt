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
    doseRate, countRate, trendPerHour, doseToday, whyThisConclusion, placeFingerprint,
    groupMeasurement, groupApp, groupOther,
    settingsAlarms, settingsAlarmsSub, settingsProfiles, settingsProfilesSub,
    settingsNotifications, settingsNotificationsSub, settingsView, settingsViewSub,
    settingsDevice, settingsDeviceSub, settingsAbout, settingsAboutSub,
    languageTitle, languageSystem,
    deviceSignals, deviceSignalsNote, deviceSound, deviceVibro,
    stateUnknown, stateOnByApp, stateOffByApp, on, off,
)

val LocalStrings = staticCompositionLocalOf<Strings> { RuStrings }

/** Каталог по языку — единственная точка, где язык превращается в строки. */
fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.EN -> EnStrings
    else -> RuStrings
}
