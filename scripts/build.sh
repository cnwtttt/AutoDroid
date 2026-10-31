#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "${1:-}" = '--native' ]; then
    shift
    exec ./scripts/build-native.sh "$@"
fi
exec ./scripts/build-compose.sh "$@"
