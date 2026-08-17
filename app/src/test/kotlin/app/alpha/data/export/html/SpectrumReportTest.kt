package app.alpha.data.export.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HTML-отчёт: самодостаточный, честный и безопасный.
 *
 * Проверяется то, что нельзя увидеть, открыв файл глазами один раз: в
 * странице нет ни одной внешней ссылки (иначе отчёт перестанет открываться
 * без сети и начнёт сообщать о себе наружу), пользовательский текст не может
 * стать разметкой, а числа и оговорки на месте.
 */
class SpectrumReportTest {

    private fun report(
        title: String = "Спектр «дома»",
        peaks: List<ReportPeak> = listOf(
            ReportPeak(1460.8, 1_240.0, 12.4, "K-40", "средняя"),
            ReportPeak(609.3, 320.0, 5.1, null, null),
        ),
        notes: List<String> = listOf("Прибор: RadiaCode 110"),
    ): SpectrumReport {
        val counts = (0 until 1024).map { channel ->
            val background = 400 - channel / 4
            val peak = if (channel in 500..520) 800 else 0
            (background + peak).coerceAtLeast(1)
        }
        return SpectrumReport(
            title = title,
            subtitle = "17 августа 2026 · 14:20",
            durationText = "126:47:03",
            totalCounts = counts.sumOf { it.toLong() },
            counts = counts,
            energies = counts.indices.map { 2.0 + it * 2.9 },
            peaks = peaks,
            details = listOf("Прибор" to "RadiaCode 110", "Прошивка" to "4.8"),
            notes = notes,
            footer = ReportRu.madeBy("Alpha", "0.7.8", "17.08.2026 02:25"),
            strings = ReportRu,
        )
    }

    private fun html(report: SpectrumReport = report()) = SpectrumReportHtml.render(report)

    @Test
    fun `страница самодостаточна`() {
        val page = html()
        // Ни одной ссылки наружу: ни шрифтов, ни библиотек, ни картинок.
        assertFalse(page.contains("http://"), "внешняя ссылка в отчёте")
        assertFalse(page.contains("https://"), "внешняя ссылка в отчёте")
        assertFalse(page.contains("<script src"), "внешний скрипт в отчёте")
        assertFalse(page.contains("<link "), "внешний стиль в отчёте")
        assertFalse(page.contains("fetch("), "сетевой запрос в отчёте")
        assertFalse(page.contains("XMLHttpRequest"), "сетевой запрос в отчёте")
    }

    @Test
    fun `разметка целая и знает, что она за отчёт`() {
        val page = html()
        assertTrue(page.startsWith("<!doctype html>"))
        assertTrue(page.trimEnd().endsWith("</html>"))
        assertTrue(page.contains("<meta name=\"alpha-report-type\" content=\"spectrum\">"))
        assertTrue(page.contains("<meta name=\"alpha-report-version\""))
        // Считать все «<» нельзя: внутри скрипта они законны как сравнения.
        // Проверяется целость каркаса: каждый открытый тег страницы закрыт.
        // Открывающая форма пишется явно: «<head» совпало бы и с «<header».
        val pairs = listOf(
            "<html" to "</html>",
            "<head>" to "</head>",
            "<body>" to "</body>",
            "<main>" to "</main>",
            "<footer>" to "</footer>",
            "<style>" to "</style>",
            "<script" to "</script>",
            "<section>" to "</section>",
            "<table" to "</table>",
            "<svg" to "</svg>",
        )
        for ((open, close) in pairs) {
            assertEquals(page.split(open).size, page.split(close).size, "тег $open не закрыт")
        }
    }

    @Test
    fun `пользовательский текст не становится разметкой`() {
        val page = html(
            report(
                title = "Спектр <script>alert(1)</script>",
                notes = listOf("заметка с <b>тегом</b> и \"кавычками\""),
            ),
        )
        assertFalse(page.contains("<script>alert(1)</script>"))
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(page.contains("&lt;b&gt;"))
    }

    @Test
    fun `числа отчёта на месте`() {
        val page = html()
        assertTrue(page.contains("126:47:03"), "накопление")
        assertTrue(page.contains("1460,8"), "энергия пика")
        assertTrue(page.contains("K-40"), "возможное совпадение")
        assertTrue(page.contains("RadiaCode 110"), "прибор")
    }

    @Test
    fun `совпадение по энергии не выдаётся за обнаружение`() {
        val page = html().lowercase()
        assertFalse(page.contains("обнаружен"), "«обнаружен» в отчёте")
        assertFalse(page.contains("безопас"), "оценка безопасности в отчёте")
        assertFalse(page.contains("норма"), "«норма» в отчёте")
        assertTrue(page.contains("гипотеза"), "оговорка о совпадении пропала")
    }

    @Test
    fun `пустые разделы не рисуются`() {
        val page = html(report(peaks = emptyList(), notes = emptyList()))
        assertFalse(page.contains(ReportRu.peaksSection))
        assertFalse(page.contains(ReportRu.notesSection))
        assertTrue(page.contains(ReportRu.spectrumSection))
    }

    @Test
    fun `пик на графике связан со строкой таблицы`() {
        val page = html()
        assertTrue(page.contains("data-peak=\"p0\""), "отметка пика")
        assertTrue(page.contains("class=\"peak\" data-figure=\"spectrum\" data-peak=\"p0\""))
    }

    @Test
    fun `график печатается и переключает шкалу`() {
        val page = html()
        assertTrue(page.contains("@media print"))
        assertTrue(page.contains("data-set-mode=\"log\""))
        assertTrue(page.contains("data-mode=\"log\""))
    }

    @Test
    fun `миллион отсчётов не уезжает в отчёт целиком`() {
        // Отчёт — не резервная копия: ряд прореживается до того, что видно.
        val points = (0 until 100_000).map {
            HtmlChart.Point(it.toDouble(), (it % 97).toDouble(), "")
        }
        val reduced = HtmlChart.downsample(points)
        assertTrue(reduced.size <= HtmlChart.MAX_POINTS + 2, "точек ${reduced.size}")
    }

    @Test
    fun `узкий пик переживает прореживание`() {
        val points = (0 until 20_000).map {
            HtmlChart.Point(it.toDouble(), if (it == 12_345) 9_999.0 else 5.0, "")
        }
        val reduced = HtmlChart.downsample(points)
        assertTrue(reduced.any { it.value == 9_999.0 }, "пик исчез при прореживании")
    }

    @Test
    fun `оба языка отчёта описывают одно и то же`() {
        val ru = SpectrumReportHtml.render(report())
        val en = SpectrumReportHtml.render(report().copy(strings = ReportEn))
        assertTrue(en.contains("Peaks found"))
        assertTrue(ru.contains("Найденные пики"))
        assertTrue(en.contains("hypothesis"))
    }
}
