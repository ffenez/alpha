package app.radiacode.analysis.evidence

/**
 * Инвентарь гамма-линий ПРИРОДНОГО ФОНА — то, что реально светит в стенах.
 *
 * Зачем он отдельно от [EvidenceLineLibrary]. Библиотека подсказок нарочно
 * узкая: чем плотнее сетка линий, тем чаще произвольный пик во что-нибудь
 * «попадает» (ADR 006). Здесь задача обратная — выбрать линии, по которым
 * можно измерять САМ ПРИБОР, и для этого нужно знать, что ЕЩЁ лежит рядом с
 * каждой такой линией. Линия, у которой в библиотеке подсказок нет соседа,
 * может иметь соседа в природе: 351,9 кэВ (Pb-214) выглядит одинокой ровно до
 * тех пор, пока в таблицу не попадёт 338,3 кэВ (Ac-228), до которой 14 кэВ при
 * FWHM около 40.
 *
 * ## Нормировка выходов
 *
 * Выход дан НА РАСПАД РОДИТЕЛЯ ЦЕПОЧКИ в вековом равновесии, а не на распад
 * самого излучателя. Разница существенна только для Tl-208: он рождается лишь
 * в 35,94 % распадов Bi-212, поэтому его табличные 99,8 % на линии 2614,5
 * превращаются в 35,9 % на распад цепочки Th-232. Без этой поправки
 * сравнение «сопоставима ли интенсивность соседа» внутри ториевого ряда
 * давало бы Tl-208 втрое больший вес, чем он имеет в спектре.
 *
 * Между РАЗНЫМИ рядами (Th-232, Ra-226, K-40) выходы всё равно несравнимы:
 * их активности задаются составом бетона и грунта, а не ядерными данными.
 * Именно поэтому правило отбора ([BackgroundCalibration]) считает соседа из
 * ЧУЖОГО ряда непредсказуемым независимо от его выхода.
 *
 * ## Неизвестный выход — не ноль
 *
 * У аннигиляционной линии 511 кэВ и у 185,7 кэВ (U-235) выход помечен `NaN`:
 * первый задаётся рождением пар от жёстких фотонов и космикой, второй —
 * изотопным составом урана в конкретном материале. Обе величины меняются от
 * места к месту, и подставить им «правдоподобное» число значило бы решить за
 * данные. `NaN` читается правилом отбора как «сопоставим» — то есть линия,
 * рядом с которой они стоят, признаётся непригодной.
 *
 * Источник чисел: ENSDF через IAEA Live Chart of Nuclides / NNDC NuDat 3, те
 * же таблицы, из которых собрана [app.radiacode.analysis.GammaLineLibrary].
 * Неопределённости источник не дал — поля `null`, см. KDoc [LibraryLine].
 */
object BackgroundLineInventory {

    /** Th-232 → Bi-212 → Tl-208: α-ветвь Bi-212, доля распадов с Tl-208. */
    const val TL208_BRANCH = 0.3594

    const val CHAIN_TH232 = "Th-232"
    const val CHAIN_RA226 = "Ra-226"

    /** Нуклид-заглушка для аннигиляционной линии: у неё нет распада-источника. */
    const val ANNIHILATION = "511"

    private fun line(
        nuclide: String,
        chain: String?,
        energyKeV: Double,
        intensityPercent: Double,
    ) = LibraryLine(
        nuclide = nuclide,
        chain = chain,
        energyKeV = energyKeV,
        energyUncertaintyKeV = null,
        intensityPercent = intensityPercent,
        intensityUncertaintyPercent = null,
        source = DataSource.ENSDF,
        natural = true,
    )

    private fun thallium(energyKeV: Double, rawIntensityPercent: Double) =
        line("Tl-208", CHAIN_TH232, energyKeV, rawIntensityPercent * TL208_BRANCH)

