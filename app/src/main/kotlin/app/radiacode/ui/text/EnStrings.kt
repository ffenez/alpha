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

    override val onboardingBrand = "Alpha"
    override val onboardingConnectTitle = "Connecting the instrument"
    override val onboardingConnectBody =
        "The app connects to a RadiaCode dosimeter over Bluetooth and records the " +
            "background level continuously. Every measurement stays on this phone."
    override val onboardingPermissions =
        "Two permissions are needed: Bluetooth, to find and connect the instrument, and " +
            "notifications, to show the measurement while the app is in the background."
    override val onboardingBluetoothDenied =
        "Without the Bluetooth permission the instrument cannot be found. If the request " +
            "no longer appears, grant the permission in the Android settings for this app."
    override val retry = "Retry"
    override val start = "Start"
    override val onboardingBackgroundTitle = "Running in the background"
    override val onboardingBackgroundBody =
        "So that recording is not interrupted overnight or with the screen off, exclude " +
            "the app from battery optimisation. Otherwise Android will eventually drop the " +
            "connection to the instrument."
    override val onboardingBatteryNote =
        "This increases battery use — usually by a small amount."
    override val later = "Later"
    override val allow = "Allow"
    override val onboardingScanTitle = "Looking for the instrument"
    override val scanning = "searching nearby…"
    override val onboardingScanBody =
        "Switch the instrument on and keep it close. The official RadiaCode app must be " +
            "closed: the instrument pairs with one phone at a time."
    override val onboardingScanFailed =
        "The scan did not start. Check that Bluetooth is on and open the app again."
    override val connecting2 = "connecting…"
    override val connect = "Connect"

    override val spectrumAccumulating = "current accumulation"
    override val spectrumContinuation = "continuing: "
    override val spectrogramEntry = "Spectrogram ▸"
    override val radonEntry = "Radon ▸"
    override val formatUnsupportedTitle = "Format not supported"

    override fun formatUnsupportedBody(version: Int) =
        "The instrument sends its spectrum in format version $version, which this app " +
            "cannot read yet. The other screens work as usual."

    override val spectrumReading = "reading the spectrum from the instrument…"
    override val noInstrumentLink = "no link to the instrument"
    override val spectrumAfterConnect =
        "The spectrum appears once connected — the link status is shown on Monitor."
    override val exportFailedTitle = "Export failed"
    override val exportFailedBody = "The file was not written — try another folder."
    override val importAction = "Import"
    override val exportXml = "Export XML"
    override val exportN42 = "Export N42"
    override val exportFormatsNote =
        "XML is the RadiaCode app's format · N42 is the standard of analysis software · " +
            "an imported snapshot appears in History"
    override val savedToPrefix = " · file saved to "
    override val continuationTitle = "Continuing accumulation"
    override val disable = "turn off"
    override val snapshotDeltaPrefix = " · snapshot Δt "

    override fun sumImpossible(reason: String) =
        "the sum is impossible: $reason — the current accumulation is shown"

    override val sumShown =
        "showing the snapshot plus the current accumulation (channels add up, Δt adds " +
            "up); «Save» will store the sum"
    override val noLiveAccumulation =
        "no live accumulation yet — the saved snapshot is shown"
    override val continuationWarning =
        "The instrument accumulates its spectrum independently of the app. If the snapshot " +
            "was taken from the current accumulation without a reset, counts are counted " +
            "twice — reset the spectrum first."
    override val spectrumInfoTitle = "How to read this spectrum"
    override val spectrumInfoAxes =
        "Energy in keV runs horizontally; counts per channel over the whole accumulation " +
            "run vertically. One screen column holds several channels and takes their " +
            "maximum: a narrow peak survives zooming out, but the continuum line rides the " +
            "upper envelope."
    override val spectrumInfoSignificance =
        "A peak's significance is its net area divided by the net area's own standard " +
            "uncertainty, which includes both the statistics of the peak window and the " +
            "uncertainty of the continuum estimated under it. A structure counts as a peak " +
            "only if its width agrees with the detector's resolution."
    override val spectrumInfoCandidate =
        "A nuclide candidate is an energy match, not a detection: reliable identification " +
            "needs accumulated statistics and, as a rule, several lines of the same nuclide."
    override val spectrumInfoScales =
        "The counts axis: linear preserves the ratio of areas but crushes everything but " +
            "the tallest towards zero; logarithmic shows single counts next to a photopeak " +
            "but visually equalises quantities that differ severalfold; the power scale 1/n " +
            "sits between them (1/2 is the familiar square root). All three are monotone " +
            "transforms of the same number: the distribution of height changes, the data " +
            "does not."
    override val spectrumInfoGestures =
        "Pinch to scale, drag to pan. Smoothing changes the display only: the underlying " +
            "data is untouched."
    override val scaleLinear = "Lin"
    override val scalePower = "Power"
    override val scaleLog = "Log"

    override fun powerDegree(root: Int) = "power 1/$root"

    override val spectrumModeRaw = "Spectrum"
    override val spectrumModeMinusBackground = "− background"
    override val smoothing = "smooth"
    override val energyRanges = "energy ranges"
    override val peakTableEnergy = "E, keV"
    override val peakTableNet = "net"
    override val peakTableSignificance = "significance"
    override val peakTableCandidate = "candidate"
    override val notEnoughForPeaks =
        "too little data for peak analysis — accumulate at least a minute"
    override val noPeaksFound = "no pronounced peaks above the continuum were found"
    override val peakTableCaveat =
        "a possible match ≠ a detection · confirmation is needed: accumulate longer · " +
            "tap a row for the nuclide reference"
    override val recordBackground = "Record background"
    override val save = "Save"
    override val reset = "Reset"
    override val resetSpectrumTitle = "Reset the spectrum?"
    override val resetSpectrumBody =
        "Accumulation starts over — the instrument clears its spectrum too. Saved " +
            "snapshots stay in History."
    override val cancel = "Cancel"

    override fun edgeCounts(counts: String) = "at the top edge of the scale: $counts counts"

    override fun rangeWhole(range: String) = "range $range · whole · pinch to zoom in"

    override fun rangeDraggable(range: String) = "range $range · drag to pan"

    override val noSpectrumBackground =
        "no background recorded — record a spectrum of ordinary surroundings and the " +
            "overlay and «minus background» will appear"

    override fun sessionsCount(total: Long) = "$total sessions"

    override val selectAll = "Select all"
    override val clearAll = "Clear all"

    override fun selectedCount(count: Int) = "selected: $count"

    override val readingJournal = "reading the journal…"
    override val noSessionsYet = "no sessions yet"
    override val sessionExplained =
        "A session is an uninterrupted period of measurement: it opens when the " +
            "instrument connects and closes when it disconnects."
    override val showMore = "Show more"
    override val accumulatedDose = "Accumulated dose"
    override val calculatedTag = "calc."
    override val partialDayNote =
        "hollow bars — the day was measured only in part: the dose accumulated over the " +
            "recording time, not over the whole day"

    override fun todayWithUnit(unit: String) = "today, $unit"

    override val days7 = "7 days"
    override val days30 = "30 days"
    override val accumulatedDoseNote =
        "The dose rate summed over the seconds actually measured — not to be confused " +
            "with the current dose rate."
    override val doseProjection = "Dose projection"
    override val noProfile = "No place"
    override val runningCannotDelete = "· running, cannot be deleted"
    override val running = "· running"
    override val avg = "avg"
    override val max = "max"
    override val dose = "dose"
    override val track = "track"
    override val spectrum = "spectrum"
    override val flight = "flight"
    override val noSamplesInSession = "no measurements were recorded in this session"
    override val profileEllipsis = "place…"
    override val sessionProfileTitle = "Place of the session"

    override fun sessionProfileBody(started: String) =
        "Session from $started. Its measurements move into the statistics of the chosen place."

    override val deviation = "Deviation"
    override val excursionPoint = "Excursion point"
    override val usually = "usually"
    override val fileSaved = "file saved"
    override val spectraTitle = "Spectra"
    override val compare = "compare"
    override val merge = "merge"
    override val markForDeletion = "tick the snapshots to delete"
    override val pickTwoToCompare = "pick two snapshots — the comparison opens"
    override val pickTwoOrMoreToMerge =
        "tick two or more snapshots — channels add up and accumulation times are summed"
    override val snapshotOpensActions = "a snapshot opens export, comparison and continuation"

    override fun mergeAction(count: Int) = "Merge ($count)"

    override fun mergedSaved(label: String) =
        "the merged snapshot «$label» is saved — it appeared in the list"

    override val mergeImpossible = "Cannot be merged"
    override val compareWithAnother = "Compare with another…"
    override val continueAccumulation = "Continue accumulating"
    override val continueAccumulationNote =
        "the snapshot adds to the live accumulation on the Spectrum screen — the " +
            "instrument keeps accumulating independently"
    override val importedTag = "import"
    override val backgroundTag = "background"
    override val delete = "Delete"

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
