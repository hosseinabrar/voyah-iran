#!/bin/sh
set -eu
[ "$(id -u)" = 0 ] || { echo 'ERROR: Root required'; exit 1; }
[ "$(uname -m)" = aarch64 ] || { echo 'ERROR: Unsupported CPU'; exit 1; }
[ -f /mnt/hirain_app/etc/software_versions.xml ] || { echo 'ERROR: Not a supported T-box'; exit 1; }
command -v systemctl >/dev/null
[ -x /usr/bin/vnsMonitorCan ] || { echo 'ERROR: OEM CAN monitor missing'; exit 1; }
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
mkdir -p "$BASE/bin"
chmod 700 "$BASE"
for f in vi-remote-agent vi-remote-collector vns-battery vns-battery.py native-check.sh sms-runtime.py uninstall.sh; do
 cp "$f" "$BASE/bin/$f.new"
 chmod 755 "$BASE/bin/$f.new"
 mv "$BASE/bin/$f.new" "$BASE/bin/$f"
done
# Side-by-side fallback only when the installed native dependency is absent.
if [ ! -r /mnt/hirain_app/usr/lib/libvns.so ] || [ ! -r /mnt/hirain_app/usr/lib/libhcore.so ]; then
 mkdir -p "$BASE/native"
 tar -xzf native-libs.tar.gz -C "$BASE/native"
fi
/bin/sh "$BASE/bin/native-check.sh" || { echo 'ERROR: Native VNS session probe failed; see /data/vi-remote/native-probe.log'; exit 1; }
# Shared by Windows/macOS installers. Integrate with an existing antenna helper
# before installing the SMS overlay, preserving its original scripts for rollback.
/usr/bin/python "$BASE/bin/sms-runtime.py" --antenna-hooks
touch "$BASE/sms-enabled"
cat > /etc/systemd/system/vi-remote-sms.service <<'UNIT'
[Unit]
Description=VI Remote OEM SMS initialization
After=network.target hirain-boot.service TBox-antenna.service TBox-remote.service
Before=vi-remote-agent.service
[Service]
Type=oneshot
ExecStart=/usr/bin/python /data/vi-remote/bin/sms-runtime.py
RemainAfterExit=yes
TimeoutStartSec=60
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
cat > /etc/systemd/system/vi-remote-agent.service <<'UNIT'
[Unit]
Description=VI Remote encrypted telemetry
After=network.target vi-remote-sms.service
Wants=vi-remote-sms.service
[Service]
ExecStartPre=/bin/sh /data/vi-remote/bin/native-check.sh
ExecStart=/data/vi-remote/bin/vi-remote-agent -listen 172.16.104.20:18782 -headunit 172.16.104.40
Restart=always
RestartSec=5
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
cat > /etc/systemd/system/vi-remote-collector.service <<'UNIT'
[Unit]
Description=VI Remote OEM telemetry collector
After=network.target
[Service]
ExecStart=/data/vi-remote/bin/vi-remote-collector -xml /mnt/hirain_app/etc/vns/dbc/can-in.xml
Restart=always
RestartSec=5
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable vi-remote-sms.service vi-remote-agent.service vi-remote-collector.service >/dev/null 2>&1
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
