#!/system/bin/sh
set -eu
# Fixed, app-owned bridge destinations only.
# No changes to OEM boot scripts or load.bin.
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

# Apply only permissions used by Voyah Iran, for the head unit's Android user 0.
# Both Windows and PowerShell installers execute this same script as root.
echo 'VOYAH_PERMISSIONS: granting microphone, overlay and background execution'
pm grant --user 0 "$package" android.permission.RECORD_AUDIO
microphone=$(dumpsys package "$package" | awk '
    /^[[:space:]]*User [0-9]+:/ { user0=($0 ~ /^[[:space:]]*User 0:/) }
    user0 && /android[.]permission[.]RECORD_AUDIO: granted=true/ { print "granted"; exit }
')
[ "$microphone" = granted ] || { echo 'ERROR: microphone permission was not granted for user 0'; exit 24; }
echo 'VOYAH_PERMISSION_OK: RECORD_AUDIO user=0'
for op in RECORD_AUDIO SYSTEM_ALERT_WINDOW RUN_IN_BACKGROUND RUN_ANY_IN_BACKGROUND; do
    cmd appops set --user 0 "$package" "$op" allow
    result=$(cmd appops get --user 0 "$package" "$op")
    echo "$result"
    echo "$result" | grep -Eq "$op: *allow([;[:space:]]|$)" ||
        { echo "ERROR: app-op verification failed: $op"; exit 25; }
    echo "VOYAH_PERMISSION_OK: $op user=0"
done
dumpsys deviceidle whitelist "+$package"
idle=$(dumpsys deviceidle whitelist "=$package" | tr -d '\r\n ')
[ "$idle" = true ] || { echo 'ERROR: background battery exemption was not confirmed'; exit 26; }
echo 'VOYAH_PERMISSION_OK: battery exemption'
# Preserve all other enabled accessibility services; this host only draws Raha.
overlay_service="$package/com.abrar.raha.feedback.RahaOverlayService"
accessibility_services=$(settings --user 0 get secure enabled_accessibility_services)
case "$accessibility_services" in null) accessibility_services='' ;; esac
# Rebind only our own host after APK replacement; preserve every other service.
other_services=$(printf '%s' "$accessibility_services" | tr ':' '\n' | awk -v own="$overlay_service" 'length && $0 != own { if(n++) printf ":"; printf "%s", $0 }')
settings --user 0 put secure enabled_accessibility_services "$other_services"
sleep 1
settings --user 0 put secure enabled_accessibility_services "${other_services:+$other_services:}$overlay_service"
settings --user 0 put secure accessibility_enabled 1
echo 'VOYAH_PERMISSION_OK: Raha overlay host enabled'

# Observe only the central OEM app-list button when launcher attachment is unavailable.
drawer_service="$package/com.abrar.voyahiran.feature.launcher.LauncherDrawerService"
current_services=$(settings --user 0 get secure enabled_accessibility_services)
case "$current_services" in null) current_services='' ;; esac
case ":$current_services:" in
    *":$drawer_service:"*) ;;
    *) settings --user 0 put secure enabled_accessibility_services "${current_services:+$current_services:}$drawer_service" ;;
esac
settings --user 0 put secure accessibility_enabled 1
drawer_actual=$(settings --user 0 get secure enabled_accessibility_services)
case ":$drawer_actual:" in
    *":$drawer_service:"*) ;;
    *) echo 'ERROR: launcher drawer service was not enabled'; exit 27 ;;
esac
echo 'VOYAH_PERMISSION_OK: launcher drawer fallback host enabled'
echo 'VOYAH_PERMISSIONS_VERIFIED' 

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
# H97A service_contexts_file is read-only even for su. Never replace it here.
# Passenger transfer for modern apps remains gated until a supported fix exists.
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
if [ "$(getprop ro.build.version.sdk)" = 28 ] &&
    { getprop ro.build.fingerprint; getprop ro.vendor.build.fingerprint; } | grep -Eiq '(^|[^a-z0-9])h97a([^a-z0-9]|$)'; then
    # H97A permits shell_exec as the shell domain entrypoint, but not zygote_exec.
    # Enter through sh, then exec ART in that same domain. Unlike su, shell has
    # the OEM NVRAM library's required read/write-open permission for vdh_device.
    sed 's|^service voyahiran_bridge .*|service voyahiran_bridge /system/bin/sh -c "exec /system/bin/app_process /system/bin com.abrar.voyahiran.core.bridge.BridgeServer"|' "$stage.rc" > "$rc.new"
    echo 'VOYAH_BRIDGE_PROFILE: H97A shell entrypoint; SELinux enforcing unchanged'
else
    cp "$stage.rc" "$rc.new"
fi
chown 0:0 "$rc.new"
chmod 644 "$rc.new"
mv "$rc.new" "$rc"
restorecon "$rc"
sync
echo 'VOYAH_BRIDGE_INSTALLED: reboot once, then use installer -Action Status'
# Return the partition to read-only when supported; failure does not hide install status.
mount -o remount,ro "$mountpoint" || echo 'Partition remains writable until reboot'

