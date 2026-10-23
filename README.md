# LYRIQ AAOS Capability Probe

An experimental, read-only Android Automotive OS diagnostic app for a Cadillac LYRIQ.
It inventories what an ordinary third-party app can see through Camera2, Android
sensors, `CarPropertyManager`, the Storage Access Framework, connectivity APIs, and
`VpnService`.

**This is not a dashcam and does not record video.** It does not write vehicle
properties, request root, use hidden APIs, or run background camera capture.

## Status

| Item | Result |
| --- | --- |
| Source, Gradle wrapper, debug APK | Built |
| Unit tests | 14 passed, 0 failed |
| Android lint | 0 errors, 24 warnings |
| Release AAB | Built, structurally validated, **unsigned** until an upload key is configured |
| AAOS emulator run | **Not performed** — this host has no `/dev/kvm` |
| Cadillac LYRIQ run | **Not performed** — no vehicle/device access |
| Play Console eligibility | **Not verified** — no Play Console or tester access |
| Car App Library `CarInfo`/`CarSensors` | Not integrated; reported as `NOT_TESTED` |

The compiled evidence, hashes, and exact limitations are in
[TEST_EVIDENCE.md](docs/TEST_EVIDENCE.md). Read
[KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) before treating any result as a
vehicle conclusion.

## The evidence ladder

The app deliberately separates discovery from active proof:

| Discovery | Active proof |
| --- | --- |
| Camera2 exposes an ID | The camera opens and closes |
| A CameraDevice opens | One JPEG/YUV frame is received in memory and discarded |
| `getPropertyList()` lists a property | A value is read and its type/status recorded |
| A storage volume appears | SAF create/write/read/verify/delete succeeds in a user-selected tree |
| `VpnService` resolves and `prepare()` returns an intent | Consent is granted, a temporary TUN is established, and then closed |
| Sensor metadata is listed | A 10-second sample receives events and cleans up its listener |

Zero camera IDs means **"No cameras exposed to this app,"** not "the LYRIQ has no
cameras." Factory/system cameras are often hidden from ordinary Camera2 enumeration.

## Modules

- **System** — Android/build/device, package install source, memory, CPU, platform features,
  car-service availability, and app permissions.
- **Permissions** — curated matrix of standard and `android.car` permissions: existence,
  declaration, grant, protection level/flags, requestability, and protected status.
  Runtime requests are explicit, one permission at a time.
- **Cameras** — Camera2 IDs, facing, hardware level, capabilities, stream sizes,
  physical/logical IDs, flash, automotive lens metadata (API 36), system-camera boundary,
  EVS boundary, open/close, and optional single-frame capture.
- **Vehicle data** — read-only `CarPropertyManager.getPropertyList()` metadata, plus an
  explicit allowlist of informational/EV values: make/model/year/trim, battery, charge
  port/state, range, speed, odometer, exterior temperature, efficiency. **VIN is never
  read.** No property writes or actuator calls exist.
- **Android sensors** — `Sensor.TYPE_ALL` inventory, metadata, and an explicit 10-second
  sample with guard checks and listener cleanup.
- **Storage** — `StorageManager` volumes, app-specific external directories, SAF tree
  selection, and a create/write/flush/read/SHA-256/delete test that only removes the file
  it created.
- **Network & VPN** — Bluetooth adapter state, Wi-Fi state, network transports,
  location-provider/GNSS availability, USB host inventory, displays, audio routes, and a
  VPN consent/temporary-TUN capability test.
- **Reports** — human summary, structured JSON, local history, SAF export, and share.

## VPN capability test

This was added specifically to test whether a normal AAOS app can run a WireGuard-style
tunnel before investing in a WireGuard port.

1. Quick Scan calls `VpnService.prepare()` and records `CONSENT_REQUIRED` or
   `ALREADY_AUTHORIZED` **without showing a dialog**.
2. The active test displays a confirmation, then launches the system VPN consent Intent
   returned by `prepare()`.
3. On consent, a temporary `VpnService` creates a TUN interface with one private address,
   `10.254.253.1/32`.
4. It adds **no routes, no DNS servers, no allowed/disallowed apps, and no underlying
  networks**, then closes the descriptor and stops the service immediately.
5. The JSON records the consent result, whether the interface was established, whether it
   closed, duration, addresses, routes, DNS, and error class/message.

This does not prove WireGuard handshaking, key storage, packet forwarding, throughput, or
survival in the background. It should not route vehicle traffic. No `INTERNET` permission
is declared.

