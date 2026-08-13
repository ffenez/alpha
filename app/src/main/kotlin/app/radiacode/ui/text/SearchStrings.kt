package app.radiacode.ui.text

/**
 * Строки области «Поиск источника» (экран `SearchScreen`, шторка «Почему?»,
 * записанный фон, отклик и спектральная подсказка).
 *
 * Область говорит ТОЛЬКО о скорости счёта, и её отказы — часть измерения, а не
 * вежливость. Перевод обязан отказываться ровно так же:
 *
 * - «Превышение над фоном не обнаружено» — результат теста НА РАЗЛИЧИЕ, а не
 *   утверждение о равенстве. Ни «at background level», ни «matches the
 *   background» здесь недопустимы: непринятие различия не доказывает совпадение.
 * - Незначимое отличие не называется «повышением» / «a rise» ни на одном языке,
 *   а процент печатается только вместе со знаменателем («к записанному фону»).
 * - Метод интервала назван словами (условный биномиальный + границы
 *   Клоппера–Пирсона) вместе с оговоркой, что нормальное приближение НЕ
 *   используется: на малых пуассоновских числах у него не тот охват.
 * - Спектральная форма помечена «не оценивается» — это не «изменений нет».
 *
 * Общие для приложения формулировки вердикта живут в [Strings] (ключи `search*`,
 * `countRate`, `backgroundTag`, `cancel`, `nowLabel`, `seconds`) и здесь НЕ
 * дублируются — экран берёт оба каталога сразу.
 */
interface SearchStrings {

    // --- экран ---
    val title: String
    val soundChip: String
    val vibroChip: String
    val feedbackOffNote: String

    /**
     * Приписка у кнопок отклика, когда канал выключен целиком: одна короткая
     * строка вместо постоянного объяснения — подробности живут под «i».
     */
    val feedbackOffShort: String
    val toneHint: String
    val vibroHint: String

    /** Чип «i» в шапке: техника уезжает под него, а не стоит на экране. */
    val infoChip: String

    /** Приписка «· сейчас ≈ 640 Гц» к подсказке канала. */
    fun currently(value: String): String

    /** Подпись процента у большого числа: знаменатель назван словом. */
    val toBackground: String
    val meterNeedsBackground: String
    val tapeTitle: String
    val bandNote: String
    val waitingStream: String
    val cpsUnit: String

    // --- режимы экрана ---
    /** Сегмент вверху: два ВОПРОСА, а не «точный» и «быстрый». */
    val modeNavigate: String
    val modeVerify: String
    val navHint: String
    val verifyHint: String

    // --- Наведение: четыре состояния и величина изменения ---
    val navTrendCollecting: String
    val navTrendNoChange: String
    val navTrendRising: String
    val navTrendFalling: String

    /** «×1,6 (95 % 1,2–2,1)» — величина изменения только с неопределённостью. */
    fun navRatio(value: String, interval: String?): String
    fun navRatioInterval(level: Int, low: String, high: String): String

    /** «×1,00 к локальному уровню» — отношение всегда со знаменателем. */
    fun navRatioToLocal(ratio: String): String

    /** Оба окна названы: слово «растёт» без окон не значит ничего. */
    fun navWindows(fast: String, local: String): String

    // --- Наведение: отношение к точке отсчёта ---
    /**
     * Крупное число модуля наведения: процент — или прочерк с названной
     * причиной. Прочерк здесь не «нет данных», а ответ теста.
     */
    val navDeltaDash: String

    /**
     * Направление модуля наведения — относительно ТОЧКИ ОТСЧЁТА.
     *
     * Знаменатель здесь другой, чем у состояния главной карточки (локальный
     * уровень), поэтому это не второе название того же вывода.
     */
    val navRefNone: String
    val navRefCollecting: String
    val navRefUnresolved: String
    val navRefAbove: String
    val navRefBelow: String
    val navDeltaCaptionNoReference: String
    val navDeltaCaptionCollecting: String
    fun navDeltaCaptionUnresolved(low: String, high: String): String
    fun navDeltaCaptionResolved(ratio: String): String

    fun navPeakValue(rate: String, agoSeconds: Int): String

    // --- Наведение: дуга и лента ---
    /** Заголовок единого модуля наведения. */
    val navModuleTitle: String

    /** Затенение на дуге — интервал отношения; линия без имени просто линия. */
    fun navBandLegend(level: Int): String
    val navScaleTitle: String
    val navScaleReference: String
    val navScalePeak: String
    val navTraceTitle: String
    val navTraceStart: String
    val navTraceLegend: String
    fun navLocalLevel(rate: String): String

    // --- Наведение: действия ---
    val navMark: String

    /** Поставленная точка отсчёта: величина и момент, а не кнопка во весь экран. */
    fun navReferenceSet(rate: String, time: String): String
    val navMarkUpdate: String
    val navMore: String
    val navResetPeak: String
    fun navMeasureHere(seconds: Int): String
    fun navSpotProgress(collected: Int, target: Int): String
    val navSpotNote: String
    val navSpotTitle: String
    fun navSpotResult(rate: String, sigma: String): String
    fun navSpotExposure(seconds: Int): String
    val navSpotToVerify: String
    fun navSpotAbortStreamLost(collected: Int, target: Int): String
    fun navSpotAbortServiceRestarted(collected: Int, target: Int): String

    // --- Наведение: отклик ---
    val navToneHint: String
    val navVibroHint: String
    val navToneNoReference: String
    val navToneAtReference: String

    // --- «i»: техника экрана, а не измерение ---
    val infoTitle: String
    val infoQuestionTitle: String
    val infoWindowsTitle: String
    val infoWindowsNote: String
    val infoScaleTitle: String
    val infoTraceTitle: String

