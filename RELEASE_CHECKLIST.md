# Release checklist

Run before every public release. Each line is a check with a command or a place
to look — not a reminder to "be careful".

## 1. Repository state

- [ ] `git status` is clean; nothing untracked that should ship, nothing tracked
      that should not.
- [ ] Version bumped in `app/build.gradle.kts` (`versionName` **and**
      `versionCode`) with a matching entry in `ui/text/ReleaseStrings` — a test
      fails if they disagree.
- [ ] Tag the commit you actually built.

## 2. Secrets and private data

- [ ] No secrets in the tree:
      `git grep -nIE "(api[_-]?key|secret|password|token|BEGIN .*PRIVATE KEY|keystore)"`
- [ ] No developer paths: `git grep -nIE "/home/[a-z]+/|/Users/[A-Za-z]+/"`
- [ ] No real device serial: `git grep -niE "serialnumber"` — fixtures must use
      the dummy `RC-101-000000`.
- [ ] No export path writes a serial: `RcXml` must not emit `<SerialNumber>`,
      `N42` must not emit `<RadInstrumentIdentifier>`, `DebugReport` must not
      print one. Guarded by tests, listed here because a format field is easy
      to re-add "for completeness".
- [ ] Nothing new and non-source in history:
      `git log --all --diff-filter=A --name-only --pretty=format: | sort -u | grep -viE '\.(kt|kts|md|json|xml|txt|ttf|pro|properties|toml|jar|bat|html)$'`
- [ ] Any image added since the last release passed `docs/images/README.md`
      (EXIF, serial, map, profile names, notifications).

## 3. Privacy and permissions

- [ ] No network code outside osmdroid:
      `git grep -nIE "HttpURLConnection|OkHttp|Retrofit|URL\(|openConnection|Socket\(|WebView|okhttp|ktor" -- 'app/src/main/**' 'protocol/**'`
      must return nothing.
- [ ] `AndroidManifest.xml`: every permission still has a call site; nothing new
      was added without a reason written next to it.
- [ ] `allowBackup="false"` **and** `res/xml/data_extraction_rules.xml` still
      exclude both `<cloud-backup>` and `<device-transfer>`.
- [ ] Exported components are still only `MainActivity` and `BootReceiver`.

## 4. Science and wording

- [ ] `./gradlew test` — the wording tests are the ones that matter here: no
      "safe/normal/dangerous", no "detected", no ratio without its denominator.
- [ ] `./gradlew :app:spectrumValidation` if the spectrum pipeline changed.
- [ ] Any new number on screen has a unit, and any new refusal names its limit.

## 5. Build and checks

Separate invocations — combining them has broken Robolectric before.

- [ ] `./gradlew test`
- [ ] `./gradlew :app:smokeTest`
- [ ] `./gradlew :app:lintDebug`
- [ ] `./gradlew :app:assembleRelease`

## 6. Dependencies and licenses

- [ ] New dependency? License recorded in `NOTICE`, and its required notice
      shipped in `app/src/main/assets/licenses/` if the license demands one.
- [ ] No GPL code copied in.
- [ ] No debug-only dependency reached the release APK:
      `unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep -iE "test|mock"`

## 7. Release artefact

- [ ] `unzip -l` the APK: no test classes, no unexpected assets.
- [ ] `unzip -p … classes.dex | strings | grep -E "/home/|/Users/"` finds nothing.
- [ ] `aapt2 dump badging` shows the intended `package`, `versionCode`,
      `versionName` and `minSdkVersion`.
- [ ] Signed with a key kept **outside** this repository.

## 8. Documentation

- [ ] `README.md` and `README_RU.md` still describe what the build actually
      does — features removed or changed must be removed or changed there.
- [ ] `README_VERIFICATION.md` updated for any new material claim.
- [ ] Screenshots match the current UI.
- [ ] Release notes in "About" read as what the user will see, not as a work log.
