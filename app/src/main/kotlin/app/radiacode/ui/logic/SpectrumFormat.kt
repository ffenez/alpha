package app.radiacode.ui.logic

import app.radiacode.analysis.EnergyWindow
import app.radiacode.analysis.EnergyWindowSpec
import app.radiacode.analysis.EnergyWindows
import app.radiacode.analysis.HintConfidence
import app.radiacode.analysis.IsotopeHint
import app.radiacode.ui.text.SpectrumRu
import app.radiacode.ui.text.SpectrumStrings
import java.util.Locale
import kotlin.math.roundToInt

/** Pure formatting for the Спектр screen. JVM-tested. */
object SpectrumFormat {

    /** Accumulation clock: «04:32», hours as «1:07:09». */
    fun accumulationClock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = s % 3600 / 60
        val secs = s % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    /** Visible-range indicator: «0–3072 кэВ». */
    fun windowLabel(window: EnergyWindow, s: SpectrumStrings = SpectrumRu): String =
        "${window.startKeV.roundToInt()}–${window.endKeV.roundToInt()} ${s.unitKeV}"

    // --- спектральные диапазоны (спец §7) ---

    /** Ячейка диапазона: «100–300» (кэВ названы в шапке колонки). */
    fun rangeLabel(spec: EnergyWindowSpec): String =
        "${spec.startKeV.roundToInt()}–${spec.endKeV.roundToInt()}"

    /**
     * Скорость счёта с её пуассоновской неопределённостью: «12,71 ± 0,04».
     * σ здесь — стандартная неопределённость СКОРОСТИ СЧЁТА, σ_R = √C/t
     * ([EnergyWindows]), поэтому в шапке колонки стоит «с⁻¹ ± σ», а не «±Σ»:
     * Σ читается как сумма и величиной не является.
     */
    fun rangeRate(window: EnergyWindows.WindowResult): String =
        "${ExperimentFormat.decimal(window.rateCps)} ± ${ExperimentFormat.decimal(window.sigmaCps)}"

    /** Доля диапазона в спектре: «52 %». */
    fun rangeShare(window: EnergyWindows.WindowResult): String =
        "${(window.fraction * 100).roundToInt()} %"

    /** Спектральное отношение со своей неопределённостью: «17,15 ± 0,04». */
    fun ratioValue(index: EnergyWindows.SpectralIndex): String =
        "${ExperimentFormat.decimal(index.value)} ± ${ExperimentFormat.decimal(index.sigma)}"

    /** Отношение без неопределённости — для свёрнутой сводки блока: «17,15». */
    fun ratioShort(index: EnergyWindows.SpectralIndex): String =
        ExperimentFormat.decimal(index.value)

    /** Реально покрытый диапазон каналов: «99,8–299,5». */
    fun rangeCovered(window: EnergyWindows.WindowResult): String =
        "${oneDecimal(window.coveredStartKeV)}–${oneDecimal(window.coveredEndKeV)}"

    private fun oneDecimal(value: Float): String =
        String.format(Locale.US, "%.1f", value).replace('.', ',')

    @Deprecated("Экран читает движок доказательств: см. matchCell/matchNotes")
    fun confidenceLabel(
        confidence: HintConfidence,
        s: SpectrumStrings = SpectrumRu,
    ): String = when (confidence) {
        HintConfidence.LOW -> s.confidenceLow
        HintConfidence.MEDIUM -> s.confidenceMedium
    }

    /** «также похоже: I-131» — alternative candidates for the same peak. */
    @Deprecated("Экран читает движок доказательств: см. matchNotes")
    fun hintAlternatives(hint: IsotopeHint, s: SpectrumStrings = SpectrumRu): String? =
        if (hint.alternatives.isEmpty()) {
            null
        } else {
            s.alsoResembles(hint.alternatives.joinToString(", "))
        }

    /** Peak-table energy cell: «661,9» (keV, one decimal, comma). */
    fun energyCell(energyKeV: Float): String =
        String.format(Locale.US, "%.1f", energyKeV).replace('.', ',')

    /** Peak-table net-counts cell: «1 240» (rounded, thousands spaced). */
    fun netCell(netCounts: Float): String = groupThousands(netCounts.roundToInt().toLong())

    /** Ячейка значимости в таблице пиков: «8,2σ». */
    /**
     * Значимость нетто-площади в единицах её собственной σ. «σ» здесь имеет
     * определённый смысл (см. `PeakDetection`), поэтому и величина названа
     * значимостью, а не «SNR»: signal-to-noise не говорит, что в знаменателе.
     */
    fun significanceCell(significance: Float): String =
        String.format(Locale.US, "%.1f", significance).replace('.', ',') + "σ"

    /**
     * Peak-table candidate cell, cautious per SPEC — never «обнаружен»:
     * natural lines read «Bi-214 · природный», the rest carry their
     * confidence: «Cs-137 · средняя ур.».
     */
    @Suppress("DEPRECATION")
    @Deprecated("Экран читает движок доказательств: см. matchCell")
    fun candidateCell(hint: IsotopeHint, s: SpectrumStrings = SpectrumRu): String =
        if (hint.natural) {
            s.candidateNatural(hint.isotope)
        } else {
            s.candidateConfidence(hint.isotope, confidenceLabel(hint.confidence, s))
        }