    /**
     * Заголовок раздела про ленту «Проверки». Отдельный от [infoTraceTitle]
     * намеренно: это разные картинки — здесь лента 60 с с полосой ожидаемых
     * колебаний фона, там спарклайн наведения.
     */
    val infoTapeTitle: String
    val infoFeedbackTitle: String

    /** Граница режима, названная явно: изменение счёта ≠ найденный источник. */
    val infoLimitTitle: String
    val infoLimit: String

    /** Доза здесь мелкая подробность, а не ответ экрана. */
    fun navDose(value: String): String

    // --- график ленты ---
    /** Левый край окна ленты: «−60 с» / «−60 s». */
    val tapeStartLabel: String

    /** Пунктир фона подписан величиной: линия без имени — просто линия. */
    fun baselineLabel(value: String): String

    /** Янтарная метка подтверждённого превышения: «устойчиво ×1,8 к фону». */
    fun excursionLabel(ratio: String): String

    val statMean60: String
    val statMax: String
    val statDecisionWindow: String
    val statBackgroundTaken: String

    // --- спектральная подсказка ---
    val openSpectrum: String
    val shapeInvitation: String
    fun shapeNotEnough(detail: String): String
    fun shapeConsistent(detail: String): String
    fun shapeChanged(detail: String): String

    // --- замер и карточка записанного фона ---
    fun backgroundRunTitle(collected: Int, target: Int): String
    val backgroundRunNote: String
    fun backgroundRecorded(rate: String, time: String): String
    fun backgroundDetail(samples: Int, seconds: Int, quality: String, profile: String?): String
    val noBackgroundTitle: String
    fun noBackgroundNote(seconds: Int): String
    val hide: String
    fun measureBackground(seconds: Int): String
    fun remeasureBackground(seconds: Int): String

    // --- пригодность записанного фона ---
    val statusUsable: String
    val statusAged: String
    val statusProfileChanged: String
    val statusDeviceChanged: String
    val statusLowQuality: String

    val proposalAged: String
    fun proposalProfileChanged(profile: String?): String
    val proposalDeviceChanged: String
    val proposalShort: String
    val proposalGappy: String
    val proposalRestless: String
    val proposalUnusable: String

    /** Качество САМОГО ЗАМЕРА — свойство записи, а не излучения. */
    val qualityGood: String
    val qualityShort: String
    val qualityGappy: String
    val qualityRestless: String

    fun abortStreamLost(collected: Int, target: Int): String
    fun abortServiceRestarted(collected: Int, target: Int): String

    // --- почему молчит отклик ---
    val reasonOff: String
    val reasonNoDevice: String
    val reasonNoData: String
    val reasonDnd: String
    val reasonNoAudio: String
    val reasonVolumeZero: String
    fun reasonNoBackground(channel: String): String
    fun reasonInsideBackground(channel: String): String
    val channelTone: String
    val channelVibro: String
    val channelFeedback: String

    /** «≈ 640 Гц» — высота поискового тона. */
    fun pitch(hz: Int): String

    /** «пульс каждые 0,4 с» — каденция вибро. */
    fun cadence(seconds: String): String

    // --- «тон по энергии» ---
    val energyToneHint: String
    val energyToneScale: String

    // --- шторка «Почему такой вывод» ---
    val whyTitle: String

    /** Легенда уровней достоверности: у Поиска модель СЧЁТА, а не профиля. */
    val evidenceLegend: String
    val understood: String

    /** Короткий всплеск — маркер, а не находка. */
    fun spikes(count: Int, peak: String): String

    val whyCountRateNow: String
    val whyBackground: String
    val whyComparison: String
    val whyDecisionWindow: String
    val whyBackgroundWindow: String
    val whyDifference: String
    val whyRatio: String
    val whyCriterion: String
    val whySignificance: String
    val whyScatter: String
    val whyHold: String
    val whyStream: String
    val whyShape: String

    val valueNoData: String
    val valueNotRecorded: String
    val valueNotPerformed: String
    val valueScatterNotEvaluated: String
    val valueNoHold: String
    val valueStreamRunning: String
    val valueStreamBroken: String

    /** Форма спектра: «не оценивается» ≠ «изменений нет». */
    val valueShapeNotEvaluated: String

    fun backgroundWindowNote(
        sigma: String,
        samples: Int,
        exposure: String,
        quality: String,
    ): String

    /**
     * Экспозиция окна с десятыми: «2,8 с». Общий [Strings.seconds] печатает
     * целые секунды, а здесь дробная часть — это укоротивший окно пропуск
     * потока, и округлять её нельзя.
     */
    fun secondsValue(value: String): String

    val noBackgroundToCompare: String
    val noReadingsInWindow: String
    fun countsInWindow(counts: String, samples: Int): String
    fun counts(counts: String): String
    fun gapNote(seconds: String): String

    /** «±0,05 с⁻¹ (1σ) · +12 % к записанному фону» — процент со знаменателем. */
    fun differenceNote(sigma: String, percent: String?): String

    /** Метод интервала назван словами; нормальное приближение не используется. */
    fun ratioNote(phrase: String): String

    fun criterionNote(test: String, model: String): String
    fun significanceNote(alpha: String, z: String?): String
    fun dispersionNote(dispersion: String, phi: String?): String
    fun holdNote(confirm: String, release: String): String
    val streamNote: String
    val shapeNote: String

    // Короткие имена критерия — колонка «значение» в шторке.
    val testConditionalBinomial: String
    val testQuasiBinomial: String
    val testNone: String

    // Полные подписи критерия, модели и дисперсии: сами перечисления живут в
    // `analysis/` (общий код движка), поэтому их подписи переводятся здесь.
    val testLabelConditionalBinomial: String
    val testLabelQuasiBinomial: String
    val testLabelNone: String
    val modelPoisson: String
    val modelEmpiricalVariance: String
    val dispersionUnknown: String
    val dispersionPoissonLike: String
    val dispersionOverdispersed: String
    val dispersionUnderdispersed: String
}

