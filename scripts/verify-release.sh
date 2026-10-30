#!/usr/bin/env bash
# Verifies a published Un-Blocker release (or an already downloaded asset directory).
# Usage: verify-release.sh <tag> [--dir <assets-dir>]
# Needs: sha256sum, unzip, aapt2, apksigner (and gh + GITHUB_REPOSITORY unless --dir is used).
# Env:   UB_RELEASE_SIGNER_SHA256 (required for v* tags), UB_TEST_SIGNER_SHA256 (optional for test-v* tags),
#        REQUIRED_ASSETS / FORBIDDEN_ASSETS (space separated APK entries; defaults below).
set -euo pipefail

usage() { echo "Usage: $0 <tag> [--dir <assets-dir>]" >&2; exit 2; }
fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "ok:   $*"; }

[ "$#" -ge 1 ] || usage
TAG="$1"; shift
DIR=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --dir) [ "$#" -ge 2 ] || usage; DIR="$2"; shift 2 ;;
    *) usage ;;
  esac
done

if ! [[ "${TAG}" =~ ^(test-)?v([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
  echo "Invalid tag '${TAG}'" >&2; exit 2
fi
VERSION="${BASH_REMATCH[2]}"
IS_TEST=false
[[ "${TAG}" == test-v* ]] && IS_TEST=true

REQUIRED_ASSETS="${REQUIRED_ASSETS:-assets/dns-rules.bin}"
FORBIDDEN_ASSETS="${FORBIDDEN_ASSETS:-assets/dns-rules.tsv}"

find_tool() { # name, env override
  local name="$1" override="${2:-}"
  if [ -n "${override}" ]; then printf '%s' "${override}"; return; fi
  local root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -n "${root}" ]; then
    if [ -f "${root}/build-tools/35.0.0/${name}" ]; then
      printf '%s' "${root}/build-tools/35.0.0/${name}"
      return
    fi
    find "${root}/build-tools" -name "${name}" -type f 2>/dev/null | sort -V | tail -n 1
  fi
}
AAPT2_BIN="$(find_tool aapt2 "${AAPT2:-}")"
APKSIGNER_BIN="$(find_tool apksigner "${APKSIGNER:-}")"
[ -n "${AAPT2_BIN}" ] || fail "aapt2 not found (set AAPT2 or ANDROID_HOME)"
[ -n "${APKSIGNER_BIN}" ] || fail "apksigner not found (set APKSIGNER or ANDROID_HOME)"

cleanup_dir=""
if [ -z "${DIR}" ]; then
  command -v gh >/dev/null 2>&1 || fail "gh CLI is required when --dir is not used"
  : "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required when --dir is not used}"
  DIR="$(mktemp -d)"; cleanup_dir="${DIR}"
  trap 'rm -rf "${cleanup_dir}"' EXIT
  gh release view "${TAG}" --repo "${GITHUB_REPOSITORY}" >/dev/null 2>&1 || fail "release ${TAG} not found"
  gh release download "${TAG}" --repo "${GITHUB_REPOSITORY}" --dir "${DIR}" || fail "could not download assets of ${TAG}"
  is_pre="$(gh release view "${TAG}" --repo "${GITHUB_REPOSITORY}" --json isPrerelease --jq .isPrerelease)"
  if [ "${IS_TEST}" = true ]; then
    [ "${is_pre}" = "true" ] || fail "test tag ${TAG} must be a prerelease"
  else
    [ "${is_pre}" = "false" ] || fail "production tag ${TAG} must not be a prerelease"
  fi
  ok "prerelease flag matches tag type"
fi

APK="${DIR}/ub-blocker-${VERSION}.apk"
[ -f "${APK}" ] || fail "missing asset ub-blocker-${VERSION}.apk"
[ -f "${DIR}/SHA256SUMS" ] || fail "missing asset SHA256SUMS"
( cd "${DIR}" && sha256sum --check --strict SHA256SUMS ) >/dev/null || fail "SHA256SUMS does not match the APK"
ok "checksum matches"

badging="$("${AAPT2_BIN}" dump badging "${APK}")" || fail "aapt2 could not read the APK"
grep -q "name='com.unblocker.app'" <<<"${badging}" || fail "unexpected package name"
grep -q "versionName='${VERSION}'" <<<"${badging}" || fail "versionName does not equal tag version ${VERSION}"
ok "package and versionName match the tag"

entries="$(unzip -Z1 "${APK}")"
for entry in ${REQUIRED_ASSETS}; do
  grep -qx "${entry}" <<<"${entries}" || fail "APK is missing required entry ${entry}"
done
for entry in ${FORBIDDEN_ASSETS}; do
  if grep -qx "${entry}" <<<"${entries}"; then fail "APK must not contain ${entry}"; fi
done
ok "required assets present, forbidden assets absent"

debuggable=false
grep -q "application-debuggable" <<<"${badging}" && debuggable=true
if [ "${IS_TEST}" = false ]; then
  [ "${debuggable}" = false ] || fail "production APK is debuggable"
  [ -n "${UB_RELEASE_SIGNER_SHA256:-}" ] || fail "UB_RELEASE_SIGNER_SHA256 is required for production tags"
  [ -f "${DIR}/mapping.txt" ] || fail "production release must attach mapping.txt"
  APKSIGNER="${APKSIGNER_BIN}" bash "$(dirname "${BASH_SOURCE[0]}")/verify-apk-signature.sh" "${APK}" "${UB_RELEASE_SIGNER_SHA256}" >/dev/null \
    || fail "production signer does not match the pinned digest"
  ok "not debuggable, signer pinned, mapping.txt attached"
else
  [ "${debuggable}" = true ] && echo "note: test build is debuggable (expected for test-v* builds)"
  if [ -n "${UB_TEST_SIGNER_SHA256:-}" ]; then
    APKSIGNER="${APKSIGNER_BIN}" bash "$(dirname "${BASH_SOURCE[0]}")/verify-apk-signature.sh" "${APK}" "${UB_TEST_SIGNER_SHA256}" >/dev/null \
      || fail "test signer does not match the pinned digest"
    ok "test signer matches the pinned digest"
  fi
fi

echo "VERIFIED ${TAG}"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  printf '### Release verification\n%s verified: checksum, version, assets, signer.\n' "${TAG}" >> "${GITHUB_STEP_SUMMARY}"
fi
