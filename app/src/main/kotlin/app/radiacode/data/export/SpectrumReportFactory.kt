package app.radiacode.data.export

import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.HintConfidence
import app.radiacode.analysis.IsotopeMatcher
import app.radiacode.analysis.PeakDetection
import app.radiacode.data.SpectrumBlob
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.data.export.html.ReportEn
import app.radiacode.data.export.html.ReportPeak
import app.radiacode.data.export.html.ReportRu
import app.radiacode.data.export.html.ReportStrings
import app.radiacode.data.export.html.SpectrumReport
import app.radiacode.ui.text.AppLanguage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Строка базы → отчёт о спектре.
 *
 * ## Почему отдельно от отчёта
 *
 * Отчёт знает только числа и слова; о таблицах и о том, как ищутся пики, он не
 * знает ничего. Здесь — переход между этими мирами: калибровка, поиск пиков
 * ТЕМ ЖЕ методом, что на экране, и подписи на языке интерфейса.
 *
 * ## Одни числа на экране и в отчёте
 *
 * Пики ищет `PeakDetection`, совпадения предлагает `IsotopeMatcher` — те же,
 * что рисуют таблицу пиков в приложении. Иначе отчёт и экран разошлись бы, и
 * человек получил бы два ответа на один вопрос.
 */
object SpectrumReportFactory {

    private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy · HH:mm")
    private val STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun build(
        entity: SpectrumSnapshotEntity,
        appName: String,
        appVersion: String,
        language: AppLanguage = AppLanguage.RU,
        resolution662: Float = PeakDetection.RESOLUTION_662,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): SpectrumReport {
        val strings: ReportStrings = if (language == AppLanguage.EN) ReportEn else ReportRu
        val counts = SpectrumBlob.decode(entity.counts)
        val calibration = EnergyCalibration(entity.a0, entity.a1, entity.a2)
        val peaks = PeakDetection.detect(
            counts = counts,
            calibration = calibration,
            resolution662 = resolution662,
        )
        val hints = IsotopeMatcher.match(peaks, resolution662).associateBy { it.peak.energyKeV }

        return SpectrumReport(
            title = SpectrumExport.title(entity),
            subtitle = DATE.withZone(zone).format(Instant.ofEpochMilli(entity.timestamp)),
            durationText = duration(entity.durationSeconds),
            totalCounts = counts.sumOf { it.toLong() },
            counts = counts,
            energies = counts.indices.map { calibration.energyAt(it.toFloat()).toDouble() },
            peaks = peaks.map { peak ->
                val hint = hints[peak.energyKeV]
                ReportPeak(
                    energyKeV = peak.energyKeV.toDouble(),
                    netCounts = peak.netCounts.toDouble(),
                    significance = peak.significance.toDouble(),
                    candidate = hint?.isotope,
                    confidence = hint?.let { confidence(it.confidence, language) },
                )
            },
            details = details(entity, language),
            // Оговорки едут вместе с числами: без них таблица пиков читается
            // как список найденных веществ.
            notes = SpectrumExport.metadataLines(entity, appVersion),
            footer = strings.madeBy(
                appName,
                appVersion,
                STAMP.withZone(zone).format(Instant.ofEpochMilli(nowMillis)),
            ),
            strings = strings,
        )
    }

    private fun details(
        entity: SpectrumSnapshotEntity,
        language: AppLanguage,
    ): List<Pair<String, String>> {
        val ru = language != AppLanguage.EN
        return buildList {
            entity.deviceSerial?.let {
                add((if (ru) "Прибор" else "Instrument") to SpectrumExport.modelFromSerial(it))
            }
            entity.firmware?.let { add((if (ru) "Прошивка" else "Firmware") to it) }
            add(
                (if (ru) "Накопление" else "Accumulation") to
                    duration(entity.durationSeconds),
            )
            add(
                (if (ru) "Каналов" else "Channels") to entity.channelCount.toString(),
            )
            add(
                (if (ru) "Калибровка" else "Calibration") to
                    String.format(
                        Locale.US,
                        "E = %.3f + %.4f·ch + %.3e·ch²",
                        entity.a0,
                        entity.a1,
                        entity.a2,
                    ),
            )
        }
    }

    private fun confidence(confidence: HintConfidence, language: AppLanguage): String {
        val ru = language != AppLanguage.EN
        return when (confidence) {
            HintConfidence.MEDIUM -> if (ru) "средняя" else "medium"
            HintConfidence.LOW -> if (ru) "низкая" else "low"
        }
    }

    /** Накопление как его показывает прибор: «126:47:03». */
    fun duration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val rest = seconds % 60
        return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
    }

    /**
     * Спектр как таблица «канал, энергия, отсчёты» — простейший формат для
     * анализа в чужой программе (§24 ТЗ). Энергия считается ТОЙ ЖЕ
     * калибровкой, что и на экране: иначе таблица описывала бы другой спектр.
     */
    fun toCsv(entity: SpectrumSnapshotEntity): String {
        val counts = SpectrumBlob.decode(entity.counts)
        val calibration = EnergyCalibration(entity.a0, entity.a1, entity.a2)
        val out = StringBuilder(counts.size * 20)
        out.append("channel,energy_keV,counts\n")
        for ((channel, value) in counts.withIndex()) {
            out.append(channel).append(',')
                .append(String.format(Locale.US, "%.2f", calibration.energyAt(channel.toFloat())))
                .append(',').append(value).append('\n')
        }
        return out.toString()
    }
}
