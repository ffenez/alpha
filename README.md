<h1 align="center">Alpha</h1>

<p align="center">
  An Android app for RadiaCode detectors. It keeps measuring, learns what a
  place normally reads, and tells you when a reading really changed.
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
> **Not made by RadiaCode Ltd.** This is a third-party app built on a
> community-reverse-engineered Bluetooth protocol. The manufacturer does not
> endorse or support it.

> **Screenshots pending.** None ship in this repository yet.

---

## What it is

Alpha connects to a RadiaCode detector over Bluetooth and runs as an
instrument, not a viewer. It records all the time, remembers what each place
usually reads, and only reports a change when a statistical test backs it up.
Spectra, routes and history stay in the app's database on your phone.

It is a second workflow next to the official app, not a replacement. What it
does differently: long measurements, comparison against your own reference,
and analysis that shows how sure it is.

## Why

A bare number tells you little. 0.17 µSv/h means nothing until you know what
this room usually reads and how much two neighbouring seconds differ anyway.
So Alpha keeps a baseline for each place, always names what a ratio is measured
against, and says out loud when it cannot tell a difference — along with how
big a difference it would have seen.

## What it does

| | |
|---|---|
| **Measuring** | One screen, two questions: *observation* compares against what is usual here, *search* against a point you mark yourself. |
| **Search** | Clicks, a tone that rises with the ratio, vibration. They run from the measurement service, so they keep working with the screen off. |
| **Journal** | One record per episode — start, end, duration, range — instead of a row per trigger. |
| **Spectrum** | Live accumulation, snapshots, background subtraction, continuum removal, peak search, isotope *candidates*, spectral ranges, spectrogram. |
| **Activity** | With a certified source you can build an efficiency curve; then a matched peak reports activity in becquerels with its uncertainty. |
| **Map** | Track recording with a colour trail, an accumulated grid over many trips, and route-to-route comparison. |
| **Dose** | Dose collected over 7, 30 and 90 days, and how much of that time was actually measured. |
| **Export** | HTML, CSV, JSON, GeoJSON, GPX, N42.42 and RadiaCode XML through the system file picker. |

## How it decides

- **Baseline.** A place is described by its median and its P10–P90 spread, not
  by an average, so one spike does not move it.
- **Difference.** Two counting windows are compared with an exact test for
  counts (Przyborowski–Wilenski, with Clopper–Pearson bounds), which stays
  correct on small numbers where the usual normal approximation does not.
- **Sensitivity.** When no difference is accepted, the screen also gives the
  limit: *"an excess would have been seen from ×1.18"*. A refusal without its
  limit is not an answer.
- **Peaks.** Area and significance come from side bands; the line centre comes
  from an asymmetric fit, and only for peaks strong enough to fit at all.
- **Gaps.** A break in the data is drawn as a break. A day without measurements
  is empty, not zero.

## What it does not tell you

- Nothing here is a statement about danger. The app never says "safe",
  "normal" or "dangerous" — that is a rule, and tests enforce it.
- A peak in a spectrum is a **candidate**, never a proven isotope.
- Comparing two objects is not food-safety testing.
- **Bq/kg does not exist in this app.** Becquerels appear only after you build
  an efficiency curve from a certified source, apply only to that geometry, and
  ignore self-absorption and cascade effects.

## Privacy

Checked in this repository, not just promised:

- **No network code.** Searching the whole app for HTTP clients, sockets,
  WebView or any networking library finds nothing.
- **One outbound connection: map tiles.** `osmdroid` fetches OpenStreetMap
  tiles when you open a part of the map it has not cached. Tile coordinates go
  out; no measurement and no track point goes with them. Everything else works
  with networking switched off.
- **No Google Play Services, no Firebase, no analytics, no crash reporting.**
- **No account.** There is nothing to sign into.
- **Android does not copy your data either.** Cloud backup and device-to-device
  transfer are both switched off.
- **Location** is used in two places only: the map shows where you are while it
  is open, and track recording writes points into a route you started yourself.
  Place recognition needs no location at all — it hashes the Wi-Fi gateway
  address on the phone.
- **Files you export carry no serial number.** See below.

## Devices

