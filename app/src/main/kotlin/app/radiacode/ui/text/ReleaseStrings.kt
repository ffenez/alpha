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

    val v037Title: String
    val v037Summary: String

    val v036Title: String
    val v036Summary: String

    val v035Title: String
    val v035Summary: String

    val v034Title: String
    val v034Summary: String

    val v033Title: String
    val v033Summary: String

    val v032Title: String
    val v032Summary: String

    val v031Title: String
    val v031Summary: String

    val v030Title: String
    val v030Summary: String

    val v021Title: String
    val v021Summary: String

    val v020Title: String
    val v020Summary: String

    val v010Title: String
    val v010Summary: String

    val v009Title: String
    val v009Summary: String

    val v008Title: String
    val v008Summary: String

    val v007Title: String
    val v007Summary: String

    val v006Title: String
    val v006Summary: String

    val v005Title: String
    val v005Summary: String

    val v001Title: String
    val v001Summary: String
}

object ReleaseRu : ReleaseStrings {

    override val v037Title = "Видно, когда Bluetooth выключен"
    override val v037Summary =
        "Главная предупреждает, если Bluetooth выключен: прибор в этот момент не " +
        "подключится ничем, а раньше это было видно только в отчёте о связи. " +
        "«Связь восстанавливается» с экрана убрано — о том, что связи нет, " +
        "говорит цвет точки в шапке."

    override val v036Title = "История короче, и пояснений больше нет по умолчанию"
    override val v036Summary =
        "Запись в журнале — место, время, среднее, доза и сколько времени были " +
        "данные; всё остальное внутри записи, а пояснения выключены с самого " +
        "начала. След на карте больше не ждёт вечно: он пишется по всем " +
        "источникам координат, а не только по спутникам, и называет причину, " +
        "если точек нет."

    override val v035Title = "Вкладки листаются пальцем"
    override val v035Summary =
        "Между вкладками теперь переходят свайпом — содержимое идёт за пальцем. " +
        "А когда всё как обычно, Главная молчит: вместо неизменной строки слева " +
        "зелёный кружок, по нему открывается «Информация» с числами и критерием."

    override val v034Title = "«Набралось сегодня» открывает свой экран"
    override val v034Summary =
        "Накопленная доза ушла из Истории на свой экран — он открывается нажатием " +
        "на плитку «Набралось сегодня» на Главной, где этот вопрос и возникает. " +
        "Там столбики по дням за 7, 30 или 90 суток: день без записи остаётся " +
        "пустым, а период не показывается, пока за ним нет измерений."

    override val v033Title = "История больше не рассыпается на куски"
    override val v033Summary =
        "Разрыв связи и перезапуск службы больше не заканчивают измерение: за три " +
        "часа дома получалась одна запись с названными перерывами, а не восемь " +
        "почти одинаковых. Строка журнала стала короче — среднее и накопленная " +
        "доза, остальное внутри записи, — а правка профиля переехала туда же."

    override val v032Title = "Пояснения можно выключить"
    override val v032Summary =
        "Серые строки, которые объясняют экран, убираются одним переключателем в " +
        "Настройках → Вид. Состояния — «нет связи», «прибор не подключён» — " +
        "остаются всегда: без них работающий экран не отличить от молчащего."

    override val v031Title = "График снова показывает измерения"
    override val v031Summary =
        "На пяти минутах видно каждое измерение, а не десяток усреднённых узлов: " +
        "ширину интервала задаёт видимое окно, а не запас, который читается " +
        "заранее ради плавного перелистывания. Чипом «сглаживание» рядом с «лог» " +
        "возвращается прежний вид с полосами разброса — данные и числа окна у " +
        "обоих видов одни."

    override val v030Title = "Выводы словами, числа под ними"
    override val v030Summary =
        "«Наведение», радон и линия во времени отвечают словами — куда вести " +
        "прибор, выделяется ли линия над фоном спектра, — а интервалы, пороги и " +
        "окна расчёта открываются кнопкой «Подробнее». Заодно: подписи кнопок не " +
        "обрезаются при крупном шрифте, спектр объясняет, зачем ему записанный " +
        "фон, а условия опыта запоминаются и напоминаются перед каждым следующим " +
        "прогоном."

