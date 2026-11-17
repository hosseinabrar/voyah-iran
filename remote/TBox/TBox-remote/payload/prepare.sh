#!/bin/sh
set -eu
BASE=/usr/local/lib/TBox-remote
exec >>/tmp/TBox-remote-prepare.log 2>&1
cd "$BASE"
[ -f "$BASE/enabled" ] || exit 1
grep -Eq 'name="TBOX_Software_Version"[[:space:]]+value="2\.0\.7"' /mnt/hirain_app/etc/software_versions.xml
sha256sum -c SHA256SUMS >/dev/null
h=$(sha256sum /mnt/hirain_app/usr/bin/hrild | cut -d ' ' -f1)
case "$h" in
 25b8787e5bfe471abb4329c4128e942dab89544ffab4b7aca3e8901b105ed721|0396fb7a3d1cf19866e4d02bb6043590dd433873fc38d3650b8def21e647654a|272602c471fb3559aae44950a732546849da124183f91dd6014edf1a08e92276) :;;
 *) echo 'ERROR: Unexpected hrild firmware'; exit 1;;
esac
# Validate both OEM binaries before changing either overlay. Undo our mounts on failure.
rollback() {
 rm -f "$BASE/enabled"
 systemctl stop TBox-remote-oem.service || true
 if [ "$(sha256sum /mnt/hirain_app/usr/bin/hrild | cut -d ' ' -f1)" = 272602c471fb3559aae44950a732546849da124183f91dd6014edf1a08e92276 ]; then
  umount -l /mnt/hirain_app/usr/bin/hrild || true
  pid=$(pidof hrild || true); [ -z "$pid" ] || kill -TERM $pid || true
 fi
}
trap rollback EXIT
if [ "$h" != 272602c471fb3559aae44950a732546849da124183f91dd6014edf1a08e92276 ]; then
 mount --bind "$BASE/hrild-remote" /mnt/hirain_app/usr/bin/hrild
 pid=$(pidof hrild || true); [ -z "$pid" ] || kill -TERM $pid
fi
mkdir -p /run/TBox-remote-libs /run/vi-rc /run/rc "$BASE/conf"
tar -xzf "$BASE/oem-config.tar.gz" -C /run/vi-rc
ln -sf /run/vi-rc/control_atoms.xml /run/rc/atoms.xml
ln -sf /run/vi-rc/control_command.xml /run/rc/commands.xml
ln -sf /run/vi-rc/control_signal_mapping.xml /run/rc/signals.xml
tar -xzf "$BASE/oem-libs.tar.gz" -C /run/TBox-remote-libs
systemctl start TBox-remote-oem.service
for n in $(seq 1 30); do
 if [ -S /run/TBox-remote-oem.sock ] && busctl --address=unix:path=/tmp/.hirain-dbus --timeout=2 introspect gdbus.hcommon.rild /gdbus/hcommon/rild gdbus.hcommon.rild.sms >/dev/null 2>&1; then trap - EXIT; echo 'DONE: OEM bridge and SMS interface ready'; exit 0; fi
 sleep 1
done
echo 'ERROR: OEM bridge or SMS service did not become ready'; exit 1
