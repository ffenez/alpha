package app.radiacode.ui.text

/**
 * Главная, шторка «Почему такой вывод» и профили — то, что осталось за общим
 * каталогом [Strings].
 *
 * Формулировки этой области несут научную честность приложения, поэтому
 * перевод переносит ПРАВИЛО, а не слова:
 *
 *  - зелёный статус означает «внутри исторического диапазона этого профиля», а
 *    не «безопасно»; ни один язык не говорит safe/dangerous/normal;
 *  - «baseline» — имя движка, а не слово на экране: по-английски это «the
 *    usual background» или «the historical range», но не «baseline»;
 *  - состояние статистики не говорит об обучении и о модели («learning»,
 *    «model» запрещены так же, как «обучение» и «учится»);
 *  - MAD подписан оговоркой «это не погрешность прибора», а любое отношение
 *    называет знаменатель («×4,8 к P90 профиля»);
 *  - жёсткость — документированный коэффициент вендора (мкрем/ч)/(имп/с), и
 *    называть её средней энергией нельзя ни на одном языке.
 */
interface MonitorStrings {

    /** Возврат графика к живому краю после жеста. */
    val backToNow: String

    /**
     * Подпись над выводом, когда свежих данных нет.
     *
     * Вывод остаётся на экране — скрывать его значит заставить человека
     * гадать, — но читаться как текущий он не имеет права.
     */
    val byLastMeasurement: String

    // --------------------------------------------- Главная: шапка и профиль
    val profileUnknown: String
    val modeAuto: String
    val modeManual: String
    val modeUnconfirmed: String
    val contextAutoKnown: String
    val contextAutoUncertain: String
    val contextTransit: String
    val contextNoContext: String
    val contextManual: String

    // ------------------------------------------ Главная: плитки и подписи
    val countTile: String

    /** Окно тренда в подписи под значением: «1 ч». */
    val trendWindowHour: String

    /** «за 1 ч» — за какое окно посчитан показанный наклон. */
    fun overWindow(window: String): String
    val usualBackgroundUpdating: String

    /**
     * Первый уровень (§12): ОДНО состояние без внутренней причины. Точные
     * причины и длительности живут в «Почему такой вывод» — на Главной они
     * читались как основной диагноз прибора, которым не являются.
     */
    val usualBackgroundNotUpdating: String
    val usualBackgroundFrozen: String
    val bandIsProfileP10P90: String
    val collectingMeasurements: String
    val statMin: String
    val statMedian: String
    val statMax: String
    val batteryBannerBody: String
    val batteryBannerAction: String

    // ------------------------------------------------ длительности словами
    fun secondsShort(value: Long): String
    fun minutesShort(value: Long): String

    /** «1,5 ч» — число уже отформатировано, здесь только единица. */
    fun hoursShort(hours: String): String

    // --------------------------------------- обычный фон: сколько собрано
    /** «изучаю обычный фон — 1,5 ч из 3». */
    fun collectingUsualBackground(collected: String, required: String): String

    /** Настройки: «обычный фон собран за 26 ч наблюдений». */
    fun usualBackgroundCollected(hours: String): String

    /** «2 ч 14 мин из минимально необходимых 3 ч». */
    fun ofMinimumRequired(collected: String, required: String): String

    // -------------------------------------------- «Почему»: сам вывод (§17)
    val verdictNoReading: String
    val verdictNoBand: String
    val verdictInsideBand: String
    val verdictAboveBand: String
    val verdictAboveThreshold: String
    val verdictAlert: String

    // ------------------------------------------------------ «Почему»: шторка
    val whyTitle: String
    val bandNotCollected: String

    /** Второй уровень: методика и статистика профиля. */
    val showCalculations: String
    val hideCalculations: String

    /** Третий уровень внутри второго: MAD, χ², z, формулы. */
    val showExpert: String
    val hideExpert: String
    val gotIt: String

