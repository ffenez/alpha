# Security policy

## Reporting a vulnerability

Open a **private** security advisory on GitHub (Security → Advisories → Report a
vulnerability) rather than a public issue, and include:

- what an attacker can do, not only what looks wrong;
- the build (`versionName` from Settings → About) and the Android version;
- steps to reproduce, or the file that triggers it if the issue is in an import.

There is no bug bounty. Expect an answer in days, not hours — this is a
spare-time project.

## What this app does with your data

- Measurements, spectra, routes and profiles live in the app's own database on
  the device. Nothing is uploaded.
- There is no account, no analytics, no telemetry and no crash reporting to any
  server. The crash log (`crashes.txt`) is written to the app's private
  directory and only leaves it inside a debug archive you export yourself.
- Cloud backup and Android 12+ device-to-device transfer are both switched off
  (`android:allowBackup="false"` plus `res/xml/data_extraction_rules.xml`), so
  the system does not copy the database anywhere on your behalf. Moving to a new
  phone is done through the app's own backup, where you choose the file.
- The single outbound connection is map tiles: **osmdroid** fetches raster tiles
  from the OpenStreetMap standard tile servers when the Map tab draws an area
  that is not cached. Those requests carry tile coordinates and reach a third
  party. No measurement value and no track point is ever sent with them. If that
  connection is unacceptable to you, do not open the Map tab, or block the app's
  network access — everything else works offline.

## Attack surface worth knowing about

- **Imports.** The app parses RadiaCode spectrum XML and its own backup files.
  Both come from outside and are treated as hostile input: the XML reader has
  external entities and DTDs disabled, and imports are size- and shape-limited.
  A malformed file should be refused, never silently reinterpreted.
- **Exported components.** Only the launcher activity and the boot receiver are
  exported, and the receiver does nothing unless "continue after restart" was
  switched on. The measurement service is not exported.
- **File output.** Every export goes through the system file picker; the app
  does not write to shared storage on its own and ships no `FileProvider`.
- **What an exported file says about you.** Spectra (RadiaCode XML, N42.42) and
  the debug archive carry the instrument **model** but never its serial number,
  even though both formats have a field for it — a serial identifies your unit
  and an exported file is meant to be shared. The serial is still kept inside
  the app, where it distinguishes one detector from another. The app's own
  backup is the exception: it is your data going back to your phone, and it
  keeps the serial so restored records stay attached to the right instrument.
  Exports of tracks and routes contain coordinates by their nature.
- **BLE.** The device link is unauthenticated by the protocol itself — anything
  in range that speaks it can present itself as a RadiaCode. The app treats
  device data as untrusted input, but cannot verify the device's identity.

## Supported versions

Only the latest release gets fixes. There are no maintenance branches.
