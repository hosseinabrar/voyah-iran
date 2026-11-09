#!/bin/sh
set -eu
BASE=/usr/local/lib/TBox-antenna
[ -f "$BASE/enabled" ] || exit 0
cp "$BASE/TBox-antenna.service" /run/systemd/system/TBox-antenna.service
systemctl daemon-reload
systemctl start --no-block TBox-antenna.service
