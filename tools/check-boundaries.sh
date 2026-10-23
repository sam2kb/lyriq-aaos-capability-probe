#!/usr/bin/env bash
# Static guard for the probe's safety boundaries. This is not a substitute for
# reviewing the code or testing on the vehicle.
set -euo pipefail
cd "$(dirname "$0")/.."

fail=0

manifest="app/src/main/AndroidManifest.xml"
forbidden_manifest='android\.permission\.(WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|SYSTEM_CAMERA|RECORD_AUDIO)|android\.car\.permission\.(CONTROL_|CAR_PRIVILEGED)'
if rg -n "$forbidden_manifest" "$manifest"; then
  echo "FAIL: forbidden permission present in manifest"
  fail=1
fi

source_files=(app/src/main/kotlin probe-core/src/main/kotlin)
forbidden_calls='\.(setProperty|setFloatProperty|setIntProperty|setBooleanProperty)\(|MediaRecorder\b|\.startRecording\(|\.setRepeatingRequest\(|\.addRoute\(|\.addDnsServer\(|\.addAllowedApplication\(|\.setUnderlyingNetworks\('
if rg -n --glob '*.kt' "$forbidden_calls" "${source_files[@]}"; then
  echo "FAIL: vehicle write, recording, repeating capture, or VPN route API call present"
  fail=1
fi

privacy_calls='Build\.getSerial|ANDROID_ID|getSerialNumber\(|getLastKnownLocation|requestLocationUpdates|getConnectionInfo\(|\.getSSID\(|\.getBSSID\(|getHardwareAddress\(|getMacAddress\('
if rg -n --glob '*.kt' "$privacy_calls" "${source_files[@]}"; then
  echo "FAIL: privacy-sensitive API call present"
  fail=1
fi

hidden_calls='ServiceManager|java\.lang\.reflect|getDeclaredMethod|getDeclaredField'
if rg -n --glob '*.kt' "$hidden_calls" "${source_files[@]}"; then
  echo "FAIL: hidden/non-SDK API use present"
  fail=1
fi

# VIN must be reported as metadata only, never added to the read allowlist.
if rg -n "allowedReadProperties" -A40 probe-core/src/main/kotlin/com/sam2kb/lyriqprobe/core/probe/VehicleProbe.kt | rg -n "INFO_VIN"; then
  echo "FAIL: INFO_VIN included in value-read allowlist"
  fail=1
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "PASS: no forbidden manifest permission, vehicle write, recording, VPN route, identifier, or hidden-API pattern found"
