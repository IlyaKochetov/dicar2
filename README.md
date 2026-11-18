# DiCar International

English-first fork of [DiCar](https://github.com/tsix2019/dicar2), a local BYD DiLink vehicle dashboard and control application.

**Planned remote access:** [Python backend specification](docs/server-specification.md) — an opt-in, self-hostable relay for a future phone companion app. The existing application does not use this server yet.

<p align="center">
  <img src="docs/images/twin-dark.png" width="100%" alt="Vehicle twin dashboard in dark theme">
</p>

A local vehicle dashboard and control app for BYD DiLink head units: reads live vehicle data,
renders a digital-twin dashboard, controls climate and windows, and offers a configurable
floating overlay.

**All vehicle data is processed on-device and never uploaded.** The app makes exactly one kind of
external network request: the update check asks GitHub for the latest release tag, and by default
that only happens when you tap "check for updates" (an opt-in "check on launch" toggle exists in
settings). The request carries no vehicle data and no device identifier.

Everything the hardware page reads also stays on the device. Exported reports **redact serial
numbers, MAC addresses, IPs, the Android ID and similar device-identifying fields by default** —
there is a switch in the export dialog if you need them. That page also deliberately avoids
requesting location permission, at the cost of not being able to show the Wi-Fi SSID or BSSID
(Android has gated those behind location since 8.1).

> ⚠️ Personal project, not affiliated with BYD. All interfaces come from public
> reverse-engineering work and vary a lot across models and firmware — use at your own risk.
> Control features (windows, sunroof, climate) physically actuate the car.

## Features

| | |
|---|---|
| **Twin dashboard** | Three columns in landscape: a large RPM dial on the left; big speed readout plus a top-down car view (doors, window/sunroof position, tyre pressure and temperature, turn signals, parking radar — all live) in the middle; a bidirectional drive/regen power bar with battery and fuel levels on the right. A bottom strip carries the 3-minute trend chart (an engine-RPM series appears automatically once the engine kicks in) and climate/tyre/door status pills. Stacks vertically in portrait |
| **3D car model** | Optional, off by default. Swaps the twin view for a drag-to-rotate, pinch-to-zoom 3D car whose doors, windows, wheel rotation and turn signals all track live data. The mesh is generated procedurally in code — no 3D engine dependency and no external model files |
| **Card view** | Full readouts grouped into powertrain, battery, climate, body and misc |
| **Controls** | A/C power, temperature, fan, vent mode, recirculation, seat heating/ventilation, all four windows, sunroof, sunshade, windshield defrost |
| **Floating overlay** | Floats above other apps; which blocks to show (readout / gauges / chart / twin view / details) and the opacity are configurable, and it is draggable |
| **Foreground service** | Keeps polling in the background with a persistent "speed \| SoC \| A/C" notification |
| **Hardware info** | A full inventory of the head unit **itself**: SoC and big.LITTLE clusters, live per-core frequency and load, GPU limits and extensions, RAM/storage/partitions, displays, sensors, cameras, radios, audio, codecs and DRM, plus every system property (often well over a thousand). Searchable, filterable by category, and exportable as text |
| **Update check** | Compares against the latest GitHub release; opens the release page or the APK directly. Manual by default; "check on launch" is opt-in |
| **Interface probe** | One tap dumps every `android.hardware.bydauto.*` class, constant, method and current value from the head unit — the basis for porting to other models |

<p align="center">
  <img src="docs/images/cards-dark.png" width="48%" alt="Vehicle data cards">
  <img src="docs/images/twin-light.png" width="48%" alt="Light theme twin dashboard">
  <br>
  <img src="docs/images/twin-3d.png" width="48%" alt="Interactive 3D car model">
  <img src="docs/images/hardware.png" width="48%" alt="Hardware information page">
  <br>
  <img src="docs/images/floating.png" width="32%" alt="Floating overlay">
</p>

## How it gets the data

Vehicle data lives behind the head unit's `android.hardware.bydauto.*` framework, but reads and
writes are guarded by **signature-level** `BYDAUTO_*_GET/SET` permissions, so a third-party app
calling them directly is rejected by the vehicle service (`permission deny`). The app provides two
channels and picks whichever works:

1. **Wireless-debugging channel (primary)** — a built-in ADB client connects to the head unit's own
   `127.0.0.1:5555`, launches a helper process via `app_process` under the shell identity, and that
   helper talks to `BYDAutoDeviceManager`. The head unit shows its "allow debugging" prompt once.
   A full polling round costs a **single** round trip thanks to request batching.
2. **In-process channel (fallback)** — plain reflection, which works on more permissive firmware.

A DiPlus local HTTP API is supported as an alternative data source.

## Build

Requires JDK 17 and the Android SDK (platform 35 / build-tools 35); point `local.properties` at your SDK. The Gradle wrapper is included in the repository.

```bash
./gradlew assembleRelease   # output in app/build/outputs/apk/release/
./gradlew testDebugUnitTest
```

The upstream release signing key is not included in the repository. If no release keystore is configured, the build uses a debug signing key. To sign releases with your own key, create an ignored `keystore.properties` file at the repository root:

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Do not commit passwords or keystores.

## Localization

Version 1.1 adds Russian labels and more compact card text. Choose the system language, English, or Russian in Settings → Language; the choice is saved on the device. The default UI strings are in `app/src/main/res/values/strings.xml`. To add a language, create a locale-qualified resource directory such as `values-fr`, copy the string and plural resource names, and translate their values. Keep positional placeholders such as `%1$s` and `%2$d` intact; Android falls back to the default resources for missing translations. Test the app with the device language set to the new locale and select Settings → Mock data for testing without a vehicle.

Vehicle protocol strings and values supplied by the head unit are not translations. Hardware reports retain their original collector labels so identifier redaction and shared diagnostics remain consistent; the hardware screen resolves known labels for display and search.

## Install

If an APK has been published, get it from [this fork's Releases](https://github.com/IlyaKochetov/dicar2/releases), or build it from source. A release may not be available yet.

```bash
adb connect <head-unit-ip>:5555
adb install -r app-release.apk
```

> If you previously side-loaded a self-built (debug-signed) build from before 0.1.0, installing over
> it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — Android does not allow a signing-key change.
> If you need to switch signing keys, uninstall the old app first with `adb uninstall com.dicar.vehicle` (this removes its app data). Subsequent in-place upgrades require the same package name and signing key.

### First run

1. Enable wireless ADB debugging on the head unit.
2. Open DiCar International and select **Auto** or **BYDAuto only** in Settings.
3. When prompted, authorize debugging; select **Always allow** if you trust the connection.
4. If no vehicle data appears, collect diagnostics:

```bash
adb logcat -s AdbTransport HelperSession BydAutoDataSource
```

The head unit may require model- or firmware-specific steps to enable ADB. Commands that control climate, windows and sunroof should only be tested while safely parked.

## Porting to other models

`BydApiMap.kt` holds every interface mapping (method names, FID symbols, value encodings) in one
place — porting to a different car is mostly editing that single file. Each entry carries a
provenance tag: `[D4]` confirmed present in a DiLink 4.0 probe report, `[D3]`/`[D5]` verified on a
real DiLink 3/5 car in public research, `[?]` guessed and needing verification via
**Settings → Probe BYDAuto interfaces**.

## Compatibility and scope

- Intended to run **on the BYD DiLink head unit**; it is not a cloud-connected smartphone remote-control app.
- The English-first fork retains the original BYDAuto / DiPlus communication interfaces. Chinese protocol command strings must not be translated.
- DiLink versions and BYD models differ in available interfaces and permissions. Compatibility with a specific Yuan Plus / DiLink 5.0 firmware build is **not verified**.
- The English UI localization is in progress; some technical diagnostic text may still appear in Chinese.

## License

Released under the MIT license. Forked from [tsix2019/dicar2](https://github.com/tsix2019/dicar2), with credit to its original contributors. Built on public research from [BYDMate](https://github.com/AndyShaman/BYDMate) and
[byd-apps](https://github.com/wheregoes/byd-apps).