    /** «0,42 — вне диапазона»: точка на краю шкалы обязана себя назвать. */
    fun outsideBand(value: String): String

    // ------------------------------------------------- метки достоверности
    val evidenceMeasuredTag: String
    val evidenceMeasuredNote: String
    val evidenceCalculatedTag: String
    val evidenceCalculatedNote: String
    val evidenceStatisticalTag: String
    val evidenceStatisticalNote: String
    val evidenceInterpretationTag: String
    val evidenceInterpretationNote: String

    // ------------------------- почему интервал не попал в обычный фон (§4.2)
    val exclusionLearningOff: String

    /**
     * Профиль, который обычный фон не собирает ПО УСТРОЙСТВУ («В пути», «Без
     * места», выключенное обучение): это его свойство, а не сбой, поэтому
     * строка не начинается с «не пополняется» и не красится янтарём.
     */
    val usualBackgroundNotCollected: String
    val exclusionContextUncertain: String
    val exclusionStreamStale: String
    val exclusionExperiment: String
    val exclusionQuarantine: String
    val exclusionStatisticsUnusable: String
    val exclusionManualFreeze: String

    // ------------------------------------------------------------ жёсткость
    val hardnessExplanation: String
    val hardnessPurpose: String
    val hardnessSigmaCaveat: String

    // ------------------------------- описательные отличия окна от профиля
    // Утверждение описывает порядковые статистики и остаётся описанием: ни σ,
    // ни p-value, ни процентов уверенности, ни слова «значимо».
    val deviationUsual: String
    val deviationNotEnough: String
    val deviationAboveBand: String
    val deviationBelowBand: String
    val deviationShiftedUp: String
    val deviationShiftedDown: String
    val deviationSpreadWider: String
    val deviationShortSpike: String

    val numberWindowMedian: String
    val numberProfileMedian: String
    val numberProfileP10: String
    val numberProfileP25: String
    val numberProfileP75: String
    val numberProfileP90: String
    val numberWindowIqr: String
    val numberProfileIqr: String
    val numberWindowMax: String
    val numberSecondsAboveP90: String
    val numberMeasuredInWindow: String

    // ------------------------------------- «Уровень изменился надолго» (§7)
    val shiftTitle: String

    /** Имя профиля может быть неизвестно — фраза остаётся целой и без него. */
    fun shiftSentence(profileName: String?): String
    val shiftExplanation: String
    val shiftUpdateAction: String
    val shiftKeepAction: String

    // ------------------------------------------------- удаление профиля
    val deleteBlockedUnknown: String
    val deleteBlockedLastLive: String
    fun deleteBlockedHasChildren(children: String): String
    val deleteBlockedRequiredRole: String
    fun deleteConfirm(profileName: String): String

    // ---------------------------------------------------- выбор профиля
    val pickerTitle: String
    fun pickerSubtitle(context: String): String
    val pickerReturnToAuto: String
    val pickerAutoNote: String
    val pickerNewProfile: String
}

object MonitorRu : MonitorStrings {

    override val backToNow = "сейчас"

    override val byLastMeasurement = "по последнему измерению"

    override val profileUnknown = "Профиль?"
    override val modeAuto = "авто"
    override val modeManual = "вручную"
    override val modeUnconfirmed = "не подтв."
    override val contextAutoKnown = "выбран автоматически по знакомой сети"
    override val contextAutoUncertain =
        "сеть пропала — место не подтверждено, статистика не пополняется"
    override val contextTransit = "знакомой сети нет — «В пути»"
    override val contextNoContext = "место определить нельзя — «Без места»"
    override val contextManual = "выбран вручную"

