package app.alpha.analysis

import app.alpha.ui.logic.PeakEvidenceBridge
import app.alpha.ui.logic.PeakMatch
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Машиночитаемый отчёт валидации спектра (`SPECTRUM_VALIDATION.md` §16).
 *
 * Живёт в тестовом исходнике намеренно: это диагностический инструмент, а не
 * часть приложения, и в APK ему делать нечего. Отчёт содержит ровно те поля,
 * по которым расчёт воспроизводится; идентификаторов прибора в нём нет.
 */
object SpectrumValidationReport {

    fun of(
        counts: List<Int>,
        calibration: EnergyCalibration,
        liveSeconds: Long,
        resolution662: Float,
        minEnergyKeV: Float,
    ): String {
        val peaks = PeakDetection
            .detect(counts, calibration, PeakDetection.DEFAULT_MIN_SIGNIFICANCE, resolution662, minEnergyKeV)
            .sortedBy { it.energyKeV }
        val verdict = PeakEvidenceBridge.analyse(peaks, counts, calibration, resolution662)

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"spectrum\": {\n")
        sb.append("    \"channels\": ${counts.size},\n")
        sb.append("    \"liveSeconds\": $liveSeconds,\n")
        sb.append("    \"totalCounts\": ${counts.sumOf { it.toLong() }},\n")
        sb.append("    \"sha256\": \"${sha256(counts)}\",\n")
        sb.append("    \"backgroundSpectrum\": null,\n")
        sb.append("    \"edgeChannelExcluded\": true\n")
        sb.append("  },\n")
        sb.append("  \"calibration\": {\n")
        sb.append("    \"model\": \"E = a0 + a1*ch + a2*ch^2\",\n")
        sb.append("    \"a0\": ${calibration.a0}, \"a1\": ${calibration.a1}, \"a2\": ${calibration.a2},\n")
        sb.append("    \"resolution662\": $resolution662,\n")
        sb.append("    \"minEnergyKeV\": $minEnergyKeV\n")
        sb.append("  },\n")
        sb.append("  \"peaks\": [\n")
        peaks.forEachIndexed { index, peak ->
            val d = diagnostics(counts, calibration, peak, resolution662)
            sb.append("    {\n")
            sb.append("      \"channel\": ${peak.channel},\n")
            sb.append("      \"energyKeV\": ${peak.energyKeV},\n")
            sb.append("      \"windowFrom\": ${d.from}, \"windowTo\": ${d.to},\n")
            sb.append("      \"grossCounts\": ${d.gross},\n")
            sb.append("      \"continuumPerChannel\": ${d.continuum},\n")
            sb.append("      \"continuumSlopePerChannel\": ${d.slope},\n")
            sb.append("      \"sidebandChannels\": ${d.sidebandChannels},\n")
            sb.append("      \"netCounts\": ${peak.netCounts},\n")
            sb.append("      \"sigmaNet\": ${d.sigma},\n")
            sb.append("      \"significance\": ${peak.significance},\n")
            sb.append("      \"observedFwhmKeV\": ${peak.fwhmKeV},\n")
            sb.append("      \"expectedFwhmKeV\": ${PeakDetection.expectedFwhmKeV(peak.energyKeV, resolution662)},\n")
            sb.append("      \"match\": ${matchJson(verdict.rows.firstOrNull { it.peak === peak }?.match)}\n")
            sb.append("    }${if (index < peaks.size - 1) "," else ""}\n")
        }
        sb.append("  ],\n")
        sb.append("  \"candidates\": [\n")
        val checks = verdict.checks.values.sortedBy { it.nuclide }
        checks.forEachIndexed { index, check ->
            sb.append("    {\n")
            sb.append("      \"nuclide\": \"${check.nuclide}\",\n")
            sb.append("      \"classification\": \"${check.classification}\",\n")
            sb.append("      \"linesFound\": ${check.foundLines}, \"linesChecked\": ${check.lines.size},\n")
            sb.append("      \"ambiguousWith\": ${check.ambiguousWith.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},\n")
            sb.append("      \"lines\": [\n")
            check.lines.forEachIndexed { k, line ->
                sb.append("        {\"energyKeV\": ${line.energyKeV}, \"intensityPercent\": ${line.intensityPercent}, ")
                sb.append("\"measuredKeV\": ${line.measuredKeV}, \"observability\": \"${line.observability}\"}")
                sb.append(if (k < check.lines.size - 1) ",\n" else "\n")
            }
            sb.append("      ]\n")
            sb.append("    }${if (index < checks.size - 1) "," else ""}\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun matchJson(match: PeakMatch?): String = when (match) {
        null, PeakMatch.None -> "{\"kind\": \"none\"}"
        is PeakMatch.Artifact -> "{\"kind\": \"artifact\", \"artifact\": \"${match.kind}\", " +
            "\"compatible\": ${match.compatibleNuclides.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}}"
        is PeakMatch.Candidate -> "{\"kind\": \"candidate\", \"nuclide\": \"${match.nuclide}\", " +
            "\"classification\": \"${match.classification}\", \"matchedLines\": ${match.matchedLines}}"
        is PeakMatch.AmbiguousGroup -> "{\"kind\": \"ambiguous\", " +
            "\"nuclides\": ${match.nuclides.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}}"
        is PeakMatch.Contradicted -> "{\"kind\": \"contradicted\", " +
            "\"nuclides\": ${match.nuclides.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}}"
    }

    /** Диагностика окна пика — те же величины, что считает поиск. */
    data class Diagnostics(
        val from: Int,
        val to: Int,
        val gross: Double,
        val continuum: Double,
        val slope: Double,
        val sidebandChannels: Int,
        val sigma: Double,
    )

    fun diagnostics(
        counts: List<Int>,
        calibration: EnergyCalibration,
        peak: Peak,
        resolution662: Float,
    ): Diagnostics {
        val i = peak.channel
        val half = PeakDetection.halfWidthChannels(calibration, i, resolution662)
        val left = (i - 3 * half)..(i - half - 1)
        val right = (i + half + 1)..(i + 3 * half)
        val leftMean = left.sumOf { counts[it].toDouble() } / left.count()
        val rightMean = right.sumOf { counts[it].toDouble() } / right.count()
        val m = left.count() + right.count()
        val b = (leftMean + rightMean) / 2.0
        val gross = (i - half..i + half).sumOf { counts[it].toDouble() }
        val width = 2 * half + 1
        return Diagnostics(
            from = i - half,
            to = i + half,
            gross = gross,
            continuum = b,
            slope = (rightMean - leftMean) / (4.0 * half + 1.0),
            sidebandChannels = m,
            sigma = sqrt(gross + width.toDouble() * width * b / m),
        )
    }

    private fun sha256(counts: List<Int>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(counts.joinToString(",").toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