    /**
     * Ячейка «возможное совпадение» из вердикта движка (ADR 006). Текст никогда
     * не утверждает обнаружение: заголовок колонки уже говорит «возможное», а
     * кандидат носит счёт совпавших линий вместо шкалы уверенности.
     */
    fun matchCell(match: PeakMatch, s: SpectrumStrings = SpectrumRu): String = when (match) {
        PeakMatch.None -> "—"
        // Противоречащий кандидат в колонке НЕ показывается — прочерк,
        // пометка уходит в детали строки ([matchNotes]).
        is PeakMatch.Contradicted -> "—"
        is PeakMatch.AmbiguousGroup -> match.nuclides.joinToString(" / ")
        is PeakMatch.Candidate ->
            if (match.natural) {
                s.candidateNatural(match.nuclide)
            } else {
                s.candidateLines(match.nuclide, match.matchedLines)
            }
        is PeakMatch.Artifact -> when (match.kind) {
            ArtifactKind.ANNIHILATION -> s.artifactAnnihilation
            ArtifactKind.SINGLE_ESCAPE, ArtifactKind.DOUBLE_ESCAPE -> s.artifactEscape
            ArtifactKind.SUM -> s.artifactSum
            ArtifactKind.BACKSCATTER -> s.artifactBackscatter
        }
    }

    /**
     * Детали строки пика: пометка противоречия, группа неразрешимости или
     * механизм артефакта. Пустой список — деталей нет.
     */
    fun matchNotes(match: PeakMatch, s: SpectrumStrings = SpectrumRu): List<String> =
        when (match) {
            PeakMatch.None -> emptyList()
            is PeakMatch.Contradicted ->
                listOf(s.contradictedNote(match.nuclides.joinToString(" / ")))
            is PeakMatch.AmbiguousGroup ->
                listOf(s.ambiguityNote(match.nuclides.joinToString(" / ")))
            is PeakMatch.Candidate ->
                if (match.rivals.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        s.ambiguityNote(
                            (listOf(match.nuclide) + match.rivals).joinToString(" / "),
                        ),
                    )
                }
            is PeakMatch.Artifact -> buildList {
                add(
                    when (match.kind) {
                        ArtifactKind.ANNIHILATION -> s.artifactAnnihilationNote
                        ArtifactKind.SINGLE_ESCAPE ->
                            s.artifactEscapeNote(energyCell(match.parentKeV ?: 0f), "511")
                        ArtifactKind.DOUBLE_ESCAPE ->
                            s.artifactEscapeNote(energyCell(match.parentKeV ?: 0f), "1022")
                        ArtifactKind.SUM -> s.artifactSumNote(
                            energyCell(match.sumFirstKeV ?: 0f),
                            energyCell(match.sumSecondKeV ?: 0f),
                            match.cascadeNuclide.orEmpty(),
                        )
                        ArtifactKind.BACKSCATTER -> s.artifactBackscatterNote
                    },
                )
                if (match.compatibleNuclides.isNotEmpty()) {
                    add(s.artifactCompatibleNote(match.compatibleNuclides.joinToString(", ")))
                }
            }
        }

    /** Header chip: «Δt 12:34 · 184 302 имп». */
    fun accumulationChip(
        durationSeconds: Long,
        totalCounts: Long,
        s: SpectrumStrings = SpectrumRu,
    ): String =
        "Δt ${accumulationClock(durationSeconds)} · ${groupThousands(totalCounts)} ${s.unitCounts}"

    /**
     * Calibration footnote: «калибровка: E = −5,6 + 2,41·ch + 4,1·10⁻⁴·ch² ·
     * 1024 канала». Coefficients as the device reports them; a2 in
     * superscript scientific notation.
     */
    fun calibrationLine(
        a0: Float,
        a1: Float,
        a2: Float,
        channelCount: Int,
        s: SpectrumStrings = SpectrumRu,
    ): String {
        val a0Text = String.format(Locale.US, "%.1f", a0)
            .replace('.', ',').replace("-", "−")
        val a1Term = signedTerm(a1, String.format(Locale.US, "%.2f", Math.abs(a1)))
        val a2Term = signedTerm(a2, scientific(Math.abs(a2).toDouble()))
        return s.calibrationLine("$a0Text$a1Term·ch$a2Term·ch²", s.channels(channelCount))
    }

    private fun signedTerm(value: Float, absText: String): String =
        (if (value < 0f) " − " else " + ") + absText.replace('.', ',')

    /** 4.1e-4 → «4,1·10⁻⁴». */
    private fun scientific(value: Double): String {
        if (value == 0.0) return "0"
        val exponent = Math.floor(Math.log10(value)).toInt()
        val mantissa = value / Math.pow(10.0, exponent.toDouble())
        val mantissaText = String.format(Locale.US, "%.1f", mantissa).replace('.', ',')
        return "$mantissaText·10${superscript(exponent)}"
    }

    private val SUPERSCRIPTS = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻',
    )

    private fun superscript(exponent: Int): String =
        exponent.toString().map { SUPERSCRIPTS.getValue(it) }.joinToString("")

    /** Thousands grouped with a space: 184302 → «184 302». */
    fun groupThousands(value: Long): String {
        val digits = value.toString()
        val sb = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && char.isDigit() && (digits.length - index) % 3 == 0) sb.append(' ')
            sb.append(char)
        }
        return sb.toString()
    }
}