    override val countTile = "Счёт"
    override val trendWindowHour = "1 ч"
    override fun overWindow(window: String) = "за $window"
    override val usualBackgroundUpdating = "обычный фон пополняется"
    // «Фоновая статистика» — имя движка, а человек читает вывод. Причина
    // называется той стороной, которая ему видна: новые измерения сейчас не
    // идут в копилку места. Подробная причина — по нажатию, в «Почему».
    override val usualBackgroundNotUpdating = "новые данные сейчас не добавляются в фон"
    override val usualBackgroundFrozen = "обычный фон заморожен вручную"
    override val bandIsProfileP10P90 = "полоса — обычный диапазон места"
    override val collectingMeasurements = "накапливаем измерения…"
    override val statMin = "мин"
    override val statMedian = "медиана"
    override val statMax = "макс"
    override val batteryBannerBody =
        "Android может остановить измерение в фоне. Чтобы запись шла непрерывно, " +
            "исключите приложение из оптимизации батареи."
    override val batteryBannerAction = "Разрешить работу в фоне"

    override fun secondsShort(value: Long) = "$value с"
    override fun minutesShort(value: Long) = "$value мин"
    override fun hoursShort(hours: String) = "$hours ч"

    override fun collectingUsualBackground(collected: String, required: String) =
        "изучаю обычный фон — $collected ч из $required"
    override fun usualBackgroundCollected(hours: String) =
        "обычный фон собран за $hours ч наблюдений"
    override fun ofMinimumRequired(collected: String, required: String) =
        "$collected из минимально необходимых $required"

    override val verdictNoReading = "Текущего измерения нет — сравнивать не с чем."
    override val verdictNoBand =
        "Обычный диапазон этого места ещё не собран, поэтому сравнение идёт только " +
            "с вашим порогом тревоги L1."
    override val verdictInsideBand =
        "Текущее значение попадает в диапазон, в котором обычно находятся измерения " +
            "этого места."
    override val verdictAboveBand =
        "Текущее значение держится выше диапазона, в котором обычно находятся измерения " +
            "этого места."
    override val verdictAboveThreshold =
        "Текущая мощность дозы выше порога тревоги, который вы задали. " +
            "Тревога объявляется по величине И длительности, поэтому идёт отсчёт " +
            "выдержки."
    override val verdictAlert =
        "Превышение держится дольше заданного вами времени. Это сравнение с вашим " +
            "порогом L1 и с обычным диапазоном этого места, а не оценка опасности."

    override val whyTitle = "Почему такой вывод"
    override val bandNotCollected = "диапазон ещё не собран"
    override val showCalculations = "Показать методику и расчёты"
    override val hideCalculations = "Скрыть методику и расчёты"
    override val showExpert = "Показать технические параметры"
    override val hideExpert = "Скрыть технические параметры"
    override val gotIt = "Понятно"
    override fun outsideBand(value: String) = "$value — вне обычного диапазона"

    override val evidenceMeasuredTag = "изм."
    override val evidenceMeasuredNote = "измерено прибором"
    override val evidenceCalculatedTag = "расчёт"
    override val evidenceCalculatedNote = "расчёт из измеренных значений"
    override val evidenceStatisticalTag = "стат."
    override val evidenceStatisticalNote = "вывод статистической модели"
    override val evidenceInterpretationTag = "гипотеза"
    override val evidenceInterpretationNote = "физическая интерпретация, не факт"

    override val exclusionLearningOff = "этот профиль не собирает обычный фон"
    override val usualBackgroundNotCollected = "этот профиль не собирает обычный фон"
    override val exclusionContextUncertain = "место не подтверждено"
    override val exclusionStreamStale = "поток данных прерван"
    override val exclusionExperiment = "идёт Поиск или эксперимент"
    // «карантин» — слово движка: человеку нужно, ЧТО произошло с измерением, а
    // не как называется механизм, который его отложил (§3, §12).
    override val exclusionQuarantine = "недавно было отклонение уровня"
    override val exclusionStatisticsUnusable = "показание слишком неточное для статистики"
    override val exclusionManualFreeze = "обычный фон заморожен вручную"