    override val v021Title = "Графики идут в ногу со временем"
    override val v021Summary =
        "Живые графики снова обновляются на глазах, а пропуск в данных остаётся " +
        "пропуском: линия заканчивается там, где кончились измерения. Надпись " +
        "«нет новых данных» теперь считается по приходу измерений с прибора, а не " +
        "по его часам."

    override val v020Title = "Понятнее человеку, строже к данным"
    override val v020Summary =
        "Объяснения разложены по глубине: сначала что показания значат, потом как " +
        "приложение это решило, и только потом расчёты. На главном экране остался " +
        "вывод, а числа под ним открываются нажатием на сам вывод."

    override val v010Title = "Точнее в терминах, чище на экране"
    override val v010Summary =
        "Приложение работает со всей серией RadiaCode: модель определяется по " +
        "серийному номеру, а разрешение детектора берётся её собственное. Отладка " +
        "сохраняется одним архивом вместе с вашим описанием происходящего."

    override val v009Title = "Главная и график читаются с одного взгляда"
    override val v009Summary =
        "Главный экран собран по порядку вопросов: доза, состояние фона, затем " +
        "счёт, тренд и накопленное за день. Объяснение открывается нажатием на " +
        "сам вывод — отдельной кнопки «Почему?» больше нет."

    override val v008Title = "График на Главной — тот же, что на весь экран"
    override val v008Summary =
        "Карточка Главной показывает ровно ту картинку, которая откроется по " +
        "нажатию: те же интервалы, тот же разброс, то же окно времени, и оно " +
        "запоминается."

    override val v007Title = "Окна времени и фон графика"
    override val v007Summary =
        "Пятнадцать окон от минуты до 30 дней вместо шести, выбранное " +
        "запоминается. Пропуски измерений заштрихованы: пустое место больше не " +
        "читается как низкий уровень."

    override val v006Title = "Полноэкранный график для трёх величин"
    override val v006Summary =
        "Мощность дозы, скорость счёта и жёсткость открываются одним и тем же " +
        "графиком с курсором, распределением и статистикой окна."

    override val v005Title = "Радиационный отпечаток места"
    override val v005Summary =
        "Приложение запоминает для места распределение дозы, распределение счёта " +
        "и форму спектра и сравнивает с ними текущие измерения. Вывод " +
        "описательный: что именно отличается от истории этого места и " +
        "насколько, а отсутствие различия не доказывает совпадение."

    override val v001Title = "Поиск, журнал и настройки"
    override val v001Summary =
        "Поиск сравнивает счёт с записанным фоном точным статистическим критерием " +
        "и показывает, из чего сделан вывод. Звук и вибрация включаются на самом " +
        "экране."

}

object ReleaseEn : ReleaseStrings {

    override val v037Title = "It is visible when Bluetooth is off"
    override val v037Summary =
        "Home warns when Bluetooth is off: the instrument cannot connect at all in " +
        "that state, and until now it showed only in the connection report. " +
        "«Reconnecting» is gone from the screen — the colour of the link dot says " +
        "that the link is down."

    override val v036Title = "История is shorter, and explanations start off"
    override val v036Summary =
        "A journal row is the place, the time, the average, the dose and how long " +
        "there was data; everything else lives inside the record, and explanations " +
        "start off. A track no longer waits forever: it is written from every source " +
        "of coordinates, not satellites alone, and it names the reason when there " +
        "are no points."

    override val v035Title = "Tabs move under your finger"
    override val v035Summary =
        "Swipe left or right to change tabs — the content follows the finger. And " +
        "when everything is as usual Home stays quiet: a green dot on the left " +
        "instead of a line that never changes, opening «Information» with the numbers."

    override val v034Title = "«Collected today» opens a screen of its own"
    override val v034Summary =
        "Accumulated dose left История for a screen of its own, opened by tapping " +
        "«Collected today» on Home, where the question comes up. It draws daily bars " +
        "over 7, 30 or 90 days: a day without recording stays empty, and a period is " +
        "not offered until there is history behind it."

    override val v033Title = "History stops falling apart"
    override val v033Summary =
        "A lost link or a restarted service no longer ends a measurement: three hours " +
        "at home are one record with its breaks named, not eight near-identical ones. " +
        "A journal row now carries the average and the accumulated dose, the rest " +
        "lives inside the record — and so does changing its profile."

