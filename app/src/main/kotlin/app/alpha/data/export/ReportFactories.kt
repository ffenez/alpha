package app.alpha.data.export

import app.alpha.analysis.AbAnalysis
import app.alpha.analysis.AbExperiment
import app.alpha.analysis.Hardness
import app.alpha.data.SessionSummary
import app.alpha.data.db.ExperimentEntity
import app.alpha.data.db.SampleEntity
import app.alpha.data.db.TrackPointEntity
import app.alpha.data.export.backup.Json
import app.alpha.data.export.html.ComparisonReport
import app.alpha.data.export.html.ReportComparison
import app.alpha.data.export.html.ReportEn
import app.alpha.data.export.html.ReportEvent
import app.alpha.data.export.html.ReportRoutePoint
import app.alpha.data.export.html.ReportRu
import app.alpha.data.export.html.ReportRun
import app.alpha.data.export.html.ReportSeries
import app.alpha.data.export.html.ReportStrings
import app.alpha.data.export.html.RoutePrivacy
import app.alpha.data.export.html.RouteReport
import app.alpha.data.export.html.SessionReport
import app.alpha.device.DoseUnits
import app.alpha.ui.logic.RouteSummary
import app.alpha.ui.text.AppLanguage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Данные приложения → модели отчётов.
 *
 * Здесь и только здесь встречаются сущности базы и слова отчёта. Отчёты сами
 * не знают ни о таблицах, ни о единицах прибора: числа приходят сюда уже
 * переведёнными в те, что читает человек.
 */
object ReportFactories {

    private val DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy · HH:mm")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun strings(language: AppLanguage): ReportStrings =
        if (language == AppLanguage.EN) ReportEn else ReportRu

    /**
     * Отчёт о сессии.
     *
     * Ряды прореживаются уже в отчёте ([HtmlChart]), поэтому сюда приходят
     * измерения как есть — но только той сессии, а не всей истории.
     */
    fun session(
        summary: SessionSummary,
        samples: List<SampleEntity>,
        events: List<ReportEvent>,
        appName: String,
        appVersion: String,
        language: AppLanguage,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): SessionReport {
        val s = strings(language)
        val ru = language != AppLanguage.EN
        val timeLabel: (Long) -> String = { TIME.withZone(zone).format(Instant.ofEpochMilli(it)) }
        val stats = summary.stats
        val doseSeries = samples.map {
            it.timestamp to DoseUnits.rawToMicroSievertPerHour(it.doseRate).toDouble()
        }
        val countSeries = samples.map { it.timestamp to it.countRate.toDouble() }
        // Жёсткость считается по тем же двум числам, что и на экране; там, где
        // счёт слишком мал, она не определена — и точки просто нет.
        val hardnessSeries = samples.mapNotNull { sample ->
            val value = Hardness.of(
                doseRateMicroSvH = DoseUnits.rawToMicroSievertPerHour(sample.doseRate).toDouble(),
                countRate = sample.countRate.toDouble(),
                seconds = Hardness.MIN_COUNTS / sample.countRate.coerceAtLeast(0.01f).toDouble(),
            )?.value ?: return@mapNotNull null
            sample.timestamp to value
        }

        return SessionReport(
            title = summary.profileName ?: (if (ru) "Сессия" else "Session"),
            subtitle = rangeText(summary.startedAt, summary.endedAt, zone),
            heroCells = listOfNotNull(
                stats.avgDoseRateMicroSvH?.let {
                    Triple(number(it.toDouble(), 3, ru), s.average, if (ru) "мкЗв/ч" else "µSv/h")
                },
                if (stats.minDoseRateMicroSvH != null && stats.maxDoseRateMicroSvH != null) {
                    Triple(
                        "${number(stats.minDoseRateMicroSvH!!.toDouble(), 2, ru)}–" +
                            number(stats.maxDoseRateMicroSvH!!.toDouble(), 2, ru),
                        s.range,
                        null,
                    )
                } else {
                    null
                },
                Triple(
                    number(summary.doseMicroSv, 2, ru),
                    s.accumulatedDose,
                    if (ru) "мкЗв" else "µSv",
                ),
                Triple(count(stats.sampleCount.toLong()), s.measurements, null),
            ),
            series = listOfNotNull(
                ReportSeries(s.doseSection, if (ru) "мкЗв/ч" else "µSv/h", doseSeries),
                ReportSeries(s.countSection, if (ru) "имп/с" else "cps", countSeries),
                hardnessSeries.takeIf { it.isNotEmpty() }?.let {
                    ReportSeries(s.hardnessSection, if (ru) "(мкрем/ч)/(имп/с)" else "", it)
                },
            ),
            events = events,
            details = buildList {
                summary.profileName?.let { add((if (ru) "Профиль" else "Profile") to it) }
                add(
                    (if (ru) "Начало" else "Started") to
                        DATE_TIME.withZone(zone).format(Instant.ofEpochMilli(summary.startedAt)),
                )
                summary.endedAt?.let {
                    add(
                        (if (ru) "Конец" else "Ended") to
                            DATE_TIME.withZone(zone).format(Instant.ofEpochMilli(it)),
                    )
                }
            },
            notes = emptyList(),
            footer = s.madeBy(appName, appVersion, stamp(nowMillis, zone)),
            strings = s,
            timeLabel = timeLabel,
        )
    }

