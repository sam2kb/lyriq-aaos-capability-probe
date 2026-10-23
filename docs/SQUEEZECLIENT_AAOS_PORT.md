# SqueezeClient AAOS port assessment

This note records what the existing SqueezeClient fork needs on Android Automotive OS and
which facts the probe is designed to establish. It is based on source inspection of
`/srv/dev/squeezeclient` (Media3 local playback, LMS HTTP/Slimproto, WorkManager downloads),
not on an AAOS build or a vehicle run.

## What the fork already contains

- `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
  `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.
- Media3 `MediaSessionService` plus `LocalPlaybackService` with a `mediaPlayback`
  foreground-service type.
- `usesCleartextTraffic="true"`; LMS server URLs are built as `http://…`.
- LMS HTTP API (normally port 9000) and Slimproto local-player TCP socket (port 3483).
- Local decoders for MP3, FLAC, OGG/Vorbis, and AAC through Media3/custom extractors.
- WorkManager download path with a `dataSync` foreground service.
- Boot receiver and Android 12+/15-aware startup worker.
- A phone-oriented View/Material 3 UI with a nav drawer and multi-pane now-playing screen.

## What the probe now measures

| Area | Probe output |
| --- | --- |
| Codecs | Platform audio decoders, relevant MIME support, hardware/software flags |
| Audio path | Native rate, frames/buffer, min buffers, direct PCM support, output devices |
| Active audio | One-second silent AudioTrack write; play state and release |
| Media session | Platform MediaSession/MediaBrowserService class availability |
| Notifications/FGS | POST_NOTIFICATIONS, notification enablement, FGS permissions, actual mediaPlayback start/stop |
| LMS network | INTERNET, cleartext policy, ACCESS_LOCAL_NETWORK boundary |
| LMS active | TCP reachability to HTTP and Slimproto ports, latency, error class |
| Storage | SAF write/read/delete in a user-selected folder |
| AAOS state | Build, car service, driving restrictions, system features |

The probe deliberately **does not** bundle Media3/ExoPlayer. The media results are a
platform-readiness signal; the fork's actual Media3 pipeline still needs a port build.

## Manifest/build changes for an AAOS port

1. Add the automotive feature and decide distribution:

   ```xml
   <uses-feature android:name="android.hardware.type.automotive"
                 android:required="true" />
   ```

   A parked app distributed on an AAOS-only track can use `required="true"` according to
   Play's current distribution guidance. Do not mark the activity distraction-optimized.

2. Keep `INTERNET`, cleartext policy, and the existing manifest network stack. The probe
   intentionally mirrors the fork's `usesCleartextTraffic="true"` so the LAN test is meaningful.

3. Android 17 / targetSdk 37 local-network migration:

   - Add `android.permission.ACCESS_LOCAL_NETWORK` and request it at runtime before the
     first LMS socket. It is in the `NEARBY_DEVICES` group.
   - The current fork targets 36; on Android 16 that path is legacy/implicit. A target-37
     build will be blocked by default without the permission.
   - Do not add the permission to this target-36 diagnostic solely to hide the migration;
     the probe reports the boundary instead.

4. Keep the Media3 `MediaSessionService` and both foreground-service types. Verify the
   actual LMS Media3 pipeline on the car, including notification visibility and audio focus.

5. Add an AAOS layout pass: the current phone UI needs landscape/large-screen testing. A
   full-screen parked app does not automatically become a Car App Library app; do not
   assume template/media category eligibility.

6. Keep the download path's SAF/app-private storage behavior. The probe's SAF test proves
   only that the app can write to a folder the user selects.

## Go/no-go questions for the port

- Do all four required decoders exist on the LYRIQ's AAOS image? (Probe: Media.)
- Does a real `mediaPlayback` foreground service start, notify, and stop? (Probe: Media.)
- Can the car open LMS HTTP and Slimproto TCP? (Probe: Network & LMS.)
- Does LAN access survive the Android 17 target bump without a permission prompt failure?
  (Probe: policy; needs a target-37 test build.)
- Does the AAOS launcher expose the activity while parked, and block it while driving?
  (Probe: driving state; real-car UI test.)
- Does the phone UI remain usable on the center display? (Not probed; needs screenshots.)
- Does Play accept a SqueezeClient-style media app on an AAOS track, and what category/track
  restrictions apply? (Not probed; Play Console.)

## Suggested port sequence

1. Run this probe on the LYRIQ; export JSON.
2. Add the automotive feature and a landscape test build of SqueezeClient.
3. Test a real LMS connection and local playback with Media3, then the FGS/notification path.
4. Add `ACCESS_LOCAL_NETWORK` only when moving targetSdk to 37.
5. Revisit Play category/track eligibility before upload.
6. Keep dashcam work separate; this probe's camera path is independent of the media port.
