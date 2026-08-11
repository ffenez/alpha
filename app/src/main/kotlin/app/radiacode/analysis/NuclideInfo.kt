package app.radiacode.analysis

/** One gamma line of the reference card: energy and emission probability. */
data class NuclideGammaLine(
    val energyKeV: Float,
    /** Photons emitted per 100 decays **of this nuclide** (not of its parent). */
    val intensityPercent: Float,
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
 * [app.radiacode.ui.logic.NuclideCard].
 */
object NuclideInfoLibrary {

    val ALL: List<Nuclide> = listOf(
        Nuclide(
            symbol = "K-40",
            name = "калий-40",
            halfLife = "1,248·10⁹ лет",
            decay = "β⁻ (89,3 %) → Ca-40; захват электрона (10,7 %) → Ar-40, " +
                "фотон 1460,8 кэВ рождается именно в этой ветви",
            origin = NuclideOrigin.NATURAL,
            chain = null,
            lines = listOf(NuclideGammaLine(1460.8f, 10.7f)),
            everyday = "Постоянная примесь природного калия — 0,0117 % его атомов, " +
                "доля не зависит от происхождения калия. Поэтому линия 1461 кэВ " +
                "видна от заменителей соли и удобрений на хлориде калия, бананов " +
                "и других богатых калием продуктов, гранита, бетона, золы и от " +
                "тела самого человека.",
            confirmation = "Других линий у K-40 в диапазоне прибора нет, так что " +
                "подтвердить совпадение второй линией нельзя. Косвенный довод — " +
                "устойчивость пика при долгом накоплении и его рост рядом с " +
                "калийным материалом.",
        ),
        Nuclide(
            symbol = "Cs-137",
            name = "цезий-137",
            halfLife = "30,08 года",
            decay = "β⁻ → Ba-137m (94,7 %), метастабильный барий за 2,55 мин " +
                "переходит в основное состояние и излучает 661,7 кэВ",
            origin = NuclideOrigin.ARTIFICIAL,
            chain = null,
            lines = listOf(NuclideGammaLine(661.7f, 85.1f)),
            everyday = "Продукт деления: глобальные следы атмосферных испытаний " +
                "и аварий в верхнем слое почвы, в лесных грибах и дичи " +
                "загрязнённых районов. Как закрытый источник встречается в " +
                "уровнемерах, плотномерах и калибровочных наборах.",
            confirmation = "Единственная заметная линия, поэтому одиночного пика " +
                "мало. Осмысленный довод — сравнение с записанным опорным фоном " +
                "того же места и повтор на другом накоплении.",
        ),
        Nuclide(
            symbol = "Co-60",
            name = "кобальт-60",
            halfLife = "5,27 года",
            decay = "β⁻ → Ni-60; обе линии испускаются каскадом почти при каждом распаде",
            origin = NuclideOrigin.ARTIFICIAL,
            chain = null,
            lines = listOf(
                NuclideGammaLine(1332.5f, 100.0f),
                NuclideGammaLine(1173.2f, 99.9f),
            ),
            everyday = "Промышленная радиография и стерилизация, медицинские " +
                "телетерапевтические установки, калибровочные источники; изредка " +
                "попадает в переплавленный металлолом.",
            confirmation = "Линии 1173 и 1333 кэВ рождаются каскадом, поэтому " +
                "осмысленное совпадение требует ОБЕ, причём примерно равной " +
                "площади. Одинокий бугор около 1173 кэВ — не Co-60.",
        ),
        Nuclide(
            symbol = "I-131",
            name = "йод-131",
            halfLife = "8,03 суток",
            decay = "β⁻ → Xe-131",
            origin = NuclideOrigin.ARTIFICIAL,
            chain = null,
            lines = listOf(
                NuclideGammaLine(364.5f, 81.5f),
                NuclideGammaLine(637.0f, 7.2f),
                NuclideGammaLine(284.3f, 6.1f),
            ),
            everyday = "Медицинский изотоп: диагностика и лечение щитовидной " +
                "железы. Человек, недавно прошедший процедуру, остаётся " +
                "источником несколько дней — это самая частая бытовая встреча " +
                "с ним, в том числе в транспорте.",
            confirmation = "Восьмидневный период полураспада проверяем: повтор " +
                "через несколько дней должен показать заметный спад. Вторая " +
                "линия 637 кэВ слабая, но её отсутствие при сильной 364 кэВ — " +
                "довод против.",
        ),
        Nuclide(
            symbol = "Am-241",
            name = "америций-241",
            halfLife = "432,6 года",
            decay = "α → Np-237, сопровождается фотоном 59,5 кэВ",
            origin = NuclideOrigin.ARTIFICIAL,
            chain = null,
            lines = listOf(NuclideGammaLine(59.5f, 35.9f)),
            everyday = "Ионизационные датчики дыма — самый распространённый " +
                "бытовой источник; также промышленные толщиномеры и плотномеры.",
            confirmation = "59,5 кэВ лежит там, где отклик CsI(Tl) и заводская " +
                "энергетическая калибровка наименее точны, поэтому " +
                "низкоэнергетическое совпадение заслуживает особого сомнения. " +
                "Других линий в диапазоне прибора нет.",
        ),
        Nuclide(
            symbol = "Bi-214",
            name = "висмут-214",
            halfLife = "19,9 минуты",
            decay = "β⁻ → Po-214; дочерний продукт радона-222 в ряду U-238",
            origin = NuclideOrigin.NATURAL,
            chain = "Ra-226",
            lines = listOf(
                NuclideGammaLine(609.3f, 45.5f),
                NuclideGammaLine(1764.5f, 15.3f),
                NuclideGammaLine(1120.3f, 14.9f),
            ),
            everyday = "Продукт распада радона-222, который сочится из грунта и " +
                "строительного камня. Обычен в подвалах и плохо проветриваемых " +
                "нижних этажах, усиливается после дождя и снегопада — осадки " +
                "вымывают продукты распада из воздуха.",
            confirmation = "Радоновая цепочка узнаётся по НЕСКОЛЬКИМ линиям " +
                "сразу — 609, 1120 и 1765 кэВ вместе с 352 кэВ от Pb-214. " +
                "Короткий период полураспада означает быстрый спад после " +
                "проветривания, и это проверяемо.",
        ),
        Nuclide(
            symbol = "Pb-214",
            name = "свинец-214",
            halfLife = "26,9 минуты",
            decay = "β⁻ → Bi-214; дочерний продукт радона-222 в ряду U-238",
            origin = NuclideOrigin.NATURAL,
            chain = "Ra-226",
            lines = listOf(
                NuclideGammaLine(351.9f, 35.6f),
                NuclideGammaLine(295.2f, 18.4f),
                NuclideGammaLine(242.0f, 7.3f),
            ),
            everyday = "Тот же радоновый ряд, что и Bi-214: подвалы, погреба, " +
                "гранит и туф, воздух после дождя.",
            confirmation = "Идёт в паре с Bi-214, поэтому 352 кэВ без 609 кэВ " +
                "выглядит странно. Обе линии вместе и их спад после " +
                "проветривания — гораздо более осмысленный довод, чем один пик.",
        ),
        Nuclide(
            symbol = "Pb-212",
            name = "свинец-212",
            halfLife = "10,64 часа",
            decay = "β⁻ → Bi-212; дочерний продукт торона (Rn-220) в ряду Th-232",
            origin = NuclideOrigin.NATURAL,
            chain = "Th-232",
            lines = listOf(
                NuclideGammaLine(238.6f, 43.6f),
                NuclideGammaLine(300.1f, 3.3f),
            ),
            everyday = "Ториевый ряд: старые калильные сетки газовых и " +
                "керосиновых ламп, ториевое оптическое стекло старых " +
                "объективов, монацитовый песок, некоторые сварочные электроды, " +
                "а также обычный гранит.",
            confirmation = "Ториевая цепочка узнаётся по 238,6 кэВ ВМЕСТЕ с " +
                "583 и 2615 кэВ от Tl-208. Линия 2615 кэВ стоит особняком в " +
                "спектре и потому самая показательная.",
        ),
        Nuclide(
            symbol = "Tl-208",
            name = "таллий-208",
            halfLife = "3,05 минуты",
            decay = "β⁻ → Pb-208; в ряду Th-232 через Tl-208 идёт лишь 35,9 % " +
                "распадов Bi-212, поэтому в пересчёте на цепочку выход линий " +
                "примерно втрое меньше приведённого",
            origin = NuclideOrigin.NATURAL,
            chain = "Th-232",
            lines = listOf(
                NuclideGammaLine(2614.5f, 99.8f),
                NuclideGammaLine(583.2f, 85.0f),
                NuclideGammaLine(510.8f, 22.6f),
            ),
            everyday = "Конец ториевого ряда: калильные сетки, ториевая оптика, " +
                "монацит, гранитные облицовки. Линия 2615 кэВ — самая жёсткая в " +
                "природном фоне и часто видна в обычной комнате.",
            confirmation = "Осмысленное совпадение — 583 и 2615 кэВ вместе, " +
                "желательно с 238,6 кэВ от Pb-212. Одна линия 583 кэВ соседствует " +
                "по энергии с другими и сама по себе слаба как довод.",
        ),
    )

    private val bySymbol: Map<String, Nuclide> = ALL.associateBy { it.symbol }

    fun of(symbol: String): Nuclide? = bySymbol[symbol]
}
