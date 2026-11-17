#!/bin/sh
set -eu
BASE=/usr/local/lib/TBox-antenna
rm -f "$BASE/enabled"
systemctl stop TBox-antenna.service || true
/bin/sh "$BASE/rollback.sh"
echo 'DONE: Patch disabled. Reboot the T-box to restore all original runtime files.'