| Device | Status |
|---|---|
| RadiaCode-110 | **Tested** — the app is developed on it |
| RadiaCode-101 / 102 / 103 / 103G | Should work, **not tested**: same protocol, per-model constants are in the code |
| RadiaCode Zero | Counter only — its plastic scintillator gives no peaks, so spectral analysis is switched off |
| Unrecognised device | Falls back to a cautious profile and says so on screen |

Android 8.0 or newer, Bluetooth LE required.

## Build

You need JDK 17 and the Android SDK with compileSdk 35 (build-tools 35.0.0).
Gradle comes with the repository.

```bash
git clone <repository-url>
cd alpha
./gradlew test                 # JVM unit tests
./gradlew :app:smokeTest       # screen tests
./gradlew :app:lintDebug
./gradlew :app:assembleRelease # unsigned APK
```

The release build is unsigned on purpose — no signing key lives in this
repository. Sign it yourself:

```bash
BT=$ANDROID_HOME/build-tools/35.0.0
$BT/zipalign -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
$BT/apksigner sign --ks <your.keystore> --out alpha.apk aligned.apk
```

No GitHub release is published yet, so building from source is the only way in.

## Import and export

**Import:** RadiaCode spectrum XML. Alpha keeps the spectrum, its background,
the calibration and the timing. It ignores fields it does not know instead of
guessing. If the file names a device, that name is dropped: an imported
spectrum is analysed as an unknown instrument and the screen says so, because
guessing the crystal would change peak widths and candidate matching. Files
have a size limit, and the parser has DTDs and external entities disabled.

**Export:** HTML report, CSV, JSON, GeoJSON, GPX, N42.42 and RadiaCode XML.
Every save goes through the system file picker; the app never writes to shared
storage by itself.

**No exported file contains your detector's serial number.** Both formats have
a field for it and Alpha never writes it, because a serial identifies your
particular device and an exported file is meant to be shared. The model name
("RadiaCode-110") is written, since a reader needs it to interpret the
spectrum, and it does not point at your unit. The same holds for the diagnostic
archive from Settings.

## Next to the official app

Checked against <https://www.radiacode.com/software> on 19 August 2026. The
official Android manual is a scanned PDF that could not be read mechanically,
so anything not on that page is unknown rather than missing.

Both apps do live readings, spectra with background subtraction, a
spectrogram, a radiation map with tracks, saved libraries, isotope hints and an
accumulated-dose screen. The official app also attaches photos to records;
Alpha does not.

Alpha adds things the official documentation does not mention — which is not
proof they are absent there: a learned baseline per place with a statistical
comparison, a detection limit printed next to every refusal, a search mode with
a reference point and audible feedback, route-to-route comparison, a place
fingerprint, activity in becquerels from your own efficiency curve, N42.42
export, and a map that works without Google Play Services.

## Limitations

- Only one model is actually tested; the rest of the series should work but is
  unverified.
- Activity needs a certified source you own. Without one, no becquerels appear
  anywhere.
- Analysis needs accumulation: peak search refuses on short spectra, and a
  baseline has to mature before it compares anything.
- Android can kill a long measurement. The app runs a foreground service and
  offers a battery-optimisation exemption, but aggressive vendor power saving
  can still stop it.
- The map needs network the first time you open an area.
- Track recording needs GPS and drains the battery.
- The Bluetooth protocol is community-reverse-engineered. A firmware update can
  break it.

## Contributing

Issues and pull requests are welcome. Two rules that reviews enforce: wording
never turns a statistic into a verdict about danger, and measurement data never
leaves the device. Tests check both.

Plans live in [`ROADMAP.md`](ROADMAP.md). Nothing there is a promise.

## License

MIT — see [`LICENSE`](LICENSE). Third-party components and their required
notices are in [`NOTICE`](NOTICE) and inside the app under Settings → About.

## Sources

- RadiaCode software features — <https://www.radiacode.com/software> (checked 19 Aug 2026)
- `cdump/radiacode`, MIT — the community protocol work this Kotlin port follows —
  <https://github.com/cdump/radiacode>
- L. A. Currie, Anal. Chem. 40 (1968) 586 — detection limits
- C. G. Ryan et al., Nucl. Instr. Meth. B34 (1988) 396 — SNIP continuum
- S. Das, arXiv:1603.08591 — ExpGaussExp line shape
- W. Cash, ApJ 228 (1979) 939 — fit statistic for counts
