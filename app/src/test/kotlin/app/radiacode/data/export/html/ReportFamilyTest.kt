package app.radiacode.data.export.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Отчёты сессии, маршрута, опыта и сравнения — по тем же правилам, что и
 * спектр: страница открывается без сети, чужой текст не становится разметкой,
 * а решение человека о координатах исполняется буквально.
 *
 * Отдельная проверка на маршрут — не формальность: файл маршрута уезжает из
 * телефона вместе с адресом дома, и «без координат» обязано означать, что
 * координат в файле НЕТ, а не что их не рисует карта.
 */
class ReportFamilyTest {

    // ------------------------------------------------------------- сессия

    private fun sessionReport(
        title: String = "Прогулка",
        events: List<ReportEvent> = listOf(ReportEvent("14:02", "отклонение от обычного")),
        series: List<ReportSeries> = listOf(
            ReportSeries(
                title = ReportRu.doseSection,
                unit = "мкЗв/ч",
                points = (0 until 600).map { 1_700_000_000_000L + it * 1000L to 0.12 + it % 7 * 0.01 },
            ),
        ),
    ) = SessionReport(
        title = title,
        subtitle = "17 августа 2026 · 14:00–15:00",
        heroCells = listOf(Triple("0,15", ReportRu.average, "мкЗв/ч")),
        series = series,
        events = events,
        details = listOf("Профиль" to "дом"),
        notes = emptyList(),
        footer = ReportRu.madeBy("RadiaCode Companion", "0.7.9", "17.08.2026 15:10"),
        strings = ReportRu,
        timeLabel = { "14:00" },
    )

    @Test
    fun `отчёт сессии самодостаточен и цел`() {
        val page = SessionReportHtml.render(sessionReport())
        assertNoNetwork(page)
        assertTrue(page.contains("<meta name=\"radiacode-report-type\" content=\"session\">"))
        assertBalanced(page)
    }

    @Test
    fun `у сессии без событий нет раздела событий`() {
        val page = SessionReportHtml.render(sessionReport(events = emptyList()))
        assertFalse(page.contains(ReportRu.eventsSection))
    }

    @Test
    fun `имя сессии не становится разметкой`() {
        val page = SessionReportHtml.render(sessionReport(title = "<img src=x onerror=alert(1)>"))
        assertFalse(page.contains("<img src=x"))
        assertTrue(page.contains("&lt;img src=x"))
    }

    // ------------------------------------------------------------ маршрут

    private fun routePoints(count: Int = 40) = (0 until count).map { index ->
        ReportRoutePoint(
            timestamp = 1_700_000_000_000L + index * 10_000L,
            latitude = 55.7500 + index * 0.0010,
            longitude = 37.6200 + index * 0.0007,
            value = 0.10 + index % 5 * 0.02,
        )
    }

    private fun routeReport(privacy: RoutePrivacy) = RouteReport(
        title = "Маршрут",
        subtitle = "17 августа 2026 · 14:00",
        heroCells = listOf(Triple("0,14", ReportRu.average, "мкЗв/ч")),
        points = routePoints(),
        valueUnit = "мкЗв/ч",
        privacy = privacy,
        details = listOf("Начало" to "17 августа 2026 · 14:00"),
        notes = emptyList(),
        footer = ReportRu.madeBy("RadiaCode Companion", "0.7.9", "17.08.2026 15:10"),
        strings = ReportRu,
        timeLabel = { "14:00" },
    )

    @Test
    fun `полный маршрут рисует карту`() {
        val page = RouteReportHtml.render(routeReport(RoutePrivacy.FULL))
        assertNoNetwork(page)
        assertTrue(page.contains("id=\"route-map\""), "карты нет")
        assertTrue(page.contains("<meta name=\"radiacode-report-type\" content=\"route\">"))
        assertBalanced(page)
    }

