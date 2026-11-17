#!/bin/sh
BASE=/usr/local/lib/TBox-antenna
if [ -f "$BASE/ready" ] && [ -f "$BASE/enabled" ]; then echo SUCCESS; exit 0; fi
state=$(systemctl show TBox-antenna.service -p ActiveState --value)
case "$state" in activating|deactivating) echo RUNNING;; *) echo FAILED;; esac
