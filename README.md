# LYRIQ AAOS Capability Probe

A read-only Android Automotive OS diagnostic app for a Cadillac LYRIQ, built to answer two
questions before more investment:

1. What cameras, vehicle properties, sensors, and storage can an ordinary AAOS app reach?
2. What would it take to run a SqueezeClient-style LMS controller and local player on AAOS?

**This is not a dashcam and does not record audio or video.** It does not write vehicle
properties, request root, use hidden APIs, or run background camera capture.

## Status

| Item | Result |
| --- | --- |
| Source, Gradle wrapper, debug APK | Built — v1.1.0 |
| Unit tests | 15 passed, 0 failed |
| Android lint | 0 errors, 24 warnings |
| Release AAB | Built, structurally validated, **unsigned** until an upload key is configured |
| AAOS emulator run | **Not performed** — this host has no `/dev/kvm` |
| Cadillac LYRIQ run | **Not performed** — no vehicle/device access |
| Play Console eligibility | **Not verified** — no Play Console or tester access |
| Car App Library `CarInfo`/`CarSensors` | Not integrated; reported as `NOT_TESTED` |

Current evidence: [TEST_EVIDENCE.md](docs/TEST_EVIDENCE.md). Read
[KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) before treating a result as a vehicle
conclusion.

## v1.1.0 change: VPN probe removed, SqueezeClient probes added

The temporary `VpnService` test, its service, permission path, report field, UI, tests, and
documentation are removed. There is no VPN consent flow or TUN interface in this build.

Added for the SqueezeClient AAOS decision:

- **Media readiness** — MediaCodec audio decoder/MIME support, AudioManager/AudioTrack
  output path, direct PCM support, MediaSession/MediaBrowserService availability,
  POST_NOTIFICATIONS state, and a real temporary `mediaPlayback` foreground-service test.
- **LMS readiness** — INTERNET, cleartext policy, Android 16/17 local-network permission
  state, and a user-initiated TCP reachability test for the LMS HTTP and Slimproto ports.
- **Port notes** — a concrete gap list in
  [SQUEEZECLIENT_AAOS_PORT.md](docs/SQUEEZECLIENT_AAOS_PORT.md).

The active media test writes one second of silent PCM through AudioTrack. The
foreground-service test posts a transient notification and stops itself. The LMS test opens
two short TCP sockets and sends no application data.

## Evidence levels

| Discovery | Active proof |
| --- | --- |
| Camera2 exposes an ID | The camera opens and closes |
| A CameraDevice opens | One JPEG/YUV frame is received in memory and discarded |
| `getPropertyList()` lists a property | A value is read and its type/status recorded |
| A storage volume appears | SAF create/write/read/verify/delete succeeds in a user-selected tree |
| A codec advertises an audio MIME | Silent AudioTrack initializes and plays; FGS starts/stops |
| An LMS policy says LAN access is allowed | The chosen HTTP and Slimproto ports actually connect |
| Sensor metadata is listed | A 10-second sample receives events and cleans up its listener |

Zero camera IDs means **"No cameras exposed to this app,"** not "the LYRIQ has no
cameras." Factory/system cameras are often hidden from ordinary Camera2 enumeration.

## Modules

- **System** — Android/build/device, package install source, memory, CPU, platform features,
  car-service availability, app permissions.
- **Permissions** — curated matrix including camera, car reads, INTERNET,
  POST_NOTIFICATIONS, media/data-sync foreground services, and the Android 17
  `ACCESS_LOCAL_NETWORK` boundary. Runtime requests are explicit and individual.
- **Cameras** — Camera2 IDs, facing, hardware level, capabilities, stream sizes,
  physical/logical IDs, automotive lens metadata (API 36), system-camera and EVS boundaries,
  open/close, optional single-frame capture.
- **Vehicle data** — read-only `CarPropertyManager.getPropertyList()` metadata plus an
  explicit allowlist of informational/EV values. **VIN is never read.** No writes.
- **Sensors** — `Sensor.TYPE_ALL` inventory and a 10-second sample with listener cleanup.
- **Storage** — volumes, app-specific external directories, SAF tree selection, and a
  create/write/flush/read/SHA-256/delete test that removes only its own file.
- **Media** — audio decoders and MIME coverage, audio output path, MediaSession APIs,
  notification state, silent AudioTrack test, and mediaPlayback foreground-service test.
- **Network & LMS** — Bluetooth/Wi-Fi/network/GNSS/USB/display/audio inventory, local
  network permission state, and explicit LMS HTTP/Slimproto TCP reachability.
- **Reports** — human summary, JSON, local history, SAF export, share.

## SqueezeClient AAOS port signals

