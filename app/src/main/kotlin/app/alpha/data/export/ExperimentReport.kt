package app.alpha.data.export

import app.alpha.analysis.AbAnalysis
import app.alpha.analysis.AbExperiment
import app.alpha.analysis.AlgorithmVersions
import app.alpha.analysis.EnergyWindowSpec
import app.alpha.data.JsonMap
import app.alpha.data.db.ExperimentEntity
import app.alpha.ui.logic.ExperimentFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Plain-text report of an A/B experiment (spec §22: a derived conclusion has
 * to be exportable together with the parameters that produced it).
 *
 * The report is deliberately readable by a human first: it names the geometry
 * the user documented, every run with its live time, the statistic behind each
 * verdict, the algorithm versions, and the mandated warnings of spec §16. It
 * uses [ExperimentFormat] on purpose — the exported wording must be the same
 * vocabulary the screen shows, so the two cannot drift apart.
 *
 * JVM-tested; the screen only adds the SAF plumbing around it.
 */
object ExperimentReport {

    private val STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** Algorithms whose versions every A/B conclusion depends on. */
    val ALGORITHM_KEYS = listOf("ab_analysis", "energy_windows", "spectrum_compare")

    fun fileName(experiment: ExperimentEntity, zone: ZoneId = ZoneId.systemDefault()): String =
        "alpha-ab-" +
            Instant.ofEpochMilli(experiment.createdAt).atZone(zone).format(FILE_STAMP) + ".txt"

