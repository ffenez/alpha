# README verification map

Every material claim in `README.md` / `README_RU.md`, and where it can be
checked. Written 19 August 2026, rechecked against `versionName 0.46.0`
(versionCode 85), when both READMEs were shortened and the instrument serial
was removed from every exported file.

Commands quoted here were actually run; their results are in
`PUBLIC_RELEASE_REPORT.md`.

---

## 1. What the app is and does

| Claim | Where it is verified |
|---|---|
| Connects to RadiaCode over BLE | `protocol/` (Kotlin port of the community protocol), `app/src/main/kotlin/app/alpha/device/DeviceConnection.kt` |
| Records continuously in a foreground service | `app/src/main/kotlin/app/alpha/service/MeasurementService.kt`; manifest declares `foregroundServiceType="connectedDevice|location"` |
| Learns what a place normally reads | `app/src/main/kotlin/app/alpha/baseline/`, ADR 002 (`docs/adr/002-baseline-method.md`) |
| Data stays in the app's database | Room database `app/src/main/kotlin/app/alpha/data/db/`; no network code (see §4) |
| Two denominators on one instrument screen | `ui/screens/InstrumentScreen.kt` (mode switch), `MonitorScreen.kt` (place median), `NavigateSection.kt` (marked reference) |
| Search feedback: clicks, tone, vibration, combinable, service-owned | `ui/feedback/FeedbackHub.kt`, `ui/logic/SearchFeedbackChannels.kt`; started/stopped by `MeasurementService` |
| One journal record per episode | `baseline/LevelEvent.kt`, `data/MeasurementRepository.openEpisode/updateEpisode`; tests `LevelEventTrackerTest`, `EpisodeJournalTest` |
| Level change and threshold are separate kinds | `baseline/LevelEventKind`; `EventEntity.SOURCE_LEVEL_CHANGE` / `SOURCE_THRESHOLD` |
| Spectrum: accumulation, snapshots, background subtraction | `ui/screens/SpectrumScreen.kt`, `ui/logic/SpectrumFrame.kt`, `analysis/SpectrumDisplay.kt` |
| SNIP continuum | `analysis/SnipContinuum.kt`; tests `SnipContinuumTest` (9) |
| Asymmetric line-shape fit | `analysis/PeakShapeFit.kt`; tests `PeakShapeFitTest` (9) |
| Isotope **candidates** with an evidence cascade | `analysis/evidence/`, ADR 006; `ui/logic/PeakEvidence.kt` |
| Spectral ranges | `ui/screens/EnergyWindowsSection.kt`, `analysis/EnergyWindows.kt` |
| Spectrogram | `ui/screens/SpectrogramScreen.kt`, ADR 007 |
| Efficiency curve → activity in Bq | `analysis/EfficiencyCurve.kt`, `analysis/ActivityEstimate.kt`, `ui/logic/PeakActivity.kt`; tests `EfficiencyCurveTest` (9), `ActivityEstimateTest` (10) |
| Track recording, colour trail, accumulated grid, route comparison | `ui/screens/MapScreen.kt`, `data/db/TrackGridSql.kt`, `ui/screens/RouteCompareScreen.kt` |
| Accumulated dose over 7/30/90 calendar days with coverage | `ui/logic/DosePeriod.kt`, `ui/logic/DailyDose.kt`, `ui/screens/DoseScreen.kt`; tests `DosePeriodTest` (13) |
| Place fingerprint | `analysis/Fingerprint.kt`, ADR 005, `ui/screens/FingerprintScreen.kt` |
| Export HTML/CSV/JSON/GeoJSON/GPX/N42/XML through the system picker | `ui/screens/ExportActions.kt` (`ExportFile` enum), `data/export/` |
| No exported file carries the instrument serial | `data/export/RcXml.kt` (no `<SerialNumber>`), `data/export/N42.kt` (no `<RadInstrumentIdentifier>`), `data/export/DebugReport.kt`; tests `SpectrumExportTest.«a live session export names the model but not the instrument»`, `N42Test.«the instrument identifier is never written»`, `RcXmlTest` |

## 2. Scientific claims

