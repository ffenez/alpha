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
    override val settingsViewSub = "язык, оформление, тема, единицы, блоки Главной"
    override val settingsDevice = "Прибор"
    override val settingsDeviceSub = "модель, прошивка, звук и вибрация прибора"
    override val settingsAbout = "О приложении"
    override val settingsAboutSub = "версия, обновления, лицензии, диагностика"

    override val languageTitle = "Язык"
    override val languageSystem = "Системный"

    override val statusNoData = "Нет данных"
    override val statusAboveL1 = "Выше порога L1"
    override val statusBelowL1 = "Ниже порога L1"
    override val statusUsual = "Показания обычны для этого места"
    override val statusUsualShort = "Обычный для этого места"
    override val statusAboveUsual = "Выше обычного диапазона профиля"
    override val statusAboveUsualShort = "Выше обычного"
    override val statusAboveThreshold = "Выше вашего порога тревоги"
    override val statusAboveThresholdShort = "Выше порога"
    override val statusAlert = "Уровень радиации изменился"

    override fun detailNoBaseline(threshold: String) =
        "порог L1 $threshold · обычный диапазон профиля ещё не собран"

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

    override val onboardingBrand = "Alpha"
    override val onboardingConnectTitle = "Подключение прибора"
    override val onboardingConnectBody =
        "Приложение подключается к дозиметру RadiaCode по Bluetooth и непрерывно " +
            "записывает уровень фона. Все измерения остаются на этом телефоне."
    override val onboardingPermissions =
        "Понадобятся разрешения: Bluetooth — чтобы найти и подключить прибор, " +
            "уведомления — чтобы показывать измерение, пока приложение свёрнуто."
    override val onboardingBluetoothDenied =
        "Без разрешения на Bluetooth прибор найти нельзя. Если запрос больше не " +
            "показывается — включите разрешение в настройках Android для этого приложения."
    override val retry = "Повторить"
    override val start = "Начать"
    override val onboardingBackgroundTitle = "Работа в фоне"
    override val onboardingBackgroundBody =
        "Чтобы запись фона не прерывалась ночью и при закрытом экране, исключите " +
            "приложение из оптимизации батареи. Иначе Android со временем разорвёт " +
            "связь с прибором."
    override val onboardingBatteryNote = "Это увеличит расход батареи — обычно незначительно."
    override val later = "Позже"
    override val allow = "Разрешить"
    override val onboardingScanTitle = "Поиск прибора"
    override val scanning = "ищем приборы рядом…"
    override val onboardingScanBody =
        "Включите прибор и держите его рядом. Официальное приложение RadiaCode должно " +
            "быть закрыто: прибор соединяется только с одним телефоном."
    override val onboardingScanFailed =
        "Поиск не запустился. Проверьте, что Bluetooth включён, и откройте приложение заново."
    override val connecting2 = "подключение…"
    override val connect = "Подключить"

    override val spectrumAccumulating = "текущее накопление"
    override val spectrumContinuation = "продолжение: "
    override val formatUnsupportedTitle = "Формат не поддержан"

    override fun formatUnsupportedBody(version: Int) =
        "Прибор передаёт спектр в формате версии $version, который это приложение пока " +
            "не умеет читать. Остальные экраны работают как обычно."

    override val spectrumReading = "читаем спектр с прибора…"
    override val noInstrumentLink = "нет соединения с прибором"
    override val spectrumAfterConnect =
        "Спектр появится после подключения — статус соединения виден на Главной."
    override val exportFailedTitle = "Экспорт не удался"
    override val exportFailedBody = "Файл не записался — попробуйте другую папку."
    override val importAction = "Импорт"
    override val exportXml = "Экспорт XML"
    override val exportN42 = "Экспорт N42"
    override val exportFormatsNote =
        "XML — формат приложения RadiaCode · N42 — стандарт программ анализа · " +
            "импортированный снимок появится в Истории"
    override val savedToPrefix = " · файл сохранён в "
    override val continuationTitle = "Продолжение накопления"
    override val disable = "отключить"
    override val snapshotDeltaPrefix = " · Δt снимка "

    override fun sumImpossible(reason: String) =
        "сумма невозможна: $reason — показано текущее накопление"

    override val sumShown =
        "показана сумма снимка и текущего накопления (каналы складываются, Δt " +
            "суммируется); «Сохранить снимок» сохранит сумму"
    override val noLiveAccumulation = "живого накопления пока нет — показан сохранённый снимок"
    override val continuationWarning =
        "Прибор копит спектр независимо от приложения. Если снимок сделан из текущего " +
            "накопления без сброса, импульсы посчитаются дважды — сначала сбросьте спектр."
    override val scaleLinear = "Лин"
    override val scalePower = "Степень"
    override val scaleLog = "Лог"

    override fun powerDegree(root: Int) = "степень 1/$root"

    override val spectrumModeRaw = "Спектр"
    override val spectrumModeMinusBackground = "− фон"
    override val smoothing = "сглаж."
    override val energyRanges = "энергетические диапазоны"
    override val peakTableEnergy = "E, кэВ"
    // «нетто» и «кандидат» — слова протокола обработки, а не разговора о
    // спектре (§3). Что площадь чистая и что совпадение — не обнаружение,
    // сказано словами под таблицей, где это и читают.
    override val peakTableNet = "площадь"
    override val peakTableSignificance = "значимость"
    override val peakTableCandidate = "возможное совпадение"
    override val notEnoughForPeaks = "мало данных для анализа пиков — накопите хотя бы минуту"
    override val noPeaksFound = "выраженных пиков над континуумом не найдено"
    // Что такое «площадь», объясняет справка «i»: на рабочем экране осталась
    // только та половина оговорки, которая ограничивает ВЫВОД.
    override val peakTableCaveat =
        "возможное совпадение ≠ обнаружение · нужно подтверждение: копите дольше"
    override val reset = "Сброс"
    override val resetSpectrumTitle = "Сбросить спектр?"
    override val resetSpectrumBody =
        "Накопление начнётся заново — на приборе спектр тоже очистится. Сохранённые " +
            "снимки останутся в Истории."
    override val cancel = "Отмена"

    override fun edgeCounts(counts: String) = "у верхней границы шкалы: $counts имп."

    override val noSpectrumBackground =
        "фон не записан — запишите спектр обычной обстановки, появятся наложение и «минус фон»"

    override fun sessionsCount(total: Long) = "$total сессий"

    override val selectAll = "Выбрать всё"
    override val clearAll = "Снять всё"

    override fun selectedCount(count: Int) = "выбрано: $count"

    override val readingJournal = "читаю журнал…"
    override val noSessionsYet = "сессий пока нет"
    override val sessionExplained =
        "Сессия — непрерывный период измерения: она начинается при подключении прибора " +
            "и закрывается при отключении."
    override val showMore = "Показать ещё"
    override val accumulatedDose = "Накопленная доза"
    override val calculatedTag = "расчёт"
    override val partialDayNote =
        "полые столбцы — день измерен не полностью: доза накоплена только за время " +
            "записи, а не за сутки"

    override fun todayWithUnit(unit: String) = "сегодня, $unit"

    override val days7 = "7 дней"
    override val days30 = "30 дней"
    override val accumulatedDoseNote =
        "Сумма мощности дозы по секундам измерения — не путать с текущей мощностью дозы."
    override val doseProjection = "Проекция дозы"
    override val noProfile = "Без профиля"
    override val runningCannotDelete = "· идёт, нельзя удалить"
    override val running = "· идёт"
    override val avg = "ср"
    override val max = "макс"
    override val dose = "доза"
    override val track = "трек"
    override val spectrum = "спектр"
    override val flight = "полёт"
    override val noSamplesInSession = "измерений в этой сессии не записано"
    override val profileEllipsis = "профиль…"
    override val sessionProfileTitle = "Профиль сессии"

    override fun sessionProfileBody(started: String) =
        "Сессия от $started. Измерения перейдут в статистику выбранного профиля."

    override val deviation = "Отклонение"
    override val excursionPoint = "Точка превышения"
    override val usually = "обычно"
    override val fileSaved = "файл сохранён"
    override val spectraTitle = "Спектры"
    override val compare = "сравнить"
    override val merge = "объединить"
    override val markForDeletion = "отметьте снимки, которые нужно удалить"
    override val pickTwoToCompare = "выберите два снимка — откроется сравнение"
    override val pickTwoOrMoreToMerge =
        "отметьте два и более снимков — каналы сложатся, время накопления просуммируется"
    override val snapshotOpensActions = "снимок открывается целиком: кривая, пики и действия"
    override val openSnapshot = "Открыть спектр"
    override val chooseSnapshotToCompare = "С каким снимком сравнить"

    override fun mergeAction(count: Int) = "Объединить ($count)"

    override fun mergedSaved(label: String) =
        "объединённый снимок «$label» сохранён — он появился в списке"

    override val mergeImpossible = "Объединить нельзя"
    override val compareWithAnother = "Сравнить с другим…"
    override val continueAccumulation = "Продолжить накопление"
    override val continueAccumulationNote =
        "снимок сложится с текущим накоплением на экране Спектр — прибор при этом копит " +
            "независимо"
    override val importedTag = "импорт"
    override val backgroundTag = "фон"
    override val delete = "Удалить"

    // Легенда стоит только там, где стоят сами метки, — за раскрытием «показать
    // методику и расчёты» (§21): на первом уровне меток больше нет.
    override val evidenceLegend =
        "Источник значения: изм. — измерено прибором · расчёт — вычислено из измерений · " +
            "стат. — вывод статистической модели профиля"
    override val nowSection = "Сейчас"
    override val usualRangeHere = "Обычный диапазон здесь"
    override val notASafetyConclusion =
        "Этот вывод описывает отличие от вашего обычного фона в этом месте. Он не является " +
            "заключением о радиационной безопасности."
    override val dataVolume = "Сколько данных"
    override val usedForComparison = "Использовано для сравнения"
    override val suitableMeasurements = "подходящих измерений этого места"
    override val measurementsCount = "Измерений"
    override val measurementsCountNote =
        "показаний прибора, использованных для статистики. Прибор пишет примерно раз в " +
            "секунду, но при пропусках это число меньше времени наблюдений"
    override val calculationsSection = "Расчёты и формулы"
    override val countIsNotDose =
        "счёт удобен для поиска изменений, но сам по себе не показывает дозу: вклад события " +
            "зависит в том числе от его энергии"
    override val deviceErrorNote =
        "± у мощности дозы — собственная оценка прибора для этого показания"
    override val deviceErrorBudget =
        "± прибора — не полная неопределённость измерения: в неё входили бы ещё калибровка и " +
            "систематические эффекты."
    override val insideUsualRange = "внутри обычного диапазона"
    override val aboveUsualRange = "выше обычного диапазона"
    override val belowUsualRange = "ниже обычного диапазона"
    override val spectralComparedPlain =
        "Форма текущего спектра сравнивается с обычной для этого места, а не с абсолютным " +
            "уровнем. Вывод описывает состав излучения."
    override val spectralTooLittlePlain =
        "Сравнение с эталоном места началось, но данных пока мало."
    override val shapeStatistics = "Статистика сравнения формы"
    override val poissonNote = "Погрешность счёта — 1σ Пуассона ≈ √(N/τ), τ = 1 с."
    override val dataSection = "Данные"
    override val profile = "Профиль"
    override val outsideProfile = "вне профиля"
    override val comparisonSection = "Сравнение с профилем"
    override val historicalRange = "Обычный диапазон этого места"
    override val notCollectedYet = "ещё не собран"
    override val comparisonRuns = "Сравнение идёт"

    override fun withThresholdL1(value: String) = "с порогом L1 $value"

    override val thresholdIsNotSafety =
        "Порог L1 — параметр тревоги приложения, а не граница безопасности."
    override val currentValue = "Текущее значение"
    override val position = "Положение"
    override val bandExplained =
        "Внутри этого диапазона находились около 80 % подходящих измерений этого места " +
            "(от P10 до P90). Это история самого места, а не норматив радиационной " +
            "безопасности."
    override val belowP10 = "ниже P10"
    override val aboveP90 = "выше P90"
    override val insideBand = "внутри P10–P90"
    override val profileStatistics = "Статистика профиля"
    override val median = "Медиана"
    override val madNote =
        "median(|xᵢ − медиана|) — робастная характеристика наблюдаемого разброса, не " +
            "требующая нормального распределения. Это не погрешность прибора"
    override val usableData = "Данных для сравнения"
    override val usableDataNote =
        "учитываются только измерения, прошедшие отбор: подтверждённое место, непрерывный " +
            "поток данных, показание с приемлемой погрешностью"
    override val minuteBuckets = "Минутных интервалов"
    override val honestN = "честное n порядковых статистик"
    override val notEnoughData = "Недостаточно данных"
    override val updating = "Обновляется"
    override val temporarilyNotUpdating = "Временно не обновляется"
    override val updatingNote =
        "Новые подходящие измерения пополняют обычный диапазон этого места."
    override val notUpdatingNote =
        "Часть измерений сейчас не идёт в обычный фон — чтобы необычное событие не стало " +
            "его частью. Измерения при этом сохраняются полностью."
    override val state = "Состояние"
    override val excludedSection = "Что исключено из статистики"
    override val excludedNow = "Причина сейчас"
    override val excludedFromStatistics = "Не учтено в статистике"
    override val statisticsState = "Состояние статистики"
    override val quarantineNote =
        "После устойчивого отклонения новые измерения некоторое время сохраняются, но не " +
            "добавляются в обычный диапазон профиля. Это предотвращает постепенное " +
            "превращение самого отклонения в новый обычный фон."
    override val howDetected = "Как обнаруживается отклонение"
    override val absoluteThresholdL1 = "Абсолютный порог L1"
    override val relativeCriterion = "Порог относительно обычного диапазона"

    override fun timesProfileP90(factor: String) = "$factor × P90 профиля"

    override val minimumDuration = "Минимальная длительность"
    override val shorterNotAnnounced = "короче — отклонение не объявляется"
    override val returnCriterion = "Возврат"
    override val backBelowThreshold = "значение снова ниже порога"
    override val exclusionAfterEvent = "Исключение после события"
    override val fromEndOfDeviation = "отсчитывается от конца отклонения"
    override val criteriaNote =
        "Это параметры алгоритма обнаружения события, а не научные границы опасности. Те " +
            "же числа используют движок и настройки тревоги."
    override val notEvaluated = "не оценивалось"
    override val notEnoughStatistics = "недостаточно статистики"
    override val noChangeDetected = "изменение не обнаружено"
    override val changeDetected = "обнаружено изменение"
    override val spectralNoReference =
        "Эталон этого места ещё не создан, поэтому спектр в вывод не входит. " +
            "«Не оценивалось» — это не «изменений нет»."

    override val spectralComparison = "Спектральное сравнение"

    override val searchFeedbackTitle = "Отклик в Поиске"
    override val feedbackOnScreenOnly = "сигнал виден только на экране Поиска"
    override val feedbackClicks = "щелчок на каждый зарегистрированный импульс"
    override val feedbackTone = "непрерывный тон: выше — дальше от записанного фона"
    override val feedbackVibro = "то же без звука: чаще пульс — дальше от записанного фона"
    override val energyTone = "тон по энергии"
    override val energyToneNote = "высота щелчка по средней энергии гамма-квантов"
    override val alarmTitle = "Тревога"
    override val archiveSaved = "архив сохранён"
    override val archiveFailed = "архив не записался — попробуйте другую папку"
    override val debugTitle = "Отладка"
    override val stateReport = "Отчёт о состоянии"
    override val debugBundleNote =
        "Один архив со всем, что нужно для разбора: состояние приложения и прибора, " +
            "накопленный спектр и записанный фон, ваше описание проблемы."
    override val whatIsWrong = "Что не работает"
    override val whatIsWrongHint = "например: подключается, но спектр пустой"
    override val saveDebugArchive = "Сохранить архив отладки"
    override val notConnected = "не подключён"

    override fun excludedBecause(reason: String) = "исключено: $reason"

    override val measurementsCounted = "измерения учитываются"
    override val no = "нет"
    override val notRecorded = "не записан"

    override fun createdAt(stamp: String) = "создан $stamp"

    override val notCreated = "не создан"
    override val translationNote =
        "Перевод выполняется по разделам: непереведённые части пока показываются " +
            "по-русски. · Translation is in progress: untranslated parts are shown in Russian."
    override val skinTitle = "Оформление"
    override val skinTerminal = "Научный терминал"
    override val skinEightBit = "8-bit"
    override val themeSystem = "Системная"
    override val themeDark = "Тёмная"
    override val themeLight = "Светлая"

    override fun signalDbm(value: Int) = "$value дБм"

    override fun alarmPreset(level: String, factor: String, held: String) =
        "от $level или $factor× к P90 профиля, $held"

    override val retentionTitle = "Хранение сырых измерений"
    override val retentionKeepAll = "всё"

    override fun retentionDays(days: Int) = "$days дней"

    override val retentionNote =
        "Посекундные записи прибора старше срока удаляются. Графики долгих периодов, " +
            "статистика мест, сводки сессий, спектры и маршруты остаются — теряется только " +
            "сырая детализация. По умолчанию хранится всё."

    override val scaleTitle = "Масштаб"
    override val scaleNote =
        "Текст и элементы регулируются отдельно: числа читают издалека, а кнопки нажимают " +
            "пальцем, и увеличивать всё сразу значит терять половину экрана. Системный " +
            "размер шрифта при этом сохраняется — проценты умножаются на него."
    override val scaleFont = "текст"
    override val scaleElements = "элементы"

    override fun scalePercent(percent: Int) = "$percent %"

    override val scaleReset = "Вернуть 100 %"
    override val crystalOrganicPlastic = "органический пластик"
    override val modeOff = "нет"
    override val modeClicks = "клики"
    override val modeTone = "тон"
    override val modeVibro = "вибро"
    override val skinNote =
        "Оформление меняет цвета, шрифт и форму рамок — и только их: показания, " +
            "формулировки и расчёты от него не зависят. Светлая и тёмная тема работают " +
            "в обоих вариантах."
    override val themeTitle = "Тема"
    override val themeNote =
        "Тёмная тема — основная: на ней графики и цифры читаются в сумерках. Светлая " +
            "пригодится на солнце."
    override val alarmsIntro =
        "Тревога срабатывает не от одиночного скачка: уровень должен превысить порог — " +
            "по абсолютной величине или относительно обычного фона места — и продержаться " +
            "указанное время."
    override val nowLabel = "сейчас"
    override val usuallyHere = "обычно здесь"
    override val thresholdL1 = "порог L1"
    override val noBandToCompare =
        "Обычный фон этого места ещё не собран — сравнивать порог пока не с чем."
    override val sensitivityNormal = "Обычная"
    override val sensitivityHigh = "Высокая"
    override val sensitivityCustom = "Своя"
    override val sensitivityCustomNote = "уровни мощности дозы задаются вручную"
    override val alarmSoundElsewhere = "Мелодия и вибрация тревоги — в разделе «Звук»."
    override val alarmSoundTitle = "Звук и вибрация тревоги"
    override val alarmSoundNote =
        "мелодия и вибрация настраиваются в системных настройках уведомления «Тревога»"

    override fun level1WithUnit(unit: String) = "уровень 1, $unit"

    override fun level2WithUnit(unit: String) = "уровень 2, $unit"

    override val saveLevels = "Сохранить уровни"
    override val enterNumbers = "Введите числа, например 0,30"
    override val level1MustBePositive = "Уровень 1 должен быть больше нуля"
    override val level2BelowLevel1 = "Уровень 2 не может быть ниже уровня 1"
    override val levelsNote =
        "Уровень 1 — линия тревоги на графиках и порог отклонения; уровень 2 — сильное " +
            "превышение."
    override val profilesTitle = "Профили"
    override val profilesIntro =
        "Профиль — обстановка со своим обычным фоном: дом, офис, дача. Приложение может " +
            "включать его само, когда телефон в знакомой сети Wi-Fi. При удалении профиля " +
            "измерения остаются в журнале."
    override val profileNameHint = "название профиля"
    override val add = "Добавить"
    override val ownProfile = "+ Свой профиль"
    override val presets = "Готовые:"
    override val active = "активен"
    override val archived = "в архиве"
    override val hiddenFromPicker = "профиль скрыт из выбора"
    override val saveName = "Сохранить имя"
    override val icon = "Значок"
    override val autoByWifi = "Включать автоматически по Wi-Fi"
    override val learnBackground = "Учить обычный фон"
    override val wifiNote =
        "Сети Wi-Fi. Сеть узнаётся по адресу роутера, а не по имени: разрешение на " +
            "геолокацию для этого не нужно."
    override val unbind = "отвязать"
    override val notOnWifi = "телефон сейчас не в сети Wi-Fi"
    override val networkAlreadyBound = "текущая сеть уже привязана к этому профилю"
    override val bindCurrentNetwork = "Привязать текущую сеть"
    override val nestInProfile = "Вложить в профиль"
    override val standalone = "самостоятельный"
    override val unarchive = "Вернуть из архива"
    override val archiveAction = "В архив"
    override val deleteProfile = "Удалить профиль"
    override val deleteProfileQuestion = "Удалить профиль?"
    override val usualBackgroundTitle = "Обычный фон"
    override val usualBackgroundIntro =
        "Обычный фон профиля пополняется только из подходящих измерений. Не учитываются: " +
            "Поиск и опыты, обрыв потока, полчаса после отклонения и время, пока место не " +
            "подтверждено. Сами измерения записываются всегда."
    override val freezeLearning = "Заморозить обучение"
    override val graceNote =
        "Сколько ждать, прежде чем считать, что телефон покинул знакомую сеть. Всё это " +
            "время профиль остаётся прежним, но фон не пополняется."
    override val instrumentTitle = "Прибор"
    override val modelLabel = "модель"
    override val serialNumber = "серийный номер"
    override val firmware = "прошивка"
    override val bluetoothConnected = "подключено"
    override val bluetoothConnecting = "подключение…"

    override fun bluetoothReconnecting(attempt: Int) = "переподключение, попытка $attempt"

    override val bluetoothNoLink = "нет соединения"
    override val serviceStopped = "служба остановлена"
    override val instrumentBattery = "батарея прибора"
    override val temperature = "температура"
    override val stream = "поток"
    override val streamActive = "активен · 1 Гц"

    override fun streamNoNewData(seconds: Long) = "нет новых данных · $seconds с"

    override val streamReconnecting = "связь восстанавливается"
    override val streamLost = "связь с прибором потеряна"
    override val streamNoDataYet = "нет текущих данных"

    override fun lastMeasurementAgo(seconds: Long): String {
        val text = when {
            seconds < 60 -> "$seconds с"
            seconds < 3600 -> "${seconds / 60} мин"
            else -> "${seconds / 3600} ч"
        }
        return "последнее измерение $text назад"
    }
    override val unitsTitle = "Единицы"
    override val unitMicroSv = "мкЗв/ч"
    override val unitMicroSvNote = "микрозиверты в час — единица СИ"
    override val unitMicroR = "мкР/ч"
    override val unitMicroRNote = "микрорентгены в час · 1 мкЗв/ч = 100 мкР/ч"
    override val unitDoseMicroSv = "мкЗв"
    override val unitDoseMicroR = "мкР"
    override val unitsNote =
        "Пересчёт только для отображения: измерения хранятся в исходных единицах прибора " +
            "без потери точности."
    override val interfaceTitle = "Интерфейс"
    override val tabsNote =
        "Вкладки меню: порядок и видимость. Настройки остаются доступны через значок λ " +
            "на Главной."
    override val alwaysVisible = "всегда видна"
    override val atLeastOneTab = "Кроме Главной должна остаться хотя бы одна вкладка."
    override val monitorBlocksNote =
        "Блоки Главной. Число, статус и график мощности дозы остаются всегда; " +
            "остальное — по вашему выбору."
    override val blockTrend = "Тренд/ч"
    override val blockDoseToday = "Доза сегодня"
    override val blockCountChart = "График скорости счёта"
    override val blockHardnessChart = "График жёсткости"
    override val blockStats = "Статистика под графиком (мин/медиана/макс/SD/n)"
    override val resetInterface = "Вернуть меню и блоки по умолчанию"
    override val visible = "видна"
    override val hidden = "скрыта"
    override val onShort = "вкл"
    override val offShort = "выкл"
    override val licencesUnreadable = "Не удалось прочитать файлы лицензий."
    override val licencesTitle = "Лицензии"
    override val licencesBody =
        "Протокол RadiaCode — порт библиотеки cdump/radiacode (MIT). BLE — Kable " +
            "(Apache-2.0). Карта — osmdroid (Apache-2.0), данные карты © участники " +
            "OpenStreetMap (ODbL). Шрифты IBM Plex Sans и IBM Plex Mono (OFL)."
    override val hideLicences = "Скрыть тексты лицензий"
    override val showLicences = "Показать тексты лицензий"
    override val reading = "читаю…"
    override val recentUpdates = "последние обновления"
    override val whatChanged = "что изменилось"

    override val deviceSignals = "Сигналы прибора"
    override val deviceSignalsNote =
        "Звук и вибрация самого прибора. Они работают, даже когда телефон отключён " +
            "или приложение закрыто, и не связаны с откликом Поиска."
    override val deviceSound = "Звук прибора"
    override val deviceVibro = "Вибрация прибора"
    override val deviceSignalsUnknownNote =
        "Приложение умеет включить и выключить их, но не умеет спросить прибор, " +
            "что в нём стоит сейчас: до первой команды состояние неизвестно."
    override val deviceSignalsOfflineNote = "Прибор не подключён — команду отправить некуда."

    override fun baselineStats(median: String, iqr: String, mad: String, buckets: Int) =
        "медиана $median · P25–P75 $iqr · MAD $mad · n $buckets минутных интервалов"
    override val stateUnknown = "состояние неизвестно"
    override val stateOnByApp = "включено этим приложением"
    override val stateOffByApp = "выключено этим приложением"
    override val on = "Вкл"
    override val off = "Выкл"
}