    /** Отчёт о маршруте; режим координат выбирает человек до выгрузки. */
    fun route(
        summary: RouteSummary,
        points: List<TrackPointEntity>,
        privacy: RoutePrivacy,
        appName: String,
        appVersion: String,
        language: AppLanguage,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): RouteReport {
        val s = strings(language)
        val ru = language != AppLanguage.EN
        return RouteReport(
            title = summary.name,
            subtitle = rangeText(summary.startedAt, summary.endedAt, zone),
            heroCells = listOfNotNull(
                summary.avgDoseMicroSvH?.let {
                    Triple(number(it.toDouble(), 3, ru), s.average, if (ru) "мкЗв/ч" else "µSv/h")
                },
                summary.maxDoseMicroSvH?.let {
                    Triple(number(it.toDouble(), 3, ru), s.maximum, null)
                },
                summary.distanceMeters?.let {
                    Triple(
                        if (it >= 1000) number(it / 1000, 2, ru) else number(it, 0, ru),
                        s.distance,
                        if (it >= 1000) (if (ru) "км" else "km") else (if (ru) "м" else "m"),
                    )
                },
                Triple(count(summary.measurementCount.toLong()), s.measurements, null),
            ),
            points = points.mapNotNull { point ->
                val value = point.doseRate?.let {
                    DoseUnits.rawToMicroSievertPerHour(it).toDouble()
                } ?: return@mapNotNull null
                ReportRoutePoint(
                    timestamp = point.timestamp,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    value = value,
                )
            },
            valueUnit = if (ru) "мкЗв/ч" else "µSv/h",
            privacy = privacy,
            details = buildList {
                add(
                    (if (ru) "Начало" else "Started") to
                        DATE_TIME.withZone(zone).format(Instant.ofEpochMilli(summary.startedAt)),
                )
                summary.endedAt?.let {
                    add(
                        (if (ru) "Конец" else "Ended") to
                            DATE_TIME.withZone(zone).format(Instant.ofEpochMilli(it)),
                    )
                }
                if (summary.interrupted) {
                    add((if (ru) "Запись" else "Recording") to (if (ru) "прервана" else "interrupted"))
                }
            },
            notes = emptyList(),
            footer = s.madeBy(appName, appVersion, stamp(nowMillis, zone)),
            strings = s,
            timeLabel = { TIME.withZone(zone).format(Instant.ofEpochMilli(it)) },
        )
    }

    /** Отчёт об опыте: геометрия, прогоны и сравнение — как на экране. */
    fun experiment(
        entity: ExperimentEntity,
        profileName: String?,
        runs: List<AbExperiment.RunData>,
        comparison: AbExperiment.Comparison?,
        verdictText: String,
        appName: String,
        appVersion: String,
        language: AppLanguage,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): app.alpha.data.export.html.ExperimentReport {
        val s = strings(language)
        val ru = language != AppLanguage.EN
        return app.alpha.data.export.html.ExperimentReport(
            title = if (ru) "Эксперимент" else "Experiment",
            subtitle = DATE_TIME.withZone(zone).format(Instant.ofEpochMilli(entity.createdAt)),
            verdictText = verdictText,
            geometry = buildList {
                if (entity.geometry.isNotBlank()) {
                    add((if (ru) "Описание" else "Description") to entity.geometry)
                }
                if (entity.placement.isNotBlank()) {
                    add((if (ru) "Размещение" else "Placement") to entity.placement)
                }
                if (entity.orientation.isNotBlank()) {
                    add((if (ru) "Ориентация" else "Orientation") to entity.orientation)
                }
                entity.distanceCm?.let {
                    add((if (ru) "Расстояние" else "Distance") to "$it ${if (ru) "см" else "cm"}")
                }
                if (entity.note.isNotBlank()) {
                    add((if (ru) "Заметка" else "Note") to entity.note)
                }
                profileName?.let { add((if (ru) "Профиль" else "Profile") to it) }
            },
            runs = runs.map { run ->
                ReportRun(
                    label = run.label,
                    timeText = TIME.withZone(zone).format(Instant.ofEpochMilli(run.startedAt)),
                    durationText = SpectrumReportFactory.duration(run.durationSeconds),
                    counts = run.totalCounts.takeIf { it > 0 },
                    rateText = run.counts?.let {
                        val seconds = run.durationSeconds.coerceAtLeast(1)
                        number(run.totalCounts.toDouble() / seconds, 1, ru) +
                            (if (ru) " имп/с" else " cps")
                    },
                    spectrum = run.counts,
                    energies = run.calibration?.let { calibration ->
                        run.counts?.indices?.map { calibration.energyAt(it.toFloat()).toDouble() }
                    },
                )
            },
            comparisons = buildList {
                comparison?.totalCounts?.let { add(comparisonRow(it, s, ru)) }
                comparison?.windows?.forEach { add(comparisonRow(it, s, ru)) }
            },
            details = buildList {
                add((if (ru) "Вид опыта" else "Kind") to entity.kind)
                add(
                    (if (ru) "Версия алгоритма" else "Algorithm version") to
                        entity.algorithmVersion.toString(),
                )
            },
            notes = comparison?.warnings.orEmpty(),
            footer = s.madeBy(appName, appVersion, stamp(nowMillis, zone)),
            strings = s,
        )
    }

