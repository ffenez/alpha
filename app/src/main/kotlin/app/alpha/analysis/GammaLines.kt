package app.alpha.analysis

import app.alpha.analysis.evidence.DataSource

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
    /**
     * Фотонов на 100 распадов ЭТОГО нуклида (не его родителя) — тот же смысл,
     * что у [NuclideGammaLine.intensityPercent].
     *
     * Выход нужен матчеру, а не только карточке: без него нельзя ни отличить
     * сильную линию от следовой, ни сказать, что у кандидата НЕ нашлась его
     * самая яркая линия. Пока таблицы жили порознь, матчер этого не знал.
     */
    val intensityPercent: Float,
    /**
     * 1σ табличной энергии, кэВ; `null` — источник её до нашей таблицы не донёс.
     *
     * Null и ноль здесь принципиально разные вещи: нулевая неопределённость
     * означала бы бесконечно точную линию и молча сузила бы окно совпадения.
     * Поэтому поле nullable, а карточка печатает отказ, а не прочерк.
     */
    val energyUncertaintyKeV: Float? = null,
    /** 1σ выхода в тех же процентных единицах; `null` — по той же причине. */
    val intensityUncertaintyPercent: Float? = null,
    /**
     * Откуда взято число. Перечисление общее с движком доказательств
     * ([DataSource]) — второго словаря источников в приложении быть не должно.
     *
     * Сегодня у ВСЕХ линий это [DataSource.ENSDF]: таблица собрана из выборки
     * ENSDF (через IAEA Live Chart / NNDC NuDat 3), где неопределённости до нас
     * не дошли. IAEA Live Chart и NuDat — два интерфейса к одному оценённому
     * набору, а не два независимых подтверждения. Переход на рекомендованные
     * значения DDEP/LNHB — отдельная работа по данным: число переносится
     * ВМЕСТЕ со своей неопределённостью, а не переклеивается ярлыком источника.
     */
    val source: DataSource = DataSource.ENSDF,
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
 * All lines are well above the acquisition threshold of the series (20–30 keV
 * depending on the model), but
 * note that at 59.5 keV (Am-241) the CsI(Tl) response and factory energy
 * calibration are least accurate, so low-energy matches deserve extra doubt.
 */
object GammaLineLibrary {

    /**
     * Ниже этого выхода линия НЕ порождает подсказку сама по себе.
     *
     * **Инженерный параметр.** Следовая линия в 1–2 % на фоне сцинтиллятора
     * почти никогда не видна, зато её присутствие в таблице сопоставления
     * расширяет окно совпадения: чем плотнее сетка линий, тем чаще любой пик
     * во что-нибудь «попадает». Слабые линии из таблицы не выброшены — они
     * работают на ПРОВЕРКУ кандидата ([LineConsistency]), где вопрос обратный:
     * согласуется ли увиденное с уже названным нуклидом.
     */
    const val MIN_MATCH_INTENSITY_PERCENT = 3f