object SearchRu : SearchStrings {

    override val title = "Поиск источника"
    override val soundChip = "звук"
    override val vibroChip = "вибро"
    override val feedbackOffNote = "сигнал только на экране · канал выбирается в Настройках"
    override val feedbackOffShort = "отклик выключен"
    override val toneHint = "тон: выше — дальше от записанного фона"
    override val vibroHint = "чаще пульс — дальше от записанного фона"

    override val infoChip = "i"

    override fun currently(value: String) = " · сейчас $value"

    override val toBackground = "к фону"
    override val meterNeedsBackground = "индикатор заработает после замера фона"
    override val tapeTitle = "с⁻¹ · последние 60 секунд"
    override val bandNote = "полоса — ожидаемые колебания фона"
    override val waitingStream = "ждём поток данных…"
    override val cpsUnit = "с⁻¹"

    override val modeNavigate = "Наведение"
    override val modeVerify = "Проверка"
    override val navHint = "куда вести прибор прямо сейчас"
    override val verifyHint = "есть ли устойчивое превышение над записанным фоном"

    override val navTrendCollecting = "… набираю статистику"
    override val navTrendNoChange = "→ без явного изменения"
    override val navTrendRising = "↑ растёт"
    override val navTrendFalling = "↓ падает"

    override fun navRatio(value: String, interval: String?) =
        "×$value" + (interval?.let { " ($it)" } ?: "")

    override fun navRatioInterval(level: Int, low: String, high: String) =
        "$level % интервал $low–$high"

    override fun navRatioToLocal(ratio: String) = "×$ratio к локальному уровню"

    override fun navWindows(fast: String, local: String) =
        "по последним $fast с против $local с до них"

    override val navDeltaDash = "—"

    override val navRefNone = "точка отсчёта не поставлена"
    override val navRefCollecting = "… набираю статистику"
    override val navRefUnresolved = "→ различие не разрешено"
    override val navRefAbove = "↑ выше точки отсчёта"
    override val navRefBelow = "↓ ниже точки отсчёта"

    override val navDeltaCaptionNoReference =
        "поставьте отсчёт — модуль ведёт прибор по отношению к нему"
    override val navDeltaCaptionCollecting =
        "окно ещё пересекается с самой точкой отсчёта"

    override fun navDeltaCaptionUnresolved(low: String, high: String) =
        "интервал $low–$high включает 1"

    override fun navDeltaCaptionResolved(ratio: String) = "к точке отсчёта · ×$ratio"

    override fun navPeakValue(rate: String, agoSeconds: Int) =
        "максимум $rate · $agoSeconds с назад"

    override val navModuleTitle = "Наведение"

    override fun navBandLegend(level: Int) = "затенение — $level % интервал отношения"

    override val navScaleTitle = "во сколько раз счёт отличается от точки отсчёта"
    override val navScaleReference = "отсчёт"
    override val navScalePeak = "макс"
    override val navTraceTitle = "с⁻¹ · последние 20 секунд"
    override val navTraceStart = "−20 с"
    override val navTraceLegend =
        "линия — короткое окно · ровная — локальный уровень за секунды до него"

    override fun navLocalLevel(rate: String) = "локальный уровень $rate"

    override val navMark = "Установить отсчёт"

    override fun navReferenceSet(rate: String, time: String) = "Отсчёт $rate с⁻¹ · $time"

    override val navMarkUpdate = "Обновить"
    override val navMore = "⋯"
    override val navResetPeak = "Сбросить максимум"

    override fun navMeasureHere(seconds: Int) = "Замерить здесь · $seconds с"

    override fun navSpotProgress(collected: Int, target: Int) =
        "замер в точке · $collected/$target с"

    override val navSpotNote =
        "Держите прибор неподвижно. Замер продолжается на других вкладках " +
            "и при погасшем экране — результат будет здесь."

    override val navSpotTitle = "Замер в точке"

    override fun navSpotResult(rate: String, sigma: String) = "$rate ±$sigma с⁻¹ (1σ)"

    override fun navSpotExposure(seconds: Int) = "измерялось $seconds с"

    override val navSpotToVerify = "Перейти к проверке"

    override fun navSpotAbortStreamLost(collected: Int, target: Int) =
        "замер в точке прерван: поток данных пропал на $collected из $target с — " +
            "среднее по неполному интервалу не сохранено"

    override fun navSpotAbortServiceRestarted(collected: Int, target: Int) =
        "замер в точке прерван: измерение перезапустилось на $collected из $target с — " +
            "в интервале дыра, среднее не сохранено"

    override val navToneHint = "тон: выше — дальше от точки отсчёта"
    override val navVibroHint = "короткий отклик на смену направления и новый максимум"
    override val navToneNoReference = "тон молчит: точка отсчёта не поставлена"
    override val navToneAtReference = "тон молчит: счёт у точки отсчёта"

    override val infoTitle = "Как устроен этот экран"
    override val infoQuestionTitle = "Вопрос режима"
    override val infoWindowsTitle = "Время подтверждения"
    // Термин алгоритма назван ОДИН раз и там, где объясняется механизм: на
    // экране он был подписью к числу, которую нечем прочитать (§3).
    override val infoWindowsNote =
        "в алгоритме это «окно решения»: длина окна подбирается по целевой относительной " +
            "ошибке счёта — чем ярче поле, тем короче окно при той же точности"
    override val infoScaleTitle = "Дуга"
    override val infoTraceTitle = "Лента"
    override val infoTapeTitle = "Лента последних секунд"
    override val infoFeedbackTitle = "Отклик"

    override val infoLimitTitle = "Что режим не утверждает"
    override val infoLimit =
        "Экран подтверждает изменение скорости счёта, а не расположение источника: " +
            "прибор считает события, а не сторону, с которой они пришли. " +
            "Спектральный состав здесь не оценивается."

