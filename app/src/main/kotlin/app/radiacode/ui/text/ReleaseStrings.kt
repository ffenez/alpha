package app.radiacode.ui.text

/**
 * Каталог строк списка обновлений — того, что раскрывается по нажатию на версию
 * в «О приложении».
 *
 * Записи отвечают на один вопрос: «что изменилось У МЕНЯ НА ЭКРАНЕ». Поэтому
 * перевод переносит ПРАВИЛО, а не слова:
 *
 *  - внутренних имён нет ни в одном языке (их ловит тест) — человек читает
 *    про экран, а не про устройство кода;
 *  - обещаний безопасности нет ни в одном языке: «safe», «normal»,
 *    «dangerous» запрещены ровно так же, как «безопасно» и «норма»;
 *  - отказ утверждать переносится дословно: «отсутствие различия не доказывает
 *    совпадение» обязано остаться отказом и по-английски.
 *
 * Номера версий, их порядок и «какая версия стоит на телефоне» живут в
 * `ui/logic/ReleaseNotes` и от языка не зависят: это факт сборки, а не текст.
 */
interface ReleaseStrings {

    val v021Title: String
    val v021Lines: List<String>

    val v020Title: String
    val v020Lines: List<String>

    val v010Title: String
    val v010Lines: List<String>

    val v009Title: String
    val v009Lines: List<String>

    val v008Title: String
    val v008Lines: List<String>

    val v007Title: String
    val v007Lines: List<String>

    val v006Title: String
    val v006Lines: List<String>

    val v005Title: String
    val v005Lines: List<String>

    val v001Title: String
    val v001Lines: List<String>
}

object ReleaseRu : ReleaseStrings {

    override val v021Title = "Графики идут в ногу со временем"
    override val v021Lines = listOf(
        "Живые графики на Главной снова обновляются на глазах: правый край " +
            "привязан к текущему времени, а не к последней пришедшей точке.",
        "Если поток прервался, линия просто заканчивается там, где кончились " +
            "измерения, и продолжается сама, когда данные пошли снова.",
        "Пропали серые полосы на графике мощности дозы: приложение точнее " +
            "определяет часы прибора и больше не теряет свежие записи.",
        "Состояние потока называется одним словом на всех элементах экрана: " +
            "пока данные идут, о разрыве не говорится ничего.",
        "С миниатюр графиков убраны единицы — их называет заголовок карточки.",
    )

    override val v020Title = "Понятнее человеку, строже к данным"
    override val v020Lines = listOf(
        "Объяснения разложены по глубине: сначала что показания значат, потом " +
            "как приложение это решило, и только потом расчёты и формулы.",
        "На главном экране остался вывод, а числа, на которых он стоит, " +
            "переехали в его объяснение — нажимается сам вывод.",
        "Спектр открывается во весь экран с курсором: энергия, номер канала и " +
            "число событий в нём.",
        "Тап по линии в справке о нуклиде отмечает эту энергию на спектре — " +
            "отметка говорит, где линия ожидается, а не что пик найден.",
        "Поиск получил режим наведения: куда вести прибор прямо сейчас, с " +
            "точкой отсчёта, шкалой и удержанием максимума.",
        "Прибор можно проверить по природному фону: приложение измеряет " +
            "разрешение и погрешность энергетической шкалы без поверочных источников.",
        "История спектра во времени больше не теряется при перезапуске, а " +
            "частота её записи выбирается в настройках.",
        "Из истории открываются полноэкранный график за время сессии и " +
            "сохранённый спектр целиком.",
        "Размер текста и элементов настраивается отдельными ползунками.",
        "Импорт спектра из штатного приложения починен: разбор файла падал " +
            "на любом файле из-за настройки разборщика.",
        "Мощность дозы в истории показывалась нулями — исправлено.",
    )

