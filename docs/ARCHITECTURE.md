# Architecture

## Shape

```text
MainActivity
  └─ ProbeViewModel (single session owner, main-thread state)
       ├─ SystemProbe
       ├─ PermissionProbe
       ├─ CameraProbe
       ├─ VehicleProbe ── CarAccess ── Car / CarPropertyManager
       ├─ SensorProbe
       ├─ StorageProbe ── SAF / DocumentFile
       ├─ ConnectivityProbe
       ├─ MediaProbe ── MediaCapabilityService / MediaProbeBroker
       ├─ LmsNetworkProbe
       └─ ReportStore

probe-core/
  ├─ model/        serializable report models, result statuses
  ├─ runner/       TestHarness: timeout, elapsed time, exception classification
  ├─ probe/        one scanner per subsystem
  ├─ car/          CarAccess + UX-restriction conversion
  ├─ report/       ReportJson, ReportFactory, human summary
  └─ util/         formatting
```

`ProbeViewModel` orchestrates the quick scan and active operations. It publishes an
immutable `ProbeUiState` consumed by Compose through `StateFlow`. `ProbeReport` is the
serializable artifact; `ProbeData` is the in-memory aggregate used to rebuild the report
after every active test.

## Result model

Every probe result carries:

- stable test id, module, and `DISCOVERY`/`ACTIVE` kind
- one of `SUCCESS`, `AVAILABLE`, `DENIED`, `PRIVILEGED_ONLY`, `UNSUPPORTED`,
  `NOT_EXPOSED`, `UNAVAILABLE`, `ERROR`, `TIMEOUT`, `NOT_TESTED`
- summary, optional technical detail, API used, elapsed milliseconds, timestamp
- exception class/message when applicable, plus string measurements

`TestHarness` wraps each test in a hard timeout and converts `SecurityException` to
`DENIED`, unsupported-class/operation failures to `UNSUPPORTED`, and other throwables to
`ERROR`. Parent-scope cancellation is rethrown so leaving the screen cannot be recorded as
a probe failure.

## Isolation

The quick scan runs modules sequentially. Each module call is wrapped in `safeSection`, so a
module-level crash becomes an error result and does not prevent later modules from running.
Within a module, individual camera/property/sensor failures are recorded without failing
the whole inventory.

Active tests are single-flight. Starting one is rejected while the quick scan or another
active test is running. The camera, sensor, storage, vehicle-value, media, and LMS-network
paths are separate explicit actions.

## Driving restrictions

`CarAccess` opens one read-only `Car` connection and exposes
`CarUxRestrictionsManager` callbacks. On a restricted state:

- the ViewModel cancels the running scan/active coroutine;
- active controls are disabled;
- the media foreground-service test (which is not a coroutine) is stopped with
  `stopService`;
- the UI shows a hard restriction notice.

If restrictions cannot be read, the UI requires an explicit parked confirmation. The
confirmation cannot override a later restricted state. The activity is not marked
distraction-optimized, so AAOS may also block it entirely while driving.

## Camera cleanup

- Enumeration only reads `CameraManager` metadata.
- Open/close waits for `onOpened` with a timeout, then closes in `finally`.
- Frame capture uses one `ImageReader`, one still-capture request, and a hard timeout. The
  JPEG/YUV bytes are read only to prove a frame exists, then discarded. No preview surface,
  MediaRecorder, file, or repeating request is created.
- Cancellation closes any device/session/reader that was created.

## Vehicle read allowlist

`getPropertyList()` is called read-only. Values are read only from a fixed informational/EV
allowlist. `INFO_VIN` may appear in the accessible metadata but is never added to the read
list. There is no `setProperty` call in the project.

## Media path

`MediaProbe` inventories platform audio decoders, relevant MIME support, AudioManager and
AudioTrack properties, MediaSession/MediaBrowserService class availability, notification
state, and media foreground-service permissions. The explicit silent-audio test writes
zeros through AudioTrack and releases it; it does not play content, change volume, or request
audio focus.

`MediaCapabilityService` is a short-lived `mediaPlayback` foreground service. It creates a
low-importance channel, posts a temporary notification, calls `startForeground` with the
media-playback type, waits about one second, removes the notification, stops, and publishes
the outcome through `MediaProbeBroker`. This mirrors the service path SqueezeClient uses
without claiming an actual media session.

## LMS network path

`LmsNetworkProbe` records INTERNET, cleartext policy, and the Android 16/17 local-network
permission boundary. Its active test resolves the user-entered LMS host and opens short TCP
connections to the HTTP port (normally 9000) and Slimproto port (normally 3483). The host/IP
is deliberately **not** written to the report; only endpoint class, ports, latency, and
errors are stored. The test sends no application payload and closes both sockets.

## Storage

Volume discovery is read-only. The SAF write test is active and only runs on a user-selected
tree. It creates a UUID/timestamp-named text file, writes known content, flushes/closes,
reads and verifies with SHA-256, and deletes that exact `DocumentFile`. It never enumerates
or modifies other user files. Removable-volume UUID path segments are redacted before they
enter the report.

## JSON and persistence

`ReportFactory` assembles the report and summary from the current aggregate. Quick Scan
saves one JSON + human summary pair under `filesDir/reports`. Active tests update the
in-memory report; the user can save/export/share it from Reports. Export uses
`ACTION_CREATE_DOCUMENT`; share uses a `FileProvider` cache URI. No network permission is
declared.

See [REPORT_SCHEMA.md](REPORT_SCHEMA.md) for the field map.
