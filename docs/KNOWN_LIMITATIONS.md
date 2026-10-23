# Known limitations and unresolved work

These are deliberately stated so a successful build is not mistaken for a successful
vehicle test.

## Not exercised in this environment

- **No AAOS emulator run.** The build host has no `/dev/kvm`, and no AAOS system image was
  installed. Runtime Compose/AAOS behavior is unverified.
- **No LYRIQ install or test.** Camera behavior, VHAL permissions, storage mounts, audio
  output, LAN access, foreground-service behavior, and driving restrictions remain unknown
  on the actual vehicle.
- **No Play Console access.** Eligibility, form-factor acceptance, screenshot approval, and
  tester installation are unverified.
- **No device screenshots.** The UI compiles and lint-passes, but its on-car layout,
  contrast, touch behavior, and text scaling have not been visually inspected.
- **No instrumentation tests.** The 15 passing tests cover the result model, timeouts,
  permissions classification, endpoint classification, formatting, and JSON/report behavior
  only.

## Camera

- A zero Camera2 count means "no cameras exposed to this app", not absent hardware.
- System/factory cameras can be hidden behind `SYSTEM_CAMERA` and are not requested.
- EVS has no public third-party enumeration/capture API used here; the report says
  `UNSUPPORTED` for that public-SDK boundary, not that EVS hardware is absent.
- The single-frame test tries JPEG first, then YUV_420_888, at no more than 2 MP. A camera
  may support only `PRIVATE` output or reject a still session; that is recorded as a
  failure/unsupported result, not a recorder defect.
- The frame test proves a frame buffer arrives; it does not prove sustained frame rate,
  MediaCodec encoding, or MP4 segmenting.
- `AUTOMOTIVE_LENS_FACING`/`AUTOMOTIVE_LOCATION` metadata is only read on API 36+.

## Vehicle data

- `getPropertyList()` only returns what the OEM/platform exposes to this app. An absent
  value is not proof the vehicle lacks the sensor.
- Only the first supported area is read for non-global properties. Multi-area values are
  not exhaustively sampled.
- The read allowlist uses informational/EV properties. It does not test ALL VHAL
  properties and intentionally excludes writes, gear, locks, HVAC, lights, doors, and
  charging controls.
- VIN metadata may be visible in the property list; the value is never read.
- `CAR_MILEAGE`, `CAR_SENSORS`, and other permission names may be undefined or
  OEM-restricted; the matrix records this.

## Sensors

- Android `SensorManager` sensors are not the LYRIQ's VHAL sensors and not the same as a
  phone running Android Auto.
- A 10-second on-change sensor sample may legitimately contain zero events and is recorded
  as `TIMEOUT`, with an explanation.
- One sensor is sampled at a time; high-rate or long-duration capture is not supported.

## Storage

- Some SD-card roots cannot be selected by SAF; the tester must choose a folder.
- USB mass storage may be visible to `StorageManager` but absent from `UsbManager`, or vice
  versa.
- The test only writes/deletes its own uniquely named temporary file in the selected tree.
  It does not prove access to arbitrary files, removable-volume roots, GM recordings, or
  hidden partitions.
- Persistent URI access can fail even when the current session has a temporary grant; the
  result records this separately.
- Volume capacity may be unavailable if the app cannot stat the volume path.

## Media

- The decoder inventory proves that a codec advertises a MIME type, not that a particular
  LMS stream/profile decodes. Media3/ExoPlayer is deliberately not bundled in this probe.
- The only active audio test writes one second of silent PCM through AudioTrack. It does
  not test MediaCodec decoding, gapless playback, audio focus, offload, volume control, or
  sustained playback.
- The foreground-service test starts/stops a `mediaPlayback` service and posts a temporary
  notification. It does not create a MediaSession, play media, or verify media controls.
- `POST_NOTIFICATIONS` may be denied; the service may still start while the notification is
  not visible. Both states are recorded separately.
- `MIME` support for Ogg maps the container to the platform Vorbis decoder where needed;
  this is a capability signal, not an end-to-end stream test.
- The media probe's audio path may use the current default output; it does not force each
  available output device.

## LMS network

- The active test only proves TCP reachability to two ports. It sends no HTTP request, no
  credentials, and no Slimproto handshake.
- Only the first resolved address is tried per endpoint; a multi-address hostname may
  connect on another address that this probe did not test.
- The user-entered hostname/IP is intentionally omitted from JSON. Results cannot be tied
  to a server from the report alone; keep a separate test note.
- Android 16/target 36 uses the legacy implicit local-network path. Android 17/target 37
  will require `ACCESS_LOCAL_NETWORK`; the app catalogues that boundary but does not request
  it. A SqueezeClient port targeting 37 must add the declaration and runtime request.
- `usesCleartextTraffic` is enabled because the fork uses `http://`; this is a deliberate
  diagnostic/port requirement, not a recommendation for public deployments.
- The probe does not test mDNS discovery, Wi-Fi roaming, IPv6 LAN routing, bandwidth, or
  streaming.

## Build/distribution

- The checked-in release AAB is **unsigned** because no upload key was provided. It must be
  signed before Play upload.
- No upload key, Play credentials, or signing secrets are in the repository.
- The build pins AGP 8.13.2 / compileSdk 36. Newer AndroidX releases require AGP 9.1/SDK 37
  and were intentionally not adopted in this pass.
- Lint has 0 errors and 24 warnings, mostly dependency/version advisories, Compose modifier
  ordering, and adaptive-icon qualifier notices.
- The app is AAOS-only (`android.hardware.type.automotive` required). It will not install on
  a phone or non-automotive emulator.

## Car App Library

`CarInfo`/`CarSensors` require a host-bound `CarAppService` and a template surface. That
component is not integrated in v1, so the report carries
`car_app_library.status = NOT_TESTED`. This is a scope boundary, not a claim that the
library is unsupported. Direct read-only VHAL access is the implemented path.

## Safety state

If `CarUxRestrictionsManager` is unavailable, the app requires an explicit parked
confirmation. That confirmation is not a technical guarantee; a person can lie to it. The
activity is not distraction-optimized and AAOS may block it while driving.