    // Первая фраза — человеческая: термин без объяснения на главном экране
    // не значит ничего. Определение и единица идут следом, они никуда не
    // делись и по-прежнему точны.
    override val hardnessExplanation =
        "Показывает, как меняется энергетический состав регистрируемого излучения; " +
            "само по себе это не мера опасности. Формально — дозовая величина на " +
            "единицу скорости счёта, (мкрем/ч)/(имп/с); это не средняя энергия фотона."
    override val hardnessPurpose =
        "Она подавляет влияние общей интенсивности: если поле то же, а его стало " +
            "больше, доза и счёт растут вместе, а отношение остаётся примерно " +
            "прежним. Точного постоянства нет — мешают статистический шум, " +
            "энергетическая характеристика детектора и погрешность оценки дозы."
    override val hardnessSigmaCaveat =
        "Погрешность посчитана по правилу частного. Доза и счёт формируются из " +
            "одних и тех же событий, а ковариация их алгоритмов в приборе не " +
            "опубликована — поэтому это оценка, а не гарантированная граница."

    override val deviationUsual = "в обычном диапазоне этого профиля"
    override val deviationNotEnough = "мало измерений для сравнения с профилем"
    override val deviationAboveBand = "медиана за это время выше обычного диапазона места"
    override val deviationBelowBand = "медиана за это время ниже обычного диапазона места"
    override val deviationShiftedUp =
        "медиана сместилась вверх относительно обычной середины профиля"
    override val deviationShiftedDown =
        "медиана сместилась вниз относительно обычной середины профиля"
    override val deviationSpreadWider = "разброс измерений в окне шире обычного для профиля"
    override val deviationShortSpike =
        "короткий всплеск: выше P90 профиля недолго, уровень не удержался"

    override val numberWindowMedian = "медиана окна"
    override val numberProfileMedian = "медиана профиля"
    override val numberProfileP10 = "P10 профиля"
    override val numberProfileP25 = "P25 профиля"
    override val numberProfileP75 = "P75 профиля"
    override val numberProfileP90 = "P90 профиля"
    override val numberWindowIqr = "P25–P75 окна"
    override val numberProfileIqr = "P25–P75 профиля"
    override val numberWindowMax = "максимум окна"
    override val numberSecondsAboveP90 = "время выше P90"
    override val numberMeasuredInWindow = "измерено в окне"

    override val shiftTitle = "Уровень изменился надолго"
    override fun shiftSentence(profileName: String?): String {
        val name = profileName?.let { "профиля «$it»" } ?: "профиля"
        return "Показания устойчиво отличаются от обычного диапазона $name. " +
            "Возможно, изменилось место прибора или сама обстановка."
    }
    override val shiftExplanation =
        "«Обновить профиль» начнёт новый период: прежний диапазон сохранится в истории, " +
            "а обычный диапазон будет считаться заново с этого момента. Сырые измерения " +
            "не меняются и не удаляются."
    override val shiftUpdateAction = "Обновить профиль"
    override val shiftKeepAction = "Оставить как есть"

    override val deleteBlockedUnknown = "профиль уже удалён"
    override val deleteBlockedLastLive =
        "это последний профиль — измерениям нужен хотя бы один, " +
            "создайте другой и удалите этот"
    override fun deleteBlockedHasChildren(children: String) =
        "сначала удалите вложенные профили: $children"
    override val deleteBlockedRequiredRole =
        "в этот профиль автоматика складывает измерения, когда знакомого места " +
            "нет — удалить его нельзя, приложение создало бы его заново. " +
            "Профиль можно переименовать или заархивировать"
    override fun deleteConfirm(profileName: String) =
        "Удалить профиль «$profileName»? Его измерения останутся в журнале — " +
            "они потеряют привязку к профилю, но не удаляются. " +
            "Обычный фон, накопленный этим профилем, пропадёт вместе с ним."