    private fun comparisonRow(
        comparison: AbAnalysis.Comparison,
        s: ReportStrings,
        ru: Boolean,
    ) = ReportComparison(
        label = comparison.label,
        a = number(comparison.rateA, 2, ru) + (if (ru) " имп/с" else " cps"),
        b = number(comparison.rateB, 2, ru) + (if (ru) " имп/с" else " cps"),
        significance = number(comparison.z, 1, ru) + " σ",
        verdict = when (comparison.verdict) {
            AbAnalysis.Verdict.CONSISTENT -> if (ru) "различий не видно" else "no difference seen"
            AbAnalysis.Verdict.CHANGED -> if (ru) "различие есть" else "difference"
            AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE ->
                if (ru) "различие уверенное" else "strong difference"
        },
    )

    /**
     * Сводный отчёт по нескольким сессиям.
     *
     * Ряды переводятся во время ОТ НАЧАЛА своей записи: сравнивают ход
     * измерения, а календарные даты остаются в таблице, где они и нужны.
     */
    fun comparison(
        records: List<Pair<SessionSummary, List<SampleEntity>>>,
        appName: String,
        appVersion: String,
        language: AppLanguage,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): ComparisonReport {
        val s = strings(language)
        val ru = language != AppLanguage.EN
        val unit = if (ru) "мкЗв/ч" else "µSv/h"
        return ComparisonReport(
            title = if (ru) "Сравнение записей" else "Records compared",
            subtitle = if (ru) {
                "${records.size} записи · ${stamp(nowMillis, zone)}"
            } else {
                "${records.size} records · ${stamp(nowMillis, zone)}"
            },
            unit = unit,
            series = records.map { (summary, samples) ->
                ReportSeries(
                    title = label(summary, zone),
                    unit = unit,
                    points = samples.map { sample ->
                        val elapsed = (sample.timestamp - summary.startedAt) / 1000
                        elapsed to DoseUnits.rawToMicroSievertPerHour(sample.doseRate).toDouble()
                    },
                )
            },
            columns = listOf(
                if (ru) "Запись" else "Record",
                if (ru) "Начало" else "Started",
                s.duration,
                s.average,
                s.maximum,
                s.accumulatedDose,
            ),
            rows = records.map { (summary, _) ->
                val duration = ((summary.endedAt ?: nowMillis) - summary.startedAt) / 1000
                listOf(
                    label(summary, zone),
                    DATE_TIME.withZone(zone).format(Instant.ofEpochMilli(summary.startedAt)),
                    SpectrumReportFactory.duration(duration),
                    summary.stats.avgDoseRateMicroSvH?.let { number(it.toDouble(), 3, ru) } ?: "—",
                    summary.stats.maxDoseRateMicroSvH?.let { number(it.toDouble(), 3, ru) } ?: "—",
                    number(summary.doseMicroSv, 2, ru),
                )
            },
            details = emptyList(),
            notes = emptyList(),
            footer = s.madeBy(appName, appVersion, stamp(nowMillis, zone)),
            strings = s,
            elapsedLabel = { seconds -> elapsed(seconds.toLong(), ru) },
        )
    }

    private fun label(summary: SessionSummary, zone: ZoneId): String =
        summary.profileName?.takeIf { it.isNotBlank() }
            ?: DATE_TIME.withZone(zone).format(Instant.ofEpochMilli(summary.startedAt))

    /** «45 с», «12 мин», «2 ч 05 мин» — подпись оси, а не полная длительность. */
    private fun elapsed(seconds: Long, ru: Boolean): String = when {
        seconds < 90 -> "$seconds ${if (ru) "с" else "s"}"
        seconds < 3600 -> "${seconds / 60} ${if (ru) "мин" else "min"}"
        else -> String.format(
            Locale.US,
            "%d %s %02d %s",
            seconds / 3600,
            if (ru) "ч" else "h",
            seconds % 3600 / 60,
            if (ru) "мин" else "min",
        )
    }

