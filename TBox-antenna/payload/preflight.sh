#!/bin/sh
set -eu
[ "$(id -u)" = 0 ] || { echo 'ERROR: Root ADB required'; exit 1; }
grep -Eq 'name="TBOX_Software_Version"[[:space:]]+value="2\.0\.7"' /usr/etc/software_versions.xml || { echo 'ERROR: T-box version must be 2.0.7'; exit 1; }
for tool in busctl systemctl mount umount sha256sum ip iptables curl nslookup pidof; do
 command -v "$tool" >/dev/null || { echo "ERROR: Missing device tool: $tool"; exit 1; }
done
# Existing research installations need explicit migration; never stack two boot helpers.
[ ! -f /usr/local/lib/voyah-tbox/enabled ] || { echo 'ERROR: Previous research patch is enabled; migrate it before installing this kit'; exit 1; }
systemctl cat serial-getty@ttyS0.service | grep -Eq -- '(--autologin[ =]+root|-a[ ]+root)'  || { echo 'ERROR: Expected factory boot hook unavailable'; exit 1; }
echo 'DONE: T-box 2.0.7 detected'