    @Test
    fun `без координат в файле нет ни одной координаты`() {
        val page = RouteReportHtml.render(routeReport(RoutePrivacy.NO_COORDINATES))
        assertFalse(page.contains("id=\"route-map\""), "карта осталась")
        assertFalse(page.contains("55,75") || page.contains("55.75"), "широта осталась")
        assertFalse(page.contains("37,62") || page.contains("37.62"), "долгота осталась")
        // Молчать об этом нельзя: читатель должен видеть, чего в файле нет.
        assertTrue(page.contains(ReportRu.privacyDropped))
    }

    @Test
    fun `обрезка концов убирает и начало, и конец`() {
        val points = routePoints()
        val kept = RouteTrim.ends(points)
        assertTrue(kept.size < points.size)
        assertFalse(kept.contains(points.first()), "начало осталось")
        assertFalse(kept.contains(points.last()), "конец остался")
        // И об этом тоже сказано на странице.
        val page = RouteReportHtml.render(routeReport(RoutePrivacy.TRIM_ENDS))
        assertTrue(page.contains(ReportRu.privacyTrimmed))
    }

    @Test
    fun `короткий маршрут не обрезается, а исчезает целиком`() {
        // У следа из пяти точек нет середины, которую можно показать, не
        // показав концы.
        assertTrue(RouteTrim.ends(routePoints(count = 5)).isEmpty())
    }

    // --------------------------------------------------------------- опыт

    private fun experimentReport(notes: List<String> = emptyList()) = ExperimentReport(
        title = "Эксперимент",
        subtitle = "17 августа 2026 · 12:00",
        verdictText = "различие подтверждено критерием",
        geometry = listOf("Расстояние" to "10 см", "Размещение" to "на столе"),
        runs = listOf(
            ReportRun(
                label = "A",
                timeText = "12:00",
                durationText = "10:00",
                counts = 12_400,
                rateText = "20,7 имп/с",
                spectrum = (0 until 256).map { 100 - it / 4 },
                energies = (0 until 256).map { 2.0 + it * 11.0 },
            ),
            ReportRun(
                label = "B",
                timeText = "12:20",
                durationText = "10:00",
                counts = 18_900,
                rateText = "31,5 имп/с",
                spectrum = null,
                energies = null,
            ),
        ),
        comparisons = listOf(
            ReportComparison("полный счёт", "20,7 имп/с", "31,5 имп/с", "8,4 σ", "различие есть"),
        ),
        details = listOf("Вид опыта" to "shielding"),
        notes = notes,
        footer = ReportRu.madeBy("RadiaCode Companion", "0.7.9", "17.08.2026 15:10"),
        strings = ReportRu,
    )

    @Test
    fun `отчёт опыта хранит геометрию и оговорку`() {
        val page = ExperimentReportHtml.render(experimentReport())
        assertNoNetwork(page)
        assertTrue(page.contains("10 см"), "геометрия потеряна")
        assertTrue(page.contains(ReportRu.experimentDisclaimer), "оговорка пропала")
        assertTrue(page.contains("<meta name=\"radiacode-report-type\" content=\"experiment\">"))
        assertBalanced(page)
    }

    @Test
    fun `вывод опыта не превращается в приговор веществу`() {
        val page = ExperimentReportHtml.render(experimentReport()).lowercase()
        for (word in listOf("обнаружен", "безопас", "опасн", "норма")) {
            assertFalse(page.contains(word), "«$word» в отчёте опыта")
        }
    }

    // ----------------------------------------------------------- сравнение

    private fun comparisonReport() = ComparisonReport(
        title = "Сравнение записей",
        subtitle = "2 записи",
        unit = "мкЗв/ч",
        series = listOf(
            ReportSeries("дом", "мкЗв/ч", (0 until 120).map { it.toLong() to 0.12 + it % 3 * 0.01 }),
            ReportSeries("работа", "мкЗв/ч", (0 until 90).map { it.toLong() to 0.16 + it % 4 * 0.01 }),
        ),
        columns = listOf("Запись", "Начало"),
        rows = listOf(listOf("дом", "17 августа"), listOf("работа", "18 августа")),
        details = emptyList(),
        notes = emptyList(),
        footer = ReportRu.madeBy("RadiaCode Companion", "0.7.9", "17.08.2026 15:10"),
        strings = ReportRu,
        elapsedLabel = { "${it.toInt()} с" },
    )

