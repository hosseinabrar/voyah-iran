#!/bin/bash
cd "$(dirname "$0")" || exit 1
/bin/bash ./host_mac.sh install
result=$?
printf '\nPress Enter to close.'; read -r _
exit "$result"
