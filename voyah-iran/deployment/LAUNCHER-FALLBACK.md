# Launcher fallback (2026-09-27)

Boot log `app list 2 log-2026-09-27_13-22-18.txt` proves the root bridge's
shell SELinux domain cannot ptrace the OEM system_app launcher. This precedes
all Java hooks. Replacing `su` does not provide a domain transition in the supplied
H97A policy. Keep the VIN-capable shell bridge and SELinux enforcing unchanged.

The navigation manager publishes `voyah_iran_launcher_fallback=1` when attachment
fails, and clears it when drawer hook readiness is reported. The installer enables
LauncherDrawerService alongside existing accessibility services. It observes only
clicks from the factory launcher's `screen_up_all_app_icon`. After the factory
click completes, it opens the central-display drawer. Home and passenger controls
are not intercepted. There is no coordinate overlay or continuous app scanning.

The drawer queries enabled/exported MAIN/LAUNCHER activities when opened and lists
user and system apps with labels/icons. Exact components launch on display 0.
OEM internal tiles without launcher activities are NOT recreated or guessed:
the “factory list” button opens the original showAllApp page for those functions.
This is a replacement list of launchable apps, not a clone of every OEM tile.

Hardware check: publish/install/reboot; expect VI/Navigation fallback=true and
VI/LauncherDrawer observer connected, then central drawer opened and app counts.
Test app launch, Home, Back, original factory list, uninstall/reopen, and unchanged
behavior on firmware where VI_LAUNCHER_APPS_READY succeeds. APK-only installation
does not provision the new accessibility service or update the copied bridge APK.

Follow-up: hardware confirmed the fallback UI works after enabling the service
manually. Installed APK contained the service and fallback=1, but the enabled
accessibility list omitted it. Installer now uses the full component name and
verifies the settings write. The bridge also reconciles that service every 30s
while fallback is active, preserving existing services, and writes only when
missing (or accessibility is disabled). This repairs missing provisioning and
startup settings-write races without re-binding a healthy service. Configuration
success means settings verified, not proof of service connection; look for the
separate LauncherDrawer observer-connected log.

Limit: fallback relies on the factory button emitting an accessibility click with
its resource ID. No hardware confirmation yet. No fallback hook for passenger
buttons is inferred on Android 9, where event display targeting is ambiguous.