    fun render(
        experiment: ExperimentEntity,
        profileName: String?,
        runs: List<AbExperiment.RunData>,
        comparison: AbExperiment.Comparison?,
        windowSpecs: List<EnergyWindowSpec>,
        distance: List<AbExperiment.DistancePoint> = emptyList(),
        appVersion: String? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        appendLine("A/B ЭКСПЕРИМЕНТ · ${ExperimentFormat.kindLabel(experiment.kind)}")
        appendLine("создан: ${time(experiment.createdAt, zone)}")
        appendLine("профиль: ${profileName ?: "не выбран"}")
        appendLine("геометрия: ${experiment.geometry.ifBlank { "не описана" }}")
        if (experiment.note.isNotBlank()) appendLine("заметка: ${experiment.note}")
        appendLine("статус: ${ExperimentFormat.EXPERIMENTAL_BADGE}")
        appendLine()

        appendLine("ПАРАМЕТРЫ АНАЛИЗА")
        appendLine("версии алгоритмов: ${AlgorithmVersions.stamp(*ALGORITHM_KEYS.toTypedArray())}")
        appendLine(
            "версия алгоритма на момент создания эксперимента: ab_analysis v" +
                experiment.algorithmVersion,
        )
        appendLine(
            "энергетические окна, кэВ: " +
                windowSpecs.joinToString(", ") { ExperimentFormat.windowLabel(it) },
        )
        appendLine("нормализация: ${ProcessingMetadata.NORMALIZATION_RATE}")
        appendLine("фон: ${ProcessingMetadata.BACKGROUND_TIME_SCALED}")
        appendLine(
            "переключение статистики: пуассоновское отношение правдоподобия при счёте " +
                "< ${AbAnalysis.NORMAL_APPROX_MIN_COUNTS.toInt()} импульсов в прогоне, иначе " +
                "χ²-подобный z = нетто/σ",
        )
        appendLine(
            "пороги вердикта: |z| < ${AbAnalysis.Z_CHANGED.toInt()} — consistent, " +
                "${AbAnalysis.Z_CHANGED.toInt()}–${AbAnalysis.Z_STRONG.toInt()} — changed, " +
                "≥ ${AbAnalysis.Z_STRONG.toInt()} — strong evidence of change",
        )
        val params = JsonMap.decode(experiment.params)
        if (params.isNotEmpty()) {
            appendLine("сохранённые параметры: " + params.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        appendLine(ExperimentFormat.WINDOWS_EDGE_NOTE)
        appendLine()

        appendLine("ПРОГОНЫ")
        if (runs.isEmpty()) appendLine("нет прогонов")
        runs.forEachIndexed { index, run ->
            appendLine(runLine(experiment.kind, index, run, zone))
        }
        appendLine()

        if (comparison != null) {
            appendComparison(comparison)
        } else {
            appendLine("СРАВНЕНИЕ: нужно как минимум два завершённых прогона")
            appendLine()
        }

        if (distance.isNotEmpty()) {
            appendLine("СЕРИЯ ПО РАССТОЯНИЮ")
            appendLine("расстояние · нетто-скорость · 1/r² от первой точки")
            distance.forEach { point ->
                val predicted = point.inverseSquareCps
                    ?.let { ExperimentFormat.cps(it) } ?: "опорная точка"
                appendLine(
                    "  ${ExperimentFormat.distance(point.distanceCm)}: " +
                        ExperimentFormat.cpsWithSigma(point.netRateCps, point.sigmaCps) +
                        " · $predicted",
                )
            }
            appendLine(ExperimentFormat.DISTANCE_WARNING)
            appendLine()
        }

        if (experiment.kind == ExperimentEntity.KIND_SHIELDING) {
            appendLine(ExperimentFormat.SHIELDING_WARNING)
            appendLine()
        }

        appendLine(ExperimentFormat.EXPERIMENTAL_NOTE)
        appVersion?.let { appendLine("приложение: $it") }
    }

    private fun runLine(
        kind: String,
        index: Int,
        run: AbExperiment.RunData,
        zone: ZoneId,
    ): String = buildString {
        append(run.label)
        append(" · ").append(ExperimentFormat.runRoleLabel(kind, index))
        append(" · начало ").append(time(run.startedAt, zone))
        append(" · длительность ").append(ExperimentFormat.duration(run.durationSeconds))
        if (run.hasSpectrum) {
            append(" · спектр ").append(run.totalCounts).append(" имп")
        } else {
            append(" · спектр не записан")
        }
        run.doseStats?.let { stats ->
            append(" · мощность дозы ")
            append(ExperimentFormat.decimal(stats.meanMicroSvH))
            append(" ±").append(ExperimentFormat.decimal(stats.sdMicroSvH))
            append(" мкЗв/ч (n=").append(stats.sampleCount).append(")")
        }
        run.distanceCm?.let { append(" · расстояние ").append(ExperimentFormat.distance(it)) }
        run.shieldingNote?.takeIf { it.isNotBlank() }?.let { append(" · материал: ").append(it) }
        if (run.endedAt == null) append(" · ПРОГОН НЕ ЗАВЕРШЁН")
    }

    private fun StringBuilder.appendComparison(comparison: AbExperiment.Comparison) {
        appendLine("СРАВНЕНИЕ ${comparison.a.label} и ${comparison.b.label}")
        appendLine(
            "вердикт: ${ExperimentFormat.verdictLabel(comparison.verdict)} " +
                "(${ExperimentFormat.verdictToken(comparison.verdict)})",
        )
        comparison.doseRate?.let { dose ->
            appendLine(
                "мощность дозы: A ${ExperimentFormat.decimal(dose.a.meanMicroSvH)}, " +
                    "B ${ExperimentFormat.decimal(dose.b.meanMicroSvH)} мкЗв/ч, " +
                    "Δ ${ExperimentFormat.decimal(dose.diffMicroSvH)} " +
                    "±${ExperimentFormat.decimal(dose.diffSigmaMicroSvH)}, " +
                    "z ${ExperimentFormat.zLabel(dose.z)} → " +
                    "${ExperimentFormat.verdictLabel(dose.verdict)} " +
                    "(${ExperimentFormat.verdictToken(dose.verdict)}); " +
                    "неопределённость — оценка снизу: секундные показания коррелированы",
            )
        }
        comparison.totalCounts?.let { appendLine("полный счёт: " + countingLine(it)) }
        if (comparison.windows.isNotEmpty()) {
            appendLine("энергетические окна:")
            comparison.windows.forEach { appendLine("  " + countingLine(it)) }
        }
        comparison.spectrum?.let { spectrum ->
            appendLine(
                "полный спектр: каналов ${spectrum.channelsUsed}, " +
                    "deviance ${ExperimentFormat.decimal(spectrum.deviance)}, " +
                    "χ² ${ExperimentFormat.decimal(spectrum.chiSquare)}, " +
                    "ν ${spectrum.degreesOfFreedom}, " +
                    "z ${ExperimentFormat.zLabel(spectrum.z)} " +
                    "(${ExperimentFormat.methodLabel(spectrum.method)}) → " +
                    "${ExperimentFormat.verdictLabel(spectrum.verdict)} " +
                    "(${ExperimentFormat.verdictToken(spectrum.verdict)})",
            )
        }
        comparison.calibrationDeltaKeV?.let {
            appendLine("расхождение калибровок прогонов: ${ExperimentFormat.decimal(it.toDouble())} кэВ")
        }
        comparison.warnings.forEach { appendLine("предупреждение: $it") }
        appendLine()
    }

    private fun countingLine(comparison: AbAnalysis.Comparison): String =
        "${comparison.label}: A ${ExperimentFormat.cps(comparison.rateA)}, " +
            "B ${ExperimentFormat.cps(comparison.rateB)}, " +
            "нетто ${ExperimentFormat.signedCounts(comparison.net)} " +
            "±${ExperimentFormat.decimal(comparison.netSigma)} имп, " +
            "z ${ExperimentFormat.zLabel(comparison.z)} " +
            "(${ExperimentFormat.methodShort(comparison.method)}) → " +
            "${ExperimentFormat.verdictLabel(comparison.verdict)} " +
            "(${ExperimentFormat.verdictToken(comparison.verdict)})"

    private fun time(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(STAMP)
}
