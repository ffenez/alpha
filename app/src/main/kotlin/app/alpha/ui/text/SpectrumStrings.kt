package app.alpha.ui.text

/**
 * Строки экрана «Спектр» и его анализа: энергетические окна, объединение и
 * вычитание снимков, край энергетической шкалы, сравнение формы.
 *
 * ## Что этот перевод обязан сохранить
 *
 * - **Совпадение линии — не обнаружение.** Слово «обнаружен»/«detected» о
 *   нуклиде не пишется нигде, а уверенность подсказки не поднимается выше
 *   «средняя»/«medium»: выше неё у прибора этого класса просто нет основания.
 * - **Отказ различить — не утверждение равенства.** «Различие не выделено» =
 *   «no difference singled out»: критерий проверял ОТЛИЧИЕ и его не выделил,
 *   а не измерил совпадение.
 * - **Ось называет обе величины** — «имп в канале», а не «имп/кэВ»: ширина
 *   канала по шкале меняется, и деление на неё показало бы не то, что
 *   показывает прибор ([SpectrumStrings.infoAxisY]).
 * - **Край шкалы описывается, а не объясняется**: «у верхней границы шкалы:
 *   N имп.» — механизм (переполнение, прошивка) первичной документацией не
 *   подтверждён, поэтому не называется.
 * - **Спектральное отношение — не мера опасности** (спец §7): отказ обязан
 *   стоять на обоих языках («не мера опасности» / «not a measure of harm»),
 *   и величина названа ОТНОШЕНИЕМ двух участков спектра, а не самостоятельной
 *   характеристикой излучения.
 * - **«Жёсткость» и «спектральное отношение» — разные величины.** Первая
 *   приходит от прибора как Ḋ/R, вторая считается по спектру; ни одна не
 *   называется словом другой ни в одном языке.
 *
 * Строки, которые уезжают в базу (`spectra.label`) или в файл экспорта
 * (RC-XML, N42, отчёт эксперимента), в каталог НЕ входят: они не должны
 * зависеть от языка интерфейса того, кто их сохранил.
 */
interface SpectrumStrings {

    /** Вход в ряд нетто-счёта по линии выбранного нуклида. */
    /** Показать записанный фон серой кривой поверх спектра. */
    val showBackgroundCurve: String

    /**
     * Чип «континуум» и его пояснение: подложка — оценка формы, а не измерение,
     * и в выводах о линиях не участвует.
     */
    /**
     * Ограничение НА КАРТИНКЕ: энергии показаны с принятой поправкой шкалы,
     * а не так, как их отдаёт прибор.
     */
    /**
     * Числа пика в его разборе: площадь и значимость — то, что до сих пор
     * считалось и никуда не выводилось.
     */
    fun peakDetails(netCounts: String, significance: String): String

    /**
     * Почему подпись пика не совпала с самым высоким каналом.
     *
     * Вопрос из поля: «в 610 больше всего импульсов, а приложение выбирает
     * 602». Ответ обязан быть на экране, а не в голове разработчика.
     */
    fun peakCentre(centre: String, max: String): String

    val scaleCorrected: String

    val continuumChip: String
    val continuumHint: String

    /**
     * Нажали то, что работает от записанного фона, а фона нет.
     *
     * Действие остаётся живым и объясняет свой вход: что оно делает, чего ему
     * не хватает и как это исправить.
     */
    /** «фон записан · 14:32» — ответ на нажатие «Сделать фоном». */
    fun backgroundRecordedAt(time: String): String

    val needBackgroundTitle: String
    /**
     * Один чип работает с фоном тремя состояниями, поэтому и объяснение одно:
     * оно называет обе вещи, которых без записанного фона не сделать.
     */
    val needBackgroundCurve: String
    val needBackgroundHow: String
    val needBackgroundNoDevice: String
    /**
     * «Сколько копить» — вопрос, который задают на этом экране первым.
     *
     * Ответ не может быть одним числом: значимость линии растёт как √t, и
     * время следует из того, что ищут. Строки называют ориентиры и правило,
     * по которому их пересчитывают под свой случай.
     */
    val infoDurationTitle: String
    val infoDurationRule: String
    val infoDurationMinute: String
    val infoDurationMinutes: String
    val infoDurationHalfHour: String
    val infoDurationHours: String
    val infoDurationCompare: String

    val infoActionsTitle: String
    val toolLineTitle: String
    val toolLineSubtitle: String

    /**
     * «Pb-214 и Bi-214 — дочерние продукты радона, один ряд распада».
     *
     * Утверждается только РОДСТВО, проверяемое по библиотеке линий: доли ряда
     * зависят от равновесия и возраста материала, поэтому ни родителя, ни
     * активности здесь назвать нельзя.
     */
    fun decayFamilyRadon(members: String): String
    fun decayFamilyChain(members: String, chain: String): String

    // --- экран: график и его подписи ---
    val legendMinusBackground: String
    val noLinkLastSpectrum: String

    // --- действия под спектром ---

    /** Кладёт спектр в журнал снимком. */
    val saveSnapshot: String

    /** Что делает «Сохранить снимок» — одной строкой под кнопкой. */
    val saveSnapshotNote: String

    /** Объявляет спектр эталоном обычной обстановки (`isBackgroundReference`). */
    val setAsBackground: String

    /** Что делает «Сделать фоном» — одной строкой под кнопкой. */
    val setAsBackgroundNote: String

    // --- полноэкранный спектр ---

    /** Курсор: энергия канала, на который смотрит палец. */
    fun cursorEnergy(keV: String): String

    /** Курсор: номер канала — не «имп/кэВ», ширина канала по шкале меняется. */
    fun cursorChannel(channel: Int): String

    /** Курсор: сырой счёт в этом канале. */
    fun cursorCounts(counts: String): String

    /** Курсор: в одной колонке экрана несколько каналов, показан максимум. */
    fun cursorMergedChannels(from: Int, to: Int): String

    /** Курсор: в этом месте найден пик — его значимость и измеренная ширина. */
    fun cursorPeak(significance: String, widthKeV: String): String

    /** Курсор: пик найден, но ширину этих отсчётов измерить не удалось. */
    fun cursorPeakNoWidth(significance: String): String

    /**
     * Подогнанная форма линии: во сколько раз левый хвост длиннее правого и
     * согласие модели с отсчётами (C/ndf).
     */
    fun cursorShape(asymmetry: String, agreement: String): String

    /** Форму одной линией описать не удалось — центр взят по центру тяжести. */
    val cursorShapeNone: String

    // --- отметка линии из справки о нуклиде ---

    /** Подпись отметки прямо на поле: «линия 661,7 кэВ». */
    fun lineMarkLabel(keV: String): String

    /** Что означает отметка: место по калибровке, а не найденный пик. */
    val lineMarkNote: String

    /** Окно зума доехало до этой энергии — картинка сменилась не сама. */
    val lineMarkWindowMoved: String

    /** Энергия вне шкалы прибора: показывать нечего. */
    val lineMarkOutOfScale: String

    /** Справка «i» полноэкранного режима: как поставить и снять курсор. */
    val infoCursor: String

    /** Справка «i» на вкладке: тап по графику открывает его во весь экран. */
    val infoFullscreen: String

    // --- справка «Как читать спектр», разложенная по вопросам и по глубине ---

    /** Заголовок панели справки. */
    val infoTitle: String

    /** Первый уровень: что вообще нарисовано. */
    val infoWhatTitle: String
    val infoAxisX: String
    val infoAxisY: String

    /** Первый уровень: почему выступ назван пиком. */
    val infoUnexplainedTitle: String
    val infoUnexplainedRule: String
    val infoUnexplainedLibrary: String
    val infoUnexplainedXray: String
    val infoUnexplainedCalibration: String
    val infoUnexplainedStatistics: String

    val infoPeakTitle: String
    val infoPeak: String

    /** Первый уровень: что значит колонка «совпадение» и чего она НЕ значит. */
    val infoCandidateTitle: String
    val infoCandidate: String