    override val v010Title = "Точнее в терминах, чище на экране"
    override val v010Lines = listOf(
        "Приложение работает со всей серией RadiaCode: модель определяется по " +
            "серийному номеру, и разрешение детектора берётся её собственное.",
        "Отладка сохраняется одним архивом: состояние приложения и прибора, " +
            "накопленный спектр, записанный фон и ваше описание проблемы.",
        "Колонка «SNR» в таблице пиков стала «значимостью» и считается строго: " +
            "нетто-площадь делится на её собственную неопределённость.",
        "Одиночный всплеск шириной в канал больше не попадает в пики — у " +
            "фотопика есть ширина, заданная разрешением прибора.",
        "Поиск говорит «превышение над фоном не обнаружено» вместо «на уровне " +
            "фона»: отсутствие различия не доказывает совпадение.",
        "Проекция дозы показывает среднюю с той точностью, из которой она " +
            "посчитана, — число теперь сходится на калькуляторе.",
        "Пояснения переехали под кнопку «i», объяснения набраны обычным " +
            "шрифтом, а моноширинный остался числам.",
    )

    override val v009Title = "Главная и график читаются с одного взгляда"
    override val v009Lines = listOf(
        "Главный экран собран по порядку вопросов: крупная мощность дозы, под ней " +
            "состояние фона, затем плитки «Счёт», «Тренд/ч» и «Сегодня».",
        "Нажатие на сам вывод («в обычном диапазоне этого профиля») открывает " +
            "объяснение — отдельную кнопку «Почему?» больше искать не нужно.",
        "Под выводом всегда видно, пополняется ли статистика места прямо сейчас, " +
            "и если нет — по какой причине.",
        "«Отпечаток места» переехал в Поиск — туда, где задают тот же вопрос про " +
            "место, а в Поиске вывод открывает разбор нажатием и говорит, что " +
            "именно не меняется и за какое время.",
        "Прочерк вместо тренда теперь объясняет себя: «интервалов 3 из 12» вместо " +
            "молчаливого «—», а посчитанный тренд называет окно, за которое он взят.",
        "Ось значений подгоняется к тому, что прибор реально показывает: обычный " +
            "фон занимает график, а не узкую полоску над нулём.",
        "Порог тревоги больше не растягивает ось: пока он далеко, у кромки стоит " +
            "указатель «↑ L1», а кадр остаётся читаемым.",
        "Под графиком осталось две строки; распределение, полная статистика и " +
            "справка «i» открываются панелями поверх и не отнимают у него высоту.",
        "Треугольник над полем теперь честно называется «максимум интервала выше " +
            "P90 профиля» и показывает оба числа сразу.",
        "Пока график открыт, экран не гаснет.",
        "Спектрограмма: шаг колонки подбирается по статистике (пятисекундная " +
            "колонка на фоне — это шум, а не линии), яркость сравнима между " +
            "столбцами, пауза в записи видна пустой колонкой.",
        "Крайний канал спектра больше не рисуется как пик у 2,8 МэВ: это граница " +
            "шкалы, а не измерение. Его содержимое показано отдельной строкой.",
    )

    override val v008Title = "График на Главной — тот же, что на весь экран"
    override val v008Lines = listOf(
        "Карточка Главной показывает ровно ту картинку, которая откроется по " +
            "нажатию: те же интервалы, тот же разброс, то же окно времени.",
        "Окно графика запоминается и общее для обоих экранов.",
        "Пока данные идут, отметка возраста не занимает место на экране — она " +
            "появляется, когда поток отстаёт или прерван.",
        "Переходы между экранами и разделами стали плавными.",
    )

    override val v007Title = "Окна времени и фон графика"
    override val v007Lines = listOf(
        "Пятнадцать окон от минуты до 30 дней вместо шести; выбранное окно " +
            "запоминается, а лента окон свёрнута в один чип.",
        "Пропуски измерений заштрихованы: пустое место больше не читается как " +
            "низкий уровень.",
        "Область до начала истории затенена, на длинных окнах видны сутки и часы.",
        "Треугольники над полем отмечают самый высокий отсчёт интервала, и " +
            "приложение объясняет, что они означают.",
    )

    override val v006Title = "Полноэкранный график для трёх величин"
    override val v006Lines = listOf(
        "Мощность дозы, скорость счёта и жёсткость открываются одним и тем же " +
            "графиком с курсором, распределением и статистикой окна.",
        "Скорость счёта и жёсткость можно включить отдельными блоками Главной.",
        "У счёта и жёсткости доступны окна до 6 часов — под графиком написано, " +
            "почему длиннее пока нет.",
    )

