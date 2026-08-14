package app.radiacode.ui.text

/**
 * Каталог строк области «Карта»: экран карты, накопленная карта следа, статус
 * тайлов и собственная позиция.
 *
 * Перевод переносит ПРАВИЛО, а не слова. Карта — самое соблазнительное место
 * для вывода, которого нет в данных, поэтому в обоих языках держатся три вещи:
 *
 *  - клетка накопленной карты называет свою статистику вслух (МЕДИАНА, а не
 *    «уровень»), рядом стоят P10–P90, n и период записей — цвет клетки это
 *    утверждение о выборке, а не о месте;
 *  - честность выборки: сколько точек реально попало в картинку, какие фиксы
 *    исключены и почему бледные клетки бледные;
 *  - статус тайлов говорит, приходят ли они, и называет причину, когда не
 *    приходит ни одного — в поле у человека нет логов.
 *
 * Термины статистики (P10–P90, медиана) не переводятся в бытовые слова.
 */
interface MapStrings {

    /** Экспорт трека — открытый формат, явное действие. */
    val exportGpx: String
    val exportSaved: String
    val exportFailed: String

    // --- заголовки и режимы ---
    val scopeCurrent: String
    val scopeAll: String
    val mapTitle: String
    val sessionTrack: String
    val noTrackInSession: String
    val gpsOff: String
    /** Строка над картой: состояние И действие в одной короткой фразе. */
    val gpsOffAction: String
    fun lastRecording(stamp: String): String
    fun recordingFor(duration: String): String

    // --- действия ---
    val back: String
    val showAllRecordings: String
    val startRecording: String
    /** На экране уже лежит маршрут — кнопка заводит НОВЫЙ, и говорит это. */
    val startNewRecording: String
    val stopRecording: String
    val routeMine: String
    val route: String

    // --- оверлей карты ---
    fun pointsAndCells(points: String, cells: String): String
    val centerOnMe: String
    val centerOnRoute: String
    val centerOnAll: String
    val metricDose: String
    val metricCps: String
    /** Единица скорости счёта — только в легенде шкалы, где величин две. */
    val unitCps: String

    // --- чем заданы границы цвета ---
    val scaleAbsolute: String
    val scaleContrast: String

    // --- легенда и клетка ---
    /** «клетка ≈ 20 м» — размер уже отформатирован вместе с единицей. */
    fun cellSize(size: String): String
    val median: String
    /** Бледные клетки собраны из малого числа точек, и это названо числом. */
    fun paleCells(count: Int, minPoints: Int): String
    fun medianValue(value: String): String
    fun cellSpread(p10: String, p90: String, min: String, max: String): String
    fun cellCoverage(points: String, from: String, to: String): String

    // --- карточки тапа ---
    val trackPoint: String
    val excursionPoint: String
    fun usuallyHere(value: String): String
    fun steadyReadings(duration: String): String

    // --- сводки ---
    val statAvg: String
    val statMax: String
    val statMedian: String
    val statPoints: String
    /** Измерение вдоль маршрута — не то же самое, что точка на карте. */
    val statMeasurements: String
    val statCells: String
    val statMarkers: String
    val inThisView: String
    fun recordedFromTo(from: String, to: String): String
    fun onlyAccurateFixes(meters: Int): String
    /** Крышка строк срезала гистограмму: картинка построена не по всему. */
    fun builtFromPoints(points: String): String

    // --- пустые состояния ---
    val emptyWaitingTitle: String
    val emptyWaitingBody: String

    /**
     * Ждать можно по-разному: спутников — или разрешения, которого не будет.
     * Второе от бесконечного ожидания неотличимо, поэтому названо словами.
     */
    val emptyNoPermissionTitle: String
    val emptyNoPermissionBody: String
    /** «12 точек» рядом с длительностью записи: видно, что след живой. */
    fun recordedPoints(count: String): String

    val emptyNoProviderTitle: String
    val emptyNoProviderBody: String
    val emptyAreaTitle: String
    val emptyAreaBody: String
    val emptyTrackTitle: String
    val emptyTrackBody: String
    val emptyNoTracksTitle: String
    val emptyNoTracksBody: String

    // --- геолокация ---
    val locationTitle: String
    val locationBody: String
    val locationAllow: String
    val waitingGps: String
    fun fixAgo(duration: String): String
    fun meWithAccuracy(accuracy: String): String
    val accuracyUnknown: String
    val unitMeters: String
    val unitKilometers: String

