#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="${here}/../check-no-binaries.sh"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
failures=0

new_repo() { rm -rf "${work}/r"; mkdir "${work}/r"; git -C "${work}/r" init -q; echo ok > "${work}/r/README.md"; git -C "${work}/r" add README.md; }
check() { # name expected(0|1) path
  local name="$1" expected="$2" path="$3" code=0
  new_repo
  if [ "${path}" != "-" ]; then
    mkdir -p "${work}/r/$(dirname "${path}")"; echo x > "${work}/r/${path}"; git -C "${work}/r" add -f "${path}"
  fi
  (cd "${work}/r" && bash "${script}" >/dev/null 2>&1) || code=$?
  if [ "${code}" -eq "${expected}" ]; then echo "ok:   ${name}"; else echo "FAIL: ${name} (exit ${code}, expected ${expected})"; failures=$((failures + 1)); fi
}

check "clean repo passes" 0 -
check "source files pass" 0 app/src/main/Foo.kt
check "gradle wrapper jar is allowed" 0 gradle/wrapper/gradle-wrapper.jar
check "apk is rejected" 1 release/app.apk
check "keystore is rejected" 1 keys/test.keystore
check "module build output is rejected" 1 vpn-engine/build/classes/Foo.txt
check ".class file is rejected" 1 src/Foo.class
check "local.properties is rejected" 1 local.properties
check "__pycache__ is rejected" 1 tools/__pycache__/x.txt
exit "${failures}"
