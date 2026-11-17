#!/bin/sh
set -eu
BASE=/usr/local/lib/TBox-remote
STAGE=/tmp/TBox-remote-stage
cd "$STAGE"
[ "$(id -u)" = 0 ] || { echo 'ERROR: Root required'; exit 1; }
grep -Eq 'name="TBOX_Software_Version"[[:space:]]+value="2\.0\.7"' /mnt/hirain_app/etc/software_versions.xml || { echo 'ERROR: Expected T-box 2.0.7'; exit 1; }
sha256sum -c SHA256SUMS >/dev/null
for cmd in busctl systemctl mount sha256sum pidof; do command -v "$cmd" >/dev/null; done
expected=$(cat remotecontrol.expected)
actual=absent
if [ -f /mnt/hirain_app/usr/bin/remotecontrol ] || pidof remotecontrol >/dev/null; then
 echo 'ERROR: Existing factory remote engine detected; refusing to run a duplicate'; exit 1
fi
case "$(sha256sum /mnt/hirain_app/usr/bin/hrild | cut -d ' ' -f1)" in
25b8787e5bfe471abb4329c4128e942dab89544ffab4b7aca3e8901b105ed721|0396fb7a3d1cf19866e4d02bb6043590dd433873fc38d3650b8def21e647654a|272602c471fb3559aae44950a732546849da124183f91dd6014edf1a08e92276) :;;
*) echo 'ERROR: Unsupported hrild; no changes made';exit 1;;esac
mkdir -p "$BASE/oem" "$BASE/state"
chmod 700 "$BASE" "$BASE/state"
# Reinstallation keeps pairing and UUID journals; never truncate replay records.
systemctl stop TBox-remote-oem.service TBox-remote.service 2>/dev/null || true
# Release only our previous SMS overlay before replacing files.
if [ "$(sha256sum /mnt/hirain_app/usr/bin/hrild | cut -d ' ' -f1)" = 272602c471fb3559aae44950a732546849da124183f91dd6014edf1a08e92276 ]; then umount -l /mnt/hirain_app/usr/bin/hrild; fi
for file in ./*; do name=${file##*/}; if [ -f "$BASE/$name" ] && cmp -s "$file" "$BASE/$name"; then continue; fi; cp "$file" "$BASE/$name.new"; mv "$BASE/$name.new" "$BASE/$name"; done
chmod 755 "$BASE/tbox-remote" "$BASE/hrild-remote" "$BASE/remotecontrol-wrapper" "$BASE/remotecontrol-bundled"
[ -f "$BASE/profile.before" ] || { [ ! -f /home/root/.profile ] || cp /home/root/.profile "$BASE/profile.before"; }
touch /home/root/.profile
if ! grep -q '# TBox-remote boot hook' /home/root/.profile; then
 cat >> /home/root/.profile <<'HOOK'

# TBox-remote boot hook
case "$(tty 2>/dev/null)" in
 /dev/ttyS0|/dev/ttyGS1) /bin/sh /usr/local/lib/TBox-remote/bootstrap.sh >/tmp/TBox-remote-bootstrap.log 2>&1 ;;
esac
HOOK
fi
[ "$actual" != absent ] || touch "$BASE/bundled-mode"

touch "$BASE/enabled"
/bin/sh "$BASE/bootstrap.sh"
echo 'DONE: Remote service installed; checking startup'