    /** Совпадение одной энергии не делает нуклид присутствующим. */
    val infoCandidateCaution: String

    /** Второй уровень: как из импульсов получилась эта картинка. */
    val infoPictureTitle: String
    val infoColumns: String
    val infoScales: String
    val infoSmoothing: String

    /** Второй уровень: крайний канал — граница шкалы (тело — [edgeExplanation]). */
    val infoEdgeTitle: String

    /** Третий уровень: как посчитана значимость пика. */
    val infoSignificanceTitle: String
    val infoSignificance: String

    /** Третий уровень: диагностика — калибровка, каналы, крайний канал. */
    val infoTechnicalTitle: String

    /** Кнопка, открывающая третий уровень справки целиком. */
    val infoHowToggle: String

    // --- дополнительный анализ: отдельные инструменты, а не вид этого графика ---
    val toolsTitle: String
    val toolFoodTitle: String
    val toolFoodSubtitle: String
    val toolCompareTitle: String
    val toolCompareSubtitle: String
    val toolSpectrogramTitle: String
    val toolSpectrogramSubtitle: String
    val toolRadonTitle: String
    val toolRadonSubtitle: String

    /** Съёмка по станциям: K, eU, eTh на точках маршрута. */
    val toolSurveyTitle: String
    val toolSurveySubtitle: String

    // --- просмотр сохранённого снимка (История → снимок) ---

    /** Чип режима: на экране снимок, а не живое накопление. */
    val snapshotViewTag: String

    /** Снимок ещё читается из базы: про прибор в этот момент говорить нечего. */
    val spectrumLoading: String

    /** Файл прочитан и разобран, но снимок не удалось положить в журнал. */
    val importNotSaved: String

    /** Подпись снимка в шапке: когда снят и сколько копился. */
    fun snapshotTakenAt(at: String, accumulation: String): String

    /** Прибор снимка не хранится — анализ идёт как у неопознанного прибора. */
    val snapshotDeviceUnknown: String

    /** Почему приборные действия недоступны у снимка. */
    val snapshotNoDevice: String
    val unknownScintillator: String
    fun noPeakAnalysis(model: String, crystal: String): String
    fun backgroundRecorded(at: String, accumulation: String): String
    val differenceNote: String
    fun snapshotSavedAt(time: String): String

    // --- файловые операции (импорт RC-XML) ---
    val acknowledge: String
    val importFailed: String
    val importTooLarge: String
    val importUnreadable: String
    val importNotRecognised: String
    val importChooseXml: String
    val importedTitle: String
    fun importedSummary(label: String, channels: String, clock: String): String
    val importedBackgroundRow: String
    val importedInHistory: String

    // --- спектральные диапазоны (спец §7) ---
    val windowsTitle: String
    val boundsChip: String
    val columnWindow: String
    val columnCounts: String
    val columnRate: String
    val columnShare: String
    val indexNote: String
    val windowsEdgeNote: String
    val boundsTitle: String
    val boundsHint: String
    val defaults: String

    // --- спектральное отношение (бывший «индекс») ---
    val ratioTitle: String
    fun ratioFormula(low: String, high: String): String
    val ratioWhat: String
    val ratioNotHardness: String
    val rangeDetails: String
    fun rangeCounts(counts: String): String
    fun rangeCovered(span: String): String

    // --- настройка границ на самом спектре ---
    /** Настройки: зачем эти границы и когда их двигают. */
    val rangesSettingsNote: String

    /** Править границы вслепую нельзя — нужна кривая под руками. */
    val boundsNeedSpectrum: String

    val boundsEditorTitle: String
    val boundsLower: String
    fun boundsInner(number: Int): String
    val boundsUpper: String
    val boundsExact: String
    val presetDefault: String
    val presetFullScale: String
    val presetCustom: String
    val presetFullScaleNote: String
    val resetBounds: String
    val done: String

    // --- нижние действия экрана ---
    val moreActions: String

    // --- компактный экран (V3) ---

    /** Сводка спектра под заголовком: профиль · накопление · импульсы. */
    fun spectrumSummary(profile: String, accumulation: String, counts: String): String

    /** Профиль не выбран — так и говорим, а не молчим. */
    val noProfileShort: String

    /** Короткие множители в сводке: «млн», «тыс». */
    val unitMillions: String
    val unitThousands: String

    /** Снимок объявлен обычной обстановкой места. */
    val backgroundTag: String


    /** Раздел «Анализ» — одна строка вместо карточки со списком. */
    val analysisRow: String

    /** Технические данные: то, что нужно редко и подробно. */
    val technicalTitle: String

    /** Действие снимка названо результатом: появится снимок. */
    val makeSnapshot: String

    /** Подтверждение сброса — что именно очистится. */
    val resetConfirmTitle: String
    val resetConfirmBody: String
    val resetAccumulation: String
    val formatsTitle: String

    // --- форматирование (SpectrumFormat) ---
    val unitKeV: String
    val unitCounts: String
    /** «1024 канала» / «1024 channels» — число и его форма склонения. */
    fun channels(count: Int): String
    val confidenceLow: String
    val confidenceMedium: String
    val natural: String
    fun alsoResembles(list: String): String
    fun candidateNatural(isotope: String): String
    fun candidateConfidence(isotope: String, confidence: String): String

    // --- колонка «возможное совпадение» из движка доказательств (ADR 006) ---

    /** «Cs-137 · 1 линия» — сколько линий кандидата совпало; не «обнаружен». */
    fun candidateLines(isotope: String, lines: Int): String

    /** «Pb-214 / I-131 — прибор эти линии не разделяет». */
    fun ambiguityNote(group: String): String

    /** Прочерк в колонке объясняется в деталях строки. */
    fun contradictedNote(nuclides: String): String

    /** Ячейка артефакта: аннигиляция. */
    val artifactAnnihilation: String

    /** Детали: «аннигиляционный пик 511 кэВ …». */
    val artifactAnnihilationNote: String

    val artifactEscape: String

    /** «escape-пик от 2614,5 кэВ …» — родитель и вычтенная энергия. */
    fun artifactEscapeNote(parent: String, shift: String): String

    val artifactSum: String

    /** «сумма 1173,2 + 1332,5 кэВ …» — слагаемые и нуклид каскада. */
    fun artifactSumNote(first: String, second: String, nuclide: String): String

    val artifactBackscatter: String
    val artifactBackscatterNote: String

    val artifactXray: String

    /** «K-серия тяжёлых элементов (Pb 84,9 · Bi 87,3 кэВ) …». */
    fun artifactXrayNote(lines: String): String

    /** Пометка строки пика, которому не нашлось ни линии, ни артефакта. */
    val noExplanationNote: String

    /** Линии, совместимые с пиком-артефактом (511 и Tl-208 510,8). */
    fun artifactCompatibleNote(nuclides: String): String

    fun calibrationLine(formula: String, channels: String): String

    // --- отказы сравнения снимков (SpectrumCompare) ---
    fun intervalChannelMismatch(first: Int, second: Int): String
    val intervalSameDuration: String
    fun intervalCalibrationMismatch(deltaKeV: String): String
    fun intervalNegativeChannels(count: Int): String
    val intervalOrderWarning: String
    fun intervalWallClockWider(wallSeconds: Long, deltaSeconds: Long): String
    val ratesZeroDuration: String
    fun ratesChannelMismatch(first: Int, second: Int): String
    fun ratesResampled(deltaKeV: String): String

    // --- отказы объединения (SpectrumMerge) ---
    val mergeNeedsTwo: String
    fun mergeChannelMismatch(input: Int, base: Int): String
    fun mergeCalibrationMismatch(input: String, base: String, deltaKeV: String): String

    // --- проверка границ окон (EnergyWindows.validate) ---
    val windowsNeedOne: String
    val windowBoundsNotNumbers: String
    fun windowBoundsOutOfRange(min: Int, max: Int): String
    fun windowTooNarrow(minWidth: Int): String
    val windowsOverlap: String

    // --- край энергетической шкалы (SpectrumEdge) ---
    val edgeExplanation: String

