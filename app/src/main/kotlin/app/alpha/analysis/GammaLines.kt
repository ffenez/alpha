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
 * All lines are well above the RC-110 acquisition threshold (~20 keV), but
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
        GammaLine("Pb-212", "Th-232", 238.6f, natural = true, intensityPercent = 43.6f),
        GammaLine("Pb-214", "Ra-226", 242.0f, natural = true, intensityPercent = 7.3f),
        GammaLine("I-131", null, 284.3f, natural = false, intensityPercent = 6.1f),
        GammaLine("Pb-214", "Ra-226", 295.2f, natural = true, intensityPercent = 18.4f),
        GammaLine("Pb-212", "Th-232", 300.1f, natural = true, intensityPercent = 3.3f),
        GammaLine("Pb-214", "Ra-226", 351.9f, natural = true, intensityPercent = 35.6f),
        GammaLine("I-131", null, 364.5f, natural = false, intensityPercent = 81.5f),
        GammaLine("Tl-208", "Th-232", 510.8f, natural = true, intensityPercent = 22.6f),
        GammaLine("Tl-208", "Th-232", 583.2f, natural = true, intensityPercent = 85.0f),
        GammaLine("Bi-214", "Ra-226", 609.3f, natural = true, intensityPercent = 45.5f),
        GammaLine("I-131", null, 637.0f, natural = false, intensityPercent = 7.2f),
        GammaLine("Cs-137", null, 661.7f, natural = false, intensityPercent = 85.1f),
        GammaLine("Bi-214", "Ra-226", 1120.3f, natural = true, intensityPercent = 14.9f),
        GammaLine("Co-60", null, 1173.2f, natural = false, intensityPercent = 99.9f),
        GammaLine("Co-60", null, 1332.5f, natural = false, intensityPercent = 100.0f),
        GammaLine("K-40", null, 1460.8f, natural = true, intensityPercent = 10.7f),
        GammaLine("Bi-214", "Ra-226", 1764.5f, natural = true, intensityPercent = 15.3f),
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
