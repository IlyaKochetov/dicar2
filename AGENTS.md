# Project guidance

- Continue authorized work until the user's requested task is complete; do not stop at a partial implementation.

- DiCar International is an on-device BYD DiLink dashboard and control app. Preserve the existing architecture and compatibility with BYDAuto and DiPlus.
- Do not translate Chinese protocol command strings. Vehicle interfaces vary by model and firmware; avoid claiming unverified compatibility.
- Vehicle controls physically actuate the car. Test control features only while safely parked.
- For app testing without vehicle hardware, select Settings → Mock data to generate random vehicle data.
- Preserve the documented privacy behavior: vehicle data stays on-device, update checks are opt-in by default, and hardware exports redact device identifiers by default.
- Build with JDK 17 and Android SDK platform 35 / build-tools 35. Use the Gradle wrapper; run `gradlew.bat testDebugUnitTest` and an appropriate build for changes.
- For every release, update `CHANGELOG.md` and `README.md`, and create a matching version tag on the exact release commit before publishing. Verify the APK version and signing compatibility with the previous release.
- Keep internal process details and publication status out of public-facing documentation and release notes.
- Never commit signing keys, passwords, `keystore.properties`, or local SDK configuration.

## Architecture map

- `VehicleApp` creates the manually wired `AppContainer`; it owns settings, the shared repository, BYDAuto probe, update checker, and hardware collectors. Keep the existing manual dependency injection.
- `VehicleRepository` serializes source access and command handling on a single IO dispatcher. Its reference-counted polling serves the UI and foreground service. Auto mode tries BYDAuto then DiPlus; mock is selected explicitly. State uses nullable readings for unavailable data.
- Commands use optimistic updates and per-key debouncing, followed by readback where supported. A sent command is not necessarily confirmed; preserve timeout, pending and unavailable-readback behavior when changing controls.
- `data/source/bydauto/BydApiMap.kt` centralizes getters, FIDs, encodings and provenance markers. BYDAuto chooses between an ADB helper and in-process reflection; the probe uses the same access channel. DiPlus uses a local HTTP service and requires its original Chinese protocol strings.
- Compose screens and `MainViewModel` consume repository flows; `VehicleMonitorService` and `FloatingWindowService` provide background monitoring and the overlay. Hardware inspection separates guarded static collectors from the stateful live monitor; exports pass through `Redactor` when redaction is enabled.
- See `README.md` for setup, privacy, data sources and compatibility limitations. Confirm behavior against code and model-specific evidence before updating these notes.
