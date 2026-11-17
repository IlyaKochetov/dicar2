# Changelog

## 1.0

- Present the dashboard, vehicle controls, floating window, settings, notifications, and hardware information in English.
- Translate vehicle status and hardware descriptions while preserving the Chinese commands required by the DiPlus protocol.
- Add English localization regression tests and an installable APK build workflow.
- Document the data sources, privacy defaults, build and setup instructions, and architecture for contributors.

The release APK is debug-signed; installing over an app signed with a different key requires uninstalling it first, which removes its local data.

Vehicle interface availability varies by model and firmware. Some technical diagnostic text and source comments remain in Chinese. Test physical controls only while safely parked.
