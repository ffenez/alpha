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
    override val spectrogramEntry = "Спектрограмма ▸"
    override val radonEntry = "Радон ▸"
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
            "суммируется); «Сохранить» сохранит сумму"
    override val noLiveAccumulation = "живого накопления пока нет — показан сохранённый снимок"
    override val continuationWarning =
        "Прибор копит спектр независимо от приложения. Если снимок сделан из текущего " +
            "накопления без сброса, импульсы посчитаются дважды — сначала сбросьте спектр."
    override val spectrumInfoTitle = "Как читать спектр"
    override val spectrumInfoAxes =
        "По горизонтали энергия в кэВ, по вертикали импульсы в канале за всё накопление. " +
            "В одну колонку экрана попадает несколько каналов, и берётся их максимум: " +
            "узкий пик не теряется при отдалении, но линия континуума проходит по верхней " +
            "огибающей."
    override val spectrumInfoSignificance =
        "Значимость пика — это его нетто-площадь, делённая на собственную стандартную " +
            "неопределённость: в неё входит и статистика окна пика, и неопределённость " +
            "оценки континуума под ним. Структура принимается за пик, только если её " +
            "ширина согласуется с разрешением детектора."
    override val spectrumInfoCandidate =
        "Кандидат нуклида — это совпадение энергии, а не обнаружение: надёжная " +
            "идентификация требует накопленной статистики и, как правило, нескольких " +
            "линий одного нуклида."
    override val spectrumInfoScales =
        "Масштаб оси импульсов: линейный передаёт отношение площадей, но прижимает всё, " +
            "кроме самого высокого, к нулю; логарифмический показывает и одиночные " +
            "отсчёты, и фотопик, но зрительно уравнивает величины, различающиеся в разы; " +
            "степенной 1/n — промежуточный (1/2 — привычный корень). Все три — монотонные " +
            "преобразования одного числа: меняется распределение высоты, а не данные."
    override val spectrumInfoGestures =
        "Щипок по графику — масштаб, перетаскивание — сдвиг. Сглаживание меняет только " +
            "отображение: исходные данные не трогаются."
    override val scaleLinear = "Лин"
    override val scalePower = "Степень"
    override val scaleLog = "Лог"

    override fun powerDegree(root: Int) = "степень 1/$root"

    override val spectrumModeRaw = "Спектр"
    override val spectrumModeMinusBackground = "− фон"
    override val smoothing = "сглаж."
    override val energyRanges = "энергетические диапазоны"
    override val peakTableEnergy = "E, кэВ"
    override val peakTableNet = "нетто"
    override val peakTableSignificance = "значимость"
    override val peakTableCandidate = "кандидат"
    override val notEnoughForPeaks = "мало данных для анализа пиков — накопите хотя бы минуту"
    override val noPeaksFound = "выраженных пиков над континуумом не найдено"
    override val peakTableCaveat =
        "возможное совпадение ≠ обнаружение · нужно подтверждение: копите дольше · " +
            "нажмите строку — справка о нуклиде"
    override val recordBackground = "Записать фон"
    override val save = "Сохранить"
    override val reset = "Сброс"
    override val resetSpectrumTitle = "Сбросить спектр?"
    override val resetSpectrumBody =
        "Накопление начнётся заново — на приборе спектр тоже очистится. Сохранённые " +
            "снимки останутся в Истории."
    override val cancel = "Отмена"

    override fun edgeCounts(counts: String) = "у верхней границы шкалы: $counts имп."

    override fun rangeWhole(range: String) = "диапазон $range · весь · щипок увеличит"

    override fun rangeDraggable(range: String) = "диапазон $range · перетаскивание сдвигает"

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
    override val snapshotOpensActions = "снимок открывает экспорт, сравнение и продолжение"

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

    override val evidenceLegend =
        "изм. — измерено прибором · расчёт — вычислено из измерений · " +
            "стат. — результат статистической модели профиля"
    override val nowSection = "Сейчас"
    override val poissonNote =
        "1σ Пуассона ≈ √(N/τ), τ = 1 с · счёт сам по себе не пересчитывается в дозу"
    override val dataSection = "Данные"
    override val profile = "Профиль"
    override val outsideProfile = "вне профиля"
    override val comparisonSection = "Сравнение с профилем"
    override val historicalRange = "Исторический диапазон"
    override val notCollectedYet = "ещё не собран"
    override val comparisonRuns = "Сравнение идёт"

    override fun withThresholdL1(value: String) = "с порогом L1 $value"

    override val thresholdIsNotSafety =
        "Порог L1 — параметр тревоги приложения, а не граница безопасности."
    override val currentValue = "Текущее значение"
    override val position = "Положение"
    override val bandExplained =
        "P10–P90 — диапазон, внутри которого находилось около 80 % пригодных исторических " +
            "измерений этого профиля. Это характеристика данного места, а не норматив " +
            "радиационной безопасности."
    override val belowP10 = "ниже P10"
    override val aboveP90 = "выше P90"
    override val insideBand = "внутри P10–P90"
    override val profileStatistics = "Статистика профиля"
    override val median = "Медиана"
    override val madNote =
        "median(|xᵢ − медиана|) — робастная характеристика наблюдаемого разброса, не " +
            "требующая нормального распределения. Это не погрешность прибора"
    override val usableData = "Пригодных данных"
    override val minuteBuckets = "Минутных корзин"
    override val honestN = "честное n порядковых статистик"
    override val notEnoughData = "Недостаточно данных"
    override val updating = "Обновляется"
    override val temporarilyNotUpdating = "Временно не обновляется"
    override val updatingNote =
        "Новые пригодные измерения учитываются при пересчёте исторического диапазона."
    override val notUpdatingNote =
        "Новые измерения сохраняются, но временно не используются для обновления " +
            "исторического диапазона."
    override val state = "Состояние"
    override val excludedFromStatistics = "Не учтено в статистике"
    override val statisticsState = "Состояние статистики"
    override val quarantineNote =
        "После устойчивого отклонения новые измерения некоторое время сохраняются, но не " +
            "добавляются в обычный диапазон профиля. Это предотвращает постепенное " +
            "превращение самого отклонения в новый обычный фон."
    override val howDetected = "Как обнаруживается отклонение"
    override val absoluteThresholdL1 = "Абсолютный порог L1"
    override val relativeCriterion = "Относительный критерий"

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

    override fun spectralTooLittle(detail: String) =
        "Сравнение с эталоном места началось, но данных пока мало: $detail"

    override fun spectralCompared(detail: String) =
        "Форма спектра сравнивается с эталоном места (не с абсолютным уровнем): $detail. " +
            "Вывод описывает состав излучения, а не его опасность."

    override val spectralComparison = "Спектральное сравнение"

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
