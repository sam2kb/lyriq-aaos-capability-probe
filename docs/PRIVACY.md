# Privacy and data handling

The probe is offline and local-first. It declares no `INTERNET` permission and contains no
analytics, advertising, account, or cloud SDK. Nothing is transmitted by the app; export and
share are user-initiated system actions.

## Collected

- Android build/device model, API level, supported ABIs, memory/storage figures, build
  fingerprint, locale, and time zone.
- App package/version, installer identity, declared/granted permissions.
- Camera2 metadata, vehicle-property metadata and allowlisted values, Android sensor
  metadata, storage/volume metadata, connectivity metadata, and VPN consent/TUN outcomes.
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
- VPN traffic, packets, keys, or remote endpoints. The probe has no remote endpoint.
- Paired Bluetooth devices, local Bluetooth name, or address.

Removable-volume UUID path segments are replaced with `[VOLUME]` before entering the
report. App-specific external directory paths are included for diagnostics but are likewise
sanitized.

## Permissions

The release manifest declares only:

- `CAMERA` — active camera open/one-frame test.
- `BLUETOOTH_CONNECT` — read adapter enabled/state; no scanning or paired-device access.
- `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — transport and enabled-state inventory.
- `android.car.permission.CAR_INFO`, `CAR_ENERGY`, `CAR_ENERGY_PORTS`, `CAR_SPEED`,
  `READ_CAR_DISPLAY_UNITS`, `CAR_MILEAGE`, `CAR_SENSORS` — read-only vehicle inventory.

Location, Bluetooth scan, storage, microphone, `SYSTEM_CAMERA`, and all-files permissions
are catalogued but **not declared**. `SYSTEM_CAMERA` and other protected permissions are
reported as boundaries and never requested.

## Local storage

- Reports and human summaries live under the app's internal `filesDir/reports`.
- `android:allowBackup="false"` and data-extraction exclusion rules keep reports out of
  backup/device transfer.
- Saved reports can be exported through `ACTION_CREATE_DOCUMENT` or shared through a
  `FileProvider` cache URI. Those destinations are chosen by the user.
- Clearing app storage or uninstalling the app removes local reports. The current UI does
  not have a per-report delete button.

## VPN

The VPN test requests the system consent dialog and creates a temporary TUN with one private
address, no routes, no DNS, and no allowed apps. It closes immediately. It does not carry
traffic or contact a server. The authorization outcome is written into the report.

## Vehicle safety

The probe never writes vehicle properties or invokes actuators. It does not access locks,
climate, gear, lights, charging controls, or safety systems. Camera capture is explicit,
single-frame, and in-memory. Active tests are locked by AAOS UX restrictions and, when the
state cannot be read, by an explicit parked confirmation.
