#!/usr/bin/env bash
# Fails if the repository tracks binaries, signing material, build outputs or local files.
set -euo pipefail

bad_ext='\.(apk|aab|jks|keystore|p12|pfx|class|dex|hprof|so|pyc)$'
bad_dir='(^|/)(build|\.gradle|\.kotlin|\.idea|__pycache__|captures)/'
bad_name='(^|/)(local\.properties|\.env)$'

matches="$(git ls-files | grep -E "${bad_ext}|${bad_dir}|${bad_name}" || true)"
if [ -n "${matches}" ]; then
  count="$(printf '%s\n' "${matches}" | wc -l | tr -d ' ')"
  echo "ERROR: ${count} tracked file(s) must not be in git (binaries, secrets, build outputs, local files):" >&2
  printf '%s\n' "${matches}" | head -n 25 >&2
  [ "${count}" -gt 25 ] && echo "... and $((count - 25)) more" >&2
  echo "Fix: git rm -r --cached <path>, then add it to .gitignore." >&2
  exit 1
fi
echo "Repository hygiene check passed (no tracked binaries, build outputs or secrets)."
