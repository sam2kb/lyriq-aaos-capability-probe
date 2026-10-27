# SqueezeClient AAOS port decision record

**Status:** findings only — no SqueezeClient code was changed.
**Current direction (2026-10-26):** clean-room, AAOS-native, lean rewrite with selected
features. This supersedes the earlier “extract a shared core and add an automotive module”
recommendation below; the port findings remain the evidence base.
**Source inspected:** `sam2kb/squeezeclient` `origin/main` at
`4bcb826a3d1b3bba8e51a88cd196183e388d8737` (version 2.5, 2026-10-23).
**Probe evidence:** v1.1.0 build; no new exported vehicle JSON was present in this repository
when these findings were recorded.

## Bottom line

A port is technically possible, but **not by changing a few manifest lines**. More
importantly, a port is no longer the preferred implementation. The best option is a
**clean-room, AAOS-native, lean app** that keeps SqueezeClient's product ideas and LMS
protocol behavior but discards its phone shell, Wear/GMS code, and media-service
architecture.

The in-car interface should be a native AAOS media experience using Car App Library media
templates (best car-native UI; currently beta/internal-closed) or, if production is needed
now, a car-host MediaBrowser/MediaLibraryService app. The phone UI can remain in the
existing app; it is not the car UI.

The first AAOS release should be **controller-only** unless local playback is proven on the
LYRIQ. Local playback, downloads, boot auto-start, Wear support, and custom volume control
are phase-2 or excluded features.

## Clean-room AAOS-native plan (current direction)

This is a new app, not a fork of the phone `app` module. Keep the phone/F-Droid release
untouched and build a separate AAOS-only artifact alongside it (new repo or new top-level
project). Reuse **protocol knowledge, wire formats, server semantics, and selected tests or
fixtures**; do not migrate the Android UI, navigation, Wear/GMS layers, or service graph
wholesale.

### Native, lean stack

- Kotlin + coroutines/Flow.
- Media3 `MediaSession`/player for LMS player state and car media controls.
- Car App Library media templates (`CarAppService`, `MediaPlaybackTemplate`,
  `SectionedItemTemplate`, `MediaPlaybackManager`) for the car UI.
- OkHttp + kotlinx.serialization for LMS HTTP/CometD, with the same protocol semantics
  proven by the existing app.
- DataStore or simple SharedPreferences for server configuration.
- Manual constructor injection; no Wear, Play Services, Hilt, Room, or web wrapper unless a
  concrete need appears.

The only non-template UI in the AAOS app should be a small vehicle-optimized settings/sign-in
activity for the LMS host, invoked through the car settings surface. It must not be marked
distraction-optimized. For Car App Library templates, the generated `CarAppActivity` is the
launcher/entry point and must be distraction-optimized; do not retain the phone
`MainActivity` as a car launcher.

### v1 feature boundary (controller-only)

Include:

- LMS server setup and connection diagnostics.
- Library browse/search: artists, albums, tracks, playlists, favorites if cheap.
- Player selection.
- Play/pause/next/previous/seek, queue, volume for LMS players.
- Now playing metadata/artwork through the template.
- Reconnect/error states, including a useful “cannot reach LMS” message.
- Template host and network permission checks.

Explicitly exclude from v1:

- Local on-car playback and Slimproto/audio output.
- Downloads/offline library.
- Wear companion, GMS flavor, Android Auto metadata/UI.
- Phone drawer, MotionLayout now-playing, slideshow, artwork animations, drag/drop player
  management, themes.
- Device volume mode; use the car's volume policy.
- Boot auto-start.

### Phase 2 after vehicle evidence

Local playback is the largest technical and policy risk. Add it only after the LYRIQ probe
shows audio output, media foreground-service behavior and LAN access. If it is required,
rework it under the MediaSessionService model rather than copying the current plain
`LocalPlaybackService`; test audio focus and car volume first. Downloads can follow as a
parked-only, user-managed offline feature.

### Licensing check

SqueezeClient is GPLv3. If code, tests, or resource files are copied into the clean-room
app, the new app must be GPL-compatible. A clean-room reimplementation based on protocol
behavior and public LMS documentation is a separate licensing decision. Resolve this before
copying implementation files.

### Why not the shared-module port

A shared module could preserve more code, but it carries the phone UI assumptions and the
existing local-player/FGS architecture into the car. A lean rewrite makes the AAOS
constraints explicit, avoids dragging Wear/GMS into the car artifact, and gives a smaller
surface for Play quality review. It also avoids spending time extracting a core from a
large app before proving the car UI model.

## What the latest source already has

- LMS HTTP/CometD connection code and Slimproto local-player socket.
- Media3 `MediaSessionService` (`service/mediasession/MediaService.kt`) for LMS player
  control.
