#!/usr/bin/env bash
# Runs every shell test in this directory.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
status=0
for test_file in "${here}"/test-*.sh; do
  echo "== ${test_file##*/}"
  bash "${test_file}" || status=1
done
exit "${status}"