    override fun navDose(value: String) = "мощность дозы $value"

    override val tapeStartLabel = "−60 с"

    override fun baselineLabel(value: String) = "фон $value"

    override fun excursionLabel(ratio: String) = "устойчиво $ratio к фону"

    override val statMean60 = "ср 60 с"
    override val statMax = "макс"
    override val statDecisionWindow = "время подтверждения"
    override val statBackgroundTaken = "фон записан"

    override val openSpectrum = "Открыть спектр"
    override val shapeInvitation = "Изменился не только счёт, но и форма спектра"

    override fun shapeNotEnough(detail: String) =
        "форма спектра: данных пока мало — $detail"

    override fun shapeConsistent(detail: String) =
        "форма спектра не изменилась в пределах статистики счёта ($detail)"

    override fun shapeChanged(detail: String) =
        "разные спектры по составу, а не только по яркости " +
            "($detail). Какой это нуклид — этот экран " +
            "не решает: посмотрите пики на вкладке «Спектр»"

    override fun backgroundRunTitle(collected: Int, target: Int) =
        "замер фона · $collected/$target с"

    override val backgroundRunNote =
        "Отойдите от предполагаемого источника и держите прибор " +
            "неподвижно. Замер продолжается на других вкладках и при " +
            "погасшем экране — результат будет здесь."

    override fun backgroundRecorded(rate: String, time: String) =
        "Фон $rate с⁻¹ · записан $time"

    override fun backgroundDetail(samples: Int, seconds: Int, quality: String, profile: String?) =
        "$samples показаний · фон измерялся $seconds с · качество: $quality" +
            (profile?.let { " · профиль «$it»" } ?: "")

    override val noBackgroundTitle = "Локальный фон не записан"

    override fun noBackgroundNote(seconds: Int) =
        "Отойдите от предполагаемого источника и держите прибор " +
            "неподвижно $seconds секунд — среднее станет точкой сравнения."

    override val hide = "скрыть"

    override fun measureBackground(seconds: Int) = "Замерить фон · $seconds с"

    override fun remeasureBackground(seconds: Int) = "Перезамерить фон · $seconds с"

    override val statusUsable = "пригоден"
    override val statusAged = "устарел"
    override val statusProfileChanged = "другой профиль"
    override val statusDeviceChanged = "другой прибор"
    override val statusLowQuality = "качество замера"

    override val proposalAged =
        "Фон записан больше получаса назад. Сравнение верно, только если " +
            "условия измерения не изменились."

    override fun proposalProfileChanged(profile: String?) =
        "Фон записан в другом профиле" +
            (profile?.let { " («$it»)" } ?: "") +
            " — сравнивать текущий счёт с ним нельзя."

    override val proposalDeviceChanged =
        "Фон записан другим прибором — у другого детектора своя скорость счёта."

    override val proposalShort = "Замер фона не был закончен — точка сравнения неполная."

    override val proposalGappy =
        "В интервале замера фона были пропуски потока — интервал с дырой."

    override val proposalRestless =
        "Во время замера фона показания разбрасывало сильнее счётной " +
            "статистики — похоже, прибор двигался. Замерьте стоя неподвижно."

    override val proposalUnusable = "Записанный фон непригоден как точка сравнения."

    override val qualityGood = "хорошее"
    override val qualityShort = "неполное"
    override val qualityGappy = "с пропусками потока"
    override val qualityRestless = "прибор не был неподвижен"

    override fun abortStreamLost(collected: Int, target: Int) =
        "замер фона прерван: поток данных пропал на $collected " +
            "из $target с — среднее по неполному интервалу не сохранено"

    override fun abortServiceRestarted(collected: Int, target: Int) =
        "замер фона прерван: измерение перезапустилось на $collected " +
            "из $target с — в интервале дыра, среднее не сохранено"

    override val reasonOff = "отклик выключен — сигнал виден только на экране"
    override val reasonNoDevice = "прибор не подключён — отклик появится после подключения"
    override val reasonNoData = "нет данных с прибора — отклик молчит, пока поток не восстановится"
    override val reasonDnd = "режим «не беспокоить» — звук и вибрация молчат, пока он включён"
    override val reasonNoAudio = "звук не запустился — система не дала звуковой канал"
    override val reasonVolumeZero = "громкость мультимедиа на нуле — прибавьте громкость кнопкой"

    override fun reasonNoBackground(channel: String) =
        "фон не записан — $channel включится после записи фона"

    override fun reasonInsideBackground(channel: String) =
        "счёт в пределах записанного фона — $channel появится, " +
            "когда он станет выше"

    override val channelTone = "тон"
    override val channelVibro = "вибрация"
    override val channelFeedback = "отклик"

    override fun pitch(hz: Int) = "≈ $hz Гц"

    override fun cadence(seconds: String) = "пульс каждые $seconds с"

    override val energyToneHint = "клик выше при жёстких гамма — 3 ступени по среднему кэВ"

    override val energyToneScale =
        "тон: <300 кэВ — ниже · 300–1000 — обычный · >1000 — выше; " +
            "по среднему кэВ спектра за 5 с, без потока спектра — обычные клики"

    override val whyTitle = "Почему такой вывод"

    override val evidenceLegend =
        "Источник значения: изм. — измерено прибором · расчёт — арифметика из измерений · " +
            "стат. — вывод статистической модели"

    override val understood = "Понятно"

    override fun spikes(count: Int, peak: String) =
        "короткие всплески: $count · сильнейший $peak к фону — " +
            "не подтверждены длительностью, отмечены как события"