**Google Play policy risk:** Play's
[VpnService policy](https://support.google.com/googleplay/android-developer/answer/9888170)
restricts VpnService to apps whose core functionality is VPN or to listed exceptions, and
requires documented, encrypted tunnels to a remote endpoint. A diagnostic-only TUN probe
is unlikely to qualify for production distribution. Internal testing may still be refused
or policy-flagged. Treat VPN distribution eligibility as unresolved until Play Console
review says otherwise. Do not submit this probe to production as a VPN app.

## Safety and privacy boundaries

- No vehicle writes, actuator commands, locks, climate, gear, lights, or charging control.
- No MediaRecorder, continuous capture, preview surface, or background camera use.
- No root, hidden APIs, arbitrary Binder calls, reflection, or permission bypasses.
- No `SYSTEM_CAMERA`, `MANAGE_EXTERNAL_STORAGE`, `RECORD_AUDIO`, or `INTERNET` permission.
- No VIN value, precise location, SSID/BSSID, IP/MAC address, USB serial, or `ANDROID_ID`.
- Removable-volume UUIDs in paths are replaced with `[VOLUME]`.
- Reports are local app-private files; nothing is uploaded. Export/share is user-initiated.
- The activity is **not** distraction-optimized. AAOS restrictions lock interactive tests;
  if restrictions cannot be read, an explicit parked confirmation is required.
- All active tests check the parked guard again while running where practical.

The quick scan writes only its local JSON report. It does not open cameras, read vehicle
values, sample sensors, write a storage test file, request VPN consent, or route traffic.

## Build

Requirements:

- JDK 21 (AGP 8.13 expects 17+; the verified build used Temurin 21)
- Android SDK platform **36**
- Android build-tools **36.0.0**
- Network access for Gradle dependencies
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` pointing at the SDK

The repository pins Gradle 8.13 (with distribution checksum), AGP 8.13.2, Kotlin 2.2.21,
and Compose BOM 2025.10.01. Do not silently bump `androidx.core` to 1.19.x: it requires
AGP 9.1 and `compileSdk 37`, while this build is intentionally on the stable AGP 8.x /
SDK 36 line. See `docs/BUILD_AND_INSTALL.md`.

```sh
./gradlew :probe-core:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:bundleRelease
```

Artifacts (paths in the checkout):

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/bundle/release/app-release.aab`

The debug APK is debug-key signed and installable by sideload. The release AAB is
**unsigned** unless `keystore.properties` is configured; see
`docs/BUILD_AND_INSTALL.md` and `keystore.properties.example`.

### Signing and Play upload

Never commit the upload key. Use a file outside the repository:

```sh
keytool -genkeypair -v \
  -keystore "$HOME/lyriq-upload.jks" \
  -alias lyriq-upload \
  -keyalg RSA -keysize 4096 -validity 10000

cp keystore.properties.example keystore.properties
# edit keystore.properties with the absolute storeFile and secrets
./gradlew clean bundleRelease
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

Google Play App Signing should hold the app-signing key; the upload key stays with the
account owner. `keystore.properties`, `*.jks`, and `*.keystore` are git-ignored.

## Installation and Play testing

Sideload (requires an AAOS developer-mode/USB-debugging setup):

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug package is `com.sam2kb.lyriqprobe.debug`; the release package is
`com.sam2kb.lyriqprobe`.

For Google Play Internal Testing:

1. Upload the signed release AAB to a dedicated **Android Automotive OS** track / form
   factor, not the mobile track.
2. Add the automotive form factor in Play Console and provide the required AAOS
   screenshots (current requirements: at least two landscape and two portrait shots).
3. Add the GM/Cadillac test account as an internal tester; install from the Play Store on
   the LYRIQ signed into that account.
4. Record the exact Play Console result, including any device-eligibility or policy block.

The manifest requires `android.hardware.type.automotive`, so the app is AAOS-only. Play
documents that a parked AAOS-only app may use this configuration on an AAOS-only track.
Internal testing may avoid the normal automotive form-factor review, but it is not a
guarantee of policy or device eligibility. This repository cannot verify Play Console
acceptance without account access. Details and unresolved items:
[PLAY_INTERNAL_TESTING.md](docs/PLAY_INTERNAL_TESTING.md).

## Repository layout

```text
app/                 Compose UI, activity, VPN service, report store
probe-core/          scanners, models, runner, JSON report factory, unit tests
docs/                architecture, build, Play, vehicle plan, evidence, limitations
keystore.properties.example
tools/check-boundaries.sh
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Build and install](docs/BUILD_AND_INSTALL.md)
- [Play internal testing notes](docs/PLAY_INTERNAL_TESTING.md)
- [Real-vehicle test plan](docs/REAL_VEHICLE_TEST_PLAN.md)
- [Report schema](docs/REPORT_SCHEMA.md)
- [Privacy](docs/PRIVACY.md)
- [Known limitations](docs/KNOWN_LIMITATIONS.md)
- [Test evidence](docs/TEST_EVIDENCE.md)

## Next decision

Only after a real Vehicle Report:

- **Path A** — camera frame capture confirmed: evaluate a separate Camera2/MediaCodec
  recorder with segmented MP4 and SAF-selected storage.
- **Path B** — cameras protected but storage accessible: investigate supported GM export
  or integration paths only.
- **Path C** — cameras and storage restricted: document the exact restriction and evaluate
  manufacturer-authorized APIs or external recording hardware.

Do not implement recording before the report proves that frames are obtainable.
