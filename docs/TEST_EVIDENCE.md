# Test and build evidence

Generated from the handoff checkout on **2026-10-23**. This is build/test evidence, not a
vehicle test.

## Environment

| Component | Value |
| --- | --- |
| JDK | Temurin OpenJDK 21.0.12.1+1 |
| Gradle | 8.13 wrapper, distribution checksum `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` |
| AGP | 8.13.2 |
| Kotlin | 2.2.21 |
| Android SDK | platform 36, build-tools 36.0.0, platform-tools 37.0.1 |
| Host device access | none; no `/dev/kvm` |
| Source commit | `229c97d34ba314600c2e1bac60d2ffab780bf3c3` (tag `v1.0.0-build.1`) |

## Commands

```sh
./gradlew :probe-core:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:bundleRelease \
  --no-daemon --console=plain

./tools/check-boundaries.sh
```

Both completed successfully.

## Unit tests

`probe-core:testDebugUnitTest`:

| Suite | Tests | Failures | Errors |
| --- | ---: | ---: | ---: |
| `PermissionClassifierTest` | 3 | 0 | 0 |
| `ReportFactoryTest` | 5 | 0 | 0 |
| `TestHarnessTest` | 4 | 0 | 0 |
| `FormatTest` | 2 | 0 | 0 |
| **Total** | **14** | **0** | **0** |

Coverage is intentionally narrow: status mapping and timeouts, permission classification,
formatters, report counts/errors, top-level JSON keys, VPN serialization, and round-trip
decode. Android runtime probes, Compose UI, VPN consent, Camera2, SAF, and CarPropertyManager
have not been executed on a device.

## Lint

`./gradlew :app:lintDebug`:

```text
0 errors, 24 warnings
```

Warnings are newer-dependency/version advisories (Gradle, AGP, Kotlin, AndroidX), Compose
`Modifier` parameter ordering, adaptive-icon qualifier/monochrome notices, and a newer
target-SDK advisory. There are no lint errors. Full report:
`app/build/reports/lint-results-debug.html` in the build workspace.

## Static safety check

```text
PASS: no forbidden manifest permission, vehicle write, recording, route, identifier, or hidden-API pattern found
```

`tools/check-boundaries.sh` checks the manifest and Kotlin sources for forbidden permissions,
vehicle setters, MediaRecorder/repeating capture, VPN route/DNS/allowed-app calls, sensitive
identifier/location calls, reflection/ServiceManager/Binder use, and accidental inclusion of
`INFO_VIN` in the value-read allowlist.

## Artifacts

| Artifact | Package | Size | SHA-256 |
| --- | --- | ---: | --- |
| `app-debug.apk` | `com.sam2kb.lyriqprobe.debug` | 29,835,264 bytes | `ac543c7985b06aef791243ea7cb60727976e31db2f106fd51fda253e6d97dcbd` |
| `app-release.aab` | `com.sam2kb.lyriqprobe` | 7,716,546 bytes | `5e6e5e825161a20755d6e3bdb9c8d329cc4c72ce4787ef734a1b7cb4931af8fd` |

### Debug APK

`apksigner verify --verbose`:

```text
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
```

Debug-signed only. Not a Play release artifact.

### Release AAB

`jarsigner -verify`:

```text
jar is unsigned.
```

The AAB is structurally valid but **unsigned** because no upload key was supplied. Configure
`keystore.properties` and rebuild before Play upload; instructions in
[BUILD_AND_INSTALL.md](BUILD_AND_INSTALL.md).

`bundletool-all 1.18.3 validate --bundle app-release.aab` exits `0` and reports one `base`
feature module.

Release manifest facts:

```text
package="com.sam2kb.lyriqprobe"
minSdkVersion=29
targetSdkVersion=36
compileSdkVersion=36
uses-feature android.hardware.type.automotive required=true
uses-feature android.hardware.camera required=false
VpnProbeService exported=true, permission=android.permission.BIND_VPN_SERVICE
```

Manifest permissions:

```text
android.permission.CAMERA
android.permission.BLUETOOTH_CONNECT
android.permission.ACCESS_NETWORK_STATE
android.permission.ACCESS_WIFI_STATE
android.car.permission.CAR_INFO
android.car.permission.CAR_ENERGY
android.car.permission.CAR_ENERGY_PORTS
android.car.permission.CAR_SPEED
android.car.permission.READ_CAR_DISPLAY_UNITS
android.car.permission.CAR_MILEAGE
android.car.permission.CAR_SENSORS
```

No `INTERNET`, location, storage, microphone, or `SYSTEM_CAMERA` permission is declared.

## Not performed

- AAOS emulator boot/install. The host has no `/dev/kvm` and no AAOS system image was
  installed; this environment cannot substantiate a launch claim.
- Installation or operation on the Cadillac LYRIQ.
- Camera enumeration/open/frame capture on real hardware.
- CarPropertyManager reads on real VHAL.
- Sensor sampling on real hardware.
- SAF write test on the rear SD reader or USB.
- VPN consent dialog and TUN establishment on Cadillac AAOS.
- Play Console upload, form-factor review, eligibility, or tester install.
- Real-device UI screenshots or accessibility inspection.

These are the remaining acceptance tests, not failed tests.