    override val whyCountRateNow = "Скорость счёта сейчас"
    override val whyBackground = "Записанный фон"
    override val whyComparison = "Сравнение"
    override val whyDecisionWindow = "Время подтверждения"
    override val whyBackgroundWindow = "Окно фона"
    override val whyDifference = "Разность"
    override val whyRatio = "Отношение скоростей"
    override val whyCriterion = "Критерий"
    override val whySignificance = "Значимость"
    override val whyScatter = "Разброс показаний"
    override val whyHold = "Длительность отклонения"
    override val whyStream = "Поток данных"
    override val whyShape = "Спектральная форма"

    override val valueNoData = "нет данных"
    override val valueNotRecorded = "не записан"
    override val valueNotPerformed = "не выполнялось"
    override val valueScatterNotEvaluated = "не оценивался"
    override val valueNoHold = "нет"
    override val valueStreamRunning = "идёт"
    override val valueStreamBroken = "прерван"
    override val valueShapeNotEvaluated = "не оценивается"

    override fun backgroundWindowNote(
        sigma: String,
        samples: Int,
        exposure: String,
        quality: String,
    ) = "±$sigma с⁻¹ · $samples показаний · экспозиция $exposure с · качество: $quality"

    override fun secondsValue(value: String) = "$value с"

    override val noBackgroundToCompare = "нет записанного фона — сравнивать не с чем"
    override val noReadingsInWindow = "за время подтверждения нет показаний: поток данных прерван"

    override fun countsInWindow(counts: String, samples: Int) =
        "$counts импульсов в окне ($samples показаний)"

    override fun counts(counts: String) = "$counts импульсов"

    override fun gapNote(seconds: String) = " · пропуск потока $seconds с"

    override fun differenceNote(sigma: String, percent: String?) =
        "±$sigma с⁻¹ (1σ) · " + (percent?.let { "$it к записанному фону" } ?: "")

    override fun ratioNote(phrase: String) =
        "$phrase · интервал точный: условное биномиальное " +
            "распределение числа импульсов окна при фиксированной сумме, " +
            "границы по Клопперу–Пирсону, перенесённые на отношение " +
            "скоростей; нормальное приближение не используется"

    override fun criterionNote(test: String, model: String) =
        "$test · модель неопределённости: $model"

    override fun significanceNote(alpha: String, z: String?) =
        "порог отличия α = $alpha" + (z?.let { " · z = $it" } ?: "") +
            " · p — вероятность увидеть такое различие, если скорости равны"

    override fun dispersionNote(dispersion: String, phi: String?) =
        dispersion + (phi?.let { " · счёт поделён на φ = $it" } ?: "")

    override fun holdNote(confirm: String, release: String) =
        "подтверждение требует $confirm подряд, снятие — $release согласия"

    override val streamNote =
        "окна строятся по времени прибора; пропуски укорачивают " +
            "экспозицию, а не растягивают последнее показание"

    override val shapeNote =
        "этот экран сравнивает только скорость счёта; изотоп по одному " +
            "росту счёта не определяется — это вкладка «Спектр»"

    override val testConditionalBinomial = "условный биномиальный"
    override val testQuasiBinomial = "квазибиномиальный"
    override val testNone = "нет"

    override val testLabelConditionalBinomial =
        "условный биномиальный тест (Przyborowski–Wilenski)"
    override val testLabelQuasiBinomial =
        "условный биномиальный тест с поправкой на сверхдисперсию"
    override val testLabelNone = "сравнение невозможно"
    override val modelPoisson = "пуассоновская статистика счёта"
    override val modelEmpiricalVariance = "эмпирическая дисперсия показаний"
    override val dispersionUnknown = "дисперсия не оценивалась"
    override val dispersionPoissonLike = "совместимо со счётной статистикой"
    override val dispersionOverdispersed = "разброс шире счётной статистики"
    override val dispersionUnderdispersed = "разброс уже счётной статистики (сглаживание прибора)"
}

object SearchEn : SearchStrings {

    override val title = "Source search"
    override val soundChip = "sound"
    override val vibroChip = "vibration"
    override val feedbackOffNote = "signal on screen only · the channel is chosen in Settings"
    override val feedbackOffShort = "feedback off"
    override val toneHint = "tone: higher means further from the recorded background"
    override val vibroHint = "faster pulses mean further from the recorded background"

    override val infoChip = "i"

    override fun currently(value: String) = " · now $value"

    override val toBackground = "vs background"
    override val meterNeedsBackground = "the meter starts working once the background is measured"
    override val tapeTitle = "s⁻¹ · last 60 seconds"
    override val bandNote = "the band — expected fluctuation of the background"
    override val waitingStream = "waiting for the data stream…"
    override val cpsUnit = "s⁻¹"

    override val modeNavigate = "Navigate"
    override val modeVerify = "Verify"
    override val navHint = "where to move the instrument right now"
    override val verifyHint = "whether the excess over the recorded background holds"

    override val navTrendCollecting = "… collecting counts"
    override val navTrendNoChange = "→ no resolved change"
    override val navTrendRising = "↑ rising"
    override val navTrendFalling = "↓ falling"

    override fun navRatio(value: String, interval: String?) =
        "×$value" + (interval?.let { " ($it)" } ?: "")

    override fun navRatioInterval(level: Int, low: String, high: String) =
        "$level % interval $low–$high"

    override fun navRatioToLocal(ratio: String) = "×$ratio to the local level"

    override fun navWindows(fast: String, local: String) =
        "last $fast s against the $local s before them"

    override val navDeltaDash = "—"

    override val navRefNone = "no reference point set"
    override val navRefCollecting = "… collecting counts"
    override val navRefUnresolved = "→ no difference resolved"
    override val navRefAbove = "↑ above the reference point"
    override val navRefBelow = "↓ below the reference point"

    override val navDeltaCaptionNoReference =
        "set a reference — the module guides relative to it"
    override val navDeltaCaptionCollecting =
        "the window still overlaps the reference point itself"

