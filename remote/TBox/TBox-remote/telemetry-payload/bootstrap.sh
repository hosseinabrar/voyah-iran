#!/bin/sh
set -eu
HOME_BASE=/usr/local/lib/vi-remote
[ -f "$HOME_BASE/enabled" ] || exit 0
if [ -f "$HOME_BASE/storage" ]; then
 storage=$(cat "$HOME_BASE/storage")
 [ "$storage" = /mnt/hirain_data/vi-remote ] || exit 1
 mkdir -p /data/vi-remote
 if ! mountpoint -q /data/vi-remote; then
  mount --bind "$storage" /data/vi-remote
  mount -o remount,bind,rw,exec /data/vi-remote
 fi
fi
for unit in /data/vi-remote/units/*.service; do cp "$unit" /run/systemd/system/; done
systemctl daemon-reload
systemctl start --no-block vi-remote-agent.service vi-remote-collector.service
