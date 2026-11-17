#!/bin/sh
set -eu
BASE=/data/vi-remote
rm -f "$BASE/native-ready"
"$BASE/bin/vns-battery" --probe > "$BASE/native-probe.log" 2>&1
# Check the exact structured result, not just the helper exit code.
grep -q 'VI_RESULT .*"status": "ready"' "$BASE/native-probe.log"
touch "$BASE/native-ready"
chmod 600 "$BASE/native-ready"
