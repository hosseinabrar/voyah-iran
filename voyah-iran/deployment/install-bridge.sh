#!/system/bin/sh
set -eu
# Fixed, app-owned destinations only. No changes to OEM boot scripts or load.bin.
stage="$1"
expected="$2"
case "$stage" in /data/local/tmp/voyahiran-stage-*) ;; *) exit 10 ;; esac
[ "$(id -u)" = 0 ] || exit 11
package=com.abrar.voyahiran
dest=/data/local/tmp/voyahiran-bridge
rc=/system/etc/init/voyahiran-bridge.rc
apk=$(pm path "$package" | sed -n 's/^package://p' | head -n 1)
[ -n "$apk" ] || { echo 'App is not installed'; exit 12; }
actual=$(sha256sum "$apk" | cut -d ' ' -f 1)
[ "$actual" = "$expected" ] || { echo 'Installed APK does not match selected APK'; exit 13; }
[ "$(wc -c < "$stage.token" | tr -d ' ')" = 64 ] || exit 16
grep -Eq '^[0-9a-f]{64}$' "$stage.token" || exit 17
grep -q 'service voyahiran_bridge ' "$stage.rc" || exit 18

# Check the complete ROA runtime before changing installation files.
[ "$(getprop ro.product.cpu.abi)" = arm64-v8a ] || { echo 'ROA runtime requires arm64-v8a'; exit 22; }
for item in inject hook; do
    expected_roa=$(cat "$stage.roa-$item.sha256")
    actual_roa=$(sha256sum "$stage.roa-$item" | cut -d ' ' -f 1)
    [ "$expected_roa" = "$actual_roa" ] || { echo "ROA payload mismatch: $item"; exit 23; }
done
# Stop here if the partition cannot be remounted. Never disable verity or SELinux.
mountpoint=/
if awk '$2 == "/system" {found=1} END {exit !found}' /proc/mounts; then mountpoint=/system; fi
mount -o remount,rw "$mountpoint" || { echo 'System is read-only. No boot files changed.'; exit 20; }
backup="/data/local/tmp/voyahiran-backup-$(date +%s)"
mkdir "$backup"
chmod 700 "$backup"
if [ -d "$dest" ]; then cp -pR "$dest" "$backup/bridge"; fi
if [ -f "$rc" ]; then cp -p "$rc" "$backup/service.rc"; fi
echo "Backup: $backup"
setprop ctl.stop voyahiran_bridge || true
mkdir -p "$dest"
chown 0:0 "$dest"
chmod 700 "$dest"
cp "$apk" "$dest/bridge.apk.new"
cp "$stage.token" "$dest/bridge.token.new"
chown 0:0 "$dest/bridge.apk.new" "$dest/bridge.token.new"
chmod 600 "$dest/bridge.apk.new" "$dest/bridge.token.new"
mv "$dest/bridge.apk.new" "$dest/bridge.apk"
mv "$dest/bridge.token.new" "$dest/bridge.token"
mkdir -p "$dest/roa"
chmod 700 "$dest/roa"
cp "$stage.roa-inject" "$dest/roa/frida-inject.new"
cp "$stage.roa-hook" "$dest/roa/hook_camera_front_roa_switch.js.new"
cp "$stage.roa-inject.sha256" "$dest/roa/frida-inject.sha256.new"
cp "$stage.roa-hook.sha256" "$dest/roa/hook_camera_front_roa_switch.js.sha256.new"
for item in frida-inject hook_camera_front_roa_switch.js frida-inject.sha256 hook_camera_front_roa_switch.js.sha256; do
    chown 0:0 "$dest/roa/$item.new"
    chmod 600 "$dest/roa/$item.new"
    mv "$dest/roa/$item.new" "$dest/roa/$item"
done
chmod 700 "$dest/roa/frida-inject"
restorecon -RF "$dest"
# The app writes its own private credential. No access to app_data_file from shell
# and no global SELinux change is required. Receiver requires INSTALL_PACKAGES.
provision=$(am broadcast --user 0 --include-stopped-packages -n "$package/.core.bridge.BridgeProvisionReceiver" --es token "$(cat "$stage.token")")
echo "$provision" | grep -q 'result=200' || { echo 'App credential provisioning failed'; exit 21; }
cp "$stage.rc" "$rc.new"
chown 0:0 "$rc.new"
chmod 644 "$rc.new"
mv "$rc.new" "$rc"
restorecon "$rc"
sync
echo 'VOYAH_BRIDGE_INSTALLED: reboot once, then use installer -Action Status'
# Return the partition to read-only when supported; failure does not hide install status.
mount -o remount,ro "$mountpoint" || echo 'Partition remains writable until reboot'

