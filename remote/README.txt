VI Remote — current installation
================================
Build: python3 remote/build-telemetry.py from repository root (or remote/build.sh).
macOS: TBox/TBox-remote/install_mac.command
Windows: TBox/TBox-remote/install_win.cmd
These install/update the current remote agent, collector and SMS support.
Uninstall uses uninstall_mac.command / uninstall_win.cmd and retains user data.
Keep the full TBox folder: the installer uses TBox-antenna/tools for ADB.

For another vehicle, provision working TBox SIM/network access first, install
current Voyah Iran on its headunit, grant remote access for its VIN on the
subscription server, then pair that vehicle's phone. Never copy private pairing
files from another car. Installation rejects unknown firmware contracts.

Current architecture: ARCHITECTURE.fa.txt
Installation/telemetry: README-telemetry.txt
Feature runbooks: TBox/TBox-remote/SECURITY.txt and SMS.txt, README-battery.txt.
Old v1 command-test installer/launchers/build entry were retired2026-11-21.
Historical sources/evidence may remain for protocol research; they are not the
production installation path. Old installer backup: parent work/remote-legacy-installer-20261121.