| Claim | Where it is verified |
|---|---|
| Baseline uses median and P10/P90, not a mean | `baseline/`, ADR 002; `analysis/quantiles/` |
| Exact conditional binomial test with Clopper–Pearson bounds | `analysis/RateComparison.kt` and its KDoc; tests `RateComparisonTest` |
| Currie detection limit shown with a refusal | `analysis/DetectionLimits.kt`; tests `DetectionLimitsTest` (8); wording in `ui/logic/SearchVerdict.kt`, `ui/logic/NavigateVerdict.kt` |
| Peak significance from side bands with known variance | `analysis/PeakDetection.kt` (IAEA net-area variance in KDoc) |
| Fit judged by the Cash statistic, only above a net-count floor | `analysis/PeakShapeFit.kt` (`MIN_FIT_COUNTS`, `MAX_REDUCED_C`) |
| Gaps drawn as gaps | `ui/chart/`, `ui/logic/ChartSeriesModel.kt`; a day without measurements is `null`, see `DoseScreen.kt` and `DosePeriodTest` |
| Wording never says safe/normal/dangerous | Enforced by tests, e.g. `SearchVerdictTest`, `CalibrationStringsTest`, `EfficiencyStringsTest`, `NavigateVerdictTest` |
| A peak is a candidate, never a detection | Rule in `CLAUDE.md`; `NavigateVerdictTest` forbids «обнаружен» in output |
| Bq/kg is not implemented | No such calculation exists; `analysis/ActivityEstimate.kt` produces Bq only, and only with an `EfficiencyCurve` |
| Activity is geometry-bound and ignores self-absorption/coincidences | Stated in `ActivityEstimate.kt` KDoc and shown on screen (`EfficiencyStrings.activityGeometryNote`) |
| Scale correction only on an explicit tap, stated on the picture | `analysis/ScaleCorrection.kt`, `ui/screens/CalibrationScreen.kt`, `SpectrumStrings.scaleCorrected` |
| The tested spectrum shows a real instrument scale drift | `HighStatisticsSpectrumTest.«шкала этого прибора уходит вниз на высоких энергиях»` |

## 3. Hardware claims

| Claim | Basis | Confidence |
|---|---|---|
| RadiaCode-110 is the tested device | Development device; real 4.16 M-count spectrum in `app/src/test/resources/spectra/alpha-20260819-143354.csv`, used by `HighStatisticsSpectrumTest` | The fixture is a real measurement, but it carries **no model field** — the model attribution rests on the developer's statement, not on the file |
| 101 / 102 / 103 / 103G expected to work | Same protocol; per-model constants in `device/DeviceModel.kt` | **Untested** — stated as such in both READMEs |
| Zero is a counter only | `DeviceModel.ZERO(organicPlastic = true)`, `isSpectrometer = this != ZERO`; spectral analysis refuses | Logic verified, **device untested** |

## 4. Privacy claims

| Claim | How it was checked |
|---|---|
| No network code in the app | `git grep -nIE "HttpURLConnection|OkHttp|Retrofit|URL\(|openConnection|Socket\(|WebView|okhttp|ktor" -- app/src/main/** protocol/**` → **no matches** |
| INTERNET permission exists only for map tiles | `AndroidManifest.xml` (documented in place); the only dependency that opens sockets is `osmdroid` |
| No Google Play Services / Firebase | grep over `*.kts`, `*.toml`, `*.xml` → no matches |
| No analytics / telemetry / crash reporting to a server | No network code (above); `data/export/CrashLog.kt` writes to the app's private directory and is exported only inside a user-triggered debug archive |
| Cloud backup and D2D transfer excluded | `android:allowBackup="false"` **plus** `res/xml/data_extraction_rules.xml` with both `<cloud-backup>` and `<device-transfer>` excluding every domain. Necessity confirmed against Android docs: with `allowBackup="false"` on API 31+, D2D "doesn't disable device-to-device transfers for the app" |
| Location read in two places only | `service/MeasurementService.kt` (track recording, user-started) and `ui/map/MapLocation.kt` (the position marker, only while the Map tab is composed and only with the permission granted — re-checked at the call site). No other `requestLocationUpdates` exists in the tree. Place recognition uses `context/NetworkIdentity` (SHA-256 of the gateway address) and no location at all |
| No account | No auth code, no credentials anywhere in the tree |
| Exported files identify the model, not the unit | `RcSpectrum` has no serial field at all, so no XML path can write one; `N42.write` has no serial parameter; `DebugSnapshot` has no serial field. The model comes from `SpectrumExport.modelFromSerial` and is a series name. The serial stays in the database (`SpectrumSnapshotEntity.deviceSerial`), where `SpectrumEpoch`, `ResolutionSource`, `BackgroundRecord` and `BackupKey` use it to tell one detector from another, and in the app's own backup |

