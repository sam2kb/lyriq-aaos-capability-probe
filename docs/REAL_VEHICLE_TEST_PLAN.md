# Real-vehicle test plan — Cadillac LYRIQ

Status: **not executed.** This is the procedure for the first parked session in the LYRIQ.
Have a second person operate the screen when possible. Do not run active tests while the
vehicle is moving or in a restricted UX state.

## Before starting

- Install the signed release from Play internal testing, or sideload the debug APK if
  allowed by the vehicle setup.
- Confirm the app version and package in Dashboard / Reports.
- Confirm the app is blocked or action-locked when AAOS reports a restricted state. If the
  UX state cannot be read, the app asks for an explicit parked confirmation.
- Start with the vehicle in Park, charging or ready state as appropriate, and note the
  ignition state in your lab notes.
- Have a way to retrieve the JSON: the Documents picker to a USB/SD volume, Share, or a
  copied file. Nothing is uploaded.

## 1. Quick Scan

1. Run Quick Scan while parked.
2. Confirm all nine steps complete and the report is saved.
3. Confirm there is no crash and the JSON is valid (Reports → Export).
4. Record:
   - camera IDs and count
   - `getPropertyList()` count
   - sensor count
   - storage volume/app-specific directories
   - media decoders, audio path, and foreground-service permissions
   - LMS network policy (no endpoint socket is opened)
   - driving restriction snapshot

Expected: no camera is opened, no sensor is sampled, no value is read, no audio is written,
no foreground service is started, and no network socket is opened by Quick Scan.

## 2. Permissions

1. Open Permissions.
2. Note `CAMERA`, car read permissions, and any `PRIVILEGED_ONLY` entries.
3. Request `CAMERA` explicitly. Grant it.
4. Record the permission result and refreshed matrix.
5. Do not attempt to obtain `SYSTEM_CAMERA` or any signature/privileged permission.

If the car does not expose a permission or returns an exception, export the report before
changing anything.

## 3. Cameras

For each exposed camera ID:

1. Run **Open + close**. Record `SUCCESS`, `DENIED`, or the exact exception.
2. If open succeeds, run **Capture 1 frame** once. This captures and discards one image;
   nothing is written to media storage.
3. Do not run repeated or continuous captures.
4. If no IDs are exposed, record "No cameras exposed to this app" exactly. Do not conclude
   the vehicle lacks cameras.

Also record the system-camera and EVS boundary results. The app cannot enumerate EVS with a
public third-party API and intentionally does not try hidden interfaces.

Questions the result answers:

- Are any exterior/factory cameras exposed to an ordinary Camera2 app?
- Can each exposed camera be opened while parked?
- Can a JPEG/YUV frame be obtained at all?
- Does the car revoke or disconnect the camera after open?

## 4. Vehicle data

1. Run **Read values now** once.
2. Record make/model/year/trim, EV battery level, charge state/port, range, speed (parked),
   odometer, exterior temperature, efficiency where present.
3. Inspect the property list for `INFO_VIN` metadata. Confirm no VIN value appears in the
   JSON.
4. Confirm no write or actuator action exists in the UI.

If every value is `DENIED` or `NOT_EXPOSED`, record that GM does not expose those VHAL
properties to this app; it may still expose them to system/OEM apps.

## 5. Android sensors

1. Sample the accelerometer, gyroscope, magnetometer, light, proximity, pressure, and any
   vendor-specific sensor that appears.
2. Wait for the 10-second sample to finish; confirm the listener is cleaned up and the app
   remains responsive.
3. Record event counts and axis min/max/mean.
4. Compare this list with Home Assistant's Android companion sensor inventory, but keep the
   distinction clear: direct AAOS SensorManager sensors are not the same as a phone running
   Android Auto, and are not the LYRIQ's VHAL sensor list.

## 6. Storage

Run this separately for the front/console USB and the rear cargo-area SD-card reader.

1. Insert the medium and confirm Android has mounted it.
2. Open Storage and choose the deepest writable folder the system picker allows.
3. Run **Test storage**.
4. Record each step: tree resolve, create, write/flush, read, SHA-256, delete.
5. Confirm only the temporary `lyriq-probe-*.txt` was created and removed.
6. Note whether the picker refuses the volume root; choose a folder instead, as required by
   Android.
7. Repeat for the other volume. Compare `StorageManager` volumes, app-specific directories,
   and `UsbManager` device visibility.

Do not use broad storage permissions, and do not delete or overwrite existing media.

## 7. Media readiness

1. Open Media. Confirm which of MP3, FLAC, OGG/Vorbis, AAC, Opus, and PCM have platform
   decoders.
2. Note the native output rate, frames per buffer, direct PCM support, and output devices.
3. Run **Test silent AudioTrack** once. Confirm success; there should be no audible content.
4. Grant `POST_NOTIFICATIONS` in Permissions.
5. Run **Test mediaPlayback FGS**. Confirm the temporary foreground service starts, posts
   its notification, and stops.
6. Record whether AAOS blocks, hides, or truncates the service/notification.
7. Record the media-session class availability. This probe does not start an actual session.

Do not expect this to prove Media3/ExoPlayer playback; it tests the platform path.

## 8. Network and LMS

1. Inventory Bluetooth/Wi-Fi/network/GPS/USB/display/audio metadata.
2. Confirm no SSID, IP, MAC, USB serial, or location fix is present in the JSON (the
   user-entered LMS host is also omitted).
3. Enter the LMS host and the HTTP/Slimproto ports (default 9000/3483) in Network & LMS.
4. Run **Test LMS ports** while parked. Both TCP connections are short and send no data.
5. Record whether the HTTP API and Slimproto ports connect, their latency, and any
   local-network permission error.
6. If the car is on Android 17/target 37, test `ACCESS_LOCAL_NETWORK`; this build targets 36
   and reports the permission boundary only.
7. Do not enter credentials; this probe sends no application data.

## 9. Driving restriction check

With a second person and a safe, legal test area:

1. While parked, confirm active buttons are available (after any required parked
   confirmation).
2. Put the vehicle in a restricted driving state and confirm the app locks active controls
   and stops any running sampler/test.
3. Return to Park and confirm the UI updates.
4. If AAOS blocks the activity entirely while moving, record that behavior rather than
   bypassing it.

Do not operate the screen while driving.

## 10. Export the evidence

- Save the final report.
- Export the JSON to a known folder.
- Record the file name, size, SHA-256 if practical, and the exact app version.
- Keep screenshots of the camera list, permission matrix, storage test, media/FGS result,
  and LMS endpoint result; these are useful for analysis and a future Play listing.
- Do not include VIN, location, account, or personal identifiers in shared evidence.

## Decision after the report

- **Path A — camera frames confirmed:** a separate recorder project can be evaluated
  (Camera2 + MediaCodec, segmented MP4, SAF storage). Do not add recording to this probe.
- **Path B — camera protected, storage accessible:** investigate supported GM export or
  integration paths; do not assume protected recordings are readable.
- **Path C — camera and storage restricted:** document exact restrictions and consider
  manufacturer-authorized APIs or external hardware.
