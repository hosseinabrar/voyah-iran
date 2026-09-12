#!/system/bin/sh
set -eu
ACTION="$1"
PAYLOAD="$2"
ORIGINAL=0faed7ebf1094c62a4f92b4bc533bbb3c08480a2cd847567cb1cff91b2d1d045
PATCHED=ede647f69dfdfc4ad86af3a9da6b056a8bff47e6fe9e41d33ab3837b2d26f0ce
TARGET=/system/framework/services.jar
BACKUP=/data/local/tmp/voyahiran-framework-backup-v1
TEMP=/system/framework/services.jar.voyahiran-new
vi_file_sha256() {
  # Use a unique name: `hash` is a shell command/alias on some OEM shells.
  raw_digest=$(sha256sum "$1") || return 1
  read -r vi_digest vi_filename <<EOF
$raw_digest
EOF
  case "$vi_digest" in
    ''|*[!0-9a-f]*) echo "Invalid SHA256 output for $1: [$raw_digest]" >&2; return 1 ;;
  esac
  printf '%s\n' "$vi_digest"
}
fail() { echo "ERROR: $*" >&2; exit 1; }
[ "$(id -u)" = 0 ] || fail 'Root required.'
[ ! -L "$TARGET" ] && [ -f "$TARGET" ] || fail 'Unexpected services.jar path.'
CURRENT=$(vi_file_sha256 "$TARGET")
case "$ACTION" in
  status) echo "services.jar SHA256=$CURRENT"; exit 0 ;;
  install)
    payload_digest=$(vi_file_sha256 "$PAYLOAD") || fail "Cannot hash payload: $PAYLOAD"
    [ "$payload_digest" = "$PATCHED" ] || fail "Payload hash mismatch. expected=$PATCHED actual=[$payload_digest] path=$PAYLOAD"
    if [ "$CURRENT" = "$PATCHED" ]; then
      [ -f "$BACKUP/services.jar" ] && [ "$(vi_file_sha256 "$BACKUP/services.jar")" = "$ORIGINAL" ] || fail 'Original backup missing.'
      echo 'Already installed. Reboot if not yet rebooted.'; exit 0
    fi
    [ "$CURRENT" = "$ORIGINAL" ] || fail 'Firmware differs from the analyzed file; no changes made.'
    ;;
  restore)
    [ "$CURRENT" = "$PATCHED" ] || [ "$CURRENT" = "$ORIGINAL" ] || fail 'Unknown current firmware; refusing restore.'
    [ -f "$BACKUP/complete" ] && [ "$(vi_file_sha256 "$BACKUP/services.jar")" = "$ORIGINAL" ] || fail 'Verified complete backup required.'
    ;;
  *) fail 'Unknown action.' ;;
esac

# Back up before remounting or changing anything in /system. Never replace an existing backup.
if [ "$ACTION" = install ]; then
  if [ -e "$BACKUP" ]; then
    [ ! -L "$BACKUP" ] && [ -f "$BACKUP/complete" ] || fail 'Incomplete backup; inspect it before retrying.'
    [ "$(vi_file_sha256 "$BACKUP/services.jar")" = "$ORIGINAL" ] || fail 'Backup hash mismatch.'
  else
    mkdir -m 700 "$BACKUP"
    cp -p "$TARGET" "$BACKUP/services.jar"
    : > "$BACKUP/preopt.list"
    for arch in arm64 arm; do
      for ext in art odex vdex; do
        source="/system/framework/oat/$arch/services.$ext"
        if [ -f "$source" ]; then
          [ ! -L "$source" ] || fail 'Unexpected preopt symlink.'
          name="$arch-services.$ext"
          cp -p "$source" "$BACKUP/$name"
          [ "$(vi_file_sha256 "$source")" = "$(vi_file_sha256 "$BACKUP/$name")" ] || fail 'Preopt backup verification failed.'
          echo "$arch $ext $(vi_file_sha256 "$source")" >> "$BACKUP/preopt.list"
        fi
      done
    done
    [ "$(vi_file_sha256 "$BACKUP/services.jar")" = "$ORIGINAL" ] || fail 'Backup verification failed.'
    touch "$BACKUP/complete"
    sync
  fi