    override val pickerTitle = "Профиль измерения"
    override fun pickerSubtitle(context: String) =
        "У каждого профиля свой обычный фон. Сейчас: $context."
    override val pickerReturnToAuto = "Вернуться к авто"
    override val pickerAutoNote =
        "Автоматически профиль выбирается по знакомой сети Wi-Fi. " +
            "Геолокация для этого не нужна."
    override val pickerNewProfile = "+ Новый профиль"
}

object MonitorEn : MonitorStrings {

    override val backToNow = "now"

    override val byLastMeasurement = "based on the last measurement"

    override val profileUnknown = "Profile?"
    override val modeAuto = "auto"
    override val modeManual = "manual"
    override val modeUnconfirmed = "unconfirmed"
    override val contextAutoKnown = "chosen automatically by a familiar network"
    // Не «learning is paused»: слово об обучении запрещено и по-английски —
    // говорим о том, что происходит с самим обычным фоном.
    override val contextAutoUncertain =
        "the network is gone — the place is unconfirmed, the usual background is not " +
            "being updated"
    override val contextTransit = "no familiar network — recorded into the in-transit profile"
    override val contextNoContext =
        "the place cannot be determined — recorded into the no-place profile"
    override val contextManual = "chosen manually"

    override val countTile = "Counts"
    override val trendWindowHour = "1 h"
    override fun overWindow(window: String) = "over $window"
    override val usualBackgroundUpdating = "the usual background is being updated"
    override val usualBackgroundNotUpdating = "new data is not being added to the background"
    override val usualBackgroundFrozen = "the usual background is frozen manually"
    override val bandIsProfileP10P90 = "the band is the usual range here"
    override val collectingMeasurements = "collecting measurements…"
    override val statMin = "min"
    override val statMedian = "median"
    override val statMax = "max"
    override val batteryBannerBody =
        "Android may stop the measurement in the background. To keep the recording " +
            "continuous, exclude the app from battery optimisation."
    override val batteryBannerAction = "Allow background work"

    override fun secondsShort(value: Long) = "$value s"
    override fun minutesShort(value: Long) = "$value min"
    override fun hoursShort(hours: String) = "$hours h"

    override fun collectingUsualBackground(collected: String, required: String) =
        "collecting the usual background — $collected h of $required"
    // «baseline» — имя движка; на экране у величины есть человеческое имя.
    override fun usualBackgroundCollected(hours: String) =
        "the usual background is built from $hours h of observation"
    override fun ofMinimumRequired(collected: String, required: String) =
        "$collected of the $required needed at minimum"

    override val verdictNoReading = "There is no current reading — nothing to compare with."
    override val verdictNoBand =
        "The usual range of this place is not collected yet, so the comparison runs " +
            "against your L1 alarm threshold only."
    override val verdictInsideBand =
        "The current value falls into the range where the measurements of this place " +
            "usually are."
    override val verdictAboveBand =
        "The current value keeps staying above the range where the measurements of this " +
            "place usually are."
    override val verdictAboveThreshold =
        "The current dose rate is above the alarm threshold you set. An alarm is " +
            "announced by magnitude AND duration, so the dwell is being counted."
    override val verdictAlert =
        "The excess has held longer than the time you set. This is a comparison with your " +
            "L1 threshold and with the usual range of this place, not an assessment of harm."

    override val whyTitle = "Why this conclusion"
    override val bandNotCollected = "the range is not collected yet"
    override val showCalculations = "Show the method and the calculations"
    override val hideCalculations = "Hide the method and the calculations"
    override val showExpert = "Show the technical parameters"
    override val hideExpert = "Hide the technical parameters"
    override val gotIt = "Got it"
    override fun outsideBand(value: String) = "$value — outside the usual range"

