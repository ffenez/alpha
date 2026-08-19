package app.alpha.ui.text

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
    override val cpsUnit = "s⁻¹"
    override val countRate = "Count rate"
    override val hardness = "Hardness"
    override val trendPerHour = "Trend"
    override val scaleThresholdTick = "threshold"
    override val indicatorTitle = "Instrument scale"
    override val indicatorDial = "Dial"
    override val indicatorBar = "Bar"
    override val indicatorNote =
        "The reading is the same: a dial reads at arm's length, a bar takes one row and " +
            "leaves more of the screen to the chart."
    override val modeObserve = "Observe"
    override val modeSearchShort = "Search"
    override val tilePlaceBackground = "place background"
    override val tilePerHour = "per hour"
    override val tilePerDay = "per day"
    override val doseToday = "Today"
    override val doseAccumulatedToday = "Collected today"
    override val placeFingerprint = "Place fingerprint"

    override val groupMeasurement = "Measurement"
    override val groupApp = "Application"
    override val groupOther = "Other"
    override val groupDevice = "Instrument"
    override val groupSystem = "System"
    override val settingsAlarms = "Alarms"
    override val settingsAlarmsSub = "thresholds, dwell time, sensitivity"
    override val settingsProfiles = "Places and background"
    override val settingsProfilesSub = "places, Wi-Fi networks, learning the usual background"
    override val settingsNotifications = "Feedback"
    override val settingsNotificationsSub = "search sound, vibration, alarm"
    override val settingsView = "Interface"
    override val settingsViewSub = "language, skin, theme, units, Monitor blocks"
    override val settingsDevice = "Instrument"
    override val settingsDeviceSub = "model, firmware, instrument sound and vibration"
    override val settingsBackup = "Data and backups"
    override val searchWordsBackup =
        listOf("backup", "restore", "copy", "transfer", "export", "import")
    override val settingsData = "Diagnostics"
    override val settingsDataSub = "background recording, state reports"
    override val settingsProfilesNone = "none"
    override val settingsSearchPlaceholder = "Search settings"
    override val settingsSearchEmpty = "Nothing found"
    override val settingsPickSection = "Pick a section on the left"
    override val searchWordsAlarms =
        listOf("alarm", "threshold", "level", "sensitivity", "alert")
    override val searchWordsProfiles =
        listOf("profile", "background", "place", "learning", "home", "wi-fi", "wifi")
    override val searchWordsSound =
        listOf("sound", "vibration", "clicks", "tone", "signal", "feedback")
    override val searchWordsView =
        listOf("language", "theme", "skin", "scale", "font", "units", "colour", "color", "tabs")
    override val searchWordsDevice =
        listOf("instrument", "device", "battery", "firmware", "serial", "bluetooth", "calibration")
    override val searchWordsData =
        listOf(
            "storage", "history", "report", "diagnostics", "memory", "recording",
            "spectrogram", "clear",
        )
    override val searchWordsAbout = listOf("version", "licences", "licenses", "updates")
    override val settingsAbout = "About"
    override val settingsAboutSub = "version, updates, licences, diagnostics"

    override val languageTitle = "Language"
    override val languageSystem = "System"

    override val statusNoData = "No measurements"
    override val statusMeasuring = "Measuring"
    override val statusAboveL1 = "Above your threshold"
    override val statusUsual = "Usual here"
    override val statusUsualShort = "Usual here"
    override val statusAboveUsual = "Above usual"
    override val statusAboveThreshold = "Above the threshold, checking"
    override val statusAboveThresholdShort = "Above threshold"
    override val statusAlert = "Holding above the threshold"

    override fun explainMeasuring(threshold: String) =
        "below your threshold $threshold; the usual background of this place is still being learnt"

    override fun detailNoBaseline(threshold: String) =
        "your threshold $threshold · not much measured here yet"

    override fun detailUsual(range: String, collected: String) =
        "usually here $range"

    override fun detailAboveUsual(range: String, held: String) =
        "usually here $range · $held"

    override fun detailAboveThreshold(threshold: String, held: String, required: String) =
        "your threshold $threshold · $held of $required"

    override fun detailAlert(reference: String, held: String) = "$reference · $held"

    override fun referenceThreshold(threshold: String) = "your threshold $threshold"

    override fun referenceProfileBand(range: String) = "usually here $range"

    override fun held(text: String) = "for $text already"

    override fun seconds(value: Long) = "${value}s"

    override fun minutes(value: Long) = "$value min"

    override fun hoursMinutes(hours: Long, minutes: Long) = "${hours}h ${minutes}min"

    override fun agoSeconds(value: Long) = "$value s ago"


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
    override val exportHtml = "HTML report"
    override val exportCsv = "CSV — channels and energies"
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
            "up); «Save snapshot» will store the sum"
    override val noLiveAccumulation =
        "no live accumulation yet — the saved snapshot is shown"
    override val continuationWarning =
        "The instrument accumulates its spectrum independently of the app. If the snapshot " +
            "was taken from the current accumulation without a reset, counts are counted " +
            "twice — reset the spectrum first."
    override val scaleLinear = "Lin"
    override val scalePower = "Power"
    override val scaleLog = "Log"

    override fun powerDegree(root: Int) = "power 1/$root"

    override val spectrumModeRaw = "Spectrum"
    override val spectrumModeMinusBackground = "− background"
    override val smoothing = "smooth"
    override val energyRanges = "energy ranges"
    override val peakTableEnergy = "E, keV"
    override val peakTableNet = "area"
    override val peakTableSignificance = "significance"
    override val peakTableCandidate = "possible match"
    override val notEnoughForPeaks =
        "too little data for peak analysis — accumulate at least a minute"
    override val noPeaksFound = "no pronounced peaks above the continuum were found"
    override val peakTableCaveat =
        "a possible match ≠ a detection · confirmation is needed: accumulate longer"
    override val reset = "Reset"
    override val resetSpectrumTitle = "Reset the spectrum?"
    override val resetSpectrumBody =
        "Accumulation starts over — the instrument clears its spectrum too. Saved " +
            "snapshots stay in History."
    override val cancel = "Cancel"

    override fun edgeCounts(counts: String) = "at the top edge of the scale: $counts counts"

    override val noSpectrumBackground =
        "no background recorded — record a spectrum of ordinary surroundings and the " +
            "overlay and «minus background» will appear"

    override fun sessionsCount(total: Long) = "$total sessions"

    override val selectAll = "Select all"
    override val clearAll = "Clear all"

    override fun selectedCount(count: Int) = "selected: $count"

    override val readingJournal = "reading the journal…"
    override val noSessionsYet = "no sessions yet"
    override val noSpectraYet = "no spectrum snapshots yet"
    override val backgroundSpectrum = "background"
    override val spectrumExplained =
        "A snapshot keeps the whole spectrum: it later shows what the count was made of."
    override val sessionExplained =
        "A session is an uninterrupted period of measurement: it opens when the " +
            "instrument connects and closes when it disconnects."
    override val showMore = "Show more"
    override val accumulatedDose = "Accumulated dose"

    override fun todayWithUnit(unit: String) = "today, $unit"

    override val days7 = "7 days"
    override val days30 = "30 days"
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
    override val snapshotOpensActions = "a tap opens the snapshot, «⋮» holds its actions"
    override val openSnapshot = "Open the spectrum"
    override val chooseSnapshotToCompare = "Which snapshot to compare with"

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
        "Source of the value: meas. — measured by the instrument · calc. — computed from " +
            "measurements · stat. — the result of the place's statistical model"
    override val nowSection = "Now"
    override val usualRangeHere = "Usual range here"
    override val notASafetyConclusion =
        "This conclusion describes a difference from your own usual background in this " +
            "place. It is not a radiation-safety assessment."
    override val dataVolume = "How much data"
    override val usedForComparison = "Used for the comparison"
    override val suitableMeasurements = "of suitable measurements of this place"
    override val measurementsCount = "Measurements"
    override val measurementsCountNote =
        "readings of the instrument used for the statistics. The instrument writes about " +
            "once a second, but with gaps this number is smaller than the observed time"
    override val calculationsSection = "Calculations and formulas"
    override val countIsNotDose =
        "counts are handy for spotting changes, but on their own they do not show the dose: " +
            "the contribution of an event depends, among other things, on its energy"
    override val deviceErrorNote =
        "the ± on the dose rate is the instrument's own estimate for this reading"
    override val deviceErrorBudget =
        "the instrument's ± is not the full measurement uncertainty: that would also " +
            "include calibration and systematic effects."
    override val insideUsualRange = "inside the usual range"
    override val aboveUsualRange = "above the usual range"
    override val belowUsualRange = "below the usual range"
    override val spectralComparedPlain =
        "The shape of the current spectrum is compared with the one usual for this place, " +
            "not with an absolute level. The conclusion describes the composition of the " +
            "radiation."
    override val spectralTooLittlePlain =
        "The comparison with the place's reference has started, but there is little data yet."
    override val shapeStatistics = "Shape-comparison statistics"
    override val poissonNote = "The count's uncertainty is Poisson 1σ ≈ √(N/τ), τ = 1 s."
    override val dataSection = "Data"
    override val profile = "Place"
    override val outsideProfile = "outside any place"
    override val comparisonSection = "Comparison with the place"
    override val historicalRange = "The usual range of this place"
    override val notCollectedYet = "not collected yet"
    override val comparisonRuns = "Compared"

    override fun withThresholdL1(value: String) = "against the L1 threshold $value"

    override val thresholdIsNotSafety =
        "L1 is a parameter of the app's alarm, not a safety limit."
    override val currentValue = "Current value"
    override val position = "Position"
    override val bandExplained =
        "About 80 % of this place's suitable measurements fell inside this range (from P10 " +
            "to P90). It is the history of the place itself, not a radiation-safety limit."
    override val belowP10 = "below P10"
    override val aboveP90 = "above P90"
    override val insideBand = "inside P10–P90"
    override val profileStatistics = "Statistics of the place"
    override val median = "Median"
    override val madNote =
        "median(|xᵢ − median|) — a robust description of the observed spread that assumes " +
            "no normal distribution. It is not the instrument's uncertainty"
    override val usableData = "Data used for comparison"
    override val usableDataNote =
        "only measurements that passed the admission checks are counted: a confirmed place, " +
            "an uninterrupted data stream, a reading with an acceptable uncertainty"
    override val minuteBuckets = "One-minute intervals"
    override val honestN = "the honest n of the order statistics"
    override val notEnoughData = "Not enough data"
    override val updating = "Updating"
    override val temporarilyNotUpdating = "Temporarily not updating"
    override val updatingNote =
        "New suitable measurements keep extending the usual range of this place."
    override val notUpdatingNote =
        "New measurements are saved but are not being added to this place's usual " +
            "background for now, so that an unusual episode does not gradually come to " +
            "count as usual."
    override val state = "Right now"
    override val excludedSection = "Which measurements did not go into the usual background"
    override val excludedNow = "Why right now"
    override val excludedFromStatistics = "Not used for the usual background"
    override val statisticsState = "Usual background"
    override val quarantineNote =
        "After a noticeable excursion the app stops topping up the usual background for a " +
            "while. Those measurements stay in the history — they simply take no part in " +
            "working out what is usual here."
    override val howDetected = "How a deviation is detected"
    override val absoluteThresholdL1 = "Absolute threshold L1"
    override val relativeCriterion = "Threshold relative to the usual range"

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

    override val spectralComparison = "Spectral comparison"

    override val searchFeedbackTitle = "Search feedback"

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
    override val skinTitle = "Skin"
    override val skinTerminal = "Science terminal"
    override val skinEightBit = "8-bit"
    override val themeSystem = "System"
    override val themeDark = "Dark"
    override val themeLight = "Light"

    override fun signalDbm(value: Int) = "$value dBm"

    override fun alarmPreset(level: String, factor: String, held: String) =
        "from $level or ×$factor of the profile's P90, $held"

    override val retentionTitle = "Measurement history"
    override val retentionKeepAll = "everything"

    override fun retentionDays(days: Int) = "$days days"

    override val retentionNote =
        "Per-second instrument records older than the limit are deleted. Long-period charts, " +
            "place statistics, session summaries, spectra and tracks remain — only the raw " +
            "detail is lost. By default everything is kept."

    override val scaleTitle = "Scale"
    override val scaleFont = "text"
    override val scaleElements = "elements"

    override fun scalePercent(percent: Int) = "$percent %"

    override val scaleReset = "Back to 100 %"
    override val crystalOrganicPlastic = "organic plastic"
    override val modeOff = "off"
    override val modeClicks = "clicks"
    override val modeTone = "tone"
    override val modeVibro = "vibration"
    override val themeTitle = "Theme"
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
    override val sensitivityHigh = "Sensitive"
    override val sensitivityCustom = "Custom"
    override val sensitivityCustomNote = "dose-rate levels are set by hand"
    override val alarmModeTitle = "Mode"
    override val thresholdNow = "Threshold now"
    override fun relativeCriterion(factor: String) = "or ×$factor of the profile's usual level"
    override val howItWorks = "How does this work?"
    override fun sensitivityNormalNote(held: String) =
        "Fewer false alarms · confirmed after $held"
    override fun sensitivityHighNote(held: String) =
        "Notices small changes sooner · confirmed after $held"
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
    override val updateBackground = "Keep learning the usual background"
    override val updateBackgroundNote = "New calm measurements refine the model of this place."
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

    override fun streamNoNewData(seconds: Long) = "no new data · $seconds s"

    override val streamLost = "the link to the instrument is lost"
    override val streamNoDataYet = "no current data"

    override fun lastMeasurementAgo(seconds: Long): String {
        val text = when {
            seconds < 60 -> "$seconds s"
            seconds < 3600 -> "${seconds / 60} min"
            else -> "${seconds / 3600} h"
        }
        return "last measurement $text ago"
    }
    override val unitsTitle = "Units"
    override val unitMicroSv = "µSv/h"
    override val unitMicroR = "µR/h"
    override val unitDoseMicroSv = "µSv"
    override val unitDoseMicroR = "µR"
    override val colorsTitle = "Colours"
    override val homeLayoutTitle = "Home"
    override val hintsNote = "Help and extra explanations on the screens."
    override val doseTintNote = "The main value changes colour when it differs noticeably from usual."
    override val interfaceTitle = "Interface"
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

    override val startOnBootTitle = "After a restart"
    override val startOnBootNote =
        "Keep measuring once the phone boots: the app starts the service itself and " +
            "connects to the last instrument. Off by default."

    override val deviceSignals = "Instrument signals"
    override val deviceSignalsNote =
        "The instrument's own sound and vibration. They work even with the phone " +
            "disconnected or the app closed, and are separate from Search feedback."
    override val deviceSound = "Instrument sound"
    override val deviceVibro = "Instrument vibration"
    override val deviceSignalsUnknownNote =
        "The app can switch them on and off, but cannot ask the instrument what is set " +
            "there right now: until the first command the state is unknown."
    override val deviceSignalsOfflineNote =
        "The instrument is not connected — there is nowhere to send the command."

    override fun baselineStats(median: String, iqr: String, mad: String, buckets: Int) =
        "median $median · P25–P75 $iqr · MAD $mad · n $buckets one-minute intervals"
    override val hintsTitle = "Explanations"
    override val doseTintTitle = "Tint the number by the place's background"
    override val doseTintFactorTitle = "Crimson at"
    override fun doseTintFactorLabel(factor: String) = "×$factor"

    override val mapScaleTitle = "Track colour"
    override val mapScaleAbsolute = "by place"
    override val mapScaleContrast = "by route"
    override val mapScaleManual = "by hand"
    override val mapScaleDoseAnchors = "dose bounds, µSv/h"
    override val mapScaleCpsAnchors = "count bounds, s⁻¹"
    override val mapScaleManualHint =
        "Bounds separated by a space: between them the colour runs " +
            "from green to crimson. This is the picture's scale only — it says " +
            "nothing about whether that is much or little."
    override val stateUnknown = "state unknown"
    override val stateRejected = "the instrument refused · try again"
    override val stateOnByApp = "switched on by this app"
    override val stateOffByApp = "switched off by this app"
    override val on = "On"
    override val off = "Off"
}
