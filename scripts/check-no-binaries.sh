#!/usr/bin/env bash
set -euo pipefail

matches=$(git ls-files | grep -E '\.(apk|aab|jks|keystore|p12)$' || true)
if [ -n "$matches" ]; then
    echo "ERROR: Tracked binary / secret files found in git repository:" >&2
    echo "$matches" >&2
    exit 1
fi
echo "Binary check passed: no tracked apk/aab/jks/keystore/p12 files."