    // --- тайлы ---
    val tilesLoading: String
    val tilesStalled: String
    fun tilesNetworkError(failed: Int): String
    fun tilesReady(loaded: Int): String
    fun tilesReadyWithFailures(loaded: Int, failed: Int): String
    val tilesNetworkHint: String
}

object MapRu : MapStrings {

    override val exportGpx = "GPX"
    override val exportSaved = "файл сохранён"
    override val exportFailed = "файл не записался — попробуйте другую папку"

    override val scopeCurrent = "Эта запись"
    override val scopeAll = "Все записи"
    override val mapTitle = "Карта"
    override val sessionTrack = "Трек сессии"
    override val noTrackInSession = "трек в этой сессии не записан"
    override val gpsOff = "GPS выключен"
    override val gpsOffAction = "GPS выключен · включить"
    override fun lastRecording(stamp: String) = "последняя · $stamp"
    override fun recordingFor(duration: String) = "запись · $duration"

    override val back = "← Назад"
    override val showAllRecordings = "Показать все записи"
    override val startRecording = "Начать маршрут"
    override val startNewRecording = "Начать новый маршрут"
    override val stopRecording = "Остановить запись"
    override val routeMine = "Мой маршрут"
    override val route = "Маршрут"

    override fun pointsAndCells(points: String, cells: String) =
        "$points точек · $cells клеток"
    override val centerOnMe = "⌖ я"
    override val centerOnRoute = "⌖ маршрут"
    override val centerOnAll = "⌖ всё"
    override val metricDose = "Доза"
    override val metricCps = "CPS"
    override val unitCps = "с⁻¹"

    override val scaleAbsolute = "цвет — по обычному фону места"
    override val scaleContrast = "цвет растянут по этому маршруту"

    override fun cellSize(size: String) = "клетка ≈ $size"
    override val median = "медиана"
    override fun paleCells(count: Int, minPoints: Int) =
        "бледные клетки ($count) — меньше $minPoints точек"
    override fun medianValue(value: String) = "$value · медиана"
    override fun cellSpread(p10: String, p90: String, min: String, max: String) =
        "P10–P90 $p10–$p90 · мин $min · макс $max"
    override fun cellCoverage(points: String, from: String, to: String) =
        "$points точек · $from → $to · расчёт"

    override val trackPoint = "Точка маршрута"
    override val excursionPoint = "Точка превышения"
    override fun usuallyHere(value: String) = "обычно здесь $value"
    override fun steadyReadings(duration: String) =
        "показания устойчивы $duration · расчёт"

    override val statAvg = "ср"
    override val statMax = "макс"
    override val statMedian = "медиана"
    override val statPoints = "точек"
    override val statMeasurements = "измерений"
    override val statCells = "клеток"
    override val statMarkers = "меток"
    override val inThisView = "В этом виде"
    override fun recordedFromTo(from: String, to: String) = "записи с $from по $to"
    override fun onlyAccurateFixes(meters: Int) = "только фиксы точнее $meters м"
    override fun builtFromPoints(points: String) =
        "картинка построена по $points точкам — приблизьте карту"

    override val emptyNoPermissionTitle = "Нет доступа к местоположению"
    override val emptyNoPermissionBody =
        "След пишется по координатам телефона. Разрешение выдаётся в настройках " +
            "системы для этого приложения."
    override fun recordedPoints(count: String) = "$count точек"

    override val emptyNoProviderTitle = "Определение места выключено"
    override val emptyNoProviderBody =
        "Ни один источник координат сейчас не включён. Включите определение " +
            "местоположения в настройках телефона — след начнёт писаться сам."

    override val emptyWaitingTitle = "Жду первые точки"
    override val emptyWaitingBody =
        "Запись идёт, координат пока нет. В помещении спутники обычно не ловятся " +
            "— точки появятся на открытом месте."
    override val emptyAreaTitle = "Здесь записей нет"
    override val emptyAreaBody =
        "В этом районе ничего не записано. Отдалите карту, чтобы увидеть " +
            "остальные записи, или начните новую."
    override val emptyTrackTitle = "В этой записи нет точек"
    override val emptyTrackBody =
        "След пишется только во время записи. Переключитесь на «все записи», " +
            "чтобы увидеть накопленную карту."
    override val emptyNoTracksTitle = "Маршрутов пока нет"
    override val emptyNoTracksBody =
        "След пишется только во время записи: пока она не включена, " +
            "координаты не сохраняются. Начните запись — маршрут окрасится " +
            "мощностью дозы, устойчивые превышения станут метками."

