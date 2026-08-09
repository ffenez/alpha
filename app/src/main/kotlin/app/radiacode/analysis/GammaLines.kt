package app.radiacode.analysis

/**
 * One gamma line of the built-in library. [chain] names the decay chain for
 * daughter nuclides (Bi-214/Pb-214 → Ra-226, Pb-212/Tl-208 → Th-232) so hints
 * can say honestly which parent the line points to.
 */
data class GammaLine(
    val isotope: String,
    val chain: String?,
    val energyKeV: Float,
    /** Present in the undisturbed environment (K-40, radon/thorium chains). */
    val natural: Boolean,
)

/**
 * Small built-in gamma-line library for cautious isotope hints (SPEC:
 * «библиотека изотопов», «отображение известных гамма-линий»).
 *
 * Energies are the principal decay gamma lines in keV, rounded to 0.1 keV,
 * from the IAEA Live Chart of Nuclides / NNDC NuDat 3 decay-radiation data:
 *  - K-40 1460.8 (10.7% BR) — natural, in every concrete wall and banana;
 *  - Cs-137 661.7 (via Ba-137m);
 *  - Co-60 1173.2 + 1332.5 (always emitted together — a lone line is weak
 *    evidence, the matcher requires both for medium confidence);
 *  - I-131 364.5 (medical isotope, 8 d half-life);
 *  - Ra-226 chain daughters: Bi-214 609.3 / 1120.3 / 1764.5, Pb-214 351.9
 *    (radon washout after rain is the classic source);
 *  - Th-232 chain daughters: Pb-212 238.6, Tl-208 583.2 / 2614.5;
 *  - Am-241 59.5 (smoke detectors).
 *
 * All lines are well above the RC-110 acquisition threshold (~20 keV), but
 * note that at 59.5 keV (Am-241) the CsI(Tl) response and factory energy
 * calibration are least accurate, so low-energy matches deserve extra doubt.
 */
object GammaLineLibrary {

    val LINES: List<GammaLine> = listOf(
        GammaLine("Am-241", null, 59.5f, natural = false),
        GammaLine("Pb-212", "Th-232", 238.6f, natural = true),
        GammaLine("Pb-214", "Ra-226", 351.9f, natural = true),
        GammaLine("I-131", null, 364.5f, natural = false),
        GammaLine("Tl-208", "Th-232", 583.2f, natural = true),
        GammaLine("Bi-214", "Ra-226", 609.3f, natural = true),
        GammaLine("Cs-137", null, 661.7f, natural = false),
        GammaLine("Bi-214", "Ra-226", 1120.3f, natural = true),
        GammaLine("Co-60", null, 1173.2f, natural = false),
        GammaLine("Co-60", null, 1332.5f, natural = false),
        GammaLine("K-40", null, 1460.8f, natural = true),
        GammaLine("Bi-214", "Ra-226", 1764.5f, natural = true),
        GammaLine("Tl-208", "Th-232", 2614.5f, natural = true),
    )

    fun linesOf(isotope: String): List<GammaLine> = LINES.filter { it.isotope == isotope }
}
