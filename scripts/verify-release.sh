#!/bin/bash
set -euo pipefail

if [ -z "${1:-}" ]; then
  echo "Usage: $0 <release-tag>"
  exit 1
fi

TAG="$1"
echo "Verifying release $TAG exists..."

if ! gh release view "$TAG" > /dev/null 2>&1; then
  echo "Error: Release $TAG not found."
  exit 1
fi

echo "Release $TAG verified successfully."