    // --- сравнение формы спектра (ShapeChange) ---
    fun shapeNotEnoughData(reference: String, excursion: String): String
    fun shapeChiSquare(bins: Int, z: String): String
}

object SpectrumRu : SpectrumStrings {

    override val showBackgroundCurve = "фон"
    override fun peakDetails(netCounts: String, significance: String) =
        "площадь $netCounts имп · значимость $significance"

    override fun peakCentre(centre: String, max: String) =
        "центр линии $centre кэВ, самый высокий канал $max кэВ. Подпись — центр тяжести чистой " +
            "площади, а не максимум: у линии с низкоэнергетическим хвостом он смещён в хвост. " +
            "Сравнение с линиями нуклидов идёт по центру."
    override val scaleCorrected = "энергии с поправкой шкалы"
    override val continuumChip = "континуум"
    override val continuumHint =
        "Пунктир — оценка гладкой подложки под линиями (SNIP). Это форма, а не измерение: " +
            "площадь и значимость линий считаются по боковым полосам, и пунктир на них не " +
            "влияет."
    override fun backgroundRecordedAt(time: String) = "фон записан · $time"

    override val needBackgroundTitle = "Фон ещё не записан"
    override val needBackgroundCurve =
        "Чип фона накладывает записанный фон серой кривой, а вторым нажатием " +
            "вычитает его. Пока фона нет, ни рисовать, ни вычитать нечего."
    override val needBackgroundHow =
        "Наберите спектр там, где обстановка обычная, и нажмите «Сделать фоном». " +
            "Прибор при этом стоит неподвижно."
    override val needBackgroundNoDevice = "Прибор не подключён — фон записывать нечем."
    override val infoDurationTitle = "Сколько копить"
    override val infoDurationRule =
        "Значимость линии растёт как корень из времени: чтобы удвоить её, копить надо " +
            "вчетверо дольше. Ниже — ориентиры при обычном фоне; рядом с источником всё " +
            "то же получается быстрее."
    override val infoDurationMinute =
        "До 1 мин — пики не разбираются вовсе: на такой статистике таблица менялась бы " +
            "от секунды к секунде."
    override val infoDurationMinutes =
        "2–5 мин — заметный источник рядом: линия видна формой, кандидат держится."
    override val infoDurationHalfHour =
        "15–30 мин — природные линии фона: калий-40 (1460 кэВ), торий-232 через " +
            "таллий-208 (2614 кэВ), продукты распада радона."
    override val infoDurationHours =
        "1–3 ч — слабые линии и отношение двух линий одного нуклида; на диагностику " +
            "калибровки по фону нужны часы и дольше."
    override val infoDurationCompare =
        "Для сравнения с фоном время считается по формуле t = 2σ²/(p²·R): при фоне 25 имп/с " +
            "добавка 10 % различима за ~1 мин, 5 % — за ~5 мин, 2 % — за полчаса. Прибор при " +
            "этом стоит неподвижно, а накопление не сбрасывается."

    override val infoActionsTitle = "Что делают кнопки"
    override val toolLineTitle = "Линия во времени"
    override val toolLineSubtitle = "как менялся счёт в окне линии выбранного нуклида"

    override fun decayFamilyRadon(members: String) =
        "$members — дочерние продукты радона: один ряд распада, вместе они и встречаются"
    override fun decayFamilyChain(members: String, chain: String) =
        "$members — один ряд распада ($chain), вместе они и встречаются"

    override val legendMinusBackground = "−фон"
    override val noLinkLastSpectrum = "нет соединения — показан последний прочитанный спектр"

    override val saveSnapshot = "Сохранить в историю"
    override val saveSnapshotNote = "спектр уйдёт в журнал: История, экспорт, сравнение"
    override val setAsBackground = "Сделать фоном"
    override val setAsBackgroundNote =
        "этот спектр станет обычной обстановкой — его вычитает режим «− фон»"

    override fun cursorEnergy(keV: String) = "$keV $unitKeV"
    override fun cursorChannel(channel: Int) = "канал $channel"
    override fun cursorCounts(counts: String) = "$counts имп в канале"
    override fun cursorMergedChannels(from: Int, to: Int) =
        "в колонке каналы $from–$to, показан максимум"
    override fun cursorPeak(significance: String, widthKeV: String) =
        "пик: значимость $significance · ширина $widthKeV $unitKeV"
    override fun cursorPeakNoWidth(significance: String) =
        "пик: значимость $significance · ширина не измерена"
    override fun cursorShape(asymmetry: String, agreement: String) =
        "форма: хвост слева ×$asymmetry · C/ndf $agreement"
    override val cursorShapeNone = "форма: одной линией не описана, центр по центру тяжести"
    override fun lineMarkLabel(keV: String) = "линия $keV $unitKeV"
    override val lineMarkNote = "Отметка показывает, где эта линия ожидается по калибровке " +
        "прибора. Это не найденный пик и не вывод о спектре."
    override val lineMarkWindowMoved = "Окно сдвинуто к этой энергии."
    override val lineMarkOutOfScale = "Эта линия лежит за шкалой прибора — места на спектре " +
        "у неё нет."
    override val infoCursor = "Удержание пальца на поле ставит курсор: он называет канал, " +
        "его энергию и счёт в нём. Перетаскивание ведёт курсор, одиночное касание снимает его."
    override val infoFullscreen = "Щипок двумя пальцами приближает участок шкалы. Тап по " +
        "графику открывает спектр во весь экран: там те же масштабы и жесты, удержание " +
        "пальца ставит курсор по каналам, а двойной тап возвращает всю шкалу."

    override val infoTitle = "Как читать спектр"
    override val infoWhatTitle = "Что на графике"
    override val infoAxisX = "Горизонтальная ось — энергия излучения: чем правее событие, " +
        "тем больше его измеренная энергия."
    override val infoAxisY = "Вертикальная ось — число зарегистрированных событий в канале " +
        "за всё накопление. Выступы на кривой называются пиками: они могут соответствовать " +
        "характерным энергиям гамма-излучения нуклидов."
    override val infoUnexplainedTitle = "Пик без объяснения"
    override val infoUnexplainedRule =
        "Прочерк в колонке совпадения означает не «здесь ничего нет», а «в библиотеке " +
            "приложения нет линии на этой энергии, и известные артефакты этот пик не " +
            "описывают». Причин обычно четыре."
    override val infoUnexplainedLibrary =
        "Нуклида нет в библиотеке: в ней природные ряды, калий-40 и то, что встречается " +
            "в быту, медицине и технике. Реакторных и лабораторных нуклидов в ней нет."
    override val infoUnexplainedXray =
        "Характеристический рентген: 73–90 кэВ дают K-линии свинца и висмута, 90–115 кэВ — " +
            "тория и урана. Его выбивает гамма-излучение из свинца окружения и из ядер самих " +
            "рядов; вся серия сливается прибором в один бугор."
    override val infoUnexplainedCalibration =
        "Сдвиг шкалы: если необъяснённой оказалась не одна энергия, а все сразу и на " +
            "одну и ту же величину, дело в калибровке прибора, а не в нуклидах."
    override val infoUnexplainedStatistics =
        "Мало импульсов: на коротком накоплении случайный выброс имеет ширину пика. " +
            "Копите дальше — настоящая линия растёт, выброс расходится."
    override val infoPeakTitle = "Что означает найденный пик"
    override val infoPeak = "Приложение отмечает выступы, которые заметно поднимаются над " +
        "окружающим спектром и имеют ширину, совместимую с разрешением детектора. " +
        "Одноканальный выброс пиком не считается."
    override val infoCandidateTitle = "Что означает «возможное совпадение»"
    override val infoCandidate = "Если энергия пика близка к известной гамма-линии, " +
        "приложение показывает возможный нуклид."
    override val infoCandidateCaution = "Совпадение одной энергии ещё не означает, что этот " +
        "нуклид действительно присутствует. Уверенность растёт, когда совпадают несколько " +
        "характерных линий одного нуклида и накоплено достаточно данных."
    override val infoPictureTitle = "Как построена картинка"
    override val infoColumns = "В одну колонку экрана попадает несколько каналов, и берётся " +
        "их максимум: узкий пик не теряется при отдалении, но линия континуума проходит по " +
        "верхней огибающей."
    override val infoScales = "Масштаб оси: линейный передаёт отношение площадей, но " +
        "прижимает всё, кроме самого высокого, к нулю; логарифмический показывает и " +
        "одиночные отсчёты, и фотопик, но зрительно уравнивает величины, различающиеся в " +
        "разы; степенной 1/n — промежуточный (1/2 — привычный корень). Все три — монотонные " +
        "преобразования одного числа: меняется распределение высоты, а не данные."
    override val infoSmoothing = "Сглаживание меняет только отображение: исходные импульсы " +
        "не трогаются."
    override val infoEdgeTitle = "Край энергетической шкалы"
    override val infoSignificanceTitle = "Как считается значимость"
    override val infoSignificance = "Значимость пика — его нетто-площадь, делённая на " +
        "собственную стандартную неопределённость: в неё входит и статистика окна пика, и " +
        "неопределённость оценки континуума под ним. Ширина структуры обязана лежать в " +
        "0,5–2,5 ожидаемой по модели разрешения детектора, иначе это не фотопик."
    override val infoTechnicalTitle = "Технические данные"
    override val infoHowToggle = "Как это посчитано"

