EXPERIMENTAL MCU HEARTBEAT COMPATIBILITY — not part of the normal installer

Scope: the owner-authorized Russian MCU relocated to X layout, package SHA256
3168d60dc85c07d91266543a1ce273cc28e93d824dfaea6fbb279104e4ff919f,
on the unchanged X 2.0.7 MPU, serial f1900a18dabeb15d.

Purpose: send OEM Linux heartbeat 04 09 with control-direction 02 every ten
seconds. The experimental MCU handler rejects other directions; its missing-
heartbeat timer expires at 300000 milliseconds. This service does NOT disable
the watchdog, change its timeout, operate vehicle equipment, acquire wake locks,
flash firmware, change banks, or change direction in other processes.

The helper uses the installed libvns/libhcore ABI, checked by SHA256. A dedicated
OEM session reconnects by exiting on error and letting systemd retry after ten
seconds. No received remote command or action is processed. RSS observed ~11MB.
An explicit enabled marker is required; this is an experimental pairing marker,
NOT automatic identification/readback of the currently installed MCU.

Installed files: /usr/local/lib/TBox-experimental-heartbeat/
Runtime unit: /run/systemd/system/TBox-experimental-heartbeat.service
Runtime log: /tmp/TBox-experimental-heartbeat.log
OEM reception evidence: /mnt/hirain_extdata/logs/messages, 'Linux Heart'.
Bootstrap uses the existing board's root console .profile boot path, with its own
marker comment. The profile.before backup is stored in the installed directory.
Boot hook installed; a deliberate reboot test is not implied by that installation.

IMPORTANT: before changing/restoring MCU firmware, stop this helper and remove
its enabled marker. It must not silently be applied to unrelated firmware:
  systemctl stop TBox-experimental-heartbeat.service
  rm /usr/local/lib/TBox-experimental-heartbeat/enabled
Remove only its marked case block from /home/root/.profile if uninstalling;
do not replace the whole profile and erase unrelated antenna/remote hooks.
Stopping it while the experimental MCU is still running can resume 5-minute
MPU resets unless another verified source supplies the required heartbeat.

No normal installer/bootstrap source was modified to enable this globally.
See docs/tbox-experimental-heartbeat-fix-20261110.txt for evidence and limits.
