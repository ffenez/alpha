# Public release report — Alpha

Date: **19 August 2026**. Subject: the whole repository at `versionName 0.45.1`
(`versionCode 84`), 359 commits on `master`, 679 Kotlin files of which 275 are
tests.

This report records what was checked, what was changed, and what is still
unverified. Where a check was not run, it says so instead of claiming a pass.

---

## Release verdict

**READY WITH NON-BLOCKING ISSUES.**

No blocking issue was found: no secret or private data in the working tree or in
the history, no hidden network traffic, a license is present with attribution,
the release build succeeds and the checks were actually executed. What remains
are owner decisions and one hardware-coverage limitation, listed below.

### Blocking issues

None.

The earlier audit (`RELEASE_AUDIT.md`, 17 Aug 2026) recorded one blocker —
Android Auto Backup uploading the measurement database. It is fixed and the fix
was extended during this audit (see change 1).

### Non-blocking issues

1. **One device model is actually tested.** RadiaCode-110. The rest of the
   series shares the protocol and has per-model constants, but nobody has run
   the app against them. Stated as such in both READMEs.
2. **No screenshots.** The repository ships none, so both READMEs carry
   placeholders and an owner action. Six screenshots exist in git history from
   earlier debugging; they were inspected and carry no private data, but they
   show a stale UI and must not be reused.
3. **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** is declared. It is justified for a
   continuous-measurement app, but Google Play restricts this permission, so
   publishing on Play would need a declaration or its removal. Irrelevant for
   sideloading and F-Droid-style distribution.
4. **The release APK carries four ABIs** (~3.6 MB). Fine for sideloading;
   an owner who publishes releases may prefer per-ABI splits.
5. **A cold `./gradlew clean test` failed once** on this machine after seven and
   a half minutes and succeeded immediately afterwards without any source
   change; the same tasks pass repeatedly from a warm state. The machine has
   6 cores and ~8 GB free, and the failure produced no compiler diagnostic. It
   looks like a resource limit in the sandbox rather than a defect, but it was
   **not** reproduced or diagnosed, so it is recorded rather than dismissed.

---

## 1. Security and privacy audit

### Secrets and credentials

| Check | Result |
|---|---|
| API keys, tokens, passwords, private keys in the tree | **None.** The only matches for `token` are a regex variable, a nav-config parser and the Wi-Fi identity tests |
| Keystores / signing material | **None.** No `signingConfig` in Gradle; `.gitignore` excludes `*.keystore`, `*.jks`; the release APK contains no signing material |
| `.env`, `local.properties`, CI secrets | No `.env`, no CI configuration; `local.properties` is ignored |
| Secrets in git history | **None found.** Every non-source file ever added was enumerated and inspected (see below) |

### Private data

| Check | Result |
|---|---|
| Email addresses | None (regex hits were Kotlin `this@Label` qualifiers) |
| Developer absolute paths | **Found and fixed** — `/home/dev/.local/jdk` and `/home/dev/Android/sdk/...` appeared in `CLAUDE.md` and `docs/architecture/engine-and-algorithms.md`. Replaced with `./gradlew` and `$ANDROID_HOME` |
| Private IPs / hostnames | Only `192.168.1.1` used as the textbook example of a default gateway in KDoc and ADRs |
| Bluetooth MAC | Only `AA:BB:CC:DD:EE:FF` in tests |
| Real RadiaCode serials | **None.** The RC-XML fixture carries the dummy `RC-101-000000`; the spectrum fixture carries `<Name>validation fixture</Name>` and no serial |
| Real GPS coordinates | Tests use 55.75/37.60 — a city-centre landmark used as synthetic input for the grid maths, not a private location |
| EXIF in images | No images are tracked. Historical ones carry only a `DateTime` tag |
| Crash dumps, databases, build artefacts | `crashes.txt` was committed once and deleted; extracted and read — pure Compose stack traces, no identifiers. `build/`, `.gradle/`, `/apk/`, `.idea/` are ignored |

**History verdict:** every file ever added that is not source, docs or a build
file was extracted from history and examined: `1.jpg`, `2.jpg`, `1.png`–`4.png`
(app screenshots — main screen, charts, calibration diagnostics; no serial, no
map, no personal names), `crashes.txt` (stack traces only) and
`RC-XML Spectrum Diff-Calc.html` (a self-contained third-party HTML tool, no
data). **No history rewrite is required.**

### Android manifest and permissions

Every permission was justified against a real call site:

