package app.alpha.analysis

import app.alpha.analysis.evidence.DataSource
import app.alpha.ui.text.NuclideRu
import app.alpha.ui.text.NuclideStrings

/**
 * One gamma line of the reference card: energy, emission probability and where
 * both numbers came from.
 *
 * Provenance живёт в самой линии, а не в подписи внизу карточки: справка
 * обязана уметь сказать про КОНКРЕТНОЕ число, откуда оно и с какой
 * неопределённостью. Сейчас у всех линий источник [DataSource.ENSDF], а
 * неопределённости `null` — см. KDoc [GammaLine]: их в нашей выборке нет, и
 * выдавать отсутствие за ноль нельзя.
 */
data class NuclideGammaLine(
    val energyKeV: Float,
    /** Photons emitted per 100 decays **of this nuclide** (not of its parent). */
    val intensityPercent: Float,
    /** 1σ табличной энергии, кэВ; `null` — источник её не дал. */
    val energyUncertaintyKeV: Float? = null,
    /** 1σ выхода в процентных единицах; `null` — источник её не дал. */
    val intensityUncertaintyPercent: Float? = null,
    val source: DataSource = DataSource.ENSDF,
)

/** Whether the nuclide exists in the undisturbed environment. */
enum class NuclideOrigin { NATURAL, ARTIFICIAL }

/**
 * Reference data about one nuclide — a справочник entry, never a statement
 * that anything was detected (spec §12).
 */
data class Nuclide(
    /** Library symbol, identical to [GammaLine.isotope] («Cs-137»). */
    val symbol: String,
    val name: String,
    val halfLife: String,
    /** Decay modes with the branch that produces the listed photons. */
    val decay: String,
    val origin: NuclideOrigin,
    /** Parent chain for daughters (Ra-226 / Th-232), else null. */
    val chain: String?,
    /** Principal gamma lines, strongest first. */
    val lines: List<NuclideGammaLine>,
    /** Where this nuclide is ordinarily met — plain, non-alarming facts. */
    val everyday: String,
    /**
     * What would have to be seen *in addition* for a match to become an
     * argument. Spec §12: a single nearby peak is never enough.
     */
    val confirmation: String,
)

/**
 * Offline nuclide reference for the Спектр candidate cards (CLAUDE.md: no
 * network, everything is bundled).
 *
 * **Source for every number below**: IAEA Live Chart of Nuclides
 * (nds.iaea.org/relnsd/vcharthtml/VChartHTML.html) and the NNDC NuDat 3
 * decay-radiation dataset (www.nndc.bnl.gov/nudat3/), both evaluated from
 * ENSDF. Half-lives are quoted as evaluated; gamma energies are rounded to
 * 0.1 keV (matching [GammaLineLibrary]) and emission probabilities to 0.1 %
 * per decay of the nuclide named by the entry — for chain daughters that is
 * **not** the same as per decay of the parent, and each such entry says so.
 *
 * The card is reference information about a nuclide. It never says the
 * nuclide is present: what the app has is a peak whose energy is compatible
 * with a library line (spec §12), and the wording that carries this lives in
 * [app.alpha.ui.logic.NuclideCard].
 */
object NuclideInfoLibrary {

