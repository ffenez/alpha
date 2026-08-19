<h1 align="center">Alpha</h1>

<p align="center">
  An independent Android companion for RadiaCode detectors that keeps measuring,
  compares what it sees against what is usual for the place, and says how sure it is.
</p>

<p align="center">
  <b>English</b> · <a href="README_RU.md">Русский</a>
</p>

<p align="center">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84">
  <img alt="Kotlin 2.1" src="https://img.shields.io/badge/Kotlin-2.1-7F52FF">
  <img alt="License MIT" src="https://img.shields.io/badge/License-MIT-blue">
</p>

> [!NOTE]
> **Not affiliated with RadiaCode Ltd.** This is a third-party application built
> on a community-reverse-engineered BLE protocol. It is not endorsed by, and
> carries no support from, the device manufacturer.

<!-- OWNER ACTION: hero collage. No screenshots ship with this repository yet;
     see docs/images/README.md for the privacy checklist every image must pass. -->

> **Screenshots pending.** The repository ships no screenshots yet — see
> [owner actions](#before-you-publish-owner-actions).

---

## What it is

Alpha connects to a RadiaCode detector over Bluetooth LE and runs as a
measurement instrument rather than a viewer: it records continuously in a
foreground service, learns what a place normally reads, and reports a change
only when a statistical test supports it. Spectra, routes, history and the
learned baselines stay in the app's own database on the phone.

It is a different workflow next to the official RadiaCode app, not a
replacement for it — the emphasis here is on long-running measurement,
comparison against a personal reference, and analysis that shows its own
uncertainty.

## Why it exists

- **A number alone says little.** 0.17 µSv/h means nothing until you know what
  this room usually reads and how much two neighbouring seconds differ anyway.
  Alpha keeps a per-place baseline and phrases every ratio with its denominator.
- **Statistics before adjectives.** A difference is announced when a test
  accepts it, and refused out loud when it does not — together with the
  sensitivity that refusal was made at.
- **The search problem is its own problem.** Finding *where it is stronger*
  needs a fast quantity, an audible channel and a reference point you set by
  hand — not a dose-rate readout.
- **Local-first.** No account, no telemetry, no upload. The only outbound
  traffic in the whole app is map tiles.

## Features

| Area | What it does |
|---|---|
| **Instrument** | One screen with two denominators: *observation* compares against what is usual for this place, *search* against a reference point you mark. Same dial, same geometry, different question. |
| **Search feedback** | Clicks per pulse, a tone whose pitch follows the ratio, and vibration — combinable, running from the measurement service so they survive a locked screen and a minimised app. |
| **Confirmed episodes** | An excess becomes one journal record with a start, an end, duration, range and coverage — not a row per detector trigger. Level change and threshold crossing are separate kinds. |
| **Spectrum** | Live accumulation and snapshots, background subtraction, SNIP continuum, peak search with an asymmetric line-shape fit, isotope *candidates* with an evidence cascade, spectral ranges, spectrogram. |
| **Efficiency & activity** | Build an efficiency curve from a certified source, then a matched photopeak reports activity in becquerels with its uncertainty — or an upper bound when the line is not resolved. |
| **Map & routes** | Track recording with a colour-coded trail, an accumulated radiation grid across recordings, and route comparison. |
| **Accumulated dose** | Dose integrated from measurements over 7/30/90 calendar days, with how much of the period was actually measured. |
| **Place fingerprint** | Dose, count rate and spectrum shape of a place compared against a stored reference, each with its own readiness. |
| **Export** | HTML reports, CSV, JSON, GeoJSON, GPX, ANSI/IEEE N42.42 and RadiaCode XML, through the system file picker. |

## How the analysis is built

```
raw device data → stored samples → derived values → statistical estimate → wording
```

The app keeps these four apart on purpose, and the wording never skips a level.

- **Baseline.** A place's profile is summarised by median and P10/P90 rather
  than a mean, so one spike does not move it.
- **Difference.** Two counting windows are compared with the exact conditional
  binomial test (Przyborowski–Wilenski) and Clopper–Pearson interval bounds —
  appropriate for Poisson counts, unlike a normal approximation on small numbers.
- **Sensitivity.** When a difference is not accepted, the screen also gives the
  Currie detection limit for that exposure: *"an excess would have been seen
  from ×1.18"*. A refusal without its limit is not an answer.
- **Peaks.** Net area and significance come from side bands whose variance is
  known; the line centre comes from an ExpGaussExp fit judged by the Cash
  statistic, and only for peaks strong enough for the fit to mean anything.
- **Gaps.** A break in the data is drawn as a break. Charts do not connect
  across a period when nothing was measured, and a day without measurements is
  empty rather than zero.

### What the analysis does **not** prove

- A statistical difference is **not** a statement about danger. The app never
  says "safe", "normal" or "dangerous", by rule and by test.
- A spectral peak is a **candidate**, never a proven radionuclide.
- Comparing two objects is **not** food-safety certification.
- **Bq/kg is not implemented at all.** Activity in becquerels appears only after
  you build an efficiency curve from a certified source, holds only for that
  geometry, and ignores self-absorption and cascade coincidences.
- Energy-scale correction is applied only when you press the button, and the
  screen says so while it is on.

## Privacy

Verified in this repository, not aspirational:

- **No network code in the app.** A search for HTTP clients, sockets, WebView or
  any networking library over `app/` and `protocol/` returns nothing.
- **One outbound connection: map tiles.** `osmdroid` requests OpenStreetMap
  standard raster tiles when the Map tab draws an uncached area. Tile
  coordinates reach a third party; no measurement or track point goes with them.
  Everything else works with networking blocked.
- **No Google Play Services, no Firebase, no analytics, no crash reporting.**
- **No account.** There is nothing to sign into.
- **The system does not copy your data either.** Cloud backup and Android 12+
  device-to-device transfer are both excluded, because the database holds
  measurements and coordinates.
- **Location** is read in exactly two places, both of them on screen and neither
  of them stored beyond the device: the Map tab shows where you are while it is
  open, and track recording writes fixes into the route you started yourself.
  Place recognition uses none of it — it hashes the Wi-Fi gateway address
  locally and needs no location permission at all.

## Supported hardware

| Device | Status |
|---|---|
| RadiaCode-110 | **Tested** — the app is developed against this device; the repository carries real spectra from it as test fixtures, with the model and serial stripped |
| RadiaCode-101 / 102 / 103 / 103G | Expected to work, **untested**: same BLE protocol, per-model crystal and resolution constants are in `DeviceModel` |
| RadiaCode Zero | Expected to work as a **counter only** — an organic-plastic scintillator gives no photopeaks, and the app disables spectral analysis for it |
| Unidentified device | Falls back to a conservative profile and says so on screen |

Android 8.0 (API 26) or newer. Bluetooth LE required.

## Install

No GitHub Release is published yet — build from source. <!-- OWNER ACTION -->

## Build from source

Requirements, taken from the build files rather than from habit:

- JDK **17** (`sourceCompatibility`/`targetCompatibility` in `app/build.gradle.kts`)
- Android SDK with **compileSdk 35** and build-tools 35.0.0
- Gradle wrapper is included — no separate Gradle install

```bash
git clone <repository-url>
cd alpha
./gradlew test                 # 2104 JVM unit tests
./gradlew :app:smokeTest       # 118 Robolectric screen tests
./gradlew :app:lintDebug
./gradlew :app:assembleRelease # unsigned APK
```

The release build is unsigned on purpose: no signing key lives in this
repository. Sign it yourself before installing:

```bash
BT=$ANDROID_HOME/build-tools/35.0.0
$BT/zipalign -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
$BT/apksigner sign --ks <your.keystore> --out alpha.apk aligned.apk
```

## Data import and export

**Import:** RadiaCode spectrum XML. Such a file can carry more than counts —
calibration coefficients, accumulation and live time, channel count and the
**device serial number**. Alpha keeps the spectrum, its background spectrum,
the calibration and the timing. The serial is parsed but **not stored**: an
imported snapshot is analysed as an unidentified instrument, and the screen
says so, because guessing a crystal from a filename would change peak widths
and candidate matching. Unknown fields are ignored rather than invented. Files
are size-limited, and DTDs and external entities are disabled in the parser.

**Export:** HTML report, CSV, JSON, GeoJSON, GPX, ANSI/IEEE N42.42-2011, and
RadiaCode XML. Every save goes through the system file picker — the app never
writes to shared storage on its own.

> [!WARNING]
> A spectrum exported from a **live** session carries your device's serial
> number in the XML, because the format has a field for it. Strip it before
> publishing a file. Snapshots imported from someone else's XML carry no serial,
> since the app never stored one.

## Compared with the official RadiaCode Android app

Checked against <https://www.radiacode.com/software> on **19 August 2026**. The
official Android manual (`downloads.radiacode.com/EN/RC-10x_Android.pdf`) is a
scanned document and could not be read mechanically, so anything not stated on
the features page is marked `?` rather than "No". **Absence from the
documentation is not evidence of absence in the app.**

| Capability | Alpha | Official RadiaCode Android |
|---|:---:|:---:|
| Live measurement dashboard | ✓ | ✓ |
| Gamma spectrum, background subtraction | ✓ | ✓ |
| Spectrogram | ✓ | ✓ |
| Radiation map with recorded tracks | ✓ | ✓ (Google Maps) |
| Saved libraries of spectra and tracks | ✓ | ✓ |
| Isotope hints from spectrum | ◐ candidates with an evidence cascade | ✓ |
| Accumulated-dose screen | ✓ | ✓ |
| Photo attached to records | — | ✓ (gamma camera) |
| Per-place learned baseline, statistical comparison | ✓ | ? |
| Detection limit reported alongside a refusal | ✓ | ? |
| Search mode with reference point and audible ratio feedback | ✓ | ? |
| Route-to-route comparison | ✓ | ? |
| Place fingerprint | ✓ | ? |
| Efficiency curve → activity in Bq with uncertainty | ◐ needs a certified source | ? |
| N42.42 export | ✓ | ? |
| Map without Google Play Services | ✓ (OpenStreetMap) | ? |

Rows are chosen to show where the workflows differ, not to count ticks.

## Limitations

- One device model is actually **tested**; the rest of the series is expected
  to work but unverified.
- The efficiency/activity feature needs a **certified source** you own; without
  one, no becquerels are shown anywhere.
- Analysis needs accumulation: peak search refuses on short spectra, and a
  baseline needs a profile to mature before it compares anything.
- Android background restrictions can stop a long measurement. The app runs a
  foreground service and offers a battery-optimisation exemption, but aggressive
  vendor power management can still kill it.
- Map tiles need network on first view of an area; without it the map is blank
  where nothing is cached.
- Track recording needs GPS and drains the battery.
- The BLE protocol is community-reverse-engineered. A firmware update can break
  it, and the device's identity cannot be verified.

## Roadmap

Kept in [`ROADMAP.md`](ROADMAP.md). Nothing there is a promise.

## Contributing

Issues and pull requests are welcome. Two house rules that reviews enforce:
wording never turns a statistic into a verdict about danger, and measurement
data never leaves the device. Both are checked by tests.

## License

MIT — see [`LICENSE`](LICENSE). Third-party components and the attribution they
require are listed in [`NOTICE`](NOTICE); the same notices ship inside the app
under Settings → About.

## Before you publish (owner actions)

- [ ] Take fresh screenshots of the current build and put them in `docs/images/`
      after the privacy checklist in `docs/images/README.md`; then replace the
      placeholders in both READMEs.
- [ ] Decide whether to publish GitHub Releases and, if so, sign the APK with a
      key kept **outside** this repository.
- [ ] Replace `<repository-url>` above with the real clone URL.

## References

- RadiaCode software features — <https://www.radiacode.com/software> (checked 19 Aug 2026)
- RadiaCode Android manual — <https://downloads.radiacode.com/EN/RC-10x_Android.pdf> (scanned; not machine-readable)
- `cdump/radiacode` — the community protocol implementation this project's
  Kotlin port follows, MIT — <https://github.com/cdump/radiacode>
- L. A. Currie, *Limits for Qualitative Detection and Quantitative
  Determination*, Anal. Chem. 40 (1968) 586 — detection limits
- C. G. Ryan et al., Nucl. Instr. Meth. B34 (1988) 396 — SNIP continuum
- S. Das, arXiv:1603.08591 — ExpGaussExp line shape
- W. Cash, ApJ 228 (1979) 939 — the fit statistic for Poisson counts
- Android Auto Backup and data-extraction rules —
  <https://developer.android.com/guide/topics/data/autobackup>