fi

# Validate the fixed-name backup entries before either install or recovery.
while read -r arch ext digest; do
  case "$arch" in arm|arm64) ;; *) fail 'Invalid backup architecture.' ;; esac
  case "$ext" in art|odex|vdex) ;; *) fail 'Invalid backup entry.' ;; esac
  [ "$(vi_file_sha256 "$BACKUP/$arch-services.$ext")" = "$digest" ] || fail 'Preopt backup damaged.'
done < "$BACKUP/preopt.list"

[ ! -e "$TEMP" ] && [ ! -L "$TEMP" ] || fail 'Temporary framework path already exists; inspect it before retrying.'
# Reserve room for an atomic replacement while retaining the live JAR.
available=$(df -k /system/framework | tail -n 1 | awk '{print $4}')
case "$available" in ''|*[!0-9]*) fail 'Cannot determine free space in system.' ;; esac
[ "$available" -ge 20000 ] || fail 'At least 20 MB free in system is required.'

MOUNTPOINT=/system
if ! mount -o remount,rw /system; then
  MOUNTPOINT=/
  mount -o remount,rw / || fail 'Cannot remount system. Verity/SELinux were not changed.'
fi
restore_files() {
  cp -p "$BACKUP/services.jar" "$TEMP" &&
  chown 0:0 "$TEMP" && chmod 644 "$TEMP" && restorecon "$TEMP" &&
  mv -f "$TEMP" "$TARGET" && restorecon "$TARGET" || return 1
  while read -r arch ext digest; do
    destination="/system/framework/oat/$arch/services.$ext"
    cp -p "$BACKUP/$arch-services.$ext" "$destination" && restorecon "$destination" || return 1
    [ "$(vi_file_sha256 "$destination")" = "$digest" ] || return 1
  done < "$BACKUP/preopt.list"
  [ "$(vi_file_sha256 "$TARGET")" = "$ORIGINAL" ] || return 1
  sync
}
on_exit() {
  code=$?
  trap - EXIT HUP INT TERM
  if [ "$code" != 0 ]; then
    if restore_files; then echo 'Original framework restored after failure.' >&2
    else echo 'RECOVERY FAILED: do not reboot; run restore and inspect the backup.' >&2; fi
  fi
  sync
  mount -o remount,ro "$MOUNTPOINT" || echo 'Could not remount read-only.' >&2
  exit "$code"
}
trap on_exit EXIT
trap 'exit 1' HUP INT TERM
if [ "$ACTION" = restore ]; then
  restore_files
  # Recovery must not depend on a running system_server. The original ignores our key.
  echo 'Original framework restored. Reboot manually.'
else
  cp "$PAYLOAD" "$TEMP"
  chown 0:0 "$TEMP"
  chmod 644 "$TEMP"
  restorecon "$TEMP"
  [ "$(vi_file_sha256 "$TEMP")" = "$PATCHED" ] || fail 'Staged system copy differs.'
  # Only these verified preoptimized files are removed, with copies retained above.
  while read -r arch ext digest; do
    source="/system/framework/oat/$arch/services.$ext"
    [ ! -e "$source" ] || [ "$(vi_file_sha256 "$source")" = "$digest" ] || fail 'Preopt changed since backup.'
    rm -f "$source"
  done < "$BACKUP/preopt.list"
  mv -f "$TEMP" "$TARGET"
  restorecon "$TARGET"
  [ "$(vi_file_sha256 "$TARGET")" = "$PATCHED" ] || fail 'Installed hash mismatch.'
  sync
  echo 'Keyboard framework installed and verified. Reboot manually.'
fi
