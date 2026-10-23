# Google Play Internal Testing notes

This is the handoff checklist for getting the probe onto the LYRIQ through an AAOS
internal test track. **No Play Console operation was performed for this repository.** The
actual acceptance result remains an open test.

Official background:

- [Distribute to cars](https://developer.android.com/training/cars/distribute)
- [Android Automotive OS app quality](https://developer.android.com/docs/quality-guidelines/automotive/app-quality)
- [Android 17 local-network permission](https://developer.android.com/privacy-and-security/local-network-permission)

## What this build declares

```xml
<uses-feature android:name="android.hardware.type.automotive"
              android:required="true" />
<uses-library android:name="android.car" android:required="false" />
```

The app is a **parked app**: it is not distraction-optimized, and it disables interactive
tests when `CarUxRestrictionsManager` reports a restriction. Play documentation permits a
parked AAOS-only app to use `required="true"` on an AAOS-only track.

The app is not a media, navigation, templated, or communication app. Do not label it as
one.

## Setup sequence

1. Create the Play Console app with package `com.sam2kb.lyriqprobe`.
2. Complete App content / privacy declarations. The app is a parked diagnostic, not a
   media client. It declares INTERNET only for the explicit LMS TCP test and does not
   collect VIN, location, accounts, device serials, or the LMS hostname/IP.
3. Add **Advanced settings → Form factors → Android Automotive OS**.
4. Add AAOS screenshots. Current Play guidance asks for at least two landscape and two
   portrait screenshots; the referenced dimensions are 1024 × 786 and 800 × 1280. Use the
   AAOS emulator hardware profiles, not a phone. These screenshots are **not** in this
   repository and were not generated here.
5. Select an **AAOS-only track** (dedicated automotive track), not the mobile track.
6. Upload the **signed release AAB** to Internal testing.
7. Add the Google account used on the LYRIQ as an internal tester, and make sure the vehicle
   is signed into that account and has Play Store access.
8. Install from the Play Store on the LYRIQ while parked.
9. Record the exact outcome, including any device-ineligible / policy / category message.

## Internal testing does not prove eligibility

The project plan notes that Play internal testing bypasses the normal automotive
form-factor review. That is not the same as guaranteeing eligibility. The app still has to
be accepted by the Play Console workflow, the AAOS form factor has to be enabled, the
account/device has to be eligible, and distribution policies still apply.

Specific unresolved risks:

- **Category/eligibility.** A diagnostic-only parked app may be accepted, need adjustment,
  or be rejected. No untested claim is made here.
- **Network and cleartext.** The app declares INTERNET and permits cleartext because the
  SqueezeClient path uses HTTP on the LAN. The LMS socket is explicit and sends no payload,
  but the store listing and data-safety answers must describe the network use accurately.
  Do not describe it as an offline app.
- **Media foreground service.** The diagnostic posts a temporary mediaPlayback notification
  and starts a short foreground service. This is normal Android permission use but should
  be disclosed consistently in the listing.
- **Android 17 local network.** This target-36 build uses the legacy implicit LAN path. A
  target-37 SqueezeClient port must declare and request `ACCESS_LOCAL_NETWORK`; do not add
  the permission to this target-36 probe merely to suppress that migration work.
- **Sensitive permission scope.** Unused location and Bluetooth-scan permissions remain
  out of the manifest; they still appear in the catalogue. INTERNET is used only by the
  explicit LMS test. Keep the declared set minimal.
- **Screenshots / listing.** AAOS screenshots and a store listing are still required.
- **Accessibility/foreground-service declarations.** Answer Play Console questions about
  notifications and foreground services factually; the media service is a probe, not a
  persistent player.

If Play refuses the build, the exact block should be recorded before considering any
alternative. Sideloading the debug APK is a fallback for capability testing only and is not
a Play distribution path.

## What to capture in the result

- Play Console app/form-factor status and any policy warning text.
- Whether the Internal testing track offers the app to the LYRIQ.
- Package name/version installed on the car (`com.sam2kb.lyriqprobe`, version 1).
- Whether the mediaPlayback foreground service and its notification appear on AAOS.
- Whether the car can reach the LMS HTTP and Slimproto ports.
- JSON report exported from the vehicle.
- Any AAOS launcher/driving-restriction behavior.

Do not mark distribution complete until the release AAB is signed with the account's upload
key and actually installs from Play on the LYRIQ.