    override val evidenceMeasuredTag = "meas."
    override val evidenceMeasuredNote = "measured by the instrument"
    override val evidenceCalculatedTag = "calc."
    override val evidenceCalculatedNote = "computed from measured values"
    override val evidenceStatisticalTag = "stat."
    override val evidenceStatisticalNote = "a conclusion of a statistical model"
    override val evidenceInterpretationTag = "hypothesis"
    override val evidenceInterpretationNote = "a physical interpretation, not a fact"

    // Причина называется симптомом человека, а не именем движка: ни «baseline»,
    // ни «learning» здесь не появляются.
    override val exclusionLearningOff = "this profile does not build a usual background"
    override val usualBackgroundNotCollected =
        "this profile does not build a usual background"
    override val exclusionContextUncertain = "the place is not confirmed"
    override val exclusionStreamStale = "the data stream is interrupted"
    override val exclusionExperiment = "Search or an experiment is running"
    override val exclusionQuarantine = "a level deviation has just ended"
    override val exclusionStatisticsUnusable = "the reading is too imprecise for statistics"
    override val exclusionManualFreeze = "the usual background is frozen manually"

    // Числитель — дозиметрическая оценка прибора, а не энергия в кристалле,
    // поэтому «average energy» запрещено и здесь.
    override val hardnessExplanation =
        "Shows how the energy make-up of the registered radiation changes. " +
            "The value by itself is not a measure of harm. " +
            "Formally it is a dose quantity per unit count rate, " +
            "(µrem/h)/(counts/s); it is not the mean photon energy."
    override val hardnessPurpose =
        "It suppresses the influence of overall intensity: with the same field made " +
            "brighter, dose and counts grow together and the ratio stays roughly where " +
            "it was. Roughly, not exactly — counting noise, the detector's energy " +
            "response and the uncertainty of the dose estimate all get in the way."
    override val hardnessSigmaCaveat =
        "The uncertainty is propagated by the quotient rule. Dose and counts are formed " +
            "from the same events, and the covariance of their firmware algorithms is " +
            "not published — so this is an estimate, not a guaranteed bound."

    override val deviationUsual = "within this profile's usual range"
    override val deviationNotEnough = "too few measurements to compare with the profile"
    override val deviationAboveBand =
        "the median over this period is above the usual range here"
    override val deviationBelowBand =
        "the median over this period is below the usual range here"
    override val deviationShiftedUp =
        "the median has shifted up relative to the profile's usual middle"
    override val deviationShiftedDown =
        "the median has shifted down relative to the profile's usual middle"
    override val deviationSpreadWider =
        "the spread of measurements in the window is wider than usual for the profile"
    override val deviationShortSpike =
        "a short spike: above the profile's P90 briefly, the level did not hold"

    override val numberWindowMedian = "window median"
    override val numberProfileMedian = "profile median"
    override val numberProfileP10 = "profile P10"
    override val numberProfileP25 = "profile P25"
    override val numberProfileP75 = "profile P75"
    override val numberProfileP90 = "profile P90"
    override val numberWindowIqr = "window P25–P75"
    override val numberProfileIqr = "profile P25–P75"
    override val numberWindowMax = "window maximum"
    override val numberSecondsAboveP90 = "time above P90"
    override val numberMeasuredInWindow = "measured in the window"

    override val shiftTitle = "The level has changed for a long time"
    override fun shiftSentence(profileName: String?): String {
        val name = profileName?.let { "of the profile «$it»" } ?: "of the profile"
        return "The readings keep differing from the usual range $name. The place " +
            "of the instrument, or the situation itself, may have changed."
    }
    override val shiftExplanation =
        "«Update the profile» starts a new period: the previous range is kept in the " +
            "history, and the usual range is counted anew from this moment. Raw " +
            "measurements are neither changed nor deleted."
    override val shiftUpdateAction = "Update the profile"
    override val shiftKeepAction = "Leave as is"

