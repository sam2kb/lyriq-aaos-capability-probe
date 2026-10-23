# JSON report schema

Schema version: **1.1**. The report is local by default. `ReportStore` writes a matching
human-readable `.txt` next to the `.json`.

## Top level

| Key | Contents |
| --- | --- |
| `schema_version` | `"1.1"` |
| `app_version` | app version name |
| `timestamp` | ISO-8601 UTC generation time |
| `app` | package, version, installer, SDKs, debuggable, granted permissions |
| `device` | manufacturer/model/device/product/hardware/board, ABIs, CPU/RAM/storage, platform features |
| `android` | release, SDK, build fingerprint/ID, security patch, locale/time zone |
| `automotive` | automotive feature, Car API/connection, manager presence, creation error |
| `permissions` | full curated matrix + requestable/protected lists + catalogue note |
| `cameras` | exposed IDs, descriptors, stream configurations, system-camera/EVS boundaries |
| `vehicle_properties` | accessible property metadata, active readings, read policy |
| `android_sensors` | sensor inventory + last 10-second sample |
| `storage` | volumes, app-specific external dirs, selected tree, SAF test steps |
| `connectivity` | Bluetooth, Wi-Fi, network, location/GNSS, USB, display, audio |
| `media` | audio decoders, MIME support, AudioTrack path, media session/notification/FGS state |
| `lms_network` | INTERNET/local-network/cleartext policy and last HTTP/Slimproto TCP test |
| `driving_state` | UX restriction snapshot and note |
| `car_app_library` | explicit `NOT_TESTED` scope boundary |
| `summary` | counts, camera/property/sensor/storage totals, key findings, human-readable text |
| `test_results` | one entry per discovery/active test |
| `errors` | derived `ERROR`/`TIMEOUT` results with exception metadata |

Fields with no data are emitted as `null` or empty arrays/objects. `test_results` and the
inventory objects remain present in the schema.

## Test result

```json
{
  "id": "camera.open.0",
  "module": "CAMERAS",
  "kind": "ACTIVE",
  "name": "Open and close camera 0",
  "status": "SUCCESS",
  "summary": "Camera 0 opened and was closed...",
  "details": "...",
  "api_used": "CameraManager.openCamera",
  "elapsed_ms": 84,
  "timestamp": "2026-10-23T13:00:00Z",
  "exception_class": null,
  "exception_message": null,
  "measurements": {"camera_id": "0"}
}
```

Statuses:

`SUCCESS`, `AVAILABLE`, `DENIED`, `PRIVILEGED_ONLY`, `UNSUPPORTED`, `NOT_EXPOSED`,
`UNAVAILABLE`, `ERROR`, `TIMEOUT`, `NOT_TESTED`.

`DISCOVERY` means metadata was observed. `ACTIVE` means the user requested an operation
that actually opened a camera, sampled a sensor, read a value, wrote a temporary file,
wrote silent audio, started a foreground service, or opened an LMS TCP socket. Do not
promote a discovery result to proof of access.

## Camera descriptor

Includes `id`, facing, hardware level, Camera2 info version, physical/logical IDs,
capabilities, top stream formats/sizes, sensor orientation, pixel/active array, flash,
focal lengths, apertures, Android 16 automotive lens-facing/location metadata, and an
inspection error if characteristics could not be read.

## Vehicle reading

Includes property ID/name, area, status, value string, value type, unit label, VHAL
property status, timestamp, and summary. `INFO_VIN` may appear in `vehicle_properties`
metadata; it is never read and has no value entry. No actuator properties are read.

## Media snapshot

Includes `audioDecoders` (`name`, audio `mimeTypes`, hardware/software flags, max instances),
`mimeSupport`, `audioPath` (native rate, frames per buffer, minimum buffers, direct PCM
support, output device summaries), `mediaSessionApiPresent`, `mediaBrowserServicePresent`,
notification/foreground-service permission states, `lastForegroundServiceTest`, and
`lastSilentAudioTest`. The FGS test record has started/stopped/channel/permission/error
fields; the audio record has sample rate, channels, buffer size, play state, and error.

## LMS network snapshot

`internetPermissionGranted`, `localNetworkPermissionDefined`,
`localNetworkPermissionGranted`, `nearbyWifiPermissionDefined`,
`cleartextTrafficPermitted`, `endpointKind` (private/public/loopback/mDNS/IPv6/hostname),
and `lastTest`. Each endpoint result has a label (`http`/`slimproto`), port, DNS/connection
flags, latency, and sanitized error class/message. The user-entered host/IP is **not**
included.

## Storage snapshot

`storage.testRecord.steps` is ordered and every step has a `success` flag. The test file
name is shown; the persisted SAF tree URI is stored only in app-private preferences and is
not included in the JSON. Removable-volume UUID path segments are redacted to `[VOLUME]`.

## Privacy exclusions

The report does not include VIN value, precise coordinates, account identities, SSID/BSSID,
IP/MAC address, USB serial numbers, `ANDROID_ID`, LMS host/IP, HTTP payloads, or audio. See [PRIVACY.md](PRIVACY.md).