The fork already has the right service architecture: Media3 `MediaSessionService`,
`LocalPlaybackService`, `mediaPlayback` and `dataSync` foreground services, POST_NOTIFICATIONS,
boot/WorkManager startup, and HTTP/Slimproto connectivity. The open questions this probe is
designed to answer are:

- Are MP3/FLAC/OGG/AAC decoders available on the LYRIQ's AAOS image?
- Does an ordinary app get a working audio output path on the car, including a real
  mediaPlayback foreground service and its notification?
- Can the app reach the LMS HTTP API and Slimproto port on the car's LAN?
- Does AAOS allow the required permissions now, and what changes when the app targets
  Android 17 (`ACCESS_LOCAL_NETWORK`)?
- Are the phone-oriented UI, downloads, boot startup, and background playback policies
  viable on the vehicle?

See [SQUEEZECLIENT_AAOS_PORT.md](docs/SQUEEZECLIENT_AAOS_PORT.md) for the concrete gap list.

## Safety and privacy

- No vehicle writes, actuator commands, locks, climate, gear, lights, or charging control.
- No MediaRecorder, continuous capture, preview surface, or background camera use.
- No root, hidden APIs, arbitrary Binder calls, reflection, or permission bypasses.
- No `SYSTEM_CAMERA`, `MANAGE_EXTERNAL_STORAGE`, or `RECORD_AUDIO`.
- INTERNET is declared **only** for the explicit LMS TCP test. That test is user-initiated,
  sends no HTTP payload, opens two short TCP connections, and does not write the host/IP to
  JSON. Cleartext is enabled because the SqueezeClient fork uses `http://`.
- No VIN value, precise location, SSID/BSSID, IP/MAC address, USB serial, or `ANDROID_ID`.
- Removable-volume UUIDs in paths are replaced with `[VOLUME]`.
- Reports are local app-private files; nothing is uploaded. Export/share is user-initiated.
- The activity is not distraction-optimized; active tests are locked by AAOS restrictions,
  or require an explicit parked confirmation when restrictions cannot be read.
- The media FGS test is a short, explicit action and does not play media.

Quick Scan writes its local JSON report. It does not open cameras, read vehicle values,
sample sensors, write a storage test file, start a foreground service, play audio, or open
network sockets.

## Build

Requirements:

- JDK 21
- Android SDK platform **36**
- Android build-tools **36.0.0**
- `ANDROID_HOME` or `ANDROID_SDK_ROOT`

Verified toolchain: Temurin 21.0.12.1, Gradle 8.13 (checksum-pinned), AGP 8.13.2, Kotlin
2.2.21, Compose BOM 2025.10.01. Do not bump `androidx.core` to 1.19.x without moving to
AGP 9.1/compileSdk 37.

```sh
./gradlew :probe-core:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:bundleRelease
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/bundle/release/app-release.aab`

The debug APK is debug-key signed. The AAB is **unsigned** unless `keystore.properties` is
configured; see [BUILD_AND_INSTALL.md](docs/BUILD_AND_INSTALL.md). Never commit a signing key.

## Installation and Play

The app is AAOS-only (`android.hardware.type.automotive` required). Sideload with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For Play, upload the signed AAB to a dedicated Android Automotive OS internal-testing track,
add the AAOS form factor and screenshots, and add the car's Google account as a tester. See
[PLAY_INTERNAL_TESTING.md](docs/PLAY_INTERNAL_TESTING.md). Play acceptance is not verified.

## Repository layout

```text
app/                 Compose UI, activity, media FGS service, report store
probe-core/          scanners, models, runner, JSON report factory, unit tests
docs/                architecture, build, Play, port gap list, vehicle plan, evidence
keystore.properties.example
tools/check-boundaries.sh
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [SqueezeClient AAOS port notes](docs/SQUEEZECLIENT_AAOS_PORT.md)
- [Build and install](docs/BUILD_AND_INSTALL.md)
- [Play internal testing](docs/PLAY_INTERNAL_TESTING.md)
- [Real-vehicle test plan](docs/REAL_VEHICLE_TEST_PLAN.md)
- [Report schema](docs/REPORT_SCHEMA.md)
- [Privacy](docs/PRIVACY.md)
- [Known limitations](docs/KNOWN_LIMITATIONS.md)
- [Test evidence](docs/TEST_EVIDENCE.md)

## Next decision

After a real Vehicle Report:

- **Path A** — camera frame capture confirmed: evaluate a separate Camera2/MediaCodec
  recorder with segmented MP4 and SAF-selected storage.
- **Path B** — cameras protected but storage accessible: investigate supported GM export
  or integration paths only.
- **Path C** — cameras and storage restricted: document the exact restrictions and evaluate
  manufacturer-authorized APIs or external recording hardware.

For SqueezeClient, use the media/LMS results to decide whether to port the existing
Media3 player and UI, or to build a smaller AAOS-first client. Do not add recording to this
probe.
