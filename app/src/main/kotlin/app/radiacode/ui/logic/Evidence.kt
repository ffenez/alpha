package app.radiacode.ui.logic

/**
 * Four levels of certainty (spec §2). The UI must never merge them into one
 * categorical sentence: «пик статистически значим» is not «изотоп обнаружен»,
 * and «спектр изменился» is not «радиация опасна».
 *
 * The level is rendered as a compact muted tag next to the value, never as a
 * colour and never instead of the value's own uncertainty. Raw measurements
 * carry no tag on the main screen: the tag exists to warn that something is
 * *derived*, and tagging everything would make the marker invisible.
 */
enum class Evidence(val tag: String, val explanation: String) {

    /** Straight from the instrument's measurement stream. */
    MEASURED("изм.", "измерено прибором"),

    /** Deterministic arithmetic over measured values (dose integral, rates). */
    CALCULATED("расчёт", "расчёт из измеренных значений"),

    /** Depends on a statistical model (baseline band, significance). */
    STATISTICALLY_DETECTED("стат.", "вывод статистической модели"),

    /** Physical interpretation, e.g. a possible radionuclide. */
    INTERPRETATION("гипотеза", "физическая интерпретация, не факт"),
}
