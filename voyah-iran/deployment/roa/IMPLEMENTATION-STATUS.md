# ROA camera switch implementation

Implemented independently from the report and the supplied factory Camera APK. No files from the other developer are needed.

## Components
- `hook_camera_front_roa_switch.js`: hooks OEM `resumeLogic()` after the original runs; makes `camera_change_iv` visible/enabled/clickable when ROA is supported. Calls original `changeClick()` before changing only the in-memory destination to 0/1. Other camera types retain the OEM path. Disable restores remembered view state and removes hooks. A heartbeat timeout removes hooks if the owner disappears.
- `RoaHookManager.kt`: runs in our authenticated root bridge; watches the exact `com.qinggan.camera` process, owns one local injector, waits for the script ready acknowledgement, logs failures, reinjects after PID changes with backoff, and persists enabled state across reboots. Uses no frida-server network port.
- Installer: installs the pinned standalone injector and script in `/data/local/tmp/voyahiran-bridge/roa`; verifies transfer hashes, preserves the previous bridge directory in its existing backup, and updates both through automatic payload preparation.
- UI: checkbox requires ACTIVE CAN state plus an armed/attached hook. An existing ACTIVE state with missing hook installs the hook without sending another toggle. The explicit “نمایش دوربین” button opens the factory Camera on display 0 with preview-only extras.

The DVR APK, regional/GDPR settings, OEM init files and other tools' `/data/local/frida/load.bin` remain untouched. This adds the switch in the factory Camera app, not the DVR recording app.

## Runtime provenance
Frida inject 16.7.19, official Android arm64 build:
https://github.com/frida/frida/releases/download/16.7.19/frida-inject-16.7.19-android-arm64.xz
Source and build instructions: https://github.com/frida/frida-core/tree/16.7.19
License: FRIDA-COPYING.txt (wxWindows Library Licence 3.1).
Uncompressed binary SHA256: 5e5ad93e0f12538829b5f98fb65f1056a72da5f1eb380ada1ddf8a89716b5cc9
The release is pinned to retain its bundled Java bridge. No download is required on the end user's laptop.

## Validation and remaining vehicle test
Executed JS mock tests: initial attach/ready, original method calls, both switch directions, preserving other camera types, capability check, resume visibility, disable/restore. PowerShell, shell, C# syntax and Android resource XML checked. New Kotlin repository/policy regression tests added but not built/run. No Android APK or Windows executable built, and no vehicle commands executed.

Still to validate on the car: SELinux permits this injector to attach; button appears in factory Camera; ID 0/2 live images; close/reopen Camera and reboot recovery; disabling removes switch; coexistence with existing tools. Failures are reported in `VI/ROAHook` and `VI/RearCameraHook`. A waiting hook means Camera is not running yet, not proof of a live picture. System SELinux/verity settings are never disabled to make injection succeed.