    override val toolsTitle = "Дополнительный анализ"
    override val toolFoodTitle = "Проверить продукт"
    override val toolFoodSubtitle =
        "фон и образец в одной геометрии: скрининг гамма-излучения"
    override val toolCompareTitle = "A/B сравнение"
    override val toolCompareSubtitle = "Сравнить два спектра"
    override val toolSpectrogramTitle = "Спектрограмма"
    override val toolSpectrogramSubtitle = "Изменение спектра во времени"
    override val toolRadonTitle = "Радон"
    override val toolRadonSubtitle = "Анализ признаков цепочки Rn-222"
    override val toolSurveyTitle = "Съёмка U-Th-K"
    override val toolSurveySubtitle = "Калий, уран и торий по станциям"

    override val snapshotViewTag = "Снимок"
    override val spectrumLoading = "снимок читается…"
    override val importNotSaved =
        "файл прочитан, но снимок не удалось сохранить в журнал"
    override fun snapshotTakenAt(at: String, accumulation: String) =
        "снят $at · накопление $accumulation"
    override val snapshotDeviceUnknown = "Прибор этого снимка не записан: пики и совпадения " +
        "линий посчитаны как для неопознанного прибора — по самому широкому опубликованному " +
        "разрешению серии. Более узкое окно искало бы структуру там, где её нет."
    override val snapshotNoDevice = "Это сохранённый спектр: он уже в журнале, а сброс " +
        "накопления, запись фона и продолжение накопления относятся к подключённому прибору, " +
        "которого у снимка нет."
    override val unknownScintillator = "неизвестный сцинтиллятор"

    override fun noPeakAnalysis(model: String, crystal: String) =
        "$model: детектор без энергетического разрешения ($crystal) — поиск пиков и " +
            "совпадения с линиями нуклидов для него не считаются."

    override fun backgroundRecorded(at: String, accumulation: String) =
        "фон: $at · $accumulation"

    override val differenceNote = "показана разница спектра и записанного фона, не меньше нуля"

    override fun snapshotSavedAt(time: String) = "снимок сохранён в $time — он виден в Истории"

    override val acknowledge = "Понятно"
    override val importFailed = "Импорт не удался"
    override val importTooLarge = "Файл больше 20 МБ — это не похоже на спектр RadiaCode."
    override val importUnreadable = "Файл не удалось прочитать — попробуйте выбрать его ещё раз."
    override val importNotRecognised = "файл не распознан"
    override val importChooseXml = "Выберите XML-файл спектра RadiaCode."
    override val importedTitle = "Спектр импортирован"

    override fun importedSummary(label: String, channels: String, clock: String) =
        "«$label» · $channels · Δt $clock"

    override val importedBackgroundRow = "Фоновый спектр из файла сохранён отдельной строкой."
    override val importedInHistory = "Снимок появился в Истории — там же сравнение и экспорт."

    override val windowsTitle = "Спектральные диапазоны"


    override val boundsChip = "настроить…"
    override val columnWindow = "диапазон, кэВ"
    override val columnCounts = "имп"
    override val columnRate = "счёт ± σ"
    override val columnShare = "доля"

    override val indexNote =
        "Спектральное отношение — описательная характеристика состава спектра, " +
            "а не мера опасности и не дозиметрическая величина. Границы диапазонов — " +
            "параметр анализа, а не физические категории излучения."

    override val windowsEdgeNote =
        "Канал целиком относится к окну, если его центр попал внутрь: дробить счёт " +
            "по краю нельзя — дробный счёт перестаёт быть пуассоновским."

    override val boundsTitle = "Границы диапазонов"
    override val boundsHint =
        "Диапазоны — параметр анализа, а не физические категории излучения. " +
            "Границы задаются в кэВ и идут по возрастанию, поэтому диапазоны " +
            "стыкуются без пересечений и разрывов."
    override val defaults = "По умолчанию"

    override val ratioTitle = "Спектральное отношение"

    override fun ratioFormula(low: String, high: String) = "S = R($low) / R($high), кэВ"

    override val ratioWhat =
        "Отношение скорости счёта в нижнем диапазоне к скорости счёта в верхнем. " +
            "Оно описывает состав зарегистрированного спектра — какая доля импульсов " +
            "пришла снизу шкалы, а какая сверху — и почти не зависит от общей " +
            "интенсивности: то же поле, ставшее ярче, даёт то же отношение."

    override val ratioNotHardness =
        "Это не «жёсткость»: жёсткость — коэффициент самого прибора, мощность дозы " +
            "на единицу скорости счёта (Ḋ/R). Спектральное отношение считается по двум " +
            "участкам спектра. Величины разные, и одна не заменяет другую."

    override val rangeDetails = "подробности"
    override fun rangeCounts(counts: String) = "импульсов: $counts"
    override fun rangeCovered(span: String) = "покрыто каналами: $span кэВ"

    override val rangesSettingsNote =
        "Границы делят спектр на участки, скорости счёта в которых сравниваются между собой. " +
            "Это параметр анализа, а не физические категории излучения."
    override val boundsNeedSpectrum =
        "чтобы двигать границы по кривой, нужен накопленный спектр — подключите прибор"
    override val boundsEditorTitle = "Настройка диапазонов"
    override val boundsLower = "нижняя"
    override fun boundsInner(number: Int) = "граница $number"
    override val boundsUpper = "верхняя"
    override val boundsExact = "Значение границы, кэВ"
    override val presetDefault = "По умолчанию"
    override val presetFullScale = "Весь диапазон прибора"
    override val presetCustom = "Свои"
    override val presetFullScaleNote =
        "Вся шкала прибора, поделённая на равные части: деление нейтральное, " +
            "физического смысла у самих частей нет."
    override val resetBounds = "Сбросить"
    override val done = "Готово"

    override val moreActions = "Ещё"

    override fun spectrumSummary(profile: String, accumulation: String, counts: String) =
        "$profile · $accumulation · $counts"
    override val noProfileShort = "Без профиля"
    override val unitMillions = "млн"
    override val unitThousands = "тыс"
    override val backgroundTag = "фоновый"
    override val analysisRow = "Анализ"
    override val technicalTitle = "Технические данные"
    override val makeSnapshot = "Создать снимок"
    override val resetConfirmTitle = "Сбросить накопленный спектр?"
    override val resetConfirmBody =
        "Прибор начнёт накопление заново. Сохранённые снимки и спектрограмма останутся: " +
            "спектрограмма — запись того, что уже измерено, и сброс накопления её не стирает."
    override val resetAccumulation = "Сбросить накопление"
    override val formatsTitle = "О форматах"

    override val unitKeV = "кэВ"
    override val unitCounts = "имп"

