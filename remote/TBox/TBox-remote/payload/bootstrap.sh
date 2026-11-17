#!/bin/sh
set -eu
BASE=/usr/local/lib/TBox-remote
[ -f "$BASE/enabled" ] || exit 0
cp "$BASE/TBox-remote.service" /run/systemd/system/TBox-remote.service
cp "$BASE/TBox-remote-oem.service" /run/systemd/system/TBox-remote-oem.service
systemctl daemon-reload
systemctl start --no-block TBox-remote.service
