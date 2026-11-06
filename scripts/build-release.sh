#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -f scripts/env.sh ]; then . ./scripts/env.sh; fi
exec python3 scripts/build_release.py
