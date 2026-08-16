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

    val v052Title: String
    val v052Summary: String

    val v051Title: String
    val v051Summary: String

    val v050Title: String
    val v050Summary: String

    val v049Title: String
    val v049Summary: String

    val v048Title: String
    val v048Summary: String

    val v047Title: String
    val v047Summary: String

    val v046Title: String
    val v046Summary: String

    val v045Title: String
    val v045Summary: String

    val v044Title: String
    val v044Summary: String

    val v043Title: String
    val v043Summary: String

    val v042Title: String
    val v042Summary: String

    val v041Title: String
    val v041Summary: String

    val v040Title: String
    val v040Summary: String

    val v039Title: String
    val v039Summary: String

    val v038Title: String
    val v038Summary: String

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

    override val v052Title = "Снимок спектра знает, откуда он"
    override val v052Summary =
        "Каждый новый снимок запоминает прибор, прошивку и непрерывный отрезок " +
        "накопления — а по нему приложение теперь умеет считать спектр ИМЕННО ЗА " +
        "промежуток между двумя снимками, а не за все накопленные часы. Через " +
        "сброс, смену прибора или несовпадающую калибровку такая разность не " +
        "считается вовсе: у неё названа причина отказа."

    override val v051Title = "Маршрут пишется и со сложенным телефоном"
    override val v051Summary =
        "Запись больше не обрывается, когда экран погас или приложение свернули, " +
        "и продолжается в ту же строку, даже если систему заставили перезапустить " +
        "приложение. Экран маршрута расчищен: значение и время показывает сам " +
        "график под пальцем, карточки под ним убраны, а цвет следа можно задать " +
        "своими границами — отдельно для дозы и для счёта."

    override val v050Title = "График показывает то, что в окне"
    override val v050Summary =
        "Ось подстраивается под ВИДИМЫЕ измерения: ушедший за край всплеск " +
        "больше не держит шкалу до 2,00 при фоне 0,15, и линия перестала быть " +
        "горизонтальной чертой — то же у жёсткости. Метки кратковременных " +
        "отклонений ушли с карточек Главной в полноэкранный график, где их " +
        "включает чип «события», а выбор нескольких сессий и спектров снова " +
        "работает."

    override val v049Title = "В журнале везде выбирают одинаково"
    override val v049Summary =
        "Долгое нажатие на запись — маршрут, сессию или снимок — включает выбор, а " +
        "действия появляются одной строкой внизу: сравнить, объединить, удалить, " +
        "отмена. Отдельные режимы «сравнение» и «объединение» у спектров убраны — " +
        "они делали из списка четыре разных списка."

    override val v048Title = "Пустые маршруты больше не заводятся"
    override val v048Summary =
        "Маршрут появляется в Истории с первой координатой, а не с нажатия, и " +
        "запись идёт только одна: пустых «идёт запись · 0 измерений» больше нет, " +
        "а оборванная сбоем запись закрывается по последней точке и названа " +
        "прерванной. Список маршрутов переделан — крупная миниатюра в цвете " +
        "следа, дни отдельными заголовками, действия в «⋮», удаление с отменой; " +
        "«показать ещё» в сессиях больше не висит вечно."

    override val v047Title = "Карта и график маршрута ходят вместе"
    override val v047Summary =
        "Под сохранённым маршрутом появился профиль во времени: ведёшь по нему " +
        "пальцем — кольцо едет по следу на карте, трогаешь след — курсор встаёт " +
        "на графике, а карточка называет момент, измерение и точность фикса. В " +
        "сравнении двух маршрутов включается «Разница»: участки, где разбросы не " +
        "перекрылись, а где перекрылись — так и сказано."

    override val v046Title = "У маршрутов своё место в Истории"
    override val v046Summary =
        "Записанные прогулки стали отдельными записями журнала: миниатюра, путь, " +
        "среднее и максимум, переименование и сравнение нескольких маршрутов на " +
        "одной шкале цвета. Журнал теперь фильтруется — Все · Сессии · Маршруты · " +
        "Спектры, — а с карты убрана легенда: переключатель Доза/CPS переехал " +
        "влево, кнопки камеры вправо."

    override val v045Title = "След на карте — сплошная цветная линия"
    override val v045Summary =
        "Маршрут рисуется линией от зелёного к багровому, а не россыпью точек, " +
        "и пропуски координат больше не соединяются выдуманной прямой. Цвет по " +
        "умолчанию считается от обычного фона места, поэтому одно значение " +
        "выглядит одинаково на любом маршруте; растяжение по маршруту включается " +
        "в Настройках → Вид."

    override val v044Title = "Плитка «Фон» — одно число, единиц на экране нет"
    override val v044Summary =
        "«Фон» показывает середину обычного для этого места — медиану, а не " +
        "среднее: один всплеск среднее сдвигает, а середину нет. Подписи " +
        "«мкЗв/ч» и «с⁻¹» с Главной и Поиска убраны (единицы названы в " +
        "«Информации»), а насколько выше обычного число становится цветным, " +
        "задаётся в Настройках → Вид."

    override val v043Title = "Плитки называются одним словом"
    override val v043Summary =
        "ФОН · ДИНАМИКА · ДОЗА на Главной и ФОН · РАЗНИЦА в Поиске: заголовок " +
        "плитки больше не переносится на вторую строку. Единица и период ушли " +
        "под значение вторичной строкой — они свойства числа, а не имя величины."

    override val v042Title = "Смена места попадает в журнал"
    override val v042Summary =
        "Уход из дома переключает место на «В пути» — и теперь это отдельная " +
        "запись в Истории, а не продолжение домашней. «Подключено» больше не " +
        "мигает при возврате из Настроек, а профиль, который фон не собирает, не " +
        "обещает «0 ч из 3»."

    override val v041Title = "Главная и Поиск устроены одинаково"
    override val v041Summary =
        "На обоих экранах: число цветом, под ним плитки, а вывод словами — только " +
        "когда есть что сказать. Плитки Главной теперь фон, тренд и сколько " +
        "набралось за день; в Поиске скорость счёта окрашена по отношению к " +
        "записанному фону."

    override val v040Title = "Число само говорит цветом"
    override val v040Summary =
        "Главное число окрашено по отношению к обычному фону места: от зелёного " +
        "внутри обычного диапазона до багрового у вашего порога, дальше цвет не " +
        "меняется. Кружки убраны совсем: пока связь есть и данные идут, шапка " +
        "молчит, а момент подключения показывает надпись, которая гаснет сама."

    override val v039Title = "Вкладка открывается та, на которую нажали"
    override val v039Summary =
        "Нажатие «Карта» с Главной открывало Спектр: анимация проезжала через " +
        "промежуточные вкладки, и одна из них объявляла себя выбранной. Заодно " +
        "«Сделать фоном» отвечает на нажатие, а строка о пополнении обычного " +
        "фона ушла с Главной в «Информацию»."

    override val v038Title = "След пишется и с приблизительным местом"
    override val v038Summary =
        "Раньше запись требовала точного разрешения: выбравший в системном " +
        "диалоге «Приблизительно» получал молчащую кнопку. Теперь пишется и " +
        "грубый след, рядом с длительностью видно число записанных точек, а " +
        "отладочный отчёт показывает, где именно теряются координаты."

    override val v037Title = "Видно, когда Bluetooth выключен"
    override val v037Summary =
        "Главная предупреждает вверху экрана, если Bluetooth выключен: прибор в " +
        "этот момент не подключится ничем. Карту больше не дёргает свайп вкладок, " +
        "а след пишется по всем источникам координат и называет причину, если " +
        "точек нет."

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

    override val v052Title = "A saved spectrum knows where it came from"
    override val v052Summary =
        "Every saved spectrum remembers the instrument, the firmware and the " +
        "continuous stretch of accumulation it belongs to — and from that the app " +
        "can now compute the spectrum for the interval BETWEEN two of them " +
        "instead of all the accumulated hours. Across a reset, another instrument " +
        "or a different calibration that difference is refused, with the reason " +
        "named."

    override val v051Title = "A route records with the phone in a pocket"
    override val v051Summary =
        "Recording no longer stops when the screen goes off or the app is put " +
        "away, and it continues into the same entry even if the system restarts " +
        "the app. The route screen is cleared: the chart itself shows the value " +
        "and the time under the finger, the cards below are gone, and the trace " +
        "colour can follow bounds you set — separately for dose and for counts."

    override val v050Title = "The chart shows what is in the window"
    override val v050Summary =
        "The axis follows the VISIBLE measurements: a spike that left the window " +
        "no longer holds the scale at 2.00 while the background is 0.15, and the " +
        "line stopped being a flat bar — the same for hardness. Markers of short " +
        "deviations moved from the Home cards to the fullscreen chart, where the " +
        "«события» chip turns them on, and selecting several sessions or spectra " +
        "works again."

    override val v049Title = "The journal is selected the same way everywhere"
    override val v049Summary =
        "A long press on an entry — a route, a session or a spectrum — starts the " +
        "selection, and the actions appear as one row below: compare, merge, " +
        "delete, cancel. The separate «compare» and «merge» modes are gone from " +
        "the spectra — they turned one list into four different ones."

    override val v048Title = "Empty routes are no longer created"
    override val v048Summary =
        "A route enters История with its first coordinate rather than with the " +
        "tap, and only one recording runs at a time: no more empty «recording · 0 " +
        "measurements», and a run cut short by a crash is closed at its last " +
        "point and called interrupted. The route list was rebuilt — a large " +
        "thumbnail in the trace colours, days as headers, actions under «⋮», " +
        "deletion with undo; «show more» in sessions no longer stays forever."

    override val v047Title = "The route map and its chart move together"
    override val v047Summary =
        "A saved route now has a profile over time below it: drag along it and " +
        "the ring moves along the trace, touch the trace and the cursor moves on " +
        "the chart, while a card names the moment, the reading and the accuracy " +
        "of the fix. Comparing two routes offers «Разница»: the patches where the " +
        "spreads did not overlap, and where they did, it says so."

    override val v046Title = "Routes have their own place in История"
    override val v046Summary =
        "Recorded walks became journal entries of their own: a thumbnail, the " +
        "distance, the mean and the maximum, renaming, and a comparison of " +
        "several routes on one colour scale. The journal now filters — All · " +
        "Sessions · Routes · Spectra — and the map lost its legend: the " +
        "Dose/CPS switch moved left, the camera buttons right."

    override val v045Title = "The map trace is a continuous coloured line"
    override val v045Summary =
        "A route is drawn as a line from green to crimson instead of scattered " +
        "dots, and gaps in the coordinates are no longer joined by an invented " +
        "straight line. By default the colour is measured against the usual " +
        "background of the place, so one value looks the same on any route; " +
        "stretching it over the route is switched on in Settings → Вид."

    override val v044Title = "The «Фон» tile is one number, and no units on screen"
    override val v044Summary =
        "«Фон» shows the middle of what is usual for the place — the median, not " +
        "the mean: a single spike moves the mean and leaves the middle where it " +
        "was. The «мкЗв/ч» and «с⁻¹» labels are gone from Home and Search (the " +
        "units are named in «Информация»), and how far above the usual a number " +
        "turns coloured is set in Settings → Вид."

    override val v043Title = "A tile is named by one word"
    override val v043Summary =
        "BACKGROUND · TREND · DOSE on Home and BACKGROUND · DIFFERENCE in Поиск: a " +
        "tile header no longer wraps onto a second line. The unit and the period " +
        "moved under the value, where they belong — they describe the number, not " +
        "the quantity."

    override val v042Title = "A change of place reaches the journal"
    override val v042Summary =
        "Leaving home switches the place to «В пути» — and that is now a record of " +
        "its own rather than a continuation of the one at home. «Подключено» no " +
        "longer blinks on the way back from Settings, and a profile that collects no " +
        "background stops promising «0 ч из 3»."

    override val v041Title = "Home and Поиск are built the same way"
    override val v041Summary =
        "On both screens: the number carries the colour, tiles sit under it, and the " +
        "verdict in words appears only when there is something to say. Home's tiles " +
        "are the background, the trend and what accumulated today; in Поиск the " +
        "count rate is tinted against the recorded background."

    override val v040Title = "The number speaks in colour"
    override val v040Summary =
        "The main number is tinted by its relation to the usual background of the " +
        "place: green inside the usual range, crimson at your threshold, and no " +
        "further change beyond it. The dots are gone: while the link is up and data " +
        "flows the header says nothing, and a connection is greeted by a line that " +
        "fades out by itself."

    override val v039Title = "The tab you tapped is the tab that opens"
    override val v039Summary =
        "Tapping «Карта» from Home opened Спектр: the animation passed through the " +
        "tabs between them and one of them announced itself as chosen. «Set as " +
        "background» now answers the press, and the line about the usual background " +
        "moved from Home into «Information»."

    override val v038Title = "A track is written with approximate location too"
    override val v038Summary =
        "Recording used to require the precise permission, so choosing «Approximate» " +
        "in the system dialog left a button that did nothing. A coarse track is now " +
        "recorded too, the number of points sits next to the duration, and the debug " +
        "report shows where coordinates are being lost."

    override val v037Title = "It is visible when Bluetooth is off"
    override val v037Summary =
        "Home warns at the top of the screen when Bluetooth is off: the instrument " +
        "cannot connect at all in that state. The map is no longer tugged by the tab " +
        "swipe, and a track is written from every source of coordinates and names " +
        "the reason when there are no points."

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
    v052Title, v052Summary,
    v051Title, v051Summary,
    v050Title, v050Summary,
    v049Title, v049Summary,
    v048Title, v048Summary,
    v047Title, v047Summary,
    v046Title, v046Summary,
    v045Title, v045Summary,
    v044Title, v044Summary,
    v043Title, v043Summary,
    v042Title, v042Summary,
    v041Title, v041Summary,
    v040Title, v040Summary,
    v039Title, v039Summary,
    v038Title, v038Summary,
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

