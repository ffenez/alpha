package app.alpha.data.export.html

import app.alpha.data.export.backup.Json

/**
 * Самодостаточная HTML-страница отчёта.
 *
 * ## Правила, из которых всё следует
 *
 * Отчёт — это ОДИН файл, который открывается двойным нажатием в любом
 * браузере, через год и без сети. Отсюда: ни одной внешней ссылки — ни на
 * шрифты, ни на библиотеки графиков, ни на карты; весь стиль и весь скрипт
 * внутри. Это же и защита данных: страница, которая ничего не загружает,
 * ничего и не отправляет.
 *
 * Отчёт не копия экрана приложения. У него своя вёрстка — светлая,
 * печатаемая, с настоящими заголовками и таблицами, — потому что его читают
 * там, где приложения нет: в браузере, в переписке, на бумаге.
 *
 * ## Экранирование
 *
 * Названия и заметки пишет человек. Всё, что попадает в страницу, проходит
 * через [escape]: заметка с `<script>` обязана остаться текстом заметки.
 */
object HtmlDocument {

    /** Версия разметки отчётов: её читает будущая версия приложения. */
    const val REPORT_VERSION = 1

    fun escape(value: String): String {
        val out = StringBuilder(value.length + 16)
        for (ch in value) {
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#39;")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * Целая страница отчёта.
     *
     * @param type что это за отчёт — «spectrum», «session», «route»,
     *   «experiment»; уезжает в машинные метаданные страницы.
     * @param metadata пары «имя → значение» для машинного чтения; в них не
     *   должно быть ничего, чего нет на экране.
     */
    fun page(
        type: String,
        title: String,
        subtitle: String?,
        metadata: List<Pair<String, String>> = emptyList(),
        footer: String,
        body: StringBuilder.() -> Unit,
    ): String {
        val out = StringBuilder(16 * 1024)
        out.append("<!doctype html>\n<html lang=\"ru\">\n<head>\n")
        out.append("<meta charset=\"utf-8\">\n")
        out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        out.append("<meta name=\"color-scheme\" content=\"light dark\">\n")
        out.append("<meta name=\"alpha-report-version\" content=\"$REPORT_VERSION\">\n")
        out.append("<meta name=\"alpha-report-type\" content=\"${escape(type)}\">\n")
        out.append("<title>").append(escape(title)).append("</title>\n")
        out.append("<style>\n").append(CSS).append("\n</style>\n")
        out.append("</head>\n<body>\n")
        out.append("<header class=\"head\">\n")
        out.append("<div class=\"head-text\">")
        out.append("<h1>").append(escape(title)).append("</h1>")
        if (!subtitle.isNullOrBlank()) {
            out.append("<p class=\"subtitle\">").append(escape(subtitle)).append("</p>")
        }
        out.append("</div>\n")
        out.append(
            "<button class=\"theme\" type=\"button\" aria-label=\"Тема\" " +
                "onclick=\"rcTheme()\">☾</button>\n",
        )
        out.append("</header>\n<main>\n")
        out.body()
        out.append("</main>\n")
        out.append("<footer>").append(escape(footer)).append("</footer>\n")
        if (metadata.isNotEmpty()) {
            out.append("<script type=\"application/json\" id=\"report-metadata\">")
            val builder = StringBuilder()
            val writer = Json.Writer(builder)
            writer.beginObject()
            for ((name, value) in metadata) writer.field(name, value)
            writer.endObject()
            out.append(builder)
            out.append("</script>\n")
        }
        out.append("<script>\n").append(JS).append("\n</script>\n")
        out.append("</body>\n</html>\n")
        return out.toString()
    }

    /** Секция отчёта; пустые секции не рисуются вовсе (§18 ТЗ). */
    fun StringBuilder.section(title: String, body: StringBuilder.() -> Unit) {
        append("<section>\n<h2>").append(escape(title)).append("</h2>\n")
        body()
        append("</section>\n")
    }

    /** Крупные числа отчёта: значение и подпись под ним. */
    fun StringBuilder.hero(cells: List<Triple<String, String, String?>>) {
        if (cells.isEmpty()) return
        append("<div class=\"hero\">\n")
        for ((value, label, unit) in cells) {
            append("<div class=\"hero-cell\"><div class=\"hero-value\">")
            append(escape(value))
            if (!unit.isNullOrBlank()) {
                append(" <span class=\"hero-unit\">").append(escape(unit)).append("</span>")
            }
            append("</div><div class=\"hero-label\">").append(escape(label)).append("</div></div>\n")
        }
        append("</div>\n")
    }

    /** Таблица «название → значение»: настоящая `<table>`, а не сетка из div. */
    fun StringBuilder.facts(rows: List<Pair<String, String>>) {
        if (rows.isEmpty()) return
        append("<table class=\"facts\">\n<tbody>\n")
        for ((name, value) in rows) {
            append("<tr><th scope=\"row\">").append(escape(name))
            append("</th><td>").append(escape(value)).append("</td></tr>\n")
        }
        append("</tbody>\n</table>\n")
    }

    fun StringBuilder.note(text: String) {
        append("<p class=\"note\">").append(escape(text)).append("</p>\n")
    }

    /**
     * Оформление отчёта.
     *
     * Светлая тема по умолчанию — печатается она, а не экранная; тёмная
     * приходит от `prefers-color-scheme` и переключателем. Печать своя:
     * белый фон, без плавающих кнопок, разрывы страниц между разделами.
     *
     * `figure:fullscreen` и запасной `figure.rc-full` — график во весь экран:
     * шириной в ладонь он читается плохо, а поворот сам по себе его не
     * увеличивает. Настоящий полноэкранный режим браузера используется, когда
     * он есть; когда его нет, та же раскладка рисуется слоем поверх страницы.
     *
     * Пояснений ВНУТРИ стиля нет намеренно: комментарий уехал бы в отчёт на
     * любом языке — стиль один на русский и английский.
     */
    private val CSS = """
        :root {
          color-scheme: light dark;
          --bg: #ffffff;
          --surface: #f6f7f8;
          --ink: #14181c;
          --ink2: #4a5560;
          --muted: #7c8794;
          --line: #dfe3e7;
          --data: #0f766e;
          --warn: #b45309;
          --crit: #b91c1c;
        }
        html[data-theme="dark"] {
          --bg: #0f1418;
          --surface: #161d23;
          --ink: #e8edf2;
          --ink2: #aab6c2;
          --muted: #7c8794;
          --line: #263039;
          --data: #2dd4bf;
          --warn: #fbbf24;
          --crit: #f87171;
        }
        @media (prefers-color-scheme: dark) {
          html:not([data-theme="light"]) {
            --bg: #0f1418;
            --surface: #161d23;
            --ink: #e8edf2;
            --ink2: #aab6c2;
            --muted: #7c8794;
            --line: #263039;
            --data: #2dd4bf;
            --warn: #fbbf24;
            --crit: #f87171;
          }
        }
        * { box-sizing: border-box; }
        body {
          margin: 0 auto;
          padding: 24px 20px 48px;
          max-width: 900px;
          background: var(--bg);
          color: var(--ink);
          font: 16px/1.5 -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
        }
        .head { display: flex; align-items: flex-start; gap: 16px; }
        .head-text { flex: 1; }
        h1 { font-size: 24px; margin: 0 0 4px; }
        h2 {
          font-size: 13px; letter-spacing: .08em; text-transform: uppercase;
          color: var(--ink2); margin: 32px 0 8px; font-weight: 600;
        }
        .subtitle { margin: 0; color: var(--ink2); }
        .theme {
          border: 1px solid var(--line); background: var(--surface); color: var(--ink2);
          border-radius: 10px; width: 36px; height: 36px; font-size: 16px; cursor: pointer;
        }
        section { border-top: 1px solid var(--line); padding-top: 4px; }
        .hero { display: flex; flex-wrap: wrap; gap: 24px; margin: 12px 0 4px; }
        .hero-value { font-size: 26px; font-variant-numeric: tabular-nums; }
        .hero-unit { font-size: 14px; color: var(--muted); }
        .hero-label { font-size: 13px; color: var(--muted); }
        table { border-collapse: collapse; width: 100%; font-size: 14px; }
        th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--line); }
        th { color: var(--ink2); font-weight: 500; }
        td { font-variant-numeric: tabular-nums; }
        table.facts th { width: 45%; }
        .note { color: var(--muted); font-size: 13px; }
        figure { margin: 8px 0 0; }
        svg { width: 100%; height: auto; background: var(--surface); border-radius: 8px; }
        .controls { display: flex; gap: 8px; margin: 8px 0; flex-wrap: wrap; }
        .controls button {
          border: 1px solid var(--line); background: var(--surface); color: var(--ink2);
          border-radius: 8px; padding: 4px 10px; font-size: 13px; cursor: pointer;
        }
        .controls button[aria-pressed="true"] { color: var(--data); border-color: var(--data); }
        figure:fullscreen, figure.rc-full {
          background: var(--bg); margin: 0; padding: 16px;
          display: flex; flex-direction: column; justify-content: center;
        }
        figure.rc-full {
          position: fixed; inset: 0; z-index: 20; overflow: auto;
        }
        figure:fullscreen svg, figure.rc-full svg { max-height: 80vh; }
        .legend {
          display: flex; gap: 16px; flex-wrap: wrap;
          font-size: 13px; margin: 0 0 4px;
        }
        .readout {
          font-size: 13px; color: var(--ink2); min-height: 1.5em;
          font-variant-numeric: tabular-nums;
        }
        tr.peak { cursor: pointer; }
        tr.peak.active td, tr.peak.active th { background: var(--surface); color: var(--data); }
        footer { margin-top: 40px; color: var(--muted); font-size: 12px; }
        @media print {
          body { max-width: none; padding: 0; background: #fff; color: #000; }
          .theme, .controls { display: none; }
          section { break-inside: avoid; page-break-inside: avoid; }
          svg { background: #fff; }
        }
    """.trimIndent()

    /**
     * Скрипт отчёта — ровно то, без чего он становится картинкой: перекрестие
     * с подписью, переключатели вида и связь «пик ↔ строка таблицы».
     *
     * Весь движок графиков сюда не переносится: отчёт открывают, чтобы
     * посмотреть результат, а не листать историю (§17 ТЗ).
     */
    private val JS = """
        function rcTheme() {
          var root = document.documentElement;
          var current = root.getAttribute('data-theme');
          if (!current) {
            current = window.matchMedia &&
              window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
          }
          root.setAttribute('data-theme', current === 'dark' ? 'light' : 'dark');
        }
        function rcSetMode(id, mode) {
          var figure = document.getElementById(id);
          if (!figure) return;
          figure.querySelectorAll('[data-mode]').forEach(function (node) {
            node.style.display = node.getAttribute('data-mode') === mode ? '' : 'none';
          });
          figure.querySelectorAll('button[data-set-mode]').forEach(function (button) {
            button.setAttribute(
              'aria-pressed',
              button.getAttribute('data-set-mode') === mode ? 'true' : 'false'
            );
          });
        }
        function rcCursor(figure) {
          var svg = figure.querySelector('svg');
          var line = figure.querySelector('.cursor-line');
          var readout = figure.querySelector('.readout');
          if (!svg || !line) return;
          var points = JSON.parse(figure.getAttribute('data-points') || '[]');
          var labels = JSON.parse(figure.getAttribute('data-labels') || '[]');
          if (!points.length) return;
          var box = svg.viewBox.baseVal;
          function move(clientX) {
            var rect = svg.getBoundingClientRect();
            var x = (clientX - rect.left) / rect.width * box.width;
            var best = 0;
            for (var i = 1; i < points.length; i++) {
              if (Math.abs(points[i] - x) < Math.abs(points[best] - x)) best = i;
            }
            line.setAttribute('x1', points[best]);
            line.setAttribute('x2', points[best]);
            line.style.display = '';
            if (readout) readout.textContent = labels[best] || '';
            var rows = figure.parentElement.querySelectorAll('tr.peak');
            rows.forEach(function (row) { row.classList.remove('active'); });
          }
          svg.addEventListener('mousemove', function (e) { move(e.clientX); });
          svg.addEventListener('touchstart', function (e) { move(e.touches[0].clientX); });
          svg.addEventListener('touchmove', function (e) { move(e.touches[0].clientX); });
          svg.addEventListener('mouseleave', function () {
            line.style.display = 'none';
            if (readout) readout.textContent = '';
          });
        }
        function rcPeaks(figureId) {
          var figure = document.getElementById(figureId);
          if (!figure) return;
          var rows = document.querySelectorAll('tr.peak[data-figure="' + figureId + '"]');
          rows.forEach(function (row) {
            row.addEventListener('click', function () {
              rows.forEach(function (other) { other.classList.remove('active'); });
              row.classList.add('active');
              var marker = figure.querySelector('[data-peak="' + row.getAttribute('data-peak') + '"]');
              figure.querySelectorAll('[data-peak]').forEach(function (node) {
                node.setAttribute('opacity', '0.35');
              });
              if (marker) marker.setAttribute('opacity', '1');
              var readout = figure.querySelector('.readout');
              if (readout) readout.textContent = row.getAttribute('data-readout') || '';
            });
          });
          figure.querySelectorAll('[data-peak]').forEach(function (marker) {
            marker.addEventListener('click', function () {
              var key = marker.getAttribute('data-peak');
              rows.forEach(function (row) {
                row.classList.toggle('active', row.getAttribute('data-peak') === key);
              });
            });
          });
        }
        function rcExpand(id) {
          var figure = document.getElementById(id);
          if (!figure) return;
          if (document.fullscreenElement === figure) {
            document.exitFullscreen();
            return;
          }
          if (figure.classList.contains('rc-full')) {
            figure.classList.remove('rc-full');
            return;
          }
          if (figure.requestFullscreen) {
            figure.requestFullscreen().catch(function () {
              figure.classList.add('rc-full');
            });
          } else {
            figure.classList.add('rc-full');
          }
        }
        document.addEventListener('keydown', function (e) {
          if (e.key !== 'Escape') return;
          document.querySelectorAll('figure.rc-full').forEach(function (figure) {
            figure.classList.remove('rc-full');
          });
        });
        document.querySelectorAll('figure[data-points]').forEach(rcCursor);
        document.querySelectorAll('figure[data-peaks="1"]').forEach(function (figure) {
          rcPeaks(figure.id);
        });
    """.trimIndent()
}