- A separate `LocalPlaybackService` with `foregroundServiceType="mediaPlayback"` and
  Media3/ExoPlayer playback.
- Media notification, POST_NOTIFICATIONS, `mediaPlayback` and `dataSync` foreground-service
  permissions.
- WorkManager downloads and an Android 15-aware boot/startup worker.
- Cleartext HTTP to LMS (`usesCleartextTraffic="true"`), INTERNET permission.
- Responsive phone resources, including landscape layouts, but no car-specific UI or
  Android Auto/AAOS metadata.
- Wear/GMS support in the `gms` flavor and `:wear`/`:wearapi` modules.

## Hard findings

### 1. The current app is not an AAOS media app

- There is no `android.hardware.type.automotive` feature, no
  `com.android.automotive` metadata, no `automotive_app_desc.xml`, no Car App Library
  dependency, and no `CarAppService`/`CarAppActivity`.
- The current entry point is a launcher activity with a phone drawer/now-playing UI. Google's
  AAOS media guidance says the vehicle media host draws the media UI and a classic AAOS media
  build should not expose launcher activities. A templated app instead uses a
  distraction-optimized `CarAppActivity`.
- `MediaService` is a Media3 `MediaSessionService`, but it is not a browse tree. There is no
  `MediaLibraryService`/`MediaBrowserService.onGetRoot/onGetChildren` implementation for a car
  host to browse LMS content. The manifest's `android.media.browse.MediaBrowserService` filter
  does not add that implementation.
- The phone UI, MotionLayout now-playing screen, drawer, player drag/drop, slideshow, and
  artwork transitions are not an acceptable car media UI. They can remain in the phone
  module; they cannot be the AAOS UI.

### 2. Media apps need a dedicated AAOS-only track

Google Play's current distribution rules say media apps must use a dedicated Android
Automotive OS track. A media artifact cannot support both the mobile and AAOS experiences.
That means a separate AAOS artifact/release is not just cleaner; it is the distribution
model for this category.

**Recommendation:** keep the existing phone/F-Droid release and add a separate
`:automotive` app module (or an automotive-only flavor with its own manifest/source set)
that depends on a shared core. Use the same package name if you want one store listing and
the same statistics; a separate package is optional but creates a separate listing and
review history. In either case use an AAOS-only track and a higher version code for the
automotive artifact.

### 3. The current local-player foreground service is a compliance risk

`LocalPlaybackService` is a plain `Service` with `foregroundServiceType="mediaPlayback"`,
separate from the Media3 `MediaSessionService`. The official AAOS media FAQ currently says
the only allowed foreground-service use case is downloading content for offline use. The
current service may be acceptable only if it is tied to a proper MediaSession/playback
architecture, or it may be rejected/restricted by AAOS/Play. This needs an explicit device
test and likely a refactor.

If local playback cannot be made compliant, the first AAOS release should be
**controller-only**: browse the LMS library and control existing LMS players, without
playing audio on the car head unit.

### 4. Templated media apps are still beta

Current Android for Cars documentation says Car App Library media templates are in beta and
can be published only to **internal and closed testing** tracks; open/production tracks are
not yet permitted. That is acceptable for the immediate LYRIQ internal-test goal, but it is
a production blocker. The alternative is the classic car-host MediaBrowser/MediaLibraryService
model, which gives up the custom in-app look and uses the vehicle's media UI.

### 5. Android 17 local-network permission is a migration item

