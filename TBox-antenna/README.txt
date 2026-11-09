TBox-antenna — prebuilt 2.0.7 patch kit

USE
1. Extract the whole folder. Connect the physical-SIM T-box directly by USB.
2. Windows: double-click install_win.cmd. macOS: double-click install_mac.command.
   If macOS blocks downloaded files, allow the launcher/ADB in Privacy & Security.
   Windows needs a working ADB USB driver. No Python, Java or .NET is required.
3. Keep USB and vehicle power connected until SUCCESS or FAILED. Logs are in logs/.
   Initial network registration can take several minutes. If USB disconnects,
   reconnect and run again to inspect the device-side installation.

The kit detects the T-box, not the Android headunit. Version 2.0.7 is required.
The included radio and signal binaries are prebuilt; no firmware is downloaded
from the vehicle or patched on the host. Hash checks also reject an unexpected
binary layout even if its version string is 2.0.7. Original firmware files stay
unchanged beneath runtime bind mounts. MCU/bootloader/firmware partitions are not
flashed. Local profile backup stays on the T-box.

APN
SIM identity automatically selects MCI (mcinet), Irancell (mtnirancell), Rightel
(rightel), Shatel (shatelmobile), plus the profiles in payload/apns.tsv. Unknown
operators request a network-default APN. Carrier-negotiated DNS is used.
If a provider requires a different APN, put only its APN into payload/apn-override;
optional IPv4 DNS goes into payload/dns-override. Then regenerate SHA256SUMS using
maintenance/update-manifest.py (developer Python only), before distributing.
No credentials are embedded. Factory PDP contexts are preserved; one unused
context is allocated. This is not a guarantee that every SIM/plan is supported.
APN profile provenance is in payload/iran-apns-source.json (AOSP, Apache 2.0).

STATUS AND ROLLBACK
SUCCESS means the prebuilt patches ran, mobile HTTPS passed on the T-box,
headunit routing was configured and the factory signal interface was available.
It does not prove headunit rendering or headunit Internet on a disconnected unit.
Verify Internet and the antenna icon on the actual headunit afterward.
Failure disables this kit's next-boot hook and attempts runtime rollback.
Use uninstall_win.cmd / uninstall_mac.command to disable and clean up, then reboot
the T-box. There is no automatic reboot. No generic firewall flush is performed.
An enabled older research helper is deliberately rejected to avoid two competing
boot scripts. It requires migration, not installing this kit over it.

VALIDATION
The source patches were tested on H97X 2.0.7 with MCI, including normal reboot.
This redistributed installer has offline tests only; no connected T-box was
available for its end-to-end test. Windows launchers have not been run on Windows.
Other operators, cold power, sleep/wake, coverage recovery and eCall regression
still require vehicle testing. This kit is not a full firmware recovery backup.

CONTENTS
install_* / uninstall_*: OS-specific double-click launchers.
host_*: USB transport; payload/: shared installation/network code and prebuilt ELF.
tools/: bundled ADB for Windows and universal Intel/Apple Silicon macOS, with
upstream notices. Distribute this entire folder, never just the launcher.
