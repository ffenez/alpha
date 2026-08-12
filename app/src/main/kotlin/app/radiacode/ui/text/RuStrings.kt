package app.radiacode.ui.text

/** Русский каталог — исходный язык приложения. */
object RuStrings : Strings {

    override val language = AppLanguage.RU

    override val tabHome = "Главная"
    override val tabSearch = "Поиск"
    override val tabSpectrum = "Спектр"
    override val tabMap = "Карта"
    override val tabHistory = "История"
    override val back = "Назад"
    override val close = "Закрыть"
    override val settings = "Настройки"

    override val connected = "подключён"
    override val connecting = "подключение"
    override val reconnecting = "переподкл."
    override val serviceOff = "служба выкл."
    override val noLink = "нет связи"
    override val noData = "нет данных"

    override val doseRate = "Мощность дозы"
    override val countRate = "Скорость счёта"
    override val trendPerHour = "Тренд/ч"
    override val doseToday = "Сегодня"
    override val whyThisConclusion = "почему такой вывод ›"
    override val placeFingerprint = "Отпечаток места"

    override val groupMeasurement = "Измерение"
    override val groupApp = "Приложение"
    override val groupOther = "Другое"
    override val settingsAlarms = "Тревоги"
    override val settingsAlarmsSub = "пороги, длительность, чувствительность"
    override val settingsProfiles = "Профили и фон"
    override val settingsProfilesSub = "места, сети Wi-Fi, обучение обычного фона"
    override val settingsNotifications = "Уведомления и отклик"
    override val settingsNotificationsSub = "звук Поиска, вибрация, тревога"
    override val settingsView = "Вид"
    override val settingsViewSub = "язык, тема, единицы, вкладки и блоки Главной"
    override val settingsDevice = "Прибор"
    override val settingsDeviceSub = "модель, прошивка, звук и вибрация прибора"
    override val settingsAbout = "О приложении"
    override val settingsAboutSub = "версия, обновления, лицензии, диагностика"

    override val languageTitle = "Язык"
    override val languageSystem = "Системный"

    override val deviceSignals = "Сигналы прибора"
    override val deviceSignalsNote =
        "Звук и вибрация самого прибора. Они работают, даже когда телефон отключён " +
            "или приложение закрыто, и не связаны с откликом Поиска."
    override val deviceSound = "Звук прибора"
    override val deviceVibro = "Вибрация прибора"
    override val stateUnknown = "состояние неизвестно"
    override val stateOnByApp = "включено этим приложением"
    override val stateOffByApp = "выключено этим приложением"
    override val on = "Вкл"
    override val off = "Выкл"
}
