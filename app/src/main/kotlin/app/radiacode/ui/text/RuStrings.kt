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
    override val hardness = "Жёсткость"
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

    override val statusNoData = "Нет данных"
    override val statusAboveL1 = "Выше порога L1"
    override val statusBelowL1 = "Ниже порога L1"
    override val statusUsual = "В обычном диапазоне этого профиля"
    override val statusUsualShort = "Обычный для этого места"
    override val statusAboveUsual = "Выше обычного диапазона профиля"
    override val statusAboveUsualShort = "Выше обычного"
    override val statusAboveThreshold = "Выше вашего порога тревоги"
    override val statusAboveThresholdShort = "Выше порога"
    override val statusAlert = "Уровень радиации изменился"

    override fun detailNoBaseline(threshold: String) =
        "порог L1 $threshold · исторический диапазон профиля ещё не собран"

    override fun detailUsual(range: String, unit: String, collected: String) =
        "P10–P90: $range $unit · наблюдений: $collected"

    override fun detailAboveUsual(range: String, unit: String, held: String) =
        "P10–P90 профиля: $range $unit · $held"

    override fun detailAboveThreshold(threshold: String, heldSeconds: Long, requiredSeconds: Long) =
        "порог L1 $threshold превышен · держится $heldSeconds с из $requiredSeconds с до тревоги"

    override fun detailAlert(reference: String, held: String) = "$reference · $held"

    override fun referenceThreshold(threshold: String) = "порог L1 $threshold"

    override fun referenceProfileBand(range: String, unit: String) =
        "P10–P90 профиля: $range $unit"

    override fun held(text: String) = "держится $text"

    override fun seconds(value: Long) = "$value с"

    override fun minutes(value: Long) = "$value мин"

    override fun hoursMinutes(hours: Long, minutes: Long) = "$hours ч $minutes мин"

    override fun agoSeconds(value: Long) = "$value с назад"

    override fun interruptedAgo(value: Long) = "прервано $value с назад"

    override val streamRunning = "поток идёт"

    override fun updatedAgo(value: Long) = "обновлено $value с назад"

    override val streamInterruptedFor = "поток прерван"

    override val searchNoBackground = "Фон не записан — сравнивать не с чем"
    override val searchWaiting = "Ждём данные прибора"
    override val searchNoExcess = "Превышение над фоном не обнаружено"
    override val searchSmallChange = "Небольшое изменение — пока недостаточно данных"
    override val searchConfirmedExcess = "Устойчивое превышение фонового счёта"
    override val searchConfirmedDeficit = "Счёт устойчиво ниже записанного фона"
    override val countRising = "счёт растёт"
    override val countFalling = "счёт снижается"
    override val countSteady = "счёт не меняется"

    override fun directionOverLast(seconds: Long) = "по последним $seconds с"

    override val searchCannotCompare =
        "Без записанного фона и живого потока данных сравнение невозможно."

    override fun searchNotConfirmed(ratio: String?) =
        "Различие с записанным фоном не подтверждено статистикой счёта" +
            (ratio?.let { ": $it" } ?: ".")

    override fun searchTooShort(confirmSeconds: String) =
        "Отличие есть, но держится меньше $confirmSeconds — по одному короткому окну " +
            "вывод не делается."

    override fun searchExcessExplained(confirmSeconds: String, ratio: String?) =
        "Скорость счёта выше записанного фона дольше $confirmSeconds" +
            (ratio?.let { ", $it" } ?: "") +
            ". Это утверждение о скорости счёта, а не о дозе и не об изотопе."

    override fun searchDeficitExplained(confirmSeconds: String, ratio: String?) =
        "Скорость счёта ниже записанного фона дольше $confirmSeconds" +
            (ratio?.let { ", $it" } ?: "") +
            ". Так выглядит уход от источника или экранирование."

    override fun ratioToBackground(ratio: String, interval: String?) =
        "×$ratio к записанному фону" + (interval ?: "")

    override fun confidenceInterval(level: Int, low: String, high: String) =
        " ($level % интервал $low–$high)"

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