    override fun channels(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        val word = when {
            mod100 in 11..14 -> "каналов"
            mod10 == 1 -> "канал"
            mod10 in 2..4 -> "канала"
            else -> "каналов"
        }
        return "$count $word"
    }

    override val confidenceLow = "низкая"
    override val confidenceMedium = "средняя"
    override val natural = "природный"
    override fun alsoResembles(list: String) = "также похоже: $list"
    override fun candidateNatural(isotope: String) = "$isotope · $natural"
    override fun candidateConfidence(isotope: String, confidence: String) =
        "$isotope · $confidence ур."

    override fun candidateLines(isotope: String, lines: Int): String {
        val word = when {
            lines % 100 in 11..14 -> "линий"
            lines % 10 == 1 -> "линия"
            lines % 10 in 2..4 -> "линии"
            else -> "линий"
        }
        return "$isotope · $lines $word"
    }

    override fun ambiguityNote(group: String) = "$group — прибор эти линии не разделяет"

    override fun contradictedNote(nuclides: String) =
        "$nuclides: противоречит ожидаемым линиям"

    override val artifactAnnihilation = "аннигиляция 511 кэВ"
    override val artifactAnnihilationNote =
        "аннигиляционный пик 511 кэВ: такой пик даёт любое излучение, рождающее " +
            "пары, — отдельный нуклид для него не нужен"
    override val artifactEscape = "escape-пик"
    override fun artifactEscapeNote(parent: String, shift: String) =
        "escape-пик от $parent кэВ: из детектора вылетело аннигиляционное " +
            "излучение (E − $shift кэВ)"
    override val artifactSum = "сумма каскада"
    override fun artifactSumNote(first: String, second: String, nuclide: String) =
        "сумма $first + $second кэВ: каскад $nuclide, зарегистрированный одним событием"
    override val artifactBackscatter = "обратное рассеяние"
    override val artifactBackscatterNote =
        "область обратного рассеяния 200–255 кэВ: фотоны, рассеянные окружением " +
            "назад в детектор"
    override val artifactXray = "рентген K-серии"
    override fun artifactXrayNote(lines: String) =
        "K-серия тяжёлых элементов ($lines): гамма-излучение выбивает электрон K-оболочки " +
            "в свинце окружения или в ядрах самих рядов"
    override val noExplanationNote =
        "ни линии библиотеки, ни известного артефакта на этой энергии — см. справку " +
            "«Пик без объяснения»"
    override fun artifactCompatibleNote(nuclides: String) =
        "с этим пиком совместимы и линии: $nuclides"

    override fun calibrationLine(formula: String, channels: String) =
        "калибровка: E = $formula · $channels"

    override fun intervalChannelMismatch(first: Int, second: Int) =
        "у снимков разное число каналов ($first и $second) — это не одно накопление"

    override val intervalSameDuration =
        "у снимков одинаковое время накопления — между ними нет интервала"

    override fun intervalCalibrationMismatch(deltaKeV: String) =
        "калибровки энергии различаются на $deltaKeV кэВ — " +
            "снимки не из одного накопления, используйте сравнение скоростей"

    override fun intervalNegativeChannels(count: Int) =
        "в $count каналах счёт позднего снимка меньше раннего — " +
            "накопление сбрасывалось между снимками, вычесть интервал нельзя"

    override val intervalOrderWarning =
        "порядок сохранения снимков не совпадает с порядком накопления — " +
            "проверьте, те ли снимки выбраны"

    override fun intervalWallClockWider(wallSeconds: Long, deltaSeconds: Long) =
        "между снимками прошло $wallSeconds с по часам, а накопления — " +
            "$deltaSeconds с: измерение прерывалось, интервал в часах шире Δt"

    override val ratesZeroDuration =
        "у одного из снимков нулевое время накопления — скорость счёта не определена"

    override fun ratesChannelMismatch(first: Int, second: Int) =
        "у снимков разное число каналов ($first и $second)"

    override fun ratesResampled(deltaKeV: String) =
        "калибровки различаются на $deltaKeV кэВ — счёт B " +
            "пересчитан на энергетическую сетку A (перераспределение по перекрытию " +
            "бинов); погрешность после пересчёта приближённая"

    override val mergeNeedsTwo = "для объединения нужно минимум два снимка"

    override fun mergeChannelMismatch(input: Int, base: Int) =
        "у снимков разное число каналов ($input и $base) — объединить нельзя"

    override fun mergeCalibrationMismatch(input: String, base: String, deltaKeV: String) =
        "калибровки «$input» и «$base» расходятся на $deltaKeV кэВ — сумма размажет пики; " +
            "для таких снимков используйте сравнение скоростей счёта"

    override val windowsNeedOne = "нужно хотя бы одно окно"
    override val windowBoundsNotNumbers = "границы окна должны быть числами"

    override fun windowBoundsOutOfRange(min: Int, max: Int) =
        "границы окон должны лежать в диапазоне прибора $min–$max кэВ"

    override fun windowTooNarrow(minWidth: Int) =
        "окно уже $minWidth кэВ — это меньше разрешения прибора"

    override val windowsOverlap = "окна пересекаются — импульс попал бы в два окна сразу"

    override val edgeExplanation =
        "Последний канал — граница энергетической шкалы. Что происходило выше неё, " +
            "прибор в этой шкале не различает, поэтому крайний канал не показан на " +
            "кривой и не участвует в поиске пиков: всплеск шириной в один канал не " +
            "является пиком."

    override fun shapeNotEnoughData(reference: String, excursion: String) =
        "спектральных данных пока мало: $reference и $excursion импульсов"

    override fun shapeChiSquare(bins: Int, z: String) = "χ² по $bins корзинам, z = $z"
}

object SpectrumEn : SpectrumStrings {

    override val showBackgroundCurve = "background"
    override fun peakDetails(netCounts: String, significance: String) =
        "area $netCounts counts · significance $significance"

    override fun peakCentre(centre: String, max: String) =
        "line centre $centre keV, tallest channel $max keV. The label is the centroid of the net " +
            "area, not the maximum: a line with a low-energy tail has its centroid pulled into " +
            "that tail. Matching against nuclide lines uses the centre."

    override val scaleCorrected = "energies with the scale correction"
    override val continuumChip = "continuum"
    override val continuumHint =
        "The dashed line estimates the smooth continuum under the lines (SNIP). It is a shape, " +
            "not a measurement: line areas and significance come from the side bands and are " +
            "unaffected by it."
    override fun backgroundRecordedAt(time: String) = "background recorded · $time"

    override val needBackgroundTitle = "No background recorded yet"
    override val needBackgroundCurve =
        "The background chip draws the recorded background as a grey curve and, on a " +
            "second tap, subtracts it. With no background there is nothing to draw or " +
            "subtract."
    override val needBackgroundHow =
        "Collect a spectrum where the surroundings are ordinary and press «Set as " +
            "background». Keep the instrument still while it collects."
    override val needBackgroundNoDevice = "No instrument connected — nothing to record a background with."
    override val infoDurationTitle = "How long to collect"
    override val infoDurationRule =
        "The significance of a line grows as the square root of time: doubling it takes " +
            "four times longer. The figures below are for an ordinary background; next to a " +
            "source the same result comes sooner."
    override val infoDurationMinute =
        "Under 1 min — peaks are not analysed at all: on that statistic the table would " +
            "change from second to second."
    override val infoDurationMinutes =
        "2–5 min — a noticeable source nearby: the line shows as a shape and the candidate " +
            "holds."
    override val infoDurationHalfHour =
        "15–30 min — the natural lines of the background: potassium-40 (1460 keV), " +
            "thorium-232 through thallium-208 (2614 keV), the decay products of radon."
    override val infoDurationHours =
        "1–3 h — weak lines and the ratio of two lines of one nuclide; diagnosing the " +
            "calibration from the background takes hours and more."
    override val infoDurationCompare =
        "For a comparison with the background the time follows t = 2σ²/(p²·R): at 25 counts/s " +
            "an addition of 10 % is distinguishable in ~1 min, 5 % in ~5 min, 2 % in half an " +
            "hour. The instrument stays still and the accumulation is not reset."

