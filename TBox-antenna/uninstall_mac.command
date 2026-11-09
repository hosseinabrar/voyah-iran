#!/bin/bash
cd "$(dirname "$0")" || exit 1
/bin/bash ./host_mac.sh uninstall
result=$?
printf '\nPress Enter to close.'; read -r _
exit "$result"