    override fun navDeltaCaptionUnresolved(low: String, high: String) =
        "the interval $low–$high contains 1"

    override fun navDeltaCaptionResolved(ratio: String) = "to the reference point · ×$ratio"

    override fun navPeakValue(rate: String, agoSeconds: Int) =
        "maximum $rate · $agoSeconds s ago"

    override val navModuleTitle = "Navigation"

    override fun navBandLegend(level: Int) = "the shading — the $level % interval of the ratio"

    override val navScaleTitle = "how many times the count rate differs from the reference point"
    override val navScaleReference = "ref"
    override val navScalePeak = "max"
    override val navTraceTitle = "s⁻¹ · last 20 seconds"
    override val navTraceStart = "−20 s"
    override val navTraceLegend =
        "line — the short window · flat — the local level of the seconds before it"

    override fun navLocalLevel(rate: String) = "local level $rate"

    override val navMark = "Set reference"

    override fun navReferenceSet(rate: String, time: String) = "Reference $rate s⁻¹ · $time"

    override val navMarkUpdate = "Update"
    override val navMore = "⋯"
    override val navResetPeak = "Reset the maximum"

    override fun navMeasureHere(seconds: Int) = "Measure here · $seconds s"

    override fun navSpotProgress(collected: Int, target: Int) =
        "measuring the spot · $collected/$target s"

    override val navSpotNote =
        "Hold the instrument still. The measurement continues on other tabs and " +
            "with the display off — the result will be here."

    override val navSpotTitle = "Spot measurement"

    override fun navSpotResult(rate: String, sigma: String) = "$rate ±$sigma s⁻¹ (1σ)"

    override fun navSpotExposure(seconds: Int) = "measured for $seconds s"

    override val navSpotToVerify = "Go to the check"

    override fun navSpotAbortStreamLost(collected: Int, target: Int) =
        "spot measurement interrupted: the data stream was lost at $collected of $target s — " +
            "an average over an incomplete interval was not stored"

    override fun navSpotAbortServiceRestarted(collected: Int, target: Int) =
        "spot measurement interrupted: measurement restarted at $collected of $target s — " +
            "the interval has a hole in it, the average was not stored"

    override val navToneHint = "tone: higher — further from the reference point"
    override val navVibroHint = "a short response to a change of direction and to a new maximum"
    override val navToneNoReference = "the tone is silent: no reference point set"
    override val navToneAtReference = "the tone is silent: the count rate is at the reference point"

    override val infoTitle = "How this screen works"
    override val infoQuestionTitle = "The question of the mode"
    override val infoWindowsTitle = "Confirmation time"
    override val infoWindowsNote =
        "the algorithm calls it the «decision window»: its length follows a target relative " +
            "counting error — the brighter the field, the shorter the window for the same " +
            "precision"
    override val infoScaleTitle = "The dial"
    override val infoTraceTitle = "The trace"
    override val infoTapeTitle = "The tape of the last seconds"
    override val infoFeedbackTitle = "Feedback"

    override val infoLimitTitle = "What the mode does not claim"
    override val infoLimit =
        "The screen confirms a change of count rate, not the location of a source: " +
            "the instrument counts events, not the side they arrived from. " +
            "Spectral composition is not evaluated here."

    override fun navDose(value: String) = "dose rate $value"

    override val tapeStartLabel = "−60 s"

    override fun baselineLabel(value: String) = "background $value"

    override fun excursionLabel(ratio: String) = "sustained $ratio of the background"

    override val statMean60 = "mean 60 s"
    override val statMax = "max"
    override val statDecisionWindow = "confirmation time"
    override val statBackgroundTaken = "background taken"

    override val openSpectrum = "Open the spectrum"
    override val shapeInvitation = "Not only the count changed, but the shape of the spectrum too"

    override fun shapeNotEnough(detail: String) =
        "spectrum shape: too little data so far — $detail"

    override fun shapeConsistent(detail: String) =
        "the shape of the spectrum did not change within the counting statistics ($detail)"

    override fun shapeChanged(detail: String) =
        "the spectra differ in composition, not only in brightness " +
            "($detail). Which nuclide this is, the screen does not decide: " +
            "look at the peaks on the «Spectrum» tab"

    override fun backgroundRunTitle(collected: Int, target: Int) =
        "background measurement · $collected/$target s"

    override val backgroundRunNote =
        "Step away from the suspected source and hold the instrument still. " +
            "The measurement continues on other tabs and with the screen off — " +
            "the result will be here."

    override fun backgroundRecorded(rate: String, time: String) =
        "Background $rate s⁻¹ · taken at $time"

    override fun backgroundDetail(samples: Int, seconds: Int, quality: String, profile: String?) =
        "$samples readings · background measured for $seconds s · recording: $quality" +
            (profile?.let { " · profile «$it»" } ?: "")

    override val noBackgroundTitle = "No local background recorded"

    override fun noBackgroundNote(seconds: Int) =
        "Step away from the suspected source and hold the instrument still for " +
            "$seconds seconds — the mean becomes the reference point."

    override val hide = "hide"

    override fun measureBackground(seconds: Int) = "Measure the background · $seconds s"

    override fun remeasureBackground(seconds: Int) = "Re-measure the background · $seconds s"

    override val statusUsable = "usable"
    override val statusAged = "stale"
    override val statusProfileChanged = "another profile"
    override val statusDeviceChanged = "another instrument"
    override val statusLowQuality = "recording quality"

    override val proposalAged =
        "The background was recorded more than half an hour ago. The comparison holds " +
            "only if the measurement conditions have not changed."

    override fun proposalProfileChanged(profile: String?) =
        "The background was recorded in another profile" +
            (profile?.let { " («$it»)" } ?: "") +
            " — the current count cannot be compared with it."