| Permission | Why | Verdict |
|---|---|---|
| `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_CONNECT` | Device link on API 31+ | Necessary; the flag correctly disclaims location derivation |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion=30`) | Same on API 26–30 | Correctly capped |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Track recording and the map position marker | Necessary; **no background location permission is requested** |
| `INTERNET` | osmdroid map tiles only | Necessary and documented in the manifest itself |
| `ACCESS_NETWORK_STATE` | Reads the current link to recognise a place by a hash of the gateway | Normal permission, no prompt, no SSID, no location |
| `FOREGROUND_SERVICE*` | Continuous measurement, `connectedDevice|location` | Types match what the service does |
| `POST_NOTIFICATIONS` | The foreground notification and the alarm | Necessary |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Long measurements | Justified; see non-blocking issue 3 |
| `VIBRATE` | Search feedback and the alarm | Necessary |
| `RECEIVE_BOOT_COMPLETED` | Optional "continue after restart", off by default | Justified; the receiver does nothing when the setting is off |

Exported components: `MainActivity` (launcher) and `BootReceiver` (needs the
boot broadcast). `MeasurementService` is not exported. No providers, no
`FileProvider`, no WebView, no custom intent filters, `usesCleartextTraffic=false`.

### Network behaviour

`git grep` over `app/src/main/**` and `protocol/**` for `HttpURLConnection`,
`OkHttp`, `Retrofit`, `URL(`, `openConnection`, `Socket(`, `WebView`, `okhttp`
and `ktor` returns **no matches**. The only component that opens a socket is
osmdroid, fetching OpenStreetMap raster tiles. No Google Play Services, no
Firebase, no analytics, no crash upload.

### Input hardening

- RC-XML parsing disables DTDs (`disallow-doctype-decl`) and both external
  general and parameter entities; XInclude is off.
- Imports are byte-limited (`MAX_IMPORT_BYTES`) before parsing, and the read
  path catches `Throwable` so a hostile content provider cannot crash the app.
- Exports go through the system picker; the app never writes to shared storage.

---

## 2. Changes made during this audit

1. **Device-to-device transfer closed.** `android:allowBackup="false"` alone
   does not stop it: Android's documentation states that for apps targeting
   API 31+, `allowBackup="false"` "disables cloud-based backup and restore …
   but doesn't disable device-to-device transfers", and a missing
   `<device-transfer>` section leaves that mode "fully enabled for all content".
   Added `app/src/main/res/xml/data_extraction_rules.xml` excluding every domain
   in both `<cloud-backup>` and `<device-transfer>`, and referenced it from the
   manifest. Verified by a successful release build.
2. **Developer machine paths removed** from `CLAUDE.md` and
   `docs/architecture/engine-and-algorithms.md`.
3. **`SECURITY.md` added** — reporting channel, what the app does with data, the
   attack surface, and the honest statement that the BLE link is unauthenticated.
4. **`.gitignore`**: `*.png` was blanket-ignored, which would have silently
   dropped README assets. Added an exception for `docs/images/*.png` plus a
   checklist file explaining what each image must be checked for.
5. **A forbidden word removed from a document.** `docs/design/eight-bit-skins.html`
   used «норма» as a colour-token label — a word the project's own rules ban in
   any wording. Replaced.
6. **A `HACK` marker rewritten** in `protocol/VsCodec.kt`. The fact it carried —
   newer firmware appends a trailing `0x00`, and the byte must be dropped or
   frame parsing desynchronises — is protocol-critical and was kept, with the
   upstream attribution; only the to-do-looking marker went.
7. **README set created** (see §6).

Nothing else in the source was changed by this audit. In particular no
measurement, threshold, statistic or wording was touched.

---

## 3. Scientific correctness audit

The pipeline `device data → parsing → storage → transformation → aggregation →
statistics → UI → export` was followed. What is verified by tests:

| Area | Status |
|---|---|
| Count rate and dose-rate units | Raw device units stored, converted only for display (`DoseUnits`, `ui/logic/Units.kt`); tested |
| Accumulated dose integration | Σ rate × measured seconds over hourly buckets; `DailyDose`, `DosePeriod`; 13 tests including coverage and empty days |
| Timestamps, gaps, out-of-order and interrupted sessions | Windows reconstruct counts by instrument time and shorten exposure across a hole (`CountWindow`, `NavigateEngine`); charts break the line at a gap |
| Downsampling and chart aggregation | ADR 004 quantile architecture, KLL sketches; tested |
| Median / percentiles | `analysis/quantiles`, baseline P10/median/P90 |
| Uncertainty and significance | Exact conditional binomial test with Clopper–Pearson bounds (`RateComparison`); Currie detection limits (`DetectionLimits`, 8 tests) |
| Baseline, deviation, episodes | `baseline/`; `LevelEventTracker` with hysteresis from the place's own spread; 21 tests including a 24-hour stable-background run that produces **zero** events |
| Spectrum channel → energy, calibration | `EnergyCalibration` from the file's own polynomial; validated against a real spectrum in `SpectrumValidationTest` |
| Background subtraction, smoothing | Time-normalised subtraction; smoothing is display-only and never mutates stored counts |
| Peak detection, area, significance | IAEA net-area variance from side bands; shape fit gated by net counts and by the Cash statistic |
| Isotope matching | Candidate-only, evidence cascade (ADR 006); "detected" is forbidden by test |
| Spectral ranges | `EnergyWindows`; channel assigned whole to a window, documented |
| Efficiency and activity | Only with a user-built curve; no extrapolation outside the calibrated range; upper bound when the line is not resolved |
| Hardness / energy response | Present, per-model resolution from `DeviceModel` |

NaN, infinity, zero division and insufficient samples are handled by returning
`null` and saying so, rather than by printing a number — this is the pattern
throughout `analysis/`. Raw measurements are never rewritten: corrections
(energy scale) apply at display and analysis time and are announced on screen.

**Real-data check.** A 4.16 million-count spectrum from the tested device is a
committed fixture. It caught two genuine defects during development — a shape
fit that rejected the strongest peak because the continuum model was too rigid,
and a scale correction that would have overstated high energies from a single
reference line. Both were fixed and pinned by `HighStatisticsSpectrumTest`.

**Not verified:** absolute dose accuracy against a reference instrument, and
any comparison of this app's numbers with the official app's numbers. Neither
was measured, and neither is claimed anywhere.

---

## 4. Dependency and license audit

`LICENSE` is present: **MIT**, "Copyright (c) 2026 alpha contributors". Not
chosen by this audit.

| Dependency | License | Attribution |
|---|---|---|
| AndroidX core, activity, Compose BOM, Material 3, Room, DataStore | Apache-2.0 | Standard |
| `com.juul.kable:kable-core` 0.37.1 | Apache-2.0 | — |
| `org.osmdroid:osmdroid-android` 6.1.20 | Apache-2.0 | In `NOTICE` and in-app |
| Kotlin stdlib / coroutines 1.10.2 | Apache-2.0 | — |
| IBM Plex fonts (bundled `.ttf`) | SIL OFL 1.1 | `NOTICE` + full OFL text shipped in `assets/licenses/` |
| Protocol port of `cdump/radiacode` | MIT | `NOTICE` + notice shipped in `assets/licenses/` |
| Test-only: JUnit 4, Robolectric, sqlite-jdbc, org.json | EPL/Apache/Public-domain-ish | Not in the release APK — verified by inspecting the APK |

Native libraries in the APK are AndroidX's own (`libandroidx.graphics.path.so`,
`libdatastore_shared_counter.so`) — known provenance, no unexplained binaries.

**Not verified:** a CVE scan. No dependency-vulnerability tooling was run; there
is no network-enabled scanner configured in this repository, and versions were
not checked against an advisory database.

---

## 5. Release build

| Check | Result |
|---|---|
| `./gradlew clean` then a cold full build | Succeeded on retry; see non-blocking issue 5 |
| R8 / ProGuard | `isMinifyEnabled = true` on release with `proguard-android-optimize.txt` + project rules |
| Debug menus, fake data, test endpoints | None. `android.util.Log` does not appear anywhere in production code |
| `applicationId` / versioning | `app.alpha`, 0.45.1 (84); a test enforces that the newest release note matches `BuildConfig.VERSION_NAME` |
| APK contents | No test classes, no developer paths (`strings` over `classes.dex` finds none), only expected assets |
| Signing secrets in repo | None; the release APK is produced unsigned |

---

## 6. Documentation produced

- `README.md` — English, primary.
- `README_RU.md` — Russian, a real localisation rather than a translation pass.
- `README_VERIFICATION.md` — every material claim mapped to a file or a test,
  the official-app comparison with its source and date, claims deliberately
  omitted, and the screenshot privacy audit.
- `SECURITY.md`, `RELEASE_CHECKLIST.md`, `docs/images/README.md`.

The official-app comparison rests on <https://www.radiacode.com/software>
fetched on 19 Aug 2026. The vendor's Android manual is a scanned PDF and could
not be read mechanically, so every capability not stated on the features page is
marked `?` rather than "not supported".

---

## 7. Commands actually run

All from the repository root, each as a separate invocation:

```
./gradlew clean                  BUILD SUCCESSFUL
./gradlew test                   BUILD SUCCESSFUL — 2104 tests, 0 failures
./gradlew :app:smokeTest         BUILD SUCCESSFUL — 118 tests, 0 failures
./gradlew :app:lintDebug         BUILD SUCCESSFUL — no errors reported
./gradlew :app:assembleRelease   BUILD SUCCESSFUL — app-release-unsigned.apk, 3.65 MB
```

Counts come from the JUnit XML in `app/build/test-results/` and
`protocol/build/test-results/`, not from the console summary.

Not run, and therefore not claimed: instrumented (device) tests — there is no
`androidTest` source set and no device available; dependency vulnerability
scanning; any measurement against a reference instrument.

---

## 8. Owner actions before publication

1. **Screenshots.** Take fresh ones of 0.45.1, check them against
   `docs/images/README.md`, commit them to `docs/images/`, and replace the
   placeholders in both READMEs.
2. **Repository URL.** Replace `<repository-url>` / `<адрес-репозитория>`.
3. **Releases and signing.** Decide whether to publish GitHub Releases; if so,
   generate a release key and keep it **outside** this repository.
4. **Task briefs.** `PUBLIC_RELEASE_AUDIT.md` and `README_RELEASE_TASK.md` are
   the instructions for this work and are currently untracked. Decide whether
   they belong in a public repository.
5. **Copyright line.** `LICENSE` says "alpha contributors" — confirm that is the
   attribution you want publicly.
6. **Play Store**, if that is the target: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
   needs a policy declaration, and a data-safety form will have to describe the
   map-tile connection.