    override val v005Title = "Радиационный отпечаток места"
    override val v005Lines = listOf(
        "Приложение запоминает для места распределение дозы, распределение счёта " +
            "и форму спектра, а потом сравнивает с ними текущие измерения.",
        "Вывод описательный, по строке на каждое измерение: что именно " +
            "отличается от истории этого места и насколько.",
        "Совпадение отпечатка не доказывает, что прибор в том же месте, а " +
            "расхождение не называет причину — это сказано на самом экране.",
    )

    override val v001Title = "Поиск, журнал и настройки"
    override val v001Lines = listOf(
        "Поиск сравнивает счёт с записанным фоном точным статистическим " +
            "критерием и показывает, из чего сделан вывод.",
        "Звук и вибро включаются двумя кнопками на самом экране, а выбор канала " +
            "живёт в настройках.",
        "В Истории можно выделить и удалить лишние сессии и спектры.",
        "Настройки разделены на категории; в «Отладке» отчёт о состоянии " +
            "приложения сохраняется в выбранную папку.",
    )
}

object ReleaseEn : ReleaseStrings {

    override val v021Title = "The charts keep up with the clock"
    override val v021Lines = listOf(
        "The live charts on the Monitor move again: the right edge is tied to " +
            "the current time, not to the last point that arrived.",
        "If the stream breaks, the line simply ends where the measurements " +
            "ended, and continues on its own once data comes back.",
        "The grey stripes on the dose-rate chart are gone: the app measures " +
            "the instrument's clock more carefully and no longer drops fresh records.",
        "The state of the stream is stated once for every element of the " +
            "screen: while data keeps coming, nothing is said about a break.",
        "The units are gone from the chart thumbnails — the card title names them.",
    )

    override val v020Title = "Clearer to a person, stricter with data"
    override val v020Lines = listOf(
        "Explanations are arranged by depth: first what the readings mean, then " +
            "how the app decided that, and only then the calculations and formulas.",
        "The main screen keeps the conclusion; the numbers it stands on moved " +
            "into its explanation — the conclusion itself is the button.",
        "The spectrum opens full screen with a cursor: energy, channel number " +
            "and the number of events in it.",
        "Tapping a line in the nuclide reference marks that energy on the " +
            "spectrum — the mark says where the line is expected, not that a peak was found.",
        "Search gained a navigation mode: where to take the instrument right " +
            "now, with a reference point, a scale and a held maximum.",
        "The instrument can be checked against the natural background: the app " +
            "measures its resolution and the uncertainty of the energy scale without reference sources.",
        "The spectrum-over-time history no longer disappears on restart, and " +
            "how often it is recorded is chosen in the settings.",
        "From the journal you can open the full-screen chart for a session and " +
            "a saved spectrum in full.",
        "Text and element sizes are adjusted by separate sliders.",
        "Importing a spectrum from the stock app is fixed: parsing failed on " +
            "every file because of a parser setting.",
        "Dose rate in the journal showed zeros — fixed.",
    )

    override val v010Title = "Sharper terms, cleaner screen"
    override val v010Lines = listOf(
        "The app works with the whole RadiaCode series: the model is recognised " +
            "by serial number, and the detector resolution is taken from that model.",
        "Diagnostics are saved as one archive: the state of the app and the device, " +
            "the accumulated spectrum, the recorded background and your description " +
            "of the problem.",
        "The «SNR» column of the peak table became «significance» and is computed " +
            "strictly: net area divided by its own uncertainty.",
        "A single spike one channel wide is no longer taken for a peak — a " +
            "photopeak has a width set by the resolution of the device.",
        "Search says «no excess over background detected» instead of «at background " +
            "level»: the absence of a difference does not prove a match.",
        "The dose projection shows the average with the precision it was computed " +
            "from — the number now adds up on a calculator.",
        "Explanations moved under the «i» button and are set in the plain typeface; " +
            "the monospaced one is left to numbers.",
    )

