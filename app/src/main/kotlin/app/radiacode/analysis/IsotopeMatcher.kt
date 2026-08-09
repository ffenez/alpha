package app.radiacode.analysis

import kotlin.math.abs
import kotlin.math.max

/** Deliberately capped at «средняя» — one spectrum never proves an isotope. */
enum class HintConfidence { LOW, MEDIUM }

/** A cautious isotope suggestion for one detected peak. */
data class IsotopeHint(
    val isotope: String,
    /** Parent chain for daughter nuclides (Ra-226 / Th-232), else null. */
    val chain: String?,
    val natural: Boolean,
    val peak: Peak,
    val lineEnergyKeV: Float,
    val confidence: HintConfidence,
    /** Other library isotopes whose lines also fit this peak. */
    val alternatives: List<String>,
)

/**
 * Matches detected peaks against [GammaLineLibrary] — cautiously, per SPEC:
 * the output is always a «возможное совпадение», never a detection.
 *
 * Tolerance is FWHM-aware: a line matches a peak within
 * max(2 % of the line energy, half the detector FWHM at that energy).
 *
 * Confidence (LOW/MEDIUM only):
 *  - MEDIUM needs a strong peak (SNR ≥ [MEDIUM_MIN_SNR]), a tight energy fit
 *    (within max(1 %, FWHM/4)) and, for isotopes with several library lines
 *    (Co-60, Bi-214, Tl-208), at least two of their lines matched somewhere
 *    in the spectrum — a lone 1173 keV bump is not «Co-60, medium»;
 *  - everything else is LOW.
 */
object IsotopeMatcher {

    /** Analysis below this accumulation is noise-reading (screen gates on it). */
    const val MIN_ANALYSIS_SECONDS = 60L

    const val MEDIUM_MIN_SNR = 8f

    fun toleranceKeV(lineEnergyKeV: Float): Float =
        max(0.02f * lineEnergyKeV, 0.5f * PeakDetection.fwhmKeV(lineEnergyKeV))

    private fun tightToleranceKeV(lineEnergyKeV: Float): Float =
        max(0.01f * lineEnergyKeV, 0.25f * PeakDetection.fwhmKeV(lineEnergyKeV))

    fun match(peaks: List<Peak>): List<IsotopeHint> {
        // Which lines of each isotope found any peak (for multi-line support).
        val matchedLineCount = mutableMapOf<String, MutableSet<Float>>()
        for (peak in peaks) {
            for (line in GammaLineLibrary.LINES) {
                if (abs(peak.energyKeV - line.energyKeV) <= toleranceKeV(line.energyKeV)) {
                    matchedLineCount.getOrPut(line.isotope) { mutableSetOf() } += line.energyKeV
                }
            }
        }

        val hints = mutableListOf<IsotopeHint>()
        for (peak in peaks) {
            val fitting = GammaLineLibrary.LINES
                .filter { abs(peak.energyKeV - it.energyKeV) <= toleranceKeV(it.energyKeV) }
                .sortedBy { abs(peak.energyKeV - it.energyKeV) }
            val primary = fitting.firstOrNull() ?: continue

            val delta = abs(peak.energyKeV - primary.energyKeV)
            val multiLine = GammaLineLibrary.linesOf(primary.isotope).size > 1
            val supported = !multiLine ||
                (matchedLineCount[primary.isotope]?.size ?: 0) >= 2
            val confidence = if (
                peak.snr >= MEDIUM_MIN_SNR &&
                delta <= tightToleranceKeV(primary.energyKeV) &&
                supported
            ) {
                HintConfidence.MEDIUM
            } else {
                HintConfidence.LOW
            }

            hints += IsotopeHint(
                isotope = primary.isotope,
                chain = primary.chain,
                natural = primary.natural,
                peak = peak,
                lineEnergyKeV = primary.energyKeV,
                confidence = confidence,
                alternatives = fitting.drop(1).map { it.isotope }.distinct(),
            )
        }

        // One hint per isotope (Co-60 with both lines is still one suggestion),
        // strongest peak first.
        return hints
            .groupBy { it.isotope }
            .map { (_, group) -> group.maxBy { it.peak.snr } }
            .sortedByDescending { it.peak.snr }
    }
}