    override val infoActionsTitle = "What the buttons do"
    override val toolLineTitle = "A line over time"
    override val toolLineSubtitle = "how the count in a chosen nuclide's line window changed"

    override fun decayFamilyRadon(members: String) =
        "$members are radon daughters: one decay chain, and they occur together"
    override fun decayFamilyChain(members: String, chain: String) =
        "$members belong to one decay chain ($chain) and occur together"

    override val legendMinusBackground = "−background"
    override val noLinkLastSpectrum = "no link — showing the last spectrum that was read"

    override val saveSnapshot = "Save to history"
    override val saveSnapshotNote = "the spectrum goes to the journal: History, export, comparison"
    override val setAsBackground = "Set as background"
    override val setAsBackgroundNote =
        "this spectrum becomes the usual surroundings — mode «− background» subtracts it"

    override fun cursorEnergy(keV: String) = "$keV $unitKeV"
    override fun cursorChannel(channel: Int) = "channel $channel"
    override fun cursorCounts(counts: String) = "$counts counts in the channel"
    override fun cursorMergedChannels(from: Int, to: Int) =
        "the column holds channels $from–$to, the maximum is drawn"
    override fun cursorPeak(significance: String, widthKeV: String) =
        "peak: significance $significance · width $widthKeV $unitKeV"
    override fun cursorPeakNoWidth(significance: String) =
        "peak: significance $significance · width not measured"
    override fun cursorShape(asymmetry: String, agreement: String) =
        "shape: left tail ×$asymmetry · C/ndf $agreement"
    override val cursorShapeNone = "shape: not described by one line, center from the centroid"
    override fun lineMarkLabel(keV: String) = "line $keV $unitKeV"
    override val lineMarkNote = "The mark shows where this line is expected by the energy " +
        "calibration. It is not a peak found in the spectrum and not a conclusion about it."
    override val lineMarkWindowMoved = "The window moved to this energy."
    override val lineMarkOutOfScale = "This line lies beyond the instrument scale — there is " +
        "no place for it on the spectrum."
    override val infoCursor = "Holding a finger on the field puts down a cursor: it names the " +
        "channel, its energy and the counts in it. Dragging moves the cursor, a single tap " +
        "lifts it."
    override val infoFullscreen = "A two-finger pinch zooms into a part of the scale. Tapping " +
        "the chart opens the spectrum full screen: the same scales and gestures, a channel " +
        "cursor under a long press, and a double tap back to the whole scale."

    override val infoTitle = "How to read this spectrum"
    override val infoWhatTitle = "What the chart shows"
    override val infoAxisX = "The horizontal axis is the energy of the radiation: the further " +
        "right an event sits, the higher its measured energy."
    override val infoAxisY = "The vertical axis is the number of registered events in a " +
        "channel over the whole accumulation. The bumps on the curve are called peaks: they " +
        "may correspond to characteristic gamma energies of nuclides."
    override val infoUnexplainedTitle = "A peak with no explanation"
    override val infoUnexplainedRule =
        "A dash in the match column does not mean «there is nothing here». It means «the " +
            "library of the app has no line at this energy, and the known artifacts do not " +
            "describe this peak». There are usually four reasons."
    override val infoUnexplainedLibrary =
        "The nuclide is not in the library: it holds the natural chains, potassium-40 and " +
            "what turns up in everyday life, medicine and industry. Reactor and laboratory " +
            "nuclides are not in it."
    override val infoUnexplainedXray =
        "Characteristic X-rays: 73–90 keV comes from the K lines of lead and bismuth, " +
            "90–115 keV from those of thorium and uranium. Gamma radiation knocks them out " +
            "of lead in the surroundings and of the nuclei of the chains themselves; the " +
            "instrument merges the whole series into one bump."
    override val infoUnexplainedCalibration =
        "A shift of the scale: if it is not one energy that went unexplained but all of " +
            "them, by the same amount, the calibration of the instrument is the reason, not " +
            "the nuclides."
    override val infoUnexplainedStatistics =
        "Too few counts: on a short accumulation a random spike has the width of a peak. " +
            "Keep collecting — a real line grows, a spike spreads out."
    override val infoPeakTitle = "What a found peak means"
    override val infoPeak = "The app marks bumps that rise noticeably above the surrounding " +
        "spectrum and whose width agrees with the resolution of the detector. A single-channel " +
        "spike does not count as a peak."
    override val infoCandidateTitle = "What «a possible match» means"
    override val infoCandidate = "When the energy of a peak is close to a known gamma line, " +
        "the app shows a possible nuclide."
    override val infoCandidateCaution = "A match of a single energy does not yet mean that " +
        "this nuclide is really there. Confidence grows when several characteristic lines of " +
        "the same nuclide match and enough data has been accumulated."
    override val infoPictureTitle = "How the picture is built"
    override val infoColumns = "One screen column holds several channels and takes their " +
        "maximum: a narrow peak survives zooming out, but the continuum line rides the upper " +
        "envelope."
    override val infoScales = "The counts axis: linear preserves the ratio of areas but " +
        "crushes everything but the tallest towards zero; logarithmic shows single counts " +
        "next to a photopeak but visually equalises quantities that differ severalfold; the " +
        "power scale 1/n sits between them (1/2 is the familiar square root). All three are " +
        "monotone transforms of the same number: the distribution of height changes, the " +
        "data does not."
    override val infoSmoothing = "Smoothing changes the display only: the underlying counts " +
        "are untouched."
    override val infoEdgeTitle = "The edge of the energy scale"
    override val infoSignificanceTitle = "How significance is computed"
    override val infoSignificance = "The significance of a peak is its net area divided by " +
        "the net area's own standard uncertainty, which includes both the statistics of the " +
        "peak window and the uncertainty of the continuum estimated under it. The width of " +
        "the structure has to lie within 0.5–2.5 of the width expected from the detector " +
        "resolution model, otherwise it is not a photopeak."
    override val infoTechnicalTitle = "Technical data"
    override val infoHowToggle = "How it is computed"

    override val toolsTitle = "Further analysis"
    override val toolFoodTitle = "Check a product"
    override val toolFoodSubtitle =
        "background and sample in one geometry: gamma screening"
    override val toolCompareTitle = "A/B comparison"
    override val toolCompareSubtitle = "Compare two spectra"
    override val toolSpectrogramTitle = "Spectrogram"
    override val toolSpectrogramSubtitle = "How the spectrum changes over time"
    override val toolRadonTitle = "Radon"
    override val toolRadonSubtitle = "Signs of the Rn-222 chain"
    override val toolSurveyTitle = "U-Th-K survey"
    override val toolSurveySubtitle = "Potassium, uranium and thorium by station"

    override val snapshotViewTag = "Snapshot"
    override val spectrumLoading = "reading the snapshot…"
    override val importNotSaved =
        "the file was read, but the snapshot could not be saved to the journal"
    override fun snapshotTakenAt(at: String, accumulation: String) =
        "taken $at · accumulation $accumulation"
    override val snapshotDeviceUnknown = "The instrument of this snapshot is not recorded: " +
        "peaks and line matches are computed as for an unidentified instrument — with the " +
        "widest published resolution of the series. A narrower window would look for " +
        "structure where there is none."
    override val snapshotNoDevice = "This is a stored spectrum: it is already in the journal, " +
        "and resetting the accumulation, recording a background or continuing it belong to a " +
        "connected instrument, which this snapshot has none of."
    override val unknownScintillator = "unknown scintillator"

    // «не считаются» = не вычисляются вовсе: у детектора без энергетического
    // разрешения совпадение линий не имеет смысла, а не «ненадёжно».
    override fun noPeakAnalysis(model: String, crystal: String) =
        "$model: a detector without energy resolution ($crystal) — peak search and " +
            "matches against nuclide lines are not computed for it."

    override fun backgroundRecorded(at: String, accumulation: String) =
        "background: $at · $accumulation"

    override val differenceNote =
        "showing the difference between the spectrum and the recorded background, " +
            "clipped at zero"