    // --- машинные форматы -------------------------------------------------

    /** Сессия как JSON: сводка и ряд измерений в единицах человека. */
    fun sessionJson(summary: SessionSummary, samples: List<SampleEntity>): String {
        val out = StringBuilder(samples.size * 64 + 512)
        val w = Json.Writer(out)
        w.beginObject()
            .field("type", "alpha-session")
            .field("startedAt", summary.startedAt)
            .field("endedAt", summary.endedAt)
            .field("profile", summary.profileName)
            .field("measurements", summary.stats.sampleCount.toLong())
            .field("doseMicroSv", summary.doseMicroSv)
            .field("avgDoseRateMicroSvH", summary.stats.avgDoseRateMicroSvH?.toDouble())
            .field("minDoseRateMicroSvH", summary.stats.minDoseRateMicroSvH?.toDouble())
            .field("maxDoseRateMicroSvH", summary.stats.maxDoseRateMicroSvH?.toDouble())
            .name("samples")
        w.beginArray()
        for (sample in samples) {
            w.beginObject()
                .field("t", sample.timestamp)
                .field(
                    "doseRateMicroSvH",
                    DoseUnits.rawToMicroSievertPerHour(sample.doseRate).toDouble(),
                )
                .field("countRate", sample.countRate.toDouble())
                .endObject()
        }
        w.endArray()
        w.endObject()
        return out.toString()
    }

    /** Опыт как JSON: геометрия, прогоны и числа сравнения. */
    fun experimentJson(
        entity: ExperimentEntity,
        runs: List<AbExperiment.RunData>,
        comparison: AbExperiment.Comparison?,
    ): String {
        val out = StringBuilder(4096)
        val w = Json.Writer(out)
        w.beginObject()
            .field("type", "alpha-experiment")
            .field("kind", entity.kind)
            .field("createdAt", entity.createdAt)
            .field("geometry", entity.geometry)
            .field("placement", entity.placement)
            .field("orientation", entity.orientation)
            .field("distanceCm", entity.distanceCm)
            .field("note", entity.note)
            .field("algorithmVersion", entity.algorithmVersion)
            .name("runs")
        w.beginArray()
        for (run in runs) {
            w.beginObject()
                .field("label", run.label)
                .field("startedAt", run.startedAt)
                .field("endedAt", run.endedAt)
                .field("durationSeconds", run.durationSeconds)
                .field("totalCounts", run.totalCounts)
                .endObject()
        }
        w.endArray()
        if (comparison != null) {
            w.name("comparison")
            w.beginObject()
            val rows = listOfNotNull(comparison.totalCounts) + comparison.windows
            w.name("rows")
            w.beginArray()
            for (row in rows) {
                w.beginObject()
                    .field("label", row.label)
                    .field("rateA", row.rateA)
                    .field("rateB", row.rateB)
                    .field("net", row.net)
                    .field("netSigma", row.netSigma)
                    .field("z", row.z)
                    .field("verdict", row.verdict.name)
                    .endObject()
            }
            w.endArray()
            w.endObject()
        }
        w.endObject()
        return out.toString()
    }

    /** Опыт таблицей: одна строка на прогон. */
    fun experimentCsv(runs: List<AbExperiment.RunData>): String {
        val out = StringBuilder(256)
        out.append("run,started_ms,duration_s,total_counts,cps\n")
        for (run in runs) {
            val seconds = run.durationSeconds.coerceAtLeast(1)
            out.append(escapeCsv(run.label)).append(',')
                .append(run.startedAt).append(',')
                .append(run.durationSeconds).append(',')
                .append(run.totalCounts).append(',')
                .append(String.format(Locale.US, "%.3f", run.totalCounts.toDouble() / seconds))
                .append('\n')
        }
        return out.toString()
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(',') || value.contains('"')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun rangeText(from: Long, to: Long?, zone: ZoneId): String {
        val start = DATE_TIME.withZone(zone).format(Instant.ofEpochMilli(from))
        val end = to?.let { TIME.withZone(zone).format(Instant.ofEpochMilli(it)) }
        return if (end == null) start else "$start–$end"
    }

    private fun stamp(millis: Long, zone: ZoneId): String =
        STAMP.withZone(zone).format(Instant.ofEpochMilli(millis))

    /**
     * Число отчёта: разделитель дробной части задаёт язык отчёта, а не локаль
     * телефона — страницу читают там, где её открыли.
     */
    private fun number(value: Double, decimals: Int, comma: Boolean = true): String =
        String.format(Locale.US, "%.${decimals}f", value)
            .let { if (comma) it.replace('.', ',') else it }

    private fun count(value: Long): String =
        value.toString().reversed().chunked(3).joinToString(" ").reversed()
}