## 5. Build claims

| Claim | Basis |
|---|---|
| JDK 17, compileSdk 35, minSdk 26, targetSdk 35 | `app/build.gradle.kts` |
| Kotlin 2.1.0, AGP 8.7.3 | `gradle/libs.versions.toml` |
| The four checks are the ones the READMEs list | `./gradlew test`, `:app:smokeTest`, `:app:lintDebug`, `:app:assembleRelease`; run separately, as `CLAUDE.md` requires |
| Release build is unsigned, no key in the repository | `app/build.gradle.kts` has no `signingConfig`; `.gitignore` excludes `*.keystore`, `*.jks`; APK inspection shows no signing material |

## 6. Comparison with the official app

Source: <https://www.radiacode.com/software>, fetched **19 August 2026**.
The Android manual (<https://downloads.radiacode.com/EN/RC-10x_Android.pdf>) is
a scanned PDF whose text could not be extracted, so it supports nothing here.

Both READMEs now state this comparison as prose rather than a table; the rows
below are what that prose is built from.

| Row | Official source | Result |
|---|---|---|
| Live measurement dashboard | features page: "real-time dashboard displaying key data" | ✓ documented |
| Gamma spectrum, background subtraction | features page: spectrum overlay, subtraction, filtering, background selection | ✓ documented |
| Spectrogram | features page: "a collection of gamma spectra recorded at specified time intervals" | ✓ documented |
| Radiation map with tracks | features page: "records radiation measurements on Google Maps, creating a color-coded track" | ✓ documented |
| Libraries of spectra and tracks | features page: "Save your research tracks and spectrums, in dedicated libraries" | ✓ documented |
| Isotope hints | features page: "isotope visualization capability" | ✓ documented (its method is not described, so the depth of the two is not comparable from documentation) |
| Accumulated dose screen | Dose Monitor described in search-result summaries of the vendor site | ✓ documented |
| Photo attached to records | features page: "gamma camera … photo documentation" | ✓ documented; **not implemented here** |
| Everything marked `?` | not found on the features page | **Not confirmed either way** — deliberately not written as "No" |

## 7. Claims deliberately omitted as unverified

- Any statement that Alpha is more accurate, more scientific or better than the
  official app.
- Any claim about official-app behaviour that is not on the vendor features
  page — including whether it has baselines, detection limits, route comparison
  or N42 export.
- Any Bq/kg, contamination or food-safety capability.
- Any support statement for RadiaCode models other than the 110.
- Battery life, measurement accuracy figures, and comparisons against reference
  instruments: none were measured.

## 8. Screenshot privacy audit

**No screenshots ship in this repository.** `docs/images/` contains only the
checklist that images must pass.

Six screenshots (`1.jpg`, `2.jpg`, `1.png`–`4.png`) were committed earlier and
deleted; they remain in git history. They were extracted and inspected on
19 Aug 2026:

- EXIF: only a `DateTime` tag on the two JPEGs; no GPS, no `Make`/`Model`, no
  `Software`, no author fields.
- Pixels: main screen, charts and calibration diagnostics. No serial number, no
  map with a real location, no personal profile name, no notification content.

Conclusion: history carries **no private data** from these files, so no history
rewrite is required on their account. They are stale UI and must not be reused
as README assets.

## 9. Remaining owner actions

1. Take fresh screenshots of 0.45.1, run them through `docs/images/README.md`,
   and replace the placeholders in both READMEs.
2. Replace `<repository-url>` / `<адрес-репозитория>` with the real clone URL.
3. Decide on GitHub Releases; if used, sign with a key stored outside the repo.
4. Decide whether `PUBLIC_RELEASE_AUDIT.md` and `README_RELEASE_TASK.md` (the
   task briefs, currently untracked) should be published at all.