    override val locationTitle = "Для карты и записи маршрута нужна геолокация"
    override val locationBody =
        "Координаты нужны, чтобы показать вас на карте и привязать точки " +
            "трека. Они сохраняются только на этом телефоне и никуда не " +
            "отправляются, а запрашиваются лишь пока открыта карта или идёт " +
            "запись. Если запрос не показывается — включите доступ в " +
            "настройках Android."
    override val locationAllow = "Разрешить геолокацию"
    override val waitingGps = "жду сигнал GPS"
    override fun fixAgo(duration: String) = "я · фикс $duration назад"
    override fun meWithAccuracy(accuracy: String) = "я · $accuracy"
    override val accuracyUnknown = "точность неизвестна"
    override val unitMeters = "м"
    override val unitKilometers = "км"

    override val tilesLoading = "тайлы: загружаются…"
    override val tilesStalled = "тайлы: не приходят"
    override fun tilesNetworkError(failed: Int) = "тайлы: ошибка сети · неудачных $failed"
    override fun tilesReady(loaded: Int) = "тайлы: готово · $loaded"
    override fun tilesReadyWithFailures(loaded: Int, failed: Int) =
        "тайлы: готово · $loaded · неудачных $failed"
    override val tilesNetworkHint =
        "тайлы не загрузились — проверьте доступ приложения к сети " +
            "(GrapheneOS: разрешение «Сеть»)"
}

object MapEn : MapStrings {

    override val exportGpx = "GPX"
    override val exportSaved = "file saved"
    override val exportFailed = "the file was not written — try another folder"

    override val scopeCurrent = "This recording"
    override val scopeAll = "All recordings"
    override val mapTitle = "Map"
    override val sessionTrack = "Session track"
    override val noTrackInSession = "no track was recorded in this session"
    override val gpsOff = "GPS is off"
    override val gpsOffAction = "GPS is off · turn on"
    override fun lastRecording(stamp: String) = "latest · $stamp"
    override fun recordingFor(duration: String) = "recording · $duration"

    override val back = "← Back"
    override val showAllRecordings = "Show all recordings"
    override val startRecording = "Start a route"
    override val startNewRecording = "Start a new route"
    override val stopRecording = "Stop recording"
    override val routeMine = "My route"
    override val route = "Route"

    override fun pointsAndCells(points: String, cells: String) =
        "$points points · $cells cells"
    override val centerOnMe = "⌖ me"
    override val centerOnRoute = "⌖ route"
    override val centerOnAll = "⌖ all"
    override val metricDose = "Dose"
    override val metricCps = "CPS"
    override val unitCps = "s⁻¹"

    override val scaleAbsolute = "colour follows the usual background of the place"
    override val scaleContrast = "colour is stretched over this route"

    override fun cellSize(size: String) = "cell ≈ $size"
    override val median = "median"
    override fun paleCells(count: Int, minPoints: Int) =
        "pale cells ($count) — fewer than $minPoints points"
    override fun medianValue(value: String) = "$value · median"
    override fun cellSpread(p10: String, p90: String, min: String, max: String) =
        "P10–P90 $p10–$p90 · min $min · max $max"
    override fun cellCoverage(points: String, from: String, to: String) =
        "$points points · $from → $to · calc."

    override val trackPoint = "Track point"
    override val excursionPoint = "Excursion point"
    override fun usuallyHere(value: String) = "usually here $value"
    override fun steadyReadings(duration: String) =
        "readings held steady for $duration · calc."

    override val statAvg = "avg"
    override val statMax = "max"
    override val statMedian = "median"
    override val statPoints = "points"
    override val statMeasurements = "measurements"
    override val statCells = "cells"
    override val statMarkers = "markers"
    override val inThisView = "In this view"
    override fun recordedFromTo(from: String, to: String) = "recorded from $from to $to"
    override fun onlyAccurateFixes(meters: Int) = "only fixes better than $meters m"
    override fun builtFromPoints(points: String) =
        "the picture is built from $points points — zoom in"

    override val emptyNoPermissionTitle = "No access to location"
    override val emptyNoPermissionBody =
        "A track is written from the phone's coordinates. The permission is granted " +
            "in the system settings for this app."
    override fun recordedPoints(count: String) = "$count points"

