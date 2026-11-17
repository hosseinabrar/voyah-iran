#!/bin/sh
BASE=/usr/local/lib/TBox-remote
rm -f "$BASE/enabled"
systemctl stop TBox-remote-oem.service TBox-remote.service 2>/dev/null || true
if [ "$(sha256sum /mnt/hirain_app/usr/bin/hrild | cut -d ' ' -f1)" = 272602c471fb3559aae44950a732546849da124183f91dd6014edf1a08e92276 ]; then
 umount -l /mnt/hirain_app/usr/bin/hrild
 pid=$(pidof hrild || true); [ -z "$pid" ] || kill -TERM $pid
fi
echo 'DONE: Remote disabled; pairing/replay state preserved. Antenna patch unchanged.'