    /**
     * Справка на выбранном языке. Каталог приходит параметром, а не из
     * композиции: эту таблицу читают и тесты, куда `LocalStrings` не доходит.
     */
    fun all(s: NuclideStrings = NuclideRu): List<Nuclide> = listOf(
        Nuclide(
            symbol = "K-40",
            name = s.k40Name,
            halfLife = s.k40HalfLife,
            decay = s.k40Decay,
            origin = NuclideOrigin.NATURAL,
            chain = null,
            lines = listOf(NuclideGammaLine(1460.8f, 10.7f)),
            everyday = s.k40Everyday,
            confirmation = s.k40Confirmation,
        ),
        Nuclide(
            symbol = "Cs-137",
            name = s.cs137Name,
            halfLife = s.cs137HalfLife,
            decay = s.cs137Decay,
            origin = NuclideOrigin.ARTIFICIAL,
            chain = null,
            lines = listOf(NuclideGammaLine(661.7f, 85.1f)),
            everyday = s.cs137Everyday,
            confirmation = s.cs137Confirmation,
        ),
        Nuclide(
            symbol = "Co-60",
            name = s.co60Name,
            halfLife = s.co60HalfLife,
            decay = s.co60Decay,
            origin = NuclideOrigin.ARTIFICIAL,
            chain = null,
            lines = listOf(
                NuclideGammaLine(1332.5f, 100.0f),
                NuclideGammaLine(1173.2f, 99.9f),
            ),
            everyday = s.co60Everyday,
            confirmation = s.co60Confirmation,
        ),
        Nuclide(
            symbol = "I-131",
            name = s.i131Name,
            halfLife = s.i131HalfLife,
            decay = s.i131Decay,
            origin = NuclideOrigin.ARTIFICIAL,
            chain = null,
            lines = listOf(
                NuclideGammaLine(364.5f, 81.5f),
                NuclideGammaLine(637.0f, 7.2f),
                NuclideGammaLine(284.3f, 6.1f),
            ),
            everyday = s.i131Everyday,
            confirmation = s.i131Confirmation,
        ),
        Nuclide(
            symbol = "Am-241",
            name = s.am241Name,
            halfLife = s.am241HalfLife,
            decay = s.am241Decay,
            origin = NuclideOrigin.ARTIFICIAL,
            chain = null,
            lines = listOf(NuclideGammaLine(59.5f, 35.9f)),
            everyday = s.am241Everyday,
            confirmation = s.am241Confirmation,
        ),
        Nuclide(
            symbol = "Ac-228",
            name = s.ac228Name,
            halfLife = s.ac228HalfLife,
            decay = s.ac228Decay,
            origin = NuclideOrigin.NATURAL,
            chain = "Th-232",
            lines = listOf(
                NuclideGammaLine(911.2f, 25.8f),
                NuclideGammaLine(969.0f, 15.8f),
                NuclideGammaLine(338.3f, 11.3f),
                NuclideGammaLine(964.8f, 5.0f),
                NuclideGammaLine(463.0f, 4.4f),
                NuclideGammaLine(795.0f, 4.3f),
                NuclideGammaLine(209.3f, 3.9f),
                NuclideGammaLine(1588.2f, 3.2f),
            ),
            everyday = s.ac228Everyday,
            confirmation = s.ac228Confirmation,
        ),
        Nuclide(
            symbol = "Bi-212",
            name = s.bi212Name,
            halfLife = s.bi212HalfLife,
            decay = s.bi212Decay,
            origin = NuclideOrigin.NATURAL,
            chain = "Th-232",
            lines = listOf(NuclideGammaLine(727.3f, 6.7f)),
            everyday = s.bi212Everyday,
            confirmation = s.bi212Confirmation,
        ),
        Nuclide(
            symbol = "Ra-226",
            name = s.ra226Name,
            halfLife = s.ra226HalfLife,
            decay = s.ra226Decay,
            origin = NuclideOrigin.NATURAL,
            chain = null,
            lines = listOf(NuclideGammaLine(186.2f, 3.6f)),
            everyday = s.ra226Everyday,
            confirmation = s.ra226Confirmation,
        ),
        Nuclide(
            symbol = "U-235",
            name = s.u235Name,
            halfLife = s.u235HalfLife,
            decay = s.u235Decay,
            origin = NuclideOrigin.NATURAL,
            chain = null,
            lines = listOf(
                NuclideGammaLine(185.7f, 57.2f),
                NuclideGammaLine(143.8f, 11.0f),
                NuclideGammaLine(163.3f, 5.1f),
                NuclideGammaLine(205.3f, 5.0f),
            ),
            everyday = s.u235Everyday,
            confirmation = s.u235Confirmation,
        ),
        Nuclide(
            symbol = "La-138",
            name = s.la138Name,
            halfLife = s.la138HalfLife,
            decay = s.la138Decay,
            origin = NuclideOrigin.NATURAL,
            chain = null,
            lines = listOf(
                NuclideGammaLine(1435.8f, 66.4f),
                NuclideGammaLine(788.7f, 33.6f),
            ),
            everyday = s.la138Everyday,
            confirmation = s.la138Confirmation,
        ),
        Nuclide(
            symbol = "Cs-134",
            name = s.cs134Name,
            halfLife = s.cs134HalfLife,
            decay = s.cs134Decay,
            origin = NuclideOrigin.ARTIFICIAL,
            chain = null,
            lines = listOf(
                NuclideGammaLine(604.7f, 97.6f),
                NuclideGammaLine(795.9f, 85.5f),
                NuclideGammaLine(569.3f, 15.4f),
                NuclideGammaLine(802.0f, 8.7f),
                NuclideGammaLine(563.2f, 8.3f),
            ),
            everyday = s.cs134Everyday,
            confirmation = s.cs134Confirmation,
        ),
        Nuclide(
            symbol = "Bi-214",
            name = s.bi214Name,
            halfLife = s.bi214HalfLife,
            decay = s.bi214Decay,
            origin = NuclideOrigin.NATURAL,
            chain = "Ra-226",
            lines = listOf(
                NuclideGammaLine(609.3f, 45.5f),
                NuclideGammaLine(1764.5f, 15.3f),
                NuclideGammaLine(1120.3f, 14.9f),
                NuclideGammaLine(1238.1f, 5.8f),
                NuclideGammaLine(2204.2f, 4.9f),
                NuclideGammaLine(1377.7f, 4.0f),
                NuclideGammaLine(934.1f, 3.0f),
            ),
            everyday = s.bi214Everyday,
            confirmation = s.bi214Confirmation,
        ),
        Nuclide(
            symbol = "Pb-214",
            name = s.pb214Name,
            halfLife = s.pb214HalfLife,
            decay = s.pb214Decay,
            origin = NuclideOrigin.NATURAL,
            chain = "Ra-226",
            lines = listOf(
                NuclideGammaLine(351.9f, 35.6f),
                NuclideGammaLine(295.2f, 18.4f),
                NuclideGammaLine(242.0f, 7.3f),
            ),
            everyday = s.pb214Everyday,
            confirmation = s.pb214Confirmation,
        ),
        Nuclide(
            symbol = "Pb-212",
            name = s.pb212Name,
            halfLife = s.pb212HalfLife,
            decay = s.pb212Decay,
            origin = NuclideOrigin.NATURAL,
            chain = "Th-232",
            lines = listOf(
                NuclideGammaLine(238.6f, 43.6f),
                NuclideGammaLine(300.1f, 3.3f),
            ),
            everyday = s.pb212Everyday,
            confirmation = s.pb212Confirmation,
        ),
        Nuclide(
            symbol = "Tl-208",
            name = s.tl208Name,
            halfLife = s.tl208HalfLife,
            decay = s.tl208Decay,
            origin = NuclideOrigin.NATURAL,
            chain = "Th-232",
            lines = listOf(
                NuclideGammaLine(2614.5f, 99.8f),
                NuclideGammaLine(583.2f, 85.0f),
                NuclideGammaLine(510.8f, 22.6f),
            ),
            everyday = s.tl208Everyday,
            confirmation = s.tl208Confirmation,
        ),
    )

    /** Русская справка — язык по умолчанию и единственный, который читают тесты. */
    val ALL: List<Nuclide> = all(NuclideRu)

    private val bySymbol: Map<String, Nuclide> = ALL.associateBy { it.symbol }

    // Русский путь держится картой, остальные языки собирают девять карточек
    // на открытие диалога — это дешевле, чем кэш, который надо синхронизировать.
    fun of(symbol: String, s: NuclideStrings = NuclideRu): Nuclide? =
        if (s === NuclideRu) bySymbol[symbol] else all(s).firstOrNull { it.symbol == symbol }
}
