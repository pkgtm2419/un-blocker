#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <apk-path> <expected-sha256>" >&2
  exit 2
fi

apk_path="$1"
expected_sha256="$(printf '%s' "$2" | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')"
apksigner_command="${APKSIGNER:-apksigner}"

if [[ ! -f "$apk_path" ]]; then
  echo "APK not found: $apk_path" >&2
  exit 2
fi

certificate_output="$("$apksigner_command" verify --print-certs "$apk_path" 2>&1)" || {
  echo "apksigner command failed ($apksigner_command):" >&2
  echo "$certificate_output" >&2
  exit 1
}
actual_sha256="$(printf '%s\n' "$certificate_output" \
  | sed -n 's/^[[:space:]]*Signer #1 certificate SHA-256 digest:[[:space:]]*//p' \
  | head -n 1 \
  | tr '[:upper:]' '[:lower:]' \
  | tr -d ':[:space:]')"

if [[ -z "$actual_sha256" ]]; then
  actual_sha256="$(printf '%s\n' "$certificate_output" \
    | grep -i "SHA-256 digest" \
    | head -n 1 \
    | sed -n -E 's/.*SHA-256 digest:[[:space:]]*([0-9a-fA-F:]+).*/\1/p' \
    | tr '[:upper:]' '[:lower:]' \
    | tr -d ':[:space:]')"
fi

if [[ -z "$actual_sha256" ]]; then
  echo "Unable to read the APK signing certificate SHA-256 digest." >&2
  echo "apksigner command: ${apksigner_command}" >&2
  echo "Raw output was:" >&2
  printf '%s\n' "${certificate_output}" >&2
  exit 1
fi

if [[ "$actual_sha256" != "$expected_sha256" ]]; then
  echo "APK signer mismatch." >&2
  echo "Expected: $expected_sha256" >&2
  echo "Actual:   $actual_sha256" >&2
  exit 1
fi

echo "APK signer verified: $actual_sha256"
