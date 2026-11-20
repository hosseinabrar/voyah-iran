#!/bin/sh
set -eu
# Deliberately retain pairings, inbox and blocked senders for reinstall.
rm -f /data/vi-remote/sms-enabled
stop_status=0
systemctl disable --now vi-remote-agent.service vi-remote-collector.service vi-remote-sms.service || stop_status=$?
/usr/bin/python /data/vi-remote/bin/sms-runtime.py --restore
if [ "$stop_status" != 0 ]; then
 echo 'ERROR: SMS overlay restored, but service shutdown needs inspection'
 exit "$stop_status"
fi
echo 'DONE: Remote disabled; SMS overlay removed; user data retained'