    override fun snapshotSavedAt(time: String) =
        "snapshot saved at $time — it is in History"

    override val acknowledge = "Got it"
    override val importFailed = "Import failed"
    override val importTooLarge =
        "The file is over 20 MB — that does not look like a RadiaCode spectrum."
    override val importUnreadable = "The file could not be read — try picking it again."
    override val importNotRecognised = "the file was not recognised"
    override val importChooseXml = "Choose a RadiaCode spectrum XML file."
    override val importedTitle = "Spectrum imported"

    override fun importedSummary(label: String, channels: String, clock: String) =
        "«$label» · $channels · Δt $clock"

    override val importedBackgroundRow =
        "The background spectrum from the file is stored as a separate row."
    override val importedInHistory =
        "The snapshot appeared in History — comparison and export are there too."

    override val windowsTitle = "Spectral ranges"


    override val boundsChip = "configure…"
    override val columnWindow = "range, keV"
    override val columnCounts = "counts"
    override val columnRate = "count ± σ"
    override val columnShare = "share"

    // «не мера опасности» → «not a measure of harm»: отношение ОПИСЫВАЕТ состав
    // спектра, и это ограничение обязано ехать в перевод вместе с ним.
    override val indexNote =
        "The spectral ratio describes the composition of the spectrum; it is not a " +
            "measure of harm and not a dosimetric quantity. The range bounds are a " +
            "parameter of the analysis, not physical categories of radiation."

    override val windowsEdgeNote =
        "A channel belongs to a window as a whole when its centre falls inside: the " +
            "counts cannot be split at the edge — a fractional count stops being Poisson."

    override val boundsTitle = "Range bounds"
    override val boundsHint =
        "The ranges are a parameter of the analysis, not physical categories of " +
            "radiation. The bounds are in keV and go in ascending order, so the ranges " +
            "meet without overlaps and without gaps."
    override val defaults = "Defaults"

    override val ratioTitle = "Spectral ratio"

    override fun ratioFormula(low: String, high: String) = "S = R($low) / R($high), keV"

    override val ratioWhat =
        "The count rate of the lower range divided by the count rate of the upper one. " +
            "It describes the composition of the recorded spectrum — which share of the " +
            "counts came from the bottom of the scale and which from the top — and " +
            "barely depends on the overall intensity: the same field made brighter " +
            "gives the same ratio."

    override val ratioNotHardness =
        "This is not «hardness»: hardness is a coefficient of the instrument itself, " +
            "the dose rate per unit count rate (Ḋ/R). The spectral ratio is computed " +
            "from two stretches of the spectrum. They are different quantities, and one " +
            "does not stand in for the other."

    override val rangeDetails = "details"
    override fun rangeCounts(counts: String) = "counts: $counts"
    override fun rangeCovered(span: String) = "covered by channels: $span keV"

    override val rangesSettingsNote =
        "The bounds split the spectrum into parts whose count rates are compared with each " +
            "other. This is a parameter of the analysis, not a physical category of radiation."
    override val boundsNeedSpectrum =
        "to drag the bounds along the curve an accumulated spectrum is needed — connect the " +
            "instrument"
    override val boundsEditorTitle = "Configure ranges"
    override val boundsLower = "lower"
    override fun boundsInner(number: Int) = "bound $number"
    override val boundsUpper = "upper"
    override val boundsExact = "Bound value, keV"
    override val presetDefault = "Defaults"
    override val presetFullScale = "Whole instrument scale"
    override val presetCustom = "Custom"
    override val presetFullScaleNote =
        "The whole scale of the instrument split into equal parts: the split is " +
            "neutral, the parts themselves carry no physical meaning."
    override val resetBounds = "Reset"
    override val done = "Done"

    override val moreActions = "More"

    override fun spectrumSummary(profile: String, accumulation: String, counts: String) =
        "$profile · $accumulation · $counts"
    override val noProfileShort = "No profile"
    override val unitMillions = "M"
    override val unitThousands = "k"
    override val backgroundTag = "background"
    override val analysisRow = "Analysis"
    override val technicalTitle = "Technical data"
    override val makeSnapshot = "Take a snapshot"
    override val resetConfirmTitle = "Clear the accumulated spectrum?"
    override val resetConfirmBody =
        "The instrument starts accumulating anew. Saved snapshots and the spectrogram stay: " +
            "the spectrogram is a record of what was already measured, and a reset does not " +
            "erase it."
    override val resetAccumulation = "Reset the accumulation"
    override val formatsTitle = "About the formats"

    override val unitKeV = "keV"
    override val unitCounts = "counts"

    override fun channels(count: Int) =
        if (count == 1) "$count channel" else "$count channels"

    override val confidenceLow = "low"
    // Выше «средней» шкала не идёт ни в одном языке: совпадение энергии — не
    // обнаружение, и «high»/«confirmed» здесь сказать нельзя.
    override val confidenceMedium = "medium"
    override val natural = "natural"
    override fun alsoResembles(list: String) = "also resembles: $list"
    override fun candidateNatural(isotope: String) = "$isotope · $natural"
    override fun candidateConfidence(isotope: String, confidence: String) =
        "$isotope · $confidence confidence"

    override fun candidateLines(isotope: String, lines: Int) =
        if (lines == 1) "$isotope · 1 line" else "$isotope · $lines lines"

    // «не разделяет» = физическое ограничение прибора, а не неудача анализа.
    override fun ambiguityNote(group: String) =
        "$group — the instrument cannot separate these lines"

    override fun contradictedNote(nuclides: String) =
        "$nuclides: contradicts the expected lines"

    override val artifactAnnihilation = "annihilation 511 keV"
    override val artifactAnnihilationNote =
        "the 511 keV annihilation peak: any radiation that produces pairs makes " +
            "this peak — no separate nuclide is needed for it"
    override val artifactEscape = "escape peak"
    override fun artifactEscapeNote(parent: String, shift: String) =
        "escape peak of $parent keV: annihilation radiation left the detector " +
            "(E − $shift keV)"
    override val artifactSum = "cascade sum"
    override fun artifactSumNote(first: String, second: String, nuclide: String) =
        "sum of $first + $second keV: a $nuclide cascade registered as one event"
    override val artifactBackscatter = "backscatter"
    override val artifactBackscatterNote =
        "the 200–255 keV backscatter region: photons scattered by the surroundings " +
            "back into the detector"
    override val artifactXray = "K-series X-rays"
    override fun artifactXrayNote(lines: String) =
        "the K series of heavy elements ($lines): gamma radiation knocks a K-shell electron " +
            "out of lead in the surroundings or of the nuclei of the chains themselves"
    override val noExplanationNote =
        "no library line and no known artifact at this energy — see «A peak with no " +
            "explanation» in the reference"
    override fun artifactCompatibleNote(nuclides: String) =
        "lines of $nuclides are also compatible with this peak"

    override fun calibrationLine(formula: String, channels: String) =
        "calibration: E = $formula · $channels"

    override fun intervalChannelMismatch(first: Int, second: Int) =
        "the snapshots have different channel counts ($first and $second) — " +
            "this is not one accumulation"

    override val intervalSameDuration =
        "the snapshots have the same accumulation time — there is no interval between them"

    override fun intervalCalibrationMismatch(deltaKeV: String) =
        "the energy calibrations differ by $deltaKeV keV — the snapshots are not from " +
            "one accumulation, use the rate comparison"

    override fun intervalNegativeChannels(count: Int) =
        "in $count channels the later snapshot counts fewer than the earlier one — " +
            "the accumulation was reset between the snapshots, the interval cannot " +
            "be subtracted"

    override val intervalOrderWarning =
        "the order in which the snapshots were saved does not match the order of " +
            "accumulation — check that these are the intended snapshots"

    override fun intervalWallClockWider(wallSeconds: Long, deltaSeconds: Long) =
        "$wallSeconds s passed between the snapshots by the clock, but the " +
            "accumulations differ by $deltaSeconds s: the measurement was interrupted, " +
            "the wall-clock interval is wider than Δt"

