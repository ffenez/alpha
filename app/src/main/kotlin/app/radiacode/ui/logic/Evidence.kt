package app.radiacode.ui.logic

import app.radiacode.ui.text.MonitorRu
import app.radiacode.ui.text.MonitorStrings

/**
 * Four levels of certainty (spec §2). The UI must never merge them into one
 * categorical sentence: «пик статистически значим» is not «изотоп обнаружен»,
 * and «спектр изменился» is not «радиация опасна».
 *
 * The level is rendered as a compact muted tag next to the value, never as a
 * colour and never instead of the value's own uncertainty. Raw measurements
 * carry no tag on the main screen: the tag exists to warn that something is
 * *derived*, and tagging everything would make the marker invisible.
 *
 * Подписи живут в каталоге языка, а не в константах перечисления: метка стоит
 * рядом со значением на экране и обязана быть на языке интерфейса.
 */
enum class Evidence {

    /** Straight from the instrument's measurement stream. */
    MEASURED,

    /** Deterministic arithmetic over measured values (dose integral, rates). */
    CALCULATED,

    /** Depends on a statistical model (baseline band, significance). */
    STATISTICALLY_DETECTED,

    /** Physical interpretation, e.g. a possible radionuclide. */
    INTERPRETATION,
}

/** Короткая метка рядом со значением: «изм.» / «meas.». */
fun Evidence.tag(s: MonitorStrings = MonitorRu): String = when (this) {
    Evidence.MEASURED -> s.evidenceMeasuredTag
    Evidence.CALCULATED -> s.evidenceCalculatedTag
    Evidence.STATISTICALLY_DETECTED -> s.evidenceStatisticalTag
    Evidence.INTERPRETATION -> s.evidenceInterpretationTag
}

/** Расшифровка метки для легенды и отчётов. */
fun Evidence.explanation(s: MonitorStrings = MonitorRu): String = when (this) {
    Evidence.MEASURED -> s.evidenceMeasuredNote
    Evidence.CALCULATED -> s.evidenceCalculatedNote
    Evidence.STATISTICALLY_DETECTED -> s.evidenceStatisticalNote
    Evidence.INTERPRETATION -> s.evidenceInterpretationNote
}