    val LINES: List<GammaLine> = listOf(
        GammaLine("Am-241", null, 59.5f, natural = false, intensityPercent = 35.9f),
        GammaLine("Ba-133", null, 81.0f, natural = false, intensityPercent = 34.1f),
        GammaLine("Eu-152", null, 121.8f, natural = false, intensityPercent = 28.5f),
        GammaLine("Co-57", null, 122.1f, natural = false, intensityPercent = 85.6f),
        GammaLine("Eu-154", null, 123.1f, natural = false, intensityPercent = 40.4f),
        GammaLine("Co-57", null, 136.5f, natural = false, intensityPercent = 10.7f),
        GammaLine("Tc-99m", null, 140.5f, natural = false, intensityPercent = 89.1f),
        GammaLine("U-235", null, 143.8f, natural = true, intensityPercent = 11.0f),
        GammaLine("U-235", null, 163.3f, natural = true, intensityPercent = 5.1f),
        GammaLine("U-235", null, 185.7f, natural = true, intensityPercent = 57.2f),
        GammaLine("Ra-226", null, 186.2f, natural = true, intensityPercent = 3.6f),
        GammaLine("U-235", null, 205.3f, natural = true, intensityPercent = 5.0f),
        GammaLine("Ac-228", "Th-232", 209.3f, natural = true, intensityPercent = 3.9f),
        GammaLine("Pb-212", "Th-232", 238.6f, natural = true, intensityPercent = 43.6f),
        GammaLine("Pb-214", "Ra-226", 242.0f, natural = true, intensityPercent = 7.3f),
        GammaLine("Eu-152", null, 244.7f, natural = false, intensityPercent = 7.6f),
        GammaLine("Ba-133", null, 276.4f, natural = false, intensityPercent = 7.2f),
        GammaLine("I-131", null, 284.3f, natural = false, intensityPercent = 6.1f),
        GammaLine("Pb-214", "Ra-226", 295.2f, natural = true, intensityPercent = 18.4f),
        GammaLine("Ir-192", null, 296.0f, natural = false, intensityPercent = 28.7f),
        GammaLine("Pb-212", "Th-232", 300.1f, natural = true, intensityPercent = 3.3f),
        GammaLine("Ba-133", null, 302.9f, natural = false, intensityPercent = 18.3f),
        GammaLine("Ir-192", null, 308.5f, natural = false, intensityPercent = 29.7f),
        GammaLine("Ir-192", null, 316.5f, natural = false, intensityPercent = 82.9f),
        GammaLine("Ac-228", "Th-232", 338.3f, natural = true, intensityPercent = 11.3f),
        GammaLine("Eu-152", null, 344.3f, natural = false, intensityPercent = 26.6f),
        GammaLine("Pb-214", "Ra-226", 351.9f, natural = true, intensityPercent = 35.6f),
        GammaLine("Ba-133", null, 356.0f, natural = false, intensityPercent = 62.1f),
        GammaLine("I-131", null, 364.5f, natural = false, intensityPercent = 81.5f),
        GammaLine("Ba-133", null, 383.9f, natural = false, intensityPercent = 8.9f),
        GammaLine("Ac-228", "Th-232", 463.0f, natural = true, intensityPercent = 4.4f),
        GammaLine("Ir-192", null, 468.1f, natural = false, intensityPercent = 47.8f),
        GammaLine("Ir-192", null, 484.6f, natural = false, intensityPercent = 3.2f),
        GammaLine("Tl-208", "Th-232", 510.8f, natural = true, intensityPercent = 22.6f),
        GammaLine("Cs-134", null, 563.2f, natural = false, intensityPercent = 8.3f),
        GammaLine("Cs-134", null, 569.3f, natural = false, intensityPercent = 15.4f),
        GammaLine("Tl-208", "Th-232", 583.2f, natural = true, intensityPercent = 85.0f),
        GammaLine("Ir-192", null, 588.6f, natural = false, intensityPercent = 4.5f),
        GammaLine("Ir-192", null, 604.4f, natural = false, intensityPercent = 8.2f),
        GammaLine("Cs-134", null, 604.7f, natural = false, intensityPercent = 97.6f),
        GammaLine("Bi-214", "Ra-226", 609.3f, natural = true, intensityPercent = 45.5f),
        GammaLine("Ir-192", null, 612.5f, natural = false, intensityPercent = 5.3f),
        GammaLine("I-131", null, 637.0f, natural = false, intensityPercent = 7.2f),
        GammaLine("Cs-137", null, 661.7f, natural = false, intensityPercent = 85.1f),
        GammaLine("Eu-154", null, 723.3f, natural = false, intensityPercent = 20.1f),
        GammaLine("Bi-212", "Th-232", 727.3f, natural = true, intensityPercent = 6.7f),
        GammaLine("Eu-152", null, 778.9f, natural = false, intensityPercent = 12.9f),
        GammaLine("La-138", null, 788.7f, natural = true, intensityPercent = 33.6f),
        GammaLine("Ac-228", "Th-232", 795.0f, natural = true, intensityPercent = 4.3f),
        GammaLine("Cs-134", null, 795.9f, natural = false, intensityPercent = 85.5f),
        GammaLine("Cs-134", null, 802.0f, natural = false, intensityPercent = 8.7f),
        GammaLine("Eu-152", null, 867.4f, natural = false, intensityPercent = 4.2f),
        GammaLine("Eu-154", null, 873.2f, natural = false, intensityPercent = 12.1f),
        GammaLine("Ac-228", "Th-232", 911.2f, natural = true, intensityPercent = 25.8f),
        GammaLine("Bi-214", "Ra-226", 934.1f, natural = true, intensityPercent = 3.0f),
        GammaLine("Eu-152", null, 964.1f, natural = false, intensityPercent = 14.5f),
        GammaLine("Ac-228", "Th-232", 964.8f, natural = true, intensityPercent = 5.0f),
        GammaLine("Ac-228", "Th-232", 969.0f, natural = true, intensityPercent = 15.8f),
        GammaLine("Eu-154", null, 996.3f, natural = false, intensityPercent = 10.5f),
        GammaLine("Eu-154", null, 1004.7f, natural = false, intensityPercent = 17.9f),
        GammaLine("Eu-152", null, 1085.9f, natural = false, intensityPercent = 10.1f),
        GammaLine("Eu-152", null, 1112.1f, natural = false, intensityPercent = 13.7f),
        GammaLine("Bi-214", "Ra-226", 1120.3f, natural = true, intensityPercent = 14.9f),
        GammaLine("Co-60", null, 1173.2f, natural = false, intensityPercent = 99.9f),
        GammaLine("Bi-214", "Ra-226", 1238.1f, natural = true, intensityPercent = 5.8f),
        GammaLine("Eu-154", null, 1274.4f, natural = false, intensityPercent = 34.9f),
        GammaLine("Na-22", null, 1274.5f, natural = false, intensityPercent = 99.9f),
        GammaLine("Co-60", null, 1332.5f, natural = false, intensityPercent = 100.0f),
        GammaLine("Bi-214", "Ra-226", 1377.7f, natural = true, intensityPercent = 4.0f),
        GammaLine("Eu-152", null, 1408.0f, natural = false, intensityPercent = 20.9f),
        GammaLine("La-138", null, 1435.8f, natural = true, intensityPercent = 66.4f),
        GammaLine("K-40", null, 1460.8f, natural = true, intensityPercent = 10.7f),
        GammaLine("Ac-228", "Th-232", 1588.2f, natural = true, intensityPercent = 3.2f),
        GammaLine("Bi-214", "Ra-226", 1764.5f, natural = true, intensityPercent = 15.3f),
        GammaLine("Bi-214", "Ra-226", 2204.2f, natural = true, intensityPercent = 4.9f),
        GammaLine("Tl-208", "Th-232", 2614.5f, natural = true, intensityPercent = 99.8f),
    )

    /** Линии, по которым вообще выдаётся подсказка (см. [MIN_MATCH_INTENSITY_PERCENT]). */
    val MATCHABLE: List<GammaLine> =
        LINES.filter { it.intensityPercent >= MIN_MATCH_INTENSITY_PERCENT }

    /** Самая яркая линия нуклида — та, отсутствие которой что-то значит. */
    fun strongestLineOf(isotope: String): GammaLine? =
        linesOf(isotope).maxByOrNull { it.intensityPercent }

    fun linesOf(isotope: String): List<GammaLine> = LINES.filter { it.isotope == isotope }
}