    override val emptyNoProviderTitle = "Location is switched off"
    override val emptyNoProviderBody =
        "No source of coordinates is enabled right now. Turn location on in the " +
            "phone settings and the track starts writing itself."

    override val emptyWaitingTitle = "Waiting for the first points"
    override val emptyWaitingBody =
        "Recording is on, there are no coordinates yet. Satellites are rarely " +
            "reachable indoors — points appear in the open."
    override val emptyAreaTitle = "Nothing recorded here"
    override val emptyAreaBody =
        "Nothing has been recorded in this area. Zoom out to see the other " +
            "recordings, or start a new one."
    override val emptyTrackTitle = "This recording has no points"
    override val emptyTrackBody =
        "The trace is written only while recording. Switch to «all recordings» " +
            "to see the accumulated map."
    override val emptyNoTracksTitle = "No routes yet"
    override val emptyNoTracksBody =
        "The trace is written only while recording: until it is on, no " +
            "coordinates are stored. Start a recording — the route will be " +
            "colored by dose rate, and sustained excursions will become markers."

    override val locationTitle = "The map and route recording need location"
    override val locationBody =
        "Coordinates are needed to show you on the map and to attach track " +
            "points to places. They are stored on this phone only and are sent " +
            "nowhere, and they are requested only while the map is open or a " +
            "recording is running. If the system prompt does not appear, grant " +
            "access in the Android settings."
    override val locationAllow = "Allow location"
    override val waitingGps = "waiting for GPS"
    override fun fixAgo(duration: String) = "me · fix $duration ago"
    override fun meWithAccuracy(accuracy: String) = "me · $accuracy"
    override val accuracyUnknown = "accuracy not reported"
    override val unitMeters = "m"
    override val unitKilometers = "km"

    override val tilesLoading = "tiles: loading…"
    override val tilesStalled = "tiles: none arriving"
    override fun tilesNetworkError(failed: Int) = "tiles: network error · failed $failed"
    override fun tilesReady(loaded: Int) = "tiles: ready · $loaded"
    override fun tilesReadyWithFailures(loaded: Int, failed: Int) =
        "tiles: ready · $loaded · failed $failed"
    override val tilesNetworkHint =
        "no tiles loaded — check the app's network access " +
            "(GrapheneOS: the «Network» permission)"
}

val MapCatalogue = AreaCatalogue(ru = MapRu, en = MapEn)

/**
 * Все строки каталога — для проверок, действующих на каждый язык. Список
 * ведётся руками: рефлексии в тестовом classpath нет, а забытая строка
 * означала бы непроверенный текст.
 */
fun MapStrings.allTexts(): List<String> = listOf(
    exportGpx, exportSaved, exportFailed,
    scopeCurrent, scopeAll, mapTitle, sessionTrack, noTrackInSession, gpsOff, gpsOffAction,
    lastRecording("12:00"), recordingFor("2 мин"),
    back, showAllRecordings, startRecording, startNewRecording, stopRecording, routeMine, route,
    pointsAndCells("1 200", "48"), centerOnMe, centerOnRoute, centerOnAll,
    metricDose, metricCps, unitCps, scaleAbsolute, scaleContrast,
    cellSize("20 м"), median, paleCells(3, 5), medianValue("0,12"),
    cellSpread("0,10", "0,18", "0,09", "0,21"),
    cellCoverage("42", "12:00", "12:30"),
    trackPoint, excursionPoint, usuallyHere("0,12"), steadyReadings("2 мин"),
    statAvg, statMax, statMedian, statPoints, statMeasurements, statCells, statMarkers, inThisView,
    recordedFromTo("12:00", "12:30"), onlyAccurateFixes(50), builtFromPoints("50 000"),
    emptyWaitingTitle, emptyWaitingBody,
    emptyNoPermissionTitle, emptyNoPermissionBody, emptyNoProviderTitle, emptyNoProviderBody,
    recordedPoints("12"),
    emptyAreaTitle, emptyAreaBody,
    emptyTrackTitle, emptyTrackBody, emptyNoTracksTitle, emptyNoTracksBody,
    locationTitle, locationBody, locationAllow,
    waitingGps, fixAgo("2 мин"), meWithAccuracy("±12 м"), accuracyUnknown,
    unitMeters, unitKilometers,
    tilesLoading, tilesStalled, tilesNetworkError(4), tilesReady(42),
    tilesReadyWithFailures(42, 3), tilesNetworkHint,
)
