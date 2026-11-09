#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
ADB="$PWD/tools/macos/adb"
mkdir -p logs
LOG="$PWD/logs/install-$(date +%Y%m%d-%H%M%S).log"
trap 'echo "FAILED: See $LOG"' ERR
chmod +x "$ADB"
"$ADB" start-server >>"$LOG" 2>&1
serial=''
while read -r candidate state rest; do
 [ "$state" = device ] || continue
 if "$ADB" -s "$candidate" shell 'test -f /usr/etc/software_versions.xml' >>"$LOG" 2>&1; then
  [ -z "$serial" ] || { echo 'ERROR: Connect only one T-box'; exit 1; }
  serial="$candidate"
 fi
done < <("$ADB" devices)
[ -n "$serial" ] || { echo 'ERROR: No authorized T-box connected'; exit 1; }
if [ "${1:-install}" = uninstall ]; then
 "$ADB" -s "$serial" shell /bin/sh /usr/local/lib/TBox-antenna/uninstall.sh
 exit $?
fi
"$ADB" -s "$serial" shell mkdir -p /tmp/TBox-antenna-stage >>"$LOG" 2>&1
"$ADB" -s "$serial" push payload/. /tmp/TBox-antenna-stage/ >>"$LOG" 2>&1
"$ADB" -s "$serial" shell /bin/sh /tmp/TBox-antenna-stage/install.sh 2>&1 | tee -a "$LOG"
for ((i=0;i<200;i++)); do
 status=$("$ADB" -s "$serial" shell /bin/sh /usr/local/lib/TBox-antenna/status.sh 2>>"$LOG" | tr -d '\r') || status=RUNNING
 case "$status" in
  SUCCESS) "$ADB" -s "$serial" shell cat /tmp/voyah-tbox-kit.log | tee -a "$LOG"; echo 'SUCCESS: Installation and T-box Internet checks completed.'; exit 0;;
  FAILED) "$ADB" -s "$serial" shell cat /tmp/voyah-tbox-kit.log | tee -a "$LOG"; exit 1;;
 esac
 sleep 5
done
echo 'ERROR: Device verification timed out. Reconnect USB and rerun to inspect installation.'
exit 1