    override val proposalDeviceChanged =
        "The background was recorded with another instrument — another detector has " +
            "its own count rate."

    override val proposalShort =
        "The background measurement was not finished — the reference point is incomplete."

    override val proposalGappy =
        "The background interval lost stream time — an interval with a hole in it."

    override val proposalRestless =
        "During the background measurement the readings scattered wider than counting " +
            "statistics — the instrument was probably moving. Measure it standing still."

    override val proposalUnusable = "The recorded background cannot serve as a reference point."

    override val qualityGood = "good"
    override val qualityShort = "incomplete"
    override val qualityGappy = "with stream gaps"
    override val qualityRestless = "the instrument was not held still"

    override fun abortStreamLost(collected: Int, target: Int) =
        "background measurement interrupted: the data stream was lost at $collected " +
            "of $target s — a mean over an incomplete interval was not saved"

    override fun abortServiceRestarted(collected: Int, target: Int) =
        "background measurement interrupted: the measurement restarted at $collected " +
            "of $target s — the interval has a hole, the mean was not saved"

    override val reasonOff = "feedback is off — the signal is shown on screen only"
    override val reasonNoDevice =
        "the instrument is not connected — feedback appears once it connects"
    override val reasonNoData =
        "no data from the instrument — feedback stays silent until the stream returns"
    override val reasonDnd =
        "«do not disturb» mode — sound and vibration stay silent while it is on"
    override val reasonNoAudio = "sound did not start — the system gave no audio channel"
    override val reasonVolumeZero =
        "the media volume is at zero — turn it up with the volume key"

    override fun reasonNoBackground(channel: String) =
        "no background recorded — the $channel starts once a background is recorded"

    override fun reasonInsideBackground(channel: String) =
        "the count is within the recorded background — the $channel appears " +
            "once it goes above it"

    override val channelTone = "tone"
    override val channelVibro = "vibration"
    override val channelFeedback = "feedback"

    override fun pitch(hz: Int) = "≈ $hz Hz"

    override fun cadence(seconds: String) = "a pulse every $seconds s"

    override val energyToneHint =
        "a higher click for harder gammas — 3 steps by the mean keV"

    override val energyToneScale =
        "pitch: <300 keV — lower · 300–1000 — as usual · >1000 — higher; " +
            "by the mean keV of the spectrum over 5 s, without a spectrum stream — plain clicks"

    override val whyTitle = "Why this conclusion"

    override val evidenceLegend =
        "Source of the value: meas. — measured by the instrument · calc. — arithmetic on " +
            "measurements · stat. — the output of a statistical model"

    override val understood = "Got it"

    override fun spikes(count: Int, peak: String) =
        "short excursions: $count · strongest $peak of the background — " +
            "not confirmed by duration, recorded as events"

    override val whyCountRateNow = "Count rate now"
    override val whyBackground = "Recorded background"
    override val whyComparison = "Comparison"
    override val whyDecisionWindow = "Confirmation time"
    override val whyBackgroundWindow = "Background window"
    override val whyDifference = "Difference"
    override val whyRatio = "Rate ratio"
    override val whyCriterion = "Criterion"
    override val whySignificance = "Significance"
    override val whyScatter = "Scatter of the readings"
    override val whyHold = "Length of the difference"
    override val whyStream = "Data stream"
    override val whyShape = "Spectrum shape"

    override val valueNoData = "no data"
    override val valueNotRecorded = "not recorded"
    override val valueNotPerformed = "not performed"
    override val valueScatterNotEvaluated = "not evaluated"
    override val valueNoHold = "none"
    override val valueStreamRunning = "running"
    override val valueStreamBroken = "interrupted"
    override val valueShapeNotEvaluated = "not evaluated"

    override fun backgroundWindowNote(
        sigma: String,
        samples: Int,
        exposure: String,
        quality: String,
    ) = "±$sigma s⁻¹ · $samples readings · exposure $exposure s · recording: $quality"

    override fun secondsValue(value: String) = "$value s"

    override val noBackgroundToCompare = "no background recorded — nothing to compare with"
    override val noReadingsInWindow =
        "no readings within the confirmation time: the data stream is interrupted"

    override fun countsInWindow(counts: String, samples: Int) =
        "$counts counts in the window ($samples readings)"

    override fun counts(counts: String) = "$counts counts"

    override fun gapNote(seconds: String) = " · stream gap $seconds s"

    override fun differenceNote(sigma: String, percent: String?) =
        "±$sigma s⁻¹ (1σ) · " + (percent?.let { "$it of the recorded background" } ?: "")

    override fun ratioNote(phrase: String) =
        "$phrase · the interval is exact: the conditional binomial distribution of the " +
            "window counts at a fixed total, Clopper–Pearson bounds carried over to the " +
            "ratio of rates; the normal approximation is not used"

    override fun criterionNote(test: String, model: String) =
        "$test · uncertainty model: $model"

    override fun significanceNote(alpha: String, z: String?) =
        "difference threshold α = $alpha" + (z?.let { " · z = $it" } ?: "") +
            " · p — the probability of seeing a difference like this if the rates are equal"

    override fun dispersionNote(dispersion: String, phi: String?) =
        dispersion + (phi?.let { " · counts divided by φ = $it" } ?: "")

    override fun holdNote(confirm: String, release: String) =
        "confirmation needs $confirm in a row, release — $release of agreement"

    override val streamNote =
        "windows are built on the instrument's clock; gaps shorten the exposure " +
            "instead of stretching the last reading"

    override val shapeNote =
        "this screen compares the count rate only; an isotope is not identified from a " +
            "rise in counts alone — that is the «Spectrum» tab"

    override val testConditionalBinomial = "conditional binomial"
    override val testQuasiBinomial = "quasi-binomial"
    override val testNone = "none"

