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
    override val settingsViewSub = "language, skin, theme, units, Monitor blocks"
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

    override val evidenceLegend =
        "meas. — measured by the instrument · calc. — computed from measurements · " +
            "stat. — the result of the place's statistical model"
    override val nowSection = "Now"
    override val poissonNote =
        "Poisson 1σ ≈ √(N/τ), τ = 1 s · a count on its own is not converted into a dose"
    override val dataSection = "Data"
    override val profile = "Place"
    override val outsideProfile = "outside any place"
    override val comparisonSection = "Comparison with the place"
    override val historicalRange = "Historical range"
    override val notCollectedYet = "not collected yet"
    override val comparisonRuns = "Compared"

    override fun withThresholdL1(value: String) = "against the L1 threshold $value"

    override val thresholdIsNotSafety =
        "L1 is a parameter of the app's alarm, not a safety limit."
    override val currentValue = "Current value"
    override val position = "Position"
    override val bandExplained =
        "P10–P90 is the range that held about 80 % of this place's usable historical " +
            "measurements. It describes this place, not a radiation-safety limit."
    override val belowP10 = "below P10"
    override val aboveP90 = "above P90"
    override val insideBand = "inside P10–P90"
    override val profileStatistics = "Statistics of the place"
    override val median = "Median"
    override val madNote =
        "median(|xᵢ − median|) — a robust description of the observed spread that assumes " +
            "no normal distribution. It is not the instrument's uncertainty"
    override val usableData = "Usable data"
    override val minuteBuckets = "Minute buckets"
    override val honestN = "the honest n of the order statistics"
    override val notEnoughData = "Not enough data"
    override val updating = "Updating"
    override val temporarilyNotUpdating = "Temporarily not updating"
    override val updatingNote =
        "New usable measurements are taken into account when the historical range is " +
            "recomputed."
    override val notUpdatingNote =
        "New measurements are being stored, but are temporarily not used to update the " +
            "historical range."
    override val state = "State"
    override val excludedFromStatistics = "Not counted in the statistics"
    override val statisticsState = "State of the statistics"
    override val quarantineNote =
        "After a sustained deviation, new measurements are stored for a while but not " +
            "added to the place's usual range. That prevents the deviation itself from " +
            "gradually becoming the new usual background."
    override val howDetected = "How a deviation is detected"
    override val absoluteThresholdL1 = "Absolute threshold L1"
    override val relativeCriterion = "Relative criterion"

    override fun timesProfileP90(factor: String) = "$factor × the place's P90"

    override val minimumDuration = "Minimum duration"
    override val shorterNotAnnounced = "shorter than this and no deviation is announced"
    override val returnCriterion = "Return"
    override val backBelowThreshold = "the value is below the threshold again"
    override val exclusionAfterEvent = "Exclusion after an event"
    override val fromEndOfDeviation = "counted from the end of the deviation"
    override val criteriaNote =
        "These are parameters of the event-detection algorithm, not scientific limits of " +
            "harm. The engine and the alarm settings use the same numbers."
    override val notEvaluated = "not evaluated"
    override val notEnoughStatistics = "not enough statistics"
    override val noChangeDetected = "no change detected"
    override val changeDetected = "a change was detected"
    override val spectralNoReference =
        "This place has no reference fingerprint yet, so the spectrum is not part of the " +
            "conclusion. «Not evaluated» is not «no change»."

    override fun spectralTooLittle(detail: String) =
        "Comparison with the place's reference has started, but there is little data yet: " +
            detail

    override fun spectralCompared(detail: String) =
        "The shape of the spectrum is compared with the place's reference (not with an " +
            "absolute level): $detail. The conclusion describes the composition of the " +
            "radiation, not its harm."

    override val spectralComparison = "Spectral comparison"

    override val searchFeedbackTitle = "Search feedback"
    override val feedbackOnScreenOnly = "the signal is shown on the Search screen only"
    override val feedbackClicks = "a click for every registered count"
    override val feedbackTone = "a continuous tone: higher means further from the background"
    override val feedbackVibro =
        "the same without sound: faster pulses mean further from the background"
    override val energyTone = "pitch by energy"
    override val energyToneNote = "click pitch follows the mean gamma energy"
    override val alarmTitle = "Alarm"
    override val archiveSaved = "archive saved"
    override val archiveFailed = "the archive was not written — try another folder"
    override val debugTitle = "Diagnostics"
    override val stateReport = "State report"
    override val debugBundleNote =
        "One archive with everything a diagnosis needs: the state of the app and the " +
            "instrument, the accumulated spectrum and the recorded background, and your " +
            "description of the problem."
    override val whatIsWrong = "What is wrong"
    override val whatIsWrongHint = "for example: it connects, but the spectrum is empty"
    override val saveDebugArchive = "Save the diagnostics archive"
    override val notConnected = "not connected"

    override fun excludedBecause(reason: String) = "excluded: $reason"

    override val measurementsCounted = "measurements are counted"
    override val no = "no"
    override val notRecorded = "not recorded"

    override fun createdAt(stamp: String) = "created $stamp"

    override val notCreated = "not created"
    override val translationNote =
        "Translation is in progress: untranslated parts are shown in Russian. · Перевод " +
            "выполняется по разделам: непереведённые части пока показываются по-русски."
    override val skinTitle = "Skin"
    override val skinNote =
        "A skin changes colours, type and the shape of borders — and nothing else: " +
            "readings, wording and calculations do not depend on it. Light and dark work " +
            "in both skins."
    override val themeTitle = "Theme"
    override val themeNote =
        "Dark is the primary theme: charts and figures read well at dusk. Light is for " +
            "bright sunlight."
    override val alarmsIntro =
        "An alarm is not triggered by a single spike: the level has to cross a threshold — " +
            "in absolute value or relative to this place's usual background — and hold " +
            "there for the stated time."
    override val nowLabel = "now"
    override val usuallyHere = "usually here"
    override val thresholdL1 = "L1 threshold"
    override val noBandToCompare =
        "This place's usual background is not collected yet — there is nothing to compare " +
            "the threshold with."
    // Не «Normal»: русское «Обычная» тоже избегает слова «норма» — уровень
    // чувствительности не должен читаться как утверждение об уровне излучения.
    override val sensitivityNormal = "Standard"
    override val sensitivityHigh = "High"
    override val sensitivityCustom = "Custom"
    override val sensitivityCustomNote = "dose-rate levels are set by hand"
    override val alarmSoundElsewhere =
        "The alarm's melody and vibration live in «Notifications and feedback»."
    override val alarmSoundTitle = "Alarm sound and vibration"
    override val alarmSoundNote =
        "the melody and vibration are set in the Android settings of the «Alarm» channel"

    override fun level1WithUnit(unit: String) = "level 1, $unit"

    override fun level2WithUnit(unit: String) = "level 2, $unit"

    override val saveLevels = "Save the levels"
    override val enterNumbers = "Enter numbers, for example 0.30"
    override val level1MustBePositive = "Level 1 must be greater than zero"
    override val level2BelowLevel1 = "Level 2 cannot be below level 1"
    override val levelsNote =
        "Level 1 is the alarm line on the charts and the deviation threshold; level 2 is " +
            "a large excess."
    override val profilesTitle = "Places"
    override val profilesIntro =
        "A place is a setting with its own usual background: home, office, a cottage. The " +
            "app can switch to it by itself when the phone joins a known Wi-Fi network. " +
            "Deleting a place leaves its measurements in the journal."
    override val profileNameHint = "name of the place"
    override val add = "Add"
    override val ownProfile = "+ Custom place"
    override val presets = "Presets:"
    override val active = "active"
    override val archived = "archived"
    override val hiddenFromPicker = "hidden from the picker"
    override val saveName = "Save the name"
    override val icon = "Icon"
    override val autoByWifi = "Switch on automatically by Wi-Fi"
    override val learnBackground = "Learn the usual background"
    override val wifiNote =
        "Wi-Fi networks. A network is recognised by the router's address, not by its " +
            "name: no location permission is needed for that."
    override val unbind = "unbind"
    override val notOnWifi = "the phone is not on Wi-Fi right now"
    override val networkAlreadyBound = "the current network is already bound to this place"
    override val bindCurrentNetwork = "Bind the current network"
    override val nestInProfile = "Nest inside a place"
    override val standalone = "standalone"
    override val unarchive = "Restore from the archive"
    override val archiveAction = "Archive"
    override val deleteProfile = "Delete the place"
    override val deleteProfileQuestion = "Delete this place?"
    override val usualBackgroundTitle = "Usual background"
    override val usualBackgroundIntro =
        "A place's usual background is fed only by admissible measurements. Left out are: " +
            "Search and experiments, a broken stream, half an hour after a deviation, and " +
            "any time the place is not confirmed. The measurements themselves are always " +
            "recorded."
    override val freezeLearning = "Freeze learning"
    override val graceNote =
        "How long to wait before deciding the phone has left a known network. Throughout " +
            "that time the place stays as it was, but the background is not fed."
    override val instrumentTitle = "Instrument"
    override val modelLabel = "model"
    override val serialNumber = "serial number"
    override val firmware = "firmware"
    override val bluetoothConnected = "connected"
    override val bluetoothConnecting = "connecting…"

    override fun bluetoothReconnecting(attempt: Int) = "reconnecting, attempt $attempt"

    override val bluetoothNoLink = "no link"
    override val serviceStopped = "service stopped"
    override val instrumentBattery = "instrument battery"
    override val temperature = "temperature"
    override val stream = "stream"
    override val streamActive = "running · 1 Hz"
    override val unitsTitle = "Units"
    override val unitMicroSv = "µSv/h"
    override val unitMicroSvNote = "microsieverts per hour — the SI unit"
    override val unitMicroR = "µR/h"
    override val unitMicroRNote = "microroentgens per hour · 1 µSv/h = 100 µR/h"
    override val unitsNote =
        "The conversion is for display only: measurements are stored in the instrument's " +
            "own units without loss of precision."
    override val interfaceTitle = "Interface"
    override val tabsNote =
        "Tabs of the menu: order and visibility. Settings stay reachable through the λ " +
            "icon on Monitor."
    override val alwaysVisible = "always visible"
    override val atLeastOneTab = "Besides Monitor, at least one tab has to remain."
    override val monitorBlocksNote =
        "Blocks of the Monitor screen. The value, the status and the dose-rate chart " +
            "always stay; the rest is your choice."
    override val blockTrend = "Trend/h"
    override val blockDoseToday = "Dose today"
    override val blockCountChart = "Count-rate chart"
    override val blockHardnessChart = "Hardness chart"
    override val blockStats = "Statistics under the chart (min/median/max/SD/n)"
    override val resetInterface = "Restore the default menu and blocks"
    override val visible = "visible"
    override val hidden = "hidden"
    override val onShort = "on"
    override val offShort = "off"
    override val licencesUnreadable = "The licence files could not be read."
    override val licencesTitle = "Licences"
    override val licencesBody =
        "The RadiaCode protocol is a port of the cdump/radiacode library (MIT). BLE — " +
            "Kable (Apache-2.0). The map — osmdroid (Apache-2.0), map data © " +
            "OpenStreetMap contributors (ODbL). Fonts IBM Plex Sans and IBM Plex Mono (OFL)."
    override val hideLicences = "Hide the licence texts"
    override val showLicences = "Show the licence texts"
    override val reading = "reading…"
    override val recentUpdates = "recent updates"
    override val whatChanged = "what changed"

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
