# Google Play Internal Testing notes

This is the handoff checklist for getting the probe onto the LYRIQ through an AAOS
internal test track. **No Play Console operation was performed for this repository.** The
actual acceptance result remains an open test.

Official background:

- [Distribute to cars](https://developer.android.com/training/cars/distribute)
- [Android Automotive OS app quality](https://developer.android.com/docs/quality-guidelines/automotive/app-quality)
- [Google Play VpnService policy](https://support.google.com/googleplay/android-developer/answer/9888170)

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
2. Complete App content / privacy declarations. The app is offline and declares no
   `INTERNET` permission. It does not collect VIN, location, accounts, or device serials.
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
- **VPN policy.** The Google Play VpnService policy permits VpnService only for apps whose
  core functionality is VPN or for listed exceptions, and requires encrypted tunnels to a
  remote endpoint. This probe creates a temporary local TUN with no remote endpoint. It is
  a capability test, not a VPN product. It may be rejected or policy-flagged even on
  internal testing. Do not disguise the app as a VPN or network tool to get around this;
  no remote tunnel or encryption endpoint exists.
- **Unused permissions.** Location and Bluetooth-scan permissions were deliberately left
  out of the manifest because the app does not use them. They still appear in the
  permission catalogue. Keep it that way unless a real feature needs them.
- **Screenshots / listing.** AAOS screenshots and a store listing are still required.
- **VPN declaration.** If Play Console asks for a VPN declaration, answer factually. The
  probe does not establish a remote tunnel and should not be represented as one.

If Play refuses the build, the exact block should be recorded before considering any
alternative. Sideloading the debug APK is a fallback for capability testing only and is not
a Play distribution path.

## What to capture in the result

- Play Console app/form-factor status and any policy warning text.
- Whether the Internal testing track offers the app to the LYRIQ.
- Package name/version installed on the car (`com.sam2kb.lyriqprobe`, version 1).
- Whether the VPN consent dialog is shown by Cadillac's system UI and the result of
  accepting it.
- JSON report exported from the vehicle.
- Any AAOS launcher/driving-restriction behavior.

Do not mark distribution complete until the release AAB is signed with the account's upload
key and actually installs from Play on the LYRIQ.