    @Test
    fun `сводный отчёт называет каждую кривую и не выносит вердикта`() {
        val page = ComparisonReportHtml.render(comparisonReport())
        assertNoNetwork(page)
        assertTrue(page.contains("class=\"legend\""), "легенды нет")
        assertTrue(page.contains("дом") && page.contains("работа"), "имена кривых пропали")
        assertTrue(page.contains(ReportRu.elapsedAxisNote), "ось не объяснена")
        assertTrue(page.contains(ReportRu.comparisonDisclaimer), "оговорка пропала")
        assertBalanced(page)
    }

    @Test
    fun `наложение делит одну шкалу на всех`() {
        // Ряды в собственных масштабах выглядели бы одинаковыми при разнице в
        // десять раз, поэтому у наложения одна ось значений.
        val svg = HtmlChart.overlay(
            id = "x",
            series = listOf(
                HtmlChart.Series("тихо", listOf(HtmlChart.Point(0.0, 1.0, ""), HtmlChart.Point(1.0, 1.0, ""))),
                HtmlChart.Series("громко", listOf(HtmlChart.Point(0.0, 10.0, ""), HtmlChart.Point(1.0, 10.0, ""))),
            ),
            axisLabels = emptyList(),
            valueUnit = "имп/с",
            title = "наложение",
        )
        val ys = Regex("""M(\d+\.\d) (\d+\.\d)""").findAll(svg).map { it.groupValues[2] }.toList()
        assertEquals(2, ys.size, "нарисованы не обе кривые")
        assertTrue(ys[0] != ys[1], "кривые совпали — значит масштабы разные")
    }


    // ------------------------------------------------- график во весь экран

    @Test
    fun `каждый график разворачивается во весь экран`() {
        // График шириной в ладонь читается плохо, и поворот его не увеличивает.
        val pages = listOf(
            SessionReportHtml.render(sessionReport()),
            RouteReportHtml.render(routeReport(RoutePrivacy.FULL)),
            ExperimentReportHtml.render(experimentReport()),
            ComparisonReportHtml.render(comparisonReport()),
        )
        for (page in pages) {
            assertTrue(page.contains("rcExpand("), "нет кнопки разворота")
            assertTrue(page.contains("figure:fullscreen"), "нет полноэкранной раскладки")
            assertTrue(page.contains("figure.rc-full"), "нет запасной раскладки")
        }
        // И у карты маршрута тоже: след во всю ширину ладони читается не лучше.
        val route = RouteReportHtml.render(routeReport(RoutePrivacy.FULL))
        assertTrue(route.contains("rcExpand('route-map')"))
    }

    @Test
    fun `кнопки графика говорят на языке отчёта`() {
        val en = SessionReportHtml.render(sessionReport().copy(strings = ReportEn))
        assertTrue(en.contains("Full screen"), "русская кнопка в английском отчёте")
        assertFalse(en.contains("Во весь экран"))
        val ru = SessionReportHtml.render(sessionReport())
        assertTrue(ru.contains("Во весь экран"))
    }

    // ------------------------------------------------------------- общее

    private fun assertNoNetwork(page: String) {
        assertFalse(page.contains("http://"), "внешняя ссылка")
        assertFalse(page.contains("https://"), "внешняя ссылка")
        assertFalse(page.contains("<script src"), "внешний скрипт")
        assertFalse(page.contains("<link "), "внешний стиль")
        assertFalse(page.contains("fetch("), "сетевой запрос")
    }

    private fun assertBalanced(page: String) {
        val pairs = listOf(
            "<html" to "</html>",
            "<head>" to "</head>",
            "<body>" to "</body>",
            "<main>" to "</main>",
            "<style>" to "</style>",
            "<script" to "</script>",
            "<section>" to "</section>",
            "<table" to "</table>",
            "<svg" to "</svg>",
            "<figure" to "</figure>",
        )
        for ((open, close) in pairs) {
            assertEquals(page.split(open).size, page.split(close).size, "тег $open не закрыт")
        }
    }
}
