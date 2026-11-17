#!/bin/sh
set -eu
BASE=/usr/local/lib/TBox-experimental-heartbeat
[ -f "$BASE/enabled" ] || exit 0
cp "$BASE/TBox-experimental-heartbeat.service" /run/systemd/system/
systemctl daemon-reload
systemctl start --no-block TBox-experimental-heartbeat.service
