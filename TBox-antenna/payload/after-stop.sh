#!/bin/sh
# Failed setup (including timeout) disables persistence and releases runtime patches.
[ "${SERVICE_RESULT:-success}" = success ] && exit 0
BASE=/usr/local/lib/TBox-antenna
rm -f "$BASE/enabled" "$BASE/ready"
/bin/sh "$BASE/rollback.sh" >>/tmp/voyah-tbox-kit.log 2>&1
echo 'ERROR: Setup failed; persistence disabled and rollback attempted' >>/tmp/voyah-tbox-kit.log