The fork targets 36 today and uses the legacy implicit LAN path. Android 17 / target 37
requires the `ACCESS_LOCAL_NETWORK` runtime permission for LMS/LAN sockets. Add it and
request it before the first LMS connection when the target moves to 37. The probe already
reports the permission boundary but does not declare it in order to keep the target-36
behavior meaningful. See the [Android local-network guidance](https://developer.android.com/privacy-and-security/local-network-permission).

## What the restricted LYRIQ access means

SqueezeClient does **not** use the camera, VHAL, vehicle properties, or vehicle sensors.
Camera/VHAL restrictions reported by the probe therefore do not block the port.

The port depends on the probe's other results:

- Audio decoders and the AudioTrack/audio-output path;
- `mediaPlayback` foreground service and notification behavior;
- INTERNET/local-network/cleartext access to the LMS host;
- app-private/SAF storage for downloads, if downloads are kept;
- AAOS media host / Car App Library host availability on the LYRIQ.

If local network, audio output, notifications, or local-player foreground-service behavior
is restricted on the car, the port is either controller-only or blocked. These are the
gating unknowns because the vehicle JSON was not committed to this repository.

## Feature decision for the AAOS build

| Feature | AAOS v1 decision | Reason |
| --- | --- | --- |
| Phone UI, drawer, MotionLayout now-playing, slideshow | Remove from AAOS build; keep phone-only | Car media UI is host/template drawn |
| Wear companion, Wear API, GMS flavor | Exclude from AAOS build | No Wear/phone companion role in the car |
| LMS library browse/search | Keep | Core value; implement in templates/host media tree |
| Playback/volume control of LMS players | Keep | Core controller function |
| Multi-player management/drag/drop | Simplify for v1 | Car UI/UX restrictions; phone interaction model not suitable |
| Server setup/settings | Keep as vehicle-optimized settings/sign-in activity | Needed; must not be distraction-optimized |
| Local device playback | Defer until audio/audio-focus/FGS test passes; then refactor under MediaSession | Plain FGS is a policy/technical risk |
| Device-volume mode | Turn off; use car volume | Avoid fighting the vehicle audio policy |
| Downloads/dataSync | Defer for v1, or keep parked-only | FGS is allowed for downloads, but storage/UX needs validation |
| Boot auto-start of local player | Off by default | Android 15+ boot/FGS restrictions and car expectations |
| Android Auto metadata/Wear | Do not include in AAOS artifact | Different platform; avoid manifest/UI conflicts |
| Voice/search | Required for a Car App Library media app quality review | VC-1/MFT requirements if templates are used |

## Architecture if the older module-port route were chosen

This section is retained as evidence for why a clean-room rewrite is preferred. It is not
the current plan.

1. Extract a shared `:core` (or `:media-core`) module from the current `app`: LMS connection,
   CometD models, player state, MediaSession control, preferences/server config, and optional
   local playback/download code.
2. Keep `:app` as the phone app and `:wear` as phone companion; keep the FOSS flavor free of
   Play Services. Build the AAOS artifact from a new `:automotive` module using the FOSS
   base.
3. Add the automotive manifest:
   - `android.hardware.type.automotive` required true;
   - `android:appCategory="audio"`;
   - `com.android.automotive` metadata + `automotive_app_desc.xml` with `<uses name="media"/>`;
   - no phone launcher activities in the automotive module;
   - for templates: `androidx.car.app:app-automotive`, `CarAppService` category `MEDIA`,
     `androidx.car.app.MEDIA_TEMPLATES`, min car API 8, and a distraction-optimized
     `CarAppActivity`;
   - for a split template APK, `android.software.car.templates_host.media` required true;
     for a single APK, false and a MediaBrowserService fallback.
4. Implement either:
   - **Car App Library templates**: `MediaPlaybackTemplate`, library/search screens,
     `MediaPlaybackManager.registerMediaPlaybackToken` using the Media3 platform token, and
     vehicle-optimized server settings; or
   - **MediaLibraryService/MediaBrowserService**: car-host browse tree, search, playback
     state, voice/search and error handling, with no custom car UI.
5. Add AAOS-specific tests: template host availability, media FGS/notification, LAN access,
   audio focus, car volume, offline/download retention, target-37 permission.

## Verdict

- **Possible?** Yes, technically. The protocol behavior and Media3 playback concepts are
  reusable, but the phone UI and service graph are not the AAOS design.
- **Recommended implementation?** A clean-room, native, lean AAOS app with a controller-only
  v1 and selected features migrated as behavior, not as a UI/module port.
- **Direct APK/manifest port?** No. The car UI and media browse integration are missing.
- **Separate AAOS-only release?** **Yes.** Build a separate AAOS-only artifact and use an
  AAOS-only track. A separate package is optional; same package keeps one listing but ties
  review/versioning together. A new repo/app package is reasonable for a clean-room rewrite.
- **Templated UI for internal testing?** Yes, but only while Google keeps media templates in
  beta/internal-closed testing.
- **Production-ready today?** Not with the phone UI or templated beta. The classic
  MediaBrowser/MediaLibraryService path is the production-compatible route, but it means
  accepting the car host's media UI.
- **Trim/disable first:** Wear/GMS, phone UI, slideshow/motion effects, device-volume mode,
  boot auto-start, and local playback/downloads until the LYRIQ probe and an AAOS media build
  prove them.

## Evidence needed to move from decision to implementation

1. LYRIQ probe JSON for media, LMS network, FGS/notification and storage, or a repeat test
   while parked.
2. The car's AAOS version and whether the Car App Library media host feature is present.
3. A minimal AAOS build of SqueezeClient with one of the two car interfaces above, tested
   first as controller-only.
4. A decision on local playback after the audio/FGS result; do not port it blindly.

## References

- [Build templated media apps for cars](https://developer.android.com/training/cars/apps/media)
- [Add Android Automotive OS support to a media app](https://developer.android.com/training/cars/media/automotive-os)
- [Distribute to cars](https://developer.android.com/training/cars/distribute)
- [Car app quality guidelines](https://developer.android.com/docs/quality-guidelines/car-app-quality)
- [Android 17 local-network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [SqueezeClient source](https://github.com/sam2kb/squeezeclient)
