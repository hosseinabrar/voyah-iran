#!/bin/sh
set -eu
STAGE=/tmp/TBox-antenna-stage
BASE=/usr/local/lib/TBox-antenna
cd "$STAGE"
integrity_log=$(mktemp)
if ! sha256sum -c SHA256SUMS >"$integrity_log" 2>&1; then
 grep -v ': OK$' "$integrity_log" || true
 rm -f "$integrity_log"
 echo 'ERROR: Payload integrity failure; use a fresh complete installer package'
 exit 1
fi
rm -f "$integrity_log"
/bin/sh ./preflight.sh
# Refuse replacement while an installed copy is enabled; installation is idempotent.
if [ -f "$BASE/enabled" ]; then
 echo 'DONE: Patch already installed (use uninstall before replacing it)'
 exit 0
fi
mkdir -p "$BASE"
cp ./* "$BASE/"
chmod 755 "$BASE/hrild-signal"
# Keep the original profile locally; append only our guarded hook.
mkdir -p /home/root
if [ ! -f "$BASE/profile-recorded" ]; then
 if [ -f /home/root/.profile ]; then cp /home/root/.profile "$BASE/profile.before"; fi
 touch "$BASE/profile-recorded"
fi
touch /home/root/.profile
if ! grep -q '# TBox-antenna boot hook' /home/root/.profile; then
 cat >>/home/root/.profile <<'HOOK'

# TBox-antenna boot hook
case "$(tty 2>/dev/null)" in
 /dev/ttyS0|/dev/ttyGS1) /bin/sh /usr/local/lib/TBox-antenna/bootstrap.sh >/tmp/TBox-antenna-bootstrap.log 2>&1 ;;
esac
HOOK
fi
: >/tmp/voyah-tbox-kit.log
rm -f "$BASE/ready"
touch "$BASE/enabled"
/bin/sh "$BASE/bootstrap.sh"
echo 'DONE: Prebuilt patches installed; starting network checks'
