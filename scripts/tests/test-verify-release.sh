#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="${here}/../verify-release.sh"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
stubs="${work}/stubs"; mkdir -p "${stubs}"
failures=0

cat > "${stubs}/aapt2" <<'STUB'
#!/usr/bin/env bash
echo "package: name='com.unblocker.app' versionCode='10' versionName='${STUB_VERSION:-2.0.2}'"
if [ "${STUB_DEBUGGABLE:-0}" = 1 ]; then echo "application-debuggable"; fi
STUB
cat > "${stubs}/apksigner" <<'STUB'
#!/usr/bin/env bash
echo "Verifies"
echo "Signer #1 certificate SHA-256 digest: ${STUB_SIGNER:-AA:BB:CC}"
STUB
chmod +x "${stubs}/aapt2" "${stubs}/apksigner"

make_assets() { # name version tsv(0|1) mapping(0|1)
  local d="${work}/$1"; mkdir -p "${d}"
  python3 - "${d}" "$2" "$3" <<'PY'
import sys, zipfile
d, version, tsv = sys.argv[1:4]
with zipfile.ZipFile(f"{d}/ub-blocker-{version}.apk", "w") as z:
    z.writestr("AndroidManifest.xml", b"m")
    z.writestr("assets/dns-rules.bin", b"x")
    if tsv == "1":
        z.writestr("assets/dns-rules.tsv", b"t")
PY
  (cd "${d}" && sha256sum "ub-blocker-$2.apk" > SHA256SUMS)
  if [ "$4" = 1 ]; then echo mapping > "${d}/mapping.txt"; fi
}

run_verify() { # tag dir [VAR=value ...]
  local tag="$1" dir="$2"; shift 2
  env AAPT2="${stubs}/aapt2" APKSIGNER="${stubs}/apksigner" "$@" bash "${script}" "${tag}" --dir "${work}/${dir}"
}
expect() { # name expected-exit tag dir [VAR=value ...]
  local name="$1" expected="$2" code=0
  shift 2
  run_verify "$@" >/dev/null 2>&1 || code=$?
  if [ "${code}" -eq "${expected}" ]; then echo "ok:   ${name}"; else echo "FAIL: ${name} (exit ${code}, expected ${expected})"; failures=$((failures + 1)); fi
}

make_assets good 2.0.2 0 1
make_assets tsv 2.0.2 1 1
make_assets nomap 2.0.2 0 0
make_assets tamper 2.0.2 0 1
echo "tampered" >> "${work}/tamper/ub-blocker-2.0.2.apk"

expect "test tag passes" 0 test-v2.0.2 good
expect "test tag with pinned signer passes" 0 test-v2.0.2 good UB_TEST_SIGNER_SHA256=aabbcc
expect "test tag with wrong pinned signer fails" 1 test-v2.0.2 good UB_TEST_SIGNER_SHA256=ffffff
expect "production tag passes" 0 v2.0.2 good UB_RELEASE_SIGNER_SHA256=aabbcc
expect "production without signer pin fails" 1 v2.0.2 good
expect "production with wrong signer fails" 1 v2.0.2 good UB_RELEASE_SIGNER_SHA256=ffffff
expect "production debuggable APK fails" 1 v2.0.2 good UB_RELEASE_SIGNER_SHA256=aabbcc STUB_DEBUGGABLE=1
expect "production without mapping.txt fails" 1 v2.0.2 nomap UB_RELEASE_SIGNER_SHA256=aabbcc
expect "tampered APK (checksum) fails" 1 test-v2.0.2 tamper
expect "versionName different from tag fails" 1 test-v2.0.2 good STUB_VERSION=9.9.9
expect "forbidden dns-rules.tsv fails" 1 test-v2.0.2 tsv
expect "missing required asset fails" 1 test-v2.0.2 good REQUIRED_ASSETS=assets/missing.bin
expect "invalid tag is a usage error" 2 v2.0 good
exit "${failures}"
