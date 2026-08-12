package app.radiacode.ui.text

/**
 * English catalogue.
 *
 * Translated by meaning, not word by word: where the Russian text refuses to
 * claim something («различие не выделено» rather than «в пределах шума»), the
 * English text refuses the same claim. The words banned by the honesty tests
 * are banned in both languages — «safe», «normal», «dangerous» are as
 * forbidden here as «безопасно» and «норма» are there.
 */
object EnStrings : Strings {

    override val language = AppLanguage.EN

    override val tabHome = "Monitor"
    override val tabSearch = "Search"
    override val tabSpectrum = "Spectrum"
    override val tabMap = "Map"
    override val tabHistory = "History"
    override val back = "Back"
    override val close = "Close"
    override val settings = "Settings"

    override val connected = "connected"
    override val connecting = "connecting"
    override val reconnecting = "reconnecting"
    override val serviceOff = "service off"
    override val noLink = "no link"
    override val noData = "no data"

    override val doseRate = "Dose rate"
    override val countRate = "Count rate"
    override val hardness = "Hardness"
    override val trendPerHour = "Trend/h"
    override val doseToday = "Today"
    override val whyThisConclusion = "why this conclusion ›"
    override val placeFingerprint = "Place fingerprint"

    override val groupMeasurement = "Measurement"
    override val groupApp = "Application"
    override val groupOther = "Other"
    override val settingsAlarms = "Alarms"
    override val settingsAlarmsSub = "thresholds, dwell time, sensitivity"
    override val settingsProfiles = "Places and background"
    override val settingsProfilesSub = "places, Wi-Fi networks, learning the usual background"
    override val settingsNotifications = "Notifications and feedback"
    override val settingsNotificationsSub = "search sound, vibration, alarm"
    override val settingsView = "Appearance"
    override val settingsViewSub = "language, theme, units, tabs and Monitor blocks"
    override val settingsDevice = "Instrument"
    override val settingsDeviceSub = "model, firmware, instrument sound and vibration"
    override val settingsAbout = "About"
    override val settingsAboutSub = "version, updates, licences, diagnostics"

    override val languageTitle = "Language"
    override val languageSystem = "System"

    override val statusNoData = "No data"
    override val statusAboveL1 = "Above the L1 threshold"
    override val statusBelowL1 = "Below the L1 threshold"
    override val statusUsual = "Within this place's usual range"
    override val statusUsualShort = "Usual for this place"
    override val statusAboveUsual = "Above this place's usual range"
    override val statusAboveUsualShort = "Above usual"
    override val statusAboveThreshold = "Above your alarm threshold"
    override val statusAboveThresholdShort = "Above threshold"
    override val statusAlert = "The radiation level has changed"

    override fun detailNoBaseline(threshold: String) =
        "L1 threshold $threshold · this place's historical range is not collected yet"

    override fun detailUsual(range: String, unit: String, collected: String) =
        "P10–P90: $range $unit · observations: $collected"

    override fun detailAboveUsual(range: String, unit: String, held: String) =
        "place P10–P90: $range $unit · $held"

    override fun detailAboveThreshold(threshold: String, heldSeconds: Long, requiredSeconds: Long) =
        "L1 threshold $threshold exceeded · held ${heldSeconds}s of the ${requiredSeconds}s " +
            "needed for an alarm"

    override fun detailAlert(reference: String, held: String) = "$reference · $held"

    override fun referenceThreshold(threshold: String) = "L1 threshold $threshold"

    override fun referenceProfileBand(range: String, unit: String) = "place P10–P90: $range $unit"

    override fun held(text: String) = "held for $text"

    override fun seconds(value: Long) = "${value}s"

    override fun minutes(value: Long) = "$value min"

    override fun hoursMinutes(hours: Long, minutes: Long) = "${hours}h ${minutes}min"

    override fun agoSeconds(value: Long) = "$value s ago"

    override fun interruptedAgo(value: Long) = "interrupted $value s ago"

    override val streamRunning = "stream running"

    override fun updatedAgo(value: Long) = "updated $value s ago"

    override val streamInterruptedFor = "stream interrupted"

    override val searchNoBackground = "No background recorded — nothing to compare with"
    override val searchWaiting = "Waiting for readings"
    override val searchNoExcess = "No excess over the background found"
    override val searchSmallChange = "A small change — not enough data yet"
    override val searchConfirmedExcess = "Sustained excess over the background count"
    override val searchConfirmedDeficit = "Count sustained below the recorded background"
    override val countRising = "count rising"
    override val countFalling = "count falling"
    override val countSteady = "count unchanged"

    override fun directionOverLast(seconds: Long) = "over the last ${seconds}s"

    override val searchCannotCompare =
        "Without a recorded background and a live stream there is nothing to compare."

    override fun searchNotConfirmed(ratio: String?) =
        "A difference from the recorded background is not supported by the counting statistics" +
            (ratio?.let { ": $it" } ?: ".")

    override fun searchTooShort(confirmSeconds: String) =
        "There is a difference, but it has held for less than $confirmSeconds — one short " +
            "window is not enough to conclude from."

    override fun searchExcessExplained(confirmSeconds: String, ratio: String?) =
        "The count rate has been above the recorded background for over $confirmSeconds" +
            (ratio?.let { ", $it" } ?: "") +
            ". This is a statement about count rate, not about dose and not about an isotope."

    override fun searchDeficitExplained(confirmSeconds: String, ratio: String?) =
        "The count rate has been below the recorded background for over $confirmSeconds" +
            (ratio?.let { ", $it" } ?: "") +
            ". That is what moving away from a source, or shielding, looks like."

    override fun ratioToBackground(ratio: String, interval: String?) =
        "×$ratio of the recorded background" + (interval ?: "")

    override fun confidenceInterval(level: Int, low: String, high: String) =
        " ($level % interval $low–$high)"

    override val deviceSignals = "Instrument signals"
    override val deviceSignalsNote =
        "The instrument's own sound and vibration. They work even with the phone " +
            "disconnected or the app closed, and are separate from Search feedback."
    override val deviceSound = "Instrument sound"
    override val deviceVibro = "Instrument vibration"
    override val stateUnknown = "state unknown"
    override val stateOnByApp = "switched on by this app"
    override val stateOffByApp = "switched off by this app"
    override val on = "On"
    override val off = "Off"
}