    override val ratesZeroDuration =
        "one of the snapshots has zero accumulation time — the count rate is undefined"

    override fun ratesChannelMismatch(first: Int, second: Int) =
        "the snapshots have different channel counts ($first and $second)"

    override fun ratesResampled(deltaKeV: String) =
        "the calibrations differ by $deltaKeV keV — the counts of B were resampled " +
            "onto the energy grid of A (redistributed by bin overlap); the uncertainty " +
            "after resampling is approximate"

    override val mergeNeedsTwo = "merging needs at least two snapshots"

    override fun mergeChannelMismatch(input: Int, base: Int) =
        "the snapshots have different channel counts ($input and $base) — they cannot be merged"

    override fun mergeCalibrationMismatch(input: String, base: String, deltaKeV: String) =
        "the calibrations of «$input» and «$base» diverge by $deltaKeV keV — the sum " +
            "would smear the peaks; for such snapshots use the count-rate comparison"

    override val windowsNeedOne = "at least one window is needed"
    override val windowBoundsNotNumbers = "the window bounds must be numbers"

    override fun windowBoundsOutOfRange(min: Int, max: Int) =
        "the window bounds must lie within the instrument's range of $min–$max keV"

    override fun windowTooNarrow(minWidth: Int) =
        "the window is narrower than $minWidth keV — that is below the instrument's resolution"

    override val windowsOverlap =
        "the windows overlap — a count would fall into two windows at once"

    // Механизм («прошивка складывает сюда всё, что вышло за диапазон») первичной
    // документацией не подтверждён: сказано только то, что край — это край.
    override val edgeExplanation =
        "The last channel is the boundary of the energy scale. What happened above it " +
            "the instrument does not resolve on this scale, so the edge channel is not " +
            "drawn on the curve and takes no part in the peak search: a spike one " +
            "channel wide is not a peak."

    override fun shapeNotEnoughData(reference: String, excursion: String) =
        "still little spectral data: $reference and $excursion counts"

    override fun shapeChiSquare(bins: Int, z: String) = "χ² over $bins bins, z = $z"
}

val SpectrumCatalogue = AreaCatalogue(ru = SpectrumRu, en = SpectrumEn)

/**
 * Все тексты области — для проверок, которые обязаны действовать на каждую
 * строку. Список ведётся руками: рефлексии в тестовом classpath нет, а
 * забытая строка означала бы непроверенный текст.
 */
fun SpectrumStrings.allTexts(): List<String> = listOf(
    spectrumSummary("Дом", "191 ч", "17,0 млн имп"), noProfileShort, backgroundTag,
    infoDurationTitle, infoDurationRule, infoDurationMinute, infoDurationMinutes,
    infoDurationHalfHour, infoDurationHours, infoDurationCompare,
    unitMillions, unitThousands, analysisRow, technicalTitle, makeSnapshot,
    resetConfirmTitle, resetConfirmBody,
    toolLineTitle, toolLineSubtitle, infoActionsTitle, showBackgroundCurve,
    peakDetails("81 007", "87σ"), scaleCorrected, continuumChip, continuumHint,
    backgroundRecordedAt("14:32"), needBackgroundTitle, needBackgroundCurve, needBackgroundHow,
    needBackgroundNoDevice,
    decayFamilyRadon("Pb-214, Bi-214"), decayFamilyChain("Pb-212, Tl-208", "Th-232"),
    rangesSettingsNote, boundsNeedSpectrum,
    spectrumLoading, importNotSaved,
    saveSnapshot, saveSnapshotNote, setAsBackground, setAsBackgroundNote,
    cursorEnergy("661,9"), cursorChannel(316), cursorCounts("12 480"),
    cursorMergedChannels(314, 318), cursorPeak("8,2σ", "47"), cursorPeakNoWidth("8,2σ"),
    cursorShape("2,1", "1,2"), cursorShapeNone,
    infoCursor, infoFullscreen,
    lineMarkLabel("661,7"), lineMarkNote, lineMarkWindowMoved, lineMarkOutOfScale,
    infoTitle, infoWhatTitle, infoAxisX, infoAxisY,
    infoPeakTitle, infoPeak, infoCandidateTitle, infoCandidate, infoCandidateCaution,
    infoPictureTitle, infoColumns, infoScales, infoSmoothing, infoEdgeTitle,
    infoSignificanceTitle, infoSignificance, infoTechnicalTitle, infoHowToggle,
    toolsTitle, toolCompareTitle, toolCompareSubtitle,
    toolSpectrogramTitle, toolSpectrogramSubtitle, toolRadonTitle, toolRadonSubtitle,
    toolSurveyTitle, toolSurveySubtitle,
    legendMinusBackground,    noLinkLastSpectrum, unknownScintillator,
    snapshotViewTag, snapshotTakenAt("12 авг 14:03", "51 ч"),
    snapshotDeviceUnknown, snapshotNoDevice,
    noPeakAnalysis("RadiaCode Zero", unknownScintillator),
    backgroundRecorded("12 авг", "10 ч"), differenceNote, snapshotSavedAt("12:30"),
    acknowledge, importFailed, importTooLarge, importUnreadable, importNotRecognised,
    importChooseXml, importedTitle, importedSummary("Spectrum", channels(1024), "12:34"),
    importedBackgroundRow, importedInHistory,
) + windowTexts() + formatTexts() + refusalTexts()

private fun SpectrumStrings.windowTexts(): List<String> = listOf(
    windowsTitle,
    boundsChip, columnWindow, columnCounts, columnRate, columnShare,
    indexNote, windowsEdgeNote, boundsTitle, boundsHint, defaults,
    ratioTitle, ratioFormula("100–300", "700–1500"), ratioWhat, ratioNotHardness,
    rangeDetails, rangeCounts("3 782 400"), rangeCovered("99,8–299,5"),
    boundsEditorTitle, boundsLower, boundsInner(1), boundsUpper,
    boundsExact, presetDefault, presetFullScale, presetCustom, presetFullScaleNote,
    resetBounds, done,
    moreActions, resetAccumulation, formatsTitle,
    windowsNeedOne, windowBoundsNotNumbers, windowBoundsOutOfRange(20, 3000),
    windowTooNarrow(20), windowsOverlap,
)

private fun SpectrumStrings.formatTexts(): List<String> = listOf(
    unitKeV, unitCounts, channels(1), channels(2), channels(1024),
    confidenceLow, confidenceMedium, natural, alsoResembles("Tl-208"),
    candidateNatural("K-40"), candidateConfidence("Cs-137", confidenceMedium),
    candidateLines("Cs-137", 1), candidateLines("Co-60", 2), candidateLines("Bi-214", 5),
    ambiguityNote("Pb-214 / I-131"), contradictedNote("Bi-214"),
    artifactAnnihilation, artifactAnnihilationNote,
    artifactEscape, artifactEscapeNote("2614,5", "511"),
    artifactSum, artifactSumNote("1173,2", "1332,5", "Co-60"),
    artifactBackscatter, artifactBackscatterNote,
    artifactXray, artifactXrayNote("Pb 84,9"), noExplanationNote,
    infoUnexplainedTitle, infoUnexplainedRule, infoUnexplainedLibrary,
    infoUnexplainedXray, infoUnexplainedCalibration, infoUnexplainedStatistics,
    artifactCompatibleNote("Tl-208"),
    calibrationLine("−5,6 + 2,41·ch", channels(1024)),
)

private fun SpectrumStrings.refusalTexts(): List<String> = listOf(
    intervalChannelMismatch(1024, 512), intervalSameDuration,
    intervalCalibrationMismatch("7,3"), intervalNegativeChannels(12),
    intervalOrderWarning, intervalWallClockWider(600, 300),
    ratesZeroDuration, ratesChannelMismatch(1024, 512), ratesResampled("7,3"),
    mergeNeedsTwo, mergeChannelMismatch(1024, 512),
    mergeCalibrationMismatch("A", "B", "7,3"),
    edgeExplanation, shapeNotEnoughData("120", "140"), shapeChiSquare(18, "4,1"),
)