    override val testLabelConditionalBinomial =
        "conditional binomial test (Przyborowski–Wilenski)"
    override val testLabelQuasiBinomial =
        "conditional binomial test corrected for overdispersion"
    override val testLabelNone = "comparison is impossible"
    override val modelPoisson = "Poisson counting statistics"
    override val modelEmpiricalVariance = "empirical variance of the readings"
    override val dispersionUnknown = "dispersion not evaluated"
    override val dispersionPoissonLike = "consistent with counting statistics"
    override val dispersionOverdispersed = "scatter wider than counting statistics"
    override val dispersionUnderdispersed =
        "scatter narrower than counting statistics (instrument smoothing)"
}

val SearchCatalogue = AreaCatalogue(ru = SearchRu, en = SearchEn)

/**
 * Все строки каталога — для проверок, действующих на каждый язык области.
 * Функции вызываются с представительными аргументами: в них тоже живёт текст.
 */
fun SearchStrings.allTexts(): List<String> = listOf(
    title, soundChip, vibroChip, feedbackOffNote, feedbackOffShort, infoChip,
    toneHint, vibroHint,
    currently(pitch(640)), toBackground, meterNeedsBackground, tapeTitle, bandNote,
    waitingStream, cpsUnit, tapeStartLabel, baselineLabel("25,5"), excursionLabel("×1,8"),
    modeNavigate, modeVerify, navHint, verifyHint,
    navTrendCollecting, navTrendNoChange, navTrendRising, navTrendFalling,
    navRatio("1,60", navRatioInterval(95, "1,20", "2,10")), navRatio("1,60", null),
    navRatioInterval(95, "1,20", "2,10"), navRatioToLocal("1,00"), navWindows("1,8", "16,0"),
    navDeltaDash, navRefNone, navRefCollecting, navRefUnresolved, navRefAbove, navRefBelow,
    navDeltaCaptionNoReference,
    navDeltaCaptionCollecting, navDeltaCaptionUnresolved("0,92", "1,31"),
    navDeltaCaptionResolved("1,31"), navPeakValue("47,6", 18), navModuleTitle, navBandLegend(95), navScaleTitle, navScaleReference, navScalePeak,
    navTraceTitle, navTraceStart, navTraceLegend, navLocalLevel("24,8"),
    navMark, navReferenceSet("26,0", "11:44"), navMarkUpdate,
    navMore, navResetPeak, navMeasureHere(10), navSpotProgress(6, 10), navSpotNote,
    navSpotTitle, navSpotResult("48,2", "2,2"), navSpotExposure(10), navSpotToVerify,
    navSpotAbortStreamLost(6, 10), navSpotAbortServiceRestarted(6, 10),
    navToneHint, navVibroHint, navToneNoReference, navToneAtReference,
    infoTitle, infoQuestionTitle, infoWindowsTitle, infoWindowsNote, infoScaleTitle,
    infoTraceTitle, infoTapeTitle, infoFeedbackTitle, infoLimitTitle, infoLimit,
    navDose("0,18 мкЗв/ч"),
    statMean60, statMax, statDecisionWindow, statBackgroundTaken,
    openSpectrum, shapeInvitation, shapeNotEnough("z = 1,2"), shapeConsistent("z = 1,2"),
    shapeChanged("z = 4,1"),
    backgroundRunTitle(12, 45), backgroundRunNote, backgroundRecorded("25,5", "14:03"),
    backgroundDetail(45, 45, qualityGood, "Дом"), backgroundDetail(45, 45, qualityGood, null),
    noBackgroundTitle, noBackgroundNote(45), hide, measureBackground(45), remeasureBackground(45),
    statusUsable, statusAged, statusProfileChanged, statusDeviceChanged, statusLowQuality,
    proposalAged, proposalProfileChanged("Дом"), proposalProfileChanged(null),
    proposalDeviceChanged, proposalShort, proposalGappy, proposalRestless, proposalUnusable,
    qualityGood, qualityShort, qualityGappy, qualityRestless,
    abortStreamLost(12, 45), abortServiceRestarted(12, 45),
    reasonOff, reasonNoDevice, reasonNoData, reasonDnd, reasonNoAudio, reasonVolumeZero,
    reasonNoBackground(channelTone), reasonInsideBackground(channelVibro),
    channelTone, channelVibro, channelFeedback, pitch(640), cadence("0,4"),
    energyToneHint, energyToneScale,
    whyTitle, evidenceLegend, understood, spikes(2, "×4,2"),
    whyCountRateNow, whyBackground, whyComparison, whyDecisionWindow, whyBackgroundWindow,
    whyDifference, whyRatio, whyCriterion, whySignificance, whyScatter, whyHold, whyStream,
    whyShape,
    valueNoData, valueNotRecorded, valueNotPerformed, valueScatterNotEvaluated, valueNoHold,
    valueStreamRunning, valueStreamBroken, valueShapeNotEvaluated,
    backgroundWindowNote("0,5", 45, "45,0", qualityGood),
    secondsValue("2,8"),
    noBackgroundToCompare, noReadingsInWindow, countsInWindow("180", 3), counts("180"),
    gapNote("2,0"), differenceNote("0,05", "+12 %"), differenceNote("0,05", null),
    ratioNote("×1,8"), criterionNote(testConditionalBinomial, modelPoisson),
    significanceNote("0,01", "3,2"), significanceNote("0,01", null),
    dispersionNote(dispersionPoissonLike, "1,4"), dispersionNote(dispersionUnknown, null),
    holdNote("4 с", "3 с"), streamNote, shapeNote,
    testConditionalBinomial, testQuasiBinomial, testNone,
    testLabelConditionalBinomial, testLabelQuasiBinomial, testLabelNone,
    modelPoisson, modelEmpiricalVariance,
    dispersionUnknown, dispersionPoissonLike, dispersionOverdispersed, dispersionUnderdispersed,
)
