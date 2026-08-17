package app.alpha.analysis

/**
 * Single source of truth for the versions of every derived analysis in the
 * app (spec §22, §24 point 8).
 *
 * Why one object instead of a constant per algorithm: a derived result that is
 * *persisted* (comparator output, A/B experiment, spectrum-snapshot analysis)
 * or *exported* must name the math that produced it, and a reader of a two-year
 * old export must be able to look the number up in one place. The numbers are
 * pinned by [app.alpha.analysis.AlgorithmVersionsTest] on purpose: changing
 * a formula makes that test fail, which forces the version bump to be a
 * conscious review step rather than a silent behaviour change.
 *
 * Bump rules:
 *  - the number changes when the *math or its parameters* change in a way that
 *    would give a different answer for the same raw data;
 *  - refactoring, renaming and UI wording do not bump anything;
 *  - old stored results keep their old version — they are not re-stamped, so
 *    raw data can be re-analysed later and compared honestly (spec §22).
 *
 * Algorithms that already carried their own constant keep it as the value they
 * are used with; the test asserts the two stay equal.
 */
object AlgorithmVersions {

    /** Weighted P10–P90 baseline band ([app.alpha.baseline.BaselineConfig]). */
    const val BASELINE = 2

    /** Baseline admission pipeline ([app.alpha.baseline.BaselineAdmission]). */
    const val BASELINE_ADMISSION = 1

    /** Permission-free network identity ([app.alpha.context.NetworkIdentity]). */
    const val NETWORK_IDENTITY = 1

    /**
     * Peak finder: smoothed local maxima + side continuum + net-area
     * significance + a width gate. v2 changed both the denominator and the
     * acceptance rule, so the numbers it produces are not comparable to v1.
     */
    const val PEAK_DETECTION = 2

    /**
     * Multi-line isotope hints ([IsotopeMatcher]). С подключением движка
     * доказательств ([PEAK_EVIDENCE]) экран Спектра его больше не показывает;
     * версия остаётся 1, потому что сама математика матчера не менялась —
     * старые сохранённые результаты продолжают честно ссылаться на неё.
     */
    const val ISOTOPE_MATCH = 1

    /**
     * Движок спектральных доказательств
     * ([app.alpha.analysis.evidence.EvidenceEngine], ADR 006) — то, что
     * теперь наполняет колонку «возможное совпадение» и справку нуклида.
     * v1 = каскад «артефакты → z-совпадение энергии → набор линий → группы
     * неразрешимости → отношения площадей → диагностика калибровки» с
     * классификацией WEAK/SUPPORTED/AMBIGUOUS/CONTRADICTED. Заведена НОВАЯ
     * константа, а не бамп [ISOTOPE_MATCH]: это другой алгоритм с другим
     * выходом, и результат, посчитанный матчером, не «вторая версия» его.
     */
    const val PEAK_EVIDENCE = 1

    /** Snapshot comparator ([SpectrumCompare]): interval extraction + rate diff. */
    const val SPECTRUM_COMPARE = 1

    /** Channel-wise snapshot merge ([SpectrumMerge]). */
    const val SPECTRUM_MERGE = 1

    /** Radon daughter ROI trend ([RadonTrend]). */
    const val RADON_TREND = 1

    /** Energy windows and the R_low/R_high spectral index ([EnergyWindows], spec §7). */
    const val ENERGY_WINDOWS = 1

    /** Dose projection D ≈ Ḋ·t ([DoseProjection], spec §6). */
    const val DOSE_PROJECTION = 1

    /** A/B net counts, statistics and verdicts ([AbAnalysis], spec §9). */
    const val AB_ANALYSIS = 1

    /**
     * Mergeable quantile sketch of long chart windows
     * ([app.alpha.analysis.quantiles.KllSketch], ADR 004, CHART SPEC §30).
     * A bump means every stored `hour_sketches` row is rebuilt from raw.
     */
    const val QUANTILE_SKETCH = 1

    /**
     * Window trend ([app.alpha.ui.logic.TrendFit], graph spec §23).
     * v2 = Theil–Sen with an availability rule; v1 was plain OLS on the
     * present columns with no minimum window.
     */
    const val TREND_FIT = 2

    /**
     * Window distribution ([app.alpha.ui.logic.DoseHistograms], graph
     * spec §14). v2 = Freedman–Diaconis with clamp/snap and the IQR = 0,
     * small-N and degenerate fallbacks; v1 was a fixed 22-bin target.
     */
    const val DOSE_HISTOGRAM = 2

