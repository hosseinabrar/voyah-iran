# Safe area / landscape investigation — 2026-09-16

No production files or vehicle state modified. Sources are the supplied firmware, existing services.jar smali decode, and VT_2.8.2.apk.

## Confirmed
- QGAPI.jar / com.qinggan.system.QGWindowManager.LayoutParams: TYPE_QG_NAVIGATION_BAR=2110 and TYPE_QG_STATUSBAR=2118.
- Launcher NavigationBarController.init uses the OEM navigation type, resource navigation_bar_width, full display height, START gravity, and per-screen memoryType. This class controls a sliding navigation window; confirm its relationship to the user's persistent left rail from a live window dump before targeting it.
- QGWindowPolicy APK provides qg.window_policy. Its WindowPolicyImpl arbitrates callback priorities for window types >2000. It is not itself the application content-inset calculator.
- services.jar DisplayPolicy.adjustWindowParamsLw (smali line 6661) recognizes privateFlags bit 0x400000 as isScreenDecor and requires hasStatusBarServicePermission before registration.
- DisplayPolicy.layoutScreenDecorWindows (3206) calculates the occupied edge from actual window frames. A full-height left-edge window advances the left bound; a full-width top-edge window advances the top bound. This is an existing mechanism for reserving screen edges.
- DisplayPolicy.beginLayoutLw invokes that layout routine. DisplayPolicy.layoutWindowLw (10816) computes application parent/display/content frames with flags, insets and IME considerations.
- ActivityRecord.getOrientation (15679), getRequestedOrientation (15787), setRequestedOrientation (24031) and resolveOverrideConfiguration (22802) are present. setRequestedOrientation forwards to setOrientation and notifies task listeners. Forcing only that setter does not necessarily override orientation specified initially in the manifest.

## Implementation candidates, not tested fixes
1. First validate OEM bars and screen-decor registration on the center display. Existing screen-decor handling is a promising edge-reservation mechanism, but applying it globally could affect OEM camera/system windows and does not by itself prove compliance for every immersive app.
2. For user-app-only enforcement, a guarded WindowManager policy patch can constrain actual app frames/configuration to the available rectangle. Coordinate configuration, IME, popups, insets and touch delivery; merely clipping a surface or raising a bar is insufficient.
3. Landscape should be per eligible ActivityRecord, handling both initial manifest orientation and later requests. A global rotation lock is not equivalent to forcing application landscape layout.
4. Use actual center-display geometry; no fixed guessed pixel widths. Exclude system apps, passenger display, cameras and system overlays.
5. Existing installer can host a separate validated services.jar patch, but this investigation has not established a final minimal patch. No patch created.

## Tweaks limits
VT_2.8.2.apk was inspected read-only as ZIP; targeted printable-string search in dex/native/assets found no usable overscan, orientation or inset implementation. Its protection means we cannot attribute the candidates above to Tweaks. The observed behavior reported by the user demonstrates their result, not their algorithm.

## Next runtime evidence
Read-only dumpsys window and dumpsys activity with Chrome open; identify real bar windows, frames, private flags, display IDs and app configuration. Compare before/after Tweaks when a suitable test vehicle is available. Measure portrait app requested orientation and actual bounds. These observations distinguish edge reservation from task resizing and prevent targeting the sliding shortcut rail by mistake.

## Resume here — handoff notes
User paused until they are at the vehicle. This stage is research only.

### Evidence locations; reuse instead of re-decompiling
Workspace: C:/Users/Nahid/Documents/Codex/2026-09-08/x20
Relative paths:
- work/ime-framework-source/smali_classes2/com/android/server/wm/DisplayPolicy.smali
- work/ime-framework-source/smali_classes2/com/android/server/wm/ActivityRecord.smali
- work/ime-framework-source/smali_classes2/com/android/server/wm/WindowState.smali
- work/ime-framework-source/smali_classes2/com/android/server/wm/WindowManagerShellCommand.smali
- work/qgapi-window/sources/com/qinggan/system/QGWindowManager.java (constants 64–65)
- work/window-policy/sources/com/qinggan/windowpolicy/service/WindowPolicyService.java
- work/window-policy/sources/defpackage/b.java (WindowPolicyImpl)
- work/raha-oem/launcher/sources/com/qinggan/launcher/navigation/NavigationBarController.java (init ~35)
- work/qgmod-window/sources (no useful targeted layout/orientation match found)

Originals:
- C:/Users/Nahid/Desktop/voyah/system/framework/services.jar
- C:/Users/Nahid/Desktop/voyah/system/framework/QGAPI.jar
- C:/Users/Nahid/Desktop/voyah/system/priv-app/QGWindowPolicy-release/QGWindowPolicy-release.apk
- C:/Users/Nahid/Desktop/VT_2.8.2.apk
- C:/Users/Nahid/Desktop/decompiled/SystemUI-release-signed (apktool smali, not Java)
The system dump is not guaranteed pristine: previous research found Tweaks' init script in it. Do not label it a clean stock comparison.

### First vehicle session — read-only
Use the user's platform-tools adb.exe. Add -s SERIAL if multiple devices are connected.
Record firmware version and whether Tweaks is installed/enabled; do not uninstall or toggle it just to collect these dumps.
Capture:
1. adb shell dumpsys display
2. adb shell wm size
3. adb shell wm density
4. With Chrome open and overlap visible: dumpsys window + dumpsys activity activities
5. With Myket showing portrait: same dumps with separate filenames.
6. A screenshot/photo of each state helps correlate frames.
No root/remount/reboot or vehicle write is required for these initial dumps.

PowerShell example from platform-tools, single connected device:
    .\adb.exe shell dumpsys window > chrome-window.txt
    .\adb.exe shell dumpsys activity activities > chrome-activities.txt
Repeat with Myket-specific filenames.

### Extract from dumps
- Center display dimensions/density/rotation and stable/content/unrestricted bounds.
- Window titles, packages/UIDs, types, frames, private flags and inset sources of the actual fixed left rail and top bar.
- Whether screen-decor bit 0x400000 is already set.
- Chrome task bounds vs WindowState frame and delivered insets.
- Myket requested orientation, resolved configuration and size-compat/letterbox state.
- Distinguish permanent left rail from sliding shortcut panels; the examined NavigationBarController animates its rail.
- Confirm display IDs from evidence, not assumptions.
- Determine whether app fullscreen flags bypass existing reserved space.

### Next decision
If bars are not registered as reserving space, investigate existing decor mechanism first.
If app bounds/configuration remain wrong, design a guarded user-app-only window policy.
Do not assume a global display-size change or rotation lock solves either problem.
Preserve camera/system window behavior and passenger display.
If services.jar needs changes, retain the existing dynamic per-firmware patch approach.
Do not claim the exact Tweaks implementation is known.
