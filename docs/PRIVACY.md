# Privacy and data handling

The probe is local-first. It contains no analytics, advertising, account, or cloud SDK.
`INTERNET` is declared solely for the explicit LMS endpoint test; Quick Scan opens no
sockets. Export and share are user-initiated system actions.

## Collected

- Android build/device model, API level, supported ABIs, memory/storage figures, build
  fingerprint, locale, and time zone.
- App package/version, installer identity, declared/granted permissions.
- Camera2 metadata, vehicle-property metadata and allowlisted values, Android sensor
  metadata, storage/volume metadata, connectivity metadata, audio/media capability metadata,
  and LMS endpoint test outcomes (without the hostname/IP).
- The user-selected SAF folder display name and test-file steps. The persistent tree URI is
  kept only in app-private preferences and is not exported in the report.
- The VIN property may appear as **metadata** in `getPropertyList()` because the platform
  reveals the property name/ID. Its value is never read or exported.

## Deliberately not collected

- VIN value, precise location, location fixes, accounts, or driver identity.
- SSID, BSSID, IP address, MAC address, or Wi-Fi connection details.
- USB serial numbers or device-unique identifiers.
- `ANDROID_ID`, `Build.SERIAL`, advertising ID, or contacts.
- Camera frames or recordings. The optional frame test reads one frame in memory to prove
  data is available, then discards it; no image is saved.
- LMS content, credentials, streamed audio, HTTP payloads, or the user-entered hostname/IP.
- Audio content or recordings. The media test writes silent PCM to the output path; nothing
  is captured.
- Paired Bluetooth devices, local Bluetooth name, or address.

Removable-volume UUID path segments are replaced with `[VOLUME]` before entering the
report. App-specific external directory paths are included for diagnostics but are likewise
sanitized.

## Permissions

The release manifest declares only:

- `INTERNET` — the explicit LMS HTTP/Slimproto TCP reachability test only.
- `CAMERA` — active camera open/one-frame test.
- `POST_NOTIFICATIONS` — show the temporary mediaPlayback foreground-service notification.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — the temporary service type test.
- `BLUETOOTH_CONNECT` — read adapter enabled/state; no scanning or paired-device access.
- `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — transport and enabled-state inventory.
- `android.car.permission.CAR_INFO`, `CAR_ENERGY`, `CAR_ENERGY_PORTS`, `CAR_SPEED`,
  `READ_CAR_DISPLAY_UNITS`, `CAR_MILEAGE`, `CAR_SENSORS` — read-only vehicle inventory.

Location, Bluetooth scan, storage, microphone, `SYSTEM_CAMERA`, and all-files permissions
are catalogued but **not declared**. `SYSTEM_CAMERA` and other protected permissions are
reported as boundaries and never requested. `ACCESS_LOCAL_NETWORK` is catalogued but not
declared in this target-36 build; see the Android 17 note in
[SQUEEZECLIENT_AAOS_PORT.md](SQUEEZECLIENT_AAOS_PORT.md).

## Local storage

- Reports and human summaries live under the app's internal `filesDir/reports`.
- `android:allowBackup="false"` and data-extraction exclusion rules keep reports out of
  backup/device transfer.
- Saved reports can be exported through `ACTION_CREATE_DOCUMENT` or shared through a
  `FileProvider` cache URI. Those destinations are chosen by the user.
- Clearing app storage or uninstalling the app removes local reports. The current UI does
  not have a per-report delete button.

## Network and media

The LMS endpoint test is user-initiated, opens two short TCP connections, sends no HTTP
payload, and omits the hostname/IP from JSON. The media test writes silent PCM to the audio
output and starts a one-second mediaPlayback foreground service with a temporary
notification. Neither test runs in the background or collects content.

## Vehicle safety

The probe never writes vehicle properties or invokes actuators. It does not access locks,
climate, gear, lights, charging controls, or safety systems. Camera capture is explicit,
single-frame, and in-memory. Active tests are locked by AAOS UX restrictions and, when the
state cannot be read, by an explicit parked confirmation.
