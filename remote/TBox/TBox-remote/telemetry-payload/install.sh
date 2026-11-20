#!/bin/sh
set -eu
[ "$(id -u)" = 0 ] || { echo 'ERROR: Root required'; exit 1; }
[ "$(uname -m)" = aarch64 ] || { echo 'ERROR: Unsupported CPU'; exit 1; }
{ [ -f /mnt/hirain_app/etc/software_versions.xml ] || [ -f /mnt/hirain_app/usr/etc/software_versions.xml ]; } || { echo 'ERROR: Not a supported T-box'; exit 1; }
command -v systemctl >/dev/null
{ [ -x /usr/bin/vnsMonitorCan ] || [ -x /mnt/hirain_app/usr/bin/vnsMonitorCan ]; } || { echo 'ERROR: OEM CAN monitor missing'; exit 1; }
[ -r /mnt/hirain_app/etc/vns/dbc/can-in.xml ] || { echo 'ERROR: OEM CAN schema missing'; exit 1; }
schema_hash=$(sha256sum /mnt/hirain_app/etc/vns/dbc/can-in.xml)
case "$schema_hash" in
  3a83470d0b2c2a43b3a4d64b3bf96c21b33e028bffcfc0fa76cf33a0d51d123e*|641700045deb8ed8fe1b6b1f006c2be93e650a77625f620616c628c47e19d668*|dd69b0cdbdc808756c805a5c6688bb6ec8cad0cf5f4f0334ebac1ab0ec9483b8*) ;;
  *) echo 'ERROR: Unverified CAN schema; install a matching telemetry profile first'; exit 1 ;;
esac
# SMS uses a verified reversible OEM process overlay; no modem/MCU flash or subscription changes.
cd /tmp/vi-remote-install
sha256sum -c SHA256SUMS >/dev/null
BASE=/data/vi-remote
# Keep executable files in an app-owned bind mount when the small root userdata
# volume cannot fit them. Never remount the factory data partition executable.
mkdir -p /usr/local/lib/vi-remote
if [ ! -d "$BASE/bin" ] && [ "$(df -k /data | awk 'NR==2 {print $4}')" -lt 51200 ]; then
 [ "$(df -k /mnt/hirain_data | awk 'NR==2 {print $4}')" -ge 51200 ] || { echo 'ERROR: Insufficient application storage'; exit 1; }
 mkdir -p /mnt/hirain_data/vi-remote "$BASE"
 chmod 700 /mnt/hirain_data/vi-remote
 mountpoint -q "$BASE" || mount --bind /mnt/hirain_data/vi-remote "$BASE"
 mount -o remount,bind,rw,exec "$BASE"
 echo /mnt/hirain_data/vi-remote > /usr/local/lib/vi-remote/storage
fi
mkdir -p "$BASE/bin" "$BASE/units"
chmod 700 "$BASE"
for f in vi-remote-agent vi-remote-collector vns-battery vns-battery.py native-check.sh sms-runtime.py uninstall.sh oem-env.sh python-runtime run-service location-read.py sms-pdu.py; do
 cp "$f" "$BASE/bin/$f.new"
 chmod 755 "$BASE/bin/$f.new"
 mv "$BASE/bin/$f.new" "$BASE/bin/$f"
done
if ! /usr/bin/python -c "import ctypes,json,hashlib,fcntl,subprocess,threading" >/dev/null 2>&1; then
 mkdir -p "$BASE/python"
 tar -xzf python-runtime.tar.gz -C "$BASE/python"
fi
# Side-by-side fallback only when the installed native dependency is absent.
if [ ! -r /mnt/hirain_app/usr/lib/libvns.so ] || [ ! -r /mnt/hirain_app/usr/lib/libhcore.so ]; then
 mkdir -p "$BASE/native"
 tar -xzf native-libs.tar.gz -C "$BASE/native"
fi
/bin/sh "$BASE/bin/native-check.sh" || { echo 'ERROR: Native VNS session probe failed; see /data/vi-remote/native-probe.log'; exit 1; }
# Shared by Windows/macOS installers. Integrate with an existing antenna helper
# before installing the SMS overlay, preserving its original scripts for rollback.
"$BASE/bin/python-runtime" "$BASE/bin/sms-runtime.py" --antenna-hooks
touch "$BASE/sms-enabled"
cat > "$BASE/units/vi-remote-sms.service" <<'UNIT'
[Unit]
Description=VI Remote OEM SMS initialization
After=network.target hirain-boot.service TBox-antenna.service TBox-remote.service
Before=vi-remote-agent.service
[Service]
Type=oneshot
ExecStart=/data/vi-remote/bin/run-service sms
RemainAfterExit=yes
TimeoutStartSec=60
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
cat > "$BASE/units/vi-remote-agent.service" <<'UNIT'
[Unit]
Description=VI Remote encrypted telemetry
After=network.target vi-remote-sms.service
Wants=vi-remote-sms.service
[Service]
ExecStartPre=/bin/sh /data/vi-remote/bin/native-check.sh
ExecStart=/data/vi-remote/bin/run-service agent
Restart=always
RestartSec=5
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
cat > "$BASE/units/vi-remote-collector.service" <<'UNIT'
[Unit]
Description=VI Remote OEM telemetry collector
After=network.target
[Service]
ExecStart=/data/vi-remote/bin/run-service collector
Restart=always
RestartSec=5
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
cp bootstrap.sh /usr/local/lib/vi-remote/bootstrap.sh
chmod 700 /usr/local/lib/vi-remote/bootstrap.sh
touch /usr/local/lib/vi-remote/enabled
if [ -w /etc/systemd/system ]; then
 cp "$BASE"/units/*.service /etc/systemd/system/
 systemctl daemon-reload
 systemctl enable vi-remote-sms.service vi-remote-agent.service vi-remote-collector.service >/dev/null 2>&1
else
 # Factory serial autologin is the existing persistent startup mechanism.
 [ -f /home/root/.profile.before-vi-remote ] || cp /home/root/.profile /home/root/.profile.before-vi-remote
 if ! grep -q '# VI Remote telemetry boot' /home/root/.profile; then
 cat >> /home/root/.profile <<'BOOT'

# VI Remote telemetry boot
case "$(tty 2>/dev/null)" in
 /dev/ttyS0|/dev/ttyGS1) /bin/sh /usr/local/lib/vi-remote/bootstrap.sh >/tmp/vi-remote-bootstrap.log 2>&1 ;;
esac
BOOT
 fi
 cp "$BASE"/units/*.service /run/systemd/system/
 systemctl daemon-reload
fi
if ! systemctl restart vi-remote-sms.service; then
 echo 'WARNING: SMS initialization failed; vehicle telemetry remains available'
fi
systemctl restart vi-remote-agent.service vi-remote-collector.service
sleep 3
systemctl is-active --quiet vi-remote-agent.service || { echo 'ERROR: Remote service did not start'; exit 1; }
systemctl is-active --quiet vi-remote-collector.service || { echo 'ERROR: Telemetry collector did not start'; exit 1; }
echo 'DONE: Remote services started'
echo 'DONE: Pairing data preserved; remote access defaults to disabled'
echo 'SUCCESS: Installation complete; enable Remote Access on the subscription, then open Voyah Iran'
