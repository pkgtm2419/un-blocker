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

certificate_output="$("$apksigner_command" verify --print-certs "$apk_path")"
actual_sha256="$(printf '%s\n' "$certificate_output" \
  | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
  | head -n 1 \
  | tr '[:upper:]' '[:lower:]' \
  | tr -d ':[:space:]')"

if [[ -z "$actual_sha256" ]]; then
  echo "Unable to read the APK signing certificate SHA-256 digest." >&2
  exit 1
fi

if [[ "$actual_sha256" != "$expected_sha256" ]]; then
  echo "APK signer mismatch." >&2
  echo "Expected: $expected_sha256" >&2
  echo "Actual:   $actual_sha256" >&2
  exit 1
fi

echo "APK signer verified: $actual_sha256"
