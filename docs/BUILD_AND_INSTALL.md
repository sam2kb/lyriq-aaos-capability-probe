# Build and install

## Verified toolchain

The checked-in artifacts were built with:

| Component | Version |
| --- | --- |
| Temurin OpenJDK | 21.0.12.1+1 |
| Gradle wrapper | 8.13 (SHA-256 pinned) |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.21 |
| Compose BOM | 2025.10.01 |
| Android SDK platform | 36 |
| Build-tools | 36.0.0 |
| Platform-tools | 37.0.1 |
| `minSdk` | 29 |
| `targetSdk` / `compileSdk` | 36 |

The project deliberately stays on the AGP 8.x/SDK 36 line. `androidx.core:core-ktx`
1.19.x requires AGP 9.1 and `compileSdk 37`; upgrading only that dependency breaks the
build. Move the whole toolchain together if you choose to upgrade.

## Commands

```sh
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"

./gradlew :probe-core:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
```

The core tests use the JVM and do not need a device. `android.car` is resolved through
Gradle's `useLibrary("android.car")` from the installed platform's
`optional/android.car.jar`; it is provided by AAOS at runtime.

Outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/bundle/release/app-release.aab
```

## Signing the release bundle

`app/build.gradle.kts` only creates a release signing config when
`keystore.properties` exists at the repository root. Without it, the AAB is generated
unsigned and cannot be uploaded to Play.

Generate an upload key **outside the repository** and configure it:

```sh
keytool -genkeypair -v \
  -keystore "$HOME/lyriq-upload.jks" \
  -alias lyriq-upload \
  -keyalg RSA -keysize 4096 -validity 10000

cp keystore.properties.example keystore.properties
# storeFile=/home/you/lyriq-upload.jks
# storePassword=...
# keyAlias=lyriq-upload
# keyPassword=...

./gradlew clean bundleRelease
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

The file must say the JAR is signed. Google Play App Signing should hold the app-signing
key; this upload key is separate. Never commit the keystore, passwords, or Play
credentials. `.gitignore` excludes `keystore.properties`, `*.jks`, and `*.keystore`.

## Sideload

This APK requires the automotive feature and is intended for AAOS:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build has application ID `com.sam2kb.lyriqprobe.debug` and is signed with the
local debug key. A phone or a non-automotive emulator will not install it because
`android.hardware.type.automotive` is required.

Device setup (developer mode, USB debugging, transport authorization) is outside this
repository's build boundary. Do not use the probe while driving.

## Play internal testing

Upload the signed release AAB to an AAOS track; see
[PLAY_INTERNAL_TESTING.md](PLAY_INTERNAL_TESTING.md). An unsigned AAB is not a Play
artifact.

## Lint and warnings

`./gradlew :app:lintDebug` completes with **0 errors**. The verified build has 24 warnings,
mostly newer-version advisories for pinned dependencies plus Compose modifier-ordering and
adaptive-icon qualifier notices. They do not block the build. See
[TEST_EVIDENCE.md](TEST_EVIDENCE.md).

## Reproducibility limits

- Gradle dependencies are resolved from Google Maven/Maven Central and are not checksum
  locked. The wrapper distribution itself is checksum-pinned.
- The local build container is not part of the repository; the JDK/SDK versions above are
  the required toolchain contract.
- The artifacts are reproducible from this checkout and tag, but byte-for-byte
  reproducibility across environments is not claimed.