    /** Все линии инвентаря, отсортированы по энергии. */
    val LINES: List<LibraryLine> = listOf(
        // --- ряд Ra-226 (U-238) ---
        line("U-235", null, 185.7, Double.NaN),
        line("Ra-226", CHAIN_RA226, 186.2, 3.64),
        line("Pb-214", CHAIN_RA226, 242.0, 7.27),
        line("Pb-214", CHAIN_RA226, 295.2, 18.41),
        line("Pb-214", CHAIN_RA226, 351.9, 35.60),
        line("Bi-214", CHAIN_RA226, 609.3, 45.49),
        line("Bi-214", CHAIN_RA226, 665.5, 1.53),
        line("Bi-214", CHAIN_RA226, 768.4, 4.89),
        line("Pb-214", CHAIN_RA226, 786.0, 1.06),
        line("Bi-214", CHAIN_RA226, 806.2, 1.26),
        line("Pb-214", CHAIN_RA226, 839.0, 0.58),
        line("Bi-214", CHAIN_RA226, 934.1, 3.10),
        line("Bi-214", CHAIN_RA226, 1120.3, 14.91),
        line("Bi-214", CHAIN_RA226, 1155.2, 1.63),
        line("Bi-214", CHAIN_RA226, 1238.1, 5.83),
        line("Bi-214", CHAIN_RA226, 1281.0, 1.43),
        line("Bi-214", CHAIN_RA226, 1377.7, 3.97),
        line("Bi-214", CHAIN_RA226, 1401.5, 1.33),
        line("Bi-214", CHAIN_RA226, 1408.0, 2.39),
        line("Bi-214", CHAIN_RA226, 1509.2, 2.13),
        line("Bi-214", CHAIN_RA226, 1661.3, 1.05),
        line("Bi-214", CHAIN_RA226, 1729.6, 2.88),
        line("Bi-214", CHAIN_RA226, 1764.5, 15.31),
        line("Bi-214", CHAIN_RA226, 1847.4, 2.02),
        line("Bi-214", CHAIN_RA226, 2118.6, 1.16),
        line("Bi-214", CHAIN_RA226, 2204.2, 4.92),
        line("Bi-214", CHAIN_RA226, 2447.9, 1.55),

        // --- ряд Th-232 ---
        line("Ac-228", CHAIN_TH232, 209.3, 3.89),
        line("Pb-212", CHAIN_TH232, 238.6, 43.60),
        line("Ac-228", CHAIN_TH232, 270.2, 3.46),
        thallium(277.4, 6.60),
        line("Pb-212", CHAIN_TH232, 300.1, 3.30),
        line("Ac-228", CHAIN_TH232, 328.0, 2.95),
        line("Ac-228", CHAIN_TH232, 338.3, 11.27),
        line("Ac-228", CHAIN_TH232, 409.5, 1.92),
        line("Ac-228", CHAIN_TH232, 463.0, 4.40),
        thallium(510.8, 22.60),
        thallium(583.2, 85.00),
        line("Bi-212", CHAIN_TH232, 727.3, 6.67),
        thallium(763.1, 1.79),
        line("Bi-212", CHAIN_TH232, 785.4, 1.10),
        line("Ac-228", CHAIN_TH232, 795.0, 4.25),
        line("Ac-228", CHAIN_TH232, 835.7, 1.61),
        thallium(860.6, 12.50),
        line("Ac-228", CHAIN_TH232, 911.2, 25.80),
        line("Ac-228", CHAIN_TH232, 964.8, 4.99),
        line("Ac-228", CHAIN_TH232, 969.0, 15.80),
        line("Ac-228", CHAIN_TH232, 1588.2, 3.22),
        line("Bi-212", CHAIN_TH232, 1620.5, 1.47),
        line("Ac-228", CHAIN_TH232, 1630.6, 1.51),
        thallium(2614.5, 99.75),

        // --- вне рядов ---
        line("K-40", null, 1460.8, 10.55),
        line(ANNIHILATION, null, 511.0, Double.NaN),
    ).sortedBy { it.energyKeV }

    /**
     * Две линии принадлежат одному источнику активности, если это один нуклид
     * или один ряд: только тогда их отношение задано ядерными данными, а не
     * составом бетона под конкретной стеной.
     */
    fun sameActivity(first: LibraryLine, second: LibraryLine): Boolean =
        first.nuclide == second.nuclide ||
            (first.chain != null && first.chain == second.chain)
}
