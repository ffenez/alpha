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