    override val v032Title = "Explanations can be switched off"
    override val v032Summary =
        "The grey lines that explain a screen are removed by one switch in Settings " +
        "→ Appearance. States — «no link», «no instrument connected» — always stay: " +
        "without them a working screen looks the same as a silent one."

    override val v031Title = "The chart shows the measurements again"
    override val v031Summary =
        "Over five minutes every measurement is visible instead of a dozen averaged " +
        "nodes: the interval width comes from the visible window, not from the " +
        "read-ahead kept for smooth panning. The «smoothing» chip next to «log» " +
        "brings back the previous view with its spread bands — both views draw the " +
        "same data and report the same numbers."

    override val v030Title = "Conclusions in words, numbers underneath"
    override val v030Summary =
        "Guidance, radon and the line over time answer in words — where to move " +
        "the probe, whether the line stands out above the spectrum background — " +
        "while intervals, thresholds and windows open under «More detail». Also: " +
        "button labels no longer lose words at a large font, the spectrum " +
        "explains why it needs a recorded background, and the conditions of an " +
        "experiment are remembered and shown before every later run."

    override val v021Title = "The charts keep up with the clock"
    override val v021Summary =
        "Live charts move again, and a gap in the data stays a gap: the line ends " +
        "where the measurements ended. «No new data» is now judged by when " +
        "readings arrived, not by the instrument's own clock."

    override val v020Title = "Clearer to a person, stricter with data"
    override val v020Summary =
        "Explanations are arranged by depth: first what the readings mean, then " +
        "how the app decided that, and only then the calculations. The main " +
        "screen keeps the conclusion; the numbers open by tapping the conclusion " +
        "itself."

    override val v010Title = "Sharper terms, cleaner screen"
    override val v010Summary =
        "The app works with the whole RadiaCode series: the model is recognised " +
        "by serial number and the detector resolution is taken from it. " +
        "Diagnostics are saved as one archive together with your description of " +
        "what happened."

    override val v009Title = "Home and the chart read at a glance"
    override val v009Summary =
        "The home screen follows the order of the questions: dose, the state of " +
        "the background, then count, trend and the day's total. The explanation " +
        "opens by tapping the conclusion — there is no separate «Why?» button any " +
        "more."

    override val v008Title = "The home chart is the full-screen one"
    override val v008Summary =
        "The home card shows exactly the picture that opens on a tap: the same " +
        "intervals, the same spread, the same time window — and it is remembered."

    override val v007Title = "Time windows and the chart background"
    override val v007Summary =
        "Fifteen windows from a minute to 30 days instead of six, and the chosen " +
        "one is remembered. Gaps in measurements are hatched: an empty spot no " +
        "longer reads as a low level."

    override val v006Title = "A full-screen chart for three quantities"
    override val v006Summary =
        "Dose rate, count rate and hardness open in one and the same chart with a " +
        "cursor, a distribution and the statistics of the window."

    override val v005Title = "Radiation fingerprint of a place"
    override val v005Summary =
        "The app remembers the dose distribution, the count distribution and the " +
        "shape of the spectrum for a place and compares current measurements with " +
        "them. The conclusion is descriptive: what exactly differs from the " +
        "history of this place and by how much, and finding no difference does " +
        "not prove a match."

    override val v001Title = "Search, log and settings"
    override val v001Summary =
        "Search compares the count with the recorded background by an exact " +
        "statistical test and shows what the conclusion is made of. Sound and " +
        "vibration are switched on from the screen itself."

}

val ReleaseCatalogue = AreaCatalogue(ru = ReleaseRu, en = ReleaseEn)

/** Весь текст области — для проверок, действующих на каждый язык. */
fun ReleaseStrings.allTexts(): List<String> = listOf(
    v037Title, v037Summary,
    v036Title, v036Summary,
    v035Title, v035Summary,
    v034Title, v034Summary,
    v033Title, v033Summary,
    v032Title, v032Summary,
    v031Title, v031Summary,
    v030Title, v030Summary,
    v021Title, v021Summary,
    v020Title, v020Summary,
    v010Title, v010Summary,
    v009Title, v009Summary,
    v008Title, v008Summary,
    v007Title, v007Summary,
    v006Title, v006Summary,
    v005Title, v005Summary,
    v001Title, v001Summary,
)