    override val v009Title = "Home and the chart read at a glance"
    override val v009Lines = listOf(
        "The home screen is laid out in the order of the questions: a large dose " +
            "rate, the state of the background under it, then the «Count», " +
            "«Trend/h» and «Today» tiles.",
        "Tapping the conclusion itself («within the usual range of this profile») " +
            "opens the explanation — there is no separate «Why?» button to hunt for.",
        "Under the conclusion you always see whether the statistics of the place " +
            "are being added to right now, and if not — for what reason.",
        "«Place fingerprint» moved into Search — where the same question about the " +
            "place is asked; there the conclusion opens its breakdown on a tap and " +
            "says what exactly stays unchanged and over what time.",
        "A dash instead of a trend now explains itself: «intervals 3 of 12» instead " +
            "of a silent «—», and a computed trend names the window it was taken over.",
        "The value axis fits what the device actually shows: an ordinary background " +
            "fills the chart instead of a narrow strip above zero.",
        "The alarm threshold no longer stretches the axis: while it is far away, an " +
            "«↑ L1» pointer stands at the edge and the frame stays readable.",
        "Two lines are left under the chart; the distribution, the full statistics " +
            "and the «i» help open as panels on top and take no height from it.",
        "The triangle above the field is now honestly called «interval maximum above " +
            "the profile P90» and shows both numbers at once.",
        "The screen stays lit while the chart is open.",
        "Spectrogram: the column step is chosen from the statistics (a five-second " +
            "column of background is noise, not lines), brightness is comparable " +
            "between columns, a pause in recording shows as an empty column.",
        "The edge channel of the spectrum is no longer drawn as a peak at 2.8 MeV: " +
            "that is the end of the scale, not a measurement. Its content is shown " +
            "on a separate line.",
    )

    override val v008Title = "The home chart is the full-screen one"
    override val v008Lines = listOf(
        "The home card shows exactly the picture that opens on a tap: the same " +
            "intervals, the same spread, the same time window.",
        "The chart window is remembered and shared by both screens.",
        "While the data keeps coming, the age mark takes no room on the screen — " +
            "it appears when the stream falls behind or breaks.",
        "Transitions between screens and sections became smooth.",
    )

    override val v007Title = "Time windows and the chart background"
    override val v007Lines = listOf(
        "Fifteen windows from a minute to 30 days instead of six; the chosen window " +
            "is remembered, and the strip of windows is folded into a single chip.",
        "Gaps in measurements are hatched: an empty spot no longer reads as a low " +
            "level.",
        "The area before the start of history is shaded; on long windows days and " +
            "hours are visible.",
        "Triangles above the field mark the highest reading of the interval, and the " +
            "app explains what they mean.",
    )

    override val v006Title = "A full-screen chart for three quantities"
    override val v006Lines = listOf(
        "Dose rate, count rate and hardness open in one and the same chart with a " +
            "cursor, a distribution and the statistics of the window.",
        "Count rate and hardness can be switched on as separate blocks of Home.",
        "Count rate and hardness have windows up to 6 hours — under the chart it is " +
            "written why longer ones are not there yet.",
    )

    override val v005Title = "Radiation fingerprint of a place"
    override val v005Lines = listOf(
        "The app remembers the dose distribution, the count distribution and the " +
            "shape of the spectrum for a place, and then compares the current " +
            "measurements with them.",
        "The conclusion is descriptive, a line per measurement: what exactly differs " +
            "from the history of this place, and by how much.",
        "A matching fingerprint does not prove that the device is in the same place, " +
            "and a discrepancy does not name the cause — the screen says so itself.",
    )

    override val v001Title = "Search, log and settings"
    override val v001Lines = listOf(
        "Search compares the count with the recorded background by an exact " +
            "statistical test and shows what the conclusion is made of.",
        "Sound and vibration are switched on by two buttons on the screen itself, " +
            "and the choice of channel lives in the settings.",
        "In History you can select and delete the sessions and spectra you no " +
            "longer need.",
        "Settings are split into categories; in «Diagnostics» a report on the state " +
            "of the app is saved into a folder you choose.",
    )
}

val ReleaseCatalogue = AreaCatalogue(ru = ReleaseRu, en = ReleaseEn)

/** Весь текст области — для проверок, действующих на каждый язык. */
fun ReleaseStrings.allTexts(): List<String> = listOf(
    listOf(v021Title) + v021Lines,
    listOf(v020Title) + v020Lines,
    listOf(v010Title) + v010Lines,
    listOf(v009Title) + v009Lines,
    listOf(v008Title) + v008Lines,
    listOf(v007Title) + v007Lines,
    listOf(v006Title) + v006Lines,
    listOf(v005Title) + v005Lines,
    listOf(v001Title) + v001Lines,
).flatten()