    /** Descriptive current-vs-baseline statements ([DescriptiveDeviation], graph spec §35). */
    const val DESCRIPTIVE_DEVIATION = 1

    /**
     * Поиск: two-sample comparison of the current counting window against the
     * recorded background ([RateComparison], search redesign §3). v1 = exact
     * conditional binomial + inverted Clopper–Pearson interval for the rate
     * ratio, with a Fano-factor dispersion check.
     */
    const val RATE_COMPARISON = 1

    /**
     * Поиск: the state ladder that turns a sequence of comparisons into a
     * displayed conclusion ([app.alpha.ui.logic.SearchLadder], search
     * redesign §11). v1 = magnitude from [RATE_COMPARISON] plus a minimum
     * confirmation time and a Theil–Sen direction over the short window.
     */
    const val SEARCH_LADDER = 1

    /**
     * Жёсткость: share of the counts above the split energy ([Hardness]).
     * v1 = fixed 100/300/1500 keV bands, binomial σ of the fraction.
     */
    const val HARDNESS = 1

    /**
     * Отпечаток места ([Fingerprint], ADR 005). v1 = полоса эталона по дозе и
     * счёту плюс χ² однородности по форме спектра; жёсткость без голоса.
     */
    const val FINGERPRINT = 1

    /**
     * Поиск: shape-only comparison of two accumulated spectra ([ShapeChange],
     * search redesign §13). v1 = two-sample χ² homogeneity on merged energy
     * bands, significance through the shared Wilson–Hilferty transform.
     */
    const val SHAPE_CHANGE = 1

    /**
     * **Candidate** current-vs-baseline test ([AnomalyStatistics], graph spec
     * §36): Mann–Whitney U / Kolmogorov–Smirnov with an N_eff correction for
     * autocorrelation. Experimental — nothing derived from it is shown, and
     * the version exists so validation runs can be attributed to a maths
     * revision.
     */
    const val ANOMALY_TEST_CANDIDATE = 1

    /**
     * Диагностика калибровки по природному фону
     * ([app.alpha.analysis.evidence.BackgroundCalibration]): отбор
     * пригодных линий, измерение ширин, подгонка √(a+bE+cE²), σ_cal из
     * остатков и относительный отклик по паре линий одного нуклида.
     * Версия хранится вместе с ПРИНЯТОЙ моделью разрешения — по ней видно,
     * какой математикой получены лежащие в настройках коэффициенты.
     */
    const val BACKGROUND_CALIBRATION = 1

    /** Stable storage/export keys → current version. Keys are a disk contract. */
    val all: Map<String, Int> = linkedMapOf(
        "baseline" to BASELINE,
        "baseline_admission" to BASELINE_ADMISSION,
        "network_identity" to NETWORK_IDENTITY,
        "peak_detection" to PEAK_DETECTION,
        "isotope_match" to ISOTOPE_MATCH,
        "peak_evidence" to PEAK_EVIDENCE,
        "spectrum_compare" to SPECTRUM_COMPARE,
        "spectrum_merge" to SPECTRUM_MERGE,
        "radon_trend" to RADON_TREND,
        "energy_windows" to ENERGY_WINDOWS,
        "dose_projection" to DOSE_PROJECTION,
        "ab_analysis" to AB_ANALYSIS,
        "quantile_sketch" to QUANTILE_SKETCH,
        "trend_fit" to TREND_FIT,
        "dose_histogram" to DOSE_HISTOGRAM,
        "descriptive_deviation" to DESCRIPTIVE_DEVIATION,
        "rate_comparison" to RATE_COMPARISON,
        "search_ladder" to SEARCH_LADDER,
        "shape_change" to SHAPE_CHANGE,
        "hardness" to HARDNESS,
        "fingerprint" to FINGERPRINT,
        "anomaly_test_candidate" to ANOMALY_TEST_CANDIDATE,
        "background_calibration" to BACKGROUND_CALIBRATION,
    )

    /**
     * «energy_windows v1 · ab_analysis v1» — the stamp exports and stored
     * analyses carry. Unknown keys are ignored rather than guessed.
     */
    fun stamp(vararg keys: String): String =
        keys.mapNotNull { key -> all[key]?.let { "$key v$it" } }.joinToString(" · ")
}