    override val deleteBlockedUnknown = "the profile is already deleted"
    override val deleteBlockedLastLive =
        "this is the last profile — measurements need at least one, create another " +
            "and then delete this one"
    override fun deleteBlockedHasChildren(children: String) =
        "delete the nested profiles first: $children"
    override val deleteBlockedRequiredRole =
        "this profile is where the app files measurements taken with no familiar place " +
            "around — it cannot be deleted, the app would create it again. The profile " +
            "can be renamed or archived"
    override fun deleteConfirm(profileName: String) =
        "Delete the profile «$profileName»? Its measurements stay in the journal — they " +
            "lose the link to the profile, but they are not deleted. The usual " +
            "background collected by this profile disappears with it."

    override val pickerTitle = "Measurement profile"
    override fun pickerSubtitle(context: String) =
        "Every profile has its own usual background. Now: $context."
    override val pickerReturnToAuto = "Back to automatic"
    override val pickerAutoNote =
        "The profile is chosen automatically by a familiar Wi-Fi network. Location " +
            "access is not needed for that."
    override val pickerNewProfile = "+ New profile"
}

val MonitorCatalogue = AreaCatalogue(ru = MonitorRu, en = MonitorEn)

/**
 * Все строки области одним списком — список ведётся вручную вместе с
 * интерфейсом: забытая строка означала бы формулировку, которую запреты
 * области не проверяют.
 */
fun MonitorStrings.allTexts(): List<String> = listOf(
    backToNow,
    byLastMeasurement,
    profileUnknown, modeAuto, modeManual, modeUnconfirmed,
    contextAutoKnown, contextAutoUncertain, contextTransit, contextNoContext, contextManual,
    countTile, trendWindowHour, overWindow(trendWindowHour),
    usualBackgroundUpdating, usualBackgroundNotUpdating,
    usualBackgroundFrozen, bandIsProfileP10P90, collectingMeasurements,
    statMin, statMedian, statMax, batteryBannerBody, batteryBannerAction,
    secondsShort(45), minutesShort(4), hoursShort("1,5"),
    collectingUsualBackground("1,5", "3"), usualBackgroundCollected("26"),
    ofMinimumRequired(hoursShort("2"), hoursShort("3")),
    verdictNoReading, verdictNoBand, verdictInsideBand, verdictAboveBand,
    verdictAboveThreshold, verdictAlert,
    whyTitle, bandNotCollected, showCalculations, hideCalculations,
    showExpert, hideExpert, gotIt,
    outsideBand("0,42"),
    evidenceMeasuredTag, evidenceMeasuredNote,
    evidenceCalculatedTag, evidenceCalculatedNote,
    evidenceStatisticalTag, evidenceStatisticalNote,
    evidenceInterpretationTag, evidenceInterpretationNote,
    exclusionLearningOff, usualBackgroundNotCollected, exclusionContextUncertain,
    exclusionStreamStale,
    exclusionExperiment, exclusionQuarantine, exclusionStatisticsUnusable,
    exclusionManualFreeze,
    hardnessExplanation, hardnessPurpose, hardnessSigmaCaveat,
    deviationUsual, deviationNotEnough, deviationAboveBand, deviationBelowBand,
    deviationShiftedUp, deviationShiftedDown, deviationSpreadWider, deviationShortSpike,
    numberWindowMedian, numberProfileMedian, numberProfileP10, numberProfileP25,
    numberProfileP75, numberProfileP90, numberWindowIqr, numberProfileIqr,
    numberWindowMax, numberSecondsAboveP90, numberMeasuredInWindow,
    shiftTitle, shiftSentence("Дом"), shiftSentence(null), shiftExplanation,
    shiftUpdateAction, shiftKeepAction,
    deleteBlockedUnknown, deleteBlockedLastLive, deleteBlockedHasChildren("Спальня"),
    deleteBlockedRequiredRole, deleteConfirm("Дом"),
    pickerTitle, pickerSubtitle(contextAutoKnown), pickerReturnToAuto,
    pickerAutoNote, pickerNewProfile,
)
